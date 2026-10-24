/* Socket bookkeeping must not enter fakeroot while its RPC mutex is held.
 * Use kernel metadata rather than interposed stat/open/mkdir ownership hooks.
 * PRoot still translates these syscalls and the 32-bit stat64 ABI stays intact. */
#ifndef BL_SOCKET_METADATA_H
#define BL_SOCKET_METADATA_H
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static inline int bl_socket_fstat(int fd, struct stat64 *st) {
#ifdef SYS_fstat64
  return (int)syscall(SYS_fstat64, fd, st);
#else
  return (int)syscall(SYS_fstat, fd, st);
#endif
}

static inline int bl_socket_stat(const char *path, struct stat64 *st) {
#ifdef SYS_fstatat64
  return (int)syscall(SYS_fstatat64, AT_FDCWD, path, st, 0);
#else
  return (int)syscall(SYS_newfstatat, AT_FDCWD, path, st, 0);
#endif
}
#endif
