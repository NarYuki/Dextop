/* wp_fractional_scale_v1, version 1. Wire definitions follow
 * wayland-protocols/staging/fractional-scale/fractional-scale-v1.xml.
 * Android's viewport uses surface-local pixels (wl_output scale 1). Explicitly
 * advertising that scale lets clients keep UI scale separate from buffer scale;
 * Chromium otherwise applies forced UI scale through its legacy integer path. */
static const struct wl_interface fractional_surface_interface;
static const struct wl_interface *fractional_get_types[] = {
    &fractional_surface_interface, &wl_surface_interface,
};
static const struct wl_message fractional_manager_requests[] = {
    {"destroy", "", NULL},
    {"get_fractional_scale", "no", fractional_get_types},
};
static const struct wl_message fractional_surface_requests[] = {{"destroy", "", NULL}};
static const struct wl_message fractional_surface_events[] = {{"preferred_scale", "u", NULL}};
static const struct wl_interface fractional_manager_interface = {
    "wp_fractional_scale_manager_v1", 1, 2, fractional_manager_requests, 0, NULL,
};
static const struct wl_interface fractional_surface_interface = {
    "wp_fractional_scale_v1", 1, 1, fractional_surface_requests, 1, fractional_surface_events,
};

static void fractional_destroy(struct wl_client *client, struct wl_resource *resource) {
    wl_resource_destroy(resource);
}

static void fractional_resource_destroy(struct wl_resource *resource) {
    struct surface *surface = wl_resource_get_user_data(resource);
    if (surface) surface->fractional_scale = NULL;
}

static const struct {
    void (*destroy)(struct wl_client *, struct wl_resource *);
} fractional_surface_impl = {fractional_destroy};

static void fractional_get(struct wl_client *client, struct wl_resource *manager,
                           uint32_t id, struct wl_resource *resource) {
    struct surface *surface = wl_resource_get_user_data(resource);
    if (surface->fractional_scale) {
        wl_resource_post_error(manager, 0, "surface already has a fractional scale object");
        return;
    }
    struct wl_resource *scale = wl_resource_create(client, &fractional_surface_interface, 1, id);
    if (!scale) { wl_client_post_no_memory(client); return; }
    surface->fractional_scale = scale;
    wl_resource_set_implementation(scale, &fractional_surface_impl, surface, fractional_resource_destroy);
    wl_resource_post_event(scale, 0, 120u);
}

static const struct {
    void (*destroy)(struct wl_client *, struct wl_resource *);
    void (*get_fractional_scale)(struct wl_client *, struct wl_resource *, uint32_t, struct wl_resource *);
} fractional_manager_impl = {fractional_destroy, fractional_get};

static void bind_fractional_scale(struct wl_client *client, void *data, uint32_t version, uint32_t id) {
    struct wl_resource *manager = wl_resource_create(client, &fractional_manager_interface, 1, id);
    if (!manager) { wl_client_post_no_memory(client); return; }
    wl_resource_set_implementation(manager, &fractional_manager_impl, data, NULL);
}
