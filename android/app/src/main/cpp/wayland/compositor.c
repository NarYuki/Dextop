/*
 * Dextop's Wayland compositor for Linux containers.
 *
 * Runs in the shell-side Linux service. Wayland clients in a container
 * (GTK/Qt applications, browsers, Vulkan programs through Mesa's Wayland
 * WSI) connect over an abstract unix socket. Every xdg toplevel becomes a
 * Plasma window and every xdg popup a Plasma popup: the service hands this
 * file the Surface of each one and forwards its input here.
 *
 * Buffers are wl_shm memory or linear dma-bufs (from Turnip/Zink). Each
 * commit copies the buffer into the surface's own pixels and releases it at
 * once; a window is drawn by compositing its surface tree (subsurfaces with
 * alpha) into the window's ANativeWindow.
 *
 * All Wayland state lives on the compositor thread. JNI entry points only
 * queue commands, which the thread runs from its event loop.
 */

#define _GNU_SOURCE
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/hardware_buffer.h>
#include <android/surface_control.h>
#include "banner-ahb-v1-server-protocol.h"
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/dma-buf.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/file.h>
#include <sys/mman.h>
#include <linux/memfd.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#include <wayland-server.h>
#include "wayland-server-protocol.h"
#include "xdg-shell-server-protocol.h"
#include "linux-dmabuf-v1-server-protocol.h"
#include "single-pixel-buffer-v1-server-protocol.h"
#include "xdg-decoration-unstable-v1-server-protocol.h"
#include "server-decoration-server-protocol.h"
#include "text-input-unstable-v3-server-protocol.h"
#include "viewporter-server-protocol.h"
#include "keymap_us.h"
#ifdef DEXTOP_WAYLAND_GPU
#include "gpu_present.h"
#include <poll.h>
#include "presentation-time-server-protocol.h"
#include "pointer-constraints-unstable-v1-server-protocol.h"
#include "relative-pointer-unstable-v1-server-protocol.h"
#endif

#define TAG "DextopWayland"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define DRM_FORMAT_ARGB8888 0x34325241
#define DRM_FORMAT_XRGB8888 0x34325258
#define DRM_FORMAT_ABGR8888 0x34324241
#define DRM_FORMAT_XBGR8888 0x34324258
#define DRM_FORMAT_MOD_LINEAR 0ull

#define BTN_LEFT 0x110
#define BTN_RIGHT 0x111
#define BTN_MIDDLE 0x112
#define BTN_SIDE 0x113
#define BTN_EXTRA 0x114

/* ------------------------------------------------------------------ types */

struct comp;

struct pixels {
    uint32_t *data; /* RGBA bytes in memory order (ANativeWindow RGBA_8888), premultiplied */
    int width, height;
};

struct viewport_state { float x, y, width, height; int dest_width, dest_height; };
struct opaque_rect { int64_t x1, y1, x2, y2; bool set; };

struct surface {
    struct comp *comp;
    struct wl_resource *resource;
    struct wl_resource *pending_buffer;
    bool pending_attach;
    struct wl_list pending_frames; /* wl_callback resources (wl_resource_get_link) */
    struct wl_list ready_frames;   /* rendered, waiting for the frame tick */
    struct pixels pixels;
    struct opaque_rect opaque, pending_opaque;
#ifdef DEXTOP_WAYLAND_GPU
    struct dmabuf *gpu_buffer;
    struct linux_gpu_image *gpu_shm;
    struct viewport_state view, pending_view;
    struct wl_resource *viewport;
    struct wl_list presentation_feedback; /* pending until wl_surface.commit */
    struct wl_list committed_feedback;
#endif
    int32_t scale, pending_scale;
    int32_t buffer_transform;
    /* Subsurface role. */
    struct surface *parent;
    struct wl_resource *subsurface;
    int32_t sub_x, sub_y, pending_sub_x, pending_sub_y;
    bool sub_sync, below_parent;
    struct surface *cached;
    bool cached_commit, cached_attach;
    struct wl_list children; /* struct surface.child_link, bottom to top */
    struct wl_list child_link;
    /* xdg role. */
    struct xdg *xdg;
    struct wl_listener destroy_listener;
};

struct xdg {
    struct comp *comp;
    struct surface *surface;
    struct wl_resource *xdg_surface;
    struct wl_resource *role; /* xdg_toplevel or xdg_popup */
    bool popup;
    struct xdg *parent;
    bool configured;
    bool mapped;
    bool client_decorated;
    bool maximized;
    uint32_t window_id;
    int32_t geo_x, geo_y, geo_w, geo_h;
    bool has_geometry;
    int32_t popup_x, popup_y, popup_w, popup_h; /* relative to the parent's window geometry */
    int32_t want_w, want_h;                     /* size Plasma asked for */
    char title[256];
    char app_id[128];
    ANativeWindow *window;
    int window_w, window_h;
    bool needs_redraw;
    bool deck_direct;
    bool deck_30, deck_no_vsync;
    bool deck; /* gamescope from the Deck launcher, not ordinary Linux windows */
    bool frame_announced;
#ifdef DEXTOP_WAYLAND_GPU
    struct linux_gpu_window *gpu_window;
    struct deck_direct_layer *direct_layer;
    unsigned deck_direct_submits;
    bool gpu_announced;
    bool gpu_disabled;
    uint64_t deck_buffer_serial, deck_submitted_serial, deck_buffer_ns;
    uint64_t deck_age_ns, deck_age_max_ns;
    unsigned deck_buffers, deck_submits, deck_coalesced, deck_redraws, deck_age_samples;
    uint64_t deck_previous_commit_ns, deck_commit_gap_max_ns;
    unsigned deck_commit_gaps[4];
#endif
};

struct positioner {
    int32_t width, height;
    int32_t anchor_x, anchor_y, anchor_w, anchor_h;
    uint32_t anchor, gravity;
    int32_t offset_x, offset_y;
};

struct dmabuf_params {
    int fd;
    uint32_t offset, stride;
    uint64_t modifier;
    bool used;
    bool deck;
    struct comp *comp;
};

struct dmabuf {
    int fd;
    uint32_t offset, stride;
    int32_t width, height;
    uint32_t format;
    uint64_t modifier;
    void *map;
    size_t map_size;
#ifdef DEXTOP_WAYLAND_GPU
    struct linux_gpu_image *image;
    AHardwareBuffer *ahb;
    struct comp *comp;
    bool deck;
    struct wl_resource *resource;
    unsigned refs, busy;
#endif
};

#ifdef DEXTOP_WAYLAND_GPU
struct gpu_job {
    struct dmabuf *buffers[LINUX_GPU_MAX_DRAWS];
    struct linux_gpu_image *images[LINUX_GPU_MAX_DRAWS];
    unsigned count;
    struct comp *comp;
    uint64_t started_ns;
    uint32_t window_id;
    int fence;
    struct wl_event_source *source;
    struct wl_list link;
};
#endif

enum command_type {
    CMD_ATTACH, CMD_DETACH, CMD_CONFIGURE, CMD_ACTIVATE, CMD_CLOSE,
    CMD_POINTER, CMD_AXIS, CMD_KEY, CMD_TEXT, CMD_QUIT, CMD_STATE, CMD_OUTPUT, CMD_DECK_VSYNC,
};

struct command {
    enum command_type type;
    uint32_t id;
    int32_t a, b, c, d;
    uint64_t frame_time_ns;
    ANativeWindow *window;
    char *text;
    struct command *next;
};

struct comp {
    uint64_t deck_vsync_ns, deck_frame_ns;
#ifdef DEXTOP_WAYLAND_GPU
    struct linux_gpu *gpu;
    struct wl_list gpu_jobs;
    struct wl_list direct_frames;
    struct wl_list buffer_releases;
    struct wl_event_source *buffer_release_idle;
    uint64_t gpu_cpu_ns, gpu_release_ns, gpu_release_max_ns;
    unsigned gpu_submits, gpu_retries, gpu_releases, gpu_commits;
    uint32_t gpu_stats_time;
#endif
    struct wl_display *display;
    struct wl_global *normal_dmabuf_global, *deck_dmabuf_global, *deck_ahb_global;
    struct wl_event_loop *loop;
    struct wl_event_source *command_source;
    struct wl_event_source *frame_timer;
    int command_fd;
    int ahb_broker_fd;
    pthread_mutex_t lock;
    struct command *commands, *commands_tail;
    pthread_t thread;
    JavaVM *vm;
    JNIEnv *env;
    jobject callback;
    jmethodID on_event;
    int32_t width, height, refresh_mhz;
    uint64_t next_frame_ns;
    struct wl_list outputs;
    uint32_t next_window_id;
    struct wl_list pointers;    /* wl_resource links */
    struct wl_list keyboards;
    struct wl_list text_inputs;
    struct xdg *xdgs[1024];     /* by window id & 1023 */
#ifdef DEXTOP_WAYLAND_GPU
    struct wl_list constraints, relative_pointers;
    wl_fixed_t pointer_x, pointer_y;
#endif
    struct surface *pointer_focus;
    struct surface *keyboard_focus;
    uint32_t mods_depressed, mods_locked;
    uint32_t text_serial;
    struct wl_array pressed_keys;
};

/* ---------------------------------------------------------------- helpers */

static uint32_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

#ifdef DEXTOP_WAYLAND_GPU
static uint64_t gpu_now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + ts.tv_nsec;
}
#endif

static void json_escape(char *out, size_t size, const char *in) {
    size_t o = 0;
    for (const char *p = in; *p && o + 7 < size; ++p) {
        unsigned char ch = (unsigned char)*p;
        if (ch == '"' || ch == '\\') { out[o++] = '\\'; out[o++] = (char)ch; }
        else if (ch < 0x20) o += (size_t)snprintf(out + o, size - o, "\\u%04x", ch);
        else out[o++] = (char)ch;
    }
    out[o] = 0;
}

static void emit(struct comp *comp, const char *json) {
    if (!comp->env || !comp->callback) return;
    jstring text = (*comp->env)->NewStringUTF(comp->env, json);
    (*comp->env)->CallVoidMethod(comp->env, comp->callback, comp->on_event, text);
    if ((*comp->env)->ExceptionCheck(comp->env)) (*comp->env)->ExceptionClear(comp->env);
    (*comp->env)->DeleteLocalRef(comp->env, text);
}

static void emit_error(struct comp *comp, const char *message) {
    char escaped[1024], json[1200];
    json_escape(escaped, sizeof(escaped), message);
    snprintf(json, sizeof(json), "{\"type\":\"error\",\"target\":\"apps\",\"message\":\"%s\"}", escaped);
    LOGW("%s", message);
    emit(comp, json);
}

static struct xdg *xdg_by_id(struct comp *comp, uint32_t id) {
    struct xdg *xdg = comp->xdgs[id & 1023];
    return xdg && xdg->window_id == id ? xdg : NULL;
}

static int surface_width(struct surface *surface) {
#ifdef DEXTOP_WAYLAND_GPU
    if (surface->view.dest_width > 0) return surface->view.dest_width;
    if (surface->view.width >= 0) return (int)surface->view.width;
#endif
    return surface->pixels.width / (surface->scale > 0 ? surface->scale : 1);
}

static int surface_height(struct surface *surface) {
#ifdef DEXTOP_WAYLAND_GPU
    if (surface->view.dest_height > 0) return surface->view.dest_height;
    if (surface->view.height >= 0) return (int)surface->view.height;
#endif
    return surface->pixels.height / (surface->scale > 0 ? surface->scale : 1);
}

static void emit_window(struct comp *comp, struct xdg *xdg, const char *type) {
    char title[600], app[300], json[1200];
    json_escape(title, sizeof(title), xdg->title);
    json_escape(app, sizeof(app), xdg->app_id);
    int w = xdg->has_geometry ? xdg->geo_w : surface_width(xdg->surface);
    int h = xdg->has_geometry ? xdg->geo_h : surface_height(xdg->surface);
    if (xdg->popup) { w = xdg->popup_w; h = xdg->popup_h; }
    snprintf(json, sizeof(json),
             "{\"type\":\"%s\",\"id\":%u,\"popup\":%s,\"parent\":%u,\"x\":%d,\"y\":%d,\"w\":%d,\"h\":%d,"
             "\"title\":\"%s\",\"class\":\"%s\",\"decorated\":%s,\"deck\":%s}",
             type, xdg->window_id, xdg->popup ? "true" : "false",
             xdg->parent ? xdg->parent->window_id : 0,
             xdg->popup ? xdg->popup_x : 0, xdg->popup ? xdg->popup_y : 0, w, h, title, app,
             xdg->client_decorated ? "false" : "true", xdg->deck ? "true" : "false");
    emit(comp, json);
}

/* ------------------------------------------------------------- rendering */

/* Converts a buffer row to RGBA memory order with premultiplied alpha. */
static void convert_row(uint32_t *dst, const uint8_t *src, int width, uint32_t format) {
    const uint32_t *in = (const uint32_t *)src;
    for (int x = 0; x < width; ++x) {
        uint32_t p = in[x];
        switch (format) {
            case DRM_FORMAT_ARGB8888: /* B G R A in memory */
                dst[x] = (p & 0xff00ff00u) | ((p & 0xffu) << 16) | ((p >> 16) & 0xffu);
                break;
            case DRM_FORMAT_XRGB8888:
                dst[x] = 0xff000000u | (p & 0x0000ff00u) | ((p & 0xffu) << 16) | ((p >> 16) & 0xffu);
                break;
            case DRM_FORMAT_ABGR8888: /* R G B A in memory */
                dst[x] = p;
                break;
            default: /* XBGR8888 */
                dst[x] = p | 0xff000000u;
                break;
        }
    }
}

static uint32_t shm_to_drm(uint32_t format) {
    if (format == WL_SHM_FORMAT_ARGB8888) return DRM_FORMAT_ARGB8888;
    if (format == WL_SHM_FORMAT_XRGB8888) return DRM_FORMAT_XRGB8888;
    return format; /* the other wl_shm codes equal their DRM fourcc */
}

static bool pixels_resize(struct pixels *pixels, int width, int height) {
    if (pixels->width == width && pixels->height == height && pixels->data) return true;
    free(pixels->data);
    pixels->data = calloc((size_t)width * height, 4);
    pixels->width = pixels->data ? width : 0;
    pixels->height = pixels->data ? height : 0;
    return pixels->data != NULL;
}

static const struct wl_buffer_interface dmabuf_buffer_impl;

#ifdef DEXTOP_WAYLAND_GPU
#include "deck_buffer_release.inc"

static void gpu_buffer_unref(struct dmabuf *buffer, bool busy) {
    if (!buffer) return;
    if (busy && --buffer->busy == 0 && buffer->resource)
        deck_buffer_release_queue(buffer->comp, buffer->resource, buffer->deck);
    if (--buffer->refs) return;
    linux_gpu_image_destroy(buffer->image);
    if (buffer->ahb) AHardwareBuffer_release(buffer->ahb);
    if (buffer->map) munmap(buffer->map, buffer->map_size);
    close(buffer->fd);
    free(buffer);
}

static void gpu_buffer_hold(struct dmabuf *buffer) {
    if (buffer->resource) deck_buffer_release_cancel(buffer->comp, buffer->resource);
    buffer->refs++; buffer->busy++;
}

static void gpu_job_finish(struct gpu_job *job) {
    uint64_t elapsed = gpu_now_ns() - job->started_ns;
    job->comp->gpu_release_ns += elapsed;
    if (elapsed > job->comp->gpu_release_max_ns) job->comp->gpu_release_max_ns = elapsed;
    job->comp->gpu_releases++;
    if (job->source) wl_event_source_remove(job->source);
    if (job->fence >= 0) close(job->fence);
    for (unsigned i = 0; i < job->count; i++) {
        linux_gpu_image_destroy(job->images[i]);
        gpu_buffer_unref(job->buffers[i], true);
    }
    wl_list_remove(&job->link);
    free(job);
}

static void window_draw(struct xdg *xdg);

static int gpu_job_ready(int fd, uint32_t mask, void *data) {
    struct gpu_job *job = data;
    struct pollfd complete = {.fd = fd, .events = POLLIN};
    if ((mask & WL_EVENT_READABLE) && poll(&complete, 1, 0) == 1 && (complete.revents & POLLIN)) {
        struct comp *comp = job->comp;
        uint32_t window_id = job->window_id;
        gpu_job_finish(job);
        /* A busy submission retains only the newest scene. Retry as soon as
         * the in-flight GPU job completes, not one 16ms timer later. Resolve
         * the ID afresh: the window may have closed while its fence was live.
         * VSync-on windows keep their existing presentation schedule. */
        struct xdg *xdg = xdg_by_id(comp, window_id);
        if (xdg && xdg->deck && xdg->deck_no_vsync && xdg->mapped && xdg->window && xdg->needs_redraw) {
            xdg->needs_redraw = false;
            window_draw(xdg);
        }
    }
    return 0;
}

static void gpu_jobs_collect(struct comp *comp, bool drained) {
    struct gpu_job *job, *tmp;
    wl_list_for_each_safe(job, tmp, &comp->gpu_jobs, link) {
        struct pollfd fd = {.fd = job->fence, .events = POLLIN};
        int r = job->fence < 0 ? 1 : poll(&fd, 1, 0);
        if (!drained && !(r == 1 && (job->fence < 0 || (fd.revents & POLLIN)))) continue;
        gpu_job_finish(job);
    }
}

static bool gpu_take_buffer(struct surface *surface, struct wl_resource *resource) {
    struct comp *comp = surface->comp;
    struct surface *root = surface;
    while (root->parent) root = root->parent;
    if (!comp->gpu || !root->xdg || root->xdg->gpu_disabled ||
        !wl_resource_instance_of(resource, &wl_buffer_interface, &dmabuf_buffer_impl)) return false;
    struct dmabuf *buffer = wl_resource_get_user_data(resource);
    if (!buffer->image) buffer->image = linux_gpu_import(comp->gpu, buffer->fd, buffer->format,
        buffer->modifier, buffer->width, buffer->height, buffer->stride, buffer->offset);
    if (!buffer->image) return false;
    gpu_buffer_hold(buffer);
    surface->gpu_buffer = buffer;
    free(surface->pixels.data);
    surface->pixels = (struct pixels){.width = buffer->width, .height = buffer->height};
    return true;
}
#endif

