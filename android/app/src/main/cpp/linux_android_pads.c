/*
 * Game controllers for Linux in Dextop's Android execution mode.
 *
 * An app UID cannot open /dev/input, and Android hands controller input to the focused
 * window anyway. Dextop takes the Android controller events there and streams them as evdev
 * events; this preload makes each stream look like an Xbox 360 pad's event node, as DroidDeck
 * does, so SDL, Steam and Wine find it without a kernel device.
 *
 * Dextop binds a directory of its own over /dev/input. It holds an empty placeholder per pad
 * ("event200" and up; their creation is what SDL's inotify hotplug sees) and ".dextop-pads":
 *   socket <abstract socket name>
 *   event<N> <index> <name>
 * Opening a placeholder connects to that socket and sends the pad index (with bit 31 set when
 * this process uses the 32-bit input_event layout); Dextop then writes input_event records.
 * The evdev ioctls, stat and close of those descriptors are answered here.
 *
 * Rumble: force-feedback effects uploaded with EVIOCSFF are kept here; playing one (an EV_FF
 * event written to the descriptor) sends Dextop [strong u16][weak u16][milliseconds u16],
 * little-endian, which it plays on the Android controller's vibrators (all zero stops).
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/un.h>
#include <unistd.h>

#define MANIFEST "/dev/input/.dextop-pads"
#define FIRST_EVENT 200
#define MAX_FDS 4096
#define MAX_PADS 16

/* Pad index + 1 per descriptor; 0 for every other descriptor. */
static unsigned char tracked[MAX_FDS];
static char names[MAX_PADS][128];

#define MAX_EFFECTS 16
static struct { int used; unsigned short strong, weak, length; } effects[MAX_PADS][MAX_EFFECTS];

static const int buttons[] = {
    BTN_SOUTH, BTN_EAST, BTN_NORTH, BTN_WEST, BTN_TL, BTN_TR,
    BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL, BTN_THUMBR,
};
static const struct { int code, min, max, fuzz, flat; } axes[] = {
    {ABS_X, -32768, 32767, 16, 128}, {ABS_Y, -32768, 32767, 16, 128},
    {ABS_Z, 0, 255, 0, 0},
    {ABS_RX, -32768, 32767, 16, 128}, {ABS_RY, -32768, 32767, 16, 128},
    {ABS_RZ, 0, 255, 0, 0},
    {ABS_HAT0X, -1, 1, 0, 0}, {ABS_HAT0Y, -1, 1, 0, 0},
};

/* Relayed USB serial terminals (linux_android_usb.c). */
int dextop_serial_ioctl(int fd, unsigned long request, void *arg, int *result);
/* Android cameras as V4L2 devices (linux_android_camera.c). */
int dextop_camera_ioctl(int fd, unsigned long request, void *arg, int *result);
void dextop_camera_close(int fd);
int dextop_camera_stat(const char *path, struct stat *st);
typedef void *(*mmap_fn)(void *, size_t, int, int, int, off_t);
void *dextop_camera_mmap(mmap_fn real, void *address, size_t length, int prot, int flags, int fd, off_t offset, int *handled);
void dextop_serial_close(int fd);
int dextop_serial_stat(const char *path, struct stat *st);

static int raw_open(const char *path) { return (int)syscall(SYS_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC); }

/* The socket name and [event]'s pad index from the manifest; -1 when [path] is not a pad. */
static int lookup(const char *path, char *socket_name, size_t socket_size) {
    if (path == NULL || strncmp(path, "/dev/input/event", 16) != 0) return -1;
    int event = atoi(path + 16);
    if (event < FIRST_EVENT || event >= FIRST_EVENT + MAX_PADS) return -1;
    int fd = raw_open(MANIFEST);
    if (fd < 0) return -1;
    char text[8192];
    ssize_t length = syscall(SYS_read, fd, text, sizeof(text) - 1);
    syscall(SYS_close, fd);
    if (length <= 0) return -1;
    text[length] = '\0';
    int index = -1;
    char *cursor = NULL;
    for (char *line = strtok_r(text, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor)) {
        int number, pad, used = 0;
        if (strncmp(line, "socket ", 7) == 0) {
            snprintf(socket_name, socket_size, "%s", line + 7);
        } else if (sscanf(line, "event%d %d %n", &number, &pad, &used) == 2 && used > 0 &&
                   number == event && pad >= 0 && pad < MAX_PADS) {
            index = pad;
            snprintf(names[pad], sizeof(names[pad]), "%s", line + used);
        }
    }
    return socket_name[0] ? index : -1;
}

