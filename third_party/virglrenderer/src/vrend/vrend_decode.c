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
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <epoxy/gl.h>
#include <fcntl.h>

#include "util/u_memory.h"
#include "pipe/p_defines.h"
#include "pipe/p_state.h"
#include "pipe/p_shader_tokens.h"
#include "virgl_context.h"
#include "virgl_resource.h"
#include "vrend_renderer.h"
#include "vrend_object.h"
#include "tgsi/tgsi_text.h"
#include "vrend_debug.h"
#include "vrend_tweaks.h"
#include "virgl_util.h"

#ifdef ENABLE_VIDEO
#include "vrend_video.h"
#endif

#define XXH_INLINE_ALL
#include "util/xxhash.h"

/* decode side */
#define DECODE_MAX_TOKENS 8000

struct vrend_decode_ctx {
   struct virgl_context base;
   struct vrend_context *grctx;
};

static inline uint32_t get_buf_entry(const uint32_t *buf, uint32_t offset)
{
   return buf[offset];
}

static inline const void *get_buf_ptr(const uint32_t *buf, uint32_t offset)
{
   return &buf[offset];
}

static int vrend_decode_create_shader(struct vrend_context *ctx,
                                      const uint32_t *buf,
                                      uint32_t handle,
                                      uint16_t length)
{
   struct pipe_stream_output_info so_info;
   unsigned i;
   int ret;
   uint32_t shader_offset, req_local_mem = 0;
   unsigned num_tokens, num_so_outputs, offlen;
   const uint8_t *shd_text;
   uint32_t type;

   if (length < VIRGL_OBJ_SHADER_HDR_SIZE(0))
      return EINVAL;

   type = get_buf_entry(buf, VIRGL_OBJ_SHADER_TYPE);

   if (type >= PIPE_SHADER_TYPES)
      return EINVAL;

   num_tokens = get_buf_entry(buf, VIRGL_OBJ_SHADER_NUM_TOKENS);
   offlen = get_buf_entry(buf, VIRGL_OBJ_SHADER_OFFSET);

   if (type == PIPE_SHADER_COMPUTE) {
      req_local_mem = get_buf_entry(buf, VIRGL_OBJ_SHADER_SO_NUM_OUTPUTS);
      num_so_outputs = 0;
   } else {
      num_so_outputs = get_buf_entry(buf, VIRGL_OBJ_SHADER_SO_NUM_OUTPUTS);
      if (length < VIRGL_OBJ_SHADER_HDR_SIZE(num_so_outputs))
         return EINVAL;

      if (num_so_outputs > PIPE_MAX_SO_OUTPUTS)
         return EINVAL;
   }

   shader_offset = 6;
   if (num_so_outputs) {
      so_info.num_outputs = num_so_outputs;
      if (so_info.num_outputs) {
         for (i = 0; i < 4; i++)
            so_info.stride[i] = get_buf_entry(buf, VIRGL_OBJ_SHADER_SO_STRIDE(i));
         for (i = 0; i < so_info.num_outputs; i++) {
            uint32_t tmp = get_buf_entry(buf, VIRGL_OBJ_SHADER_SO_OUTPUT0(i));

            so_info.output[i].register_index = tmp & 0xff;
            so_info.output[i].start_component = (tmp >> 8) & 0x3;
            so_info.output[i].num_components = (tmp >> 10) & 0x7;
            so_info.output[i].output_buffer = (tmp >> 13) & 0x7;
            so_info.output[i].dst_offset = (tmp >> 16) & 0xffff;
            tmp = get_buf_entry(buf, VIRGL_OBJ_SHADER_SO_OUTPUT0_SO(i));
            so_info.output[i].stream = (tmp & 0x3);
            so_info.output[i].need_temp = so_info.output[i].num_components < 4;
         }

         for (i = 0; i < so_info.num_outputs - 1; i++) {
            for (unsigned j = i + 1; j < so_info.num_outputs; j++) {
               so_info.output[j].need_temp |=
                     (so_info.output[i].register_index == so_info.output[j].register_index);
            }
         }
      }
      shader_offset += 4 + (2 * num_so_outputs);
   } else
     memset(&so_info, 0, sizeof(so_info));

   shd_text = get_buf_ptr(buf, shader_offset);
   ret = vrend_create_shader(ctx, handle, &so_info, req_local_mem, (const char *)shd_text, offlen, num_tokens, type, length - shader_offset + 1);

   return ret;
}

static int vrend_decode_create_stream_output_target(struct vrend_context *ctx, const uint32_t *buf,
                                                    uint32_t handle, uint16_t length)
{
   uint32_t res_handle, buffer_size, buffer_offset;

   if (length != VIRGL_OBJ_STREAMOUT_SIZE)
      return EINVAL;

   res_handle = get_buf_entry(buf, VIRGL_OBJ_STREAMOUT_RES_HANDLE);
   buffer_offset = get_buf_entry(buf, VIRGL_OBJ_STREAMOUT_BUFFER_OFFSET);
   buffer_size = get_buf_entry(buf, VIRGL_OBJ_STREAMOUT_BUFFER_SIZE);

   return vrend_create_so_target(ctx, handle, res_handle, buffer_offset,
                                 buffer_size);
}

static int vrend_decode_set_framebuffer_state(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length < 2)
      return EINVAL;

   uint32_t nr_cbufs = get_buf_entry(buf, VIRGL_SET_FRAMEBUFFER_STATE_NR_CBUFS);
   uint32_t zsurf_handle = get_buf_entry(buf, VIRGL_SET_FRAMEBUFFER_STATE_NR_ZSURF_HANDLE);
   uint32_t surf_handle[8];
   uint32_t i;

   if (length != (2u + nr_cbufs))
      return EINVAL;

   if (nr_cbufs > 8)
      return EINVAL;

   for (i = 0; i < nr_cbufs; i++)
      surf_handle[i] = get_buf_entry(buf, VIRGL_SET_FRAMEBUFFER_STATE_CBUF_HANDLE(i));
   vrend_set_framebuffer_state(ctx, nr_cbufs, surf_handle, zsurf_handle);
   return 0;
}

static int vrend_decode_set_framebuffer_state_no_attach(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t width, height;
   uint32_t layers, samples;
   uint32_t tmp;

   if (length != VIRGL_SET_FRAMEBUFFER_STATE_NO_ATTACH_SIZE)
      return EINVAL;

   tmp = get_buf_entry(buf, VIRGL_SET_FRAMEBUFFER_STATE_NO_ATTACH_WIDTH_HEIGHT);
   width = VIRGL_SET_FRAMEBUFFER_STATE_NO_ATTACH_WIDTH(tmp);
   height = VIRGL_SET_FRAMEBUFFER_STATE_NO_ATTACH_HEIGHT(tmp);

   tmp = get_buf_entry(buf, VIRGL_SET_FRAMEBUFFER_STATE_NO_ATTACH_LAYERS_SAMPLES);
   layers = VIRGL_SET_FRAMEBUFFER_STATE_NO_ATTACH_LAYERS(tmp);
   samples = VIRGL_SET_FRAMEBUFFER_STATE_NO_ATTACH_SAMPLES(tmp);

   vrend_set_framebuffer_state_no_attach(ctx, width, height, layers, samples);
   return 0;
}

static int vrend_decode_clear(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   union pipe_color_union color;
   double depth;
   unsigned stencil, buffers;
   int i;

   if (length != VIRGL_OBJ_CLEAR_SIZE)
      return EINVAL;
   buffers = get_buf_entry(buf, VIRGL_OBJ_CLEAR_BUFFERS);
   for (i = 0; i < 4; i++)
      color.ui[i] = get_buf_entry(buf, VIRGL_OBJ_CLEAR_COLOR_0 + i);
   const void *depth_ptr = get_buf_ptr(buf, VIRGL_OBJ_CLEAR_DEPTH_0);
   memcpy(&depth, depth_ptr, sizeof(double));
   stencil = get_buf_entry(buf, VIRGL_OBJ_CLEAR_STENCIL);

   vrend_clear(ctx, buffers, &color, depth, stencil);
   return 0;
}

static int vrend_decode_clear_texture(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_box box;
   uint32_t handle;
   uint32_t level;
   uint32_t arr[4] = {0};

   if (length != VIRGL_CLEAR_TEXTURE_SIZE)
      return EINVAL;

   handle = get_buf_entry(buf, VIRGL_TEXTURE_HANDLE);

   struct vrend_resource *res = vrend_renderer_ctx_res_lookup(ctx, handle);
   if (!res || !res->gl_id) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, handle);
      return EINVAL;
   }

   level = get_buf_entry(buf, VIRGL_TEXTURE_LEVEL);
   box.x = get_buf_entry(buf, VIRGL_TEXTURE_SRC_X);
   box.y = get_buf_entry(buf, VIRGL_TEXTURE_SRC_Y);
   box.z = get_buf_entry(buf, VIRGL_TEXTURE_SRC_Z);
   box.width = get_buf_entry(buf, VIRGL_TEXTURE_SRC_W);
   box.height = get_buf_entry(buf, VIRGL_TEXTURE_SRC_H);
   box.depth = get_buf_entry(buf, VIRGL_TEXTURE_SRC_D);
   arr[0] = get_buf_entry(buf, VIRGL_TEXTURE_ARRAY_A);
   arr[1] = get_buf_entry(buf, VIRGL_TEXTURE_ARRAY_B);
   arr[2] = get_buf_entry(buf, VIRGL_TEXTURE_ARRAY_C);
   arr[3] = get_buf_entry(buf, VIRGL_TEXTURE_ARRAY_D);

   return vrend_clear_texture(ctx, res, level, &box, (void *) &arr);
}

static int vrend_decode_clear_surface(struct vrend_context *ctx,
                                      const uint32_t *buf, uint32_t length) {
   union pipe_color_union color;
   unsigned buffers, dstx, dsty, width, height;
   uint32_t s0, surf_handle;
   bool render_condition_enable;
   int i;

   if (length != VIRGL_CLEAR_SURFACE_SIZE)
      return EINVAL;

   s0 = get_buf_entry(buf, VIRGL_CLEAR_SURFACE_S0);
   render_condition_enable = s0 & 0x1;
   buffers = (s0 >> 1) & 0x7;

   surf_handle = get_buf_entry(buf, VIRGL_CLEAR_SURFACE_HANDLE);

   for (i = 0; i < 4; i++)
      color.ui[i] = get_buf_entry(buf, VIRGL_CLEAR_SURFACE_COLOR_0 + i);

   dstx = get_buf_entry(buf, VIRGL_CLEAR_SURFACE_DST_X);
   dsty = get_buf_entry(buf, VIRGL_CLEAR_SURFACE_DST_Y);
   width = get_buf_entry(buf, VIRGL_CLEAR_SURFACE_WIDTH);
   height = get_buf_entry(buf, VIRGL_CLEAR_SURFACE_HEIGHT);

   vrend_clear_surface(ctx, surf_handle, buffers, &color, dstx, dsty, width,
                       height, render_condition_enable);
   return 0;
}

