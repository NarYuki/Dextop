/*
 * /proc/stat, /proc/uptime and /proc/loadavg for Linux in Dextop's Android execution mode.
 *
 * Android denies these to ordinary app UIDs, so top, htop, uptime, procps' boot time, psutil and
 * Node's os.uptime()/os.loadavg() fail there. When the real open is denied, a read-only memfd with
 * equivalent content is returned instead, built from sysinfo(2), which apps may call: uptime and
 * load from the kernel, boot time from those, and per-CPU lines with zero times (Android hides
 * the counters). Opens that succeed (the privileged execution mode) are untouched.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <time.h>
#include <unistd.h>

static int synthesized(const char *path) {
    if (path == NULL) return 0;
    return strcmp(path, "/proc/stat") == 0 || strcmp(path, "/proc/uptime") == 0 || strcmp(path, "/proc/loadavg") == 0;
}

/* A sealed memfd holding [path]'s stand-in, or -1. */
static int stand_in(const char *path) {
    struct sysinfo info;
    if (sysinfo(&info) != 0) return -1;
    char text[8192];
    int length = 0;
    double scale = 1.0 / (1 << SI_LOAD_SHIFT);
    if (strcmp(path, "/proc/uptime") == 0) {
        length = snprintf(text, sizeof(text), "%ld.00 0.00\n", info.uptime);
    } else if (strcmp(path, "/proc/loadavg") == 0) {
        length = snprintf(text, sizeof(text), "%.2f %.2f %.2f 1/%u %d\n", info.loads[0] * scale,
                          info.loads[1] * scale, info.loads[2] * scale, info.procs, (int) getpid());
    } else {
        long cpus = sysconf(_SC_NPROCESSORS_CONF);
        length = snprintf(text, sizeof(text), "cpu  0 0 0 0 0 0 0 0 0 0\n");
        for (long i = 0; i < cpus && i < 256 && length < (int) sizeof(text) - 64; i++) {
            length += snprintf(text + length, sizeof(text) - length, "cpu%ld 0 0 0 0 0 0 0 0 0 0\n", i);
        }
        length += snprintf(text + length, sizeof(text) - length,
                           "intr 0\nctxt 0\nbtime %ld\nprocesses 0\nprocs_running 1\nprocs_blocked 0\nsoftirq 0\n",
                           (long) time(NULL) - info.uptime);
    }
    if (length <= 0 || length >= (int) sizeof(text)) return -1;
    int fd = memfd_create("dextop-proc", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (fd < 0) return -1;
    if (write(fd, text, (size_t) length) != length || lseek(fd, 0, SEEK_SET) != 0) {
        close(fd);
        return -1;
    }
    fcntl(fd, F_ADD_SEALS, F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE | F_SEAL_SEAL);
    return fd;
}

static int fallback(const char *path, int flags, int fd) {
    if (fd >= 0 || errno != EACCES || !synthesized(path) || (flags & O_ACCMODE) != O_RDONLY) return fd;
    int saved = errno;
    int replacement = stand_in(path);
    if (replacement < 0) { errno = saved; return fd; }
    if (!(flags & O_CLOEXEC)) fcntl(replacement, F_SETFD, 0);
    return replacement;
}

/* Controllers streamed from Android (linux_android_pads.c, linked in the same library). */
int dextop_pad_open(const char *path, int flags);
/* USB serial ports streamed from Android (linux_android_usb.c, also linked here). */
int dextop_serial_open(const char *path, int flags);
int dextop_usb_open(const char *path, int flags);
int dextop_camera_open(const char *path, int flags);
#define PAD(path, flags) do { int pad_fd = dextop_pad_open(path, flags); if (pad_fd != -2) return pad_fd; \
    pad_fd = dextop_serial_open(path, flags); if (pad_fd != -2) return pad_fd; \
    pad_fd = dextop_camera_open(path, flags); if (pad_fd != -2) return pad_fd; } while (0)
/* USB passthrough: a refused /dev/bus/usb node comes from Dextop instead. */
static int usb_fallback(const char *path, int flags, int fd) {
    if (fd >= 0 || (errno != EACCES && errno != EPERM) || path == NULL || strncmp(path, "/dev/bus/usb/", 13) != 0) return fd;
    int saved = errno;
    int device = dextop_usb_open(path, flags);
    if (device < 0) errno = saved;
    return device < 0 ? fd : device;
}

/* Folders picked in Android's folder picker (linux_android_folders.c, also linked here). */
int dextop_folder_open(int dir, const char *path, int flags, int mode);
int dextop_folder_fopen(const char *path, const char *mode, FILE **file);
void dextop_folder_forget(int fd);
#define FOLDER(dir, path, flags, mode) do { int folder_fd = dextop_folder_open(dir, path, flags, mode); \
    if (folder_fd != -2) return folder_fd; } while (0)
static int opened(int fd) { if (fd >= 0) dextop_folder_forget(fd); return fd; }

typedef int (*open_fn)(const char *, int, ...);
typedef int (*openat_fn)(int, const char *, int, ...);
typedef FILE *(*fopen_fn)(const char *, const char *);
#define NEXT(name, type) static type next_##name; if (!next_##name) next_##name = (type) dlsym(RTLD_NEXT, #name)

