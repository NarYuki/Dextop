#include "gpu_present.h"
#include <adrenotools/driver.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <vulkan/vulkan.h>

#define INSTANCE_FUNCTIONS(X) \
    X(DestroyInstance) X(EnumeratePhysicalDevices) X(GetPhysicalDeviceProperties) \
    X(GetPhysicalDeviceQueueFamilyProperties) X(EnumerateDeviceExtensionProperties) \
    X(GetPhysicalDeviceFormatProperties) X(GetPhysicalDeviceFormatProperties2) X(GetPhysicalDeviceMemoryProperties) X(CreateDevice) X(GetDeviceProcAddr) \
    X(CreateAndroidSurfaceKHR) X(DestroySurfaceKHR) X(GetPhysicalDeviceSurfaceSupportKHR) \
    X(GetPhysicalDeviceSurfaceCapabilitiesKHR) X(GetPhysicalDeviceSurfaceFormatsKHR)
#define DEVICE_FUNCTIONS(X) \
    X(DestroyDevice) X(GetDeviceQueue) X(DeviceWaitIdle) X(CreateImage) X(DestroyImage) \
    X(GetImageMemoryRequirements) X(GetMemoryFdPropertiesKHR) X(AllocateMemory) X(FreeMemory) \
    X(BindImageMemory) X(CreateSwapchainKHR) X(DestroySwapchainKHR) X(GetSwapchainImagesKHR) \
    X(AcquireNextImageKHR) X(QueuePresentKHR) X(CreateSemaphore) X(DestroySemaphore) \
    X(ImportSemaphoreFdKHR) X(GetSemaphoreFdKHR) X(CreateCommandPool) X(DestroyCommandPool) \
    X(ResetCommandPool) X(AllocateCommandBuffers) X(BeginCommandBuffer) X(EndCommandBuffer) \
    X(CmdPipelineBarrier) X(CmdBlitImage) X(QueueSubmit) X(CreateFence) X(DestroyFence) \
    X(GetFenceStatus) X(ResetFences) X(CreateSampler) X(DestroySampler) \
    X(CreateDescriptorSetLayout) X(DestroyDescriptorSetLayout) X(CreateDescriptorPool) X(DestroyDescriptorPool) \
    X(AllocateDescriptorSets) X(FreeDescriptorSets) X(UpdateDescriptorSets) X(CreateImageView) X(DestroyImageView) \
    X(CreateRenderPass) X(DestroyRenderPass) X(CreateFramebuffer) X(DestroyFramebuffer) \
    X(CreateShaderModule) X(DestroyShaderModule) X(CreatePipelineLayout) X(DestroyPipelineLayout) \
    X(CreateGraphicsPipelines) X(DestroyPipeline) X(CmdBeginRenderPass) X(CmdEndRenderPass) \
    X(CmdBindPipeline) X(CmdBindDescriptorSets) X(CmdPushConstants) X(CmdDraw) X(CmdSetViewport) X(CmdSetScissor) \
    X(MapMemory) X(UnmapMemory) X(GetImageSubresourceLayout)

struct linux_gpu {
    void *loader;
    VkInstance instance;
    VkPhysicalDevice physical;
    VkDevice device;
    VkQueue queue;
    uint32_t family;
    bool failed;
    VkSampler sampler;
    VkDescriptorSetLayout descriptors;
    VkDescriptorPool descriptor_pool;
#define FIELD(n) PFN_vk##n n;
    INSTANCE_FUNCTIONS(FIELD)
    DEVICE_FUNCTIONS(FIELD)
#undef FIELD
};
struct linux_gpu_image {
    struct linux_gpu *gpu;
    VkImage image;
    VkDeviceMemory memory;
    int width, height;
    VkImageView view;
    VkDescriptorSet descriptor;
    VkImageLayout layout;
    VkFormat format;
    unsigned refs;
    bool external, opaque;
};
struct linux_gpu_window {
    struct linux_gpu *gpu;
    ANativeWindow *window;
    VkSurfaceKHR surface;
    VkSwapchainKHR swapchain;
    VkExtent2D extent;
    VkImage *images;
    VkSemaphore *rendered;
    uint32_t image_count;
    VkSemaphore acquired, producer, release;
    VkCommandPool pool;
    VkCommandBuffer command;
    VkFence fence;
    bool submitted, failed;
    VkFormat format;
    VkRenderPass render_pass;
    VkPipelineLayout pipeline_layout;
    VkPipeline pipeline;
    VkImageView *views;
    VkFramebuffer *framebuffers;
    VkSemaphore scene_waits[LINUX_GPU_MAX_DRAWS];
};