static int vrend_decode_set_viewport_state(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_viewport_state vps[PIPE_MAX_VIEWPORTS];
   unsigned i, v;
   uint32_t num_viewports, start_slot;
   if (length < 1)
      return EINVAL;

   if ((length - 1) % 6)
      return EINVAL;

   num_viewports = (length - 1) / 6;
   start_slot = get_buf_entry(buf, VIRGL_SET_VIEWPORT_START_SLOT);

   if (num_viewports > PIPE_MAX_VIEWPORTS ||
       start_slot > (PIPE_MAX_VIEWPORTS - num_viewports))
      return EINVAL;

   for (v = 0; v < num_viewports; v++) {
      for (i = 0; i < 3; i++)
         vps[v].scale[i] = uif(get_buf_entry(buf, VIRGL_SET_VIEWPORT_STATE_SCALE_0(v) + i));
      for (i = 0; i < 3; i++)
         vps[v].translate[i] = uif(get_buf_entry(buf, VIRGL_SET_VIEWPORT_STATE_TRANSLATE_0(v) + i));
   }

   vrend_set_viewport_states(ctx, start_slot, num_viewports, vps);
   return 0;
}

static int vrend_decode_set_index_buffer(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1 && length != 3)
      return EINVAL;
   vrend_set_index_buffer(ctx,
                          get_buf_entry(buf, VIRGL_SET_INDEX_BUFFER_HANDLE),
                          (length == 3) ? get_buf_entry(buf, VIRGL_SET_INDEX_BUFFER_INDEX_SIZE) : 0,
                          (length == 3) ? get_buf_entry(buf, VIRGL_SET_INDEX_BUFFER_OFFSET) : 0);
   return 0;
}

static int vrend_decode_set_constant_buffer(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t shader;
   int nc = (length - 2);
   const float *data = NULL;

   if (length < 2)
      return EINVAL;

   shader = get_buf_entry(buf, VIRGL_SET_CONSTANT_BUFFER_SHADER_TYPE);
   /* VIRGL_SET_CONSTANT_BUFFER_INDEX is not used */

   if (shader >= PIPE_SHADER_TYPES)
      return EINVAL;

   if (length > 2)
      data = get_buf_ptr(buf, VIRGL_SET_CONSTANT_BUFFER_DATA_START);

   vrend_set_constants(ctx, shader, nc, data);
   return 0;
}

static int vrend_decode_set_uniform_buffer(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != VIRGL_SET_UNIFORM_BUFFER_SIZE)
      return EINVAL;

   uint32_t shader = get_buf_entry(buf, VIRGL_SET_UNIFORM_BUFFER_SHADER_TYPE);
   uint32_t index = get_buf_entry(buf, VIRGL_SET_UNIFORM_BUFFER_INDEX);
   uint32_t offset = get_buf_entry(buf, VIRGL_SET_UNIFORM_BUFFER_OFFSET);
   uint32_t blength = get_buf_entry(buf, VIRGL_SET_UNIFORM_BUFFER_LENGTH);
   uint32_t handle = get_buf_entry(buf, VIRGL_SET_UNIFORM_BUFFER_RES_HANDLE);

   if (shader >= PIPE_SHADER_TYPES)
      return EINVAL;

   if (index >= PIPE_MAX_CONSTANT_BUFFERS)
      return EINVAL;

   vrend_set_uniform_buffer(ctx, shader, index, offset, blength, handle);
   return 0;
}

static int vrend_decode_set_vertex_buffers(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   int num_vbo;
   int i;

   /* must be a multiple of 3 */
   if (length && (length % 3))
      return EINVAL;

   num_vbo = (length / 3);
   if (num_vbo > PIPE_MAX_ATTRIBS)
      return EINVAL;

   for (i = 0; i < num_vbo; i++) {
      vrend_set_single_vbo(ctx, i,
                           get_buf_entry(buf, VIRGL_SET_VERTEX_BUFFER_STRIDE(i)),
                           get_buf_entry(buf, VIRGL_SET_VERTEX_BUFFER_OFFSET(i)),
                           get_buf_entry(buf, VIRGL_SET_VERTEX_BUFFER_HANDLE(i)));
   }
   vrend_set_num_vbo(ctx, num_vbo);
   return 0;
}

static int vrend_decode_set_sampler_views(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t num_samps;
   uint32_t i;
   uint32_t shader_type;
   uint32_t start_slot;

   if (length < 2)
      return EINVAL;
   num_samps = length - 2;
   shader_type = get_buf_entry(buf, VIRGL_SET_SAMPLER_VIEWS_SHADER_TYPE);
   start_slot = get_buf_entry(buf, VIRGL_SET_SAMPLER_VIEWS_START_SLOT);

   if (shader_type >= PIPE_SHADER_TYPES)
      return EINVAL;

   if (num_samps > PIPE_MAX_SHADER_SAMPLER_VIEWS ||
       start_slot > (PIPE_MAX_SHADER_SAMPLER_VIEWS - num_samps))
      return EINVAL;

   for (i = 0; i < num_samps; i++) {
      uint32_t handle = get_buf_entry(buf, VIRGL_SET_SAMPLER_VIEWS_V0_HANDLE + i);
      vrend_set_single_sampler_view(ctx, shader_type, i + start_slot, handle);
   }
   vrend_set_num_sampler_views(ctx, shader_type, start_slot, num_samps);
   return 0;
}

static void vrend_decode_transfer_common(const uint32_t *buf,
                                         uint32_t *dst_handle,
                                         struct vrend_transfer_info *info)
{
   *dst_handle = get_buf_entry(buf, VIRGL_RESOURCE_IW_RES_HANDLE);

   info->level = get_buf_entry(buf, VIRGL_RESOURCE_IW_LEVEL);
   info->stride = get_buf_entry(buf, VIRGL_RESOURCE_IW_STRIDE);
   info->layer_stride = get_buf_entry(buf, VIRGL_RESOURCE_IW_LAYER_STRIDE);
   info->box->x = get_buf_entry(buf, VIRGL_RESOURCE_IW_X);
   info->box->y = get_buf_entry(buf, VIRGL_RESOURCE_IW_Y);
   info->box->z = get_buf_entry(buf, VIRGL_RESOURCE_IW_Z);
   info->box->width = get_buf_entry(buf, VIRGL_RESOURCE_IW_W);
   info->box->height = get_buf_entry(buf, VIRGL_RESOURCE_IW_H);
   info->box->depth = get_buf_entry(buf, VIRGL_RESOURCE_IW_D);
}

static int vrend_decode_resource_inline_write(struct vrend_context *ctx, const uint32_t *buf,
                                              uint32_t length)
{
   struct pipe_box box;
   uint32_t dst_handle;
   struct vrend_transfer_info info;
   uint32_t data_len;
   struct iovec dataiovec;
   const void *data;

   if (length < 12)
      return EINVAL;

   memset(&info, 0, sizeof(info));
   info.box = &box;
   vrend_decode_transfer_common(buf, &dst_handle, &info);
   data_len = (length - 11) * 4;
   data = get_buf_ptr(buf, VIRGL_RESOURCE_IW_DATA_START);

   info.offset = 0;

   dataiovec.iov_base = (void*)data;
   dataiovec.iov_len = data_len;

   info.iovec = &dataiovec;
   info.iovec_cnt = 1;
   return vrend_transfer_inline_write(ctx, dst_handle, &info);
}

static int vrend_decode_draw_vbo(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_draw_info info;
   uint32_t cso;
   uint32_t handle = 0, indirect_draw_count_handle = 0;
   if (length != VIRGL_DRAW_VBO_SIZE && length != VIRGL_DRAW_VBO_SIZE_TESS &&
       length != VIRGL_DRAW_VBO_SIZE_INDIRECT)
      return EINVAL;
   memset(&info, 0, sizeof(struct pipe_draw_info));

   info.start = get_buf_entry(buf, VIRGL_DRAW_VBO_START);
   info.count = get_buf_entry(buf, VIRGL_DRAW_VBO_COUNT);
   info.mode = get_buf_entry(buf, VIRGL_DRAW_VBO_MODE);
   info.indexed = !!get_buf_entry(buf, VIRGL_DRAW_VBO_INDEXED);
   info.instance_count = get_buf_entry(buf, VIRGL_DRAW_VBO_INSTANCE_COUNT);
   info.index_bias = get_buf_entry(buf, VIRGL_DRAW_VBO_INDEX_BIAS);
   info.start_instance = get_buf_entry(buf, VIRGL_DRAW_VBO_START_INSTANCE);
   info.primitive_restart = !!get_buf_entry(buf, VIRGL_DRAW_VBO_PRIMITIVE_RESTART);
   info.restart_index = get_buf_entry(buf, VIRGL_DRAW_VBO_RESTART_INDEX);
   info.min_index = get_buf_entry(buf, VIRGL_DRAW_VBO_MIN_INDEX);
   info.max_index = get_buf_entry(buf, VIRGL_DRAW_VBO_MAX_INDEX);

   if (length >= VIRGL_DRAW_VBO_SIZE_TESS) {
      info.vertices_per_patch = get_buf_entry(buf, VIRGL_DRAW_VBO_VERTICES_PER_PATCH);
      info.drawid = get_buf_entry(buf, VIRGL_DRAW_VBO_DRAWID);
   }

   if (length == VIRGL_DRAW_VBO_SIZE_INDIRECT) {
      handle = get_buf_entry(buf, VIRGL_DRAW_VBO_INDIRECT_HANDLE);
      info.indirect.offset = get_buf_entry(buf, VIRGL_DRAW_VBO_INDIRECT_OFFSET);
      info.indirect.stride = get_buf_entry(buf, VIRGL_DRAW_VBO_INDIRECT_STRIDE);
      info.indirect.draw_count = get_buf_entry(buf, VIRGL_DRAW_VBO_INDIRECT_DRAW_COUNT);
      info.indirect.indirect_draw_count_offset = get_buf_entry(buf, VIRGL_DRAW_VBO_INDIRECT_DRAW_COUNT_OFFSET);
      indirect_draw_count_handle = get_buf_entry(buf, VIRGL_DRAW_VBO_INDIRECT_DRAW_COUNT_HANDLE);
   }

   cso = get_buf_entry(buf, VIRGL_DRAW_VBO_COUNT_FROM_SO);

   return vrend_draw_vbo(ctx, &info, cso, handle, indirect_draw_count_handle);
}

static int vrend_decode_create_blend(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   struct pipe_blend_state *blend_state;
   uint32_t tmp;
   int i;

   if (length != VIRGL_OBJ_BLEND_SIZE) {
      return EINVAL;
   }

   blend_state = CALLOC_STRUCT(pipe_blend_state);
   if (!blend_state)
      return ENOMEM;

   tmp = get_buf_entry(buf, VIRGL_OBJ_BLEND_S0);
   blend_state->independent_blend_enable = (tmp & 1);
   blend_state->logicop_enable = (tmp >> 1) & 0x1;
   blend_state->dither = (tmp >> 2) & 0x1;
   blend_state->alpha_to_coverage = (tmp >> 3) & 0x1;
   blend_state->alpha_to_one = (tmp >> 4) & 0x1;

   tmp = get_buf_entry(buf, VIRGL_OBJ_BLEND_S1);
   blend_state->logicop_func = tmp & 0xf;

   for (i = 0; i < PIPE_MAX_COLOR_BUFS; i++) {
      tmp = get_buf_entry(buf, VIRGL_OBJ_BLEND_S2(i));
      blend_state->rt[i].blend_enable = tmp & 0x1;
      blend_state->rt[i].rgb_func = (tmp >> 1) & 0x7;
      blend_state->rt[i].rgb_src_factor = (tmp >> 4) & 0x1f;
      blend_state->rt[i].rgb_dst_factor = (tmp >> 9) & 0x1f;
      blend_state->rt[i].alpha_func = (tmp >> 14) & 0x7;
      blend_state->rt[i].alpha_src_factor = (tmp >> 17) & 0x1f;
      blend_state->rt[i].alpha_dst_factor = (tmp >> 22) & 0x1f;
      blend_state->rt[i].colormask = (tmp >> 27) & 0xf;
   }

   tmp = vrend_renderer_object_insert(ctx, blend_state, handle,
                                      VIRGL_OBJECT_BLEND);
   if (tmp == 0) {
      FREE(blend_state);
      return ENOMEM;
   }
   return 0;
}

