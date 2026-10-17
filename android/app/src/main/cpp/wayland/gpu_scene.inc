#include "shaders/scene_spv.h"
#include <math.h>

static bool scene_image_init(struct linux_gpu_image *image) {
    struct linux_gpu *g = image->gpu;
    if (!g->descriptors) {
        VkDescriptorSetLayoutBinding binding = {.binding = 0,
            .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, .descriptorCount = 1,
            .stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT};
        VkDescriptorSetLayoutCreateInfo layout = {.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
            .bindingCount = 1, .pBindings = &binding};
        if (g->CreateDescriptorSetLayout(g->device, &layout, NULL, &g->descriptors) != VK_SUCCESS) return false;
    }
    if (!g->sampler) {
        VkSamplerCreateInfo sampler = {.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
            .magFilter = VK_FILTER_NEAREST, .minFilter = VK_FILTER_NEAREST,
            .mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST,
            .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, .maxLod = 0};
        if (g->CreateSampler(g->device, &sampler, NULL, &g->sampler) != VK_SUCCESS) return false;
    }
    if (!g->descriptor_pool) {
        VkDescriptorPoolSize size = {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 4096};
        VkDescriptorPoolCreateInfo pool = {.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
            .flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT, .maxSets = 4096,
            .poolSizeCount = 1, .pPoolSizes = &size};
        if (g->CreateDescriptorPool(g->device, &pool, NULL, &g->descriptor_pool) != VK_SUCCESS) return false;
    }
    VkImageViewCreateInfo view = {.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, .image = image->image,
        .viewType = VK_IMAGE_VIEW_TYPE_2D, .format = image->format,
        .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
    if (g->CreateImageView(g->device, &view, NULL, &image->view) != VK_SUCCESS) return false;
    VkDescriptorSetAllocateInfo allocate = {.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
        .descriptorPool = g->descriptor_pool, .descriptorSetCount = 1, .pSetLayouts = &g->descriptors};
    if (g->AllocateDescriptorSets(g->device, &allocate, &image->descriptor) != VK_SUCCESS) return false;
    VkDescriptorImageInfo pixels = {.sampler = g->sampler, .imageView = image->view,
        .imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkWriteDescriptorSet write = {.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
        .dstSet = image->descriptor, .dstBinding = 0, .descriptorCount = 1,
        .descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, .pImageInfo = &pixels};
    g->UpdateDescriptorSets(g->device, 1, &write, 0, NULL);
    return true;
}

struct linux_gpu_image *linux_gpu_upload_rgba(struct linux_gpu *g, int width, int height,
    const void *pixels, uint32_t stride) {
    if (!g || g->failed || !pixels || width <= 0 || height <= 0 || width > 16384 || height > 16384 ||
        stride < (uint64_t)width * 4) return NULL;
    VkFormatProperties properties;
    g->GetPhysicalDeviceFormatProperties(g->physical, VK_FORMAT_R8G8B8A8_UNORM, &properties);
    if (!(properties.linearTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)) return NULL;
    struct linux_gpu_image *image = calloc(1, sizeof(*image));
    if (!image) return NULL;
    image->gpu = g; image->refs = 1; image->width = width; image->height = height;
    image->format = VK_FORMAT_R8G8B8A8_UNORM; image->layout = VK_IMAGE_LAYOUT_PREINITIALIZED;
    VkImageCreateInfo info = {.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .imageType = VK_IMAGE_TYPE_2D,
        .format = image->format, .extent = {(uint32_t)width, (uint32_t)height, 1},
        .mipLevels = 1, .arrayLayers = 1, .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = VK_IMAGE_TILING_LINEAR, .usage = VK_IMAGE_USAGE_SAMPLED_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE, .initialLayout = image->layout};
    if (g->CreateImage(g->device, &info, NULL, &image->image) != VK_SUCCESS) goto fail;
    VkMemoryRequirements requirements;
    g->GetImageMemoryRequirements(g->device, image->image, &requirements);
    VkPhysicalDeviceMemoryProperties memory;
    g->GetPhysicalDeviceMemoryProperties(g->physical, &memory);
    uint32_t type = UINT32_MAX;
    for (uint32_t i = 0; i < memory.memoryTypeCount; i++)
        if ((requirements.memoryTypeBits & (1u << i)) &&
            (memory.memoryTypes[i].propertyFlags & (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) ==
            (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) { type = i; break; }
    if (type == UINT32_MAX) goto fail;
    VkMemoryAllocateInfo allocate = {.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = requirements.size, .memoryTypeIndex = type};
    if (g->AllocateMemory(g->device, &allocate, NULL, &image->memory) != VK_SUCCESS ||
        g->BindImageMemory(g->device, image->image, image->memory, 0) != VK_SUCCESS) goto fail;
    void *map = NULL;
    if (g->MapMemory(g->device, image->memory, 0, VK_WHOLE_SIZE, 0, &map) != VK_SUCCESS) goto fail;
    VkImageSubresource subresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT};
    VkSubresourceLayout layout;
    g->GetImageSubresourceLayout(g->device, image->image, &subresource, &layout);
    bool fits = layout.offset + layout.rowPitch * (height - 1) + (uint64_t)width * 4 <= requirements.size;
    if (fits) for (int row = 0; row < height; row++)
        memcpy((char *)map + layout.offset + layout.rowPitch * row, (const char *)pixels + (size_t)stride * row, (size_t)width * 4);
    g->UnmapMemory(g->device, image->memory);
    if (!fits || !scene_image_init(image)) goto fail;
    return image;
fail:
    linux_gpu_image_destroy(image);
    return NULL;
}

static void scene_window_destroy(struct linux_gpu_window *w) {
    struct linux_gpu *g = w->gpu;
    for (unsigned i = 0; i < LINUX_GPU_MAX_DRAWS; i++)
        if (w->scene_waits[i]) g->DestroySemaphore(g->device, w->scene_waits[i], NULL);
    if (w->framebuffers) for (uint32_t i = 0; i < w->image_count; i++)
        if (w->framebuffers[i]) g->DestroyFramebuffer(g->device, w->framebuffers[i], NULL);
    if (w->views) for (uint32_t i = 0; i < w->image_count; i++)
        if (w->views[i]) g->DestroyImageView(g->device, w->views[i], NULL);
    if (w->pipeline) g->DestroyPipeline(g->device, w->pipeline, NULL);
    if (w->pipeline_layout) g->DestroyPipelineLayout(g->device, w->pipeline_layout, NULL);
    if (w->render_pass) g->DestroyRenderPass(g->device, w->render_pass, NULL);
    free(w->views); free(w->framebuffers);
}

static bool scene_window_init(struct linux_gpu_window *w) {
    if (w->pipeline) return true;
    struct linux_gpu *g = w->gpu;
    VkShaderModule vertex = VK_NULL_HANDLE, fragment = VK_NULL_HANDLE;
    VkAttachmentDescription attachment = {.format = w->format, .samples = VK_SAMPLE_COUNT_1_BIT,
        .loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR, .storeOp = VK_ATTACHMENT_STORE_OP_STORE,
        .stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE, .stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE,
        .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED, .finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR};
    VkAttachmentReference reference = {.attachment = 0, .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass = {.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS,
        .colorAttachmentCount = 1, .pColorAttachments = &reference};
    VkSubpassDependency dependency = {.srcSubpass = VK_SUBPASS_EXTERNAL, .dstSubpass = 0,
        .srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        .dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
        .dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT};
    VkRenderPassCreateInfo pass = {.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &attachment, .subpassCount = 1, .pSubpasses = &subpass,
        .dependencyCount = 1, .pDependencies = &dependency};
    if (g->CreateRenderPass(g->device, &pass, NULL, &w->render_pass) != VK_SUCCESS) goto fail;
    w->views = calloc(w->image_count, sizeof(*w->views));
    w->framebuffers = calloc(w->image_count, sizeof(*w->framebuffers));
    if (!w->views || !w->framebuffers) goto fail;
    for (uint32_t i = 0; i < w->image_count; i++) {
        VkImageViewCreateInfo view = {.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO, .image = w->images[i],
            .viewType = VK_IMAGE_VIEW_TYPE_2D, .format = w->format,
            .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
        if (g->CreateImageView(g->device, &view, NULL, &w->views[i]) != VK_SUCCESS) goto fail;
        VkFramebufferCreateInfo framebuffer = {.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
            .renderPass = w->render_pass, .attachmentCount = 1, .pAttachments = &w->views[i],
            .width = w->extent.width, .height = w->extent.height, .layers = 1};
        if (g->CreateFramebuffer(g->device, &framebuffer, NULL, &w->framebuffers[i]) != VK_SUCCESS) goto fail;
    }
    VkPushConstantRange push = {.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, .size = 48};
    VkPipelineLayoutCreateInfo layout = {.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
        .setLayoutCount = 1, .pSetLayouts = &g->descriptors, .pushConstantRangeCount = 1, .pPushConstantRanges = &push};
    if (g->CreatePipelineLayout(g->device, &layout, NULL, &w->pipeline_layout) != VK_SUCCESS) goto fail;
    VkShaderModuleCreateInfo shader = {.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
        .codeSize = sizeof(scene_vert), .pCode = scene_vert};
    if (g->CreateShaderModule(g->device, &shader, NULL, &vertex) != VK_SUCCESS) goto fail;
    shader.codeSize = sizeof(scene_frag); shader.pCode = scene_frag;
    if (g->CreateShaderModule(g->device, &shader, NULL, &fragment) != VK_SUCCESS) goto fail;
    VkPipelineShaderStageCreateInfo stages[] = {
        {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_VERTEX_BIT,
            .module = vertex, .pName = "main"},
        {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO, .stage = VK_SHADER_STAGE_FRAGMENT_BIT,
            .module = fragment, .pName = "main"}};
    VkPipelineVertexInputStateCreateInfo input = {.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
    VkPipelineInputAssemblyStateCreateInfo assembly = {.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
        .topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST};
    VkPipelineViewportStateCreateInfo viewport = {.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
        .viewportCount = 1, .scissorCount = 1};
    VkPipelineRasterizationStateCreateInfo raster = {.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
        .polygonMode = VK_POLYGON_MODE_FILL, .cullMode = VK_CULL_MODE_NONE, .frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE, .lineWidth = 1};
    VkPipelineMultisampleStateCreateInfo multisample = {.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO,
        .rasterizationSamples = VK_SAMPLE_COUNT_1_BIT};
    VkPipelineColorBlendAttachmentState blend = {.blendEnable = VK_TRUE,
        .srcColorBlendFactor = VK_BLEND_FACTOR_ONE, .dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
        .colorBlendOp = VK_BLEND_OP_ADD, .srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE,
        .dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, .alphaBlendOp = VK_BLEND_OP_ADD, .colorWriteMask = 15};
    VkPipelineColorBlendStateCreateInfo color = {.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
        .attachmentCount = 1, .pAttachments = &blend};
    VkDynamicState dynamic_states[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamic = {.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
        .dynamicStateCount = 2, .pDynamicStates = dynamic_states};
    VkGraphicsPipelineCreateInfo pipeline = {.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
        .stageCount = 2, .pStages = stages, .pVertexInputState = &input, .pInputAssemblyState = &assembly,
        .pViewportState = &viewport, .pRasterizationState = &raster, .pMultisampleState = &multisample,
        .pColorBlendState = &color, .pDynamicState = &dynamic, .layout = w->pipeline_layout, .renderPass = w->render_pass};
    VkResult result = g->CreateGraphicsPipelines(g->device, VK_NULL_HANDLE, 1, &pipeline, NULL, &w->pipeline);
    g->DestroyShaderModule(g->device, vertex, NULL); g->DestroyShaderModule(g->device, fragment, NULL);
    if (result != VK_SUCCESS) { error("scene pipeline", result); return false; }
    return true;
fail:
    if (vertex) g->DestroyShaderModule(g->device, vertex, NULL);
    if (fragment) g->DestroyShaderModule(g->device, fragment, NULL);
    return false;
}

int linux_gpu_present_scene(struct linux_gpu_window *w, const struct linux_gpu_draw *draws,
    unsigned count, int scene_width, int scene_height, int *release_fd) {
    if (release_fd) *release_fd = -1;
    if (!w || w->failed || w->gpu->failed || !draws || !count || count > LINUX_GPU_MAX_DRAWS ||
        !release_fd || scene_width <= 0 || scene_height <= 0) return -1;
    struct linux_gpu *g = w->gpu;
    VkResult r = VK_ERROR_INITIALIZATION_FAILED;
    for (unsigned i = 0; i < count; i++) {
        const struct linux_gpu_draw *d = &draws[i];
        if (!d->image || d->image->gpu != g || !isfinite(d->sx + d->sy + d->sw + d->sh +
            d->x + d->y + d->width + d->height) || d->sx < 0 || d->sy < 0 || d->sw <= 0 || d->sh <= 0 ||
            d->sx + d->sw > d->image->width || d->sy + d->sh > d->image->height || d->width <= 0 || d->height <= 0) return -1;
    }
    if (w->submitted) {
        r = g->GetFenceStatus(g->device, w->fence);
        if (r == VK_NOT_READY) return 0;
        if (r != VK_SUCCESS) goto fail;
    }
    if (!scene_window_init(w)) goto fail;
    if ((r = g->ResetCommandPool(g->device, w->pool, 0)) != VK_SUCCESS) goto fail;
    uint32_t index;
    r = g->AcquireNextImageKHR(g->device, w->swapchain, 0, w->acquired, VK_NULL_HANDLE, &index);
    if (r == VK_NOT_READY || r == VK_TIMEOUT) return 0;
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) goto fail;
    VkSemaphore waits[LINUX_GPU_MAX_DRAWS + 1] = {w->acquired};
    VkPipelineStageFlags wait_stages[LINUX_GPU_MAX_DRAWS + 1] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    unsigned wait_count = 1;
    VkImageMemoryBarrier barriers[LINUX_GPU_MAX_DRAWS];
    struct linux_gpu_image *images[LINUX_GPU_MAX_DRAWS];
    unsigned unique = 0;
    for (unsigned i = 0; i < count; i++) {
        struct linux_gpu_image *image = draws[i].image;
        bool duplicate = false;
        for (unsigned j = 0; j < unique; j++) if (images[j] == image) duplicate = true;
        if (duplicate) continue;
        images[unique] = image;
        barriers[unique++] = (VkImageMemoryBarrier){.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .srcAccessMask = image->layout == VK_IMAGE_LAYOUT_PREINITIALIZED ? VK_ACCESS_HOST_WRITE_BIT : 0,
            .dstAccessMask = VK_ACCESS_SHADER_READ_BIT, .oldLayout = image->layout,
            .newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            .srcQueueFamilyIndex = image->external ? VK_QUEUE_FAMILY_FOREIGN_EXT : VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = image->external ? g->family : VK_QUEUE_FAMILY_IGNORED,
            .image = image->image, .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}};
        if (draws[i].acquire_fd < 0) continue;
        VkSemaphoreCreateInfo semaphore = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        if (!w->scene_waits[i] && (r = g->CreateSemaphore(g->device, &semaphore, NULL, &w->scene_waits[i])) != VK_SUCCESS) goto fail;
        int fd = dup(draws[i].acquire_fd);
        if (fd < 0) goto fail;
        VkImportSemaphoreFdInfoKHR import = {.sType = VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_FD_INFO_KHR,
            .semaphore = w->scene_waits[i], .flags = VK_SEMAPHORE_IMPORT_TEMPORARY_BIT,
            .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT, .fd = fd};
        r = g->ImportSemaphoreFdKHR(g->device, &import);
        if (r != VK_SUCCESS) { close(fd); goto fail; }
        waits[wait_count] = w->scene_waits[i];
        wait_stages[wait_count++] = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    }
    VkCommandBufferBeginInfo begin = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT};
    if ((r = g->BeginCommandBuffer(w->command, &begin)) != VK_SUCCESS) goto fail;
    g->CmdPipelineBarrier(w->command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT | VK_PIPELINE_STAGE_HOST_BIT,
        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, 0, NULL, 0, NULL, unique, barriers);
    VkClearValue clear = {.color = {.float32 = {0, 0, 0, 1}}};
    VkRenderPassBeginInfo pass = {.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO,
        .renderPass = w->render_pass, .framebuffer = w->framebuffers[index],
        .renderArea = {.extent = w->extent}, .clearValueCount = 1, .pClearValues = &clear};
    g->CmdBeginRenderPass(w->command, &pass, VK_SUBPASS_CONTENTS_INLINE);
    g->CmdBindPipeline(w->command, VK_PIPELINE_BIND_POINT_GRAPHICS, w->pipeline);
    VkViewport viewport = {.width = w->extent.width, .height = w->extent.height, .maxDepth = 1};
    VkRect2D scissor = {.extent = w->extent};
    g->CmdSetViewport(w->command, 0, 1, &viewport); g->CmdSetScissor(w->command, 0, 1, &scissor);
    for (unsigned i = 0; i < count; i++) {
        const struct linux_gpu_draw *d = &draws[i];
        float push[12] = {d->sx / d->image->width, d->sy / d->image->height,
            d->sw / d->image->width, d->sh / d->image->height,
            -1 + 2 * d->x / scene_width, -1 + 2 * d->y / scene_height,
            2 * d->width / scene_width, 2 * d->height / scene_height, d->image->opaque ? 1 : 0};
        g->CmdBindDescriptorSets(w->command, VK_PIPELINE_BIND_POINT_GRAPHICS, w->pipeline_layout,
            0, 1, &d->image->descriptor, 0, NULL);
        g->CmdPushConstants(w->command, w->pipeline_layout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
            0, sizeof(push), push);
        g->CmdDraw(w->command, 6, 1, 0, 0);
    }
    g->CmdEndRenderPass(w->command);
    for (unsigned i = 0; i < unique; i++) {
        barriers[i].srcAccessMask = VK_ACCESS_SHADER_READ_BIT; barriers[i].dstAccessMask = 0;
        barriers[i].oldLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; barriers[i].newLayout = VK_IMAGE_LAYOUT_GENERAL;
        barriers[i].srcQueueFamilyIndex = images[i]->external ? g->family : VK_QUEUE_FAMILY_IGNORED;
        barriers[i].dstQueueFamilyIndex = images[i]->external ? VK_QUEUE_FAMILY_FOREIGN_EXT : VK_QUEUE_FAMILY_IGNORED;
    }
    g->CmdPipelineBarrier(w->command, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, NULL, 0, NULL, unique, barriers);
    if ((r = g->EndCommandBuffer(w->command)) != VK_SUCCESS) goto fail;
    VkSemaphore signals[] = {w->rendered[index], w->release};
    VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .waitSemaphoreCount = wait_count,
        .pWaitSemaphores = waits, .pWaitDstStageMask = wait_stages,
        .commandBufferCount = 1, .pCommandBuffers = &w->command, .signalSemaphoreCount = 2, .pSignalSemaphores = signals};
    if ((r = g->ResetFences(g->device, 1, &w->fence)) != VK_SUCCESS ||
        (r = g->QueueSubmit(g->queue, 1, &submit, w->fence)) != VK_SUCCESS) goto fail;
    w->submitted = true;
    for (unsigned i = 0; i < unique; i++) images[i]->layout = VK_IMAGE_LAYOUT_GENERAL;
    VkSemaphoreGetFdInfoKHR export = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR,
        .semaphore = w->release, .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    if ((r = g->GetSemaphoreFdKHR(g->device, &export, release_fd)) != VK_SUCCESS) goto fail;
    VkPresentInfoKHR present = {.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1, .pWaitSemaphores = &w->rendered[index], .swapchainCount = 1,
        .pSwapchains = &w->swapchain, .pImageIndices = &index};
    r = g->QueuePresentKHR(g->queue, &present);
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) goto fail;
    return 1;
fail:
    error("Linux GPU scene", r); w->failed = true;
    if (r == VK_ERROR_DEVICE_LOST) g->failed = true;
    g->DeviceWaitIdle(g->device);
    if (*release_fd >= 0) { close(*release_fd); *release_fd = -1; }
    return -1;
}
