#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/dma-buf.h>
#include <linux/dma-heap.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <sys/time.h>

int guest_dmabuf_send_fds(int fd, int fence_fd) {
    int code = 3;
    int socket_fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (socket_fd < 0) return code;
    struct timeval timeout = {.tv_sec = 15};
    if (setsockopt(socket_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout))) {
        close(socket_fd); return code;
    }
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    const char *name = "dextop-gpu-import-guest-probe";
    memcpy(address.sun_path + 1, name, strlen(name));
    if (connect(socket_fd, (struct sockaddr *)&address, sizeof(sa_family_t) + 1 + strlen(name))) {
        perror("connect"); close(socket_fd); return code;
    }
    char data = 1, control[CMSG_SPACE(2 * sizeof(int))] = {0};
    int descriptors[2] = {fd, fence_fd};
    unsigned fd_count = fence_fd >= 0 ? 2 : 1;
    struct iovec iov = {.iov_base = &data, .iov_len = 1};
    struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1,
        .msg_control = control, .msg_controllen = CMSG_SPACE(fd_count * sizeof(int))};
    struct cmsghdr *header = CMSG_FIRSTHDR(&message);
    header->cmsg_level = SOL_SOCKET; header->cmsg_type = SCM_RIGHTS;
    header->cmsg_len = CMSG_LEN(fd_count * sizeof(int));
    memcpy(CMSG_DATA(header), descriptors, fd_count * sizeof(int));
    if (sendmsg(socket_fd, &message, 0) == 1 && read(socket_fd, &data, 1) == 1 && data == 1) {
        puts("GUEST_DMABUF_SEND_GPU_ACK_OK"); code = 0;
    }
    close(socket_fd);
    return code;
}

#ifndef DEXTOP_GUEST_GPU_PROBE
int main(void) {
    int heap = open("/dev/dma_heap/system", O_RDONLY | O_CLOEXEC);
    if (heap < 0) { perror("heap"); return 1; }
    struct dma_heap_allocation_data allocation = {.len = 65536, .fd_flags = O_RDWR | O_CLOEXEC};
    if (ioctl(heap, DMA_HEAP_IOCTL_ALLOC, &allocation)) { perror("alloc"); close(heap); return 2; }
    close(heap);
    int fd = allocation.fd, code = 3;
    uint32_t *map = mmap(NULL, allocation.len, PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
    if (map == MAP_FAILED) { close(fd); return code; }
    struct dma_buf_sync sync = {.flags = DMA_BUF_SYNC_START | DMA_BUF_SYNC_WRITE};
    if (ioctl(fd, DMA_BUF_IOCTL_SYNC, &sync)) goto done;
    for (unsigned i = 0; i < 4096; i++) map[i] = 0xffff0000u;
    sync.flags = DMA_BUF_SYNC_END | DMA_BUF_SYNC_WRITE;
    if (ioctl(fd, DMA_BUF_IOCTL_SYNC, &sync)) goto done;
    code = guest_dmabuf_send_fds(fd, -1);
done:
    munmap(map, allocation.len);
    close(fd);
    return code;
}
#endif
