#define _GNU_SOURCE
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <pwd.h>
#include <pthread.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/auxv.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/statvfs.h>
#include <sys/syscall.h>
#include <sys/sysinfo.h>
#include <sys/time.h>
#include <sys/un.h>
#include <sys/utsname.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>
#include <utime.h>

#define FP_STUB_ADDR 0xffff00000UL
#define FP_STUB_SIZE 4096UL
#define MAX_BINDS 256
#define FP_SLOW (-100000L)
#define MAX_LINKS 16

typedef long (*stub_fn)(long nr, long a, long b, long c, long d, long e, long f);
static stub_fn fp_sys;
static int fp_on;

struct bind { char *host; size_t hlen; char *guest; size_t glen; int canon; int dropped; };
static struct bind binds[MAX_BINDS];
static int nbinds;
static char root[PATH_MAX];
static size_t rootlen;
static long long ttl_ns = 2000000000LL;
static unsigned long hits, misses;

static long sc(long nr, long a, long b, long c, long d, long e) { return fp_sys(nr, a, b, c, d, e, 0); }
static void fp_init(void);
static pthread_once_t init_once = PTHREAD_ONCE_INIT;
static inline int fp_ready(void) {
  pthread_once(&init_once, fp_init);
  return fp_on;
}

static int under(const char *path, const char *dir, size_t dlen) {
  if (dlen == 1 && dir[0] == '/') return path[0] == '/';
  return strncmp(path, dir, dlen) == 0 && (path[dlen] == 0 || path[dlen] == '/');
}


static void stats(void) {
  char line[160];
  int n = snprintf(line, sizeof line, "fastpath[%d] %s: %lu fast, %lu to proot\n", getpid(),
                   program_invocation_short_name, hits, misses);
  if (write(2, line, n) < 0) {}
}

static const char *miss_log;
static long fp_open(int dirfd, const char *path, int flags, mode_t mode);

static void missed(const char *call, int dirfd, const char *path) {
  misses++;
  if (!miss_log) return;
  char line[2 * PATH_MAX + 128], base[PATH_MAX] = "";
  if (path && path[0] != '/' && dirfd != AT_FDCWD) {
    char link[40];
    snprintf(link, sizeof link, "/proc/self/fd/%d", dirfd);
    long n = sc(SYS_readlinkat, AT_FDCWD, (long)link, (long)base, sizeof base - 1, 0);
    base[n > 0 ? n : 0] = 0;
  }
  int n = snprintf(line, sizeof line, "%d %s %s %s%s%s\n", getpid(), program_invocation_short_name, call,
                   base, base[0] ? "/" : "", path ? path : "(null)");
  long fd = fp_open(AT_FDCWD, miss_log, O_WRONLY | O_APPEND | O_CREAT | O_CLOEXEC, 0644);
  if (fd < 0) return;
  sc(SYS_write, fd, (long)line, n < (int)sizeof line ? n : (int)sizeof line - 1, 0, 0);
  sc(SYS_close, fd, 0, 0, 0, 0);
}

static long canonical(char *path, size_t size) {
  long fd = sc(SYS_openat, AT_FDCWD, (long)path, O_PATH | O_CLOEXEC, 0, 0);
  if (fd < 0) return fd;
  char link[40], out[PATH_MAX];
  snprintf(link, sizeof link, "/proc/self/fd/%ld", fd);
  long n = sc(SYS_readlinkat, AT_FDCWD, (long)link, (long)out, sizeof out - 1, 0);
  sc(SYS_close, fd, 0, 0, 0, 0);
  if (n <= 0 || out[0] != '/') return -ENOENT;
  if ((size_t)n >= size) return -ENAMETOOLONG;
  out[n] = 0;
  memcpy(path, out, n + 1);
  return 0;
}

static long tracer;

static pthread_mutex_t cache_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t bind_lock;
static pthread_mutex_t long_lock;
static void after_fork(void) { pthread_mutex_init(&cache_lock, NULL); pthread_mutex_init(&bind_lock, NULL); pthread_mutex_init(&long_lock, NULL); }

static int traced_by_match(const char *key) {
  static char buf[32768];
  char path[48], want[160];
  long fd = sc(SYS_openat, AT_FDCWD, (long)"/proc/self/status", O_RDONLY | O_CLOEXEC, 0, 0);
  if (fd < 0) return 0;
  long n = sc(SYS_read, fd, (long)buf, 4095, 0, 0);
  sc(SYS_close, fd, 0, 0, 0, 0);
  if (n <= 0) return 0;
  buf[n] = 0;
  const char *line = strstr(buf, "\nTracerPid:");
  if (!line) return 0;
  tracer = strtol(line + 11, NULL, 10);
  if (tracer <= 0) return 0;
  snprintf(path, sizeof path, "/proc/%ld/environ", tracer);
  if (snprintf(want, sizeof want, "PROOT_FASTPATH=%s", key) >= (int)sizeof want) return 0;
  fd = sc(SYS_openat, AT_FDCWD, (long)path, O_RDONLY | O_CLOEXEC, 0, 0);
  if (fd < 0) return 0;
  size_t have = 0;
  for (long r; have < sizeof buf - 1 && (r = sc(SYS_read, fd, (long)buf + have, sizeof buf - 1 - have, 0, 0)) > 0;) have += r;
  sc(SYS_close, fd, 0, 0, 0, 0);
  buf[have] = 0;
  int found = 0;
  for (size_t i = 0; i < have; i += strlen(buf + i) + 1) {
    if (strcmp(buf + i, want) == 0) found = 1;
    if (strncmp(buf + i, "PROOT_NO_SECCOMP=", 17) == 0) return 0;
  }
  return found;
}

static int in_tracer(struct bind *bd) {
  char h[PATH_MAX];
  if (snprintf(h, sizeof h, "/proc/%ld%s", tracer, bd->host + 10) >= (int)sizeof h) return 0;
  long e = canonical(h, sizeof h);
  if (e == -EACCES || e == -EPERM) return 0;
  memcpy(bd->host, h, strlen(h) + 1);
  bd->hlen = strlen(bd->host);
  bd->dropped = e < 0;
  bd->canon = 1;
  return 1;
}

