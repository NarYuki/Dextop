/* Common named FIFO transport. The Android endpoint transfers real kernel
 * FIFO descriptors. A tagged dangling symlink publishes the guest pathname;
 * normal successful file opens never take the bridge path. */
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stddef.h>
#include <stdint.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/random.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <unistd.h>

static const char prefix[] = "/dev/dextop-fifo-v1/";
#ifndef DEXTOP_FIFO_CONFIG
#define DEXTOP_FIFO_CONFIG "/etc/dextop/fifo-bridge"
#endif

static int bridge(int operation, int flags, const char id[32]) {
    char name[96];
    int config = syscall(SYS_openat, AT_FDCWD, DEXTOP_FIFO_CONFIG, O_RDONLY | O_CLOEXEC, 0);
    if (config < 0) return -1;
    ssize_t n = read(config, name, sizeof(name) - 1);
    close(config);
    if (n <= 0) { errno = ENOTCONN; return -1; }
    while (n && (name[n - 1] == '\n' || name[n - 1] == '\r')) --n;
    name[n] = 0;
    if (n < 1) { errno = ENOTCONN; return -1; }
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    memcpy(address.sun_path + 1, name, n);
    int socket_fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (socket_fd < 0) return -1;
    int result = -1, saved;
    if (connect(socket_fd, (struct sockaddr *)&address, offsetof(struct sockaddr_un, sun_path) + 1 + n)) goto finish;
    unsigned char request[44];
    uint32_t fields[] = {htonl(0x44465031), htonl(operation), htonl(flags)};
    memcpy(request, fields, 12);
    memcpy(request + 12, id, 32);
    for (size_t sent = 0; sent < sizeof(request);) {
        ssize_t count = send(socket_fd, request + sent, sizeof(request) - sent, MSG_NOSIGNAL);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) goto finish;
        sent += count;
    }
    uint32_t response = 0;
    struct iovec vector = {&response, sizeof(response)};
    union {struct cmsghdr align; char bytes[CMSG_SPACE(sizeof(int) * 4)];} control = {0};
    struct msghdr message = {.msg_iov = &vector, .msg_iovlen = 1,
        .msg_control = control.bytes, .msg_controllen = sizeof(control.bytes)};
    ssize_t count;
    do { count = recvmsg(socket_fd, &message, MSG_WAITALL | MSG_CMSG_CLOEXEC); } while (count < 0 && errno == EINTR);
    int descriptor = -1;
    for (struct cmsghdr *header = count >= 0 ? CMSG_FIRSTHDR(&message) : NULL; header; header = CMSG_NXTHDR(&message, header)) {
        if (header->cmsg_level != SOL_SOCKET || header->cmsg_type != SCM_RIGHTS) continue;
        size_t descriptors = (header->cmsg_len - CMSG_LEN(0)) / sizeof(int);
        int *fds = (int *)CMSG_DATA(header);
        for (size_t i = 0; i < descriptors; ++i) {
            if (descriptor < 0) descriptor = fds[i]; else close(fds[i]);
        }
    }
    if (count != sizeof(response) || (message.msg_flags & (MSG_CTRUNC | MSG_TRUNC))) {
        if (descriptor >= 0) close(descriptor);
        errno = EIO;
        goto finish;
    }
    if (ntohl(response)) {
        if (descriptor >= 0) close(descriptor);
        errno = ntohl(response);
        goto finish;
    }
    if (operation == 2) {
        if (descriptor < 0) { errno = EIO; goto finish; }
        if (!(flags & O_CLOEXEC) && fcntl(descriptor, F_SETFD, 0)) {
            saved = errno; close(descriptor); errno = saved; goto finish;
        }
        result = descriptor;
    } else {
        if (descriptor >= 0) close(descriptor);
        result = 0;
    }
finish:
    saved = errno;
    close(socket_fd);
    errno = saved;
    return result;
}

/* Returns 1 only for our exact marker, never arbitrary dangling symlinks. */
int dextop_fifo_metadata(int dir, const char *path, char id[33], unsigned *mode, unsigned *uid, unsigned *gid) {
    int saved = errno;
    char target[128];
    ssize_t n = syscall(SYS_readlinkat, dir, path, target, sizeof(target) - 1);
    int match = 0, used = 0;
    if (n > 0) {
        target[n] = 0;
        if (!strncmp(target, prefix, sizeof(prefix) - 1) &&
            sscanf(target + sizeof(prefix) - 1, "%32[0-9a-f]/%o/%u/%u%n", id, mode, uid, gid, &used) == 4 &&
            strlen(id) == 32 && used == n - (int)(sizeof(prefix) - 1) && *mode <= 0777) match = 1;
    }
    errno = saved;
    return match;
}

