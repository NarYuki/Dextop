/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_QUEUE_H
#define VKR_QUEUE_H

#include "vkr_common.h"

struct vkr_queue_sync {
   VkFence fence;
   bool device_lost;

   uint32_t flags;
   uint32_t ring_idx;
   uint64_t fence_id;

   struct list_head head;
};

struct vkr_queue {
   struct vkr_object base;

   struct vkr_context *context;
   struct vkr_device *device;

   VkDeviceQueueCreateFlags flags;
   uint32_t family;
   uint32_t index;

   /* only used when client driver uses multiple timelines */
   uint32_t ring_idx;

   /* Ensure host access to VkQueue being externally synchronized between renderer main
    * thread and ring thread.
    */
   mtx_t vk_mutex;

   /* Submitted fences are added to sync_thread.syncs first. With required
    * VKR_RENDERER_THREAD_SYNC and VKR_RENDERER_ASYNC_FENCE_CB in render server, the sync
    * thread calls vkWaitForFences and retires signaled fences in order.
    */
   struct {
      mtx_t mutex;
      cnd_t cond;
      struct list_head syncs;
      thrd_t thread;
      bool join;
   } sync_thread;
};
VKR_DEFINE_OBJECT_CAST(queue, VK_OBJECT_TYPE_QUEUE, VkQueue)

struct vkr_fence {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(fence, VK_OBJECT_TYPE_FENCE, VkFence)

struct vkr_semaphore {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(semaphore, VK_OBJECT_TYPE_SEMAPHORE, VkSemaphore)

struct vkr_event {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(event, VK_OBJECT_TYPE_EVENT, VkEvent)

void
vkr_context_init_queue_dispatch(struct vkr_context *ctx);

void
vkr_context_init_fence_dispatch(struct vkr_context *ctx);

void
vkr_context_init_semaphore_dispatch(struct vkr_context *ctx);

void
vkr_context_init_event_dispatch(struct vkr_context *ctx);

struct vkr_queue *
vkr_queue_create(struct vkr_context *ctx,
                 struct vkr_device *dev,
                 VkDeviceQueueCreateFlags flags,
                 uint32_t family,
                 uint32_t index,
                 VkQueue handle);

void
vkr_queue_destroy(struct vkr_context *ctx, struct vkr_queue *queue);

bool
vkr_queue_sync_submit(struct vkr_queue *queue,
                      uint32_t flags,
                      uint32_t ring_idx,
                      uint64_t fence_id);

#endif /* VKR_QUEUE_H */
