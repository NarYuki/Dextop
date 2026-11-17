/* Dextop's conservative Landlock experiment. GPL-3.0-or-later.
 * Keep upstream fastpath.c as a reference; never build its unrestricted hooks.
 * Only openat is exempted. Walk symlink-free paths with directory descriptors;
 * let PRoot resolve everything else. Metadata, sockets and watches stay traced.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

#define STUB_ADDR 0xffff00000UL
#define SLOW (-100000L)
typedef long (*stub_fn)(long, long, long, long, long, long, long);
static stub_fn stub;
static pthread_once_t once = PTHREAD_ONCE_INIT;
static _Thread_local int inside;
static char root[PATH_MAX];
static struct { char host[PATH_MAX], guest[PATH_MAX]; } binds[256];
static size_t count;
static unsigned long hits;
static void report(void) {
  fprintf(stderr, "landlock-fastpath[%d]: %lu open calls accelerated\n", getpid(), __atomic_load_n(&hits, __ATOMIC_RELAXED));
}

static long direct_open(const char *path, int flags, mode_t mode) {
  return stub(SYS_openat, AT_FDCWD, (long)path, flags, mode, 0, 0);
}
static int under(const char *path, const char *base) {
  size_t n = strlen(base);
  return !strncmp(path, base, n) && (!path[n] || path[n] == '/');
}
static void initialize(void) {
#if defined(__aarch64__)
  const char *r = getenv("PROOT_FP_ROOT"), *key = getenv("PROOT_FP_KEY");
  const char *spec = getenv("PROOT_FP_BINDS");
  if (!r || r[0] != '/' || !r[1] || strlen(r) >= sizeof root || !key ||
      !*key || strlen(key) > 100 || getenv("PROOT_FP_OFF")) return;
  void *page = mmap((void *)STUB_ADDR, 4096, PROT_READ | PROT_WRITE,
                   MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
  /* An existing mapping is not trusted code. */
  if (page != (void *)STUB_ADDR) {
    if (page != MAP_FAILED) munmap(page, 4096);
    if (getenv("PROOT_FP_STATS")) fprintf(stderr, "landlock-fastpath: trampoline mapping unavailable (%d)\n", errno);
    return;
  }
  const uint32_t code[] = { 0xaa0003e8, 0xaa0103e0, 0xaa0203e1,
    0xaa0303e2, 0xaa0403e3, 0xaa0503e4, 0xaa0603e5, 0xd4000001, 0xd65f03c0 };
  memcpy(page, code, sizeof code);
  __builtin___clear_cache(page, (char *)page + sizeof code);
  if (mprotect(page, 4096, PROT_READ | PROT_EXEC)) { munmap(page, 4096); return; }
  stub = (stub_fn)page;
  const char *reason = "filter activation probe";
  /* This synthetic prctl returns EUCLEAN only from PRoot's armed filter.
   * No syscall executes for the probe. In normal PRoot it returns EINVAL. */
  if (stub(SYS_prctl, 0x44584c4c, 0, 0, 0, 0, 0) != -117) goto off;
  reason = "bind specification";
  strcpy(root, r);
  if (spec && *spec) {
    char *copy = strdup(spec), *cursor = copy, *entry;
    if (!copy) goto off;
    while ((entry = strsep(&cursor, "|"))) {
      char *colon = strchr(entry, ':');
      if (colon) *colon++ = 0;
      const char *guest = colon ? colon : entry;
      if (count == 256 || entry[0] != '/' || guest[0] != '/' ||
          strlen(entry) >= PATH_MAX || strlen(guest) >= PATH_MAX || strchr(guest, ':')) {
        free(copy); goto off;
      }
      strcpy(binds[count].host, entry);
      strcpy(binds[count++].guest, guest);
    }
    free(copy);
  }
  if (getenv("PROOT_FP_STATS")) atexit(report);
  return;
off:
  if (getenv("PROOT_FP_STATS")) fprintf(stderr, "landlock-fastpath: inactive (%s)\n", reason);
  stub = NULL;
  munmap(page, 4096);