static int vrend_decode_create_dsa(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   int i;
   struct pipe_depth_stencil_alpha_state dsa_state;
   uint32_t tmp;

   if (length != VIRGL_OBJ_DSA_SIZE)
      return EINVAL;

   memset(&dsa_state, 0, sizeof(dsa_state));
   tmp = get_buf_entry(buf, VIRGL_OBJ_DSA_S0);
   dsa_state.depth.enabled = tmp & 0x1;
   dsa_state.depth.writemask = (tmp >> 1) & 0x1;
   dsa_state.depth.func = (tmp >> 2) & 0x7;

   dsa_state.alpha.enabled = (tmp >> 8) & 0x1;
   dsa_state.alpha.func = (tmp >> 9) & 0x7;

   for (i = 0; i < 2; i++) {
      tmp = get_buf_entry(buf, VIRGL_OBJ_DSA_S1 + i);
      dsa_state.stencil[i].enabled = tmp & 0x1;
      dsa_state.stencil[i].func = (tmp >> 1) & 0x7;
      dsa_state.stencil[i].fail_op = (tmp >> 4) & 0x7;
      dsa_state.stencil[i].zpass_op = (tmp >> 7) & 0x7;
      dsa_state.stencil[i].zfail_op = (tmp >> 10) & 0x7;
      dsa_state.stencil[i].valuemask = (tmp >> 13) & 0xff;
      dsa_state.stencil[i].writemask = (tmp >> 21) & 0xff;
   }

   tmp = get_buf_entry(buf, VIRGL_OBJ_DSA_ALPHA_REF);
   dsa_state.alpha.ref_value = uif(tmp);

   return vrend_create_dsa(ctx, handle, &dsa_state);
}

static int vrend_decode_create_rasterizer(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   struct pipe_rasterizer_state *rs_state;
   uint32_t tmp;

   if (length != VIRGL_OBJ_RS_SIZE)
      return EINVAL;

   rs_state = CALLOC_STRUCT(pipe_rasterizer_state);
   if (!rs_state)
      return ENOMEM;

   tmp = get_buf_entry(buf, VIRGL_OBJ_RS_S0);
#define ebit(name, bit) rs_state->name = (tmp >> bit) & 0x1
#define emask(name, bit, mask) rs_state->name = (tmp >> bit) & mask

   ebit(flatshade, 0);
   ebit(depth_clip, 1);
   ebit(clip_halfz, 2);
   ebit(rasterizer_discard, 3);
   ebit(flatshade_first, 4);
   ebit(light_twoside, 5);
   ebit(sprite_coord_mode, 6);
   ebit(point_quad_rasterization, 7);
   emask(cull_face, 8, 0x3);
   emask(fill_front, 10, 0x3);
   emask(fill_back, 12, 0x3);
   ebit(scissor, 14);
   ebit(front_ccw, 15);
   ebit(clamp_vertex_color, 16);
   ebit(clamp_fragment_color, 17);
   ebit(offset_line, 18);
   ebit(offset_point, 19);
   ebit(offset_tri, 20);
   ebit(poly_smooth, 21);
   ebit(poly_stipple_enable, 22);
   ebit(point_smooth, 23);
   ebit(point_size_per_vertex, 24);
   ebit(multisample, 25);
   ebit(line_smooth, 26);
   ebit(line_stipple_enable, 27);
   ebit(line_last_pixel, 28);
   ebit(half_pixel_center, 29);
   ebit(bottom_edge_rule, 30);
   ebit(force_persample_interp, 31);
   rs_state->point_size = uif(get_buf_entry(buf, VIRGL_OBJ_RS_POINT_SIZE));
   rs_state->sprite_coord_enable = get_buf_entry(buf, VIRGL_OBJ_RS_SPRITE_COORD_ENABLE);
   tmp = get_buf_entry(buf, VIRGL_OBJ_RS_S3);
   emask(line_stipple_pattern, 0, 0xffff);
   emask(line_stipple_factor, 16, 0xff);
   emask(clip_plane_enable, 24, 0xff);

   rs_state->line_width = uif(get_buf_entry(buf, VIRGL_OBJ_RS_LINE_WIDTH));
   rs_state->offset_units = uif(get_buf_entry(buf, VIRGL_OBJ_RS_OFFSET_UNITS));
   rs_state->offset_scale = uif(get_buf_entry(buf, VIRGL_OBJ_RS_OFFSET_SCALE));
   rs_state->offset_clamp = uif(get_buf_entry(buf, VIRGL_OBJ_RS_OFFSET_CLAMP));

   tmp = vrend_renderer_object_insert(ctx, rs_state, handle,
                                      VIRGL_OBJECT_RASTERIZER);
   if (tmp == 0) {
      FREE(rs_state);
      return ENOMEM;
   }
   return 0;
}

static int vrend_decode_create_surface_common(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint32_t sample_count)
{
   uint32_t res_handle = get_buf_entry(buf, VIRGL_OBJ_SURFACE_RES_HANDLE);

   struct vrend_resource *res = vrend_renderer_ctx_res_lookup(ctx, res_handle);
   if (!res || !res->gl_id) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
      return EINVAL;
   }

   enum virgl_formats format = get_buf_entry(buf, VIRGL_OBJ_SURFACE_FORMAT);

   if (format >= PIPE_FORMAT_COUNT) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_FORMAT, format);
      return EINVAL;
   }

   /* decide later if these are texture or buffer */
   uint32_t val0 = get_buf_entry(buf, VIRGL_OBJ_SURFACE_BUFFER_FIRST_ELEMENT);
   uint32_t val1 = get_buf_entry(buf, VIRGL_OBJ_SURFACE_BUFFER_LAST_ELEMENT);

   int first_layer = val1 & 0xffff;
   int last_layer = (val1 >> 16) & 0xffff;

   int num_layers = last_layer - first_layer + 1;

   if (num_layers <= 0) {
      virgl_error("%s: Invalid number of layers (%d) requested\n", __func__, num_layers);
      return EINVAL;
   }

   return vrend_create_surface(ctx, handle, res, format, val0, first_layer, last_layer, sample_count);
}

static int vrend_decode_create_surface(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   if (length != VIRGL_OBJ_SURFACE_SIZE)
      return EINVAL;

   return vrend_decode_create_surface_common(ctx, buf, handle, 0);
}

static int vrend_decode_create_msaa_surface(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   if (length != VIRGL_OBJ_MSAA_SURFACE_SIZE)
      return EINVAL;

   uint32_t sample_count = get_buf_entry(buf, VIRGL_OBJ_SURFACE_SAMPLE_COUNT);
   return vrend_decode_create_surface_common(ctx, buf, handle, sample_count);
}

static int vrend_decode_create_sampler_view(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   if (length != VIRGL_OBJ_SAMPLER_VIEW_SIZE)
      return EINVAL;

   uint32_t res_handle = get_buf_entry(buf, VIRGL_OBJ_SAMPLER_VIEW_RES_HANDLE);
   uint32_t format_data = get_buf_entry(buf, VIRGL_OBJ_SAMPLER_VIEW_FORMAT);

   enum virgl_formats format = format_data & 0xffffff;
   if (!format || format >= VIRGL_FORMAT_MAX) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_FORMAT, format_data);
      return EINVAL;
   }

   enum pipe_texture_target pipe_target = (format_data >> 24) & 0xff;
   if (pipe_target >= PIPE_MAX_TEXTURE_TYPES) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SAMPLER_VIEW_TARGET,
                                 format_data);
      return EINVAL;
   }

   struct vrend_resource *res = vrend_renderer_ctx_res_lookup(ctx, res_handle);
   if (!res || !res->gl_id) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
      return EINVAL;
   }

   uint32_t val0 = get_buf_entry(buf, VIRGL_OBJ_SAMPLER_VIEW_BUFFER_FIRST_ELEMENT);
   uint32_t val1 = get_buf_entry(buf, VIRGL_OBJ_SAMPLER_VIEW_BUFFER_LAST_ELEMENT);

   uint32_t swizzle_packed = get_buf_entry(buf, VIRGL_OBJ_SAMPLER_VIEW_SWIZZLE);
   return vrend_create_sampler_view(ctx, handle, res, format, pipe_target, val0, val1, swizzle_packed);
}

static int vrend_decode_create_sampler_state(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   struct pipe_sampler_state state;
   int i;
   uint32_t tmp;

   if (length != VIRGL_OBJ_SAMPLER_STATE_SIZE)
      return EINVAL;
   tmp = get_buf_entry(buf, VIRGL_OBJ_SAMPLER_STATE_S0);
   state.wrap_s = tmp & 0x7;
   state.wrap_t = (tmp >> 3) & 0x7;
   state.wrap_r = (tmp >> 6) & 0x7;
   state.min_img_filter = (tmp >> 9) & 0x1;
   state.min_mip_filter = (tmp >> 11) & 0x3;
   state.mag_img_filter = (tmp >> 13) & 0x1;
   state.compare_mode = (tmp >> 15) & 0x1;
   state.compare_func = (tmp >> 16) & 0x7;
   state.seamless_cube_map = (tmp >> 19) & 0x1;
   state.max_anisotropy = (tmp >> 20) & 0x1f;

   state.lod_bias = uif(get_buf_entry(buf, VIRGL_OBJ_SAMPLER_STATE_LOD_BIAS));
   state.min_lod = uif(get_buf_entry(buf, VIRGL_OBJ_SAMPLER_STATE_MIN_LOD));
   state.max_lod = uif(get_buf_entry(buf, VIRGL_OBJ_SAMPLER_STATE_MAX_LOD));

   for (i = 0; i < 4; i++)
      state.border_color.ui[i] = get_buf_entry(buf, VIRGL_OBJ_SAMPLER_STATE_BORDER_COLOR(i));

   if (state.min_mip_filter != PIPE_TEX_MIPFILTER_NONE &&
       state.min_mip_filter != PIPE_TEX_MIPFILTER_LINEAR &&
       state.min_mip_filter != PIPE_TEX_MIPFILTER_NEAREST)
     return EINVAL;

   return vrend_create_sampler_state(ctx, handle, &state);
}