static void surface_take_dmabuf(struct surface *surface, struct dmabuf *dmabuf) {
        if (dmabuf->modifier != DRM_FORMAT_MOD_LINEAR) {
            emit_error(surface->comp, "Compressed Deck buffer requires GPU import; CPU conversion is unavailable");
            return;
        }
        if (!dmabuf->map) {
            dmabuf->map_size = dmabuf->offset + (size_t)dmabuf->stride * dmabuf->height;
            dmabuf->map = mmap(NULL, dmabuf->map_size, PROT_READ, MAP_SHARED, dmabuf->fd, 0);
            if (dmabuf->map == MAP_FAILED) {
                char message[256];
                snprintf(message, sizeof(message), "Wayland dma-buf mmap failed: %s", strerror(errno));
                emit_error(surface->comp, message);
                dmabuf->map = NULL;
                return;
            }
        }
        if (!pixels_resize(&surface->pixels, dmabuf->width, dmabuf->height)) return;
        struct dma_buf_sync sync = {.flags = DMA_BUF_SYNC_START | DMA_BUF_SYNC_READ};
        ioctl(dmabuf->fd, DMA_BUF_IOCTL_SYNC, &sync);
        const uint8_t *data = (const uint8_t *)dmabuf->map + dmabuf->offset;
        for (int y = 0; y < dmabuf->height; ++y)
            convert_row(surface->pixels.data + (size_t)y * dmabuf->width,
                        data + (size_t)y * dmabuf->stride, dmabuf->width, dmabuf->format);
        sync.flags = DMA_BUF_SYNC_END | DMA_BUF_SYNC_READ;
        ioctl(dmabuf->fd, DMA_BUF_IOCTL_SYNC, &sync);
}

static void solid_buffer_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static const struct wl_buffer_interface solid_buffer_impl = {.destroy = solid_buffer_destroy};
static void solid_buffer_free(struct wl_resource *r) { free(wl_resource_get_user_data(r)); }

static void solid_create(struct wl_client *client, struct wl_resource *manager, uint32_t id,
                         uint32_t red, uint32_t green, uint32_t blue, uint32_t alpha) {
    uint32_t *pixel = malloc(sizeof(*pixel));
    struct wl_resource *buffer = pixel ? wl_resource_create(client, &wl_buffer_interface, 1, id) : NULL;
    if (!buffer) { free(pixel); wl_client_post_no_memory(client); return; }
    /* pixels.data uses premultiplied RGBA bytes, like converted wl_shm. */
    *pixel = (red >> 24) | ((green >> 24) << 8) | ((blue >> 24) << 16) | ((alpha >> 24) << 24);
    wl_resource_set_implementation(buffer, &solid_buffer_impl, pixel, solid_buffer_free);
}
static void solid_manager_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static const struct wp_single_pixel_buffer_manager_v1_interface solid_manager_impl = {
    .destroy = solid_manager_destroy, .create_u32_rgba_buffer = solid_create,
};
static void bind_solid(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *r = wl_resource_create(client, &wp_single_pixel_buffer_manager_v1_interface, 1, id);
    if (!r) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(r, &solid_manager_impl, NULL, NULL);
}

/* Copies a committed buffer into the surface's pixels. */
static void surface_take_buffer(struct surface *surface, struct wl_resource *buffer) {
    if (wl_resource_instance_of(buffer, &wl_buffer_interface, &solid_buffer_impl)) {
        if (pixels_resize(&surface->pixels, 1, 1))
            surface->pixels.data[0] = *(uint32_t *)wl_resource_get_user_data(buffer);
        return;
    }
    struct wl_shm_buffer *shm = wl_shm_buffer_get(buffer);
    if (shm) {
        int width = wl_shm_buffer_get_width(shm), height = wl_shm_buffer_get_height(shm);
        int stride = wl_shm_buffer_get_stride(shm);
        uint32_t format = shm_to_drm(wl_shm_buffer_get_format(shm));
        if (!pixels_resize(&surface->pixels, width, height)) return;
        wl_shm_buffer_begin_access(shm);
        const uint8_t *data = wl_shm_buffer_get_data(shm);
        for (int y = 0; y < height; ++y)
            convert_row(surface->pixels.data + (size_t)y * width, data + (size_t)y * stride, width, format);
        wl_shm_buffer_end_access(shm);
        return;
    }
    if (wl_resource_instance_of(buffer, &wl_buffer_interface, &dmabuf_buffer_impl))
        surface_take_dmabuf(surface, wl_resource_get_user_data(buffer));
}

/* Draws [surface] and its subsurfaces at (x, y) of a w x h RGBA target. */
static void composite(struct surface *surface, uint32_t *target, int stride, int tw, int th, int x, int y) {
    struct surface *child;
    wl_list_for_each(child, &surface->children, child_link)
        if (child->below_parent)
            composite(child, target, stride, tw, th, x + child->sub_x, y + child->sub_y);
    const struct pixels *p = &surface->pixels;
    int width = surface_width(surface), height = surface_height(surface);
    float sx = 0, sy = 0, sw = p->width, sh = p->height;
#ifdef DEXTOP_WAYLAND_GPU
    if (surface->view.width >= 0) {
        int scale = surface->scale > 0 ? surface->scale : 1;
        sx = surface->view.x * scale; sy = surface->view.y * scale;
        sw = surface->view.width * scale; sh = surface->view.height * scale;
    }
#endif
    for (int row = 0; p->data && row < height; ++row) {
        int ty = y + row;
        if (ty < 0 || ty >= th) continue;
        int x0 = x < 0 ? -x : 0;
        int x1 = width;
        if (x + x1 > tw) x1 = tw - x;
        if (x1 <= x0) continue;
        int source_y = (int)(sy + row * sh / height);
        if (source_y < 0 || source_y >= p->height) continue;
        const uint32_t *src = p->data + (size_t)source_y * p->width;
        uint32_t *dst = target + (size_t)ty * stride;
        for (int col = x0; col < x1; ++col) {
            int source_x = (int)(sx + col * sw / width);
            if (source_x < 0 || source_x >= p->width) continue;
            uint32_t s = src[source_x];
            if (surface->opaque.set && col >= surface->opaque.x1 && row >= surface->opaque.y1 &&
                col < surface->opaque.x2 && row < surface->opaque.y2) s |= 0xff000000u;
            uint32_t a = s >> 24;
            if (a == 0xff) { dst[x + col] = s; continue; }
            if (a == 0) continue;
            uint32_t d = dst[x + col], inv = 255 - a;
            uint32_t out = 0;
            for (unsigned shift = 0; shift < 32; shift += 8)
                out |= (((s >> shift) & 255) + ((((d >> shift) & 255) * inv + 127) / 255)) << shift;
            dst[x + col] = out;
        }
    }
    wl_list_for_each(child, &surface->children, child_link)
        if (!child->below_parent)
            composite(child, target, stride, tw, th, x + child->sub_x, y + child->sub_y);
}

#ifdef DEXTOP_WAYLAND_GPU
static bool gpu_collect_scene(struct surface *surface, float x, float y, struct linux_gpu_draw *draws,
    struct gpu_job *job, unsigned depth) {
    if (depth > 64) return false;
    struct surface *child;
    wl_list_for_each(child, &surface->children, child_link)
        if (child->below_parent &&
            !gpu_collect_scene(child, x + child->sub_x, y + child->sub_y, draws, job, depth + 1)) return false;
    struct dmabuf *buffer = surface->gpu_buffer;
    if (!buffer && surface->pixels.data && !surface->gpu_shm)
        surface->gpu_shm = linux_gpu_upload_rgba(surface->comp->gpu, surface->pixels.width,
            surface->pixels.height, surface->pixels.data, surface->pixels.width * 4);
    struct linux_gpu_image *image = buffer ? buffer->image : surface->gpu_shm;
    if (buffer || surface->pixels.data) {
        if (!image || job->count >= LINUX_GPU_MAX_DRAWS) return false;
        unsigned i = job->count++;
        job->buffers[i] = buffer;
        job->images[i] = image;
        float scale = surface->scale > 0 ? surface->scale : 1;
        float sw = surface->view.width >= 0 ? surface->view.width * scale : surface->pixels.width;
        float sh = surface->view.height >= 0 ? surface->view.height * scale : surface->pixels.height;
        draws[i] = (struct linux_gpu_draw){.image = image,
            .sx = surface->view.width >= 0 ? surface->view.x * scale : 0,
            .sy = surface->view.height >= 0 ? surface->view.y * scale : 0, .sw = sw, .sh = sh,
            .x = x, .y = y,
            .width = surface->view.dest_width > 0 ? surface->view.dest_width : sw / scale,
            .height = surface->view.dest_height > 0 ? surface->view.dest_height : sh / scale,
            .acquire_fd = -1,
            .opaque = surface->opaque.set && surface->opaque.x1 <= 0 && surface->opaque.y1 <= 0 &&
                surface->opaque.x2 >= surface_width(surface) && surface->opaque.y2 >= surface_height(surface)};
        if (buffer) {
        struct dma_buf_export_sync_file acquire = {.flags = DMA_BUF_SYNC_READ, .fd = -1};
            if (ioctl(buffer->fd, DMA_BUF_IOCTL_EXPORT_SYNC_FILE, &acquire)) return false;
            draws[i].acquire_fd = acquire.fd;
        }
    }
    wl_list_for_each(child, &surface->children, child_link)
        if (!child->below_parent &&
            !gpu_collect_scene(child, x + child->sub_x, y + child->sub_y, draws, job, depth + 1)) return false;
    return true;
}

static void gpu_materialize_tree(struct surface *surface) {
    if (surface->gpu_buffer) {
        struct dmabuf *buffer = surface->gpu_buffer;
        surface_take_dmabuf(surface, buffer);
        surface->gpu_buffer = NULL;
        gpu_buffer_unref(buffer, true);
    }
    struct surface *child;
    wl_list_for_each(child, &surface->children, child_link) gpu_materialize_tree(child);
}

static void presentation_submitted(struct surface *surface);

/* Direct layer and its asynchronous display-release ownership. */
#include "deck_direct.inc"

static bool gpu_draw_window(struct xdg *xdg) {
    if (deck_direct_present(xdg)) {
        presentation_submitted(xdg->surface);
        if (!xdg->frame_announced) { xdg->frame_announced = true; emit_window(xdg->comp, xdg, "frame"); }
        return true;
    }
    if (!xdg->comp->gpu || xdg->gpu_disabled) return false;
    uint64_t started = gpu_now_ns();
    struct gpu_job *job = calloc(1, sizeof(*job));
    if (!job) { xdg->needs_redraw = true; return true; }
    struct linux_gpu_draw draws[LINUX_GPU_MAX_DRAWS] = {0};
    int ox = xdg->has_geometry ? xdg->geo_x : 0, oy = xdg->has_geometry ? xdg->geo_y : 0;
    bool collected = gpu_collect_scene(xdg->surface, -ox, -oy, draws, job, 0);
    int result = -1;
    if (collected && job->count) {
        int width = xdg->has_geometry ? xdg->geo_w : (int)draws[0].width;
        int height = xdg->has_geometry ? xdg->geo_h : (int)draws[0].height;
        if (!xdg->gpu_window) xdg->gpu_window = linux_gpu_window_create(xdg->comp->gpu, xdg->window, xdg->deck && xdg->deck_direct);
        if (xdg->gpu_window) result = linux_gpu_present_scene(xdg->gpu_window, draws, job->count,
            width, height, &job->fence);
    }
    for (unsigned i = 0; i < job->count; i++) if (draws[i].acquire_fd >= 0) close(draws[i].acquire_fd);
    if (result == 1) {
        deck_direct_hide(xdg, false);
        if (xdg->deck) {
            xdg->deck_submits++;
            if (xdg->deck_buffer_serial != xdg->deck_submitted_serial) {
                uint64_t age = started >= xdg->deck_buffer_ns ? started - xdg->deck_buffer_ns : 0;
                xdg->deck_age_ns += age;
                if (age > xdg->deck_age_max_ns) xdg->deck_age_max_ns = age;
                xdg->deck_age_samples++;
                xdg->deck_coalesced += xdg->deck_buffer_serial - xdg->deck_submitted_serial - 1;
                xdg->deck_submitted_serial = xdg->deck_buffer_serial;
            } else xdg->deck_redraws++;
        }
        job->comp = xdg->comp;
        job->window_id = xdg->window_id;
        job->started_ns = started;
        xdg->comp->gpu_submits++;
        xdg->comp->gpu_cpu_ns += gpu_now_ns() - started;
        for (unsigned i = 0; i < job->count; i++) {
            linux_gpu_image_ref(job->images[i]);
            if (job->buffers[i]) gpu_buffer_hold(job->buffers[i]);
        }
        wl_list_insert(&xdg->comp->gpu_jobs, &job->link);
        presentation_submitted(xdg->surface);
        if (xdg->deck && !xdg->frame_announced) {
            xdg->frame_announced = true;
            emit_window(xdg->comp, xdg, "frame");
        }
        if (job->fence >= 0) job->source = wl_event_loop_add_fd(xdg->comp->loop, job->fence,
            WL_EVENT_READABLE, gpu_job_ready, job);
        if (!xdg->gpu_announced) {
            LOGI("GPU scene presented window=%u layers=%u", xdg->window_id, job->count);
            xdg->gpu_announced = true;
        }
        return true;
    }
    free(job);
    if (result == 0) { xdg->comp->gpu_retries++; xdg->needs_redraw = true; return true; }
    LOGW("CPU fallback window=%u (GPU scene unavailable)", xdg->window_id);
    xdg->gpu_disabled = true;
    if (xdg->gpu_window) { linux_gpu_window_destroy(xdg->gpu_window); xdg->gpu_window = NULL; }
    gpu_materialize_tree(xdg->surface);
    return false;
}
#endif

static void window_draw(struct xdg *xdg) {
    if (!xdg->window) return;
#ifdef DEXTOP_WAYLAND_GPU
    if (gpu_draw_window(xdg)) return;
#endif
    if (!xdg->surface->pixels.data) return;
    int ox = xdg->has_geometry ? xdg->geo_x : 0;
    int oy = xdg->has_geometry ? xdg->geo_y : 0;
    int w = xdg->has_geometry ? xdg->geo_w : surface_width(xdg->surface);
    int h = xdg->has_geometry ? xdg->geo_h : surface_height(xdg->surface);
    if (w <= 0 || h <= 0) return;
    if (w != xdg->window_w || h != xdg->window_h) {
        ANativeWindow_setBuffersGeometry(xdg->window, w, h, WINDOW_FORMAT_RGBA_8888);
        xdg->window_w = w;
        xdg->window_h = h;
    }
    ANativeWindow_Buffer out;
    if (ANativeWindow_lock(xdg->window, &out, NULL) != 0) return;
    uint32_t *target = out.bits;
    for (int row = 0; row < out.height; ++row) memset(target + (size_t)row * out.stride, 0, (size_t)out.width * 4);
    composite(xdg->surface, target, out.stride, out.width, out.height, -ox, -oy);
    int posted = ANativeWindow_unlockAndPost(xdg->window);
    if (posted == 0 && xdg->deck && !xdg->frame_announced) {
        xdg->frame_announced = true;
        emit_window(xdg->comp, xdg, "frame");
    }
#ifdef DEXTOP_WAYLAND_GPU
    if (posted == 0) { deck_direct_hide(xdg, false); presentation_submitted(xdg->surface); }
#endif
}

/* ------------------------------------------------------------ wl_surface */

static struct surface *root_of(struct surface *surface) {
    while (surface->parent) surface = surface->parent;
    return surface;
}

#ifdef DEXTOP_WAYLAND_GPU
static void presentation_discard_list(struct wl_list *list) {
    struct wl_resource *feedback, *tmp;
    wl_resource_for_each_safe(feedback, tmp, list) {
        wp_presentation_feedback_send_discarded(feedback);
        wl_resource_destroy(feedback);
    }
}

static void presentation_discard(struct surface *surface) {
    presentation_discard_list(&surface->presentation_feedback);
    presentation_discard_list(&surface->committed_feedback);
}

/* This nested output is presented into an Android Surface. Report successful
 * submission in the compositor clock domain; no hardware-clock/completion or
 * scanout/VSYNC flags are claimed for this estimate. Gamescope needs successful
 * feedback to advance its nested vblank clock, not an endless stream of drops. */
/* DD uses the Android VSync tick for nested presentation feedback, with a
 * monotonic render-time fallback if the tick is stale. Only the authenticated
 * Auto Deck output uses that policy; ordinary Linux feedback is unchanged. */
static void presentation_submitted_at(struct surface *surface, uint64_t nanos, uint32_t flags) {
    uint64_t seconds = nanos / 1000000000ull;
    uint32_t refresh = (uint32_t)(1000000000000ull / (unsigned)surface->comp->refresh_mhz);
    struct surface *root = root_of(surface);
    if (root->xdg && root->xdg->deck && root->xdg->deck_30) refresh = 33333333;
    struct wl_resource *feedback, *tmp;
    wl_resource_for_each_safe(feedback, tmp, &surface->committed_feedback) {
        wp_presentation_feedback_send_presented(feedback, (uint32_t)(seconds >> 32),
            (uint32_t)seconds, (uint32_t)(nanos % 1000000000ull), refresh, 0, 0, flags);
        wl_resource_destroy(feedback);
    }
    struct surface *child;
    wl_list_for_each(child, &surface->children, child_link) presentation_submitted_at(child, nanos, flags);
}

