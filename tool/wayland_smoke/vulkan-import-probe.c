#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>
#ifdef HAVE_ADRENOTOOLS
#include <adrenotools/driver.h>
#include <dlfcn.h>
#endif

#ifdef HAVE_DMABUF_ROUNDTRIP
struct ANativeWindow;
int dmabuf_gpu_roundtrip(VkInstance, VkPhysicalDevice, PFN_vkGetInstanceProcAddr, struct ANativeWindow *);
#endif

static int has(const VkExtensionProperties *ext, uint32_t n, const char *name) {
    for (uint32_t i = 0; i < n; i++) if (!strcmp(ext[i].extensionName, name)) return 1;
    return 0;
}

int main(int argc, char **argv) {
    PFN_vkGetInstanceProcAddr get_proc = vkGetInstanceProcAddr;
#ifdef HAVE_ADRENOTOOLS
    if (argc >= 4) {
        void *loader = adrenotools_open_libvulkan(RTLD_LOCAL | RTLD_NOW,
            ADRENOTOOLS_DRIVER_CUSTOM, NULL, argv[3], argv[1], argv[2], NULL, NULL);
        if (!loader) { fprintf(stderr, "CUSTOM_DRIVER_LOAD_FAILED\n"); return 4; }
        get_proc = (PFN_vkGetInstanceProcAddr)dlsym(loader, "vkGetInstanceProcAddr");
        if (!get_proc) return 5;
    }
#else
    (void)argc; (void)argv;
#endif
    PFN_vkCreateInstance create_instance = (PFN_vkCreateInstance)get_proc(VK_NULL_HANDLE, "vkCreateInstance");
    VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "Dextop Linux GPU import probe", .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo create = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    VkInstance instance;
    VkResult r = create_instance(&create, NULL, &instance);
    if (r != VK_SUCCESS) { fprintf(stderr, "vkCreateInstance=%d\n", r); return 1; }
#define LOAD(name) PFN_##name name = (PFN_##name)get_proc(instance, #name)
    LOAD(vkEnumeratePhysicalDevices);
    LOAD(vkDestroyInstance);
    LOAD(vkGetPhysicalDeviceProperties);
    LOAD(vkEnumerateDeviceExtensionProperties);
    LOAD(vkGetPhysicalDeviceFormatProperties2);
#undef LOAD
    uint32_t n = 0;
    r = vkEnumeratePhysicalDevices(instance, &n, NULL);
    if (r != VK_SUCCESS || !n) { vkDestroyInstance(instance, NULL); return 2; }
    VkPhysicalDevice *devices = calloc(n, sizeof(*devices));
    if (!devices) { vkDestroyInstance(instance, NULL); return 3; }
    vkEnumeratePhysicalDevices(instance, &n, devices);
    for (uint32_t d = 0; d < n; d++) {
        VkPhysicalDeviceProperties properties;
        vkGetPhysicalDeviceProperties(devices[d], &properties);
        printf("DEVICE=%s api=%u.%u.%u\n", properties.deviceName,
            VK_API_VERSION_MAJOR(properties.apiVersion), VK_API_VERSION_MINOR(properties.apiVersion),
            VK_API_VERSION_PATCH(properties.apiVersion));
        uint32_t count = 0;
        vkEnumerateDeviceExtensionProperties(devices[d], NULL, &count, NULL);
        VkExtensionProperties *ext = calloc(count, sizeof(*ext));
        if (!ext) continue;
        vkEnumerateDeviceExtensionProperties(devices[d], NULL, &count, ext);
        const char *names[] = {VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
            VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME,
            VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME, VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
            VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        for (unsigned i = 0; i < sizeof(names) / sizeof(names[0]); i++)
            printf("%s=%d\n", names[i], has(ext, count, names[i]));
        if (has(ext, count, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME)) {
            VkDrmFormatModifierPropertiesListEXT list = {.sType = VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_EXT};
            VkFormatProperties2 format = {.sType = VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2, .pNext = &list};
            vkGetPhysicalDeviceFormatProperties2(devices[d], VK_FORMAT_B8G8R8A8_UNORM, &format);
            printf("BGRA8_MODIFIER_COUNT=%u\n", list.drmFormatModifierCount);
        }
        printf("DMABUF_IMPORT_CANDIDATE=%d\n", has(ext, count, VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME) &&
            has(ext, count, VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME) &&
            has(ext, count, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME));
        free(ext);
#ifdef HAVE_DMABUF_ROUNDTRIP
        if (argc == 5 && !strcmp(argv[4], "--dmabuf")) {
            for (int round = 0; round < 3; round++) {
                int code = dmabuf_gpu_roundtrip(instance, devices[d], get_proc, NULL);
                if (code) { free(devices); vkDestroyInstance(instance, NULL); return code; }
            }
        }
#endif
    }
    free(devices);
    vkDestroyInstance(instance, NULL);
    return 0;
}
