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

#ifndef VREND_OBJECT_H
#define VREND_OBJECT_H

#include "virgl_protocol.h"

struct vrend_resource;

struct util_hash_table *vrend_object_init_ctx_table(void);
void vrend_object_fini_ctx_table(struct util_hash_table *ctx_hash);

void vrend_object_remove(struct util_hash_table *handle_hash, uint32_t handle, enum virgl_object_type obj);
void *vrend_object_lookup(struct util_hash_table *handle_hash, uint32_t handle, enum virgl_object_type obj);
uint32_t vrend_object_insert(struct util_hash_table *handle_hash,
                             void *data,
                             uint32_t handle,
                             enum virgl_object_type type);

void vrend_object_set_destroy_callback(int type, void (*cb)(void *));

struct util_hash_table *vrend_ctx_resource_init_table(void);
void vrend_ctx_resource_fini_table(struct util_hash_table *res_hash);

void vrend_ctx_resource_insert(struct util_hash_table *res_hash,
                               uint32_t res_id,
                               struct vrend_resource *res);
void vrend_ctx_resource_remove(struct util_hash_table *res_hash,
                               uint32_t res_id);
struct vrend_resource *vrend_ctx_resource_lookup(struct util_hash_table *res_hash,
                                                 uint32_t res_id);

#endif