static void presentation_submitted(struct surface *surface) {
    uint64_t nanos = gpu_now_ns();
    uint32_t flags = 0;
    struct xdg *deck = NULL;
    for (unsigned i = 0; i < 1024; i++) {
        struct xdg *candidate = surface->comp->xdgs[i];
        if (candidate && candidate->surface == surface) { deck = candidate; break; }
    }
    if (deck && deck->deck && deck->deck_direct && !deck->deck_no_vsync) {
        uint64_t tick = surface->comp->deck_frame_ns;
        uint64_t interval = 1000000000000ull / (unsigned)surface->comp->refresh_mhz;
        if (tick && nanos >= tick && nanos - tick <= interval) {
            nanos = tick;
            flags = WP_PRESENTATION_FEEDBACK_KIND_VSYNC;
        }
    }
    presentation_submitted_at(surface, nanos, flags);
}
#endif

static void schedule_frames(struct surface *surface) {
    wl_list_insert_list(&surface->ready_frames, &surface->pending_frames);
    wl_list_init(&surface->pending_frames);
}

static void xdg_send_configure(struct xdg *xdg);
static void focus_keyboard(struct comp *comp, struct surface *surface);
static void pointer_focus(struct comp *comp, struct surface *surface, wl_fixed_t x, wl_fixed_t y);
static void xdg_map_if_ready(struct xdg *xdg);

static void surface_destroy_request(struct wl_client *client, struct wl_resource *resource) { wl_resource_destroy(resource); }

static void surface_attach(struct wl_client *client, struct wl_resource *resource, struct wl_resource *buffer, int32_t x, int32_t y) {
    struct surface *surface = wl_resource_get_user_data(resource);
    surface->pending_buffer = buffer;
    surface->pending_attach = true;
}

static void surface_damage(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y, int32_t w, int32_t h) {}

static void callback_unlink(struct wl_resource *resource) { wl_list_remove(wl_resource_get_link(resource)); }

static void surface_frame(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct surface *surface = wl_resource_get_user_data(resource);
    struct wl_resource *callback = wl_resource_create(client, &wl_callback_interface, 1, id);
    if (!callback) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(callback, NULL, NULL, callback_unlink);
    wl_list_insert(surface->pending_frames.prev, wl_resource_get_link(callback));
}

static void surface_region(struct wl_client *c, struct wl_resource *r, struct wl_resource *region) {}
static void surface_opaque_region(struct wl_client *c, struct wl_resource *r, struct wl_resource *region);
static void surface_transform(struct wl_client *c, struct wl_resource *r, int32_t t) {
    struct surface *surface = wl_resource_get_user_data(r);
    surface->buffer_transform = t;
}

static void surface_scale(struct wl_client *c, struct wl_resource *r, int32_t scale) {
    struct surface *surface = wl_resource_get_user_data(r);
    surface->pending_scale = scale;
}

static bool surface_synchronized(struct surface *surface) {
    for (; surface && surface->parent; surface = surface->parent)
        if (surface->sub_sync) return true;
    return false;
}

static void surface_clear_content(struct surface *surface) {
#ifdef DEXTOP_WAYLAND_GPU
    gpu_buffer_unref(surface->gpu_buffer, true);
    surface->gpu_buffer = NULL;
    linux_gpu_image_destroy(surface->gpu_shm);
    surface->gpu_shm = NULL;
#endif
    free(surface->pixels.data);
    surface->pixels = (struct pixels){0};
}

static void surface_apply_cached(struct surface *surface) {
    struct surface *cached = surface->cached;
    if (!cached || !surface->cached_commit) return;
    if (surface->cached_attach) {
        surface_clear_content(surface);
        surface->pixels = cached->pixels;
        cached->pixels = (struct pixels){0};
#ifdef DEXTOP_WAYLAND_GPU
        surface->gpu_buffer = cached->gpu_buffer;
        cached->gpu_buffer = NULL;
#endif
    }
    surface->scale = cached->scale;
    surface->opaque = cached->opaque;
#ifdef DEXTOP_WAYLAND_GPU
    surface->view = cached->view;
    presentation_discard_list(&surface->committed_feedback);
    wl_list_insert_list(&surface->committed_feedback, &cached->committed_feedback);
    wl_list_init(&cached->committed_feedback);
#endif
    wl_list_insert_list(&surface->ready_frames, &cached->pending_frames);
    wl_list_init(&cached->pending_frames);
    surface->cached_commit = surface->cached_attach = false;
}

static void surface_apply_children(struct surface *surface) {
    struct surface *child;
    wl_list_for_each(child, &surface->children, child_link) {
        /* Subsurface positions are latched by the parent, even in desync mode. */
        child->sub_x = child->pending_sub_x;
        child->sub_y = child->pending_sub_y;
        if (surface_synchronized(child)) {
            surface_apply_cached(child);
            surface_apply_children(child);
        }
    }
}

static void surface_cache_destroy(struct surface *surface) {
    struct surface *cached = surface->cached;
    if (!cached) return;
    surface_clear_content(cached);
    struct wl_resource *callback, *tmp;
    wl_resource_for_each_safe(callback, tmp, &cached->pending_frames) wl_resource_destroy(callback);
#ifdef DEXTOP_WAYLAND_GPU
    presentation_discard(cached);
#endif
    free(cached);
    surface->cached = NULL;
    surface->cached_commit = surface->cached_attach = false;
}

#ifdef DEXTOP_WAYLAND_GPU
static void constraints_commit(struct surface *surface);
#endif
static void surface_commit(struct wl_client *client, struct wl_resource *resource) {
    struct surface *surface = wl_resource_get_user_data(resource);
#ifdef DEXTOP_WAYLAND_GPU
    if (surface->xdg && surface->xdg->deck && surface->pending_attach && surface->pending_buffer) {
        surface->xdg->deck_buffer_serial++;
        surface->xdg->deck_buffers++;
        uint64_t committed_ns = gpu_now_ns();
        struct xdg *deck = surface->xdg;
        if (deck->deck_previous_commit_ns) {
            uint64_t gap = committed_ns - deck->deck_previous_commit_ns;
            unsigned bucket = gap < 25000000 ? 0 : gap < 40000000 ? 1 : gap < 60000000 ? 2 : 3;
            deck->deck_commit_gaps[bucket]++;
            if (gap > deck->deck_commit_gap_max_ns) deck->deck_commit_gap_max_ns = gap;
        }
        deck->deck_previous_commit_ns = committed_ns;
        deck->deck_buffer_ns = committed_ns;
    }
#endif
    struct surface *owner = surface;
    bool synchronized = surface_synchronized(surface);
    if (synchronized) {
        if (!owner->cached) {
            owner->cached = calloc(1, sizeof(*owner->cached));
            if (!owner->cached) { wl_client_post_no_memory(client); return; }
            owner->cached->comp = owner->comp;
            wl_list_init(&owner->cached->pending_frames);
#ifdef DEXTOP_WAYLAND_GPU
            wl_list_init(&owner->cached->presentation_feedback);
            wl_list_init(&owner->cached->committed_feedback);
#endif
        }
        surface = owner->cached;
        surface->parent = owner->parent;
        surface->pending_scale = owner->pending_scale;
        surface->pending_opaque = owner->pending_opaque;
        surface->pending_attach = owner->pending_attach;
        surface->pending_buffer = owner->pending_buffer;
        owner->cached_attach |= owner->pending_attach;
        owner->pending_attach = false;
        owner->pending_buffer = NULL;
        wl_list_insert_list(&surface->pending_frames, &owner->pending_frames);
        wl_list_init(&owner->pending_frames);
#ifdef DEXTOP_WAYLAND_GPU
        surface->pending_view = owner->pending_view;
        wl_list_insert_list(&surface->presentation_feedback, &owner->presentation_feedback);
        wl_list_init(&owner->presentation_feedback);
#endif
        owner->cached_commit = true;
    } else {
        surface_apply_cached(surface);
    }
    int old_width = surface_width(surface), old_height = surface_height(surface);
    surface->scale = surface->pending_scale;
    surface->opaque = surface->pending_opaque;
#ifdef DEXTOP_WAYLAND_GPU
    if (!synchronized) constraints_commit(surface);
#endif
#ifdef DEXTOP_WAYLAND_GPU
    /* A commit supersedes the previous, unpresented content. New feedback
     * belongs to this commit and must survive until it is submitted. */
    presentation_discard_list(&surface->committed_feedback);
    wl_list_insert_list(&surface->committed_feedback, &surface->presentation_feedback);
    wl_list_init(&surface->presentation_feedback);
    surface->comp->gpu_commits++;
    surface->view = surface->pending_view;
#endif
    if (surface->pending_attach) {
        surface->pending_attach = false;
        struct wl_resource *buffer = surface->pending_buffer;
        surface->pending_buffer = NULL;
#ifdef DEXTOP_WAYLAND_GPU
        struct dmabuf *previous = surface->gpu_buffer;
        surface->gpu_buffer = NULL;
        linux_gpu_image_destroy(surface->gpu_shm);
        surface->gpu_shm = NULL;
#endif
        if (buffer) {
#ifdef DEXTOP_WAYLAND_GPU
            if (!gpu_take_buffer(surface, buffer)) {
#endif
            surface_take_buffer(surface, buffer);
#ifdef DEXTOP_WAYLAND_GPU
            if (!wl_resource_instance_of(buffer, &wl_buffer_interface, &dmabuf_buffer_impl) ||
                ((struct dmabuf *)wl_resource_get_user_data(buffer))->busy == 0)
            deck_buffer_release_queue(surface->comp, buffer, root_of(owner)->xdg && root_of(owner)->xdg->deck);
#else
            wl_buffer_send_release(buffer);
#endif
#ifdef DEXTOP_WAYLAND_GPU
            }
#endif
        } else {
            free(surface->pixels.data);
            surface->pixels = (struct pixels){0};
            if (surface->xdg && surface->xdg->mapped) {
                if(surface->comp->keyboard_focus==surface)focus_keyboard(surface->comp,NULL);
                if(surface->comp->pointer_focus==surface)pointer_focus(surface->comp,NULL,0,0);
                surface->xdg->mapped = false;
                emit_window(surface->comp, surface->xdg, "unmap");
            }
        }
#ifdef DEXTOP_WAYLAND_GPU
        gpu_buffer_unref(previous, true);
#endif
    }
    if (synchronized) return;
    surface_apply_children(surface);
    schedule_frames(surface);
    struct surface *root = root_of(surface);
    if (root->xdg) {
        if (!root->xdg->configured) xdg_send_configure(root->xdg);
        else if (root == surface) {
            if (root->xdg->mapped && !root->xdg->has_geometry &&
                (old_width != surface_width(surface) || old_height != surface_height(surface)))
                emit_window(surface->comp, root->xdg, "configure");
            xdg_map_if_ready(root->xdg);
        }
        root->xdg->needs_redraw = true;
        /* gamescope paces the producer. With VSync off, forward its latest
         * frame without another independently phased cap. */
        if (root->xdg->deck && root->xdg->deck_no_vsync && root->xdg->mapped) {
            root->xdg->needs_redraw = false;
            window_draw(root->xdg);
        }
    }
}

static void surface_offset(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y) {}

static const struct wl_surface_interface surface_impl = {
    .destroy = surface_destroy_request, .attach = surface_attach, .damage = surface_damage,
    .frame = surface_frame, .set_opaque_region = surface_opaque_region, .set_input_region = surface_region,
    .commit = surface_commit, .set_buffer_transform = surface_transform, .set_buffer_scale = surface_scale,
    .damage_buffer = surface_damage, .offset = surface_offset,
};

static void xdg_forget(struct xdg *xdg);

static void surface_resource_destroy(struct wl_resource *resource) {
    struct surface *surface = wl_resource_get_user_data(resource);
    surface_cache_destroy(surface);
    if (surface->subsurface) wl_resource_set_user_data(surface->subsurface, NULL);
#ifdef DEXTOP_WAYLAND_GPU
    presentation_discard(surface);
    if (surface->viewport) wl_resource_set_user_data(surface->viewport, NULL);
#endif
    struct comp *comp = surface->comp;
    struct wl_resource *callback, *tmp;
    wl_resource_for_each_safe(callback, tmp, &surface->pending_frames) wl_resource_destroy(callback);
    wl_resource_for_each_safe(callback, tmp, &surface->ready_frames) wl_resource_destroy(callback);
    struct surface *child, *ctmp;
    wl_list_for_each_safe(child, ctmp, &surface->children, child_link) {
        wl_list_remove(&child->child_link);
        wl_list_init(&child->child_link);
        child->parent = NULL;
    }
    if (surface->parent) {
        wl_list_remove(&surface->child_link);
        surface->parent = NULL;
    }
    if (surface->xdg) {
        xdg_forget(surface->xdg);
    }
    if (comp->pointer_focus == surface) comp->pointer_focus = NULL;
    if (comp->keyboard_focus == surface) comp->keyboard_focus = NULL;
#ifdef DEXTOP_WAYLAND_GPU
    gpu_buffer_unref(surface->gpu_buffer, true);
    linux_gpu_image_destroy(surface->gpu_shm);
#endif
    free(surface->pixels.data);
    free(surface);
}

/* ------------------------------------------------------------- compositor */

struct pointer_rect {int64_t x1,y1,x2,y2;};
struct pointer_region {struct pointer_rect *rects;size_t count;};
static void surface_opaque_region(struct wl_client *c, struct wl_resource *r, struct wl_resource *region) {
    struct surface *surface = wl_resource_get_user_data(r);
    struct pointer_region *rects = region ? wl_resource_get_user_data(region) : NULL;
    surface->pending_opaque = (struct opaque_rect){0};
    /* A single rectangle is sufficient for full-surface opaque declarations.
     * Complex regions keep the buffer's alpha rather than overclaiming opacity. */
    if (rects && rects->count == 1) {
        struct pointer_rect q = rects->rects[0];
        surface->pending_opaque = (struct opaque_rect){q.x1, q.y1, q.x2, q.y2, true};
    }
}
static void region_clear(struct pointer_region *region){free(region->rects);region->rects=NULL;region->count=0;}
static bool region_append(struct pointer_region *region,struct pointer_rect rect){
    if(rect.x1>=rect.x2||rect.y1>=rect.y2)return true;
    if(region->count>=4096)return false;
    void *next=realloc(region->rects,(region->count+1)*sizeof(rect));if(!next)return false;
    region->rects=next;region->rects[region->count++]=rect;return true;
}
static bool region_copy(struct pointer_region *dest,const struct pointer_region *src){
    struct pointer_region copy={0};
    for(size_t i=0;i<src->count;i++)if(!region_append(&copy,src->rects[i])){region_clear(&copy);return false;}
    region_clear(dest);*dest=copy;return true;
}
static void region_resource_destroy(struct wl_resource*r){struct pointer_region*region=wl_resource_get_user_data(r);region_clear(region);free(region);}
static void region_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static void region_add(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y, int32_t w, int32_t h) {
    if(w<=0||h<=0)return;
    if(!region_append(wl_resource_get_user_data(r),(struct pointer_rect){x,y,(int64_t)x+w,(int64_t)y+h}))wl_client_post_no_memory(c);
}
static void region_subtract(struct wl_client*c,struct wl_resource*r,int32_t x,int32_t y,int32_t w,int32_t h){
    if(w<=0||h<=0)return;
    struct pointer_region *region=wl_resource_get_user_data(r),result={0};bool ok=true;
    int64_t x2=(int64_t)x+w,y2=(int64_t)y+h;
    for(size_t i=0;i<region->count&&ok;i++){
        struct pointer_rect q=region->rects[i];
        int64_t a=q.x1>x?q.x1:x,b=q.y1>y?q.y1:y,d=q.x2<x2?q.x2:x2,e=q.y2<y2?q.y2:y2;
        if(a>=d||b>=e){ok=region_append(&result,q);continue;}
        ok=region_append(&result,(struct pointer_rect){q.x1,q.y1,q.x2,b})&&
           region_append(&result,(struct pointer_rect){q.x1,e,q.x2,q.y2})&&
           region_append(&result,(struct pointer_rect){q.x1,b,a,e})&&
           region_append(&result,(struct pointer_rect){d,b,q.x2,e});
    }
    if(!ok){region_clear(&result);wl_client_post_no_memory(c);return;}
    region_clear(region);*region=result;
}
static const struct wl_region_interface region_impl = {.destroy = region_destroy, .add = region_add, .subtract = region_subtract};

static void compositor_create_surface(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct comp *comp = wl_resource_get_user_data(resource);
    struct surface *surface = calloc(1, sizeof(*surface));
    if (!surface) { wl_client_post_no_memory(client); return; }
    surface->comp = comp;
    surface->scale = surface->pending_scale = 1;
#ifdef DEXTOP_WAYLAND_GPU
    wl_list_init(&surface->presentation_feedback);
    wl_list_init(&surface->committed_feedback);
    surface->view = surface->pending_view = (struct viewport_state){.width = -1, .height = -1,
        .dest_width = -1, .dest_height = -1};
#endif
    wl_list_init(&surface->pending_frames);
    wl_list_init(&surface->ready_frames);
    wl_list_init(&surface->children);
    wl_list_init(&surface->child_link);
    surface->resource = wl_resource_create(client, &wl_surface_interface, wl_resource_get_version(resource), id);
    wl_resource_set_implementation(surface->resource, &surface_impl, surface, surface_resource_destroy);
}

static void compositor_create_region(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct wl_resource *region = wl_resource_create(client, &wl_region_interface, 1, id);
    struct pointer_region *data=calloc(1,sizeof(*data));
    if(!region||!data){free(data);wl_client_post_no_memory(client);return;}
    wl_resource_set_implementation(region, &region_impl, data, region_resource_destroy);
}

static const struct wl_compositor_interface compositor_impl = {
    .create_surface = compositor_create_surface, .create_region = compositor_create_region,
};

static void bind_compositor(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &wl_compositor_interface, version, id);
    wl_resource_set_implementation(resource, &compositor_impl, data, NULL);
}

/* ---------------------------------------------------------- subcompositor */