static int vrend_decode_create_ve(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   struct pipe_vertex_element *ve = NULL;
   int num_elements;
   int i;
   int ret;

   if (length < 1)
      return EINVAL;

   if ((length - 1) % 4)
      return EINVAL;

   num_elements = (length - 1) / 4;

   if (num_elements > PIPE_MAX_ATTRIBS)
      return EINVAL;

   if (num_elements) {
      ve = calloc(num_elements, sizeof(struct pipe_vertex_element));

      if (!ve)
         return ENOMEM;

      for (i = 0; i < num_elements; i++) {
         ve[i].src_offset = get_buf_entry(buf, VIRGL_OBJ_VERTEX_ELEMENTS_V0_SRC_OFFSET(i));
         ve[i].instance_divisor = get_buf_entry(buf, VIRGL_OBJ_VERTEX_ELEMENTS_V0_INSTANCE_DIVISOR(i));
         ve[i].vertex_buffer_index = get_buf_entry(buf, VIRGL_OBJ_VERTEX_ELEMENTS_V0_VERTEX_BUFFER_INDEX(i));

         if (ve[i].vertex_buffer_index >= PIPE_MAX_ATTRIBS) {
            FREE(ve);
            return EINVAL;
         }

         ve[i].src_format = get_buf_entry(buf, VIRGL_OBJ_VERTEX_ELEMENTS_V0_SRC_FORMAT(i));
      }
   }

   ret = vrend_create_vertex_elements_state(ctx, handle, num_elements, ve);

   FREE(ve);
   return ret;
}

static int vrend_decode_create_query(struct vrend_context *ctx, const uint32_t *buf, uint32_t handle, uint16_t length)
{
   uint32_t query_type;
   uint32_t query_index;
   uint32_t res_handle;
   uint32_t offset;
   uint32_t tmp;

   if (length != VIRGL_OBJ_QUERY_SIZE)
      return EINVAL;

   tmp = get_buf_entry(buf, VIRGL_OBJ_QUERY_TYPE_INDEX);
   query_type = VIRGL_OBJ_QUERY_TYPE(tmp);
   query_index = (tmp >> 16) & 0xffff;

   offset = get_buf_entry(buf, VIRGL_OBJ_QUERY_OFFSET);
   res_handle = get_buf_entry(buf, VIRGL_OBJ_QUERY_RES_HANDLE);

   return vrend_create_query(ctx, handle, query_type, query_index, res_handle, offset);
}

static int vrend_decode_create_object(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length < 1)
      return EINVAL;

   uint32_t header = get_buf_entry(buf, VIRGL_OBJ_CREATE_HEADER);
   uint32_t handle = get_buf_entry(buf, VIRGL_OBJ_CREATE_HANDLE);
   uint8_t obj_type = (header >> 8) & 0xff;
   int ret = 0;

   if (handle == 0)
      return EINVAL;

   VREND_DEBUG(dbg_object, ctx,"  CREATE %-18s handle:0x%x len:%d\n",
               vrend_get_object_type_name(obj_type), handle, length);

   TRACE_SCOPE(vrend_get_object_type_name(obj_type));

   switch (obj_type){
   case VIRGL_OBJECT_BLEND:
      ret = vrend_decode_create_blend(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_DSA:
      ret = vrend_decode_create_dsa(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_RASTERIZER:
      ret = vrend_decode_create_rasterizer(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_SHADER:
      ret = vrend_decode_create_shader(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_VERTEX_ELEMENTS:
      ret = vrend_decode_create_ve(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_SURFACE:
      ret = vrend_decode_create_surface(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_SAMPLER_VIEW:
      ret = vrend_decode_create_sampler_view(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_SAMPLER_STATE:
      ret = vrend_decode_create_sampler_state(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_QUERY:
      ret = vrend_decode_create_query(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_STREAMOUT_TARGET:
      ret = vrend_decode_create_stream_output_target(ctx, buf, handle, length);
      break;
   case VIRGL_OBJECT_MSAA_SURFACE:
      ret = vrend_decode_create_msaa_surface(ctx, buf, handle, length);
      break;
   default:
      return EINVAL;
   }

   return ret;
}

static int vrend_decode_bind_object(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1)
      return EINVAL;

   uint32_t header = get_buf_entry(buf, VIRGL_OBJ_BIND_HEADER);
   uint32_t handle = get_buf_entry(buf, VIRGL_OBJ_BIND_HANDLE);
   uint8_t obj_type = (header >> 8) & 0xff;

   VREND_DEBUG(dbg_object, ctx,
               "  BIND %-20s handle:0x%x len:%d\n",
               vrend_get_object_type_name(obj_type), handle, length);

   switch (obj_type) {
   case VIRGL_OBJECT_BLEND:
      vrend_object_bind_blend(ctx, handle);
      break;
   case VIRGL_OBJECT_DSA:
      vrend_object_bind_dsa(ctx, handle);
      break;
   case VIRGL_OBJECT_RASTERIZER:
      vrend_object_bind_rasterizer(ctx, handle);
      break;
   case VIRGL_OBJECT_VERTEX_ELEMENTS:
      vrend_bind_vertex_elements_state(ctx, handle);
      break;
   default:
      return EINVAL;
   }

   return 0;
}

static int vrend_decode_destroy_object(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1)
      return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_OBJ_DESTROY_HANDLE);

   VREND_DEBUG_EXT(dbg_object, ctx,
               uint32_t obj = (get_buf_entry(buf, 0) >> 8) & 0xFF;
               virgl_debug("  DESTROY %-17s handle:0x%x\n",
                       vrend_get_object_type_name(obj), handle));

   vrend_renderer_object_destroy(ctx, handle);
   return 0;
}

static int vrend_decode_set_stencil_ref(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != VIRGL_SET_STENCIL_REF_SIZE)
      return EINVAL;

   struct pipe_stencil_ref ref;
   uint32_t val = get_buf_entry(buf, VIRGL_SET_STENCIL_REF);

   ref.ref_value[0] = val & 0xff;
   ref.ref_value[1] = (val >> 8) & 0xff;
   vrend_set_stencil_ref(ctx, &ref);
   return 0;
}

static int vrend_decode_set_blend_color(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_blend_color color;
   int i;

   if (length != VIRGL_SET_BLEND_COLOR_SIZE)
      return EINVAL;

   for (i = 0; i < 4; i++)
      color.color[i] = uif(get_buf_entry(buf, VIRGL_SET_BLEND_COLOR(i)));

   vrend_set_blend_color(ctx, &color);
   return 0;
}

static int vrend_decode_set_scissor_state(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length < 1)
      return EINVAL;

   if ((length - 1) % 2)
      return EINVAL;

   const int32_t num_scissor = (length - 1) / 2;
   if (num_scissor > PIPE_MAX_VIEWPORTS)
      return EINVAL;

   const uint32_t start_slot = get_buf_entry(buf, VIRGL_SET_SCISSOR_START_SLOT);

   if (start_slot >= PIPE_MAX_VIEWPORTS ||
       start_slot + num_scissor > PIPE_MAX_VIEWPORTS) {
      vrend_report_buffer_error(ctx, 0);
      return EINVAL;
   }

   struct pipe_scissor_state ss[PIPE_MAX_VIEWPORTS];
   for (int s = 0; s < num_scissor; s++) {
      const uint32_t minxy = get_buf_entry(buf, VIRGL_SET_SCISSOR_MINX_MINY(s));
      ss[s].minx = minxy & 0xffff;
      ss[s].miny = (minxy >> 16) & 0xffff;

      const uint32_t maxxy = get_buf_entry(buf, VIRGL_SET_SCISSOR_MAXX_MAXY(s));
      ss[s].maxx = maxxy & 0xffff;
      ss[s].maxy = (maxxy >> 16) & 0xffff;
   }

   vrend_set_scissor_state(ctx, start_slot, num_scissor, ss);
   return 0;
}

static int vrend_decode_set_polygon_stipple(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_poly_stipple ps;
   int i;

   if (length != VIRGL_POLYGON_STIPPLE_SIZE)
      return EINVAL;

   for (i = 0; i < 32; i++)
      ps.stipple[i] = get_buf_entry(buf, VIRGL_POLYGON_STIPPLE_P0 + i);

   vrend_set_polygon_stipple(ctx, &ps);
   return 0;
}

static int vrend_decode_set_clip_state(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_clip_state clip;
   int i, j;

   if (length != VIRGL_SET_CLIP_STATE_SIZE)
      return EINVAL;

   for (i = 0; i < 8; i++)
      for (j = 0; j < 4; j++)
         clip.ucp[i][j] = uif(get_buf_entry(buf, VIRGL_SET_CLIP_STATE_C0 + (i * 4) + j));
   vrend_set_clip_state(ctx, &clip);
   return 0;
}

static int vrend_decode_set_sample_mask(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   unsigned mask;

   if (length != VIRGL_SET_SAMPLE_MASK_SIZE)
      return EINVAL;
   mask = get_buf_entry(buf, VIRGL_SET_SAMPLE_MASK_MASK);
   vrend_set_sample_mask(ctx, mask);
   return 0;
}

static int vrend_decode_set_min_samples(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   unsigned min_samples;

   if (length != VIRGL_SET_MIN_SAMPLES_SIZE)
      return EINVAL;
   min_samples = get_buf_entry(buf, VIRGL_SET_MIN_SAMPLES_MASK);
   vrend_set_min_samples(ctx, min_samples);
   return 0;
}

static int vrend_decode_resource_copy_region(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_box box;
   uint32_t dst_handle, src_handle;
   uint32_t dst_level, dstx, dsty, dstz;
   uint32_t src_level;

   if (length != VIRGL_CMD_RESOURCE_COPY_REGION_SIZE)
      return EINVAL;

   dst_handle = get_buf_entry(buf, VIRGL_CMD_RCR_DST_RES_HANDLE);
   dst_level = get_buf_entry(buf, VIRGL_CMD_RCR_DST_LEVEL);
   dstx = get_buf_entry(buf, VIRGL_CMD_RCR_DST_X);
   dsty = get_buf_entry(buf, VIRGL_CMD_RCR_DST_Y);
   dstz = get_buf_entry(buf, VIRGL_CMD_RCR_DST_Z);
   src_handle = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_RES_HANDLE);
   src_level = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_LEVEL);
   box.x = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_X);
   box.y = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_Y);
   box.z = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_Z);
   box.width = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_W);
   box.height = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_H);
   box.depth = get_buf_entry(buf, VIRGL_CMD_RCR_SRC_D);

   vrend_renderer_resource_copy_region(ctx, dst_handle,
                                       dst_level, dstx, dsty, dstz,
                                       src_handle, src_level,
                                       &box);
   return 0;
}


static int vrend_decode_blit(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_blit_info info;
   uint32_t dst_handle, src_handle, temp;

   if (length != VIRGL_CMD_BLIT_SIZE)
      return EINVAL;
   temp = get_buf_entry(buf, VIRGL_CMD_BLIT_S0);
   info.mask = temp & 0xff;
   info.filter = (temp >> 8) & 0x3;
   info.scissor_enable = (temp >> 10) & 0x1;
   info.render_condition_enable = (temp >> 11) & 0x1;
   info.alpha_blend = (temp >> 12) & 0x1;
   temp = get_buf_entry(buf, VIRGL_CMD_BLIT_SCISSOR_MINX_MINY);
   info.scissor.minx = temp & 0xffff;
   info.scissor.miny = (temp >> 16) & 0xffff;
   temp = get_buf_entry(buf, VIRGL_CMD_BLIT_SCISSOR_MAXX_MAXY);
   info.scissor.maxx = temp & 0xffff;
   info.scissor.maxy = (temp >> 16) & 0xffff;
   dst_handle = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_RES_HANDLE);
   info.dst.level = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_LEVEL);
   info.dst.format = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_FORMAT);
   info.dst.box.x = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_X);
   info.dst.box.y = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_Y);
   info.dst.box.z = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_Z);
   info.dst.box.width = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_W);
   info.dst.box.height = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_H);
   info.dst.box.depth = get_buf_entry(buf, VIRGL_CMD_BLIT_DST_D);

   src_handle = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_RES_HANDLE);
   info.src.level = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_LEVEL);
   info.src.format = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_FORMAT);
   info.src.box.x = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_X);
   info.src.box.y = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_Y);
   info.src.box.z = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_Z);
   info.src.box.width = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_W);
   info.src.box.height = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_H);
   info.src.box.depth = get_buf_entry(buf, VIRGL_CMD_BLIT_SRC_D);

   vrend_renderer_blit(ctx, dst_handle, src_handle, &info);
   return 0;
}

