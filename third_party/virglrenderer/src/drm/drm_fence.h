/*
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef DRM_FENCE_H_
#define DRM_FENCE_H_

#include <stdbool.h>
#include <stdint.h>

#include "c11/threads.h"
#include "util/list.h"

/*
 * Helpers to deal with managing dma-fence fd's.  This should something that
 * can be re-used by any virtgpu native context implementation.
 */

struct drm_fence;
struct virgl_context;

/**
 * Represents a single timeline of fence-fd's.  Fences on a timeline are
 * signaled in FIFO order.
 */
struct drm_timeline {
   struct virgl_context *vctx;
   const char *name;
   int ring_idx;
   virgl_context_fence_retire fence_retire;

   int last_fence_fd;
   struct list_head pending_fences;

   mtx_t fence_mutex;
   cnd_t fence_cond;
   thrd_t sync_thread;
   bool stop_sync_thread;
};

void drm_timeline_init(struct drm_timeline *timeline, struct virgl_context *vctx,
                       const char *name, int ring_idx,
                       virgl_context_fence_retire fence_retire);

void drm_timeline_fini(struct drm_timeline *timeline);

int drm_timeline_submit_fence(struct drm_timeline *timeline, uint32_t flags,
                              uint64_t fence_id);

void drm_timeline_set_last_fence_fd(struct drm_timeline *timeline, int fd);

#endif /* DRM_FENCE_H_ */
