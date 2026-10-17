#include <vulkan/vulkan.h>
#ifdef DEXTOP_GUEST_GPU_PROBE
typedef struct ANativeWindow ANativeWindow;
int guest_dmabuf_send_fds(int fd, int fence_fd);
#include <pthread.h>
#include <poll.h>
#include <unistd.h>
struct delayed_event { VkDevice device; VkEvent event; PFN_vkSetEvent signal; };
static void *signal_delayed_event(void *data) {
    struct delayed_event *event = data;
    usleep(500000);
    event->signal(event->device, event->event);
    return NULL;
}
#else
#include <android/native_window.h>
#endif
#include <errno.h>
#include <fcntl.h>
#include <linux/dma-buf.h>
#include <linux/dma-heap.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

int dmabuf_gpu_blit_window(VkInstance, VkPhysicalDevice, VkDevice, uint32_t,
    PFN_vkGetInstanceProcAddr, ANativeWindow *, VkImage, int);

int dmabuf_gpu_import_test(VkInstance instance, VkPhysicalDevice physical,
                        PFN_vkGetInstanceProcAddr get_proc, ANativeWindow *window, int external_fd, int acquire_fd) {
#define INSTANCE(name) PFN_##name name = (PFN_##name)get_proc(instance, #name)
    INSTANCE(vkGetPhysicalDeviceQueueFamilyProperties);
    INSTANCE(vkCreateDevice);
    INSTANCE(vkGetDeviceProcAddr);
#undef INSTANCE
    uint32_t n = 0, queue_index = UINT32_MAX;
    vkGetPhysicalDeviceQueueFamilyProperties(physical, &n, NULL);
    VkQueueFamilyProperties *families = calloc(n, sizeof(*families));
    if (!families) return 1;
    vkGetPhysicalDeviceQueueFamilyProperties(physical, &n, families);
    for (uint32_t i = 0; i < n; i++) if (families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) { queue_index = i; break; }
    free(families);
    if (queue_index == UINT32_MAX) return 2;
    float priority = 1;
    VkDeviceQueueCreateInfo queue_info = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = queue_index, .queueCount = 1, .pQueuePriorities = &priority};
    const char *extensions[] = {VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
        VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME,
        VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME, VK_KHR_SWAPCHAIN_EXTENSION_NAME,
        VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME, VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME};
    VkDeviceCreateInfo device_info = {.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1, .pQueueCreateInfos = &queue_info,
        .enabledExtensionCount = 7, .ppEnabledExtensionNames = extensions};
    VkDevice device;
    VkResult result = vkCreateDevice(physical, &device_info, NULL, &device);
    if (result != VK_SUCCESS) { fprintf(stderr, "createDevice=%d\n", result); return 3; }
#define DEVICE(name) PFN_##name name = (PFN_##name)vkGetDeviceProcAddr(device, #name)
    DEVICE(vkDestroyDevice); DEVICE(vkCreateImage); DEVICE(vkDestroyImage);
    DEVICE(vkGetImageMemoryRequirements); DEVICE(vkGetMemoryFdPropertiesKHR);
    DEVICE(vkAllocateMemory); DEVICE(vkFreeMemory); DEVICE(vkBindImageMemory);
    DEVICE(vkGetDeviceQueue); DEVICE(vkCreateCommandPool); DEVICE(vkDestroyCommandPool);
    DEVICE(vkAllocateCommandBuffers); DEVICE(vkBeginCommandBuffer); DEVICE(vkEndCommandBuffer);
    DEVICE(vkCmdPipelineBarrier); DEVICE(vkCmdClearColorImage); DEVICE(vkQueueSubmit);
    DEVICE(vkCreateFence); DEVICE(vkDestroyFence); DEVICE(vkWaitForFences); DEVICE(vkDeviceWaitIdle);
    DEVICE(vkGetFenceFdKHR);
#ifndef DEXTOP_GUEST_GPU_PROBE
    (void)vkGetFenceFdKHR;
#else
    (void)vkWaitForFences;
    DEVICE(vkCreateEvent); DEVICE(vkDestroyEvent); DEVICE(vkCmdWaitEvents); DEVICE(vkSetEvent);
    VkEvent producer_gate = VK_NULL_HANDLE;
    pthread_t signal_thread;
    int signal_thread_started = 0;
    struct delayed_event delayed = {0};
#endif
#undef DEVICE
    VkImage image = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    int heap = -1, fd = -1, imported_fd = -1, code = 4;
    void *map = MAP_FAILED;
    size_t size = 0;