static int vrend_decode_bind_sampler_states(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   const uint32_t *handles = NULL;
   if (length < 2)
      return EINVAL;

   uint32_t shader_type = get_buf_entry(buf, VIRGL_BIND_SAMPLER_STATES_SHADER_TYPE);
   uint32_t start_slot = get_buf_entry(buf, VIRGL_BIND_SAMPLER_STATES_START_SLOT);
   uint32_t num_states = length - 2;

   if (shader_type >= PIPE_SHADER_TYPES)
      return EINVAL;

   if (num_states > 0)
      handles = get_buf_ptr(buf, VIRGL_BIND_SAMPLER_STATES_S0_HANDLE);

   vrend_bind_sampler_states(ctx, shader_type, start_slot, num_states, handles);
   return 0;
}

static int vrend_decode_begin_query(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1)
      return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_QUERY_BEGIN_HANDLE);

   return vrend_begin_query(ctx, handle);
}

static int vrend_decode_end_query(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1)
      return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_QUERY_END_HANDLE);

   return vrend_end_query(ctx, handle);
}

static int vrend_decode_get_query_result(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 2)
      return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_QUERY_RESULT_HANDLE);
   uint32_t wait = get_buf_entry(buf, VIRGL_QUERY_RESULT_WAIT);

   return vrend_get_query_result(ctx, handle, wait);
}

static int vrend_decode_get_query_result_qbo(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != VIRGL_QUERY_RESULT_QBO_SIZE)
      return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_QUERY_RESULT_QBO_HANDLE);
   uint32_t qbo_handle = get_buf_entry(buf, VIRGL_QUERY_RESULT_QBO_QBO_HANDLE);
   uint32_t wait = get_buf_entry(buf, VIRGL_QUERY_RESULT_QBO_WAIT);
   uint32_t result_type = get_buf_entry(buf, VIRGL_QUERY_RESULT_QBO_RESULT_TYPE);
   uint32_t offset = get_buf_entry(buf, VIRGL_QUERY_RESULT_QBO_OFFSET);
   int32_t index = get_buf_entry(buf, VIRGL_QUERY_RESULT_QBO_INDEX);

   return vrend_get_query_result_qbo(ctx, handle, qbo_handle, wait, result_type, offset, index);
}

static int vrend_decode_set_render_condition(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != VIRGL_RENDER_CONDITION_SIZE)
      return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_RENDER_CONDITION_HANDLE);
   bool condition = get_buf_entry(buf, VIRGL_RENDER_CONDITION_CONDITION) & 1;
   uint32_t mode = get_buf_entry(buf, VIRGL_RENDER_CONDITION_MODE);

   vrend_render_condition(ctx, handle, condition, mode);
   return 0;
}

static int vrend_decode_set_sub_ctx(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1)
      return EINVAL;

   uint32_t ctx_sub_id = get_buf_entry(buf, 1);

   vrend_renderer_set_sub_ctx(ctx, ctx_sub_id);
   return 0;
}

static int vrend_decode_create_sub_ctx(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1)
      return EINVAL;

   uint32_t ctx_sub_id = get_buf_entry(buf, 1);

   vrend_renderer_create_sub_ctx(ctx, ctx_sub_id);
   return 0;
}

static int vrend_decode_destroy_sub_ctx(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != 1)
      return EINVAL;

   uint32_t ctx_sub_id = get_buf_entry(buf, 1);

   vrend_renderer_destroy_sub_ctx(ctx, ctx_sub_id);
   return 0;
}

static int vrend_decode_link_shader(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != VIRGL_LINK_SHADER_SIZE)
      return EINVAL;

   uint32_t handles[PIPE_SHADER_TYPES];
   handles[PIPE_SHADER_VERTEX] = get_buf_entry(buf, VIRGL_LINK_SHADER_VERTEX_HANDLE);
   handles[PIPE_SHADER_FRAGMENT] = get_buf_entry(buf, VIRGL_LINK_SHADER_FRAGMENT_HANDLE);
   handles[PIPE_SHADER_GEOMETRY] = get_buf_entry(buf, VIRGL_LINK_SHADER_GEOMETRY_HANDLE);
   handles[PIPE_SHADER_TESS_CTRL] = get_buf_entry(buf, VIRGL_LINK_SHADER_TESS_CTRL_HANDLE);
   handles[PIPE_SHADER_TESS_EVAL] = get_buf_entry(buf, VIRGL_LINK_SHADER_TESS_EVAL_HANDLE);
   handles[PIPE_SHADER_COMPUTE] = get_buf_entry(buf, VIRGL_LINK_SHADER_COMPUTE_HANDLE);

   vrend_link_program_hook(ctx, handles);
   return 0;
}

static int vrend_decode_bind_shader(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t handle, type;
   if (length != VIRGL_BIND_SHADER_SIZE)
      return EINVAL;

   handle = get_buf_entry(buf, VIRGL_BIND_SHADER_HANDLE);
   type = get_buf_entry(buf, VIRGL_BIND_SHADER_TYPE);

   vrend_bind_shader(ctx, handle, type);
   return 0;
}

static int vrend_decode_set_tess_state(struct vrend_context *ctx,
                                       const uint32_t *buf, uint32_t length)
{
   float tess_factors[6];
   int i;

   if (length != VIRGL_TESS_STATE_SIZE)
      return EINVAL;

   for (i = 0; i < 6; i++) {
      tess_factors[i] = uif(get_buf_entry(buf, i + 1));
   }
   vrend_set_tess_state(ctx, tess_factors);
   return 0;
}

static int vrend_decode_set_shader_buffers(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t num_ssbo;
   uint32_t shader_type, start_slot;

   if (length < 2)
      return EINVAL;

   num_ssbo = (length - 2) / VIRGL_SET_SHADER_BUFFER_ELEMENT_SIZE;
   shader_type = get_buf_entry(buf, VIRGL_SET_SHADER_BUFFER_SHADER_TYPE);
   start_slot = get_buf_entry(buf, VIRGL_SET_SHADER_BUFFER_START_SLOT);
   if (shader_type >= PIPE_SHADER_TYPES)
      return EINVAL;

   if (num_ssbo < 1)
      return 0;

   if (start_slot > PIPE_MAX_SHADER_BUFFERS ||
       num_ssbo > PIPE_MAX_SHADER_BUFFERS - start_slot)
      return EINVAL;

   for (uint32_t i = 0; i < num_ssbo; i++) {
      uint32_t offset = get_buf_entry(buf, VIRGL_SET_SHADER_BUFFER_OFFSET(i));
      uint32_t buf_len = get_buf_entry(buf, VIRGL_SET_SHADER_BUFFER_LENGTH(i));
      uint32_t handle = get_buf_entry(buf, VIRGL_SET_SHADER_BUFFER_RES_HANDLE(i));
      vrend_set_single_ssbo(ctx, shader_type, start_slot + i, offset, buf_len,
                            handle);
   }
   return 0;
}

static int vrend_decode_set_atomic_buffers(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t num_abo;
   uint32_t start_slot;

   if (length < 2)
      return EINVAL;

   num_abo = (length - 1) / VIRGL_SET_ATOMIC_BUFFER_ELEMENT_SIZE;
   start_slot = get_buf_entry(buf, VIRGL_SET_ATOMIC_BUFFER_START_SLOT);
   if (num_abo < 1)
      return 0;

   if (num_abo > PIPE_MAX_HW_ATOMIC_BUFFERS ||
       start_slot > PIPE_MAX_HW_ATOMIC_BUFFERS ||
       start_slot > PIPE_MAX_HW_ATOMIC_BUFFERS - num_abo)
      return EINVAL;

   for (uint32_t i = 0; i < num_abo; i++) {
      uint32_t offset = get_buf_entry(buf, i * VIRGL_SET_ATOMIC_BUFFER_ELEMENT_SIZE + 2);
      uint32_t buf_len = get_buf_entry(buf, i * VIRGL_SET_ATOMIC_BUFFER_ELEMENT_SIZE + 3);
      uint32_t handle = get_buf_entry(buf, i * VIRGL_SET_ATOMIC_BUFFER_ELEMENT_SIZE + 4);
      vrend_set_single_abo(ctx, start_slot + i, offset, buf_len, handle);
   }

   return 0;
}

static int vrend_decode_set_shader_images(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t num_images;
   uint32_t shader_type, start_slot;
   if (length < 2)
      return EINVAL;

   num_images = (length - 2) / VIRGL_SET_SHADER_IMAGE_ELEMENT_SIZE;
   shader_type = get_buf_entry(buf, VIRGL_SET_SHADER_IMAGE_SHADER_TYPE);
   start_slot = get_buf_entry(buf, VIRGL_SET_SHADER_IMAGE_START_SLOT);
   if (shader_type >= PIPE_SHADER_TYPES)
      return EINVAL;

   if (num_images < 1) {
      return 0;
   }

   if (start_slot > PIPE_MAX_SHADER_IMAGES ||
       start_slot + num_images > PIPE_MAX_SHADER_IMAGES)
      return EINVAL;

   for (uint32_t i = 0; i < num_images; i++) {
      uint32_t format = get_buf_entry(buf, VIRGL_SET_SHADER_IMAGE_FORMAT(i));
      uint32_t access = get_buf_entry(buf, VIRGL_SET_SHADER_IMAGE_ACCESS(i));
      uint32_t layer_offset = get_buf_entry(buf, VIRGL_SET_SHADER_IMAGE_LAYER_OFFSET(i));
      uint32_t level_size = get_buf_entry(buf, VIRGL_SET_SHADER_IMAGE_LEVEL_SIZE(i));
      uint32_t handle = get_buf_entry(buf, VIRGL_SET_SHADER_IMAGE_RES_HANDLE(i));
      int ret = vrend_set_single_image_view(ctx, shader_type, start_slot + i, format, access,
                                  layer_offset, level_size, handle);
      if (ret)
         return ret;
   }
   return 0;
}

static int vrend_decode_memory_barrier(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != VIRGL_MEMORY_BARRIER_SIZE)
      return EINVAL;

   unsigned flags = get_buf_entry(buf, VIRGL_MEMORY_BARRIER_FLAGS);
   vrend_memory_barrier(ctx, flags);
   return 0;
}

