/*
 * Folders picked in Android's folder picker, for Linux in the Android mode without "All files
 * access" (LinuxFolders.kt).
 *
 * Android grants such folders as documents, not as paths. /media/android holds placeholders
 * Dextop keeps current: real directories, and sparse files of each document's size and time, so
 * listings and stat need nothing here. Opening a file asks Dextop, over the device bridge named
 * in /tmp/dextop-devices.conf, for the document's own descriptor; creating, deleting and
 * renaming go through Dextop, which updates the placeholders. A directory is read again from
 * Android when it is opened. Programs that bypass libc see only the placeholders.
 */
#define _GNU_SOURCE
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/time.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define CONFIG "/tmp/dextop-devices.conf"
#define ROOT "/media/android"
#define MAX_FDS 4096
#define PATH 4096
#ifndef RENAME_NOREPLACE
#define RENAME_NOREPLACE 1
#endif

static char *directories[MAX_FDS]; /* guest paths of directory descriptors inside ROOT */
static char *written[MAX_FDS];     /* guest paths of documents opened for writing */
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static char cwd[PATH];
static int cwd_known;

#define NEXT(name, type) static type next_##name; if (!next_##name) next_##name = (type) dlsym(RTLD_NEXT, #name)

/* The bridge's socket name when the picked folders are in use (a "folders" line); 1 if so. */
static int config(char *socket_name, size_t size) {
    int fd = (int) syscall(SYS_openat, AT_FDCWD, CONFIG, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    char text[8192];
    ssize_t length = syscall(SYS_read, fd, text, sizeof(text) - 1);
    syscall(SYS_close, fd);
    if (length <= 0) return 0;
    text[length] = '\0';
    int folders = 0;
    socket_name[0] = '\0';
    char *cursor = NULL;
    for (char *line = strtok_r(text, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor)) {
        if (strncmp(line, "socket ", 7) == 0) snprintf(socket_name, size, "%s", line + 7);
        else if (strcmp(line, "folders") == 0) folders = 1;
    }
    return folders && socket_name[0];
}

/* Sends [line] and returns Dextop's status (0 or an errno), -1 without an answer; [fd] receives a descriptor. */
static int relay(const char *socket_name, const char *line, int *fd) {
    if (fd) *fd = -1;
    int s = (int) syscall(SYS_socket, AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (s < 0) return -1;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t name = strlen(socket_name);
    if (name > sizeof(address.sun_path) - 2) { syscall(SYS_close, s); return -1; }
    memcpy(address.sun_path + 1, socket_name, name);
    /* Raw syscalls: the guest's socket shim must not move this abstract name. */
    if (syscall(SYS_connect, s, &address, (socklen_t) (offsetof(struct sockaddr_un, sun_path) + 1 + name)) != 0) {
        syscall(SYS_close, s);
        return -1;
    }
    /* A stalled Dextop must not hang the application: give up after 30 seconds. */
    struct timeval timeout = {.tv_sec = 30};
    syscall(SYS_setsockopt, s, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    syscall(SYS_setsockopt, s, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    /* The newline ends the request. */
    size_t length = strlen(line) + 1;
    char *data = malloc(length);
    if (data == NULL) { syscall(SYS_close, s); return -1; }
    memcpy(data, line, length - 1);
    data[length - 1] = '\n';
    for (size_t sent = 0; sent < length;) {
        ssize_t n = syscall(SYS_sendto, s, data + sent, length - sent, MSG_NOSIGNAL, NULL, 0);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) { free(data); syscall(SYS_close, s); return -1; }
        sent += (size_t) n;
    }
    free(data);
    unsigned char status = 0;
    char control[CMSG_SPACE(sizeof(int))];
    struct iovec iov = {.iov_base = &status, .iov_len = 1};
    struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof(control)};
    ssize_t got;
    do got = syscall(SYS_recvmsg, s, &message, MSG_CMSG_CLOEXEC); while (got < 0 && errno == EINTR);
    if (got == 1) {
        for (struct cmsghdr *header = CMSG_FIRSTHDR(&message); header; header = CMSG_NXTHDR(&message, header)) {
            if (header->cmsg_level != SOL_SOCKET || header->cmsg_type != SCM_RIGHTS) continue;
            int received;
            memcpy(&received, CMSG_DATA(header), sizeof(int));
            if (fd && *fd < 0) *fd = received; else syscall(SYS_close, received);
        }
    }
    syscall(SYS_close, s);
    return got == 1 ? status : -1;
}

/* [path] with "." and ".." resolved and repeated slashes removed; [path] is absolute. */
static int normalize(const char *path, char *out, size_t size) {
    size_t length = 0;
    out[0] = '\0';
    for (const char *p = path; *p;) {
        while (*p == '/') p++;
        if (!*p) break;
        const char *end = strchr(p, '/');
        size_t n = end ? (size_t) (end - p) : strlen(p);
        if (n == 1 && p[0] == '.') {
        } else if (n == 2 && p[0] == '.' && p[1] == '.') {
            while (length > 0 && out[length - 1] != '/') length--;
            if (length > 0) length--;
            out[length] = '\0';
        } else {
            if (length + 1 + n >= size) return -1;
            out[length++] = '/';
            memcpy(out + length, p, n);
            length += n;
            out[length] = '\0';
        }
        p += n;
    }
    if (length == 0) snprintf(out, size, "/");
    return 0;
}

static int under_root(const char *path) {
    return strncmp(path, ROOT "/", sizeof(ROOT)) == 0 && path[sizeof(ROOT)] != '\0';
}

/* The guest path [path] (relative to [dir]) names inside a picked folder, into [out]; 1 if so. */
static int inside(int dir, const char *path, char *out) {
    if (path == NULL || path[0] == '\0') return 0;
    char joined[PATH * 2];
    if (path[0] == '/') {
        if (strstr(path, "android") == NULL) return 0;
        snprintf(joined, sizeof(joined), "%s", path);
    } else {
        char base[PATH] = "";
        if (dir == AT_FDCWD) {
            if (!cwd_known) {
                if (syscall(SYS_getcwd, cwd, sizeof(cwd)) <= 0) cwd[0] = '\0';
                cwd_known = 1;
            }
            snprintf(base, sizeof(base), "%s", cwd);
        } else if (dir >= 0 && dir < MAX_FDS) {
            pthread_mutex_lock(&lock);
            if (directories[dir]) snprintf(base, sizeof(base), "%s", directories[dir]);
            pthread_mutex_unlock(&lock);
        }
        if (strncmp(base, ROOT, sizeof(ROOT) - 1) != 0 || (base[sizeof(ROOT) - 1] != '/' && base[sizeof(ROOT) - 1] != '\0')) return 0;
        snprintf(joined, sizeof(joined), "%s/%s", base, path);
    }
    return normalize(joined, out, PATH) == 0 && under_root(out);
}

static void remember(char **table, int fd, const char *path) {
    if (fd < 0 || fd >= MAX_FDS) return;
    char *copy = strdup(path);
    pthread_mutex_lock(&lock);
    char *old = table[fd];
    table[fd] = copy;
    pthread_mutex_unlock(&lock);
    free(old);
}

/* A descriptor number now used for something else. */
void dextop_folder_forget(int fd) {
    if (fd < 0 || fd >= MAX_FDS || (!directories[fd] && !written[fd])) return;
    pthread_mutex_lock(&lock);
    char *dir = directories[fd], *path = written[fd];
    directories[fd] = written[fd] = NULL;
    pthread_mutex_unlock(&lock);
    free(dir);
    free(path);
}

/* Before a written document's descriptor closes: its placeholder takes the new size and time. */
void dextop_folder_close(int fd) {
    if (fd < 0 || fd >= MAX_FDS || (!directories[fd] && !written[fd])) return;
    pthread_mutex_lock(&lock);
    char *dir = directories[fd], *path = written[fd];
    directories[fd] = written[fd] = NULL;
    pthread_mutex_unlock(&lock);
    free(dir);
    if (path == NULL) return;
    int saved = errno;
    struct stat st;
    if (fstat(fd, &st) == 0 && S_ISREG(st.st_mode)) {
        /* The placeholder itself, not the document (truncate() is hooked below). */
        syscall(SYS_truncate, path, st.st_size);
        struct timespec times[2] = {st.st_atim, st.st_mtim};
        utimensat(AT_FDCWD, path, times, 0);
    }
    errno = saved;
    free(path);
}

/* Reads a directory again from Android, at most every two seconds per directory. */
static int refresh(const char *socket_name, const char *guest) {
    static char last[PATH];
    static time_t when;
    time_t now = time(NULL);
    pthread_mutex_lock(&lock);
    int fresh = now - when < 2 && strcmp(last, guest) == 0;
    if (!fresh) { snprintf(last, sizeof(last), "%s", guest); when = now; }
    pthread_mutex_unlock(&lock);
    if (fresh) return 0;
    char line[PATH + 8];
    snprintf(line, sizeof(line), "F L %s", guest);
    return relay(socket_name, line, NULL) == 0;
}

/*
 * stat() found nothing at [path] in a picked folder. Dextop fills directories as they are
 * opened, so a path below one not opened yet is missing: its directory is read; 1 to retry.
 */
int dextop_folder_missing(int dir, const char *path) {
    char guest[PATH], socket_name[108];
    if (!inside(dir, path, guest)) return 0;
    char *slash = strrchr(guest, '/');
    if (slash == NULL || slash - guest <= (long) (sizeof(ROOT) - 1)) return 0;
    *slash = '\0';
    if (!config(socket_name, sizeof(socket_name))) return 0;
    int saved = errno;
    int read = refresh(socket_name, guest);
    errno = saved;
    return read;
}

/*
 * Opens [path] when it lies in a picked folder: directories are the placeholders themselves,
 * files are Dextop's descriptors. -2 for any other path.
 */
int dextop_folder_open(int dir, const char *path, int flags, int mode) {
    char guest[PATH], socket_name[108];
    if (!inside(dir, path, guest) || !config(socket_name, sizeof(socket_name))) return -2;
    struct stat st;
    int exists = fstatat(AT_FDCWD, guest, &st, 0) == 0;
    if (exists && S_ISDIR(st.st_mode)) {
        refresh(socket_name, guest);
        int fd = (int) syscall(SYS_openat, AT_FDCWD, guest, flags, mode);
        if (fd >= 0) {
            dextop_folder_forget(fd);
            remember(directories, fd, guest);
        }
        return fd;
    }
    if (flags & O_DIRECTORY) {
        errno = exists ? ENOTDIR : ENOENT;
        return -1;
    }
    char line[PATH + 32];
    snprintf(line, sizeof(line), "F O %d %s", flags & (O_ACCMODE | O_CREAT | O_EXCL | O_TRUNC), guest);
    int fd = -1;
    int status = relay(socket_name, line, &fd);
    if (status != 0 || fd < 0) {
        if (fd >= 0) syscall(SYS_close, fd);
        errno = status > 0 ? status : EACCES;
        return -1;
    }
    if (!(flags & O_CLOEXEC)) fcntl(fd, F_SETFD, 0);
    if (flags & (O_APPEND | O_NONBLOCK)) fcntl(fd, F_SETFL, flags & (O_APPEND | O_NONBLOCK));
    dextop_folder_forget(fd);
    if ((flags & O_ACCMODE) != O_RDONLY) remember(written, fd, guest);
    return fd;
}

/* fopen() for a picked folder; 1 when handled, the stream (or NULL with errno) in [file]. */
int dextop_folder_fopen(const char *path, const char *mode, FILE **file) {
    if (mode == NULL) return 0;
    int flags;
    switch (mode[0]) {
        case 'r': flags = O_RDONLY; break;
        case 'w': flags = O_WRONLY | O_CREAT | O_TRUNC; break;
        case 'a': flags = O_WRONLY | O_CREAT | O_APPEND; break;
        default: return 0;
    }
    int plus = strchr(mode, '+') != NULL;
    if (plus) flags = (flags & ~O_ACCMODE) | O_RDWR;
    if (strchr(mode, 'e')) flags |= O_CLOEXEC;
    if (strchr(mode, 'x')) flags |= O_EXCL;
    int fd = dextop_folder_open(AT_FDCWD, path, flags, 0666);
    if (fd == -2) return 0;
    *file = NULL;
    if (fd < 0) return 1;
    char basic[3] = {mode[0], plus ? '+' : '\0', '\0'};
    *file = fdopen(fd, basic);
    if (*file == NULL) {
        int saved = errno;
        close(fd);
        errno = saved;
    }
    return 1;
}

/* A request without a descriptor for [path]; 1 when [path] is in a picked folder, the result in [result]. */
static int simple(int dir, const char *path, char op, int *result) {
    char guest[PATH], socket_name[108];
    if (!inside(dir, path, guest) || !config(socket_name, sizeof(socket_name))) return 0;
    char line[PATH + 8];
    snprintf(line, sizeof(line), "F %c %s", op, guest);
    int status = relay(socket_name, line, NULL);
    if (status == 0) {
        *result = 0;
    } else {
        errno = status > 0 ? status : EACCES;
        *result = -1;
    }
    return 1;
}

typedef int (*mkdir_fn)(const char *, mode_t);
typedef int (*mkdirat_fn)(int, const char *, mode_t);
typedef int (*path_fn)(const char *);
typedef int (*unlinkat_fn)(int, const char *, int);
typedef int (*rename_fn)(const char *, const char *);
typedef int (*renameat_fn)(int, const char *, int, const char *);
typedef int (*renameat2_fn)(int, const char *, int, const char *, unsigned int);
typedef int (*creat_fn)(const char *, mode_t);
typedef int (*truncate_fn)(const char *, off_t);
typedef DIR *(*opendir_fn)(const char *);
typedef int (*closedir_fn)(DIR *);
typedef int (*fclose_fn)(FILE *);
typedef int (*fchdir_fn)(int);
typedef int (*dup2_fn)(int, int);
typedef int (*dup3_fn)(int, int, int);

int mkdir(const char *path, mode_t mode) {
    int result;
    if (simple(AT_FDCWD, path, 'M', &result)) return result;
    NEXT(mkdir, mkdir_fn);
    return next_mkdir(path, mode);
}

int mkdirat(int dir, const char *path, mode_t mode) {
    int result;
    if (simple(dir, path, 'M', &result)) return result;
    NEXT(mkdirat, mkdirat_fn);
    return next_mkdirat(dir, path, mode);
}

int unlink(const char *path) {
    int result;
    if (simple(AT_FDCWD, path, 'D', &result)) return result;
    NEXT(unlink, path_fn);
    return next_unlink(path);
}

int rmdir(const char *path) {
    int result;
    if (simple(AT_FDCWD, path, 'R', &result)) return result;
    NEXT(rmdir, path_fn);
    return next_rmdir(path);
}

int unlinkat(int dir, const char *path, int flags) {
    int result;
    if (simple(dir, path, (flags & AT_REMOVEDIR) ? 'R' : 'D', &result)) return result;
    NEXT(unlinkat, unlinkat_fn);
    return next_unlinkat(dir, path, flags);
}

/* Renames inside one picked folder go to Dextop; into or out of the folders, mv copies (EXDEV). */
static int moved(int olddir, const char *old, int newdir, const char *new, unsigned int flags, int *result) {
    char from[PATH], to[PATH], socket_name[108];
    int a = inside(olddir, old, from), b = inside(newdir, new, to);
    if ((!a && !b) || !config(socket_name, sizeof(socket_name))) return 0;
    *result = -1;
    if (!a || !b) { errno = EXDEV; return 1; }
    char line[PATH * 2 + 16];
    snprintf(line, sizeof(line), "F N %u %s\t%s", flags & RENAME_NOREPLACE, from, to);
    int status = relay(socket_name, line, NULL);
    if (status == 0) *result = 0;
    else errno = status > 0 ? status : EACCES;
    return 1;
}

int rename(const char *old, const char *new) {
    int result;
    if (moved(AT_FDCWD, old, AT_FDCWD, new, 0, &result)) return result;
    NEXT(rename, rename_fn);
    return next_rename(old, new);
}

int renameat(int olddir, const char *old, int newdir, const char *new) {
    int result;
    if (moved(olddir, old, newdir, new, 0, &result)) return result;
    NEXT(renameat, renameat_fn);
    return next_renameat(olddir, old, newdir, new);
}

int renameat2(int olddir, const char *old, int newdir, const char *new, unsigned int flags) {
    int result;
    if (moved(olddir, old, newdir, new, flags, &result)) return result;
    NEXT(renameat2, renameat2_fn);
    if (!next_renameat2) { errno = ENOSYS; return -1; }
    return next_renameat2(olddir, old, newdir, new, flags);
}

/* glibc's creat and truncate open through internal calls no interposer sees. */
int creat(const char *path, mode_t mode) {
    int fd = dextop_folder_open(AT_FDCWD, path, O_WRONLY | O_CREAT | O_TRUNC, mode);
    if (fd != -2) return fd;
    NEXT(creat, creat_fn);
    return next_creat(path, mode);
}

int creat64(const char *path, mode_t mode) {
    int fd = dextop_folder_open(AT_FDCWD, path, O_WRONLY | O_CREAT | O_TRUNC, mode);
    if (fd != -2) return fd;
    NEXT(creat64, creat_fn);
    return next_creat64(path, mode);
}

int truncate(const char *path, off_t length) {
    char guest[PATH], socket_name[108];
    if (inside(AT_FDCWD, path, guest) && config(socket_name, sizeof(socket_name))) {
        int fd = dextop_folder_open(AT_FDCWD, guest, O_WRONLY, 0);
        if (fd < 0) return -1;
        int result = ftruncate(fd, length);
        int saved = errno;
        close(fd);
        errno = saved;
        return result;
    }
    NEXT(truncate, truncate_fn);
    return next_truncate(path, length);
}

DIR *opendir(const char *path) {
    char guest[PATH], socket_name[108];
    int mine = inside(AT_FDCWD, path, guest) && config(socket_name, sizeof(socket_name));
    if (mine) refresh(socket_name, guest);
    NEXT(opendir, opendir_fn);
    DIR *dir = next_opendir(path);
    if (dir != NULL) {
        dextop_folder_forget(dirfd(dir));
        if (mine) remember(directories, dirfd(dir), guest);
    }
    return dir;
}

/* closedir and fclose close through internal calls too. */
int closedir(DIR *dir) {
    if (dir != NULL) dextop_folder_forget(dirfd(dir));
    NEXT(closedir, closedir_fn);
    return next_closedir(dir);
}

int fclose(FILE *file) {
    int fd = file != NULL ? fileno(file) : -1;
    if (fd >= 0 && fd < MAX_FDS && written[fd]) {
        fflush(file);
        dextop_folder_close(fd);
    } else if (fd >= 0) {
        dextop_folder_forget(fd);
    }
    NEXT(fclose, fclose_fn);
    return next_fclose(file);
}

int chdir(const char *path) {
    /* Dextop fills placeholder directories as they are opened; one entered directly is read first. */
    char guest[PATH], socket_name[108];
    if (inside(AT_FDCWD, path, guest) && config(socket_name, sizeof(socket_name))) refresh(socket_name, guest);
    NEXT(chdir, path_fn);
    int result = next_chdir(path);
    if (result == 0) cwd_known = 0;
    return result;
}

int fchdir(int fd) {
    NEXT(fchdir, fchdir_fn);
    int result = next_fchdir(fd);
    if (result == 0) cwd_known = 0;
    return result;
}

int dup2(int old, int new) {
    NEXT(dup2, dup2_fn);
    int result = next_dup2(old, new);
    if (result >= 0 && old != new) dextop_folder_forget(result);
    return result;
}

int dup3(int old, int new, int flags) {
    NEXT(dup3, dup3_fn);
    int result = next_dup3(old, new, flags);
    if (result >= 0) dextop_folder_forget(result);
    return result;
}
