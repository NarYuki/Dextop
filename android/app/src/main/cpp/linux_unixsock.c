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
#define AF_NETLINK 16
#define NETLINK_AUDIT 9
#if defined(__aarch64__)
#define SYS_bind 200
#define SYS_socket 198
#define SYS_connect 203
#define SYS_openat 56
#define SYS_read 63
#define SYS_close 57
#define SYS_getcwd 17
#define SYS_prctl 167
#define SYS_fchownat 54
#define SYS_fchmodat 53
#define SYS_sendto 206
#define SYS_sendmsg 211
#elif defined(__x86_64__)
#define SYS_bind 49
#define SYS_socket 41
#define SYS_connect 42
#define SYS_openat 257
#define SYS_read 0
#define SYS_close 3
#define SYS_getcwd 79
#define SYS_prctl 157
#define SYS_fchownat 260
#define SYS_fchmodat 268
#define SYS_sendto 44
#define SYS_sendmsg 46
#elif defined(__i386__)
#define SYS_bind 361
#define SYS_socket 359
#define SYS_connect 362
#define SYS_openat 295
#define SYS_read 3
#define SYS_close 6
#define SYS_getcwd 183
#define SYS_prctl 172
#define SYS_fchownat 298
#define SYS_fchmodat 306
#else
#error Unsupported socket shim architecture
#endif

#define E_PERM 1
#define E_NOENT 2
#define E_ACCES 13
#define E_ROFS 30
#define E_CONNREFUSED 111
#define E_ADDRINUSE 98
#define E_PROTONOSUPPORT 93
#define E_INVAL 22
#define E_ROFS_DEVICE 30
#define PR_SET_SECCOMP_OPTION 22
#define SECCOMP_MODE_FILTER_VALUE 2

struct sockaddr_un_compat {
    unsigned short sun_family;
    char sun_path[108];
};

extern char **environ;
extern int *__errno_location(void);
extern void dextop_socket_marker(const char *) __attribute__((weak));
extern int dextop_socket_marker_exists(const char *) __attribute__((weak));

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

static long raw_syscall5(long number, long a, long b, long c, long d, long e) {
#if defined(__aarch64__)
    register long x8 __asm__("x8") = number;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    register long x3 __asm__("x3") = d;
    register long x4 __asm__("x4") = e;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4) : "memory", "cc");
    return x0;
#elif defined(__x86_64__)
    long result;
    register long r10 __asm__("r10") = d;
    register long r8 __asm__("r8") = e;
    __asm__ volatile("syscall" : "=a"(result) : "a"(number), "D"(a), "S"(b), "d"(c), "r"(r10), "r"(r8) : "rcx", "r11", "memory", "cc");
    return result;
#else
    long result;
    __asm__ volatile("push %%ebx; mov %2, %%ebx; int $0x80; pop %%ebx"
        : "=a"(result) : "a"(number), "r"(a), "c"(b), "d"(c), "S"(d), "D"(e) : "memory", "cc");
    return result;
#endif
}

#if !defined(__i386__)
static long raw_syscall6(long number, long a, long b, long c, long d, long e, long f) {
#if defined(__aarch64__)
    register long x8 __asm__("x8") = number;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    register long x3 __asm__("x3") = d;
    register long x4 __asm__("x4") = e;
    register long x5 __asm__("x5") = f;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5) : "memory", "cc");
    return x0;
#else
    long result;
    register long r10 __asm__("r10") = d;
    register long r8 __asm__("r8") = e;
    register long r9 __asm__("r9") = f;
    __asm__ volatile("syscall" : "=a"(result) : "a"(number), "D"(a), "S"(b), "d"(c), "r"(r10), "r"(r8), "r"(r9) : "rcx", "r11", "memory", "cc");
    return result;
#endif
}
#endif