int open(const char *path, int flags, ...) {
    PAD(path, flags);
    NEXT(open, open_fn);
    va_list args; va_start(args, flags);
    int mode = (flags & (O_CREAT | O_TMPFILE)) ? va_arg(args, int) : 0;
    va_end(args);
    FOLDER(AT_FDCWD, path, flags, mode);
    return opened(usb_fallback(path, flags, fallback(path, flags, next_open(path, flags, mode))));
}

int open64(const char *path, int flags, ...) {
    PAD(path, flags);
    NEXT(open64, open_fn);
    va_list args; va_start(args, flags);
    int mode = (flags & (O_CREAT | O_TMPFILE)) ? va_arg(args, int) : 0;
    va_end(args);
    FOLDER(AT_FDCWD, path, flags, mode);
    return opened(usb_fallback(path, flags, fallback(path, flags, next_open64(path, flags, mode))));
}

int openat(int dir, const char *path, int flags, ...) {
    PAD(path, flags);
    NEXT(openat, openat_fn);
    va_list args; va_start(args, flags);
    int mode = (flags & (O_CREAT | O_TMPFILE)) ? va_arg(args, int) : 0;
    va_end(args);
    FOLDER(dir, path, flags, mode);
    return opened(usb_fallback(path, flags, fallback(path, flags, next_openat(dir, path, flags, mode))));
}

int openat64(int dir, const char *path, int flags, ...) {
    PAD(path, flags);
    NEXT(openat64, openat_fn);
    va_list args; va_start(args, flags);
    int mode = (flags & (O_CREAT | O_TMPFILE)) ? va_arg(args, int) : 0;
    va_end(args);
    FOLDER(dir, path, flags, mode);
    return opened(usb_fallback(path, flags, fallback(path, flags, next_openat64(dir, path, flags, mode))));
}

/* glibc's fopen opens through an internal call no interposer sees. */
static FILE *fopen_fallback(const char *path, const char *mode, FILE *file) {
    if (file != NULL || errno != EACCES || !synthesized(path) || mode == NULL || mode[0] != 'r' || strchr(mode, '+')) return file;
    int saved = errno;
    int fd = stand_in(path);
    if (fd < 0) { errno = saved; return NULL; }
    FILE *replacement = fdopen(fd, "r");
    if (replacement == NULL) { close(fd); errno = saved; }
    return replacement;
}

FILE *fopen(const char *path, const char *mode) {
    FILE *folder;
    if (dextop_folder_fopen(path, mode, &folder)) return folder;
    NEXT(fopen, fopen_fn);
    return fopen_fallback(path, mode, next_fopen(path, mode));
}

FILE *fopen64(const char *path, const char *mode) {
    FILE *folder;
    if (dextop_folder_fopen(path, mode, &folder)) return folder;
    NEXT(fopen64, fopen_fn);
    return fopen_fallback(path, mode, next_fopen64(path, mode));
}

/*
 * Android forbids hard links to app UIDs. dpkg backs up its database with link()
 * (status-old, available-old) and fails the whole installation without it; a copy
 * of a regular file serves those callers. Directories and other types still fail.
 */
static int copy_file(int olddir, const char *old, int newdir, const char *new) {
    struct stat info;
    if (fstatat(olddir, old, &info, 0) != 0 || !S_ISREG(info.st_mode)) return -1;
    int in = openat(olddir, old, O_RDONLY | O_CLOEXEC);
    if (in < 0) return -1;
    int out = openat(newdir, new, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, info.st_mode & 07777);
    if (out < 0) { close(in); return -1; }
    char buffer[65536];
    ssize_t n;
    int failed = 0;
    while ((n = read(in, buffer, sizeof buffer)) != 0) {
        if (n < 0) { if (errno == EINTR) continue; failed = 1; break; }
        for (ssize_t done = 0; done < n;) {
            ssize_t w = write(out, buffer + done, (size_t) (n - done));
            if (w < 0) { if (errno == EINTR) continue; failed = 1; break; }
            done += w;
        }
        if (failed) break;
    }
    close(in);
    if (close(out) != 0) failed = 1;
    if (failed) { unlinkat(newdir, new, 0); return -1; }
    return 0;
}

typedef int (*linkat_fn)(int, const char *, int, const char *, int);
typedef int (*link_fn)(const char *, const char *);

int linkat(int olddir, const char *old, int newdir, const char *new, int flags) {
    NEXT(linkat, linkat_fn);
    int result = next_linkat(olddir, old, newdir, new, flags);
    if (result == 0 || (errno != EPERM && errno != EACCES)) return result;
    int saved = errno;
    if (copy_file(olddir, old, newdir, new) == 0) return 0;
    errno = saved;
    return -1;
}

int link(const char *old, const char *new) {
    NEXT(link, link_fn);
    int result = next_link(old, new);
    if (result == 0 || (errno != EPERM && errno != EACCES)) return result;
    int saved = errno;
    if (copy_file(AT_FDCWD, old, AT_FDCWD, new) == 0) return 0;
    errno = saved;
    return -1;
}
