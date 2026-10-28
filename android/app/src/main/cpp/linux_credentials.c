/* fakeroot changes libc's uid/gid, but SCM_CREDENTIALS must describe the real
 * kernel identity. Retry rejected credentials without impersonating a user. */
#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <dlfcn.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>

ssize_t sendmsg(int fd, const struct msghdr *message, int flags) {
    static ssize_t (*next)(int, const struct msghdr *, int);
    if (!next) next = dlsym(RTLD_NEXT, "sendmsg");
    ssize_t result = next(fd, message, flags);
    if (result >= 0 || errno != EPERM || !message || !message->msg_control ||
        message->msg_controllen > 65536) return result;
    int saved = errno;
    void *control = malloc(message->msg_controllen);
    if (!control) { errno = saved; return result; }
    memcpy(control, message->msg_control, message->msg_controllen);
    struct msghdr retry = *message;
    retry.msg_control = control;
    int changed = 0;
    struct ucred actual = { .pid = syscall(SYS_getpid),
        .uid = syscall(SYS_getuid), .gid = syscall(SYS_getgid) };
    for (struct cmsghdr *c = CMSG_FIRSTHDR(&retry); c; c = CMSG_NXTHDR(&retry, c)) {
        if (c->cmsg_level != SOL_SOCKET || c->cmsg_type != SCM_CREDENTIALS ||
            c->cmsg_len != CMSG_LEN(sizeof(struct ucred))) continue;
        struct ucred *claimed = (struct ucred *)CMSG_DATA(c);
        if (memcmp(claimed, &actual, sizeof(actual))) {
            memcpy(claimed, &actual, sizeof(actual)); changed = 1;
        }
    }
    errno = saved;
    if (changed) result = next(fd, &retry, flags);
    saved = errno;
    free(control);
    errno = saved;
    return result;
}
