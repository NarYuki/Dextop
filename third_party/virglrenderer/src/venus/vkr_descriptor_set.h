/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_DESCRIPTOR_SET_H
#define VKR_DESCRIPTOR_SET_H

#include "vkr_common.h"

#include "vkr_context.h"

struct vkr_descriptor_set_layout {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(descriptor_set_layout,
                       VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT,
                       VkDescriptorSetLayout)

struct vkr_descriptor_pool {
   struct vkr_object base;

   VkDescriptorPoolCreateFlags flags;

   struct list_head descriptor_sets;
};
VKR_DEFINE_OBJECT_CAST(descriptor_pool, VK_OBJECT_TYPE_DESCRIPTOR_POOL, VkDescriptorPool)

struct vkr_descriptor_set {
   struct vkr_object base;

   struct vkr_device *device;
};
VKR_DEFINE_OBJECT_CAST(descriptor_set, VK_OBJECT_TYPE_DESCRIPTOR_SET, VkDescriptorSet)

struct vkr_descriptor_update_template {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(descriptor_update_template,
                       VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE,
                       VkDescriptorUpdateTemplate)

void
vkr_context_init_descriptor_set_layout_dispatch(struct vkr_context *ctx);

void
vkr_context_init_descriptor_pool_dispatch(struct vkr_context *ctx);

void
vkr_context_init_descriptor_set_dispatch(struct vkr_context *ctx);

void
vkr_context_init_descriptor_update_template_dispatch(struct vkr_context *ctx);

static inline void
vkr_descriptor_pool_release(struct vkr_context *ctx, struct vkr_descriptor_pool *pool)
{
   vkr_context_remove_objects(ctx, &pool->descriptor_sets);
}

#endif /* VKR_DESCRIPTOR_SET_H */
