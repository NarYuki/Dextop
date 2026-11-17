/*
 * rtnetlink for Linux in Dextop's Android execution mode.
 *
 * Android refuses RTM_GETLINK and RTM_GETNEIGH (and bind) on NETLINK_ROUTE sockets to apps, while
 * addresses, routes and rules stay readable. `ip addr`, `ip link`, `ip route`, glibc's
 * getifaddrs/if_nameindex users and psutil's interface lists all ask for links first and stop.
 *
 * When such a request is refused, the socket's descriptor is quietly replaced by one end of a
 * datagram socket pair. Link requests are answered from what apps may read (the address dump,
 * interface ioctls and Dextop's /proc/net/dev stand-in); neighbour dumps are empty; every other
 * request still goes to the kernel through the original socket, kept aside, and its answers are
 * passed back unchanged except for the port id the program saw. Nothing changes for sockets the
 * kernel serves.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
/* glibc's net/if.h first: linux/if.h then leaves its definitions alone. */
#include <net/if.h>
#include <linux/if_arp.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <poll.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

#define SLOTS 1024
struct emulated { int hidden; int peer; uint32_t port; };
static struct emulated *table[SLOTS];
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

#define REAL(name, type) static type real_##name; if (!real_##name) real_##name = (type) dlsym(RTLD_NEXT, #name)
typedef ssize_t (*sendto_fn)(int, const void *, size_t, int, const struct sockaddr *, socklen_t);
typedef ssize_t (*send_fn)(int, const void *, size_t, int);
typedef ssize_t (*sendmsg_fn)(int, const struct msghdr *, int);
typedef ssize_t (*recvmsg_fn)(int, struct msghdr *, int);
typedef ssize_t (*recvfrom_fn)(int, void *, size_t, int, struct sockaddr *, socklen_t *);
typedef int (*getsockname_fn)(int, struct sockaddr *, socklen_t *);
typedef int (*setsockopt_fn)(int, int, int, const void *, socklen_t);
typedef int (*close_fn)(int);

static struct nlmsghdr *message_at(char *base, size_t total, size_t *offset) {
    if (*offset > total || total - *offset < sizeof(struct nlmsghdr)) return NULL;
    struct nlmsghdr *header = (struct nlmsghdr *) (base + *offset);
    size_t length = header->nlmsg_len;
    if (length < sizeof(struct nlmsghdr) || length > total - *offset) return NULL;
    size_t step = NLMSG_ALIGN(length);
    *offset += step < total - *offset ? step : total - *offset;
    return header;
}

static struct emulated *lookup(int fd) { return fd >= 0 && fd < SLOTS ? table[fd] : NULL; }

static int route_socket(int fd) {
    int domain = 0, protocol = -1;
    socklen_t length = sizeof(int);
    if (getsockopt(fd, SOL_SOCKET, SO_DOMAIN, &domain, &length) != 0 || domain != AF_NETLINK) return 0;
    length = sizeof(int);
    return getsockopt(fd, SOL_SOCKET, SO_PROTOCOL, &protocol, &length) == 0 && protocol == NETLINK_ROUTE;
}

static uint32_t port_of(int fd) {
    REAL(getsockname, getsockname_fn);
    struct sockaddr_nl address = {0};
    socklen_t length = sizeof(address);
    return real_getsockname(fd, (struct sockaddr *) &address, &length) == 0 ? address.nl_pid : 0;
}

/* Puts a socket pair in [fd]'s place; the kernel socket stays open aside. */
static struct emulated *convert(int fd) {
    if (fd < 0 || fd >= SLOTS) return NULL;
    pthread_mutex_lock(&lock);
    struct emulated *state = table[fd];
    if (state != NULL) { pthread_mutex_unlock(&lock); return state; }
    int pair[2];
    int status = fcntl(fd, F_GETFL), descriptor = fcntl(fd, F_GETFD);
    /* The port id the program knows (getsockname below makes it the kernel's own). */
    uint32_t port = port_of(fd);
    if (socketpair(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0, pair) != 0) { pthread_mutex_unlock(&lock); return NULL; }
    int hidden = fcntl(fd, F_DUPFD_CLOEXEC, 64);
    if (hidden < 0 || dup3(pair[0], fd, (descriptor & FD_CLOEXEC) ? O_CLOEXEC : 0) < 0) {
        if (hidden >= 0) close(hidden);
        close(pair[0]); close(pair[1]);
        pthread_mutex_unlock(&lock);
        return NULL;
    }
    close(pair[0]);
    if (status >= 0) fcntl(fd, F_SETFL, status & O_NONBLOCK);
    int size = 1 << 20;
    setsockopt(pair[1], SOL_SOCKET, SO_SNDBUF, &size, sizeof(size));
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
    state = calloc(1, sizeof(*state));
    if (state == NULL) { pthread_mutex_unlock(&lock); return NULL; }
    state->hidden = hidden;
    state->peer = pair[1];
    state->port = port;
    table[fd] = state;
    pthread_mutex_unlock(&lock);
    return state;
}

