
#define _GNU_SOURCE
#include <wayland-client.h>
#include "xdg-shell-client-protocol.h"
#include <sys/mman.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
static struct wl_compositor *compositor;
static struct wl_shm *shm;
static struct xdg_wm_base *wm;
static struct wl_surface *surface;
static struct wl_buffer *buffer;
static int configured,frames,rate=60000;static uint32_t started;
static void tick(void*d,struct wl_callback*c,uint32_t time);
static const struct wl_callback_listener frame_listener={tick};
static void schedule(void){struct wl_callback*c=wl_surface_frame(surface);wl_callback_add_listener(c,&frame_listener,0);wl_surface_commit(surface);}
static void tick(void*d,struct wl_callback*c,uint32_t time){wl_callback_destroy(c);if(!started)started=time;frames++;if(time-started>=1000){printf("FRAME_RATE target=%d actual=%.1f\n",rate,frames*1000.0/(time-started));fflush(stdout);frames=0;started=time;}schedule();}
static void geometry(void*d,struct wl_output*o,int32_t x,int32_t y,int32_t pw,int32_t ph,int32_t sub,const char*make,const char*model,int32_t transform){}
static void mode(void*d,struct wl_output*o,uint32_t flags,int32_t w,int32_t h,int32_t hz){if(flags&1){rate=hz;printf("OUTPUT_MODE %d %d %d\n",w,h,hz);fflush(stdout);}}
static void done(void*d,struct wl_output*o){}
static void scale(void*d,struct wl_output*o,int32_t f){}
static const struct wl_output_listener output_listener={.geometry=geometry,.mode=mode,.done=done,.scale=scale};
static void ping(void*d,struct xdg_wm_base*w,uint32_t serial){xdg_wm_base_pong(w,serial);}
static const struct xdg_wm_base_listener wm_listener={.ping=ping};
static void global(void*d,struct wl_registry*r,uint32_t name,const char*iface,uint32_t version){
 if(!strcmp(iface,"wl_output")){struct wl_output*o=wl_registry_bind(r,name,&wl_output_interface,2);wl_output_add_listener(o,&output_listener,0);}
 if(!strcmp(iface,"wl_compositor"))compositor=wl_registry_bind(r,name,&wl_compositor_interface,4);
 if(!strcmp(iface,"wl_shm"))shm=wl_registry_bind(r,name,&wl_shm_interface,1);
 if(!strcmp(iface,"xdg_wm_base")){wm=wl_registry_bind(r,name,&xdg_wm_base_interface,1);xdg_wm_base_add_listener(wm,&wm_listener,0);}
}
static void removed(void*d,struct wl_registry*r,uint32_t name){}
static const struct wl_registry_listener registry_listener={global,removed};
static void configure(void*d,struct xdg_surface*x,uint32_t serial){
 xdg_surface_ack_configure(x,serial);
 if(!configured){configured=1;wl_surface_attach(surface,buffer,0,0);wl_surface_damage(surface,0,0,128,64);schedule();}
}
static const struct xdg_surface_listener surface_listener={configure};
int main(){
 int fd=socket(AF_UNIX,SOCK_STREAM,0);struct sockaddr_un addr={.sun_family=AF_UNIX};
 const char*name="dextop-wayland-client-probe";memcpy(addr.sun_path+1,name,strlen(name));
 if(connect(fd,(void*)&addr,2+1+strlen(name)))return 2;
 struct wl_display*display=wl_display_connect_to_fd(fd);if(!display)return 3;
 struct wl_registry*r=wl_display_get_registry(display);wl_registry_add_listener(r,&registry_listener,0);wl_display_roundtrip(display);
 if(!compositor||!shm||!wm)return 4;
 int mem=memfd_create("wayland-probe",MFD_CLOEXEC);ftruncate(mem,128*64*4);
 unsigned*p=mmap(0,128*64*4,PROT_READ|PROT_WRITE,MAP_SHARED,mem,0);for(int i=0;i<128*64;i++)p[i]=0xffff0000;
 struct wl_shm_pool*pool=wl_shm_create_pool(shm,mem,128*64*4);buffer=wl_shm_pool_create_buffer(pool,0,128,64,128*4,WL_SHM_FORMAT_ARGB8888);
 surface=wl_compositor_create_surface(compositor);struct xdg_surface*x=xdg_wm_base_get_xdg_surface(wm,surface);xdg_surface_add_listener(x,&surface_listener,0);
 struct xdg_toplevel*top=xdg_surface_get_toplevel(x);xdg_toplevel_set_title(top,"Dextop Wayland probe");xdg_toplevel_set_app_id(top,"dextop.probe");wl_surface_commit(surface);
 for(int i=0;i<4;i++)if(wl_display_roundtrip(display)<0)return 5;
 while(wl_display_dispatch(display)>=0){}return 0;
}
