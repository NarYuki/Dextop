/*
 * USB devices for Linux in Dextop, passed through Android's USB host API.
 *
 * Android owns USB: neither apps nor the shell user may open /dev/bus/usb. Dextop opens the
 * devices the user allowed through UsbManager and serves them on an abstract socket named in
 * /tmp/dextop-devices.conf:
 *   socket <abstract socket name>
 *   serial <ttyUSB0|ttyACM0|...> <Android device name>
 *
 * USB passthrough: when opening /dev/bus/usb/BBB/DDD is refused, the request goes to Dextop,
 * which answers with the device's usbfs descriptor; libusb and other usbfs users then talk to
 * the kernel directly. libusb's hotplug socket (netlink), which apps may not bind, is let be.
 *
 * USB serial: Android has no kernel driver for applications to use, so Dextop runs the serial
 * protocol itself (as Android serial apps do). Opening /dev/ttyUSB0 gives a pseudo-terminal
 * whose other side this library relays to Dextop: data, line settings (read from the
 * terminal's termios) and the DTR/RTS lines (ioctls on the terminal, answered here).
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/un.h>
#include <termios.h>
#include <unistd.h>

#define CONFIG "/tmp/dextop-devices.conf"
#define MAX_FDS 4096

enum { FRAME_DATA = 0, FRAME_CONFIG = 1, FRAME_CONTROL = 2, FRAME_BREAK = 3 };

struct serial {
    int socket;
    int master;
    int modem; /* TIOCM_* bits the application set */
    pthread_mutex_t lock;
};

static struct serial *serials[MAX_FDS];

/* Socket name, and for [tty] ("ttyUSB0") the Android device name; 0 when listed. */
static int config(const char *tty, char *socket_name, size_t socket_size, char *device, size_t device_size) {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, CONFIG, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char text[8192];
    ssize_t length = syscall(SYS_read, fd, text, sizeof(text) - 1);
    syscall(SYS_close, fd);
    if (length <= 0) return -1;
    text[length] = '\0';
    int found = tty == NULL ? 0 : -1;
    char *cursor = NULL;
    for (char *line = strtok_r(text, "\n", &cursor); line != NULL; line = strtok_r(NULL, "\n", &cursor)) {
        if (strncmp(line, "socket ", 7) == 0) {
            snprintf(socket_name, socket_size, "%s", line + 7);
        } else if (tty != NULL && strncmp(line, "serial ", 7) == 0) {
            char name[64], path[256];
            if (sscanf(line + 7, "%63s %255s", name, path) == 2 && strcmp(name, tty) == 0) {
                snprintf(device, device_size, "%s", path);
                found = 0;
            }
        }
    }
    return socket_name[0] ? found : -1;
}

/* A connection to Dextop carrying [request] ("U <path>" or "S <path>"); -1 on failure. */
static int connect_dextop(const char *socket_name, const char *request) {
    int fd = (int)syscall(SYS_socket, AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t length = strlen(socket_name);
    if (length > sizeof(address.sun_path) - 2) { syscall(SYS_close, fd); return -1; }
    memcpy(address.sun_path + 1, socket_name, length);
    char line[300];
    int size = snprintf(line, sizeof(line), "%s\n", request);
    /* Raw syscalls: the guest's socket shim must not move this abstract name. */
    if (syscall(SYS_connect, fd, &address, (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length)) != 0 ||
        syscall(SYS_sendto, fd, line, (size_t)size, MSG_NOSIGNAL, NULL, 0) != size) {
        syscall(SYS_close, fd);
        return -1;
    }
    return fd;
}

/* USB passthrough: the usbfs descriptor of [path] from Dextop, or -1. */
int dextop_usb_open(const char *path, int flags) {
    if (path == NULL || strncmp(path, "/dev/bus/usb/", 13) != 0) return -1;
    char socket_name[108] = "";
    if (config(NULL, socket_name, sizeof(socket_name), NULL, 0) != 0) return -1;
    char request[300];
    snprintf(request, sizeof(request), "U %s", path);
    int fd = connect_dextop(socket_name, request);
    if (fd < 0) return -1;
    char status = 1;
    char control[CMSG_SPACE(sizeof(int))];
    struct iovec iov = {.iov_base = &status, .iov_len = 1};
    struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1, .msg_control = control, .msg_controllen = sizeof(control)};
    int device = -1;
    if (syscall(SYS_recvmsg, fd, &message, MSG_CMSG_CLOEXEC) == 1 && status == 0) {
        for (struct cmsghdr *header = CMSG_FIRSTHDR(&message); header; header = CMSG_NXTHDR(&message, header)) {
            if (header->cmsg_level == SOL_SOCKET && header->cmsg_type == SCM_RIGHTS) memcpy(&device, CMSG_DATA(header), sizeof(int));
        }
    }
    syscall(SYS_close, fd);
    if (device >= 0 && !(flags & O_CLOEXEC)) fcntl(device, F_SETFD, 0);
    if (device < 0) errno = EACCES;
    return device;
}