/* A reply under construction. */
struct buffer { char *data; size_t length, room; };

static void *grow(struct buffer *out, size_t extra) {
    size_t aligned = NLMSG_ALIGN(extra);
    if (out->length + aligned > out->room) {
        size_t room = out->room ? out->room * 2 : 8192;
        while (room < out->length + aligned) room *= 2;
        char *data = realloc(out->data, room);
        if (data == NULL) return NULL;
        out->data = data;
        out->room = room;
    }
    void *at = out->data + out->length;
    memset(at, 0, aligned);
    out->length += aligned;
    return at;
}

static void attribute(struct buffer *out, struct nlmsghdr **header, unsigned short type, const void *value, size_t size) {
    size_t offset = (char *) *header - out->data;
    struct rtattr *rta = grow(out, RTA_LENGTH(size));
    *header = (struct nlmsghdr *) (out->data + offset);
    if (rta == NULL) return;
    rta->rta_type = type;
    rta->rta_len = RTA_LENGTH(size);
    memcpy(RTA_DATA(rta), value, size);
    (*header)->nlmsg_len = out->data + out->length - (char *) *header;
}

static int send_to_program(struct emulated *state, const void *data, size_t length) {
    REAL(send, send_fn);
    return real_send(state->peer, data, length, MSG_NOSIGNAL) == (ssize_t) length ? 0 : -1;
}

static void done(struct emulated *state, const struct nlmsghdr *request, int error) {
    struct { struct nlmsghdr header; struct nlmsgerr body; } reply;
    memset(&reply, 0, sizeof(reply));
    if (error == 0 && (request->nlmsg_flags & NLM_F_DUMP) == NLM_F_DUMP) {
        reply.header.nlmsg_len = NLMSG_LENGTH(sizeof(int));
        reply.header.nlmsg_type = NLMSG_DONE;
        reply.header.nlmsg_flags = NLM_F_MULTI;
    } else {
        reply.header.nlmsg_len = NLMSG_LENGTH(sizeof(struct nlmsgerr));
        reply.header.nlmsg_type = NLMSG_ERROR;
        reply.body.error = -error;
        reply.body.msg = *request;
    }
    reply.header.nlmsg_seq = request->nlmsg_seq;
    reply.header.nlmsg_pid = state->port;
    send_to_program(state, &reply, reply.header.nlmsg_len);
}

/* Interface indexes from the address dump (allowed), names from SIOCGIFNAME. */
static int indexes(int hidden, int *out, int room) {
    REAL(sendto, sendto_fn);
    REAL(recvfrom, recvfrom_fn);
    struct { struct nlmsghdr header; struct ifaddrmsg body; } request = {0};
    request.header.nlmsg_len = sizeof(request);
    request.header.nlmsg_type = RTM_GETADDR;
    request.header.nlmsg_flags = NLM_F_REQUEST | NLM_F_DUMP;
    request.header.nlmsg_seq = 0x44585450;
    struct sockaddr_nl kernel = { .nl_family = AF_NETLINK };
    int count = 0;
    if (real_sendto(hidden, &request, sizeof(request), 0, (struct sockaddr *) &kernel, sizeof(kernel)) < 0) return 0;
    char data[32768];
    for (;;) {
        struct pollfd ready = { hidden, POLLIN, 0 };
        if (poll(&ready, 1, 2000) <= 0) break;
        ssize_t n = real_recvfrom(hidden, data, sizeof(data), 0, NULL, NULL);
        if (n <= 0) break;
        int finished = 0;
        size_t offset = 0;
        for (struct nlmsghdr *h; (h = message_at(data, (size_t) n, &offset)) != NULL;) {
            if (h->nlmsg_seq != request.header.nlmsg_seq) continue;
            if (h->nlmsg_type == NLMSG_DONE || h->nlmsg_type == NLMSG_ERROR) { finished = 1; break; }
            if (h->nlmsg_type != RTM_NEWADDR) continue;
            int index = (int) ((struct ifaddrmsg *) NLMSG_DATA(h))->ifa_index, known = 0;
            for (int i = 0; i < count; i++) known |= out[i] == index;
            if (!known && count < room) out[count++] = index;
        }
        if (finished) break;
    }
    if (count == 0) out[count++] = 1;
    /* Keep kernel order, like the real dump. */
    for (int i = 1; i < count; i++) for (int j = i; j > 0 && out[j - 1] > out[j]; j--) { int t = out[j]; out[j] = out[j - 1]; out[j - 1] = t; }
    return count;
}

