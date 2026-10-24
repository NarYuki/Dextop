/* Deck-only SurfaceControl output. The pool owns gralloc buffers independently
 * of Vulkan; transaction callbacks may outlive a replaced compositor window.
 * Like DroidDeck's layer-copy path, GPU completion releases the input images,
 * while SurfaceFlinger's previous-buffer fence controls output-slot reuse. */
static void deck_output_unref(struct deck_output *d) {
    if (atomic_fetch_sub(&d->refs, 1) != 1) return;
    for (unsigned i = 0; i < DECK_OUTPUT_SLOTS; i++) {
        if (d->release_fds[i] >= 0) close(d->release_fds[i]);
        if (d->buffers[i]) AHardwareBuffer_release(d->buffers[i]);
    }
    if (d->control) ASurfaceControl_release(d->control);
    pthread_mutex_destroy(&d->lock);
    free(d);
}

static void deck_output_destroy(struct linux_gpu_window *w) {
    if (!w->deck) return;
    struct deck_output *d = w->deck;
    ASurfaceTransaction *tx = ASurfaceTransaction_create();
    ASurfaceTransaction_setVisibility(tx, d->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
    ASurfaceTransaction_setBuffer(tx, d->control, NULL, -1);
    ASurfaceTransaction_reparent(tx, d->control, NULL);
    ASurfaceTransaction_apply(tx);
    ASurfaceTransaction_delete(tx);
    for (unsigned i = 0; i < DECK_OUTPUT_SLOTS; i++) {
        if (w->images && w->images[i]) w->gpu->DestroyImage(w->gpu->device, w->images[i], NULL);
        if (w->deck_memory[i]) w->gpu->FreeMemory(w->gpu->device, w->deck_memory[i], NULL);
        w->deck_memory[i] = VK_NULL_HANDLE;
    }
    w->deck = NULL;
    deck_output_unref(d);
}

static bool deck_output_buffer_init(struct linux_gpu_window *w, unsigned i, uint64_t usage) {
    struct linux_gpu *g = w->gpu;
    struct deck_output *d = w->deck;
    AHardwareBuffer_Desc desc = {.width = w->extent.width, .height = w->extent.height,
        .layers = 1, .format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM, .usage = usage};
    if (AHardwareBuffer_allocate(&desc, &d->buffers[i])) return false;
    VkAndroidHardwareBufferFormatPropertiesANDROID format = {
        .sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID};
    VkAndroidHardwareBufferPropertiesANDROID props = {
        .sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID, .pNext = &format};
    if (g->GetAndroidHardwareBufferPropertiesANDROID(g->device, d->buffers[i], &props) != VK_SUCCESS ||
        format.format != w->format || !props.memoryTypeBits ||
        !(format.formatFeatures & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT)) goto fail;
    bool blit = (format.formatFeatures & VK_FORMAT_FEATURE_BLIT_DST_BIT) != 0;
    VkExternalMemoryImageCreateInfo external = {.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
        .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID};
    VkImageCreateInfo image = {.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &external,
        .imageType = VK_IMAGE_TYPE_2D, .format = w->format,
        .extent = {w->extent.width, w->extent.height, 1}, .mipLevels = 1, .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT, .tiling = VK_IMAGE_TILING_OPTIMAL,
        .usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | (blit ? VK_IMAGE_USAGE_TRANSFER_DST_BIT : 0),
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE, .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED};
    if (g->CreateImage(g->device, &image, NULL, &w->images[i]) != VK_SUCCESS) goto fail;
    VkImportAndroidHardwareBufferInfoANDROID imported = {
        .sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID, .buffer = d->buffers[i]};
    VkMemoryDedicatedAllocateInfo dedicated = {.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
        .pNext = &imported, .image = w->images[i]};
    VkMemoryAllocateInfo allocate = {.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = &dedicated,
        .allocationSize = props.allocationSize, .memoryTypeIndex = (uint32_t)__builtin_ctz(props.memoryTypeBits)};
    if (g->AllocateMemory(g->device, &allocate, NULL, &w->deck_memory[i]) != VK_SUCCESS ||
        g->BindImageMemory(g->device, w->images[i], w->deck_memory[i], 0) != VK_SUCCESS) goto fail;
    w->deck_can_blit = w->deck_can_blit && blit;
    return true;
fail:
    if (w->images[i]) g->DestroyImage(g->device, w->images[i], NULL);
    if (w->deck_memory[i]) g->FreeMemory(g->device, w->deck_memory[i], NULL);
    AHardwareBuffer_release(d->buffers[i]);
    w->images[i] = VK_NULL_HANDLE; w->deck_memory[i] = VK_NULL_HANDLE; d->buffers[i] = NULL;
    return false;
}

static bool deck_output_init(struct linux_gpu_window *w) {
    struct linux_gpu *g = w->gpu;
    if (!g->GetAndroidHardwareBufferPropertiesANDROID) return false;
    ASurfaceControl *control = ASurfaceControl_createFromWindow(w->window, "Dextop Deck GPU");
    if (!control) return false;
    struct deck_output *d = calloc(1, sizeof(*d));
    if (!d) { ASurfaceControl_release(control); return false; }
    pthread_mutex_init(&d->lock, NULL);
    atomic_init(&d->refs, 1);
    d->control = control;
    d->current = -1;
    for (unsigned i = 0; i < DECK_OUTPUT_SLOTS; i++) d->release_fds[i] = -1;
    w->deck = d;
    w->extent = (VkExtent2D){ANativeWindow_getWidth(w->window), ANativeWindow_getHeight(w->window)};
    if (!w->extent.width || !w->extent.height || w->extent.width > 16384 || w->extent.height > 16384) goto fail;
    w->format = VK_FORMAT_R8G8B8A8_UNORM;
    w->deck_can_blit = true;
    w->image_count = DECK_OUTPUT_SLOTS;
    w->images = calloc(DECK_OUTPUT_SLOTS, sizeof(*w->images));
    if (!w->images) goto fail;
    const uint64_t base_usage = AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT |
        AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE | AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY;
    /* DroidDeck's Qualcomm gralloc UBWC request; rejected layouts fall back to the standard allocation. */
    const uint64_t ubwc_usage = UINT64_C(1) << 28;
    unsigned requested = 0;
    for (unsigned i = 0; i < DECK_OUTPUT_SLOTS; i++) {
        if (deck_output_buffer_init(w, i, base_usage | ubwc_usage)) requested++;
        else if (!deck_output_buffer_init(w, i, base_usage)) goto fail;
    }
    __android_log_print(ANDROID_LOG_INFO, "DextopLinuxGpu", "Deck output UBWC request accepted/imported slots=%u/%d",
        requested, DECK_OUTPUT_SLOTS);
    __android_log_print(ANDROID_LOG_INFO, "DextopLinuxGpu", "Deck AHB SurfaceControl output %ux%u slots=%d (no FIFO swapchain)",
        w->extent.width, w->extent.height, DECK_OUTPUT_SLOTS);
    return true;
fail:
    deck_output_destroy(w);
    free(w->images); w->images = NULL; w->image_count = 0;
    return false;
}

/* Never wait on the compositor thread for SurfaceFlinger. The currently displayed
 * buffer cannot be reused until a later transaction's previous-buffer callback. */
#include "deck_slot_wait.inc"

struct deck_complete { struct deck_output *output; int previous; };
static void deck_output_complete(void *context, ASurfaceTransactionStats *stats) {
    struct deck_complete *c = context;
    struct deck_output *d = c->output;
    if (c->previous >= 0) {
        int fd = ASurfaceTransactionStats_getPreviousReleaseFenceFd(stats, d->control);
        pthread_mutex_lock(&d->lock);
        if (d->release_fds[c->previous] >= 0) close(d->release_fds[c->previous]);
        d->release_fds[c->previous] = fd;
        d->busy[c->previous] = false;
        pthread_mutex_unlock(&d->lock);
    }
    deck_output_unref(d);
    free(c);
}

static bool deck_output_present(struct linux_gpu_window *w, uint32_t index, int gpu_fd, bool opaque) {
    struct deck_output *d = w->deck;
    struct deck_complete *c = calloc(1, sizeof(*c));
    if (!c) return false;
    int acquire = gpu_fd >= 0 ? dup(gpu_fd) : -1;
    if (gpu_fd >= 0 && acquire < 0) { free(c); return false; }
    ASurfaceTransaction *tx = ASurfaceTransaction_create();
    c->output = d; c->previous = d->current;
    atomic_fetch_add(&d->refs, 1);
    ASurfaceTransaction_setBuffer(tx, d->control, d->buffers[index], acquire);
    ARect rect = {0, 0, (int32_t)w->extent.width, (int32_t)w->extent.height};
    ASurfaceTransaction_setGeometry(tx, d->control, &rect, &rect, 0);
    ASurfaceTransaction_setBufferTransparency(tx, d->control, opaque ?
        ASURFACE_TRANSACTION_TRANSPARENCY_OPAQUE : ASURFACE_TRANSACTION_TRANSPARENCY_TRANSLUCENT);
    ASurfaceTransaction_setVisibility(tx, d->control, ASURFACE_TRANSACTION_VISIBILITY_SHOW);
    ASurfaceTransaction_setOnComplete(tx, c, deck_output_complete);
    d->current = (int)index;
    ASurfaceTransaction_apply(tx);
    ASurfaceTransaction_delete(tx);
    return true;
}
