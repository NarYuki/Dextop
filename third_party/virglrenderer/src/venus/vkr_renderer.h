/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_RENDERER_H
#define VKR_RENDERER_H

#include "config.h"

#include <stddef.h>
#include <stdint.h>

#include "virgl_resource.h"
#include "virglrenderer.h"

#define VKR_RENDERER_THREAD_SYNC (1u << 0)
#define VKR_RENDERER_ASYNC_FENCE_CB (1u << 1)

typedef void (*vkr_renderer_retire_fence_callback_type)(uint32_t ctx_id,
                                                        uint32_t ring_idx,
                                                        uint64_t fence_id);

struct vkr_renderer_callbacks {
   virgl_log_callback_type debug_logger;
   vkr_renderer_retire_fence_callback_type retire_fence;
};

size_t
vkr_get_capset(void *capset, uint32_t flags);

bool
vkr_renderer_init(uint32_t flags, const struct vkr_renderer_callbacks *cbs);

void
vkr_renderer_fini(void);

bool
vkr_renderer_create_context(uint32_t ctx_id,
                            uint32_t ctx_flags,
                            uint32_t nlen,
                            const char *name);

void
vkr_renderer_destroy_context(uint32_t ctx_id);

bool
vkr_renderer_submit_cmd(uint32_t ctx_id, void *cmd, uint32_t size);

bool
vkr_renderer_submit_fence(uint32_t ctx_id,
                          uint32_t flags,
                          uint64_t ring_idx,
                          uint64_t fence_id);

bool
vkr_renderer_create_resource(uint32_t ctx_id,
                             uint32_t res_id,
                             uint64_t blob_id,
                             uint64_t blob_size,
                             uint32_t blob_flags,
                             enum virgl_resource_fd_type *out_fd_type,
                             int *out_res_fd,
                             uint32_t *out_map_info,
                             struct virgl_resource_vulkan_info *out_vulkan_info);

bool
vkr_renderer_import_resource(uint32_t ctx_id,
                             uint32_t res_id,
                             enum virgl_resource_fd_type fd_type,
                             int fd,
                             uint64_t size);

void
vkr_renderer_destroy_resource(uint32_t ctx_id, uint32_t res_id);

#endif /* VKR_RENDERER_H */