/* Counters for [name] from Dextop's /proc/net/dev stand-in (LinuxSysStandIns). */
static int counters(const char *name, unsigned long long values[16]) {
    FILE *file = fopen("/run/dextop-standins/proc/net_dev", "re");
    if (file == NULL) file = fopen("/proc/net/dev", "re");
    if (file == NULL) return 0;
    char line[512];
    int found = 0;
    while (!found && fgets(line, sizeof(line), file)) {
        char *colon = strchr(line, ':');
        if (colon == NULL) continue;
        *colon = '\0';
        char *start = line;
        while (*start == ' ') start++;
        if (strcmp(start, name) != 0) continue;
        found = sscanf(colon + 1, "%llu %llu %llu %llu %llu %llu %llu %llu %llu %llu %llu %llu %llu %llu %llu %llu",
                       &values[0], &values[1], &values[2], &values[3], &values[4], &values[5], &values[6], &values[7],
                       &values[8], &values[9], &values[10], &values[11], &values[12], &values[13], &values[14], &values[15]) == 16;
    }
    fclose(file);
    return found;
}

static void link_message(struct buffer *out, const struct nlmsghdr *request, uint32_t port, int index, int multi) {
    char name[IF_NAMESIZE] = "";
    struct ifreq ifr;
    int probe = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    memset(&ifr, 0, sizeof(ifr));
    ifr.ifr_ifindex = index;
    if (probe < 0 || ioctl(probe, SIOCGIFNAME, &ifr) != 0) {
        if (probe >= 0) close(probe);
        return;
    }
    strncpy(name, ifr.ifr_name, sizeof(name) - 1);
    unsigned flags = 0, mtu = 1500, queue = 1000;
    unsigned short hardware = ARPHRD_ETHER;
    unsigned char address[6] = {0}, broadcast[6] = {0xff, 0xff, 0xff, 0xff, 0xff, 0xff};
    if (ioctl(probe, SIOCGIFFLAGS, &ifr) == 0) flags = (unsigned short) ifr.ifr_flags;
    if (ioctl(probe, SIOCGIFMTU, &ifr) == 0) mtu = ifr.ifr_mtu;
    if (ioctl(probe, SIOCGIFTXQLEN, &ifr) == 0) queue = ifr.ifr_qlen;
    if (ioctl(probe, SIOCGIFHWADDR, &ifr) == 0) { hardware = ifr.ifr_hwaddr.sa_family; memcpy(address, ifr.ifr_hwaddr.sa_data, 6); }
    close(probe);
    if (flags & IFF_LOOPBACK) { hardware = ARPHRD_LOOPBACK; memset(broadcast, 0, sizeof(broadcast)); }

    struct nlmsghdr *header = grow(out, NLMSG_LENGTH(sizeof(struct ifinfomsg)));
    if (header == NULL) return;
    header->nlmsg_len = NLMSG_LENGTH(sizeof(struct ifinfomsg));
    header->nlmsg_type = RTM_NEWLINK;
    header->nlmsg_flags = multi ? NLM_F_MULTI : 0;
    header->nlmsg_seq = request->nlmsg_seq;
    header->nlmsg_pid = port;
    struct ifinfomsg *info = NLMSG_DATA(header);
    info->ifi_family = AF_UNSPEC;
    info->ifi_type = hardware;
    info->ifi_index = index;
    info->ifi_flags = flags;
    attribute(out, &header, IFLA_IFNAME, name, strlen(name) + 1);
    attribute(out, &header, IFLA_MTU, &mtu, sizeof(mtu));
    attribute(out, &header, IFLA_TXQLEN, &queue, sizeof(queue));
    unsigned char operstate = (flags & IFF_LOOPBACK) ? 0 /* IF_OPER_UNKNOWN */ : (flags & IFF_RUNNING) ? 6 /* UP */ : 2 /* DOWN */;
    unsigned char mode = 0;
    attribute(out, &header, IFLA_OPERSTATE, &operstate, 1);
    attribute(out, &header, IFLA_LINKMODE, &mode, 1);
    attribute(out, &header, IFLA_QDISC, (flags & IFF_LOOPBACK) ? "noqueue" : "mq", (flags & IFF_LOOPBACK) ? 8 : 3);
    attribute(out, &header, IFLA_ADDRESS, address, 6);
    attribute(out, &header, IFLA_BROADCAST, broadcast, 6);
    unsigned long long values[16] = {0};
    if (counters(name, values)) {
        struct rtnl_link_stats64 stats;
        memset(&stats, 0, sizeof(stats));
        stats.rx_bytes = values[0]; stats.rx_packets = values[1]; stats.rx_errors = values[2]; stats.rx_dropped = values[3];
        stats.rx_fifo_errors = values[4]; stats.rx_frame_errors = values[5]; stats.rx_compressed = values[6]; stats.multicast = values[7];
        stats.tx_bytes = values[8]; stats.tx_packets = values[9]; stats.tx_errors = values[10]; stats.tx_dropped = values[11];
        stats.tx_fifo_errors = values[12]; stats.collisions = values[13]; stats.tx_carrier_errors = values[14]; stats.tx_compressed = values[15];
        attribute(out, &header, IFLA_STATS64, &stats, sizeof(stats));
    }
}