#endif
}
static long fast_open(const char *path, int flags, mode_t mode) {
  /* Walk symlink-free components with ordinary read-directory descriptors.
   * Never use O_PATH (Landlock does not check it), and never cache path facts.
   * Symlinks, dot components, relative names and creation go through PRoot. */
  if (inside || !path || path[0] != '/' ||
      (flags & (O_CREAT | O_NOFOLLOW | O_PATH)) || (flags & O_TMPFILE) == O_TMPFILE)
    return SLOW;
  inside = 1;
  pthread_once(&once, initialize);
  long result = SLOW, directory = -1;
  if (!stub || under(path, "/proc") || under(path, "/dev") || under(path, "/sys")) goto out;
  const char *base = root, *suffix = path;
  size_t longest = 0;
  for (size_t i = 0; i < count; i++) {
    size_t len = strlen(binds[i].guest);
    if (len >= longest && under(path, binds[i].guest)) {
      base = binds[i].host; suffix = path + len; longest = len;
    }
  }
  /* A file bind has no suffix. The final source symlink is still rejected. */
  if (!*suffix) {
    result = direct_open(base, flags | O_NOFOLLOW, mode);
    goto done;
  }
  if (*suffix != '/' || strlen(suffix) >= PATH_MAX) goto out;
  char parts[PATH_MAX];
  strcpy(parts, suffix + 1);
  char *part = parts, *slash;
  /* Reject the entire path before any access. This also avoids entering a
   * nested bind via a lexical alias such as /usr/../etc. */
  for (char *c = parts; ; ) {
    size_t len = strcspn(c, "/");
    if (!len || (len == 1 && c[0] == '.') || (len == 2 && c[0] == '.' && c[1] == '.')) goto out;
    if (!c[len]) break;
    c += len + 1;
  }
  directory = direct_open(base, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW, 0);
  if (directory < 0) goto out;
  while ((slash = strchr(part, '/'))) {
    *slash = 0;
    long next = stub(SYS_openat, directory, (long)part,
                    O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW, 0, 0, 0);
    if (next < 0) goto out;
    syscall(SYS_close, directory);
    directory = next;
    part = slash + 1;
  }
  result = stub(SYS_openat, directory, (long)part, flags | O_NOFOLLOW, mode, 0, 0);
done:
  if (result < 0) result = SLOW;
  else __atomic_add_fetch(&hits, 1, __ATOMIC_RELAXED);
out:
  if (directory >= 0) syscall(SYS_close, directory);
  inside = 0;
  return result;
}
static mode_t open_mode(int flags, va_list ap) {
  return ((flags & O_CREAT) || (flags & O_TMPFILE) == O_TMPFILE) ? va_arg(ap, int) : 0;
}
#define OPEN_WRAPPER(name) \
int name(const char *path, int flags, ...) { \
  va_list ap; va_start(ap, flags); mode_t mode = open_mode(flags, ap); va_end(ap); \
  long fd = fast_open(path, flags, mode); \
  if (fd != SLOW) return (int)fd; \
  int (*next)(const char *, int, ...) = dlsym(RTLD_NEXT, #name); \
  if (!next) { errno = ENOSYS; return -1; } \
  return next(path, flags, mode); \
}
OPEN_WRAPPER(open)
OPEN_WRAPPER(open64)
#define OPENAT_WRAPPER(name) \
int name(int dir, const char *path, int flags, ...) { \
  va_list ap; va_start(ap, flags); mode_t mode = open_mode(flags, ap); va_end(ap); \
  long fd = fast_open(path, flags, mode); \
  if (fd != SLOW) return (int)fd; \
  int (*next)(int, const char *, int, ...) = dlsym(RTLD_NEXT, #name); \
  if (!next) { errno = ENOSYS; return -1; } \
  return next(dir, path, flags, mode); \
}
OPENAT_WRAPPER(openat)
OPENAT_WRAPPER(openat64)
