#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>
typedef struct {char name[256];unsigned version;} Extension;
typedef struct {unsigned type;const void*next;unsigned flags;const void*application;unsigned layers;const char**layerNames;unsigned extensions;const char**extensionNames;} InstanceInfo;
static void note(const char*s){DWORD n;HANDLE f=CreateFileA("Z:\\tmp\\dextop-win-vulkan-probe-result.txt",FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,0,OPEN_ALWAYS,0,0);if(f!=INVALID_HANDLE_VALUE){WriteFile(f,s,(DWORD)strlen(s),&n,0);CloseHandle(f);}puts(s);fflush(stdout);}
int main(void){char msg[256];HMODULE dll=LoadLibraryA("vulkan-1.dll");if(!dll){snprintf(msg,sizeof(msg),"WIN_VULKAN_LOAD_ERROR=%lu\n",GetLastError());note(msg);return 2;}
 int(WINAPI*enumerate)(const char*,unsigned*,Extension*)=(void*)GetProcAddress(dll,"vkEnumerateInstanceExtensionProperties");
 int(WINAPI*create)(const InstanceInfo*,const void*,void**)=(void*)GetProcAddress(dll,"vkCreateInstance");
 int(WINAPI*devices)(void*,unsigned*,void**)=(void*)GetProcAddress(dll,"vkEnumeratePhysicalDevices");
 void(WINAPI*destroy)(void*,const void*)=(void*)GetProcAddress(dll,"vkDestroyInstance");
 if(!enumerate||!create||!devices||!destroy){note("WIN_VULKAN_EXPORT_MISSING\n");return 3;}
 unsigned n=128,surface=0;Extension e[128];int er=enumerate(0,&n,e);
 if(!er)for(unsigned i=0;i<n&&i<128;i++)if(!strcmp(e[i].name,"VK_KHR_surface"))surface=1;
 void*instance=0;InstanceInfo info={.type=1};int cr=create(&info,0,&instance);unsigned gpu=0;int dr=cr?cr:devices(instance,&gpu,0);
 snprintf(msg,sizeof(msg),"WIN_VULKAN bits=%u enum=%d count=%u surface=%u create=%d devices=%d gpu_count=%u\n",(unsigned)(sizeof(void*)*8),er,n,surface,cr,dr,gpu);note(msg);
 if(!cr)destroy(instance,0);return er||cr||dr||!gpu?4:0;
}