/* The name a single-link request asks for (IFLA_IFNAME), if any. */
static const char *requested_name(const struct nlmsghdr *request) {
    if (request->nlmsg_len < NLMSG_LENGTH(sizeof(struct ifinfomsg))) return NULL;
    int remaining = request->nlmsg_len - NLMSG_LENGTH(sizeof(struct ifinfomsg));
    for (struct rtattr *rta = (struct rtattr *) ((char *) NLMSG_DATA(request) + NLMSG_ALIGN(sizeof(struct ifinfomsg)));
         RTA_OK(rta, remaining); rta = RTA_NEXT(rta, remaining)) {
        if (rta->rta_type == IFLA_IFNAME && RTA_PAYLOAD(rta) > 0) return RTA_DATA(rta);
    }
    return NULL;
}

static void answer_links(struct emulated *state, const struct nlmsghdr *request) {
    int list[64], count = indexes(state->hidden, list, 64);
    int dump = (request->nlmsg_flags & NLM_F_DUMP) == NLM_F_DUMP;
    int wanted = 0;
    const char *name = NULL;
    if (!dump && request->nlmsg_len >= NLMSG_LENGTH(sizeof(struct ifinfomsg))) {
        wanted = ((const struct ifinfomsg *) NLMSG_DATA(request))->ifi_index;
        name = requested_name(request);
        if (wanted == 0 && name != NULL) wanted = (int) if_nametoindex(name);
        if (wanted == 0) { done(state, request, ENODEV); return; }
    }
    struct buffer out = {0};
    for (int i = 0; i < count; i++) {
        if (!dump && list[i] != wanted) continue;
        link_message(&out, request, state->port, list[i], dump);
        /* One datagram per batch of links, as the kernel does. */
        if (out.length > 16384) { send_to_program(state, out.data, out.length); out.length = 0; }
    }
    if (!dump && out.length == 0 && wanted > 0) link_message(&out, request, state->port, wanted, 0);
    if (out.length) send_to_program(state, out.data, out.length);
    free(out.data);
    if (dump) done(state, request, 0);
    else if (out.room == 0) done(state, request, ENODEV);
    else if (request->nlmsg_flags & NLM_F_ACK) done(state, request, 0);
}

