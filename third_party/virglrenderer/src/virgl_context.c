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

#include "virgl_context.h"

#include <errno.h>

#ifndef WIN32
#include "util/libsync.h"
#endif
#include "util/os_misc.h"
#include "util/u_hash_table.h"
#include "util/u_pointer.h"
#include "virgl_util.h"

static struct util_hash_table *virgl_context_table;

static void
virgl_context_destroy_func(void *val)
{
   struct virgl_context *ctx = val;

   if (ctx->in_fence_fd >= 0)
      close(ctx->in_fence_fd);

   ctx->destroy(ctx);
}

int
virgl_context_table_init(void)
{
   virgl_context_table = util_hash_table_create(hash_func_u32,
                                                equal_func,
                                                virgl_context_destroy_func);
   return virgl_context_table ? 0 : ENOMEM;
}

void
virgl_context_table_cleanup(void)
{
   util_hash_table_destroy(virgl_context_table);
   virgl_context_table = NULL;
}

void
virgl_context_table_reset(void)
{
   util_hash_table_clear(virgl_context_table);
}

int
virgl_context_add(struct virgl_context *ctx)
{
   const enum pipe_error err = util_hash_table_set(
         virgl_context_table, uintptr_to_pointer(ctx->ctx_id), ctx);
   return err == PIPE_OK ? 0 : ENOMEM;
}

void
virgl_context_remove(uint32_t ctx_id)
{
   util_hash_table_remove(virgl_context_table, uintptr_to_pointer(ctx_id));
}

struct virgl_context *
virgl_context_lookup(uint32_t ctx_id)
{
   return util_hash_table_get(virgl_context_table,
                              uintptr_to_pointer(ctx_id));
}

static enum pipe_error
virgl_context_foreach_func(UNUSED void *key, void *val, void *data)
{
   const struct virgl_context_foreach_args *args = data;
   struct virgl_context *ctx = val;

   return args->callback(ctx, args->data) ? PIPE_OK : PIPE_ERROR;
}

void
virgl_context_foreach(const struct virgl_context_foreach_args *args)
{
   util_hash_table_foreach(virgl_context_table,
                           virgl_context_foreach_func,
                           (void *)args);
}

int virgl_context_take_in_fence_fd(struct virgl_context *ctx)
{
   TRACE_FUNC();

   if (ctx->in_fence_fd < 0)
      return -1;

   int ret = ctx->in_fence_fd;
   ctx->in_fence_fd = -1;

   return ret;
}
