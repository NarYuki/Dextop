#include <stdatomic.h>
/* Deck-only AHB presentation. Protocol: The412Banner's MIT banner-ahb-v1.
 * Transaction callbacks never access Wayland objects: a socket carries the
 * previous-buffer release fence back to the owning compositor thread. */
struct deck_direct_frame {
    struct dmabuf *buffer;
    struct wl_event_loop *loop;
    struct wl_event_source *source;
    struct wl_list link;
    int fd;
    bool socket;
};
struct deck_direct_submission { int fd; AHardwareBuffer *buffer; };
struct deck_direct_layer {
    atomic_uint refs;
    ASurfaceControl *control;
    struct deck_direct_submission *current;
};
struct deck_direct_complete {
    struct deck_direct_layer *layer;
    ASurfaceControl *control;
    struct deck_direct_submission *previous;
};

static void deck_direct_layer_unref(struct deck_direct_layer *layer) {
    if (atomic_fetch_sub(&layer->refs, 1) == 1) { ASurfaceControl_release(layer->control); free(layer); }
}

static void deck_direct_frame_destroy(struct deck_direct_frame *frame) {
    if (frame->source) wl_event_source_remove(frame->source);
    close(frame->fd);
    gpu_buffer_unref(frame->buffer, true);
    wl_list_remove(&frame->link);
    free(frame);
}

static int deck_direct_released(int fd, uint32_t mask, void *data) {
    struct deck_direct_frame *frame = data;
    if (!(mask & WL_EVENT_READABLE)) return 0;
    if (frame->socket) {
        char byte, ancillary[CMSG_SPACE(sizeof(int))];
        struct iovec iov = {&byte, 1};
        struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1,
            .msg_control = ancillary, .msg_controllen = sizeof(ancillary)};
        if (recvmsg(fd, &msg, MSG_DONTWAIT | MSG_CMSG_CLOEXEC) != 1) return 0;
        int fence = -1;
        for (struct cmsghdr *c = CMSG_FIRSTHDR(&msg); c; c = CMSG_NXTHDR(&msg, c))
            if (c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS &&
                c->cmsg_len == CMSG_LEN(sizeof(int))) memcpy(&fence, CMSG_DATA(c), sizeof(fence));
        if (fence >= 0) {
            wl_event_source_remove(frame->source);
            close(frame->fd);
            frame->fd = fence;
            frame->socket = false;
            frame->source = wl_event_loop_add_fd(frame->loop, fence, WL_EVENT_READABLE, deck_direct_released, frame);
        } else deck_direct_frame_destroy(frame);
    } else deck_direct_frame_destroy(frame);
    return 0;
}

static void deck_direct_complete(void *data, ASurfaceTransactionStats *stats) {
    struct deck_direct_complete *complete = data;
    struct deck_direct_submission *previous = complete->previous;
    if (previous) {
        int fence = ASurfaceTransactionStats_getPreviousReleaseFenceFd(stats, complete->control);
        char byte = 1, ancillary[CMSG_SPACE(sizeof(int))] = {0};
        struct iovec iov = {&byte, 1};
        struct msghdr msg = {.msg_iov = &iov, .msg_iovlen = 1};
        if (fence >= 0) {
            msg.msg_control = ancillary; msg.msg_controllen = sizeof(ancillary);
            struct cmsghdr *c = CMSG_FIRSTHDR(&msg);
            c->cmsg_level = SOL_SOCKET; c->cmsg_type = SCM_RIGHTS;
            c->cmsg_len = CMSG_LEN(sizeof(int)); memcpy(CMSG_DATA(c), &fence, sizeof(fence));
        }
        sendmsg(previous->fd, &msg, MSG_NOSIGNAL | MSG_DONTWAIT);
        if (fence >= 0) close(fence);
        close(previous->fd);
        AHardwareBuffer_release(previous->buffer);
        free(previous);
    }
    deck_direct_layer_unref(complete->layer);
    free(complete);
}

static bool deck_direct_transaction(struct deck_direct_layer *layer, ASurfaceTransaction *tx,
                                    struct deck_direct_submission *next) {
    struct deck_direct_complete *complete = calloc(1, sizeof(*complete));
    if (!complete) return false;
    complete->layer = layer;
    complete->control = layer->control;
    complete->previous = layer->current;
    atomic_fetch_add(&layer->refs, 1);
    ASurfaceTransaction_setOnComplete(tx, complete, deck_direct_complete);
    layer->current = next;
    ASurfaceTransaction_apply(tx);
    return true;
}

