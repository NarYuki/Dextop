/* A glibc proxy for the small AHardwareBuffer API used by Mesa's Wayland WSI.
 * Allocation and Android handle serialization stay in the Android process.
 * No Android/bionic libraries are loaded into a glibc process. */
#define _GNU_SOURCE
#include "wayland/deck_ahb_wire.h"
#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
struct native_handle { int version, num_fds, num_ints, data[DECK_AHB_MAX_FDS + DECK_AHB_MAX_INTS]; };
struct AHardwareBuffer {
    struct deck_ahb_desc desc;
    struct native_handle handle;
    pthread_mutex_t lock;
    int socket;
};
int AHardwareBuffer_allocate(const struct deck_ahb_desc *desc, struct AHardwareBuffer **out) {
    *out = NULL;
    const char *ns = getenv("DEXTOP_SOCKET_NS");
    if (!ns || !desc) return -EINVAL;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    const char suffix[] = "/tmp/runtime-root/wayland-0.ahb";
    size_t length = strlen(ns) + sizeof(suffix) - 1;
    if (length + 1 > sizeof(address.sun_path)) return -EINVAL;
    memcpy(address.sun_path + 1, ns, strlen(ns));
    memcpy(address.sun_path + 1 + strlen(ns), suffix, sizeof(suffix)-1);
    int fd = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    if (fd < 0) return -errno;
    struct AHardwareBuffer *b = calloc(1, sizeof(*b));
    if (!b) { close(fd); return -ENOMEM; }
    struct timeval timeout = {.tv_sec = 5};
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    if (connect(fd, (void *)&address, offsetof(struct sockaddr_un, sun_path) + 1 + length)) goto fail;
    struct deck_ahb_request request = {.version = DECK_AHB_WIRE_VERSION, .operation = 1, .desc = *desc};
    if (send(fd, &request, sizeof(request), MSG_NOSIGNAL) != sizeof(request)) goto fail;
    struct deck_ahb_reply reply;
    char ancillary[CMSG_SPACE(DECK_AHB_MAX_FDS*sizeof(int))] = {0};
    struct iovec iov = {&reply, sizeof(reply)};
    struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1,
        .msg_control = ancillary, .msg_controllen = sizeof(ancillary)};
    ssize_t n = recvmsg(fd, &msg, MSG_CMSG_CLOEXEC);
    int received[DECK_AHB_MAX_FDS], count = 0;
    for (struct cmsghdr *c = CMSG_FIRSTHDR(&msg); c; c = CMSG_NXTHDR(&msg,c)) {
        if (c->cmsg_level != SOL_SOCKET || c->cmsg_type != SCM_RIGHTS) continue;
        size_t bytes = c->cmsg_len - CMSG_LEN(0);
        if (bytes > sizeof(received) - count*sizeof(int)) goto fail;
        memcpy(received+count, CMSG_DATA(c), bytes); count += bytes/sizeof(int);
    }
    if (n != sizeof(reply) || (msg.msg_flags & (MSG_TRUNC|MSG_CTRUNC)) || reply.status ||
        reply.num_fds != count || count < 1 || reply.num_ints < 0 || reply.num_ints > DECK_AHB_MAX_INTS) {
        for (int i=0;i<count;i++) close(received[i]);
        goto fail;
    }
    b->desc = reply.desc;
    b->handle.version = 3*sizeof(int); b->handle.num_fds = count; b->handle.num_ints = reply.num_ints;
    memcpy(b->handle.data, received, count*sizeof(int));
    memcpy(b->handle.data+count, reply.ints, reply.num_ints*sizeof(int));
    pthread_mutex_init(&b->lock, NULL); b->socket = fd; *out = b;
    return 0;
fail:
    close(fd); free(b); return -EIO;
}
void AHardwareBuffer_release(struct AHardwareBuffer *b) {
    if (!b) return;
    close(b->socket);
    for (int i=0;i<b->handle.num_fds;i++) close(b->handle.data[i]);
    pthread_mutex_destroy(&b->lock); free(b);
}
void AHardwareBuffer_describe(const struct AHardwareBuffer *b, struct deck_ahb_desc *desc) { *desc=b->desc; }
const void *AHardwareBuffer_getNativeHandle(const struct AHardwareBuffer *b) { return &b->handle; }
int AHardwareBuffer_sendHandleToUnixSocket(const struct AHardwareBuffer *buffer, int target) {
    struct AHardwareBuffer *b = (void *)buffer;
    char byte=1, ancillary[CMSG_SPACE(sizeof(int))] = {0};
    struct iovec iov = {&byte,1};
    struct msghdr msg = {.msg_iov=&iov,.msg_iovlen=1,.msg_control=ancillary,.msg_controllen=sizeof(ancillary)};
    struct cmsghdr *c=CMSG_FIRSTHDR(&msg);
    c->cmsg_level=SOL_SOCKET; c->cmsg_type=SCM_RIGHTS; c->cmsg_len=CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(c),&target,sizeof(target));
    pthread_mutex_lock(&b->lock);
    int status=-EIO;
    if (sendmsg(b->socket,&msg,MSG_NOSIGNAL)==1 && recv(b->socket,&status,sizeof(status),0)!=sizeof(status)) status=-EIO;
    pthread_mutex_unlock(&b->lock);
    return status;
}