/* A descriptor streaming the pad behind [path], -1 with errno set, or -2 when it is no pad. */
int dextop_pad_open(const char *path, int flags) {
    char socket_name[108] = "";
    int index = lookup(path, socket_name, sizeof(socket_name));
    if (index < 0) return -2;
    int type = SOCK_STREAM | ((flags & O_CLOEXEC) ? SOCK_CLOEXEC : 0) | ((flags & O_NONBLOCK) ? SOCK_NONBLOCK : 0);
    /* Raw syscalls: the guest's socket shim must not move this abstract name. */
    int fd = (int)syscall(SYS_socket, AF_UNIX, type, 0);
    if (fd < 0) return -1;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t length = strlen(socket_name);
    memcpy(address.sun_path + 1, socket_name, length);
    unsigned int header = (unsigned int)index | (sizeof(struct input_event) == 16 ? 0x80000000u : 0);
    if (fd >= MAX_FDS ||
        syscall(SYS_connect, fd, &address, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length)) != 0 ||
        syscall(SYS_sendto, fd, &header, sizeof(header), MSG_NOSIGNAL, NULL, 0) != (long)sizeof(header)) {
        syscall(SYS_close, fd);
        errno = ENODEV;
        return -1;
    }
    tracked[fd] = (unsigned char)(index + 1);
    return fd;
}

static int pad_of(int fd) { return fd >= 0 && fd < MAX_FDS ? tracked[fd] - 1 : -1; }

static void set_bit(unsigned char *bits, size_t size, int bit) {
    if ((size_t)bit / 8 < size) bits[bit / 8] |= (unsigned char)(1 << (bit % 8));
}

