#include <dlfcn.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
typedef struct { char name[256];unsigned version;} Extension;
typedef struct {unsigned type;const void *next;unsigned flags;const void *application;unsigned layers;const char **layerNames;unsigned extensions;const char **extensionNames;} InstanceInfo;
int main(void){void*l=dlopen("libvulkan.so.1",RTLD_NOW);if(!l){printf("VK_LOADER_ERROR %s\n",dlerror());return 2;}int(*enumerate)(const char*,unsigned*,Extension*)=dlsym(l,"vkEnumerateInstanceExtensionProperties");int(*create)(const InstanceInfo*,const void*,void**)=dlsym(l,"vkCreateInstance");unsigned n=128;Extension e[128];int r=enumerate(0,&n,e);unsigned surface=0,xlib=0;for(unsigned i=0;i<n&&i<128;i++){if(!strcmp(e[i].name,"VK_KHR_surface"))surface=1;if(!strcmp(e[i].name,"VK_KHR_xlib_surface"))xlib=1;}void*instance=0;InstanceInfo info={.type=1};int c=create(&info,0,&instance);printf("VULKAN_PROBE bits=%u enum=%d count=%u surface=%u xlib=%u create=%d\n",(unsigned)(sizeof(void*)*8),r,n,surface,xlib,c);return 0;}