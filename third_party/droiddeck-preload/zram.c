#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>


#ifndef MADV_PAGEOUT
#define MADV_PAGEOUT 21
#endif
#ifndef ZR_CTL
#define ZR_CTL "/tmp/.bl-zram"
#endif
#define ZR_SWAPPED (1ULL << 62)
#define ZR_BATCH 512

static long (*zr_sys)(long, ...);
static unsigned long zr_page;
static int zr_busy;
static int zr_pending;
static char zr_maps[8192];
static uint64_t zr_entries[ZR_BATCH];
static struct iovec zr_iov[ZR_BATCH];
static char zr_sink[ZR_BATCH];

static int zr_open(const char *path) {
  return (int)zr_sys(SYS_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0);
}

static int zr_mode(void) {
  char c = 0;
  int fd = zr_open(ZR_CTL);
  if (fd < 0) return -1;
  long n = zr_sys(SYS_read, fd, &c, 1);
  zr_sys(SYS_close, fd);
  return n == 1 && (c == '0' || c == '1') ? c - '0' : -1;
}

static unsigned long zr_hex(const char **p) {
  unsigned long v = 0;
  for (;; (*p)++) {
    char c = **p;
    if (c >= '0' && c <= '9') v = v * 16 + (unsigned long)(c - '0');
    else if (c >= 'a' && c <= 'f') v = v * 16 + (unsigned long)(c - 'a' + 10);
    else return v;
  }
}

static int zr_eligible(const char *line, int compress, unsigned long *start, unsigned long *end) {
  const char *p = line;
  *start = zr_hex(&p);
  if (*p++ != '-') return 0;
  *end = zr_hex(&p);
  if (*p++ != ' ' || p[0] != 'r' || p[3] != 'p') return 0;
  if (!compress) return 1;
  if (p[1] != 'w') return 0;
  for (int field = 0; field < 4; field++) {
    while (*p && *p != ' ') p++;
    while (*p == ' ') p++;
  }
  return *p == 0 || strncmp(p, "[heap]", 6) == 0 || strncmp(p, "[anon:", 6) == 0;
}

static void zr_read_back(int pagemap, unsigned long start, unsigned long end) {
  pid_t self = (pid_t)zr_sys(SYS_getpid);
  struct iovec local = {zr_sink, 0};
  for (unsigned long addr = start; addr < end;) {
    unsigned long count = (end - addr) / zr_page;
    if (count > ZR_BATCH) count = ZR_BATCH;
    if (zr_sys(SYS_lseek, pagemap, (long)(addr / zr_page * sizeof(uint64_t)), SEEK_SET) < 0) return;
    long got = zr_sys(SYS_read, pagemap, zr_entries, count * sizeof(uint64_t));
    if (got < (long)sizeof(uint64_t)) return;
    unsigned long n = 0;
    for (unsigned long i = 0; i < (unsigned long)got / sizeof(uint64_t); i++) {
      if (!(zr_entries[i] & ZR_SWAPPED)) continue;
      zr_iov[n].iov_base = (void *)(addr + i * zr_page);
      zr_iov[n++].iov_len = 1;
    }
    for (unsigned long done = 0; done < n;) {
      local.iov_len = n - done;
      long r = zr_sys(SYS_process_vm_readv, self, &local, 1UL, zr_iov + done, n - done, 0UL);
      done += r > 0 ? (unsigned long)r : 1;
    }
    addr += (unsigned long)got / sizeof(uint64_t) * zr_page;
  }
}

static void zr_walk(int compress) {
  int maps = zr_open("/proc/self/maps");
  if (maps < 0) return;
  int pagemap = compress ? -1 : zr_open("/proc/self/pagemap");
  if (!compress && pagemap < 0) {
    zr_sys(SYS_close, maps);
    return;
  }
  size_t have = 0;
  for (;;) {
    long n = zr_sys(SYS_read, maps, zr_maps + have, sizeof(zr_maps) - 1 - have);
    if (n < 0 && errno == EINTR) continue;
    if (n <= 0) break;
    have += (size_t)n;
    zr_maps[have] = 0;
    char *line = zr_maps;
    char *nl;
    while ((nl = memchr(line, '\n', have - (size_t)(line - zr_maps))) != NULL) {
      *nl = 0;
      unsigned long s, e;
      if (zr_eligible(line, compress, &s, &e) && e > s) {
        if (compress) zr_sys(SYS_madvise, s, e - s, MADV_PAGEOUT);
        else zr_read_back(pagemap, s, e);
      }
      line = nl + 1;
    }
    have -= (size_t)(line - zr_maps);
    memmove(zr_maps, line, have);
    if (have == sizeof(zr_maps) - 1) have = 0;
  }
  if (pagemap >= 0) zr_sys(SYS_close, pagemap);
  zr_sys(SYS_close, maps);
}

static void zr_handler(int sig, siginfo_t *info, void *context) {
  (void)sig;
  (void)context;
  if (info == NULL || info->si_code != SI_USER) return;
  int saved = errno;
  __atomic_store_n(&zr_pending, 1, __ATOMIC_SEQ_CST);
  while (__atomic_load_n(&zr_pending, __ATOMIC_SEQ_CST)
         && !__atomic_exchange_n(&zr_busy, 1, __ATOMIC_SEQ_CST)) {
    __atomic_store_n(&zr_pending, 0, __ATOMIC_SEQ_CST);
    int mode = zr_mode();
    if (mode >= 0) zr_walk(mode);
    __atomic_store_n(&zr_busy, 0, __ATOMIC_SEQ_CST);
  }
  errno = saved;
}

static void zr_child(void) {
  __atomic_store_n(&zr_busy, 0, __ATOMIC_SEQ_CST);
  __atomic_store_n(&zr_pending, 0, __ATOMIC_SEQ_CST);
}

__attribute__((constructor)) static void zr_init(void) {
  const char *name = program_invocation_short_name;
  if (strcmp(name, "steam") != 0 && strcmp(name, "steamwebhelper") != 0) return;
  void *libc = dlopen("libc.so.6", RTLD_LAZY | RTLD_NOLOAD);
  if (libc == NULL) return;
  zr_sys = (long (*)(long, ...))dlsym(libc, "syscall");
  if (zr_sys == NULL) return;
  int fd = zr_open(ZR_CTL);
  if (fd < 0) return;
  zr_sys(SYS_close, fd);
  long page = sysconf(_SC_PAGESIZE);
  zr_page = page > 0 ? (unsigned long)page : 4096UL;
  struct sigaction old;
  if (sigaction(SIGURG, NULL, &old) != 0) return;
  if ((old.sa_flags & SA_SIGINFO) || old.sa_handler != SIG_DFL) return;
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sa.sa_sigaction = zr_handler;
  sa.sa_flags = SA_SIGINFO | SA_RESTART;
  sigemptyset(&sa.sa_mask);
  if (sigaction(SIGURG, &sa, NULL) == 0) pthread_atfork(NULL, NULL, zr_child);
}
