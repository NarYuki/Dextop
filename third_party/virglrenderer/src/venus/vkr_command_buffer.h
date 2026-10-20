/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_COMMAND_BUFFER_H
#define VKR_COMMAND_BUFFER_H

#include "vkr_common.h"

#include "vkr_context.h"

struct vkr_command_pool {
   struct vkr_object base;

   struct list_head command_buffers;
};
VKR_DEFINE_OBJECT_CAST(command_pool, VK_OBJECT_TYPE_COMMAND_POOL, VkCommandPool)

struct vkr_command_buffer {
   struct vkr_object base;

   struct vkr_device *device;
};
VKR_DEFINE_OBJECT_CAST(command_buffer, VK_OBJECT_TYPE_COMMAND_BUFFER, VkCommandBuffer)

void
vkr_context_init_command_pool_dispatch(struct vkr_context *ctx);

void
vkr_context_init_command_buffer_dispatch(struct vkr_context *ctx);

static inline void
vkr_command_pool_release(struct vkr_context *ctx, struct vkr_command_pool *pool)
{
   vkr_context_remove_objects(ctx, &pool->command_buffers);
}

#endif /* VKR_COMMAND_BUFFER_H */