static int resolve_fifo(int dir, const char *path, int follow, char resolved[4096],
                        char id[33], unsigned *mode, unsigned *uid, unsigned *gid) {
    int saved = errno, found = 0;
    if (strlen(path) >= 4096) return 0;
    strcpy(resolved, path);
    for (int links = 0; links < 40; ++links) {
        if (dextop_fifo_metadata(dir, resolved, id, mode, uid, gid)) {found = 1; break;}
        if (!follow) break;
        char target[4096];
        ssize_t n = syscall(SYS_readlinkat, dir, resolved, target, sizeof(target) - 1);
        if (n <= 0 || n == sizeof(target) - 1) break;
        target[n] = 0;
        if (target[0] == '/') strcpy(resolved, target);
        else {
            char *slash = strrchr(resolved, '/');
            size_t parent = slash ? (size_t)(slash + 1 - resolved) : 0;
            if (parent + (size_t)n >= 4096) break;
            memcpy(resolved + parent, target, n + 1);
        }
    }
    errno = saved;
    return found;
}

/* Called only after the ordinary file open failed. -2 means unhandled. */
int dextop_fifo_open(int dir, const char *path, int flags) {
    char id[33], resolved[4096]; unsigned mode, uid, gid;
    if (!resolve_fifo(dir, path, !(flags & O_NOFOLLOW), resolved, id, &mode, &uid, &gid)) return -2;
    if ((flags & (O_CREAT | O_EXCL)) == (O_CREAT | O_EXCL)) { errno = EEXIST; return -1; }
    if (flags & O_DIRECTORY) { errno = ENOTDIR; return -1; }
    int supported = O_ACCMODE | O_NONBLOCK | O_CLOEXEC | O_NOFOLLOW | O_PATH;
    return bridge(2, flags & supported, id);
}

static int create_fifo(int dir, const char *path, mode_t mode) {
    struct stat existing;
    if (!fstatat(dir, path, &existing, AT_SYMLINK_NOFOLLOW)) {errno = EEXIST; return -1;}
    int result = syscall(SYS_mknodat, dir, path, S_IFIFO | mode, 0);
    if (!result || (errno != EACCES && errno != EPERM)) return result;
    int original = errno;
    unsigned char bytes[16];
    if (getrandom(bytes, sizeof(bytes), 0) != sizeof(bytes)) { errno = original; return -1; }
    char id[33];
    for (int i = 0; i < 16; ++i) snprintf(id + i * 2, 3, "%02x", bytes[i]);
    /* A temporary regular inode lets the kernel apply this process's umask
     * without changing the process-wide mask or guessing Android's mask. */
    char temporary[4096], target[128];
    const char *slash = strrchr(path, '/');
    size_t parent = slash ? (size_t)(slash + 1 - path) : 0;
    if (parent + 48 >= sizeof(temporary)) { errno = ENAMETOOLONG; return -1; }
    memcpy(temporary, path, parent);
    snprintf(temporary + parent, sizeof(temporary) - parent, ".dextop-fifo-%s", id);
    int fd = syscall(SYS_openat, dir, temporary, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, mode & 0777);
    if (fd < 0) return -1;
    struct stat metadata;
    result = fstat(fd, &metadata);
    int saved = errno;
    close(fd);
    syscall(SYS_unlinkat, dir, temporary, 0);
    if (result) { errno = saved; return -1; }
    if (bridge(1, metadata.st_mode & 0777, id)) return -1;
    snprintf(target, sizeof(target), "%s%s/%o/%u/%u", prefix, id, metadata.st_mode & 0777,
        (unsigned)metadata.st_uid, (unsigned)metadata.st_gid);
    result = syscall(SYS_symlinkat, target, dir, path);
    if (result) { saved = errno; bridge(3, 0, id); errno = saved; }
    return result;
}

int mkfifo(const char *path, mode_t mode) { return create_fifo(AT_FDCWD, path, mode); }
int mkfifoat(int dir, const char *path, mode_t mode) { return create_fifo(dir, path, mode); }
int mknodat(int dir, const char *path, mode_t mode, dev_t device) {
    if (S_ISFIFO(mode)) return create_fifo(dir, path, mode & 0777);
    return syscall(SYS_mknodat, dir, path, mode, device);
}
int mknod(const char *path, mode_t mode, dev_t device) { return mknodat(AT_FDCWD, path, mode, device); }

