#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int refused(int error) { return error == EPERM || error == EACCES; }

static int moves_instead(void) {
  const char *v = getenv("DROIDDECK_LINK_RENAME");
  return v != NULL && strcmp(v, "1") == 0;
}

static int lock_name(const char *path) {
  size_t n = path ? strlen(path) : 0;
  return n > 2 && strcmp(path + n - 2, "-l") == 0;
}

__attribute__((visibility("hidden"))) int bl_link_lock(const char *oldpath, const char *newpath, int result) {
  static int (*real_symlink)(const char *, const char *);
  if (result == 0 || !refused(errno)) return result;
  if (moves_instead()) return renameat2(AT_FDCWD, oldpath, AT_FDCWD, newpath, RENAME_NOREPLACE);
  if (!lock_name(newpath)) return result;
  if (real_symlink == NULL) real_symlink = dlsym(RTLD_NEXT, "symlink");
  return real_symlink(oldpath, newpath);
}

#if __SIZEOF_POINTER__ != 8
int link(const char *oldpath, const char *newpath) {
  static int (*real_link)(const char *, const char *);
  if (real_link == NULL) real_link = dlsym(RTLD_NEXT, "link");
  return bl_link_lock(oldpath, newpath, real_link(oldpath, newpath));
}
#endif

int linkat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, int flags) {
  static int (*real_linkat)(int, const char *, int, const char *, int);
  if (real_linkat == NULL) real_linkat = dlsym(RTLD_NEXT, "linkat");
  int result = real_linkat(olddirfd, oldpath, newdirfd, newpath, flags);
  if (result != 0 && refused(errno) && flags == 0 && moves_instead())
    return renameat2(olddirfd, oldpath, newdirfd, newpath, RENAME_NOREPLACE);
  if (result != 0 && refused(errno) && lock_name(newpath) && oldpath[0] == '/' && flags == 0)
    return symlinkat(oldpath, newdirfd, newpath);
  return result;
}