static void subsurface_destroy_request(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static void subsurface_position(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y) {
    struct surface *surface = wl_resource_get_user_data(r);
    if (!surface) return;
    surface->pending_sub_x = x;
    surface->pending_sub_y = y;
}

static void subsurface_place(struct wl_client *c, struct wl_resource *r, struct wl_resource *sibling) {
    struct surface *surface = wl_resource_get_user_data(r);
    struct surface *other = wl_resource_get_user_data(sibling);
    if (!surface || !surface->parent || !other || other == surface ||
        (other != surface->parent && other->parent != surface->parent)) return;
    wl_list_remove(&surface->child_link);
    surface->below_parent = other == surface->parent ? false : other->below_parent;
    if (other == surface->parent) wl_list_insert(&surface->parent->children, &surface->child_link);
    else wl_list_insert(&other->child_link, &surface->child_link);
}

static void subsurface_place_below(struct wl_client *c, struct wl_resource *r, struct wl_resource *sibling) {
    struct surface *surface = wl_resource_get_user_data(r);
    struct surface *other = wl_resource_get_user_data(sibling);
    if (!surface || !surface->parent || !other || other == surface ||
        (other != surface->parent && other->parent != surface->parent)) return;
    wl_list_remove(&surface->child_link);
    surface->below_parent = other == surface->parent ? true : other->below_parent;
    if (other == surface->parent) wl_list_insert(surface->parent->children.prev, &surface->child_link);
    else wl_list_insert(other->child_link.prev, &surface->child_link);
}

static void subsurface_sync(struct wl_client *c, struct wl_resource *r) {
    struct surface *surface = wl_resource_get_user_data(r);
    if (surface) surface->sub_sync = true;
}

static void subsurface_desync(struct wl_client *c, struct wl_resource *r) {
    struct surface *surface = wl_resource_get_user_data(r);
    if (!surface) return;
    surface->sub_sync = false;
    if (!surface_synchronized(surface)) {
        surface_apply_cached(surface);
        surface_apply_children(surface);
        struct surface *root = root_of(surface);
        if (root->xdg) root->xdg->needs_redraw = true;
    }
}

static const struct wl_subsurface_interface subsurface_impl = {
    .destroy = subsurface_destroy_request, .set_position = subsurface_position,
    .place_above = subsurface_place, .place_below = subsurface_place_below,
    .set_sync = subsurface_sync, .set_desync = subsurface_desync,
};

static void subsurface_resource_destroy(struct wl_resource *resource) {
    struct surface *surface = wl_resource_get_user_data(resource);
    if (!surface) return;
    surface_cache_destroy(surface);
    surface->subsurface = NULL;
    if (surface->parent) {
        struct surface *root = root_of(surface);
        wl_list_remove(&surface->child_link);
        wl_list_init(&surface->child_link);
        surface->parent = NULL;
        if (root->xdg) root->xdg->needs_redraw = true;
    }
}

static void subcompositor_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static void subcompositor_get(struct wl_client *client, struct wl_resource *resource, uint32_t id,
                              struct wl_resource *surface_resource, struct wl_resource *parent_resource) {
    struct surface *surface = wl_resource_get_user_data(surface_resource);
    struct surface *parent = wl_resource_get_user_data(parent_resource);
    struct wl_resource *sub = wl_resource_create(client, &wl_subsurface_interface, 1, id);
    wl_resource_set_implementation(sub, &subsurface_impl, surface, subsurface_resource_destroy);
    surface->subsurface = sub;
    surface->parent = parent;
    surface->sub_sync = true;
    wl_list_remove(&surface->child_link);
    wl_list_insert(parent->children.prev, &surface->child_link);
}

static const struct wl_subcompositor_interface subcompositor_impl = {
    .destroy = subcompositor_destroy, .get_subsurface = subcompositor_get,
};

static void bind_subcompositor(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &wl_subcompositor_interface, version, id);
    wl_resource_set_implementation(resource, &subcompositor_impl, data, NULL);
}

/* ---------------------------------------------------------------- output */

static void output_destroy(struct wl_resource *r) { wl_list_remove(wl_resource_get_link(r)); }
static void output_release(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static const struct wl_output_interface output_impl = {.release = output_release};

static void bind_output(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct comp *comp = data;
    struct wl_resource *resource = wl_resource_create(client, &wl_output_interface, version, id);
    wl_resource_set_implementation(resource, &output_impl, comp, output_destroy);
    wl_list_insert(&comp->outputs, wl_resource_get_link(resource));
    wl_output_send_geometry(resource, 0, 0, comp->width * 254 / 1600, comp->height * 254 / 1600,
                            WL_OUTPUT_SUBPIXEL_UNKNOWN, "Dextop", "Plasma", WL_OUTPUT_TRANSFORM_NORMAL);
    wl_output_send_mode(resource, WL_OUTPUT_MODE_CURRENT | WL_OUTPUT_MODE_PREFERRED, comp->width, comp->height, comp->refresh_mhz);
    if (version >= 4) {
        wl_output_send_name(resource, "DEXTOP-1");
        wl_output_send_description(resource, "Dextop Plasma display");
    }
    if (version >= 2) {
        wl_output_send_scale(resource, 1);
        wl_output_send_done(resource);
    }
}

/* ------------------------------------------------------------------ seat */

static void unlink_resource(struct wl_resource *resource) { wl_list_remove(wl_resource_get_link(resource)); }

static void pointer_set_cursor(struct wl_client *c, struct wl_resource *r, uint32_t serial, struct wl_resource *s, int32_t x, int32_t y) {}
static void pointer_release(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static const struct wl_pointer_interface pointer_impl = {.set_cursor = pointer_set_cursor, .release = pointer_release};

static void keyboard_release(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static const struct wl_keyboard_interface keyboard_impl = {.release = keyboard_release};

static void seat_get_pointer(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct comp *comp = wl_resource_get_user_data(resource);
    struct wl_resource *pointer = wl_resource_create(client, &wl_pointer_interface, wl_resource_get_version(resource), id);
    wl_resource_set_implementation(pointer, &pointer_impl, comp, unlink_resource);
    wl_list_insert(&comp->pointers, wl_resource_get_link(pointer));
}

static void send_keymap(struct wl_resource *keyboard) {
    size_t size = sizeof(dextop_keymap_us);
    int fd = memfd_create("dextop-keymap", MFD_CLOEXEC);
    if (fd < 0) return;
    if (write(fd, dextop_keymap_us, size) == (ssize_t)size)
        wl_keyboard_send_keymap(keyboard, WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd, (uint32_t)size);
    close(fd);
}

static void seat_get_keyboard(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct comp *comp = wl_resource_get_user_data(resource);
    uint32_t version = wl_resource_get_version(resource);
    struct wl_resource *keyboard = wl_resource_create(client, &wl_keyboard_interface, version, id);
    wl_resource_set_implementation(keyboard, &keyboard_impl, comp, unlink_resource);
    wl_list_insert(&comp->keyboards, wl_resource_get_link(keyboard));
    send_keymap(keyboard);
    if (version >= WL_KEYBOARD_REPEAT_INFO_SINCE_VERSION) wl_keyboard_send_repeat_info(keyboard, 30, 400);
    if (comp->keyboard_focus && wl_resource_get_client(comp->keyboard_focus->resource) == client) {
        wl_keyboard_send_enter(keyboard, wl_display_next_serial(comp->display), comp->keyboard_focus->resource, &comp->pressed_keys);
        wl_keyboard_send_modifiers(keyboard, wl_display_next_serial(comp->display), comp->mods_depressed, 0, comp->mods_locked, 0);
    }
}

static void seat_get_touch(struct wl_client *c, struct wl_resource *r, uint32_t id) {
    wl_resource_post_error(r, WL_SEAT_ERROR_MISSING_CAPABILITY, "no touch");
}

static void seat_release(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static const struct wl_seat_interface seat_impl = {
    .get_pointer = seat_get_pointer, .get_keyboard = seat_get_keyboard,
    .get_touch = seat_get_touch, .release = seat_release,
};

static void bind_seat(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &wl_seat_interface, version, id);
    wl_resource_set_implementation(resource, &seat_impl, data, NULL);
    wl_seat_send_capabilities(resource, WL_SEAT_CAPABILITY_POINTER | WL_SEAT_CAPABILITY_KEYBOARD);
    if (version >= WL_SEAT_NAME_SINCE_VERSION) wl_seat_send_name(resource, "seat0");
}

/* ------------------------------------------------------- data device stub */

static void source_offer(struct wl_client *c, struct wl_resource *r, const char *t) {}
static void source_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static void source_actions(struct wl_client *c, struct wl_resource *r, uint32_t a) {}
static const struct wl_data_source_interface source_impl = {.offer = source_offer, .destroy = source_destroy, .set_actions = source_actions};
static void device_drag(struct wl_client *c, struct wl_resource *r, struct wl_resource *s, struct wl_resource *o, struct wl_resource *i, uint32_t serial) {}
static void device_selection(struct wl_client *c, struct wl_resource *r, struct wl_resource *s, uint32_t serial) {}
static void device_release(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static const struct wl_data_device_interface device_impl = {.start_drag = device_drag, .set_selection = device_selection, .release = device_release};

static void ddm_create_source(struct wl_client *client, struct wl_resource *r, uint32_t id) {
    struct wl_resource *s = wl_resource_create(client, &wl_data_source_interface, wl_resource_get_version(r), id);
    wl_resource_set_implementation(s, &source_impl, NULL, NULL);
}

static void ddm_get_device(struct wl_client *client, struct wl_resource *r, uint32_t id, struct wl_resource *seat) {
    struct wl_resource *d = wl_resource_create(client, &wl_data_device_interface, wl_resource_get_version(r), id);
    wl_resource_set_implementation(d, &device_impl, NULL, NULL);
}

static const struct wl_data_device_manager_interface ddm_impl = {.create_data_source = ddm_create_source, .get_data_device = ddm_get_device};

static void bind_ddm(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &wl_data_device_manager_interface, version, id);
    wl_resource_set_implementation(resource, &ddm_impl, data, NULL);
}

/* ------------------------------------------------------------- xdg-shell */

static void xdg_send_configure(struct xdg *xdg) {
    struct comp *comp = xdg->comp;
    if (!xdg->role) return;
    if (xdg->popup) {
        xdg_popup_send_configure(xdg->role, xdg->popup_x, xdg->popup_y, xdg->popup_w, xdg->popup_h);
    } else {
        struct wl_array states;
        wl_array_init(&states);
        if (xdg->maximized) {
            uint32_t *state = wl_array_add(&states, sizeof(uint32_t));
            if (state) *state = XDG_TOPLEVEL_STATE_MAXIMIZED;
        }
        if (comp->keyboard_focus && xdg->surface && comp->keyboard_focus == xdg->surface) {
            uint32_t *s = wl_array_add(&states, sizeof(uint32_t));
            if (s) *s = XDG_TOPLEVEL_STATE_ACTIVATED;
        }
        xdg_toplevel_send_configure(xdg->role, xdg->want_w, xdg->want_h, &states);
        wl_array_release(&states);
    }
    xdg_surface_send_configure(xdg->xdg_surface, wl_display_next_serial(comp->display));
    xdg->configured = true;
}

static void xdg_map_if_ready(struct xdg *xdg) {
    if (xdg->mapped || !xdg->surface || !xdg->role) return;
#ifdef DEXTOP_WAYLAND_GPU
    if (!xdg->surface->gpu_buffer && !xdg->surface->pixels.data) return;
#else
    if (!xdg->surface->pixels.data) return;
#endif
    xdg->mapped = true;
    emit_window(xdg->comp, xdg, "map");
}

static void xdg_forget(struct xdg *xdg) {
#ifdef DEXTOP_WAYLAND_GPU
    deck_direct_hide(xdg, true);
    linux_gpu_window_destroy(xdg->gpu_window);
    xdg->gpu_window = NULL;
#endif
    if (xdg->mapped) emit_window(xdg->comp, xdg, "unmap");
    xdg->mapped = false;
    if (xdg->window) { ANativeWindow_release(xdg->window); xdg->window = NULL; }
    if (xdg->comp->xdgs[xdg->window_id & 1023] == xdg) xdg->comp->xdgs[xdg->window_id & 1023] = NULL;
    if (xdg->surface) { xdg->surface->xdg = NULL; xdg->surface = NULL; }
}

static void toplevel_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static void toplevel_parent(struct wl_client *c, struct wl_resource *r, struct wl_resource *p) {}

static void toplevel_title(struct wl_client *c, struct wl_resource *r, const char *title) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    if (!xdg) return;
    snprintf(xdg->title, sizeof(xdg->title), "%s", title);
    if (xdg->mapped) emit_window(xdg->comp, xdg, "title");
}

static bool is_deck_client(const struct wl_client *client) {
    pid_t pid; uid_t uid; gid_t gid;
    wl_client_get_credentials((struct wl_client *)client, &pid, &uid, &gid);
    char path[64], data[65536];
    snprintf(path, sizeof(path), "/proc/%d/environ", (int)pid);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    ssize_t n = fd >= 0 ? read(fd, data, sizeof(data)) : -1;
    if (fd >= 0) close(fd);
    static const char marker[] = "DEXTOP_STEAM_INSIDE=1";
    for (ssize_t i = 0; i + (ssize_t)sizeof(marker) <= n; i++)
        if ((i == 0 || !data[i - 1]) && !memcmp(data + i, marker, sizeof(marker))) return true;
    return false;
}

static bool filter_deck_global(const struct wl_client *client, const struct wl_global *global, void *data) {
    struct comp *comp = data;
    if (global == comp->deck_dmabuf_global || global == comp->deck_ahb_global) return is_deck_client(client);
    if (global == comp->normal_dmabuf_global) return !is_deck_client(client);
    return true;
}

static void toplevel_app_id(struct wl_client *c, struct wl_resource *r, const char *app_id) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    if (!xdg) return;
    snprintf(xdg->app_id, sizeof(xdg->app_id), "%s", app_id);
    if (!strcmp(app_id, "gamescope")) {
        xdg->deck = is_deck_client(c);
        if (xdg->deck) LOGI("Deck VSync eligible window=%u", xdg->window_id);
    }
    if (xdg->mapped) emit_window(xdg->comp, xdg, "class");
}

static void toplevel_menu(struct wl_client *c, struct wl_resource *r, struct wl_resource *seat, uint32_t serial, int32_t x, int32_t y) {}
static void toplevel_move(struct wl_client *c, struct wl_resource *r, struct wl_resource *seat, uint32_t serial) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    char json[96];
    if (!xdg) return;
    snprintf(json, sizeof(json), "{\"type\":\"move\",\"id\":%u,\"popup\":false}", xdg->window_id);
    emit(xdg->comp, json);
}
static void toplevel_resize(struct wl_client *c, struct wl_resource *r, struct wl_resource *seat, uint32_t serial, uint32_t edges) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    int direction = edges == 5 ? 0 : edges == 1 ? 1 : edges == 9 ? 2 : edges == 8 ? 3 :
        edges == 10 ? 4 : edges == 2 ? 5 : edges == 6 ? 6 : edges == 4 ? 7 : -1;
    if (!xdg || direction < 0) return;
    char json[128];
    snprintf(json, sizeof(json), "{\"type\":\"moveresize\",\"id\":%u,\"direction\":%d}", xdg->window_id, direction);
    emit(xdg->comp, json);
}
static void toplevel_maximize_request(struct wl_resource *r, int action) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    if (!xdg) return;
    char json[96];
    snprintf(json, sizeof(json), "{\"type\":\"maximize\",\"id\":%u,\"action\":%d}", xdg->window_id, action);
    emit(xdg->comp, json);
}
static void toplevel_maximize(struct wl_client *c, struct wl_resource *r) { toplevel_maximize_request(r, 1); }
static void toplevel_unmaximize(struct wl_client *c, struct wl_resource *r) { toplevel_maximize_request(r, 0); }
static void toplevel_size(struct wl_client *c, struct wl_resource *r, int32_t w, int32_t h) {}
static void toplevel_noop(struct wl_client *c, struct wl_resource *r) {}
static void toplevel_fullscreen(struct wl_client *c, struct wl_resource *r, struct wl_resource *output) {}

static void toplevel_minimize(struct wl_client *c, struct wl_resource *r) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    char json[96];
    if (!xdg) return;
    snprintf(json, sizeof(json), "{\"type\":\"minimize\",\"id\":%u,\"popup\":false}", xdg->window_id);
    emit(xdg->comp, json);
}

static const struct xdg_toplevel_interface toplevel_impl = {
    .destroy = toplevel_destroy, .set_parent = toplevel_parent, .set_title = toplevel_title,
    .set_app_id = toplevel_app_id, .show_window_menu = toplevel_menu, .move = toplevel_move,
    .resize = toplevel_resize, .set_max_size = toplevel_size, .set_min_size = toplevel_size,
    .set_maximized = toplevel_maximize, .unset_maximized = toplevel_unmaximize,
    .set_fullscreen = toplevel_fullscreen, .unset_fullscreen = toplevel_noop,
    .set_minimized = toplevel_minimize,
};

static void role_resource_destroy(struct wl_resource *resource) {
    struct xdg *xdg = wl_resource_get_user_data(resource);
    if (!xdg) return;
    xdg->role = NULL;
    if (xdg->mapped) { emit_window(xdg->comp, xdg, "unmap"); xdg->mapped = false; }
}

static void popup_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static void popup_grab(struct wl_client *c, struct wl_resource *r, struct wl_resource *seat, uint32_t serial) {}

