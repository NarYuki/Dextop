/* Xvfb CPU-copy presentation cannot report KHR present completion reliably.
 * Advertise that limitation instead of allowing applications to wait forever.
 * No present results are fabricated and no application names are inspected. */
#define _POSIX_C_SOURCE 200809L
#include <vulkan/vulkan_core.h>
#include <vulkan/vk_layer.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

typedef struct Instance {
    void *key;
    VkInstance handle;
    PFN_vkGetInstanceProcAddr gipa;
    PFN_GetPhysicalDeviceProcAddr gpdpa;
    int mask;
    struct Instance *next;
} Instance;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static Instance *instances;
static void *key(const void *handle) { return handle ? *(void *const *)handle : NULL; }
static int lookup(const void *handle, Instance *out) {
    void *k = key(handle); int found = 0;
    pthread_mutex_lock(&lock);
    for (Instance *s = instances; s; s = s->next) if (s->key == k) { *out = *s; found = 1; break; }
    pthread_mutex_unlock(&lock); return found;
}
static int masked(const char *name) {
    return !strcmp(name, "VK_KHR_present_wait") || !strcmp(name, "VK_KHR_present_wait2") ||
           !strcmp(name, "VK_KHR_present_id") || !strcmp(name, "VK_KHR_present_id2");
}
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance, const char *);
static PFN_vkVoidFunction VKAPI_CALL physical_proc(VkInstance, const char *);

