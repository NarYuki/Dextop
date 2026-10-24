#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

struct gbm_device;
struct gbm_bo;
union gbm_handle { void *ptr; int32_t s32; uint32_t u32; uint64_t u64; };

int main(int argc, char **argv) {
    const char *node = argc > 1 ? argv[1] : "/dev/kgsl-3d0";
    const char *library = argc > 2 ? argv[2] : "libgbm.so.1";
    void *lib = dlopen(library, RTLD_NOW | RTLD_LOCAL);
    if (!lib) { fprintf(stderr, "GBM_LOAD_FAILED %s\n", dlerror()); return 1; }
#define LOAD(result, name, args) \
    result (*name) args = dlsym(lib, #name); \
    if (!name) { fprintf(stderr, "GBM_SYMBOL_MISSING %s\n", #name); return 1; }
    LOAD(struct gbm_device *, gbm_create_device, (int));
    LOAD(void, gbm_device_destroy, (struct gbm_device *));
    LOAD(const char *, gbm_device_get_backend_name, (struct gbm_device *));
    LOAD(struct gbm_bo *, gbm_bo_create, (struct gbm_device *, uint32_t, uint32_t, uint32_t, uint32_t));
    LOAD(void, gbm_bo_destroy, (struct gbm_bo *));
    LOAD(int, gbm_bo_get_fd, (struct gbm_bo *));
    LOAD(int, gbm_bo_get_fd_for_plane, (struct gbm_bo *, int));
    LOAD(union gbm_handle, gbm_bo_get_handle_for_plane, (struct gbm_bo *, int));
    LOAD(uint32_t, gbm_bo_get_stride, (struct gbm_bo *));
    LOAD(uint64_t, gbm_bo_get_modifier, (struct gbm_bo *));
    void *drm = dlopen("libdrm.so.2", RTLD_NOW | RTLD_GLOBAL);
    int (*prime_export)(int, uint32_t, uint32_t, int *) = dlsym(RTLD_DEFAULT, "drmPrimeHandleToFD");
    if (!drm || !prime_export) { fprintf(stderr, "DRM_LOAD_FAILED\n"); return 1; }
    Dl_info info = {0};
    dladdr((void *)gbm_create_device, &info);
    printf("GBM_LIBRARY=%s node=%s override=%s gallium=%s\n",
        info.dli_fname ? info.dli_fname : library, node,
        getenv("MESA_LOADER_DRIVER_OVERRIDE") ?: "", getenv("GALLIUM_DRIVER") ?: "");
    int fd = open(node, O_RDWR | O_CLOEXEC);
    if (fd < 0) { perror("GBM_NODE_OPEN_FAILED"); return 1; }
    struct gbm_device *dev = gbm_create_device(fd);
    if (!dev) { perror("GBM_DEVICE_FAILED"); close(fd); return 1; }
    printf("GBM_BACKEND=%s\n", gbm_device_get_backend_name(dev));
    /* Chromium exports both opaque and alpha UI buffers through GBM. */
    const uint32_t formats[] = {0x34325258, 0x34325241}; /* XR24, AR24 */
    const uint32_t uses[] = {4, 4 | 16}; /* RENDERING, RENDERING | LINEAR */
    int failed = 0;
    for (size_t i = 0; i < 2; ++i) for (size_t j = 0; j < 2; ++j) {
        errno = 0;
        struct gbm_bo *bo = gbm_bo_create(dev, 128, 128, formats[i], uses[j]);
        if (!bo) {
            printf("GBM_ALLOC_FAILED format=%08x use=%u errno=%d\n", formats[i], uses[j], errno);
            failed = 1;
            continue;
        }
        errno = 0;
        int plain = gbm_bo_get_fd(bo), plain_error = errno;
        errno = 0;
        int plane = gbm_bo_get_fd_for_plane(bo, 0), plane_error = errno;
        /* Chromium 126 exports the KMS handle, rather than calling GBM's fd API. */
        uint32_t handle = gbm_bo_get_handle_for_plane(bo, 0).u32;
        int chromium_fd = -1;
        errno = 0;
        int chromium_result = prime_export(fd, handle, 3, &chromium_fd);
        int chromium_error = errno;
        struct stat a, b;
        int same = plain >= 0 && plane >= 0 && !fstat(plain, &a) && !fstat(plane, &b)
            && a.st_dev == b.st_dev && a.st_ino == b.st_ino;
        printf("GBM_EXPORT format=%08x use=%u stride=%u modifier=%016" PRIx64
            " fd=%d errno=%d plane_fd=%d plane_errno=%d same_buffer=%d"
            " kms_handle=%u chromium_result=%d chromium_fd=%d chromium_errno=%d\n",
            formats[i], uses[j], gbm_bo_get_stride(bo), gbm_bo_get_modifier(bo),
            plain, plain_error, plane, plane_error, same,
            handle, chromium_result, chromium_fd, chromium_error);
        if (plain < 0 || plane < 0 || !same || chromium_result || chromium_fd < 0) failed = 1;
        if (plain >= 0) close(plain);
        if (plane >= 0) close(plane);
        if (chromium_fd >= 0) close(chromium_fd);
        gbm_bo_destroy(bo);
    }
    gbm_device_destroy(dev);
    close(fd);
    dlclose(lib);
    dlclose(drm);
    return failed;
}
