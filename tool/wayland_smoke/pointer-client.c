
#define _GNU_SOURCE
#include <wayland-client.h>
#include "xdg-shell-client-protocol.h"
#include "relative-pointer-client-protocol.h"
#include "pointer-constraints-client-protocol.h"
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
static int configured,locked,unlocked,relative_ok,buttons,bad_motion,confine_mode,confined_motion;
static struct wl_seat*seat;static struct wl_pointer*pointer;
static struct zwp_relative_pointer_manager_v1*relative_manager;
static struct zwp_pointer_constraints_v1*constraints;
static void lock_event(void*d,struct zwp_locked_pointer_v1*p){locked=1;}
static void unlock_event(void*d,struct zwp_locked_pointer_v1*p){unlocked=1;}
static const struct zwp_locked_pointer_v1_listener lock_listener={lock_event,unlock_event};
static void relative_event(void*d,struct zwp_relative_pointer_v1*p,uint32_t hi,uint32_t lo,wl_fixed_t x,wl_fixed_t y,wl_fixed_t ux,wl_fixed_t uy){if(wl_fixed_to_int(x)==7&&wl_fixed_to_int(y)==-3&&x==ux&&y==uy)relative_ok=1;}
static const struct zwp_relative_pointer_v1_listener relative_listener={relative_event};
static void enter(void*d,struct wl_pointer*p,uint32_t serial,struct wl_surface*s,wl_fixed_t x,wl_fixed_t y){}
static void leave(void*d,struct wl_pointer*p,uint32_t serial,struct wl_surface*s){}
static void motion(void*d,struct wl_pointer*p,uint32_t t,wl_fixed_t x,wl_fixed_t y){if(locked&&!unlocked){if(confine_mode){double px=wl_fixed_to_double(x),py=wl_fixed_to_double(y);if(px<10||px>=25||py<10||py>=30)bad_motion++;else if(px>=24&&py==17)confined_motion=1;}else bad_motion++;}}
static void button(void*d,struct wl_pointer*p,uint32_t serial,uint32_t t,uint32_t b,uint32_t state){if(b==272)buttons++;}
static void axis(void*d,struct wl_pointer*p,uint32_t t,uint32_t a,wl_fixed_t v){}
static const struct wl_pointer_listener pointer_listener={.enter=enter,.leave=leave,.motion=motion,.button=button,.axis=axis};
static void ping(void*d,struct xdg_wm_base*w,uint32_t serial){xdg_wm_base_pong(w,serial);}
static const struct xdg_wm_base_listener wm_listener={.ping=ping};
static void global(void*d,struct wl_registry*r,uint32_t name,const char*iface,uint32_t version){
 if(!strcmp(iface,"wl_seat")){seat=wl_registry_bind(r,name,&wl_seat_interface,1);pointer=wl_seat_get_pointer(seat);wl_pointer_add_listener(pointer,&pointer_listener,0);}
 if(!strcmp(iface,"zwp_relative_pointer_manager_v1"))relative_manager=wl_registry_bind(r,name,&zwp_relative_pointer_manager_v1_interface,1);
 if(!strcmp(iface,"zwp_pointer_constraints_v1"))constraints=wl_registry_bind(r,name,&zwp_pointer_constraints_v1_interface,1);
 if(!strcmp(iface,"wl_compositor"))compositor=wl_registry_bind(r,name,&wl_compositor_interface,4);
 if(!strcmp(iface,"wl_shm"))shm=wl_registry_bind(r,name,&wl_shm_interface,1);
 if(!strcmp(iface,"xdg_wm_base")){wm=wl_registry_bind(r,name,&xdg_wm_base_interface,1);xdg_wm_base_add_listener(wm,&wm_listener,0);}
}
static void removed(void*d,struct wl_registry*r,uint32_t name){}
static const struct wl_registry_listener registry_listener={global,removed};
static void configure(void*d,struct xdg_surface*x,uint32_t serial){
 xdg_surface_ack_configure(x,serial);
 if(!configured){configured=1;wl_surface_attach(surface,buffer,0,0);wl_surface_damage(surface,0,0,128,64);wl_surface_commit(surface);}
}
static const struct xdg_surface_listener surface_listener={configure};
static void confined(void*d,struct zwp_confined_pointer_v1*p){locked=1;}
static void unconfined(void*d,struct zwp_confined_pointer_v1*p){unlocked=1;}
static const struct zwp_confined_pointer_v1_listener confined_listener={confined,unconfined};
int main(int argc,char**argv){confine_mode=argc>1;
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
 if(!pointer||!constraints||!relative_manager)return 7;
 struct zwp_relative_pointer_v1*relative=zwp_relative_pointer_manager_v1_get_relative_pointer(relative_manager,pointer);zwp_relative_pointer_v1_add_listener(relative,&relative_listener,0);
 struct zwp_locked_pointer_v1*lock=NULL;struct zwp_confined_pointer_v1*confine=NULL;
 if(confine_mode){struct wl_region*region=wl_compositor_create_region(compositor);wl_region_add(region,10,10,20,20);wl_region_subtract(region,25,10,5,20);
  confine=zwp_pointer_constraints_v1_confine_pointer(constraints,surface,pointer,region,ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT);zwp_confined_pointer_v1_add_listener(confine,&confined_listener,0);wl_region_destroy(region);
 }else{lock=zwp_pointer_constraints_v1_lock_pointer(constraints,surface,pointer,0,ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT);zwp_locked_pointer_v1_add_listener(lock,&lock_listener,0);}
 wl_display_flush(display);
 while(!unlocked)if(wl_display_dispatch(display)<0)return 8;
 if(!locked||!relative_ok||buttons!=2||bad_motion||(confine_mode&&!confined_motion)){fprintf(stderr,"locked=%d relative=%d buttons=%d bad_motion=%d\n",locked,relative_ok,buttons,bad_motion);return 9;}
 xdg_toplevel_destroy(top);xdg_surface_destroy(x);wl_surface_destroy(surface);wl_display_roundtrip(display);
 if(lock)zwp_locked_pointer_v1_destroy(lock);if(confine)zwp_confined_pointer_v1_destroy(confine);zwp_relative_pointer_v1_destroy(relative);wl_display_roundtrip(display);wl_display_disconnect(display);
 puts(confine_mode?"WAYLAND_CONFINE_REGION_SUBTRACT_RELATIVE_RELEASE_OK":"WAYLAND_LOCK_RELATIVE_BUTTONS_FOCUS_RELEASE_SURFACE_DESTROY_OK");return 0;
}
