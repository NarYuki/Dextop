#pragma once
#include <android/native_window.h>
#include <stdbool.h>
#include <stdint.h>

struct linux_gpu;
struct linux_gpu_image;
struct linux_gpu_window;

/* All calls for a context run on its owning Linux compositor thread. */
struct linux_gpu *linux_gpu_create(const char *driver_dir, const char *library, const char *hooks);
void linux_gpu_destroy(struct linux_gpu *gpu);
struct linux_gpu_image *linux_gpu_import(struct linux_gpu *gpu, int fd, uint32_t fourcc,
    uint64_t modifier, int width, int height, uint32_t stride, uint32_t offset);
void linux_gpu_image_destroy(struct linux_gpu_image *image);
void linux_gpu_image_ref(struct linux_gpu_image *image);
struct linux_gpu_image *linux_gpu_upload_rgba(struct linux_gpu *gpu, int width, int height,
    const void *pixels, uint32_t stride);
struct linux_gpu_window *linux_gpu_window_create(struct linux_gpu *gpu, ANativeWindow *window);
void linux_gpu_window_destroy(struct linux_gpu_window *window);

/* 1: submitted, 0: retry later, -1: failed. acquire_fd remains caller-owned.
 * A successful submission returns an owned sync FD in release_fd. The source
 * image must remain alive until that FD signals. No CPU GPU-completion wait. */
int linux_gpu_present(struct linux_gpu_window *window, struct linux_gpu_image *image,
    int x, int y, int width, int height, int acquire_fd, int *release_fd);

#define LINUX_GPU_MAX_DRAWS 64
struct linux_gpu_draw {
    struct linux_gpu_image *image;
    float sx, sy, sw, sh;
    float x, y, width, height;
    int acquire_fd;
};
int linux_gpu_present_scene(struct linux_gpu_window *window, const struct linux_gpu_draw *draws,
    unsigned count, int scene_width, int scene_height, int *release_fd);