static bool scene_image_init(struct linux_gpu_image *image);
static void scene_window_destroy(struct linux_gpu_window *window);

static void error(const char *operation, VkResult result) {
    __android_log_print(ANDROID_LOG_WARN, "DextopLinuxGpu", "%s: VkResult=%d", operation, result);
}
static bool extension(const VkExtensionProperties *properties, uint32_t n, const char *name) {
    for (uint32_t i = 0; i < n; i++) if (!strcmp(properties[i].extensionName, name)) return true;
    return false;
}

struct linux_gpu *linux_gpu_create(const char *driver_dir, const char *library, const char *hooks) {
    if (!driver_dir || !library || !hooks) return NULL;
    struct linux_gpu *g = calloc(1, sizeof(*g));
    if (!g) return NULL;
    g->loader = adrenotools_open_libvulkan(RTLD_LOCAL | RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM,
        NULL, hooks, driver_dir, library, NULL, NULL);
    if (!g->loader) goto fail;
    PFN_vkGetInstanceProcAddr proc = (PFN_vkGetInstanceProcAddr)dlsym(g->loader, "vkGetInstanceProcAddr");
    if (!proc) goto fail;
    PFN_vkCreateInstance create = (PFN_vkCreateInstance)proc(NULL, "vkCreateInstance");
    const char *instance_extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "Dextop Linux windows", .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo info = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app,
        .enabledExtensionCount = 2, .ppEnabledExtensionNames = instance_extensions};
    VkResult r = create(&info, NULL, &g->instance);
    if (r != VK_SUCCESS) { error("create instance", r); goto fail; }
#define LOAD(n) g->n = (PFN_vk##n)proc(g->instance, "vk" #n); if (!g->n) goto fail;
    INSTANCE_FUNCTIONS(LOAD)
#undef LOAD
    uint32_t n = 0;
    if (g->EnumeratePhysicalDevices(g->instance, &n, NULL) != VK_SUCCESS || !n) goto fail;
    VkPhysicalDevice *devices = calloc(n, sizeof(*devices));
    if (!devices) goto fail;
    r = g->EnumeratePhysicalDevices(g->instance, &n, devices);
    if (r == VK_SUCCESS) g->physical = devices[0];
    free(devices);
    if (!g->physical) goto fail;
    n = 0;
    if (g->EnumerateDeviceExtensionProperties(g->physical, NULL, &n, NULL) != VK_SUCCESS) goto fail;
    VkExtensionProperties *properties = calloc(n, sizeof(*properties));
    if (!properties) goto fail;
    r = g->EnumerateDeviceExtensionProperties(g->physical, NULL, &n, properties);
    const char *required[] = {VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME,
        VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME,
        VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME, VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME,
        VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    bool supported = r == VK_SUCCESS;
    for (unsigned i = 0; i < sizeof(required) / sizeof(required[0]); i++) {
        if (!extension(properties, n, required[i])) {
            __android_log_print(ANDROID_LOG_WARN, "DextopLinuxGpu", "missing %s", required[i]);
            supported = false;
        }
    }
    free(properties);
    if (!supported) goto fail;
    g->GetPhysicalDeviceQueueFamilyProperties(g->physical, &n, NULL);
    VkQueueFamilyProperties *families = calloc(n, sizeof(*families));
    if (!families) goto fail;
    g->GetPhysicalDeviceQueueFamilyProperties(g->physical, &n, families);
    g->family = UINT32_MAX;
    for (uint32_t i = 0; i < n; i++) if (families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) { g->family = i; break; }
    free(families);
    if (g->family == UINT32_MAX) goto fail;
    float priority = 1;
    VkDeviceQueueCreateInfo queue = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = g->family, .queueCount = 1, .pQueuePriorities = &priority};
    VkDeviceCreateInfo device = {.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1, .pQueueCreateInfos = &queue,
        .enabledExtensionCount = sizeof(required) / sizeof(required[0]), .ppEnabledExtensionNames = required};
    r = g->CreateDevice(g->physical, &device, NULL, &g->device);
    if (r != VK_SUCCESS) { error("create device", r); goto fail; }