static int vrend_decode_launch_grid(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   uint32_t block[3], grid[3];
   uint32_t indirect_handle, indirect_offset;
   if (length != VIRGL_LAUNCH_GRID_SIZE)
      return EINVAL;

   block[0] = get_buf_entry(buf, VIRGL_LAUNCH_BLOCK_X);
   block[1] = get_buf_entry(buf, VIRGL_LAUNCH_BLOCK_Y);
   block[2] = get_buf_entry(buf, VIRGL_LAUNCH_BLOCK_Z);
   grid[0] = get_buf_entry(buf, VIRGL_LAUNCH_GRID_X);
   grid[1] = get_buf_entry(buf, VIRGL_LAUNCH_GRID_Y);
   grid[2] = get_buf_entry(buf, VIRGL_LAUNCH_GRID_Z);
   indirect_handle = get_buf_entry(buf, VIRGL_LAUNCH_INDIRECT_HANDLE);
   indirect_offset = get_buf_entry(buf, VIRGL_LAUNCH_INDIRECT_OFFSET);
   vrend_launch_grid(ctx, block, grid, indirect_handle, indirect_offset);
   return 0;
}

static int vrend_decode_set_streamout_targets(struct vrend_context *ctx,
                                              const uint32_t *buf, uint32_t length)
{
   uint32_t handles[16];
   uint32_t num_handles = length - 1;
   uint32_t append_bitmask;
   unsigned i;

   if (length < 1)
      return EINVAL;
   if (num_handles > ARRAY_SIZE(handles))
      return EINVAL;

   append_bitmask = get_buf_entry(buf, VIRGL_SET_STREAMOUT_TARGETS_APPEND_BITMASK);
   for (i = 0; i < num_handles; i++)
      handles[i] = get_buf_entry(buf, VIRGL_SET_STREAMOUT_TARGETS_H0 + i);
   vrend_set_streamout_targets(ctx, append_bitmask, num_handles, handles);
   return 0;
}

static int vrend_decode_texture_barrier(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   if (length != VIRGL_TEXTURE_BARRIER_SIZE)
      return EINVAL;

   unsigned flags = get_buf_entry(buf, VIRGL_TEXTURE_BARRIER_FLAGS);
   vrend_texture_barrier(ctx, flags);
   return 0;
}

static int vrend_decode_set_debug_mask(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   char *flagstring;
   size_t slen = sizeof(uint32_t) * (length - VIRGL_SET_DEBUG_FLAGSTRING_OFFSET);

   if (length < VIRGL_SET_DEBUG_FLAGS_MIN_SIZE)
      return EINVAL;

   const uint32_t *flag_buf = get_buf_ptr(buf, VIRGL_SET_DEBUG_FLAGSTRING_OFFSET);
   flagstring = malloc(slen+1);

   if (!flagstring) {
      return ENOMEM;
   }

   memcpy(flagstring, flag_buf, slen);
   flagstring[slen] = 0;
   vrend_context_set_debug_flags(ctx, flagstring);

   free(flagstring);

   return 0;
}

static int vrend_decode_set_tweaks(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   VREND_DEBUG(dbg_tweak, NULL, "Received TWEAK set command\n");

   if (length < VIRGL_SET_TWEAKS_SIZE)
      return EINVAL;

   uint32_t tweak_id = get_buf_entry(buf, VIRGL_SET_TWEAKS_ID);
   uint32_t tweak_value = get_buf_entry(buf, VIRGL_SET_TWEAKS_VALUE);

   vrend_set_active_tweaks(vrend_get_context_tweaks(ctx), tweak_id, tweak_value);
   return 0;
}


static int vrend_decode_transfer3d(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_box box;
   uint32_t dst_handle;
   struct vrend_transfer_info info;

   if (length < VIRGL_TRANSFER3D_SIZE)
      return EINVAL;

   memset(&info, 0, sizeof(info));
   info.box = &box;
   vrend_decode_transfer_common(buf, &dst_handle, &info);
   info.offset = get_buf_entry(buf, VIRGL_TRANSFER3D_DATA_OFFSET);
   int transfer_mode = get_buf_entry(buf, VIRGL_TRANSFER3D_DIRECTION);

   if (transfer_mode != VIRGL_TRANSFER_TO_HOST &&
       transfer_mode != VIRGL_TRANSFER_FROM_HOST)
      return EINVAL;

   return vrend_renderer_transfer_iov(ctx, dst_handle, &info,
                                      transfer_mode);
}

static int check_copy_transfer3d_handles(struct vrend_context *ctx, uint32_t src_handle,  uint32_t dst_handle,
                                         struct vrend_resource *src_res, struct vrend_resource *dst_res)
{
   if (!src_res || !src_res->iov) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, src_handle);
      return EINVAL;
   }

   if (!dst_res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, dst_handle);
      return EINVAL;
   }
   return 0;
}

static int vrend_decode_copy_transfer3d(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct pipe_box box;
   struct vrend_transfer_info info;
   uint32_t dst_handle;
   uint32_t src_handle;
   struct vrend_resource *src_res, *dst_res;

   if (length != VIRGL_COPY_TRANSFER3D_SIZE)
      return EINVAL;

   memset(&info, 0, sizeof(info));
   info.box = &box;

   // synchronized is set either to 1 or 0. This means that we can use other bits
   // to identify the direction of copy transfer
   uint32_t flags = get_buf_entry(buf, VIRGL_COPY_TRANSFER3D_FLAGS);
   bool read_from_host = (flags & VIRGL_COPY_TRANSFER3D_FLAGS_READ_FROM_HOST) != 0;
   info.synchronized = (flags & VIRGL_COPY_TRANSFER3D_FLAGS_SYNCHRONIZED) != 0;
   info.offset = get_buf_entry(buf, VIRGL_COPY_TRANSFER3D_SRC_RES_OFFSET);

#ifdef ENABLE_TESTS
   info.no_gbm_mapping = (flags & VIRGL_COPY_TRANSFER3D_FLAGS_DEBUG_TEST_NO_GBM_MAPPING) != 0;
#endif

   if (unlikely(read_from_host)) {
      vrend_decode_transfer_common(buf, &src_handle, &info);
      dst_handle = get_buf_entry(buf, VIRGL_COPY_TRANSFER3D_SRC_RES_HANDLE);

      src_res = vrend_renderer_ctx_res_lookup(ctx, src_handle);
      dst_res = vrend_renderer_ctx_res_lookup(ctx, dst_handle);

      int ret = check_copy_transfer3d_handles(ctx, src_handle, dst_handle, src_res, dst_res);
      if (ret)
         return ret;

      return vrend_renderer_copy_transfer3d_from_host(ctx, dst_handle, src_handle, dst_res, src_res,
                                                      &info);
   } else {
      // this means that guest would like to make transfer to host
      // it can also mean that guest is using legacy copy transfer path
      vrend_decode_transfer_common(buf, &dst_handle, &info);
      src_handle = get_buf_entry(buf, VIRGL_COPY_TRANSFER3D_SRC_RES_HANDLE);

      src_res = vrend_renderer_ctx_res_lookup(ctx, src_handle);
      dst_res = vrend_renderer_ctx_res_lookup(ctx, dst_handle);

      int ret = check_copy_transfer3d_handles(ctx, src_handle, dst_handle, src_res, dst_res);
      if (ret)
         return ret;

      return vrend_renderer_copy_transfer3d(ctx, dst_handle, dst_res, src_res,
                                            &info);
   }
}

static int vrend_decode_pipe_resource_create(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct vrend_renderer_resource_create_args args = { 0 };
   uint32_t blob_id;

   if (length != VIRGL_PIPE_RES_CREATE_SIZE)
      return EINVAL;

   args.target = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_TARGET);
   args.format = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_FORMAT);
   args.bind = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_BIND);
   args.width = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_WIDTH);
   args.height = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_HEIGHT);
   args.depth = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_DEPTH);
   args.array_size = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_ARRAY_SIZE);
   args.last_level = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_LAST_LEVEL);
   args.nr_samples = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_NR_SAMPLES);
   args.flags = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_FLAGS);
   blob_id = get_buf_entry(buf, VIRGL_PIPE_RES_CREATE_BLOB_ID);

   return vrend_renderer_pipe_resource_create(ctx, blob_id, &args);
}

static int vrend_decode_pipe_resource_set_type(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   struct vrend_renderer_resource_set_type_args args = { 0 };
   uint32_t res_id;

   if (length >= VIRGL_PIPE_RES_SET_TYPE_SIZE(0))
      args.plane_count = (length - VIRGL_PIPE_RES_SET_TYPE_SIZE(0)) / 2;

   if (length != VIRGL_PIPE_RES_SET_TYPE_SIZE(args.plane_count) ||
       !args.plane_count || args.plane_count > VIRGL_GBM_MAX_PLANES)
      return EINVAL;

   res_id = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_RES_HANDLE);
   args.format = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_FORMAT);
   args.bind = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_BIND);
   args.width = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_WIDTH);
   args.height = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_HEIGHT);
   args.usage = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_USAGE);
   args.modifier = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_MODIFIER_LO);
   args.modifier |= (uint64_t)get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_MODIFIER_HI) << 32;
   for (uint32_t i = 0; i < args.plane_count; i++) {
      args.plane_strides[i] = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_PLANE_STRIDE(i));
      args.plane_offsets[i] = get_buf_entry(buf, VIRGL_PIPE_RES_SET_TYPE_PLANE_OFFSET(i));
   }

   return vrend_renderer_pipe_resource_set_type(ctx, res_id, &args);
}

static void vrend_decode_ctx_init_base(struct vrend_decode_ctx *dctx,
                                       uint32_t ctx_id);

static void vrend_decode_ctx_fence_retire(uint64_t fence_id,
                                          void *retire_data)
{
   struct vrend_decode_ctx *dctx = retire_data;
   dctx->base.fence_retire(&dctx->base, 0, fence_id);
}

struct virgl_context *vrend_renderer_context_create(uint32_t handle,
                                                    uint32_t nlen,
                                                    const char *debug_name)
{
   struct vrend_decode_ctx *dctx;

   dctx = malloc(sizeof(struct vrend_decode_ctx));
   if (!dctx)
      return NULL;

   vrend_decode_ctx_init_base(dctx, handle);

   dctx->grctx = vrend_create_context(handle, nlen, debug_name);
   if (!dctx->grctx) {
      free(dctx);
      return NULL;
   }

   vrend_renderer_set_fence_retire(dctx->grctx,
                                   vrend_decode_ctx_fence_retire,
                                   dctx);

   return &dctx->base;
}

static void vrend_decode_ctx_destroy(struct virgl_context *ctx)
{
   TRACE_FUNC();
   struct vrend_decode_ctx *dctx = (struct vrend_decode_ctx *)ctx;

   vrend_destroy_context(dctx->grctx);
   free(dctx);
}

static void vrend_decode_ctx_attach_resource(struct virgl_context *ctx,
                                             struct virgl_resource *res)
{
   TRACE_FUNC();
   struct vrend_decode_ctx *dctx = (struct vrend_decode_ctx *)ctx;
   vrend_renderer_attach_res_ctx(dctx->grctx, res);
}

static void vrend_decode_ctx_detach_resource(struct virgl_context *ctx,
                                             struct virgl_resource *res)
{
   TRACE_FUNC();
   struct vrend_decode_ctx *dctx = (struct vrend_decode_ctx *)ctx;
   vrend_renderer_detach_res_ctx(dctx->grctx, res);
}