static void positioner_place(struct xdg *xdg, const struct positioner *p) {
    int32_t ax = p->anchor_x, ay = p->anchor_y;
    switch (p->anchor) {
        case XDG_POSITIONER_ANCHOR_TOP: ax += p->anchor_w / 2; break;
        case XDG_POSITIONER_ANCHOR_BOTTOM: ax += p->anchor_w / 2; ay += p->anchor_h; break;
        case XDG_POSITIONER_ANCHOR_LEFT: ay += p->anchor_h / 2; break;
        case XDG_POSITIONER_ANCHOR_RIGHT: ax += p->anchor_w; ay += p->anchor_h / 2; break;
        case XDG_POSITIONER_ANCHOR_TOP_LEFT: break;
        case XDG_POSITIONER_ANCHOR_BOTTOM_LEFT: ay += p->anchor_h; break;
        case XDG_POSITIONER_ANCHOR_TOP_RIGHT: ax += p->anchor_w; break;
        case XDG_POSITIONER_ANCHOR_BOTTOM_RIGHT: ax += p->anchor_w; ay += p->anchor_h; break;
        default: ax += p->anchor_w / 2; ay += p->anchor_h / 2; break;
    }
    int32_t x = ax, y = ay;
    switch (p->gravity) {
        case XDG_POSITIONER_GRAVITY_TOP: x -= p->width / 2; y -= p->height; break;
        case XDG_POSITIONER_GRAVITY_BOTTOM: x -= p->width / 2; break;
        case XDG_POSITIONER_GRAVITY_LEFT: x -= p->width; y -= p->height / 2; break;
        case XDG_POSITIONER_GRAVITY_RIGHT: y -= p->height / 2; break;
        case XDG_POSITIONER_GRAVITY_TOP_LEFT: x -= p->width; y -= p->height; break;
        case XDG_POSITIONER_GRAVITY_BOTTOM_LEFT: x -= p->width; break;
        case XDG_POSITIONER_GRAVITY_TOP_RIGHT: y -= p->height; break;
        case XDG_POSITIONER_GRAVITY_BOTTOM_RIGHT: break;
        default: x -= p->width / 2; y -= p->height / 2; break;
    }
    xdg->popup_x = x + p->offset_x;
    xdg->popup_y = y + p->offset_y;
    xdg->popup_w = p->width;
    xdg->popup_h = p->height;
}

static void popup_reposition(struct wl_client *c, struct wl_resource *r, struct wl_resource *positioner, uint32_t token) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    if (!xdg) return;
    positioner_place(xdg, wl_resource_get_user_data(positioner));
    xdg_popup_send_repositioned(r, token);
    xdg_send_configure(xdg);
    if (xdg->mapped) emit_window(xdg->comp, xdg, "configure");
}

static const struct xdg_popup_interface popup_impl = {.destroy = popup_destroy, .grab = popup_grab, .reposition = popup_reposition};

static void xdg_surface_destroy_request(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static struct xdg *xdg_register(struct xdg *xdg) {
    struct comp *comp = xdg->comp;
    for (int tries = 0; tries < 1024; ++tries) {
        uint32_t id = comp->next_window_id++;
        if (id == 0) continue;
        if (!comp->xdgs[id & 1023]) {
            xdg->window_id = id;
            comp->xdgs[id & 1023] = xdg;
            return xdg;
        }
    }
    return xdg;
}

static void xdg_surface_get_toplevel(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct xdg *xdg = wl_resource_get_user_data(resource);
    xdg->role = wl_resource_create(client, &xdg_toplevel_interface, wl_resource_get_version(resource), id);
    wl_resource_set_implementation(xdg->role, &toplevel_impl, xdg, role_resource_destroy);
    xdg_register(xdg);
}

static void xdg_surface_get_popup(struct wl_client *client, struct wl_resource *resource, uint32_t id,
                                  struct wl_resource *parent, struct wl_resource *positioner) {
    struct xdg *xdg = wl_resource_get_user_data(resource);
    xdg->popup = true;
    xdg->parent = parent ? wl_resource_get_user_data(parent) : NULL;
    xdg->role = wl_resource_create(client, &xdg_popup_interface, wl_resource_get_version(resource), id);
    wl_resource_set_implementation(xdg->role, &popup_impl, xdg, role_resource_destroy);
    /* Positions are relative to the parent's window geometry, as the window Plasma shows. */
    positioner_place(xdg, wl_resource_get_user_data(positioner));
    xdg_register(xdg);
}

static void xdg_surface_geometry(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y, int32_t w, int32_t h) {
    struct xdg *xdg = wl_resource_get_user_data(r);
    bool changed = !xdg->has_geometry || xdg->geo_w != w || xdg->geo_h != h;
    xdg->geo_x = x; xdg->geo_y = y; xdg->geo_w = w; xdg->geo_h = h;
    xdg->has_geometry = true;
    if (changed && xdg->mapped && !xdg->popup) emit_window(xdg->comp, xdg, "configure");
}

static void xdg_surface_ack(struct wl_client *c, struct wl_resource *r, uint32_t serial) {}

static const struct xdg_surface_interface xdg_surface_impl = {
    .destroy = xdg_surface_destroy_request, .get_toplevel = xdg_surface_get_toplevel,
    .get_popup = xdg_surface_get_popup, .set_window_geometry = xdg_surface_geometry,
    .ack_configure = xdg_surface_ack,
};

static void xdg_surface_resource_destroy(struct wl_resource *resource) {
    struct xdg *xdg = wl_resource_get_user_data(resource);
    xdg_forget(xdg);
    if (xdg->role) wl_resource_set_user_data(xdg->role, NULL);
    /* Popups of this surface keep running without a parent. */
    for (int i = 0; i < 1024; ++i) {
        struct xdg *other = xdg->comp->xdgs[i];
        if (other && other->parent == xdg) other->parent = NULL;
    }
    free(xdg);
}

static void positioner_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static void positioner_size(struct wl_client *c, struct wl_resource *r, int32_t w, int32_t h) {
    struct positioner *p = wl_resource_get_user_data(r); p->width = w; p->height = h;
}
static void positioner_anchor_rect(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y, int32_t w, int32_t h) {
    struct positioner *p = wl_resource_get_user_data(r); p->anchor_x = x; p->anchor_y = y; p->anchor_w = w; p->anchor_h = h;
}
static void positioner_anchor(struct wl_client *c, struct wl_resource *r, uint32_t a) { ((struct positioner *)wl_resource_get_user_data(r))->anchor = a; }
static void positioner_gravity(struct wl_client *c, struct wl_resource *r, uint32_t g) { ((struct positioner *)wl_resource_get_user_data(r))->gravity = g; }
static void positioner_constraint(struct wl_client *c, struct wl_resource *r, uint32_t a) {}
static void positioner_offset(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y) {
    struct positioner *p = wl_resource_get_user_data(r); p->offset_x = x; p->offset_y = y;
}
static void positioner_reactive(struct wl_client *c, struct wl_resource *r) {}
static void positioner_parent_size(struct wl_client *c, struct wl_resource *r, int32_t w, int32_t h) {}
static void positioner_parent_configure(struct wl_client *c, struct wl_resource *r, uint32_t serial) {}

static const struct xdg_positioner_interface positioner_impl = {
    .destroy = positioner_destroy, .set_size = positioner_size, .set_anchor_rect = positioner_anchor_rect,
    .set_anchor = positioner_anchor, .set_gravity = positioner_gravity,
    .set_constraint_adjustment = positioner_constraint, .set_offset = positioner_offset,
    .set_reactive = positioner_reactive, .set_parent_size = positioner_parent_size,
    .set_parent_configure = positioner_parent_configure,
};

static void free_user_data(struct wl_resource *resource) { free(wl_resource_get_user_data(resource)); }

static void wm_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static void wm_create_positioner(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct positioner *p = calloc(1, sizeof(*p));
    struct wl_resource *r = wl_resource_create(client, &xdg_positioner_interface, wl_resource_get_version(resource), id);
    wl_resource_set_implementation(r, &positioner_impl, p, free_user_data);
}

static void wm_get_xdg_surface(struct wl_client *client, struct wl_resource *resource, uint32_t id, struct wl_resource *surface_resource) {
    struct comp *comp = wl_resource_get_user_data(resource);
    struct surface *surface = wl_resource_get_user_data(surface_resource);
    struct xdg *xdg = calloc(1, sizeof(*xdg));
    xdg->comp = comp;
    xdg->surface = surface;
    surface->xdg = xdg;
    xdg->xdg_surface = wl_resource_create(client, &xdg_surface_interface, wl_resource_get_version(resource), id);
    wl_resource_set_implementation(xdg->xdg_surface, &xdg_surface_impl, xdg, xdg_surface_resource_destroy);
}

static void wm_pong(struct wl_client *c, struct wl_resource *r, uint32_t serial) {}

static const struct xdg_wm_base_interface wm_impl = {
    .destroy = wm_destroy, .create_positioner = wm_create_positioner,
    .get_xdg_surface = wm_get_xdg_surface, .pong = wm_pong,
};

static void bind_wm(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &xdg_wm_base_interface, version, id);
    wl_resource_set_implementation(resource, &wm_impl, data, NULL);
}

/* ---------------------------------------------------------- xdg-decoration */

static void decoration_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

struct decoration_target { struct comp *comp; uint32_t id; };
static void decoration_free(struct wl_resource *r) { free(wl_resource_get_user_data(r)); }

static void decoration_mode(struct wl_client *c, struct wl_resource *r, uint32_t mode) {
    struct decoration_target *target = wl_resource_get_user_data(r);
    struct xdg *xdg = target ? xdg_by_id(target->comp, target->id) : NULL;
    bool client = mode == ZXDG_TOPLEVEL_DECORATION_V1_MODE_CLIENT_SIDE;
    if (xdg && xdg->client_decorated != client) {
        xdg->client_decorated = client;
        if (xdg->mapped) emit_window(xdg->comp, xdg, "decorations");
    }
    zxdg_toplevel_decoration_v1_send_configure(r, client ?
        ZXDG_TOPLEVEL_DECORATION_V1_MODE_CLIENT_SIDE : ZXDG_TOPLEVEL_DECORATION_V1_MODE_SERVER_SIDE);
}

static void decoration_unset(struct wl_client *c, struct wl_resource *r) { decoration_mode(c, r, 0); }

static const struct zxdg_toplevel_decoration_v1_interface decoration_impl = {
    .destroy = decoration_destroy, .set_mode = decoration_mode, .unset_mode = decoration_unset,
};

static void decoration_manager_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static void decoration_manager_get(struct wl_client *client, struct wl_resource *resource, uint32_t id, struct wl_resource *toplevel) {
    struct xdg *xdg = wl_resource_get_user_data(toplevel);
    struct decoration_target *target = calloc(1, sizeof(*target));
    if (!target) { wl_client_post_no_memory(client); return; }
    target->comp = xdg->comp; target->id = xdg->window_id;
    struct wl_resource *r = wl_resource_create(client, &zxdg_toplevel_decoration_v1_interface, 1, id);
    if (!r) { free(target); wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(r, &decoration_impl, target, decoration_free);
    zxdg_toplevel_decoration_v1_send_configure(r, ZXDG_TOPLEVEL_DECORATION_V1_MODE_SERVER_SIDE);
}

static const struct zxdg_decoration_manager_v1_interface decoration_manager_impl = {
    .destroy = decoration_manager_destroy, .get_toplevel_decoration = decoration_manager_get,
};

static void bind_decoration(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &zxdg_decoration_manager_v1_interface, version, id);
    wl_resource_set_implementation(resource, &decoration_manager_impl, data, NULL);
}

/* GTK 3 uses KDE's older negotiation rather than xdg-decoration. */
static void kde_decoration_mode(struct wl_client *client, struct wl_resource *r, uint32_t mode) {
    org_kde_kwin_server_decoration_send_mode(r, ORG_KDE_KWIN_SERVER_DECORATION_MODE_SERVER);
}
static const struct org_kde_kwin_server_decoration_interface kde_decoration_impl = {
    .release = decoration_destroy, .request_mode = kde_decoration_mode,
};
static void kde_decoration_create(struct wl_client *client, struct wl_resource *manager,
                                  uint32_t id, struct wl_resource *surface) {
    struct wl_resource *r = wl_resource_create(client, &org_kde_kwin_server_decoration_interface, 1, id);
    if (!r) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(r, &kde_decoration_impl, NULL, NULL);
    kde_decoration_mode(client, r, 0);
}
static const struct org_kde_kwin_server_decoration_manager_interface kde_decoration_manager_impl = {
    .create = kde_decoration_create,
};
static void bind_kde_decoration(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *r = wl_resource_create(client, &org_kde_kwin_server_decoration_manager_interface, 1, id);
    if (!r) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(r, &kde_decoration_manager_impl, data, NULL);
    org_kde_kwin_server_decoration_manager_send_default_mode(r, ORG_KDE_KWIN_SERVER_DECORATION_MANAGER_MODE_SERVER);
}

/* --------------------------------------------------------------- dma-buf */

static void dmabuf_buffer_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static const struct wl_buffer_interface dmabuf_buffer_impl = {.destroy = dmabuf_buffer_destroy};

static void dmabuf_resource_destroy(struct wl_resource *resource) {
    struct dmabuf *dmabuf = wl_resource_get_user_data(resource);
#ifdef DEXTOP_WAYLAND_GPU
    dmabuf->resource = NULL;
    gpu_buffer_unref(dmabuf, false);
#else
    if (dmabuf->map) munmap(dmabuf->map, dmabuf->map_size);
    close(dmabuf->fd);
    free(dmabuf);
#endif
}

static void params_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static void params_add(struct wl_client *c, struct wl_resource *r, int32_t fd, uint32_t plane, uint32_t offset,
                       uint32_t stride, uint32_t hi, uint32_t lo) {
    struct dmabuf_params *params = wl_resource_get_user_data(r);
    if (plane != 0 || params->fd >= 0) { close(fd); return; }
    params->fd = fd;
    params->offset = offset;
    params->stride = stride;
    params->modifier = ((uint64_t)hi << 32) | lo;
}

static struct wl_resource *params_make_buffer(struct wl_client *client, struct wl_resource *r, uint32_t id,
                                              int32_t width, int32_t height, uint32_t format) {
    struct dmabuf_params *params = wl_resource_get_user_data(r);
    if (params->used || params->fd < 0 || width <= 0 || height <= 0 ||
        width > 16384 || height > 16384 || params->stride < (uint64_t)width * 4 ||
        (params->modifier != DRM_FORMAT_MOD_LINEAR && !params->deck) ||
        (format != DRM_FORMAT_ARGB8888 && format != DRM_FORMAT_XRGB8888 &&
         format != DRM_FORMAT_ABGR8888 && format != DRM_FORMAT_XBGR8888)) {
        return NULL;
    }
    struct stat info;
    uint64_t required = (uint64_t)params->offset + (uint64_t)params->stride * (height - 1) + (uint64_t)width * 4;
    if (fstat(params->fd, &info) != 0 || info.st_size <= 0 || params->offset >= (uint64_t)info.st_size ||
        (params->modifier == DRM_FORMAT_MOD_LINEAR && required > (uint64_t)info.st_size)) return NULL;
#ifdef DEXTOP_WAYLAND_GPU
    if (params->modifier != DRM_FORMAT_MOD_LINEAR) {
        uint64_t mods[8];
        unsigned n = linux_gpu_modifiers(params->comp->gpu, format, mods, 8);
        bool supported = false;
        for (unsigned i = 0; i < n; i++) if (mods[i] == params->modifier) supported = true;
        if (!supported) return NULL;
    }
#else
    if (params->modifier != DRM_FORMAT_MOD_LINEAR) return NULL;
#endif
    params->used = true;
    struct dmabuf *dmabuf = calloc(1, sizeof(*dmabuf));
    if (!dmabuf) { wl_client_post_no_memory(client); return NULL; }
    dmabuf->fd = params->fd;
    params->fd = -1;
    dmabuf->offset = params->offset;
    dmabuf->stride = params->stride;
    dmabuf->width = width;
    dmabuf->height = height;
    dmabuf->format = format;
    dmabuf->modifier = params->modifier;
    struct wl_resource *buffer = wl_resource_create(client, &wl_buffer_interface, 1, id);
    if (!buffer) { close(dmabuf->fd); free(dmabuf); wl_client_post_no_memory(client); return NULL; }
    wl_resource_set_implementation(buffer, &dmabuf_buffer_impl, dmabuf, dmabuf_resource_destroy);
#ifdef DEXTOP_WAYLAND_GPU
    dmabuf->resource = buffer;
    dmabuf->comp = params->comp;
    dmabuf->deck = params->deck;
    dmabuf->refs = 1;
#endif
    LOGI("dma-buf imported %dx%d format=0x%x stride=%u", width, height, format, dmabuf->stride);
    return buffer;
}

static void params_create(struct wl_client *client, struct wl_resource *r, int32_t w, int32_t h, uint32_t format, uint32_t flags) {
    struct wl_resource *buffer = params_make_buffer(client, r, 0, w, h, format);
    if (buffer) zwp_linux_buffer_params_v1_send_created(r, buffer);
    else zwp_linux_buffer_params_v1_send_failed(r);
}

static void params_create_immed(struct wl_client *client, struct wl_resource *r, uint32_t id, int32_t w, int32_t h, uint32_t format, uint32_t flags) {
    if (!params_make_buffer(client, r, id, w, h, format))
        wl_resource_post_error(r, ZWP_LINUX_BUFFER_PARAMS_V1_ERROR_INVALID_WL_BUFFER, "unsupported dma-buf");
}

static const struct zwp_linux_buffer_params_v1_interface params_impl = {
    .destroy = params_destroy, .add = params_add, .create = params_create, .create_immed = params_create_immed,
};

static void params_resource_destroy(struct wl_resource *resource) {
    struct dmabuf_params *params = wl_resource_get_user_data(resource);
    if (params->fd >= 0) close(params->fd);
    free(params);
}

