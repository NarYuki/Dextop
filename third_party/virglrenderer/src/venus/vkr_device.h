/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_DEVICE_H
#define VKR_DEVICE_H

#include "vkr_common.h"

#include "venus-protocol/vn_protocol_renderer_util.h"

#include "vkr_context.h"

struct vkr_device {
   struct vkr_object base;

   struct vkr_physical_device *physical_device;

   struct vn_device_proc_table proc_table;

   struct list_head queues;

   mtx_t free_sync_mutex;
   struct list_head free_syncs;

   mtx_t object_mutex;
   struct list_head objects;
};
VKR_DEFINE_OBJECT_CAST(device, VK_OBJECT_TYPE_DEVICE, VkDevice)

void
vkr_context_init_device_dispatch(struct vkr_context *ctx);

void
vkr_device_destroy(struct vkr_context *ctx, struct vkr_device *dev, bool destroy_vk);

static inline bool
vkr_device_should_track_object(const struct vkr_object *obj)
{
   assert(vkr_is_recognized_object_type(obj->type));

   switch (obj->type) {
   case VK_OBJECT_TYPE_INSTANCE:        /* non-device objects */
   case VK_OBJECT_TYPE_PHYSICAL_DEVICE: /* non-device objects */
   case VK_OBJECT_TYPE_DEVICE:          /* device itself */
   case VK_OBJECT_TYPE_QUEUE:           /* not tracked as device objects */
   case VK_OBJECT_TYPE_COMMAND_BUFFER:  /* pool objects */
   case VK_OBJECT_TYPE_DESCRIPTOR_SET:  /* pool objects */
      return false;
   default:
      return true;
   }
}

static inline void
vkr_device_add_object(struct vkr_context *ctx,
                      struct vkr_device *dev,
                      struct vkr_object *obj)
{
   vkr_context_add_object(ctx, obj);

   assert(vkr_device_should_track_object(obj));

   mtx_lock(&dev->object_mutex);
   list_add(&obj->track_head, &dev->objects);
   mtx_unlock(&dev->object_mutex);
}

static inline void
vkr_device_remove_object(struct vkr_context *ctx,
                         UNUSED struct vkr_device *dev,
                         struct vkr_object *obj)
{
   assert(vkr_device_should_track_object(obj));

   mtx_lock(&dev->object_mutex);
   list_del(&obj->track_head);
   mtx_unlock(&dev->object_mutex);

   /* this frees obj */
   vkr_context_remove_object(ctx, obj);
}

#endif /* VKR_DEVICE_H */