static int vrend_decode_ctx_transfer_3d(struct virgl_context *ctx,
                                        struct virgl_resource *res,
                                        const struct vrend_transfer_info *info,
                                        int transfer_mode)
{
   TRACE_FUNC();
   struct vrend_decode_ctx *dctx = (struct vrend_decode_ctx *)ctx;
   int ret = vrend_renderer_transfer_iov(dctx->grctx, res->res_id, info,
                                         transfer_mode);
   return vrend_check_no_error(dctx->grctx) || ret ? ret : EINVAL;
}

static int vrend_decode_ctx_get_blob(struct virgl_context *ctx,
                                     UNUSED uint32_t res_id,
                                     uint64_t blob_id,
                                     UNUSED uint64_t blob_size,
                                     UNUSED uint32_t blob_flags,
                                     struct virgl_context_blob *blob)
{
   TRACE_FUNC();
   struct vrend_decode_ctx *dctx = (struct vrend_decode_ctx *)ctx;

   blob->type = VIRGL_RESOURCE_FD_INVALID;
   /* this transfers ownership and blob_id is no longer valid */
   blob->u.pipe_resource = vrend_get_blob_pipe(dctx->grctx, blob_id);
   if (!blob->u.pipe_resource)
      return -EINVAL;

   blob->map_info = vrend_renderer_resource_get_map_info(blob->u.pipe_resource);
   return 0;
}

static int vrend_decode_get_memory_info(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   TRACE_FUNC();
   if (length != 1)
      return EINVAL;

   uint32_t res_handle = get_buf_entry(buf, 1);

   vrend_renderer_get_meminfo(ctx, res_handle);

   return 0;
}

static int vrend_decode_send_string_marker(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   size_t buf_len = sizeof(uint32_t) * (length - 1);

   if (length < VIRGL_SEND_STRING_MARKER_MIN_SIZE) {
      virgl_error("VIRGL_SEND_STRING_MARKER: minimal command length not okay\n");
      return EINVAL;
   }

   uint32_t str_len = get_buf_entry(buf, VIRGL_SEND_STRING_MARKER_STRING_SIZE);
   if (str_len > buf_len) {
       virgl_error("VIRGL_SEND_STRING_MARKER: String len %u > buf_len %zu\n", str_len, buf_len);
       return EINVAL;
   }

   vrend_context_emit_string_marker(ctx, str_len, get_buf_ptr(buf, VIRGL_SEND_STRING_MARKER_OFFSET));

   return 0;
}

static int vrend_decode_get_pipe_resource_layout(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   TRACE_FUNC();
   if (length < VIRGL_RESOURCE_LAYOUT_SIZE)
      return EINVAL;

   uint32_t out_handle = get_buf_entry(buf, VIRGL_RESOURCE_LAYOUT_HANDLE_OUT);
   uint32_t target_handle = get_buf_entry(buf, VIRGL_RESOURCE_LAYOUT_HANDLE_TARGET);

   return vrend_renderer_pipe_resource_get_layout(ctx, out_handle, target_handle);
}

#ifdef ENABLE_VIDEO
/* video codec related functions */

static int vrend_decode_create_video_codec(struct vrend_context *ctx,
                                           const uint32_t *buf,
                                           uint32_t length)
{
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

   if (length < VIRGL_CREATE_VIDEO_CODEC_MIN_SIZE)
      return EINVAL;

   uint32_t handle     = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_HANDLE);
   uint32_t profile    = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_PROFILE);
   uint32_t entrypoint = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_ENTRYPOINT);
   uint32_t chroma_fmt = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_CHROMA_FMT);
   uint32_t level      = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_LEVEL);
   uint32_t width      = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_WIDTH);
   uint32_t height     = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_HEIGHT);
   uint32_t max_ref    = 2; /* The max number of ref frames is 2 by default */

   if (length >= VIRGL_CREATE_VIDEO_CODEC_MAX_REF)
      max_ref = get_buf_entry(buf, VIRGL_CREATE_VIDEO_CODEC_MAX_REF);

   vrend_video_create_codec(vctx, handle, profile, entrypoint,
                            chroma_fmt, level, width, height, max_ref, 0);

   return 0;
}

static int vrend_decode_destroy_video_codec(struct vrend_context *ctx,
                                            const uint32_t *buf,
                                            uint32_t length)
{
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

    if (length < VIRGL_DESTROY_VIDEO_CODEC_MIN_SIZE)
       return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_DESTROY_VIDEO_CODEC_HANDLE);
   vrend_video_destroy_codec(vctx, handle);

   return 0;
}

static int vrend_decode_create_video_buffer(struct vrend_context *ctx,
                                            const uint32_t *buf,
                                            uint32_t length)
{
   uint32_t i, num_res;
   uint32_t res_handles[VREND_VIDEO_BUFFER_PLANE_NUM];
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

   if (length < VIRGL_CREATE_VIDEO_BUFFER_MIN_SIZE)
      return EINVAL;

   num_res = length - VIRGL_CREATE_VIDEO_BUFFER_RES_BASE + 1;
   if (num_res > VREND_VIDEO_BUFFER_PLANE_NUM)
       num_res = VREND_VIDEO_BUFFER_PLANE_NUM;

   uint32_t handle = get_buf_entry(buf, VIRGL_CREATE_VIDEO_BUFFER_HANDLE);
   uint32_t format = get_buf_entry(buf, VIRGL_CREATE_VIDEO_BUFFER_FORMAT);
   uint32_t width  = get_buf_entry(buf, VIRGL_CREATE_VIDEO_BUFFER_WIDTH);
   uint32_t height = get_buf_entry(buf, VIRGL_CREATE_VIDEO_BUFFER_HEIGHT);

   memset(res_handles, 0, sizeof(res_handles));
   for (i = 0; i < num_res; i++)
       res_handles[i] = get_buf_entry(buf,
                                      VIRGL_CREATE_VIDEO_BUFFER_RES_BASE + i);

   vrend_video_create_buffer(vctx, handle, format, width, height,
                                      res_handles, num_res);

   return 0;
}

static int vrend_decode_destroy_video_buffer(struct vrend_context *ctx,
                                             const uint32_t *buf,
                                             uint32_t length)
{
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

   if (length < VIRGL_DESTROY_VIDEO_BUFFER_MIN_SIZE)
      return EINVAL;

   uint32_t handle = get_buf_entry(buf, VIRGL_DESTROY_VIDEO_BUFFER_HANDLE);
   vrend_video_destroy_buffer(vctx, handle);

   return 0;
}

static int vrend_decode_begin_frame(struct vrend_context *ctx,
                                    const uint32_t *buf,
                                    uint32_t length)
{
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

   if (length < VIRGL_BEGIN_FRAME_MIN_SIZE)
      return EINVAL;

   uint32_t cdc_handle = get_buf_entry(buf, VIRGL_BEGIN_FRAME_CDC_HANDLE);
   uint32_t tgt_handle = get_buf_entry(buf, VIRGL_BEGIN_FRAME_TGT_HANDLE);
   vrend_video_begin_frame(vctx, cdc_handle, tgt_handle);

   return 0;
}

static int vrend_decode_decode_bitstream(struct vrend_context *ctx,
                                         const uint32_t *buf,
                                         uint32_t length)
{
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

   if (length < VIRGL_DECODE_BS_MIN_SIZE)
      return EINVAL;

   uint32_t cdc_handle = get_buf_entry(buf, VIRGL_DECODE_BS_CDC_HANDLE);
   uint32_t tgt_handle = get_buf_entry(buf, VIRGL_DECODE_BS_TGT_HANDLE);
   uint32_t dsc_handle = get_buf_entry(buf, VIRGL_DECODE_BS_DSC_HANDLE);
   uint32_t buf_handle = get_buf_entry(buf, VIRGL_DECODE_BS_BUF_HANDLE);
   uint32_t buf_size   = get_buf_entry(buf, VIRGL_DECODE_BS_BUF_SIZE);

   vrend_video_decode_bitstream(vctx, cdc_handle, tgt_handle,
                                dsc_handle, 1, &buf_handle, &buf_size);

   return 0;
}

static int vrend_decode_encode_bitstream(struct vrend_context *ctx,
                                         const uint32_t *buf,
                                         uint32_t length)
{
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

   if (length < VIRGL_ENCODE_BS_MIN_SIZE)
      return EINVAL;

   uint32_t cdc_handle  = get_buf_entry(buf, VIRGL_ENCODE_BS_CDC_HANDLE);
   uint32_t src_handle  = get_buf_entry(buf, VIRGL_ENCODE_BS_SRC_HANDLE);
   uint32_t dest_handle = get_buf_entry(buf, VIRGL_ENCODE_BS_DEST_HANDLE);
   uint32_t desc_handle = get_buf_entry(buf, VIRGL_ENCODE_BS_DESC_HANDLE);
   uint32_t feed_handle = get_buf_entry(buf, VIRGL_ENCODE_BS_FEED_HANDLE);

   vrend_video_encode_bitstream(vctx, cdc_handle, src_handle, dest_handle,
                                desc_handle, feed_handle);

   return 0;
}

static int vrend_decode_end_frame(struct vrend_context *ctx,
                                  const uint32_t *buf,
                                  uint32_t length)
{
   struct vrend_video_context *vctx = vrend_context_get_video_ctx(ctx);

   if (length < VIRGL_END_FRAME_MIN_SIZE)
      return EINVAL;

   uint32_t cdc_handle = get_buf_entry(buf, VIRGL_END_FRAME_CDC_HANDLE);
   uint32_t tgt_handle = get_buf_entry(buf, VIRGL_END_FRAME_TGT_HANDLE);

   vrend_video_end_frame(vctx, cdc_handle, tgt_handle);

   return 0;
}

#else

static int vrend_unsupported(struct vrend_context *ctx,
                                    const uint32_t *buf,
                                    uint32_t length)
{
   (void)ctx;
   (void)buf;
   (void)length;
   return EINVAL;
}

#endif /* ENABLE_VIDEO */


typedef int (*vrend_decode_callback)(struct vrend_context *ctx, const uint32_t *buf, uint32_t length);

static int vrend_decode_dummy(struct vrend_context *ctx, const uint32_t *buf, uint32_t length)
{
   (void)ctx;
   (void)buf;
   (void)length;
   return 0;
}