static int pad_ioctl(int pad, unsigned long request, void *arg) {
    if (_IOC_TYPE(request) != 'E') { errno = ENOTTY; return -1; }
    unsigned int nr = _IOC_NR(request), size = _IOC_SIZE(request);
    if (request == EVIOCGVERSION) { *(int *)arg = EV_VERSION; return 0; }
    if (request == EVIOCGID) {
        struct input_id id = {.bustype = BUS_USB, .vendor = 0x045e, .product = 0x028e, .version = 0x0110};
        memcpy(arg, &id, sizeof(id));
        return 0;
    }
    if (request == EVIOCGRAB || request == EVIOCSCLOCKID || request == EVIOCREVOKE) return 0;
    if (request == EVIOCGEFFECTS) { *(int *)arg = MAX_EFFECTS; return 0; }
    if (_IOC_DIR(request) == _IOC_WRITE && nr == _IOC_NR(EVIOCSFF)) {
        struct ff_effect *effect = arg;
        unsigned short strong, weak;
        if (effect->type == FF_RUMBLE) {
            strong = effect->u.rumble.strong_magnitude;
            weak = effect->u.rumble.weak_magnitude;
        } else if (effect->type == FF_PERIODIC) {
            strong = weak = (unsigned short)(effect->u.periodic.magnitude < 0 ? -effect->u.periodic.magnitude : effect->u.periodic.magnitude) * 2;
        } else {
            errno = EINVAL;
            return -1;
        }
        int id = effect->id;
        if (id < 0) {
            for (id = 0; id < MAX_EFFECTS && effects[pad][id].used; id++) {}
            if (id == MAX_EFFECTS) { errno = ENOSPC; return -1; }
            effect->id = (short)id;
        } else if (id >= MAX_EFFECTS) {
            errno = EINVAL;
            return -1;
        }
        effects[pad][id].used = 1;
        effects[pad][id].strong = strong;
        effects[pad][id].weak = weak;
        effects[pad][id].length = effect->replay.length;
        return 0;
    }
    if (_IOC_DIR(request) == _IOC_WRITE && nr == _IOC_NR(EVIOCRMFF)) {
        /* The effect id comes by value, not by pointer. */
        long id = (long)(intptr_t)arg;
        if (id < 0 || id >= MAX_EFFECTS) { errno = EINVAL; return -1; }
        effects[pad][id].used = 0;
        return 0;
    }
    if (_IOC_DIR(request) == _IOC_READ && nr == _IOC_NR(EVIOCGNAME(0))) {
        const char *name = names[pad][0] ? names[pad] : "Xbox 360 Controller";
        size_t length = strlen(name) + 1 < size ? strlen(name) + 1 : size;
        memcpy(arg, name, length);
        return (int)length;
    }
    if (_IOC_DIR(request) == _IOC_READ && (nr == _IOC_NR(EVIOCGPHYS(0)) || nr == _IOC_NR(EVIOCGUNIQ(0)))) {
        char text[32] = "";
        if (nr == _IOC_NR(EVIOCGPHYS(0))) snprintf(text, sizeof(text), "dextop/pad%d", pad);
        size_t length = strlen(text) + 1 < size ? strlen(text) + 1 : size;
        memcpy(arg, text, length);
        return (int)length;
    }
    if (_IOC_DIR(request) == _IOC_READ && (nr == _IOC_NR(EVIOCGPROP(0)) || nr == _IOC_NR(EVIOCGKEY(0)) ||
        nr == _IOC_NR(EVIOCGLED(0)) || nr == _IOC_NR(EVIOCGSND(0)) || nr == _IOC_NR(EVIOCGSW(0)))) {
        memset(arg, 0, size);
        return (int)size;
    }
    if (_IOC_DIR(request) == _IOC_READ && nr >= _IOC_NR(EVIOCGBIT(0, 0)) && nr < _IOC_NR(EVIOCGBIT(EV_MAX, 0)) + 1) {
        unsigned char *bits = arg;
        memset(bits, 0, size);
        switch (nr - _IOC_NR(EVIOCGBIT(0, 0))) {
            case 0: set_bit(bits, size, EV_SYN); set_bit(bits, size, EV_KEY); set_bit(bits, size, EV_ABS); set_bit(bits, size, EV_FF); break;
            case EV_FF: set_bit(bits, size, FF_RUMBLE); set_bit(bits, size, FF_PERIODIC); set_bit(bits, size, FF_SINE); set_bit(bits, size, FF_GAIN); break;
            case EV_KEY: for (size_t i = 0; i < sizeof(buttons) / sizeof(*buttons); i++) set_bit(bits, size, buttons[i]); break;
            case EV_ABS: for (size_t i = 0; i < sizeof(axes) / sizeof(*axes); i++) set_bit(bits, size, axes[i].code); break;
        }
        return (int)size;
    }
    if (_IOC_DIR(request) == _IOC_READ && nr >= _IOC_NR(EVIOCGABS(0)) && nr <= _IOC_NR(EVIOCGABS(ABS_MAX))) {
        int code = (int)(nr - _IOC_NR(EVIOCGABS(0)));
        for (size_t i = 0; i < sizeof(axes) / sizeof(*axes); i++) {
            if (axes[i].code != code) continue;
            struct input_absinfo info = {.value = 0, .minimum = axes[i].min, .maximum = axes[i].max,
                                         .fuzz = axes[i].fuzz, .flat = axes[i].flat};
            memcpy(arg, &info, sizeof(info) < size ? sizeof(info) : size);
            return 0;
        }
        errno = EINVAL;
        return -1;
    }
    errno = EINVAL;
    return -1;
}

