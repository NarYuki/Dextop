/* One release per buffer at the end of a Deck client's request batch.
 * A buffer reused by another surface or GPU job before that point remains
 * acquired. Destroy listeners prevent stale queued wl_resource pointers. */
struct deck_buffer_release {
    struct wl_resource *buffer;
    struct wl_listener destroy;
    struct wl_list link;
};

static void deck_buffer_release_remove(struct deck_buffer_release *release) {
    wl_list_remove(&release->destroy.link);
    wl_list_remove(&release->link);
    free(release);
}

static void deck_buffer_release_destroyed(struct wl_listener *listener, void *data) {
    struct deck_buffer_release *release = wl_container_of(listener, release, destroy);
    deck_buffer_release_remove(release);
}

static void deck_buffer_release_cancel(struct comp *comp, struct wl_resource *buffer) {
    struct deck_buffer_release *release, *next;
    wl_list_for_each_safe(release, next, &comp->buffer_releases, link)
        if (release->buffer == buffer) deck_buffer_release_remove(release);
}

static void deck_buffer_releases_flush(void *data) {
    struct comp *comp = data;
    comp->buffer_release_idle = NULL;
    struct deck_buffer_release *release, *next;
    wl_list_for_each_safe(release, next, &comp->buffer_releases, link) {
        wl_buffer_send_release(release->buffer);
        deck_buffer_release_remove(release);
    }
    wl_display_flush_clients(comp->display);
}

static void deck_buffer_release_queue(struct comp *comp, struct wl_resource *buffer, bool deck) {
    if (!buffer) return;
    if (!deck) { wl_buffer_send_release(buffer); return; }
    struct deck_buffer_release *release;
    wl_list_for_each(release, &comp->buffer_releases, link)
        if (release->buffer == buffer) return;
    release = calloc(1, sizeof(*release));
    if (!release) { wl_buffer_send_release(buffer); return; }
    release->buffer = buffer;
    release->destroy.notify = deck_buffer_release_destroyed;
    wl_resource_add_destroy_listener(buffer, &release->destroy);
    wl_list_insert(&comp->buffer_releases, &release->link);
    if (!comp->buffer_release_idle)
        comp->buffer_release_idle = wl_event_loop_add_idle(comp->loop, deck_buffer_releases_flush, comp);
    if (!comp->buffer_release_idle) deck_buffer_releases_flush(comp);
}