#define LOAD(n) g->n = (PFN_vk##n)g->GetDeviceProcAddr(g->device, "vk" #n); if (!g->n) goto fail;
    DEVICE_FUNCTIONS(LOAD)
#undef LOAD
    g->GetDeviceQueue(g->device, g->family, 0, &g->queue);
    VkPhysicalDeviceProperties name;
    g->GetPhysicalDeviceProperties(g->physical, &name);
    __android_log_print(ANDROID_LOG_INFO, "DextopLinuxGpu", "Linux window GPU: %s", name.deviceName);
    return g;
fail:
    linux_gpu_destroy(g);
    return NULL;
}

void linux_gpu_destroy(struct linux_gpu *g) {
    if (!g) return;
    if (g->device && g->DeviceWaitIdle) g->DeviceWaitIdle(g->device);
    if (g->descriptor_pool) g->DestroyDescriptorPool(g->device, g->descriptor_pool, NULL);
    if (g->sampler) g->DestroySampler(g->device, g->sampler, NULL);
    if (g->descriptors) g->DestroyDescriptorSetLayout(g->device, g->descriptors, NULL);
    if (g->device && g->DestroyDevice) g->DestroyDevice(g->device, NULL);
    if (g->instance && g->DestroyInstance) g->DestroyInstance(g->instance, NULL);
    /* Driver namespaces/hooks are process-scoped; retain the loader handle. */
    free(g);
}

struct linux_gpu_image *linux_gpu_import(struct linux_gpu *g, int fd, uint32_t fourcc,
    uint64_t modifier, int width, int height, uint32_t stride, uint32_t offset) {
    if (!g || g->failed || fd < 0 || modifier != 0 || width <= 0 || height <= 0 ||
        width > 16384 || height > 16384 || stride < (uint64_t)width * 4) return NULL;
    VkFormat format;
    switch (fourcc) {
        case 0x34325241: case 0x34325258: format = VK_FORMAT_B8G8R8A8_UNORM; break;
        case 0x34324241: case 0x34324258: format = VK_FORMAT_R8G8B8A8_UNORM; break;
        default: return NULL;
    }
    struct stat stat;
    uint64_t required_bytes = (uint64_t)offset + (uint64_t)stride * (height - 1) + (uint64_t)width * 4;
    if (fstat(fd, &stat) || stat.st_size <= 0 || required_bytes > (uint64_t)stat.st_size) return NULL;
    VkDrmFormatModifierPropertiesListEXT list = {.sType = VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_EXT};
    VkFormatProperties2 format_properties = {.sType = VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2, .pNext = &list};
    g->GetPhysicalDeviceFormatProperties2(g->physical, format, &format_properties);
    VkDrmFormatModifierPropertiesEXT *modifiers = calloc(list.drmFormatModifierCount, sizeof(*modifiers));
    if (!modifiers) return NULL;
    list.pDrmFormatModifierProperties = modifiers;
    g->GetPhysicalDeviceFormatProperties2(g->physical, format, &format_properties);
    bool can_blit = false;
    for (uint32_t i = 0; i < list.drmFormatModifierCount; i++)
        if (modifiers[i].drmFormatModifier == modifier && modifiers[i].drmFormatModifierPlaneCount == 1 &&
            (modifiers[i].drmFormatModifierTilingFeatures & VK_FORMAT_FEATURE_BLIT_SRC_BIT) &&
            (modifiers[i].drmFormatModifierTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)) can_blit = true;
    free(modifiers);
    if (!can_blit) return NULL;
    struct linux_gpu_image *image = calloc(1, sizeof(*image));
    if (!image) return NULL;
    image->gpu = g; image->width = width; image->height = height;
    image->refs = 1; image->external = true; image->format = format;
    image->opaque = fourcc == 0x34325258 || fourcc == 0x34324258;
    image->layout = VK_IMAGE_LAYOUT_GENERAL;
    VkSubresourceLayout plane = {.offset = offset, .rowPitch = stride};
    VkImageDrmFormatModifierExplicitCreateInfoEXT mod = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_DRM_FORMAT_MODIFIER_EXPLICIT_CREATE_INFO_EXT,
        .drmFormatModifier = modifier, .drmFormatModifierPlaneCount = 1, .pPlaneLayouts = &plane};
    VkExternalMemoryImageCreateInfo external = {.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
        .pNext = &mod, .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkImageCreateInfo info = {.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO, .pNext = &external,
        .imageType = VK_IMAGE_TYPE_2D, .format = format, .extent = {(uint32_t)width, (uint32_t)height, 1},
        .mipLevels = 1, .arrayLayers = 1, .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT,
        .usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE, .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED};
    VkResult r = g->CreateImage(g->device, &info, NULL, &image->image);
    if (r != VK_SUCCESS) { error("import create image", r); goto fail; }
    VkMemoryRequirements requirements;
    g->GetImageMemoryRequirements(g->device, image->image, &requirements);
    if (requirements.size > (uint64_t)stat.st_size) goto fail;
    int imported_fd = dup(fd);
    if (imported_fd < 0) goto fail;
    VkMemoryFdPropertiesKHR properties = {.sType = VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR};
    r = g->GetMemoryFdPropertiesKHR(g->device, VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT,
        imported_fd, &properties);
    uint32_t bits = requirements.memoryTypeBits & properties.memoryTypeBits;
    if (r != VK_SUCCESS || !bits) { close(imported_fd); goto fail; }
    VkImportMemoryFdInfoKHR import = {.sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR,
        .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT, .fd = imported_fd};
    VkMemoryDedicatedAllocateInfo dedicated = {.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,
        .pNext = &import, .image = image->image};
    VkMemoryAllocateInfo allocation = {.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .pNext = &dedicated, .allocationSize = requirements.size, .memoryTypeIndex = __builtin_ctz(bits)};
    r = g->AllocateMemory(g->device, &allocation, NULL, &image->memory);
    if (r != VK_SUCCESS) { close(imported_fd); error("import memory", r); goto fail; }
    r = g->BindImageMemory(g->device, image->image, image->memory, 0);
    if (r != VK_SUCCESS) { error("import bind", r); goto fail; }
    if (!scene_image_init(image)) goto fail;
    return image;
fail:
    linux_gpu_image_destroy(image);
    return NULL;
}