static void dmabuf_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static void dmabuf_create_params(struct wl_client *client, struct wl_resource *resource, uint32_t id) {
    struct dmabuf_params *params = calloc(1, sizeof(*params));
    if (!params) { wl_client_post_no_memory(client); return; }
    params->fd = -1;
    params->comp = wl_resource_get_user_data(resource);
    params->deck = is_deck_client(client);
    struct wl_resource *r = wl_resource_create(client, &zwp_linux_buffer_params_v1_interface, wl_resource_get_version(resource), id);
    if (!r) { free(params); wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(r, &params_impl, params, params_resource_destroy);
}

#include "deck_dmabuf.inc"
#include "deck_ahb_protocol.inc"

static const struct zwp_linux_dmabuf_v1_interface dmabuf_impl = {
    .destroy = dmabuf_destroy, .create_params = dmabuf_create_params,
    .get_default_feedback = deck_default_feedback, .get_surface_feedback = deck_surface_feedback,
};

static void bind_dmabuf(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &zwp_linux_dmabuf_v1_interface, version, id);
    if (!resource) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(resource, &dmabuf_impl, data, NULL);
    if (version >= 4) return;
    const uint32_t formats[] = {DRM_FORMAT_ARGB8888, DRM_FORMAT_XRGB8888, DRM_FORMAT_ABGR8888, DRM_FORMAT_XBGR8888};
    for (size_t i = 0; i < sizeof(formats) / sizeof(formats[0]); ++i) {
        zwp_linux_dmabuf_v1_send_format(resource, formats[i]);
        if (version >= 3) {
            zwp_linux_dmabuf_v1_send_modifier(resource, formats[i], 0, 0);
#ifdef DEXTOP_WAYLAND_GPU
            if (is_deck_client(client)) {
                uint64_t mods[8];
                unsigned n = linux_gpu_modifiers(((struct comp *)data)->gpu, formats[i], mods, 8);
                for (unsigned m = 0; m < n; m++) if (mods[m])
                    zwp_linux_dmabuf_v1_send_modifier(resource, formats[i], mods[m] >> 32, (uint32_t)mods[m]);
            }
#endif
        }
    }
}

/* ------------------------------------------------------------ text input */

struct text_input {
    struct comp *comp;
    struct wl_resource *resource;
    bool enabled, pending_enabled;
    uint32_t commits;
};

static void ti_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static void ti_enable(struct wl_client *c, struct wl_resource *r) { ((struct text_input *)wl_resource_get_user_data(r))->pending_enabled = true; }
static void ti_disable(struct wl_client *c, struct wl_resource *r) { ((struct text_input *)wl_resource_get_user_data(r))->pending_enabled = false; }
static void ti_surrounding(struct wl_client *c, struct wl_resource *r, const char *t, int32_t a, int32_t b) {}
static void ti_cause(struct wl_client *c, struct wl_resource *r, uint32_t cause) {}
static void ti_content(struct wl_client *c, struct wl_resource *r, uint32_t hint, uint32_t purpose) {}
static void ti_cursor(struct wl_client *c, struct wl_resource *r, int32_t x, int32_t y, int32_t w, int32_t h) {}
static void ti_commit(struct wl_client *c, struct wl_resource *r) {
    struct text_input *ti = wl_resource_get_user_data(r);
    ti->enabled = ti->pending_enabled;
    ti->commits++;
}

static const struct zwp_text_input_v3_interface ti_impl = {
    .destroy = ti_destroy, .enable = ti_enable, .disable = ti_disable,
    .set_surrounding_text = ti_surrounding, .set_text_change_cause = ti_cause,
    .set_content_type = ti_content, .set_cursor_rectangle = ti_cursor, .commit = ti_commit,
};

static void ti_resource_destroy(struct wl_resource *resource) {
    wl_list_remove(wl_resource_get_link(resource));
    free(wl_resource_get_user_data(resource));
}

static void tim_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }

static void tim_get(struct wl_client *client, struct wl_resource *resource, uint32_t id, struct wl_resource *seat) {
    struct comp *comp = wl_resource_get_user_data(resource);
    struct text_input *ti = calloc(1, sizeof(*ti));
    ti->comp = comp;
    ti->resource = wl_resource_create(client, &zwp_text_input_v3_interface, 1, id);
    wl_resource_set_implementation(ti->resource, &ti_impl, ti, ti_resource_destroy);
    wl_list_insert(&comp->text_inputs, wl_resource_get_link(ti->resource));
    if (comp->keyboard_focus && wl_resource_get_client(comp->keyboard_focus->resource) == client)
        zwp_text_input_v3_send_enter(ti->resource, comp->keyboard_focus->resource);
}

static const struct zwp_text_input_manager_v3_interface tim_impl = {.destroy = tim_destroy, .get_text_input = tim_get};

static void bind_text_input(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &zwp_text_input_manager_v3_interface, version, id);
    wl_resource_set_implementation(resource, &tim_impl, data, NULL);
}

/* ------------------------------------------------------------- viewporter */

#ifdef DEXTOP_WAYLAND_GPU
struct pointer_constraint {
    struct comp *comp;
    struct wl_resource *resource;
    struct surface *surface;
    struct wl_list link;
    struct wl_listener surface_destroy;
    bool locked, active, spent, persistent;
    struct pointer_region region,pending_region;
    bool region_set,pending_region_set,region_dirty;
    wl_fixed_t hint_x,hint_y;bool hint_set;
};
static void constraints_commit(struct surface *surface){
    struct pointer_constraint*c;
    wl_list_for_each(c,&surface->comp->constraints,link)if(c->surface==surface&&c->region_dirty){
        region_clear(&c->region);c->region=c->pending_region;c->pending_region=(struct pointer_region){0};
        c->region_set=c->pending_region_set;c->region_dirty=false;
    }
}
static bool constraint_point(struct pointer_constraint*c,wl_fixed_t x,wl_fixed_t y){
    if(!c->surface)return false;
    double px=wl_fixed_to_double(x),py=wl_fixed_to_double(y);
    if(px<0||py<0||px>=surface_width(c->surface)||py>=surface_height(c->surface))return false;
    if(!c->region_set)return true;
    for(size_t i=0;i<c->region.count;i++){
        struct pointer_rect r=c->region.rects[i];double px=wl_fixed_to_double(x),py=wl_fixed_to_double(y);
        if(px>=r.x1&&px<r.x2&&py>=r.y1&&py<r.y2)return true;
    }return false;
}
static void confine_position(struct comp*comp,wl_fixed_t *x,wl_fixed_t*y){
    struct pointer_constraint*c;
    wl_list_for_each(c,&comp->constraints,link)if(c->active&&!c->locked&&c->region_set&&!constraint_point(c,*x,*y)){
        double best=1e100;wl_fixed_t bx=comp->pointer_x,by=comp->pointer_y;
        for(size_t i=0;i<c->region.count;i++){
            struct pointer_rect r=c->region.rects[i];
            if(r.x1<0)r.x1=0;if(r.y1<0)r.y1=0;
            if(r.x2>surface_width(c->surface))r.x2=surface_width(c->surface);
            if(r.y2>surface_height(c->surface))r.y2=surface_height(c->surface);
            if(r.x1>=r.x2||r.y1>=r.y2)continue;
            double px=wl_fixed_to_double(*x),py=wl_fixed_to_double(*y);
            double cx=px<r.x1?r.x1:px>=r.x2?r.x2-1.0/256:px;
            double cy=py<r.y1?r.y1:py>=r.y2?r.y2-1.0/256:py;
            double dist=(cx-px)*(cx-px)+(cy-py)*(cy-py);
            if(dist<best){best=dist;bx=wl_fixed_from_double(cx);by=wl_fixed_from_double(cy);}
        }*x=bx;*y=by;
    }
}
static void constraint_state(struct pointer_constraint *c, bool active) {
    if (active == c->active) return;
    c->active = active;
    if (c->locked) {
        if (active) zwp_locked_pointer_v1_send_locked(c->resource);
        else zwp_locked_pointer_v1_send_unlocked(c->resource);
    } else {
        if (active) zwp_confined_pointer_v1_send_confined(c->resource);
        else zwp_confined_pointer_v1_send_unconfined(c->resource);
    }
    if (!active && !c->persistent) c->spent = true;
    if (c->surface && c->surface->xdg) {
        char json[160];
        snprintf(json, sizeof(json), "{\"type\":\"pointer-capture\",\"id\":%u,\"capture\":%s}",
                 c->surface->xdg->window_id, active ? "true" : "false");
        emit(c->comp, json);
    }
}
static void constraints_focus(struct comp *comp) {
    struct pointer_constraint *c;
    wl_list_for_each(c, &comp->constraints, link)
        constraint_state(c, c->surface && !c->spent && c->surface == comp->pointer_focus && c->surface == comp->keyboard_focus && (c->active || constraint_point(c,comp->pointer_x,comp->pointer_y)));
}
static bool pointer_locked(struct comp *comp) {
    struct pointer_constraint *c;
    wl_list_for_each(c, &comp->constraints, link) if(c->active && c->locked) return true;
    return false;
}
static void constraint_resource_destroy(struct wl_resource *resource) {
    struct pointer_constraint *c = wl_resource_get_user_data(resource);
    if(c->active&&c->locked&&c->hint_set&&constraint_point(c,c->hint_x,c->hint_y)){
        c->comp->pointer_x=c->hint_x;c->comp->pointer_y=c->hint_y;
    }
    constraint_state(c, false);
    wl_list_remove(&c->surface_destroy.link); wl_list_remove(&c->link);
    region_clear(&c->region);region_clear(&c->pending_region);free(c);
}
static void constraint_surface_destroy(struct wl_listener *listener, void *data) {
    struct pointer_constraint *c = wl_container_of(listener, c, surface_destroy);
    constraint_state(c, false); c->surface = NULL;
    wl_list_remove(&c->surface_destroy.link); wl_list_init(&c->surface_destroy.link);
}
static void constraint_region(struct wl_client *client, struct wl_resource *resource, struct wl_resource *region) {
    struct pointer_constraint*c=wl_resource_get_user_data(resource);
    if(region&&!region_copy(&c->pending_region,wl_resource_get_user_data(region))){wl_client_post_no_memory(client);return;}
    if(!region)region_clear(&c->pending_region);
    c->pending_region_set=region!=NULL;c->region_dirty=true;
}
static void constraint_hint(struct wl_client *client, struct wl_resource *resource, wl_fixed_t x, wl_fixed_t y) {
    struct pointer_constraint*c=wl_resource_get_user_data(resource);c->hint_x=x;c->hint_y=y;c->hint_set=true;
}
static const struct zwp_locked_pointer_v1_interface locked_impl = {
    .destroy = decoration_destroy, .set_cursor_position_hint = constraint_hint, .set_region = constraint_region,
};
static const struct zwp_confined_pointer_v1_interface confined_impl = {
    .destroy = decoration_destroy, .set_region = constraint_region,
};
static void constraint_create(struct wl_client *client, struct wl_resource *manager, uint32_t id,
    struct wl_resource *surface, struct wl_resource *pointer, struct wl_resource *region, uint32_t lifetime, bool locked) {
    struct comp *comp = wl_resource_get_user_data(manager);
    struct surface *target = wl_resource_get_user_data(surface);
    struct pointer_constraint *existing;
    wl_list_for_each(existing, &comp->constraints, link) if(existing->surface == target) {
        wl_resource_post_error(manager, ZWP_POINTER_CONSTRAINTS_V1_ERROR_ALREADY_CONSTRAINED, "surface already constrained");return;
    }
    struct pointer_constraint *c = calloc(1, sizeof(*c));
    if (!c) { wl_client_post_no_memory(client); return; }
    c->comp = comp; c->surface = target; c->locked = locked;
    c->persistent = lifetime == ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT;
    c->resource = wl_resource_create(client, locked ? &zwp_locked_pointer_v1_interface : &zwp_confined_pointer_v1_interface, 1, id);
    if (!c->resource) {free(c);wl_client_post_no_memory(client);return;}
    wl_resource_set_implementation(c->resource, locked ? (const void*)&locked_impl : (const void*)&confined_impl, c, constraint_resource_destroy);
    c->surface_destroy.notify = constraint_surface_destroy;
    wl_resource_add_destroy_listener(surface, &c->surface_destroy);
    wl_list_insert(&comp->constraints, &c->link);
    if(region){
        c->region_set=true;
        if(!region_copy(&c->region,wl_resource_get_user_data(region))){wl_client_post_no_memory(client);return;}
    }
    constraints_focus(comp);
}
static void constraint_lock(struct wl_client *client, struct wl_resource *manager, uint32_t id,
    struct wl_resource *surface, struct wl_resource *pointer, struct wl_resource *region, uint32_t lifetime) {
    constraint_create(client,manager,id,surface,pointer,region,lifetime,true);
}
static void constraint_confine(struct wl_client *client, struct wl_resource *manager, uint32_t id,
    struct wl_resource *surface, struct wl_resource *pointer, struct wl_resource *region, uint32_t lifetime) {
    constraint_create(client,manager,id,surface,pointer,region,lifetime,false);
}
static const struct zwp_pointer_constraints_v1_interface constraints_impl = {
    .destroy = decoration_destroy, .lock_pointer = constraint_lock, .confine_pointer = constraint_confine,
};
static void bind_constraints(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &zwp_pointer_constraints_v1_interface, 1, id);
    if (!resource) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(resource, &constraints_impl, data, NULL);
}
static void relative_destroy(struct wl_resource *resource) {wl_list_remove(wl_resource_get_link(resource));}
static const struct zwp_relative_pointer_v1_interface relative_impl = {.destroy = decoration_destroy};
static void relative_get(struct wl_client *client, struct wl_resource *manager, uint32_t id,
    struct wl_resource *pointer) {
    struct comp *comp = wl_resource_get_user_data(manager);
    struct wl_resource *resource = wl_resource_create(client, &zwp_relative_pointer_v1_interface, 1, id);
    if (!resource) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(resource, &relative_impl, comp, relative_destroy);
    wl_list_insert(&comp->relative_pointers, wl_resource_get_link(resource));
}
static const struct zwp_relative_pointer_manager_v1_interface relative_manager_impl = {
    .destroy = decoration_destroy, .get_relative_pointer = relative_get,
};
static void bind_relative(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &zwp_relative_pointer_manager_v1_interface, 1, id);
    if (!resource) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(resource, &relative_manager_impl, data, NULL);
}

static void presentation_feedback_destroy(struct wl_resource *resource) {
    wl_list_remove(wl_resource_get_link(resource));
}

static void presentation_feedback(struct wl_client *client, struct wl_resource *resource,
    struct wl_resource *surface_resource, uint32_t id) {
    struct surface *surface = wl_resource_get_user_data(surface_resource);
    struct wl_resource *feedback = wl_resource_create(client, &wp_presentation_feedback_interface, 1, id);
    if (!feedback) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(feedback, NULL, NULL, presentation_feedback_destroy);
    wl_list_insert(&surface->presentation_feedback, wl_resource_get_link(feedback));
}

static const struct wp_presentation_interface presentation_impl = {
    .destroy = decoration_destroy, .feedback = presentation_feedback,
};
static void bind_presentation(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &wp_presentation_interface, 1, id);
    if (!resource) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(resource, &presentation_impl, data, NULL);
    wp_presentation_send_clock_id(resource, CLOCK_MONOTONIC);
}
#endif

static void viewport_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
static void viewport_source(struct wl_client *c, struct wl_resource *r, wl_fixed_t x, wl_fixed_t y, wl_fixed_t w, wl_fixed_t h) {
#ifdef DEXTOP_WAYLAND_GPU
    struct surface *surface = wl_resource_get_user_data(r);
    if (!surface) return;
    if (w == wl_fixed_from_int(-1) && h == wl_fixed_from_int(-1)) {
        surface->pending_view.width = surface->pending_view.height = -1;
        return;
    }
    if (x < 0 || y < 0 || w <= 0 || h <= 0) {
        wl_resource_post_error(r, WP_VIEWPORT_ERROR_BAD_VALUE, "invalid source viewport"); return;
    }
    surface->pending_view.x = wl_fixed_to_double(x); surface->pending_view.y = wl_fixed_to_double(y);
    surface->pending_view.width = wl_fixed_to_double(w); surface->pending_view.height = wl_fixed_to_double(h);
#endif
}
static void viewport_destination(struct wl_client *c, struct wl_resource *r, int32_t w, int32_t h) {
#ifdef DEXTOP_WAYLAND_GPU
    struct surface *surface = wl_resource_get_user_data(r);
    if (!surface) return;
    if (!((w > 0 && h > 0) || (w == -1 && h == -1))) {
        wl_resource_post_error(r, WP_VIEWPORT_ERROR_BAD_VALUE, "invalid destination viewport"); return;
    }
    surface->pending_view.dest_width = w; surface->pending_view.dest_height = h;
#endif
}
static const struct wp_viewport_interface viewport_impl = {.destroy = viewport_destroy, .set_source = viewport_source, .set_destination = viewport_destination};
static void viewporter_destroy(struct wl_client *c, struct wl_resource *r) { wl_resource_destroy(r); }
#ifdef DEXTOP_WAYLAND_GPU
static void viewport_resource_destroy(struct wl_resource *resource) {
    struct surface *surface = wl_resource_get_user_data(resource);
    if (!surface) return;
    surface->viewport = NULL;
    surface->pending_view = (struct viewport_state){.width = -1, .height = -1, .dest_width = -1, .dest_height = -1};
}
#endif
static void viewporter_get(struct wl_client *client, struct wl_resource *r, uint32_t id, struct wl_resource *surface) {
#ifdef DEXTOP_WAYLAND_GPU
    struct surface *target = wl_resource_get_user_data(surface);
    if (target->viewport) {
        wl_resource_post_error(r, WP_VIEWPORTER_ERROR_VIEWPORT_EXISTS, "surface already has a viewport"); return;
    }
#endif
    struct wl_resource *v = wl_resource_create(client, &wp_viewport_interface, 1, id);
#ifdef DEXTOP_WAYLAND_GPU
    if (!v) { wl_client_post_no_memory(client); return; }
    target->viewport = v;
    wl_resource_set_implementation(v, &viewport_impl, target, viewport_resource_destroy);
#else
    wl_resource_set_implementation(v, &viewport_impl, NULL, NULL);
#endif
}
static const struct wp_viewporter_interface viewporter_impl = {.destroy = viewporter_destroy, .get_viewport = viewporter_get};
static void bind_viewporter(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *resource = wl_resource_create(client, &wp_viewporter_interface, version, id);
    wl_resource_set_implementation(resource, &viewporter_impl, data, NULL);
}

