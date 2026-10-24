/*
 * LD_PRELOAD shim loaded into every Linux container process (glibc or
 * musl, aarch64). It has no libc dependency of its own: system calls are
 * made directly, and only errno is taken from the guest's libc.
 *
 * Dextop's container processes run as the Android shell user, which SELinux
 * does not allow to create filesystem unix sockets under /data/local/tmp.
 * When a bind() of a path socket is refused, the socket is bound in the
 * abstract namespace instead, under "dextop-<container>" + path; connect()
 * to a path that has no socket file tries the same abstract name. Programs
 * that rendezvous through a socket path (Chrome's ProcessSingleton, dconf,
 * PulseAudio, the VirGL renderer at /tmp/.virgl_test) then keep working.
 */

#include <stddef.h>
#include <stdint.h>

#define AF_UNIX 1
#if defined(__aarch64__)
#define SYS_bind 200
#define SYS_connect 203
#define SYS_openat 56
#define SYS_read 63
#define SYS_close 57
#define SYS_getcwd 17
#elif defined(__x86_64__)
#define SYS_bind 49
#define SYS_connect 42
#define SYS_openat 257
#define SYS_read 0
#define SYS_close 3
#define SYS_getcwd 79
#elif defined(__i386__)
#define SYS_bind 361
#define SYS_connect 362
#define SYS_openat 295
#define SYS_read 3
#define SYS_close 6
#define SYS_getcwd 183
#else
#error Unsupported socket shim architecture
#endif

#define E_PERM 1
#define E_NOENT 2
#define E_ACCES 13
#define E_ROFS 30
#define E_CONNREFUSED 111

struct sockaddr_un_compat {
    unsigned short sun_family;
    char sun_path[108];
};

extern char **environ;
extern int *__errno_location(void);
extern void dextop_socket_marker(const char *) __attribute__((weak));

static long raw_syscall3(long number, long a, long b, long c) {
#if defined(__aarch64__)
    register long x8 __asm__("x8") = number;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2) : "memory", "cc");
    return x0;
#elif defined(__x86_64__)
    long result;
    __asm__ volatile("syscall" : "=a"(result) : "a"(number), "D"(a), "S"(b), "d"(c) : "rcx", "r11", "memory", "cc");
    return result;
#else
    long result;
    __asm__ volatile("int $0x80" : "=a"(result) : "a"(number), "b"(a), "c"(b), "d"(c) : "memory", "cc");
    return result;
#endif
}

static int finish(long result) {
    if (result < 0 && result > -4096) {
        *__errno_location() = (int)-result;
        return -1;
    }
    return (int)result;
}

static const char *namespace_prefix(char fallback[64]) {
    static const char key[] = "DEXTOP_SOCKET_NS=";
    for (char **entry = environ; entry != NULL && *entry != NULL; ++entry) {
        const char *value = *entry;
        size_t i = 0;
        while (key[i] != '\0' && value[i] == key[i]) ++i;
        if (key[i] == '\0') return value + i;
    }
    /* Steam and container launchers may clear LD_PRELOAD and the environment. */
    long fd = raw_syscall3(SYS_openat, -100, (long)"/etc/dextop/socket-namespace", 0x80000 /* O_CLOEXEC */);
    if (fd < 0) return NULL;
    long n = raw_syscall3(SYS_read, fd, (long)fallback, 63);
    raw_syscall3(SYS_close, fd, 0, 0);
    if (n <= 0) return NULL;
    size_t i = 0;
    while (i < (size_t)n && fallback[i] != '\n' && fallback[i] != '\r') ++i;
    fallback[i] = '\0';
    return fallback;
}

