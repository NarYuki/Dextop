/* Loaded only by Dextop's private faked launcher, never by guest applications.
 * Keeps the distro's actual faked/libfakeroot protocol and confines its listener
 * to loopback. Loopback is reachable by every app on the device, so each
 * connection must also present a per-daemon token: it is written to a file only
 * the container's user can read (/tmp/.dextop-faked/<port>), and the guest's
 * connect() shim (linux_unixsock.c) sends it first. A client without it gets a
 * closed stream, which faked handles as an ordinary disconnect. */
#include <stddef.h>
#include <stdint.h>
extern int *__errno_location(void);
/* glibc's fork-handler registration (pthread_atfork is a libc_nonshared wrapper of it). */
extern int __register_atfork(void (*)(void), void (*)(void), void (*)(void), void *) __attribute__((weak));

#define SYS_mkdirat 34
#define SYS_unlinkat 35
#define SYS_renameat 38
#define SYS_openat 56
#define SYS_close 57
#define SYS_write 64
#define SYS_getpid 172
#define SYS_socketpair 199
#define SYS_bind 200
#define SYS_listen 201
#define SYS_accept 202
#define SYS_getsockname 204
#define SYS_recvfrom 207
#define SYS_setsockopt 208
#define SYS_accept4 242
#define SYS_getrandom 278
#define AT_FDCWD (-100)
#define TOKEN_DIR "/tmp/.dextop-faked"
#define TOKEN_LENGTH 32

static long call6(long number, long a, long b, long c, long d, long e, long f) {
    register long x8 __asm__("x8") = number;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    register long x3 __asm__("x3") = d;
    register long x4 __asm__("x4") = e;
    register long x5 __asm__("x5") = f;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5) : "memory", "cc");
    return x0;
}
static long call3(long number, long a, long b, long c) { return call6(number, a, b, c, 0, 0, 0); }
static int checked(long result) {
    if (result < 0 && result >= -4095) { *__errno_location() = (int)-result; return -1; }
    return (int)result;
}

__attribute__((visibility("default")))
int bind(int fd, const void *address, unsigned length) {
    unsigned char local[128];
    const unsigned char *source = address;
    if (address && length <= sizeof local && length >= 2 &&
        (*(const unsigned short *)address == 2 || *(const unsigned short *)address == 10)) {
        for (unsigned i = 0; i < length; ++i) local[i] = source[i];
        if (*(const unsigned short *)address == 2 && length >= 16) {
            local[4] = 127; local[5] = 0; local[6] = 0; local[7] = 1;
        } else if (*(const unsigned short *)address == 10 && length >= 28) {
            for (unsigned i = 8; i < 24; ++i) local[i] = 0;
            local[23] = 1;
        }
        address = local;
    }
    return checked(call3(SYS_bind, fd, (long)address, length));
}

/* The token of the listening socket this daemon serves; empty until listen(). */
static int guarded_fd = -1;
static unsigned guarded_port;
static char token[TOKEN_LENGTH];

static unsigned put_number(char *out, unsigned long value) {
    char digits[24]; unsigned n = 0, i = 0;
    do { digits[n++] = (char)('0' + value % 10); value /= 10; } while (value && n < sizeof digits);
    while (n) out[i++] = digits[--n];
    return i;
}

/* Publishes "<token>\n<pid>\n" atomically with mode 0600; failure leaves the daemon unguarded
 * only if no token could be generated, never with a token nobody can read. */
static int write_token(unsigned port) {
    /* Built by hand: a freestanding library must not need memcpy/memset. */
    char path[64], staged[80];
    static const char dir[] = TOKEN_DIR "/";
    unsigned at = 0;
    for (; dir[at]; ++at) path[at] = dir[at];
    at += put_number(path + at, port);
    path[at] = 0;
    for (unsigned i = 0; i <= at; ++i) staged[i] = path[i];
    staged[at] = '.'; staged[at + 1] = 'n'; staged[at + 2] = 'e'; staged[at + 3] = 'w'; staged[at + 4] = 0;
    call3(SYS_mkdirat, AT_FDCWD, (long)TOKEN_DIR, 0700);
    long fd = call6(SYS_openat, AT_FDCWD, (long)staged, 01 | 0100 | 01000 | 02000000 /* WRONLY|CREAT|TRUNC|CLOEXEC */, 0600, 0, 0);
    if (fd < 0) return -1;
    char body[TOKEN_LENGTH + 32];
    unsigned n = 0;
    for (; n < TOKEN_LENGTH; ++n) body[n] = token[n];
    body[n++] = '\n';
    n += put_number(body + n, (unsigned long)call3(SYS_getpid, 0, 0, 0));
    body[n++] = '\n';
    long written = call3(SYS_write, fd, (long)body, n);
    call3(SYS_close, fd, 0, 0);
    if (written != (long)n || call6(SYS_renameat, AT_FDCWD, (long)staged, AT_FDCWD, (long)path, 0, 0) != 0) {
        call3(SYS_unlinkat, AT_FDCWD, (long)staged, 0);
        return -1;
    }
    return 0;
}

