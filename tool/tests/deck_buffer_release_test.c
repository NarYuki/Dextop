#define _GNU_SOURCE
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/socket.h>
#include <unistd.h>
#include <wayland-server.h>
#include "wayland-server-protocol.h"
struct comp {
    struct wl_display *display;
    struct wl_event_loop *loop;
    struct wl_list buffer_releases;
    struct wl_event_source *buffer_release_idle;
};
#include "deck_buffer_release.inc"

static unsigned releases(int fd) {
    uint32_t words[256];
    ssize_t n = recv(fd, words, sizeof(words), MSG_DONTWAIT);
    if (n < 0) return 0;
    unsigned count=0;
    for (unsigned offset=0;offset<(unsigned)n;) {
        uint32_t object=words[offset/4], header=words[offset/4+1];
        unsigned size=header>>16;
        assert(size>=8 && offset+size<=(unsigned)n);
        if(object==2 && (header&0xffff)==WL_BUFFER_RELEASE) count++;
        offset+=size;
    }
    return count;
}
int main(void) {
    struct comp comp={0};
    comp.display=wl_display_create();assert(comp.display);
    comp.loop=wl_display_get_event_loop(comp.display);
    wl_list_init(&comp.buffer_releases);
    int pair[2];assert(!socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,pair));
    struct wl_client *client=wl_client_create(comp.display,pair[0]);assert(client);
    struct wl_resource *buffer=wl_resource_create(client,&wl_buffer_interface,1,2);assert(buffer);
    /* Multiple copies/uses in one client dispatch produce one wire release. */
    deck_buffer_release_queue(&comp,buffer,true);
    deck_buffer_release_queue(&comp,buffer,true);
    wl_event_loop_dispatch_idle(comp.loop);
    assert(releases(pair[1])==1);
    /* Reacquisition before the release dispatch must suppress that release. */
    deck_buffer_release_queue(&comp,buffer,true);
    deck_buffer_release_cancel(&comp,buffer);
    wl_event_loop_dispatch_idle(comp.loop);
    assert(releases(pair[1])==0);
    deck_buffer_release_queue(&comp,buffer,true);
    wl_event_loop_dispatch_idle(comp.loop);
    assert(releases(pair[1])==1);
    /* Ordinary Linux clients retain their immediate release policy. */
    deck_buffer_release_queue(&comp,buffer,false);
    wl_display_flush_clients(comp.display);
    assert(releases(pair[1])==1);
    /* Destruction while queued cannot leave a stale wl_resource. */
    deck_buffer_release_queue(&comp,buffer,true);
    wl_resource_destroy(buffer);
    assert(wl_list_empty(&comp.buffer_releases));
    wl_event_loop_dispatch_idle(comp.loop);
    assert(releases(pair[1])==0);
    wl_client_destroy(client);close(pair[1]);wl_display_destroy(comp.display);
    puts("PASS: coalesced wire release, reacquisition, ordinary-client policy and queued destruction");
    return 0;
}