VKAPI_ATTR VkResult VKAPI_CALL vkCreateInstance(const VkInstanceCreateInfo *info,
        const VkAllocationCallbacks *allocator, VkInstance *out) {
    VkLayerInstanceCreateInfo *link = (void *)info->pNext;
    while (link && (link->sType != VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO || link->function != VK_LAYER_LINK_INFO)) link = (void *)link->pNext;
    if (!link) return VK_ERROR_INITIALIZATION_FAILED;
    Instance *state = calloc(1, sizeof(*state));
    if (!state) return VK_ERROR_OUT_OF_HOST_MEMORY;
    state->gipa = link->u.pLayerInfo->pfnNextGetInstanceProcAddr;
    state->gpdpa = link->u.pLayerInfo->pfnNextGetPhysicalDeviceProcAddr;
    PFN_vkCreateInstance create = (void *)state->gipa(NULL, "vkCreateInstance");
    link->u.pLayerInfo = link->u.pLayerInfo->pNext;
    VkResult result = create(info, allocator, out);
    if (result != VK_SUCCESS) { free(state); return result; }
    state->handle = *out; state->key = key(*out);
    const char *enabled = getenv("DEXTOP_XVFB_WSI");
    if (enabled) state->mask = !strcmp(enabled, "1");
    else {
        char mode[16] = {0};
        FILE *config = fopen("/etc/dextop/vulkan-wsi", "r");
        if (config) { (void)fgets(mode, sizeof(mode), config); fclose(config); }
        state->mask = !strcmp(mode, "xvfb\n") || !strcmp(mode, "xvfb");
    }
    pthread_mutex_lock(&lock); state->next = instances; instances = state; pthread_mutex_unlock(&lock);
    return result;
}
VKAPI_ATTR void VKAPI_CALL vkDestroyInstance(VkInstance instance, const VkAllocationCallbacks *allocator) {
    Instance state;
    if (!lookup(instance, &state)) return;
    PFN_vkDestroyInstance destroy = (void *)state.gipa(instance, "vkDestroyInstance");
    pthread_mutex_lock(&lock);
    Instance **p = &instances;
    while (*p) { if ((*p)->handle == instance) { Instance *old = *p; *p = old->next; free(old); break; } p = &(*p)->next; }
    pthread_mutex_unlock(&lock);
    destroy(instance, allocator);
}
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceExtensionProperties(VkPhysicalDevice physical,
        const char *layer, uint32_t *count, VkExtensionProperties *out) {
    Instance state;
    if (!lookup(physical, &state)) return VK_ERROR_INITIALIZATION_FAILED;
    PFN_vkEnumerateDeviceExtensionProperties enumerate = (void *)state.gipa(state.handle, "vkEnumerateDeviceExtensionProperties");
    if (!state.mask || layer) return enumerate(physical, layer, count, out);
    uint32_t n = 0; VkResult result;
    VkExtensionProperties *all = NULL;
    // Re-query if a lower layer's extension list changes between count/data calls.
    for (int attempt = 0; attempt < 4; ++attempt) {
        result = enumerate(physical, NULL, &n, NULL);
        if (result != VK_SUCCESS) return result;
        all = calloc(n ? n : 1, sizeof(*all));
        if (!all) return VK_ERROR_OUT_OF_HOST_MEMORY;
        result = enumerate(physical, NULL, &n, all);
        if (result != VK_INCOMPLETE) break;
        free(all); all = NULL;
    }
    if (result != VK_SUCCESS) { free(all); return result; }
    uint32_t kept = 0, capacity = out ? *count : 0;
    for (uint32_t i = 0; i < n; ++i) if (!masked(all[i].extensionName)) {
        if (out && kept < capacity) out[kept] = all[i];
        ++kept;
    }
    free(all); *count = out && capacity < kept ? capacity : kept;
    return out && capacity < kept ? VK_INCOMPLETE : VK_SUCCESS;
}
static void mask_features(VkPhysicalDeviceFeatures2 *features) {
    for (VkBaseOutStructure *p = features->pNext; p; p = p->pNext) {
        switch (p->sType) {
        case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_WAIT_FEATURES_KHR:
            ((VkPhysicalDevicePresentWaitFeaturesKHR *)p)->presentWait = VK_FALSE; break;
        case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_ID_FEATURES_KHR:
            ((VkPhysicalDevicePresentIdFeaturesKHR *)p)->presentId = VK_FALSE; break;
        case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_WAIT_2_FEATURES_KHR:
            ((VkPhysicalDevicePresentWait2FeaturesKHR *)p)->presentWait2 = VK_FALSE; break;
        case VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_ID_2_FEATURES_KHR:
            ((VkPhysicalDevicePresentId2FeaturesKHR *)p)->presentId2 = VK_FALSE; break;
        default: break;
        }
    }
}
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceFeatures2(VkPhysicalDevice physical, VkPhysicalDeviceFeatures2 *features) {
    Instance state; if (!lookup(physical, &state)) return;
    PFN_vkGetPhysicalDeviceFeatures2 fn = (void *)state.gipa(state.handle, "vkGetPhysicalDeviceFeatures2");
    if (!fn) fn = (void *)state.gipa(state.handle, "vkGetPhysicalDeviceFeatures2KHR");
    fn(physical, features); if (state.mask) mask_features(features);
}
static PFN_vkVoidFunction intercept(const char *name) {
#define MATCH(fn) if (!strcmp(name, #fn)) return (PFN_vkVoidFunction)fn
    MATCH(vkGetInstanceProcAddr); MATCH(vkCreateInstance); MATCH(vkDestroyInstance);
    MATCH(vkEnumerateDeviceExtensionProperties); MATCH(vkGetPhysicalDeviceFeatures2);
    if (!strcmp(name, "vkGetPhysicalDeviceFeatures2KHR")) return (PFN_vkVoidFunction)vkGetPhysicalDeviceFeatures2;
#undef MATCH
    return NULL;
}
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char *name) {
    PFN_vkVoidFunction fn = intercept(name); if (fn) return fn;
    Instance state; return lookup(instance, &state) ? state.gipa(instance, name) : NULL;
}
static PFN_vkVoidFunction VKAPI_CALL physical_proc(VkInstance instance, const char *name) {
    PFN_vkVoidFunction fn = intercept(name); if (fn) return fn;
    Instance state; if (!lookup(instance, &state)) return NULL;
    return state.gpdpa ? state.gpdpa(instance, name) : state.gipa(instance, name);
}
VKAPI_ATTR VkResult VKAPI_CALL vkNegotiateLoaderLayerInterfaceVersion(VkNegotiateLayerInterface *info) {
    if (info->loaderLayerInterfaceVersion < 2) return VK_ERROR_INITIALIZATION_FAILED;
    info->loaderLayerInterfaceVersion = 2;
    info->pfnGetInstanceProcAddr = vkGetInstanceProcAddr;
    info->pfnGetDeviceProcAddr = NULL; // Device calls go directly to the next layer.
    info->pfnGetPhysicalDeviceProcAddr = physical_proc;
    return VK_SUCCESS;
}