/* Keep this transport in its own preload so its hooks compose with the
 * existing session library rather than replacing any of its file hooks. */
int openat(int dir, const char *path, int flags, ...) {
    static int (*next)(int, const char *, int, ...);
    if (!next) next = dlsym(RTLD_NEXT, "openat");
    mode_t mode = 0;
    if ((flags & O_CREAT) || (flags & O_TMPFILE) == O_TMPFILE) {
        va_list args; va_start(args, flags); mode = va_arg(args, mode_t); va_end(args);
    }
    // O_PATH|O_NOFOLLOW would otherwise successfully open our physical
    // symlink instead of the logical FIFO. Ordinary opens stay on the fast path.
    if (flags & O_PATH) {
        int replacement = dextop_fifo_open(dir, path, flags);
        if (replacement != -2) return replacement;
    }
    int fd = next(dir, path, flags, mode);
    if (fd >= 0) return fd;
    int saved = errno;
    int replacement = dextop_fifo_open(dir, path, flags);
    if (replacement != -2) return replacement;
    errno = saved;
    return -1;
}
int open(const char *path, int flags, ...) {
    mode_t mode = 0;
    if ((flags & O_CREAT) || (flags & O_TMPFILE) == O_TMPFILE) {
        va_list args; va_start(args, flags); mode = va_arg(args, mode_t); va_end(args);
    }
    return openat(AT_FDCWD, path, flags, mode);
}
int open64(const char *path, int flags, ...) {
    mode_t mode = 0;
    if ((flags & O_CREAT) || (flags & O_TMPFILE) == O_TMPFILE) {
        va_list args; va_start(args, flags); mode = va_arg(args, mode_t); va_end(args);
    }
    return openat(AT_FDCWD, path, flags | O_LARGEFILE, mode);
}
int openat64(int dir, const char *path, int flags, ...) {
    mode_t mode = 0;
    if ((flags & O_CREAT) || (flags & O_TMPFILE) == O_TMPFILE) {
        va_list args; va_start(args, flags); mode = va_arg(args, mode_t); va_end(args);
    }
    return openat(dir, path, flags | O_LARGEFILE, mode);
}

