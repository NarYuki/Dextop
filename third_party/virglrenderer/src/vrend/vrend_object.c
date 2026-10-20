/**************************************************************************
 *
 * Copyright (C) 2014 Red Hat Inc.
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

#include "util/u_pointer.h"
#include "util/u_memory.h"
#include "util/u_hash_table.h"

#include "virgl_util.h"
#include "vrend_object.h"

struct vrend_object_types {
   void (*unref)(void *);
} obj_types[VIRGL_MAX_OBJECTS];

void vrend_object_set_destroy_callback(int type, void (*cb)(void *))
{
   obj_types[type].unref = cb;
}

struct vrend_object {
   enum virgl_object_type type;
   uint32_t handle;
   void *data;
};

static void free_object(void *value)
{
   struct vrend_object *obj = value;

   if (obj_types[obj->type].unref)
      obj_types[obj->type].unref(obj->data);
   else {
      /* for objects with no callback just free them */
      free(obj->data);
   }

   free(obj);
}

struct util_hash_table *vrend_object_init_ctx_table(void)
{
   struct util_hash_table *ctx_hash;
   ctx_hash = util_hash_table_create(hash_func_u32, equal_func, free_object);
   return ctx_hash;
}

void vrend_object_fini_ctx_table(struct util_hash_table *ctx_hash)
{
   if (!ctx_hash)
      return;

   util_hash_table_destroy(ctx_hash);
}

static void vrend_ctx_resource_destroy_func(UNUSED void *val)
{
   /* we don't own a reference of vrend_resource */
}

struct util_hash_table *
vrend_ctx_resource_init_table(void)
{
   return util_hash_table_create(hash_func_u32,
                                 equal_func,
                                 vrend_ctx_resource_destroy_func);
}

void vrend_ctx_resource_fini_table(struct util_hash_table *res_hash)
{
   util_hash_table_destroy(res_hash);
}

uint32_t
vrend_object_insert(struct util_hash_table *handle_hash,
                    void *data, uint32_t handle,
                    enum virgl_object_type type)
{
   struct vrend_object *obj = CALLOC_STRUCT(vrend_object);

   if (!obj)
      return 0;
   obj->handle = handle;
   obj->data = data;
   obj->type = type;
   util_hash_table_set(handle_hash, intptr_to_pointer(obj->handle), obj);
   return obj->handle;
}

void
vrend_object_remove(struct util_hash_table *handle_hash,
                    uint32_t handle, UNUSED enum virgl_object_type type)
{
   util_hash_table_remove(handle_hash, intptr_to_pointer(handle));
}

void *vrend_object_lookup(struct util_hash_table *handle_hash,
                          uint32_t handle, enum virgl_object_type type)
{
   struct vrend_object *obj;

   obj = util_hash_table_get(handle_hash, intptr_to_pointer(handle));
   if (!obj) {
      return NULL;
   }

   if (obj->type != type)
      return NULL;
   return obj->data;
}

void vrend_ctx_resource_insert(struct util_hash_table *res_hash,
                               uint32_t res_id,
                               struct vrend_resource *res)
{
   util_hash_table_set(res_hash, uintptr_to_pointer(res_id), res);
}

void vrend_ctx_resource_remove(struct util_hash_table *res_hash,
                               uint32_t res_id)
{
   util_hash_table_remove(res_hash, uintptr_to_pointer(res_id));
}

struct vrend_resource *vrend_ctx_resource_lookup(struct util_hash_table *res_hash,
                                                 uint32_t res_id)
{
   return util_hash_table_get(res_hash, uintptr_to_pointer(res_id));
}
