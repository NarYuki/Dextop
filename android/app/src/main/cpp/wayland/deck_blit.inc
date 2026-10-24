static int deck_present_blit(struct linux_gpu_window *w, const struct linux_gpu_draw *draw, int *release_fd) {
    struct linux_gpu *g = w->gpu;
    struct linux_gpu_image *image = draw->image;
    VkResult r;
    if (w->submitted) {
        r = g->GetFenceStatus(g->device, w->fence);
        if (r == VK_NOT_READY) return 0;
        if (r != VK_SUCCESS) goto fail;
    }
    if ((r = g->ResetCommandPool(g->device, w->pool, 0)) != VK_SUCCESS) goto fail;
    uint32_t index;
    bool output_wait = false;
    int taken = deck_output_take(w, &index, &output_wait);
    if (!taken) return 0;
    if (taken < 0) { r = VK_ERROR_INVALID_EXTERNAL_HANDLE; goto fail; }
    if (draw->acquire_fd >= 0) {
        int fd = dup(draw->acquire_fd);
        if (fd < 0) { r = VK_ERROR_OUT_OF_HOST_MEMORY; goto fail; }
        VkImportSemaphoreFdInfoKHR import = {.sType = VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_FD_INFO_KHR,
            .semaphore = w->producer, .flags = VK_SEMAPHORE_IMPORT_TEMPORARY_BIT,
            .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT, .fd = fd};
        r = g->ImportSemaphoreFdKHR(g->device, &import);
        if (r != VK_SUCCESS) { close(fd); goto fail; }
    }
    VkCommandBufferBeginInfo begin = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT};
    if ((r = g->BeginCommandBuffer(w->command, &begin)) != VK_SUCCESS) goto fail;
    bool reused = w->deck_initialized[index];
    VkImageMemoryBarrier barriers[2] = {
        {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
            .oldLayout = image->layout, .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT, .dstQueueFamilyIndex = g->family,
            .image = image->image, .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}},
        {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .oldLayout = reused ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_UNDEFINED,
            .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            .srcQueueFamilyIndex = reused ? VK_QUEUE_FAMILY_FOREIGN_EXT : VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = reused ? g->family : VK_QUEUE_FAMILY_IGNORED,
            .image = w->images[index], .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}}};
    g->CmdPipelineBarrier(w->command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, NULL, 0, NULL, 2, barriers);
    int x = (int)draw->sx, y = (int)draw->sy;
    VkImageBlit blit = {.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
        .srcOffsets = {{x, y, 0}, {x + (int)draw->sw, y + (int)draw->sh, 1}},
        .dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
        .dstOffsets = {{0, 0, 0}, {(int32_t)w->extent.width, (int32_t)w->extent.height, 1}}};
    g->CmdBlitImage(w->command, image->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        w->images[index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_NEAREST);
    for (unsigned i = 0; i < 2; i++) {
        barriers[i].srcAccessMask = i ? VK_ACCESS_TRANSFER_WRITE_BIT : VK_ACCESS_TRANSFER_READ_BIT;
        barriers[i].dstAccessMask = 0;
        barriers[i].oldLayout = i ? VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL : VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        barriers[i].newLayout = VK_IMAGE_LAYOUT_GENERAL;
        barriers[i].srcQueueFamilyIndex = g->family;
        barriers[i].dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
    }
    g->CmdPipelineBarrier(w->command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, NULL, 0, NULL, 2, barriers);
    if ((r = g->EndCommandBuffer(w->command)) != VK_SUCCESS) goto fail;
    VkSemaphore waits[2] = {w->acquired, w->producer};
    unsigned wait_count = output_wait ? 1 : 0;
    if (draw->acquire_fd >= 0) waits[wait_count++] = w->producer;
    VkPipelineStageFlags stages[2] = {VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT};
    VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .waitSemaphoreCount = wait_count, .pWaitSemaphores = waits, .pWaitDstStageMask = stages,
        .commandBufferCount = 1, .pCommandBuffers = &w->command, .signalSemaphoreCount = 1, .pSignalSemaphores = &w->release};
    if ((r = g->ResetFences(g->device, 1, &w->fence)) != VK_SUCCESS ||
        (r = g->QueueSubmit(g->queue, 1, &submit, w->fence)) != VK_SUCCESS) goto fail;
    w->submitted = true;
    image->layout = VK_IMAGE_LAYOUT_GENERAL;
    w->deck_initialized[index] = true;
    VkSemaphoreGetFdInfoKHR export = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR,
        .semaphore = w->release, .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    if ((r = g->GetSemaphoreFdKHR(g->device, &export, release_fd)) != VK_SUCCESS) goto fail;
    if (!deck_output_present(w, index, *release_fd, true)) goto fail;
    if (!w->deck_blit_logged) {
        w->deck_blit_logged = true;
        __android_log_print(ANDROID_LOG_INFO, "DextopLinuxGpu", "Deck fullscreen opaque GPU blit (scene shader bypassed)");
    }
    return 1;
fail:
    error("Deck GPU blit", r);
    w->failed = true;
    if (r == VK_ERROR_DEVICE_LOST) g->failed = true;
    g->DeviceWaitIdle(g->device);
    if (*release_fd >= 0) { close(*release_fd); *release_fd = -1; }
    return -1;
}
