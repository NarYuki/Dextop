#include <jni.h>
#include <adrenotools/driver.h>
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

JNIEXPORT jobject JNICALL Java_WaylandPresentationProbe_compressedBuffer(JNIEnv *env, jclass cls, jintArray layout) {
    void *loader = adrenotools_open_libvulkan(RTLD_NOW | RTLD_LOCAL, ADRENOTOOLS_DRIVER_CUSTOM,
        NULL, getenv("DEXTOP_LINUX_GPU_HOOKS"), getenv("DEXTOP_LINUX_GPU_DRIVER"),
        getenv("DEXTOP_LINUX_GPU_LIBRARY"), NULL, NULL);
    if (!loader) return NULL;
    PFN_vkGetInstanceProcAddr proc = dlsym(loader, "vkGetInstanceProcAddr");
    PFN_vkCreateInstance create = (PFN_vkCreateInstance)proc(NULL, "vkCreateInstance");
    VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo ci = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    VkInstance instance;
    if (create(&ci, NULL, &instance) != VK_SUCCESS) { dlclose(loader); return NULL; }
#define I(name) PFN_vk##name name = (PFN_vk##name)proc(instance, "vk" #name)
    I(DestroyInstance); I(EnumeratePhysicalDevices); I(GetPhysicalDeviceQueueFamilyProperties);
    I(GetPhysicalDeviceMemoryProperties); I(CreateDevice); I(GetDeviceProcAddr);
#undef I
    uint32_t n = 1;
    VkPhysicalDevice physical;
    EnumeratePhysicalDevices(instance, &n, &physical);
    VkQueueFamilyProperties queues[32]; n = 32;
    GetPhysicalDeviceQueueFamilyProperties(physical, &n, queues);
    uint32_t family = 0;
    while (family < n && !(queues[family].queueFlags & VK_QUEUE_GRAPHICS_BIT)) family++;
    if (family == n) { DestroyInstance(instance, NULL); dlclose(loader); return NULL; }
    float priority = 1;
    VkDeviceQueueCreateInfo qc = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = family, .queueCount = 1, .pQueuePriorities = &priority};
    const char *extensions[] = {VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME, VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME,
        VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME, VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME};
    VkDeviceCreateInfo dc = {.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, .queueCreateInfoCount = 1,
        .pQueueCreateInfos = &qc, .enabledExtensionCount = 4, .ppEnabledExtensionNames = extensions};
    VkDevice device;
    if (CreateDevice(physical, &dc, NULL, &device) != VK_SUCCESS) {
        DestroyInstance(instance, NULL); dlclose(loader); return NULL;
    }
#define D(name) PFN_vk##name name = (PFN_vk##name)GetDeviceProcAddr(device, "vk" #name)
    D(DestroyDevice); D(CreateImage); D(DestroyImage); D(GetImageMemoryRequirements); D(AllocateMemory);
    D(FreeMemory); D(BindImageMemory); D(GetImageSubresourceLayout); D(GetMemoryFdKHR); D(GetDeviceQueue);
    D(CreateCommandPool); D(DestroyCommandPool); D(AllocateCommandBuffers); D(BeginCommandBuffer); D(EndCommandBuffer);
    D(CmdPipelineBarrier); D(CmdClearColorImage); D(QueueSubmit); D(QueueWaitIdle);
#undef D
    VkImage image = VK_NULL_HANDLE; VkDeviceMemory memory = VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE; int fd = -1; VkResult r;
#define OK(call) do { r = (call); if (r != VK_SUCCESS) { fprintf(stderr, "COMPRESSED_PRODUCER_FAILED line=%d result=%d\n", __LINE__, r); goto done; } } while (0)
    uint64_t compressed = UINT64_C(0x0500000000000001);
    VkImageDrmFormatModifierListCreateInfoEXT mods = {.sType = VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_LIST_CREATE_INFO_EXT,
        .drmFormatModifierCount = 1, .pDrmFormatModifiers = &compressed};
    VkExternalMemoryImageCreateInfo external = {.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
        .pNext = &mods, .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkImageCreateInfo image_info = {.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &external,
        .imageType = VK_IMAGE_TYPE_2D, .format = VK_FORMAT_B8G8R8A8_UNORM, .extent = {32, 32, 1},
        .mipLevels = 1, .arrayLayers = 1, .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT, .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT};
    OK(CreateImage(device, &image_info, NULL, &image));
    VkMemoryRequirements requirements; GetImageMemoryRequirements(device, image, &requirements);
    VkExportMemoryAllocateInfo export = {.sType = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO,
        .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkMemoryDedicatedAllocateInfo dedicated = {.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
        .pNext = &export, .image = image};
    VkMemoryAllocateInfo alloc = {.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = &dedicated,
        .allocationSize = requirements.size, .memoryTypeIndex = (uint32_t)__builtin_ctz(requirements.memoryTypeBits)};
    OK(AllocateMemory(device, &alloc, NULL, &memory)); OK(BindImageMemory(device, image, memory, 0));
    VkCommandPoolCreateInfo pc = {.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, .queueFamilyIndex = family};
    OK(CreateCommandPool(device, &pc, NULL, &pool));
    VkCommandBufferAllocateInfo bc = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
    VkCommandBuffer cmd; OK(AllocateCommandBuffers(device, &bc, &cmd));
    VkCommandBufferBeginInfo begin = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    OK(BeginCommandBuffer(cmd, &begin));
    VkImageMemoryBarrier barrier = {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT, .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .newLayout = VK_IMAGE_LAYOUT_GENERAL, .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .image = image,
        .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
    CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, NULL, 0, NULL, 1, &barrier);
    VkClearColorValue red = {.float32 = {1, 0, 0, 0}};
    CmdClearColorImage(cmd, image, VK_IMAGE_LAYOUT_GENERAL, &red, 1, &barrier.subresourceRange);
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT; barrier.dstAccessMask = 0;
    barrier.oldLayout = VK_IMAGE_LAYOUT_GENERAL; barrier.srcQueueFamilyIndex = family;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
    CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, NULL, 0, NULL, 1, &barrier);
    OK(EndCommandBuffer(cmd));
    VkQueue queue; GetDeviceQueue(device, family, 0, &queue);
    VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &cmd};
    OK(QueueSubmit(queue, 1, &submit, VK_NULL_HANDLE)); OK(QueueWaitIdle(queue));
    VkMemoryGetFdInfoKHR get_fd = {.sType = VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR,
        .memory = memory, .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    OK(GetMemoryFdKHR(device, &get_fd, &fd));
    VkImageSubresource subresource = {.aspectMask = VK_IMAGE_ASPECT_MEMORY_PLANE_0_BIT_EXT};
    VkSubresourceLayout plane; GetImageSubresourceLayout(device, image, &subresource, &plane);
    jint values[] = {(jint)plane.rowPitch, (jint)plane.offset};
    (*env)->SetIntArrayRegion(env, layout, 0, 2, values);
done:
    if (pool) DestroyCommandPool(device, pool, NULL);
    if (image) DestroyImage(device, image, NULL);
    if (memory) FreeMemory(device, memory, NULL);
    DestroyDevice(device, NULL); DestroyInstance(instance, NULL); dlclose(loader);
    if (fd < 0) return NULL;
    jclass type = (*env)->FindClass(env, "java/io/FileDescriptor");
    jobject result = (*env)->NewObject(env, type, (*env)->GetMethodID(env, type, "<init>", "()V"));
    (*env)->SetIntField(env, result, (*env)->GetFieldID(env, type, "descriptor", "I"), fd);
    return result;
}
