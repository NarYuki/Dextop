/* Prefer an immediately reusable slot. If only release-fenced slots remain,
 * put that dependency on the GPU instead of losing a whole compositor tick. */
static int deck_output_take(struct linux_gpu_window *w, uint32_t *index, bool *output_wait) {
    struct deck_output *d = w->deck;
    int ready = -1, pending = -1, wait_fd = -1;
    *output_wait = false;
    pthread_mutex_lock(&d->lock);
    for (unsigned i = 0; i < DECK_OUTPUT_SLOTS; i++) {
        if (d->busy[i]) continue;
        int fd = d->release_fds[i];
        if (fd >= 0) {
            struct pollfd p = {.fd = fd, .events = POLLIN};
            int state = poll(&p, 1, 0);
            if (state == 0) { if (pending < 0) pending = (int)i; continue; }
            if (state != 1 || !(p.revents & POLLIN)) continue;
            close(fd); d->release_fds[i] = -1;
        }
        ready = (int)i;
        break;
    }
    if (ready < 0 && pending >= 0) {
        ready = pending;
        wait_fd = d->release_fds[ready];
        d->release_fds[ready] = -1;
    }
    if (ready >= 0) { d->busy[ready] = true; *index = (uint32_t)ready; }
    pthread_mutex_unlock(&d->lock);
    if (ready < 0) return 0;
    if (wait_fd >= 0) {
        VkImportSemaphoreFdInfoKHR import = {.sType = VK_STRUCTURE_TYPE_IMPORT_SEMAPHORE_FD_INFO_KHR,
            .semaphore = w->acquired, .flags = VK_SEMAPHORE_IMPORT_TEMPORARY_BIT,
            .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT, .fd = wait_fd};
        VkResult result = w->gpu->ImportSemaphoreFdKHR(w->gpu->device, &import);
        if (result != VK_SUCCESS) { close(wait_fd); error("Deck output release fence import", result); return -1; }
        *output_wait = true;
    }
    return 1;
}
