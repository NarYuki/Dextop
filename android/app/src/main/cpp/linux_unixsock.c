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

#define AF_UNIX 1
#define SYS_bind 200
#define SYS_connect 203

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

static long raw_syscall3(long number, long a, long b, long c) {
    register long x8 __asm__("x8") = number;
    register long x0 __asm__("x0") = a;
    register long x1 __asm__("x1") = b;
    register long x2 __asm__("x2") = c;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2) : "memory", "cc");
    return x0;
}

static int finish(long result) {
    if (result < 0 && result > -4096) {
        *__errno_location() = (int)-result;
        return -1;
    }
    return (int)result;
}

static const char *namespace_prefix(void) {
    static const char key[] = "DEXTOP_SOCKET_NS=";
    if (environ == NULL) return NULL;
    for (char **entry = environ; *entry != NULL; ++entry) {
        const char *value = *entry;
        size_t i = 0;
        while (key[i] != '\0' && value[i] == key[i]) ++i;
        if (key[i] == '\0') return value + i;
    }
    return NULL;
}

/* Builds the abstract twin of a path socket address; returns its length or 0. */
static unsigned abstract_twin(const void *address, unsigned length, struct sockaddr_un_compat *out) {
    const struct sockaddr_un_compat *in = address;
    if (address == NULL || length <= offsetof(struct sockaddr_un_compat, sun_path) + 1) return 0;
    if (in->sun_family != AF_UNIX || in->sun_path[0] == '\0') return 0;
    const char *prefix = namespace_prefix();
    if (prefix == NULL || prefix[0] == '\0') return 0;
    size_t used = 1;
    out->sun_family = AF_UNIX;
    out->sun_path[0] = '\0';
    for (size_t i = 0; prefix[i] != '\0'; ++i) {
        if (used >= sizeof(out->sun_path)) return 0;
        out->sun_path[used++] = prefix[i];
    }
    size_t limit = length - offsetof(struct sockaddr_un_compat, sun_path);
    for (size_t i = 0; i < limit && i < sizeof(in->sun_path) && in->sun_path[i] != '\0'; ++i) {
        if (used >= sizeof(out->sun_path)) return 0;
        out->sun_path[used++] = in->sun_path[i];
    }
    return (unsigned)(offsetof(struct sockaddr_un_compat, sun_path) + used);
}

__attribute__((visibility("default")))
int bind(int fd, const void *address, unsigned length) {
    /* proot rewrites the caller's address to the host path during the call,
     * so the abstract twin is made from the guest path beforehand. */
    struct sockaddr_un_compat twin;
    unsigned twin_length = abstract_twin(address, length, &twin);
    long result = raw_syscall3(SYS_bind, fd, (long)address, length);
    if (twin_length != 0 && (result == -E_ACCES || result == -E_PERM || result == -E_ROFS)) {
        result = raw_syscall3(SYS_bind, fd, (long)&twin, twin_length);
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
