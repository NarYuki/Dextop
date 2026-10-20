/*
 * Copyright 2022 Google LLC
 * Copyright 2024 Collabora, Ltd.
 * SPDX-License-Identifier: MIT
 */

#ifndef DRM_CONTEXT_H_
#define DRM_CONTEXT_H_

#include <stdint.h>

#include "virgl_context.h"
#include "virgl_util.h"

#include "drm_hw.h"

#ifdef ENABLE_DRM

struct drm_context;

struct drm_ccmd {
   const char *name;
   int (*handler)(struct drm_context *ctx, struct vdrm_ccmd_req *hdr);
   size_t size;
};

struct drm_object {
   /* Context-specific, assigned by guest userspace. It's used to link the bo
    * created via CCMD that creates GEM and the get_blob() callback.
    */
   uint32_t blob_id;
   /* Global, assigned by guest kernel. */
   uint32_t res_id;
   /* GEM handle, used in ioctl (eg: amdgpu_cs_submit_raw2). */
   uint32_t handle;
   /* GEM size. */
   uint64_t size;
};

struct drm_context {
   struct virgl_context base;

   struct vdrm_shmem *shmem;
   uint8_t *rsp_mem;
   uint32_t rsp_mem_sz;

   struct vdrm_ccmd_rsp *current_rsp;

   struct hash_table *blob_table;
   struct hash_table *resource_table;

   int fd;

   const struct drm_ccmd *ccmd_dispatch;
   unsigned int dispatch_size;
   unsigned int ccmd_alignment;

   void (*free_object)(struct drm_context *dctx, struct drm_object *dobj);
};
DEFINE_CAST(virgl_context, drm_context)

bool drm_context_init(struct drm_context *dctx, int fd,
                      const struct drm_ccmd *ccmd_dispatch, unsigned int dispatch_size);

void drm_context_deinit(struct drm_context *dctx);

void drm_context_fence_retire(struct virgl_context *vctx,
                              uint32_t ring_idx, uint64_t fence_id);

void *drm_context_rsp(struct drm_context *dctx, const struct vdrm_ccmd_req *hdr,
                      size_t len);

int drm_context_get_shmem_blob(struct drm_context *dctx,
                               const char *name, size_t shmem_size, uint64_t blob_size,
                               uint32_t blob_flags, struct virgl_context_blob *blob);

bool drm_context_blob_id_valid(struct drm_context *dctx, uint32_t blob_id);

struct drm_object *drm_context_retrieve_object_from_blob_id(struct drm_context *dctx,
                                                            uint64_t blob_id);

void drm_context_object_set_blob_id(struct drm_context *dctx,
                                    struct drm_object *obj,
                                    uint32_t blob_id);

void drm_context_object_set_res_id(struct drm_context *dctx,
                                   struct drm_object *obj,
                                   uint32_t res_id);

struct drm_object *drm_context_get_object_from_res_id(struct drm_context *dctx,
                                                      uint32_t res_id);

bool drm_context_res_id_unused(struct drm_context *dctx, uint32_t res_id);

#endif /* ENABLE_DRM */

#endif /* DRM_CONTEXT_H_ */
