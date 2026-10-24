#include <dlfcn.h>
/* Receiver for The412Banner's MIT-licensed banner-ahb-v1 protocol.
 * Only clients from the Deck launcher can see or bind this global. */
static void deck_ahb_destroy(struct wl_client *client, struct wl_resource *resource) {
    wl_resource_destroy(resource);
}

static void deck_ahb_attach(struct wl_client *client, struct wl_resource *resource,
    struct wl_resource *buffer, int32_t socket_fd, uint32_t width, uint32_t height,
    uint32_t stride, uint32_t modifier_hi, uint32_t modifier_lo, uint32_t image_count) {
    AHardwareBuffer *ahb = NULL;
    if (!is_deck_client(client) ||
        !wl_resource_instance_of(buffer, &wl_buffer_interface, &dmabuf_buffer_impl)) goto done;
    struct dmabuf *b = wl_resource_get_user_data(buffer);
    if (!b || b->ahb || width != (uint32_t)b->width || height != (uint32_t)b->height ||
        b->offset || b->modifier != ((uint64_t)modifier_hi << 32 | modifier_lo) ||
        !stride || stride > UINT32_MAX / 4 || b->stride != stride * 4) goto done;
    /* A client must have already written its handle. Never wait for a client
     * while the compositor owns input and every other application's display. */
    struct pollfd ready = {.fd = socket_fd, .events = POLLIN};
    if (poll(&ready, 1, 0) != 1 || !(ready.revents & POLLIN)) goto done;
    int flags = fcntl(socket_fd, F_GETFL);
    if (flags < 0 || fcntl(socket_fd, F_SETFL, flags | O_NONBLOCK)) goto done;
    if (AHardwareBuffer_recvHandleFromUnixSocket(socket_fd, &ahb) || !ahb) goto done;
    AHardwareBuffer_Desc desc;
    AHardwareBuffer_describe(ahb, &desc);
    if (desc.width != width || desc.height != height || desc.stride != stride || desc.layers != 1 ||
        (desc.format != AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM &&
         desc.format != AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM) ||
        (b->format != DRM_FORMAT_ABGR8888 && b->format != DRM_FORMAT_XBGR8888)) goto done;
    /* AHB and wl_buffer must name the same allocation, not merely share sizes.
     * native_handle_t's public ABI starts with version, fd/int counts, fd array. */
    struct handle_header { int version, num_fds, num_ints, data[]; };
    void *nw = dlopen("libnativewindow.so", RTLD_NOW | RTLD_LOCAL);
    const void *(*get_handle)(const AHardwareBuffer *) = nw ? dlsym(nw, "AHardwareBuffer_getNativeHandle") : NULL;
    const struct handle_header *handle = get_handle ? get_handle(ahb) : NULL;
    /* get_handle returns storage owned by ahb, independent of this dlopen ref. */
    if (nw) dlclose(nw);
    struct stat original, received;
    if (!handle || handle->num_fds < 1 || fstat(b->fd, &original) || fstat(handle->data[0], &received) ||
        original.st_dev != received.st_dev || original.st_ino != received.st_ino) goto done;
    b->ahb = ahb; ahb = NULL;
    LOGI("Deck direct AHB attached %ux%u stride=%u images=%u", width, height, stride, image_count);
done:
    if (ahb) AHardwareBuffer_release(ahb);
    close(socket_fd);
}

static const struct banner_ahb_v1_interface deck_ahb_impl = {
    .destroy = deck_ahb_destroy, .attach = deck_ahb_attach,
};
static void bind_deck_ahb(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    if (!is_deck_client(client)) return;
    struct wl_resource *resource = wl_resource_create(client, &banner_ahb_v1_interface, version > 2 ? 2 : version, id);
    if (!resource) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(resource, &deck_ahb_impl, data, NULL);
    struct comp *comp = data;
    if (wl_resource_get_version(resource) >= 2) banner_ahb_v1_send_mode(resource, comp->gpu && comp->ahb_broker_fd >= 0);
}