static void deck_direct_hide(struct xdg *xdg, bool destroy) {
    struct deck_direct_layer *layer = xdg->direct_layer;
    if (!layer) return;
    if (layer->current) {
        ASurfaceTransaction *tx = ASurfaceTransaction_create();
        ASurfaceTransaction_setBuffer(tx, layer->control, NULL, -1);
        ASurfaceTransaction_setVisibility(tx, layer->control, ASURFACE_TRANSACTION_VISIBILITY_HIDE);
        if (destroy) ASurfaceTransaction_reparent(tx, layer->control, NULL);
        bool applied = deck_direct_transaction(layer, tx, NULL);
        ASurfaceTransaction_delete(tx);
        if (!applied) return; /* retain the displayed buffer on allocation failure */
    }
    if (destroy) {
        deck_direct_layer_unref(layer); xdg->direct_layer = NULL;
    }
}

/* Conservative eligibility: exactly one opaque buffer, untransformed,
 * full-window coverage. Subsurfaces/notifications use the existing compositor. */
static bool deck_direct_present(struct xdg *xdg) {
    struct surface *s = xdg->surface;
    if (!xdg->deck || !xdg->deck_direct || !s || !wl_list_empty(&s->children)) return false;
    struct dmabuf *b = s->gpu_buffer;
    if (!b || !b->ahb || s->buffer_transform) return false;
    int sw = surface_width(s), sh = surface_height(s);
    if (xdg->has_geometry && (xdg->geo_x || xdg->geo_y || xdg->geo_w != sw || xdg->geo_h != sh)) return false;
    if (!(b->format == DRM_FORMAT_XRGB8888 || b->format == DRM_FORMAT_XBGR8888 ||
        (s->opaque.set && s->opaque.x1 <= 0 && s->opaque.y1 <= 0 && s->opaque.x2 >= sw && s->opaque.y2 >= sh))) return false;
    if (s->view.width >= 0 || s->view.height >= 0) return false;
    struct dma_buf_export_sync_file acquire = {.flags = DMA_BUF_SYNC_READ, .fd = -1};
    if (ioctl(b->fd, DMA_BUF_IOCTL_EXPORT_SYNC_FILE, &acquire)) return false;
    int sockets[2];
    if (socketpair(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC | SOCK_NONBLOCK, 0, sockets)) {
        if (acquire.fd >= 0) close(acquire.fd); return false;
    }
    struct deck_direct_frame *frame = calloc(1, sizeof(*frame));
    struct deck_direct_submission *submission = calloc(1, sizeof(*submission));
    if (!frame || !submission) goto fail;
    if (!xdg->direct_layer) {
        struct deck_direct_layer *layer = calloc(1, sizeof(*layer));
        if (!layer) goto fail;
        atomic_init(&layer->refs, 1);
        layer->control = ASurfaceControl_createFromWindow(xdg->window, "Dextop Deck direct AHB");
        if (!layer->control) { free(layer); goto fail; }
        xdg->direct_layer = layer;
    }
    frame->loop = xdg->comp->loop;
    frame->buffer = b; frame->fd = sockets[0]; frame->socket = true;
    frame->source = wl_event_loop_add_fd(xdg->comp->loop, frame->fd, WL_EVENT_READABLE, deck_direct_released, frame);
    if (!frame->source) goto fail;
    submission->fd = sockets[1]; submission->buffer = b->ahb;
    ASurfaceTransaction *tx = ASurfaceTransaction_create();
    ARect src = {0, 0, b->width, b->height};
    ARect dst = {0, 0, ANativeWindow_getWidth(xdg->window), ANativeWindow_getHeight(xdg->window)};
    ASurfaceTransaction_setBuffer(tx, xdg->direct_layer->control, b->ahb, acquire.fd);
    acquire.fd = -1; /* transaction owns the acquire fence */
    ASurfaceTransaction_setGeometry(tx, xdg->direct_layer->control, &src, &dst, 0);
    ASurfaceTransaction_setZOrder(tx, xdg->direct_layer->control, 1);
    ASurfaceTransaction_setBufferTransparency(tx, xdg->direct_layer->control, ASURFACE_TRANSACTION_TRANSPARENCY_OPAQUE);
    ASurfaceTransaction_setVisibility(tx, xdg->direct_layer->control, ASURFACE_TRANSACTION_VISIBILITY_SHOW);
    AHardwareBuffer_acquire(submission->buffer);
    gpu_buffer_hold(b);
    wl_list_insert(&xdg->comp->direct_frames, &frame->link);
    bool applied = deck_direct_transaction(xdg->direct_layer, tx, submission);
    ASurfaceTransaction_delete(tx);
    if (!applied) {
        AHardwareBuffer_release(submission->buffer);
        deck_direct_frame_destroy(frame); close(sockets[1]); free(submission); return false;
    }
    xdg->deck_direct_submits++;
    return true;
fail:
    if (frame && frame->source) wl_event_source_remove(frame->source);
    free(frame); free(submission); close(sockets[0]); close(sockets[1]);
    if (acquire.fd >= 0) close(acquire.fd);
    return false;
}
