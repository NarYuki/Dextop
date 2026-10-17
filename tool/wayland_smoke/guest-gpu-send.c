#include <vulkan/vulkan.h>
#include <stdio.h>

typedef struct ANativeWindow ANativeWindow;
int dmabuf_gpu_roundtrip(VkInstance, VkPhysicalDevice, PFN_vkGetInstanceProcAddr, ANativeWindow *);
int dmabuf_gpu_blit_window(VkInstance instance, VkPhysicalDevice physical, VkDevice device,
    uint32_t queue_index, PFN_vkGetInstanceProcAddr proc, ANativeWindow *window, VkImage image, int acquire_fd) {
    (void)instance; (void)physical; (void)device; (void)queue_index;
    (void)proc; (void)window; (void)image; (void)acquire_fd;
    return 1;
}

int main(void) {
    VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo info = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    VkInstance instance;
    if (vkCreateInstance(&info, NULL, &instance)) return 1;
    uint32_t count = 1;
    VkPhysicalDevice physical;
    int code = 2;
    if (vkEnumeratePhysicalDevices(instance, &count, &physical) == VK_SUCCESS && count) {
        VkPhysicalDeviceProperties properties;
        vkGetPhysicalDeviceProperties(physical, &properties);
        printf("GUEST_GPU=%s\n", properties.deviceName);
        code = dmabuf_gpu_roundtrip(instance, physical, vkGetInstanceProcAddr, NULL);
    }
    vkDestroyInstance(instance, NULL);
    return code;
}