typedef int (*ioctl_fn)(int, unsigned long, ...);
int ioctl(int fd, unsigned long request, ...) {
    va_list args;
    va_start(args, request);
    void *arg = va_arg(args, void *);
    va_end(args);
    int pad = pad_of(fd);
    if (pad >= 0) return pad_ioctl(pad, request, arg);
    int handled;
    if (dextop_serial_ioctl(fd, request, arg, &handled)) return handled;
    if (dextop_camera_ioctl(fd, request, arg, &handled)) return handled;
    static ioctl_fn next;
    if (!next) next = (ioctl_fn)dlsym(RTLD_NEXT, "ioctl");
    return next(fd, request, arg);
}

/* Picked folders (linux_android_folders.c), linked only into the Android-mode library. */
void dextop_folder_close(int fd) __attribute__((weak));

/* Playing and stopping effects: EV_FF events written to a pad's descriptor. */
static void rumble(int fd, int pad, const struct input_event *event) {
    if (event->type != EV_FF || event->code >= MAX_EFFECTS) return;
    unsigned short frame[3] = {0, 0, 0};
    if (event->value > 0 && effects[pad][event->code].used) {
        frame[0] = effects[pad][event->code].strong;
        frame[1] = effects[pad][event->code].weak;
        frame[2] = effects[pad][event->code].length;
    }
    syscall(SYS_sendto, fd, frame, sizeof(frame), MSG_NOSIGNAL | MSG_DONTWAIT, NULL, 0);
}

typedef ssize_t (*write_fn)(int, const void *, size_t);
ssize_t write(int fd, const void *buffer, size_t count) {
    int pad = pad_of(fd);
    if (pad >= 0) {
        const struct input_event *events = buffer;
        for (size_t i = 0; i < count / sizeof(struct input_event); i++) rumble(fd, pad, &events[i]);
        return (ssize_t)count;
    }
    static write_fn next;
    if (!next) next = (write_fn)dlsym(RTLD_NEXT, "write");
    return next(fd, buffer, count);
}

typedef int (*close_fn)(int);
int close(int fd) {
    if (fd >= 0 && fd < MAX_FDS) tracked[fd] = 0;
    dextop_serial_close(fd);
    dextop_camera_close(fd);
    if (dextop_folder_close) dextop_folder_close(fd);
    static close_fn next;
    if (!next) next = (close_fn)dlsym(RTLD_NEXT, "close");
    return next(fd);
}

/*
 * The placeholders are empty regular files; SDL tells devices apart by st_rdev, so a pad
 * reports a character device of its own (evdev's major 13, minors from 64).
 */
static int pad_index_of_path(const char *path) {
    char socket_name[108] = "";
    return lookup(path, socket_name, sizeof(socket_name));
}

static void as_device(struct stat *st, int pad) {
    st->st_mode = S_IFCHR | 0660;
    st->st_rdev = makedev(13, 64 + FIRST_EVENT + pad);
    st->st_size = 0;
}

typedef int (*stat_fn)(const char *, struct stat *);
typedef int (*xstat_fn)(int, const char *, struct stat *);
typedef int (*fstatat_fn)(int, const char *, struct stat *, int);

static int real_stat(const char *name, const char *xname, const char *path, struct stat *st) {
    stat_fn direct = (stat_fn)dlsym(RTLD_NEXT, name);
    if (direct) return direct(path, st);
    xstat_fn versioned = (xstat_fn)dlsym(RTLD_NEXT, xname);
#ifdef _STAT_VER
    if (versioned) return versioned(_STAT_VER, path, st);
#endif
    errno = ENOSYS;
    return -1;
}

/* Picked folders (linux_android_folders.c): a path below a directory Dextop has not read yet. */
int dextop_folder_missing(int dir, const char *path) __attribute__((weak));
#define FOLDER_MISSING(dir, path) (result != 0 && errno == ENOENT && dextop_folder_missing && dextop_folder_missing(dir, path))

