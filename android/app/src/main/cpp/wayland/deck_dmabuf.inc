struct deck_format_entry { uint32_t format, padding; uint64_t modifier; };

static void deck_feedback_destroy(struct wl_client *client, struct wl_resource *resource) {
    wl_resource_destroy(resource);
}

static const struct zwp_linux_dmabuf_feedback_v1_interface deck_feedback_impl = {
    .destroy = deck_feedback_destroy,
};

static void deck_feedback(struct wl_client *client, struct wl_resource *parent, uint32_t id) {
    struct comp *comp = wl_resource_get_user_data(parent);
    struct wl_resource *resource = wl_resource_create(client, &zwp_linux_dmabuf_feedback_v1_interface, 1, id);
    if (!resource) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(resource, &deck_feedback_impl, NULL, NULL);
    static const uint32_t formats[] = {
        DRM_FORMAT_ARGB8888, DRM_FORMAT_XRGB8888, DRM_FORMAT_ABGR8888, DRM_FORMAT_XBGR8888,
    };
    struct deck_format_entry entries[32];
    uint16_t indices[32];
    unsigned count = 0;
    for (unsigned f = 0; f < 4; f++) {
        uint64_t mods[8] = {DRM_FORMAT_MOD_LINEAR};
        unsigned n = 0;
#ifdef DEXTOP_WAYLAND_GPU
        n = linux_gpu_modifiers(comp->gpu, formats[f], mods, 8);
#endif
        if (!n) n = 1;
        for (unsigned m = 0; m < n; m++) {
            indices[count] = (uint16_t)count;
            entries[count++] = (struct deck_format_entry){formats[f], 0, mods[m]};
        }
    }
    int fd = memfd_create("dextop-deck-formats", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    size_t size = count * sizeof(entries[0]);
    if (fd < 0 || write(fd, entries, size) != (ssize_t)size ||
        fcntl(fd, F_ADD_SEALS, F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE) < 0) {
        if (fd >= 0) close(fd);
        wl_client_post_no_memory(client);
        return;
    }
    struct stat node;
    dev_t device = stat("/dev/kgsl-3d0", &node) == 0 && S_ISCHR(node.st_mode) ? node.st_rdev : 0;
    struct wl_array dev = {.size = sizeof(device), .alloc = sizeof(device), .data = &device};
    struct wl_array idx = {.size = count * sizeof(indices[0]), .alloc = sizeof(indices), .data = indices};
    zwp_linux_dmabuf_feedback_v1_send_format_table(resource, fd, (uint32_t)size);
    close(fd);
    zwp_linux_dmabuf_feedback_v1_send_main_device(resource, &dev);
    zwp_linux_dmabuf_feedback_v1_send_tranche_target_device(resource, &dev);
    zwp_linux_dmabuf_feedback_v1_send_tranche_flags(resource, 0);
    zwp_linux_dmabuf_feedback_v1_send_tranche_formats(resource, &idx);
    zwp_linux_dmabuf_feedback_v1_send_tranche_done(resource);
    zwp_linux_dmabuf_feedback_v1_send_done(resource);
    LOGI("Deck dma-buf feedback: %u format/modifier pairs", count);
}

static void deck_default_feedback(struct wl_client *client, struct wl_resource *parent, uint32_t id) {
    deck_feedback(client, parent, id);
}

static void deck_surface_feedback(struct wl_client *client, struct wl_resource *parent, uint32_t id,
                                  struct wl_resource *surface) {
    deck_feedback(client, parent, id);
}
