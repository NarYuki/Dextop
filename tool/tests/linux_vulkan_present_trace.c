#define _POSIX_C_SOURCE 200809L
#include <vulkan/vulkan.h>
#include <vulkan/vk_layer.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static PFN_vkGetInstanceProcAddr gi;static PFN_vkGetDeviceProcAddr gd;static VkInstance inst;static VkDevice dev;
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance,const char*);
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(VkDevice,const char*);
#define NOTE(name,stage,r) do{fprintf(stderr,"DEXTOP_VK_TRACE %s %s result=%d\n",name,stage,(int)r);fflush(stderr);}while(0)
VKAPI_ATTR VkResult VKAPI_CALL vkCreateInstance(const VkInstanceCreateInfo*i,const VkAllocationCallbacks*a,VkInstance*out){VkLayerInstanceCreateInfo*l=(void*)i->pNext;while(l&&(l->sType!=VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO||l->function!=VK_LAYER_LINK_INFO))l=(void*)l->pNext;if(!l)return VK_ERROR_INITIALIZATION_FAILED;gi=l->u.pLayerInfo->pfnNextGetInstanceProcAddr;PFN_vkCreateInstance fn=(void*)gi(0,"vkCreateInstance");l->u.pLayerInfo=l->u.pLayerInfo->pNext;VkResult r=fn(i,a,out);if(!r)inst=*out;NOTE("CreateInstance","RETURN",r);return r;}
VKAPI_ATTR VkResult VKAPI_CALL vkCreateDevice(VkPhysicalDevice p,const VkDeviceCreateInfo*i,const VkAllocationCallbacks*a,VkDevice*out){VkLayerDeviceCreateInfo*l=(void*)i->pNext;while(l&&(l->sType!=VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO||l->function!=VK_LAYER_LINK_INFO))l=(void*)l->pNext;if(!l)return VK_ERROR_INITIALIZATION_FAILED;gd=l->u.pLayerInfo->pfnNextGetDeviceProcAddr;PFN_vkCreateDevice fn=(void*)gi(inst,"vkCreateDevice");l->u.pLayerInfo=l->u.pLayerInfo->pNext;VkResult r=fn(p,i,a,out);if(!r)dev=*out;NOTE("CreateDevice","RETURN",r);return r;}
#define WRAP(name,params,call) VKAPI_ATTR VkResult VKAPI_CALL name params {PFN_##name fn=(void*)gd(dev,#name);NOTE(#name,"ENTER",0);VkResult r=fn call;NOTE(#name,"RETURN",r);return r;}
WRAP(vkQueuePresentKHR,(VkQueue q,const VkPresentInfoKHR*i),(q,i))
WRAP(vkAcquireNextImageKHR,(VkDevice d,VkSwapchainKHR s,uint64_t t,VkSemaphore sem,VkFence f,uint32_t*i),(d,s,t,sem,f,i))
WRAP(vkAcquireNextImage2KHR,(VkDevice d,const VkAcquireNextImageInfoKHR*a,uint32_t*i),(d,a,i))
WRAP(vkWaitForPresentKHR,(VkDevice d,VkSwapchainKHR s,uint64_t id,uint64_t t),(d,s,id,t))
WRAP(vkWaitForPresent2KHR,(VkDevice d,VkSwapchainKHR s,const VkPresentWait2InfoKHR*i),(d,s,i))
WRAP(vkWaitForFences,(VkDevice d,uint32_t n,const VkFence*f,VkBool32 all,uint64_t t),(d,n,f,all,t))
WRAP(vkQueueSubmit,(VkQueue q,uint32_t n,const VkSubmitInfo*i,VkFence f),(q,n,i,f))
WRAP(vkQueueSubmit2,(VkQueue q,uint32_t n,const VkSubmitInfo2*i,VkFence f),(q,n,i,f))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceExtensionProperties(VkPhysicalDevice p,const char*layer,uint32_t*count,VkExtensionProperties*out){
 PFN_vkEnumerateDeviceExtensionProperties fn=(void*)gi(inst,"vkEnumerateDeviceExtensionProperties");
 if(!getenv("DEXTOP_VK_TRACE_DISABLE_PRESENT_WAIT"))return fn(p,layer,count,out);
 uint32_t n=0;VkResult r=fn(p,layer,&n,0);if(r)return r;
 VkExtensionProperties*all=calloc(n,sizeof(*all));if(!all)return VK_ERROR_OUT_OF_HOST_MEMORY;
 r=fn(p,layer,&n,all);if(r){free(all);return r;}uint32_t kept=0,capacity=out?*count:0;
 for(uint32_t k=0;k<n;k++){
  const char*s=all[k].extensionName;
  if(!strcmp(s,"VK_KHR_present_wait")||!strcmp(s,"VK_KHR_present_wait2")||!strcmp(s,"VK_KHR_present_id")||!strcmp(s,"VK_KHR_present_id2"))continue;
  if(out&&kept<capacity)out[kept]=all[k];kept++;
 }
 free(all);*count=out&&capacity<kept?capacity:kept;return out&&capacity<kept?VK_INCOMPLETE:VK_SUCCESS;
}
#define MATCH(name) if(!strcmp(n,#name))return (PFN_vkVoidFunction)name
static PFN_vkVoidFunction hook(const char*n){MATCH(vkEnumerateDeviceExtensionProperties);MATCH(vkGetInstanceProcAddr);MATCH(vkGetDeviceProcAddr);MATCH(vkCreateInstance);MATCH(vkCreateDevice);MATCH(vkQueuePresentKHR);MATCH(vkAcquireNextImageKHR);MATCH(vkAcquireNextImage2KHR);MATCH(vkWaitForPresentKHR);MATCH(vkWaitForPresent2KHR);MATCH(vkWaitForFences);MATCH(vkQueueSubmit);MATCH(vkQueueSubmit2);return 0;}
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance i,const char*n){PFN_vkVoidFunction f=hook(n);return f?f:gi?gi(i,n):0;}
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(VkDevice d,const char*n){PFN_vkVoidFunction f=hook(n);return f?f:gd?gd(d,n):0;}
VKAPI_ATTR VkResult VKAPI_CALL vkNegotiateLoaderLayerInterfaceVersion(VkNegotiateLayerInterface*i){if(i->loaderLayerInterfaceVersion>2)i->loaderLayerInterfaceVersion=2;i->pfnGetInstanceProcAddr=vkGetInstanceProcAddr;i->pfnGetDeviceProcAddr=vkGetDeviceProcAddr;return VK_SUCCESS;}