int dextop_stat(const char *path, struct stat *st) __asm__("stat");
int dextop_stat(const char *path, struct stat *st) {
    int result = real_stat("stat", "__xstat", path, st);
    if (FOLDER_MISSING(AT_FDCWD, path)) result = real_stat("stat", "__xstat", path, st);
    int pad = result == 0 ? pad_index_of_path(path) : -1;
    if (pad >= 0) as_device(st, pad);
    if (result != 0 && (dextop_serial_stat(path, st) == 0 || dextop_camera_stat(path, st) == 0)) return 0;
    return result;
}

int dextop_lstat(const char *path, struct stat *st) __asm__("lstat");
int dextop_lstat(const char *path, struct stat *st) {
    int result = real_stat("lstat", "__lxstat", path, st);
    if (FOLDER_MISSING(AT_FDCWD, path)) result = real_stat("lstat", "__lxstat", path, st);
    int pad = result == 0 ? pad_index_of_path(path) : -1;
    if (pad >= 0) as_device(st, pad);
    if (result != 0 && (dextop_serial_stat(path, st) == 0 || dextop_camera_stat(path, st) == 0)) return 0;
    return result;
}

int dextop_xstat(int version, const char *path, struct stat *st) __asm__("__xstat");
int dextop_xstat(int version, const char *path, struct stat *st) {
    xstat_fn next = (xstat_fn)dlsym(RTLD_NEXT, "__xstat");
    if (!next) { errno = ENOSYS; return -1; }
    int result = next(version, path, st);
    if (FOLDER_MISSING(AT_FDCWD, path)) result = next(version, path, st);
    int pad = result == 0 ? pad_index_of_path(path) : -1;
    if (pad >= 0) as_device(st, pad);
    if (result != 0 && (dextop_serial_stat(path, st) == 0 || dextop_camera_stat(path, st) == 0)) return 0;
    return result;
}

int dextop_fstatat(int dir, const char *path, struct stat *st, int flags) __asm__("fstatat");
int dextop_fstatat(int dir, const char *path, struct stat *st, int flags) {
    fstatat_fn next = (fstatat_fn)dlsym(RTLD_NEXT, "fstatat");
    if (!next) { errno = ENOSYS; return -1; }
    int result = next(dir, path, st, flags);
    if (FOLDER_MISSING(dir, path)) result = next(dir, path, st, flags);
    int pad = result == 0 && path && path[0] == '/' ? pad_index_of_path(path) : -1;
    if (pad >= 0) as_device(st, pad);
    if (result != 0 && (dextop_serial_stat(path, st) == 0 || dextop_camera_stat(path, st) == 0)) return 0;
    return result;
}

/* Camera buffers are memory of this process (see linux_android_camera.c). */
void *mmap(void *address, size_t length, int prot, int flags, int fd, off_t offset) {
    static mmap_fn next;
    if (!next) next = (mmap_fn)dlsym(RTLD_NEXT, "mmap");
    int handled = 0;
    void *memory = fd >= 0 ? dextop_camera_mmap(next, address, length, prot, flags, fd, offset, &handled) : MAP_FAILED;
    return handled ? memory : next(address, length, prot, flags, fd, offset);
}

#if !defined(__LP64__)
typedef void *(*mmap64_fn)(void *, size_t, int, int, int, off64_t);
void *mmap64(void *address, size_t length, int prot, int flags, int fd, off64_t offset) {
    static mmap64_fn next;
    if (!next) next = (mmap64_fn)dlsym(RTLD_NEXT, "mmap64");
    static mmap_fn plain;
    if (!plain) plain = (mmap_fn)dlsym(RTLD_NEXT, "mmap");
    int handled = 0;
    void *memory = fd >= 0 ? dextop_camera_mmap(plain, address, length, prot, flags, fd, (off_t)offset, &handled) : MAP_FAILED;
    return handled ? memory : next(address, length, prot, flags, fd, offset);
}
#endif
