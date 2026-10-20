/**************************************************************************
 *
 * Copyright (C) 2022 Collabora Ltd
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

#ifndef VKR_ALLOCATOR_H
#define VKR_ALLOCATOR_H

#include <stdint.h>

struct virgl_resource;

#ifdef ENABLE_VENUS

int
vkr_allocator_init(void);
void
vkr_allocator_fini(void);

int
vkr_allocator_resource_map(struct virgl_resource *res, void **map, uint64_t *out_size);
int
vkr_allocator_resource_unmap(struct virgl_resource *res);

#else /* ENABLE_VENUS */

#include "util/macros.h"

static inline int
vkr_allocator_init(void)
{
   return -1;
}

static inline void
vkr_allocator_fini(void)
{
}

static inline int
vkr_allocator_resource_map(UNUSED struct virgl_resource *res,
                           UNUSED void **map,
                           UNUSED uint64_t *out_size)
{
   return -1;
}

static inline int
vkr_allocator_resource_unmap(UNUSED struct virgl_resource *res)
{
   return -1;
}

#endif /* ENABLE_VENUS */

#endif /* VKR_ALLOCATOR_H */