/* Builds the abstract twin of a path socket address; returns its length or 0. */
static unsigned abstract_twin(const void *address, unsigned length, struct sockaddr_un_compat *out) {
    const struct sockaddr_un_compat *in = address;
    if (address == NULL || length <= offsetof(struct sockaddr_un_compat, sun_path) + 1) return 0;
    if (in->sun_family != AF_UNIX || in->sun_path[0] == '\0') return 0;
    char fallback[64];
    const char *prefix = namespace_prefix(fallback);
    if (prefix == NULL || prefix[0] == '\0') return 0;
    size_t used = 1;
    out->sun_family = AF_UNIX;
    out->sun_path[0] = '\0';
    for (size_t i = 0; prefix[i] != '\0'; ++i) {
        if (used >= sizeof(out->sun_path)) return 0;
        out->sun_path[used++] = prefix[i];
    }
    size_t limit = length - offsetof(struct sockaddr_un_compat, sun_path);
    char path[4096];
    size_t count = 0;
    /* Wine uses the relative name "socket" after chdir to its prefix's
     * server directory. Include that directory so prefixes cannot collide. */
    if (in->sun_path[0] != '/') {
        long n = raw_syscall3(SYS_getcwd, (long)path, sizeof(path) - 109, 0);
        if (n <= 0) return 0;
        count = (size_t)n - 1;
        if (count > 0 && path[count - 1] != '/') path[count++] = '/';
    }
    for (size_t i = 0; i < limit && i < sizeof(in->sun_path) && in->sun_path[i] != '\0'; ++i) {
        if (in->sun_path[i] == '/' && count > 0 && path[count - 1] == '/') continue;
        path[count++] = in->sun_path[i];
    }
    if (used + count <= sizeof(out->sun_path)) {
        for (size_t i = 0; i < count; ++i) out->sun_path[used++] = path[i];
    } else {
        /* Keep short names stable for the host services; long guest-only
         * sockets get a deterministic name instead of losing the fallback. */
        uint64_t a = UINT64_C(14695981039346656037), b = UINT64_C(7809847782465536322);
        for (size_t i = 0; i < count; ++i) {
            a = (a ^ (unsigned char)path[i]) * UINT64_C(1099511628211);
            b = (b ^ (unsigned char)path[i]) * UINT64_C(14029467366897019727);
        }
        if (used + 34 > sizeof(out->sun_path)) return 0;
        out->sun_path[used++] = '/'; out->sun_path[used++] = '#';
        const char hex[] = "0123456789abcdef";
        for (int shift = 60; shift >= 0; shift -= 4) out->sun_path[used++] = hex[(a >> shift) & 15];
        for (int shift = 60; shift >= 0; shift -= 4) out->sun_path[used++] = hex[(b >> shift) & 15];
    }
    return (unsigned)(offsetof(struct sockaddr_un_compat, sun_path) + used);
}

__attribute__((visibility("default")))
int bind(int fd, const void *address, unsigned length) {
    /* proot rewrites the caller's address to the host path during the call,
     * so the abstract twin is made from the guest path beforehand. */
    struct sockaddr_un_compat twin;
    unsigned twin_length = abstract_twin(address, length, &twin);
    char name[109];
    size_t name_length = 0;
    if (twin_length) {
        const struct sockaddr_un_compat *in = address;
        while (name_length < sizeof(in->sun_path) && name_length + 2 < length && in->sun_path[name_length]) {
            name[name_length] = in->sun_path[name_length]; ++name_length;
        }
    }
    name[name_length] = '\0';
    long result = raw_syscall3(SYS_bind, fd, (long)address, length);
    if (twin_length != 0 && (result == -E_ACCES || result == -E_PERM || result == -E_ROFS)) {
        result = raw_syscall3(SYS_bind, fd, (long)&twin, twin_length);
        /* Wine checks for a socket or FIFO before it attempts connect().
         * The glibc companion reports our tagged discovery entry as a FIFO;
         * traffic uses the abstract socket. */
        if (result == 0 && dextop_socket_marker) dextop_socket_marker(name);
    }
    return finish(result);
}

__attribute__((visibility("default")))
int connect(int fd, const void *address, unsigned length) {
    struct sockaddr_un_compat twin;
    unsigned twin_length = abstract_twin(address, length, &twin);
    long result = raw_syscall3(SYS_connect, fd, (long)address, length);
    if (twin_length != 0 && (result == -E_NOENT || result == -E_CONNREFUSED || result == -E_ACCES)) {
        long retry = raw_syscall3(SYS_connect, fd, (long)&twin, twin_length);
        if (retry == 0 || retry == -115 /* EINPROGRESS */) result = retry;
    }
    return finish(result);
}
