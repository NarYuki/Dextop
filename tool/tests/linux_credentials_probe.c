#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <assert.h>
int main(void) {
    int pair[2], enabled = 1;
    assert(!socketpair(AF_UNIX, SOCK_STREAM, 0, pair));
    assert(!setsockopt(pair[1], SOL_SOCKET, SO_PASSCRED, &enabled, sizeof(enabled)));
    char data = 'x', control[CMSG_SPACE(sizeof(struct ucred))] = {0};
    struct iovec iov = { &data, 1 };
    struct msghdr message = { .msg_iov = &iov, .msg_iovlen = 1,
        .msg_control = control, .msg_controllen = sizeof(control) };
    struct cmsghdr *c = CMSG_FIRSTHDR(&message);
    c->cmsg_level = SOL_SOCKET; c->cmsg_type = SCM_CREDENTIALS;
    c->cmsg_len = CMSG_LEN(sizeof(struct ucred));
    struct ucred spoofed = { .pid = getpid(), .uid = 12345, .gid = 12345 };
    memcpy(CMSG_DATA(c), &spoofed, sizeof(spoofed));
    assert(sendmsg(pair[0], &message, 0) == 1);
    assert(!memcmp(CMSG_DATA(c), &spoofed, sizeof(spoofed)));
    memset(control, 0, sizeof(control));
    assert(recvmsg(pair[1], &message, 0) == 1);
    struct ucred *actual = (struct ucred *)CMSG_DATA(CMSG_FIRSTHDR(&message));
    assert(actual->uid == syscall(SYS_getuid) && actual->gid == syscall(SYS_getgid));
    assert(actual->pid == getpid());
    puts("PASS: rejected fake credentials retried as kernel identity; caller buffer unchanged");
    close(pair[0]); close(pair[1]);
}
