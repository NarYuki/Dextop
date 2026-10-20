/**************************************************************************
 *
 * Copyright (C) 2020 Chromium
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

#ifndef VIRGL_RESOURCE_H
#define VIRGL_RESOURCE_H

#include <stdbool.h>
#include <stdint.h>

struct iovec;
struct pipe_resource;
struct virgl_context;

enum virgl_resource_fd_type {
   VIRGL_RESOURCE_FD_DMABUF,
   VIRGL_RESOURCE_FD_OPAQUE,
   /* mmap()-able, usually memfd or shm */
   VIRGL_RESOURCE_FD_SHM,

   /**
    * An opaque handle can be something like a GEM handle, from which a
    * fd can be created upon demand.
    *
    * Renderers which use this type must implement virgl_context::export_fd
    *
    * Do not use this type for resources that are _BLOB_FLAG_USE_SHAREABLE,
    * as the opaque handle can become invalid/stale any time outside of the
    * original context.
    */
   VIRGL_RESOURCE_OPAQUE_HANDLE,

   VIRGL_RESOURCE_FD_INVALID = -1,
};

struct virgl_resource_vulkan_info {
   uint8_t device_uuid[16];
   uint8_t driver_uuid[16];
   uint64_t allocation_size;
   uint32_t memory_type_index;
};

/**
 * A global cross-context resource.  A virgl_resource is not directly usable
 * by renderer contexts, but must be attached and imported into renderer
 * contexts to create context objects first.  For example, it can be attached
 * and imported into a vrend_decode_ctx to create a vrend_resource.
 *
 * It is also possible to create a virgl_resource from a context object.
 *
 * The underlying storage of a virgl_resource is provided by a pipe_resource
 * and/or a fd.  When it is provided by a pipe_resource, the virgl_resource is
 * said to be typed because pipe_resource also provides the type information.
 *
 * Conventional resources are always typed.  Blob resources by definition do
 * not have nor need type information, but those created from vrend_decode_ctx
 * objects are typed.  That should be considered a convenience rather than
 * something to be relied upon.  Contexts must not assume that every resource is
 * typed when interop is expected.
 */
struct virgl_resource {
   uint32_t res_id;

   struct pipe_resource *pipe_resource;

   /* valid fd or handle type: */
   enum virgl_resource_fd_type fd_type;
   int fd;

   /**
    * For fd_type==VIRGL_RESOURCE_OPAQUE_HANDLE, the id of the context
    * which created this resource
    */
   uint32_t opaque_handle_context_id;
   uint32_t opaque_handle;

   const struct iovec *iov;
   int iov_count;

   uint32_t map_info;

   uint64_t map_size;
   void *mapped;
   bool mapped_from_pipe_resource;

   struct virgl_resource_vulkan_info vulkan_info;

   void *private_data;
};

struct virgl_resource_pipe_callbacks {
   void *data;

   void (*unref)(struct pipe_resource *pres, void *data);

   void (*attach_iov)(struct pipe_resource *pres,
                      const struct iovec *iov,
                      int iov_count,
                      void *data);
   void (*detach_iov)(struct pipe_resource *pres, void *data);

   enum virgl_resource_fd_type (*export_fd)(struct pipe_resource *pres,
                                            int *fd,
                                            void *data);
};

int
virgl_resource_table_init(const struct virgl_resource_pipe_callbacks *callbacks);

void
virgl_resource_table_cleanup(void);

void
virgl_resource_table_reset(void);

struct virgl_resource *
virgl_resource_create_from_pipe(uint32_t res_id,
                                struct pipe_resource *pres,
                                const struct iovec *iov,
                                int iov_count);

struct virgl_resource *
virgl_resource_create_from_fd(uint32_t res_id,
                              enum virgl_resource_fd_type fd_type,
                              int fd,
                              const struct iovec *iov,
                              int iov_count,
                              const struct virgl_resource_vulkan_info *vulkan_info);

struct virgl_resource *
virgl_resource_create_from_opaque_handle(struct virgl_context *ctx,
                                         uint32_t res_id,
                                         uint32_t opaque_handle);

struct virgl_resource *
virgl_resource_create_from_iov(uint32_t res_id,
                               const struct iovec *iov,
                               int iov_count);

void
virgl_resource_remove(uint32_t res_id);

struct virgl_resource *
virgl_resource_lookup(uint32_t res_id);

int
virgl_resource_attach_iov(struct virgl_resource *res,
                          const struct iovec *iov,
                          int iov_count);

void
virgl_resource_detach_iov(struct virgl_resource *res);

enum virgl_resource_fd_type
virgl_resource_export_fd(struct virgl_resource *res, int *fd);

#endif /* VIRGL_RESOURCE_H */
