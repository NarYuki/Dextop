/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_IMAGE_H
#define VKR_IMAGE_H

#include "vkr_common.h"

struct vkr_image {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(image, VK_OBJECT_TYPE_IMAGE, VkImage)

struct vkr_image_view {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(image_view, VK_OBJECT_TYPE_IMAGE_VIEW, VkImageView)

struct vkr_sampler {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(sampler, VK_OBJECT_TYPE_SAMPLER, VkSampler)

struct vkr_sampler_ycbcr_conversion {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(sampler_ycbcr_conversion,
                       VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION,
                       VkSamplerYcbcrConversion)

void
vkr_context_init_image_dispatch(struct vkr_context *ctx);

void
vkr_context_init_image_view_dispatch(struct vkr_context *ctx);

void
vkr_context_init_sampler_dispatch(struct vkr_context *ctx);

void
vkr_context_init_sampler_ycbcr_conversion_dispatch(struct vkr_context *ctx);

#endif /* VKR_IMAGE_H */