static int publish_token(unsigned port) {
    unsigned char random[TOKEN_LENGTH / 2];
    if (call3(SYS_getrandom, (long)random, sizeof random, 0) != (long)sizeof random) return -1;
    static const char hex[] = "0123456789abcdef";
    for (unsigned i = 0; i < sizeof random; ++i) {
        token[2 * i] = hex[random[i] >> 4];
        token[2 * i + 1] = hex[random[i] & 15];
    }
    return write_token(port);
}

/* faked daemonizes with fork() after listening: the recorded pid must be the child that serves,
 * so clients can tell a live daemon's token from a stale file on a reused port. */
static void republish_in_child(void) {
    if (guarded_fd >= 0) write_token(guarded_port);
}

/* faked can call listen() without bind() when requesting an ephemeral port.
 * Linux otherwise auto-binds that socket to every interface. */
__attribute__((visibility("default")))
int listen(int fd, int backlog) {
    unsigned char address[128];
    unsigned length = sizeof address;
    if (call3(SYS_getsockname, fd, (long)address, (long)&length) == 0 && length >= 4 &&
        (*(unsigned short *)address == 2 || *(unsigned short *)address == 10) &&
        address[2] == 0 && address[3] == 0) {
        if (bind(fd, address, length) != 0) return -1;
    }
    int result = checked(call3(SYS_listen, fd, backlog, 0));
    length = sizeof address;
    if (result == 0 && call3(SYS_getsockname, fd, (long)address, (long)&length) == 0 && length >= 4 &&
        (*(unsigned short *)address == 2 || *(unsigned short *)address == 10)) {
        unsigned port = (unsigned)address[2] << 8 | address[3];
        /* Fail closed: a daemon whose token cannot be published refuses to listen. */
        if (publish_token(port) != 0) { call3(SYS_close, fd, 0, 0); *__errno_location() = 13; return -1; }
        guarded_fd = fd;
        guarded_port = port;
        static int registered;
        if (!registered && __register_atfork) registered = __register_atfork(0, 0, republish_in_child, 0) == 0;
    }
    return result;
}

/* A stream whose peer is already closed: faked reads EOF and drops it like a client that left. */
static long rejected(void) {
    int pair[2];
    if (call6(SYS_socketpair, 1 /* AF_UNIX */, 1 | 02000000 /* SOCK_STREAM|CLOEXEC */, 0, (long)pair, 0, 0) != 0) return -103; /* ECONNABORTED */
    call3(SYS_close, pair[1], 0, 0);
    return pair[0];
}

static long guarded(int fd, void *address, unsigned *length, int flags) {
    long client = call6(SYS_accept4, fd, (long)address, (long)length, flags, 0, 0);
    if (client < 0 || fd != guarded_fd) return client;
    struct { long seconds, micros; } wait = { 1, 0 }, none = { 0, 0 };
    call6(SYS_setsockopt, client, 1 /* SOL_SOCKET */, 20 /* SO_RCVTIMEO */, (long)&wait, sizeof wait, 0);
    char presented[TOKEN_LENGTH];
    unsigned have = 0;
    while (have < TOKEN_LENGTH) {
        long got = call6(SYS_recvfrom, client, (long)(presented + have), TOKEN_LENGTH - have, 0, 0, 0);
        if (got <= 0) break;
        have += (unsigned)got;
    }
    call6(SYS_setsockopt, client, 1, 20, (long)&none, sizeof none, 0);
    unsigned char difference = have != TOKEN_LENGTH;
    for (unsigned i = 0; i < TOKEN_LENGTH; ++i) difference |= (unsigned char)(presented[i] ^ token[i]);
    if (!difference) return client;
    call3(SYS_close, client, 0, 0);
    return rejected();
}

__attribute__((visibility("default")))
int accept(int fd, void *address, unsigned *length) { return checked(guarded(fd, address, length, 0)); }

__attribute__((visibility("default")))
int accept4(int fd, void *address, unsigned *length, int flags) { return checked(guarded(fd, address, length, flags)); }
