#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#define LOAD(lib, name, type) type name = (type)dlsym(lib, #name); if (!name) { fprintf(stderr, "%s missing\n", #name); return 2; }
int main(void) {
 setbuf(stdout,NULL);
 write(1,"loading OpenAL\n",15);
 void *al=dlopen("libopenal.so.1",RTLD_NOW|RTLD_LOCAL);
 if(!al){fprintf(stderr,"OpenAL: %s\n",dlerror());return 1;}
 puts("loading crypto");
 void *crypto=dlopen("libcrypto.so.1.0.0",RTLD_NOW|RTLD_LOCAL);
 if(!crypto){fprintf(stderr,"legacy crypto: %s\n",dlerror());return 1;}
 puts("loading GL/X11");
 void *xlib=dlopen("libX11.so.6",RTLD_NOW|RTLD_GLOBAL), *gl=dlopen("libGL.so.1",RTLD_NOW|RTLD_GLOBAL);
 if(!xlib||!gl){fprintf(stderr,"GL/X11: %s\n",dlerror());return 1;}
 typedef void *(*open_display)(const char*);
 typedef void **(*choose_config)(void*,int,const int*,int*);
 typedef void *(*new_context)(void*,void*,int,void*,int);
 typedef unsigned long (*new_pbuffer)(void*,void*,const int*);
 typedef int (*make_current)(void*,unsigned long,unsigned long,void*);
 typedef void (*clear_color)(float,float,float,float);
 typedef void (*clear)(unsigned);
 typedef void (*read_pixels)(int,int,int,int,unsigned,unsigned,void*);
 typedef const unsigned char *(*get_string)(unsigned);
 LOAD(xlib,XOpenDisplay,open_display);
 LOAD(gl,glXChooseFBConfig,choose_config);LOAD(gl,glXCreateNewContext,new_context);
 LOAD(gl,glXCreatePbuffer,new_pbuffer);LOAD(gl,glXMakeContextCurrent,make_current);
 LOAD(gl,glClearColor,clear_color);LOAD(gl,glClear,clear);LOAD(gl,glReadPixels,read_pixels);LOAD(gl,glGetString,get_string);
 puts("opening X display");
 void *display=XOpenDisplay(NULL);if(!display){puts("no X11 display");return 3;}
 int attributes[]={0x8010,4,0x8011,1,8,8,9,8,10,8,0},count=0;
 puts("choosing GLX config");
 void **configs=glXChooseFBConfig(display,0,attributes,&count);if(!configs||count<1){puts("no pbuffer config");return 4;}
 puts("creating GLX context");
 void *context=glXCreateNewContext(display,configs[0],0x8014,NULL,1);
 int size[]={0x8041,8,0x8040,8,0};unsigned long pbuffer=glXCreatePbuffer(display,configs[0],size);
 if(!context||!pbuffer||!glXMakeContextCurrent(display,pbuffer,pbuffer,context)){puts("no GLX context");return 5;}
 unsigned char pixel[4]={0};glClearColor(1,0,0,1);glClear(0x4000);glReadPixels(0,0,1,1,0x1908,0x1401,pixel);
 printf("ELF%zu_GLX_PIXEL %u %u %u renderer=%s\n",sizeof(void*)*8,pixel[0],pixel[1],pixel[2],glGetString(0x1F01));
 return pixel[0]>250&&pixel[1]<5&&pixel[2]<5?0:6;
}
