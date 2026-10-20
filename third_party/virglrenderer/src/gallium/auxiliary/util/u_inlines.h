/**************************************************************************
 * 
 * Copyright 2007 VMware, Inc.
 * All Rights Reserved.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sub license, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice (including the
 * next paragraph) shall be included in all copies or substantial portions
 * of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
 * IN NO EVENT SHALL VMWARE AND/OR ITS SUPPLIERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 **************************************************************************/

#ifndef U_INLINES_H
#define U_INLINES_H

#include "pipe/p_defines.h"
#include "pipe/p_shader_tokens.h"
#include "pipe/p_state.h"
#include "util/u_debug.h"
#include "util/u_debug_describe.h"
#include "util/u_debug_refcnt.h"
#include "util/u_atomic.h"
#include "util/u_math.h"


#ifdef __cplusplus
extern "C" {
#endif


/*
 * Reference counting helper functions.
 */


static inline void
pipe_reference_init(struct pipe_reference *reference, unsigned count)
{
   p_atomic_set(&reference->count, count);
}

static inline bool
pipe_is_referenced(struct pipe_reference *reference)
{
   return p_atomic_read(&reference->count) != 0;
}

/**
 * Update reference counting.
 * The old thing pointed to, if any, will be unreferenced.
 * Both 'ptr' and 'reference' may be NULL.
 * \return true if the object's refcount hits zero and should be destroyed.
 */
static inline bool
pipe_reference_described(struct pipe_reference *ptr, 
                         struct pipe_reference *reference, 
                         debug_reference_descriptor get_desc)
{
   bool destroy = false;

   if(ptr != reference) {
      /* bump the reference.count first */
      if (reference) {
         assert(pipe_is_referenced(reference));
         p_atomic_inc(&reference->count);
         debug_reference(reference, get_desc, 1);
      }

      if (ptr) {
         assert(pipe_is_referenced(ptr));
         if (p_atomic_dec_zero(&ptr->count)) {
            destroy = true;
         }
         debug_reference(ptr, get_desc, -1);
      }
   }

   return destroy;
}

static inline bool
pipe_reference(struct pipe_reference *ptr, struct pipe_reference *reference)
{
   return pipe_reference_described(ptr, reference, 
                                   (debug_reference_descriptor)debug_describe_reference);
}

/* Return true if the surfaces are equal. */
static inline bool
pipe_surface_equal(struct pipe_surface *s1, struct pipe_surface *s2)
{
   return s1->texture == s2->texture &&
          s1->format == s2->format &&
          (s1->texture->target != PIPE_BUFFER ||
           (s1->u.buf.first_element == s2->u.buf.first_element &&
            s1->u.buf.last_element == s2->u.buf.last_element)) &&
          (s1->texture->target == PIPE_BUFFER ||
           (s1->u.tex.level == s2->u.tex.level &&
            s1->u.tex.first_layer == s2->u.tex.first_layer &&
            s1->u.tex.last_layer == s2->u.tex.last_layer));
}

/**
 * Get the polygon offset enable/disable flag for the given polygon fill mode.
 * \param fill_mode  one of PIPE_POLYGON_MODE_POINT/LINE/FILL
 */
static inline bool
util_get_offset(const struct pipe_rasterizer_state *templ,
                unsigned fill_mode)
{
   switch(fill_mode) {
   case PIPE_POLYGON_MODE_POINT:
      return templ->offset_point;
   case PIPE_POLYGON_MODE_LINE:
      return templ->offset_line;
   case PIPE_POLYGON_MODE_FILL:
      return templ->offset_tri;
   default:
      assert(0);
      return false;
   }
}

static inline float
util_get_min_point_size(const struct pipe_rasterizer_state *state)
{
   /* The point size should be clamped to this value at the rasterizer stage.
    */
   return !state->point_quad_rasterization &&
          !state->point_smooth &&
          !state->multisample ? 1.0f : 0.0f;
}

static inline void
util_query_clear_result(union pipe_query_result *result, unsigned type)
{
   switch (type) {
   case PIPE_QUERY_OCCLUSION_PREDICATE:
   case PIPE_QUERY_SO_OVERFLOW_PREDICATE:
   case PIPE_QUERY_GPU_FINISHED:
      result->b = false;
      break;
   case PIPE_QUERY_OCCLUSION_COUNTER:
   case PIPE_QUERY_TIMESTAMP:
   case PIPE_QUERY_TIME_ELAPSED:
   case PIPE_QUERY_PRIMITIVES_GENERATED:
   case PIPE_QUERY_PRIMITIVES_EMITTED:
      result->u64 = 0;
      break;
   case PIPE_QUERY_SO_STATISTICS:
      memset(&result->so_statistics, 0, sizeof(result->so_statistics));
      break;
   case PIPE_QUERY_TIMESTAMP_DISJOINT:
      memset(&result->timestamp_disjoint, 0, sizeof(result->timestamp_disjoint));
      break;
   case PIPE_QUERY_PIPELINE_STATISTICS:
      memset(&result->pipeline_statistics, 0, sizeof(result->pipeline_statistics));
      break;
   default:
      memset(result, 0, sizeof(*result));
   }
}

/** Convert PIPE_TEXTURE_x to TGSI_TEXTURE_x */
static inline enum tgsi_texture_type
util_pipe_tex_to_tgsi_tex(enum pipe_texture_target pipe_tex_target,
                          unsigned nr_samples)
{
   switch (pipe_tex_target) {
   case PIPE_TEXTURE_1D:
      assert(nr_samples <= 1);
      return TGSI_TEXTURE_1D;

   case PIPE_TEXTURE_2D:
      return nr_samples > 1 ? TGSI_TEXTURE_2D_MSAA : TGSI_TEXTURE_2D;

   case PIPE_TEXTURE_RECT:
      assert(nr_samples <= 1);
      return TGSI_TEXTURE_RECT;

   case PIPE_TEXTURE_3D:
      assert(nr_samples <= 1);
      return TGSI_TEXTURE_3D;

   case PIPE_TEXTURE_CUBE:
      assert(nr_samples <= 1);
      return TGSI_TEXTURE_CUBE;

   case PIPE_TEXTURE_1D_ARRAY:
      assert(nr_samples <= 1);
      return TGSI_TEXTURE_1D_ARRAY;

   case PIPE_TEXTURE_2D_ARRAY:
      return nr_samples > 1 ? TGSI_TEXTURE_2D_ARRAY_MSAA :
                              TGSI_TEXTURE_2D_ARRAY;

   case PIPE_TEXTURE_CUBE_ARRAY:
      return TGSI_TEXTURE_CUBE_ARRAY;

   default:
      assert(0 && "unexpected texture target");
      return TGSI_TEXTURE_UNKNOWN;
   }
}


static inline unsigned
util_max_layer(const struct pipe_resource *r, unsigned level)
{
   switch (r->target) {
   case PIPE_TEXTURE_CUBE:
      return 6 - 1;
   case PIPE_TEXTURE_3D:
      return u_minify(r->depth0, level) - 1;
   case PIPE_TEXTURE_1D_ARRAY:
   case PIPE_TEXTURE_2D_ARRAY:
   case PIPE_TEXTURE_CUBE_ARRAY:
      return r->array_size - 1;
   default:
      return 0;
   }
}

#ifdef __cplusplus
}
#endif

#endif /* U_INLINES_H */