static int send_frame(struct serial *serial, int type, const void *data, size_t size) {
    unsigned char header[3] = {(unsigned char)type, (unsigned char)(size & 0xff), (unsigned char)(size >> 8)};
    pthread_mutex_lock(&serial->lock);
    int ok = syscall(SYS_sendto, serial->socket, header, 3, MSG_NOSIGNAL, NULL, 0) == 3 &&
        (size == 0 || syscall(SYS_sendto, serial->socket, data, size, MSG_NOSIGNAL, NULL, 0) == (long)size);
    pthread_mutex_unlock(&serial->lock);
    return ok ? 0 : -1;
}

static speed_t baud_of(speed_t code) {
    static const struct { speed_t code; unsigned int baud; } table[] = {
        {B300, 300}, {B1200, 1200}, {B2400, 2400}, {B4800, 4800}, {B9600, 9600}, {B19200, 19200},
        {B38400, 38400}, {B57600, 57600}, {B115200, 115200}, {B230400, 230400}, {B460800, 460800},
        {B500000, 500000}, {B921600, 921600}, {B1000000, 1000000}, {B2000000, 2000000},
    };
    for (size_t i = 0; i < sizeof(table) / sizeof(*table); i++) if (table[i].code == code) return table[i].baud;
    return 9600;
}

static void send_config(struct serial *serial, const struct termios *settings) {
    unsigned int baud = baud_of(cfgetospeed(settings));
    unsigned char payload[7] = {
        (unsigned char)baud, (unsigned char)(baud >> 8), (unsigned char)(baud >> 16), (unsigned char)(baud >> 24),
        (unsigned char)((settings->c_cflag & CSIZE) == CS5 ? 5 : (settings->c_cflag & CSIZE) == CS6 ? 6 :
                        (settings->c_cflag & CSIZE) == CS7 ? 7 : 8),
        (unsigned char)((settings->c_cflag & CSTOPB) ? 2 : 1),
        (unsigned char)(!(settings->c_cflag & PARENB) ? 0 : (settings->c_cflag & PARODD) ? 1 : 2),
    };
    send_frame(serial, FRAME_CONFIG, payload, sizeof(payload));
}

/* Relays the terminal's master side and Dextop's socket until either closes. */
static void *relay(void *argument) {
    struct serial *serial = argument;
    struct termios last = {0};
    tcgetattr(serial->master, &last);
    send_config(serial, &last);
    unsigned char buffer[4096];
    size_t pending = 0; /* bytes of a socket frame still to copy to the terminal */
    for (;;) {
        struct pollfd fds[2] = {{.fd = serial->master, .events = POLLIN}, {.fd = serial->socket, .events = POLLIN}};
        int ready = poll(fds, 2, 200);
        if (ready < 0 && errno != EINTR) break;
        /* Line settings changed by the application (pyserial, stty, Arduino tools). */
        struct termios now;
        if (tcgetattr(serial->master, &now) == 0 &&
            (cfgetospeed(&now) != cfgetospeed(&last) || (now.c_cflag & (CSIZE | CSTOPB | PARENB | PARODD)) !=
             (last.c_cflag & (CSIZE | CSTOPB | PARENB | PARODD)))) {
            last = now;
            send_config(serial, &now);
        }
        if (fds[0].revents & POLLIN) {
            ssize_t n = read(serial->master, buffer, sizeof(buffer));
            if (n > 0 && send_frame(serial, FRAME_DATA, buffer, (size_t)n) != 0) break;
        }
        if (fds[1].revents & (POLLIN | POLLHUP | POLLERR)) {
            if (pending == 0) {
                unsigned char header[3];
                if (recv(serial->socket, header, 3, MSG_WAITALL) != 3) break;
                pending = header[1] | (size_t)header[2] << 8;
                if (header[0] != FRAME_DATA) { /* nothing else comes from Dextop; skip it */
                    while (pending > 0) {
                        ssize_t n = recv(serial->socket, buffer, pending < sizeof(buffer) ? pending : sizeof(buffer), 0);
                        if (n <= 0) goto done;
                        pending -= (size_t)n;
                    }
                    continue;
                }
            }
            ssize_t n = recv(serial->socket, buffer, pending < sizeof(buffer) ? pending : sizeof(buffer), 0);
            if (n <= 0) break;
            pending -= (size_t)n;
            for (ssize_t done = 0; done < n;) {
                ssize_t w = write(serial->master, buffer + done, (size_t)(n - done));
                if (w < 0 && errno != EINTR) goto done;
                if (w > 0) done += w;
            }
        }
    }
done:
    close(serial->master);
    close(serial->socket);
    return NULL;
}

