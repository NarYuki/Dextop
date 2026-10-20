/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_QUERY_POOL_H
#define VKR_QUERY_POOL_H

#include "vkr_common.h"

struct vkr_query_pool {
   struct vkr_object base;
};
VKR_DEFINE_OBJECT_CAST(query_pool, VK_OBJECT_TYPE_QUERY_POOL, VkQueryPool)

void
vkr_context_init_query_pool_dispatch(struct vkr_context *ctx);

#endif /* VKR_QUERY_POOL_H */