/* Anything else: to the kernel, and its answer back with the port id the program knows. */
static void relay(struct emulated *state, const struct nlmsghdr *request) {
    REAL(sendto, sendto_fn);
    REAL(recvfrom, recvfrom_fn);
    struct sockaddr_nl kernel = { .nl_family = AF_NETLINK };
    if (real_sendto(state->hidden, request, request->nlmsg_len, 0, (struct sockaddr *) &kernel, sizeof(kernel)) < 0) {
        done(state, request, errno);
        return;
    }
    int dump = (request->nlmsg_flags & NLM_F_DUMP) == NLM_F_DUMP;
    char *data = malloc(65536);
    if (data == NULL) return;
    for (;;) {
        struct pollfd ready = { state->hidden, POLLIN, 0 };
        if (poll(&ready, 1, 5000) <= 0) break;
        ssize_t n = real_recvfrom(state->hidden, data, 65536, 0, NULL, NULL);
        if (n <= 0) break;
        int finished = !dump && !(request->nlmsg_flags & NLM_F_ACK);
        size_t offset = 0;
        for (struct nlmsghdr *h; (h = message_at(data, (size_t) n, &offset)) != NULL;) {
            h->nlmsg_pid = state->port;
            if (h->nlmsg_type == NLMSG_DONE || h->nlmsg_type == NLMSG_ERROR) finished = 1;
        }
        send_to_program(state, data, n);
        if (finished) break;
    }
    free(data);
}

static ssize_t emulate(struct emulated *state, const char *data, size_t length) {
    size_t offset = 0;
    for (const struct nlmsghdr *h; (h = message_at((char *) data, length, &offset)) != NULL;) {
        if (h->nlmsg_type == RTM_GETLINK) answer_links(state, h);
        else if (h->nlmsg_type == RTM_GETNEIGH && (h->nlmsg_flags & NLM_F_DUMP) == NLM_F_DUMP) done(state, h, 0);
        else relay(state, h);
    }
    return (ssize_t) length;
}

/* Whether a refused send on [fd] is one this file answers. */
static int refused(int fd, const void *data, size_t length) {
    if (errno != EACCES && errno != EPERM) return 0;
    if (length < sizeof(struct nlmsghdr) || !route_socket(fd)) return 0;
    unsigned short type = ((const struct nlmsghdr *) data)->nlmsg_type;
    return type == RTM_GETLINK || type == RTM_GETNEIGH;
}

ssize_t sendto(int fd, const void *data, size_t length, int flags, const struct sockaddr *to, socklen_t size) {
    struct emulated *state = lookup(fd);
    if (state) return emulate(state, data, length);
    REAL(sendto, sendto_fn);
    ssize_t result = real_sendto(fd, data, length, flags, to, size);
    if (result >= 0 || !refused(fd, data, length)) return result;
    int saved = errno;
    if ((state = convert(fd)) == NULL) { errno = saved; return result; }
    return emulate(state, data, length);
}

ssize_t send(int fd, const void *data, size_t length, int flags) {
    struct emulated *state = lookup(fd);
    if (state) return emulate(state, data, length);
    REAL(send, send_fn);
    ssize_t result = real_send(fd, data, length, flags);
    if (result >= 0 || !refused(fd, data, length)) return result;
    int saved = errno;
    if ((state = convert(fd)) == NULL) { errno = saved; return result; }
    return emulate(state, data, length);
}

/* The iovecs as one buffer: netlink requests are small. */
static char *gather(const struct msghdr *message, size_t *length) {
    size_t total = 0;
    for (size_t i = 0; i < message->msg_iovlen; i++) total += message->msg_iov[i].iov_len;
    char *data = malloc(total ? total : 1);
    if (data == NULL) return NULL;
    size_t at = 0;
    for (size_t i = 0; i < message->msg_iovlen; i++) {
        memcpy(data + at, message->msg_iov[i].iov_base, message->msg_iov[i].iov_len);
        at += message->msg_iov[i].iov_len;
    }
    *length = total;
    return data;
}

ssize_t sendmsg(int fd, const struct msghdr *message, int flags) {
    struct emulated *state = lookup(fd);
    REAL(sendmsg, sendmsg_fn);
    ssize_t result = state ? -1 : real_sendmsg(fd, message, flags);
    if (!state && result >= 0) return result;
    int saved = errno;
    size_t length = 0;
    char *data = gather(message, &length);
    if (data == NULL) { errno = saved; return result; }
    if (!state && (!refused(fd, data, length) || (state = convert(fd)) == NULL)) { free(data); errno = saved; return result; }
    result = emulate(state, data, length);
    free(data);
    return result;
}

