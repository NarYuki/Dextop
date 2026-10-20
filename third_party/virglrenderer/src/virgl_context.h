/**************************************************************************
 *
 * Copyright (C) 2020 Chromium.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 **************************************************************************/

#ifndef VIRGL_CONTEXT_H
#define VIRGL_CONTEXT_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <unistd.h>

#include "virgl_resource.h"

#include "util/list.h"

struct vrend_transfer_info;
struct pipe_resource;

struct virgl_context_blob {
   /* valid fd or pipe resource */
   enum virgl_resource_fd_type type;
   union {
      int fd;
      uint32_t opaque_handle;
      struct pipe_resource *pipe_resource;
   } u;

   uint32_t map_info;

   struct virgl_resource_vulkan_info vulkan_info;
};

struct virgl_context;

typedef void (*virgl_context_fence_retire)(struct virgl_context *ctx,
                                           uint32_t ring_idx,
                                           uint64_t fence_id);

/**
 * Base class for renderer contexts.  For example, vrend_decode_ctx is a
 * subclass of virgl_context.
 */
struct virgl_context {
   uint32_t ctx_id;

   int in_fence_fd;

   uint32_t capset_id;

   /*
    * Each fence goes through submitted, signaled, and retired.  This callback
    * is called from virgl_context::retire_fences to retire signaled fences of
    * each queue.  When a queue has multiple signaled fences by the time
    * virgl_context::retire_fences is called, this callback might not be called
    * on all fences but only on the latest one, depending on the flags of the
    * fences.
    */
   virgl_context_fence_retire fence_retire;

   bool supports_fence_sharing;

   void (*destroy)(struct virgl_context *ctx);

   void (*attach_resource)(struct virgl_context *ctx,
                           struct virgl_resource *res);
   void (*detach_resource)(struct virgl_context *ctx,
                           struct virgl_resource *res);
   enum virgl_resource_fd_type (*export_opaque_handle)(struct virgl_context *ctx,
                                                       struct virgl_resource *res,
                                                       int *out_fd);

   int (*transfer_3d)(struct virgl_context *ctx,
                      struct virgl_resource *res,
                      const struct vrend_transfer_info *info,
                      int transfer_mode);

   /* These are used to create a virgl_resource from a context object.
    *
    * get_blob returns a virgl_context_blob from which a virgl_resource can be
    * created.
    *
    * Note that get_blob is a one-time thing.  The context object might be
    * destroyed or reject subsequent get_blob calls.
    */
   int (*get_blob)(struct virgl_context *ctx,
                   uint32_t res_id,
                   uint64_t blob_id,
                   uint64_t blob_size,
                   uint32_t blob_flags,
                   struct virgl_context_blob *blob);

   int (*submit_cmd)(struct virgl_context *ctx,
                     const void *buffer,
                     size_t size);

   /*
    * Return an fd that is readable whenever there is any signaled fence in
    * any queue, or -1 if not supported.
    */
   int (*get_fencing_fd)(struct virgl_context *ctx);

   /* retire signaled fences of all queues */
   void (*retire_fences)(struct virgl_context *ctx);

   /* submit a fence to the queue identified by ring_idx */
   int (*submit_fence)(struct virgl_context *ctx,
                       uint32_t flags,
                       uint32_t ring_idx,
                       uint64_t fence_id);

   /* For DRM native contexts, return the device fd: */
   int (*get_device_fd)(struct virgl_context *ctx);

   /* map a resource into a particular address */
   void * (*resource_map)(struct virgl_context *ctx,
                          struct virgl_resource *res,
                          void *addr,
                          int32_t prot,
                          int32_t flags);
};

struct virgl_context_foreach_args {
   bool (*callback)(struct virgl_context *ctx, void *data);
   void *data;
};

int
virgl_context_table_init(void);

void
virgl_context_table_cleanup(void);

void
virgl_context_table_reset(void);

int
virgl_context_add(struct virgl_context *ctx);

void
virgl_context_remove(uint32_t ctx_id);

struct virgl_context *
virgl_context_lookup(uint32_t ctx_id);

void
virgl_context_foreach(const struct virgl_context_foreach_args *args);

int virgl_context_take_in_fence_fd(struct virgl_context *ctx);

#endif /* VIRGL_CONTEXT_H */
