/*
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_ACCELERATION_STRUCTURE_H
#define VKR_ACCELERATION_STRUCTURE_H

#include "vkr_common.h"

struct vkr_acceleration_structure {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(acceleration_structure,
                       VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_KHR,
                       VkAccelerationStructureKHR)

void
vkr_context_init_acceleration_structure_dispatch(struct vkr_context *ctx);

#endif /* VKR_ACCELERATION_STRUCTURE_H */