/* Replies come from a socket pair: the program expects the kernel's netlink address. */
static void kernel_address(struct sockaddr *address, socklen_t *size) {
    if (address == NULL || size == NULL) return;
    struct sockaddr_nl kernel = { .nl_family = AF_NETLINK };
    memcpy(address, &kernel, *size < sizeof(kernel) ? *size : sizeof(kernel));
    *size = sizeof(kernel);
}

ssize_t recvmsg(int fd, struct msghdr *message, int flags) {
    REAL(recvmsg, recvmsg_fn);
    struct emulated *state = lookup(fd);
    if (!state) return real_recvmsg(fd, message, flags);
    void *name = message->msg_name;
    socklen_t room = message->msg_namelen;
    message->msg_name = NULL;
    message->msg_namelen = 0;
    ssize_t result = real_recvmsg(fd, message, flags);
    message->msg_name = name;
    message->msg_namelen = room;
    if (result >= 0) kernel_address(name, &message->msg_namelen);
    return result;
}

ssize_t recvfrom(int fd, void *data, size_t length, int flags, struct sockaddr *from, socklen_t *size) {
    REAL(recvfrom, recvfrom_fn);
    struct emulated *state = lookup(fd);
    if (!state) return real_recvfrom(fd, data, length, flags, from, size);
    ssize_t result = real_recvfrom(fd, data, length, flags, NULL, NULL);
    if (result >= 0) kernel_address(from, size);
    return result;
}

/* bind: linux_android_usb.c already accepts a refused netlink bind (an autobind follows). */

/*
 * A refused bind is accepted without binding (linux_android_usb.c), so the socket has no port yet
 * and getsockname says 0; the kernel then autobinds on the first request and stamps its replies
 * with that port, which programs (ip, glibc) compare with what getsockname said and drop. A
 * no-op message, which the kernel neither answers nor needs to allow, autobinds it first.
 */
int getsockname(int fd, struct sockaddr *address, socklen_t *size) {
    struct emulated *state = lookup(fd);
    REAL(getsockname, getsockname_fn);
    if (!state) {
        int result = real_getsockname(fd, address, size);
        if (result != 0 || address == NULL || size == NULL || *size < sizeof(struct sockaddr_nl) ||
            address->sa_family != AF_NETLINK || ((struct sockaddr_nl *) address)->nl_pid != 0 || !route_socket(fd)) return result;
        REAL(sendto, sendto_fn);
        struct nlmsghdr noop = { .nlmsg_len = sizeof(struct nlmsghdr), .nlmsg_type = NLMSG_NOOP };
        struct sockaddr_nl kernel = { .nl_family = AF_NETLINK };
        int saved = errno;
        real_sendto(fd, &noop, sizeof(noop), MSG_DONTWAIT, (struct sockaddr *) &kernel, sizeof(kernel));
        errno = saved;
        return real_getsockname(fd, address, size);
    }
    if (address == NULL || size == NULL) { errno = EFAULT; return -1; }
    struct sockaddr_nl local = { .nl_family = AF_NETLINK, .nl_pid = state->port };
    memcpy(address, &local, *size < sizeof(local) ? *size : sizeof(local));
    *size = sizeof(local);
    return 0;
}

int setsockopt(int fd, int level, int name, const void *value, socklen_t size) {
    REAL(setsockopt, setsockopt_fn);
    struct emulated *state = lookup(fd);
    if (state && level == SOL_NETLINK) return real_setsockopt(state->hidden, level, name, value, size);
    return real_setsockopt(fd, level, name, value, size);
}

/* Called by close (linux_android_pads.c). */
void dextop_netlink_close(int fd) {
    if (fd < 0 || fd >= SLOTS || table[fd] == NULL) return;
    REAL(close, close_fn);
    pthread_mutex_lock(&lock);
    struct emulated *state = table[fd];
    table[fd] = NULL;
    pthread_mutex_unlock(&lock);
    if (state) { real_close(state->hidden); real_close(state->peer); free(state); }
}