#define PATH_STAT(name, type, lookup, follow) \
int name(const char *path, struct type *out) { \
    static int (*next)(const char *, struct type *); \
    static int (*linkstat)(const char *, struct type *); \
    if (!next) next = dlsym(RTLD_NEXT, #name); \
    int rc = next(path, out), saved = errno; \
    if (rc || S_ISLNK(out->st_mode)) { \
        char id[33], resolved[4096]; unsigned mode, uid, gid; \
        if (resolve_fifo(AT_FDCWD, path, follow, resolved, id, &mode, &uid, &gid)) { \
            if (!linkstat) linkstat = dlsym(RTLD_NEXT, lookup); \
            rc = linkstat(resolved, out); \
            if (!rc) {out->st_mode = S_IFIFO | mode; out->st_uid = uid; out->st_gid = gid; out->st_size = 0;} \
        } \
    } \
    errno = saved; return rc; \
}
PATH_STAT(stat, stat, "lstat", 1)
PATH_STAT(lstat, stat, "lstat", 0)
PATH_STAT(stat64, stat64, "lstat64", 1)
PATH_STAT(lstat64, stat64, "lstat64", 0)
int __xstat(int version, const char *path, struct stat *out) {(void)version; return stat(path, out);}
int __lxstat(int version, const char *path, struct stat *out) {(void)version; return lstat(path, out);}
int __xstat64(int version, const char *path, struct stat64 *out) {(void)version; return stat64(path, out);}
int __lxstat64(int version, const char *path, struct stat64 *out) {(void)version; return lstat64(path, out);}

#define AT_STAT(name, type) \
int name(int dir, const char *path, struct type *out, int flags) { \
    static int (*next)(int, const char *, struct type *, int); \
    if (!next) next = dlsym(RTLD_NEXT, #name); \
    int rc = next(dir, path, out, flags), saved = errno; \
    if (rc || S_ISLNK(out->st_mode)) { \
        char id[33], resolved[4096]; unsigned mode, uid, gid; \
        if (resolve_fifo(dir, path, !(flags & AT_SYMLINK_NOFOLLOW), resolved, id, &mode, &uid, &gid)) { \
            rc = next(dir, resolved, out, flags | AT_SYMLINK_NOFOLLOW); \
            if (!rc) {out->st_mode = S_IFIFO | mode; out->st_uid = uid; out->st_gid = gid; out->st_size = 0;} \
        } \
    } \
    errno = saved; return rc; \
}
AT_STAT(fstatat, stat)
AT_STAT(fstatat64, stat64)
int __fxstatat(int version, int dir, const char *path, struct stat *out, int flags) {
    (void)version; return fstatat(dir, path, out, flags);
}
int __fxstatat64(int version, int dir, const char *path, struct stat64 *out, int flags) {
    (void)version; return fstatat64(dir, path, out, flags);
}

int statx(int dir, const char *path, int flags, unsigned mask, struct statx *out) {
    static int (*next)(int, const char *, int, unsigned, struct statx *);
    if (!next) next = dlsym(RTLD_NEXT, "statx");
    if (!next) {errno = ENOSYS; return -1;}
    int rc = next(dir, path, flags, mask, out), saved = errno;
    if (rc || S_ISLNK(out->stx_mode)) {
        char id[33], resolved[4096]; unsigned mode, uid, gid;
        if (resolve_fifo(dir, path, !(flags & AT_SYMLINK_NOFOLLOW), resolved, id, &mode, &uid, &gid)) {
            rc = next(dir, resolved, flags | AT_SYMLINK_NOFOLLOW, mask, out);
            if (!rc) {
                out->stx_mode = S_IFIFO | mode; out->stx_uid = uid; out->stx_gid = gid; out->stx_size = 0;
            }
        }
    }
    errno = saved; return rc;
}

static int app_fifo_descriptor(int fd) {
    char path[64], target[4096];
    snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
    ssize_t n = syscall(SYS_readlinkat, AT_FDCWD, path, target, sizeof(target) - 1);
    if (n <= 0) return 0;
    target[n] = 0;
    const char *id = strstr(target, "/files/linux-fifos/");
    if (!id) return 0;
    id += strlen("/files/linux-fifos/");
    if (strlen(id) < 32) return 0;
    for (int i = 0; i < 32; ++i)
        if (!((id[i] >= '0' && id[i] <= '9') || (id[i] >= 'a' && id[i] <= 'f'))) return 0;
    return !id[32] || !strcmp(id + 32, " (deleted)");
}

int fstat(int fd, struct stat *out) {
    static int (*next)(int, struct stat *);
    if (!next) next = dlsym(RTLD_NEXT, "fstat");
    int rc = next(fd, out), saved = errno;
    if (!rc && S_ISFIFO(out->st_mode) && app_fifo_descriptor(fd)) {out->st_uid = getuid(); out->st_gid = getgid();}
    errno = saved; return rc;
}
int fstat64(int fd, struct stat64 *out) {
    static int (*next)(int, struct stat64 *);
    // The existing session preload has an i386 fstat64 alias whose structure
    // layout differs from fstat. Resolve libc's actual 64-bit ABI explicitly.
    if (!next) {
        void *libc = dlopen("libc.so.6", RTLD_LAZY | RTLD_LOCAL | RTLD_NOLOAD);
        if (libc) {next = dlsym(libc, "fstat64"); dlclose(libc);}
    }
    if (!next) {errno = ENOSYS; return -1;}
    int rc = next(fd, out), saved = errno;
    if (!rc && S_ISFIFO(out->st_mode) && app_fifo_descriptor(fd)) {out->st_uid = getuid(); out->st_gid = getgid();}
    errno = saved; return rc;
}
int __fxstat(int version, int fd, struct stat *out) {(void)version; return fstat(fd, out);}
int __fxstat64(int version, int fd, struct stat64 *out) {(void)version; return fstat64(fd, out);}

int unlinkat(int dir, const char *path, int flags) {
    char id[33]; unsigned mode, uid, gid;
    int tagged = !flags && dextop_fifo_metadata(dir, path, id, &mode, &uid, &gid);
    struct stat info;
    int last = tagged && !fstatat(dir, path, &info, AT_SYMLINK_NOFOLLOW) && info.st_nlink == 1;
    int rc = syscall(SYS_unlinkat, dir, path, flags), saved = errno;
    if (!rc && last) bridge(3, 0, id);
    errno = saved;
    return rc;
}
int unlink(const char *path) { return unlinkat(AT_FDCWD, path, 0); }

ssize_t readlinkat(int dir, const char *path, char *buffer, size_t size) {
    char id[33]; unsigned mode, uid, gid;
    if (dextop_fifo_metadata(dir, path, id, &mode, &uid, &gid)) {errno = EINVAL; return -1;}
    return syscall(SYS_readlinkat, dir, path, buffer, size);
}
ssize_t readlink(const char *path, char *buffer, size_t size) { return readlinkat(AT_FDCWD, path, buffer, size); }