#define CHECK(call) do { result = (call); if (result != VK_SUCCESS) { \
    fprintf(stderr, "%s=%d\n", #call, result); goto cleanup; } } while (0)
    VkSubresourceLayout plane = {.rowPitch = 64 * 4};
    VkImageDrmFormatModifierExplicitCreateInfoEXT modifier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT,
        .drmFormatModifier = 0, .drmFormatModifierPlaneCount = 1, .pPlaneLayouts = &plane};
    VkExternalMemoryImageCreateInfo external = {.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
        .pNext = &modifier, .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkImageCreateInfo image_info = {.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &external,
        .imageType = VK_IMAGE_TYPE_2D, .format = VK_FORMAT_B8G8R8A8_UNORM, .extent = {64, 64, 1},
        .mipLevels = 1, .arrayLayers = 1, .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT,
        .usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE, .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED};
    CHECK(vkCreateImage(device, &image_info, NULL, &image));
    VkMemoryRequirements requirements;
    vkGetImageMemoryRequirements(device, image, &requirements);
    size = (size_t)((requirements.size + 4095) & ~(VkDeviceSize)4095);
    if (external_fd >= 0) {
        fd = dup(external_fd);
        if (fd < 0) goto cleanup;
        off_t actual = lseek(fd, 0, SEEK_END);
        if (actual < 0 || (uint64_t)actual < size) { fprintf(stderr, "EXTERNAL_BUFFER_TOO_SMALL\n"); goto cleanup; }
    } else {
        heap = open("/dev/dma_heap/system", O_RDONLY | O_CLOEXEC);
        if (heap < 0) { perror("dma heap"); goto cleanup; }
        struct dma_heap_allocation_data allocation = {.len = size, .fd_flags = O_RDWR | O_CLOEXEC};
        if (ioctl(heap, DMA_HEAP_IOCTL_ALLOC, &allocation) < 0) { perror("dma allocation"); goto cleanup; }
        fd = allocation.fd;
    }
    imported_fd = dup(fd);
    if (imported_fd < 0) goto cleanup;
    VkMemoryFdPropertiesKHR properties = {.sType = VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR};
    CHECK(vkGetMemoryFdPropertiesKHR(device, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, imported_fd, &properties));
    uint32_t bits = requirements.memoryTypeBits & properties.memoryTypeBits;
    if (!bits) goto cleanup;
    uint32_t type = __builtin_ctz(bits);
    VkImportMemoryFdInfoKHR import = {.sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR,
        .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, .fd = imported_fd};
    VkMemoryDedicatedAllocateInfo dedicated = {.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
        .pNext = &import, .image = image};
    VkMemoryAllocateInfo memory_info = {.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = &dedicated,
        .allocationSize = size, .memoryTypeIndex = type};
    CHECK(vkAllocateMemory(device, &memory_info, NULL, &memory));
    imported_fd = -1;
    CHECK(vkBindImageMemory(device, image, memory, 0));
    if (external_fd < 0) {
    VkCommandPoolCreateInfo pool_info = {.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .queueFamilyIndex = queue_index};
    CHECK(vkCreateCommandPool(device, &pool_info, NULL, &pool));
    VkCommandBufferAllocateInfo command_info = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
    VkCommandBuffer command;
    CHECK(vkAllocateCommandBuffers(device, &command_info, &command));
    VkCommandBufferBeginInfo begin = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    CHECK(vkBeginCommandBuffer(command, &begin));
#ifdef DEXTOP_GUEST_GPU_PROBE
    VkEventCreateInfo gate_info = {.sType = VK_STRUCTURE_TYPE_EVENT_CREATE_INFO};
    CHECK(vkCreateEvent(device, &gate_info, NULL, &producer_gate));
    vkCmdWaitEvents(command, 1, &producer_gate, VK_PIPELINE_STAGE_HOST_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, NULL, 0, NULL, 0, NULL);
#endif
    VkImageMemoryBarrier barrier = {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT, .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .image = image,
        .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, NULL, 0, NULL, 1, &barrier);
    VkClearColorValue red = {.float32 = {1, 0, 0, 1}};
    vkCmdClearColorImage(command, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &red, 1, &barrier.subresourceRange);
    barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barrier.dstAccessMask = 0;
    barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
    barrier.srcQueueFamilyIndex = queue_index;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, NULL, 0, NULL, 1, &barrier);
    CHECK(vkEndCommandBuffer(command));
    VkFenceCreateInfo fence_info = {.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
#ifdef DEXTOP_GUEST_GPU_PROBE
    VkExportFenceCreateInfo export_info = {.sType = VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO,
        .handleTypes = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT};
    fence_info.pNext = &export_info;
#endif
    CHECK(vkCreateFence(device, &fence_info, NULL, &fence));
    VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1, .pCommandBuffers = &command};
    VkQueue queue;
    vkGetDeviceQueue(device, queue_index, 0, &queue);
    CHECK(vkQueueSubmit(queue, 1, &submit, fence));
#ifdef DEXTOP_GUEST_GPU_PROBE
    delayed = (struct delayed_event){device, producer_gate, vkSetEvent};
    if (pthread_create(&signal_thread, NULL, signal_delayed_event, &delayed)) {
        vkSetEvent(device, producer_gate);
        goto cleanup;
    }
    signal_thread_started = 1;
    VkFenceGetFdInfoKHR fence_fd_info = {.sType = VK_STRUCTURE_TYPE_FENCE_GET_FD_INFO_KHR,
        .fence = fence, .handleType = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT};
    int sync_fd = -1;
    CHECK(vkGetFenceFdKHR(device, &fence_fd_info, &sync_fd));
    printf("GUEST_GPU_SUBMITTED_WITH_SYNC_FD fd=%d\n", sync_fd);
    struct pollfd pending = {.fd = sync_fd, .events = POLLIN};
    if (sync_fd < 0 || poll(&pending, 1, 0) != 0) {
        fprintf(stderr, "EXPECTED_PENDING_PRODUCER_FENCE\n");
        if (sync_fd >= 0) close(sync_fd);
        goto cleanup;
    }
    puts("GUEST_ACQUIRE_FENCE_CONFIRMED_PENDING");
    int sent = guest_dmabuf_send_fds(fd, sync_fd);
    if (sync_fd >= 0) close(sync_fd);
    if (sent) goto cleanup;
#else
    CHECK(vkWaitForFences(device, 1, &fence, VK_TRUE, 5000000000ULL));
#endif
    }
    if (window && dmabuf_gpu_blit_window(instance, physical, device, queue_index, get_proc, window, image, acquire_fd))
        goto cleanup;
    map = mmap(NULL, size, PROT_READ, MAP_SHARED, fd, 0);
    if (map == MAP_FAILED) goto cleanup;
    struct dma_buf_sync sync = {.flags = DMA_BUF_SYNC_START | DMA_BUF_SYNC_READ};
    if (ioctl(fd, DMA_BUF_IOCTL_SYNC, &sync) < 0) goto cleanup;
    unsigned matches = 0;
    for (unsigned i = 0; i < 64 * 64; i++) if (((uint32_t *)map)[i] == 0xffff0000u) matches++;
    sync.flags = DMA_BUF_SYNC_END | DMA_BUF_SYNC_READ;
    if (ioctl(fd, DMA_BUF_IOCTL_SYNC, &sync) < 0) goto cleanup;
    printf("DMABUF_GPU_RED_PIXELS=%u/4096 bytes=%zu\n", matches, size);
    if (matches == 4096) {
        puts(external_fd >= 0 ? "DMABUF_EXTERNAL_GPU_IMPORT_OK" : "DMABUF_GPU_IMPORT_CLEAR_OK");
        code = 0;
    }
cleanup:
#ifdef DEXTOP_GUEST_GPU_PROBE
    if (signal_thread_started) pthread_join(signal_thread, NULL);
    else if (producer_gate) vkSetEvent(device, producer_gate);
#endif
    vkDeviceWaitIdle(device);
#ifdef DEXTOP_GUEST_GPU_PROBE
    if (producer_gate) vkDestroyEvent(device, producer_gate, NULL);
#endif
    if (map != MAP_FAILED) munmap(map, size);
    if (fence) vkDestroyFence(device, fence, NULL);
    if (pool) vkDestroyCommandPool(device, pool, NULL);
    if (image) vkDestroyImage(device, image, NULL);
    if (memory) vkFreeMemory(device, memory, NULL);
    if (imported_fd >= 0) close(imported_fd);
    if (fd >= 0) close(fd);
    if (heap >= 0) close(heap);
    vkDestroyDevice(device, NULL);
    return code;
}

int dmabuf_gpu_roundtrip(VkInstance instance, VkPhysicalDevice physical,
                        PFN_vkGetInstanceProcAddr get_proc, ANativeWindow *window) {
    return dmabuf_gpu_import_test(instance, physical, get_proc, window, -1, -1);
}
