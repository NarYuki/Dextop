/*
 * Ported from WinNative (maxjivi05, feature/wayland-gamescope, 2026-09-18/19), GPL-3.0.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stddef.h>
#include <string.h>
#include <sys/types.h>

int bl_status_without_tracer(const char *path, int flags) __attribute__((visibility("hidden")));
int bl_writable_retry(int dirfd, const char *path, int flags) __attribute__((visibility("hidden")));
int bl_ntsync_open(const char *path, int flags) __attribute__((visibility("hidden")));
int bl_dircache_create(int dirfd, const char *path, int flags, mode_t mode,
                       int (*real)(int, const char *, int, mode_t)) __attribute__((visibility("hidden")));

static int (*real_open)(const char *, int, ...);
static int (*real_openat)(int, const char *, int, ...);

static int via_open(int dirfd, const char *path, int flags, mode_t mode) {
  (void)dirfd;
  return real_open(path, flags, mode);
}

static int via_openat(int dirfd, const char *path, int flags, mode_t mode) {
  return real_openat(dirfd, path, flags, mode);
}

static int retry_without_noatime(int fd, int flags) {
  return fd < 0 && errno == EPERM && (flags & O_NOATIME);
}

static int is_trace_marker(const char *path) {
  return path != NULL
      && (strcmp(path, "/sys/kernel/tracing/trace_marker") == 0
          || strcmp(path, "/sys/kernel/debug/tracing/trace_marker") == 0);
}

int open(const char *path, int flags, ...) {
  va_list ap;
  mode_t mode;
  int fd;

  va_start(ap, flags);
  mode = va_arg(ap, mode_t);
  va_end(ap);
  if (is_trace_marker(path)) {
    errno = EACCES;
    return -1;
  }
  if ((fd = bl_status_without_tracer(path, flags)) >= 0) return fd;
  if ((fd = bl_ntsync_open(path, flags)) != -2) return fd;
  if (real_open == NULL) real_open = dlsym(RTLD_NEXT, "open");
  if (real_open == NULL) {
    errno = ENOSYS;
    return -1;
  }
  fd = bl_dircache_create(AT_FDCWD, path, flags, mode, via_open);
  if (retry_without_noatime(fd, flags)) fd = real_open(path, flags & ~O_NOATIME, mode);
  if (fd < 0 && bl_writable_retry(AT_FDCWD, path, flags)) fd = real_open(path, flags & ~O_NOATIME, mode);
  return fd;
}

int openat(int dirfd, const char *path, int flags, ...) {
  va_list ap;
  mode_t mode;
  int fd;

  va_start(ap, flags);
  mode = va_arg(ap, mode_t);
  va_end(ap);
  if (is_trace_marker(path)) {
    errno = EACCES;
    return -1;
  }
  if ((fd = bl_status_without_tracer(path, flags)) >= 0) return fd;
  if ((fd = bl_ntsync_open(path, flags)) != -2) return fd;
  if (real_openat == NULL) real_openat = dlsym(RTLD_NEXT, "openat");
  if (real_openat == NULL) {
    errno = ENOSYS;
    return -1;
  }
  fd = bl_dircache_create(dirfd, path, flags, mode, via_openat);
  if (retry_without_noatime(fd, flags)) fd = real_openat(dirfd, path, flags & ~O_NOATIME, mode);
  if (fd < 0 && bl_writable_retry(dirfd, path, flags)) fd = real_openat(dirfd, path, flags & ~O_NOATIME, mode);
  return fd;
}

int open64(const char *path, int flags, ...) __attribute__((alias("open")));
int openat64(int dirfd, const char *path, int flags, ...) __attribute__((alias("openat")));
