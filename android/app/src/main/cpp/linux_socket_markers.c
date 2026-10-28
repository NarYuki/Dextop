/* Wine accepts FIFO discovery entries for its AF_UNIX server. Android denies
 * creating both socket and FIFO inodes here, so only our tagged regular files
 * are reported as FIFOs. The actual connection uses linux_unixsock.c.
 * Shared by the glibc container session and x86 compatibility libraries. */
#define _GNU_SOURCE
#define _LARGEFILE64_SOURCE
#include <sys/stat.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

static const char marker[] = "Dextop abstract UNIX socket\n";

int dextop_socket_marker_exists(const char *path) {
    int saved = errno, yes = 0;
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (fd >= 0) {
        char bytes[sizeof(marker) + 1];
        yes = read(fd, bytes, sizeof(bytes)) == sizeof(marker) && !memcmp(bytes, marker, sizeof(marker));
        close(fd);
    }
    errno = saved;
    return yes;
}

void dextop_socket_marker(const char *path) {
    int saved = errno;
    const char *enabled = getenv("DEXTOP_SOCKET_MARKERS");
    if (enabled && !strcmp(enabled, "0")) return;
    int fd = open(path, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd >= 0) {
        if (write(fd, marker, sizeof(marker)) != sizeof(marker)) unlink(path);
        close(fd);
    }
    errno = saved;
}

static int tagged(int dir, const char *path, mode_t mode, off_t size) {
    int saved = errno, yes = 0;
    const char *enabled = getenv("DEXTOP_SOCKET_MARKERS");
    if ((!enabled || strcmp(enabled, "0")) && S_ISREG(mode) && size == sizeof(marker)) {
        int fd = openat(dir, path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        if (fd >= 0) {
            char bytes[sizeof(marker)];
            yes = read(fd, bytes, sizeof(bytes)) == sizeof(bytes) && !memcmp(bytes, marker, sizeof(bytes));
            close(fd);
        }
    }
    errno = saved;
    return yes;
}

#define WRAP(name, type) \
int name(const char *path, struct type *st) { \
    static int (*real)(const char *, struct type *); \
    if (!real) real = dlsym(RTLD_NEXT, #name); \
    int rc = real(path, st); \
    if (!rc && tagged(AT_FDCWD, path, st->st_mode, st->st_size)) st->st_mode = (st->st_mode & ~S_IFMT) | S_IFIFO; \
    return rc; \
}
WRAP(lstat, stat)
WRAP(lstat64, stat64)
WRAP(stat, stat)
WRAP(stat64, stat64)

int __lxstat(int version, const char *path, struct stat *st) { (void)version; return lstat(path, st); }
int __lxstat64(int version, const char *path, struct stat64 *st) { (void)version; return lstat64(path, st); }
