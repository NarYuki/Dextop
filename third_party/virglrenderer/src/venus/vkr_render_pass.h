/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_RENDER_PASS_H
#define VKR_RENDER_PASS_H

#include "vkr_common.h"

struct vkr_render_pass {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(render_pass, VK_OBJECT_TYPE_RENDER_PASS, VkRenderPass)

struct vkr_framebuffer {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(framebuffer, VK_OBJECT_TYPE_FRAMEBUFFER, VkFramebuffer)

void
vkr_context_init_render_pass_dispatch(struct vkr_context *ctx);

void
vkr_context_init_framebuffer_dispatch(struct vkr_context *ctx);

#endif /* VKR_RENDER_PASS_H */
