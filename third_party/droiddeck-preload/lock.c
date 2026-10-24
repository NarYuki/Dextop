/*
 * Ported from WinNative (maxjivi05, feature/wayland-gamescope, 2026-09-18/19), GPL-3.0.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stddef.h>
#include <sys/file.h>
#include <unistd.h>

static int unsupported(int error) {
  return error == ENOSYS || error == ENOTSUP || error == EOPNOTSUPP;
}

int flock(int fd, int operation) {
  static int (*real_flock)(int, int);
  int result;

  if (real_flock == NULL) real_flock = dlsym(RTLD_NEXT, "flock");
  if (real_flock == NULL) return 0;
  result = real_flock(fd, operation);
  if (result < 0 && unsupported(errno)) return 0;
  return result;
}

static int is_lock_command(int cmd) {
  switch (cmd) {
    case F_SETLK:
    case F_SETLKW:
#ifdef F_OFD_SETLK
    case F_OFD_SETLK:
#endif
#ifdef F_OFD_SETLKW
    case F_OFD_SETLKW:
#endif
      return 1;
    default:
      return 0;
  }
}

static int is_lock_query(int cmd) {
  switch (cmd) {
    case F_GETLK:
#ifdef F_OFD_GETLK
    case F_OFD_GETLK:
#endif
      return 1;
    default:
      return 0;
  }
}

int fcntl(int fd, int cmd, ...) {
  static int (*real_fcntl)(int, int, ...);
  va_list ap;
  void *arg;
  int result;

  va_start(ap, cmd);
  arg = va_arg(ap, void *);
  va_end(ap);

  if (real_fcntl == NULL) real_fcntl = dlsym(RTLD_NEXT, "fcntl");
  if (real_fcntl == NULL) {
    errno = ENOSYS;
    return -1;
  }
  result = real_fcntl(fd, cmd, arg);
  if (result >= 0 || !unsupported(errno)) return result;
  if (is_lock_command(cmd)) return 0;
  if (is_lock_query(cmd) && arg != NULL) {
    ((struct flock *)arg)->l_type = F_UNLCK;
    return 0;
  }
  return result;
}

#if defined(__GLIBC__) && defined(F_SETLK64)
int fcntl64(int fd, int cmd, ...) __attribute__((alias("fcntl")));
#endif
