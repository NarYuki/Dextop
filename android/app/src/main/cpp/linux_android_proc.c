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
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
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

/*
 * Stand-ins Dextop keeps in the guest (LinuxSysStandIns): /proc/net/dev, which proot cannot bind
 * because /proc/net is a per-process link, and listings of /sys directories Android refuses to
 * list while their entries stay readable by path.
 */
#define STANDINS "/run/dextop-standins"

static int net_dev(const char *path) {
    if (path == NULL || strncmp(path, "/proc/", 6) != 0) return 0;
    const char *rest = path + 6;
    if (strncmp(rest, "self/", 5) == 0 || strncmp(rest, "thread-self/", 12) == 0) rest = strchr(rest, '/') + 1;
    else if (*rest >= '0' && *rest <= '9') { while (*rest >= '0' && *rest <= '9') rest++; if (*rest++ != '/') return 0; }
    return strcmp(rest, "net/dev") == 0;
}

/* The stand-in for a refused listing of [path] (absolute, under /sys), or NULL. */
static const char *listing(const char *path, char *out, size_t size) {
    if (path == NULL || strncmp(path, "/sys", 4) != 0 || (path[4] != '\0' && path[4] != '/')) return NULL;
    size_t length = strlen(path);
    while (length > 1 && path[length - 1] == '/') length--;
    /* Listings are flat: "/sys/class" → STANDINS/dirs/sys!class (LinuxSysStandIns). */
    if (strstr(path, "/.") != NULL || strstr(path, "//") != NULL ||
        snprintf(out, size, STANDINS "/dirs/%.*s", (int) length - 1, path + 1) >= (int) size) return NULL;
    for (char *c = out + strlen(STANDINS "/dirs/"); *c; c++) if (*c == '/') *c = '!';
    struct stat info;
    if (stat(out, &info) == 0 && S_ISDIR(info.st_mode)) return out;
    /* Another refused /sys directory lists as empty, as a filtered sysfs would; programs such as
     * lm-sensors treat a refused listing as a broken kernel interface. */
    if (stat(path, &info) != 0 || !S_ISDIR(info.st_mode) || snprintf(out, size, STANDINS "/dirs/.empty") >= (int) size) return NULL;
    return stat(out, &info) == 0 && S_ISDIR(info.st_mode) ? out : NULL;
}

typedef int (*open_fn)(const char *, int, ...);
static int standin_open(const char *path, int flags, int fd) {
    if (fd >= 0 || (errno != EACCES && errno != ENOENT && errno != EPERM)) return fd;
    int saved = errno;
    char buffer[512];
    const char *replacement = NULL;
    if ((flags & O_ACCMODE) == O_RDONLY && net_dev(path)) replacement = STANDINS "/proc/net_dev";
    else if ((flags & O_DIRECTORY) && saved == EACCES) replacement = listing(path, buffer, sizeof buffer);
    if (replacement == NULL) return fd;
    static open_fn real_open;
    if (!real_open) real_open = (open_fn) dlsym(RTLD_NEXT, "open");
    int opened = real_open(replacement, flags & ~(O_CREAT | O_TMPFILE));
    if (opened < 0) { errno = saved; return fd; }
    return opened;
}

