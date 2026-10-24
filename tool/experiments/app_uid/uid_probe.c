#include <arpa/inet.h>
#include <errno.h>
#include <netinet/ip_icmp.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

static unsigned short checksum(const void *data, size_t length) {
    const unsigned short *p = data;
    unsigned sum = 0;
    while (length >= 2) { sum += *p++; length -= 2; }
    if (length) sum += *(const unsigned char *)p;
    while (sum >> 16) sum = (sum & 0xffff) + (sum >> 16);
    return (unsigned short)~sum;
}

int main(void) {
    int bits = prctl(PR_GET_SECUREBITS, 0, 0, 0, 0);
    int nnp = prctl(PR_GET_NO_NEW_PRIVS, 0, 0, 0, 0);
    int keep = prctl(PR_GET_KEEPCAPS, 0, 0, 0, 0);
    errno = 0;
    int changed = prctl(PR_SET_KEEPCAPS, 1, 0, 0, 0);
    int keep_error = errno;
    if (changed == 0 && keep >= 0) prctl(PR_SET_KEEPCAPS, keep, 0, 0, 0);
    errno = 0;
    int fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
    int socket_error = fd < 0 ? errno : 0;
    int echo_error = 0, echo_bytes = -1;
    if (fd >= 0) {
        struct timeval timeout = { .tv_sec = 1 };
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        struct sockaddr_in address = { .sin_family = AF_INET };
        inet_pton(AF_INET, "127.0.0.1", &address.sin_addr);
        struct icmphdr packet = { .type = ICMP_ECHO, .un.echo.sequence = htons(1) };
        packet.checksum = checksum(&packet, sizeof(packet));
        if (sendto(fd, &packet, sizeof(packet), 0, (void *)&address, sizeof(address)) < 0) echo_error = errno;
        else {
            unsigned char reply[256];
            echo_bytes = recv(fd, reply, sizeof(reply), 0);
            if (echo_bytes < 0) echo_error = errno;
            else if (reply[0] != ICMP_ECHOREPLY) echo_error = EPROTO;
        }
        close(fd);
    }
    printf("{\"uid\":%u,\"gid\":%u,\"securebits\":%d,\"no_new_privs\":%d,"
           "\"keepcaps_result\":%d,\"keepcaps_errno\":%d,\"icmp_socket_errno\":%d,"
           "\"echo_bytes\":%d,\"echo_errno\":%d}\n", getuid(), getgid(), bits, nnp,
           changed, keep_error, socket_error, echo_bytes, echo_error);
    return 0;
}