static void fp_init(void) {
  const char *r = getenv("PROOT_FP_ROOT"), *b = getenv("PROOT_FP_BINDS"), *t = getenv("PROOT_FP_TTL_MS");
  const char *key = getenv("PROOT_FP_KEY");
  if (!r || !*r || !key || !*key || getenv("PROOT_FP_OFF")) return;
  void *p = mmap((void *)FP_STUB_ADDR, FP_STUB_SIZE, PROT_READ | PROT_WRITE,
                 MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
  if (p != (void *)FP_STUB_ADDR) {
    if (p != MAP_FAILED) { munmap(p, FP_STUB_SIZE); return; }
    if (errno != EEXIST) return;
  } else {
    static const uint32_t code[] = {
        0xAA0003E8,    0xAA0103E0,    0xAA0203E1,
        0xAA0303E2,    0xAA0403E3,    0xAA0503E4,
        0xAA0603E5,    0xD4000001,        0xD65F03C0,
    };
    memcpy(p, code, sizeof code);
    __builtin___clear_cache((char *)p, (char *)p + sizeof code);
    if (mprotect(p, FP_STUB_SIZE, PROT_READ | PROT_EXEC) != 0) { munmap(p, FP_STUB_SIZE); return; }
  }
  fp_sys = (stub_fn)FP_STUB_ADDR;
  if (!traced_by_match(key)) { fp_sys = NULL; return; }
  if (t) ttl_ns = atoll(t) * 1000000LL;
  snprintf(root, sizeof root, "%s", r);
  canonical(root, sizeof root);
  rootlen = strlen(root);
  if (rootlen <= 1) { fp_sys = NULL; return; }
  const char *s = b;
  int unheld = 0;
  for (; s && *s && nbinds < MAX_BINDS;) {
    const char *end = strchr(s, '|');
    size_t len = end ? (size_t)(end - s) : strlen(s);
    char spec[2 * PATH_MAX];
    if (len > 0 && len < sizeof spec) {
      memcpy(spec, s, len);
      spec[len] = 0;
      char *colon = strchr(spec, ':');
      if (colon) *colon = 0;
      struct bind *bd = &binds[nbinds];
      static char hosts[MAX_BINDS][PATH_MAX], guests[MAX_BINDS][512];
      const char *g = colon ? colon + 1 : spec;
      if (strlen(g) >= sizeof guests[0]) { unheld = 1; break; }
      bd->host = hosts[nbinds];
      bd->guest = guests[nbinds];
      snprintf(bd->guest, sizeof guests[0], "%s", g);
      snprintf(bd->host, PATH_MAX, "%.*s", PATH_MAX - 1, spec);
      bd->hlen = strlen(bd->host);
      bd->glen = strlen(bd->guest);
      if (bd->guest[0] == '/' && bd->glen > 1 && bd->hlen > 0) nbinds++;
    } else if (len > 0) {
      unheld = 1;
      break;
    }
    s = end ? end + 1 : NULL;
  }
  if (unheld || (s && *s)) { fp_sys = NULL; return; }
  for (int i = 0; i < nbinds; i++)
    if (under(binds[i].host, "/proc/self", 10) && !in_tracer(&binds[i])) { fp_sys = NULL; return; }
  pthread_atfork(NULL, NULL, after_fork);
  fp_on = 1;
  if (getenv("PROOT_FP_STATS")) atexit(stats);
  miss_log = getenv("PROOT_FP_LOG");
  if (miss_log && miss_log[0] != '/') miss_log = NULL;
}

static pthread_mutex_t bind_lock = PTHREAD_MUTEX_INITIALIZER;
static int held(struct bind *bd) {
  if (!__atomic_load_n(&bd->canon, __ATOMIC_ACQUIRE)) {
    if (pthread_mutex_trylock(&bind_lock) != 0) return -1;
    if (!bd->canon) {
      bd->dropped = canonical(bd->host, PATH_MAX) < 0;
      bd->hlen = strlen(bd->host);
      __atomic_store_n(&bd->canon, 1, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&bind_lock);
  }
  return !bd->dropped;
}


static int reserved(const char *g) {
  return under(g, "/dev/fd", 7) || under(g, "/dev/stdin", 10) || under(g, "/dev/stdout", 11)
      || under(g, "/dev/stderr", 11);
}

static int bind_of(const char *g, struct bind **out) {
  struct bind *bd = NULL;
  for (int i = 0; i < nbinds; i++) {
    if (!under(g, binds[i].guest, binds[i].glen) || (bd && binds[i].glen < bd->glen)) continue;
    int h = held(&binds[i]);
    if (h < 0) return -1;
    if (h) bd = &binds[i];
  }
  *out = bd;
  return 0;
}

static int to_host(const char *g, char *host, struct bind **through) {
  struct bind *bd;
  if (bind_of(g, &bd) < 0) return -1;
  if (through) *through = bd;
  int n = bd ? snprintf(host, PATH_MAX, "%s%s", bd->host, g + bd->glen)
             : snprintf(host, PATH_MAX, "%s%s", root, strcmp(g, "/") ? g : "");
  return n < PATH_MAX ? 0 : -1;
}

static int glue_above_binding(const char *g, size_t len) {
  int glue = 0;
  for (int i = 0; i < nbinds && glue == 0; i++)
    if (binds[i].glen > len && under(binds[i].guest, g, len)) glue = held(&binds[i]);
  return glue;
}

static int guest_name(const char *h, int k, char *g) {
  char back[PATH_MAX];
  size_t deepest = 0;
  if (under(h, root, rootlen)) {
    if (k > 0 || snprintf(g, PATH_MAX, "%s", h[rootlen] ? h + rootlen : "/") >= PATH_MAX) return -1;
    return reserved(g) || to_host(g, back, NULL) < 0 || strcmp(back, h) != 0 ? -1 : 0;
  }
  for (int i = 0; i < nbinds; i++) {
    int in = held(&binds[i]);
    if (in < 0) return -1;
    if (in && under(h, binds[i].host, binds[i].hlen) && binds[i].hlen > deepest) deepest = binds[i].hlen;
  }
  for (int i = 0; deepest && i < nbinds; i++) {
    if (binds[i].dropped || binds[i].hlen != deepest || !under(h, binds[i].host, binds[i].hlen)) continue;
    if (k-- > 0) continue;
    if (snprintf(g, PATH_MAX, "%s%s", binds[i].guest, h + binds[i].hlen) >= PATH_MAX) return -1;
    return reserved(g) || to_host(g, back, NULL) < 0 || strcmp(back, h) != 0 ? -1 : 0;
  }
  return -1;
}

static int to_guest(const char *h, char *g, size_t size) {
  char other[PATH_MAX];
  if (size < PATH_MAX || guest_name(h, 0, g) < 0) return -1;
  for (int k = 1; guest_name(h, k, other) == 0; k++)
    if (strcmp(other, g) != 0) return -1;
  return 0;
}

static int guest_text(const char *h, char *g) {
  int in = h[0] == '/' && under(h, root, rootlen);
  for (int i = 0; h[0] == '/' && !in && i < nbinds; i++) {
    int live = held(&binds[i]);
    if (live < 0) return -1;
    in = live && under(h, binds[i].host, binds[i].hlen);
  }
  if (in) return to_guest(h, g, PATH_MAX);
  memcpy(g, h, strlen(h) + 1);
  return 0;
}

static int host_bind(const char *h) {
  int found = -1;
  for (int i = 0; i < nbinds; i++) {
    int live = held(&binds[i]);
    if (live < 0) return -2;
    if (!live || !under(h, binds[i].host, binds[i].hlen)) continue;
    if (found < 0 || binds[i].hlen > binds[found].hlen) found = i;
    else if (binds[i].hlen == binds[found].hlen && strcmp(binds[i].guest, binds[found].guest) != 0) found = -3;
  }
  return found == -3 ? -2 : found;
}

static long detranslated(char *text, const char *at) {
  if (text[0] != '/') return 0;
  if (!under(at, root, rootlen) && !under(text, root, rootlen)) {
    int own = host_bind(at), to = host_bind(text);
    if (own == -2 || to == -2) return FP_SLOW;
    if (own >= 0 && to >= 0 && strcmp(binds[own].host, binds[to].host) == 0) {
      const struct bind *bd = &binds[to];
      if (strcmp(bd->host, bd->guest) == 0) return 0;
      char g[PATH_MAX];
      if (snprintf(g, sizeof g, "%s%s", bd->guest, text + bd->hlen) >= (int)sizeof g) return FP_SLOW;
      strcpy(text, g);
      return 1;
    }
  }
  if (!under(text, root, rootlen)) return 0;
  if (!text[rootlen]) strcpy(text, "/");
  else memmove(text, text + rootlen, strlen(text + rootlen) + 1);
  return 1;
}


#define SLOTS 512
#define SLOT_PATH 256
struct slot { long long stamp; unsigned hash; unsigned gen; char literal[SLOT_PATH]; char guest[SLOT_PATH]; };
static struct slot cache[SLOTS];
static unsigned generation;

static long long now_ns(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC_COARSE, &ts);
  return ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

static unsigned hash_of(const char *s) {
  unsigned h = 2166136261u;
  while (*s) h = (h ^ (unsigned char)*s++) * 16777619u;
  return h;
}

static void forget(void) { __atomic_add_fetch(&generation, 1, __ATOMIC_RELEASE); }

static int cached(const char *literal, unsigned h, char *guest) {
  if (ttl_ns <= 0 || pthread_mutex_trylock(&cache_lock) != 0) return 0;
  struct slot *e = &cache[h % SLOTS];
  int hit = e->stamp && e->hash == h && e->gen == __atomic_load_n(&generation, __ATOMIC_ACQUIRE)
         && now_ns() - e->stamp < ttl_ns && strcmp(e->literal, literal) == 0;
  if (hit) memcpy(guest, e->guest, strlen(e->guest) + 1);
  pthread_mutex_unlock(&cache_lock);
  return hit;
}

static void remember(const char *literal, unsigned h, const char *guest) {
  if (ttl_ns <= 0 || strlen(literal) >= SLOT_PATH || strlen(guest) >= SLOT_PATH) return;
  unsigned gen = __atomic_load_n(&generation, __ATOMIC_ACQUIRE);
  if (pthread_mutex_trylock(&cache_lock) != 0) return;
  struct slot *e = &cache[h % SLOTS];
  e->hash = h;
  e->gen = gen;
  strcpy(e->literal, literal);
  strcpy(e->guest, guest);
  e->stamp = now_ns();
  pthread_mutex_unlock(&cache_lock);
}

static int binding_below(const char *g) {
  size_t len = strlen(g);
  for (int i = 0; i < nbinds; i++)
    if (binds[i].glen > len && under(binds[i].guest, g, len)) return 1;
  return 0;
}


struct res {
  char guest[PATH_MAX];
  char host[PATH_MAX];
  struct stat st;
  int exists;
  int named;
  int libc;
  int moving;
  int magic;
};

static int gone(const char *h, size_t n) {
  return n > 10 && strcmp(h + n - 10, " (deleted)") == 0;
}

static long own_cwd(char *g) {
  char h[PATH_MAX];
  long n = sc(SYS_readlinkat, AT_FDCWD, (long)"/proc/self/cwd", (long)h, sizeof h - 1, 0);
  if (n <= 0 || h[0] != '/') return FP_SLOW;
  h[n] = 0;
  if (gone(h, n) || to_guest(h, g, PATH_MAX) < 0) return FP_SLOW;
  return (long)strlen(g);
}

static char exe_text[PATH_MAX];
static int exe_len;
static long own_exe(char *g) {
  int n = __atomic_load_n(&exe_len, __ATOMIC_ACQUIRE);
  if (n > 0) {
    memcpy(g, exe_text, n + 1);
    return n;
  }
  long t = syscall(SYS_readlinkat, AT_FDCWD, "/proc/self/exe", g, PATH_MAX - 1);
  if (t <= 0 || t >= PATH_MAX - 1) return FP_SLOW;
  g[t] = 0;
  static pthread_mutex_t exe_lock = PTHREAD_MUTEX_INITIALIZER;
  if (pthread_mutex_trylock(&exe_lock) == 0) {
    if (!exe_len) {
      memcpy(exe_text, g, t + 1);
      __atomic_store_n(&exe_len, (int)t, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&exe_lock);
  }
  return t;
}

static long proc_readlink(const struct res *r, char *text, size_t size, int *changed) {
  const char *name = strrchr(r->guest, '/') + 1;
  size_t blen = name - 1 - r->guest;
  char base[32];
  int pid = blen > 6 ? atoi(r->guest + 6) : 0;
  int bn = snprintf(base, sizeof base, "/proc/%d", pid);
  if (pid != 0 && (size_t)bn == blen && strncmp(r->guest, base, blen) == 0
      && (!strcmp(name, "exe") || !strcmp(name, "cwd") || !strcmp(name, "root"))) {
    if (pid != getpid() || !strcmp(name, "root")) return FP_SLOW;
    *changed = 1;
    return name[0] == 'e' ? own_exe(text) : own_cwd(text);
  }
  char h[PATH_MAX];
  long t = sc(SYS_readlinkat, AT_FDCWD, (long)r->host, (long)h, sizeof h - 1, 0);
  if (t < 0) return t;
  if (t >= (long)sizeof h - 1) return FP_SLOW;
  if (size && (size_t)t > size) t = size;
  h[t] = 0;
  if (guest_text(h, text) < 0) return FP_SLOW;
  *changed = strcmp(h, text) != 0;
  return (long)strlen(text);
}

static int fd_link(const char *g) {
  const char *p = g + 6;
  if (strncmp(g, "/proc/", 6) != 0 || *p < '1' || *p > '9') return 0;
  while (*p >= '0' && *p <= '9') p++;
  if (strncmp(p, "/fd/", 4) != 0 || p[4] < '0' || p[4] > '9') return 0;
  return 1;
}

static long proc_link(struct res *r, char *text, int final) {
  int changed;
  if (!under(r->host, "/proc", 5)) return FP_SLOW;
  r->moving = 1;
  if (!strcmp(r->guest, "/proc/self") || !strcmp(r->guest, "/proc/thread-self")) {
    long t = sc(SYS_readlinkat, AT_FDCWD, (long)r->host, (long)text, PATH_MAX - 1, 0);
    if (t <= 0 || t >= PATH_MAX - 1) return FP_SLOW;
    text[t] = 0;
    return t;
  }
  if (!r->libc && final && fd_link(r->guest)) return 0;
  long t = proc_readlink(r, text, 0, &changed);
  return t <= 0 ? FP_SLOW : t;
}

static int pop(char *g) {
  char *slash = strrchr(g, '/');
  if (!slash) return -1;
  if (slash == g) g[1] = 0;
  else *slash = 0;
  return 0;
}

#define UNSEEN 2
#define MAY_BE_UNSEEN 2
#define GLUE 3

static long walk(struct res *r, const char *text, int follow, int last) {
  char rest[PATH_MAX];
  size_t len = strlen(text);
  int links = 0, stated = 0;
  if (len >= sizeof rest) return FP_SLOW;
  memcpy(rest, text, len + 1);
  char *c = rest;
  r->exists = 0;
  for (;;) {
    while (*c == '/') c++;
    if (!*c) break;
    char *e = strchrnul(c, '/');
    size_t n = e - c;
    char *after = e;
    while (*after == '/') after++;
    int final = *after == 0;
    stated = 0;
    if (n == 1 && c[0] == '.') { c = e; continue; }
    if (n == 2 && c[0] == '.' && c[1] == '.') { pop(r->guest); c = e; continue; }
    size_t glen = strlen(r->guest);
    if (glen + n + 2 >= PATH_MAX) return FP_SLOW;
    if (glen > 1) r->guest[glen++] = '/';
    memcpy(r->guest + glen, c, n);
    r->guest[glen + n] = 0;
    if (reserved(r->guest)) return FP_SLOW;
    struct bind *through;
    if (to_host(r->guest, r->host, &through) < 0) return FP_SLOW;
    long s = sc(SYS_newfstatat, AT_FDCWD, (long)r->host, (long)&r->st, AT_SYMLINK_NOFOLLOW, 0);
    if (s < 0) {
      int glue = glue_above_binding(r->guest, glen + n);
      if (glue < 0) return FP_SLOW;
      if (glue) {
        if (final) return GLUE;
        c = e;
        continue;
      }
      if (!final || !last) return r->libc ? s : -ENOENT;
      return s == -ENOENT ? 1 : UNSEEN;
    }
    if (S_ISLNK(r->st.st_mode) && (!final || follow)) {
      char target[PATH_MAX];
      long t;
      if (++links > MAX_LINKS || binding_below(r->guest)) return FP_SLOW;
      if (under(r->guest, "/proc", 5)) {
        t = proc_link(r, target, final && last);
        if (t == FP_SLOW) return FP_SLOW;
        if (t == 0) {
          if (sc(SYS_newfstatat, AT_FDCWD, (long)r->host, (long)&r->st, 0, 0) < 0) return FP_SLOW;
          r->magic = 1;
          r->exists = 1;
          return 0;
        }
      } else {
        t = sc(SYS_readlinkat, AT_FDCWD, (long)r->host, (long)target, sizeof target - 1, 0);
        if (t <= 0 || t >= (long)sizeof target - 1) return FP_SLOW;
        target[t] = 0;
        if (detranslated(target, r->host) == FP_SLOW) return FP_SLOW;
        t = strlen(target);
      }
      if (target[0] == '/') strcpy(r->guest, "/");
      else pop(r->guest);
      size_t tail = strlen(e);
      if ((size_t)t + 1 + tail >= sizeof rest) return FP_SLOW;
      memmove(rest + t + 1, e, tail + 1);
      memcpy(rest, target, t);
      rest[t] = '/';
      c = rest;
      continue;
    }
    if (!final && !S_ISDIR(r->st.st_mode)) return -ENOTDIR;
    stated = 1;
    c = e;
  }
  if (!stated) {
    if (to_host(r->guest, r->host, NULL) < 0) return FP_SLOW;
    long s = sc(SYS_newfstatat, AT_FDCWD, (long)r->host, (long)&r->st, AT_SYMLINK_NOFOLLOW, 0);
    if (s < 0 && strcmp(r->guest, "/") != 0 && glue_above_binding(r->guest, strlen(r->guest))) return FP_SLOW;
    if (s < 0) return s == -ENOENT ? FP_SLOW : s;
  }
  r->exists = 1;
  return 0;
}

static int base_of(int dirfd, char *h) {
  char link[40];
  if (dirfd == AT_FDCWD) snprintf(link, sizeof link, "/proc/self/cwd");
  else snprintf(link, sizeof link, "/proc/self/fd/%d", dirfd);
  long n = sc(SYS_readlinkat, AT_FDCWD, (long)link, (long)h, PATH_MAX - 1, 0);
  if (n <= 0 || n >= PATH_MAX - 1) return -1;
  if (h[0] != '/') return dirfd == AT_FDCWD ? -1 : -ENOTDIR;
  h[n] = 0;
  return gone(h, n) ? -1 : 0;
}

static long resolve_in(const char *base, const char *path, int follow, struct res *r) {
  char norm[PATH_MAX];
  size_t o = 0;
  int dotdot = 0;
  size_t plen = strlen(path);
  int trailing = plen > 1 && path[plen - 1] == '/';
  for (int part = base ? 0 : 1; part < 2; part++) {
    for (const char *c = part ? path : base; *c;) {
      while (*c == '/') c++;
      if (!*c) break;
      const char *e = strchrnul(c, '/');
      size_t len = e - c;
      c = e;
      if (len == 1 && e[-1] == '.') continue;
      if (len == 2 && e[-1] == '.' && e[-2] == '.') dotdot = 1;
      if (o + len + 2 >= sizeof norm) return FP_SLOW;
      norm[o++] = '/';
      memcpy(norm + o, e - len, len);
      o += len;
    }
  }
  norm[o] = 0;
  strcpy(r->guest, "/");
  r->moving = 0;
  r->magic = 0;
  long w;
  char *slash = strrchr(norm, '/');
  if (o == 0 || dotdot || slash == norm) {
    w = walk(r, o ? norm : "/", follow || trailing, 1);
  } else {
    *slash = 0;
    unsigned h = hash_of(norm);
    if (!cached(norm, h, r->guest)) {
      w = walk(r, norm, 1, 0);
      if (w < 0) return w;
      if (w != GLUE && !S_ISDIR(r->st.st_mode)) return -ENOTDIR;
      if (!r->moving) remember(norm, h, r->guest);
    }
    *slash = '/';
    w = walk(r, slash + 1, follow || trailing, 1);
  }
  if (w < 0) return w;
  if (w == GLUE || (trailing && w != 1 && (w != 0 || !S_ISDIR(r->st.st_mode)))) return FP_SLOW;
  return w;
}

static __attribute__((noinline)) long same_from_other_names(const char *h, const char *path, int follow,
                                                           long rs, const struct res *r) {
  char base[PATH_MAX];
  struct res other;
  other.libc = r->libc;
  for (int k = 1; guest_name(h, k, base) == 0; k++) {
    if (resolve_in(base, path, follow, &other) != rs) return FP_SLOW;
    if (rs >= 0 && strcmp(other.host, r->host) != 0) return FP_SLOW;
  }
  return rs;
}

static long resolve_as(int dirfd, const char *path, int how, int libc, struct res *r) {
  int follow = how & 1;
  long rs;
  char h[PATH_MAX], base[PATH_MAX];
  if (!path || !path[0] || !fp_ready()) return FP_SLOW;
  r->named = 1;
  r->libc = libc;
  if (path[0] == '/') {
    rs = resolve_in(NULL, path, follow, r);
  } else {
    int b = base_of(dirfd, h);
    if (b == -ENOTDIR) return b;
    if (b < 0 || guest_name(h, 0, base) < 0) return FP_SLOW;
    rs = resolve_in(base, path, follow, r);
    if (rs != FP_SLOW && guest_name(h, 1, base) == 0) {
      r->named = 0;
      rs = same_from_other_names(h, path, follow, rs, r);
    }
  }
  return rs == UNSEEN && !(how & MAY_BE_UNSEEN) ? FP_SLOW : rs;
}

static long resolve(int dirfd, const char *path, int how, struct res *r) {
  return resolve_as(dirfd, path, how, 0, r);
}

static int trailing_slash(const char *path) {
  size_t len = path ? strlen(path) : 0;
  return len > 1 && path[len - 1] == '/';
}

static int ret(long r) {
  if (r < 0) { errno = (int)-r; return -1; }
  return (int)r;
}

#define REAL(name, type) \
  static __typeof__(type) real_##name; \
  if (!real_##name) real_##name = (type)dlsym(RTLD_NEXT, #name)

#define ANSWER(r, dirfd, path, real_call) \
  do { long r_ = (r); if (r_ != FP_SLOW) { hits++; return ret(r_); } missed(__func__, dirfd, path); return real_call; } while (0)


static long fp_open(int dirfd, const char *path, int flags, mode_t mode) {
  struct res r;
  if ((flags & __O_TMPFILE) == __O_TMPFILE) return FP_SLOW;
  int follow = !(flags & O_NOFOLLOW) && !((flags & O_CREAT) && (flags & O_EXCL));
  if ((flags & O_CREAT) && trailing_slash(path)) return FP_SLOW;
  long rs = resolve(dirfd, path, follow | MAY_BE_UNSEEN, &r);
  if (rs < 0) return rs;
  if (rs == 1 && !(flags & O_CREAT)) return -ENOENT;
  if (rs == UNSEEN) return sc(SYS_openat, AT_FDCWD, (long)r.host, flags, (flags & O_CREAT) ? mode : 0, 0);
  return sc(SYS_openat, AT_FDCWD, (long)r.host, flags | (follow && !r.magic ? O_NOFOLLOW : 0), (flags & O_CREAT) ? mode : 0, 0);
}

static int do_openat(int dirfd, const char *path, int flags, mode_t mode, int (*real)(int, const char *, int, ...)) {
  long r = fp_open(dirfd, path, flags, mode);
  if (r != FP_SLOW) { hits++; return ret(r); }
  missed(__func__, dirfd, path);
  return real(dirfd, path, flags, mode);
}

int openat(int dirfd, const char *path, int flags, ...) {
  REAL(openat, int (*)(int, const char *, int, ...));
  mode_t mode = 0;
  if (flags & (O_CREAT | __O_TMPFILE)) { va_list ap; va_start(ap, flags); mode = va_arg(ap, mode_t); va_end(ap); }
  return do_openat(dirfd, path, flags, mode, real_openat);
}
int openat64(int dirfd, const char *path, int flags, ...) __attribute__((alias("openat")));

int open(const char *path, int flags, ...) {
  REAL(openat, int (*)(int, const char *, int, ...));
  mode_t mode = 0;
  if (flags & (O_CREAT | __O_TMPFILE)) { va_list ap; va_start(ap, flags); mode = va_arg(ap, mode_t); va_end(ap); }
  return do_openat(AT_FDCWD, path, flags, mode, real_openat);
}
int open64(const char *path, int flags, ...) __attribute__((alias("open")));
int __open_2(const char *path, int flags) { return open(path, flags); }
int __open64_2(const char *path, int flags) { return open(path, flags); }
int __openat_2(int d, const char *path, int flags) { return openat(d, path, flags); }
int __openat64_2(int d, const char *path, int flags) { return openat(d, path, flags); }
int creat(const char *path, mode_t mode) { return open(path, O_WRONLY | O_CREAT | O_TRUNC, mode); }
int creat64(const char *path, mode_t mode) __attribute__((alias("creat")));


static long fp_stat(int dirfd, const char *path, int flags, struct stat *st) {
  struct res r;
  if ((flags & AT_EMPTY_PATH) && path && !path[0] && fp_ready())
    return sc(SYS_newfstatat, dirfd, (long)"", (long)st, flags, 0);
  if (flags & ~(AT_SYMLINK_NOFOLLOW | AT_NO_AUTOMOUNT)) return FP_SLOW;
  long rs = resolve(dirfd, path, (flags & AT_SYMLINK_NOFOLLOW ? 0 : 1) | MAY_BE_UNSEEN, &r);
  if (rs < 0) return rs;
  if (rs == 1) return -ENOENT;
  if (rs == UNSEEN) return sc(SYS_newfstatat, AT_FDCWD, (long)r.host, (long)st, flags, 0);
  *st = r.st;
  return 0;
}

static int stat_common(int dirfd, const char *path, struct stat *st, int flags,
                       int (*real)(int, const char *, struct stat *, int)) {
  ANSWER(fp_stat(dirfd, path, flags, st), dirfd, path, real(dirfd, path, st, flags));
}

int fstatat(int dirfd, const char *path, struct stat *st, int flags) {
  REAL(fstatat, int (*)(int, const char *, struct stat *, int));
  return stat_common(dirfd, path, st, flags, real_fstatat);
}
int fstatat64(int d, const char *p, struct stat64 *st, int f) { return fstatat(d, p, (struct stat *)st, f); }
int stat(const char *path, struct stat *st) {
  REAL(fstatat, int (*)(int, const char *, struct stat *, int));
  return stat_common(AT_FDCWD, path, st, 0, real_fstatat);
}
int stat64(const char *p, struct stat64 *st) { return stat(p, (struct stat *)st); }
int lstat(const char *path, struct stat *st) {
  REAL(fstatat, int (*)(int, const char *, struct stat *, int));
  return stat_common(AT_FDCWD, path, st, AT_SYMLINK_NOFOLLOW, real_fstatat);
}
int lstat64(const char *p, struct stat64 *st) { return lstat(p, (struct stat *)st); }

int fstat(int fd, struct stat *st) {
  REAL(fstat, int (*)(int, struct stat *));
  if (!fp_ready()) return real_fstat(fd, st);
  hits++;
  return ret(sc(SYS_fstat, fd, (long)st, 0, 0, 0));
}
int fstat64(int fd, struct stat64 *st) { return fstat(fd, (struct stat *)st); }

#define FP_STAT_VER 0
static int xstat_common(int ver, int dirfd, const char *path, struct stat *st, int flags) {
  REAL(fstatat, int (*)(int, const char *, struct stat *, int));
  if (ver != FP_STAT_VER) { errno = EINVAL; return -1; }
  return stat_common(dirfd, path, st, flags, real_fstatat);
}
int __xstat(int ver, const char *p, struct stat *st) { return xstat_common(ver, AT_FDCWD, p, st, 0); }
int __xstat64(int ver, const char *p, struct stat64 *st) { return xstat_common(ver, AT_FDCWD, p, (struct stat *)st, 0); }
int __lxstat(int ver, const char *p, struct stat *st) { return xstat_common(ver, AT_FDCWD, p, st, AT_SYMLINK_NOFOLLOW); }
int __lxstat64(int ver, const char *p, struct stat64 *st) { return xstat_common(ver, AT_FDCWD, p, (struct stat *)st, AT_SYMLINK_NOFOLLOW); }
int __fxstatat(int ver, int d, const char *p, struct stat *st, int f) { return xstat_common(ver, d, p, st, f); }
int __fxstatat64(int ver, int d, const char *p, struct stat64 *st, int f) { return xstat_common(ver, d, p, (struct stat *)st, f); }
int __fxstat(int ver, int fd, struct stat *st) {
  if (ver != FP_STAT_VER) { errno = EINVAL; return -1; }
  return fstat(fd, st);
}
int __fxstat64(int ver, int fd, struct stat64 *st) { return __fxstat(ver, fd, (struct stat *)st); }

int statx(int dirfd, const char *path, int flags, unsigned mask, struct statx *out) {
  REAL(statx, int (*)(int, const char *, int, unsigned, struct statx *));
  struct res r;
  long rs;
  if ((flags & AT_EMPTY_PATH) && !path[0] && fp_ready()) {
    hits++;
    return ret(sc(SYS_statx, dirfd, (long)"", flags, mask, (long)out));
  }
  rs = (flags & ~(AT_SYMLINK_NOFOLLOW | AT_NO_AUTOMOUNT | AT_STATX_SYNC_TYPE)) ? FP_SLOW
       : resolve(dirfd, path, (flags & AT_SYMLINK_NOFOLLOW ? 0 : 1) | MAY_BE_UNSEEN, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 || rs == UNSEEN)
    rs = sc(SYS_statx, AT_FDCWD, (long)r.host, flags | (rs == 0 && !r.magic ? AT_SYMLINK_NOFOLLOW : 0), mask, (long)out);
  ANSWER(rs, dirfd, path, real_statx(dirfd, path, flags, mask, out));
}


static int effective_is_real(void) {
  return !getauxval(AT_SECURE) && getuid() == geteuid() && getgid() == getegid();
}

static int access_common(int dirfd, const char *path, int mode, int flags) {
  REAL(faccessat, int (*)(int, const char *, int, int));
  struct res r;
  if ((flags & AT_EACCESS) && effective_is_real()) flags &= ~AT_EACCESS;
  long rs = (flags & ~AT_SYMLINK_NOFOLLOW) == 0
            ? resolve(dirfd, path, (flags & AT_SYMLINK_NOFOLLOW ? 0 : 1) | MAY_BE_UNSEEN, &r) : FP_SLOW;
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 && S_ISLNK(r.st.st_mode)) rs = FP_SLOW;
  if (rs == 0 || rs == UNSEEN) rs = sc(SYS_faccessat, AT_FDCWD, (long)r.host, mode, 0, 0);
  if (rs != FP_SLOW) { hits++; return ret(rs); }
  missed(__func__, dirfd, path);
  if (flags == 0) return (int)syscall(SYS_faccessat, dirfd, path, mode);
  return real_faccessat(dirfd, path, mode, flags);
}
int faccessat(int dirfd, const char *path, int mode, int flags) { return access_common(dirfd, path, mode, flags); }
int access(const char *path, int mode) { return access_common(AT_FDCWD, path, mode, 0); }
int eaccess(const char *path, int mode) { return access_common(AT_FDCWD, path, mode, AT_EACCESS); }
int euidaccess(const char *path, int mode) __attribute__((alias("eaccess")));


static long link_text(const struct res *r, char *text, size_t size, int *changed) {
  int in_proc = under(r->guest, "/proc", 5);
  *changed = 0;
  if (in_proc != under(r->host, "/proc", 5)) return FP_SLOW;
  if (in_proc) return proc_readlink(r, text, size, changed);
  long t = sc(SYS_readlinkat, AT_FDCWD, (long)r->host, (long)text, PATH_MAX - 1, 0);
  if (t < 0) return t;
  if (t >= PATH_MAX - 1) return FP_SLOW;
  if (size && (size_t)t > size) t = size;
  text[t] = 0;
  long d = detranslated(text, r->host);
  if (d == FP_SLOW) return FP_SLOW;
  *changed = (int)d;
  return (long)strlen(text);
}

static ssize_t readlink_common(int dirfd, const char *path, char *buf, size_t size) {
  REAL(readlinkat, ssize_t (*)(int, const char *, char *, size_t));
  struct res r;
  char text[PATH_MAX];
  int changed;
  long rs = size ? resolve(dirfd, path, 0, &r) : FP_SLOW;
  if (rs == 1) rs = -ENOENT;
  if (rs == 0) rs = link_text(&r, text, size, &changed);
  if (rs == FP_SLOW) {
    missed(__func__, dirfd, path);
    return real_readlinkat(dirfd, path, buf, size);
  }
  hits++;
  if (rs < 0) return ret(rs);
  size_t n = rs;
  if (!changed) {
    n = n < size ? n : size;
    memcpy(buf, text, n);
    return n;
  }
  size_t max = size < PATH_MAX ? size : PATH_MAX;
  if (n + 1 < max) {
    memcpy(buf, text, n + 1);
    return n;
  }
  memcpy(buf, text, max);
  return max;
}
ssize_t readlinkat(int dirfd, const char *path, char *buf, size_t size) { return readlink_common(dirfd, path, buf, size); }
ssize_t readlink(const char *path, char *buf, size_t size) { return readlink_common(AT_FDCWD, path, buf, size); }

ssize_t __readlink_chk(const char *path, char *buf, size_t size, size_t room) {
  REAL(__readlink_chk, ssize_t (*)(const char *, char *, size_t, size_t));
  if (size > room) return real___readlink_chk(path, buf, size, room);
  return readlink_common(AT_FDCWD, path, buf, size);
}
ssize_t __readlinkat_chk(int dirfd, const char *path, char *buf, size_t size, size_t room) {
  REAL(__readlinkat_chk, ssize_t (*)(int, const char *, char *, size_t, size_t));
  if (size > room) return real___readlinkat_chk(dirfd, path, buf, size, room);
  return readlink_common(dirfd, path, buf, size);
}

char *realpath(const char *path, char *resolved) {
  REAL(realpath, char *(*)(const char *, char *));
  struct res r;
  long rs = resolve_as(AT_FDCWD, path, 1, 1, &r);
  if (rs == 0 && !r.named) rs = FP_SLOW;
  if (rs == FP_SLOW) { missed(__func__, AT_FDCWD, path); return real_realpath(path, resolved); }
  hits++;
  if (rs == 1) rs = -ENOENT;
  if (rs < 0) { errno = (int)-rs; return NULL; }
  size_t n = strlen(r.guest) + 1;
  if (!resolved) {
    resolved = malloc(n);
    if (!resolved) return NULL;
  }
  memcpy(resolved, r.guest, n);
  return resolved;
}
char *__realpath_chk(const char *path, char *resolved, size_t size) {
  REAL(__realpath_chk, char *(*)(const char *, char *, size_t));
  if (resolved && size < PATH_MAX) return real___realpath_chk(path, resolved, size);
  return realpath(path, resolved);
}
char *canonicalize_file_name(const char *path) { return realpath(path, NULL); }

char *getcwd(char *buf, size_t size) {
  REAL(getcwd, char *(*)(char *, size_t));
  char g[PATH_MAX];
  if (!fp_ready() || own_cwd(g) == FP_SLOW) { missed(__func__, AT_FDCWD, "."); return real_getcwd(buf, size); }
  hits++;
  size_t need = strlen(g) + 1;
  if (buf && size == 0) { errno = EINVAL; return NULL; }
  if (buf && size < need) { errno = ERANGE; return NULL; }
  if (!buf) {
    if (size && size < need) { errno = ERANGE; return NULL; }
    buf = malloc(size ? size : need);
    if (!buf) return NULL;
  }
  memcpy(buf, g, need);
  return buf;
}
char *__getcwd_chk(char *buf, size_t size, size_t room) {
  REAL(__getcwd_chk, char *(*)(char *, size_t, size_t));
  if (size > room) return real___getcwd_chk(buf, size, room);
  return getcwd(buf, size);
}


int mkdirat(int dirfd, const char *path, mode_t mode) {
  REAL(mkdirat, int (*)(int, const char *, mode_t));
  struct res r;
  long rs = resolve(dirfd, path, MAY_BE_UNSEEN, &r);
  if (rs == 0 || rs == 1 || rs == UNSEEN) rs = sc(SYS_mkdirat, AT_FDCWD, (long)r.host, mode, 0, 0);
  ANSWER(rs, dirfd, path, real_mkdirat(dirfd, path, mode));
}
int mkdir(const char *path, mode_t mode) { return mkdirat(AT_FDCWD, path, mode); }

static int ends_in_dots(const char *path) {
  size_t n = strlen(path);
  while (n > 1 && path[n - 1] == '/') n--;
  size_t b = n;
  while (b > 0 && path[b - 1] != '/') b--;
  return (n - b == 1 && path[b] == '.') || (n - b == 2 && path[b] == '.' && path[b + 1] == '.');
}

static long fp_unlink(int dirfd, const char *path, int flags) {
  struct res r;
  char bare[PATH_MAX];
  int slash = trailing_slash(path);
  if ((flags & ~AT_REMOVEDIR) || !path || ends_in_dots(path) || (slash && !(flags & AT_REMOVEDIR))) return FP_SLOW;
  if (slash) {
    size_t n = strlen(path);
    if (n >= sizeof bare) return FP_SLOW;
    while (n > 1 && path[n - 1] == '/') n--;
    memcpy(bare, path, n);
    bare[n] = 0;
    path = bare;
  }
  long rs = resolve(dirfd, path, MAY_BE_UNSEEN, &r);
  if (rs == 1) rs = -ENOENT;
  if (slash && rs == UNSEEN) rs = FP_SLOW;
  if (slash && rs == 0 && !S_ISDIR(r.st.st_mode)) rs = S_ISLNK(r.st.st_mode) ? FP_SLOW : -ENOTDIR;
  if (rs == 0 && (flags & AT_REMOVEDIR) && strcmp(r.guest, "/") == 0) rs = FP_SLOW;
  if (rs == 0 || rs == UNSEEN) {
    int changes_paths = (flags & AT_REMOVEDIR) || rs == UNSEEN || S_ISLNK(r.st.st_mode);
    rs = sc(SYS_unlinkat, AT_FDCWD, (long)r.host, flags, 0, 0);
    if (rs == 0 && changes_paths) forget();
  }
  return rs;
}

static int unlink_common(int dirfd, const char *path, int flags) {
  REAL(unlinkat, int (*)(int, const char *, int));
  ANSWER(fp_unlink(dirfd, path, flags), dirfd, path, real_unlinkat(dirfd, path, flags));
}
int unlinkat(int dirfd, const char *path, int flags) { return unlink_common(dirfd, path, flags); }
int unlink(const char *path) { return unlink_common(AT_FDCWD, path, 0); }
int rmdir(const char *path) { return unlink_common(AT_FDCWD, path, AT_REMOVEDIR); }

static int rename_common(int olddirfd, const char *old, int newdirfd, const char *new, unsigned flags) {
  REAL(renameat2, int (*)(int, const char *, int, const char *, unsigned));
  struct res from, to;
  long rs = flags & ~RENAME_NOREPLACE || trailing_slash(old) || trailing_slash(new) ? FP_SLOW : resolve(olddirfd, old, 0, &from);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 && S_ISDIR(from.st.st_mode)) rs = FP_SLOW;
  if (rs == 0) {
    long rt = resolve(newdirfd, new, 0, &to);
    if (rt < 0) rs = rt;
    else if (rt == 0 && S_ISDIR(to.st.st_mode)) rs = FP_SLOW;
    else {
      rs = flags ? sc(SYS_renameat2, AT_FDCWD, (long)from.host, AT_FDCWD, (long)to.host, flags)
                 : sc(SYS_renameat, AT_FDCWD, (long)from.host, AT_FDCWD, (long)to.host, 0);
      if (rs == 0) forget();
    }
  }
  if (rs != FP_SLOW) { hits++; return ret(rs); }
  missed(__func__, olddirfd, old);
  if (!flags) return (int)syscall(SYS_renameat, olddirfd, old, newdirfd, new);
  return real_renameat2(olddirfd, old, newdirfd, new, flags);
}
int renameat2(int olddirfd, const char *old, int newdirfd, const char *new, unsigned flags) {
  return rename_common(olddirfd, old, newdirfd, new, flags);
}
int renameat(int olddirfd, const char *old, int newdirfd, const char *new) { return rename_common(olddirfd, old, newdirfd, new, 0); }
int rename(const char *old, const char *new) { return rename_common(AT_FDCWD, old, AT_FDCWD, new, 0); }

int symlinkat(const char *target, int dirfd, const char *path) {
  REAL(symlinkat, int (*)(const char *, int, const char *));
  struct res r;
  long rs = trailing_slash(path) ? FP_SLOW : resolve(dirfd, path, MAY_BE_UNSEEN, &r);
  if (rs == 0 || rs == 1 || rs == UNSEEN) {
    rs = sc(SYS_symlinkat, (long)target, AT_FDCWD, (long)r.host, 0, 0);
    if (rs == 0) forget();
  }
  ANSWER(rs, dirfd, path, real_symlinkat(target, dirfd, path));
}
int symlink(const char *target, const char *path) { return symlinkat(target, AT_FDCWD, path); }

int fchmodat(int dirfd, const char *path, mode_t mode, int flags) {
  REAL(fchmodat, int (*)(int, const char *, mode_t, int));
  struct res r;
  long rs = flags ? FP_SLOW : resolve(dirfd, path, 1 | MAY_BE_UNSEEN, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 || rs == UNSEEN) rs = sc(SYS_fchmodat, AT_FDCWD, (long)r.host, mode, 0, 0);
  ANSWER(rs, dirfd, path, real_fchmodat(dirfd, path, mode, flags));
}
int chmod(const char *path, mode_t mode) { return fchmodat(AT_FDCWD, path, mode, 0); }

static int utimens_common(int dirfd, const char *path, const struct timespec times[2], int flags) {
  REAL(utimensat, int (*)(int, const char *, const struct timespec[2], int));
  struct res r;
  long rs;
  if (!path && fp_ready()) {
    rs = sc(SYS_utimensat, dirfd, 0, (long)times, flags, 0);
  } else {
    rs = flags & ~AT_SYMLINK_NOFOLLOW ? FP_SLOW : resolve(dirfd, path, (flags & AT_SYMLINK_NOFOLLOW ? 0 : 1) | MAY_BE_UNSEEN, &r);
    if (rs == 1) rs = -ENOENT;
    if (rs == 0 || rs == UNSEEN) rs = sc(SYS_utimensat, AT_FDCWD, (long)r.host, (long)times, flags, 0);
  }
  ANSWER(rs, dirfd, path, real_utimensat(dirfd, path, times, flags));
}
int utimensat(int dirfd, const char *path, const struct timespec times[2], int flags) {
  return utimens_common(dirfd, path, times, flags);
}
int futimens(int fd, const struct timespec times[2]) { return utimens_common(fd, NULL, times, 0); }

static int utimes_common(int dirfd, const char *path, const struct timeval tv[2], int flags) {
  struct timespec ts[2];
  if (tv) {
    for (int i = 0; i < 2; i++) {
      if (tv[i].tv_usec < 0 || tv[i].tv_usec >= 1000000) { errno = EINVAL; return -1; }
      ts[i].tv_sec = tv[i].tv_sec;
      ts[i].tv_nsec = tv[i].tv_usec * 1000;
    }
  }
  return utimens_common(dirfd, path, tv ? ts : NULL, flags);
}
int utimes(const char *path, const struct timeval tv[2]) { return utimes_common(AT_FDCWD, path, tv, 0); }
int lutimes(const char *path, const struct timeval tv[2]) { return utimes_common(AT_FDCWD, path, tv, AT_SYMLINK_NOFOLLOW); }
int futimes(int fd, const struct timeval tv[2]) { return utimes_common(fd, NULL, tv, 0); }
int utime(const char *path, const struct utimbuf *times) {
  if (!times) return utimens_common(AT_FDCWD, path, NULL, 0);
  struct timespec ts[2] = { { times->actime, 0 }, { times->modtime, 0 } };
  return utimens_common(AT_FDCWD, path, ts, 0);
}

int truncate(const char *path, off_t length) {
  REAL(truncate, int (*)(const char *, off_t));
  struct res r;
  long rs = resolve(AT_FDCWD, path, 1 | MAY_BE_UNSEEN, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 || rs == UNSEEN) rs = sc(SYS_truncate, (long)r.host, length, 0, 0, 0);
  ANSWER(rs, AT_FDCWD, path, real_truncate(path, length));
}
int truncate64(const char *path, off_t length) __attribute__((alias("truncate")));

static int statfs_common(const char *path, struct statfs *buf, int (*real)(const char *, struct statfs *)) {
  struct res r;
  long rs = resolve(AT_FDCWD, path, 1, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 && under(r.guest, "/dev/shm", 8)) rs = FP_SLOW;
  if (rs == 0) rs = sc(SYS_statfs, (long)r.host, (long)buf, 0, 0, 0);
  ANSWER(rs, AT_FDCWD, path, real(path, buf));
}
int statfs(const char *path, struct statfs *buf) {
  REAL(statfs, int (*)(const char *, struct statfs *));
  return statfs_common(path, buf, real_statfs);
}
int statfs64(const char *path, struct statfs64 *buf) {
  REAL(statfs64, int (*)(const char *, struct statfs *));
  return statfs_common(path, (struct statfs *)buf, real_statfs64);
}

#define FP_ST_VALID 0x0020


static int statvfs_common(const char *path, struct statvfs *buf, int (*real)(const char *, struct statvfs *)) {
  struct res r;
  struct statfs fs;
  long rs = resolve(AT_FDCWD, path, 1, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 && under(r.guest, "/dev/shm", 8)) rs = FP_SLOW;
  if (rs == 0) rs = sc(SYS_statfs, (long)r.host, (long)&fs, 0, 0, 0);
  if (rs == 0 && !(fs.f_flags & FP_ST_VALID)) rs = FP_SLOW;
  if (rs == 0) {
    memset(buf, 0, sizeof *buf);
    buf->f_bsize = fs.f_bsize;
    buf->f_frsize = fs.f_frsize ? fs.f_frsize : fs.f_bsize;
    buf->f_blocks = fs.f_blocks;
    buf->f_bfree = fs.f_bfree;
    buf->f_bavail = fs.f_bavail;
    buf->f_files = fs.f_files;
    buf->f_ffree = fs.f_ffree;
    buf->f_favail = fs.f_ffree;
    _Static_assert(sizeof buf->f_fsid == sizeof fs.f_fsid, "fsid layout");
    memcpy(&buf->f_fsid, &fs.f_fsid, sizeof fs.f_fsid);
    buf->f_flag = fs.f_flags ^ FP_ST_VALID;
    buf->f_namemax = fs.f_namelen;
  }
  ANSWER(rs, AT_FDCWD, path, real(path, buf));
}
int statvfs(const char *path, struct statvfs *buf) {
  REAL(statvfs, int (*)(const char *, struct statvfs *));
  return statvfs_common(path, buf, real_statvfs);
}
int statvfs64(const char *path, struct statvfs64 *buf) {
  REAL(statvfs64, int (*)(const char *, struct statvfs *));
  return statvfs_common(path, (struct statvfs *)buf, real_statvfs64);
}

static ssize_t getxattr_common(const char *path, const char *name, void *value, size_t size, int follow,
                               ssize_t (*real)(const char *, const char *, void *, size_t)) {
  struct res r;
  long rs = resolve(AT_FDCWD, path, follow | MAY_BE_UNSEEN, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 || rs == UNSEEN) rs = sc(follow ? SYS_getxattr : SYS_lgetxattr, (long)r.host, (long)name, (long)value, size, 0);
  if (rs != FP_SLOW) { hits++; return rs < 0 ? ret(rs) : rs; }
  missed(__func__, AT_FDCWD, path);
  return real(path, name, value, size);
}
ssize_t getxattr(const char *path, const char *name, void *value, size_t size) {
  REAL(getxattr, ssize_t (*)(const char *, const char *, void *, size_t));
  return getxattr_common(path, name, value, size, 1, real_getxattr);
}
ssize_t lgetxattr(const char *path, const char *name, void *value, size_t size) {
  REAL(lgetxattr, ssize_t (*)(const char *, const char *, void *, size_t));
  return getxattr_common(path, name, value, size, 0, real_lgetxattr);
}

static ssize_t listxattr_common(const char *path, char *list, size_t size, int follow,
                                ssize_t (*real)(const char *, char *, size_t)) {
  struct res r;
  long rs = resolve(AT_FDCWD, path, follow | MAY_BE_UNSEEN, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 || rs == UNSEEN) rs = sc(follow ? SYS_listxattr : SYS_llistxattr, (long)r.host, (long)list, size, 0, 0);
  if (rs != FP_SLOW) { hits++; return rs < 0 ? ret(rs) : rs; }
  missed(__func__, AT_FDCWD, path);
  return real(path, list, size);
}
ssize_t listxattr(const char *path, char *list, size_t size) {
  REAL(listxattr, ssize_t (*)(const char *, char *, size_t));
  return listxattr_common(path, list, size, 1, real_listxattr);
}
ssize_t llistxattr(const char *path, char *list, size_t size) {
  REAL(llistxattr, ssize_t (*)(const char *, char *, size_t));
  return listxattr_common(path, list, size, 0, real_llistxattr);
}

int inotify_add_watch(int fd, const char *path, uint32_t mask) {
  REAL(inotify_add_watch, int (*)(int, const char *, uint32_t));
  struct res r;
  long rs = resolve(AT_FDCWD, path, (mask & IN_DONT_FOLLOW ? 0 : 1) | MAY_BE_UNSEEN, &r);
  if (rs == 1) rs = -ENOENT;
  if (rs == 0 || rs == UNSEEN) rs = sc(SYS_inotify_add_watch, fd, (long)r.host, mask, 0, 0);
  ANSWER(rs, AT_FDCWD, path, real_inotify_add_watch(fd, path, mask));
}


/* The kernel reports a socket named through /proc/self/fd/N/<name> under that name: map it back, per socket. */
enum { LONG_SELF, LONG_PEER };
static struct { unsigned long ino; int kind; char name[sizeof ((struct sockaddr_un *)0)->sun_path]; char guest[sizeof ((struct sockaddr_un *)0)->sun_path]; } long_names[64];
static unsigned long_next;
static pthread_mutex_t long_lock = PTHREAD_MUTEX_INITIALIZER;

static const char *fd_name(const char *path) {
  if (strncmp(path, "/proc/self/fd/", 14) != 0) return NULL;
  const char *p = path + 14;
  if (*p < '0' || *p > '9') return NULL;
  while (*p >= '0' && *p <= '9') p++;
  return *p == '/' && p[1] ? p + 1 : NULL;
}

static unsigned long socket_ino(int fd) {
  struct stat st;
  return sc(SYS_newfstatat, fd, (long)"", (long)&st, AT_EMPTY_PATH, 0) == 0 ? (unsigned long)st.st_ino : 0;
}

static void remember_long(int fd, int kind, const char *name, const char *guest) {
  unsigned long ino = socket_ino(fd);
  if (!ino || !name || strlen(name) >= sizeof long_names[0].name || strlen(guest) >= sizeof long_names[0].guest) return;
  unsigned count = sizeof long_names / sizeof long_names[0], i = count;
  pthread_mutex_lock(&long_lock);
  for (unsigned n = 0; n < count && i == count; n++)
    if (long_names[n].ino == ino && long_names[n].kind == kind) i = n;
  if (i == count) i = long_next++ % count;
  long_names[i].ino = ino;
  long_names[i].kind = kind;
  snprintf(long_names[i].name, sizeof long_names[i].name, "%s", name);
  snprintf(long_names[i].guest, sizeof long_names[i].guest, "%s", guest);
  pthread_mutex_unlock(&long_lock);
}

static int long_entry(int fd, int kind, const char *name, char *guest, size_t size) {
  unsigned long ino = socket_ino(fd);
  int found = -1;
  if (!ino || !name) return -1;
  pthread_mutex_lock(&long_lock);
  for (unsigned n = 0; n < sizeof long_names / sizeof long_names[0] && found < 0; n++)
    if (long_names[n].ino == ino && long_names[n].kind == kind && strcmp(long_names[n].name, name) == 0
        && strlen(long_names[n].guest) < size) {
      memcpy(guest, long_names[n].guest, strlen(long_names[n].guest) + 1);
      found = 0;
    }
  pthread_mutex_unlock(&long_lock);
  return found;
}

static void remember_long_address(int fd, int kind, const struct sockaddr *addr, socklen_t len, const struct sockaddr_un *used) {
  const struct sockaddr_un *un = (const struct sockaddr_un *)addr;
  char guest[sizeof un->sun_path + 1];
  size_t glen = strnlen(un->sun_path, len - offsetof(struct sockaddr_un, sun_path));
  memcpy(guest, un->sun_path, glen);
  guest[glen] = 0;
  remember_long(fd, kind, fd_name(used->sun_path), guest);
}

static long socket_address(const struct sockaddr *addr, socklen_t len, int follow, struct sockaddr_un *out, socklen_t *out_len, long *dir) {
  const struct sockaddr_un *un = (const struct sockaddr_un *)addr;
  *dir = -1;
  size_t room = sizeof un->sun_path;
  if (!addr || len <= offsetof(struct sockaddr_un, sun_path) || addr->sa_family != AF_UNIX || un->sun_path[0] == 0)
    return 0;
  size_t plen = strnlen(un->sun_path, len - offsetof(struct sockaddr_un, sun_path));
  char path[sizeof un->sun_path + 1];
  if (plen > room) return FP_SLOW;
  memcpy(path, un->sun_path, plen);
  path[plen] = 0;
  if (trailing_slash(path)) return FP_SLOW;
  struct res r;
  long rs = resolve(AT_FDCWD, path, follow, &r);
  if (rs < 0) return rs;
  if (rs == 1 && follow) return -ENOENT;
  if (r.magic) return FP_SLOW;
  size_t hlen = strlen(r.host);
  memset(out, 0, sizeof *out);
  out->sun_family = AF_UNIX;
  if (hlen >= room) {
    /* proot would bind it to a temporary name the fast path cannot see: name it through its directory. */
    char *slash = strrchr(r.host, '/');
    if (!slash || slash == r.host) return FP_SLOW;
    *slash = 0;
    *dir = sc(SYS_openat, AT_FDCWD, (long)r.host, O_PATH | O_DIRECTORY | O_CLOEXEC, 0, 0);
    if (*dir < 0) return FP_SLOW;
    hlen = snprintf(out->sun_path, room, "/proc/self/fd/%ld/%s", *dir, slash + 1);
    if (hlen >= room) { sc(SYS_close, *dir, 0, 0, 0, 0); *dir = -1; return FP_SLOW; }
  } else {
    memcpy(out->sun_path, r.host, hlen + 1);
  }
  *out_len = offsetof(struct sockaddr_un, sun_path) + hlen + 1;
  return 1;
}

static int nonblocking(int fd) {
  long fl = sc(SYS_fcntl, fd, F_GETFL, 0, 0, 0);
  return fl >= 0 && (fl & O_NONBLOCK);
}

int connect(int fd, const struct sockaddr *addr, socklen_t len) {
  REAL(connect, int (*)(int, const struct sockaddr *, socklen_t));
  struct sockaddr_un un;
  socklen_t un_len;
  long dir = -1;
  long rs = fp_ready() ? socket_address(addr, len, 1, &un, &un_len, &dir) : FP_SLOW;
  if (rs == 0 && !(addr && addr->sa_family == AF_UNIX) && !nonblocking(fd)) rs = FP_SLOW;
  if (rs == 0) rs = sc(SYS_connect, fd, (long)addr, len, 0, 0);
  else if (rs == 1) rs = sc(SYS_connect, fd, (long)&un, un_len, 0, 0);
  if (dir >= 0) {
    sc(SYS_close, dir, 0, 0, 0, 0);
    if (rs == 0 || rs == -EINPROGRESS) remember_long_address(fd, LONG_PEER, addr, len, &un);
  }
  ANSWER(rs, AT_FDCWD, "(connect)", real_connect(fd, addr, len));
}

int bind(int fd, const struct sockaddr *addr, socklen_t len) {
  REAL(bind, int (*)(int, const struct sockaddr *, socklen_t));
  struct sockaddr_un un;
  socklen_t un_len;
  long dir = -1;
  long rs = fp_ready() ? socket_address(addr, len, 0, &un, &un_len, &dir) : FP_SLOW;
  if (rs == 0) rs = sc(SYS_bind, fd, (long)addr, len, 0, 0);
  else if (rs == 1) rs = sc(SYS_bind, fd, (long)&un, un_len, 0, 0);
  if (dir >= 0) {
    sc(SYS_close, dir, 0, 0, 0, 0);
    if (rs == 0) remember_long_address(fd, LONG_SELF, addr, len, &un);
  }
  ANSWER(rs, AT_FDCWD, "(bind)", real_bind(fd, addr, len));
}

static void report_address(int fd, int kind, const struct sockaddr_storage *got, socklen_t got_len, struct sockaddr *addr, socklen_t *len) {
  struct sockaddr_storage shown = *got;
  socklen_t shown_len = got_len;
  const struct sockaddr_un *un = (const struct sockaddr_un *)got;
  if (got_len > offsetof(struct sockaddr_un, sun_path) && un->sun_family == AF_UNIX && un->sun_path[0]) {
    char g[PATH_MAX], h[sizeof un->sun_path + 1];
    size_t hl = strnlen(un->sun_path, got_len - offsetof(struct sockaddr_un, sun_path));
    memcpy(h, un->sun_path, hl);
    h[hl] = 0;
    struct sockaddr_un *out = (struct sockaddr_un *)&shown;
    if ((long_entry(fd, kind, fd_name(h), g, sizeof g) == 0 || to_guest(h, g, sizeof g) == 0) && strlen(g) < sizeof out->sun_path) {
      memset(out->sun_path, 0, sizeof out->sun_path);
      strcpy(out->sun_path, g);
      shown_len = offsetof(struct sockaddr_un, sun_path) + strlen(g) + 1;
    }
  }
  memcpy(addr, &shown, *len < shown_len ? *len : shown_len);
  *len = shown_len;
}


static void inherit_long_name(int listener, int accepted) {
  unsigned long ino = socket_ino(listener);
  char name[sizeof long_names[0].name], guest[sizeof long_names[0].guest];
  int found = -1;
  if (!ino) return;
  pthread_mutex_lock(&long_lock);
  for (unsigned n = 0; n < sizeof long_names / sizeof long_names[0] && found < 0; n++)
    if (long_names[n].ino == ino && long_names[n].kind == LONG_SELF) {
      snprintf(name, sizeof name, "%s", long_names[n].name);
      snprintf(guest, sizeof guest, "%s", long_names[n].guest);
      found = 0;
    }
  pthread_mutex_unlock(&long_lock);
  if (found == 0) remember_long(accepted, LONG_SELF, name, guest);
}

static int pending(int fd) {
  struct pollfd p = { .fd = fd, .events = POLLIN };
  struct timespec now = { 0, 0 };
  return sc(SYS_ppoll, (long)&p, 1, (long)&now, 0, 0) == 1 && (p.revents & POLLIN);
}

static int accept_common(int fd, struct sockaddr *addr, socklen_t *len, int flags) {
  REAL(accept4, int (*)(int, struct sockaddr *, socklen_t *, int));
  if (!fp_ready() || (addr && !len) || (!nonblocking(fd) && !pending(fd))) { missed(__func__, AT_FDCWD, "(accept)"); return real_accept4(fd, addr, len, flags); }
  hits++;
  struct sockaddr_storage got;
  socklen_t got_len = sizeof got;
  long r = sc(SYS_accept4, fd, addr ? (long)&got : 0, addr ? (long)&got_len : 0, flags, 0);
  if (r >= 0) inherit_long_name(fd, (int)r);
  if (!addr) return ret(r);
  if (r >= 0) report_address((int)r, LONG_PEER, &got, got_len, addr, len);
  return ret(r);
}
int accept4(int fd, struct sockaddr *addr, socklen_t *len, int flags) { return accept_common(fd, addr, len, flags); }
int accept(int fd, struct sockaddr *addr, socklen_t *len) { return accept_common(fd, addr, len, 0); }

static int name_common(long nr, int fd, struct sockaddr *addr, socklen_t *len, int (*real)(int, struct sockaddr *, socklen_t *)) {
  if (!fp_ready() || !addr || !len) { missed(__func__, AT_FDCWD, "(name)"); return real(fd, addr, len); }
  hits++;
  struct sockaddr_storage got;
  socklen_t got_len = sizeof got;
  long r = sc(nr, fd, (long)&got, (long)&got_len, 0, 0);
  if (r == 0) report_address(fd, nr == SYS_getsockname ? LONG_SELF : LONG_PEER, &got, got_len, addr, len);
  return ret(r);
}
int getsockname(int fd, struct sockaddr *addr, socklen_t *len) {
  REAL(getsockname, int (*)(int, struct sockaddr *, socklen_t *));
  return name_common(SYS_getsockname, fd, addr, len, real_getsockname);
}
int getpeername(int fd, struct sockaddr *addr, socklen_t *len) {
  REAL(getpeername, int (*)(int, struct sockaddr *, socklen_t *));
  return name_common(SYS_getpeername, fd, addr, len, real_getpeername);
}


static int shm_path(const char *name, char *path) {
  while (*name == '/') name++;
  size_t n = strlen(name);
  if (n == 0 || n > NAME_MAX || strchr(name, '/')) return -1;
  memcpy(path, "/dev/shm/", 9);
  memcpy(path + 9, name, n + 1);
  return 0;
}

int shm_open(const char *name, int flags, mode_t mode) {
  REAL(shm_open, int (*)(const char *, int, mode_t));
  char path[sizeof "/dev/shm/" + NAME_MAX];
  long rs = shm_path(name, path) == 0 ? fp_open(AT_FDCWD, path, flags | O_NOFOLLOW | O_CLOEXEC, mode) : FP_SLOW;
  if (rs == -EISDIR) rs = -EINVAL;
  ANSWER(rs, AT_FDCWD, name, real_shm_open(name, flags, mode));
}

int shm_unlink(const char *name) {
  REAL(shm_unlink, int (*)(const char *));
  char path[sizeof "/dev/shm/" + NAME_MAX];
  long rs = shm_path(name, path) == 0 ? fp_unlink(AT_FDCWD, path, 0) : FP_SLOW;
  if (rs == -EPERM) rs = -EACCES;
  ANSWER(rs, AT_FDCWD, name, real_shm_unlink(name));
}

DIR *opendir(const char *path) {
  REAL(opendir, DIR *(*)(const char *));
  long fd = fp_open(AT_FDCWD, path, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NONBLOCK, 0);
  if (fd == FP_SLOW) { missed(__func__, AT_FDCWD, path); return real_opendir(path); }
  hits++;
  if (fd < 0) { errno = (int)-fd; return NULL; }
  DIR *d = fdopendir((int)fd);
  if (!d) close((int)fd);
  return d;
}

static FILE *fopen_common(const char *path, const char *m, FILE *(*real)(const char *, const char *)) {
  int flags;
  switch (m[0]) {
  case 'r': flags = strchr(m, '+') ? O_RDWR : O_RDONLY; break;
  case 'w': flags = (strchr(m, '+') ? O_RDWR : O_WRONLY) | O_CREAT | O_TRUNC; break;
  case 'a': flags = (strchr(m, '+') ? O_RDWR : O_WRONLY) | O_CREAT | O_APPEND; break;
  default: return real(path, m);
  }
  if (strchr(m, 'e')) flags |= O_CLOEXEC;
  if (strchr(m, 'x')) flags |= O_EXCL;
  long fd = fp_open(AT_FDCWD, path, flags, 0666);
  if (fd == FP_SLOW) { missed(__func__, AT_FDCWD, path); return real(path, m); }
  hits++;
  if (fd < 0) { errno = (int)-fd; return NULL; }
  FILE *f = fdopen((int)fd, m);
  if (!f) close((int)fd);
  return f;
}
FILE *fopen(const char *path, const char *m) { REAL(fopen, FILE *(*)(const char *, const char *)); return fopen_common(path, m, real_fopen); }
FILE *fopen64(const char *path, const char *m) { REAL(fopen64, FILE *(*)(const char *, const char *)); return fopen_common(path, m, real_fopen64); }

static int online_cpus(void) {
  long fd = fp_open(AT_FDCWD, "/sys/devices/system/cpu/online", O_RDONLY | O_CLOEXEC, 0);
  if (fd < 0) return -1;
  char buf[256];
  long n = sc(SYS_read, fd, (long)buf, sizeof buf - 1, 0, 0);
  sc(SYS_close, fd, 0, 0, 0, 0);
  if (n <= 0) return -1;
  buf[n] = 0;
  int count = 0;
  for (char *p = buf; *p && *p != '\n';) {
    char *end;
    long lo = strtol(p, &end, 10), hi = lo;
    if (end == p) return -1;
    if (*end == '-') { p = end + 1; hi = strtol(p, &end, 10); if (end == p || hi < lo) return -1; }
    count += (int)(hi - lo + 1);
    p = *end == ',' ? end + 1 : end;
  }
  return count > 0 ? count : -1;
}
int get_nprocs(void) {
  REAL(get_nprocs, int (*)(void));
  int n = fp_ready() ? online_cpus() : -1;
  if (n > 0) { hits++; return n; }
  missed(__func__, AT_FDCWD, "(nprocs)");
  return real_get_nprocs();
}
long sysconf(int name) {
  REAL(sysconf, long (*)(int));
  if (name == _SC_NPROCESSORS_ONLN) return get_nprocs();
  return real_sysconf(name);
}


static struct utsname uts;
static int uts_known;
static long own_uname(struct utsname *u) {
  if (!fp_ready()) return FP_SLOW;
  if (!__atomic_load_n(&uts_known, __ATOMIC_ACQUIRE)) {
    struct utsname got;
    if (syscall(SYS_uname, &got) != 0) return FP_SLOW;
    static pthread_mutex_t uts_lock = PTHREAD_MUTEX_INITIALIZER;
    if (pthread_mutex_trylock(&uts_lock) == 0) {
      if (!uts_known) {
        uts = got;
        __atomic_store_n(&uts_known, 1, __ATOMIC_RELEASE);
      }
      pthread_mutex_unlock(&uts_lock);
    }
    *u = got;
    return 0;
  }
  *u = uts;
  return 0;
}

int uname(struct utsname *u) {
  REAL(uname, int (*)(struct utsname *));
  if (!u) return real_uname(u);
  ANSWER(own_uname(u), AT_FDCWD, "(uname)", real_uname(u));
}

int gethostname(char *name, size_t len) {
  REAL(gethostname, int (*)(char *, size_t));
  struct utsname u;
  if (own_uname(&u) == FP_SLOW) { missed(__func__, AT_FDCWD, "(gethostname)"); return real_gethostname(name, len); }
  hits++;
  size_t n = strlen(u.nodename) + 1;
  memcpy(name, u.nodename, len < n ? len : n);
  if (n > len) { errno = ENAMETOOLONG; return -1; }
  return 0;
}

int isatty(int fd) {
  REAL(isatty, int (*)(int));
  struct termios k;
  if (!fp_ready()) return real_isatty(fd);
  hits++;
  long r = sc(SYS_ioctl, fd, TCGETS, (long)&k, 0, 0);
  if (r < 0) { errno = (int)-r; return 0; }
  return r == 0;
}

int memfd_create(const char *name, unsigned flags) {
  REAL(memfd_create, int (*)(const char *, unsigned));
  if (!name || !strncmp(name, "JITCode:", 8) || !strcmp(name, "opcache_lock") || !strncmp(name, "lib/apk/exec/", 13)
      || !fp_ready()) return real_memfd_create(name, flags);
  long r = sc(SYS_memfd_create, (long)name, flags, 0, 0, 0);
  if (r == -ENOSYS) return real_memfd_create(name, flags);
  hits++;
  return ret(r);
}


static long read_small(const char *path, char *buf, size_t size) {
  long fd = fp_open(AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0);
  if (fd < 0) return -1;
  long have = 0, r = 0;
  while ((size_t)have < size && (r = sc(SYS_read, fd, (long)buf + have, size - have, 0, 0)) > 0) have += r;
  sc(SYS_close, fd, 0, 0, 0, 0);
  return (size_t)have < size && r == 0 ? have : -1;
}

static int passwd_is_files(char *conf, size_t size) {
  long n = read_small("/etc/nsswitch.conf", conf, size - 1);
  if (n < 0) return 0;
  conf[n] = 0;
  int found = 0;
  for (char *line = conf; line;) {
    char *end = strchr(line, '\n');
    if (end) *end = 0;
    if (strncmp(line, "passwd", 6) == 0 && strchr(" \t:", line[6])) {
      char *p = line + 6;
      while (*p == ' ' || *p == '\t') p++;
      if (*p++ != ':' || found++ || strchr(p, '#')) return 0;
      while (*p == ' ' || *p == '\t') p++;
      if (strncmp(p, "files", 5) != 0 || (p[5] && p[5] != ' ' && p[5] != '\t')) return 0;
      for (p += 5; *p == ' ' || *p == '\t'; p++) {}
      if (*p == '[') return 0;
    } else if (strstr(line, "passwd") && line[0] != '#') {
      return 0;
    }
    line = end ? end + 1 : NULL;
  }
  return found;
}

static int passwd_line(char *file, const char *name, uid_t uid, char **f, size_t *longest) {
  *longest = 0;
  for (char *line = file; line && *line;) {
    char *end = strchr(line, '\n');
    if (end) *end = 0;
    size_t len = strlen(line);
    if (len > *longest) *longest = len;
    if (line[0] && line[0] != '#') {
      int k = 0;
      f[k++] = line;
      for (char *p = line; *p && k < 8; p++)
        if (*p == ':') { *p = 0; if (k < 7) f[k] = p + 1; k++; }
      if (k != 7 || !f[0][0] || strchr("+- \t", f[0][0])) return 0;
      for (int i = 2; i < 4; i++) {
        size_t d = strspn(f[i], "0123456789");
        if (d == 0 || d > 10 || f[i][d] || strtoul(f[i], NULL, 10) > 0xffffffffUL) return 0;
      }
      if (name ? strcmp(f[0], name) == 0 : (uid_t)strtoul(f[2], NULL, 10) == uid) return 1;
    }
    line = end ? end + 1 : NULL;
  }
  return -1;
}

static int own_passwd(const char *name, uid_t uid, struct passwd *pw, char *buf, size_t buflen) {
  char file[16384], *f[7];
  size_t longest;
  if (!fp_ready() || !passwd_is_files(file, sizeof file)) return 0;
  long n = read_small("/etc/passwd", file, sizeof file - 1);
  if (n < 0) return 0;
  file[n] = 0;
  if (passwd_line(file, name, uid, f, &longest) != 1 || longest + 3 > buflen) return 0;
  size_t off = 0;
  char *s[7];
  for (int i = 0; i < 7; i++) {
    size_t len = strlen(f[i]) + 1;
    s[i] = buf + off;
    memcpy(s[i], f[i], len);
    off += len;
  }
  pw->pw_name = s[0];
  pw->pw_passwd = s[1];
  pw->pw_uid = (uid_t)strtoul(s[2], NULL, 10);
  pw->pw_gid = (gid_t)strtoul(s[3], NULL, 10);
  pw->pw_gecos = s[4];
  pw->pw_dir = s[5];
  pw->pw_shell = s[6];
  return 1;
}

static int passwd_r(const char *name, uid_t uid, struct passwd *pw, char *buf, size_t buflen, struct passwd **result,
                    int (*real_name)(const char *, struct passwd *, char *, size_t, struct passwd **),
                    int (*real_uid)(uid_t, struct passwd *, char *, size_t, struct passwd **)) {
  if (!own_passwd(name, uid, pw, buf, buflen)) {
    missed(name ? "getpwnam" : "getpwuid", AT_FDCWD, "(passwd)");
    return name ? real_name(name, pw, buf, buflen, result) : real_uid(uid, pw, buf, buflen, result);
  }
  hits++;
  *result = pw;
  errno = 0;
  return 0;
}

int getpwnam_r(const char *name, struct passwd *pw, char *buf, size_t buflen, struct passwd **result) {
  REAL(getpwnam_r, int (*)(const char *, struct passwd *, char *, size_t, struct passwd **));
  return passwd_r(name, 0, pw, buf, buflen, result, real_getpwnam_r, NULL);
}

int getpwuid_r(uid_t uid, struct passwd *pw, char *buf, size_t buflen, struct passwd **result) {
  REAL(getpwuid_r, int (*)(uid_t, struct passwd *, char *, size_t, struct passwd **));
  return passwd_r(NULL, uid, pw, buf, buflen, result, NULL, real_getpwuid_r);
}

static struct passwd static_pw;
static char static_pw_buf[4096];
static pthread_mutex_t static_pw_lock = PTHREAD_MUTEX_INITIALIZER;

static struct passwd *passwd_static(const char *name, uid_t uid) {
  pthread_mutex_lock(&static_pw_lock);
  int own = own_passwd(name, uid, &static_pw, static_pw_buf, sizeof static_pw_buf);
  pthread_mutex_unlock(&static_pw_lock);
  if (!own) return NULL;
  hits++;
  errno = 0;
  return &static_pw;
}

struct passwd *getpwnam(const char *name) {
  REAL(getpwnam, struct passwd *(*)(const char *));
  struct passwd *pw = passwd_static(name, 0);
  if (pw) return pw;
  missed(__func__, AT_FDCWD, "(passwd)");
  return real_getpwnam(name);
}

struct passwd *getpwuid(uid_t uid) {
  REAL(getpwuid, struct passwd *(*)(uid_t));
  struct passwd *pw = passwd_static(NULL, uid);
  if (pw) return pw;
  missed(__func__, AT_FDCWD, "(passwd)");
  return real_getpwuid(uid);
}