static const vrend_decode_callback decode_table[VIRGL_MAX_COMMANDS] = {
   [VIRGL_CCMD_NOP] = vrend_decode_dummy,
   [VIRGL_CCMD_CREATE_OBJECT] = vrend_decode_create_object,
   [VIRGL_CCMD_BIND_OBJECT] = vrend_decode_bind_object,
   [VIRGL_CCMD_DESTROY_OBJECT] = vrend_decode_destroy_object,
   [VIRGL_CCMD_CLEAR] = vrend_decode_clear,
   [VIRGL_CCMD_CLEAR_TEXTURE] = vrend_decode_clear_texture,
   [VIRGL_CCMD_CLEAR_SURFACE] = vrend_decode_clear_surface,
   [VIRGL_CCMD_DRAW_VBO] = vrend_decode_draw_vbo,
   [VIRGL_CCMD_SET_FRAMEBUFFER_STATE] = vrend_decode_set_framebuffer_state,
   [VIRGL_CCMD_SET_VERTEX_BUFFERS] = vrend_decode_set_vertex_buffers,
   [VIRGL_CCMD_RESOURCE_INLINE_WRITE] = vrend_decode_resource_inline_write,
   [VIRGL_CCMD_SET_VIEWPORT_STATE] = vrend_decode_set_viewport_state,
   [VIRGL_CCMD_SET_SAMPLER_VIEWS] = vrend_decode_set_sampler_views,
   [VIRGL_CCMD_SET_INDEX_BUFFER] = vrend_decode_set_index_buffer,
   [VIRGL_CCMD_SET_CONSTANT_BUFFER] = vrend_decode_set_constant_buffer,
   [VIRGL_CCMD_SET_STENCIL_REF] = vrend_decode_set_stencil_ref,
   [VIRGL_CCMD_SET_BLEND_COLOR] = vrend_decode_set_blend_color,
   [VIRGL_CCMD_SET_SCISSOR_STATE] = vrend_decode_set_scissor_state,
   [VIRGL_CCMD_BLIT] = vrend_decode_blit,
   [VIRGL_CCMD_RESOURCE_COPY_REGION] = vrend_decode_resource_copy_region,
   [VIRGL_CCMD_BIND_SAMPLER_STATES] = vrend_decode_bind_sampler_states,
   [VIRGL_CCMD_BEGIN_QUERY] = vrend_decode_begin_query,
   [VIRGL_CCMD_END_QUERY] = vrend_decode_end_query,
   [VIRGL_CCMD_GET_QUERY_RESULT] = vrend_decode_get_query_result,
   [VIRGL_CCMD_SET_POLYGON_STIPPLE] = vrend_decode_set_polygon_stipple,
   [VIRGL_CCMD_SET_CLIP_STATE] = vrend_decode_set_clip_state,
   [VIRGL_CCMD_SET_SAMPLE_MASK] = vrend_decode_set_sample_mask,
   [VIRGL_CCMD_SET_MIN_SAMPLES] = vrend_decode_set_min_samples,
   [VIRGL_CCMD_SET_STREAMOUT_TARGETS] = vrend_decode_set_streamout_targets,
   [VIRGL_CCMD_SET_RENDER_CONDITION] = vrend_decode_set_render_condition,
   [VIRGL_CCMD_SET_UNIFORM_BUFFER] = vrend_decode_set_uniform_buffer,
   [VIRGL_CCMD_SET_SUB_CTX] = vrend_decode_set_sub_ctx,
   [VIRGL_CCMD_CREATE_SUB_CTX] = vrend_decode_create_sub_ctx,
   [VIRGL_CCMD_DESTROY_SUB_CTX] = vrend_decode_destroy_sub_ctx,
   [VIRGL_CCMD_BIND_SHADER] = vrend_decode_bind_shader,
   [VIRGL_CCMD_SET_TESS_STATE] = vrend_decode_set_tess_state,
   [VIRGL_CCMD_SET_SHADER_BUFFERS] = vrend_decode_set_shader_buffers,
   [VIRGL_CCMD_SET_SHADER_IMAGES] = vrend_decode_set_shader_images,
   [VIRGL_CCMD_SET_ATOMIC_BUFFERS] = vrend_decode_set_atomic_buffers,
   [VIRGL_CCMD_MEMORY_BARRIER] = vrend_decode_memory_barrier,
   [VIRGL_CCMD_LAUNCH_GRID] = vrend_decode_launch_grid,
   [VIRGL_CCMD_SET_FRAMEBUFFER_STATE_NO_ATTACH] = vrend_decode_set_framebuffer_state_no_attach,
   [VIRGL_CCMD_TEXTURE_BARRIER] = vrend_decode_texture_barrier,
   [VIRGL_CCMD_SET_DEBUG_FLAGS] = vrend_decode_set_debug_mask,
   [VIRGL_CCMD_GET_QUERY_RESULT_QBO] = vrend_decode_get_query_result_qbo,
   [VIRGL_CCMD_TRANSFER3D] = vrend_decode_transfer3d,
   [VIRGL_CCMD_COPY_TRANSFER3D] = vrend_decode_copy_transfer3d,
   [VIRGL_CCMD_END_TRANSFERS] = vrend_decode_dummy,
   [VIRGL_CCMD_SET_TWEAKS] = vrend_decode_set_tweaks,
   [VIRGL_CCMD_PIPE_RESOURCE_CREATE] = vrend_decode_pipe_resource_create,
   [VIRGL_CCMD_PIPE_RESOURCE_SET_TYPE] = vrend_decode_pipe_resource_set_type,
   [VIRGL_CCMD_GET_MEMORY_INFO] = vrend_decode_get_memory_info,
   [VIRGL_CCMD_SEND_STRING_MARKER] = vrend_decode_send_string_marker,
   [VIRGL_CCMD_LINK_SHADER] = vrend_decode_link_shader,
#ifdef ENABLE_VIDEO
   [VIRGL_CCMD_CREATE_VIDEO_CODEC] = vrend_decode_create_video_codec,
   [VIRGL_CCMD_DESTROY_VIDEO_CODEC] = vrend_decode_destroy_video_codec,
   [VIRGL_CCMD_CREATE_VIDEO_BUFFER] = vrend_decode_create_video_buffer,
   [VIRGL_CCMD_DESTROY_VIDEO_BUFFER] = vrend_decode_destroy_video_buffer,
   [VIRGL_CCMD_BEGIN_FRAME] = vrend_decode_begin_frame,
   [VIRGL_CCMD_DECODE_MACROBLOCK] = vrend_decode_dummy,
   [VIRGL_CCMD_DECODE_BITSTREAM] = vrend_decode_decode_bitstream,
   [VIRGL_CCMD_ENCODE_BITSTREAM] = vrend_decode_encode_bitstream,
   [VIRGL_CCMD_END_FRAME] = vrend_decode_end_frame,
#else
   [VIRGL_CCMD_CREATE_VIDEO_CODEC] = vrend_unsupported,
   [VIRGL_CCMD_DESTROY_VIDEO_CODEC] = vrend_unsupported,
   [VIRGL_CCMD_CREATE_VIDEO_BUFFER] = vrend_unsupported,
   [VIRGL_CCMD_DESTROY_VIDEO_BUFFER] = vrend_unsupported,
   [VIRGL_CCMD_BEGIN_FRAME] = vrend_unsupported,
   [VIRGL_CCMD_DECODE_MACROBLOCK] = vrend_unsupported,
   [VIRGL_CCMD_DECODE_BITSTREAM] = vrend_unsupported,
   [VIRGL_CCMD_ENCODE_BITSTREAM] = vrend_unsupported,
   [VIRGL_CCMD_END_FRAME] = vrend_unsupported,
#endif
   [VIRGL_CCMD_GET_PIPE_RESOURCE_LAYOUT] = vrend_decode_get_pipe_resource_layout,
};

static void dump_command_stream_to_file(const void *buffer, size_t size)
{
   uint64_t hash = XXH64(buffer, size, 0);
   char fname[64];
   snprintf(fname,
            sizeof(fname), "buffer_%016" PRIx64 ".seed",  hash);
   FILE *f = fopen(fname, "wb");
   if (f) {
      fwrite(buffer, 1, size, f);
      fclose(f);
   }
}

static int vrend_decode_ctx_submit_cmd(struct virgl_context *ctx,
                                       const void *buffer,
                                       size_t size)
{
   TRACE_FUNC();
   struct vrend_decode_ctx *gdctx = (struct vrend_decode_ctx *)ctx;
   bool bret;
   int ret;

#define TRANSFER_HEADER_SIZE 4096

   bret = vrend_hw_switch_context(gdctx->grctx, true);
   if (bret == false)
      return EINVAL;

   if (VREND_DEBUG_ENABLED &&
       vrend_debug(gdctx->grctx, dbg_dump_cmd_streams) &&
       size > TRANSFER_HEADER_SIZE) {
      dump_command_stream_to_file((char *)buffer + TRANSFER_HEADER_SIZE,
                                  size - TRANSFER_HEADER_SIZE);
   }

   const uint32_t *typed_buf = (const uint32_t *)buffer;
   const uint32_t buf_total = (uint32_t)(size / sizeof(uint32_t));
   uint32_t buf_offset = 0;

   while (buf_offset < buf_total) {
      const uint32_t cur_offset = buf_offset;
      const uint32_t *buf = &typed_buf[buf_offset];
      uint32_t len = *buf >> 16;
      uint32_t cmd = *buf & 0xff;

      if (cmd >= VIRGL_MAX_COMMANDS)
         return EINVAL;

      buf_offset += len + 1;

      ret = 0;
      /* check if the guest is doing something bad */
      if (buf_offset > buf_total) {
         vrend_report_buffer_error(gdctx->grctx, 0);
         break;
      }

      VREND_DEBUG(dbg_cmd, gdctx->grctx, "%-4d %-20s len:%d\n",
                  cur_offset, vrend_get_comand_name(cmd), len);

      TRACE_SCOPE_SLOW(vrend_get_comand_name(cmd));

      ret = decode_table[cmd](gdctx->grctx, buf, len);
      if (!vrend_check_no_error(gdctx->grctx) && !ret)
         ret = EINVAL;
      if (ret) {
         virgl_error("context %d failed to dispatch %s: %d\n",
               gdctx->base.ctx_id, vrend_get_comand_name(cmd), ret);
         if (ret == EINVAL)
            vrend_report_buffer_error(gdctx->grctx, *buf);
         return ret;
      }
   }
   return 0;
}

static int vrend_decode_ctx_get_fencing_fd(UNUSED struct virgl_context *ctx)
{
   return vrend_renderer_get_poll_fd();
}

static void vrend_decode_ctx_retire_fences(UNUSED struct virgl_context *ctx)
{
   vrend_renderer_check_fences();
}

static int vrend_decode_ctx_submit_fence(struct virgl_context *ctx,
                                         uint32_t flags,
                                         uint32_t ring_idx,
                                         uint64_t fence_id)
{
   struct vrend_decode_ctx *dctx = (struct vrend_decode_ctx *)ctx;

   if (ring_idx)
      return -EINVAL;

   if (!dctx->grctx)
      return EINVAL;

   return vrend_renderer_create_fence(dctx->grctx, flags, fence_id);
}

static void vrend_decode_ctx_init_base(struct vrend_decode_ctx *dctx,
                                       uint32_t ctx_id)
{
   struct virgl_context *ctx = &dctx->base ;

   for (unsigned i = 0; i < VIRGL_MAX_COMMANDS; ++i)
      assert(decode_table[i]);

   ctx->ctx_id = ctx_id;
   ctx->destroy = vrend_decode_ctx_destroy;
   ctx->attach_resource = vrend_decode_ctx_attach_resource;
   ctx->detach_resource = vrend_decode_ctx_detach_resource;
   ctx->transfer_3d = vrend_decode_ctx_transfer_3d;
   ctx->get_blob = vrend_decode_ctx_get_blob;
   ctx->submit_cmd = vrend_decode_ctx_submit_cmd;

   ctx->get_fencing_fd = vrend_decode_ctx_get_fencing_fd;
   ctx->retire_fences = vrend_decode_ctx_retire_fences;
   ctx->submit_fence = vrend_decode_ctx_submit_fence;
}