/* ----------------------------------------------------------------- input */

static void focus_keyboard(struct comp *comp, struct surface *surface) {
    if (comp->keyboard_focus == surface) return;
    struct wl_resource *r;
    if (comp->keyboard_focus) {
        struct wl_client *old = wl_resource_get_client(comp->keyboard_focus->resource);
        wl_resource_for_each(r, &comp->keyboards)
            if (wl_resource_get_client(r) == old) wl_keyboard_send_leave(r, wl_display_next_serial(comp->display), comp->keyboard_focus->resource);
        wl_resource_for_each(r, &comp->text_inputs)
            if (wl_resource_get_client(r) == old) zwp_text_input_v3_send_leave(r, comp->keyboard_focus->resource);
    }
    struct surface *previous = comp->keyboard_focus;
    comp->keyboard_focus = surface;
#ifdef DEXTOP_WAYLAND_GPU
    constraints_focus(comp);
#endif
    if (previous && previous->xdg && !previous->xdg->popup) xdg_send_configure(previous->xdg);
    if (!surface) return;
    struct wl_client *client = wl_resource_get_client(surface->resource);
    wl_resource_for_each(r, &comp->keyboards) {
        if (wl_resource_get_client(r) != client) continue;
        wl_keyboard_send_enter(r, wl_display_next_serial(comp->display), surface->resource, &comp->pressed_keys);
        wl_keyboard_send_modifiers(r, wl_display_next_serial(comp->display), comp->mods_depressed, 0, comp->mods_locked, 0);
    }
    wl_resource_for_each(r, &comp->text_inputs)
        if (wl_resource_get_client(r) == client) zwp_text_input_v3_send_enter(r, surface->resource);
    if (surface->xdg && !surface->xdg->popup) xdg_send_configure(surface->xdg);
}

static void pointer_frame(struct comp *comp, struct wl_client *client) {
    struct wl_resource *r;
    wl_resource_for_each(r, &comp->pointers)
        if (wl_resource_get_client(r) == client && wl_resource_get_version(r) >= WL_POINTER_FRAME_SINCE_VERSION)
            wl_pointer_send_frame(r);
}

static void pointer_focus(struct comp *comp, struct surface *surface, wl_fixed_t x, wl_fixed_t y) {
    struct wl_resource *r;
    if (comp->pointer_focus == surface) return;
    if (comp->pointer_focus) {
        struct wl_client *old = wl_resource_get_client(comp->pointer_focus->resource);
        wl_resource_for_each(r, &comp->pointers)
            if (wl_resource_get_client(r) == old) wl_pointer_send_leave(r, wl_display_next_serial(comp->display), comp->pointer_focus->resource);
        pointer_frame(comp, old);
    }
    comp->pointer_focus = surface;
#ifdef DEXTOP_WAYLAND_GPU
    comp->pointer_x=x;comp->pointer_y=y;
#endif
#ifdef DEXTOP_WAYLAND_GPU
    constraints_focus(comp);
#endif
    if (!surface) return;
    struct wl_client *client = wl_resource_get_client(surface->resource);
    wl_resource_for_each(r, &comp->pointers)
        if (wl_resource_get_client(r) == client) wl_pointer_send_enter(r, wl_display_next_serial(comp->display), surface->resource, x, y);
}

static uint32_t x_button_to_linux(int32_t button) {
    switch (button) {
        case 1: return BTN_LEFT;
        case 2: return BTN_MIDDLE;
        case 3: return BTN_RIGHT;
        case 8: return BTN_SIDE;
        case 9: return BTN_EXTRA;
        default: return 0;
    }
}

static void handle_pointer(struct comp *comp, struct xdg *xdg, int32_t action, int32_t lx, int32_t ly, int32_t button) {
    if (!xdg || !xdg->surface) return;
#ifdef DEXTOP_WAYLAND_GPU
    bool relative = action >= 10;
    if (relative) {
        action -= 10;
        if (comp->pointer_focus != xdg->surface) return;
        if (action == 0) {
            struct wl_resource *resource;
            struct wl_client *client = wl_resource_get_client(xdg->surface->resource);
            struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
            uint64_t us = (uint64_t)ts.tv_sec * 1000000ull + ts.tv_nsec / 1000;
            wl_resource_for_each(resource, &comp->relative_pointers)
                if (wl_resource_get_client(resource) == client)
                    zwp_relative_pointer_v1_send_relative_motion(resource, us >> 32, (uint32_t)us,
                        wl_fixed_from_int(lx), wl_fixed_from_int(ly), wl_fixed_from_int(lx), wl_fixed_from_int(ly));
            if (pointer_locked(comp)) return;
        }
        lx = wl_fixed_to_int(comp->pointer_x) + (action == 0 ? lx : 0);
        ly = wl_fixed_to_int(comp->pointer_y) + (action == 0 ? ly : 0);
        if (xdg->has_geometry) {lx -= xdg->geo_x; ly -= xdg->geo_y;}
        lx = lx < 0 ? 0 : lx; ly = ly < 0 ? 0 : ly;
        int w = xdg->has_geometry ? xdg->geo_w : surface_width(xdg->surface);
        int h = xdg->has_geometry ? xdg->geo_h : surface_height(xdg->surface);
        if (lx >= w) lx = w-1; if (ly >= h) ly = h-1;
    }
#endif
    /* Window coordinates start at the window geometry; surface coordinates at the buffer. */
    int32_t sx = lx + (xdg->has_geometry ? xdg->geo_x : 0);
    int32_t sy = ly + (xdg->has_geometry ? xdg->geo_y : 0);
    if (xdg->popup && action == 1 && (lx < 0 || ly < 0 || lx >= xdg->popup_w || ly >= xdg->popup_h)) {
        /* A press outside an open menu dismisses it. */
        if (xdg->role) xdg_popup_send_popup_done(xdg->role);
        return;
    }
    wl_fixed_t fx = wl_fixed_from_int(sx), fy = wl_fixed_from_int(sy);
    struct wl_client *client = wl_resource_get_client(xdg->surface->resource);
    pointer_focus(comp, xdg->surface, fx, fy);
#ifdef DEXTOP_WAYLAND_GPU
    confine_position(comp,&fx,&fy);
    comp->pointer_x = fx; comp->pointer_y = fy;
#endif
    struct wl_resource *r;
    uint32_t time = now_ms();
    wl_resource_for_each(r, &comp->pointers) {
        if (wl_resource_get_client(r) != client) continue;
#ifdef DEXTOP_WAYLAND_GPU
        if (!pointer_locked(comp))
#endif
        wl_pointer_send_motion(r, time, fx, fy);
        if (action == 1 || action == 2) {
            uint32_t linux_button = x_button_to_linux(button);
            if (linux_button) wl_pointer_send_button(r, wl_display_next_serial(comp->display), time, linux_button,
                                                     action == 1 ? WL_POINTER_BUTTON_STATE_PRESSED : WL_POINTER_BUTTON_STATE_RELEASED);
        }
    }
    pointer_frame(comp, client);
    if (action == 1 && !xdg->popup) focus_keyboard(comp, xdg->surface);
}

static void handle_axis(struct comp *comp, int32_t dx, int32_t dy) {
    struct surface *surface = comp->pointer_focus;
    if (!surface) return;
    struct wl_client *client = wl_resource_get_client(surface->resource);
    struct wl_resource *r;
    uint32_t time = now_ms();
    wl_resource_for_each(r, &comp->pointers) {
        if (wl_resource_get_client(r) != client) continue;
        uint32_t version = wl_resource_get_version(r);
        if (version >= WL_POINTER_AXIS_SOURCE_SINCE_VERSION) wl_pointer_send_axis_source(r, WL_POINTER_AXIS_SOURCE_WHEEL);
        if (dy) {
            if (version >= WL_POINTER_AXIS_DISCRETE_SINCE_VERSION && version < 8) wl_pointer_send_axis_discrete(r, WL_POINTER_AXIS_VERTICAL_SCROLL, dy);
            wl_pointer_send_axis(r, time, WL_POINTER_AXIS_VERTICAL_SCROLL, wl_fixed_from_int(dy * 15));
        }
        if (dx) {
            if (version >= WL_POINTER_AXIS_DISCRETE_SINCE_VERSION && version < 8) wl_pointer_send_axis_discrete(r, WL_POINTER_AXIS_HORIZONTAL_SCROLL, dx);
            wl_pointer_send_axis(r, time, WL_POINTER_AXIS_HORIZONTAL_SCROLL, wl_fixed_from_int(dx * 15));
        }
    }
    pointer_frame(comp, client);
}

/* Modifier masks of the US keymap: Shift 1, Lock 2, Control 4, Mod1 (Alt) 8, Mod4 (Super) 64. */
static uint32_t modifier_bit(uint32_t key) {
    switch (key) {
        case 42: case 54: return 1;
        case 29: case 97: return 4;
        case 56: case 100: return 8;
        case 125: case 126: return 64;
        default: return 0;
    }
}

static void handle_key(struct comp *comp, uint32_t key, bool down) {
    struct surface *surface = comp->keyboard_focus;
    uint32_t bit = modifier_bit(key);
    if (bit) {
        if (down) comp->mods_depressed |= bit; else comp->mods_depressed &= ~bit;
    }
    if (key == 58 && down) comp->mods_locked ^= 2;
    uint32_t *k;
    if (down) {
        bool present = false;
        wl_array_for_each(k, &comp->pressed_keys) if (*k == key) present = true;
        if (!present) { uint32_t *slot = wl_array_add(&comp->pressed_keys, sizeof(uint32_t)); if (slot) *slot = key; }
    } else {
        uint32_t *keys = comp->pressed_keys.data;
        size_t count = comp->pressed_keys.size / sizeof(uint32_t), kept = 0;
        for (size_t i = 0; i < count; ++i) if (keys[i] != key) keys[kept++] = keys[i];
        comp->pressed_keys.size = kept * sizeof(uint32_t);
    }
    if (!surface) return;
    struct wl_client *client = wl_resource_get_client(surface->resource);
    struct wl_resource *r;
    uint32_t time = now_ms();
    wl_resource_for_each(r, &comp->keyboards) {
        if (wl_resource_get_client(r) != client) continue;
        wl_keyboard_send_key(r, wl_display_next_serial(comp->display), time, key,
                             down ? WL_KEYBOARD_KEY_STATE_PRESSED : WL_KEYBOARD_KEY_STATE_RELEASED);
        if (bit || key == 58)
            wl_keyboard_send_modifiers(r, wl_display_next_serial(comp->display), comp->mods_depressed, 0, comp->mods_locked, 0);
    }
}

/* IME text: text-input-v3 when the focused client enabled it, otherwise
 * key presses for what the US layout can type. */
static int ascii_key(char ch, bool *shift) {
    static const char *lower = "\0\0" "1234567890-=\0\0qwertyuiop[]\0\0asdfghjkl;'`\0\\zxcvbnm,./";
    static const char *upper = "\0\0" "!@#$%^&*()_+\0\0QWERTYUIOP{}\0\0ASDFGHJKL:\"~\0|ZXCVBNM<>?";
    if (ch == ' ') { *shift = false; return 57; }
    if (ch == '\n') { *shift = false; return 28; }
    if (ch == '\t') { *shift = false; return 15; }
    for (int i = 2; i < 54; ++i) {
        if (lower[i] && lower[i] == ch) { *shift = false; return i; }
        if (upper[i] && upper[i] == ch) { *shift = true; return i; }
    }
    return 0;
}

static void handle_text(struct comp *comp, const char *text) {
    struct surface *surface = comp->keyboard_focus;
    if (!surface) return;
    struct wl_client *client = wl_resource_get_client(surface->resource);
    struct wl_resource *r;
    wl_resource_for_each(r, &comp->text_inputs) {
        struct text_input *ti = wl_resource_get_user_data(r);
        if (wl_resource_get_client(r) != client || !ti->enabled) continue;
        zwp_text_input_v3_send_commit_string(r, text);
        zwp_text_input_v3_send_done(r, ti->commits);
        return;
    }
    for (const char *p = text; *p; ++p) {
        bool shift = false;
        int key = ascii_key(*p, &shift);
        if (!key) continue;
        if (shift) handle_key(comp, 42, true);
        handle_key(comp, (uint32_t)key, true);
        handle_key(comp, (uint32_t)key, false);
        if (shift) handle_key(comp, 42, false);
    }
}

/* -------------------------------------------------------------- commands */

static void finish_frames(struct surface *surface, uint32_t time);

/* gamescope owns the Deck frame limit. Do not add an independent 30Hz gate
 * here: a frame arriving just after it would wait an entire extra period. */
static void run_command(struct comp *comp, struct command *cmd) {
    struct xdg *xdg = xdg_by_id(comp, cmd->id);
    switch (cmd->type) {
        case CMD_DECK_VSYNC: {
            uint64_t interval = 1000000000000ull / (unsigned)comp->refresh_mhz;
            if (cmd->frame_time_ns <= comp->deck_frame_ns ||
                (comp->deck_frame_ns && cmd->frame_time_ns - comp->deck_frame_ns < interval * 3 / 4)) break;
            comp->deck_frame_ns = cmd->frame_time_ns;
            comp->deck_vsync_ns = (uint64_t)now_ms() * 1000000ull;
#ifdef DEXTOP_WAYLAND_GPU
            gpu_jobs_collect(comp, false);
#endif
            for (int i = 0; i < 1024; ++i) {
                struct xdg *deck = comp->xdgs[i];
                if (!deck || !deck->deck || !deck->mapped || !deck->surface || deck->deck_no_vsync) continue;
                if (deck->needs_redraw) {
                    deck->needs_redraw = false;
                    window_draw(deck);
                }
                finish_frames(deck->surface, (uint32_t)(cmd->frame_time_ns / 1000000ull));
            }
            break;
        }
        case CMD_OUTPUT: {
            if (cmd->a < 1 || cmd->b < 1 || cmd->c < 1000) break;
            struct wl_resource *output;
            wl_resource_for_each(output, &comp->outputs) {
                wl_output_send_mode(output, 0, comp->width, comp->height, comp->refresh_mhz);
            }
            comp->width = cmd->a; comp->height = cmd->b; comp->refresh_mhz = cmd->c;
            comp->next_frame_ns = 0;
            wl_resource_for_each(output, &comp->outputs) {
                wl_output_send_geometry(output, 0, 0, comp->width * 254 / 1600, comp->height * 254 / 1600,
                    WL_OUTPUT_SUBPIXEL_UNKNOWN, "Dextop", "Plasma", WL_OUTPUT_TRANSFORM_NORMAL);
                wl_output_send_mode(output, WL_OUTPUT_MODE_CURRENT | WL_OUTPUT_MODE_PREFERRED, comp->width, comp->height, comp->refresh_mhz);
                if (wl_resource_get_version(output) >= 2) wl_output_send_done(output);
            }
            break;
        }
        case CMD_STATE:
            if (xdg && xdg->maximized != (bool)cmd->a) {
                xdg->maximized = (bool)cmd->a;
                xdg_send_configure(xdg);
            }
            break;
        case CMD_ATTACH:
            if (xdg) {
#ifdef DEXTOP_WAYLAND_GPU
                deck_direct_hide(xdg, true);
                linux_gpu_window_destroy(xdg->gpu_window);
                xdg->gpu_window = NULL;
#endif
                if (xdg->window) ANativeWindow_release(xdg->window);
                xdg->deck_direct = cmd->a != 0;
                xdg->deck_30 = cmd->b != 0;
                xdg->deck_no_vsync = cmd->c != 0;
                xdg->frame_announced = false;
                xdg->window = cmd->window;
                cmd->window = NULL;
                xdg->window_w = xdg->window_h = 0;
                window_draw(xdg);
            }
            break;
        case CMD_DETACH:
#ifdef DEXTOP_WAYLAND_GPU
            if (xdg) { deck_direct_hide(xdg, true); linux_gpu_window_destroy(xdg->gpu_window); xdg->gpu_window = NULL; }
#endif
            if (xdg && xdg->window) { ANativeWindow_release(xdg->window); xdg->window = NULL; }
            break;
        case CMD_CONFIGURE:
            if (xdg && !xdg->popup && (xdg->want_w != cmd->c || xdg->want_h != cmd->d)) {
                xdg->want_w = cmd->c;
                xdg->want_h = cmd->d;
                xdg_send_configure(xdg);
            }
            break;
        case CMD_ACTIVATE:
            if (xdg && xdg->surface && !xdg->popup) focus_keyboard(comp, xdg->surface);
            else if (!xdg) {focus_keyboard(comp, NULL);pointer_focus(comp, NULL, 0, 0);}
#ifdef DEXTOP_WAYLAND_GPU
            constraints_focus(comp);
#endif
            break;
        case CMD_CLOSE:
            if (xdg && xdg->role) {
                if (xdg->popup) xdg_popup_send_popup_done(xdg->role);
                else xdg_toplevel_send_close(xdg->role);
            }
            break;
        case CMD_POINTER:
            handle_pointer(comp, xdg, cmd->a, cmd->b, cmd->c, cmd->d);
            break;
        case CMD_AXIS:
            handle_axis(comp, cmd->a, cmd->b);
            break;
        case CMD_KEY:
            handle_key(comp, (uint32_t)cmd->a, cmd->b != 0);
            break;
        case CMD_TEXT:
            if (cmd->text) handle_text(comp, cmd->text);
            break;
        case CMD_QUIT:
            wl_display_terminate(comp->display);
            break;
    }
    if (cmd->window) ANativeWindow_release(cmd->window);
    free(cmd->text);
    free(cmd);
}

static int on_command(int fd, uint32_t mask, void *data) {
    struct comp *comp = data;
    uint64_t value;
    if (read(fd, &value, sizeof(value)) < 0) { /* drained below anyway */ }
    for (;;) {
        pthread_mutex_lock(&comp->lock);
        struct command *cmd = comp->commands;
        if (cmd) {
            comp->commands = cmd->next;
            if (!comp->commands) comp->commands_tail = NULL;
        }
        pthread_mutex_unlock(&comp->lock);
        if (!cmd) break;
        run_command(comp, cmd);
    }
    wl_display_flush_clients(comp->display);
    return 0;
}

