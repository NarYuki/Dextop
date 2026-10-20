#include <jni.h>
#include <android/native_window_jni.h>
#include <adrenotools/driver.h>
#include <dlfcn.h>
#include <vulkan/vulkan.h>
#include <stdio.h>
#include <poll.h>
#include <unistd.h>
#include "gpu_present.h"

int dmabuf_gpu_roundtrip(VkInstance, VkPhysicalDevice, PFN_vkGetInstanceProcAddr, ANativeWindow *);
int dmabuf_gpu_import_test(VkInstance, VkPhysicalDevice, PFN_vkGetInstanceProcAddr, ANativeWindow *, int, int);

static int test_presenter(ANativeWindow *native, ANativeWindow *second, int fd, int acquire_fd, int scene) {
    struct linux_gpu *gpu = linux_gpu_create("/data/local/tmp/dextop-wayland-smoke/turnip-dd/",
        "libvulkan_freedreno.so", "/data/local/tmp/dextop-wayland-smoke/hooks/");
    if (!gpu) return 20;
    struct linux_gpu_image *image = linux_gpu_import(gpu, fd, 0x34325241, 0, 64, 64, 256, 0);
    struct linux_gpu_window *window = linux_gpu_window_create(gpu, native);
    struct linux_gpu_window *other = second ? linux_gpu_window_create(gpu, second) : NULL;
    uint32_t blue = 0x80800000u;
    struct linux_gpu_image *overlay = scene ? linux_gpu_upload_rgba(gpu, 1, 1, &blue, 4) : NULL;
    int code = 21;
    if (image && window && (!second || other) && (!scene || overlay)) {
        for (int frame = 0; frame < (other ? 4 : 2); frame++) {
            if (other && frame == 2) {
                linux_gpu_window_destroy(window);
                window = NULL;
                puts("LINUX_GPU_FIRST_WINDOW_DETACHED");
            }
            int release = -1, submitted = 0;
            for (int retry = 0; retry < 500 && !submitted; retry++) {
                if (scene) {
                    struct linux_gpu_draw draws[] = {
                        {.image = image, .sw = 64, .sh = 64, .width = 64, .height = 64,
                            .acquire_fd = frame == 0 ? acquire_fd : -1},
                        {.image = overlay, .sw = 1, .sh = 1, .x = 16, .y = 16, .width = 32, .height = 32,
                            .acquire_fd = -1}};
                    submitted = linux_gpu_present_scene(frame >= 2 ? other : window, draws, 2, 64, 64, &release);
                } else submitted = linux_gpu_present(frame >= 2 ? other : window, image, 0, 0, 64, 64,
                    frame == 0 ? acquire_fd : -1, &release);
                if (!submitted) usleep(1000);
            }
            if (submitted != 1) goto done;
            if (release >= 0) {
                struct pollfd completion = {.fd = release, .events = POLLIN};
                int r = poll(&completion, 1, 5000);
                close(release);
                if (r != 1 || !(completion.revents & POLLIN)) goto done;
            }
            printf("LINUX_GPU_CACHED_IMAGE_FRAME_OK frame=%d\n", frame);
        }
        code = 0;
    }
done:
    linux_gpu_window_destroy(other);
    linux_gpu_window_destroy(window);
    linux_gpu_image_destroy(image);
    linux_gpu_image_destroy(overlay);
    linux_gpu_destroy(gpu);
    fflush(stdout);
    return code;
}

JNIEXPORT jint JNICALL Java_VulkanSurfaceSmoke_present(JNIEnv *env, jclass cls,
    jobject surface, jobject second, jobject descriptor, jobject acquire, jboolean scene) {
    (void)cls;
    jclass type = (*env)->FindClass(env, "java/io/FileDescriptor");
    jfieldID field = type ? (*env)->GetFieldID(env, type, "descriptor", "I") : NULL;
    if (!field || !descriptor) return 22;
    int fd = (*env)->GetIntField(env, descriptor, field);
    int acquire_fd = acquire ? (*env)->GetIntField(env, acquire, field) : -1;
    (*env)->DeleteLocalRef(env, type);
    ANativeWindow *native = ANativeWindow_fromSurface(env, surface);
    if (!native) return 23;
    ANativeWindow *other = second ? ANativeWindow_fromSurface(env, second) : NULL;
    int code = second && !other ? 24 : test_presenter(native, other, fd, acquire_fd, scene);
    if (other) ANativeWindow_release(other);
    ANativeWindow_release(native);
    return code;
}

JNIEXPORT jint JNICALL Java_VulkanSurfaceSmoke_render(JNIEnv *env, jclass cls, jobject surface, jobject descriptor, jobject acquire) {
    (void)cls;
    void *loader = adrenotools_open_libvulkan(RTLD_LOCAL | RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM,
        NULL, "/data/local/tmp/dextop-wayland-smoke/hooks/",
        "/data/local/tmp/dextop-wayland-smoke/turnip-dd/", "libvulkan_freedreno.so", NULL, NULL);
    if (!loader) return 10;
    PFN_vkGetInstanceProcAddr proc = (PFN_vkGetInstanceProcAddr)dlsym(loader, "vkGetInstanceProcAddr");
    if (!proc) return 11;
    PFN_vkCreateInstance create = (PFN_vkCreateInstance)proc(NULL, "vkCreateInstance");
    const char *extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .apiVersion = VK_API_VERSION_1_1};
    VkInstanceCreateInfo info = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app,
        .enabledExtensionCount = 2, .ppEnabledExtensionNames = extensions};
    VkInstance instance;
    if (create(&info, NULL, &instance)) return 12;
    PFN_vkEnumeratePhysicalDevices enumerate = (PFN_vkEnumeratePhysicalDevices)proc(instance, "vkEnumeratePhysicalDevices");
    PFN_vkDestroyInstance destroy = (PFN_vkDestroyInstance)proc(instance, "vkDestroyInstance");
    uint32_t count = 1;
    VkPhysicalDevice physical;
    int code = 13;
    if (enumerate(instance, &count, &physical) == VK_SUCCESS && count) {
        ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
        if (window) {
            int fd = -1;
            if (descriptor) {
                jclass type = (*env)->GetObjectClass(env, descriptor);
                jfieldID field = (*env)->GetFieldID(env, type, "descriptor", "I");
                if (field) fd = (*env)->GetIntField(env, descriptor, field);
                (*env)->DeleteLocalRef(env, type);
                if ((*env)->ExceptionCheck(env) || fd < 0) {
                    ANativeWindow_release(window);
                    destroy(instance, NULL);
                    return 14;
                }
            }
            int acquire_fd = -1;
            if (acquire) {
                jclass type = (*env)->GetObjectClass(env, acquire);
                jfieldID field = (*env)->GetFieldID(env, type, "descriptor", "I");
                if (field) acquire_fd = (*env)->GetIntField(env, acquire, field);
                (*env)->DeleteLocalRef(env, type);
                if ((*env)->ExceptionCheck(env) || acquire_fd < 0) {
                    ANativeWindow_release(window); destroy(instance, NULL); return 15;
                }
            }
            code = dmabuf_gpu_import_test(instance, physical, proc, window, fd, acquire_fd);
            ANativeWindow_release(window);
        }
    }
    destroy(instance, NULL);
    fflush(stdout);
    return code;
}
