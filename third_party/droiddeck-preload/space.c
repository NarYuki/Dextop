#define _GNU_SOURCE
#include <dlfcn.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/statfs.h>
#include <sys/statvfs.h>

static const char *content_of(const char *path, char *buf, size_t len) {
  const char *roots = getenv("BL_LIBRARY_SPACE");
  if (!path || !roots || !*roots)
    return NULL;

  size_t path_len = strlen(path);
  while (*roots) {
    const char *end = strchr(roots, ':');
    size_t root_len = end ? (size_t)(end - roots) : strlen(roots);
    if (root_len > 0) {
      int match = (path_len == root_len && !strncmp(path, roots, root_len)) ||
                  (path_len == root_len + 10 && !strncmp(path, roots, root_len) &&
                   !strcmp(path + root_len, "/steamapps"));
      if (match) {
        if (root_len + sizeof("/steamapps/common") > len)
          return NULL;
        memcpy(buf, roots, root_len);
        strcpy(buf + root_len, "/steamapps/common");
        return buf;
      }
    }
    if (!end)
      break;
    roots = end + 1;
  }
  return NULL;
}

#define FORWARD(name, type)                                                    \
  int name(const char *path, type *out) {                                      \
    static int (*real)(const char *, type *);                                  \
    char buf[4096];                                                            \
    const char *content;                                                       \
    if (!real)                                                                 \
      real = dlsym(RTLD_NEXT, #name);                                          \
    if (!real)                                                                 \
      return -1;                                                               \
    content = content_of(path, buf, sizeof(buf));                              \
    return real(content ? content : path, out);                                \
  }

FORWARD(statfs, struct statfs)
FORWARD(statvfs, struct statvfs)
#ifdef __USE_LARGEFILE64
FORWARD(statfs64, struct statfs64)
FORWARD(statvfs64, struct statvfs64)
#endif