void linux_gpu_image_destroy(struct linux_gpu_image *image) {
    if (!image) return;
    if (--image->refs) return;
    struct linux_gpu *g = image->gpu;
    if (image->descriptor) g->FreeDescriptorSets(g->device, g->descriptor_pool, 1, &image->descriptor);
    if (image->view) g->DestroyImageView(g->device, image->view, NULL);
    if (image->image) g->DestroyImage(g->device, image->image, NULL);
    if (image->memory) g->FreeMemory(g->device, image->memory, NULL);
    free(image);
}

void linux_gpu_image_ref(struct linux_gpu_image *image) { if (image) image->refs++; }

struct linux_gpu_window *linux_gpu_window_create(struct linux_gpu *g, ANativeWindow *native) {
    if (!g || g->failed || !native) return NULL;
    struct linux_gpu_window *w = calloc(1, sizeof(*w));
    if (!w) return NULL;
    w->gpu = g; w->window = native;
    ANativeWindow_acquire(native);
    VkAndroidSurfaceCreateInfoKHR info = {.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
        .window = native};
    VkResult r = g->CreateAndroidSurfaceKHR(g->instance, &info, NULL, &w->surface);
    if (r != VK_SUCCESS) goto fail;
    VkBool32 present = VK_FALSE;
    if (g->GetPhysicalDeviceSurfaceSupportKHR(g->physical, g->family, w->surface, &present) != VK_SUCCESS || !present) goto fail;
    VkSurfaceCapabilitiesKHR caps;
    if (g->GetPhysicalDeviceSurfaceCapabilitiesKHR(g->physical, w->surface, &caps) != VK_SUCCESS ||
        !(caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT)) goto fail;
    uint32_t count = 0;
    if (g->GetPhysicalDeviceSurfaceFormatsKHR(g->physical, w->surface, &count, NULL) != VK_SUCCESS || !count) goto fail;
    VkSurfaceFormatKHR *formats = calloc(count, sizeof(*formats));
    if (!formats) goto fail;
    r = g->GetPhysicalDeviceSurfaceFormatsKHR(g->physical, w->surface, &count, formats);
    VkSurfaceFormatKHR format = formats[0];
    w->format = format.format;
    for (uint32_t i = 0; i < count; i++) if (formats[i].format == VK_FORMAT_R8G8B8A8_UNORM) { format = formats[i]; break; }
    free(formats);
    w->format = format.format;
    if (r != VK_SUCCESS) goto fail;
    VkFormatProperties properties;
    g->GetPhysicalDeviceFormatProperties(g->physical, format.format, &properties);
    if (!(properties.optimalTilingFeatures & VK_FORMAT_FEATURE_BLIT_DST_BIT)) goto fail;
    w->extent = caps.currentExtent;
    if (w->extent.width == UINT32_MAX) {
        w->extent.width = ANativeWindow_getWidth(native);
        w->extent.height = ANativeWindow_getHeight(native);
    }
    if (!w->extent.width || !w->extent.height) goto fail;
    uint32_t n = caps.minImageCount + 1;
    if (caps.maxImageCount && n > caps.maxImageCount) n = caps.maxImageCount;
    VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    if (!(caps.supportedCompositeAlpha & alpha)) alpha = (VkCompositeAlphaFlagBitsKHR)(caps.supportedCompositeAlpha & -caps.supportedCompositeAlpha);
    VkSwapchainCreateInfoKHR swap = {.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
        .surface = w->surface, .minImageCount = n, .imageFormat = format.format,
        .imageColorSpace = format.colorSpace, .imageExtent = w->extent, .imageArrayLayers = 1,
        .imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
        .imageSharingMode = VK_SHARING_MODE_EXCLUSIVE,
        .preTransform = caps.currentTransform, .compositeAlpha = alpha, .presentMode = VK_PRESENT_MODE_FIFO_KHR,
        .clipped = VK_TRUE};
    if (g->CreateSwapchainKHR(g->device, &swap, NULL, &w->swapchain) != VK_SUCCESS) goto fail;
    if (g->GetSwapchainImagesKHR(g->device, w->swapchain, &w->image_count, NULL) != VK_SUCCESS) goto fail;
    w->images = calloc(w->image_count, sizeof(*w->images));
    w->rendered = calloc(w->image_count, sizeof(*w->rendered));
    if (!w->images || !w->rendered || g->GetSwapchainImagesKHR(g->device, w->swapchain, &w->image_count, w->images) != VK_SUCCESS) goto fail;
    VkSemaphoreCreateInfo sem = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    for (uint32_t i = 0; i < w->image_count; i++)
        if (g->CreateSemaphore(g->device, &sem, NULL, &w->rendered[i]) != VK_SUCCESS) goto fail;
    if (g->CreateSemaphore(g->device, &sem, NULL, &w->acquired) != VK_SUCCESS ||
        g->CreateSemaphore(g->device, &sem, NULL, &w->producer) != VK_SUCCESS) goto fail;
    VkExportSemaphoreCreateInfo export = {.sType = VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO,
        .handleTypes = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    sem.pNext = &export;
    if (g->CreateSemaphore(g->device, &sem, NULL, &w->release) != VK_SUCCESS) goto fail;
    VkCommandPoolCreateInfo pool = {.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .queueFamilyIndex = g->family};
    if (g->CreateCommandPool(g->device, &pool, NULL, &w->pool) != VK_SUCCESS) goto fail;
    VkCommandBufferAllocateInfo command = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = w->pool, .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
    if (g->AllocateCommandBuffers(g->device, &command, &w->command) != VK_SUCCESS) goto fail;
    VkFenceCreateInfo fence = {.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    if (g->CreateFence(g->device, &fence, NULL, &w->fence) != VK_SUCCESS) goto fail;
    return w;
fail:
    error("create Linux GPU window", r);
    linux_gpu_window_destroy(w);
    return NULL;
}

void linux_gpu_window_destroy(struct linux_gpu_window *w) {
    if (!w) return;
    struct linux_gpu *g = w->gpu;
    /* Surface replacement/shutdown is outside the per-frame path. */
    g->DeviceWaitIdle(g->device);
    scene_window_destroy(w);
    if (w->fence) g->DestroyFence(g->device, w->fence, NULL);
    if (w->pool) g->DestroyCommandPool(g->device, w->pool, NULL);
    if (w->acquired) g->DestroySemaphore(g->device, w->acquired, NULL);
    if (w->producer) g->DestroySemaphore(g->device, w->producer, NULL);
    if (w->release) g->DestroySemaphore(g->device, w->release, NULL);
    if (w->rendered) for (uint32_t i = 0; i < w->image_count; i++)
        if (w->rendered[i]) g->DestroySemaphore(g->device, w->rendered[i], NULL);
    if (w->swapchain) g->DestroySwapchainKHR(g->device, w->swapchain, NULL);
    if (w->surface) g->DestroySurfaceKHR(g->instance, w->surface, NULL);
    ANativeWindow_release(w->window);
    free(w->images); free(w->rendered); free(w);
}

#include "gpu_scene.inc"

int linux_gpu_present(struct linux_gpu_window *w, struct linux_gpu_image *image,
    int x, int y, int width, int height, int acquire_fd, int *release_fd) {
    if (release_fd) *release_fd = -1;
    if (!w || !image || !release_fd || w->gpu != image->gpu || w->failed || w->gpu->failed ||
        x < 0 || y < 0 || width <= 0 || height <= 0 ||
        (int64_t)x + width > image->width || (int64_t)y + height > image->height) return -1;
    struct linux_gpu *g = w->gpu;
    VkResult r;
    if (w->submitted) {
        r = g->GetFenceStatus(g->device, w->fence);
        if (r == VK_NOT_READY) return 0;
        if (r != VK_SUCCESS) goto fail;
    }
    if ((r = g->ResetCommandPool(g->device, w->pool, 0)) != VK_SUCCESS) goto fail;
    uint32_t index;
    r = g->AcquireNextImageKHR(g->device, w->swapchain, 0, w->acquired, VK_NULL_HANDLE, &index);
    if (r == VK_NOT_READY || r == VK_TIMEOUT) return 0;
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) goto fail;
    if (acquire_fd >= 0) {
        int fd = dup(acquire_fd);
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
    VkImageMemoryBarrier barriers[2] = {
        {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_GENERAL, .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT, .dstQueueFamilyIndex = g->family,
            .image = image->image, .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}},
        {.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, .dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED, .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED, .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = w->images[index], .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1}}};
    g->CmdPipelineBarrier(w->command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
        0, 0, NULL, 0, NULL, 2, barriers);
    VkImageBlit blit = {.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
        .srcOffsets = {{x, y, 0}, {x + width, y + height, 1}},
        .dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1},
        .dstOffsets = {{0, 0, 0}, {(int32_t)w->extent.width, (int32_t)w->extent.height, 1}}};
    g->CmdBlitImage(w->command, image->image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        w->images[index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_NEAREST);
    barriers[0].srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT; barriers[0].dstAccessMask = 0;
    barriers[0].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL; barriers[0].newLayout = VK_IMAGE_LAYOUT_GENERAL;
    barriers[0].srcQueueFamilyIndex = g->family; barriers[0].dstQueueFamilyIndex = VK_QUEUE_FAMILY_FOREIGN_EXT;
    barriers[1].srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT; barriers[1].dstAccessMask = 0;
    barriers[1].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; barriers[1].newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    g->CmdPipelineBarrier(w->command, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, NULL, 0, NULL, 2, barriers);
    if ((r = g->EndCommandBuffer(w->command)) != VK_SUCCESS) goto fail;
    VkSemaphore waits[] = {w->acquired, w->producer}, signals[] = {w->rendered[index], w->release};
    VkPipelineStageFlags stages[] = {VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT};
    VkSubmitInfo submit = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .waitSemaphoreCount = acquire_fd >= 0 ? 2 : 1, .pWaitSemaphores = waits, .pWaitDstStageMask = stages,
        .commandBufferCount = 1, .pCommandBuffers = &w->command,
        .signalSemaphoreCount = 2, .pSignalSemaphores = signals};
    if ((r = g->ResetFences(g->device, 1, &w->fence)) != VK_SUCCESS) goto fail;
    if ((r = g->QueueSubmit(g->queue, 1, &submit, w->fence)) != VK_SUCCESS) goto fail;
    w->submitted = true;
    VkSemaphoreGetFdInfoKHR export = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR,
        .semaphore = w->release, .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    if ((r = g->GetSemaphoreFdKHR(g->device, &export, release_fd)) != VK_SUCCESS) goto fail;
    VkPresentInfoKHR present = {.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
        .waitSemaphoreCount = 1, .pWaitSemaphores = &w->rendered[index],
        .swapchainCount = 1, .pSwapchains = &w->swapchain, .pImageIndices = &index};
    r = g->QueuePresentKHR(g->queue, &present);
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) goto fail;
    return 1;
fail:
    error("Linux GPU present", r);
    w->failed = true;
    if (r == VK_ERROR_DEVICE_LOST) g->failed = true;
    /* A failed present may follow a successful submit; drain on failure only. */
    g->DeviceWaitIdle(g->device);
    if (*release_fd >= 0) { close(*release_fd); *release_fd = -1; }
    return -1;
}
