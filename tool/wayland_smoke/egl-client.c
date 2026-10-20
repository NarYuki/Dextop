
#define _GNU_SOURCE
#include <wayland-client.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
struct wl_egl_window;
extern struct wl_egl_window *wl_egl_window_create(struct wl_surface*,int,int);
extern void wl_egl_window_destroy(struct wl_egl_window*);
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
static int configured;
static void ping(void*d,struct xdg_wm_base*w,uint32_t serial){xdg_wm_base_pong(w,serial);}
static const struct xdg_wm_base_listener wm_listener={.ping=ping};
static void global(void*d,struct wl_registry*r,uint32_t name,const char*iface,uint32_t version){
 if(!strcmp(iface,"wl_compositor"))compositor=wl_registry_bind(r,name,&wl_compositor_interface,4);
 if(!strcmp(iface,"wl_shm"))shm=wl_registry_bind(r,name,&wl_shm_interface,1);
 if(!strcmp(iface,"xdg_wm_base")){wm=wl_registry_bind(r,name,&xdg_wm_base_interface,1);xdg_wm_base_add_listener(wm,&wm_listener,0);}
}
static void removed(void*d,struct wl_registry*r,uint32_t name){}
static const struct wl_registry_listener registry_listener={global,removed};
static void configure(void*d,struct xdg_surface*x,uint32_t serial){
 xdg_surface_ack_configure(x,serial);
 configured=1;
}
static const struct xdg_surface_listener surface_listener={configure};
int main(int argc, char **argv){
 int fd=socket(AF_UNIX,SOCK_STREAM,0);struct sockaddr_un addr={.sun_family=AF_UNIX};
 const char*name="dextop-wayland-client-probe";memcpy(addr.sun_path+1,name,strlen(name));
 if(connect(fd,(void*)&addr,2+1+strlen(name)))return 2;
 struct wl_display*display=wl_display_connect_to_fd(fd);if(!display)return 3;
 struct wl_registry*r=wl_display_get_registry(display);wl_registry_add_listener(r,&registry_listener,0);wl_display_roundtrip(display);
 if(!compositor||!shm||!wm)return 4;
 surface=wl_compositor_create_surface(compositor);struct xdg_surface*x=xdg_wm_base_get_xdg_surface(wm,surface);xdg_surface_add_listener(x,&surface_listener,0);
 struct xdg_toplevel*top=xdg_surface_get_toplevel(x);xdg_toplevel_set_title(top,"Dextop Wayland probe");xdg_toplevel_set_app_id(top,"dextop.probe");wl_surface_commit(surface);
 for(int i=0;i<4;i++)if(wl_display_roundtrip(display)<0)return 5;

 EGLDisplay ed=eglGetDisplay((EGLNativeDisplayType)display);EGLint major,minor;
 if(!eglInitialize(ed,&major,&minor)){printf("EGL_INIT_FAILED %x\n",eglGetError());return 10;}
 eglBindAPI(EGL_OPENGL_ES_API);
 EGLint attrs[]={EGL_SURFACE_TYPE,EGL_WINDOW_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};EGLConfig config;EGLint count;
 if(!eglChooseConfig(ed,attrs,&config,1,&count)||!count)return 11;
 struct wl_egl_window*win=wl_egl_window_create(surface,128,64);EGLSurface es=eglCreateWindowSurface(ed,config,(EGLNativeWindowType)win,0);
 EGLint ctxattrs[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};EGLContext ctx=eglCreateContext(ed,config,EGL_NO_CONTEXT,ctxattrs);
 if(!eglMakeCurrent(ed,es,es,ctx)){printf("EGL_CURRENT_FAILED %x\n",eglGetError());return 12;}
 printf("GL_RENDERER=%s\n",glGetString(GL_RENDERER));fflush(stdout);
 int frames=argc>1?atoi(argv[1]):1;
 if(frames<1||frames>10000)return 14;
 for(int frame=0;frame<frames;frame++){
  glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
  if(!eglSwapBuffers(ed,es)){printf("EGL_SWAP_FAILED %x\n",eglGetError());return 13;}
 }
 printf("WAYLAND_EGL_FRAMES=%d\n",frames);
 wl_display_roundtrip(display);printf("WAYLAND_EGL_CLIENT_OK\n");fflush(stdout);sleep(2);
 eglMakeCurrent(ed,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroyContext(ed,ctx);eglDestroySurface(ed,es);eglTerminate(ed);wl_egl_window_destroy(win);
 xdg_toplevel_destroy(top);xdg_surface_destroy(x);wl_surface_destroy(surface);wl_display_roundtrip(display);wl_display_disconnect(display);return configured?0:6;
}
