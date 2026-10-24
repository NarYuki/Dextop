#define _GNU_SOURCE
#include <android/hardware_buffer.h>
#include <assert.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include "wayland/deck_ahb_broker.inc"
static int listen_at(const char *name) {
    int fd=socket(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC,0); assert(fd>=0);
    struct sockaddr_un address={.sun_family=AF_UNIX};
    memcpy(address.sun_path+1,name,strlen(name));
    assert(!bind(fd,(void *)&address,offsetof(struct sockaddr_un,sun_path)+1+strlen(name)));
    assert(!listen(fd,4));return fd;
}
int main(void) {
    int broker=listen_at("dextop-ahb-probe/tmp/runtime-root/wayland-0.ahb");
    int verify=listen_at("dextop-ahb-probe-verify");
    puts("AHB probe ready");fflush(stdout);
    struct pollfd sockets[2]={{broker,POLLIN,0},{verify,POLLIN,0}};
    while (poll(sockets,2,30000)>0) {
        if(sockets[0].revents&POLLIN) deck_ahb_accept(broker,0,NULL);
        if(sockets[1].revents&POLLIN) {
            int client=accept4(verify,NULL,NULL,SOCK_CLOEXEC);
            char byte,control[CMSG_SPACE(sizeof(int))]={0};
            struct iovec iov={&byte,1};
            struct msghdr msg={.msg_iov=&iov,.msg_iovlen=1,.msg_control=control,.msg_controllen=sizeof(control)};
            assert(recvmsg(client,&msg,MSG_CMSG_CLOEXEC)==1);
            struct cmsghdr *c=CMSG_FIRSTHDR(&msg);assert(c && c->cmsg_type==SCM_RIGHTS);
            int handle=-1;memcpy(&handle,CMSG_DATA(c),sizeof(int));
            AHardwareBuffer *b=NULL;
            int status=AHardwareBuffer_recvHandleFromUnixSocket(handle,&b);
            if(!status && b) {
                AHardwareBuffer_Desc desc;AHardwareBuffer_describe(b,&desc);
                status=!(desc.width==96 && desc.height==64 && desc.stride>=96 && desc.format==1);
                AHardwareBuffer_release(b);
            }
            close(handle);send(client,&status,sizeof(status),MSG_NOSIGNAL);close(client);
            printf("Android AHB deserialization status=%d\n",status);fflush(stdout);
        }
    }
    close(broker);close(verify);return 0;
}