static int fallback(const char *path, int flags, int fd) {
    fd = standin_open(path, flags, fd);
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
    if (fd >= 0 || (errno != EACCES && errno != EPERM && errno != ENOENT) || path == NULL || strncmp(path, "/dev/bus/usb/", 13) != 0) return fd;
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
    if (file == NULL && mode != NULL && mode[0] == 'r' && !strchr(mode, '+') && net_dev(path) && (errno == EACCES || errno == ENOENT)) {
        typedef FILE *(*fopen_fn2)(const char *, const char *);
        static fopen_fn2 real;
        if (!real) real = (fopen_fn2) dlsym(RTLD_NEXT, "fopen");
        int saved = errno;
        FILE *replacement = real(STANDINS "/proc/net_dev", mode);
        if (replacement) return replacement;
        errno = saved;
    }
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
 * of a regular file serves those callers, but is NOT a hard link. In particular,
 * transactional stores inspect st_nlink and fall back to their own O_EXCL copy:
 * silently precreating that destination makes them retry forever. Preserve errno
 * and leave no destination for ordinary applications. Installers explicitly opt in.
 * Directories and other types still fail.
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
            if (w <= 0) { if (w < 0 && errno == EINTR) continue; failed = 1; break; }
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

/*
 * Installers and the account tools they run opt in by name, however they were started (sudo -i,
 * sudo sh -c "apt ...", a maintainer script); children inherit the opt-in through the environment.
 */
__attribute__((constructor)) static void hardlink_opt_in(void) {
    static const char *const names[] = {
        "apt", "apt-get", "aptitude", "dpkg", "dpkg-deb", "pacman", "rpm", "dnf", "zypper", "apk",
        "groupadd", "groupdel", "groupmod", "useradd", "userdel", "usermod", "gpasswd", "passwd",
        "chpasswd", "chgpasswd", "chfn", "chsh", "chage", "newusers", "pwconv", "grpconv", "pwunconv",
        "grpunconv", "vipw", "vigr", "grpck", "pwck", "newgrp", "atd", "Xvfb", "Xorg", "Xvnc", "Xtigervnc", "Xephyr", NULL,
    };
    extern char *program_invocation_short_name;
    for (int i = 0; names[i] != NULL; i++) {
        if (strcmp(program_invocation_short_name, names[i]) == 0) { setenv("DEXTOP_HARDLINK_COPY", "1", 1); return; }
    }
}

/*
 * The files this process "linked" by copying. Lock protocols (shadow's commonio, used by groupadd
 * and useradd in package scripts) link a temporary file to the lock and then require st_nlink == 2
 * on it; this process sees that count for its own copies, nobody else does.
 */
static struct { dev_t dev; ino_t ino; } copies[64];
static int copy_count;

static void remember(int dir, const char *path) {
    struct stat info;
    if (fstatat(dir, path, &info, 0) != 0) return;
    int slot = __atomic_fetch_add(&copy_count, 1, __ATOMIC_RELAXED) % 64;
    copies[slot].dev = info.st_dev;
    copies[slot].ino = info.st_ino;
}

static int copied(dev_t dev, ino_t ino) {
    int count = __atomic_load_n(&copy_count, __ATOMIC_RELAXED);
    for (int i = 0; i < count && i < 64; i++) if (copies[i].dev == dev && copies[i].ino == ino) return 1;
    return 0;
}

/* Called by the stat interposers (linux_android_pads.c). */
int dextop_link_counted(int result, struct stat *info) {
    if (result == 0 && info != NULL && info->st_nlink == 1 && copy_count > 0 && copied(info->st_dev, info->st_ino)) info->st_nlink = 2;
    return result;
}

void dextop_link_counted_x(struct statx *info) {
    if (info != NULL && info->stx_nlink == 1 && copy_count > 0 &&
        copied(makedev(info->stx_dev_major, info->stx_dev_minor), info->stx_ino)) info->stx_nlink = 2;
}

static int link_copy(int olddir, const char *old, int newdir, const char *new) {
    if (copy_file(olddir, old, newdir, new) != 0) return -1;
    remember(olddir, old);
    remember(newdir, new);
    return 0;
}

int linkat(int olddir, const char *old, int newdir, const char *new, int flags) {
    NEXT(linkat, linkat_fn);
    int result = next_linkat(olddir, old, newdir, new, flags);
    if (result == 0 || (errno != EPERM && errno != EACCES)) return result;
    int saved = errno;
    const char *allow = getenv("DEXTOP_HARDLINK_COPY");
    if (allow == NULL || strcmp(allow, "1") != 0) return result;
    if (flags & ~(AT_SYMLINK_FOLLOW)) return result;
    if (link_copy(olddir, old, newdir, new) == 0) return 0;
    errno = saved;
    return -1;
}

int link(const char *old, const char *new) {
    NEXT(link, link_fn);
    int result = next_link(old, new);
    if (result == 0 || (errno != EPERM && errno != EACCES)) return result;
    int saved = errno;
    const char *allow = getenv("DEXTOP_HARDLINK_COPY");
    if (allow == NULL || strcmp(allow, "1") != 0) return result;
    if (link_copy(AT_FDCWD, old, AT_FDCWD, new) == 0) return 0;
    errno = saved;
    return -1;
}

/*
 * opendir and scandir open through glibc's internal calls, past the open interposers above, so a
 * refused /sys listing is redirected here as well (btop, lm-sensors, psutil and ls list /sys).
 */
#include <dirent.h>
/* Called by opendir (linux_android_folders.c) when the real listing is refused. */
DIR *dextop_sys_listing(const char *path) {
    typedef DIR *(*opendir_fn)(const char *);
    static opendir_fn real;
    if (!real) real = (opendir_fn) dlsym(RTLD_NEXT, "opendir");
    int saved = errno;
    char buffer[512];
    const char *replacement = listing(path, buffer, sizeof buffer);
    DIR *dir = replacement != NULL ? real(replacement) : NULL;
    if (dir == NULL) errno = saved;
    return dir;
}

typedef int (*scandir_fn)(const char *, struct dirent ***, int (*)(const struct dirent *),
                          int (*)(const struct dirent **, const struct dirent **));
int scandir(const char *path, struct dirent ***list, int (*filter)(const struct dirent *),
            int (*compare)(const struct dirent **, const struct dirent **)) {
    NEXT(scandir, scandir_fn);
    int count = next_scandir(path, list, filter, compare);
    if (count >= 0 || errno != EACCES) return count;
    int saved = errno;
    char buffer[512];
    const char *replacement = listing(path, buffer, sizeof buffer);
    if (replacement != NULL && (count = next_scandir(replacement, list, filter, compare)) >= 0) return count;
    errno = saved;
    return -1;
}

/*
 * The kernel log: Android refuses syslog(2) to apps (ENOSYS through seccomp). dmesg and
 * journalctl -k read an empty log instead of failing; clearing it does nothing.
 */
#include <sys/klog.h>
typedef int (*klogctl_fn)(int, char *, int);
int klogctl(int type, char *buffer, int length) {
    NEXT(klogctl, klogctl_fn);
    int result = next_klogctl(type, buffer, length);
    if (result >= 0 || (errno != ENOSYS && errno != EPERM && errno != EACCES)) return result;
    switch (type) {
    case 2: case 3: case 4: case 9: return 0;           /* read, read all, read+clear, unread count */
    case 10: return 4096;                                /* buffer size */
    case 5: case 6: case 7: case 8: return 0;           /* clear, console off/on/level */
    default: return result;
    }
}

/*
 * iputils ping asks to keep capabilities across its uid change, then opens an unprivileged
 * ICMP datagram socket, and gives up when the request fails. Android's shell user has locked
 * securebits, so PR_SET_KEEPCAPS is refused even for a process that holds no capabilities to
 * keep. With an empty permitted set the request would change nothing: report success then.
 */
#include <linux/capability.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
typedef int (*prctl_fn)(int, unsigned long, unsigned long, unsigned long, unsigned long);

static int holds_no_capabilities(void) {
    struct __user_cap_header_struct header = { _LINUX_CAPABILITY_VERSION_3, 0 };
    struct __user_cap_data_struct data[2];
    if (syscall(SYS_capget, &header, data) != 0) return 0;
    return data[0].permitted == 0 && data[1].permitted == 0;
}

int prctl(int option, ...) {
    NEXT(prctl, prctl_fn);
    va_list arguments;
    va_start(arguments, option);
    unsigned long a2 = va_arg(arguments, unsigned long), a3 = va_arg(arguments, unsigned long);
    unsigned long a4 = va_arg(arguments, unsigned long), a5 = va_arg(arguments, unsigned long);
    va_end(arguments);
    int result = next_prctl(option, a2, a3, a4, a5);
    if (result != 0 && errno == EPERM && option == PR_SET_KEEPCAPS && holds_no_capabilities()) {
        errno = 0;
        return 0;
    }
    return result;
}