static void post(struct comp *comp, struct command *cmd) {
    cmd->next = NULL;
    pthread_mutex_lock(&comp->lock);
    if (comp->commands_tail) comp->commands_tail->next = cmd; else comp->commands = cmd;
    comp->commands_tail = cmd;
    pthread_mutex_unlock(&comp->lock);
    uint64_t one = 1;
    if (write(comp->command_fd, &one, sizeof(one)) < 0) { /* the loop drains the queue on its next wakeup */ }
}

/* Redraws changed windows and answers frame callbacks, at most once per refresh. */
static void finish_frames(struct surface *surface, uint32_t time) {
    struct wl_resource *callback, *tmp;
    wl_resource_for_each_safe(callback, tmp, &surface->ready_frames) {
        wl_callback_send_done(callback, time);
        wl_resource_destroy(callback);
    }
    struct surface *child;
    wl_list_for_each(child, &surface->children, child_link) finish_frames(child, time);
}

static int on_frame(void *data) {
    struct comp *comp = data;
#ifdef DEXTOP_WAYLAND_GPU
    gpu_jobs_collect(comp, false);
    uint32_t stats_now = now_ms();
    if (!comp->gpu_stats_time) comp->gpu_stats_time = stats_now;
    if (comp->gpu && stats_now - comp->gpu_stats_time >= 5000) {
        LOGI("GPU stats ms=%u commits=%u submits=%u retries=%u cpu_avg_us=%llu release_avg_us=%llu release_max_us=%llu",
            stats_now - comp->gpu_stats_time, comp->gpu_commits, comp->gpu_submits, comp->gpu_retries,
            (unsigned long long)(comp->gpu_cpu_ns / (comp->gpu_submits ? comp->gpu_submits : 1) / 1000),
            (unsigned long long)(comp->gpu_release_ns / (comp->gpu_releases ? comp->gpu_releases : 1) / 1000),
            (unsigned long long)(comp->gpu_release_max_ns / 1000));
        comp->gpu_stats_time = stats_now;
        for (int i = 0; i < 1024; ++i) {
            struct xdg *deck = comp->xdgs[i];
            if (!deck || !deck->deck) continue;
            LOGI("Deck producer cadence window=%u gaps_lt25ms=%u gaps_25to40ms=%u gaps_40to60ms=%u gaps_ge60ms=%u max_us=%llu",
                deck->window_id, deck->deck_commit_gaps[0], deck->deck_commit_gaps[1],
                deck->deck_commit_gaps[2], deck->deck_commit_gaps[3],
                (unsigned long long)(deck->deck_commit_gap_max_ns / 1000));
            memset(deck->deck_commit_gaps, 0, sizeof(deck->deck_commit_gaps));
            deck->deck_commit_gap_max_ns = 0;
            LOGI("Deck direct submissions=%u", deck->deck_direct_submits);
            deck->deck_direct_submits = 0;
            LOGI("Deck pipeline window=%u root_buffers=%u submits=%u coalesced=%u redraw_submits=%u latest_buffer_age_avg_us=%llu latest_buffer_age_max_us=%llu",
                deck->window_id, deck->deck_buffers, deck->deck_submits, deck->deck_coalesced, deck->deck_redraws,
                (unsigned long long)(deck->deck_age_ns / (deck->deck_age_samples ? deck->deck_age_samples : 1) / 1000),
                (unsigned long long)(deck->deck_age_max_ns / 1000));
            deck->deck_buffers = deck->deck_submits = deck->deck_coalesced = deck->deck_redraws = deck->deck_age_samples = 0;
            deck->deck_age_ns = deck->deck_age_max_ns = 0;
        }
        comp->gpu_cpu_ns = comp->gpu_release_ns = comp->gpu_release_max_ns = 0;
        comp->gpu_commits = comp->gpu_submits = comp->gpu_retries = comp->gpu_releases = 0;
    }
#endif
    uint32_t time = now_ms();
    for (int i = 0; i < 1024; ++i) {
        struct xdg *xdg = comp->xdgs[i];
        if (!xdg || !xdg->surface) continue;
        /* DD drives Deck rendering from Android VSync. Keep the existing timer
         * for other Linux windows and as a fallback if Android stops ticking. */
        if (xdg->deck && !xdg->deck_no_vsync && comp->deck_vsync_ns &&
            (uint32_t)(time - comp->deck_vsync_ns / 1000000ull) < 100) continue;
        if (xdg->needs_redraw) {
            xdg->needs_redraw = false;
            if (xdg->mapped) window_draw(xdg);
        }
        finish_frames(xdg->surface, time);
    }
    struct timespec pacing_now;
    clock_gettime(CLOCK_MONOTONIC, &pacing_now);
    uint64_t nanos = (uint64_t)pacing_now.tv_sec * 1000000000ull + pacing_now.tv_nsec;
    uint64_t interval = 1000000000000ull / (unsigned)comp->refresh_mhz;
    comp->next_frame_ns += interval;
    if (comp->next_frame_ns <= nanos) comp->next_frame_ns = nanos + interval;
    int delay_ms = (int)((comp->next_frame_ns - nanos + 999999ull) / 1000000ull);
    wl_event_source_timer_update(comp->frame_timer, delay_ms > 0 ? delay_ms : 1);
    return 0;
}

/* ------------------------------------------------------------- lifecycle */

#include "deck_ahb_broker.inc"

static void *compositor_main(void *data) {
    struct comp *comp = data;
    JavaVMAttachArgs args = {JNI_VERSION_1_6, "DextopWayland", NULL};
    (*comp->vm)->AttachCurrentThread(comp->vm, &comp->env, &args);
    LOGI("compositor running %dx%d", comp->width, comp->height);
#ifdef DEXTOP_WAYLAND_GPU
    const char *driver = getenv("DEXTOP_LINUX_GPU_DRIVER");
    const char *library = getenv("DEXTOP_LINUX_GPU_LIBRARY");
    const char *hooks = getenv("DEXTOP_LINUX_GPU_HOOKS");
    if (driver && library && hooks) comp->gpu = linux_gpu_create(driver, library, hooks);
#endif
    wl_display_run(comp->display);
    wl_display_destroy_clients(comp->display);
#ifdef DEXTOP_WAYLAND_GPU
    /* Client teardown destroys windows and drains their GPU work. */
    gpu_jobs_collect(comp, true);
    struct deck_direct_frame *direct, *next_direct;
    wl_list_for_each_safe(direct, next_direct, &comp->direct_frames, link) deck_direct_frame_destroy(direct);
    linux_gpu_destroy(comp->gpu);
#endif
    wl_display_destroy(comp->display);
    (*comp->env)->DeleteGlobalRef(comp->env, comp->callback);
    (*comp->vm)->DetachCurrentThread(comp->vm);
    if (comp->ahb_broker_fd >= 0) close(comp->ahb_broker_fd);
    close(comp->command_fd);
    wl_array_release(&comp->pressed_keys);
    pthread_mutex_destroy(&comp->lock);
    free(comp);
    return NULL;
}

static int listen_abstract(const char *name) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    size_t length = strlen(name);
    if (length + 1 > sizeof(address.sun_path)) { close(fd); return -1; }
    memcpy(address.sun_path + 1, name, length);
    socklen_t size = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length);
    if (bind(fd, (struct sockaddr *)&address, size) < 0 || listen(fd, 128) < 0) {
        LOGW("cannot listen on @%s: %s", name, strerror(errno));
        close(fd);
        return -1;
    }
    return fd;
}

JNIEXPORT jlong JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeStart(JNIEnv *env, jclass cls, jstring socket_name,
                                                          jint width, jint height, jobject callback) {
    struct comp *comp = calloc(1, sizeof(*comp));
    if (!comp) return 0;
    comp->ahb_broker_fd = -1;
    comp->refresh_mhz = 60000;
    comp->width = width;
    comp->height = height;
    comp->next_window_id = 1;
    wl_list_init(&comp->outputs);
    wl_list_init(&comp->pointers);
    wl_list_init(&comp->keyboards);
    wl_list_init(&comp->text_inputs);
#ifdef DEXTOP_WAYLAND_GPU
    wl_list_init(&comp->constraints);
    wl_list_init(&comp->relative_pointers);
    wl_list_init(&comp->gpu_jobs);
    wl_list_init(&comp->direct_frames);
    wl_list_init(&comp->buffer_releases);
#endif
    wl_array_init(&comp->pressed_keys);
    pthread_mutex_init(&comp->lock, NULL);
    (*env)->GetJavaVM(env, &comp->vm);
    comp->callback = (*env)->NewGlobalRef(env, callback);
    jclass callback_class = (*env)->GetObjectClass(env, callback);
    comp->on_event = (*env)->GetMethodID(env, callback_class, "onEvent", "(Ljava/lang/String;)V");

    const char *name = (*env)->GetStringUTFChars(env, socket_name, NULL);
    int listen_fd = listen_abstract(name);
    char broker_name[108];
    snprintf(broker_name, sizeof(broker_name), "%s.ahb", name);
    (*env)->ReleaseStringUTFChars(env, socket_name, name);
    comp->display = wl_display_create();
    if (listen_fd < 0 || !comp->display || wl_display_add_socket_fd(comp->display, listen_fd) != 0) {
        if (listen_fd >= 0) close(listen_fd);
        if (comp->display) wl_display_destroy(comp->display);
        (*env)->DeleteGlobalRef(env, comp->callback);
        free(comp);
        return 0;
    }
    comp->loop = wl_display_get_event_loop(comp->display);
    struct sockaddr_un broker_address = {.sun_family = AF_UNIX};
    size_t broker_length = strlen(broker_name);
    if (broker_length + 1 < sizeof(broker_address.sun_path)) {
        memcpy(broker_address.sun_path + 1, broker_name, broker_length);
        int broker = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
        if (broker >= 0 && !bind(broker, (void *)&broker_address, offsetof(struct sockaddr_un, sun_path) + 1 + broker_length) && !listen(broker, 16)) {
            if (wl_event_loop_add_fd(comp->loop, broker, WL_EVENT_READABLE, deck_ahb_accept, NULL)) comp->ahb_broker_fd = broker;
            else close(broker);
        } else if (broker >= 0) close(broker);
    }
    wl_display_init_shm(comp->display);
    wl_display_add_shm_format(comp->display, WL_SHM_FORMAT_ABGR8888);
    wl_display_add_shm_format(comp->display, WL_SHM_FORMAT_XBGR8888);
    wl_global_create(comp->display, &wl_compositor_interface, 5, comp, bind_compositor);
    wl_global_create(comp->display, &wl_subcompositor_interface, 1, comp, bind_subcompositor);
    wl_global_create(comp->display, &wl_output_interface, 4, comp, bind_output);
#ifdef DEXTOP_WAYLAND_GPU
    wl_global_create(comp->display, &wl_seat_interface, 8, comp, bind_seat);
#else
    wl_global_create(comp->display, &wl_seat_interface, 7, comp, bind_seat);
#endif
    wl_global_create(comp->display, &wl_data_device_manager_interface, 3, comp, bind_ddm);
    wl_global_create(comp->display, &xdg_wm_base_interface, 5, comp, bind_wm);
    wl_global_create(comp->display, &zxdg_decoration_manager_v1_interface, 1, comp, bind_decoration);
    wl_global_create(comp->display, &org_kde_kwin_server_decoration_manager_interface, 1, comp, bind_kde_decoration);
    comp->normal_dmabuf_global = wl_global_create(comp->display, &zwp_linux_dmabuf_v1_interface, 3, comp, bind_dmabuf);
    comp->deck_dmabuf_global = wl_global_create(comp->display, &zwp_linux_dmabuf_v1_interface, 4, comp, bind_dmabuf);
    comp->deck_ahb_global = wl_global_create(comp->display, &banner_ahb_v1_interface, 2, comp, bind_deck_ahb);
    wl_display_set_global_filter(comp->display, filter_deck_global, comp);
    wl_global_create(comp->display, &zwp_text_input_manager_v3_interface, 1, comp, bind_text_input);
    wl_global_create(comp->display, &wp_viewporter_interface, 1, comp, bind_viewporter);
    wl_global_create(comp->display, &wp_single_pixel_buffer_manager_v1_interface, 1, comp, bind_solid);
#ifdef DEXTOP_WAYLAND_GPU
    wl_global_create(comp->display, &wp_presentation_interface, 1, comp, bind_presentation);
    wl_global_create(comp->display, &zwp_pointer_constraints_v1_interface, 1, comp, bind_constraints);
    wl_global_create(comp->display, &zwp_relative_pointer_manager_v1_interface, 1, comp, bind_relative);
#endif

    comp->command_fd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    comp->command_source = wl_event_loop_add_fd(comp->loop, comp->command_fd, WL_EVENT_READABLE, on_command, comp);
    comp->frame_timer = wl_event_loop_add_timer(comp->loop, on_frame, comp);
    wl_event_source_timer_update(comp->frame_timer, 16);
    if (pthread_create(&comp->thread, NULL, compositor_main, comp) != 0) {
        wl_display_destroy(comp->display);
        (*env)->DeleteGlobalRef(env, comp->callback);
        free(comp);
        return 0;
    }
    pthread_detach(comp->thread);
    return (jlong)(intptr_t)comp;
}

static struct command *command(enum command_type type, jint id) {
    struct command *cmd = calloc(1, sizeof(*cmd));
    if (cmd) { cmd->type = type; cmd->id = (uint32_t)id; }
    return cmd;
}

#define COMP(handle) ((struct comp *)(intptr_t)(handle))

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeStop(JNIEnv *env, jclass cls, jlong handle) {
    struct command *cmd = command(CMD_QUIT, 0);
    if (cmd && handle) post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeAttach(JNIEnv *env, jclass cls, jlong handle, jint id, jobject surface, jboolean deck_direct, jboolean deck_30, jboolean no_vsync) {
    struct command *cmd = command(CMD_ATTACH, id);
    if (!cmd) return;
    cmd->a = deck_direct;
    cmd->b = deck_30;
    cmd->c = no_vsync;
    cmd->window = ANativeWindow_fromSurface(env, surface);
    post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeDetach(JNIEnv *env, jclass cls, jlong handle, jint id) {
    struct command *cmd = command(CMD_DETACH, id);
    if (cmd) post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeOutput(JNIEnv *env, jclass cls, jlong handle, jint w, jint h, jint refresh) {
    struct command *cmd = command(CMD_OUTPUT, 0);
    if (!cmd) return;
    cmd->a = w; cmd->b = h; cmd->c = refresh;
    post((struct comp *)(intptr_t)handle, cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeDeckVsync(JNIEnv *env, jclass cls, jlong handle, jlong frame_time_ns) {
    if (frame_time_ns <= 0) return;
    struct command *cmd = command(CMD_DECK_VSYNC, 0);
    if (!cmd) return;
    cmd->frame_time_ns = (uint64_t)frame_time_ns;
    post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeConfigure(JNIEnv *env, jclass cls, jlong handle, jint id, jint w, jint h) {
    struct command *cmd = command(CMD_CONFIGURE, id);
    if (!cmd) return;
    cmd->c = w;
    cmd->d = h;
    post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeActivate(JNIEnv *env, jclass cls, jlong handle, jint id) {
    struct command *cmd = command(CMD_ACTIVATE, id);
    if (cmd) post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeSetState(JNIEnv *env, jclass cls, jlong handle, jint id, jboolean maximized) {
    struct command *cmd = command(CMD_STATE, id);
    if (!cmd) return;
    cmd->a = maximized;
    post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeClose(JNIEnv *env, jclass cls, jlong handle, jint id) {
    struct command *cmd = command(CMD_CLOSE, id);
    if (cmd) post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativePointer(JNIEnv *env, jclass cls, jlong handle, jint id,
                                                            jint action, jint x, jint y, jint button) {
    struct command *cmd = command(CMD_POINTER, id);
    if (!cmd) return;
    cmd->a = action; cmd->b = x; cmd->c = y; cmd->d = button;
    post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeAxis(JNIEnv *env, jclass cls, jlong handle, jint dx, jint dy) {
    struct command *cmd = command(CMD_AXIS, 0);
    if (!cmd) return;
    cmd->a = dx; cmd->b = dy;
    post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeKey(JNIEnv *env, jclass cls, jlong handle, jint key, jboolean down) {
    struct command *cmd = command(CMD_KEY, 0);
    if (!cmd) return;
    cmd->a = key; cmd->b = down;
    post(COMP(handle), cmd);
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeText(JNIEnv *env, jclass cls, jlong handle, jstring text) {
    struct command *cmd = command(CMD_TEXT, 0);
    if (!cmd) return;
    const char *utf = (*env)->GetStringUTFChars(env, text, NULL);
    cmd->text = strdup(utf);
    (*env)->ReleaseStringUTFChars(env, text, utf);
    post(COMP(handle), cmd);
}

/*
 * Holds <runtime dir>/wayland-N.lock the way libwayland does for a socket it
 * serves. Guest compositors (KWin's kwin_wayland_wrapper, wl_socket_create)
 * take the first wayland-N whose lock they can flock; without this lock they
 * pick wayland-0, whose name Dextop already serves, and give up.
 */
JNIEXPORT jint JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeLockName(JNIEnv *env, jclass cls, jstring path) {
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    int fd = open(utf, O_CREAT | O_CLOEXEC | O_RDWR, 0660);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    if (fd < 0) return -errno;
    if (flock(fd, LOCK_EX | LOCK_NB) < 0) {
        int error = errno;
        close(fd);
        return -error;
    }
    return fd;
}

JNIEXPORT void JNICALL
Java_moe_n4tsu_dextop_linux_WaylandCompositor_nativeUnlockName(JNIEnv *env, jclass cls, jint fd) {
    if (fd >= 0) close(fd);
}
