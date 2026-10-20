#include <android/native_window.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <vulkan/vulkan.h>

int dmabuf_gpu_blit_window(VkInstance instance, VkPhysicalDevice physical, VkDevice device,
                          uint32_t queue_index, PFN_vkGetInstanceProcAddr get_proc,
                          ANativeWindow *window, VkImage source, int acquire_fd) {
#define INSTANCE(name) PFN_##name name = (PFN_##name)get_proc(instance, #name)
    INSTANCE(vkCreateAndroidSurfaceKHR); INSTANCE(vkDestroySurfaceKHR);
    INSTANCE(vkGetPhysicalDeviceSurfaceSupportKHR); INSTANCE(vkGetPhysicalDeviceSurfaceCapabilitiesKHR);
    INSTANCE(vkGetPhysicalDeviceSurfaceFormatsKHR); INSTANCE(vkGetDeviceProcAddr);
#undef INSTANCE
#define DEVICE(name) PFN_##name name = (PFN_##name)vkGetDeviceProcAddr(device, #name)
    DEVICE(vkCreateSwapchainKHR); DEVICE(vkDestroySwapchainKHR); DEVICE(vkGetSwapchainImagesKHR);
    DEVICE(vkAcquireNextImageKHR); DEVICE(vkQueuePresentKHR);
    DEVICE(vkCreateSemaphore); DEVICE(vkDestroySemaphore); DEVICE(vkGetDeviceQueue);
    DEVICE(vkImportSemaphoreFdKHR);
    DEVICE(vkCreateCommandPool); DEVICE(vkDestroyCommandPool); DEVICE(vkAllocateCommandBuffers);
    DEVICE(vkBeginCommandBuffer); DEVICE(vkEndCommandBuffer); DEVICE(vkCmdPipelineBarrier);
    DEVICE(vkCmdBlitImage); DEVICE(vkQueueSubmit); DEVICE(vkQueueWaitIdle);
#undef DEVICE
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkSemaphore acquired = VK_NULL_HANDLE, rendered = VK_NULL_HANDLE, producer = VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkImage *images = NULL;
    VkSurfaceFormatKHR *formats = NULL;
    VkQueue queue;
    vkGetDeviceQueue(device, queue_index, 0, &queue);
    int code = 1;
    VkResult result;
#define CHECK(call) do { result = (call); if (result != VK_SUCCESS) { \
    fprintf(stderr, "%s=%d\n", #call, result); goto cleanup; } } while (0)
    VkAndroidSurfaceCreateInfoKHR android_surface = {.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
        .window = window};
    CHECK(vkCreateAndroidSurfaceKHR(instance, &android_surface, NULL, &surface));
    VkBool32 supported = VK_FALSE;
    CHECK(vkGetPhysicalDeviceSurfaceSupportKHR(physical, queue_index, surface, &supported));
    if (!supported) { fprintf(stderr, "QUEUE_CANNOT_PRESENT\n"); goto cleanup; }
    VkSurfaceCapabilitiesKHR caps;
    CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, &caps));
    if (!(caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT)) goto cleanup;
    uint32_t count = 0;
    CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &count, NULL));
    if (!count) goto cleanup;
    formats = calloc(count, sizeof(*formats));
    if (!formats) goto cleanup;
    CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &count, formats));
    VkSurfaceFormatKHR format = formats[0];
    for (uint32_t i = 0; i < count; i++) if (formats[i].format == VK_FORMAT_R8G8B8A8_UNORM) { format = formats[i]; break; }
    VkExtent2D extent = caps.currentExtent;
    if (extent.width == UINT32_MAX) extent = (VkExtent2D){64, 64};
    uint32_t image_count = caps.minImageCount + 1;
    if (caps.maxImageCount && image_count > caps.maxImageCount) image_count = caps.maxImageCount;
    VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    if (!(caps.supportedCompositeAlpha & alpha)) alpha = (VkCompositeAlphaFlagBitsKHR)(caps.supportedCompositeAlpha & -caps.supportedCompositeAlpha);
    VkSwapchainCreateInfoKHR swapchain_info = {.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
        .surface = surface, .minImageCount = image_count, .imageFormat = format.format,
        .imageColorSpace = format.colorSpace, .imageExtent = extent, .imageArrayLayers = 1,
        .imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT, .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .preTransform = caps.currentTransform, .compositeAlpha = alpha,
        .presentMode = VK_PRESENT_MODE_FIFO_KHR, .clipped = VK_TRUE};
    CHECK(vkCreateSwapchainKHR(device, &swapchain_info, NULL, &swapchain));
    CHECK(vkGetSwapchainImagesKHR(device, swapchain, &count, NULL));
    images = calloc(count, sizeof(*images));
    if (!images) goto cleanup;
    CHECK(vkGetSwapchainImagesKHR(device, swapchain, &count, images));
    VkSemaphoreCreateInfo semaphore_info = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    CHECK(vkCreateSemaphore(device, &semaphore_info, NULL, &acquired));
    CHECK(vkCreateSemaphore(device, &semaphore_info, NULL, &rendered));
    if (acquire_fd >= 0) {
        CHECK(vkCreateSemaphore(device, &semaphore_info, NULL, &producer));
        int imported_fd = dup(acquire_fd);
        if (imported_fd < 0) goto cleanup;
        VkImportSemaphoreFdInfoKHR import = {.sType = VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_FD_INFO_KHR,
            .semaphore = producer, .flags = VK_SEMAPHORE_IMPORT_TEMPORARY_BIT,
            .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT, .fd = imported_fd};
        result = vkImportSemaphoreFdKHR(device, &import);
        if (result != VK_SUCCESS) { close(imported_fd); goto cleanup; }
    }
    uint32_t image_index;
    CHECK(vkAcquireNextImageKHR(device, swapchain, 5000000000ULL, acquired, VK_NULL_HANDLE, &image_index));
    VkCommandPoolCreateInfo pool_info = {.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .queueFamilyIndex = queue_index};
    CHECK(vkCreateCommandPool(device, &pool_info, NULL, &pool));
    VkCommandBufferAllocateInfo command_info = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
    VkCommandBuffer command;
    CHECK(vkAllocateCommandBuffers(device, &command_info, &command));
    VkCommandBufferBeginInfo begin = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    CHECK(vkBeginCommandBuffer(command, &begin));
    VkImageMemoryBarrier barriers[2] = {
        {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_GENERAL, .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT, .dstQueueFamilyIndex = queue_index,
            .image = source, .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}},
        {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED, .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = images[image_index], .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}}};
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, NULL, 0, NULL, 2, barriers);
    VkImageBlit blit = {.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
        .srcOffsets = {{0, 0, 0}, {64, 64, 1}}, .dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
        .dstOffsets = {{0, 0, 0}, {(int32_t)extent.width, (int32_t)extent.height, 1}}};
    vkCmdBlitImage(command, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        images[image_index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_NEAREST);
    barriers[0].srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    barriers[0].dstAccessMask = 0;
    barriers[0].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    barriers[0].newLayout = VK_IMAGE_LAYOUT_GENERAL;
    barriers[0].srcQueueFamilyIndex = queue_index;
    barriers[0].dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
    barriers[1].srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    barriers[1].dstAccessMask = 0;
    barriers[1].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    barriers[1].newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, NULL, 0, NULL, 2, barriers);
    CHECK(vkEndCommandBuffer(command));
    VkPipelineStageFlags wait_stages[] = {VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT};
    VkSemaphore waits[] = {acquired, producer};
    VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .waitSemaphoreCount = producer ? 2 : 1,
        .pWaitSemaphores = waits, .pWaitDstStageMask = wait_stages,
        .commandBufferCount = 1, .pCommandBuffers = &command,
        .signalSemaphoreCount = 1, .pSignalSemaphores = &rendered};
    CHECK(vkQueueSubmit(queue, 1, &submit, VK_NULL_HANDLE));
    if (producer) puts("GUEST_ACQUIRE_SYNC_FD_GPU_WAIT_SUBMITTED");
    VkPresentInfoKHR present = {.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1, .pWaitSemaphores = &rendered, .swapchainCount = 1,
        .pSwapchains = &swapchain, .pImageIndices = &image_index};
    CHECK(vkQueuePresentKHR(queue, &present));
    CHECK(vkQueueWaitIdle(queue));
    printf("DMABUF_GPU_SURFACE_PRESENT_OK extent=%ux%u\n", extent.width, extent.height);
    code = 0;
cleanup:
    vkQueueWaitIdle(queue);
    if (pool) vkDestroyCommandPool(device, pool, NULL);
    if (acquired) vkDestroySemaphore(device, acquired, NULL);
    if (rendered) vkDestroySemaphore(device, rendered, NULL);
    if (producer) vkDestroySemaphore(device, producer, NULL);
    if (swapchain) vkDestroySwapchainKHR(device, swapchain, NULL);
    if (surface) vkDestroySurfaceKHR(instance, surface, NULL);
    free(images);
    free(formats);
    return code;
}
