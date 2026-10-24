#define _GNU_SOURCE
#include "wayland/deck_ahb_wire.h"
#include <assert.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>
struct AHardwareBuffer;
int AHardwareBuffer_allocate(const struct deck_ahb_desc *,struct AHardwareBuffer **);
void AHardwareBuffer_release(struct AHardwareBuffer *);
void AHardwareBuffer_describe(const struct AHardwareBuffer *,struct deck_ahb_desc *);
const void *AHardwareBuffer_getNativeHandle(const struct AHardwareBuffer *);
int AHardwareBuffer_sendHandleToUnixSocket(const struct AHardwareBuffer *,int);
int main(void) {
    struct deck_ahb_desc wanted={.width=96,.height=64,.layers=1,.format=1,.usage=0xb00};
    struct AHardwareBuffer *b=NULL;
    assert(AHardwareBuffer_allocate(&wanted,&b)==0 && b);
    struct deck_ahb_desc actual;
    AHardwareBuffer_describe(b,&actual);
    assert(actual.width==96 && actual.height==64 && actual.stride>=96);
    struct handle {int version,nfds,nints,data[];};
    const struct handle *h=AHardwareBuffer_getNativeHandle(b);
    assert(h && h->nfds>0);
    struct stat allocation;
    assert(!fstat(h->data[0],&allocation));
    int pair[2]; assert(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair));
    assert(!AHardwareBuffer_sendHandleToUnixSocket(b,pair[0]));
    int verify=socket(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC,0);
    struct sockaddr_un address={.sun_family=AF_UNIX};
    const char name[]="dextop-ahb-probe-verify";
    memcpy(address.sun_path+1,name,sizeof(name)-1);
    assert(!connect(verify,(void *)&address,offsetof(struct sockaddr_un,sun_path)+sizeof(name)));
    char byte=1,control[CMSG_SPACE(sizeof(int))]={0};
    struct iovec iov={&byte,1};
    struct msghdr msg={.msg_iov=&iov,.msg_iovlen=1,.msg_control=control,.msg_controllen=sizeof(control)};
    struct cmsghdr *c=CMSG_FIRSTHDR(&msg);
    c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(c),&pair[1],sizeof(int));
    assert(sendmsg(verify,&msg,MSG_NOSIGNAL)==1);
    int status=-1; assert(recv(verify,&status,sizeof(status),0)==sizeof(status) && status==0);
    close(verify);close(pair[0]);close(pair[1]);
    AHardwareBuffer_release(b);
    wanted.format=0xffffffff;
    assert(AHardwareBuffer_allocate(&wanted,&b)!=0 && !b);
    puts("AHB allocation, native dma-buf FD, Android handle round-trip and invalid format: PASS");
    return 0;
}
