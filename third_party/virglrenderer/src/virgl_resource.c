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

#include "virgl_resource.h"

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

#include "util/os_file.h"
#include "util/u_hash_table.h"
#include "util/u_pointer.h"
#include "virgl_util.h"
#include "virgl_context.h"

static struct util_hash_table *virgl_resource_table;
static struct virgl_resource_pipe_callbacks pipe_callbacks;

static void
virgl_resource_destroy_func(void *val)
{
   struct virgl_resource *res = (struct virgl_resource *)val;

   if (res->pipe_resource)
      pipe_callbacks.unref(res->pipe_resource, pipe_callbacks.data);
   if ((res->fd_type != VIRGL_RESOURCE_FD_INVALID) &&
       (res->fd_type != VIRGL_RESOURCE_OPAQUE_HANDLE))
      close(res->fd);

   free(res);
}

int
virgl_resource_table_init(const struct virgl_resource_pipe_callbacks *callbacks)
{
   virgl_resource_table = util_hash_table_create(hash_func_u32,
                                                 equal_func,
                                                 virgl_resource_destroy_func);
   if (!virgl_resource_table)
      return ENOMEM;

   if (callbacks)
      pipe_callbacks = *callbacks;

   return 0;
}

void
virgl_resource_table_cleanup(void)
{
   util_hash_table_destroy(virgl_resource_table);
   virgl_resource_table = NULL;
   memset(&pipe_callbacks, 0, sizeof(pipe_callbacks));
}

void
virgl_resource_table_reset(void)
{
   util_hash_table_clear(virgl_resource_table);
}

static struct virgl_resource *
virgl_resource_create(uint32_t res_id)
{
   struct virgl_resource *res;
   enum pipe_error err;

   res = calloc(1, sizeof(*res));
   if (!res)
      return NULL;

   err = util_hash_table_set(virgl_resource_table,
                             uintptr_to_pointer(res_id),
                             res);
   if (err != PIPE_OK) {
      free(res);
      return NULL;
   }

   res->res_id = res_id;
   res->fd_type = VIRGL_RESOURCE_FD_INVALID;
   res->fd = -1;

   return res;
}

struct virgl_resource *
virgl_resource_create_from_pipe(uint32_t res_id,
                                struct pipe_resource *pres,
                                const struct iovec *iov,
                                int iov_count)
{
   struct virgl_resource *res;

   res = virgl_resource_create(res_id);
   if (!res) {
      pipe_callbacks.unref(pres, pipe_callbacks.data);
      return NULL;
   }

   /* take ownership */
   res->pipe_resource = pres;

   res->iov = iov;
   res->iov_count = iov_count;

   return res;
}

struct virgl_resource *
virgl_resource_create_from_fd(uint32_t res_id,
                              enum virgl_resource_fd_type fd_type,
                              int fd,
                              const struct iovec *iov,
                              int iov_count,
                              const struct virgl_resource_vulkan_info *vulkan_info)
{
   struct virgl_resource *res;

   assert(fd_type != VIRGL_RESOURCE_FD_INVALID  && fd >= 0);

   res = virgl_resource_create(res_id);
   if (!res) {
      close(fd);
      return NULL;
   }

   res->fd_type = fd_type;
   /* take ownership */
   res->fd = fd;

   res->iov = iov;
   res->iov_count = iov_count;

   if (vulkan_info && fd_type == VIRGL_RESOURCE_FD_OPAQUE)
      res->vulkan_info = *vulkan_info;

   return res;
}

struct virgl_resource *
virgl_resource_create_from_opaque_handle(struct virgl_context *ctx,
                                         uint32_t res_id,
                                         uint32_t opaque_handle)
{
   struct virgl_resource *res;

   res = virgl_resource_create(res_id);
   if (!res)
      return NULL;

   res->fd_type = VIRGL_RESOURCE_OPAQUE_HANDLE;
   res->opaque_handle = opaque_handle;

   /* We need the ctx to get an fd from handle (which we don't want to do
    * until asked, to avoid file descriptor limits)
    *
    * Shareable resources should not use OPAQUE_HANDLE, to avoid lifetime
    * issues (ie. resource outliving the context which created it).
    */
   res->opaque_handle_context_id = ctx->ctx_id;

   return res;
}

struct virgl_resource *
virgl_resource_create_from_iov(uint32_t res_id,
                               const struct iovec *iov,
                               int iov_count)
{
   struct virgl_resource *res;

   if (iov_count)
      assert(iov);

   res = virgl_resource_create(res_id);
   if (!res)
      return NULL;

   res->iov = iov;
   res->iov_count = iov_count;

   return res;
}

void
virgl_resource_remove(uint32_t res_id)
{
   util_hash_table_remove(virgl_resource_table, uintptr_to_pointer(res_id));
}

struct virgl_resource *virgl_resource_lookup(uint32_t res_id)
{
   return util_hash_table_get(virgl_resource_table,
                              uintptr_to_pointer(res_id));
}

int
virgl_resource_attach_iov(struct virgl_resource *res,
                          const struct iovec *iov,
                          int iov_count)
{
   if (res->iov)
      return EINVAL;

   res->iov = iov;
   res->iov_count = iov_count;

   if (res->pipe_resource) {
      pipe_callbacks.attach_iov(res->pipe_resource,
                                iov,
                                iov_count,
                                pipe_callbacks.data);
   }

   return 0;
}

void
virgl_resource_detach_iov(struct virgl_resource *res)
{
   if (!res->iov)
      return;

   if (res->pipe_resource)
      pipe_callbacks.detach_iov(res->pipe_resource, pipe_callbacks.data);

   res->iov = NULL;
   res->iov_count = 0;
}

enum virgl_resource_fd_type
virgl_resource_export_fd(struct virgl_resource *res, int *fd)
{
   if (res->fd_type == VIRGL_RESOURCE_OPAQUE_HANDLE) {
      struct virgl_context *ctx;

      ctx = virgl_context_lookup(res->opaque_handle_context_id);
      if (!ctx)
         return VIRGL_RESOURCE_FD_INVALID;

      return ctx->export_opaque_handle(ctx, res, fd);
   } else if (res->fd_type != VIRGL_RESOURCE_FD_INVALID) {
      *fd = os_dupfd_cloexec(res->fd);
      return *fd >= 0 ? res->fd_type : VIRGL_RESOURCE_FD_INVALID;
   } else if (res->pipe_resource) {
      return pipe_callbacks.export_fd(res->pipe_resource,
                                      fd,
                                      pipe_callbacks.data);
   }

   return VIRGL_RESOURCE_FD_INVALID;
}