static long finish_long(long result) {
    if (result < 0 && result > -4096) {
        *__errno_location() = (int)-result;
        return -1;
    }
    return result;
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

/* PRoot shortens long host paths with a private per-process binding. A successful
 * filesystem bind can therefore be invisible to a client in another PRoot.
 * Managed containers opt into stable abstract names, including clean environments. */
static int abstract_transport(void) {
    static const char key[] = "DEXTOP_SOCKET_TRANSPORT=";
    for (char **entry = environ; entry != NULL && *entry != NULL; ++entry) {
        size_t i = 0;
        while (key[i] && (*entry)[i] == key[i]) ++i;
        if (!key[i]) {
            const char *v = *entry + i;
            return v[0] == 'a' && v[1] == 'b' && v[2] == 's' && v[3] == 't' &&
                v[4] == 'r' && v[5] == 'a' && v[6] == 'c' && v[7] == 't' && !v[8];
        }
    }
    long fd = raw_syscall3(SYS_openat, -100, (long)"/etc/dextop/socket-transport", 0x80000);
    if (fd < 0) return 0;
    char mode[9] = {0};
    long n = raw_syscall3(SYS_read, fd, (long)mode, sizeof(mode));
    raw_syscall3(SYS_close, fd, 0, 0);
    return n >= 8 && mode[0] == 'a' && mode[1] == 'b' && mode[2] == 's' &&
        mode[3] == 't' && mode[4] == 'r' && mode[5] == 'a' && mode[6] == 'c' &&
        mode[7] == 't' && (n == 8 || mode[8] == '\n' || mode[8] == '\r');
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
    if (twin_length && abstract_transport()) {
        long result = raw_syscall3(SYS_bind, fd, (long)&twin, twin_length);
        if (result == 0 && dextop_socket_marker) dextop_socket_marker(name);
        return finish(result);
    }
    long result = raw_syscall3(SYS_bind, fd, (long)address, length);
    if (twin_length != 0 && (result == -E_ACCES || result == -E_PERM || result == -E_ROFS ||
        (result == -E_ADDRINUSE && dextop_socket_marker_exists && dextop_socket_marker_exists(name)))) {
        result = raw_syscall3(SYS_bind, fd, (long)&twin, twin_length);
        /* Wine checks for a socket or FIFO before it attempts connect().
         * The glibc companion reports our tagged discovery entry as a FIFO;
         * traffic uses the abstract socket. */
        if (result == 0 && dextop_socket_marker) dextop_socket_marker(name);
    }
    return finish(result);
}

/*
 * Dextop's faked (linux_faked_loopback.c) listens on 127.0.0.1, which every app on the device
 * can reach, and serves only clients that first send its token. It publishes
 * "<32 hex>\n<pid>\n" in /tmp/.dextop-faked/<port>, readable by the container's user only.
 * Returns 1 and fills token when [address] is such a daemon (the recorded pid is still a
 * faked), so no other service on a reused port ever receives the bytes.
 */
static int faked_token(const void *address, unsigned length, char token[32]) {
    const unsigned char *in = address;
    if (!address || length < 16 || *(const unsigned short *)address != 2 /* AF_INET */ ||
        in[4] != 127 || in[5] != 0 || in[6] != 0 || in[7] != 1) return 0;
    unsigned port = (unsigned)in[2] << 8 | in[3];
    char path[48], digits[8];
    static const char dir[] = "/tmp/.dextop-faked/";
    unsigned at = 0, n = 0;
    for (; dir[at]; ++at) path[at] = dir[at];
    do { digits[n++] = (char)('0' + port % 10); port /= 10; } while (port);
    while (n) path[at++] = digits[--n];
    path[at] = 0;
    long file = raw_syscall5(SYS_openat, -100, (long)path, 02000000 /* O_CLOEXEC */, 0, 0);
    if (file < 0) return 0;
    char body[64];
    long got = raw_syscall3(SYS_read, file, (long)body, sizeof body - 1);
    raw_syscall3(SYS_close, file, 0, 0);
    if (got < 34 || body[32] != '\n') return 0;
    for (unsigned i = 0; i < 32; ++i) {
        char c = body[i];
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return 0;
        token[i] = c;
    }
    /* /proc/<pid>/comm of the daemon that wrote it: Dextop runs faked as
     * /run/dextop-faked-original ("dextop-faked-or"); a distro faked is "faked-tcp". */
    char comm[48], name[16];
    static const char proc[] = "/proc/";
    at = 0;
    for (; proc[at]; ++at) comm[at] = proc[at];
    for (long i = 33; i < got && body[i] != '\n' && at < 30; ++i) {
        if (body[i] < '0' || body[i] > '9') return 0;
        comm[at++] = body[i];
    }
    static const char tail[] = "/comm";
    for (unsigned i = 0; tail[i]; ++i) comm[at++] = tail[i];
    comm[at] = 0;
    file = raw_syscall5(SYS_openat, -100, (long)comm, 02000000, 0, 0);
    if (file < 0) return 0;
    got = raw_syscall3(SYS_read, file, (long)name, sizeof name);
    raw_syscall3(SYS_close, file, 0, 0);
    static const char dextop[] = "dextop-faked", plain[] = "faked";
    int is_dextop = got >= (long)sizeof dextop - 1, is_plain = got >= (long)sizeof plain - 1;
    for (unsigned i = 0; i < sizeof dextop - 1 && is_dextop; ++i) is_dextop = name[i] == dextop[i];
    for (unsigned i = 0; i < sizeof plain - 1 && is_plain; ++i) is_plain = name[i] == plain[i];
    return is_dextop || is_plain;
}

__attribute__((visibility("default")))
int connect(int fd, const void *address, unsigned length) {
    char token[32];
    if (faked_token(address, length, token)) {
        long result = raw_syscall3(SYS_connect, fd, (long)address, length);
        /* libfakeroot connects blocking; its first bytes must be the token. */
        if (result == 0 && raw_syscall6(SYS_sendto, fd, (long)token, sizeof token, 0x4000 /* MSG_NOSIGNAL */, 0, 0) != (long)sizeof token)
            result = -E_CONNREFUSED;
        return finish(result);
    }
    struct sockaddr_un_compat twin;
    unsigned twin_length = abstract_twin(address, length, &twin);
    if (twin_length && abstract_transport()) {
        long result = raw_syscall3(SYS_connect, fd, (long)&twin, twin_length);
        if (result == 0 || result == -115 /* EINPROGRESS */) return finish(result);
        /* An older daemon can still own a real filesystem socket during upgrade. */
    }
    long result = raw_syscall3(SYS_connect, fd, (long)address, length);
    if (twin_length != 0 && (result == -E_NOENT || result == -E_CONNREFUSED || result == -E_ACCES)) {
        long retry = raw_syscall3(SYS_connect, fd, (long)&twin, twin_length);
        if (retry == 0 || retry == -115 /* EINPROGRESS */) result = retry;
    }
    return finish(result);
}

/*
 * Android's SELinux refuses the kernel audit socket (EACCES). Linux-PAM and its helpers
 * (unix_chkpwd, passwd) take only the errors of a kernel built without audit as "no audit"
 * and fail every non-root authentication on any other error; report that case instead.
 */
__attribute__((visibility("default")))
int socket(int domain, int type, int protocol) {
    long result = raw_syscall3(SYS_socket, domain, type, protocol);
    if (result == -E_ACCES && domain == AF_NETLINK && protocol == NETLINK_AUDIT) result = -E_PROTONOSUPPORT;
    return finish(result);
}

/*
 * PRoot rewrites a tracee's system calls, and since Linux 4.8 a seccomp filter is
 * re-checked after the tracer's change: a guest program's own filter then kills it
 * with SIGSYS (OpenSSH's preauth sandbox ends every connection during key exchange,
 * Firefox's sandboxes crash on Linux 6.12). Report filters as unsupported, the error
 * of a kernel without CONFIG_SECCOMP_FILTER, so programs take their unsandboxed path.
 */
__attribute__((visibility("default")))
int prctl(int option, unsigned long a2, unsigned long a3, unsigned long a4, unsigned long a5) {
    if (option == PR_SET_SECCOMP_OPTION && a2 == SECCOMP_MODE_FILTER_VALUE) return finish(-E_INVAL);
    return finish(raw_syscall5(SYS_prctl, option, (long)a2, (long)a3, (long)a4, (long)a5));
}

/*
 * Android's devpts refuses chown and chmod of an app's own pseudo-terminal. OpenSSH
 * gives a pty to group tty, mode 0620, and aborts every interactive login when that
 * fails, except with EROFS while the terminal is already private (it is: 0600, the
 * user's own). Report that case for /dev/pts only; every other path is unchanged.
 */
static int pts_path(const char *path) {
    static const char prefix[] = "/dev/pts/";
    for (size_t i = 0; prefix[i] != '\0'; ++i) if (path == NULL || path[i] != prefix[i]) return 0;
    return 1;
}

static long pts_error(const char *path, long result) {
    return pts_path(path) && (result == -E_ACCES || result == -E_PERM) ? -E_ROFS_DEVICE : result;
}

__attribute__((visibility("default")))
int chown(const char *path, unsigned owner, unsigned group) {
    return finish(pts_error(path, raw_syscall5(SYS_fchownat, -100, (long)path, owner, group, 0)));
}

__attribute__((visibility("default")))
int chmod(const char *path, unsigned mode) {
    return finish(pts_error(path, raw_syscall5(SYS_fchmodat, -100, (long)path, mode, 0, 0)));
}

#if !defined(__i386__)
struct msghdr_compat {
    void *name;
    unsigned namelen;
    void *iov;
    unsigned long iovlen;
    void *control;
    unsigned long controllen;
    int flags;
};

static int retry_with_twin(long result) {
    return result == -E_NOENT || result == -E_CONNREFUSED || result == -E_ACCES;
}

__attribute__((visibility("default")))
long sendto(int fd, const void *buffer, unsigned long length, int flags, const void *address, unsigned address_length) {
    struct sockaddr_un_compat twin;
    unsigned twin_length = address != 0 ? abstract_twin(address, address_length, &twin) : 0;
    if (twin_length && abstract_transport()) {
        long result = raw_syscall6(SYS_sendto, fd, (long)buffer, (long)length, flags, (long)&twin, twin_length);
        if (!retry_with_twin(result)) return finish_long(result);
    }
    long result = raw_syscall6(SYS_sendto, fd, (long)buffer, (long)length, flags, (long)address, address_length);
    if (twin_length && retry_with_twin(result)) {
        long retry = raw_syscall6(SYS_sendto, fd, (long)buffer, (long)length, flags, (long)&twin, twin_length);
        if (retry >= 0) result = retry;
    }
    return finish_long(result);
}

__attribute__((visibility("default")))
long sendmsg(int fd, const struct msghdr_compat *message, int flags) {
    struct sockaddr_un_compat twin;
    unsigned twin_length = message != 0 && message->name != 0 ? abstract_twin(message->name, message->namelen, &twin) : 0;
    if (!twin_length) return finish_long(raw_syscall3(SYS_sendmsg, fd, (long)message, flags));
    struct msghdr_compat copy;
    copy.name = &twin;
    copy.namelen = twin_length;
    copy.iov = message->iov;
    copy.iovlen = message->iovlen;
    copy.control = message->control;
    copy.controllen = message->controllen;
    copy.flags = message->flags;
    if (abstract_transport()) {
        long result = raw_syscall3(SYS_sendmsg, fd, (long)&copy, flags);
        if (!retry_with_twin(result)) return finish_long(result);
    }
    long result = raw_syscall3(SYS_sendmsg, fd, (long)message, flags);
    if (retry_with_twin(result)) {
        long retry = raw_syscall3(SYS_sendmsg, fd, (long)&copy, flags);
        if (retry >= 0) result = retry;
    }
    return finish_long(result);
}
#endif
