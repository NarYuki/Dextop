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

#ifndef VREND_SHADER_H
#define VREND_SHADER_H

#include "pipe/p_state.h"
#include "pipe/p_shader_tokens.h"

#include "vrend_strbuf.h"

#define VIRGL_NUM_CLIP_PLANES 8

#define VREND_POLYGON_STIPPLE_SIZE 32
#define VREND_MAX_COMBINED_SSBO_BINDING_POINTS 32

#define VREND_SHADER_SAMPLER_VIEWS_MASK_LENGTH \
   ((PIPE_MAX_SHADER_SAMPLER_VIEWS + 63) / 64)

enum gl_advanced_blend_mode
{
   BLEND_NONE = 0,
   BLEND_MULTIPLY,
   BLEND_SCREEN,
   BLEND_OVERLAY,
   BLEND_DARKEN,
   BLEND_LIGHTEN,
   BLEND_COLORDODGE,
   BLEND_COLORBURN,
   BLEND_HARDLIGHT,
   BLEND_SOFTLIGHT,
   BLEND_DIFFERENCE,
   BLEND_EXCLUSION,
   BLEND_HSL_HUE,
   BLEND_HSL_SATURATION,
   BLEND_HSL_COLOR,
   BLEND_HSL_LUMINOSITY,
   BLEND_ALL
};


/* need to store patching info for interpolation */
struct vrend_interp_info {
   unsigned semantic_name : 6;
   unsigned semantic_index : 16;
   unsigned interpolate : 3;
   unsigned location : 3;
};

struct vrend_array {
   int first;
   int array_size;
};

struct vrend_layout_info {
   unsigned name : 6;
   unsigned sid : 16 ;
   unsigned location : 16 ;
   unsigned array_id : 16 ;
};

struct vrend_fs_shader_info {
   int num_interps;
   int glsl_ver;
   bool has_sample_input;
   bool has_noperspective;
   struct vrend_interp_info interpinfo[PIPE_MAX_SHADER_INPUTS];
};

struct vrend_shader_info_out {
   uint8_t num_generic_and_patch;
   uint8_t num_indirect_generic;
   uint8_t num_indirect_patch;
   bool guest_sent_io_arrays;
};

struct vrend_shader_info_in {
   uint64_t generic_emitted_mask;
   uint64_t texcoord_emitted_mask;
   bool indirect_generic_or_patch : 1;
   bool use_pervertex : 1;
};

struct vrend_shader_io_array {
   enum tgsi_semantic name : 6;
   uint32_t sid : 6;
   uint32_t size : 6;
   uint32_t array_id : 6;
   uint32_t padding : 8;
};

struct vrend_shader_io_array_info {
   uint32_t num_arrays;
   struct vrend_shader_io_array layout[16];
};

struct vrend_shader_info {
   uint32_t invariant_outputs[4];
   uint64_t in_generic_emitted_mask;
   uint64_t in_texcoord_emitted_mask;

   uint64_t out_generic_emitted_mask;
   uint64_t out_patch_emitted_mask;

   struct vrend_shader_io_array_info output_arrays;

   struct vrend_array *sampler_arrays;
   struct vrend_array *image_arrays;
   char **so_names;
   struct pipe_stream_output_info so_info;

   /* 8 cbufs + depth + stencil + samplemask */
   int8_t fs_output_layout[12];

   uint32_t samplers_used_mask;
   uint32_t images_used_mask;
   uint32_t image_binding_offset;
   int32_t image_last_binding;
   uint32_t ubo_used_mask;
   uint32_t ssbo_used_mask;
   uint32_t ssbo_binding_offset;
   int32_t ssbo_last_binding;
   uint32_t shadow_samp_mask;
   uint32_t attrib_input_mask;
   uint32_t fs_blend_equation_advanced;
   uint32_t fog_input_mask;
   uint32_t fog_output_mask;

   int num_consts;
   int num_inputs;
   int num_outputs;
   int gs_out_prim;
   int tes_prim;
   int num_sampler_arrays;
   int num_image_arrays;

   uint8_t out_texcoord_emitted_mask;
   uint8_t ubo_indirect : 1;
   uint8_t tes_point_mode : 1;
   uint8_t gles_use_tex_query_level : 1;
   uint8_t separable_program : 1;
   uint8_t has_input_arrays : 1;
   uint8_t has_output_arrays : 1;
   uint8_t use_pervertex_in : 1;
   uint8_t reads_drawid : 1;
};

struct vrend_variable_shader_info {
   struct vrend_fs_shader_info fs_info;
   uint32_t num_in_clip:4;
   uint32_t num_in_cull:4;
   uint32_t num_out_clip:4;
   uint32_t num_out_cull:4;
   int num_ucp;
   int legacy_color_bits;
};

struct vrend_shader_key {
   uint64_t out_generic_expected_mask;
   uint64_t out_texcoord_expected_mask;

   uint64_t in_generic_expected_mask;
   uint64_t in_texcoord_expected_mask;
   uint64_t in_patch_expected_mask;

   uint32_t force_invariant_inputs[4];

