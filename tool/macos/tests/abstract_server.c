/* Linux baseline fixture: give darlingserver the abstract endpoint used on
 * Android, so real Mach-O exec/RPC can be verified without Android SELinux.
 * This fixture is never installed in the application/runtime payload.
 */
#define bind dextop_test_unused_bind
#define connect dextop_test_unused_connect
#include "../../../android/app/src/main/cpp/linux_unixsock.c"
#undef bind
#undef connect
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <dlfcn.h>
#include <errno.h>

static ssize_t (*next_readlink)(const char *, char *, size_t);
static int deny_namespace;
extern char *program_invocation_short_name;
__attribute__((constructor)) static void resolve_readlink(void) {
    next_readlink = dlsym(RTLD_NEXT, "readlink");
    const char *deny = getenv("DEXTOP_TEST_DENY_PID_NAMESPACE");
    /* Scope this fixture to the launcher whose shutdown path is under test. */
    deny_namespace = deny && !strcmp(deny, "1") &&
        program_invocation_short_name && !strcmp(program_invocation_short_name, "darling");
}

ssize_t readlink(const char *path, char *buffer, size_t size) {
    if (deny_namespace && !strncmp(path, "/proc/", 6) && strstr(path, "/ns/pid")) {
        errno = EACCES;
        return -1;
    }
    if (!next_readlink) { errno = ENOSYS; return -1; }
    return next_readlink(path, buffer, size);
}

int bind(int fd, const void *address, unsigned length) {
    const struct sockaddr_un_compat *in = address;
    static const char suffix[] = "/.darlingserver.sock";
    if (address && length > offsetof(struct sockaddr_un_compat, sun_path) && in->sun_family == AF_UNIX) {
        size_t count = strnlen(in->sun_path, sizeof(in->sun_path));
        if (count >= sizeof(suffix) - 1 &&
            !memcmp(in->sun_path + count - (sizeof(suffix) - 1), suffix, sizeof(suffix) - 1)) {
            struct sockaddr_un_compat twin;
            unsigned mapped = abstract_twin(address, length, &twin);
            if (mapped) return finish(raw_syscall3(SYS_bind, fd, (long)&twin, mapped));
        }
    }
    return finish(raw_syscall3(SYS_bind, fd, (long)address, length));
}
