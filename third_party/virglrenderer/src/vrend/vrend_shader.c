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

#include "tgsi/tgsi_info.h"
#include "tgsi/tgsi_iterate.h"
#include "tgsi/tgsi_scan.h"
#include "util/u_memory.h"
#include "util/u_math.h"
#include <string.h>
#include <stdint.h>
#include <stdio.h>
#include <math.h>
#include <errno.h>
#include "vrend_shader.h"
#include "vrend_debug.h"

#include "vrend_strbuf.h"

/* start convert of tgsi to glsl */

#define INVARI_PREFIX "invariant"

#define SHADER_REQ_NONE 0
#define SHADER_REQ_SAMPLER_RECT       (1ULL << 0)
#define SHADER_REQ_CUBE_ARRAY         (1ULL << 1)
#define SHADER_REQ_INTS               (1ULL << 2)
#define SHADER_REQ_SAMPLER_MS         (1ULL << 3)
#define SHADER_REQ_INSTANCE_ID        (1ULL << 4)
#define SHADER_REQ_LODQ               (1ULL << 5)
#define SHADER_REQ_TXQ_LEVELS         (1ULL << 6)
#define SHADER_REQ_TG4                (1ULL << 7)
#define SHADER_REQ_VIEWPORT_IDX       (1ULL << 8)
#define SHADER_REQ_STENCIL_EXPORT     (1ULL << 9)
#define SHADER_REQ_LAYER              (1ULL << 10)
#define SHADER_REQ_SAMPLE_SHADING     (1ULL << 11)
#define SHADER_REQ_GPU_SHADER5        (1ULL << 12)
#define SHADER_REQ_DERIVATIVE_CONTROL (1ULL << 13)
#define SHADER_REQ_FP64               (1ULL << 14)
#define SHADER_REQ_IMAGE_LOAD_STORE   (1ULL << 15)
#define SHADER_REQ_ES31_COMPAT        (1ULL << 16)
#define SHADER_REQ_IMAGE_SIZE         (1ULL << 17)
#define SHADER_REQ_TXQS               (1ULL << 18)
#define SHADER_REQ_FBFETCH            (1ULL << 19)
#define SHADER_REQ_SHADER_CLOCK       (1ULL << 20)
#define SHADER_REQ_PSIZE              (1ULL << 21)
#define SHADER_REQ_IMAGE_ATOMIC       (1ULL << 22)
#define SHADER_REQ_CLIP_DISTANCE      (1ULL << 23)
#define SHADER_REQ_ENHANCED_LAYOUTS   (1ULL << 24)
#define SHADER_REQ_SEPERATE_SHADER_OBJECTS (1ULL << 25)
#define SHADER_REQ_ARRAYS_OF_ARRAYS  (1ULL << 26)
#define SHADER_REQ_SHADER_INTEGER_FUNC (1ULL << 27)
#define SHADER_REQ_SHADER_ATOMIC_FLOAT (1ULL << 28)
#define SHADER_REQ_NV_IMAGE_FORMATS    (1ULL << 29)
#define SHADER_REQ_CONSERVATIVE_DEPTH  (1ULL << 30)
#define SHADER_REQ_SAMPLER_BUF        (1ULL << 31)
#define SHADER_REQ_GEOMETRY_SHADER    (1ULL << 32)
#define SHADER_REQ_BLEND_EQUATION_ADVANCED (1ULL << 33)
#define SHADER_REQ_EXPLICIT_ATTRIB_LOCATION (1ULL << 34)
#define SHADER_REQ_SHADER_NOPERSPECTIVE_INTERPOLATION (1ULL << 35)
#define SHADER_REQ_TEXTURE_SHADOW_LOD (1ULL << 36)
#define SHADER_REQ_AMD_VS_LAYER (1ULL << 37)
#define SHADER_REQ_AMD_VIEWPORT_IDX (1ULL << 38)
#define SHADER_REQ_SHADER_DRAW_PARAMETERS (1ULL << 39)
#define SHADER_REQ_SHADER_GROUP_VOTE      (1ULL << 40)
#define SHADER_REQ_EXPLICIT_UNIFORM_LOCATION (1ULL << 41)

#define FRONT_COLOR_EMITTED (1 << 0)
#define BACK_COLOR_EMITTED  (1 << 1);

#define MAX_VARYING 32

enum vrend_sysval_uniform {
   UNIFORM_WINSYS_ADJUST_Y,
   UNIFORM_CLIP_PLANE,
   UNIFORM_ALPHA_REF_VAL,
   UNIFORM_PSTIPPLE_SAMPLER,
   UNIFORM_DRAWID_BASE
};

enum vec_type {
   VEC_FLOAT = 0,
   VEC_INT = 1,
   VEC_UINT = 2
};

struct vrend_shader_sampler {
   int tgsi_sampler_type;
   enum tgsi_return_type tgsi_sampler_return;
};

struct vrend_shader_table {
   uint64_t key;
   const char *string;
};

struct vrend_shader_image {
   struct tgsi_declaration_image decl;
   enum tgsi_return_type image_return;
   bool vflag;
   bool coherent;
};

#define MAX_IMMEDIATE 1024
struct immed {
   enum tgsi_imm_type type;
   union imm {
      uint32_t ui;
      int32_t i;
      float f;
   } val[4];
};

struct vrend_temp_range {
   int first;
   int last;
   int array_id;
   bool precise_result;
};

struct vrend_shader_io {
   char glsl_name[128];
   struct vrend_shader_io *overlapping_array;
   unsigned sid : 16;
   unsigned first : 16;
   unsigned last : 16;
   unsigned array_id : 10;
   enum tgsi_interpolate_mode interpolate : 4;
   enum tgsi_interpolate_loc location : 2;

   unsigned array_offset : 8;
   enum tgsi_semantic name : 8;
   unsigned stream : 2;
   unsigned usage_mask : 4;
   enum vec_type type : 2;
   unsigned num_components : 3;

   bool invariant : 1;
   bool precise : 1;
   bool glsl_predefined_no_emit : 1;
   bool glsl_no_index : 1;
   bool glsl_gl_block : 1;
   bool override_no_wm : 1;
   bool is_int : 1;
   bool fbfetch_used : 1;
   bool needs_override : 1;
};

struct vrend_io_range {
   struct vrend_shader_io io;
   bool used;
};

struct vrend_glsl_strbufs {
   int indent_level;
   uint8_t required_sysval_uniform_decls;
   struct vrend_strbuf glsl_main;
   struct vrend_strbuf glsl_hdr;
   struct vrend_strbuf glsl_ver_ext;
};

struct vrend_interface_bits {
   uint64_t outputs_expected_mask;
   uint64_t inputs_emitted_mask;
   uint64_t outputs_emitted_mask;
};

struct vrend_generic_ios {
   struct vrend_interface_bits match;
   struct vrend_io_range input_range;
   struct vrend_io_range output_range;
};

struct vrend_texcoord_ios {
   struct vrend_interface_bits match;
};

struct vrend_patch_ios {
   struct vrend_io_range input_range;
   struct vrend_io_range output_range;
};

struct dump_ctx {
   struct tgsi_iterate_context iter;
   const struct vrend_shader_cfg *cfg;
   struct tgsi_shader_info info;
   enum tgsi_processor_type prog_type;
   int size;
   struct vrend_glsl_strbufs glsl_strbufs;
   unsigned instno;

   struct vrend_strbuf src_bufs[TGSI_FULL_MAX_SRC_REGISTERS];
   struct vrend_strbuf dst_bufs[TGSI_FULL_MAX_DST_REGISTERS];

   uint64_t interp_input_mask;
   uint32_t num_inputs;
   uint32_t attrib_input_mask;
   struct vrend_shader_io inputs[64];
   uint32_t num_outputs;
   struct vrend_shader_io outputs[64];
   uint8_t front_back_color_emitted_flags[64];
   uint32_t num_system_values;
   struct vrend_shader_io system_values[32];

   bool guest_sent_io_arrays;
   struct vrend_texcoord_ios texcoord_ios;
   struct vrend_generic_ios generic_ios;
   struct vrend_patch_ios patch_ios;

   uint32_t num_temp_ranges;
   struct vrend_temp_range *temp_ranges;

   struct vrend_shader_sampler samplers[32];
   uint32_t samplers_used;

   uint32_t ssbo_first_binding;
   uint32_t ssbo_used_mask;
   uint32_t ssbo_atomic_mask;
   uint32_t ssbo_array_base;
   uint32_t ssbo_atomic_array_base;
   uint32_t ssbo_integer_mask;
   uint8_t ssbo_memory_qualifier[32];
   int32_t ssbo_last_binding;

   struct vrend_shader_image images[32];
   uint32_t images_used_mask;
   int32_t image_last_binding;

   struct vrend_array *image_arrays;
   uint32_t num_image_arrays;

   struct vrend_array *sampler_arrays;
   uint32_t num_sampler_arrays;

   uint32_t fog_input_mask;
   uint32_t fog_output_mask;

   int num_consts;
   int num_imm;
   struct immed imm[MAX_IMMEDIATE];

   uint32_t req_local_mem;
   bool integer_memory;

   uint32_t ubo_base;
   uint32_t ubo_used_mask;
   int ubo_sizes[32];
   uint32_t num_address;

   uint32_t num_abo;
   int abo_idx[32];
   int abo_sizes[32];
   int abo_offsets[32];

   uint64_t shader_req_bits;
   uint64_t patches_emitted_mask;

   struct pipe_stream_output_info *so;
   char **so_names;
   bool write_so_outputs[PIPE_MAX_SO_OUTPUTS];
   bool write_all_cbufs;
   uint32_t shadow_samp_mask;

   bool fs_lower_left_origin, fs_integer_pixel_center;
   int fs_depth_layout;
   uint32_t fs_blend_equation_advanced;

   bool separable_program;

   int gs_in_prim, gs_out_prim, gs_max_out_verts;
   int gs_num_invocations;

   const struct vrend_shader_key *key;
   int num_in_clip_dist;
   int num_out_clip_dist;
   int fs_uses_clipdist_input;
   int glsl_ver_required;
   int color_in_mask;
   int color_out_mask;
   /* only used when cull is enabled */
   uint8_t num_cull_dist_prop, num_clip_dist_prop;
   bool has_pervertex;
   bool front_face_emitted;

   bool has_clipvertex;
   bool has_clipvertex_so;
   bool write_mul_utemp;
   bool write_mul_itemp;
   bool has_sample_input;
   bool has_noperspective;
   bool early_depth_stencil;
   bool has_file_memory;
   bool force_color_two_side;
   bool gles_use_tex_query_level;
   bool has_pointsize_input;
   bool has_pointsize_output;

   bool has_input_arrays;
   bool has_output_arrays;

   int tcs_vertices_out;
   int tes_prim_mode;
   int tes_spacing;
   int tes_vertex_order;
   int tes_point_mode;
   bool is_last_vertex_stage;
   bool require_dummy_value;

   uint16_t local_cs_block_size[3];
};

static const struct vrend_shader_table shader_req_table[] = {
    { SHADER_REQ_SAMPLER_RECT, "ARB_texture_rectangle" },
    { SHADER_REQ_CUBE_ARRAY, "ARB_texture_cube_map_array" },
    { SHADER_REQ_INTS, "ARB_shader_bit_encoding" },
    { SHADER_REQ_SAMPLER_MS, "ARB_texture_multisample" },
    { SHADER_REQ_INSTANCE_ID, "ARB_draw_instanced" },
    { SHADER_REQ_LODQ, "ARB_texture_query_lod" },
    { SHADER_REQ_TXQ_LEVELS, "ARB_texture_query_levels" },
    { SHADER_REQ_TG4, "ARB_texture_gather" },
    { SHADER_REQ_VIEWPORT_IDX, "ARB_viewport_array" },
    { SHADER_REQ_STENCIL_EXPORT, "ARB_shader_stencil_export" },
    { SHADER_REQ_LAYER, "ARB_fragment_layer_viewport" },
    { SHADER_REQ_SAMPLE_SHADING, "ARB_sample_shading" },
    { SHADER_REQ_GPU_SHADER5, "ARB_gpu_shader5" },
    { SHADER_REQ_DERIVATIVE_CONTROL, "ARB_derivative_control" },
    { SHADER_REQ_FP64, "ARB_gpu_shader_fp64" },
    { SHADER_REQ_IMAGE_LOAD_STORE, "ARB_shader_image_load_store" },
    { SHADER_REQ_ES31_COMPAT, "ARB_ES3_1_compatibility" },
    { SHADER_REQ_IMAGE_SIZE, "ARB_shader_image_size" },
    { SHADER_REQ_TXQS, "ARB_shader_texture_image_samples" },
    { SHADER_REQ_FBFETCH, "EXT_shader_framebuffer_fetch" },
    { SHADER_REQ_SHADER_CLOCK, "ARB_shader_clock" },
    { SHADER_REQ_SHADER_INTEGER_FUNC, "MESA_shader_integer_functions" },
    { SHADER_REQ_SHADER_ATOMIC_FLOAT, "NV_shader_atomic_float"},
    { SHADER_REQ_CONSERVATIVE_DEPTH, "ARB_conservative_depth"},
    {SHADER_REQ_BLEND_EQUATION_ADVANCED, "KHR_blend_equation_advanced"},
    { SHADER_REQ_TEXTURE_SHADOW_LOD, "EXT_texture_shadow_lod"},
    { SHADER_REQ_AMD_VS_LAYER, "AMD_vertex_shader_layer"},
    { SHADER_REQ_AMD_VIEWPORT_IDX, "AMD_vertex_shader_viewport_index"},
    { SHADER_REQ_SHADER_DRAW_PARAMETERS, "ARB_shader_draw_parameters"},
    { SHADER_REQ_SHADER_GROUP_VOTE, "ARB_shader_group_vote"},
    { SHADER_REQ_EXPLICIT_UNIFORM_LOCATION, "ARB_explicit_uniform_location"},
};

enum vrend_type_qualifier {
   TYPE_CONVERSION_NONE = 0,
   FLOAT = 1,
   VEC2 = 2,
   VEC3 = 3,
   VEC4 = 4,
   INT = 5,
   IVEC2 = 6,
   IVEC3 = 7,
   IVEC4 = 8,
   UINT = 9,
   UVEC2 = 10,
   UVEC3 = 11,
   UVEC4 = 12,
   FLOAT_BITS_TO_UINT = 13,
   UINT_BITS_TO_FLOAT = 14,
   FLOAT_BITS_TO_INT = 15,
   INT_BITS_TO_FLOAT = 16,
   DOUBLE = 17,
   DVEC2 = 18,
};

struct dest_info {
  enum vrend_type_qualifier dtypeprefix;
  enum vrend_type_qualifier dstconv;
  enum vrend_type_qualifier udstconv;
  enum vrend_type_qualifier idstconv;
  bool dst_override_no_wm[TGSI_FULL_MAX_DST_REGISTERS];
  int32_t dest_index;
};

struct source_info {
   enum vrend_type_qualifier svec4;
   int32_t sreg_index;
   bool tg4_has_component;
   bool override_no_wm[TGSI_FULL_MAX_SRC_REGISTERS];
   bool override_no_cast[TGSI_FULL_MAX_SRC_REGISTERS];
   int imm_value;
};

static const struct vrend_shader_table conversion_table[] =
{
   {TYPE_CONVERSION_NONE, ""},
   {FLOAT, "float"},
   {VEC2, "vec2"},
   {VEC3, "vec3"},
   {VEC4, "vec4"},
   {INT, "int"},
   {IVEC2, "ivec2"},
   {IVEC3, "ivec3"},
   {IVEC4, "ivec4"},
   {UINT, "uint"},
   {UVEC2, "uvec2"},
   {UVEC3, "uvec3"},
   {UVEC4, "uvec4"},
   {FLOAT_BITS_TO_UINT, "floatBitsToUint"},
   {UINT_BITS_TO_FLOAT, "uintBitsToFloat"},
   {FLOAT_BITS_TO_INT, "floatBitsToInt"},
   {INT_BITS_TO_FLOAT, "intBitsToFloat"},
   {DOUBLE, "double"},
   {DVEC2, "dvec2"},
};

enum io_type {
   io_in,
   io_out
};

enum io_decl_type {
   decl_plain,
   decl_block
};

static
void vrend_shader_write_io_as_src(struct vrend_strbuf *buf,
                                  const  char *arrayname,
                                  const struct vrend_shader_io *io,
                                  const struct tgsi_full_src_register *src,
                                  enum io_decl_type decl_type);

static
void vrend_shader_write_io_as_dst(struct vrend_strbuf *buf,
                                  const  char *arrayname,
                                  const struct vrend_shader_io *io,
                                  const struct tgsi_full_dst_register *src,
                                  enum io_decl_type decl_type);

/* We prefer arrays of arrays, but if this is not available then TCS, GEOM, and TES
 * inputs must be blocks, but FS input should not because interpolateAt* doesn't
 * support dereferencing block members. */
static inline bool prefer_generic_io_block(const struct dump_ctx *ctx, enum io_type io)
{
   if (ctx->cfg->has_arrays_of_arrays && !ctx->cfg->use_gles)
      return false;

   switch (ctx->prog_type) {
   case TGSI_PROCESSOR_FRAGMENT:
      return false;

   case TGSI_PROCESSOR_TESS_CTRL:
      return true;

   case TGSI_PROCESSOR_TESS_EVAL:
      return io == io_in ?  true : (ctx->key->gs_present ? true : false);

   case TGSI_PROCESSOR_GEOMETRY:
      return io == io_in;

   case TGSI_PROCESSOR_VERTEX:
      if (io == io_in)
         return false;
      return (ctx->key->gs_present || ctx->key->tes_present);

   default:
      return false;
   }
}

static inline const char *get_string(enum vrend_type_qualifier key)
{
   if (key >= ARRAY_SIZE(conversion_table)) {
      printf("Unable to find the correct conversion\n");
      return conversion_table[TYPE_CONVERSION_NONE].string;
   }

   return conversion_table[key].string;
}

static inline const char *get_wm_string(unsigned wm)
{
   switch(wm) {
   case TGSI_WRITEMASK_NONE:
      return "";
   case TGSI_WRITEMASK_X:
      return ".x";
   case TGSI_WRITEMASK_XY:
      return ".xy";
   case TGSI_WRITEMASK_XYZ:
      return ".xyz";
   case TGSI_WRITEMASK_W:
      return ".w";
   default:
      printf("Unable to unknown writemask\n");
      return "";
   }
}

static inline const char *get_swizzle_string(uint8_t swizzle)
{
   switch (swizzle) {
   case PIPE_SWIZZLE_X: return ".x";
   case PIPE_SWIZZLE_Y: return ".y";
   case PIPE_SWIZZLE_Z: return ".z";
   case PIPE_SWIZZLE_W: return ".w";
   case PIPE_SWIZZLE_0:
   case PIPE_SWIZZLE_1: return ".0";
   default:
      assert(0);
      return "";
   }
}

const char *get_internalformat_string(int virgl_format, enum tgsi_return_type *stype);

static inline const char *tgsi_proc_to_prefix(int shader_type)
{
   switch (shader_type) {
   case TGSI_PROCESSOR_VERTEX: return "vs";
   case TGSI_PROCESSOR_FRAGMENT: return "fs";
   case TGSI_PROCESSOR_GEOMETRY: return "gs";
   case TGSI_PROCESSOR_TESS_CTRL: return "tc";
   case TGSI_PROCESSOR_TESS_EVAL: return "te";
   case TGSI_PROCESSOR_COMPUTE: return "cs";
   default:
      return NULL;
   };
}

static inline const char *prim_to_name(int prim)
{
   switch (prim) {
   case PIPE_PRIM_POINTS: return "points";
   case PIPE_PRIM_LINES: return "lines";
   case PIPE_PRIM_LINE_STRIP: return "line_strip";
   case PIPE_PRIM_LINES_ADJACENCY: return "lines_adjacency";
   case PIPE_PRIM_TRIANGLES: return "triangles";
   case PIPE_PRIM_TRIANGLE_STRIP: return "triangle_strip";
   case PIPE_PRIM_TRIANGLES_ADJACENCY: return "triangles_adjacency";
   case PIPE_PRIM_QUADS: return "quads";
   default: return "UNKNOWN";
   };
}

static inline const char *prim_to_tes_name(int prim)
{
   switch (prim) {
   case PIPE_PRIM_QUADS: return "quads";
   case PIPE_PRIM_TRIANGLES: return "triangles";
   case PIPE_PRIM_LINES: return "isolines";
   default: return "UNKNOWN";
   }
}

static inline const char *blend_to_name(enum gl_advanced_blend_mode blend)
{
   switch (blend) {
   case BLEND_MULTIPLY: return "multiply";
   case BLEND_SCREEN: return "screen";
   case BLEND_OVERLAY: return "overlay";
   case BLEND_DARKEN: return "darken";
   case BLEND_LIGHTEN: return "lighten";
   case BLEND_COLORDODGE: return "colordodge";
   case BLEND_COLORBURN: return "colorburn";
   case BLEND_HARDLIGHT: return "hardlight";
   case BLEND_SOFTLIGHT: return "softlight";
   case BLEND_DIFFERENCE: return "difference";
   case BLEND_EXCLUSION: return "exclusion";
   case BLEND_HSL_HUE: return "hsl_hue";
   case BLEND_HSL_SATURATION: return "hsl_saturation";
   case BLEND_HSL_COLOR: return "hsl_color";
   case BLEND_HSL_LUMINOSITY: return "hsl_luminosity";
   case BLEND_ALL: return "all_equations";
   default: return "UNKNOWN";
   };
}

static const char *get_spacing_string(int spacing)
{
   switch (spacing) {
   case PIPE_TESS_SPACING_FRACTIONAL_ODD:
      return "fractional_odd_spacing";
   case PIPE_TESS_SPACING_FRACTIONAL_EVEN:
      return "fractional_even_spacing";
   case PIPE_TESS_SPACING_EQUAL:
   default:
      return "equal_spacing";
   }
}

static inline int gs_input_prim_to_size(int prim)
{
   switch (prim) {
   case PIPE_PRIM_POINTS: return 1;
   case PIPE_PRIM_LINES: return 2;
   case PIPE_PRIM_LINES_ADJACENCY: return 4;
   case PIPE_PRIM_TRIANGLES: return 3;
   case PIPE_PRIM_TRIANGLES_ADJACENCY: return 6;
   default: return -1;
   };
}

static inline bool fs_emit_layout(const struct dump_ctx *ctx)
{
   if (ctx->fs_integer_pixel_center)
      return true;

   /* if fs_lower_left_origin is 0 and lower_left_origin is 0 - emit origin_upper_left,
      if fs_lower_left_origin is 0 and lower_left_origin is 1 - emit nothing (lower)
      if fs_lower_left_origin is 1 and lower_left_origin is 0 - emit nothing (lower)
      if fs_lower_left_origin is 1 and lower_left_origin is 1 - emit origin_upper_left */
   return ctx->fs_lower_left_origin == ctx->key->fs.lower_left_origin;
}

static const char *get_stage_input_name_prefix(const struct dump_ctx *ctx, int processor)
{
   const char *name_prefix;
   switch (processor) {
   case TGSI_PROCESSOR_FRAGMENT:
      if (ctx->key->gs_present)
         name_prefix = "gso";
      else if (ctx->key->tes_present)
         name_prefix = "teo";
      else
         name_prefix = "vso";
      break;
   case TGSI_PROCESSOR_GEOMETRY:
      if (ctx->key->tes_present)
         name_prefix = "teo";
      else
         name_prefix = "vso";
      break;
   case TGSI_PROCESSOR_TESS_EVAL:
      if (ctx->key->tcs_present)
         name_prefix = "tco";
      else
         name_prefix = "vso";
      break;
   case TGSI_PROCESSOR_TESS_CTRL:
       name_prefix = "vso";
       break;
   case TGSI_PROCESSOR_VERTEX:
   default:
      name_prefix = "in";
      break;
   }
   return name_prefix;
}

static const char *get_stage_output_name_prefix(int processor)
{
   const char *name_prefix;
   switch (processor) {
   case TGSI_PROCESSOR_FRAGMENT:
      name_prefix = "fsout";
      break;
   case TGSI_PROCESSOR_GEOMETRY:
      name_prefix = "gso";
      break;
   case TGSI_PROCESSOR_VERTEX:
      name_prefix = "vso";
      break;
   case TGSI_PROCESSOR_TESS_CTRL:
      name_prefix = "tco";
      break;
   case TGSI_PROCESSOR_TESS_EVAL:
      name_prefix = "teo";
      break;
   default:
      name_prefix = "out";
      break;
   }
   return name_prefix;
}

static int require_glsl_ver(const struct dump_ctx *ctx, int glsl_ver)
{
   return glsl_ver > ctx->glsl_ver_required ? glsl_ver : ctx->glsl_ver_required;
}

static void emit_indent(struct vrend_glsl_strbufs *glsl_strbufs)
{
   if (glsl_strbufs->indent_level > 0) {
      /* very high levels of indentation doesn't improve readability */
      int indent_level = MIN2(glsl_strbufs->indent_level, 15);
      char buf[16];
      memset(buf, '\t', indent_level);
      buf[indent_level] = '\0';
      strbuf_append(&glsl_strbufs->glsl_main, buf);
   }
}

static void emit_buf(struct vrend_glsl_strbufs *glsl_strbufs, const char *buf)
{
   emit_indent(glsl_strbufs);
   strbuf_append(&glsl_strbufs->glsl_main, buf);
}

static void indent_buf(struct vrend_glsl_strbufs *glsl_strbufs)
{
   glsl_strbufs->indent_level++;
}

static void outdent_buf(struct vrend_glsl_strbufs *glsl_strbufs)
{
   if (glsl_strbufs->indent_level <= 0) {
      strbuf_set_error(&glsl_strbufs->glsl_main);
      return;
   }
   glsl_strbufs->indent_level--;
}

static void set_buf_error(struct vrend_glsl_strbufs *glsl_strbufs)
{
   strbuf_set_error(&glsl_strbufs->glsl_main);
}

__attribute__((format(printf, 2, 3)))
static void emit_buff(struct vrend_glsl_strbufs *glsl_strbufs, const char *fmt, ...)
{
   va_list va;
   va_start(va, fmt);
   emit_indent(glsl_strbufs);
   strbuf_vappendf(&glsl_strbufs->glsl_main, fmt, va);
   va_end(va);
}

static void emit_hdr(struct vrend_glsl_strbufs *glsl_strbufs, const char *buf)
{
   strbuf_append(&glsl_strbufs->glsl_hdr, buf);
}

static void set_hdr_error(struct vrend_glsl_strbufs *glsl_strbufs)
{
   strbuf_set_error(&glsl_strbufs->glsl_hdr);
}

__attribute__((format(printf, 2, 3)))
static void emit_hdrf(struct vrend_glsl_strbufs *glsl_strbufs, const char *fmt, ...)
{
   va_list va;
   va_start(va, fmt);
   strbuf_vappendf(&glsl_strbufs->glsl_hdr, fmt, va);
   va_end(va);
}

static void emit_ver_ext(struct vrend_glsl_strbufs *glsl_strbufs, const char *buf)
{
   strbuf_append(&glsl_strbufs->glsl_ver_ext, buf);
}

__attribute__((format(printf, 2, 3)))
static void emit_ver_extf(struct vrend_glsl_strbufs *glsl_strbufs, const char *fmt, ...)
{
   va_list va;
   va_start(va, fmt);
   strbuf_vappendf(&glsl_strbufs->glsl_ver_ext, fmt, va);
   va_end(va);
}

static bool allocate_temp_range(struct vrend_temp_range **temp_ranges, uint32_t *num_temp_ranges, int first, int last,
                                int array_id)
{
   int idx = *num_temp_ranges;

   if (array_id > 0) {

      *temp_ranges = realloc(*temp_ranges, sizeof(struct vrend_temp_range) * (idx + 1));
      if (unlikely(!*temp_ranges))
         return false;

      (*temp_ranges)[idx].first = first;
      (*temp_ranges)[idx].last = last;
      (*temp_ranges)[idx].array_id = array_id;
      (*temp_ranges)[idx].precise_result = false;
      (*num_temp_ranges)++;
   } else {
      int ntemps = last - first + 1;
      *temp_ranges = realloc(*temp_ranges, sizeof(struct vrend_temp_range) * (idx + ntemps));
      if (unlikely(!*temp_ranges))
         return false;

      for (int i = 0; i < ntemps; ++i) {
         (*temp_ranges)[idx + i].first = first + i;
         (*temp_ranges)[idx + i].last = first + i;
         (*temp_ranges)[idx + i].array_id = 0;
         (*temp_ranges)[idx + i].precise_result = false;
      }
      (*num_temp_ranges) += ntemps;


   }
   return true;
}

static struct vrend_temp_range *find_temp_range(const struct dump_ctx *ctx, int index)
{
   uint32_t i;
   for (i = 0; i < ctx->num_temp_ranges; i++) {
      if (index >= ctx->temp_ranges[i].first &&
          index <= ctx->temp_ranges[i].last)
         return &ctx->temp_ranges[i];
   }
   return NULL;
}

static bool samplertype_is_shadow(int sampler_type)
{
   switch (sampler_type) {
   case TGSI_TEXTURE_SHADOW1D:
   case TGSI_TEXTURE_SHADOW1D_ARRAY:
   case TGSI_TEXTURE_SHADOW2D:
   case TGSI_TEXTURE_SHADOWRECT:
   case TGSI_TEXTURE_SHADOW2D_ARRAY:
   case TGSI_TEXTURE_SHADOWCUBE:
   case TGSI_TEXTURE_SHADOWCUBE_ARRAY:
      return true;
   default:
      return false;
   }
}

static uint32_t samplertype_to_req_bits(int sampler_type)
{

   switch (sampler_type) {
   case TGSI_TEXTURE_SHADOWCUBE_ARRAY:
   case TGSI_TEXTURE_CUBE_ARRAY:
      return SHADER_REQ_CUBE_ARRAY;
   case TGSI_TEXTURE_2D_MSAA:
   case TGSI_TEXTURE_2D_ARRAY_MSAA:
      return SHADER_REQ_SAMPLER_MS;
   case TGSI_TEXTURE_BUFFER:
      return SHADER_REQ_SAMPLER_BUF;
   case TGSI_TEXTURE_SHADOWRECT:
   case TGSI_TEXTURE_RECT:
      return SHADER_REQ_SAMPLER_RECT;
   default:
      return 0;
   }
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static bool add_images(struct dump_ctx *ctx, int first, int last,
                       const struct tgsi_declaration_image *img_decl)
{
   int i;

   const struct util_format_description *descr = util_format_description(img_decl->Format);
   if (descr->nr_channels == 2 &&
       descr->swizzle[0] == PIPE_SWIZZLE_X &&
       descr->swizzle[1] == PIPE_SWIZZLE_Y &&
       descr->swizzle[2] == PIPE_SWIZZLE_0 &&
       descr->swizzle[3] == PIPE_SWIZZLE_1) {
      ctx->shader_req_bits |= SHADER_REQ_NV_IMAGE_FORMATS;
   } else if (img_decl->Format == PIPE_FORMAT_R11G11B10_FLOAT ||
              img_decl->Format == PIPE_FORMAT_R10G10B10A2_UINT ||
              img_decl->Format == PIPE_FORMAT_R10G10B10A2_UNORM ||
              img_decl->Format == PIPE_FORMAT_R16G16B16A16_UNORM||
              img_decl->Format == PIPE_FORMAT_R16G16B16A16_SNORM)
      ctx->shader_req_bits |= SHADER_REQ_NV_IMAGE_FORMATS;
   else if (descr->nr_channels == 1 &&
            descr->swizzle[0] == PIPE_SWIZZLE_X &&
            descr->swizzle[1] == PIPE_SWIZZLE_0 &&
            descr->swizzle[2] == PIPE_SWIZZLE_0 &&
            descr->swizzle[3] == PIPE_SWIZZLE_1 &&
            (descr->channel[0].size == 8 || descr->channel[0].size ==16))
      ctx->shader_req_bits |= SHADER_REQ_NV_IMAGE_FORMATS;

   for (i = first; i <= last; i++) {
      ctx->images[i].decl = *img_decl;
      ctx->images[i].vflag = false;
      ctx->images_used_mask |= (1 << i);

      if (!samplertype_is_shadow(ctx->images[i].decl.Resource))
         ctx->shader_req_bits |= samplertype_to_req_bits(ctx->images[i].decl.Resource);
   }

   if (ctx->info.indirect_files & (1 << TGSI_FILE_IMAGE)) {
      if (ctx->num_image_arrays) {
         struct vrend_array *last_array = &ctx->image_arrays[ctx->num_image_arrays - 1];
         /*
          * If this set of images is consecutive to the last array,
          * and has compatible return and decls, then increase the array size.
          */
         if ((last_array->first + last_array->array_size == first) &&
             !memcmp(&ctx->images[last_array->first].decl, &ctx->images[first].decl, sizeof(ctx->images[first].decl)) &&
             ctx->images[last_array->first].image_return == ctx->images[first].image_return) {
            last_array->array_size += last - first + 1;
            if (ctx->image_last_binding < last)
               ctx->image_last_binding = last;
            return true;
         }
      }

      /* allocate a new image array for this range of images */
      ctx->num_image_arrays++;
      ctx->image_arrays = realloc(ctx->image_arrays, sizeof(struct vrend_array) * ctx->num_image_arrays);
      if (!ctx->image_arrays)
         return false;
      ctx->image_arrays[ctx->num_image_arrays - 1].first = first;
      ctx->image_arrays[ctx->num_image_arrays - 1].array_size = last - first + 1;
   }

   if (ctx->image_last_binding < last)
      ctx->image_last_binding = last;
   return true;
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static bool add_sampler_array(struct dump_ctx *ctx, int first, int last)
{
   int idx = ctx->num_sampler_arrays;
   ctx->num_sampler_arrays++;
   ctx->sampler_arrays = realloc(ctx->sampler_arrays, sizeof(struct vrend_array) * ctx->num_sampler_arrays);
   if (!ctx->sampler_arrays)
      return false;

   ctx->sampler_arrays[idx].first = first;
   ctx->sampler_arrays[idx].array_size = last - first + 1;
   return true;
}

static int lookup_sampler_array(const struct dump_ctx *ctx, int index)
{
   uint32_t i;
   for (i = 0; i < ctx->num_sampler_arrays; i++) {
      int last = ctx->sampler_arrays[i].first + ctx->sampler_arrays[i].array_size - 1;
      if (index >= ctx->sampler_arrays[i].first &&
          index <= last) {
         return ctx->sampler_arrays[i].first;
      }
   }
   return -1;
}

int vrend_shader_lookup_sampler_array(const struct vrend_shader_info *sinfo, int index)
{
   int i;
   for (i = 0; i < sinfo->num_sampler_arrays; i++) {
      int last = sinfo->sampler_arrays[i].first + sinfo->sampler_arrays[i].array_size - 1;
      if (index >= sinfo->sampler_arrays[i].first &&
          index <= last) {
         return sinfo->sampler_arrays[i].first;
      }
   }
   return -1;
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static bool add_samplers(struct dump_ctx *ctx, int first, int last, int sview_type, enum tgsi_return_type sview_rtype)
{
   if (sview_rtype == TGSI_RETURN_TYPE_SINT ||
       sview_rtype == TGSI_RETURN_TYPE_UINT)
      ctx->shader_req_bits |= SHADER_REQ_INTS;

   for (int i = first; i <= last; i++) {
      ctx->samplers[i].tgsi_sampler_return = sview_rtype;
      ctx->samplers[i].tgsi_sampler_type = sview_type;
   }

   if (ctx->info.indirect_files & (1 << TGSI_FILE_SAMPLER)) {
      if (ctx->num_sampler_arrays) {
         struct vrend_array *last_array = &ctx->sampler_arrays[ctx->num_sampler_arrays - 1];
         if ((last_array->first + last_array->array_size == first) &&
             ctx->samplers[last_array->first].tgsi_sampler_type == sview_type &&
             ctx->samplers[last_array->first].tgsi_sampler_return == sview_rtype) {
            last_array->array_size += last - first + 1;
            return true;
         }
      }

      /* allocate a new image array for this range of images */
      return add_sampler_array(ctx, first, last);
   }
   return true;
}

typedef enum
{
   VARYING_SLOT_POS,
   VARYING_SLOT_COL0, /* COL0 and COL1 must be contiguous */
   VARYING_SLOT_COL1,
   VARYING_SLOT_FOGC,
   VARYING_SLOT_TEX0, /* TEX0-TEX7 must be contiguous */
   VARYING_SLOT_TEX1,
   VARYING_SLOT_TEX2,
   VARYING_SLOT_TEX3,
   VARYING_SLOT_TEX4,
   VARYING_SLOT_TEX5,
   VARYING_SLOT_TEX6,
   VARYING_SLOT_TEX7,
   VARYING_SLOT_PSIZ, /* Does not appear in FS */
   VARYING_SLOT_BFC0, /* Does not appear in FS */
   VARYING_SLOT_BFC1, /* Does not appear in FS */
   VARYING_SLOT_EDGE, /* Does not appear in FS */
   VARYING_SLOT_CLIP_VERTEX, /* Does not appear in FS */
   VARYING_SLOT_CLIP_DIST0,
   VARYING_SLOT_CLIP_DIST1,
   VARYING_SLOT_CULL_DIST0,
   VARYING_SLOT_CULL_DIST1,
   VARYING_SLOT_PRIMITIVE_ID, /* Does not appear in VS */
   VARYING_SLOT_LAYER, /* Appears as VS or GS output */
   VARYING_SLOT_VIEWPORT, /* Appears as VS or GS output */
   VARYING_SLOT_FACE, /* FS only */
   VARYING_SLOT_PNTC, /* FS only */
   VARYING_SLOT_TESS_LEVEL_OUTER, /* Only appears as TCS output. */
   VARYING_SLOT_TESS_LEVEL_INNER, /* Only appears as TCS output. */
   VARYING_SLOT_BOUNDING_BOX0, /* Only appears as TCS output. */
   VARYING_SLOT_BOUNDING_BOX1, /* Only appears as TCS output. */
   VARYING_SLOT_VIEW_INDEX,
   VARYING_SLOT_VIEWPORT_MASK, /* Does not appear in FS */
   VARYING_SLOT_PRIMITIVE_SHADING_RATE = VARYING_SLOT_FACE, /* Does not appear in FS. */

   VARYING_SLOT_PRIMITIVE_COUNT = VARYING_SLOT_TESS_LEVEL_OUTER, /* Only appears in MESH. */
   VARYING_SLOT_PRIMITIVE_INDICES = VARYING_SLOT_TESS_LEVEL_INNER, /* Only appears in MESH. */
   VARYING_SLOT_TASK_COUNT = VARYING_SLOT_BOUNDING_BOX0, /* Only appears in TASK. */

   VARYING_SLOT_VAR0 = 32, /* First generic varying slot */
   /* the remaining are simply for the benefit of gl_varying_slot_name()
    * and not to be construed as an upper bound:
    */
   VARYING_SLOT_VAR1,
   VARYING_SLOT_VAR2,
   VARYING_SLOT_VAR3,
   VARYING_SLOT_VAR4,
   VARYING_SLOT_VAR5,
   VARYING_SLOT_VAR6,
   VARYING_SLOT_VAR7,
   VARYING_SLOT_VAR8,
   VARYING_SLOT_VAR9,
   VARYING_SLOT_VAR10,
   VARYING_SLOT_VAR11,
   VARYING_SLOT_VAR12,
   VARYING_SLOT_VAR13,
   VARYING_SLOT_VAR14,
   VARYING_SLOT_VAR15,
   VARYING_SLOT_VAR16,
   VARYING_SLOT_VAR17,
   VARYING_SLOT_VAR18,
   VARYING_SLOT_VAR19,
   VARYING_SLOT_VAR20,
   VARYING_SLOT_VAR21,
   VARYING_SLOT_VAR22,
   VARYING_SLOT_VAR23,
   VARYING_SLOT_VAR24,
   VARYING_SLOT_VAR25,
   VARYING_SLOT_VAR26,
   VARYING_SLOT_VAR27,
   VARYING_SLOT_VAR28,
   VARYING_SLOT_VAR29,
   VARYING_SLOT_VAR30,
   VARYING_SLOT_VAR31,
   /* Account for the shift without CAP_TEXCOORD in mesa*/
   VARYING_SLOT_PATCH0 = VARYING_SLOT_VAR31 + 9
} gl_varying_slot;

static uint32_t
varying_bit_from_semantic_and_index(enum tgsi_semantic semantic, int index)
{
   switch (semantic) {
   case TGSI_SEMANTIC_POSITION:
      return VARYING_SLOT_POS;
   case TGSI_SEMANTIC_COLOR:
      if (index == 0)
         return VARYING_SLOT_COL0;
      else
         return VARYING_SLOT_COL1;
   case TGSI_SEMANTIC_BCOLOR:
      if (index == 0)
         return VARYING_SLOT_BFC0;
      else
         return VARYING_SLOT_BFC1;
   case TGSI_SEMANTIC_FOG:
      return VARYING_SLOT_FOGC;
   case TGSI_SEMANTIC_PSIZE:
      return VARYING_SLOT_PSIZ;
   case TGSI_SEMANTIC_GENERIC:
      if (unlikely(index >= MAX_VARYING)) {
         virgl_warn("Warning: Out of range TGSI_SEMANTIC_GENERIC index: %d\n", index);
         return VARYING_SLOT_VAR0;
      }

      return VARYING_SLOT_VAR0 + index;
   case TGSI_SEMANTIC_FACE:
      return VARYING_SLOT_FACE;
   case TGSI_SEMANTIC_EDGEFLAG:
      return VARYING_SLOT_EDGE;
   case TGSI_SEMANTIC_PRIMID:
      return VARYING_SLOT_PRIMITIVE_ID;
   case TGSI_SEMANTIC_CLIPDIST:
      if (index == 0)
         return VARYING_SLOT_CLIP_DIST0;
      else
         return VARYING_SLOT_CLIP_DIST1;
   case TGSI_SEMANTIC_CLIPVERTEX:
      return VARYING_SLOT_CLIP_VERTEX;
   case TGSI_SEMANTIC_TEXCOORD:
      if (unlikely(index >= 8)) {
         virgl_warn("Warning: Out of range TGSI_SEMANTIC_TEXCOORD index: %d\n", index);
         return VARYING_SLOT_TEX0;
      }

      return (VARYING_SLOT_TEX0 + index);
   case TGSI_SEMANTIC_PCOORD:
      return VARYING_SLOT_PNTC;
   case TGSI_SEMANTIC_VIEWPORT_INDEX:
      return VARYING_SLOT_VIEWPORT;
   case TGSI_SEMANTIC_LAYER:
      return VARYING_SLOT_LAYER;
   case TGSI_SEMANTIC_TESSINNER:
      return VARYING_SLOT_TESS_LEVEL_INNER;
   case TGSI_SEMANTIC_TESSOUTER:
      return VARYING_SLOT_TESS_LEVEL_OUTER;
   case TGSI_SEMANTIC_PATCH:
      if (unlikely(index >= MAX_VARYING)) {
         virgl_warn("Warning: Out of range TGSI_SEMANTIC_PATCH index: %d\n", index);
         return VARYING_SLOT_PATCH0;
      }

      return VARYING_SLOT_PATCH0 + index;
   default:
      virgl_warn("Warning: Bad TGSI semantic: %d/%d\n", semantic, index);
      return 0;
   }
}

static struct vrend_array *lookup_image_array_ptr(const struct dump_ctx *ctx, int index)
{
   uint32_t i;
   for (i = 0; i < ctx->num_image_arrays; i++) {
      if (index >= ctx->image_arrays[i].first &&
          index <= ctx->image_arrays[i].first + ctx->image_arrays[i].array_size - 1) {
         return &ctx->image_arrays[i];
      }
   }
   return NULL;
}

static int lookup_image_array(const struct dump_ctx *ctx, int index)
{
   struct vrend_array *image = lookup_image_array_ptr(ctx, index);
   return image ? image->first : -1;
}

static bool
iter_decls(struct tgsi_iterate_context *iter,
           struct tgsi_full_declaration *decl)
{
   struct dump_ctx *ctx = (struct dump_ctx *)iter;
   switch (decl->Declaration.File) {
   case TGSI_FILE_INPUT:
      /* Tag used semantic fog inputs */
      if (decl->Semantic.Name == TGSI_SEMANTIC_FOG) {
         ctx->fog_input_mask |= (1 << decl->Semantic.Index);
      }

      if (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT) {
         if (ctx->num_inputs >= ARRAY_SIZE(ctx->inputs)) {
               virgl_error( "Number of inputs exceeded, max is %zd\n", ARRAY_SIZE(ctx->inputs));
            return false;
         }

         for (uint32_t j = 0; j < ctx->num_inputs; j++) {
            if (ctx->inputs[j].name == decl->Semantic.Name &&
                ctx->inputs[j].sid == decl->Semantic.Index &&
                ctx->inputs[j].first == decl->Range.First)
                  return true;
         }
         ctx->inputs[ctx->num_inputs].name = decl->Semantic.Name;
         ctx->inputs[ctx->num_inputs].first = decl->Range.First;
         ctx->inputs[ctx->num_inputs].last = decl->Range.Last;
         ctx->num_inputs++;
      }
      break;

   case TGSI_FILE_OUTPUT:
      if (decl->Semantic.Name == TGSI_SEMANTIC_FOG) {
         ctx->fog_output_mask |= (1 << decl->Semantic.Index);
      }
      break;
   case TGSI_FILE_BUFFER:
      if (ctx->ssbo_first_binding > decl->Range.First)
         ctx->ssbo_first_binding = decl->Range.First;
   default:
      break;
   }
   return true;
}

static bool logiop_require_inout(const struct vrend_shader_key *key)
{
   if (!key->fs.logicop_enabled)
      return false;

   switch (key->fs.logicop_func) {
   case PIPE_LOGICOP_CLEAR:
   case PIPE_LOGICOP_SET:
   case PIPE_LOGICOP_COPY:
   case PIPE_LOGICOP_COPY_INVERTED:
      return false;
   default:
      return true;
   }
}

static enum vec_type get_type(uint32_t signed_int_mask,
                              uint32_t unsigned_int_mask,
                              int bit)
{
   if (signed_int_mask & (1 << bit))
      return VEC_INT;
   else if (unsigned_int_mask & (1 << bit))
      return VEC_UINT;
   else
      return VEC_FLOAT;
}

static struct vrend_shader_io *
find_overlapping_io(struct vrend_shader_io io[static 64],
                    uint32_t num_io,
                    const struct tgsi_full_declaration *decl)
{
   for (uint32_t j = 0; j < num_io - 1; j++) {
      if (io[j].interpolate == decl->Interp.Interpolate &&
          io[j].name == decl->Semantic.Name &&
          ((io[j].first <= decl->Range.First &&
            io[j].last > decl->Range.First) ||
           (io[j].first < decl->Range.Last &&
            io[j].last >= decl->Range.Last))) {
         return &io[j];
      }
   }
   return NULL;
}

static void
map_overlapping_io_array(struct vrend_shader_io io[static 64],
                         struct vrend_shader_io *new_io,
                         uint32_t num_io,
                         const struct tgsi_full_declaration *decl)
{
   struct vrend_shader_io *overlap_io = find_overlapping_io(io, num_io, decl);
   if (overlap_io && !overlap_io->needs_override) {
      int delta = new_io->first - overlap_io->first;
      if (delta >= 0) {
         new_io->array_offset = delta;
         new_io->overlapping_array = overlap_io;
         overlap_io->last = MAX2(overlap_io->last, new_io->last);
      } else if (delta < 0) {
         overlap_io->overlapping_array = new_io;
         overlap_io->array_offset = -delta;
         new_io->last = MAX2(overlap_io->last, new_io->last);
      }
      overlap_io->usage_mask |= new_io->usage_mask;
      new_io->usage_mask = overlap_io->usage_mask;
   }
}

struct syvalue_prop_map {
   const char *glsl_name;
   uint64_t required_ext;
   bool override_no_wm;
} sysvalue_map[TGSI_SEMANTIC_COUNT] = {
   [TGSI_SEMANTIC_INSTANCEID] = {"gl_InstanceID", SHADER_REQ_INSTANCE_ID | SHADER_REQ_INTS, true},
   [TGSI_SEMANTIC_VERTEXID] = {"gl_VertexID", SHADER_REQ_INTS, true},
   [TGSI_SEMANTIC_HELPER_INVOCATION] = {"gl_HelperInvocation", SHADER_REQ_ES31_COMPAT, true},
   [TGSI_SEMANTIC_SAMPLEID] = {"gl_SampleID", SHADER_REQ_SAMPLE_SHADING | SHADER_REQ_INTS, true},
   [TGSI_SEMANTIC_SAMPLEPOS] = { "gl_SamplePosition", SHADER_REQ_SAMPLE_SHADING, true},
   [TGSI_SEMANTIC_INVOCATIONID] = { "gl_InvocationID", SHADER_REQ_INTS | SHADER_REQ_GPU_SHADER5, true},
   [TGSI_SEMANTIC_VERTEXID_NOBASE] = {"(gl_VertexID - gl_BaseVertexARB)", SHADER_REQ_SHADER_DRAW_PARAMETERS | SHADER_REQ_INTS | SHADER_REQ_GPU_SHADER5, true},
   [TGSI_SEMANTIC_SAMPLEMASK] = {"gl_SampleMaskIn[0]", SHADER_REQ_INTS | SHADER_REQ_GPU_SHADER5, true},
   [TGSI_SEMANTIC_PRIMID] = {"gl_PrimitiveID", SHADER_REQ_INTS | SHADER_REQ_GPU_SHADER5, true},
   [TGSI_SEMANTIC_TESSCOORD] = {"gl_TessCoord", SHADER_REQ_NONE, false},
   [TGSI_SEMANTIC_VERTICESIN] = { "gl_PatchVerticesIn", SHADER_REQ_INTS, true},
   [TGSI_SEMANTIC_TESSOUTER] = {"gl_TessLevelOuter", SHADER_REQ_NONE,  true},
   [TGSI_SEMANTIC_TESSINNER] = {"gl_TessLevelInner", SHADER_REQ_NONE,  true},
   [TGSI_SEMANTIC_THREAD_ID] = {"gl_LocalInvocationID", SHADER_REQ_NONE, false},
   [TGSI_SEMANTIC_BLOCK_ID] = {"gl_WorkGroupID", SHADER_REQ_NONE, false},
   [TGSI_SEMANTIC_GRID_SIZE]= {"gl_NumWorkGroups", SHADER_REQ_NONE, false},
   [TGSI_SEMANTIC_BASEVERTEX]= {"gl_BaseVertexARB", SHADER_REQ_SHADER_DRAW_PARAMETERS | SHADER_REQ_INTS, true},
   [TGSI_SEMANTIC_BASEINSTANCE]= {"gl_BaseInstanceARB", SHADER_REQ_SHADER_DRAW_PARAMETERS | SHADER_REQ_INTS, true},
   [TGSI_SEMANTIC_DRAWID]= {"gl_DrawIDARB + drawid_base", SHADER_REQ_SHADER_DRAW_PARAMETERS | SHADER_REQ_INTS, true},
};


static bool
iter_declaration(struct tgsi_iterate_context *iter,
                 struct tgsi_full_declaration *decl)
{
   struct dump_ctx *ctx = (struct dump_ctx *)iter;
   uint32_t i;
   int color_offset = 0;
   const char *name_prefix;
   bool add_two_side = false;

   switch (decl->Declaration.File) {
   case TGSI_FILE_INPUT:
      for (uint32_t j = 0; j < ctx->num_inputs; j++) {
         if (ctx->inputs[j].name == decl->Semantic.Name &&
             ctx->inputs[j].sid == decl->Semantic.Index &&
             ctx->inputs[j].first == decl->Range.First &&
             ((!decl->Declaration.Array && ctx->inputs[j].array_id == 0) ||
              (ctx->inputs[j].array_id  == decl->Array.ArrayID))) {
            return true;
         }
      }

      i = ctx->num_inputs++;
      if (ctx->num_inputs > ARRAY_SIZE(ctx->inputs)) {
         virgl_error( "Number of inputs exceeded, max is %zd\n", ARRAY_SIZE(ctx->inputs));
         return false;
      }

      if (unlikely(decl->Range.First > decl->Range.Last)) {
         virgl_error("Wrong range: First (%u) > Last (%u)\n", decl->Range.First, decl->Range.Last);
         return false;
      }

      if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX) {
         ctx->attrib_input_mask |= (1 << decl->Range.First);
         ctx->inputs[i].type = get_type(ctx->key->vs.attrib_signed_int_bitmask,
                                        ctx->key->vs.attrib_unsigned_int_bitmask,
                                        decl->Range.First);
      }
      ctx->inputs[i].name = decl->Semantic.Name;
      ctx->inputs[i].sid = decl->Semantic.Index;
      ctx->inputs[i].interpolate = decl->Interp.Interpolate;
      ctx->inputs[i].location = decl->Interp.Location;
      ctx->inputs[i].first = decl->Range.First;
      ctx->inputs[i].last = decl->Range.Last;
      ctx->inputs[i].array_id = decl->Declaration.Array ? decl->Array.ArrayID : 0;
      ctx->inputs[i].usage_mask = decl->Declaration.UsageMask;
      ctx->inputs[i].num_components = 4;

      ctx->inputs[i].glsl_predefined_no_emit = false;
      ctx->inputs[i].glsl_no_index = false;
      ctx->inputs[i].override_no_wm = ctx->inputs[i].num_components == 1;
      ctx->inputs[i].glsl_gl_block = false;
      ctx->inputs[i].overlapping_array = NULL;

      if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
         if (decl->Interp.Location == TGSI_INTERPOLATE_LOC_SAMPLE) {
            ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
            ctx->has_sample_input = true;
         }
         if (decl->Interp.Interpolate == TGSI_INTERPOLATE_LINEAR && ctx->cfg->use_gles &&
             ctx->cfg->has_nopersective) {
            ctx->shader_req_bits |= SHADER_REQ_SHADER_NOPERSPECTIVE_INTERPOLATION;
            ctx->has_noperspective = true;
         }
      }

      map_overlapping_io_array(ctx->inputs, &ctx->inputs[i], ctx->num_inputs, decl);

      if (!ctx->inputs[i].glsl_predefined_no_emit) {

         /* If the output of the previous shader contained arrays we
          * have to check whether a non-array input here should be part
          * of an array */
         for (uint32_t j = 0; j < ctx->key->in_arrays.num_arrays; j++) {
            const struct vrend_shader_io_array *array = &ctx->key->in_arrays.layout[j];

            if (array->name == decl->Semantic.Name &&
                array->sid <= decl->Semantic.Index &&
                array->sid + array->size >= decl->Semantic.Index) {
               ctx->inputs[i].sid = array->sid;
               ctx->inputs[i].last = MAX2(ctx->inputs[i].first + array->size, ctx->inputs[i].last);
               break;
            }
         }
      }

      if (ctx->inputs[i].first != ctx->inputs[i].last)
         ctx->glsl_ver_required = require_glsl_ver(ctx, 150);

      name_prefix = get_stage_input_name_prefix(ctx, iter->processor.Processor);

      switch (ctx->inputs[i].name) {
      case TGSI_SEMANTIC_COLOR:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            if (ctx->glsl_ver_required < 140) {
               if (decl->Semantic.Index == 0)
                  name_prefix = "gl_Color";
               else if (decl->Semantic.Index == 1)
                  name_prefix = "gl_SecondaryColor";
               else
                  virgl_error( "got illegal color semantic index %d\n", decl->Semantic.Index);
               ctx->inputs[i].glsl_no_index = true;
            } else {
               if (ctx->key->color_two_side) {
                  uint32_t j = ctx->num_inputs++;
                  if (ctx->num_inputs >= ARRAY_SIZE(ctx->inputs)) {
                     virgl_error( "Number of inputs exceeded, max is %zd\n", ARRAY_SIZE(ctx->inputs));
                     return false;
                  }

                  ctx->inputs[j].name = TGSI_SEMANTIC_BCOLOR;
                  ctx->inputs[j].sid = decl->Semantic.Index;
                  ctx->inputs[j].interpolate = decl->Interp.Interpolate;
                  ctx->inputs[j].location = decl->Interp.Location;
                  ctx->inputs[j].first = decl->Range.First;
                  ctx->inputs[j].last = decl->Range.Last;
                  ctx->inputs[j].glsl_predefined_no_emit = false;
                  ctx->inputs[j].glsl_no_index = false;
                  ctx->inputs[j].override_no_wm = false;

                  ctx->color_in_mask |= (1 << decl->Semantic.Index);

                  if (ctx->front_face_emitted == false) {
                     uint32_t k = ctx->num_inputs++;
                     if (ctx->num_inputs >= ARRAY_SIZE(ctx->inputs)) {
                        virgl_error( "Number of inputs exceeded, max is %zd\n", ARRAY_SIZE(ctx->inputs));
                        return false;
                     }

                     ctx->inputs[k].name = TGSI_SEMANTIC_FACE;
                     ctx->inputs[k].sid = 0;
                     ctx->inputs[k].interpolate = TGSI_INTERPOLATE_CONSTANT;
                     ctx->inputs[k].location = TGSI_INTERPOLATE_LOC_CENTER;
                     ctx->inputs[k].first = 0;
                     ctx->inputs[k].override_no_wm = false;
                     ctx->inputs[k].glsl_predefined_no_emit = true;
                     ctx->inputs[k].glsl_no_index = true;
                  }
                  add_two_side = true;
               }
            }
         }
         break;
      case TGSI_SEMANTIC_PRIMID:
         if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY) {
            name_prefix = "gl_PrimitiveIDIn";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->inputs[i].override_no_wm = true;
            ctx->shader_req_bits |= SHADER_REQ_INTS;
         } else if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            name_prefix = "gl_PrimitiveID";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->glsl_ver_required = require_glsl_ver(ctx, 150);
            ctx->shader_req_bits |= SHADER_REQ_GEOMETRY_SHADER;
         }
         break;
      case TGSI_SEMANTIC_VIEWPORT_INDEX:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->inputs[i].is_int = true;
            ctx->inputs[i].type = VEC_INT;
            ctx->inputs[i].override_no_wm = true;
            name_prefix = "gl_ViewportIndex";
            ctx->shader_req_bits |= SHADER_REQ_LAYER;
            if (ctx->cfg->use_gles)
               ctx->shader_req_bits |= SHADER_REQ_VIEWPORT_IDX;
         }
         break;
      case TGSI_SEMANTIC_LAYER:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            name_prefix = "gl_Layer";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->inputs[i].is_int = true;
            ctx->inputs[i].type = VEC_INT;
            ctx->inputs[i].override_no_wm = true;
            ctx->shader_req_bits |= SHADER_REQ_LAYER;
         }
         break;
      case TGSI_SEMANTIC_PSIZE:
         if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_EVAL) {
            name_prefix = "gl_PointSize";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->inputs[i].override_no_wm = true;
            ctx->inputs[i].glsl_gl_block = true;
            ctx->shader_req_bits |= SHADER_REQ_PSIZE;
            ctx->has_pointsize_input = true;
         }
         break;
      case TGSI_SEMANTIC_CLIPDIST:
         if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_EVAL) {
            name_prefix = "gl_ClipDistance";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->inputs[i].glsl_gl_block = true;
            ctx->num_in_clip_dist += 4 * (ctx->inputs[i].last - ctx->inputs[i].first + 1);
            ctx->shader_req_bits |= SHADER_REQ_CLIP_DISTANCE;
            if (ctx->inputs[i].last != ctx->inputs[i].first)
               ctx->guest_sent_io_arrays = true;
         } else if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            name_prefix = "gl_ClipDistance";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->num_in_clip_dist += 4 * (ctx->inputs[i].last - ctx->inputs[i].first + 1);
            ctx->shader_req_bits |= SHADER_REQ_CLIP_DISTANCE;
            if (ctx->inputs[i].last != ctx->inputs[i].first)
               ctx->guest_sent_io_arrays = true;
         }
         break;
      case TGSI_SEMANTIC_POSITION:
         if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_EVAL) {
            name_prefix = "gl_Position";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->inputs[i].glsl_gl_block = true;
         } else if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            if (ctx->cfg->use_gles && ctx->fs_integer_pixel_center) {
               name_prefix = "(gl_FragCoord - vec4(0.5, 0.5, 0.0, 0.0))";
            } else
               name_prefix = "gl_FragCoord";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
         }
         break;
      case TGSI_SEMANTIC_FACE:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            if (ctx->front_face_emitted) {
               ctx->num_inputs--;
               return true;
            }
            name_prefix = "gl_FrontFacing";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->front_face_emitted = true;
         }
         break;
      case TGSI_SEMANTIC_PCOORD:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            if (ctx->cfg->use_gles) {
               name_prefix = "vec4(gl_PointCoord.x, mix(1.0 - gl_PointCoord.y, gl_PointCoord.y, clamp(winsys_adjust_y, 0.0, 1.0)), 0.0, 1.0)";
               ctx->glsl_strbufs.required_sysval_uniform_decls |= BIT(UNIFORM_WINSYS_ADJUST_Y);
            } else
               name_prefix = "vec4(gl_PointCoord, 0.0, 1.0)";
            ctx->inputs[i].glsl_predefined_no_emit = true;
            ctx->inputs[i].glsl_no_index = true;
            ctx->inputs[i].num_components = 4;
            ctx->inputs[i].usage_mask = 0xf;
         }
         break;
      case TGSI_SEMANTIC_PATCH:
         if (iter->processor.Processor == TGSI_PROCESSOR_TESS_EVAL)
            name_prefix = "patch";
         /* fallthrough */
      case TGSI_SEMANTIC_GENERIC:
      case TGSI_SEMANTIC_TEXCOORD:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            if (ctx->key->fs.coord_replace & (1 << ctx->inputs[i].sid)) {
               if (ctx->cfg->use_gles) {
                  name_prefix = "vec4(gl_PointCoord.x, mix(1.0 - gl_PointCoord.y, gl_PointCoord.y, clamp(winsys_adjust_y, 0.0, 1.0)), 0.0, 1.0)";
                  ctx->glsl_strbufs.required_sysval_uniform_decls |= BIT(UNIFORM_WINSYS_ADJUST_Y);
               } else
                  name_prefix = "vec4(gl_PointCoord, 0.0, 1.0)";
               ctx->inputs[i].glsl_predefined_no_emit = true;
               ctx->inputs[i].glsl_no_index = true;
               ctx->inputs[i].num_components = 4;
               ctx->inputs[i].usage_mask = 0xf;
               break;
            }
         }
         if (ctx->inputs[i].first != ctx->inputs[i].last ||
             ctx->inputs[i].array_id > 0) {
            ctx->guest_sent_io_arrays = true;
            if (!ctx->cfg->use_gles &&
                (ctx->prog_type == TGSI_PROCESSOR_GEOMETRY ||
                 ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL ||
                 ctx->prog_type == TGSI_PROCESSOR_TESS_EVAL)) {
               ctx->shader_req_bits |= SHADER_REQ_ARRAYS_OF_ARRAYS;
            }
         }
         break;
      default:
         virgl_warn("Unhandled input semantic: %x\n", ctx->inputs[i].name);
         break;
      }

      if (ctx->inputs[i].glsl_no_index)
         snprintf(ctx->inputs[i].glsl_name, 128, "%s", name_prefix);
      else {
         if (ctx->inputs[i].name == TGSI_SEMANTIC_FOG){
            ctx->inputs[i].usage_mask = 0xf;
            ctx->inputs[i].num_components = 4;
            ctx->inputs[i].override_no_wm = false;
            snprintf(ctx->inputs[i].glsl_name, 128, "%s_f%d", name_prefix, ctx->inputs[i].sid);
         } else if (ctx->inputs[i].name == TGSI_SEMANTIC_COLOR)
            snprintf(ctx->inputs[i].glsl_name, 128, "%s_c%d", name_prefix, ctx->inputs[i].sid);
         else if (ctx->inputs[i].name == TGSI_SEMANTIC_BCOLOR)
            snprintf(ctx->inputs[i].glsl_name, 128, "%s_bc%d", name_prefix, ctx->inputs[i].sid);
         else if (ctx->inputs[i].name == TGSI_SEMANTIC_GENERIC)
            snprintf(ctx->inputs[i].glsl_name, 128, "%s_g%d", name_prefix, ctx->inputs[i].sid);
         else if (ctx->inputs[i].name == TGSI_SEMANTIC_PATCH)
            snprintf(ctx->inputs[i].glsl_name, 128, "%s%d", name_prefix, ctx->inputs[i].sid);
         else if (ctx->inputs[i].name == TGSI_SEMANTIC_TEXCOORD)
            snprintf(ctx->inputs[i].glsl_name, 128, "%s_t%d", name_prefix, ctx->inputs[i].sid);
         else
            snprintf(ctx->inputs[i].glsl_name, 128, "%s_%d", name_prefix, ctx->inputs[i].first);
      }
      if (add_two_side) {
         snprintf(ctx->inputs[i + 1].glsl_name, 128, "%s_bc%d", name_prefix, ctx->inputs[i + 1].sid);
         if (!ctx->front_face_emitted) {
            snprintf(ctx->inputs[i + 2].glsl_name, 128, "%s", "gl_FrontFacing");
            ctx->front_face_emitted = true;
         }
      }
      break;
   case TGSI_FILE_OUTPUT:
      for (uint32_t j = 0; j < ctx->num_outputs; j++) {
         if (ctx->outputs[j].name == decl->Semantic.Name &&
             ctx->outputs[j].sid == decl->Semantic.Index &&
             ctx->outputs[j].first == decl->Range.First &&
             ((!decl->Declaration.Array && ctx->outputs[j].array_id == 0) ||
              (ctx->outputs[j].array_id  == decl->Array.ArrayID)))
            return true;
      }
      i = ctx->num_outputs++;
      if (ctx->num_outputs > ARRAY_SIZE(ctx->outputs)) {
         virgl_error("Number of outputs exceeded, max is %zd\n", ARRAY_SIZE(ctx->outputs));
         return false;
      }

      if (unlikely(decl->Range.First > decl->Range.Last)) {
         virgl_error("Wrong range: First (%u) > Last (%u)\n", decl->Range.First, decl->Range.Last);
         return false;
      }

      ctx->outputs[i].name = decl->Semantic.Name;
      ctx->outputs[i].sid = decl->Semantic.Index;
      ctx->outputs[i].interpolate = decl->Interp.Interpolate;
      ctx->outputs[i].invariant = decl->Declaration.Invariant;
      ctx->outputs[i].precise = false;
      ctx->outputs[i].first = decl->Range.First;
      ctx->outputs[i].last = decl->Range.Last;
      ctx->outputs[i].array_id = decl->Declaration.Array ? decl->Array.ArrayID : 0;
      ctx->outputs[i].usage_mask = decl->Declaration.UsageMask;
      ctx->outputs[i].num_components = 4;
      ctx->outputs[i].glsl_predefined_no_emit = false;
      ctx->outputs[i].glsl_no_index = false;
      ctx->outputs[i].override_no_wm = ctx->outputs[i].num_components == 1;
      ctx->outputs[i].is_int = false;
      ctx->outputs[i].fbfetch_used = false;
      ctx->outputs[i].overlapping_array = NULL;

      map_overlapping_io_array(ctx->outputs, &ctx->outputs[i], ctx->num_outputs, decl);

      name_prefix = get_stage_output_name_prefix(iter->processor.Processor);

      switch (ctx->outputs[i].name) {
      case TGSI_SEMANTIC_POSITION:
         if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX ||
             iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_EVAL) {
            if (ctx->outputs[i].first > 0)
               virgl_warn("Illegal position input\n");
            name_prefix = "gl_Position";
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            if (iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL)
               ctx->outputs[i].glsl_gl_block = true;
         } else if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            name_prefix = "gl_FragDepth";
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
         }
         break;
      case TGSI_SEMANTIC_STENCIL:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            name_prefix = "gl_FragStencilRefARB";
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            ctx->outputs[i].is_int = true;
            ctx->shader_req_bits |= (SHADER_REQ_INTS | SHADER_REQ_STENCIL_EXPORT);
         }
         break;
      case TGSI_SEMANTIC_CLIPDIST:
         ctx->shader_req_bits |= SHADER_REQ_CLIP_DISTANCE;
         name_prefix = "gl_ClipDistance";
         ctx->outputs[i].glsl_predefined_no_emit = true;
         ctx->outputs[i].glsl_no_index = true;
         ctx->num_out_clip_dist += 4 * (ctx->outputs[i].last - ctx->outputs[i].first + 1);
         if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX &&
             (ctx->key->gs_present || ctx->key->tcs_present))
            ctx->glsl_ver_required = require_glsl_ver(ctx, 150);
         if (iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL)
            ctx->outputs[i].glsl_gl_block = true;
         if (ctx->outputs[i].last != ctx->outputs[i].first)
            ctx->guest_sent_io_arrays = true;
         break;
      case TGSI_SEMANTIC_CLIPVERTEX:
         ctx->outputs[i].override_no_wm = true;
         ctx->outputs[i].invariant = false;
         if (ctx->glsl_ver_required >= 140) {
            ctx->has_clipvertex = true;
            name_prefix = get_stage_output_name_prefix(iter->processor.Processor);
         } else {
            name_prefix = "gl_ClipVertex";
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
         }
         break;
      case TGSI_SEMANTIC_SAMPLEMASK:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            ctx->outputs[i].is_int = true;
            ctx->shader_req_bits |= (SHADER_REQ_INTS | SHADER_REQ_SAMPLE_SHADING);
            name_prefix = "gl_SampleMask";
            break;
         }
         break;
      case TGSI_SEMANTIC_COLOR:
         if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
            ctx->outputs[i].type = get_type(ctx->key->fs.cbufs_signed_int_bitmask,
                                            ctx->key->fs.cbufs_unsigned_int_bitmask,
                                            ctx->outputs[i].sid);
            name_prefix = ctx->key->fs.logicop_enabled ? "fsout_tmp" : "fsout";
         } else {
            if (ctx->glsl_ver_required < 140) {
               ctx->outputs[i].glsl_no_index = true;
               if (ctx->outputs[i].sid == 0)
                  name_prefix = "gl_FrontColor";
               else if (ctx->outputs[i].sid == 1)
                  name_prefix = "gl_FrontSecondaryColor";
            } else {
                      ctx->color_out_mask |= (1 << decl->Semantic.Index);
            }
         }
         ctx->outputs[i].override_no_wm = false;
         break;
      case TGSI_SEMANTIC_BCOLOR:
         if (ctx->glsl_ver_required < 140) {
            ctx->outputs[i].glsl_no_index = true;
            if (ctx->outputs[i].sid == 0)
               name_prefix = "gl_BackColor";
            else if (ctx->outputs[i].sid == 1)
               name_prefix = "gl_BackSecondaryColor";
         } else {
            ctx->outputs[i].override_no_wm = false;
            ctx->color_out_mask |= (1 << decl->Semantic.Index) << 2;
         }
         break;
      case TGSI_SEMANTIC_PSIZE:
         if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX ||
             iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL ||
             iter->processor.Processor == TGSI_PROCESSOR_TESS_EVAL) {
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            ctx->shader_req_bits |= SHADER_REQ_PSIZE;
            name_prefix = "gl_PointSize";
            ctx->has_pointsize_output = true;
            if (iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL)
               ctx->outputs[i].glsl_gl_block = true;
         }
         break;
      case TGSI_SEMANTIC_LAYER:
         if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
             (iter->processor.Processor == TGSI_PROCESSOR_VERTEX &&
              ctx->cfg->has_vs_layer)) {
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            ctx->outputs[i].is_int = true;
            name_prefix = "gl_Layer";
            if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX)
               ctx->shader_req_bits |= SHADER_REQ_AMD_VS_LAYER;
         }
         break;
      case TGSI_SEMANTIC_PRIMID:
         if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY) {
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            ctx->outputs[i].is_int = true;
            name_prefix = "gl_PrimitiveID";
         }
         break;
      case TGSI_SEMANTIC_VIEWPORT_INDEX:
         if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
             (iter->processor.Processor == TGSI_PROCESSOR_VERTEX &&
              !ctx->cfg->use_gles && ctx->cfg->has_vs_viewport_index)) {
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            ctx->outputs[i].is_int = true;
            name_prefix = "gl_ViewportIndex";
            if (iter->processor.Processor == TGSI_PROCESSOR_GEOMETRY) {
               ctx->shader_req_bits |= SHADER_REQ_VIEWPORT_IDX;
               ctx->glsl_ver_required = require_glsl_ver(ctx, 140);
            } else {
               ctx->shader_req_bits |= SHADER_REQ_AMD_VIEWPORT_IDX;
            }
         }
         break;
      case TGSI_SEMANTIC_TESSOUTER:
         if (iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL) {
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            name_prefix = "gl_TessLevelOuter";
         }
         break;
      case TGSI_SEMANTIC_TESSINNER:
         if (iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL) {
            ctx->outputs[i].glsl_predefined_no_emit = true;
            ctx->outputs[i].glsl_no_index = true;
            ctx->outputs[i].override_no_wm = true;
            name_prefix = "gl_TessLevelInner";
         }
         break;
      case TGSI_SEMANTIC_PATCH:
         if (iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL)
            name_prefix = "patch";
         /* fallthrough */
      case TGSI_SEMANTIC_GENERIC:
      case TGSI_SEMANTIC_TEXCOORD:
         if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX)
            if (ctx->outputs[i].name == TGSI_SEMANTIC_GENERIC)
               color_offset = -1;

         if (ctx->outputs[i].first != ctx->outputs[i].last ||
             ctx->outputs[i].array_id > 0) {
            ctx->guest_sent_io_arrays = true;

            if (!ctx->cfg->use_gles &&
                (ctx->prog_type == TGSI_PROCESSOR_GEOMETRY ||
                 ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL ||
                 ctx->prog_type == TGSI_PROCESSOR_TESS_EVAL)) {
               ctx->shader_req_bits |= SHADER_REQ_ARRAYS_OF_ARRAYS;
            }
         }
         break;
      default:
         virgl_warn("Unhandled output semantic: %x\n", ctx->outputs[i].name);
         break;
      }

      if (ctx->outputs[i].glsl_no_index)
         snprintf(ctx->outputs[i].glsl_name, 64, "%s", name_prefix);
      else {
         if (ctx->outputs[i].name == TGSI_SEMANTIC_FOG) {
            ctx->outputs[i].usage_mask = 0xf;
            ctx->outputs[i].num_components = 4;
            ctx->outputs[i].override_no_wm = false;
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_f%d", name_prefix, ctx->outputs[i].sid);
         } else if (ctx->outputs[i].name == TGSI_SEMANTIC_COLOR)
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_c%d", name_prefix, ctx->outputs[i].sid);
         else if (ctx->outputs[i].name == TGSI_SEMANTIC_BCOLOR)
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_bc%d", name_prefix, ctx->outputs[i].sid);
         else if (ctx->outputs[i].name == TGSI_SEMANTIC_PATCH)
            snprintf(ctx->outputs[i].glsl_name, 64, "%s%d", name_prefix, ctx->outputs[i].sid);
         else if (ctx->outputs[i].name == TGSI_SEMANTIC_GENERIC)
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_g%d", name_prefix, ctx->outputs[i].sid);
         else if (ctx->outputs[i].name == TGSI_SEMANTIC_TEXCOORD)
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_t%d", name_prefix, ctx->outputs[i].sid);
         else
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_%d", name_prefix, ctx->outputs[i].first + color_offset);

      }
      break;
   case TGSI_FILE_TEMPORARY:
      if (unlikely(decl->Range.First > decl->Range.Last)) {
         virgl_error("Wrong range: First (%u) > Last (%u)\n", decl->Range.First, decl->Range.Last);
         return false;
      }

      if (!allocate_temp_range(&ctx->temp_ranges, &ctx->num_temp_ranges, decl->Range.First, decl->Range.Last,
                               decl->Array.ArrayID))
         return false;
      break;
   case TGSI_FILE_SAMPLER:
      ctx->samplers_used |= (1 << decl->Range.Last);
      break;
   case TGSI_FILE_SAMPLER_VIEW:
      if (unlikely(decl->Range.First > decl->Range.Last)) {
         virgl_error("Wrong range: First (%u) > Last (%u)\n", decl->Range.First, decl->Range.Last);
         return false;
      }

      if (decl->Range.Last >= ARRAY_SIZE(ctx->samplers)) {
         virgl_error("Sampler view exceeded, max is %zd\n", ARRAY_SIZE(ctx->samplers));
         return false;
      }
      if (!add_samplers(ctx, decl->Range.First, decl->Range.Last, decl->SamplerView.Resource, decl->SamplerView.ReturnTypeX))
         return false;
      break;
   case TGSI_FILE_IMAGE:
      if (unlikely(decl->Range.First > decl->Range.Last)) {
         virgl_error("Wrong range: First (%u) > Last (%u)\n", decl->Range.First, decl->Range.Last);
         return false;
      }

      ctx->shader_req_bits |= SHADER_REQ_IMAGE_LOAD_STORE;
      ctx->shader_req_bits |= SHADER_REQ_EXPLICIT_UNIFORM_LOCATION;
      ctx->shader_req_bits |= SHADER_REQ_EXPLICIT_ATTRIB_LOCATION;
      if (decl->Range.Last >= ARRAY_SIZE(ctx->images)) {
         virgl_error("Image view exceeded, max is %zd\n", ARRAY_SIZE(ctx->images));
         return false;
      }
      if (!add_images(ctx, decl->Range.First, decl->Range.Last, &decl->Image))
         return false;
      break;
   case TGSI_FILE_BUFFER:
      if (decl->Range.First + ctx->key->ssbo_binding_offset >= VREND_MAX_COMBINED_SSBO_BINDING_POINTS) {
         virgl_error("Buffer view exceeded, max is %d\n", VREND_MAX_COMBINED_SSBO_BINDING_POINTS);
         return false;
      }
      ctx->ssbo_used_mask |= (1 << decl->Range.First);

      if (decl->Declaration.Atomic) {
         if (decl->Range.First < ctx->ssbo_atomic_array_base)
            ctx->ssbo_atomic_array_base = decl->Range.First;
         ctx->ssbo_atomic_mask |= (1 << decl->Range.First);
      } else {
         if (decl->Range.First < ctx->ssbo_array_base)
            ctx->ssbo_array_base = decl->Range.First;
      }
      if (ctx->ssbo_last_binding < decl->Range.Last)
         ctx->ssbo_last_binding = decl->Range.Last;
      ctx->glsl_ver_required = require_glsl_ver(ctx, 140);
      break;
   case TGSI_FILE_CONSTANT:
      if (decl->Declaration.Dimension && decl->Dim.Index2D != 0) {
         if (decl->Dim.Index2D > 31) {
            virgl_error("Number of uniforms exceeded, max is 32\n");
            return false;
         }
         if (ctx->ubo_used_mask & (1 << decl->Dim.Index2D)) {
            virgl_error("UBO #%d is already defined\n", decl->Dim.Index2D);
            return false;
         }
         ctx->ubo_used_mask |= (1 << decl->Dim.Index2D);
         ctx->ubo_sizes[decl->Dim.Index2D] = decl->Range.Last + 1;
      } else {
         /* if we have a normal single const set then ubo base should be 1 */
         ctx->ubo_base = 1;
         if (decl->Range.Last) {
            if (decl->Range.Last + 1 > ctx->num_consts)
               ctx->num_consts = decl->Range.Last + 1;
         } else
            ctx->num_consts++;
      }
      break;
   case TGSI_FILE_ADDRESS:
      ctx->num_address = decl->Range.Last + 1;
      break;
   case TGSI_FILE_SYSTEM_VALUE:
      i = ctx->num_system_values++;
      if (ctx->num_system_values > ARRAY_SIZE(ctx->system_values)) {
         virgl_error("Number of system values exceeded, max is %zd\n", ARRAY_SIZE(ctx->system_values));
         return false;
      }

      ctx->system_values[i].name = decl->Semantic.Name;
      ctx->system_values[i].sid = decl->Semantic.Index;
      ctx->system_values[i].glsl_predefined_no_emit = true;
      ctx->system_values[i].glsl_no_index = true;
      ctx->system_values[i].first = decl->Range.First;

      if (decl->Semantic.Name < TGSI_SEMANTIC_COUNT) {
         struct syvalue_prop_map *svmap = &sysvalue_map[decl->Semantic.Name];
         name_prefix = svmap->glsl_name;
         if (!name_prefix) {
            virgl_error("Unsupported system value %d\n", decl->Semantic.Name);
            return false;
         }
         ctx->shader_req_bits |= svmap->required_ext;
         ctx->system_values[i].override_no_wm = svmap->override_no_wm;
         snprintf(ctx->system_values[i].glsl_name, 64, "%s", name_prefix);
         if (decl->Semantic.Name == TGSI_SEMANTIC_DRAWID)
            ctx->glsl_strbufs.required_sysval_uniform_decls |= BIT(UNIFORM_DRAWID_BASE);
         break;
      } else {
         virgl_error("System value %d out of range\n", decl->Semantic.Name);
         return false;
      }
   case TGSI_FILE_MEMORY:
      ctx->has_file_memory = true;
      break;
   case TGSI_FILE_HW_ATOMIC:
      if (unlikely(decl->Range.First > decl->Range.Last)) {
         virgl_error("Wrong range: First (%u) > Last (%u)\n", decl->Range.First, decl->Range.Last);
         return false;
      }

      if (ctx->num_abo >= ARRAY_SIZE(ctx->abo_idx)) {
         virgl_error("Number of atomic counter buffers exceeded, max is %zd\n", ARRAY_SIZE(ctx->abo_idx));
         return false;
      }
      ctx->abo_idx[ctx->num_abo] = decl->Dim.Index2D;
      ctx->abo_sizes[ctx->num_abo] = decl->Range.Last - decl->Range.First + 1;
      ctx->abo_offsets[ctx->num_abo] = decl->Range.First;
      ctx->num_abo++;
      ctx->glsl_ver_required = require_glsl_ver(ctx, 140);
      break;
   default:
      virgl_error("Unsupported file %d declaration\n", decl->Declaration.File);
      break;
   }

   return true;
}

static bool
iter_property(struct tgsi_iterate_context *iter,
              struct tgsi_full_property *prop)
{
   struct dump_ctx *ctx = (struct dump_ctx *) iter;

   switch (prop->Property.PropertyName) {
   case TGSI_PROPERTY_FS_COLOR0_WRITES_ALL_CBUFS:
      if (prop->u[0].Data == 1)
         ctx->write_all_cbufs = true;
      break;
   case TGSI_PROPERTY_FS_COORD_ORIGIN:
      ctx->fs_lower_left_origin = prop->u[0].Data ? true : false;
      break;
   case TGSI_PROPERTY_FS_COORD_PIXEL_CENTER:
      ctx->fs_integer_pixel_center = prop->u[0].Data ? true : false;
      break;
   case TGSI_PROPERTY_FS_DEPTH_LAYOUT:
      /* If the host doesn't support this, then we can savely ignore this,
       * we only lost an opportunity to optimize */
      if (ctx->cfg->has_conservative_depth) {
         ctx->shader_req_bits |= SHADER_REQ_CONSERVATIVE_DEPTH;
         ctx->fs_depth_layout = prop->u[0].Data;
      }
      break;
   case TGSI_PROPERTY_GS_INPUT_PRIM:
      ctx->gs_in_prim = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_GS_OUTPUT_PRIM:
      ctx->gs_out_prim = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_GS_MAX_OUTPUT_VERTICES:
      ctx->gs_max_out_verts = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_GS_INVOCATIONS:
      ctx->gs_num_invocations = prop->u[0].Data;
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_PROPERTY_NUM_CLIPDIST_ENABLED:
      ctx->shader_req_bits |= SHADER_REQ_CLIP_DISTANCE;
      ctx->num_clip_dist_prop = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_NUM_CULLDIST_ENABLED:
      ctx->num_cull_dist_prop = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_TCS_VERTICES_OUT:
      ctx->tcs_vertices_out = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_TES_PRIM_MODE:
      ctx->tes_prim_mode = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_TES_SPACING:
      ctx->tes_spacing = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_TES_VERTEX_ORDER_CW:
      ctx->tes_vertex_order = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_TES_POINT_MODE:
      ctx->tes_point_mode = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_FS_EARLY_DEPTH_STENCIL:
      ctx->early_depth_stencil = prop->u[0].Data > 0;
      if (ctx->early_depth_stencil) {
         ctx->glsl_ver_required = require_glsl_ver(ctx, 150);
         ctx->shader_req_bits |= SHADER_REQ_IMAGE_LOAD_STORE;
      }
      break;
   case TGSI_PROPERTY_CS_FIXED_BLOCK_WIDTH:
      ctx->local_cs_block_size[0] = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_CS_FIXED_BLOCK_HEIGHT:
      ctx->local_cs_block_size[1] = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_CS_FIXED_BLOCK_DEPTH:
      ctx->local_cs_block_size[2] = prop->u[0].Data;
      break;
   case TGSI_PROPERTY_FS_BLEND_EQUATION_ADVANCED:
      ctx->fs_blend_equation_advanced = prop->u[0].Data;
      if (!ctx->cfg->use_gles || ctx->cfg->glsl_version < 320) {
         ctx->glsl_ver_required = require_glsl_ver(ctx, 150);
         ctx->shader_req_bits |= SHADER_REQ_BLEND_EQUATION_ADVANCED;
      }
      break;
   case TGSI_PROPERTY_SEPARABLE_PROGRAM:
      /* GLES is very strict in how separable shaders interfaces should be matched.
       * It doesn't allow, for example, inputs without matching outputs. So, we just
       * disable separable shaders for GLES. */
      if (!ctx->cfg->use_gles) {
          ctx->separable_program = prop->u[0].Data;
          ctx->shader_req_bits |= SHADER_REQ_SEPERATE_SHADER_OBJECTS;
          ctx->shader_req_bits |= SHADER_REQ_EXPLICIT_ATTRIB_LOCATION;
      }
      break;
   default:
      virgl_error("Unhandled property: %x\n", prop->Property.PropertyName);
      return false;
   }

   return true;
}

static bool
iter_immediate(struct tgsi_iterate_context *iter,
               struct tgsi_full_immediate *imm)
{
   struct dump_ctx *ctx = (struct dump_ctx *) iter;
   int i;
   uint32_t first = ctx->num_imm;

   if (unlikely(first >= MAX_IMMEDIATE)) {
      virgl_error("Number of immediates exceeded, max is: %u\n", MAX_IMMEDIATE);
      return false;
   }

   ctx->imm[first].type = imm->Immediate.DataType;
   for (i = 0; i < 4; i++) {
      switch (imm->Immediate.DataType) {
      case TGSI_IMM_FLOAT32:
         ctx->imm[first].val[i].f = imm->u[i].Float;
         break;
      case TGSI_IMM_UINT32:
      case TGSI_IMM_FLOAT64:
         ctx->shader_req_bits |= SHADER_REQ_INTS;
         ctx->imm[first].val[i].ui = imm->u[i].Uint;
         break;
      case TGSI_IMM_INT32:
         ctx->shader_req_bits |= SHADER_REQ_INTS;
         ctx->imm[first].val[i].i = imm->u[i].Int;
         break;
      default:
         virgl_error("Unhandled immediate type, ignoring: %x\n", imm->Immediate.DataType);
         break;
      }
   }
   ctx->num_imm++;
   return true;
}

static char get_swiz_char(int swiz)
{
   switch(swiz){
   case TGSI_SWIZZLE_X: return 'x';
   case TGSI_SWIZZLE_Y: return 'y';
   case TGSI_SWIZZLE_Z: return 'z';
   case TGSI_SWIZZLE_W: return 'w';
   default: return 0;
   }
}

static void emit_cbuf_writes(const struct dump_ctx *ctx,
                             struct vrend_glsl_strbufs *glsl_strbufs)
{
   int i;

   for (i = ctx->num_outputs; i < ctx->cfg->max_draw_buffers; i++) {
      emit_buff(glsl_strbufs, "fsout_c%d = fsout_c0;\n", i);
   }
}

static void emit_a8_swizzle(struct vrend_glsl_strbufs *glsl_strbufs)
{
   emit_buf(glsl_strbufs, "fsout_c0.x = fsout_c0.w;\n");
}

static const char *atests[PIPE_FUNC_ALWAYS + 1] = {
   "false",
   "<",
   "==",
   "<=",
   ">",
   "!=",
   ">=",
   "true"
};

static void emit_alpha_test(const struct dump_ctx *ctx,
                            struct vrend_glsl_strbufs *glsl_strbufs)
{
   char comp_buf[128];

   if (!ctx->num_outputs)
      return;

   if (!ctx->write_all_cbufs) {
      /* only emit alpha stanza if first output is 0 */
      if (ctx->outputs[0].sid != 0)
         return;
   }
   switch (ctx->key->alpha_test) {
   case PIPE_FUNC_NEVER:
   case PIPE_FUNC_ALWAYS:
      snprintf(comp_buf, 128, "%s", atests[ctx->key->alpha_test]);
      break;
   case PIPE_FUNC_LESS:
   case PIPE_FUNC_EQUAL:
   case PIPE_FUNC_LEQUAL:
   case PIPE_FUNC_GREATER:
   case PIPE_FUNC_NOTEQUAL:
   case PIPE_FUNC_GEQUAL:
      snprintf(comp_buf, 128, "%s %s alpha_ref_val", "fsout_c0.w", atests[ctx->key->alpha_test]);
      glsl_strbufs->required_sysval_uniform_decls |= BIT(UNIFORM_ALPHA_REF_VAL);
      break;
   default:
      virgl_error("Invalid alpha-test: %x\n", ctx->key->alpha_test);
      set_buf_error(glsl_strbufs);
      return;
   }

   emit_buff(glsl_strbufs, "if (!(%s)) {\n\tdiscard;\n}\n", comp_buf);
}

static void emit_pstipple_pass(struct vrend_glsl_strbufs *glsl_strbufs)
{
   static_assert(VREND_POLYGON_STIPPLE_SIZE == 32,
         "According to the spec stipple size must be 32");

   const int mask = VREND_POLYGON_STIPPLE_SIZE - 1;

   emit_buf(glsl_strbufs, "{\n");
   emit_buff(glsl_strbufs, "   int spx = int(gl_FragCoord.x) & %d;\n", mask);
   emit_buff(glsl_strbufs, "   int spy = int(gl_FragCoord.y) & %d;\n", mask);
   emit_buf(glsl_strbufs, "   stip_temp = stipple_pattern[spy] & (0x80000000u >> spx);\n");
   emit_buf(glsl_strbufs, "   if (stip_temp == 0u) {\n      discard;\n   }\n");
   emit_buf(glsl_strbufs, "}\n");
   glsl_strbufs->required_sysval_uniform_decls |= BIT(UNIFORM_PSTIPPLE_SAMPLER);
}

static void emit_color_select(const struct dump_ctx *ctx,
                              struct vrend_glsl_strbufs *glsl_strbufs)
{
   if (!ctx->key->color_two_side || !(ctx->color_in_mask & 0x3))
      return;

   const char *name_prefix = get_stage_input_name_prefix(ctx, ctx->prog_type);
   if (ctx->color_in_mask & 1)
      emit_buff(glsl_strbufs, "realcolor0 = gl_FrontFacing ? %s_c0 : %s_bc0;\n",
               name_prefix, name_prefix);

   if (ctx->color_in_mask & 2)
      emit_buff(glsl_strbufs, "realcolor1 = gl_FrontFacing ? %s_c1 : %s_bc1;\n",
                name_prefix, name_prefix);
}

static void emit_prescale(struct vrend_glsl_strbufs *glsl_strbufs)
{
   emit_buf(glsl_strbufs, "gl_Position.y = gl_Position.y * winsys_adjust_y;\n");
   glsl_strbufs->required_sysval_uniform_decls |= BIT(UNIFORM_WINSYS_ADJUST_Y);
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static void prepare_so_movs(struct dump_ctx *ctx)
{
   uint32_t i;
   for (i = 0; i < ctx->so->num_outputs; i++) {
      ctx->write_so_outputs[i] = true;
      if (ctx->so->output[i].start_component != 0)
         continue;
      if (ctx->so->output[i].num_components != 4)
         continue;
      if (ctx->outputs[ctx->so->output[i].register_index].name == TGSI_SEMANTIC_CLIPDIST)
         continue;
      if (ctx->outputs[ctx->so->output[i].register_index].name == TGSI_SEMANTIC_POSITION)
         continue;

      ctx->outputs[ctx->so->output[i].register_index].stream = ctx->so->output[i].stream;
      if (ctx->prog_type == TGSI_PROCESSOR_GEOMETRY && ctx->so->output[i].stream)
         ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;

      ctx->write_so_outputs[i] = false;
   }
}

static const struct vrend_shader_io *get_io_slot(const struct vrend_shader_io *slots, uint32_t nslots, int idx)
{
   const struct vrend_shader_io *result = slots;
   for (uint32_t i = 0; i < nslots; ++i, ++result) {
      if ((result->first <=  idx) && (result->last >=  idx))
         return result;
   }
   assert(0 && "Output not found");
   return NULL;
}

static inline void
get_blockname(char outvar[64], const char *stage_prefix, const struct vrend_shader_io *io)
{
   snprintf(outvar, 64, "block_%sg%d", stage_prefix, io->sid);
}

static inline void
get_blockvarname(char outvar[64], const char *stage_prefix, const struct vrend_shader_io *io, const char *postfix)
{
   snprintf(outvar, 64, "%sg%d%s", stage_prefix, io->first, postfix);
}

static void get_so_name(const struct dump_ctx *ctx, bool from_block, const struct vrend_shader_io *output, int index, char out_var[255], char *wm)
{
   if (output->first == output->last ||
       (output->name != TGSI_SEMANTIC_GENERIC &&
        output->name != TGSI_SEMANTIC_TEXCOORD))
      snprintf(out_var, 255, "%s%s", output->glsl_name, wm);
   else {
      if ((output->name == TGSI_SEMANTIC_GENERIC) && prefer_generic_io_block(ctx, io_out)) {
         char blockname[64];
         const char *stage_prefix = get_stage_output_name_prefix(ctx->prog_type);
         if (from_block)
            get_blockname(blockname, stage_prefix, output);
         else
            get_blockvarname(blockname, stage_prefix, output, "");
         snprintf(out_var, 255, "%s.%s[%d]%s",  blockname, output->glsl_name, index - output->first, wm);
      } else {
         snprintf(out_var, 255, "%s[%d]%s",  output->glsl_name, index - output->first, wm);
      }
   }
}

static void emit_so_movs(const struct dump_ctx *ctx,
                         struct vrend_glsl_strbufs *glsl_strbufs,
                         bool *has_clipvertex_so)
{
   uint32_t i, j;
   char outtype[15] = "";
   char writemask[6];

   if (ctx->so->num_outputs >= PIPE_MAX_SO_OUTPUTS) {
      virgl_error("Num outputs exceeded, max is %u\n", PIPE_MAX_SO_OUTPUTS);
      set_buf_error(glsl_strbufs);
      return;
   }

   for (i = 0; i < ctx->so->num_outputs; i++) {
      const struct vrend_shader_io *output = get_io_slot(&ctx->outputs[0], ctx->num_outputs, ctx->so->output[i].register_index);
      if (ctx->so->output[i].start_component != 0) {
         int wm_idx = 0;
         writemask[wm_idx++] = '.';
         for (j = 0; j < ctx->so->output[i].num_components; j++) {
            unsigned idx = ctx->so->output[i].start_component + j;
            if (idx >= 4)
               break;
            if (idx <= 2)
               writemask[wm_idx++] = 'x' + idx;
            else
               writemask[wm_idx++] = 'w';
         }
         writemask[wm_idx] = '\0';
      } else
         writemask[0] = 0;

      if (!ctx->write_so_outputs[i]) {
         if (ctx->so_names[i])
            free(ctx->so_names[i]);
         if (ctx->so->output[i].register_index > ctx->num_outputs)
            ctx->so_names[i] = NULL;
         else if (output->name == TGSI_SEMANTIC_CLIPVERTEX && ctx->has_clipvertex) {
            ctx->so_names[i] = strdup("clipv_tmp");
            *has_clipvertex_so = true;
         } else {
            char out_var[255];
            const struct vrend_shader_io *used_output_io = output;
            if (output->name == TGSI_SEMANTIC_GENERIC && ctx->generic_ios.output_range.used) {
               used_output_io = &ctx->generic_ios.output_range.io;
            } else if (output->name == TGSI_SEMANTIC_PATCH && ctx->patch_ios.output_range.used) {
               used_output_io = &ctx->patch_ios.output_range.io;
            }
            get_so_name(ctx, true, used_output_io, ctx->so->output[i].register_index, out_var, "");
            ctx->so_names[i] = strdup(out_var);
         }
      } else {
         if (!ctx->so_names[i]) {
            char ntemp[8];
            snprintf(ntemp, 8, "tfout%d", i);
            ctx->so_names[i] = strdup(ntemp);
         }
      }
      if (ctx->so->output[i].num_components == 1) {
         if (output->is_int)
            snprintf(outtype, 15, "intBitsToFloat");
         else
            snprintf(outtype, 15, "float");
      } else
         snprintf(outtype, 15, "vec%d", ctx->so->output[i].num_components);

      if (output->name == TGSI_SEMANTIC_CLIPDIST) {
         if (output->first == output->last)
            emit_buff(glsl_strbufs, "tfout%d = %s(clip_dist_temp[%d]%s);\n", i, outtype, output->sid,
                      writemask);
         else
            emit_buff(glsl_strbufs, "tfout%d = %s(clip_dist_temp[%d]%s);\n", i, outtype,
                      output->sid + ctx->so->output[i].register_index - output->first,
                      writemask);
      } else {
         if (ctx->write_so_outputs[i]) {
            char out_var[255];
            if (ctx->so->output[i].need_temp || ctx->prog_type == TGSI_PROCESSOR_GEOMETRY ||
                output->glsl_predefined_no_emit) {
               get_so_name(ctx, false, output, ctx->so->output[i].register_index, out_var, writemask);
               emit_buff(glsl_strbufs, "tfout%d = %s(%s);\n", i, outtype, out_var);
            } else {
               get_so_name(ctx, true, output, ctx->so->output[i].register_index, out_var, writemask);
               free(ctx->so_names[i]);
               ctx->so_names[i] = strdup(out_var);
            }
         }
      }
   }
}

static void emit_clip_dist_movs(const struct dump_ctx *ctx,
                                struct vrend_glsl_strbufs *glsl_strbufs)
{
   int i;
   bool has_prop = (ctx->num_clip_dist_prop + ctx->num_cull_dist_prop) > 0;
   int num_clip = has_prop ? ctx->num_clip_dist_prop : ctx->key->num_out_clip;
   int num_cull = has_prop ? ctx->num_cull_dist_prop : ctx->key->num_out_cull;


   int num_clip_cull = num_cull + num_clip;
   if (ctx->num_out_clip_dist && !num_clip_cull)
      num_clip = ctx->num_out_clip_dist;

   int ndists;
   const char *prefix="";

   if (ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL)
      prefix = "gl_out[gl_InvocationID].";

   if (ctx->num_out_clip_dist == 0 &&
       ctx->is_last_vertex_stage &&
       ctx->num_outputs + 2 <= MAX_VARYING) {
      emit_buff(glsl_strbufs, "if (clip_plane_enabled) {\n");
      for (i = 0; i < 8; i++) {
         emit_buff(glsl_strbufs, "  %sgl_ClipDistance[%d] = dot(%s, clipp[%d]);\n",
                   prefix, i, ctx->has_clipvertex ? "clipv_tmp" : "gl_Position", i);
      }
      emit_buff(glsl_strbufs, "}\n");
      glsl_strbufs->required_sysval_uniform_decls |= BIT(UNIFORM_CLIP_PLANE);
   }
   ndists = ctx->num_out_clip_dist;
   if (has_prop)
      ndists = num_clip + num_cull;
   for (i = 0; i < ndists; i++) {
      int clipidx = i < 4 ? 0 : 1;
      char swiz = i & 3;
      char wm = 0;
      switch (swiz) {
      default:
      case 0: wm = 'x'; break;
      case 1: wm = 'y'; break;
      case 2: wm = 'z'; break;
      case 3: wm = 'w'; break;
      }
      bool is_cull = false;
      const char *clip_cull = "Clip";

      if (i >= num_clip) {
         if (i < ndists) {
            is_cull = true;
            clip_cull = "Cull";
         } else {
            clip_cull = "ERROR";
         }
      }

      emit_buff(glsl_strbufs, "%sgl_%sDistance[%d] = clip_dist_temp[%d].%c;\n", prefix, clip_cull,
               is_cull ? i - num_clip : i, clipidx, wm);
   }
}

static void emit_fog_fixup_hdr(const struct dump_ctx *ctx,
                               struct vrend_glsl_strbufs *glsl_strbufs)
{
   uint32_t fixup_mask = ctx->key->vs.fog_fixup_mask;
   int semantic;
   const char *prefix = get_stage_output_name_prefix(TGSI_PROCESSOR_VERTEX);

   while (fixup_mask) {
      semantic = ffs(fixup_mask) - 1;

      emit_hdrf(glsl_strbufs, "out vec4 %s_f%d;\n", prefix, semantic);
      fixup_mask &= (~(1 << semantic));
   }
}

static void emit_fog_fixup_write(const struct dump_ctx *ctx,
                                 struct vrend_glsl_strbufs *glsl_strbufs)
{
   uint32_t fixup_mask = ctx->key->vs.fog_fixup_mask;
   int semantic;
   const char *prefix = get_stage_output_name_prefix(TGSI_PROCESSOR_VERTEX);

   while (fixup_mask) {
      semantic = ffs(fixup_mask) - 1;

      /*
      *  Force unwritten fog outputs to 0,0,0,1
      */
      emit_buff(glsl_strbufs, "%s_f%d = vec4(0.0, 0.0, 0.0, 1.0);\n",
               prefix, semantic);
      fixup_mask &= (~(1 << semantic));
   }
}

#define emit_arit_op2(op) emit_buff(&ctx->glsl_strbufs, "%s = %s(%s((%s %s %s))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], op, srcs[1], writemask)
#define emit_op1(op) emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(%s(%s))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), op, srcs[0], writemask)
#define emit_compare(op) emit_buff(&ctx->glsl_strbufs, "%s = %s(%s((%s(%s(%s), %s(%s))))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), op, get_string(sinfo.svec4), srcs[0], get_string(sinfo.svec4), srcs[1], writemask)

#define emit_ucompare(op) emit_buff(&ctx->glsl_strbufs, "%s = %s(uintBitsToFloat(%s(%s(%s(%s), %s(%s))%s) * %s(0xffffffff)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.udstconv), op, get_string(sinfo.svec4), srcs[0], get_string(sinfo.svec4), srcs[1], writemask, get_string(dinfo.udstconv))

static void handle_vertex_proc_exit(const struct dump_ctx *ctx,
                                    struct vrend_glsl_strbufs *glsl_strbufs,
                                    bool *has_clipvertex_so)
{
    if (ctx->so && !ctx->key->gs_present && !ctx->key->tes_present)
       emit_so_movs(ctx, glsl_strbufs, has_clipvertex_so);

    if (ctx->cfg->has_cull_distance)
       emit_clip_dist_movs(ctx, glsl_strbufs);

    if (!ctx->key->gs_present && !ctx->key->tes_present)
       emit_prescale(glsl_strbufs);

    if (ctx->key->vs.fog_fixup_mask)
       emit_fog_fixup_write(ctx, glsl_strbufs);
}

static void emit_fragment_logicop(const struct dump_ctx *ctx,
                                  struct vrend_glsl_strbufs *glsl_strbufs)
{
   char src[PIPE_MAX_COLOR_BUFS][64];
   char src_fb[PIPE_MAX_COLOR_BUFS][64];
   double scale[PIPE_MAX_COLOR_BUFS];
   int mask[PIPE_MAX_COLOR_BUFS];

   struct vrend_strbuf full_op_buf[PIPE_MAX_COLOR_BUFS];
   for (int i = 0; i < PIPE_MAX_COLOR_BUFS; ++i) {
      strbuf_alloc(&full_op_buf[i], 134);
   }


   for (uint32_t i = 0; i < ctx->num_outputs; i++) {
      mask[i] = (1 << ctx->key->fs.surface_component_bits[i]) - 1;
      scale[i] = mask[i];
      switch (ctx->key->fs.logicop_func) {
      case PIPE_LOGICOP_INVERT:
         snprintf(src_fb[i], ARRAY_SIZE(src_fb[i]),
                  "ivec4(%f * fsout_c%d + 0.5)", scale[i], i);
         break;
      case PIPE_LOGICOP_NOR:
      case PIPE_LOGICOP_AND_INVERTED:
      case PIPE_LOGICOP_AND_REVERSE:
      case PIPE_LOGICOP_XOR:
      case PIPE_LOGICOP_NAND:
      case PIPE_LOGICOP_AND:
      case PIPE_LOGICOP_EQUIV:
      case PIPE_LOGICOP_OR_INVERTED:
      case PIPE_LOGICOP_OR_REVERSE:
      case PIPE_LOGICOP_OR:
         snprintf(src_fb[i], ARRAY_SIZE(src_fb[i]),
                  "ivec4(%f * fsout_c%d + 0.5)", scale[i], i);
         /* fallthrough */
      case PIPE_LOGICOP_COPY_INVERTED:
         snprintf(src[i], ARRAY_SIZE(src[i]),
                  "ivec4(%f * fsout_tmp_c%d + 0.5)", scale[i], i);
         break;
      case PIPE_LOGICOP_COPY:
      case PIPE_LOGICOP_NOOP:
      case PIPE_LOGICOP_CLEAR:
      case PIPE_LOGICOP_SET:
         break;
      }
   }

   for (uint32_t i = 0; i < ctx->num_outputs; i++) {
      switch (ctx->key->fs.logicop_func) {
      case PIPE_LOGICOP_CLEAR:
         strbuf_fmt(&full_op_buf[i], "%s", "vec4(0)");
         break;
      case PIPE_LOGICOP_NOOP:
         strbuf_fmt(&full_op_buf[i], "%s", "");
         break;
      case PIPE_LOGICOP_SET:
         strbuf_fmt(&full_op_buf[i], "%s", "vec4(1)");
         break;
      case PIPE_LOGICOP_COPY:
         strbuf_fmt(&full_op_buf[i], "fsout_tmp_c%d", i);
         break;
      case PIPE_LOGICOP_COPY_INVERTED:
         strbuf_fmt(&full_op_buf[i], "~%s", src[i]);
         break;
      case PIPE_LOGICOP_INVERT:
         strbuf_fmt(&full_op_buf[i], "~%s", src_fb[i]);
         break;
      case PIPE_LOGICOP_AND:
         strbuf_fmt(&full_op_buf[i], "%s & %s", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_NAND:
         strbuf_fmt(&full_op_buf[i], "~( %s & %s )", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_NOR:
         strbuf_fmt(&full_op_buf[i], "~( %s | %s )", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_AND_INVERTED:
         strbuf_fmt(&full_op_buf[i], "~%s & %s", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_AND_REVERSE:
         strbuf_fmt(&full_op_buf[i], "%s & ~%s", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_XOR:
         strbuf_fmt(&full_op_buf[i], "%s ^%s", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_EQUIV:
         strbuf_fmt(&full_op_buf[i], "~( %s ^ %s )", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_OR_INVERTED:
         strbuf_fmt(&full_op_buf[i], "~%s | %s", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_OR_REVERSE:
         strbuf_fmt(&full_op_buf[i], "%s | ~%s", src[i], src_fb[i]);
         break;
      case PIPE_LOGICOP_OR:
         strbuf_fmt(&full_op_buf[i], "%s | %s", src[i], src_fb[i]);
         break;
      }
   }

   for (uint32_t i = 0; i < ctx->num_outputs; i++) {
      switch (ctx->key->fs.logicop_func) {
      case PIPE_LOGICOP_NOOP:
         break;
      case PIPE_LOGICOP_COPY:
      case PIPE_LOGICOP_CLEAR:
      case PIPE_LOGICOP_SET:
         emit_buff(glsl_strbufs, "fsout_c%d = %s;\n", i, full_op_buf[i].buf);
         break;
      default:
         emit_buff(glsl_strbufs, "fsout_c%d = vec4((%s) & %d) / %f;\n", i, full_op_buf[i].buf, mask[i], scale[i]);
      }
   }
}

static void emit_cbuf_swizzle(const struct dump_ctx *ctx,
                              struct vrend_glsl_strbufs *glsl_strbufs)
{
   int cbuf_id = 0;
   for (uint32_t i = 0; i < ctx->num_outputs; i++) {
      if (ctx->outputs[i].name == TGSI_SEMANTIC_COLOR) {
         if (ctx->key->fs.swizzle_output_rgb_to_bgr & (1 << cbuf_id)) {
            emit_buff(glsl_strbufs, "fsout_c%d = fsout_c%d.zyxw;\n", cbuf_id, cbuf_id);
         }
         ++cbuf_id;
      }
   }
}

static void emit_cbuf_colorspace_convert(const struct dump_ctx *ctx,
                                         struct vrend_glsl_strbufs *glsl_strbufs)
{
   for (uint32_t i = 0; i < ctx->num_outputs; i++) {
      if (ctx->key->fs.needs_manual_srgb_encode_bitmask & (1 << i)) {
         emit_buff(glsl_strbufs,
                   "{\n"
                   "   vec3 temp = fsout_c%d.xyz;\n"
                   "   bvec3 thresh = lessThanEqual(temp, vec3(0.0031308));\n"
                   "   vec3 a = temp * vec3(12.92);\n"
                   "   vec3 b = ( vec3(1.055) * pow(temp, vec3(1.0/2.4)) ) - vec3(0.055);\n"
                   "   fsout_c%d.xyz = mix(b, a, thresh);\n"
                   "}\n"
                   , i, i);
      }
   }
}

static void handle_fragment_proc_exit(const struct dump_ctx *ctx,
                                      struct vrend_glsl_strbufs *glsl_strbufs)
{
    if (ctx->key->pstipple_enabled)
       emit_pstipple_pass(glsl_strbufs);

    if (ctx->key->fs.cbufs_are_a8_bitmask)
       emit_a8_swizzle(glsl_strbufs);

    if (ctx->key->add_alpha_test)
       emit_alpha_test(ctx, glsl_strbufs);


    if (ctx->key->fs.logicop_enabled)
       emit_fragment_logicop(ctx, glsl_strbufs);

    if (ctx->key->fs.swizzle_output_rgb_to_bgr)
       emit_cbuf_swizzle(ctx, glsl_strbufs);

    if (ctx->key->fs.needs_manual_srgb_encode_bitmask)
       emit_cbuf_colorspace_convert(ctx, glsl_strbufs);

    if (ctx->write_all_cbufs)
       emit_cbuf_writes(ctx, glsl_strbufs);

}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static bool set_texture_reqs(struct dump_ctx *ctx,
                             const struct tgsi_full_instruction *inst,
                             uint32_t sreg_index)
{
   if (sreg_index >= ARRAY_SIZE(ctx->samplers)) {
      virgl_error("Sampler view exceeded, max is %zd\n", ARRAY_SIZE(ctx->samplers));
      return false;
   }
   ctx->samplers[sreg_index].tgsi_sampler_type = inst->Texture.Texture;

   ctx->shader_req_bits |= samplertype_to_req_bits(inst->Texture.Texture);

   if (ctx->cfg->glsl_version >= 140)
      if (ctx->shader_req_bits & (SHADER_REQ_SAMPLER_RECT |
                                  SHADER_REQ_SAMPLER_BUF))
         ctx->glsl_ver_required = require_glsl_ver(ctx, 140);
   return true;
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const

/* size queries are pretty much separate */
static void emit_txq(struct dump_ctx *ctx,
                     const struct tgsi_full_instruction *inst,
                     uint32_t sreg_index,
                     const char *srcs[4],
                     const char *dst,
                     const char *writemask)
{
   unsigned twm = TGSI_WRITEMASK_NONE;
   char bias[128] = "";
   const int sampler_index = 1;
   enum vrend_type_qualifier dtypeprefix = INT_BITS_TO_FLOAT;

   set_texture_reqs(ctx, inst, sreg_index);

   /* No LOD for these texture types, but on GLES we emulate RECT by using
    * a normal 2D texture, so we have to give LOD 0 */
   switch (inst->Texture.Texture) {
   case TGSI_TEXTURE_RECT:
   case TGSI_TEXTURE_SHADOWRECT:
      if (ctx->cfg->use_gles) {
         snprintf(bias, 128, ", 0");
         break;
      }
      /* fallthrough */
   case TGSI_TEXTURE_BUFFER:
   case TGSI_TEXTURE_2D_MSAA:
   case TGSI_TEXTURE_2D_ARRAY_MSAA:
      break;
   default:
      snprintf(bias, 128, ", int(%s.x)", srcs[0]);
   }

   /* need to emit a textureQueryLevels */
   if (inst->Dst[0].Register.WriteMask & 0x8) {

      if (inst->Texture.Texture != TGSI_TEXTURE_BUFFER &&
          inst->Texture.Texture != TGSI_TEXTURE_RECT &&
          inst->Texture.Texture != TGSI_TEXTURE_2D_MSAA &&
          inst->Texture.Texture != TGSI_TEXTURE_2D_ARRAY_MSAA) {
         ctx->shader_req_bits |= SHADER_REQ_TXQ_LEVELS;
         if (inst->Dst[0].Register.WriteMask & 0x7)
            twm = TGSI_WRITEMASK_W;

         if (!ctx->cfg->use_gles) {
            emit_buff(&ctx->glsl_strbufs, "%s%s = %s(textureQueryLevels(%s));\n", dst,
                      get_wm_string(twm), get_string(dtypeprefix),
                      srcs[sampler_index]);
         } else {
            const struct tgsi_full_src_register *src = &inst->Src[1];

            int gles_sampler_index = 0;
            for (int i = 0; i < src->Register.Index; ++i) {
               if (ctx->samplers_used & (1 << i))
                  ++gles_sampler_index;
            }

            char sampler_str[64];

            if (ctx->info.indirect_files & (1 << TGSI_FILE_SAMPLER) && src->Register.Indirect) {
               snprintf(sampler_str, sizeof(sampler_str), "addr%d+%d", src->Indirect.Index, gles_sampler_index);
            } else {
               snprintf(sampler_str, sizeof(sampler_str), "%d", gles_sampler_index);
            }
            emit_buff(&ctx->glsl_strbufs, "%s%s = %s(%s_texlod[%s]);\n", dst, get_wm_string(twm),
                      get_string(dtypeprefix), tgsi_proc_to_prefix(ctx->info.processor),
                      sampler_str);
            ctx->gles_use_tex_query_level = true;
         }
      }

      if (inst->Dst[0].Register.WriteMask & 0x7) {
         switch (inst->Texture.Texture) {
         case TGSI_TEXTURE_1D:
         case TGSI_TEXTURE_BUFFER:
         case TGSI_TEXTURE_SHADOW1D:
            twm = TGSI_WRITEMASK_X;
            break;
         case TGSI_TEXTURE_1D_ARRAY:
         case TGSI_TEXTURE_SHADOW1D_ARRAY:
         case TGSI_TEXTURE_2D:
         case TGSI_TEXTURE_SHADOW2D:
         case TGSI_TEXTURE_RECT:
         case TGSI_TEXTURE_SHADOWRECT:
         case TGSI_TEXTURE_CUBE:
         case TGSI_TEXTURE_SHADOWCUBE:
         case TGSI_TEXTURE_2D_MSAA:
            twm = TGSI_WRITEMASK_XY;
            break;
         case TGSI_TEXTURE_3D:
         case TGSI_TEXTURE_2D_ARRAY:
         case TGSI_TEXTURE_SHADOW2D_ARRAY:
         case TGSI_TEXTURE_SHADOWCUBE_ARRAY:
         case TGSI_TEXTURE_CUBE_ARRAY:
         case TGSI_TEXTURE_2D_ARRAY_MSAA:
            twm = TGSI_WRITEMASK_XYZ;
            break;
         }
      }
   }

   if (inst->Dst[0].Register.WriteMask & 0x7) {
      char wm_buffer[16];
      bool txq_returns_vec = (inst->Texture.Texture != TGSI_TEXTURE_BUFFER) &&
                             (ctx->cfg->use_gles ||
                              (inst->Texture.Texture != TGSI_TEXTURE_1D &&
                               inst->Texture.Texture != TGSI_TEXTURE_SHADOW1D));

      if (ctx->cfg->use_gles &&
          (inst->Texture.Texture == TGSI_TEXTURE_1D_ARRAY ||
           inst->Texture.Texture == TGSI_TEXTURE_SHADOW1D_ARRAY)) {
         snprintf(wm_buffer, sizeof(wm_buffer), ".xz%s", writemask);
         writemask = wm_buffer;
      }

      emit_buff(&ctx->glsl_strbufs, "%s%s = %s(textureSize(%s%s))%s;\n", dst,
                get_wm_string(twm), get_string(dtypeprefix),
                srcs[sampler_index], bias, txq_returns_vec ? writemask : "");
   }
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
/* sample queries are pretty much separate */
static void emit_txqs(struct dump_ctx *ctx,
                      const struct tgsi_full_instruction *inst,
                      uint32_t sreg_index,
                      const char *srcs[4],
                      const char *dst)
{
   const int sampler_index = 0;
   enum vrend_type_qualifier dtypeprefix = INT_BITS_TO_FLOAT;

   ctx->shader_req_bits |= SHADER_REQ_TXQS;
   set_texture_reqs(ctx, inst, sreg_index);

   if (inst->Texture.Texture != TGSI_TEXTURE_2D_MSAA &&
       inst->Texture.Texture != TGSI_TEXTURE_2D_ARRAY_MSAA) {
      set_buf_error(&ctx->glsl_strbufs);
      return;
   }

   emit_buff(&ctx->glsl_strbufs, "%s = %s(textureSamples(%s));\n", dst,
            get_string(dtypeprefix), srcs[sampler_index]);
}

static const char *get_tex_inst_ext(const struct tgsi_full_instruction *inst)
{
   switch (inst->Instruction.Opcode) {
   case TGSI_OPCODE_TXP:
      if (inst->Texture.Texture == TGSI_TEXTURE_CUBE ||
          inst->Texture.Texture == TGSI_TEXTURE_2D_ARRAY ||
          inst->Texture.Texture == TGSI_TEXTURE_1D_ARRAY)
         return "";
      else if (inst->Texture.NumOffsets == 1)
         return "ProjOffset";
      else
         return "Proj";
   case TGSI_OPCODE_TXL:
   case TGSI_OPCODE_TXL2:
      if (inst->Texture.NumOffsets == 1)
         return "LodOffset";
      else
         return "Lod";
   case TGSI_OPCODE_TXD:
      if (inst->Texture.NumOffsets == 1)
         return "GradOffset";
      else
         return "Grad";
   case TGSI_OPCODE_TG4:
      if (inst->Texture.NumOffsets == 4)
         return "GatherOffsets";
      else if (inst->Texture.NumOffsets == 1)
         return "GatherOffset";
      else
         return "Gather";
   default:
      if (inst->Texture.NumOffsets == 1)
         return "Offset";
      else
         return  "";
   }
}

static void
get_temp(const struct dump_ctx *ctx,
         bool indirect_dim, int dim, int reg,
         char buf[static 64], bool *require_dummy_value)
{
   struct vrend_temp_range *range = find_temp_range(ctx, reg);
   if (range) {
      if (indirect_dim) {
         snprintf(buf, 64, "temp%d[addr%d + %d]", range->first, dim, reg - range->first);
      } else {
         if (range->array_id > 0) {
            snprintf(buf, 64, "temp%d[%d]", range->first, reg - range->first);
         } else {
            snprintf(buf, 64, "temp%d", reg);
         }
      }
   } else {
      snprintf(buf, 64, "dummy_value");
      *require_dummy_value = true;
   }
}

static bool fill_offset_buffer(const struct dump_ctx *ctx,
                               const struct tgsi_full_instruction *inst,
                               struct vrend_strbuf *offset_buf,
                               bool *require_dummy_value)
{
   if (inst->TexOffsets[0].File == TGSI_FILE_IMMEDIATE) {
      if (unlikely((unsigned) inst->TexOffsets[0].Index >= MAX_IMMEDIATE)) {
         virgl_error("Immediate exceeded, max is %u\n", MAX_IMMEDIATE);
         return false;
      }
      const struct immed *imd = &ctx->imm[inst->TexOffsets[0].Index];
      switch (inst->Texture.Texture) {
      case TGSI_TEXTURE_1D:
      case TGSI_TEXTURE_1D_ARRAY:
      case TGSI_TEXTURE_SHADOW1D:
      case TGSI_TEXTURE_SHADOW1D_ARRAY:
         if (!ctx->cfg->use_gles)
            strbuf_appendf(offset_buf, ", int(%d)", imd->val[inst->TexOffsets[0].SwizzleX].i);
         else
            strbuf_appendf(offset_buf, ", ivec2(%d, 0)", imd->val[inst->TexOffsets[0].SwizzleX].i);
         break;
      case TGSI_TEXTURE_RECT:
      case TGSI_TEXTURE_SHADOWRECT:
      case TGSI_TEXTURE_2D:
      case TGSI_TEXTURE_2D_ARRAY:
      case TGSI_TEXTURE_SHADOW2D:
      case TGSI_TEXTURE_SHADOW2D_ARRAY:
         strbuf_appendf(offset_buf, ", ivec2(%d, %d)", imd->val[inst->TexOffsets[0].SwizzleX].i, imd->val[inst->TexOffsets[0].SwizzleY].i);
         break;
      case TGSI_TEXTURE_3D:
         strbuf_appendf(offset_buf, ", ivec3(%d, %d, %d)", imd->val[inst->TexOffsets[0].SwizzleX].i, imd->val[inst->TexOffsets[0].SwizzleY].i,
                  imd->val[inst->TexOffsets[0].SwizzleZ].i);
         break;
      default:
         virgl_error("Unhandled texture: %x\n", inst->Texture.Texture);
         return false;
      }
   } else if (inst->TexOffsets[0].File == TGSI_FILE_TEMPORARY) {
      char temp_buf[64];
      get_temp(ctx, false, 0, inst->TexOffsets[0].Index, temp_buf, require_dummy_value);
      switch (inst->Texture.Texture) {
      case TGSI_TEXTURE_1D:
      case TGSI_TEXTURE_1D_ARRAY:
      case TGSI_TEXTURE_SHADOW1D:
      case TGSI_TEXTURE_SHADOW1D_ARRAY:

         strbuf_appendf(offset_buf, ", int(floatBitsToInt(%s.%c))",
                  temp_buf,
                  get_swiz_char(inst->TexOffsets[0].SwizzleX));
         break;
      case TGSI_TEXTURE_RECT:
      case TGSI_TEXTURE_SHADOWRECT:
      case TGSI_TEXTURE_2D:
      case TGSI_TEXTURE_2D_ARRAY:
      case TGSI_TEXTURE_SHADOW2D:
      case TGSI_TEXTURE_SHADOW2D_ARRAY:
         strbuf_appendf(offset_buf, ", ivec2(floatBitsToInt(%s.%c), floatBitsToInt(%s.%c))",
                  temp_buf,
                  get_swiz_char(inst->TexOffsets[0].SwizzleX),
                  temp_buf,
                  get_swiz_char(inst->TexOffsets[0].SwizzleY));
            break;
      case TGSI_TEXTURE_3D:
         strbuf_appendf(offset_buf, ", ivec3(floatBitsToInt(%s.%c), floatBitsToInt(%s.%c), floatBitsToInt(%s.%c)",
                  temp_buf,
                  get_swiz_char(inst->TexOffsets[0].SwizzleX),
                  temp_buf,
                  get_swiz_char(inst->TexOffsets[0].SwizzleY),
                  temp_buf,
                  get_swiz_char(inst->TexOffsets[0].SwizzleZ));
         break;
      default:
         virgl_error("Unhandled texture: %x\n", inst->Texture.Texture);
         return false;
         break;
      }
   } else if (inst->TexOffsets[0].File == TGSI_FILE_INPUT) {
      for (uint32_t j = 0; j < ctx->num_inputs; j++) {
         if (ctx->inputs[j].first != inst->TexOffsets[0].Index)
            continue;
         switch (inst->Texture.Texture) {
         case TGSI_TEXTURE_1D:
         case TGSI_TEXTURE_1D_ARRAY:
         case TGSI_TEXTURE_SHADOW1D:
         case TGSI_TEXTURE_SHADOW1D_ARRAY:
            strbuf_appendf(offset_buf, ", int(floatBitsToInt(%s.%c))",
                     ctx->inputs[j].glsl_name,
                     get_swiz_char(inst->TexOffsets[0].SwizzleX));
            break;
         case TGSI_TEXTURE_RECT:
         case TGSI_TEXTURE_SHADOWRECT:
         case TGSI_TEXTURE_2D:
         case TGSI_TEXTURE_2D_ARRAY:
         case TGSI_TEXTURE_SHADOW2D:
         case TGSI_TEXTURE_SHADOW2D_ARRAY:
            strbuf_appendf(offset_buf, ", ivec2(floatBitsToInt(%s.%c), floatBitsToInt(%s.%c))",
                     ctx->inputs[j].glsl_name,
                     get_swiz_char(inst->TexOffsets[0].SwizzleX),
                     ctx->inputs[j].glsl_name,
                     get_swiz_char(inst->TexOffsets[0].SwizzleY));
            break;
         case TGSI_TEXTURE_3D:
            strbuf_appendf(offset_buf, ", ivec3(floatBitsToInt(%s.%c), floatBitsToInt(%s.%c), floatBitsToInt(%s.%c)",
                     ctx->inputs[j].glsl_name,
                     get_swiz_char(inst->TexOffsets[0].SwizzleX),
                     ctx->inputs[j].glsl_name,
                     get_swiz_char(inst->TexOffsets[0].SwizzleY),
                     ctx->inputs[j].glsl_name,
                     get_swiz_char(inst->TexOffsets[0].SwizzleZ));
            break;
         default:
            virgl_error("Unhandled texture: %x\n", inst->Texture.Texture);
            return false;
            break;
         }
      }
   }
   return true;
}

static void
emit_lodq(struct dump_ctx *ctx,
          const struct tgsi_full_instruction *inst,
          const struct source_info *sinfo,
          const struct dest_info *dinfo,
          const char *srcs[4],
          const char *dst,
          const char *writemask)
{
   ctx->shader_req_bits |= SHADER_REQ_LODQ;

   set_texture_reqs(ctx, inst, sinfo->sreg_index);

   emit_buff(&ctx->glsl_strbufs, "%s = %s(textureQueryLOD(%s, ",
          dst, get_string(dinfo->dstconv), srcs[1]);

   switch (inst->Texture.Texture) {
   case TGSI_TEXTURE_1D:
   case TGSI_TEXTURE_1D_ARRAY:
   case TGSI_TEXTURE_SHADOW1D:
   case TGSI_TEXTURE_SHADOW1D_ARRAY:
      if (ctx->cfg->use_gles)
         emit_buff(&ctx->glsl_strbufs, "vec2(%s.x, 0)", srcs[0]);
      else
         emit_buff(&ctx->glsl_strbufs, "%s.x", srcs[0]);
      break;
   case TGSI_TEXTURE_2D:
   case TGSI_TEXTURE_2D_ARRAY:
   case TGSI_TEXTURE_2D_MSAA:
   case TGSI_TEXTURE_2D_ARRAY_MSAA:
   case TGSI_TEXTURE_RECT:
   case TGSI_TEXTURE_SHADOW2D:
   case TGSI_TEXTURE_SHADOW2D_ARRAY:
   case TGSI_TEXTURE_SHADOWRECT:
      emit_buff(&ctx->glsl_strbufs, "%s.xy", srcs[0]);
      break;
   case TGSI_TEXTURE_3D:
   case TGSI_TEXTURE_CUBE:
   case TGSI_TEXTURE_SHADOWCUBE:
   case TGSI_TEXTURE_SHADOWCUBE_ARRAY:
   case TGSI_TEXTURE_CUBE_ARRAY:
      emit_buff(&ctx->glsl_strbufs, "%s.xyz", srcs[0]);
      break;
   default:
      emit_buff(&ctx->glsl_strbufs, "%s", srcs[0]);
      break;
   }

   emit_buff(&ctx->glsl_strbufs, ")%s);\n", writemask);
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static void translate_tex(struct dump_ctx *ctx,
                          const struct tgsi_full_instruction *inst,
                          const struct source_info *sinfo,
                          const struct dest_info *dinfo,
                          const char *srcs[4],
                          const char *dst,
                          const char *writemask)
{
   enum vrend_type_qualifier txfi = TYPE_CONVERSION_NONE;
   const char *src_swizzle;
   enum vrend_type_qualifier dtypeprefix = TYPE_CONVERSION_NONE;
   bool is_shad;

   int sampler_index = 1;

   struct vrend_strbuf bias_buf;
   struct vrend_strbuf offset_buf;

   strbuf_alloc(&bias_buf, 128);
   strbuf_alloc(&offset_buf, 128);

   if (!set_texture_reqs(ctx, inst, sinfo->sreg_index)) {
      set_buf_error(&ctx->glsl_strbufs);
      goto cleanup;
   }

   is_shad = samplertype_is_shadow(inst->Texture.Texture);

   switch (ctx->samplers[sinfo->sreg_index].tgsi_sampler_return) {
   case TGSI_RETURN_TYPE_SINT:
      /* if dstconv isn't an int */
      if (dinfo->dstconv != INT)
         dtypeprefix = INT_BITS_TO_FLOAT;
      break;
   case TGSI_RETURN_TYPE_UINT:
      /* if dstconv isn't an int */
      if (dinfo->dstconv != INT)
         dtypeprefix = UINT_BITS_TO_FLOAT;
      break;
   default:
      break;
   }

   switch (inst->Texture.Texture) {
   case TGSI_TEXTURE_1D:
   case TGSI_TEXTURE_BUFFER:
      if (inst->Instruction.Opcode == TGSI_OPCODE_TXP)
         src_swizzle = "";
      else
         src_swizzle = ".x";
      txfi = INT;
      break;
   case TGSI_TEXTURE_1D_ARRAY:
      src_swizzle = ".xy";
      txfi = IVEC2;
      break;
   case TGSI_TEXTURE_2D:
   case TGSI_TEXTURE_RECT:
      if (inst->Instruction.Opcode == TGSI_OPCODE_TXP)
         src_swizzle = "";
      else
         src_swizzle = ".xy";
      txfi = IVEC2;
      break;
   case TGSI_TEXTURE_SHADOW1D:
   case TGSI_TEXTURE_SHADOW2D:
   case TGSI_TEXTURE_SHADOW1D_ARRAY:
   case TGSI_TEXTURE_SHADOWRECT:
   case TGSI_TEXTURE_3D:
      if (inst->Instruction.Opcode == TGSI_OPCODE_TXP)
         src_swizzle = "";
      else if (inst->Instruction.Opcode == TGSI_OPCODE_TG4)
         src_swizzle = ".xy";
      else
         src_swizzle = ".xyz";
      txfi = IVEC3;
      break;
   case TGSI_TEXTURE_CUBE:
   case TGSI_TEXTURE_2D_ARRAY:
      src_swizzle = ".xyz";
      txfi = IVEC3;
      break;
   case TGSI_TEXTURE_2D_MSAA:
      src_swizzle = ".xy";
      txfi = IVEC2;
      break;
   case TGSI_TEXTURE_2D_ARRAY_MSAA:
      src_swizzle = ".xyz";
      txfi = IVEC3;
      break;

   case TGSI_TEXTURE_SHADOWCUBE:
   case TGSI_TEXTURE_SHADOW2D_ARRAY:
   case TGSI_TEXTURE_SHADOWCUBE_ARRAY:
   case TGSI_TEXTURE_CUBE_ARRAY:
   default:
      if (inst->Instruction.Opcode == TGSI_OPCODE_TG4 &&
          inst->Texture.Texture != TGSI_TEXTURE_CUBE_ARRAY &&
          inst->Texture.Texture != TGSI_TEXTURE_SHADOWCUBE_ARRAY)
         src_swizzle = ".xyz";
      else
         src_swizzle = "";
      txfi = TYPE_CONVERSION_NONE;
      break;
   }

   switch (inst->Instruction.Opcode) {
   case TGSI_OPCODE_TEX2:
      sampler_index = 2;
      if (inst->Texture.Texture == TGSI_TEXTURE_SHADOWCUBE_ARRAY)
         strbuf_appendf(&bias_buf, ", %s.x", srcs[1]);
      break;
   case TGSI_OPCODE_TXB2:
   case TGSI_OPCODE_TXL2:
      sampler_index = 2;
      strbuf_appendf(&bias_buf, ", %s.x", srcs[1]);
      if (inst->Texture.Texture == TGSI_TEXTURE_SHADOWCUBE_ARRAY)
         strbuf_appendf(&bias_buf, ", %s.y", srcs[1]);
      break;
   case TGSI_OPCODE_TXB:
   case TGSI_OPCODE_TXL:
      /* On GLES we emulate the 1D array by using a 2D array, for this
       * there is no shadow lookup with bias unless EXT_texture_shadow_lod is used.
       * To avoid that compiling an invalid shader results in a crash we ignore
       * the bias value */
      if (!(ctx->cfg->use_gles && !ctx->cfg->has_texture_shadow_lod &&
            TGSI_TEXTURE_SHADOW1D_ARRAY == inst->Texture.Texture))
         strbuf_appendf(&bias_buf, ", %s.w", srcs[0]);
      break;
   case TGSI_OPCODE_TXF:
      if (inst->Texture.Texture == TGSI_TEXTURE_1D ||
          inst->Texture.Texture == TGSI_TEXTURE_2D ||
          inst->Texture.Texture == TGSI_TEXTURE_2D_MSAA ||
          inst->Texture.Texture == TGSI_TEXTURE_2D_ARRAY_MSAA ||
          inst->Texture.Texture == TGSI_TEXTURE_3D ||
          inst->Texture.Texture == TGSI_TEXTURE_1D_ARRAY ||
          inst->Texture.Texture == TGSI_TEXTURE_2D_ARRAY)
         strbuf_appendf(&bias_buf, ", int(%s.w)", srcs[0]);
      break;
   case TGSI_OPCODE_TXD:
      sampler_index = 3;
      switch (inst->Texture.Texture) {
      case TGSI_TEXTURE_1D:
      case TGSI_TEXTURE_SHADOW1D:
      case TGSI_TEXTURE_1D_ARRAY:
      case TGSI_TEXTURE_SHADOW1D_ARRAY:
         if (ctx->cfg->use_gles)
            strbuf_appendf(&bias_buf, ", vec2(%s.x, 0), vec2(%s.x, 0)", srcs[1], srcs[2]);
         else
            strbuf_appendf(&bias_buf, ", %s.x, %s.x", srcs[1], srcs[2]);
         break;
      case TGSI_TEXTURE_2D:
      case TGSI_TEXTURE_SHADOW2D:
      case TGSI_TEXTURE_2D_ARRAY:
      case TGSI_TEXTURE_SHADOW2D_ARRAY:
      case TGSI_TEXTURE_RECT:
      case TGSI_TEXTURE_SHADOWRECT:
         strbuf_appendf(&bias_buf, ", %s.xy, %s.xy", srcs[1], srcs[2]);
         break;
      case TGSI_TEXTURE_3D:
      case TGSI_TEXTURE_CUBE:
      case TGSI_TEXTURE_SHADOWCUBE:
      case TGSI_TEXTURE_CUBE_ARRAY:
         strbuf_appendf(&bias_buf, ", %s.xyz, %s.xyz", srcs[1], srcs[2]);
         break;
      default:
         strbuf_appendf(&bias_buf, ", %s, %s", srcs[1], srcs[2]);
         break;
      }
      break;
   case TGSI_OPCODE_TG4:
      sampler_index = 2;
      ctx->shader_req_bits |= SHADER_REQ_TG4;
      if (!ctx->cfg->use_gles) {
         if (inst->Texture.NumOffsets > 1 || is_shad || (ctx->shader_req_bits & SHADER_REQ_SAMPLER_RECT))
            ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      }
      if (inst->Texture.NumOffsets == 1) {
         if (inst->TexOffsets[0].File != TGSI_FILE_IMMEDIATE)
            ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      }
      if (is_shad) {
         if (inst->Texture.Texture == TGSI_TEXTURE_SHADOWCUBE ||
             inst->Texture.Texture == TGSI_TEXTURE_SHADOW2D_ARRAY)
            strbuf_appendf(&bias_buf, ", %s.w", srcs[0]);
         else if (inst->Texture.Texture == TGSI_TEXTURE_SHADOWCUBE_ARRAY)
            strbuf_appendf(&bias_buf, ", %s.x", srcs[1]);
         else
            strbuf_appendf(&bias_buf, ", %s.z", srcs[0]);
      } else if (sinfo->tg4_has_component) {
         if (inst->Texture.NumOffsets == 0) {
            if (inst->Texture.Texture == TGSI_TEXTURE_2D ||
                inst->Texture.Texture == TGSI_TEXTURE_RECT ||
                inst->Texture.Texture == TGSI_TEXTURE_CUBE ||
                inst->Texture.Texture == TGSI_TEXTURE_2D_ARRAY ||
                inst->Texture.Texture == TGSI_TEXTURE_CUBE_ARRAY)
               strbuf_appendf(&bias_buf, ", int(%s)", srcs[1]);
         } else if (inst->Texture.NumOffsets) {
            if (inst->Texture.Texture == TGSI_TEXTURE_2D ||
                inst->Texture.Texture == TGSI_TEXTURE_RECT ||
                inst->Texture.Texture == TGSI_TEXTURE_2D_ARRAY)
               strbuf_appendf(&bias_buf, ", int(%s)", srcs[1]);
         }
      }
      break;
   default:
      ;
   }

   bool exchange_bias_offset = false;
   if (inst->Texture.NumOffsets == 1) {
      if (unlikely((unsigned) inst->TexOffsets[0].Index >= MAX_IMMEDIATE)) {
         virgl_error("Immediate exceeded, max is %u\n", MAX_IMMEDIATE);
         set_buf_error(&ctx->glsl_strbufs);
         goto cleanup;
      }

      if (!fill_offset_buffer(ctx, inst, &offset_buf, &ctx->require_dummy_value)) {
         set_buf_error(&ctx->glsl_strbufs);
         goto cleanup;
      }

      exchange_bias_offset = inst->Instruction.Opcode == TGSI_OPCODE_TXL ||
         inst->Instruction.Opcode == TGSI_OPCODE_TXL2 ||
         inst->Instruction.Opcode == TGSI_OPCODE_TXD ||
         (inst->Instruction.Opcode == TGSI_OPCODE_TG4 && is_shad);

   }

   bool has_bias = strbuf_get_len (&bias_buf) != 0;
   bool has_offset = strbuf_get_len (&offset_buf) != 0;
   // EXT_texture_shadow_lod defines a few more functions handling bias
   if (has_bias &&
       (inst->Texture.Texture == TGSI_TEXTURE_SHADOW2D_ARRAY ||
        inst->Texture.Texture == TGSI_TEXTURE_SHADOWCUBE ||
        inst->Texture.Texture == TGSI_TEXTURE_SHADOWCUBE_ARRAY))
      ctx->shader_req_bits |= SHADER_REQ_TEXTURE_SHADOW_LOD;

   // EXT_texture_shadow_lod also adds the missing textureOffset for 2DArrayShadow in GLES
   if ((has_bias || has_offset) && ctx->cfg->use_gles &&
       (inst->Texture.Texture == TGSI_TEXTURE_SHADOW1D_ARRAY ||
        inst->Texture.Texture == TGSI_TEXTURE_SHADOW2D_ARRAY))
      ctx->shader_req_bits |= SHADER_REQ_TEXTURE_SHADOW_LOD;

   char buf[255];
   const char *new_srcs[4] = { buf, srcs[1], srcs[2], srcs[3] };
   const char *tex_ext = get_tex_inst_ext(inst);
   const char *bias = exchange_bias_offset ? offset_buf.buf : bias_buf.buf;
   const char *offset = exchange_bias_offset ? bias_buf.buf : offset_buf.buf;

   /* We have to unnormalize the coordinate for all but the texel fetch instruction */
   if (inst->Instruction.Opcode != TGSI_OPCODE_TXF &&
       vrend_shader_sampler_views_mask_get(ctx->key->sampler_views_emulated_rect_mask, sinfo->sreg_index)) {

      const char *bias = "";

      /* No LOD for these texture types, but on GLES we emulate RECT by using
       * a normal 2D texture, so we have to give LOD 0 */
      switch (inst->Texture.Texture) {
      case TGSI_TEXTURE_BUFFER:
      case TGSI_TEXTURE_2D_MSAA:
      case TGSI_TEXTURE_2D_ARRAY_MSAA:
         break;
      case TGSI_TEXTURE_RECT:
      case TGSI_TEXTURE_SHADOWRECT:
         if (!ctx->cfg->use_gles)
            break;
         /* fallthrough */
      default:
         bias = ", 0";
      }

      switch (inst->Instruction.Opcode) {
      case TGSI_OPCODE_TXP:
         snprintf(buf, 255, "vec4(%s)/vec4(textureSize(%s%s), 1, 1)", srcs[0], srcs[sampler_index], bias);
         break;

      case TGSI_OPCODE_TG4:
         snprintf(buf, 255, "%s.xy/vec2(textureSize(%s%s))", srcs[0], srcs[sampler_index], bias);
         break;

      default:
         /* Non TG4 ops have the compare value in the z components */
         if (inst->Texture.Texture == TGSI_TEXTURE_SHADOWRECT) {
            snprintf(buf, 255, "vec3(%s.xy/vec2(textureSize(%s%s)), %s.z)", srcs[0], srcs[sampler_index], bias, srcs[0]);
         } else
            snprintf(buf, 255, "%s.xy/vec2(textureSize(%s%s))", srcs[0], srcs[sampler_index], bias);
      }
      srcs = new_srcs;
   }

   if (inst->Instruction.Opcode == TGSI_OPCODE_TXF) {
      if (ctx->cfg->use_gles &&
          (inst->Texture.Texture == TGSI_TEXTURE_1D ||
           inst->Texture.Texture == TGSI_TEXTURE_1D_ARRAY ||
           inst->Texture.Texture == TGSI_TEXTURE_RECT)) {
         if (inst->Texture.Texture == TGSI_TEXTURE_1D)
            emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texelFetch%s(%s, ivec2(%s(%s%s), 0)%s%s)%s));\n",
                      dst, get_string(dinfo->dstconv), get_string(dtypeprefix),
                      tex_ext, srcs[sampler_index], get_string(txfi),
                      srcs[0], src_swizzle, bias, offset,
                      dinfo->dst_override_no_wm[0] ? "" : writemask);
         else if (inst->Texture.Texture == TGSI_TEXTURE_1D_ARRAY) {
            /* the y coordinate must go into the z element and the y must be zero */
            emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texelFetch%s(%s, ivec3(%s(%s%s), 0).xzy%s%s)%s));\n",
                      dst, get_string(dinfo->dstconv), get_string(dtypeprefix),
                      tex_ext, srcs[sampler_index], get_string(txfi),
                      srcs[0], src_swizzle, bias, offset,
                      dinfo->dst_override_no_wm[0] ? "" : writemask);
         } else {
            emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texelFetch%s(%s, %s(%s%s), 0%s)%s));\n",
                      dst, get_string(dinfo->dstconv), get_string(dtypeprefix),
                      tex_ext, srcs[sampler_index], get_string(txfi),
                      srcs[0], src_swizzle, offset,
                      dinfo->dst_override_no_wm[0] ? "" : writemask);
         }
      } else {

         /* To inject the swizzle for texturebufffers with emulated formats do
          *
          * {
          *    vec4 val = texelFetch( )
          *    val = vec4(0/1/swizzle_x, ...);
          *    dest.writemask = val.writemask;
          * }
          *
          */
         emit_buff(&ctx->glsl_strbufs, "{\n  vec4 val = %s(texelFetch%s(%s, %s(%s%s)%s%s));\n",
                   get_string(dtypeprefix),
                   tex_ext, srcs[sampler_index], get_string(txfi),
                   srcs[0], src_swizzle, bias, offset);

         if (vrend_shader_sampler_views_mask_get(ctx->key->sampler_views_lower_swizzle_mask, sinfo->sreg_index)) {
            int16_t  packed_swizzles = ctx->key->tex_swizzle[sinfo->sreg_index];
            emit_buff(&ctx->glsl_strbufs,  "   val = vec4(");

            for (int i = 0; i < 4; ++i) {
               if (i > 0)
                  emit_buff(&ctx->glsl_strbufs,  ", ");

               int swz = (packed_swizzles >> (i * 3)) & 7;
               switch (swz) {
               case PIPE_SWIZZLE_0 : emit_buf(&ctx->glsl_strbufs,  "0.0"); break;
               case PIPE_SWIZZLE_1 :
                  switch (dtypeprefix) {
                  case UINT_BITS_TO_FLOAT:
                     emit_buf(&ctx->glsl_strbufs,  "uintBitsToFloat(1u)");
                     break;
                  case INT_BITS_TO_FLOAT:
                     emit_buf(&ctx->glsl_strbufs,  "intBitsToFloat(1)");
                     break;
                  default:
                     emit_buf(&ctx->glsl_strbufs,  "1.0");
                     break;
                  }
                  break;
               default:
                  emit_buff(&ctx->glsl_strbufs,  "val%s", get_swizzle_string(swz));
               }
            }

            emit_buff(&ctx->glsl_strbufs,  ");\n");
         }

         emit_buff(&ctx->glsl_strbufs, "  %s  = val%s;\n}\n",
                   dst, dinfo->dst_override_no_wm[0] ? "" : writemask);
      }
   } else if ((ctx->cfg->glsl_version < 140 && (ctx->shader_req_bits & SHADER_REQ_SAMPLER_RECT)) &&
              !vrend_shader_sampler_views_mask_get(ctx->key->sampler_views_emulated_rect_mask, sinfo->sreg_index)) {
      /* rect is special in GLSL 1.30 */
      if (inst->Texture.Texture == TGSI_TEXTURE_RECT)
         emit_buff(&ctx->glsl_strbufs, "%s = texture2DRect(%s, %s.xy)%s;\n",
                   dst, srcs[sampler_index], srcs[0], writemask);
      else if (inst->Texture.Texture == TGSI_TEXTURE_SHADOWRECT)
         emit_buff(&ctx->glsl_strbufs, "%s = shadow2DRect(%s, %s.xyz)%s;\n",
                   dst, srcs[sampler_index], srcs[0], writemask);
   } else if (is_shad && inst->Instruction.Opcode != TGSI_OPCODE_TG4) { /* TGSI returns 1.0 in alpha */
      const char *cname = tgsi_proc_to_prefix(ctx->prog_type);
      const struct tgsi_full_src_register *src = &inst->Src[sampler_index];

      if (ctx->cfg->use_gles &&
          (inst->Texture.Texture == TGSI_TEXTURE_SHADOW1D ||
           inst->Texture.Texture == TGSI_TEXTURE_SHADOW1D_ARRAY)) {
         if (inst->Texture.Texture == TGSI_TEXTURE_SHADOW1D) {
            if (inst->Instruction.Opcode == TGSI_OPCODE_TXP)
               emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(vec4(vec4(texture%s(%s, vec4(%s%s.xzw, 0).xwyz %s%s)) * %sshadmask%d + %sshadadd%d)%s));\n",
                         dst, get_string(dinfo->dstconv),
                         get_string(dtypeprefix), tex_ext, srcs[sampler_index],
                         srcs[0], src_swizzle, offset, bias,
                         cname, src->Register.Index,
                         cname, src->Register.Index, writemask);
            else
               emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(vec4(vec4(texture%s(%s, vec3(%s%s.xz, 0).xzy %s%s)) * %sshadmask%d + %sshadadd%d)%s));\n",
                         dst, get_string(dinfo->dstconv),
                         get_string(dtypeprefix), tex_ext, srcs[sampler_index],
                         srcs[0], src_swizzle, offset, bias,
                         cname, src->Register.Index,
                         cname, src->Register.Index, writemask);
         } else if (inst->Texture.Texture == TGSI_TEXTURE_SHADOW1D_ARRAY) {
            emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(vec4(vec4(texture%s(%s, vec4(%s%s, 0).xwyz %s%s)) * %sshadmask%d + %sshadadd%d)%s));\n",
                      dst, get_string(dinfo->dstconv), get_string(dtypeprefix),
                      tex_ext, srcs[sampler_index], srcs[0],
                      src_swizzle, offset, bias, cname,
                      src->Register.Index, cname,
                      src->Register.Index, writemask);
         }
      } else
         emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(vec4(vec4(texture%s(%s, %s%s%s%s)) * %sshadmask%d + %sshadadd%d)%s));\n",
                   dst, get_string(dinfo->dstconv), get_string(dtypeprefix),
                   tex_ext, srcs[sampler_index], srcs[0],
                   src_swizzle, offset, bias,
                   cname, src->Register.Index,
                   cname, src->Register.Index, writemask);
   } else {
      /* OpenGL ES do not support 1D texture
       * so we use a 2D texture with a parameter set to 0.5
       */
      if (ctx->cfg->use_gles &&
          (inst->Texture.Texture == TGSI_TEXTURE_1D ||
           inst->Texture.Texture == TGSI_TEXTURE_1D_ARRAY)) {
         if (inst->Texture.Texture == TGSI_TEXTURE_1D) {
            if (inst->Instruction.Opcode == TGSI_OPCODE_TXP)
               emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texture%s(%s, vec3(%s.xw, 0).xzy %s%s)%s));\n",
                         dst, get_string(dinfo->dstconv),
                         get_string(dtypeprefix), tex_ext, srcs[sampler_index],
                         srcs[0], offset, bias,
                         dinfo->dst_override_no_wm[0] ? "" : writemask);
            else
               emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texture%s(%s, vec2(%s%s, 0.5) %s%s)%s));\n",
                         dst, get_string(dinfo->dstconv),
                         get_string(dtypeprefix), tex_ext, srcs[sampler_index],
                         srcs[0], src_swizzle, offset, bias,
                         dinfo->dst_override_no_wm[0] ? "" : writemask);
         } else if (inst->Texture.Texture == TGSI_TEXTURE_1D_ARRAY) {
            if (inst->Instruction.Opcode == TGSI_OPCODE_TXP)
               emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texture%s(%s, vec3(%s.x / %s.w, 0, %s.y) %s%s)%s));\n",
                         dst, get_string(dinfo->dstconv),
                         get_string(dtypeprefix), tex_ext, srcs[sampler_index],
                         srcs[0], srcs[0], srcs[0], offset, bias,
                         dinfo->dst_override_no_wm[0] ? "" : writemask);
            else
               emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texture%s(%s, vec3(%s%s, 0).xzy %s%s)%s));\n",
                         dst, get_string(dinfo->dstconv),
                         get_string(dtypeprefix), tex_ext, srcs[sampler_index],
                         srcs[0], src_swizzle, offset, bias,
                         dinfo->dst_override_no_wm[0] ? "" : writemask);
         }
      } else {
         emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(texture%s(%s, %s%s%s%s)%s));\n",
                   dst, get_string(dinfo->dstconv), get_string(dtypeprefix),
                   tex_ext, srcs[sampler_index], srcs[0], src_swizzle,
                   offset, bias, dinfo->dst_override_no_wm[0] ? "" : writemask);
      }
   }

cleanup:
   strbuf_free(&offset_buf);
   strbuf_free(&bias_buf);
}

static void
create_swizzled_clipdist(const struct dump_ctx *ctx,
                         struct vrend_strbuf *result,
                         const struct tgsi_full_src_register *src,
                         int input_idx,
                         bool gl_in,
                         const char *stypeprefix,
                         const char *prefix,
                         const char *arrayname, int offset)
{
   char clipdistvec[4][80] = { 0, };

   char clip_indirect[32] = "";

   bool has_prop = (ctx->num_cull_dist_prop + ctx->num_clip_dist_prop) > 0;
   int num_culls = has_prop ? ctx->num_cull_dist_prop : ctx->key->num_out_cull;
   int num_clips = has_prop ? ctx->num_clip_dist_prop : ctx->key->num_out_clip;

   int num_clip_cull = num_culls + num_clips;
   if (ctx->num_in_clip_dist && !num_clip_cull)
      num_clips = ctx->num_in_clip_dist;

   int base_idx = ctx->inputs[input_idx].sid * 4;

   // This doesn't work for indirect adressing
   int base_offset = (src->Register.Index - offset) * 4;

   /* With arrays enabled , but only when gl_ClipDistance or gl_CullDistance are emitted (>4)
    * then we need to add indirect addressing */
   if (src->Register.Indirect && ((num_clips > 4 && base_idx < num_clips) || num_culls > 4))
      snprintf(clip_indirect, 32, "4*addr%d +", src->Indirect.Index);
   else if (src->Register.Index != offset)
      snprintf(clip_indirect, 32, "4*%d +", src->Register.Index - offset);

   for (unsigned cc = 0; cc < 4; cc++) {
      const char *cc_name = ctx->inputs[input_idx].glsl_name;
      int idx = base_idx;
      if (cc == 0)
         idx += src->Register.SwizzleX;
      else if (cc == 1)
         idx += src->Register.SwizzleY;
      else if (cc == 2)
         idx += src->Register.SwizzleZ;
      else if (cc == 3)
         idx += src->Register.SwizzleW;

      if (num_culls) {
         if (idx + base_offset >= num_clips) {
            idx -= num_clips;
            cc_name = "gl_CullDistance";
         }
      }

#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wformat-truncation"
#endif
      if (gl_in)
         snprintf(clipdistvec[cc], 80, "%sgl_in%s.%s[%s %d]", prefix, arrayname, cc_name, clip_indirect,  idx);
      else
         snprintf(clipdistvec[cc], 80, "%s%s%s[%s %d]", prefix, arrayname, cc_name, clip_indirect, idx);
#pragma GCC diagnostic pop
   }
   strbuf_fmt(result, "%s(vec4(%s,%s,%s,%s))", stypeprefix, clipdistvec[0], clipdistvec[1], clipdistvec[2], clipdistvec[3]);
}

static
void load_clipdist_fs(const struct dump_ctx *ctx,
                      struct vrend_strbuf *result,
                      const struct tgsi_full_src_register *src,
                      int input_idx,
                      const char *stypeprefix,
                      int offset)
{
   char clip_indirect[32] = "";

   char swz[5] = {
      get_swiz_char(src->Register.SwizzleX),
      get_swiz_char(src->Register.SwizzleY),
      get_swiz_char(src->Register.SwizzleZ),
      get_swiz_char(src->Register.SwizzleW),
      0
   };

   int base_idx = ctx->inputs[input_idx].sid;

   /* With arrays enabled , but only when gl_ClipDistance or gl_CullDistance are emitted (>4)
    * then we need to add indirect addressing */
   if (src->Register.Indirect)
      snprintf(clip_indirect, 32, "addr%d + %d", src->Indirect.Index, base_idx);
   else
      snprintf(clip_indirect, 32, "%d + %d", src->Register.Index - offset, base_idx);

   strbuf_fmt(result, "%s(clip_dist_temp[%s].%s)", stypeprefix, clip_indirect, swz);
}


static enum vrend_type_qualifier get_coord_prefix(int resource, bool *is_ms, bool use_gles)
{
   switch(resource) {
   case TGSI_TEXTURE_1D:
      return use_gles ? IVEC2: INT;
   case TGSI_TEXTURE_BUFFER:
      return INT;
   case TGSI_TEXTURE_1D_ARRAY:
      return use_gles ? IVEC3: IVEC2;
   case TGSI_TEXTURE_2D:
   case TGSI_TEXTURE_RECT:
      return IVEC2;
   case TGSI_TEXTURE_3D:
   case TGSI_TEXTURE_CUBE:
   case TGSI_TEXTURE_2D_ARRAY:
   case TGSI_TEXTURE_CUBE_ARRAY:
      return IVEC3;
   case TGSI_TEXTURE_2D_MSAA:
      *is_ms = true;
      return IVEC2;
   case TGSI_TEXTURE_2D_ARRAY_MSAA:
      *is_ms = true;
      return IVEC3;
   default:
      return TYPE_CONVERSION_NONE;
   }
}

static bool is_integer_memory(const struct dump_ctx *ctx, enum tgsi_file_type file_type, uint32_t index)
{
   switch(file_type) {
   case TGSI_FILE_BUFFER:
      return !!(ctx->ssbo_integer_mask & (1 << index));
   case TGSI_FILE_MEMORY:
      return ctx->integer_memory;
   default:
      virgl_error("Invalid file type");
   }

   return false;
}

static void set_image_qualifier(struct vrend_shader_image images[],
                                uint32_t image_used_mask,
                                const struct tgsi_full_instruction *inst,
                                uint32_t reg_index, bool indirect)
{
   if (inst->Memory.Qualifier == TGSI_MEMORY_COHERENT) {
      if (indirect) {
         while (image_used_mask)
            images[u_bit_scan(&image_used_mask)].coherent = true;
      } else
         images[reg_index].coherent = true;
   }
}

static void set_memory_qualifier(uint8_t ssbo_memory_qualifier[],
                                 uint32_t ssbo_used_mask,
                                 const struct tgsi_full_instruction *inst,
                                 uint32_t reg_index, bool indirect)
{
   if (inst->Memory.Qualifier == TGSI_MEMORY_COHERENT) {
      if (indirect) {
         while (ssbo_used_mask)
            ssbo_memory_qualifier[u_bit_scan(&ssbo_used_mask)] = TGSI_MEMORY_COHERENT;
      } else
         ssbo_memory_qualifier[reg_index] = TGSI_MEMORY_COHERENT;
   }
}

static void emit_store_mem(struct vrend_glsl_strbufs *glsl_strbufs, const char *dst, int writemask,
                           const char *srcs[4], const char *conversion)
{
   static const char swizzle_char[] = "xyzw";
   for (int i = 0; i < 4; ++i) {
      if (writemask & (1 << i)) {
         emit_buff(glsl_strbufs, "%s[(uint(floatBitsToUint(%s)) >> 2) + %du] = %s(%s).%c;\n",
                   dst, srcs[0], i, conversion, srcs[1], swizzle_char[i]);
      }
   }
}

static void make_ssbo_varstring(const struct dump_ctx *ctx, char result[128],
                                unsigned register_index, bool indirect, int indirect_register)
{
   const char *cname = tgsi_proc_to_prefix(ctx->prog_type);
   bool atomic_ssbo = ctx->ssbo_atomic_mask & (1 << register_index);
   const char *atomic_str = atomic_ssbo ? "atomic" : "";
   unsigned base = atomic_ssbo ? ctx->ssbo_atomic_array_base : ctx->ssbo_array_base;

   if (ctx->info.indirect_files & (1 << TGSI_FILE_BUFFER)) {
      if (indirect && !ctx->cfg->use_gles)
         snprintf(result, 128, "%sssboarr%s[addr%d + %d].%sssbocontents%d", cname, atomic_str,
                  indirect_register, register_index - base, cname, base);
      else
         snprintf(result, 128, "%sssboarr%s[%d].%sssbocontents%d", cname, atomic_str,
                  register_index - base, cname, base);
   } else {
      snprintf(result, 128, "%sssbocontents%d", cname, register_index);
   }
}

static void
translate_store(const struct dump_ctx *ctx,
                struct vrend_glsl_strbufs *glsl_strbufs,
                uint8_t ssbo_memory_qualifier[],
                struct vrend_shader_image images[],
                const struct tgsi_full_instruction *inst,
                struct source_info *sinfo,
                const char *srcs[4],
                const struct dest_info *dinfo,
                const char *dst)
{
   const struct tgsi_full_dst_register *dst_reg = &inst->Dst[0];

   if (dinfo->dest_index < 0) {
      set_buf_error(glsl_strbufs);
      return;
   }
   if (dst_reg->Register.File == TGSI_FILE_IMAGE) {

      /* bail out if we want to write to a non-existing image */
      if (!((1 << dinfo->dest_index) & ctx->images_used_mask))
            return;

      set_image_qualifier(images, ctx->images_used_mask, inst, inst->Src[0].Register.Index, inst->Src[0].Register.Indirect);

      bool is_ms = false;
      enum vrend_type_qualifier coord_prefix = get_coord_prefix(ctx->images[dst_reg->Register.Index].decl.Resource, &is_ms, ctx->cfg->use_gles);
      enum tgsi_return_type itype;
      char ms_str[32] = "";
      enum vrend_type_qualifier stypeprefix = TYPE_CONVERSION_NONE;
      const char *conversion = sinfo->override_no_cast[0] ? "" : get_string(FLOAT_BITS_TO_INT);
      get_internalformat_string(inst->Memory.Format, &itype);
      if (is_ms) {
         snprintf(ms_str, 32, "int(%s.w),", srcs[0]);
      }
      switch (itype) {
      case TGSI_RETURN_TYPE_UINT:
         stypeprefix = FLOAT_BITS_TO_UINT;
         break;
      case TGSI_RETURN_TYPE_SINT:
         stypeprefix = FLOAT_BITS_TO_INT;
         break;
      default:
         break;
      }
      if (!ctx->cfg->use_gles || !dst_reg->Register.Indirect) {
         emit_buff(glsl_strbufs, "imageStore(%s,%s(%s(%s)),%s%s(%s));\n",
                   dst, get_string(coord_prefix), conversion, srcs[0],
                   ms_str, get_string(stypeprefix), srcs[1]);
      } else {
         struct vrend_array *image = lookup_image_array_ptr(ctx, dst_reg->Register.Index);
         if (image) {
            int basearrayidx = image->first;
            int array_size = image->array_size;
            emit_buff(glsl_strbufs, "switch (addr%d + %d) {\n", dst_reg->Indirect.Index,
                      dst_reg->Register.Index - basearrayidx);
            const char *cname = tgsi_proc_to_prefix(ctx->prog_type);

            for (int i = 0; i < array_size; ++i) {
               emit_buff(glsl_strbufs, "case %d: imageStore(%simg%d[%d],%s(%s(%s)),%s%s(%s)); break;\n",
                         i, cname, basearrayidx, i, get_string(coord_prefix),
                         conversion, srcs[0], ms_str, get_string(stypeprefix),
                         srcs[1]);
            }
            emit_buff(glsl_strbufs, "}\n");
         }
      }
   } else if (dst_reg->Register.File == TGSI_FILE_BUFFER ||
              dst_reg->Register.File == TGSI_FILE_MEMORY) {
      enum vrend_type_qualifier dtypeprefix;
      set_memory_qualifier(ssbo_memory_qualifier, ctx->ssbo_used_mask, inst, dst_reg->Register.Index,
                           dst_reg->Register.Indirect);
      dtypeprefix = is_integer_memory(ctx, dst_reg->Register.File, dst_reg->Register.Index) ?
                    FLOAT_BITS_TO_INT : FLOAT_BITS_TO_UINT;
      const char *conversion = sinfo->override_no_cast[1] ? "" : get_string(dtypeprefix);

      if (!ctx->cfg->use_gles || !dst_reg->Register.Indirect) {
         emit_store_mem(glsl_strbufs, dst, dst_reg->Register.WriteMask, srcs,
                        conversion);
      } else {
         bool atomic_ssbo = ctx->ssbo_atomic_mask & (1 << dst_reg->Register.Index);
         int base = atomic_ssbo ? ctx->ssbo_atomic_array_base : ctx->ssbo_array_base;
         uint32_t mask = ctx->ssbo_used_mask;
         int start, array_count;
         u_bit_scan_consecutive_range(&mask, &start, &array_count);

         emit_buff(glsl_strbufs, "switch (addr%d + %d) {\n", dst_reg->Indirect.Index,
                   dst_reg->Register.Index - base);

         for (int i = 0; i < array_count; ++i)  {
            char dst_tmp[128];
            emit_buff(glsl_strbufs, "case %d:\n", i);
            make_ssbo_varstring(ctx, dst_tmp, i + start, false, 0);
            emit_store_mem(glsl_strbufs, dst_tmp, dst_reg->Register.WriteMask, srcs,
                           conversion);
            emit_buff(glsl_strbufs, "break;\n");
         }
         emit_buf(glsl_strbufs, "}\n");
      }
   }
}

static void emit_load_mem(struct vrend_glsl_strbufs *glsl_strbufs, const char *dst, int writemask,
                          const char *conversion, const char *atomic_op, const char *src0,
                          const char *atomic_src)
{
   static const char swizzle_char[] = "xyzw";
   for (int i = 0; i < 4; ++i) {
      if (writemask & (1 << i)) {
         emit_buff(glsl_strbufs, "%s.%c = (%s(%s(%s[ssbo_addr_temp + %du]%s)));\n", dst,
                   swizzle_char[i], conversion, atomic_op, src0, i, atomic_src);
      }
   }
}

static bool
translate_load(const struct dump_ctx *ctx,
               struct vrend_glsl_strbufs *glsl_strbufs,
               uint8_t ssbo_memory_qualifier[],
               struct vrend_shader_image images[],
               const struct tgsi_full_instruction *inst,
               struct source_info *sinfo,
               struct dest_info *dinfo,
               const char *srcs[4],
               const char *dst,
               const char *writemask)
{
   const struct tgsi_full_src_register *src = &inst->Src[0];
   if (src->Register.File == TGSI_FILE_IMAGE) {

      /* Bail out if we want to load from an image that is not actually used */
      if (sinfo->sreg_index < 0) {
         return false;
      }
      if (!((1 << sinfo->sreg_index) & ctx->images_used_mask))
            return false;

      set_image_qualifier(images, ctx->images_used_mask, inst, inst->Src[0].Register.Index, inst->Src[0].Register.Indirect);


      bool is_ms = false;
      enum vrend_type_qualifier coord_prefix = get_coord_prefix(ctx->images[sinfo->sreg_index].decl.Resource, &is_ms, ctx->cfg->use_gles);
      enum vrend_type_qualifier dtypeprefix = TYPE_CONVERSION_NONE;
      const char *conversion = sinfo->override_no_cast[1] ? "" : get_string(FLOAT_BITS_TO_INT);
      enum tgsi_return_type itype;
      get_internalformat_string(ctx->images[sinfo->sreg_index].decl.Format, &itype);
      char ms_str[32] = "";
      const char *wm = dinfo->dst_override_no_wm[0] ? "" : writemask;
      if (is_ms) {
         snprintf(ms_str, 32, ", int(%s.w)", srcs[1]);
      }
      switch (itype) {
      case TGSI_RETURN_TYPE_UINT:
         dtypeprefix = UINT_BITS_TO_FLOAT;
         break;
      case TGSI_RETURN_TYPE_SINT:
         dtypeprefix = INT_BITS_TO_FLOAT;
         break;
      default:
         break;
      }

      /* On GL WR translates to writable, but on GLES we translate this to writeonly
       * because for most formats one has to specify one or the other, so if we have an
       * image with the TGSI WR specification, and read from it, we drop the Writable flag.
       * For the images that allow RW this is of no consequence, and for the others a write
       * access will fail instead of the read access, but this doesn't constitue a regression
       * because we couldn't do both, read and write, anyway. */
      if (ctx->cfg->use_gles && ctx->images[sinfo->sreg_index].decl.Writable &&
          (ctx->images[sinfo->sreg_index].decl.Format != PIPE_FORMAT_R32_FLOAT) &&
          (ctx->images[sinfo->sreg_index].decl.Format != PIPE_FORMAT_R32_SINT) &&
          (ctx->images[sinfo->sreg_index].decl.Format != PIPE_FORMAT_R32_UINT))
         images[sinfo->sreg_index].decl.Writable = 0;

      if (!ctx->cfg->use_gles || !inst->Src[0].Register.Indirect) {
         emit_buff(glsl_strbufs, "%s = %s(imageLoad(%s, %s(%s(%s))%s)%s);\n",
               dst, get_string(dtypeprefix),
               srcs[0], get_string(coord_prefix), conversion, srcs[1],
               ms_str, wm);
      } else {
         char src[32] = "";
         struct vrend_array *image = lookup_image_array_ptr(ctx, inst->Src[0].Register.Index);
         if (image) {
            int basearrayidx = image->first;
            int array_size = image->array_size;
            emit_buff(glsl_strbufs, "switch (addr%d + %d) {\n", inst->Src[0].Indirect.Index, inst->Src[0].Register.Index - basearrayidx);
            const char *cname = tgsi_proc_to_prefix(ctx->prog_type);

            for (int i = 0; i < array_size; ++i) {
               snprintf(src, 32, "%simg%d[%d]", cname, basearrayidx, i);
               emit_buff(glsl_strbufs, "case %d: %s = %s(imageLoad(%s, %s(%s(%s))%s)%s);break;\n",
                         i, dst, get_string(dtypeprefix),
                         src, get_string(coord_prefix), conversion, srcs[1],
                         ms_str, wm);
            }
            emit_buff(glsl_strbufs, "}\n");
         }
      }
   } else if (src->Register.File == TGSI_FILE_BUFFER ||
              src->Register.File == TGSI_FILE_MEMORY) {
      char mydst[255], atomic_op[9], atomic_src[10];
      enum vrend_type_qualifier dtypeprefix;

      set_memory_qualifier(ssbo_memory_qualifier, ctx->ssbo_used_mask, inst, inst->Src[0].Register.Index, inst->Src[0].Register.Indirect);

      const char *d = dst;
      char *md = mydst;
      unsigned i = 0;
      while ((i < sizeof(mydst) - 1) && *d && *d != '.')
         *md++ = *d++;
      *md = 0;

      emit_buff(glsl_strbufs, "ssbo_addr_temp = uint(floatBitsToUint(%s)) >> 2;\n", srcs[1]);

      atomic_op[0] = atomic_src[0] = '\0';
      if (ctx->ssbo_atomic_mask & (1 << src->Register.Index)) {
         /* Emulate atomicCounter with atomicOr. */
         strcpy(atomic_op, "atomicOr");
         strcpy(atomic_src, ", uint(0)");
      }

      dtypeprefix = (is_integer_memory(ctx, src->Register.File, src->Register.Index)) ? INT_BITS_TO_FLOAT : UINT_BITS_TO_FLOAT;

      if (!ctx->cfg->use_gles || !inst->Src[0].Register.Indirect) {
         emit_load_mem(glsl_strbufs, mydst, inst->Dst[0].Register.WriteMask, get_string(dtypeprefix), atomic_op, srcs[0], atomic_src);
      } else {
         char src[128] = "";

         bool atomic_ssbo = ctx->ssbo_atomic_mask & (1 << inst->Src[0].Register.Index);
         unsigned base = atomic_ssbo ? ctx->ssbo_atomic_array_base : ctx->ssbo_array_base;
         int start, array_count;
         uint32_t mask = ctx->ssbo_used_mask;
         u_bit_scan_consecutive_range(&mask, &start, &array_count);

         emit_buff(glsl_strbufs, "switch (addr%d + %d) {\n", inst->Src[0].Indirect.Index, inst->Src[0].Register.Index - base);
         for (int i = 0; i < array_count; ++i) {
            emit_buff(glsl_strbufs, "case %d:\n", i);
            make_ssbo_varstring(ctx, src, i + start, false, 0);
            emit_load_mem(glsl_strbufs, mydst, inst->Dst[0].Register.WriteMask, get_string(dtypeprefix), atomic_op, src, atomic_src);
            emit_buff(glsl_strbufs, "  break;\n");
         }
         emit_buf(glsl_strbufs, "}\n");
      }
   } else if (src->Register.File == TGSI_FILE_HW_ATOMIC) {
      emit_buff(glsl_strbufs, "%s = uintBitsToFloat(atomicCounter(%s));\n", dst, srcs[0]);
   }
   return true;
}

static const char *get_atomic_opname(int tgsi_opcode, bool *is_cas)
{
   const char *opname;
   *is_cas = false;
   switch (tgsi_opcode) {
   case TGSI_OPCODE_ATOMUADD:
      opname = "Add";
      break;
   case TGSI_OPCODE_ATOMXCHG:
      opname = "Exchange";
      break;
   case TGSI_OPCODE_ATOMCAS:
      opname = "CompSwap";
      *is_cas = true;
      break;
   case TGSI_OPCODE_ATOMAND:
      opname = "And";
      break;
   case TGSI_OPCODE_ATOMOR:
      opname = "Or";
      break;
   case TGSI_OPCODE_ATOMXOR:
      opname = "Xor";
      break;
   case TGSI_OPCODE_ATOMUMIN:
      opname = "Min";
      break;
   case TGSI_OPCODE_ATOMUMAX:
      opname = "Max";
      break;
   case TGSI_OPCODE_ATOMIMIN:
      opname = "Min";
      break;
   case TGSI_OPCODE_ATOMIMAX:
      opname = "Max";
      break;
   default:
      virgl_error("Illegal atomic opcode");
      return NULL;
   }
   return opname;
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static void
translate_resq(struct dump_ctx *ctx, const struct tgsi_full_instruction *inst,
               const char *srcs[4], const char *dst, const char *writemask)
{
   const struct tgsi_full_src_register *src = &inst->Src[0];

   if (src->Register.File == TGSI_FILE_IMAGE) {
      if (inst->Dst[0].Register.WriteMask & 0x8) {
         ctx->shader_req_bits |= SHADER_REQ_TXQS | SHADER_REQ_INTS;
         emit_buff(&ctx->glsl_strbufs, "%s = %s(imageSamples(%s));\n",
                   dst, get_string(INT_BITS_TO_FLOAT), srcs[0]);
      }
      if (inst->Dst[0].Register.WriteMask & 0x7) {
         const char *swizzle_mask = (ctx->cfg->use_gles && inst->Memory.Texture == TGSI_TEXTURE_1D_ARRAY) ?
                                   ".xz" : "";
         ctx->shader_req_bits |= SHADER_REQ_IMAGE_SIZE | SHADER_REQ_INTS;
         bool skip_emit_writemask = inst->Memory.Texture == TGSI_TEXTURE_BUFFER ||
                                    (!ctx->cfg->use_gles && inst->Memory.Texture == TGSI_TEXTURE_1D);

         emit_buff(&ctx->glsl_strbufs, "%s = %s(imageSize(%s)%s%s);\n",
                   dst, get_string(INT_BITS_TO_FLOAT), srcs[0],
                   swizzle_mask, skip_emit_writemask ? "" : writemask);
      }
   } else if (src->Register.File == TGSI_FILE_BUFFER) {
      emit_buff(&ctx->glsl_strbufs, "%s = %s(int(%s.length()) << 2);\n",
                dst, get_string(INT_BITS_TO_FLOAT), srcs[0]);
   }
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static void
translate_atomic(struct dump_ctx *ctx,
                 const struct tgsi_full_instruction *inst,
                 struct source_info *sinfo,
                 const char *srcs[4],
                 char *dst)
{
   const struct tgsi_full_src_register *src = &inst->Src[0];
   const char *opname;
   enum vrend_type_qualifier stypeprefix = TYPE_CONVERSION_NONE;
   enum vrend_type_qualifier dtypeprefix = TYPE_CONVERSION_NONE;
   enum vrend_type_qualifier stypecast = TYPE_CONVERSION_NONE;
   bool is_cas;
   char cas_str[128] = "";

   if (src->Register.File == TGSI_FILE_IMAGE) {
     enum tgsi_return_type itype;
     get_internalformat_string(ctx->images[sinfo->sreg_index].decl.Format, &itype);
     switch (itype) {
     default:
     case TGSI_RETURN_TYPE_UINT:
        stypeprefix = FLOAT_BITS_TO_UINT;
        dtypeprefix = UINT_BITS_TO_FLOAT;
        stypecast = UINT;
        break;
     case TGSI_RETURN_TYPE_SINT:
        stypeprefix = FLOAT_BITS_TO_INT;
        dtypeprefix = INT_BITS_TO_FLOAT;
        stypecast = INT;
        break;
     case TGSI_RETURN_TYPE_FLOAT:
        if (ctx->cfg->has_es31_compat)
           ctx->shader_req_bits |= SHADER_REQ_ES31_COMPAT;
        else
           ctx->shader_req_bits |= SHADER_REQ_SHADER_ATOMIC_FLOAT;
        stypecast = FLOAT;
        break;
     }
   } else {
     stypeprefix = FLOAT_BITS_TO_UINT;
     dtypeprefix = UINT_BITS_TO_FLOAT;
     stypecast = UINT;
   }

   opname = get_atomic_opname(inst->Instruction.Opcode, &is_cas);
   if (!opname) {
      set_buf_error(&ctx->glsl_strbufs);
      return;
   }

   if (is_cas)
      snprintf(cas_str, 128, ", %s(%s(%s))", get_string(stypecast), get_string(stypeprefix), srcs[3]);

   if (src->Register.File == TGSI_FILE_IMAGE) {
      bool is_ms = false;
      enum vrend_type_qualifier coord_prefix = get_coord_prefix(ctx->images[sinfo->sreg_index].decl.Resource, &is_ms, ctx->cfg->use_gles);
      const char *conversion = sinfo->override_no_cast[1] ? "" : get_string(FLOAT_BITS_TO_INT);
      char ms_str[32] = "";
      if (is_ms) {
         snprintf(ms_str, 32, ", int(%s.w)", srcs[1]);
      }

      set_image_qualifier(ctx->images, ctx->images_used_mask, inst,
                          inst->Src[0].Register.Index, inst->Src[0].Register.Indirect);

      if (!ctx->cfg->use_gles || !inst->Src[0].Register.Indirect) {
         emit_buff(&ctx->glsl_strbufs, "%s = %s(imageAtomic%s(%s, %s(%s(%s))%s, %s(%s(%s))%s));\n",
                   dst, get_string(dtypeprefix), opname, srcs[0],
                   get_string(coord_prefix), conversion, srcs[1], ms_str,
                   get_string(stypecast), get_string(stypeprefix), srcs[2],
                   cas_str);
      } else {
         char src[32] = "";
         struct vrend_array *image = lookup_image_array_ptr(ctx, inst->Src[0].Register.Index);
         if (image) {
            int basearrayidx = image->first;
            int array_size = image->array_size;
            emit_buff(&ctx->glsl_strbufs, "switch (addr%d + %d) {\n", inst->Src[0].Indirect.Index, inst->Src[0].Register.Index - basearrayidx);
            const char *cname = tgsi_proc_to_prefix(ctx->prog_type);

            for (int i = 0; i < array_size; ++i) {
               snprintf(src, 32, "%simg%d[%d]", cname, basearrayidx, i);
               emit_buff(&ctx->glsl_strbufs, "case %d: %s = %s(imageAtomic%s(%s, %s(%s(%s))%s, %s(%s(%s))%s));\n",
                         i, dst, get_string(dtypeprefix), opname, src,
                         get_string(coord_prefix), conversion, srcs[1],
                         ms_str, get_string(stypecast),
                         get_string(stypeprefix), srcs[2], cas_str);
            }
            emit_buff(&ctx->glsl_strbufs, "}\n");
         }
      }
      ctx->shader_req_bits |= SHADER_REQ_IMAGE_ATOMIC;
   }
   if (src->Register.File == TGSI_FILE_BUFFER || src->Register.File == TGSI_FILE_MEMORY) {
      enum vrend_type_qualifier type;
      if ((is_integer_memory(ctx, src->Register.File, src->Register.Index))) {
         type = INT;
         dtypeprefix = INT_BITS_TO_FLOAT;
         stypeprefix = FLOAT_BITS_TO_INT;
      } else {
         type = UINT;
         dtypeprefix = UINT_BITS_TO_FLOAT;
         stypeprefix = FLOAT_BITS_TO_UINT;
      }

      if (is_cas)
         snprintf(cas_str, sizeof(cas_str), ", %s(%s(%s))", get_string(type), get_string(stypeprefix), srcs[3]);

      emit_buff(&ctx->glsl_strbufs, "%s = %s(atomic%s(%s[int(floatBitsToInt(%s)) >> 2], %s(%s(%s).x)%s));\n",
                dst, get_string(dtypeprefix), opname, srcs[0], srcs[1],
                get_string(type), get_string(stypeprefix), srcs[2], cas_str);
   }
   if(src->Register.File == TGSI_FILE_HW_ATOMIC) {
      if (sinfo->imm_value == -1)
         emit_buff(&ctx->glsl_strbufs, "%s = %s(atomicCounterDecrement(%s) + 1u);\n",
                   dst, get_string(dtypeprefix), srcs[0]);
      else if (sinfo->imm_value == 1)
         emit_buff(&ctx->glsl_strbufs, "%s = %s(atomicCounterIncrement(%s));\n",
                   dst, get_string(dtypeprefix), srcs[0]);
      else
         emit_buff(&ctx->glsl_strbufs, "%s = %s(atomicCounter%sARB(%s, floatBitsToUint(%s).x%s));\n",
                   dst, get_string(dtypeprefix), opname, srcs[0], srcs[2],
                   cas_str);
   }

}

static const char *reswizzle_dest(const struct vrend_shader_io *io, const struct tgsi_full_dst_register *dst_reg,
                                  char *reswizzled, const char *writemask)
{
   if (io->usage_mask != 0xf) {
      if (io->num_components > 1) {
         const int wm = dst_reg->Register.WriteMask;
         int k = 1;
         reswizzled[0] = '.';
         for (int i = 0; i < io->num_components; ++i) {
            if (wm & (1 << i))
               reswizzled[k++] = get_swiz_char(i);
         }
         reswizzled[k] = 0;
      }
      writemask = reswizzled;
   }
   return writemask;
}

static void get_destination_info_generic(const struct dump_ctx *ctx,
                                         const struct tgsi_full_dst_register *dst_reg,
                                         const struct vrend_shader_io *io,
                                         const char *writemask,
                                         struct vrend_strbuf *result)
{
   const char *blkarray = (ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL) ? "[gl_InvocationID]" : "";
   const char *stage_prefix = get_stage_output_name_prefix(ctx->prog_type);
   const char *wm = io->override_no_wm ? "" : writemask;
   char reswizzled[6] = "";
   char outvarname[64];

   wm = reswizzle_dest(io, dst_reg, reswizzled, writemask);

   strbuf_reset(result);

   enum io_decl_type decl_type = decl_plain;
   if (io->first != io->last && prefer_generic_io_block(ctx, io_out)) {
      get_blockvarname(outvarname, stage_prefix,  io, blkarray);
      blkarray = outvarname;
      decl_type = decl_block;
   }
   vrend_shader_write_io_as_dst(result, blkarray, io, dst_reg, decl_type);
   strbuf_appendf(result, "%s", wm);
}

static
int find_io_index(int num_io, struct vrend_shader_io *io, int index)
{
   for (int j = 0; j < num_io; j++) {
      if (io[j].first <= index &&
          io[j].last >= index) {
         return j;
      }
   }
   return -1;
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static bool
get_destination_info(struct dump_ctx *ctx,
                     const struct tgsi_full_instruction *inst,
                     struct dest_info *dinfo,
                     struct vrend_strbuf dst_bufs[TGSI_FULL_MAX_DST_REGISTERS],
                     char fp64_dsts[TGSI_FULL_MAX_DST_REGISTERS][255],
                     char *writemask)
{
   if (inst->Instruction.NumDstRegs > TGSI_FULL_MAX_DST_REGISTERS)
      return false;

   const struct tgsi_full_dst_register *dst_reg;
   enum tgsi_opcode_type dtype = tgsi_opcode_infer_dst_type(inst->Instruction.Opcode);

   if (dtype == TGSI_TYPE_SIGNED || dtype == TGSI_TYPE_UNSIGNED)
      ctx->shader_req_bits |= SHADER_REQ_INTS;

   if (dtype == TGSI_TYPE_DOUBLE) {
      /* we need the uvec2 conversion for doubles */
      ctx->shader_req_bits |= SHADER_REQ_INTS | SHADER_REQ_FP64;
   }

   if (inst->Instruction.Opcode == TGSI_OPCODE_TXQ) {
      dinfo->dtypeprefix = INT_BITS_TO_FLOAT;
   } else {
      switch (dtype) {
      case TGSI_TYPE_UNSIGNED:
         dinfo->dtypeprefix = UINT_BITS_TO_FLOAT;
         break;
      case TGSI_TYPE_SIGNED:
         dinfo->dtypeprefix = INT_BITS_TO_FLOAT;
         break;
      default:
         break;
      }
   }

   for (uint32_t i = 0; i < inst->Instruction.NumDstRegs; i++) {
      char fp64_writemask[6] = "";
      dst_reg = &inst->Dst[i];
      dinfo->dst_override_no_wm[i] = false;
      if (dst_reg->Register.WriteMask != TGSI_WRITEMASK_XYZW) {
         int wm_idx = 0, dbl_wm_idx = 0;
         writemask[wm_idx++] = '.';
         fp64_writemask[dbl_wm_idx++] = '.';

         if (dst_reg->Register.WriteMask & 0x1)
            writemask[wm_idx++] = 'x';
         if (dst_reg->Register.WriteMask & 0x2)
            writemask[wm_idx++] = 'y';
         if (dst_reg->Register.WriteMask & 0x4)
            writemask[wm_idx++] = 'z';
         if (dst_reg->Register.WriteMask & 0x8)
            writemask[wm_idx++] = 'w';

         if (dtype == TGSI_TYPE_DOUBLE) {
           if (dst_reg->Register.WriteMask & 0x3)
             fp64_writemask[dbl_wm_idx++] = 'x';
           if (dst_reg->Register.WriteMask & 0xc)
             fp64_writemask[dbl_wm_idx++] = 'y';
         }

         if (dtype == TGSI_TYPE_DOUBLE) {
            if (dbl_wm_idx == 2)
               dinfo->dstconv = DOUBLE;
            else
               dinfo->dstconv = DVEC2;
         } else {
            dinfo->dstconv = FLOAT + wm_idx - 2;
            dinfo->udstconv = UINT + wm_idx - 2;
            dinfo->idstconv = INT + wm_idx - 2;
         }
      } else {
         if (dtype == TGSI_TYPE_DOUBLE)
            dinfo->dstconv = DVEC2;
         else
            dinfo->dstconv = VEC4;
         dinfo->udstconv = UVEC4;
         dinfo->idstconv = IVEC4;
      }

      switch (dst_reg->Register.File) {
      case TGSI_FILE_OUTPUT: {
         int j = find_io_index(ctx->num_outputs, ctx->outputs,
                               dst_reg->Register.Index);

         if (j < 0)
            return false;

         struct vrend_shader_io *output = &ctx->outputs[j];

         if (inst->Instruction.Precise) {
            if (!output->invariant && output->name != TGSI_SEMANTIC_CLIPVERTEX &&
                ctx->cfg->has_gpu_shader5) {
               output->precise = true;
               ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
            }
         }

         if (ctx->glsl_ver_required >= 140 && output->name == TGSI_SEMANTIC_CLIPVERTEX) {
            if (ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL) {
               strbuf_fmt(&dst_bufs[i], "%s[gl_InvocationID]", output->glsl_name);
            } else {
               strbuf_fmt(&dst_bufs[i], "%s", ctx->is_last_vertex_stage ? "clipv_tmp" : output->glsl_name);
            }
         } else if (output->name == TGSI_SEMANTIC_CLIPDIST) {
            char clip_indirect[32] = "";
            if (output->first != output->last) {
               if (dst_reg->Register.Indirect)
                  snprintf(clip_indirect, sizeof(clip_indirect), "+ addr%d", dst_reg->Indirect.Index);
               else
                  snprintf(clip_indirect, sizeof(clip_indirect), "+ %d", dst_reg->Register.Index - output->first);
            }
            strbuf_fmt(&dst_bufs[i], "clip_dist_temp[%d %s]%s", output->sid, clip_indirect, writemask);
         } else if (output->name == TGSI_SEMANTIC_TESSOUTER ||
                    output->name == TGSI_SEMANTIC_TESSINNER ||
                    output->name == TGSI_SEMANTIC_SAMPLEMASK) {
            int idx;
            switch (dst_reg->Register.WriteMask) {
            case 0x1: idx = 0; break;
            case 0x2: idx = 1; break;
            case 0x4: idx = 2; break;
            case 0x8: idx = 3; break;
            default:
               idx = 0;
               break;
            }
            strbuf_fmt(&dst_bufs[i], "%s[%d]", output->glsl_name, idx);
            if (output->is_int) {
               dinfo->dtypeprefix = FLOAT_BITS_TO_INT;
               dinfo->dstconv = INT;
            }
         } else {
            if (output->glsl_gl_block) {
               strbuf_fmt(&dst_bufs[i], "gl_out[%s].%s%s",
                          ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL ? "gl_InvocationID" : "0",
                          output->glsl_name,
                          output->override_no_wm ? "" : writemask);
            } else if (output->name == TGSI_SEMANTIC_GENERIC) {
               struct vrend_shader_io *io = ctx->generic_ios.output_range.used ? &ctx->generic_ios.output_range.io : output;
               get_destination_info_generic(ctx, dst_reg, io, writemask, &dst_bufs[i]);
               dinfo->dst_override_no_wm[i] = output->override_no_wm;
            } else if (output->name == TGSI_SEMANTIC_TEXCOORD) {
               get_destination_info_generic(ctx, dst_reg, output, writemask, &dst_bufs[i]);
               dinfo->dst_override_no_wm[i] = output->override_no_wm;
            } else if (output->name == TGSI_SEMANTIC_PATCH) {
               struct vrend_shader_io *io = ctx->patch_ios.output_range.used ? &ctx->patch_ios.output_range.io : output;
               char reswizzled[6] = "";
               const char *wm = reswizzle_dest(io, dst_reg, reswizzled, writemask);
               strbuf_reset(&dst_bufs[i]);
               vrend_shader_write_io_as_dst(&dst_bufs[i], "", io, dst_reg, decl_plain);
               if (!output->override_no_wm)
                  strbuf_appendf(&dst_bufs[i], "%s", wm);
               dinfo->dst_override_no_wm[i] = output->override_no_wm;
            } else {
               if (ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL) {
                  strbuf_fmt(&dst_bufs[i], "%s[gl_InvocationID]%s", output->glsl_name, output->override_no_wm ? "" : writemask);
               } else {
                  strbuf_fmt(&dst_bufs[i], "%s%s", output->glsl_name, output->override_no_wm ? "" : writemask);
               }
               dinfo->dst_override_no_wm[i] = output->override_no_wm;
            }
            if (output->is_int) {
               if (dinfo->dtypeprefix == TYPE_CONVERSION_NONE)
                  dinfo->dtypeprefix = FLOAT_BITS_TO_INT;
               dinfo->dstconv = INT;
            }
            else if (output->type == VEC_UINT) {
               if (dinfo->dtypeprefix == TYPE_CONVERSION_NONE)
                  dinfo->dtypeprefix = FLOAT_BITS_TO_UINT;
               dinfo->dstconv = dinfo->udstconv;
            }
            else if (output->type == VEC_INT) {
               if (dinfo->dtypeprefix == TYPE_CONVERSION_NONE)
                  dinfo->dtypeprefix = FLOAT_BITS_TO_INT;
               dinfo->dstconv = dinfo->idstconv;
            }
            if (output->name == TGSI_SEMANTIC_PSIZE) {
               dinfo->dstconv = FLOAT;
               break;
            }
         }
         break;
      }
      case TGSI_FILE_TEMPORARY: {
         char temp_buf[64];
         get_temp(ctx, dst_reg->Register.Indirect, 0, dst_reg->Register.Index,
                  temp_buf, &ctx->require_dummy_value);
         strbuf_fmt(&dst_bufs[i], "%s%s", temp_buf, writemask);
         if (inst->Instruction.Precise) {
            struct vrend_temp_range *range = find_temp_range(ctx, dst_reg->Register.Index);
            if (range && ctx->cfg->has_gpu_shader5) {
               range->precise_result = true;
               ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
            }
         }
         break;
      }
      case TGSI_FILE_IMAGE: {
         const char *cname = tgsi_proc_to_prefix(ctx->prog_type);
         if (ctx->info.indirect_files & (1 << TGSI_FILE_IMAGE)) {
            int basearrayidx = lookup_image_array(ctx, dst_reg->Register.Index);
            if (dst_reg->Register.Indirect) {
               if (dst_reg->Indirect.File != TGSI_FILE_ADDRESS)
                  return false;
               strbuf_fmt(&dst_bufs[i], "%simg%d[addr%d + %d]", cname, basearrayidx, dst_reg->Indirect.Index, dst_reg->Register.Index - basearrayidx);
            } else
               strbuf_fmt(&dst_bufs[i], "%simg%d[%d]", cname, basearrayidx, dst_reg->Register.Index - basearrayidx);
         } else
            strbuf_fmt(&dst_bufs[i], "%simg%d", cname, dst_reg->Register.Index);
         dinfo->dest_index = dst_reg->Register.Index;
         break;
      }
      case TGSI_FILE_BUFFER: {
         char dst[128];
         make_ssbo_varstring(ctx, dst, dst_reg->Register.Index, dst_reg->Register.Indirect, dst_reg->Indirect.Index);
         strbuf_fmt(&dst_bufs[i], "%s", dst);
         dinfo->dest_index = dst_reg->Register.Index;
         break;
      }
      case TGSI_FILE_MEMORY:
         strbuf_fmt(&dst_bufs[i], "values");
         break;
      case TGSI_FILE_ADDRESS:
         strbuf_fmt(&dst_bufs[i], "addr%d", dst_reg->Register.Index);
         break;
      default:
         /* illegal destination type */
         return false;
      }

      if (dtype == TGSI_TYPE_DOUBLE) {
         snprintf(fp64_dsts[i], sizeof(fp64_dsts[i]), "%s", dst_bufs[i].buf);
         strbuf_fmt(&dst_bufs[i], "fp64_dst[%d]%s", i, fp64_writemask);
         writemask[0] = 0;
      }

   }

   return true;
}

static const char *shift_swizzles(const struct vrend_shader_io *io, const struct tgsi_full_src_register *src,
                                  int swz_offset, char *swizzle_shifted, const char *swizzle)
{
   if (io->usage_mask != 0xf && swizzle[0]) {
      if (io->num_components > 1) {
         swizzle_shifted[swz_offset++] = '.';
         for (int i = 0; i < 4; ++i) {
            switch (i) {
            case 0: swizzle_shifted[swz_offset++] = get_swiz_char(src->Register.SwizzleX);
               break;
            case 1: swizzle_shifted[swz_offset++] = get_swiz_char(src->Register.SwizzleY);
               break;
            case 2: swizzle_shifted[swz_offset++] = src->Register.SwizzleZ < io->num_components ?
                                                       get_swiz_char(src->Register.SwizzleZ) : 'x';
               break;
            case 3: swizzle_shifted[swz_offset++] = src->Register.SwizzleW < io->num_components ?
                                                       get_swiz_char(src->Register.SwizzleW) : 'x';
            }
         }
         swizzle_shifted[swz_offset] = 0;
      }
      swizzle = swizzle_shifted;
   }
   return swizzle;
}

static void get_source_info_generic(const struct dump_ctx *ctx,
                                    enum io_type iot,
                                    enum vrend_type_qualifier srcstypeprefix,
                                    const char *prefix,
                                    const struct tgsi_full_src_register *src,
                                    const struct vrend_shader_io *io,
                                    const  char *arrayname,
                                    const char *swizzle,
                                    struct vrend_strbuf *result)
{
   int swz_offset = 0;
   char swizzle_shifted[6] = "";
   char outvarname[64];

   if (swizzle[0] == ')') {
      swizzle_shifted[swz_offset++] = ')';
      swizzle_shifted[swz_offset] = 0;
   }

   /* This IO element is not using all vector elements, so we have to shift the swizzle names */
   swizzle = shift_swizzles(io, src, swz_offset, swizzle_shifted, swizzle);

   strbuf_fmt(result, "%s(%s", get_string(srcstypeprefix), prefix);

   enum io_decl_type decl_type = decl_plain;

   if ((io->first != io->last || io->overlapping_array) &&
       prefer_generic_io_block(ctx, iot)) {

      const struct vrend_shader_io *array = io->overlapping_array ?
            io->overlapping_array : io;

      const char *stage_prefix = iot == io_in ?
                                    get_stage_input_name_prefix(ctx, ctx->prog_type) :
                                    get_stage_output_name_prefix(ctx->prog_type);
      get_blockvarname(outvarname, stage_prefix, array, arrayname);
      arrayname = outvarname;
      decl_type = decl_block;
   }

   vrend_shader_write_io_as_src(result, arrayname, io, src, decl_type);
   strbuf_appendf(result, "%s)", io->is_int ? "" : swizzle);
}

static void get_source_info_patch(enum vrend_type_qualifier srcstypeprefix,
                                  const char *prefix,
                                  const struct tgsi_full_src_register *src,
                                  const struct vrend_shader_io *io,
                                  const  char *arrayname,
                                  const char *swizzle,
                                  struct vrend_strbuf *result)
{
   int swz_offset = 0;
   char swizzle_shifted[7] = "";
   if (swizzle[0] == ')') {
      swizzle_shifted[swz_offset++] = ')';
      swizzle_shifted[swz_offset] = 0;
   }

   swizzle = shift_swizzles(io, src, swz_offset, swizzle_shifted, swizzle);

   strbuf_fmt(result, "%s(%s", get_string(srcstypeprefix), prefix);
   vrend_shader_write_io_as_src(result, io->last == io->first ? arrayname : "", io, src, decl_plain);
   strbuf_appendf(result, "%s)", io->is_int ? "" : swizzle);
}

static void get_tesslevel_as_source(struct vrend_strbuf *src_buf, const char *prefix,
                                    const char *name, const struct tgsi_src_register *reg)
{
   strbuf_fmt(src_buf, "%s(vec4(%s[%d], %s[%d], %s[%d], %s[%d]))",
              prefix,
              name, reg->SwizzleX,
              name, reg->SwizzleY,
              name, reg->SwizzleZ,
              name, reg->SwizzleW);
}

static void get_source_swizzle(const struct tgsi_full_src_register *src, char swizzle[8])
{
   if (src->Register.SwizzleX != TGSI_SWIZZLE_X ||
       src->Register.SwizzleY != TGSI_SWIZZLE_Y ||
       src->Register.SwizzleZ != TGSI_SWIZZLE_Z ||
       src->Register.SwizzleW != TGSI_SWIZZLE_W) {
      *swizzle++ = '.';
      *swizzle++ = get_swiz_char(src->Register.SwizzleX);
      *swizzle++ = get_swiz_char(src->Register.SwizzleY);
      *swizzle++ = get_swiz_char(src->Register.SwizzleZ);
      *swizzle++ = get_swiz_char(src->Register.SwizzleW);
   }

   *swizzle++ = 0;
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static bool
get_source_info(struct dump_ctx *ctx,
                const struct tgsi_full_instruction *inst,
                struct source_info *sinfo,
                struct vrend_strbuf srcs[TGSI_FULL_MAX_SRC_REGISTERS], char src_swizzle0[16])
{
   if (inst->Instruction.NumSrcRegs > TGSI_FULL_MAX_SRC_REGISTERS)
      return false;

   bool stprefix = false;

   enum vrend_type_qualifier stypeprefix = TYPE_CONVERSION_NONE;
   enum tgsi_opcode_type stype = tgsi_opcode_infer_src_type(inst->Instruction.Opcode);

   if (stype == TGSI_TYPE_SIGNED || stype == TGSI_TYPE_UNSIGNED)
      ctx->shader_req_bits |= SHADER_REQ_INTS;
   if (stype == TGSI_TYPE_DOUBLE)
      ctx->shader_req_bits |= SHADER_REQ_INTS | SHADER_REQ_FP64;

   switch (stype) {
   case TGSI_TYPE_DOUBLE:
      stypeprefix = FLOAT_BITS_TO_UINT;
      sinfo->svec4 = DVEC2;
      stprefix = true;
      break;
   case TGSI_TYPE_UNSIGNED:
      stypeprefix = FLOAT_BITS_TO_UINT;
      sinfo->svec4 = UVEC4;
      stprefix = true;
      break;
   case TGSI_TYPE_SIGNED:
      stypeprefix = FLOAT_BITS_TO_INT;
      sinfo->svec4 = IVEC4;
      stprefix = true;
      break;
   default:
      break;
   }

   for (uint32_t i = 0; i < inst->Instruction.NumSrcRegs; i++) {
      const struct tgsi_full_src_register *src = &inst->Src[i];
      struct vrend_strbuf *src_buf = &srcs[i];
      char swizzle[16] = "";
      char *swizzle_writer = swizzle;
      char prefix[6] = "";
      char arrayname[16] = "";
      char fp64_src[255];
      int swz_idx = 0, pre_idx = 0;
      bool isfloatabsolute = src->Register.Absolute && stype != TGSI_TYPE_DOUBLE;

      sinfo->override_no_wm[i] = false;
      sinfo->override_no_cast[i] = false;

      if (src->Register.Negate)
         prefix[pre_idx++] = '-';
      if (isfloatabsolute)
         strcpy(&prefix[pre_idx++], "abs(");

      if (src->Register.Dimension) {
         if (src->Dimension.Indirect) {
            if (src->DimIndirect.File != TGSI_FILE_ADDRESS)
               return false;
            sprintf(arrayname, "[addr%d]", src->DimIndirect.Index);
         } else
            sprintf(arrayname, "[%d]", src->Dimension.Index);
      }

      /* These instructions don't support swizzles in the first parameter
       * pass the swizzle to the caller instead */
      if ((inst->Instruction.Opcode == TGSI_OPCODE_INTERP_SAMPLE ||
           inst->Instruction.Opcode == TGSI_OPCODE_INTERP_OFFSET ||
           inst->Instruction.Opcode == TGSI_OPCODE_INTERP_CENTROID) &&
          i == 0) {
         swizzle_writer = src_swizzle0;
      }

      if (isfloatabsolute)
         swizzle_writer[swz_idx++] = ')';

      get_source_swizzle(src, swizzle_writer + swz_idx);

      switch (src->Register.File) {
      case TGSI_FILE_INPUT: {
         int j = find_io_index(ctx->num_inputs, ctx->inputs, src->Register.Index);
         if (j < 0)
            return false;

         struct vrend_shader_io *input = &ctx->inputs[j];

         if (ctx->prog_type == TGSI_PROCESSOR_VERTEX) {
            if (ctx->key->vs.attrib_zyxw_bitmask & (1 << input->first)) {
               swizzle_writer[swz_idx++] = '.';
               swizzle_writer[swz_idx++] = 'z';
               swizzle_writer[swz_idx++] = 'y';
               swizzle_writer[swz_idx++] = 'x';
               swizzle_writer[swz_idx++] = 'w';
            }
            get_source_swizzle(src, swizzle_writer + swz_idx);
         }

         if (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT &&
             ctx->key->color_two_side && input->name == TGSI_SEMANTIC_COLOR)
            strbuf_fmt(src_buf, "%s(%s%s%d%s%s)", get_string(stypeprefix), prefix, "realcolor", input->sid, arrayname, swizzle);
         else if (input->glsl_gl_block) {
            /* GS input clipdist requires a conversion */
            if (input->name == TGSI_SEMANTIC_CLIPDIST) {
               create_swizzled_clipdist(ctx, src_buf, src, j, true, get_string(stypeprefix), prefix, arrayname, input->first);
            } else {
               strbuf_fmt(src_buf, "%s(vec4(%sgl_in%s.%s)%s)", get_string(stypeprefix), prefix, arrayname, input->glsl_name, swizzle);
            }
         }
         else if (input->name == TGSI_SEMANTIC_PRIMID)
            strbuf_fmt(src_buf, "%s(vec4(intBitsToFloat(%s)))", get_string(stypeprefix), input->glsl_name);
         else if (input->name == TGSI_SEMANTIC_FACE)
            strbuf_fmt(src_buf, "%s(%s ? 1.0 : -1.0)", get_string(stypeprefix), input->glsl_name);
         else if (input->name == TGSI_SEMANTIC_CLIPDIST) {
            if (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT)
               load_clipdist_fs(ctx, src_buf, src, j, get_string(stypeprefix), input->first);
            else
               create_swizzled_clipdist(ctx, src_buf, src, j, false, get_string(stypeprefix), prefix, arrayname, input->first);
         }  else if (input->name == TGSI_SEMANTIC_TESSOUTER ||
                     input->name == TGSI_SEMANTIC_TESSINNER) {
            get_tesslevel_as_source(src_buf, prefix, input->glsl_name, &src->Register);
         } else {
            enum vrend_type_qualifier srcstypeprefix = stypeprefix;
            if (input->type != VEC_FLOAT) {
               if (stype == TGSI_TYPE_UNSIGNED)
                  srcstypeprefix = UVEC4;
               else if (stype == TGSI_TYPE_SIGNED)
                  srcstypeprefix = IVEC4;
               else if (input->type == VEC_INT)
                  srcstypeprefix = INT_BITS_TO_FLOAT;
               else // input->type == VEC_UINT
                  srcstypeprefix = UINT_BITS_TO_FLOAT;
            }

            if (inst->Instruction.Opcode == TGSI_OPCODE_INTERP_SAMPLE && i == 1) {
               strbuf_fmt(src_buf, "floatBitsToInt(%s%s%s%s)", prefix, input->glsl_name, arrayname, swizzle);
            } else if (input->name == TGSI_SEMANTIC_GENERIC) {
               get_source_info_generic(ctx, io_in, srcstypeprefix, prefix, src,
                                       &ctx->inputs[j], arrayname, swizzle, src_buf);
            } else if (input->name == TGSI_SEMANTIC_TEXCOORD) {
               get_source_info_generic(ctx, io_in, srcstypeprefix, prefix, src,
                                       &ctx->inputs[j], arrayname, swizzle, src_buf);
            } else if (input->name == TGSI_SEMANTIC_PATCH) {
               get_source_info_patch(srcstypeprefix, prefix, src,
                                     &ctx->inputs[j], arrayname, swizzle, src_buf);
            } else if (input->name == TGSI_SEMANTIC_POSITION && ctx->prog_type == TGSI_PROCESSOR_VERTEX &&
                       input->first != input->last) {
               if (src->Register.Indirect)
                  strbuf_fmt(src_buf, "%s(%s%s%s[addr%d + %d]%s)", get_string(srcstypeprefix), prefix, input->glsl_name, arrayname,
                             src->Indirect.Index, src->Register.Index, input->is_int ? "" : swizzle);
               else
                  strbuf_fmt(src_buf, "%s(%s%s%s[%d]%s)", get_string(srcstypeprefix), prefix, input->glsl_name, arrayname,
                             src->Register.Index, input->is_int ? "" : swizzle);
            } else
               strbuf_fmt(src_buf, "%s(%s%s%s%s)", get_string(srcstypeprefix), prefix, input->glsl_name, arrayname, input->is_int ? "" : swizzle);
         }
         sinfo->override_no_wm[i] = input->override_no_wm;
         break;
      }
      case TGSI_FILE_OUTPUT: {
         int j = find_io_index(ctx->num_outputs, ctx->outputs, src->Register.Index);
         if (j < 0)
            return false;

         struct vrend_shader_io *output = &ctx->outputs[j];

         if (inst->Instruction.Opcode == TGSI_OPCODE_FBFETCH) {
            output->fbfetch_used = true;
            ctx->shader_req_bits |= SHADER_REQ_FBFETCH;
         }

         enum vrend_type_qualifier srcstypeprefix = stypeprefix;
         if (stype == TGSI_TYPE_UNSIGNED && output->is_int)
            srcstypeprefix = TYPE_CONVERSION_NONE;
         if (output->glsl_gl_block) {
            if (output->name == TGSI_SEMANTIC_CLIPDIST) {
               char clip_indirect[32] = "";
               if (output->first != output->last) {
                  if (src->Register.Indirect)
                     snprintf(clip_indirect, sizeof(clip_indirect), "+ addr%d", src->Indirect.Index);
                  else
                     snprintf(clip_indirect, sizeof(clip_indirect), "+ %d", src->Register.Index - output->first);
               }
               strbuf_fmt(src_buf, "clip_dist_temp[%d%s]", output->sid, clip_indirect);
            }
         } else if (output->name == TGSI_SEMANTIC_GENERIC) {
            struct vrend_shader_io *io = ctx->generic_ios.output_range.used ? &ctx->generic_ios.output_range.io : &ctx->outputs[j];
            get_source_info_generic(ctx, io_out, srcstypeprefix, prefix, src, io, arrayname, swizzle, src_buf);
         } else if (output->name == TGSI_SEMANTIC_PATCH) {
            struct vrend_shader_io *io = ctx->patch_ios.output_range.used ? &ctx->patch_ios.output_range.io : &ctx->outputs[j];
            get_source_info_patch(srcstypeprefix, prefix, src, io, arrayname, swizzle, src_buf);
         } else if (output->name == TGSI_SEMANTIC_TESSOUTER ||
                    output->name == TGSI_SEMANTIC_TESSINNER) {
            get_tesslevel_as_source(src_buf, prefix, output->glsl_name, &src->Register);
         } else {
            strbuf_fmt(src_buf, "%s(%s%s%s%s)", get_string(srcstypeprefix), prefix, output->glsl_name, arrayname, output->is_int ? "" : swizzle);
         }
         sinfo->override_no_wm[i] = output->override_no_wm;
         break;
         }
      case TGSI_FILE_TEMPORARY: {
         struct vrend_temp_range *range = find_temp_range(ctx, src->Register.Index);
         if (!range)
            return false;
         if (inst->Instruction.Opcode == TGSI_OPCODE_INTERP_SAMPLE && i == 1) {
            stprefix = true;
            stypeprefix = FLOAT_BITS_TO_INT;
         }
         char temp_buf[64];
         get_temp(ctx, src->Register.Indirect, src->Indirect.Index, src->Register.Index,
                  temp_buf, &ctx->require_dummy_value);
         strbuf_fmt(src_buf, "%s%cvec4(%s%s)%s%c", get_string(stypeprefix), stprefix ? '(' : ' ', prefix, temp_buf, swizzle, stprefix ? ')' : ' ');
         break;
      }
      case TGSI_FILE_CONSTANT: {
         const char *cname = tgsi_proc_to_prefix(ctx->prog_type);
         int dim = 0;
         if (src->Register.Dimension && src->Dimension.Index != 0) {
            dim = src->Dimension.Index;
            if (src->Dimension.Indirect) {
               if (src->DimIndirect.File != TGSI_FILE_ADDRESS)
                  return false;
               ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
               if (src->Register.Indirect) {
                  if (src->Indirect.File != TGSI_FILE_ADDRESS)
                     return false;
                  strbuf_fmt(src_buf, "%s(%s%suboarr[addr%d].ubocontents[addr%d + %d]%s)", get_string(stypeprefix), prefix, cname, src->DimIndirect.Index, src->Indirect.Index, src->Register.Index, swizzle);
               } else
                  strbuf_fmt(src_buf, "%s(%s%suboarr[addr%d].ubocontents[%d]%s)", get_string(stypeprefix), prefix, cname, src->DimIndirect.Index, src->Register.Index, swizzle);
            } else {
               if (ctx->info.dimension_indirect_files & (1 << TGSI_FILE_CONSTANT)) {
                  if (src->Register.Indirect) {
                     strbuf_fmt(src_buf, "%s(%s%suboarr[%d].ubocontents[addr%d + %d]%s)", get_string(stypeprefix), prefix, cname, dim - ctx->ubo_base, src->Indirect.Index, src->Register.Index, swizzle);
                  } else
                     strbuf_fmt(src_buf, "%s(%s%suboarr[%d].ubocontents[%d]%s)", get_string(stypeprefix), prefix, cname, dim - ctx->ubo_base, src->Register.Index, swizzle);
               } else {
                  if (src->Register.Indirect) {
                     if (src->Indirect.File != TGSI_FILE_ADDRESS)
                        return false;
                     strbuf_fmt(src_buf, "%s(%s%subo%dcontents[addr%d + %d]%s)",
                                get_string(stypeprefix), prefix, cname, dim,
                                src->Indirect.Index, src->Register.Index,
                                swizzle);
                  } else
                     strbuf_fmt(src_buf, "%s(%s%subo%dcontents[%d]%s)", get_string(stypeprefix), prefix, cname, dim, src->Register.Index, swizzle);
               }
            }
         } else {
            enum vrend_type_qualifier csp = TYPE_CONVERSION_NONE;
            ctx->shader_req_bits |= SHADER_REQ_INTS;
            if (inst->Instruction.Opcode == TGSI_OPCODE_INTERP_SAMPLE && i == 1)
               csp = IVEC4;
            else if (stype == TGSI_TYPE_FLOAT || stype == TGSI_TYPE_UNTYPED)
               csp = UINT_BITS_TO_FLOAT;
            else if (stype == TGSI_TYPE_SIGNED)
               csp = IVEC4;

            if (src->Register.Indirect) {
               if (src->Indirect.File != TGSI_FILE_ADDRESS)
                  return false;
               strbuf_fmt(src_buf, "%s%s(%sconst%d[addr%d + %d]%s)", prefix,
                          get_string(csp), cname, dim, src->Indirect.Index,
                          src->Register.Index, swizzle);
            } else
               strbuf_fmt(src_buf, "%s%s(%sconst%d[%d]%s)", prefix, get_string(csp), cname, dim, src->Register.Index, swizzle);
         }
         break;
      }
      case TGSI_FILE_SAMPLER: {
         const char *cname = tgsi_proc_to_prefix(ctx->prog_type);
         if (ctx->info.indirect_files & (1 << TGSI_FILE_SAMPLER)) {
            int basearrayidx = lookup_sampler_array(ctx, src->Register.Index);
            if (src->Register.Indirect) {
               strbuf_fmt(src_buf, "%ssamp%d[addr%d+%d]%s", cname, basearrayidx, src->Indirect.Index, src->Register.Index - basearrayidx, swizzle);
            } else {
               strbuf_fmt(src_buf, "%ssamp%d[%d]%s", cname, basearrayidx, src->Register.Index - basearrayidx, swizzle);
            }
         } else {
            strbuf_fmt(src_buf, "%ssamp%d%s", cname, src->Register.Index, swizzle);
         }
         sinfo->sreg_index = src->Register.Index;
      } break;
      case TGSI_FILE_IMAGE: {
         const char *cname = tgsi_proc_to_prefix(ctx->prog_type);
         if (ctx->info.indirect_files & (1 << TGSI_FILE_IMAGE)) {
            int basearrayidx = lookup_image_array(ctx, src->Register.Index);
            if (src->Register.Indirect) {
               if (src->Indirect.File != TGSI_FILE_ADDRESS)
                  return false;
               strbuf_fmt(src_buf, "%simg%d[addr%d + %d]", cname, basearrayidx, src->Indirect.Index, src->Register.Index - basearrayidx);
            } else
               strbuf_fmt(src_buf, "%simg%d[%d]", cname, basearrayidx, src->Register.Index - basearrayidx);
         } else
            strbuf_fmt(src_buf, "%simg%d%s", cname, src->Register.Index, swizzle);
         sinfo->sreg_index = src->Register.Index;
      } break;
      case  TGSI_FILE_BUFFER: {
         char src_str[128];
         make_ssbo_varstring(ctx, src_str, src->Register.Index, src->Register.Indirect, src->Indirect.Index);
         strbuf_fmt(src_buf, "%s", src_str);
         sinfo->sreg_index = src->Register.Index;
      } break;
      case  TGSI_FILE_MEMORY:
         strbuf_fmt(src_buf, "values");
         sinfo->sreg_index = src->Register.Index;
         break;
      case TGSI_FILE_IMMEDIATE: {
            if (unlikely((unsigned) src->Register.Index >= MAX_IMMEDIATE)) {
               virgl_error("Immediate exceeded, max is %u\n", MAX_IMMEDIATE);
               return false;
            }
            struct immed *imd = &ctx->imm[src->Register.Index];
            int idx = src->Register.SwizzleX;
            char temp[48];
            enum vrend_type_qualifier vtype = VEC4;
            enum vrend_type_qualifier imm_stypeprefix = stypeprefix;

            if ((inst->Instruction.Opcode == TGSI_OPCODE_TG4 && i == 1) ||
                (inst->Instruction.Opcode == TGSI_OPCODE_INTERP_SAMPLE && i == 1))
               stype = TGSI_TYPE_SIGNED;

            switch (imd->type) {
            case TGSI_IMM_INT32:
               vtype = IVEC4;
               if (stype == TGSI_TYPE_SIGNED)
                  imm_stypeprefix = TYPE_CONVERSION_NONE;
               else if (stype == TGSI_TYPE_UNSIGNED)
                  imm_stypeprefix = UVEC4;
               else if (stype == TGSI_TYPE_FLOAT || stype == TGSI_TYPE_UNTYPED)
                  imm_stypeprefix = INT_BITS_TO_FLOAT;
               break;
            case TGSI_IMM_UINT32:
               vtype = UVEC4;
               if (stype == TGSI_TYPE_UNSIGNED)
                  imm_stypeprefix = TYPE_CONVERSION_NONE;
               else if (stype == TGSI_TYPE_SIGNED)
                  imm_stypeprefix = IVEC4;
               else if (stype == TGSI_TYPE_FLOAT || stype == TGSI_TYPE_UNTYPED)
                  imm_stypeprefix = UINT_BITS_TO_FLOAT;
               break;
            case TGSI_IMM_FLOAT64:
               vtype = UVEC4;
               if (stype == TGSI_TYPE_DOUBLE)
                  imm_stypeprefix = TYPE_CONVERSION_NONE;
               else
                  imm_stypeprefix = UINT_BITS_TO_FLOAT;
               break;
            case TGSI_IMM_INT64:
            case TGSI_IMM_UINT64:
            case TGSI_IMM_FLOAT32:
               break;
            }

            /* build up a vec4 of immediates */
            strbuf_fmt(src_buf, "%s%s(%s(", prefix,
                       get_string(imm_stypeprefix), get_string(vtype));

            for (uint32_t j = 0; j < 4; j++) {
               if (j == 0)
                  idx = src->Register.SwizzleX;
               else if (j == 1)
                  idx = src->Register.SwizzleY;
               else if (j == 2)
                  idx = src->Register.SwizzleZ;
               else if (j == 3)
                  idx = src->Register.SwizzleW;

               if (inst->Instruction.Opcode == TGSI_OPCODE_TG4 && i == 1 && j == 0) {
                  if (imd->val[idx].ui > 0) {
                     sinfo->tg4_has_component = true;
                     if (!ctx->cfg->use_gles)
                        ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
                  }
               }

               switch (imd->type) {
               case TGSI_IMM_FLOAT32:
                  if (isinf(imd->val[idx].f) || isnan(imd->val[idx].f)) {
                     ctx->shader_req_bits |= SHADER_REQ_INTS;
                     snprintf(temp, 48, "uintBitsToFloat(%uU)", imd->val[idx].ui);
                  } else
                     snprintf(temp, 25, "%.8g", imd->val[idx].f);
                  break;
               case TGSI_IMM_UINT32:
                  snprintf(temp, 25, "%uU", imd->val[idx].ui);
                  break;
               case TGSI_IMM_INT32:
                  snprintf(temp, 25, "%d", imd->val[idx].i);
                  sinfo->imm_value = imd->val[idx].i;
                  break;
               case TGSI_IMM_FLOAT64:
                  snprintf(temp, 48, "%uU", imd->val[idx].ui);
                  break;
               default:
                  virgl_error("Unhandled imm type: %x\n", imd->type);
                  return false;
               }
               strbuf_append(src_buf, temp);
               if (j < 3)
                  strbuf_append(src_buf, ",");
               else {
                  snprintf(temp, 4, "))%c", isfloatabsolute ? ')' : 0);
                  strbuf_append(src_buf, temp);
               }
            }
      }  break;
      case  TGSI_FILE_SYSTEM_VALUE: {
         bool sysvalue_found = false;
         for (uint32_t j = 0; j < ctx->num_system_values; j++) {
            if (ctx->system_values[j].first == src->Register.Index) {
               switch (ctx->system_values[j].name) {
               case TGSI_SEMANTIC_VERTEXID:
               case TGSI_SEMANTIC_VERTEXID_NOBASE:
               case TGSI_SEMANTIC_INSTANCEID:
               case TGSI_SEMANTIC_PRIMID:
               case TGSI_SEMANTIC_VERTICESIN:
               case TGSI_SEMANTIC_INVOCATIONID:
               case TGSI_SEMANTIC_SAMPLEID:
                  if (inst->Instruction.Opcode == TGSI_OPCODE_INTERP_SAMPLE && i == 1)
                     strbuf_fmt(src_buf, "ivec4(%s)", ctx->system_values[j].glsl_name);
                  else
                     strbuf_fmt(src_buf, "%s(vec4(intBitsToFloat(%s)))", get_string(stypeprefix), ctx->system_values[j].glsl_name);
                  break;
               case TGSI_SEMANTIC_HELPER_INVOCATION:
                  strbuf_fmt(src_buf, "uvec4(%s)", ctx->system_values[j].glsl_name);
                  break;
               case TGSI_SEMANTIC_TESSINNER:
               case TGSI_SEMANTIC_TESSOUTER:
                  strbuf_fmt(src_buf, "%s(vec4(%s[%d], %s[%d], %s[%d], %s[%d]))",
                             prefix,
                             ctx->system_values[j].glsl_name, src->Register.SwizzleX,
                             ctx->system_values[j].glsl_name, src->Register.SwizzleY,
                             ctx->system_values[j].glsl_name, src->Register.SwizzleZ,
                             ctx->system_values[j].glsl_name, src->Register.SwizzleW);
                  break;
               case TGSI_SEMANTIC_SAMPLEPOS: {
                  /* gl_SamplePosition is a vec2, but TGSI_SEMANTIC_SAMPLEPOS
                   * is a vec4 with z = w = 0
                   */
                  const char *components[4] = {
                     "gl_SamplePosition.x", "gl_SamplePosition.y", "0.0", "0.0"
                  };
                  strbuf_fmt(src_buf, "%s(vec4(%s, %s, %s, %s))",
                             prefix,
                             components[src->Register.SwizzleX],
                             components[src->Register.SwizzleY],
                             components[src->Register.SwizzleZ],
                             components[src->Register.SwizzleW]);
                  break;
               }
               case TGSI_SEMANTIC_TESSCOORD:
                  strbuf_fmt(src_buf, "%s(vec4(%s.%c, %s.%c, %s.%c, %s.%c))",
                             prefix,
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleX),
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleY),
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleZ),
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleW));
                  break;
               case TGSI_SEMANTIC_GRID_SIZE:
               case TGSI_SEMANTIC_THREAD_ID:
               case TGSI_SEMANTIC_BLOCK_ID: {
                  enum vrend_type_qualifier mov_conv = TYPE_CONVERSION_NONE;
                  if (inst->Instruction.Opcode == TGSI_OPCODE_MOV &&
                      inst->Dst[0].Register.File == TGSI_FILE_TEMPORARY)
                     mov_conv = UINT_BITS_TO_FLOAT;
                  strbuf_fmt(src_buf, "%s(uvec4(%s.%c, %s.%c, %s.%c, %s.%c))", get_string(mov_conv),
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleX),
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleY),
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleZ),
                             ctx->system_values[j].glsl_name, get_swiz_char(src->Register.SwizzleW));
                  sinfo->override_no_cast[i] = true;
                  break;
               }
               case TGSI_SEMANTIC_SAMPLEMASK: {
                  const char *vec_type = "ivec4";
                  enum vrend_type_qualifier srcstypeprefix = TYPE_CONVERSION_NONE;
                  if (stypeprefix == TYPE_CONVERSION_NONE)
                     srcstypeprefix = INT_BITS_TO_FLOAT;
                  else if (stype == TGSI_TYPE_UNSIGNED)
                     vec_type = "uvec4";

                  ctx->shader_req_bits |= SHADER_REQ_SAMPLE_SHADING | SHADER_REQ_INTS;
                  strbuf_fmt(src_buf, "%s(%s(%s, %s, %s, %s))",
                     get_string(srcstypeprefix),
                     vec_type,
                     src->Register.SwizzleX == TGSI_SWIZZLE_X ? ctx->system_values[j].glsl_name : "0",
                     src->Register.SwizzleY == TGSI_SWIZZLE_X ? ctx->system_values[j].glsl_name : "0",
                     src->Register.SwizzleZ == TGSI_SWIZZLE_X ? ctx->system_values[j].glsl_name : "0",
                     src->Register.SwizzleW == TGSI_SWIZZLE_X ? ctx->system_values[j].glsl_name : "0");
                  break;
               }
               default:
                  strbuf_fmt(src_buf, "%s%s", prefix, ctx->system_values[j].glsl_name);
                  sinfo->override_no_wm[i] = ctx->system_values[j].override_no_wm;
               }
               sysvalue_found = true;
               break;
            }
         }
         if (!sysvalue_found)
            return false;
      } break;
      case TGSI_FILE_HW_ATOMIC: {
         for (uint32_t j = 0; j < ctx->num_abo; j++) {
            if (src->Dimension.Index == ctx->abo_idx[j] &&
                src->Register.Index >= ctx->abo_offsets[j] &&
                src->Register.Index < ctx->abo_offsets[j] + ctx->abo_sizes[j]) {
               int abo_idx = ctx->abo_idx[j];
               int abo_offset = ctx->abo_offsets[j] * 4;
               if (ctx->abo_sizes[j] > 1) {
                  int offset = src->Register.Index - ctx->abo_offsets[j];
                  if (src->Register.Indirect) {
                     if (src->Indirect.File != TGSI_FILE_ADDRESS)
                        return false;
                     strbuf_fmt(src_buf, "ac%d_%d[addr%d + %d]", abo_idx, abo_offset, src->Indirect.Index, offset);
                  } else
                     strbuf_fmt(src_buf, "ac%d_%d[%d]", abo_idx, abo_offset, offset);
               } else
                  strbuf_fmt(src_buf, "ac%d_%d", abo_idx, abo_offset);
               break;
            }
         }
         sinfo->sreg_index = src->Register.Index;
      } break;
      default:
         return false;
      }

      if (stype == TGSI_TYPE_DOUBLE) {
         bool isabsolute = src->Register.Absolute;
         snprintf(fp64_src, sizeof(fp64_src), "%s", src_buf->buf);
         strbuf_fmt(src_buf, "fp64_src[%d]", i);
         emit_buff(&ctx->glsl_strbufs, "%s.x = %spackDouble2x32(uvec2(%s%s))%s;\n", src_buf->buf, isabsolute ? "abs(" : "", fp64_src, swizzle, isabsolute ? ")" : "");
      }
   }

   return true;
}

static bool rewrite_1d_image_coordinate(struct vrend_strbuf *src, const struct tgsi_full_instruction *inst)
{
   if (inst->Src[0].Register.File == TGSI_FILE_IMAGE &&
       (inst->Memory.Texture == TGSI_TEXTURE_1D ||
        inst->Memory.Texture == TGSI_TEXTURE_1D_ARRAY))  {

      /* duplicate src */
      char *buf = strdup(src->buf);
      if (!buf)
         return false;

      if (inst->Memory.Texture == TGSI_TEXTURE_1D)
         strbuf_fmt(src, "vec2(vec4(%s).x, 0)", buf);
      else if (inst->Memory.Texture == TGSI_TEXTURE_1D_ARRAY)
         strbuf_fmt(src, "vec3(%s.xy, 0).xzy", buf);

      free(buf);
   }
   return true;
}

/* We have indirect IO access, but the guest actually send separate values, so
 * now we have to emulate arrays by putting IO values into arrays according
 * to semantic. Only join elements that are consecutive. */
static int
make_array_from_semantic(struct vrend_shader_io *io, int start_index,
                         int num_entries, enum tgsi_semantic semantic)
{
   struct vrend_shader_io *io_out_range = &io[start_index];

   int last_sid = io_out_range->sid;
   for (int i = start_index + 1; i < num_entries; ++i) {
      if (io[i].name == semantic && (io[i].sid - last_sid == 1)) {
         io[i].glsl_predefined_no_emit = true;
         last_sid = io[i].sid;
         io[i].array_offset = io[i].sid - io_out_range->sid;
         io_out_range->last = io_out_range->first + io[i].array_offset;
         io[i].overlapping_array = io_out_range;
      } else {
         break;
      }
   }
   return io_out_range->last + 1;
}

static bool
collapse_vars_to_arrays(struct vrend_shader_io *io,
                           int num_entries,
                           enum tgsi_semantic semantic)
{

   bool retval = 0;
   int start_index = 0;
   while (start_index < num_entries) {
      if (io[start_index].name == semantic && !io[start_index].glsl_predefined_no_emit) {
         int new_start_index = make_array_from_semantic(io, start_index, num_entries, semantic);
         retval |= io[start_index].first != io[start_index].last;
         start_index = new_start_index;
      } else {
         ++start_index;
      }
   }

   io->num_components = 4;
   io->usage_mask = 0xf;
   return retval;
}

static void
rewrite_io_ranged(struct dump_ctx *ctx)
{
   if ((ctx->info.indirect_files & (1 << TGSI_FILE_INPUT)) ||
       ctx->key->require_input_arrays) {

      bool generic_array = collapse_vars_to_arrays(ctx->inputs, ctx->num_inputs,
                                                  TGSI_SEMANTIC_GENERIC);
      bool patch_array = collapse_vars_to_arrays(ctx->inputs, ctx->num_inputs,
                                                 TGSI_SEMANTIC_PATCH);

      ctx->has_input_arrays = generic_array || patch_array;

      if (prefer_generic_io_block(ctx, io_in))
         ctx->glsl_ver_required = require_glsl_ver(ctx, 150);
   }

   if ((ctx->info.indirect_files & (1 << TGSI_FILE_OUTPUT)) ||
       ctx->key->require_output_arrays) {

      bool generic_array = collapse_vars_to_arrays(ctx->outputs, ctx->num_outputs,
                                                  TGSI_SEMANTIC_GENERIC);
      bool patch_array = collapse_vars_to_arrays(ctx->outputs, ctx->num_outputs,
                                                TGSI_SEMANTIC_PATCH);

      ctx->has_output_arrays = generic_array || patch_array;

      if (prefer_generic_io_block(ctx, io_out))
         ctx->glsl_ver_required = require_glsl_ver(ctx, 150);
   }

   if ((ctx->has_output_arrays || ctx->has_input_arrays)
       && ctx->cfg->has_arrays_of_arrays && !ctx->cfg->use_gles)
      ctx->shader_req_bits |= SHADER_REQ_ARRAYS_OF_ARRAYS;

}

static
void rewrite_vs_pos_array(struct dump_ctx *ctx)
{
   int range_start = 0xffff;
   int range_end = 0;
   int io_idx = 0;

   for (uint32_t i = 0; i < ctx->num_inputs; ++i) {
      if (ctx->inputs[i].name == TGSI_SEMANTIC_POSITION) {
         ctx->inputs[i].glsl_predefined_no_emit = true;
         if (ctx->inputs[i].first < range_start) {
            io_idx = i;
            range_start = ctx->inputs[i].first;
         }
         if (ctx->inputs[i].last > range_end)
            range_end = ctx->inputs[i].last;
      }
   }

   if (range_start != range_end) {
      ctx->inputs[io_idx].first = range_start;
      ctx->inputs[io_idx].last = range_end;
      ctx->inputs[io_idx].glsl_predefined_no_emit = false;
      ctx->glsl_ver_required = require_glsl_ver(ctx, 150);
   }
}


static
void emit_fs_clipdistance_load(const struct dump_ctx *ctx,
                               struct vrend_glsl_strbufs *glsl_strbufs)
{
   int i;

   if (!ctx->fs_uses_clipdist_input)
      return;

   int prev_num = ctx->key->num_in_clip + ctx->key->num_in_cull;
   int ndists;
   const char *prefix="";

   if (ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL)
      prefix = "gl_out[gl_InvocationID].";

   ndists = ctx->num_in_clip_dist;
   if (prev_num > 0)
      ndists = prev_num;

   for (i = 0; i < ndists; i++) {
      int clipidx = i < 4 ? 0 : 1;
      char swiz = i & 3;
      char wm = 0;
      switch (swiz) {
      default:
      case 0: wm = 'x'; break;
      case 1: wm = 'y'; break;
      case 2: wm = 'z'; break;
      case 3: wm = 'w'; break;
      }
      bool is_cull = false;
      if (prev_num > 0) {
         if (i >= ctx->key->num_in_clip && i < prev_num)
            is_cull = true;
      }
      const char *clip_cull = is_cull ? "Cull" : "Clip";
      emit_buff(glsl_strbufs, "clip_dist_temp[%d].%c = %sgl_%sDistance[%d];\n", clipidx, wm, prefix, clip_cull,
                is_cull ? i - ctx->key->num_in_clip : i);
   }
}

static
void renumber_io_arrays(uint32_t nio, struct vrend_shader_io *io)
{
   int next_array_id = 1;
   for (uint32_t i = 0; i < nio; ++i) {
      if (io[i].name != TGSI_SEMANTIC_GENERIC &&
          io[i].name != TGSI_SEMANTIC_PATCH)
         continue;
      if (io[i].array_id > 0)
         io[i].array_id = next_array_id++;
   }
}

// TODO Consider exposing non-const ctx-> members as args to make *ctx const
static void handle_io_arrays(struct dump_ctx *ctx)
{
   /* If the guest sent real IO arrays then we declare them individually,
    * and have to do some work to deal with overlapping values, regions and
    * enhanced layouts */
   if (ctx->guest_sent_io_arrays)  {

      /* Array ID numbering is not ordered accross shaders, so do
       * some renumbering for generics and patches. */
      renumber_io_arrays(ctx->num_inputs, ctx->inputs);
      renumber_io_arrays(ctx->num_outputs, ctx->outputs);

   } else {
      /* The guest didn't send real arrays, do we might have to add a big array
       * for all generic and another for patch inputs */
      rewrite_io_ranged(ctx);
   }
}

static int
compare_shader_io(const void *vlhs, const void *vrhs)
{
   struct vrend_shader_io *lhs = (struct vrend_shader_io *)vlhs;
   struct vrend_shader_io *rhs = (struct vrend_shader_io *)vrhs;

   if (lhs->name < rhs->name)
      return -1;
   if (lhs->name > rhs->name)
      return 1;
   return lhs->sid - rhs->sid;
}

static void
add_missing_semantic_inputs(struct vrend_shader_io *inputs, int *num_inputs,
                            int *next_location, uint64_t sids_missing,
                            const char *prefix, char *type_prefix,
                            enum tgsi_semantic name,
                            const struct vrend_shader_key *key)
{

   while (sids_missing) {
      int sid = u_bit_scan64(&sids_missing);
      struct vrend_shader_io *io = &inputs[*num_inputs];
      io->sid = sid;
      io->last = io->first = *next_location;
      io->name = name;
      io->type = VEC_FLOAT;
      uint32_t sids_added = 1 << sid;


      for (uint32_t j = 0; j < key->in_arrays.num_arrays; j++) {
         const struct vrend_shader_io_array *array = &key->in_arrays.layout[j];
         if (array->name == name &&
             array->sid <= sid &&
             array->sid + array->size >= sid) {
            io->last = io->first + array->size;
            io->sid = array->sid;
            sids_added = ((1u << array->size) - 1) << sid;
            break;
         }
      }

      (*next_location) += io->last - io->first + 1;

      sids_missing &= ~sids_added;

      snprintf(io->glsl_name, 128, "%s%s%d", prefix, type_prefix, sid);
      (*num_inputs)++;
   }
}

static int
add_missing_inputs(const struct dump_ctx *ctx, struct vrend_shader_io *inputs,
                   int num_inputs)
{
   uint64_t generics_declared = 0;
   uint64_t patches_declared = 0;
   uint8_t texcoord_declared = 0;

   int next_location = 0;
   for (int i = 0; i < num_inputs; ++i) {
      int offset = 0;
      for (int k = inputs[i].first; k <= inputs[i].last; ++k, ++offset) {
         int sid = inputs[i].sid + offset;
         switch (inputs[i].name) {
         case TGSI_SEMANTIC_GENERIC:
            generics_declared |= 1ull << sid;
            break;
         case TGSI_SEMANTIC_PATCH:
            patches_declared |= 1ull << sid;
            break;
         case TGSI_SEMANTIC_TEXCOORD:
            texcoord_declared |= 1ull << sid;
            break;
         default:
            ;
         }
      }
      if (next_location < inputs[i].last)
         next_location = inputs[i].last;
   }
   ++next_location;

   uint64_t generics_missing = ctx->key->in_generic_expected_mask & ~generics_declared;
   uint64_t patches_missing = ctx->key->in_patch_expected_mask & ~patches_declared;
   uint64_t texcoord_missing = ctx->key->in_texcoord_expected_mask & ~texcoord_declared;

   const char *prefix = get_stage_input_name_prefix(ctx, ctx->prog_type);
   add_missing_semantic_inputs(inputs, &num_inputs, &next_location,
                               generics_missing, prefix, "_g",
                               TGSI_SEMANTIC_GENERIC, ctx->key);
   add_missing_semantic_inputs(inputs, &num_inputs, &next_location,
                               texcoord_missing, prefix, "_t",
                               TGSI_SEMANTIC_TEXCOORD, ctx->key);
   add_missing_semantic_inputs(inputs, &num_inputs, &next_location,
                               patches_missing, "patch", "",
                               TGSI_SEMANTIC_PATCH, ctx->key);

   qsort(inputs, num_inputs, sizeof(struct vrend_shader_io),
         compare_shader_io);
   return num_inputs;
}

static bool
iter_instruction(struct tgsi_iterate_context *iter,
                 struct tgsi_full_instruction *inst)
{
   struct dump_ctx *ctx = (struct dump_ctx *)iter;
   struct dest_info dinfo = { 0 };
   struct source_info sinfo = { 0 };
   const char *srcs[4];
   char *dsts[TGSI_FULL_MAX_DST_REGISTERS];
   char fp64_dsts[TGSI_FULL_MAX_DST_REGISTERS][255];
   unsigned instno = ctx->instno++;
   char writemask[6] = "";
   char src_swizzle0[16];

   sinfo.svec4 = VEC4;

   if (ctx->prog_type == (enum tgsi_processor_type) -1)
      ctx->prog_type = iter->processor.Processor;

   if (instno == 0) {
      if (ctx->prog_type != TGSI_PROCESSOR_VERTEX)
         ctx->num_inputs = add_missing_inputs(ctx, ctx->inputs, ctx->num_inputs);
      handle_io_arrays(ctx);

      /* Vertex shader inputs are not send as arrays, but the access may still be
       * indirect. so we have to deal with that */
      if (ctx->prog_type == TGSI_PROCESSOR_VERTEX &&
          ctx->info.indirect_files & (1 << TGSI_FILE_INPUT)) {
         rewrite_vs_pos_array(ctx);
      }

      emit_buf(&ctx->glsl_strbufs, "void main(void)\n{\n");
      if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
         emit_color_select(ctx, &ctx->glsl_strbufs);
         if (ctx->fs_uses_clipdist_input)
            emit_fs_clipdistance_load(ctx, &ctx->glsl_strbufs);
      }
      if (ctx->so)
         prepare_so_movs(ctx);

      /* GLES doesn't allow invariant specifiers on inputs, but on GL with
       * GLSL < 4.30 it is required to match the output of the previous stage */
      if (!ctx->cfg->use_gles) {
         for (uint32_t i = 0; i < ctx->num_inputs; ++i) {
            uint32_t bit_pos = varying_bit_from_semantic_and_index(ctx->inputs[i].name, ctx->inputs[i].sid);
            uint32_t slot = bit_pos / 32;
            uint32_t bit = 1u << (bit_pos & 0x1f);
            if (ctx->key->force_invariant_inputs[slot] & bit)
               ctx->inputs[i].invariant = 1;
            else
               ctx->inputs[i].invariant = 0;
         }
      }
   }

   if (!get_destination_info(ctx, inst, &dinfo, ctx->dst_bufs, fp64_dsts, writemask))
      return false;

   if (!get_source_info(ctx, inst, &sinfo, ctx->src_bufs, src_swizzle0))
      return false;

   for (size_t i = 0; i < ARRAY_SIZE(srcs); ++i)
      srcs[i] = ctx->src_bufs[i].buf;

   for (size_t i = 0; i < ARRAY_SIZE(dsts); ++i)
      dsts[i] = ctx->dst_bufs[i].buf;

   switch (inst->Instruction.Opcode) {
   case TGSI_OPCODE_SQRT:
   case TGSI_OPCODE_DSQRT:
      emit_buff(&ctx->glsl_strbufs, "%s = sqrt(vec4(%s))%s;\n", dsts[0], srcs[0], writemask);
      break;
   case TGSI_OPCODE_LRP:
      emit_buff(&ctx->glsl_strbufs, "%s = mix(vec4(%s), vec4(%s), vec4(%s))%s;\n", dsts[0], srcs[2], srcs[1], srcs[0], writemask);
      break;
   case TGSI_OPCODE_DP2:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(dot(vec2(%s), vec2(%s)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1]);
      break;
   case TGSI_OPCODE_DP3:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(dot(vec3(%s), vec3(%s)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1]);
      break;
   case TGSI_OPCODE_DP4:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(dot(vec4(%s), vec4(%s)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1]);
      break;
   case TGSI_OPCODE_DPH:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(dot(vec4(vec3(%s), 1.0), vec4(%s)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1]);
      break;
   case TGSI_OPCODE_MAX:
   case TGSI_OPCODE_DMAX:
   case TGSI_OPCODE_IMAX:
   case TGSI_OPCODE_UMAX:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(max(%s, %s))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], writemask);
      break;
   case TGSI_OPCODE_MIN:
   case TGSI_OPCODE_DMIN:
   case TGSI_OPCODE_IMIN:
   case TGSI_OPCODE_UMIN:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(min(%s, %s))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], writemask);
      break;
   case TGSI_OPCODE_ABS:
   case TGSI_OPCODE_IABS:
   case TGSI_OPCODE_DABS:
      emit_op1("abs");
      break;
   case TGSI_OPCODE_KILL_IF:
      emit_buff(&ctx->glsl_strbufs, "if (any(lessThan(%s, vec4(0.0))))\ndiscard;\n", srcs[0]);
      break;
   case TGSI_OPCODE_IF:
   case TGSI_OPCODE_UIF:
      emit_buff(&ctx->glsl_strbufs, "if (bool(%s.x)) {\n", srcs[0]);
      indent_buf(&ctx->glsl_strbufs);
      break;
   case TGSI_OPCODE_ELSE:
      outdent_buf(&ctx->glsl_strbufs);
      emit_buf(&ctx->glsl_strbufs, "} else {\n");
      indent_buf(&ctx->glsl_strbufs);
      break;
   case TGSI_OPCODE_ENDIF:
      emit_buf(&ctx->glsl_strbufs, "}\n");
      outdent_buf(&ctx->glsl_strbufs);
      break;
   case TGSI_OPCODE_KILL:
      emit_buff(&ctx->glsl_strbufs, "discard;\n");
      break;
   case TGSI_OPCODE_DST:
      emit_buff(&ctx->glsl_strbufs, "%s = vec4(1.0, %s.y * %s.y, %s.z, %s.w);\n", dsts[0],
               srcs[0], srcs[1], srcs[0], srcs[1]);
      break;
   case TGSI_OPCODE_LIT:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(vec4(1.0, max(%s.x, 0.0), step(0.0, %s.x) * pow(max(0.0, %s.y), clamp(%s.w, -128.0, 128.0)), 1.0)%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[0], srcs[0], srcs[0], writemask);
      break;
   case TGSI_OPCODE_EX2:
      emit_op1("exp2");
      break;
   case TGSI_OPCODE_LG2:
      emit_op1("log2");
      break;
   case TGSI_OPCODE_EXP:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(vec4(pow(2.0, floor(%s.x)), %s.x - floor(%s.x), exp2(%s.x), 1.0)%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[0], srcs[0], srcs[0], writemask);
      break;
   case TGSI_OPCODE_LOG:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(vec4(floor(log2(%s.x)), %s.x / pow(2.0, floor(log2(%s.x))), log2(%s.x), 1.0)%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[0], srcs[0], srcs[0], writemask);
      break;
   case TGSI_OPCODE_COS:
      emit_op1("cos");
      break;
   case TGSI_OPCODE_SIN:
      emit_op1("sin");
      break;
   case TGSI_OPCODE_SCS:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(vec4(cos(%s.x), sin(%s.x), 0, 1)%s);\n", dsts[0], get_string(dinfo.dstconv),
               srcs[0], srcs[0], writemask);
      break;
   case TGSI_OPCODE_DDX:
      emit_op1("dFdx");
      break;
   case TGSI_OPCODE_DDY:
      emit_op1("dFdy");
      break;
   case TGSI_OPCODE_DDX_FINE:
      ctx->shader_req_bits |= SHADER_REQ_DERIVATIVE_CONTROL;
      emit_op1("dFdxFine");
      break;
   case TGSI_OPCODE_DDY_FINE:
      ctx->shader_req_bits |= SHADER_REQ_DERIVATIVE_CONTROL;
      emit_op1("dFdyFine");
      break;
   case TGSI_OPCODE_RCP:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(1.0/(%s));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_DRCP:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(1.0LF/(%s));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_FLR:
   case TGSI_OPCODE_DFLR:
      emit_op1("floor");
      break;
   case TGSI_OPCODE_ROUND:
   case TGSI_OPCODE_DROUND:
      // There is no TGSI OPCODE for roundEven, prefer roundEven
      // so roundEven in guest gets translated to roundEven.
      if ((ctx->cfg->use_gles && ctx->cfg->glsl_version >= 300) ||
          ctx->cfg->glsl_version >= 400)
         emit_op1("roundEven");
      else
         emit_op1("round");
      break;
   case TGSI_OPCODE_ISSG:
      emit_op1("sign");
      break;
   case TGSI_OPCODE_CEIL:
   case TGSI_OPCODE_DCEIL:
      emit_op1("ceil");
      break;
   case TGSI_OPCODE_FRC:
   case TGSI_OPCODE_DFRAC:
      emit_op1("fract");
      break;
   case TGSI_OPCODE_TRUNC:
   case TGSI_OPCODE_DTRUNC:
      emit_op1("trunc");
      break;
   case TGSI_OPCODE_SSG:
   case TGSI_OPCODE_DSSG:
      emit_op1("sign");
      break;
   case TGSI_OPCODE_VOTE_ALL:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(allInvocationsARB(bool(%s.x)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      ctx->shader_req_bits |= SHADER_REQ_SHADER_GROUP_VOTE;
      ctx->glsl_ver_required = require_glsl_ver(ctx, 430);
      break;
   case TGSI_OPCODE_VOTE_ANY:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(anyInvocationARB(bool(%s.x)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      ctx->shader_req_bits |= SHADER_REQ_SHADER_GROUP_VOTE;
      ctx->glsl_ver_required = require_glsl_ver(ctx, 430);
      break;
   case TGSI_OPCODE_VOTE_EQ:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(allInvocationsEqualARB(bool(%s.x)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      ctx->shader_req_bits |= SHADER_REQ_SHADER_GROUP_VOTE;
      ctx->glsl_ver_required = require_glsl_ver(ctx, 430);
      break;
   case TGSI_OPCODE_RSQ:
   case TGSI_OPCODE_DRSQ:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(inversesqrt(%s.x));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_FBFETCH:
   case TGSI_OPCODE_MOV:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(%s%s));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], sinfo.override_no_wm[0] ? "" : writemask);
      break;
   case TGSI_OPCODE_ADD:
   case TGSI_OPCODE_DADD:
      emit_arit_op2("+");
      break;
   case TGSI_OPCODE_UADD:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(uvec4(%s) + uvec4(%s))%s);\n", dsts[0],
            get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], writemask);
      break;
   case TGSI_OPCODE_SUB:
      emit_arit_op2("-");
      break;
   case TGSI_OPCODE_MUL:
   case TGSI_OPCODE_DMUL:
      emit_arit_op2("*");
      break;
   case TGSI_OPCODE_DIV:
   case TGSI_OPCODE_DDIV:
      emit_arit_op2("/");
      break;
   case TGSI_OPCODE_UMUL:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s((uvec4(%s) * uvec4(%s)))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], writemask);
      break;
   case TGSI_OPCODE_UMOD:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s((uvec4(%s) %% uvec4(%s)))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], writemask);
      break;
   case TGSI_OPCODE_IDIV:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s((ivec4(%s) / ivec4(%s)))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], writemask);
      break;
   case TGSI_OPCODE_UDIV:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s((uvec4(%s) / uvec4(%s)))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], writemask);
      break;
   case TGSI_OPCODE_ISHR:
   case TGSI_OPCODE_USHR:
      emit_arit_op2(">>");
      break;
   case TGSI_OPCODE_SHL:
      emit_arit_op2("<<");
      break;
   case TGSI_OPCODE_MAD:
      emit_buff(&ctx->glsl_strbufs, "%s = %s((%s * %s + %s)%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1], srcs[2], writemask);
      break;
   case TGSI_OPCODE_UMAD:
   case TGSI_OPCODE_DMAD:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s((%s * %s + %s)%s));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], srcs[2], writemask);
      break;
   case TGSI_OPCODE_OR:
      emit_arit_op2("|");
      break;
   case TGSI_OPCODE_AND:
      emit_arit_op2("&");
      break;
   case TGSI_OPCODE_XOR:
      emit_arit_op2("^");
      break;
   case TGSI_OPCODE_MOD:
      emit_arit_op2("%");
      break;
   case TGSI_OPCODE_TEX:
   case TGSI_OPCODE_TEX2:
   case TGSI_OPCODE_TXB:
   case TGSI_OPCODE_TXL:
   case TGSI_OPCODE_TXB2:
   case TGSI_OPCODE_TXL2:
   case TGSI_OPCODE_TXD:
   case TGSI_OPCODE_TXF:
   case TGSI_OPCODE_TG4:
   case TGSI_OPCODE_TXP:
      translate_tex(ctx, inst, &sinfo, &dinfo, srcs, dsts[0], writemask);
      break;
   case TGSI_OPCODE_LODQ:
      emit_lodq(ctx, inst, &sinfo, &dinfo, srcs, dsts[0], writemask);
      break;
   case TGSI_OPCODE_TXQ:
      emit_txq(ctx, inst, sinfo.sreg_index, srcs, dsts[0], writemask);
      break;
   case TGSI_OPCODE_TXQS:
      emit_txqs(ctx, inst, sinfo.sreg_index, srcs, dsts[0]);
      break;
   case TGSI_OPCODE_I2F:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(ivec4(%s)%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0], writemask);
      break;
   case TGSI_OPCODE_I2D:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(ivec4(%s));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_D2F:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_U2F:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(uvec4(%s)%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0], writemask);
      break;
   case TGSI_OPCODE_U2D:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(uvec4(%s));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_F2I:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(ivec4(%s))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], writemask);
      break;
   case TGSI_OPCODE_D2I:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(%s(%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), get_string(dinfo.idstconv), srcs[0]);
      break;
   case TGSI_OPCODE_F2U:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(uvec4(%s))%s);\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], writemask);
      break;
   case TGSI_OPCODE_D2U:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(%s(%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), get_string(dinfo.udstconv), srcs[0]);
      break;
   case TGSI_OPCODE_F2D:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(%s));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0]);
      break;
   case TGSI_OPCODE_NOT:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(uintBitsToFloat(~(uvec4(%s))));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_INEG:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(intBitsToFloat(-(ivec4(%s))));\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_DNEG:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(-%s);\n", dsts[0], get_string(dinfo.dstconv), srcs[0]);
      break;
   case TGSI_OPCODE_SEQ:
      emit_compare("equal");
      break;
   case TGSI_OPCODE_USEQ:
   case TGSI_OPCODE_FSEQ:
   case TGSI_OPCODE_DSEQ:
      if (inst->Instruction.Opcode == TGSI_OPCODE_DSEQ)
         strcpy(writemask, ".x");
      emit_ucompare("equal");
      break;
   case TGSI_OPCODE_SLT:
      emit_compare("lessThan");
      break;
   case TGSI_OPCODE_SLE:
      emit_compare("lessThanEqual");
      break;
   case TGSI_OPCODE_SGT:
      emit_compare("greaterThan");
      break;
   case TGSI_OPCODE_ISLT:
   case TGSI_OPCODE_USLT:
   case TGSI_OPCODE_FSLT:
   case TGSI_OPCODE_DSLT:
      if (inst->Instruction.Opcode == TGSI_OPCODE_DSLT)
         strcpy(writemask, ".x");
      emit_ucompare("lessThan");
      break;
   case TGSI_OPCODE_SNE:
      emit_compare("notEqual");
      break;
   case TGSI_OPCODE_USNE:
   case TGSI_OPCODE_FSNE:
   case TGSI_OPCODE_DSNE:
      if (inst->Instruction.Opcode == TGSI_OPCODE_DSNE)
         strcpy(writemask, ".x");
      emit_ucompare("notEqual");
      break;
   case TGSI_OPCODE_SGE:
      emit_compare("greaterThanEqual");
      break;
   case TGSI_OPCODE_ISGE:
   case TGSI_OPCODE_USGE:
   case TGSI_OPCODE_FSGE:
   case TGSI_OPCODE_DSGE:
      if (inst->Instruction.Opcode == TGSI_OPCODE_DSGE)
          strcpy(writemask, ".x");
      emit_ucompare("greaterThanEqual");
      break;
   case TGSI_OPCODE_POW:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(pow(%s, %s));\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1]);
      break;
   case TGSI_OPCODE_CMP:
      emit_buff(&ctx->glsl_strbufs, "%s = mix(%s, %s, greaterThanEqual(%s, vec4(0.0)))%s;\n", dsts[0], srcs[1], srcs[2], srcs[0], writemask);
      break;
   case TGSI_OPCODE_UCMP:
      emit_buff(&ctx->glsl_strbufs, "%s = mix(%s, %s, notEqual(floatBitsToUint(%s), uvec4(0.0)))%s;\n", dsts[0], srcs[2], srcs[1], srcs[0], writemask);
      break;
   case TGSI_OPCODE_END:
      if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX) {
         handle_vertex_proc_exit(ctx, &ctx->glsl_strbufs, &ctx->has_clipvertex_so);
      } else if (iter->processor.Processor == TGSI_PROCESSOR_TESS_CTRL && ctx->cfg->has_cull_distance) {
         emit_clip_dist_movs(ctx, &ctx->glsl_strbufs);
      } else if (iter->processor.Processor == TGSI_PROCESSOR_TESS_EVAL && ctx->cfg->has_cull_distance) {
         if (ctx->so && !ctx->key->gs_present)
            emit_so_movs(ctx, &ctx->glsl_strbufs, &ctx->has_clipvertex_so);
         emit_clip_dist_movs(ctx, &ctx->glsl_strbufs);
         if (!ctx->key->gs_present) {
            emit_prescale(&ctx->glsl_strbufs);
         }
      } else if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
         handle_fragment_proc_exit(ctx, &ctx->glsl_strbufs);
      }
      emit_buf(&ctx->glsl_strbufs, "}\n");
      break;
   case TGSI_OPCODE_RET:
      if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX) {
         handle_vertex_proc_exit(ctx, &ctx->glsl_strbufs, &ctx->has_clipvertex_so);
      } else if (iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT) {
         handle_fragment_proc_exit(ctx, &ctx->glsl_strbufs);
      }
      emit_buf(&ctx->glsl_strbufs, "return;\n");
      break;
   case TGSI_OPCODE_ARL:
      emit_buff(&ctx->glsl_strbufs, "%s = int(floor(%s)%s);\n", dsts[0], srcs[0], writemask);
      break;
   case TGSI_OPCODE_UARL:
      emit_buff(&ctx->glsl_strbufs, "%s = int(%s);\n", dsts[0], srcs[0]);
      break;
   case TGSI_OPCODE_XPD:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(cross(vec3(%s), vec3(%s)));\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1]);
      break;
   case TGSI_OPCODE_BGNLOOP:
      emit_buf(&ctx->glsl_strbufs, "do {\n");
      indent_buf(&ctx->glsl_strbufs);
      break;
   case TGSI_OPCODE_ENDLOOP:
      outdent_buf(&ctx->glsl_strbufs);
      emit_buf(&ctx->glsl_strbufs, "} while(true);\n");
      break;
   case TGSI_OPCODE_BRK:
      emit_buf(&ctx->glsl_strbufs, "break;\n");
      break;
   case TGSI_OPCODE_EMIT:
      if (likely((unsigned) inst->Src[0].Register.Index < MAX_IMMEDIATE)) {
         struct immed *imd = &ctx->imm[inst->Src[0].Register.Index];
         if (ctx->so && ctx->key->gs_present)
            emit_so_movs(ctx, &ctx->glsl_strbufs, &ctx->has_clipvertex_so);
         if (ctx->cfg->has_cull_distance && ctx->key->gs.emit_clip_distance)
            emit_clip_dist_movs(ctx, &ctx->glsl_strbufs);
         emit_prescale(&ctx->glsl_strbufs);
         if (imd->val[inst->Src[0].Register.SwizzleX].ui > 0) {
            ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
            emit_buff(&ctx->glsl_strbufs, "EmitStreamVertex(%d);\n", imd->val[inst->Src[0].Register.SwizzleX].ui);
         } else
            emit_buf(&ctx->glsl_strbufs, "EmitVertex();\n");
         break;
      } else {
         virgl_error("Immediate range exceeded, max is %u\n", MAX_IMMEDIATE);
         return false;
      }
   case TGSI_OPCODE_ENDPRIM:
      if (likely((unsigned) inst->Src[0].Register.Index < MAX_IMMEDIATE)) {
         struct immed *imd = &ctx->imm[inst->Src[0].Register.Index];
         if (imd->val[inst->Src[0].Register.SwizzleX].ui > 0) {
            ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
            emit_buff(&ctx->glsl_strbufs, "EndStreamPrimitive(%d);\n", imd->val[inst->Src[0].Register.SwizzleX].ui);
         } else
            emit_buf(&ctx->glsl_strbufs, "EndPrimitive();\n");
         break;
      } else {
         virgl_error("Immediate range exceeded, max is %u\n", MAX_IMMEDIATE);
         return false;
      }
   case TGSI_OPCODE_INTERP_CENTROID:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(vec4(interpolateAtCentroid(%s)%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], src_swizzle0);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_INTERP_SAMPLE:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(vec4(interpolateAtSample(%s, %s.x)%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], src_swizzle0);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_INTERP_OFFSET:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(vec4(interpolateAtOffset(%s, %s.xy)%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], src_swizzle0);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_UMUL_HI:
      emit_buff(&ctx->glsl_strbufs, "umulExtended(%s, %s, umul_temp, mul_utemp);\n", srcs[0], srcs[1]);
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(umul_temp%s));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), writemask);
      if (!ctx->cfg->use_gles) {
         if (ctx->cfg->has_gpu_shader5)
            ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
         else
            ctx->shader_req_bits |= SHADER_REQ_SHADER_INTEGER_FUNC;
      }
      ctx->write_mul_utemp = true;
      break;
   case TGSI_OPCODE_IMUL_HI:
      emit_buff(&ctx->glsl_strbufs, "imulExtended(%s, %s, imul_temp, mul_itemp);\n", srcs[0], srcs[1]);
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(imul_temp%s));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), writemask);
      if (!ctx->cfg->use_gles) {
         if (ctx->cfg->has_gpu_shader5)
            ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
         else
            ctx->shader_req_bits |= SHADER_REQ_SHADER_INTEGER_FUNC;
      }
      ctx->write_mul_itemp = true;
      break;

   case TGSI_OPCODE_IBFE:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(bitfieldExtract(%s, int(%s.x), int(%s.x))));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], srcs[2]);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_UBFE:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(bitfieldExtract(%s, int(%s.x), int(%s.x))));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0], srcs[1], srcs[2]);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_BFI:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(uintBitsToFloat(bitfieldInsert(%s, %s, int(%s), int(%s))));\n", dsts[0], get_string(dinfo.dstconv), srcs[0], srcs[1], srcs[2], srcs[3]);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_BREV:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(bitfieldReverse(%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0]);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_POPC:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(bitCount(%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0]);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_LSB:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(findLSB(%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0]);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_IMSB:
   case TGSI_OPCODE_UMSB:
      emit_buff(&ctx->glsl_strbufs, "%s = %s(%s(findMSB(%s)));\n", dsts[0], get_string(dinfo.dstconv), get_string(dinfo.dtypeprefix), srcs[0]);
      ctx->shader_req_bits |= SHADER_REQ_GPU_SHADER5;
      break;
   case TGSI_OPCODE_BARRIER:
      emit_buf(&ctx->glsl_strbufs, "barrier();\n");
      break;
   case TGSI_OPCODE_MEMBAR:
      if (likely((unsigned) inst->Src[0].Register.Index < MAX_IMMEDIATE)) {
         struct immed *imd = &ctx->imm[inst->Src[0].Register.Index];
         uint32_t val = imd->val[inst->Src[0].Register.SwizzleX].ui;
         uint32_t all_val = (TGSI_MEMBAR_SHADER_BUFFER |
                             TGSI_MEMBAR_ATOMIC_BUFFER |
                             TGSI_MEMBAR_SHADER_IMAGE |
                             TGSI_MEMBAR_SHARED);

         if (val & TGSI_MEMBAR_THREAD_GROUP) {
            emit_buf(&ctx->glsl_strbufs, "groupMemoryBarrier();\n");
         } else {
            if ((val & all_val) == all_val) {
               emit_buf(&ctx->glsl_strbufs, "memoryBarrier();\n");
               ctx->shader_req_bits |= SHADER_REQ_IMAGE_LOAD_STORE;
            } else {
               if (val & TGSI_MEMBAR_SHADER_BUFFER) {
                  emit_buf(&ctx->glsl_strbufs, "memoryBarrierBuffer();\n");
               }
               if (val & TGSI_MEMBAR_ATOMIC_BUFFER) {
                  emit_buf(&ctx->glsl_strbufs, "memoryBarrierAtomicCounter();\n");
               }
               if (val & TGSI_MEMBAR_SHADER_IMAGE) {
                  emit_buf(&ctx->glsl_strbufs, "memoryBarrierImage();\n");
               }
               if (val & TGSI_MEMBAR_SHARED) {
               emit_buf(&ctx->glsl_strbufs, "memoryBarrierShared();\n");
               }
            }
         }
         break;
      } else {
         virgl_error("Immediate range exceeded, max is %u\n", MAX_IMMEDIATE);
         return false;
      }
   case TGSI_OPCODE_STORE:
      if (ctx->cfg->use_gles) {
         if (!rewrite_1d_image_coordinate(ctx->src_bufs + 1, inst))
            return false;
         srcs[1] = ctx->src_bufs[1].buf;
      }
      /* Don't try to write to dest with a negative index. */
      if (dinfo.dest_index >= 0)
         translate_store(ctx, &ctx->glsl_strbufs, ctx->ssbo_memory_qualifier, ctx->images,
                         inst, &sinfo, srcs, &dinfo, dsts[0]);
      break;
   case TGSI_OPCODE_LOAD:
      if (ctx->cfg->use_gles) {
         if (!rewrite_1d_image_coordinate(ctx->src_bufs + 1, inst))
            return false;
         srcs[1] = ctx->src_bufs[1].buf;
      }
      /* Replace an obvious out-of-bounds load with loading zero. */
      if (sinfo.sreg_index < 0 ||
          !translate_load(ctx, &ctx->glsl_strbufs, ctx->ssbo_memory_qualifier, ctx->images,
                          inst, &sinfo, &dinfo, srcs, dsts[0], writemask)) {
         emit_buff(&ctx->glsl_strbufs, "%s = vec4(0.0, 0.0, 0.0, 0.0)%s;\n", dsts[0], writemask);
      }
      break;
   case TGSI_OPCODE_ATOMUADD:
   case TGSI_OPCODE_ATOMXCHG:
   case TGSI_OPCODE_ATOMCAS:
   case TGSI_OPCODE_ATOMAND:
   case TGSI_OPCODE_ATOMOR:
   case TGSI_OPCODE_ATOMXOR:
   case TGSI_OPCODE_ATOMUMIN:
   case TGSI_OPCODE_ATOMUMAX:
   case TGSI_OPCODE_ATOMIMIN:
   case TGSI_OPCODE_ATOMIMAX:
      if (ctx->cfg->use_gles) {
         if (!rewrite_1d_image_coordinate(ctx->src_bufs + 1, inst))
            return false;
         srcs[1] = ctx->src_bufs[1].buf;
      }
      translate_atomic(ctx, inst, &sinfo, srcs, dsts[0]);
      break;
   case TGSI_OPCODE_RESQ:
      translate_resq(ctx, inst, srcs, dsts[0], writemask);
      break;
   case TGSI_OPCODE_CLOCK:
      ctx->shader_req_bits |= SHADER_REQ_SHADER_CLOCK;
      emit_buff(&ctx->glsl_strbufs, "%s = uintBitsToFloat(clock2x32ARB());\n", dsts[0]);
      break;
   default:
      virgl_warn("Failed to convert opcode %d\n", inst->Instruction.Opcode);
      break;
   }

   for (uint32_t i = 0; i < 1; i++) {
      enum tgsi_opcode_type dtype = tgsi_opcode_infer_dst_type(inst->Instruction.Opcode);
      if (dtype == TGSI_TYPE_DOUBLE) {
         emit_buff(&ctx->glsl_strbufs, "%s = uintBitsToFloat(unpackDouble2x32(%s));\n", fp64_dsts[0], dsts[0]);
      }
   }
   if (inst->Instruction.Saturate) {
      emit_buff(&ctx->glsl_strbufs, "%s = clamp(%s, 0.0, 1.0);\n", dsts[0], dsts[0]);
   }

   if (strbuf_get_error(&ctx->glsl_strbufs.glsl_main))
       return false;
   return true;
}

static bool
prolog(struct tgsi_iterate_context *iter)
{
   struct dump_ctx *ctx = (struct dump_ctx *)iter;

   if (ctx->prog_type == (enum tgsi_processor_type) -1)
      ctx->prog_type = iter->processor.Processor;

   if (iter->processor.Processor == TGSI_PROCESSOR_VERTEX &&
       ctx->key->gs_present)
      ctx->glsl_ver_required = require_glsl_ver(ctx, 150);

   return true;
}

static void emit_ext(struct vrend_glsl_strbufs *glsl_strbufs, const char *name,
                     const char *verb)
{
   emit_ver_extf(glsl_strbufs, "#extension GL_%s : %s\n", name, verb);
}

static void emit_header(const struct dump_ctx *ctx, struct vrend_glsl_strbufs *glsl_strbufs)
{
   if (ctx->cfg->use_gles) {
      emit_ver_extf(glsl_strbufs, "#version %d es\n", ctx->cfg->glsl_version);

      if ((ctx->shader_req_bits & SHADER_REQ_CLIP_DISTANCE) ||
          (ctx->cfg->has_cull_distance && ctx->num_out_clip_dist == 0)) {
         emit_ext(glsl_strbufs, "EXT_clip_cull_distance", "require");
      }

      if (ctx->shader_req_bits & SHADER_REQ_SAMPLER_MS)
         emit_ext(glsl_strbufs, "OES_texture_storage_multisample_2d_array", "require");

      if (ctx->shader_req_bits & SHADER_REQ_CONSERVATIVE_DEPTH)
         emit_ext(glsl_strbufs, "EXT_conservative_depth", "require");

      if (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT) {
         if (ctx->shader_req_bits & SHADER_REQ_FBFETCH)
            emit_ext(glsl_strbufs, "EXT_shader_framebuffer_fetch", "require");
         if (ctx->shader_req_bits & SHADER_REQ_BLEND_EQUATION_ADVANCED)
            emit_ext(glsl_strbufs, "KHR_blend_equation_advanced", "require");
         if (ctx->cfg->has_dual_src_blend)
            emit_ext(glsl_strbufs, "EXT_blend_func_extended", "require");
      }

      if (ctx->shader_req_bits & SHADER_REQ_VIEWPORT_IDX)
         emit_ext(glsl_strbufs, "OES_viewport_array", "require");

      if (ctx->prog_type == TGSI_PROCESSOR_GEOMETRY) {
         emit_ext(glsl_strbufs, "EXT_geometry_shader", "require");
         if (ctx->shader_req_bits & SHADER_REQ_PSIZE)
            emit_ext(glsl_strbufs, "OES_geometry_point_size", "enable");
      }

      if (ctx->shader_req_bits & SHADER_REQ_NV_IMAGE_FORMATS)
         emit_ext(glsl_strbufs, "NV_image_formats", "require");

      if (ctx->shader_req_bits & SHADER_REQ_SEPERATE_SHADER_OBJECTS)
         emit_ext(glsl_strbufs, "EXT_separate_shader_objects", "require");

      if ((ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL ||
           ctx->prog_type == TGSI_PROCESSOR_TESS_EVAL)) {
         if (ctx->cfg->glsl_version < 320)
            emit_ext(glsl_strbufs, "OES_tessellation_shader", "require");
         emit_ext(glsl_strbufs, "OES_tessellation_point_size", "enable");
      }

      if (ctx->cfg->glsl_version < 320) {
         if (ctx->shader_req_bits & SHADER_REQ_SAMPLER_BUF)
            emit_ext(glsl_strbufs, "EXT_texture_buffer", "require");
         if (prefer_generic_io_block(ctx, io_in) || prefer_generic_io_block(ctx, io_out)) {
            emit_ext(glsl_strbufs, "OES_shader_io_blocks", "require");
         }
         if (ctx->shader_req_bits & SHADER_REQ_SAMPLE_SHADING)
            emit_ext(glsl_strbufs, "OES_sample_variables", "require");
         if (ctx->shader_req_bits & SHADER_REQ_GPU_SHADER5) {
            emit_ext(glsl_strbufs, "OES_gpu_shader5", "require");
            emit_ext(glsl_strbufs, "OES_shader_multisample_interpolation",
                           "require");
         }
         if (ctx->shader_req_bits & SHADER_REQ_CUBE_ARRAY)
            emit_ext(glsl_strbufs, "OES_texture_cube_map_array", "require");
         if (ctx->shader_req_bits & SHADER_REQ_LAYER)
            emit_ext(glsl_strbufs, "EXT_geometry_shader", "require");
         if (ctx->shader_req_bits & SHADER_REQ_IMAGE_ATOMIC)
            emit_ext(glsl_strbufs, "OES_shader_image_atomic", "require");

         if (ctx->shader_req_bits & SHADER_REQ_GEOMETRY_SHADER)
            emit_ext(glsl_strbufs, "EXT_geometry_shader", "require");
      }

      if (logiop_require_inout(ctx->key)) {
         if (ctx->cfg->has_fbfetch_coherent)
            emit_ext(glsl_strbufs, "EXT_shader_framebuffer_fetch", "require");
         else
            emit_ext(glsl_strbufs, "EXT_shader_framebuffer_fetch_non_coherent", "require");

      }

      if (ctx->shader_req_bits & SHADER_REQ_TEXTURE_SHADOW_LOD)
         emit_ext(glsl_strbufs, "EXT_texture_shadow_lod", "require");

      if (ctx->shader_req_bits & SHADER_REQ_LODQ)
         emit_ext(glsl_strbufs, "EXT_texture_query_lod", "require");

      if (ctx->shader_req_bits & SHADER_REQ_SHADER_NOPERSPECTIVE_INTERPOLATION)
         emit_ext(glsl_strbufs, "NV_shader_noperspective_interpolation", "require");

      emit_hdr(glsl_strbufs, "precision highp float;\n");
      emit_hdr(glsl_strbufs, "precision highp int;\n");
   } else {
      if (ctx->prog_type == TGSI_PROCESSOR_COMPUTE) {
         emit_ver_ext(glsl_strbufs, "#version 330\n");
         emit_ext(glsl_strbufs, "ARB_compute_shader", "require");
      } else {
         if (ctx->glsl_ver_required > 150)
            emit_ver_extf(glsl_strbufs, "#version %d\n", ctx->glsl_ver_required);
         else if (ctx->prog_type == TGSI_PROCESSOR_GEOMETRY ||
             ctx->prog_type == TGSI_PROCESSOR_TESS_EVAL ||
             ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL ||
             ctx->glsl_ver_required == 150)
            emit_ver_ext(glsl_strbufs, "#version 150\n");
         else if (ctx->glsl_ver_required == 140)
            emit_ver_ext(glsl_strbufs, "#version 140\n");
         else
            emit_ver_ext(glsl_strbufs, "#version 130\n");
      }

      if (ctx->shader_req_bits & SHADER_REQ_ENHANCED_LAYOUTS)
         emit_ext(glsl_strbufs, "ARB_enhanced_layouts", "require");

      if (ctx->shader_req_bits & SHADER_REQ_SEPERATE_SHADER_OBJECTS)
         emit_ext(glsl_strbufs, "ARB_separate_shader_objects", "require");

      if (ctx->shader_req_bits & SHADER_REQ_EXPLICIT_ATTRIB_LOCATION)
         emit_ext(glsl_strbufs, "ARB_explicit_attrib_location", "require");

      if (ctx->shader_req_bits & SHADER_REQ_ARRAYS_OF_ARRAYS)
         emit_ext(glsl_strbufs, "ARB_arrays_of_arrays", "require");

      if (ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL ||
          ctx->prog_type == TGSI_PROCESSOR_TESS_EVAL)
         emit_ext(glsl_strbufs, "ARB_tessellation_shader", "require");

      if (ctx->prog_type == TGSI_PROCESSOR_VERTEX && ctx->cfg->use_explicit_locations)
         emit_ext(glsl_strbufs, "ARB_explicit_attrib_location", "require");
      if (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT && fs_emit_layout(ctx))
         emit_ext(glsl_strbufs, "ARB_fragment_coord_conventions", "require");

      if (ctx->ubo_used_mask)
         emit_ext(glsl_strbufs, "ARB_uniform_buffer_object", "require");

      if (ctx->num_cull_dist_prop || ctx->key->num_in_cull || ctx->key->num_out_cull)
         emit_ext(glsl_strbufs, "ARB_cull_distance", "require");
      if (ctx->ssbo_used_mask)
         emit_ext(glsl_strbufs, "ARB_shader_storage_buffer_object", "require");

      if (ctx->num_abo) {
         emit_ext(glsl_strbufs, "ARB_shader_atomic_counters", "require");
         emit_ext(glsl_strbufs, "ARB_shader_atomic_counter_ops", "require");
      }

      for (uint32_t i = 0; i < ARRAY_SIZE(shader_req_table); i++) {
         if (shader_req_table[i].key == SHADER_REQ_SAMPLER_RECT && ctx->glsl_ver_required >= 140)
            continue;

         if (ctx->shader_req_bits & shader_req_table[i].key) {
            emit_ext(glsl_strbufs, shader_req_table[i].string, "require");
         }
      }
   }
}

char vrend_shader_samplerreturnconv(enum tgsi_return_type type)
{
   switch (type) {
   case TGSI_RETURN_TYPE_SINT:
      return 'i';
   case TGSI_RETURN_TYPE_UINT:
      return 'u';
   default:
      return ' ';
   }
}

const char *vrend_shader_samplertypeconv(bool use_gles, int sampler_type)
{
   switch (sampler_type) {
   case TGSI_TEXTURE_BUFFER: return "Buffer";
   case TGSI_TEXTURE_1D:
      if (!use_gles)
         return "1D";
      /* fallthrough */
   case TGSI_TEXTURE_2D: return "2D";
   case TGSI_TEXTURE_3D: return "3D";
   case TGSI_TEXTURE_CUBE: return "Cube";
   case TGSI_TEXTURE_RECT: return use_gles ? "2D" : "2DRect";
   case TGSI_TEXTURE_SHADOW1D:
      if (!use_gles) {
         return "1DShadow";
      }
      /* fallthrough */
   case TGSI_TEXTURE_SHADOW2D: return "2DShadow";
   case TGSI_TEXTURE_SHADOWRECT:
      return (!use_gles) ? "2DRectShadow" : "2DShadow";
   case TGSI_TEXTURE_1D_ARRAY:
      if (!use_gles)
         return "1DArray";
      /* fallthrough */
   case TGSI_TEXTURE_2D_ARRAY: return "2DArray";
   case TGSI_TEXTURE_SHADOW1D_ARRAY:
      if (!use_gles) {
         return "1DArrayShadow";
      }
      /* fallthrough */
   case TGSI_TEXTURE_SHADOW2D_ARRAY: return "2DArrayShadow";
   case TGSI_TEXTURE_SHADOWCUBE: return "CubeShadow";
   case TGSI_TEXTURE_CUBE_ARRAY: return "CubeArray";
   case TGSI_TEXTURE_SHADOWCUBE_ARRAY: return "CubeArrayShadow";
   case TGSI_TEXTURE_2D_MSAA: return "2DMS";
   case TGSI_TEXTURE_2D_ARRAY_MSAA: return "2DMSArray";
   default: return NULL;
   }
}

static const char *get_interp_string(const struct vrend_shader_cfg *cfg, enum tgsi_interpolate_mode interpolate, bool flatshade)
{
   switch (interpolate) {
   case TGSI_INTERPOLATE_LINEAR:
      if (cfg->has_nopersective)
         return "noperspective ";
      else
         return "";
   case TGSI_INTERPOLATE_PERSPECTIVE:
      return "smooth ";
   case TGSI_INTERPOLATE_CONSTANT:
      return "flat ";
   case TGSI_INTERPOLATE_COLOR:
      if (flatshade)
         return "flat ";
      /* fallthrough */
   default:
      return "";
   }
}

static const char *get_aux_string(enum tgsi_interpolate_loc location)
{
   switch (location) {
   case TGSI_INTERPOLATE_LOC_CENTER:
   default:
      return "";
   case TGSI_INTERPOLATE_LOC_CENTROID:
      return "centroid ";
   case TGSI_INTERPOLATE_LOC_SAMPLE:
      return "sample ";
   }
}

static void emit_sampler_decl(const struct dump_ctx *ctx,
                              struct vrend_glsl_strbufs *glsl_strbufs,
                              uint32_t *shadow_samp_mask,
                              uint32_t i, uint32_t range,
                              const struct vrend_shader_sampler *sampler)
{
   char ptc;
   bool is_shad;
   const char *sname, *precision, *stc;

   sname = tgsi_proc_to_prefix(ctx->prog_type);

   precision = (ctx->cfg->use_gles) ? "highp" : "";

   ptc = vrend_shader_samplerreturnconv(sampler->tgsi_sampler_return);
   stc = vrend_shader_samplertypeconv(ctx->cfg->use_gles, sampler->tgsi_sampler_type);
   is_shad = samplertype_is_shadow(sampler->tgsi_sampler_type);

   if (range)
      emit_hdrf(glsl_strbufs, "uniform %s %csampler%s %ssamp%d[%d];\n", precision, ptc, stc, sname, i, range);
   else
      emit_hdrf(glsl_strbufs, "uniform %s %csampler%s %ssamp%d;\n", precision, ptc, stc, sname, i);

   if (is_shad) {
      emit_hdrf(glsl_strbufs, "uniform %s vec4 %sshadmask%d;\n", precision, sname, i);
      emit_hdrf(glsl_strbufs, "uniform %s vec4 %sshadadd%d;\n", precision, sname, i);
      *shadow_samp_mask |= (1 << i);
   }
}

const char *get_internalformat_string(int virgl_format, enum tgsi_return_type *stype)
{
   switch (virgl_format) {
   case PIPE_FORMAT_R11G11B10_FLOAT:
      *stype = TGSI_RETURN_TYPE_FLOAT;
      return "r11f_g11f_b10f";
   case PIPE_FORMAT_R10G10B10A2_UNORM:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "rgb10_a2";
   case PIPE_FORMAT_R10G10B10A2_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "rgb10_a2ui";
   case PIPE_FORMAT_R8_UNORM:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "r8";
   case PIPE_FORMAT_R8_SNORM:
      *stype = TGSI_RETURN_TYPE_SNORM;
      return "r8_snorm";
   case PIPE_FORMAT_R8_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "r8ui";
   case PIPE_FORMAT_R8_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "r8i";
   case PIPE_FORMAT_R8G8_UNORM:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "rg8";
   case PIPE_FORMAT_R8G8_SNORM:
      *stype = TGSI_RETURN_TYPE_SNORM;
      return "rg8_snorm";
   case PIPE_FORMAT_R8G8_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "rg8ui";
   case PIPE_FORMAT_R8G8_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "rg8i";
   case PIPE_FORMAT_R8G8B8A8_UNORM:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "rgba8";
   case PIPE_FORMAT_R8G8B8A8_SNORM:
      *stype = TGSI_RETURN_TYPE_SNORM;
      return "rgba8_snorm";
   case PIPE_FORMAT_R8G8B8A8_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "rgba8ui";
   case PIPE_FORMAT_R8G8B8A8_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "rgba8i";
   case PIPE_FORMAT_R16_UNORM:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "r16";
   case PIPE_FORMAT_R16_SNORM:
      *stype = TGSI_RETURN_TYPE_SNORM;
      return "r16_snorm";
   case PIPE_FORMAT_R16_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "r16ui";
   case PIPE_FORMAT_R16_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "r16i";
   case PIPE_FORMAT_R16_FLOAT:
      *stype = TGSI_RETURN_TYPE_FLOAT;
      return "r16f";
   case PIPE_FORMAT_R16G16_UNORM:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "rg16";
   case PIPE_FORMAT_R16G16_SNORM:
      *stype = TGSI_RETURN_TYPE_SNORM;
      return "rg16_snorm";
   case PIPE_FORMAT_R16G16_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "rg16ui";
   case PIPE_FORMAT_R16G16_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "rg16i";
   case PIPE_FORMAT_R16G16_FLOAT:
      *stype = TGSI_RETURN_TYPE_FLOAT;
      return "rg16f";
   case PIPE_FORMAT_R16G16B16A16_UNORM:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "rgba16";
   case PIPE_FORMAT_R16G16B16A16_SNORM:
      *stype = TGSI_RETURN_TYPE_SNORM;
      return "rgba16_snorm";
   case PIPE_FORMAT_R16G16B16A16_FLOAT:
      *stype = TGSI_RETURN_TYPE_FLOAT;
      return "rgba16f";
   case PIPE_FORMAT_R32_FLOAT:
      *stype = TGSI_RETURN_TYPE_FLOAT;
      return "r32f";
   case PIPE_FORMAT_R32_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "r32ui";
   case PIPE_FORMAT_R32_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "r32i";
   case PIPE_FORMAT_R32G32_FLOAT:
      *stype = TGSI_RETURN_TYPE_FLOAT;
      return "rg32f";
   case PIPE_FORMAT_R32G32_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "rg32ui";
   case PIPE_FORMAT_R32G32_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "rg32i";
   case PIPE_FORMAT_R32G32B32A32_FLOAT:
      *stype = TGSI_RETURN_TYPE_FLOAT;
      return "rgba32f";
   case PIPE_FORMAT_R32G32B32A32_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "rgba32ui";
   case PIPE_FORMAT_R16G16B16A16_UINT:
      *stype = TGSI_RETURN_TYPE_UINT;
      return "rgba16ui";
   case PIPE_FORMAT_R16G16B16A16_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "rgba16i";
   case PIPE_FORMAT_R32G32B32A32_SINT:
      *stype = TGSI_RETURN_TYPE_SINT;
      return "rgba32i";
   case PIPE_FORMAT_NONE:
      *stype = TGSI_RETURN_TYPE_UNORM;
      return "";
   default:
      *stype = TGSI_RETURN_TYPE_UNORM;
      virgl_warn("Illegal format %d\n", virgl_format);
      return "";
   }
}

static void emit_image_decl(const struct dump_ctx *ctx,
                            struct vrend_glsl_strbufs *glsl_strbufs,
                            uint32_t i, uint32_t range,
                            const struct vrend_shader_image *image)
{
   char ptc;
   const char *sname, *stc, *formatstr;
   enum tgsi_return_type itype;
   const char *volatile_str = image->vflag ? "volatile " : "";
   const char *coherent_str = image->coherent ? "coherent " : "";
   const char *precision = ctx->cfg->use_gles ? "highp " : "";
   const char *access = "";
   formatstr = get_internalformat_string(image->decl.Format, &itype);
   ptc = vrend_shader_samplerreturnconv(itype);
   sname = tgsi_proc_to_prefix(ctx->prog_type);
   stc = vrend_shader_samplertypeconv(ctx->cfg->use_gles, image->decl.Resource);


   /* From ARB_shader_image_load_store:
      Any image variable used for shader loads or atomic memory operations must
      be declared with a format layout qualifier matching the format of its
      associated image unit, ...  Otherwise, the access is considered to
      involve a format mismatch, ...  Image variables used exclusively for
      image stores need not include a format layout qualifier, but any declared
      qualifier must match the image unit format to avoid a format mismatch. */
   bool require_format_specifer = true;
   if (!image->decl.Writable) {
      access = "readonly ";
   } else if (!image->decl.Format ||
            (ctx->cfg->use_gles &&
             (image->decl.Format != PIPE_FORMAT_R32_FLOAT) &&
             (image->decl.Format != PIPE_FORMAT_R32_SINT) &&
             (image->decl.Format != PIPE_FORMAT_R32_UINT))) {
      access = "writeonly ";
      require_format_specifer = formatstr[0] != '\0';
   }

   const char *loc_bind = ctx->cfg->use_gles ? "binding" : "location";

   if (require_format_specifer) {
         emit_hdrf(glsl_strbufs, "layout(%s=%d, %s) ", loc_bind,
                   i + ctx->key->image_binding_offset, formatstr[0] != '\0' ? formatstr : "rgba32f");
   } else
      emit_hdrf(glsl_strbufs, "layout(%s=%d%s%s) ", loc_bind,
               i + ctx->key->image_binding_offset, formatstr[0] != '\0' ? ", ": ", rgba32f", formatstr);

   if (range)
      emit_hdrf(glsl_strbufs, "%s%s%suniform %s%cimage%s %simg%d[%d];\n",
               access, volatile_str, coherent_str, precision, ptc, stc, sname, i, range);
   else
      emit_hdrf(glsl_strbufs, "%s%s%suniform %s%cimage%s %simg%d;\n",
               access, volatile_str, coherent_str, precision, ptc, stc, sname, i);
}

static int emit_ios_common(const struct dump_ctx *ctx,
                           struct vrend_glsl_strbufs *glsl_strbufs,
                           uint32_t *shadow_samp_mask)
{
   uint32_t i;
   const char *sname = tgsi_proc_to_prefix(ctx->prog_type);
   int glsl_ver_required = ctx->glsl_ver_required;

   for (i = 0; i < ctx->num_temp_ranges; i++) {
      const char *precise = ctx->temp_ranges[i].precise_result ? "precise" : "";
      if (ctx->temp_ranges[i].array_id > 0) {
         emit_hdrf(glsl_strbufs, "%s vec4 temp%d[%d];\n", precise, ctx->temp_ranges[i].first,
                   ctx->temp_ranges[i].last - ctx->temp_ranges[i].first + 1);
      } else {
         emit_hdrf(glsl_strbufs, "%s vec4 temp%d;\n", precise, ctx->temp_ranges[i].first);
      }
   }

   if (ctx->require_dummy_value)
      emit_hdr(glsl_strbufs, "vec4 dummy_value = vec4(0.0, 0.0, 0.0, 0.0);\n");

   if (ctx->write_mul_utemp) {
      emit_hdr(glsl_strbufs, "uvec4 mul_utemp;\n");
      emit_hdr(glsl_strbufs, "uvec4 umul_temp;\n");
   }

   if (ctx->write_mul_itemp) {
      emit_hdr(glsl_strbufs, "ivec4 mul_itemp;\n");
      emit_hdr(glsl_strbufs, "ivec4 imul_temp;\n");
   }

   if (ctx->ssbo_used_mask || ctx->has_file_memory) {
     emit_hdr(glsl_strbufs, "uint ssbo_addr_temp;\n");
   }

   if (ctx->shader_req_bits & SHADER_REQ_FP64) {
      emit_hdr(glsl_strbufs, "dvec2 fp64_dst[3];\n");
      emit_hdr(glsl_strbufs, "dvec2 fp64_src[4];\n");
   }

   for (i = 0; i < ctx->num_address; i++) {
      emit_hdrf(glsl_strbufs, "int addr%d;\n", i);
   }
   if (ctx->num_consts) {
      const char *cname = tgsi_proc_to_prefix(ctx->prog_type);
      emit_hdrf(glsl_strbufs, "uniform uvec4 %sconst0[%d];\n", cname, ctx->num_consts);
   }

   if (ctx->ubo_used_mask) {
      const char *cname = tgsi_proc_to_prefix(ctx->prog_type);

      if (ctx->info.dimension_indirect_files & (1 << TGSI_FILE_CONSTANT)) {
         glsl_ver_required = require_glsl_ver(ctx, 150);
         int first = ffs(ctx->ubo_used_mask) - 1;
         unsigned num_ubo = util_bitcount(ctx->ubo_used_mask);
         emit_hdrf(glsl_strbufs, "uniform %subo { vec4 ubocontents[%d]; } %suboarr[%d];\n", cname, ctx->ubo_sizes[first], cname, num_ubo);
      } else {
         unsigned mask = ctx->ubo_used_mask;
         while (mask) {
            uint32_t i = u_bit_scan(&mask);
            emit_hdrf(glsl_strbufs, "uniform %subo%d { vec4 %subo%dcontents[%d]; };\n", cname, i, cname, i, ctx->ubo_sizes[i]);
         }
      }
   }

   if (ctx->info.indirect_files & (1 << TGSI_FILE_SAMPLER)) {
      for (i = 0; i < ctx->num_sampler_arrays; i++) {
         uint32_t first = ctx->sampler_arrays[i].first;
         uint32_t range = ctx->sampler_arrays[i].array_size;

         emit_sampler_decl(ctx, glsl_strbufs, shadow_samp_mask, first, range, ctx->samplers + first);
      }
   } else {
      unsigned nsamp = util_last_bit(ctx->samplers_used);
      for (i = 0; i < nsamp; i++) {

         if ((ctx->samplers_used & (1 << i)) == 0)
            continue;

         emit_sampler_decl(ctx, glsl_strbufs, shadow_samp_mask, i, 0, ctx->samplers + i);
      }
   }

   if (ctx->cfg->use_gles && ctx->gles_use_tex_query_level)
      emit_hdrf(glsl_strbufs, "uniform int %s_texlod[%d];\n", tgsi_proc_to_prefix(ctx->info.processor),
                util_bitcount(ctx->samplers_used));

   if (ctx->info.indirect_files & (1 << TGSI_FILE_IMAGE)) {
      for (i = 0; i < ctx->num_image_arrays; i++) {
         uint32_t first = ctx->image_arrays[i].first;
         uint32_t range = ctx->image_arrays[i].array_size;
         emit_image_decl(ctx, glsl_strbufs, first, range, ctx->images + first);
      }
   } else {
      uint32_t mask = ctx->images_used_mask;
      while (mask) {
         i = u_bit_scan(&mask);
         emit_image_decl(ctx, glsl_strbufs, i, 0, ctx->images + i);
      }
   }

   for (i = 0; i < ctx->num_abo; i++){
      emit_hdrf(glsl_strbufs, "layout (binding = %d, offset = %d) uniform atomic_uint ac%d_%d", ctx->abo_idx[i], ctx->abo_offsets[i] * 4, ctx->abo_idx[i], ctx->abo_offsets[i] * 4);
      if (ctx->abo_sizes[i] > 1)
         emit_hdrf(glsl_strbufs, "[%d]", ctx->abo_sizes[i]);
      emit_hdrf(glsl_strbufs, ";\n");
   }

   if (ctx->info.indirect_files & (1 << TGSI_FILE_BUFFER)) {
      uint32_t mask = ctx->ssbo_used_mask;
      while (mask) {
         int start, count;
         u_bit_scan_consecutive_range(&mask, &start, &count);
         int binding = start + ctx->key->ssbo_binding_offset - ctx->ssbo_first_binding;
         const char *atomic = (ctx->ssbo_atomic_mask & (1 << start)) ? "atomic" : "";
         emit_hdrf(glsl_strbufs, "layout (binding = %d, std430) buffer %sssbo%d { uint %sssbocontents%d[]; } %sssboarr%s[%d];\n", binding, sname, start, sname, start, sname, atomic, count);
      }
   } else {
      uint32_t mask = ctx->ssbo_used_mask;
      while (mask) {
         uint32_t id = u_bit_scan(&mask);
         int binding = id + ctx->key->ssbo_binding_offset - ctx->ssbo_first_binding;
         enum vrend_type_qualifier type = (ctx->ssbo_integer_mask & (1 << id)) ? INT : UINT;
         char *coherent = ctx->ssbo_memory_qualifier[id] == TGSI_MEMORY_COHERENT ? "coherent" : "";
         emit_hdrf(glsl_strbufs, "layout (binding = %d, std430) %s buffer %sssbo%d { %s %sssbocontents%d[]; };\n", binding, coherent, sname, id,
                  get_string(type), sname, id);
      }
   }

   return glsl_ver_required;
}

static void emit_ios_streamout(const struct dump_ctx *ctx,
                               struct vrend_glsl_strbufs *glsl_strbufs)
{
   if (ctx->so) {
      char outtype[6] = "";
      for (uint32_t i = 0; i < ctx->so->num_outputs; i++) {
         if (!ctx->write_so_outputs[i])
            continue;
         if (ctx->so->output[i].num_components == 1)
            snprintf(outtype, 6, "float");
         else
            snprintf(outtype, 6, "vec%d", ctx->so->output[i].num_components);

         if (ctx->so->output[i].stream && ctx->prog_type == TGSI_PROCESSOR_GEOMETRY)
            emit_hdrf(glsl_strbufs, "layout (stream=%d) out %s tfout%d;\n", ctx->so->output[i].stream, outtype, i);
         else  {
            const struct vrend_shader_io *output = get_io_slot(&ctx->outputs[0], ctx->num_outputs,
                  ctx->so->output[i].register_index);
            if (ctx->so->output[i].need_temp || output->name == TGSI_SEMANTIC_CLIPDIST ||
                ctx->prog_type == TGSI_PROCESSOR_GEOMETRY || output->glsl_predefined_no_emit) {

               if (ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL)
                  emit_hdrf(glsl_strbufs, "out %s tfout%d[];\n", outtype, i);
               else
                  emit_hdrf(glsl_strbufs, "out %s tfout%d;\n", outtype, i);
            }
         }
      }
   }
}

static void emit_ios_indirect_generics_output(const struct dump_ctx *ctx,
                                              struct vrend_glsl_strbufs *glsl_strbufs,
                                              const char *postfix)
{
   if (ctx->generic_ios.output_range.used) {
      int size = ctx->generic_ios.output_range.io.last -
         ctx->generic_ios.output_range.io.first + 1;
      char array_handle[32] = "";
      if (size > 1)
         snprintf(array_handle, sizeof(array_handle), "[%d]", size);

      if (prefer_generic_io_block(ctx, io_out)) {
         char blockname[64];
         const char *stage_prefix = get_stage_output_name_prefix(ctx->prog_type);
         get_blockname(blockname, stage_prefix, &ctx->generic_ios.output_range.io);

         char blockvarame[64];
         get_blockvarname(blockvarame, stage_prefix, &ctx->generic_ios.output_range.io, postfix);

         emit_hdrf(glsl_strbufs, "out %s {\n  vec4 %s%s; \n} %s;\n", blockname,
                   ctx->generic_ios.output_range.io.glsl_name, array_handle, blockvarame);
      } else
         emit_hdrf(glsl_strbufs, "out vec4 %s%s%s;\n",
                   ctx->generic_ios.output_range.io.glsl_name,
                   postfix,
                   array_handle);
   }
}

static void emit_ios_indirect_generics_input(const struct dump_ctx *ctx,
                                             struct vrend_glsl_strbufs *glsl_strbufs,
                                             const char *postfix)
{
   if (ctx->generic_ios.input_range.used) {
      int size = ctx->generic_ios.input_range.io.last -
         ctx->generic_ios.input_range.io.first + 1;
      char array_handle[32] = "";
      if (size > 1)
         snprintf(array_handle, sizeof(array_handle), "[%d]", size);

      if (size >= 256 || size < 0) {
         set_buf_error(glsl_strbufs);
         return;
      }

      if (prefer_generic_io_block(ctx, io_in)) {

         char blockname[64];
         char blockvarame[64];
         const char *stage_prefix = get_stage_input_name_prefix(ctx, ctx->prog_type);

         get_blockname(blockname, stage_prefix, &ctx->generic_ios.input_range.io);
         get_blockvarname(blockvarame, stage_prefix, &ctx->generic_ios.input_range.io,
                          postfix);

         emit_hdrf(glsl_strbufs, "in %s {\n        vec4 %s%s; \n} %s;\n",
                   blockname, ctx->generic_ios.input_range.io.glsl_name,
                   array_handle, blockvarame);
      } else
         emit_hdrf(glsl_strbufs, "in vec4 %s%s%s;\n",
                   ctx->generic_ios.input_range.io.glsl_name,
                   postfix,
                   array_handle);
   }
}

static void
emit_ios_generic(const struct dump_ctx *ctx,
                 struct vrend_glsl_strbufs *glsl_strbufs,
                 struct vrend_generic_ios *generic_ios,
                 struct vrend_texcoord_ios *texcoord_ios,
                 enum io_type iot,  const char *prefix,
                 const struct vrend_shader_io *io, const char *inout,
                 const char *postfix)
{
   const char *atype[3] =  {
      " vec4", "ivec4", "uvec4"
   };

   const char *t = atype[io->type];

   char layout[128] = "";

   if (io->overlapping_array)
      return;

   if (ctx->separable_program && io->name == TGSI_SEMANTIC_GENERIC &&
       !(ctx->prog_type == TGSI_PROCESSOR_FRAGMENT && strcmp(inout, "in") != 0)) {
      snprintf(layout, sizeof(layout), "layout(location = %d) ", 31 - io->sid);
   }

   if (io->first == io->last) {
      emit_hdr(glsl_strbufs, layout);
      /* ugly leave spaces to patch interp in later */
      emit_hdrf(glsl_strbufs, "%s%s %s  %s %s %s%s;\n",
                io->precise ? "precise" : "",
                io->invariant ? "invariant" : "",
                prefix,
                inout,
                t,
                io->glsl_name,
                postfix);

      if (io->name == TGSI_SEMANTIC_GENERIC) {
         if (io->sid >= 64) {
            set_buf_error(glsl_strbufs);
            return;
         }

         if (iot == io_in) {
            generic_ios->match.inputs_emitted_mask |= 1ull << io->sid;
         } else {
            generic_ios->match.outputs_emitted_mask |= 1ull << io->sid;
         }
      } else if (io->name == TGSI_SEMANTIC_TEXCOORD) {
         if (io->sid >= 8)  {
            set_buf_error(glsl_strbufs);
            return;
         }
         if (iot == io_in) {
            texcoord_ios->match.inputs_emitted_mask |= 1ull << io->sid;
         } else {
            texcoord_ios->match.outputs_emitted_mask |= 1ull << io->sid;
         }
      }

   } else {
      int array_size = io->last - io->first + 1;
      if (prefer_generic_io_block(ctx, iot)) {
         const char *stage_prefix = iot == io_in ? get_stage_input_name_prefix(ctx, ctx->prog_type):
                                                   get_stage_output_name_prefix(ctx->prog_type);

         char blockname[64];
         get_blockname(blockname, stage_prefix, io);

         char blockvarame[64];
         get_blockvarname(blockvarame, stage_prefix, io, postfix);

         emit_hdrf(glsl_strbufs, "%s %s {\n", inout, blockname);
         emit_hdr(glsl_strbufs, layout);
         emit_hdrf(glsl_strbufs, "%s%s\n%s     %s %s[%d]; \n} %s;\n",
                   io->precise ? "precise" : "",
                   io->invariant ? "invariant" : "",
                   prefix,
                   t,
                   io->glsl_name,
                   array_size,
                   blockvarame);
      } else {
         emit_hdr(glsl_strbufs, layout);
         emit_hdrf(glsl_strbufs, "%s%s\n%s       %s %s %s%s[%d];\n",
                   io->precise ? "precise" : "",
                   io->invariant ? "invariant" : "",
                   prefix,
                   inout,
                   t,
                   io->glsl_name,
                   postfix,
                   array_size);

         uint64_t mask = ((1ull << array_size) - 1) << io->sid;
         if (io->name == TGSI_SEMANTIC_GENERIC) {
            if (io->sid + array_size >= 64)  {
               set_buf_error(glsl_strbufs);
               return;
            }
            if (iot == io_in) {
               generic_ios->match.inputs_emitted_mask |= mask;
            } else {
               generic_ios->match.outputs_emitted_mask |= mask;
            }
         } else if (io->name == TGSI_SEMANTIC_TEXCOORD) {
            if (io->sid + array_size > 8)  {
               set_buf_error(glsl_strbufs);
               return;
            }
            if (iot == io_in) {
               texcoord_ios->match.inputs_emitted_mask |= mask;
            } else {
               texcoord_ios->match.outputs_emitted_mask |= mask;
            }
         }
      }
   }
}

typedef bool (*can_emit_generic_callback)(const struct vrend_shader_io *io);

/* Front and back color of the same semantic ID must have the same interpolator
 * specifiers, and it may happen, that one or the other shader doesn't define
 * both, front and back color, so always compare these two as COLOR. */
static inline
enum tgsi_semantic get_semantic_to_compare(enum tgsi_semantic name)
{
   switch (name) {
   case TGSI_SEMANTIC_COLOR:
   case TGSI_SEMANTIC_BCOLOR:
      return TGSI_SEMANTIC_COLOR;
   default:
      return name;
   }
}

static const char *
get_interpolator_prefix(struct vrend_strbuf *buf,
                        const struct vrend_shader_cfg *cfg, const struct vrend_shader_io *io,
                        const struct vrend_fs_shader_info *fs_info, bool flatshade)
{
   if (io->name == TGSI_SEMANTIC_GENERIC ||
       io->name == TGSI_SEMANTIC_TEXCOORD ||
       io->name == TGSI_SEMANTIC_COLOR ||
       io->name == TGSI_SEMANTIC_BCOLOR) {
      enum tgsi_semantic name = get_semantic_to_compare(io->name);

      for (int j = 0; j < fs_info->num_interps; ++j) {
         if (get_semantic_to_compare(fs_info->interpinfo[j].semantic_name) == name &&
             fs_info->interpinfo[j].semantic_index == io->sid) {
            strbuf_fmt(buf, "%s %s",
                       get_interp_string(cfg, fs_info->interpinfo[j].interpolate, flatshade),
                       get_aux_string(fs_info->interpinfo[j].location));
            return buf->buf;
         }
      }
   }
   return "";
}

static void
emit_ios_generic_outputs(const struct dump_ctx *ctx,
                         struct vrend_glsl_strbufs *glsl_strbufs,
                         struct vrend_generic_ios *generic_ios,
                         struct vrend_texcoord_ios *texcoord_ios,
                         uint8_t front_back_color_emitted_flags[64],
                         bool *force_color_two_side,
                         const can_emit_generic_callback can_emit_generic)
{
   uint32_t i;
   uint64_t fc_emitted = 0;
   uint64_t bc_emitted = 0;

   char buffer[64];
   struct vrend_strbuf buf;
   strbuf_alloc_fixed(&buf, buffer, sizeof(buffer));

   for (i = 0; i < ctx->num_outputs; i++) {

      if (!ctx->outputs[i].glsl_predefined_no_emit) {
         /* GS stream outputs are handled separately */
         if (!can_emit_generic(&ctx->outputs[i]))
            continue;

         /* It is save to use buf here even though it is declared outside the loop, because
          * when written it is reset, and the content is used within the iteration */
         const char *prefix = get_interpolator_prefix(&buf, ctx->cfg, &ctx->outputs[i],
                                                      &ctx->key->fs_info, ctx->key->flatshade);

         if (ctx->outputs[i].name == TGSI_SEMANTIC_COLOR) {
            if (ctx->outputs[i].sid >= 64) {
               virgl_error("Number of output id exceeded, max is 64\n");
               set_buf_error(glsl_strbufs);
               return;
            }

            front_back_color_emitted_flags[ctx->outputs[i].sid] |= FRONT_COLOR_EMITTED;
            fc_emitted |= 1ull << ctx->outputs[i].sid;
         }

         if (ctx->outputs[i].name == TGSI_SEMANTIC_BCOLOR) {
            if (ctx->outputs[i].sid >= 64) {
               virgl_error("Number of output id exceeded, max is 64\n");
               set_buf_error(glsl_strbufs);
               return;
            }

            front_back_color_emitted_flags[ctx->outputs[i].sid] |= BACK_COLOR_EMITTED;
            bc_emitted |= 1ull << ctx->outputs[i].sid;
         }

         emit_ios_generic(ctx, glsl_strbufs, generic_ios, texcoord_ios,
                          io_out, prefix, &ctx->outputs[i],
                          ctx->outputs[i].fbfetch_used ? "inout" : "out", "");
      } else if (ctx->outputs[i].invariant || ctx->outputs[i].precise) {
         emit_hdrf(glsl_strbufs, "%s%s;\n",
                   ctx->outputs[i].precise ? "precise " :
                   (ctx->outputs[i].invariant ? "invariant " : ""),
                   ctx->outputs[i].glsl_name);
      }
   }

   /* If a back color emitted without a corresponding front color, then
    * we have to force two side coloring, because the FS shader might expect
    * a front color too. */
   if (bc_emitted & ~fc_emitted)
      *force_color_two_side = 1;
}

static uint64_t
emit_ios_patch(struct vrend_glsl_strbufs *glsl_strbufs,
               const char *prefix, const struct vrend_shader_io *io,
               const char *inout, int size, bool emit_location)
{
   uint64_t emitted_patches = 0;

   /* We start these locations from 32 and proceed downwards, to avoid
    * conflicting with generic IO locations. */
   if (emit_location)
      emit_hdrf(glsl_strbufs, "layout(location = %d) ", io->sid);

   if (io->last == io->first) {
      emit_hdrf(glsl_strbufs, "%s %s vec4 %s;\n", prefix, inout, io->glsl_name);
      emitted_patches |= 1ul << io->sid;
   } else {
      emit_hdrf(glsl_strbufs, "%s %s vec4 %s[%d];\n", prefix, inout,
                io->glsl_name, size);
      uint64_t mask = (1ul << size) - 1;
      emitted_patches |= mask << io->sid;
   }
   return emitted_patches;
}

static bool
can_emit_generic_default(UNUSED const struct vrend_shader_io *io)
{
   return true;
}

static void emit_ios_vs(const struct dump_ctx *ctx,
                        struct vrend_glsl_strbufs *glsl_strbufs,
                        struct vrend_generic_ios *generic_ios,
                        struct vrend_texcoord_ios *texcoord_ios,
                        uint8_t front_back_color_emitted_flags[64],
                        bool *force_color_two_side)
{
   uint32_t i;

   for (i = 0; i < ctx->num_inputs; i++) {
      char postfix[32] = "";
      if (!ctx->inputs[i].glsl_predefined_no_emit) {
         if (ctx->cfg->use_explicit_locations) {
            emit_hdrf(glsl_strbufs, "layout(location=%d) ", ctx->inputs[i].first);
         }
         if (ctx->inputs[i].first != ctx->inputs[i].last)
            snprintf(postfix, sizeof(postfix), "[%d]", ctx->inputs[i].last - ctx->inputs[i].first + 1);
         const char *vtype[3] = {"vec4", "ivec4", "uvec4"};
         emit_hdrf(glsl_strbufs, "in %s %s%s;\n",
                   vtype[ctx->inputs[i].type], ctx->inputs[i].glsl_name, postfix);
      }
   }

   emit_ios_indirect_generics_output(ctx, glsl_strbufs, "");

   emit_ios_generic_outputs(ctx, glsl_strbufs, generic_ios, texcoord_ios,
                            front_back_color_emitted_flags, force_color_two_side,
                            can_emit_generic_default);
   if (strbuf_get_error(&glsl_strbufs->glsl_main))
      return;

   if (ctx->key->color_two_side || ctx->force_color_two_side) {
      bool fcolor_emitted, bcolor_emitted;

      enum tgsi_interpolate_mode interpolators[2] = {TGSI_INTERPOLATE_COLOR, TGSI_INTERPOLATE_COLOR};
      enum tgsi_interpolate_loc interp_loc[2] = { TGSI_INTERPOLATE_LOC_CENTER, TGSI_INTERPOLATE_LOC_CENTER};
      for (int k = 0; k < ctx->key->fs_info.num_interps; k++) {
         const struct vrend_interp_info *interp_info = &ctx->key->fs_info.interpinfo[k];
         if (interp_info->semantic_name == TGSI_SEMANTIC_COLOR ||
             interp_info->semantic_name == TGSI_SEMANTIC_BCOLOR) {
            interpolators[interp_info->semantic_index] = interp_info->interpolate;
            interp_loc[interp_info->semantic_index] = interp_info->location;
         }
      }

      for (i = 0; i < ctx->num_outputs; i++) {
         if (ctx->outputs[i].sid >= 2)
            continue;

         fcolor_emitted = bcolor_emitted = false;

         fcolor_emitted = front_back_color_emitted_flags[ctx->outputs[i].sid] & FRONT_COLOR_EMITTED;
         bcolor_emitted = front_back_color_emitted_flags[ctx->outputs[i].sid] & BACK_COLOR_EMITTED;

         if (fcolor_emitted && !bcolor_emitted) {
            emit_hdrf(glsl_strbufs, "%s %s out vec4 vso_bc%d;\n",
                      get_interp_string(ctx->cfg, interpolators[ctx->outputs[i].sid], ctx->key->flatshade),
                      get_aux_string(interp_loc[ctx->outputs[i].sid]),
                      ctx->outputs[i].sid);
            front_back_color_emitted_flags[ctx->outputs[i].sid] |= BACK_COLOR_EMITTED;
         }
         if (bcolor_emitted && !fcolor_emitted) {
            emit_hdrf(glsl_strbufs, "%s %s out vec4 vso_c%d;\n",
                      get_interp_string(ctx->cfg, interpolators[ctx->outputs[i].sid], ctx->key->flatshade),
                      get_aux_string(interp_loc[ctx->outputs[i].sid]),
                      ctx->outputs[i].sid);
            front_back_color_emitted_flags[ctx->outputs[i].sid] |= FRONT_COLOR_EMITTED;
         }
      }
   }

   if (ctx->key->vs.fog_fixup_mask)
      emit_fog_fixup_hdr(ctx, glsl_strbufs);

   if (ctx->has_clipvertex && ctx->is_last_vertex_stage) {
      emit_hdrf(glsl_strbufs, "%svec4 clipv_tmp;\n", ctx->has_clipvertex_so ? "out " : "");
   }

   char cull_buf[64] = "";
   char clip_buf[64] = "";

   if (ctx->cfg->has_cull_distance && (ctx->num_out_clip_dist || ctx->is_last_vertex_stage)) {
      int num_clip_dists = ctx->num_clip_dist_prop ? ctx->num_clip_dist_prop : 0;
      int num_cull_dists = ctx->num_cull_dist_prop ? ctx->num_cull_dist_prop : 0;

      int num_clip_cull = num_clip_dists + num_cull_dists;
      if (ctx->num_out_clip_dist && !num_clip_cull)
         num_clip_dists = ctx->num_out_clip_dist;

      if (num_clip_dists)
         snprintf(clip_buf, 64, "out float gl_ClipDistance[%d];\n", num_clip_dists);
      if (num_cull_dists)
         snprintf(cull_buf, 64, "out float gl_CullDistance[%d];\n", num_cull_dists);

      if (ctx->is_last_vertex_stage) {
         emit_hdrf(glsl_strbufs, "%s%s", clip_buf, cull_buf);
      }

      emit_hdr(glsl_strbufs, "vec4 clip_dist_temp[2];\n");      
   }

   const char *psize_buf = ctx->has_pointsize_output ? "out float gl_PointSize;\n" : "";

   if (!ctx->is_last_vertex_stage && ctx->key->use_pervertex_in) {
      emit_hdrf(glsl_strbufs, "out gl_PerVertex {\n vec4 gl_Position;\n %s%s%s};\n", clip_buf, cull_buf, psize_buf);
   }
}

static const char *get_depth_layout(int depth_layout)
{
   const char *dl[4]  = {
      "depth_any",
      "depth_greater",
      "depth_less",
      "depth_unchanged"
   };

   if (depth_layout < 1 || depth_layout > TGSI_FS_DEPTH_LAYOUT_UNCHANGED)
      return NULL;
   return dl[depth_layout -1];
}

static void emit_ios_fs(const struct dump_ctx *ctx,
                        struct vrend_glsl_strbufs *glsl_strbufs,
                        struct vrend_generic_ios *generic_ios,
                        struct vrend_texcoord_ios *texcoord_ios,
                        uint64_t *interp_input_mask
                        )
{
   uint32_t i;

   if (fs_emit_layout(ctx)) {
      bool upper_left = ctx->fs_lower_left_origin == ctx->key->fs.lower_left_origin;
      char comma = (upper_left && ctx->fs_integer_pixel_center) ? ',' : ' ';

      if (!ctx->cfg->use_gles)
         emit_hdrf(glsl_strbufs, "layout(%s%c%s) in vec4 gl_FragCoord;\n",
                   upper_left ? "origin_upper_left" : "",
                   comma,
                   ctx->fs_integer_pixel_center ? "pixel_center_integer" : "");
   }
   if (ctx->early_depth_stencil) {
      emit_hdr(glsl_strbufs, "layout(early_fragment_tests) in;\n");
   }

   emit_ios_indirect_generics_input(ctx, glsl_strbufs, "");

   for (i = 0; i < ctx->num_inputs; i++) {
      if (!ctx->inputs[i].glsl_predefined_no_emit) {
         const char *prefix = "";
         const char *auxprefix = "";

         if (ctx->cfg->use_gles) {
            if (ctx->inputs[i].name == TGSI_SEMANTIC_COLOR) {
               if (!(ctx->key->fs.available_color_in_bits & (1 << ctx->inputs[i].sid))) {
                  emit_hdrf(glsl_strbufs, "vec4 %s = vec4(0.0, 0.0, 0.0, 0.0);\n",
                            ctx->inputs[i].glsl_name);
                  continue;
               }
            }

            if (ctx->inputs[i].name == TGSI_SEMANTIC_BCOLOR) {
               if (!(ctx->key->fs.available_color_in_bits & (1 << ctx->inputs[i].sid) << 2)) {
                  emit_hdrf(glsl_strbufs, "vec4 %s = vec4(0.0, 0.0, 0.0, 0.0);\n",
                            ctx->inputs[i].glsl_name);
                  continue;
               }
            }
         }

         if (ctx->inputs[i].name == TGSI_SEMANTIC_GENERIC ||
              ctx->inputs[i].name == TGSI_SEMANTIC_COLOR ||
              ctx->inputs[i].name == TGSI_SEMANTIC_BCOLOR) {
            prefix = get_interp_string(ctx->cfg, ctx->inputs[i].interpolate, ctx->key->flatshade);
            if (!prefix)
               prefix = "";
            auxprefix = get_aux_string(ctx->inputs[i].location);
            *interp_input_mask |= UINT64_C(1) << i;
         }

         char prefixes[64];
         snprintf(prefixes, sizeof(prefixes), "%s %s", prefix, auxprefix);
         emit_ios_generic(ctx, glsl_strbufs, generic_ios, texcoord_ios, io_in, prefixes, &ctx->inputs[i], "in", "");
      }
   }

   if (ctx->key->color_two_side) {
      if (ctx->color_in_mask & 1)
         emit_hdr(glsl_strbufs, "vec4 realcolor0;\n");
      if (ctx->color_in_mask & 2)
         emit_hdr(glsl_strbufs, "vec4 realcolor1;\n");
   }

   unsigned choices = ctx->fs_blend_equation_advanced;
   while (choices) {
      enum gl_advanced_blend_mode choice = (enum gl_advanced_blend_mode)u_bit_scan(&choices);
      emit_hdrf(glsl_strbufs, "layout(blend_support_%s) out;\n", blend_to_name(choice));
   }

   if (ctx->write_all_cbufs) {
      const char* type = "vec4";
      if (ctx->key->fs.cbufs_unsigned_int_bitmask)
         type = "uvec4";
      else if (ctx->key->fs.cbufs_signed_int_bitmask)
         type = "ivec4";

      for (i = 0; i < (uint32_t)ctx->cfg->max_draw_buffers; i++) {
         if (ctx->cfg->use_gles) {
            if (ctx->key->fs.logicop_enabled)
               emit_hdrf(glsl_strbufs, "%s fsout_tmp_c%d;\n", type, i);

            if (logiop_require_inout(ctx->key)) {
               const char *noncoherent = ctx->cfg->has_fbfetch_coherent ? "" : ", noncoherent";
               emit_hdrf(glsl_strbufs, "layout (location=%d%s) inout highp %s fsout_c%d;\n", i, noncoherent, type, i);
            } else
               emit_hdrf(glsl_strbufs, "layout (location=%d) out %s fsout_c%d;\n", i,
                         type, i);
         } else
            emit_hdrf(glsl_strbufs, "out %s fsout_c%d;\n", type, i);
      }
   } else {
      for (i = 0; i < ctx->num_outputs; i++) {

         if (!ctx->outputs[i].glsl_predefined_no_emit) {
            char prefix[64] = "";

            if (ctx->cfg->use_gles &&
                ctx->outputs[i].name == TGSI_SEMANTIC_COLOR &&
                !ctx->cfg->has_dual_src_blend)
               sprintf(prefix, "layout(location = %d)", ctx->outputs[i].sid);

            emit_ios_generic(ctx, glsl_strbufs, generic_ios, texcoord_ios, io_out, prefix, &ctx->outputs[i],
                              ctx->outputs[i].fbfetch_used ? "inout" : "out", "");

         } else if (ctx->outputs[i].invariant || ctx->outputs[i].precise) {
            emit_hdrf(glsl_strbufs, "%s%s;\n",
                      ctx->outputs[i].precise ? "precise " :
                      (ctx->outputs[i].invariant ? "invariant " : ""),
                      ctx->outputs[i].glsl_name);
         }
      }
   }

   if (ctx->fs_depth_layout) {
      const char *depth_layout = get_depth_layout(ctx->fs_depth_layout);
      if (depth_layout)
         emit_hdrf(glsl_strbufs, "layout (%s) out float gl_FragDepth;\n", depth_layout);
   }

   if (ctx->num_in_clip_dist) {
      if (ctx->key->num_in_clip) {
         emit_hdrf(glsl_strbufs, "in float gl_ClipDistance[%d];\n", ctx->key->num_in_clip);
      } else if (ctx->num_in_clip_dist > 4 && !ctx->key->num_in_cull) {
         emit_hdrf(glsl_strbufs, "in float gl_ClipDistance[%d];\n", ctx->num_in_clip_dist);
      }

      if (ctx->key->num_in_cull) {
         emit_hdrf(glsl_strbufs, "in float gl_CullDistance[%d];\n", ctx->key->num_in_cull);
      }
      if(ctx->fs_uses_clipdist_input)
         emit_hdr(glsl_strbufs, "vec4 clip_dist_temp[2];\n");
   }
}

static bool
can_emit_generic_geom(const struct vrend_shader_io *io)
{
   return io->stream == 0;
}

static void emit_ios_per_vertex_in(const struct dump_ctx *ctx,
                                   struct vrend_glsl_strbufs *glsl_strbufs,
                                   bool *has_pervertex)
{
   char clip_var[64] = "";
   char cull_var[64] = "";

   if (ctx->num_in_clip_dist) {
      int clip_dist, cull_dist;

      clip_dist = ctx->key->num_in_clip;
      cull_dist = ctx->key->num_in_cull;

      int num_clip_cull = clip_dist + cull_dist;
      if (ctx->num_in_clip_dist && !num_clip_cull)
         clip_dist = ctx->num_in_clip_dist;

      if (clip_dist)
         snprintf(clip_var, 64, "float gl_ClipDistance[%d];\n", clip_dist);
      if (cull_dist)
         snprintf(cull_var, 64, "float gl_CullDistance[%d];\n", cull_dist);

      (*has_pervertex) = true;
      emit_hdrf(glsl_strbufs, "in gl_PerVertex {\n vec4 gl_Position; \n %s%s%s\n} gl_in[];\n",
                clip_var, cull_var, ctx->has_pointsize_input ? "float gl_PointSize;\n" : "");

   }

}


static void emit_ios_per_vertex_out(const struct dump_ctx *ctx,
                                    struct vrend_glsl_strbufs *glsl_strbufs, const char *instance_var)
{
   int clip_dist = ctx->num_clip_dist_prop ? ctx->num_clip_dist_prop : ctx->key->num_out_clip;
   int cull_dist = ctx->num_cull_dist_prop ? ctx->num_cull_dist_prop : ctx->key->num_out_cull;
   int num_clip_cull = clip_dist + cull_dist;

   if (ctx->num_out_clip_dist && !num_clip_cull)
      clip_dist = ctx->num_out_clip_dist;

   if (ctx->key->use_pervertex_in) {
      char clip_var[64] = "", cull_var[64] = "";
      if (cull_dist)
         snprintf(cull_var, 64, "float gl_CullDistance[%d];\n", cull_dist);

      if (clip_dist)
         snprintf(clip_var, 64, "float gl_ClipDistance[%d];\n", clip_dist);

      emit_hdrf(glsl_strbufs, "out gl_PerVertex {\n vec4 gl_Position; \n %s%s%s\n} %s;\n",
                clip_var, cull_var,
                ctx->has_pointsize_output ? "float gl_PointSize;\n" : "",
                instance_var);
   }

   if (clip_dist + cull_dist > 0)
      emit_hdr(glsl_strbufs, "vec4 clip_dist_temp[2];\n");

}

static void emit_ios_geom(const struct dump_ctx *ctx,
                          struct vrend_glsl_strbufs *glsl_strbufs,
                          struct vrend_generic_ios *generic_ios,
                          struct vrend_texcoord_ios *texcoord_ios,
                          uint8_t front_back_color_emitted_flags[64],
                          uint64_t *interp_input_mask,
                          bool *has_pervertex,
                          bool *force_color_two_side)
{
   uint32_t i;
   char invocbuf[25];

   if (ctx->gs_num_invocations)
      snprintf(invocbuf, 25, ", invocations = %d", ctx->gs_num_invocations);

   emit_hdrf(glsl_strbufs, "layout(%s%s) in;\n", prim_to_name(ctx->gs_in_prim),
             ctx->gs_num_invocations > 1 ? invocbuf : "");
   emit_hdrf(glsl_strbufs, "layout(%s, max_vertices = %d) out;\n", prim_to_name(ctx->gs_out_prim), ctx->gs_max_out_verts);


   for (i = 0; i < ctx->num_inputs; i++) {
      if (!ctx->inputs[i].glsl_predefined_no_emit) {
         char postfix[64];
         snprintf(postfix, sizeof(postfix), "[%d]", gs_input_prim_to_size(ctx->gs_in_prim));
         emit_ios_generic(ctx, glsl_strbufs, generic_ios, texcoord_ios,
                          io_in, "", &ctx->inputs[i], "in", postfix);
      }
   }

   for (i = 0; i < ctx->num_outputs; i++) {
      if (!ctx->outputs[i].glsl_predefined_no_emit) {
         if (!ctx->outputs[i].stream)
            continue;

         const char *prefix = "";
         if (ctx->outputs[i].name == TGSI_SEMANTIC_GENERIC ||
             ctx->outputs[i].name == TGSI_SEMANTIC_COLOR ||
             ctx->outputs[i].name == TGSI_SEMANTIC_BCOLOR) {
            *interp_input_mask |= UINT64_C(1) << i;
         }

         emit_hdrf(glsl_strbufs, "layout (stream = %d) %s%s%sout vec4 %s;\n", ctx->outputs[i].stream, prefix,
                   ctx->outputs[i].precise ? "precise " : "",
                   ctx->outputs[i].invariant ? "invariant " : "",
                   ctx->outputs[i].glsl_name);
      }
   }

   emit_ios_indirect_generics_output(ctx, glsl_strbufs, "");

   emit_ios_generic_outputs(ctx, glsl_strbufs, generic_ios, texcoord_ios,
                            front_back_color_emitted_flags, force_color_two_side,
                            can_emit_generic_geom);
   if (strbuf_get_error(&glsl_strbufs->glsl_main))
      return;

   emit_ios_per_vertex_in(ctx, glsl_strbufs, has_pervertex);

   if (ctx->has_clipvertex) {
      emit_hdrf(glsl_strbufs, "%svec4 clipv_tmp;\n", ctx->has_clipvertex_so ? "out " : "");
   }

   if (ctx->num_out_clip_dist) {
      bool has_clip_or_cull_prop = ctx->num_clip_dist_prop + ctx->num_cull_dist_prop > 0;

      int num_clip_dists = has_clip_or_cull_prop ? ctx->num_clip_dist_prop :
                                                   (ctx->num_out_clip_dist ? ctx->num_out_clip_dist : 8);
      int num_cull_dists = has_clip_or_cull_prop ? ctx->num_cull_dist_prop : 0;

      char cull_buf[64] = "";
      char clip_buf[64] = "";

      if (num_clip_dists)
         snprintf(clip_buf, 64, "out float gl_ClipDistance[%d];\n", num_clip_dists);
      if (num_cull_dists)
         snprintf(cull_buf, 64, "out float gl_CullDistance[%d];\n", num_cull_dists);

      emit_hdrf(glsl_strbufs, "%s%s\n", clip_buf, cull_buf);
      emit_hdrf(glsl_strbufs, "vec4 clip_dist_temp[2];\n");
   }
}


static void emit_ios_tcs(const struct dump_ctx *ctx,
                         struct vrend_glsl_strbufs *glsl_strbufs,
                         struct vrend_generic_ios *generic_ios,
                         struct vrend_texcoord_ios *texcoord_ios,
                         uint64_t *emitted_out_patches_mask,
                         bool *has_pervertex)
{
   uint32_t i;

   emit_ios_indirect_generics_input(ctx, glsl_strbufs, "[]");

   for (i = 0; i < ctx->num_inputs; i++) {
      if (!ctx->inputs[i].glsl_predefined_no_emit) {
         if (ctx->inputs[i].name == TGSI_SEMANTIC_PATCH) {
            emit_ios_patch(glsl_strbufs, "",  &ctx->inputs[i], "in",
                           ctx->inputs[i].last - ctx->inputs[i].first + 1,
                           ctx->separable_program);
         } else
            emit_ios_generic(ctx, glsl_strbufs, generic_ios, texcoord_ios, io_in, "", &ctx->inputs[i], "in", "[]");
      }
   }

   uint64_t emitted_patches = 0;

   emit_hdrf(glsl_strbufs, "layout(vertices = %d) out;\n", ctx->tcs_vertices_out);

   if (ctx->patch_ios.output_range.used)
      emitted_patches |= emit_ios_patch(glsl_strbufs, "patch", &ctx->patch_ios.output_range.io, "out",
                                        ctx->patch_ios.output_range.io.last - ctx->patch_ios.output_range.io.first + 1,
                                        ctx->separable_program);

   for (i = 0; i < ctx->num_outputs; i++) {
      if (!ctx->outputs[i].glsl_predefined_no_emit) {
         if (ctx->outputs[i].name == TGSI_SEMANTIC_PATCH) {

            emitted_patches |= emit_ios_patch(glsl_strbufs, "patch", &ctx->outputs[i], "out",
                                              ctx->outputs[i].last - ctx->outputs[i].first + 1,
                                              ctx->separable_program);
         } else
            emit_ios_generic(ctx, glsl_strbufs, generic_ios, texcoord_ios, io_out, "", &ctx->outputs[i], "out", "[]");
      } else if (ctx->outputs[i].invariant || ctx->outputs[i].precise) {
         emit_hdrf(glsl_strbufs, "%s%s;\n",
                   ctx->outputs[i].precise ? "precise " :
                   (ctx->outputs[i].invariant ? "invariant " : ""),
                   ctx->outputs[i].glsl_name);
      }
   }

   emit_ios_per_vertex_in(ctx, glsl_strbufs, has_pervertex);
   emit_ios_per_vertex_out(ctx, glsl_strbufs, " gl_out[]");

   *emitted_out_patches_mask = emitted_patches;
}

static void emit_ios_tes(const struct dump_ctx *ctx,
                         struct vrend_glsl_strbufs *glsl_strbufs,
                         struct vrend_generic_ios *generic_ios,
                         struct vrend_texcoord_ios *texcoord_ios,
                         uint8_t front_back_color_emitted_flags[64],
                         bool *has_pervertex,
                         bool *force_color_two_side)
{
   uint32_t i;

   if (ctx->patch_ios.input_range.used)
      emit_ios_patch(glsl_strbufs, "patch", &ctx->patch_ios.input_range.io, "in",
                     ctx->patch_ios.input_range.io.last -
                        ctx->patch_ios.input_range.io.first + 1,
                     ctx->separable_program);

   if (generic_ios->input_range.used)
      emit_ios_indirect_generics_input(ctx, glsl_strbufs, "[]");

   for (i = 0; i < ctx->num_inputs; i++) {
      if (!ctx->inputs[i].glsl_predefined_no_emit) {
         if (ctx->inputs[i].name == TGSI_SEMANTIC_PATCH)
            emit_ios_patch(glsl_strbufs, "patch", &ctx->inputs[i], "in",
                           ctx->inputs[i].last - ctx->inputs[i].first + 1,
                           ctx->separable_program);
         else
            emit_ios_generic(ctx, glsl_strbufs, generic_ios, texcoord_ios, io_in, "", &ctx->inputs[i], "in", "[]");
      }
   }

   emit_hdrf(glsl_strbufs, "layout(%s, %s, %s%s) in;\n",
             prim_to_tes_name(ctx->tes_prim_mode),
             get_spacing_string(ctx->tes_spacing),
             ctx->tes_vertex_order ? "cw" : "ccw",
             ctx->tes_point_mode ? ", point_mode" : "");

   emit_ios_indirect_generics_output(ctx, glsl_strbufs, "");

   emit_ios_generic_outputs(ctx, glsl_strbufs, generic_ios, texcoord_ios,
                            front_back_color_emitted_flags, force_color_two_side,
                            can_emit_generic_default);
   if (strbuf_get_error(&glsl_strbufs->glsl_main))
      return;

   emit_ios_per_vertex_in(ctx, glsl_strbufs, has_pervertex);
   emit_ios_per_vertex_out(ctx, glsl_strbufs, "");

   if (ctx->has_clipvertex && !ctx->key->gs_present) {
      emit_hdrf(glsl_strbufs, "%svec4 clipv_tmp;\n", ctx->has_clipvertex_so ? "out " : "");
   }

}


static void emit_ios_cs(const struct dump_ctx *ctx,
                        struct vrend_glsl_strbufs *glsl_strbufs)
{
   emit_hdrf(glsl_strbufs, "layout (local_size_x = %d, local_size_y = %d, local_size_z = %d) in;\n",
             ctx->local_cs_block_size[0], ctx->local_cs_block_size[1], ctx->local_cs_block_size[2]);

   if (ctx->req_local_mem) {
      enum vrend_type_qualifier type = ctx->integer_memory ? INT : UINT;
      emit_hdrf(glsl_strbufs, "shared %s values[%d];\n", get_string(type), ctx->req_local_mem / 4);
   }
}

static void emit_interp_info(struct vrend_glsl_strbufs *glsl_strbufs,
                             const struct vrend_shader_cfg *cfg,
                             const struct vrend_fs_shader_info *fs_info,
                             enum tgsi_semantic semantic, int sid, bool flatshade)
{
   for (int j = 0; j < fs_info->num_interps; ++j) {
      if (fs_info->interpinfo[j].semantic_name == semantic &&
          fs_info->interpinfo[j].semantic_index == sid) {
         emit_hdrf(glsl_strbufs, "%s %s ",
                   get_interp_string(cfg, fs_info->interpinfo[j].interpolate, flatshade),
                   get_aux_string(fs_info->interpinfo[j].location));
         break;
      }
   }
}

struct sematic_info {
   enum tgsi_semantic name;
   const char prefix;
};

static void emit_match_interfaces(struct vrend_glsl_strbufs *glsl_strbufs,
                                  const struct dump_ctx *ctx,
                                  const struct vrend_interface_bits *match,
                                  const struct sematic_info *semantic)
{
   uint64_t mask = (match->outputs_expected_mask | match->outputs_emitted_mask)
                   ^ match->outputs_emitted_mask;

   while (mask) {
      int i = u_bit_scan64(&mask);
      emit_interp_info(glsl_strbufs, ctx->cfg, &ctx->key->fs_info,
                       semantic->name, i, ctx->key->flatshade);

      if (semantic->name == TGSI_SEMANTIC_GENERIC && ctx->separable_program)
          emit_hdrf(glsl_strbufs, "layout(location=%d) ", i);

      emit_hdrf(glsl_strbufs, "out vec4 %s_%c%d%s;\n",
                get_stage_output_name_prefix(ctx->prog_type),
                semantic->prefix, i,
                ctx->prog_type == TGSI_PROCESSOR_TESS_CTRL ? "[]" : "");
   }
}

static int emit_ios(const struct dump_ctx *ctx,
                    struct vrend_glsl_strbufs *glsl_strbufs,
                    struct vrend_generic_ios *generic_ios,
                    struct vrend_texcoord_ios *texcoord_ios,
                    uint64_t *patches_emitted_mask,
                    uint8_t front_back_color_emitted_flags[64],
                    uint64_t *interp_input_mask,
                    bool *has_pervertex,
                    bool *force_color_two_side,
                    uint32_t *shadow_samp_mask)
{
   *interp_input_mask = 0;
   int glsl_ver_required = ctx->glsl_ver_required;

   if (ctx->so && ctx->so->num_outputs >= PIPE_MAX_SO_OUTPUTS) {
      virgl_error("Num outputs exceeded, max is %u\n", PIPE_MAX_SO_OUTPUTS);
      set_hdr_error(glsl_strbufs);
      return glsl_ver_required;
   }

   switch (ctx->prog_type) {
   case TGSI_PROCESSOR_VERTEX:
      emit_ios_vs(ctx, glsl_strbufs, generic_ios, texcoord_ios, front_back_color_emitted_flags, force_color_two_side);
      break;
   case TGSI_PROCESSOR_FRAGMENT:
      emit_ios_fs(ctx, glsl_strbufs, generic_ios, texcoord_ios, interp_input_mask);
      break;
   case TGSI_PROCESSOR_GEOMETRY:
      emit_ios_geom(ctx, glsl_strbufs, generic_ios, texcoord_ios, front_back_color_emitted_flags, interp_input_mask, has_pervertex, force_color_two_side);
      break;
   case TGSI_PROCESSOR_TESS_CTRL:
      emit_ios_tcs(ctx, glsl_strbufs, generic_ios, texcoord_ios, patches_emitted_mask, has_pervertex);
      break;
   case TGSI_PROCESSOR_TESS_EVAL:
      emit_ios_tes(ctx, glsl_strbufs, generic_ios, texcoord_ios, front_back_color_emitted_flags, has_pervertex, force_color_two_side);
      break;
   case TGSI_PROCESSOR_COMPUTE:
      emit_ios_cs(ctx, glsl_strbufs);
      break;
   default:
      virgl_error("Unknown shader processor %d\n", ctx->prog_type);
      set_hdr_error(glsl_strbufs);
      return glsl_ver_required;
   }

   if (strbuf_get_error(&glsl_strbufs->glsl_main))
      return glsl_ver_required;

   const struct sematic_info generic = {TGSI_SEMANTIC_GENERIC, 'g'};
   const struct sematic_info texcoord = {TGSI_SEMANTIC_TEXCOORD, 't'};

   emit_match_interfaces(glsl_strbufs, ctx, &generic_ios->match, &generic);
   emit_match_interfaces(glsl_strbufs, ctx, &texcoord_ios->match, &texcoord);

   emit_ios_streamout(ctx, glsl_strbufs);
   glsl_ver_required = emit_ios_common(ctx, glsl_strbufs, shadow_samp_mask);

   if (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT &&
       ctx->key->pstipple_enabled) {
      emit_hdr(glsl_strbufs, "uint stip_temp;\n");
   }

   return glsl_ver_required;
}

static bool fill_fragment_interpolants(const struct dump_ctx *ctx, struct vrend_fs_shader_info *fs_info)
{
   uint32_t i, index = 0;

   for (i = 0; i < ctx->num_inputs; i++) {
      if (!(ctx->interp_input_mask & (1 << i)))
         continue;

      fs_info->interpinfo[index].semantic_name = ctx->inputs[i].name;
      fs_info->interpinfo[index].semantic_index = ctx->inputs[i].sid;
      fs_info->interpinfo[index].interpolate = ctx->inputs[i].interpolate;
      fs_info->interpinfo[index].location = ctx->inputs[i].location;
      index++;
   }
   return true;
}

static bool fill_interpolants(const struct dump_ctx *ctx, struct vrend_variable_shader_info *sinfo)
{
   if (!ctx->interp_input_mask)
      return true;
   if (ctx->prog_type != TGSI_PROCESSOR_FRAGMENT)
      return true;

   return fill_fragment_interpolants(ctx, &sinfo->fs_info);
}

static bool analyze_instruction(struct tgsi_iterate_context *iter,
                                   struct tgsi_full_instruction *inst)
{
   struct dump_ctx *ctx = (struct dump_ctx *)iter;
   uint32_t opcode = inst->Instruction.Opcode;
   if (opcode == TGSI_OPCODE_ATOMIMIN || opcode == TGSI_OPCODE_ATOMIMAX) {
       const struct tgsi_full_src_register *src = &inst->Src[0];
       if (src->Register.File == TGSI_FILE_BUFFER)
         ctx->ssbo_integer_mask |= 1 << src->Register.Index;
       if (src->Register.File == TGSI_FILE_MEMORY)
         ctx->integer_memory = true;
   }

   if (!ctx->fs_uses_clipdist_input && (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT)) {
      for (int i = 0; i < inst->Instruction.NumSrcRegs; ++i) {
         if (inst->Src[i].Register.File == TGSI_FILE_INPUT) {
            int idx = inst->Src[i].Register.Index;
            for (uint32_t j = 0; j < ctx->num_inputs; ++j) {
               if (ctx->inputs[j].first <= idx && ctx->inputs[j].last >= idx &&
                   ctx->inputs[j].name == TGSI_SEMANTIC_CLIPDIST) {
                  ctx->fs_uses_clipdist_input = true;
                  break;
               }
            }
         }
      }
   }


   return true;
}

static void fill_var_sinfo(const struct dump_ctx *ctx, struct vrend_variable_shader_info *sinfo)
{
   sinfo->num_ucp = ctx->is_last_vertex_stage ? VIRGL_NUM_CLIP_PLANES : 0;
   sinfo->fs_info.has_sample_input = ctx->has_sample_input;
   sinfo->fs_info.has_noperspective = ctx->has_noperspective;
   sinfo->fs_info.num_interps = util_bitcount(ctx->interp_input_mask);
   sinfo->fs_info.glsl_ver = ctx->glsl_ver_required;
   bool has_prop = (ctx->num_clip_dist_prop + ctx->num_cull_dist_prop) > 0;

   sinfo->num_in_clip = has_prop ? ctx->num_clip_dist_prop : ctx->key->num_in_clip;
   sinfo->num_in_cull = has_prop ? ctx->num_cull_dist_prop : ctx->key->num_in_cull;
   sinfo->num_out_clip = has_prop ? ctx->num_clip_dist_prop : ctx->key->num_out_clip;
   sinfo->num_out_cull = has_prop ? ctx->num_cull_dist_prop : ctx->key->num_out_cull;
   sinfo->legacy_color_bits = ctx->color_out_mask;
}

static void fill_sinfo(const struct dump_ctx *ctx, struct vrend_shader_info *sinfo)
{
   sinfo->use_pervertex_in = ctx->has_pervertex;
   sinfo->samplers_used_mask = ctx->samplers_used;
   sinfo->images_used_mask = ctx->images_used_mask;
   sinfo->image_binding_offset = ctx->key->image_binding_offset;
   sinfo->image_last_binding = ctx->key->image_binding_offset + ctx->image_last_binding;
   sinfo->num_consts = ctx->num_consts;
   sinfo->ubo_used_mask = ctx->ubo_used_mask;
   sinfo->fog_input_mask = ctx->fog_input_mask;
   sinfo->fog_output_mask = ctx->fog_output_mask;

   sinfo->ssbo_used_mask = ctx->ssbo_used_mask >> (ctx->ssbo_first_binding != UINT32_MAX ? ctx->ssbo_first_binding : 0);
   sinfo->ssbo_binding_offset  = ctx->key->ssbo_binding_offset;

   sinfo->ssbo_last_binding = ctx->key->ssbo_binding_offset + ctx->ssbo_last_binding -
                              (ctx->ssbo_first_binding != UINT32_MAX ? ctx->ssbo_first_binding : 0);

   sinfo->ubo_indirect = !!(ctx->info.dimension_indirect_files & (1 << TGSI_FILE_CONSTANT));

   sinfo->has_output_arrays = ctx->has_output_arrays;
   sinfo->has_input_arrays = ctx->has_input_arrays;

   sinfo->out_generic_emitted_mask = ctx->generic_ios.match.outputs_emitted_mask;
   sinfo->out_texcoord_emitted_mask = ctx->texcoord_ios.match.outputs_emitted_mask;
   sinfo->out_patch_emitted_mask = ctx->patches_emitted_mask;

   sinfo->num_inputs = ctx->num_inputs;
   sinfo->num_outputs = ctx->num_outputs;
   sinfo->shadow_samp_mask = ctx->shadow_samp_mask;
   sinfo->gs_out_prim = ctx->gs_out_prim;
   sinfo->tes_prim = ctx->tes_prim_mode;
   sinfo->tes_point_mode = ctx->tes_point_mode;
   sinfo->fs_blend_equation_advanced = ctx->fs_blend_equation_advanced;
   sinfo->separable_program = ctx->separable_program;
   sinfo->reads_drawid = !!(ctx->glsl_strbufs.required_sysval_uniform_decls & BIT(UNIFORM_DRAWID_BASE));

   if (sinfo->so_names || ctx->so_names) {
      if (sinfo->so_names) {
         for (uint32_t i = 0; i < sinfo->so_info.num_outputs; ++i)
            free(sinfo->so_names[i]);
         free(sinfo->so_names);
      }
   }

   /* Record information about the layout of generics and patches for apssing it
    * to the next shader stage. mesa/tgsi doesn't provide this information for
    * TCS, TES, and GEOM shaders.
    */
   for(uint32_t i = 0; i < ctx->num_outputs; i++) {
      if (ctx->prog_type == TGSI_PROCESSOR_FRAGMENT) {
         if (ctx->outputs[i].name == TGSI_SEMANTIC_COLOR)
            sinfo->fs_output_layout[i] = ctx->outputs[i].sid;
         else
            sinfo->fs_output_layout[i] = -1;
      }
   }

   sinfo->so_names = ctx->so_names;
   sinfo->attrib_input_mask = ctx->attrib_input_mask;
   if (sinfo->sampler_arrays)
      free(sinfo->sampler_arrays);
   sinfo->sampler_arrays = ctx->sampler_arrays;
   sinfo->num_sampler_arrays = ctx->num_sampler_arrays;
   if (sinfo->image_arrays)
      free(sinfo->image_arrays);
   sinfo->image_arrays = ctx->image_arrays;
   sinfo->num_image_arrays = ctx->num_image_arrays;
   sinfo->in_generic_emitted_mask = ctx->generic_ios.match.inputs_emitted_mask;
   sinfo->in_texcoord_emitted_mask = ctx->texcoord_ios.match.inputs_emitted_mask;

   for (uint32_t i = 0; i < ctx->num_outputs; ++i) {
      if (ctx->outputs[i].invariant) {
         uint32_t bit_pos = varying_bit_from_semantic_and_index(ctx->outputs[i].name, ctx->outputs[i].sid);
         uint32_t slot = bit_pos / 32;
         uint32_t bit = 1u << (bit_pos & 0x1f);
         sinfo->invariant_outputs[slot] |= bit;
      }
   }
   sinfo->gles_use_tex_query_level = ctx->gles_use_tex_query_level;

   if (ctx->guest_sent_io_arrays) {
      sinfo->output_arrays.num_arrays = 0;
      for (uint32_t i = 0; i < ctx->num_outputs; ++i) {
         const struct vrend_shader_io *io = &ctx->outputs[i];
         if (io->array_id  > 0) {
            struct vrend_shader_io_array *array =
                  &sinfo->output_arrays.layout[sinfo->output_arrays.num_arrays];
            array->sid = io->sid;
            array->size = io->last - io->first;
            array->name = io->name;
            array->array_id = io->array_id;
            ++sinfo->output_arrays.num_arrays;
         }
      }
   }
}

static bool allocate_strbuffers(struct vrend_glsl_strbufs* glsl_strbufs)
{
   if (!strbuf_alloc(&glsl_strbufs->glsl_main, 4096))
      return false;

   if (strbuf_get_error(&glsl_strbufs->glsl_main))
      return false;

   if (!strbuf_alloc(&glsl_strbufs->glsl_hdr, 1024))
      return false;

   if (!strbuf_alloc(&glsl_strbufs->glsl_ver_ext, 1024))
      return false;

   return true;
}

static void set_strbuffers(const struct vrend_glsl_strbufs* glsl_strbufs,
                           struct vrend_strarray *shader)
{
   strarray_addstrbuf(shader, &glsl_strbufs->glsl_ver_ext);
   strarray_addstrbuf(shader, &glsl_strbufs->glsl_hdr);
   strarray_addstrbuf(shader, &glsl_strbufs->glsl_main);
}

static void emit_required_sysval_uniforms(struct vrend_strbuf *block, uint32_t mask)
{
   if (!mask)
      return;

   strbuf_append(block, "layout (std140) uniform VirglBlock {\n");
   strbuf_append(block, "\tvec4 clipp[8];\n");
   strbuf_appendf(block, "\tuint stipple_pattern[%d];\n", VREND_POLYGON_STIPPLE_SIZE);
   strbuf_append(block, "\tfloat winsys_adjust_y;\n");
   strbuf_append(block, "\tfloat alpha_ref_val;\n");
   strbuf_append(block, "\tbool clip_plane_enabled;\n");
   strbuf_append(block, "\tint drawid_base;\n");
   strbuf_append(block, "};\n");

}

static int compare_sid(const void *lhs, const void *rhs)
{
   const struct vrend_shader_io *l = (struct vrend_shader_io *)lhs;
   const struct vrend_shader_io *r = (struct vrend_shader_io *)rhs;

   if (l->name != r->name)
      return l->name - r->name;

   return l->sid - r->sid;
}

struct sso_scan_ctx {
   struct tgsi_iterate_context iter;
   const struct vrend_shader_cfg *cfg;
   uint8_t max_generic_in_sid;
   uint8_t max_patch_in_sid;
   uint8_t max_generic_out_sid;
   uint8_t max_patch_out_sid;
   bool separable_program;
   bool unsupported_io;
};

static bool
iter_prop_for_separable(struct tgsi_iterate_context *iter,
          struct tgsi_full_property *prop)
{
   struct sso_scan_ctx *ctx = (struct sso_scan_ctx *) iter;

   if (prop->Property.PropertyName == TGSI_PROPERTY_SEPARABLE_PROGRAM)
      ctx->separable_program = prop->u[0].Data != 0;
   return true;
}

static bool
iter_decl_for_overlap(struct tgsi_iterate_context *iter,
                      struct tgsi_full_declaration *decl)
{
   struct sso_scan_ctx *ctx = (struct sso_scan_ctx *) iter;

   /* VS inputs and FS outputs are of no interest
    * when it comes to IO matching */
   if (decl->Declaration.File == TGSI_FILE_INPUT &&
       iter->processor.Processor == TGSI_PROCESSOR_VERTEX)
      return true;

   if (decl->Declaration.File == TGSI_FILE_OUTPUT &&
       iter->processor.Processor == TGSI_PROCESSOR_FRAGMENT)
      return true;

   switch (decl->Semantic.Name) {
   case TGSI_SEMANTIC_PATCH:
      if (decl->Declaration.File == TGSI_FILE_INPUT) {
         if (ctx->max_patch_in_sid < decl->Semantic.Index)
            ctx->max_patch_in_sid = decl->Semantic.Index;
      } else {
         if (ctx->max_patch_out_sid < decl->Semantic.Index)
            ctx->max_patch_out_sid = decl->Semantic.Index;
      }
      break;
   case TGSI_SEMANTIC_GENERIC:
      if (decl->Declaration.File == TGSI_FILE_INPUT) {
         if (ctx->max_generic_in_sid < decl->Semantic.Index)
            ctx->max_generic_in_sid = decl->Semantic.Index;
      } else {
         if (ctx->max_generic_out_sid < decl->Semantic.Index)
            ctx->max_generic_out_sid = decl->Semantic.Index;
      }
      break;
   case TGSI_SEMANTIC_COLOR:
   case TGSI_SEMANTIC_CLIPVERTEX:
   case TGSI_SEMANTIC_BCOLOR:
   case TGSI_SEMANTIC_TEXCOORD:
   case TGSI_SEMANTIC_FOG:
      /* These are semantics that need to be matched by name and since we can't
       * guarantee that they exist in all the stages of separable shaders
       * we can't emit the shader as SSO */
      ctx->unsupported_io = true;
      break;
   default:
      ;
   }
   return true;
}


bool vrend_shader_query_separable_program(const struct tgsi_token *tokens,
                                          const struct vrend_shader_cfg *cfg)
{
   struct sso_scan_ctx ctx = {0};
   ctx.cfg = cfg;
   ctx.iter.iterate_property = iter_prop_for_separable;
   ctx.iter.iterate_declaration = iter_decl_for_overlap;
   tgsi_iterate_shader(tokens, &ctx.iter);

   /* Since we have to match by location, and have to handle generics and patches
    * at in the limited range of 32 locations, we have to make sure that the
    * the generics range and the patch range don't overlap. In addition, to
    * work around that radeonsi doesn't support patch locations above 30 we have
    * to check that limit too. */
   bool supports_separable = !ctx.unsupported_io &&
                             (ctx.max_generic_in_sid + ctx.max_patch_in_sid < MAX_VARYING) &&
                             (ctx.max_generic_out_sid + ctx.max_patch_out_sid < MAX_VARYING) &&
                             (ctx.max_patch_in_sid < ctx.cfg->max_shader_patch_varyings) &&
                             (ctx.max_patch_out_sid < ctx.cfg->max_shader_patch_varyings);
   return ctx.separable_program && supports_separable;
}

bool vrend_convert_shader(const struct vrend_context *rctx,
                          const struct vrend_shader_cfg *cfg,
                          const struct tgsi_token *tokens,
                          uint32_t req_local_mem,
                          const struct vrend_shader_key *key,
                          struct vrend_shader_info *sinfo,
                          struct vrend_variable_shader_info *var_sinfo,
                          struct vrend_strarray *shader)
{
   struct dump_ctx ctx;
   bool bret;

   memset(&ctx, 0, sizeof(struct dump_ctx));
   ctx.cfg = cfg;

   /* First pass to deal with edge cases. */
   ctx.iter.iterate_declaration = iter_decls;
   ctx.iter.iterate_instruction = analyze_instruction;
   ctx.ssbo_first_binding = UINT32_MAX;
   bret = tgsi_iterate_shader(tokens, &ctx.iter);
   if (bret == false)
      return false;

   ctx.is_last_vertex_stage =
         (ctx.iter.processor.Processor == TGSI_PROCESSOR_GEOMETRY) ||
         (ctx.iter.processor.Processor == TGSI_PROCESSOR_TESS_EVAL && !key->gs_present) ||
         (ctx.iter.processor.Processor == TGSI_PROCESSOR_VERTEX &&  !key->gs_present && !key->tes_present);

   ctx.num_inputs = 0;
   ctx.iter.prolog = prolog;
   ctx.iter.iterate_instruction = iter_instruction;
   ctx.iter.iterate_declaration = iter_declaration;
   ctx.iter.iterate_immediate = iter_immediate;
   ctx.iter.iterate_property = iter_property;
   ctx.iter.epilog = NULL;
   ctx.key = key;
   ctx.cfg = cfg;
   ctx.prog_type = -1;
   ctx.num_image_arrays = 0;
   ctx.image_arrays = NULL;
   ctx.num_sampler_arrays = 0;
   ctx.sampler_arrays = NULL;
   ctx.ssbo_array_base = 0xffffffff;
   ctx.ssbo_atomic_array_base = 0xffffffff;

   /* If we don't have a SSBO (or an image) the offset in the next shader will
    * be "last_binding + 1" which nicely becomes 0, so we don't have to do extra
    * checks to seem whether there were any SSBOs (images) declared in the shader */
   ctx.ssbo_last_binding = -1;
   ctx.image_last_binding = -1;

   ctx.has_sample_input = false;
   ctx.req_local_mem = req_local_mem;
   ctx.guest_sent_io_arrays = false;
   ctx.generic_ios.match.outputs_expected_mask = key->out_generic_expected_mask;
   ctx.texcoord_ios.match.outputs_expected_mask = key->out_texcoord_expected_mask;

   if (!tgsi_scan_shader(tokens, &ctx.info))
      goto fail;

   /* if we are in core profile mode we should use GLSL 1.40 */
   if (cfg->glsl_version >= 140)
      ctx.glsl_ver_required = require_glsl_ver(&ctx, 140);

   if (ctx.iter.processor.Processor == TGSI_PROCESSOR_GEOMETRY ||
       key->gs_present)
      ctx.glsl_ver_required = require_glsl_ver(&ctx, 140);

   if (ctx.iter.processor.Processor == TGSI_PROCESSOR_TESS_EVAL ||
       ctx.iter.processor.Processor == TGSI_PROCESSOR_TESS_CTRL ||
       key->tes_present || key->tcs_present)
         ctx.glsl_ver_required = require_glsl_ver(&ctx, 150);

   if (sinfo->so_info.num_outputs) {
      ctx.so = &sinfo->so_info;
      ctx.so_names = calloc(sinfo->so_info.num_outputs, sizeof(char *));
      if (!ctx.so_names)
         goto fail;
   } else
      ctx.so_names = NULL;

   if (ctx.info.dimension_indirect_files & (1 << TGSI_FILE_CONSTANT))
      ctx.glsl_ver_required = require_glsl_ver(&ctx, 150);

   if (ctx.info.indirect_files & (1 << TGSI_FILE_BUFFER) ||
       ctx.info.indirect_files & (1 << TGSI_FILE_IMAGE)) {
      ctx.glsl_ver_required = require_glsl_ver(&ctx, 150);
      ctx.shader_req_bits |= SHADER_REQ_GPU_SHADER5;
   }
   if (ctx.info.indirect_files & (1 << TGSI_FILE_SAMPLER))
      ctx.shader_req_bits |= SHADER_REQ_GPU_SHADER5;

   if (!allocate_strbuffers(&ctx.glsl_strbufs))
      goto fail;

   bret = tgsi_iterate_shader(tokens, &ctx.iter);
   if (bret == false)
      goto fail;

   if (ctx.shader_req_bits & SHADER_REQ_FP64)
      ctx.glsl_ver_required = require_glsl_ver(&ctx, 150);

   /* If we need a sysvalue UBO then we require GLSL 1.40 */
   if (ctx.glsl_strbufs.required_sysval_uniform_decls)
      ctx.glsl_ver_required = require_glsl_ver(&ctx, 140);

   if (!ctx.cfg->use_gles &&
      ( key->in_arrays.num_arrays > 0 ) &&
       (ctx.prog_type == TGSI_PROCESSOR_GEOMETRY ||
        ctx.prog_type == TGSI_PROCESSOR_TESS_CTRL ||
        ctx.prog_type == TGSI_PROCESSOR_TESS_EVAL)) {
      ctx.shader_req_bits |= SHADER_REQ_ARRAYS_OF_ARRAYS;
   }

   for (size_t i = 0; i < ARRAY_SIZE(ctx.src_bufs); ++i)
      strbuf_free(ctx.src_bufs + i);

   for (size_t i = 0; i < ARRAY_SIZE(ctx.dst_bufs); ++i)
      strbuf_free(ctx.dst_bufs + i);

   if (ctx.prog_type == TGSI_PROCESSOR_FRAGMENT)
      qsort(ctx.outputs, ctx.num_outputs, sizeof(struct vrend_shader_io), compare_sid);

   const struct vrend_fs_shader_info *fs_info = &key->fs_info;

   if (fs_info->num_interps && fs_info->has_sample_input &&
       ((cfg->use_gles && cfg->glsl_version < 320) ||
        cfg->glsl_version >= 320)) {
      ctx.shader_req_bits |= SHADER_REQ_GPU_SHADER5;
   }

   if (fs_info->num_interps && fs_info->has_noperspective &&
       cfg->use_gles) {
      ctx.shader_req_bits |= SHADER_REQ_SHADER_NOPERSPECTIVE_INTERPOLATION;
   }

   emit_header(&ctx, &ctx.glsl_strbufs);
   ctx.glsl_ver_required = emit_ios(&ctx, &ctx.glsl_strbufs, &ctx.generic_ios,
                                    &ctx.texcoord_ios, &ctx.patches_emitted_mask,
                                    ctx.front_back_color_emitted_flags,
                                    &ctx.interp_input_mask, &ctx.has_pervertex,
                                    &ctx.force_color_two_side,
                                    &ctx.shadow_samp_mask);

   if (strbuf_get_error(&ctx.glsl_strbufs.glsl_hdr))
      goto fail;

   bret = fill_interpolants(&ctx, var_sinfo);
   if (bret == false)
      goto fail;

   free(ctx.temp_ranges);

   fill_sinfo(&ctx, sinfo);
   fill_var_sinfo(&ctx, var_sinfo);

   emit_required_sysval_uniforms (&ctx.glsl_strbufs.glsl_hdr,
                                  ctx.glsl_strbufs.required_sysval_uniform_decls);
   set_strbuffers(&ctx.glsl_strbufs, shader);

   VREND_DEBUG(dbg_shader_glsl, rctx, "GLSL:");
   VREND_DEBUG_EXT(dbg_shader_glsl, rctx, strarray_dump(shader));
   VREND_DEBUG(dbg_shader_glsl, rctx, "\n");

   return true;
 fail:
   strbuf_free(&ctx.glsl_strbufs.glsl_main);
   strbuf_free(&ctx.glsl_strbufs.glsl_hdr);
   strbuf_free(&ctx.glsl_strbufs.glsl_ver_ext);
   free(ctx.so_names);
   free(ctx.temp_ranges);
   return false;
}

static bool
iter_vs_declaration(struct tgsi_iterate_context *iter,
                    struct tgsi_full_declaration *decl)
{
   struct dump_ctx *ctx = (struct dump_ctx *)iter;

   const char *shader_in_prefix = "vso";
   const char *shader_out_prefix = "tco";
   const char *name_prefix = "";
   uint32_t i;

   // Generate a shader that passes through all VS outputs
   if (decl->Declaration.File == TGSI_FILE_OUTPUT) {
      for (uint32_t j = 0; j < ctx->num_inputs; j++) {
         if (ctx->inputs[j].name == decl->Semantic.Name &&
             ctx->inputs[j].sid == decl->Semantic.Index &&
             ctx->inputs[j].first == decl->Range.First &&
             ctx->inputs[j].usage_mask  == decl->Declaration.UsageMask &&
             ((!decl->Declaration.Array && ctx->inputs[j].array_id == 0) ||
              (ctx->inputs[j].array_id  == decl->Array.ArrayID)))
            return true;
      }
      i = ctx->num_inputs++;

      ctx->inputs[i].name = decl->Semantic.Name;
      ctx->inputs[i].sid = decl->Semantic.Index;
      ctx->inputs[i].interpolate = decl->Interp.Interpolate;
      ctx->inputs[i].location = decl->Interp.Location;
      ctx->inputs[i].first = decl->Range.First;
      ctx->inputs[i].last = decl->Range.Last;
      ctx->inputs[i].array_id = decl->Declaration.Array ? decl->Array.ArrayID : 0;
      ctx->inputs[i].usage_mask = decl->Declaration.UsageMask;
      ctx->inputs[i].num_components = 4;      
      ctx->inputs[i].glsl_predefined_no_emit = false;
      ctx->inputs[i].glsl_no_index = false;
      ctx->inputs[i].override_no_wm = ctx->inputs[i].num_components == 1;
      ctx->inputs[i].glsl_gl_block = false;

      switch (ctx->inputs[i].name) {
      case TGSI_SEMANTIC_PSIZE:
         name_prefix = "gl_PointSize";
         ctx->inputs[i].glsl_predefined_no_emit = true;
         ctx->inputs[i].glsl_no_index = true;
         ctx->inputs[i].override_no_wm = true;
         ctx->inputs[i].glsl_gl_block = true;
         ctx->shader_req_bits |= SHADER_REQ_PSIZE;
         break;

      case TGSI_SEMANTIC_CLIPDIST:
         name_prefix = "gl_ClipDistance";
         ctx->inputs[i].glsl_predefined_no_emit = true;
         ctx->inputs[i].glsl_no_index = true;
         ctx->inputs[i].glsl_gl_block = true;
         ctx->num_in_clip_dist += 4 * (ctx->inputs[i].last - ctx->inputs[i].first + 1);
         ctx->shader_req_bits |= SHADER_REQ_CLIP_DISTANCE;
         if (ctx->inputs[i].last != ctx->inputs[i].first)
            ctx->guest_sent_io_arrays = true;
         break;

      case TGSI_SEMANTIC_POSITION:
         name_prefix = "gl_Position";
         ctx->inputs[i].glsl_predefined_no_emit = true;
         ctx->inputs[i].glsl_no_index = true;
         ctx->inputs[i].glsl_gl_block = true;
         break;

      case TGSI_SEMANTIC_PATCH:
      case TGSI_SEMANTIC_GENERIC:
         if (ctx->inputs[i].first != ctx->inputs[i].last ||
             ctx->inputs[i].array_id > 0) {
            ctx->guest_sent_io_arrays = true;
            if (!ctx->cfg->use_gles)
               ctx->shader_req_bits |= SHADER_REQ_ARRAYS_OF_ARRAYS;
         }
         break;
      default:
         break;
      }

      memcpy(&ctx->outputs[i], &ctx->inputs[i], sizeof(struct vrend_shader_io));

      if (ctx->inputs[i].glsl_no_index) {
         snprintf(ctx->inputs[i].glsl_name, 128, "%s", name_prefix);
         snprintf(ctx->outputs[i].glsl_name, 128, "%s", name_prefix);
      } else {
         if (ctx->inputs[i].name == TGSI_SEMANTIC_FOG){
            ctx->inputs[i].usage_mask = 0xf;
            ctx->inputs[i].num_components = 4;
            ctx->inputs[i].override_no_wm = false;
            snprintf(ctx->inputs[i].glsl_name, 64, "%s_f%d", shader_in_prefix, ctx->inputs[i].sid);
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_f%d", shader_out_prefix, ctx->inputs[i].sid);
         } else if (ctx->inputs[i].name == TGSI_SEMANTIC_COLOR) {
            snprintf(ctx->inputs[i].glsl_name, 64, "%s_c%d", shader_in_prefix, ctx->inputs[i].sid);
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_c%d", shader_out_prefix, ctx->inputs[i].sid);
         } else if (ctx->inputs[i].name == TGSI_SEMANTIC_GENERIC) {
            snprintf(ctx->inputs[i].glsl_name, 64, "%s_g%d", shader_in_prefix, ctx->inputs[i].sid);
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_g%d", shader_out_prefix, ctx->inputs[i].sid);
         } else {
            snprintf(ctx->outputs[i].glsl_name, 64, "%s_%d", shader_in_prefix, ctx->inputs[i].first);
            snprintf(ctx->inputs[i].glsl_name, 64, "%s_%d", shader_out_prefix, ctx->inputs[i].first);
         }
      }
   }
   return true;
}

bool vrend_shader_create_passthrough_tcs(const struct vrend_context *rctx,
                                         const struct vrend_shader_cfg *cfg,
                                         const struct tgsi_token *vs_tokens,
                                         const struct vrend_shader_key *key,
                                         const float tess_factors[6],
                                         struct vrend_shader_info *sinfo,
                                         struct vrend_strarray *shader,
                                         uint8_t vertices_per_patch)
{
   struct dump_ctx ctx;

   memset(&ctx, 0, sizeof(struct dump_ctx));

   ctx.prog_type = TGSI_PROCESSOR_TESS_CTRL;
   ctx.cfg = cfg;
   ctx.key = key;
   ctx.iter.iterate_declaration = iter_vs_declaration;
   ctx.ssbo_array_base = 0xffffffff;
   ctx.ssbo_atomic_array_base = 0xffffffff;
   ctx.has_sample_input = false;

   if (!allocate_strbuffers(&ctx.glsl_strbufs))
      goto fail;

   tgsi_iterate_shader(vs_tokens, &ctx.iter);

   /*  What is the default on GL? */
   ctx.tcs_vertices_out = vertices_per_patch;

   ctx.num_outputs = ctx.num_inputs;

   handle_io_arrays(&ctx);

   emit_header(&ctx, &ctx.glsl_strbufs);
   ctx.glsl_ver_required = emit_ios(&ctx, &ctx.glsl_strbufs, &ctx.generic_ios,
                                    &ctx.texcoord_ios, &ctx.patches_emitted_mask,
                                    ctx.front_back_color_emitted_flags,
                                    &ctx.interp_input_mask, &ctx.has_pervertex,
                                    &ctx.force_color_two_side,
                                    &ctx.shadow_samp_mask);

   emit_buf(&ctx.glsl_strbufs, "void main() {\n");

   for (uint32_t i = 0; i < ctx.num_inputs; ++i) {
      const char *out_prefix = "";
      const char *in_prefix = "";

      const char *postfix = "";

      if (ctx.inputs[i].glsl_gl_block) {
         out_prefix = "gl_out[gl_InvocationID].";
         in_prefix = "gl_in[gl_InvocationID].";
      } else {
         postfix = "[gl_InvocationID]";
      }

      if (ctx.inputs[i].first == ctx.inputs[i].last) {
         emit_buff(&ctx.glsl_strbufs, "%s%s%s = %s%s%s;\n",
                   out_prefix, ctx.outputs[i].glsl_name, postfix,
                   in_prefix, ctx.inputs[i].glsl_name, postfix);
      } else {
         unsigned size = ctx.inputs[i].last == ctx.inputs[i].first + 1;
         for (unsigned int k = 0; k < size; ++k) {
            emit_buff(&ctx.glsl_strbufs, "%s%s%s[%d] = %s%s%s[%d];\n",
                      out_prefix, ctx.outputs[i].glsl_name, postfix, k,
                      in_prefix, ctx.inputs[i].glsl_name, postfix, k);
         }
      }
   }

   for (int i = 0; i < 4; ++i)
      emit_buff(&ctx.glsl_strbufs, "gl_TessLevelOuter[%d] = %f;\n", i, tess_factors[i]);

   for (int i = 0; i < 2; ++i)
      emit_buff(&ctx.glsl_strbufs, "gl_TessLevelInner[%d] = %f;\n", i, tess_factors[i + 4]);

   emit_buf(&ctx.glsl_strbufs, "}\n");

   fill_sinfo(&ctx, sinfo);
   emit_required_sysval_uniforms (&ctx.glsl_strbufs.glsl_hdr,
                                  ctx.glsl_strbufs.required_sysval_uniform_decls);
   set_strbuffers(&ctx.glsl_strbufs, shader);

   VREND_DEBUG(dbg_shader_glsl, rctx, "GLSL:");
   VREND_DEBUG_EXT(dbg_shader_glsl, rctx, strarray_dump(shader));
   VREND_DEBUG(dbg_shader_glsl, rctx, "\n");

   return true;
fail:
   strbuf_free(&ctx.glsl_strbufs.glsl_main);
   strbuf_free(&ctx.glsl_strbufs.glsl_hdr);
   strbuf_free(&ctx.glsl_strbufs.glsl_ver_ext);
   free(ctx.so_names);
   free(ctx.temp_ranges);
   return false;
}

static
void vrend_shader_write_io_as_src(struct vrend_strbuf *result,
                                  const  char *array_or_varname,
                                  const struct vrend_shader_io *io,
                                  const struct tgsi_full_src_register *src,
                                  enum io_decl_type decl_type)
{


   if (io->first == io->last && !io->overlapping_array) {
      strbuf_appendf(result, "%s%s", io->glsl_name, array_or_varname);
   } else {
      const struct vrend_shader_io *base = io->overlapping_array ? io->overlapping_array : io;
      const int offset = src->Register.Index - io->first + io->array_offset;

      if (decl_type == decl_block) {
         if (src->Register.Indirect)
            strbuf_appendf(result, "%s.%s[addr%d + %d]", array_or_varname, base->glsl_name,
                           src->Indirect.Index, offset);
         else
            strbuf_appendf(result, "%s.%s[%d]", array_or_varname, base->glsl_name, offset);
      } else {
         if (src->Register.Indirect)
            strbuf_appendf(result, "%s%s[addr%d + %d]", base->glsl_name,
                           array_or_varname, src->Indirect.Index, offset);
         else
            strbuf_appendf(result, "%s%s[%d]", base->glsl_name,
                           array_or_varname, offset);
      }
   }
}

static
void vrend_shader_write_io_as_dst(struct vrend_strbuf *result,
                                  const  char *array_or_varname,
                                  const struct vrend_shader_io *io,
                                  const struct tgsi_full_dst_register *src,
                                  enum io_decl_type decl_type)
{

   if (io->first == io->last) {
      if (io->overlapping_array)
         strbuf_appendf(result, "%s%s[%d]", io->overlapping_array->glsl_name,
                        array_or_varname, io->array_offset);
      else
         strbuf_appendf(result, "%s%s", io->glsl_name, array_or_varname);
   } else {
      const struct vrend_shader_io *base = io->overlapping_array ? io->overlapping_array : io;
      const int offset = src->Register.Index - io->first + io->array_offset;

      if (decl_type == decl_block) {
         if (src->Register.Indirect)
            strbuf_appendf(result, "%s.%s[addr%d + %d]", array_or_varname, base->glsl_name,
                           src->Indirect.Index, offset);
         else
            strbuf_appendf(result, "%s.%s[%d]", array_or_varname, base->glsl_name, offset);
      } else {
         if (src->Register.Indirect)
            strbuf_appendf(result, "%s%s[addr%d + %d]", base->glsl_name,
                           array_or_varname, src->Indirect.Index, offset);
         else
            strbuf_appendf(result, "%s%s[%d]", base->glsl_name,
                           array_or_varname, offset);
      }
   }
}

bool vrend_shader_needs_alpha_func(const struct vrend_shader_key *key) {
   if (!key->add_alpha_test)
      return false;
   switch (key->alpha_test) {
   default:
      return false;
   case PIPE_FUNC_LESS:
   case PIPE_FUNC_EQUAL:
   case PIPE_FUNC_LEQUAL:
   case PIPE_FUNC_GREATER:
   case PIPE_FUNC_NOTEQUAL:
   case PIPE_FUNC_GEQUAL:
      return true;
   }
}