/* USB serial: the terminal relayed to Dextop for /dev/ttyUSB0 and the like; -2 when [path] is none. */
int dextop_serial_open(const char *path, int flags) {
    if (path == NULL || (strncmp(path, "/dev/ttyUSB", 11) != 0 && strncmp(path, "/dev/ttyACM", 11) != 0)) return -2;
    char socket_name[108] = "", device[256] = "";
    if (config(path + 5, socket_name, sizeof(socket_name), device, sizeof(device)) != 0) return -2;
    char request[300];
    snprintf(request, sizeof(request), "S %s", device);
    int socket = connect_dextop(socket_name, request);
    if (socket < 0) { errno = EBUSY; return -1; }
    char status = 1;
    if (recv(socket, &status, 1, MSG_WAITALL) != 1 || status != 0) { close(socket); errno = EIO; return -1; }
    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) != 0 || unlockpt(master) != 0) { close(socket); errno = EIO; return -1; }
    int slave = open(ptsname(master), (flags & ~O_CREAT) | O_NOCTTY);
    if (slave < 0) { close(master); close(socket); return -1; }
    /* A serial port is raw until the application says otherwise. */
    struct termios raw;
    if (tcgetattr(slave, &raw) == 0) { cfmakeraw(&raw); cfsetspeed(&raw, B9600); tcsetattr(slave, TCSANOW, &raw); }
    struct serial *serial = calloc(1, sizeof(*serial));
    serial->socket = socket;
    serial->master = master;
    serial->modem = TIOCM_DTR | TIOCM_RTS;
    pthread_mutex_init(&serial->lock, NULL);
    if (slave < MAX_FDS) serials[slave] = serial;
    pthread_t thread;
    pthread_attr_t attributes;
    pthread_attr_init(&attributes);
    pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
    pthread_create(&thread, &attributes, relay, serial);
    pthread_attr_destroy(&attributes);
    return slave;
}

/* DTR/RTS and breaks on a relayed terminal, which a pseudo-terminal cannot carry; 1 when handled. */
int dextop_serial_ioctl(int fd, unsigned long request, void *arg, int *result) {
    struct serial *serial = fd >= 0 && fd < MAX_FDS ? serials[fd] : NULL;
    if (serial == NULL) return 0;
    int bits = arg ? *(int *)arg : 0;
    switch (request) {
        case TIOCMGET: *(int *)arg = serial->modem | TIOCM_CTS | TIOCM_DSR | TIOCM_CAR; *result = 0; return 1;
        case TIOCMSET: serial->modem = bits; break;
        case TIOCMBIS: serial->modem |= bits; break;
        case TIOCMBIC: serial->modem &= ~bits; break;
        case TCSBRK: case TCSBRKP: case TIOCSBRK: case TIOCCBRK: {
            unsigned char ms[2] = {250, 0};
            send_frame(serial, FRAME_BREAK, ms, 2);
            *result = 0;
            return 1;
        }
        default: return 0;
    }
    unsigned char lines[2] = {(unsigned char)!!(serial->modem & TIOCM_DTR), (unsigned char)!!(serial->modem & TIOCM_RTS)};
    send_frame(serial, FRAME_CONTROL, lines, 2);
    *result = 0;
    return 1;
}

void dextop_serial_close(int fd) {
    if (fd >= 0 && fd < MAX_FDS) serials[fd] = NULL; /* the relay ends when the terminal closes */
}

/* A listed serial port reports itself as a character device (ttyUSB major 188, ttyACM 166). */
int dextop_serial_stat(const char *path, struct stat *st) {
    if (path == NULL || (strncmp(path, "/dev/ttyUSB", 11) != 0 && strncmp(path, "/dev/ttyACM", 11) != 0)) return -1;
    char socket_name[108] = "", device[256] = "";
    if (config(path + 5, socket_name, sizeof(socket_name), device, sizeof(device)) != 0) return -1;
    memset(st, 0, sizeof(*st));
    st->st_mode = S_IFCHR | 0660;
    st->st_rdev = makedev(strncmp(path, "/dev/ttyACM", 11) == 0 ? 166 : 188, atoi(path + 11));
    st->st_nlink = 1;
    return 0;
}

/* libusb watches hotplug through a uevent netlink socket, which an app may not bind; it then
 * refuses to start. The socket stays silent instead: devices are found by scanning. */
typedef int (*bind_fn)(int, const struct sockaddr *, socklen_t);
int bind(int fd, const struct sockaddr *address, socklen_t length) {
    static bind_fn next;
    if (!next) next = (bind_fn)dlsym(RTLD_NEXT, "bind");
    int result = next(fd, address, length);
    if (result != 0 && (errno == EACCES || errno == EPERM) && address && address->sa_family == AF_NETLINK) return 0;
    return result;
}