   struct vrend_fs_shader_info fs_info;
   struct vrend_shader_io_array_info in_arrays;

   union {
      struct {
         uint8_t surface_component_bits[PIPE_MAX_COLOR_BUFS];
         uint32_t coord_replace;
         uint8_t swizzle_output_rgb_to_bgr;
         uint8_t needs_manual_srgb_encode_bitmask;
         uint8_t cbufs_are_a8_bitmask;
         uint8_t cbufs_signed_int_bitmask;
         uint8_t cbufs_unsigned_int_bitmask;
         uint32_t logicop_func : 4;
         uint32_t logicop_enabled : 1;
         uint32_t prim_is_points : 1;
         uint32_t lower_left_origin : 1;
         uint32_t available_color_in_bits : 4;
      } fs;

      struct {
         uint32_t attrib_signed_int_bitmask;
         uint32_t attrib_unsigned_int_bitmask;
         uint32_t attrib_zyxw_bitmask;
         uint32_t fog_fixup_mask;
      } vs;

      struct {
         uint32_t emit_clip_distance : 1;
      } gs;
   };

   uint64_t sampler_views_lower_swizzle_mask[VREND_SHADER_SAMPLER_VIEWS_MASK_LENGTH];
   uint64_t sampler_views_emulated_rect_mask[VREND_SHADER_SAMPLER_VIEWS_MASK_LENGTH];
   uint16_t tex_swizzle[PIPE_MAX_SHADER_SAMPLER_VIEWS];

   uint8_t ssbo_binding_offset;
   uint8_t image_binding_offset;
   uint8_t alpha_test;
   uint8_t num_in_cull : 4;
   uint8_t num_in_clip : 4;
   uint8_t num_out_cull : 4;
   uint8_t num_out_clip : 4;
   uint8_t pstipple_enabled : 1;
   uint8_t add_alpha_test : 1;
   uint8_t color_two_side : 1;
   uint8_t gs_present : 1;
   uint8_t tcs_present : 1;
   uint8_t tes_present : 1;
   uint8_t flatshade : 1;
   uint8_t require_input_arrays : 1;
   uint8_t require_output_arrays : 1;
   uint8_t use_pervertex_in : 1;
};

struct vrend_shader_cfg {
   uint32_t glsl_version : 12;
   uint32_t max_draw_buffers : 4;
   uint32_t max_shader_patch_varyings : 6;
   uint32_t use_gles : 1;
   uint32_t use_core_profile : 1;
   uint32_t use_explicit_locations : 1;
   uint32_t has_arrays_of_arrays : 1;
   uint32_t has_gpu_shader5 : 1;
   uint32_t has_es31_compat : 1;
   uint32_t has_conservative_depth : 1;
   uint32_t use_integer : 1;
   uint32_t has_dual_src_blend : 1;
   uint32_t has_fbfetch_coherent : 1;
   uint32_t has_cull_distance : 1;
   uint32_t has_nopersective : 1;
   uint32_t has_texture_shadow_lod : 1;
   uint32_t has_vs_layer : 1;
   uint32_t has_vs_viewport_index : 1;
};

struct vrend_context;

#define SHADER_MAX_STRINGS 3
#define SHADER_STRING_VER_EXT 0
#define SHADER_STRING_HDR 1

bool vrend_convert_shader(const struct vrend_context *rctx,
                          const struct vrend_shader_cfg *cfg,
                          const struct tgsi_token *tokens,
                          uint32_t req_local_mem,
                          const struct vrend_shader_key *key,
                          struct vrend_shader_info *sinfo,
                          struct vrend_variable_shader_info *var_sinfo,
                          struct vrend_strarray *shader);

const char *vrend_shader_samplertypeconv(bool use_gles, int sampler_type);

char vrend_shader_samplerreturnconv(enum tgsi_return_type type);

int vrend_shader_lookup_sampler_array(const struct vrend_shader_info *sinfo, int index);

bool vrend_shader_create_passthrough_tcs(const struct vrend_context *ctx,
                                         const struct vrend_shader_cfg *cfg,
                                         const struct tgsi_token *vs_info,
                                         const struct vrend_shader_key *key,
                                         const float tess_factors[6],
                                         struct vrend_shader_info *sinfo,
                                         struct vrend_strarray *shader,
                                         uint8_t vertices_per_patch);

bool vrend_shader_needs_alpha_func(const struct vrend_shader_key *key);

bool vrend_shader_query_separable_program(const struct tgsi_token *tokens,
                                          const struct vrend_shader_cfg *cfg);

static inline bool vrend_shader_sampler_views_mask_get(
   const uint64_t mask[static VREND_SHADER_SAMPLER_VIEWS_MASK_LENGTH],
   int index)
{
   return (mask[index / 64] >> (index % 64)) & 1;
}

static inline void vrend_shader_sampler_views_mask_set(
   uint64_t mask[static VREND_SHADER_SAMPLER_VIEWS_MASK_LENGTH],
   int index)
{
   mask[index / 64] |= 1ull << (index % 64);
}

#endif
