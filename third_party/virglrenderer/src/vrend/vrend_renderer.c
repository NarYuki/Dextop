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
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <unistd.h>
#include <stdatomic.h>
#include <stdio.h>
#include <errno.h>
#include "pipe/p_shader_tokens.h"

#include "pipe/p_defines.h"
#include "pipe/p_state.h"
#include "util/macros.h"
#include "util/u_inlines.h"
#include "util/u_memory.h"
#include "util/u_dual_blend.h"
#include "util/hash_table.h"
#include "util/ralloc.h"

#include "util/u_thread.h"
#include "util/u_format.h"
#include "tgsi/tgsi_dump.h"
#include "tgsi/tgsi_parse.h"

#include "vrend_object.h"
#include "vrend_shader.h"

#include "vrend_renderer.h"
#include "vrend_blitter.h"
#include "vrend_debug.h"
#include "vrend_winsys.h"
#include "vrend_blitter.h"

#include "virgl_util.h"

#include "virgl_hw.h"
#include "virgl_resource.h"
#include "virglrenderer.h"
#include "virgl_protocol.h"
#include "virgl_fence.h"
#include "virtgpu_drm.h"

#include "tgsi/tgsi_text.h"

#ifdef HAVE_EPOXY_GLX_H
#include <epoxy/glx.h>
#endif

#ifdef ENABLE_VIDEO
#include "vrend_video.h"
#endif

#ifdef WIN32
#include <dxgi1_2.h>
#endif

/*
 * VIRTGPU_DRM_CAPSET_VIRGL has version 0 and 1, but they are both
 * virgl_caps_v1 and are exactly the same.
 *
 * VIRTGPU_DRM_CAPSET_VIRGL2 has version 0, 1, and 2, but they are
 * all virgl_caps_v2 and are exactly the same.
 *
 * Since virgl_caps_v2 is growable and no backward-incompatible change is
 * expected, we don't bump up these versions anymore.
 */
#define VREND_CAPSET_VIRGL_MAX_VERSION 1
#define VREND_CAPSET_VIRGL2_MAX_VERSION 2

static const uint32_t fake_occlusion_query_samples_passed_default = 1024;

const struct vrend_if_cbs *vrend_clicbs;

struct vrend_fence {
   /* When the sync thread is waiting on the fence and the main thread
    * destroys the context, ctx is set to NULL.  Otherwise, ctx is always
    * valid.
    */
   struct vrend_context *ctx;
   uint32_t flags;
   uint64_t fence_id;

   union {
      GLsync glsyncobj;
#ifdef HAVE_EPOXY_EGL_H
      EGLSyncKHR eglsyncobj;
#endif
   };
   struct list_head fences;
};

struct vrend_query {
   struct list_head waiting_queries;

   GLuint id;
   GLuint type;
   GLuint index;
   GLuint gltype;
   struct vrend_context *ctx;
   int sub_ctx_id;
   struct vrend_resource *res;
   bool fake_samples_passed;
};

struct global_error_state {
   enum virgl_errors last_error;
};

enum features_id
{
   feat_arb_or_gles_ext_texture_buffer,
   feat_arb_robustness,
   feat_arb_buffer_storage,
   feat_arrays_of_arrays,
   feat_ati_meminfo,
   feat_atomic_counters,
   feat_base_instance,
   feat_barrier,
   feat_bind_vertex_buffers,
   feat_bit_encoding,
   feat_blend_equation_advanced,
   feat_clear_texture,
   feat_clip_control,
   feat_compute_shader,
   feat_copy_image,
   feat_conditional_render_inverted,
   feat_conservative_depth,
   feat_cube_map_array,
   feat_cull_distance,
   feat_debug_cb,
   feat_depth_clamp,
   feat_draw_instance,
   feat_draw_parameters,
   feat_dual_src_blend,
   feat_egl_image,
   feat_egl_image_storage,
   feat_enhanced_layouts,
   feat_fb_no_attach,
   feat_framebuffer_fetch,
   feat_framebuffer_fetch_non_coherent,
   feat_geometry_shader,
   feat_gl_conditional_render,
   feat_gl_prim_restart,
   feat_gles_khr_robustness,
   feat_gles31_compatibility,
   feat_gles31_vertex_attrib_binding,
   feat_gpu_shader5,
   feat_group_vote,
   feat_images,
   feat_indep_blend,
   feat_indep_blend_func,
   feat_indirect_draw,
   feat_indirect_params,
   feat_khr_debug,
   feat_memory_object,
   feat_memory_object_fd,
   feat_mesa_invert,
   feat_ms_scaled_blit,
   feat_multisample,
   feat_multi_draw_indirect,
   feat_nv_conditional_render,
   feat_nv_prim_restart,
   feat_shader_noperspective_interpolation,
   feat_nvx_gpu_memory_info,
   feat_polygon_offset_clamp,
   feat_occlusion_query,
   feat_occlusion_query_boolean,
   feat_pipeline_statistics_query,
   feat_qbo,
   feat_robust_buffer_access,
   feat_sample_mask,
   feat_sample_shading,
   feat_samplers,
   feat_sampler_border_colors,
   feat_shader_clock,
   feat_separate_shader_objects,
   feat_ssbo,
   feat_ssbo_barrier,
   feat_srgb_write_control,
   feat_stencil_texturing,
   feat_storage_multisample,
   feat_tessellation,
   feat_texture_array,
   feat_texture_barrier,
   feat_texture_buffer_range,
   feat_texture_gather,
   feat_texture_mirror_clamp_to_edge,
   feat_texture_mirror_clamp,
   feat_texture_mirror_clamp_to_border,
   feat_texture_multisample,
   feat_texture_query_lod,
   feat_texture_shadow_lod,
   feat_texture_srgb_decode,
   feat_texture_storage,
   feat_texture_view,
   feat_timer_query,
   feat_transform_feedback,
   feat_transform_feedback2,
   feat_transform_feedback3,
   feat_transform_feedback_overflow_query,
   feat_txqs,
   feat_ubo,
   feat_viewport_array,
   feat_implicit_msaa,
   feat_anisotropic_filter,
   feat_seamless_cubemap_per_texture,
   feat_vs_layer_viewport,
   feat_vs_viewport_index,
   feat_last,
};

#define FEAT_MAX_EXTS 4
#define UNAVAIL INT_MAX

#define FEAT(NAME, GLVER, GLESVER, ...) \
   [feat_ ## NAME ] = {GLVER, GLESVER, { __VA_ARGS__ }, #NAME}

static const  struct {
   int gl_ver;
   int gles_ver;
   const char *gl_ext[FEAT_MAX_EXTS];
   const char *log_name;
} feature_list[] = {
   FEAT(arb_or_gles_ext_texture_buffer, 31, UNAVAIL, "GL_ARB_texture_buffer_object", "GL_EXT_texture_buffer", NULL),
   FEAT(arb_robustness, UNAVAIL, UNAVAIL,  "GL_ARB_robustness" ),
   FEAT(arb_buffer_storage, 44, UNAVAIL, "GL_ARB_buffer_storage", "GL_EXT_buffer_storage"),
   FEAT(arrays_of_arrays, 43, 31, "GL_ARB_arrays_of_arrays"),
   FEAT(ati_meminfo, UNAVAIL, UNAVAIL, "GL_ATI_meminfo" ),
   FEAT(atomic_counters, 42, 31,  "GL_ARB_shader_atomic_counters" ),
   FEAT(base_instance, 42, UNAVAIL,  "GL_ARB_base_instance", "GL_EXT_base_instance" ),
   FEAT(barrier, 42, 31, "GL_ARB_shader_image_load_store"),
   FEAT(bind_vertex_buffers, 44, UNAVAIL, NULL),
   FEAT(bit_encoding, 33, UNAVAIL,  "GL_ARB_shader_bit_encoding" ),
   FEAT(blend_equation_advanced, UNAVAIL, 32,  "GL_KHR_blend_equation_advanced" ),
   FEAT(clear_texture, 44, UNAVAIL, "GL_ARB_clear_texture", "GL_EXT_clear_texture"),
   FEAT(clip_control, 45, UNAVAIL, "GL_ARB_clip_control", "GL_EXT_clip_control"),
   FEAT(compute_shader, 43, 31,  "GL_ARB_compute_shader" ),
   FEAT(copy_image, 43, 32,  "GL_ARB_copy_image", "GL_EXT_copy_image", "GL_OES_copy_image" ),
   FEAT(conditional_render_inverted, 45, UNAVAIL,  "GL_ARB_conditional_render_inverted" ),
   FEAT(conservative_depth, 42, UNAVAIL, "GL_ARB_conservative_depth", "GL_EXT_conservative_depth" ),
   FEAT(cube_map_array, 40, 32,  "GL_ARB_texture_cube_map_array", "GL_EXT_texture_cube_map_array", "GL_OES_texture_cube_map_array" ),
   FEAT(cull_distance, 45, UNAVAIL, "GL_ARB_cull_distance", "GL_EXT_clip_cull_distance" ),
   FEAT(debug_cb, UNAVAIL, UNAVAIL, NULL), /* special case */
   FEAT(draw_instance, 31, 30,  "GL_ARB_draw_instanced" ),
   FEAT(draw_parameters, 46, UNAVAIL, "ARB_shader_draw_parameters"),
   FEAT(dual_src_blend, 33, UNAVAIL,  "GL_ARB_blend_func_extended", "GL_EXT_blend_func_extended" ),
   FEAT(depth_clamp, 32, UNAVAIL, "GL_ARB_depth_clamp", "GL_EXT_depth_clamp", "GL_NV_depth_clamp"),
   FEAT(enhanced_layouts, 44, UNAVAIL, "GL_ARB_enhanced_layouts"),
   FEAT(egl_image, UNAVAIL, UNAVAIL, "GL_OES_EGL_image"),
   FEAT(egl_image_storage, UNAVAIL, UNAVAIL, "GL_EXT_EGL_image_storage"),
   FEAT(fb_no_attach, 43, 31,  "GL_ARB_framebuffer_no_attachments" ),
   FEAT(framebuffer_fetch, UNAVAIL, UNAVAIL,  "GL_EXT_shader_framebuffer_fetch" ),
   FEAT(framebuffer_fetch_non_coherent, UNAVAIL, UNAVAIL,  "GL_EXT_shader_framebuffer_fetch_non_coherent" ),
   FEAT(geometry_shader, 32, 32, "GL_EXT_geometry_shader", "GL_OES_geometry_shader"),
   FEAT(gl_conditional_render, 30, UNAVAIL, NULL),
   FEAT(gl_prim_restart, 31, 30, NULL),
   FEAT(gles_khr_robustness, UNAVAIL, UNAVAIL,  "GL_KHR_robustness" ),
   FEAT(gles31_compatibility, 45, 31, "ARB_ES3_1_compatibility" ),
   FEAT(gles31_vertex_attrib_binding, 43, 31,  "GL_ARB_vertex_attrib_binding" ),
   FEAT(gpu_shader5, 40, 32, "GL_ARB_gpu_shader5", "GL_EXT_gpu_shader5", "GL_OES_gpu_shader5" ),
   FEAT(group_vote, 46, UNAVAIL, "GL_ARB_shader_group_vote"),
   FEAT(images, 42, 31,  "GL_ARB_shader_image_load_store" ),
   FEAT(indep_blend, 30, 32,  "GL_EXT_draw_buffers2", "GL_OES_draw_buffers_indexed" ),
   FEAT(indep_blend_func, 40, 32,  "GL_ARB_draw_buffers_blend", "GL_OES_draw_buffers_indexed"),
   FEAT(indirect_draw, 40, 31,  "GL_ARB_draw_indirect" ),
   FEAT(indirect_params, 46, UNAVAIL,  "GL_ARB_indirect_parameters" ),
   FEAT(khr_debug, 43, 32,  "GL_KHR_debug" ),
   FEAT(memory_object, UNAVAIL, UNAVAIL, "GL_EXT_memory_object"),
   FEAT(memory_object_fd, UNAVAIL, UNAVAIL, "GL_EXT_memory_object_fd"),
   FEAT(mesa_invert, UNAVAIL, UNAVAIL,  "GL_MESA_pack_invert" ),
   FEAT(ms_scaled_blit, UNAVAIL, UNAVAIL,  "GL_EXT_framebuffer_multisample_blit_scaled" ),
   FEAT(multisample, 32, 30,  "GL_ARB_texture_multisample" ),
   FEAT(multi_draw_indirect, 43, UNAVAIL,  "GL_ARB_multi_draw_indirect", "GL_EXT_multi_draw_indirect" ),
   FEAT(nv_conditional_render, UNAVAIL, UNAVAIL,  "GL_NV_conditional_render" ),
   FEAT(nv_prim_restart, UNAVAIL, UNAVAIL,  "GL_NV_primitive_restart" ),
   FEAT(shader_noperspective_interpolation, 31, UNAVAIL, "GL_NV_shader_noperspective_interpolation", "GL_EXT_gpu_shader4"),
   FEAT(nvx_gpu_memory_info, UNAVAIL, UNAVAIL, "GL_NVX_gpu_memory_info" ),
   FEAT(pipeline_statistics_query, 46, UNAVAIL, "GL_ARB_pipeline_statistics_query"),
   FEAT(polygon_offset_clamp, 46, UNAVAIL,  "GL_ARB_polygon_offset_clamp", "GL_EXT_polygon_offset_clamp"),
   FEAT(occlusion_query, 15, UNAVAIL, "GL_ARB_occlusion_query"),
   FEAT(occlusion_query_boolean, 33, 30, "GL_EXT_occlusion_query_boolean", "GL_ARB_occlusion_query2"),
   FEAT(qbo, 44, UNAVAIL, "GL_ARB_query_buffer_object" ),
   FEAT(robust_buffer_access, 43, UNAVAIL,  "GL_ARB_robust_buffer_access_behavior", "GL_KHR_robust_buffer_access_behavior" ),
   FEAT(sample_mask, 32, 31,  "GL_ARB_texture_multisample" ),
   FEAT(sample_shading, 40, 32,  "GL_ARB_sample_shading", "GL_OES_sample_shading" ),
   FEAT(samplers, 33, 30,  "GL_ARB_sampler_objects" ),
   FEAT(sampler_border_colors, 33, 32,  "GL_ARB_sampler_objects", "GL_EXT_texture_border_clamp", "GL_OES_texture_border_clamp" ),
   FEAT(separate_shader_objects, 41, 31, "GL_ARB_seperate_shader_objects"),
   FEAT(shader_clock, UNAVAIL, UNAVAIL,  "GL_ARB_shader_clock" ),
   FEAT(ssbo, 43, 31,  "GL_ARB_shader_storage_buffer_object" ),
   FEAT(ssbo_barrier, 43, 31, "GL_ARB_shader_storage_buffer_object"),
   FEAT(srgb_write_control, 30, UNAVAIL, "GL_EXT_sRGB_write_control"),
   FEAT(stencil_texturing, 43, 31,  "GL_ARB_stencil_texturing" ),
   FEAT(storage_multisample, 43, 31,  "GL_ARB_texture_storage_multisample" ),
   FEAT(tessellation, 40, 32,  "GL_ARB_tessellation_shader", "GL_OES_tessellation_shader", "GL_EXT_tessellation_shader" ),
   FEAT(texture_array, 30, 30,  "GL_EXT_texture_array" ),
   FEAT(texture_barrier, 45, UNAVAIL,  "GL_ARB_texture_barrier" ),
   FEAT(texture_buffer_range, 43, 32,  "GL_ARB_texture_buffer_range" ),
   FEAT(texture_gather, 40, 31,  "GL_ARB_texture_gather" ),
   FEAT(texture_mirror_clamp_to_edge, UNAVAIL, UNAVAIL, "GL_ATI_texture_mirror_once", "GL_EXT_texture_mirror_clamp", "GL_ARB_texture_mirror_clamp_to_edge", "GL_EXT_texture_mirror_clamp_to_edge"),
   FEAT(texture_mirror_clamp, UNAVAIL, UNAVAIL, "GL_ATI_texture_mirror_once", "GL_EXT_texture_mirror_clamp"),
   FEAT(texture_mirror_clamp_to_border, UNAVAIL, UNAVAIL, "GL_EXT_texture_mirror_clamp"),
   FEAT(texture_multisample, 32, 31,  "GL_ARB_texture_multisample" ),
   FEAT(texture_query_lod, 40, UNAVAIL, "GL_ARB_texture_query_lod", "GL_EXT_texture_query_lod"),
   FEAT(texture_shadow_lod, UNAVAIL, UNAVAIL, "GL_EXT_texture_shadow_lod"),
   FEAT(texture_srgb_decode, UNAVAIL, UNAVAIL,  "GL_EXT_texture_sRGB_decode" ),
   FEAT(texture_storage, 42, 30,  "GL_ARB_texture_storage" ),
   FEAT(texture_view, 43, UNAVAIL,  "GL_ARB_texture_view", "GL_OES_texture_view", "GL_EXT_texture_view" ),
   FEAT(timer_query, 33, UNAVAIL, "GL_ARB_timer_query", "GL_EXT_disjoint_timer_query"),
   FEAT(transform_feedback, 30, 30,  "GL_EXT_transform_feedback" ),
   FEAT(transform_feedback2, 40, 30,  "GL_ARB_transform_feedback2" ),
   FEAT(transform_feedback3, 40, UNAVAIL,  "GL_ARB_transform_feedback3" ),
   FEAT(transform_feedback_overflow_query, 46, UNAVAIL,  "GL_ARB_transform_feedback_overflow_query" ),
   FEAT(txqs, 45, UNAVAIL,  "GL_ARB_shader_texture_image_samples" ),
   FEAT(ubo, 31, 30,  "GL_ARB_uniform_buffer_object" ),
   FEAT(viewport_array, 41, UNAVAIL,  "GL_ARB_viewport_array", "GL_OES_viewport_array"),
   FEAT(implicit_msaa, UNAVAIL, UNAVAIL,  "GL_EXT_multisampled_render_to_texture"),
   FEAT(anisotropic_filter, 46, UNAVAIL,  "GL_EXT_texture_filter_anisotropic", "GL_ARB_texture_filter_anisotropic"),
   FEAT(seamless_cubemap_per_texture, UNAVAIL, UNAVAIL,  "GL_AMD_seamless_cubemap_per_texture" ),
   FEAT(vs_layer_viewport, UNAVAIL, UNAVAIL, "GL_AMD_vertex_shader_layer"),
   FEAT(vs_viewport_index, UNAVAIL, UNAVAIL, "GL_AMD_vertex_shader_viewport_index"),
};

struct global_renderer_state {
   struct vrend_context *ctx0;
   struct vrend_context *current_ctx;
   struct vrend_context *current_hw_ctx;

   struct list_head waiting_query_list;
   struct list_head fence_list;
   struct list_head fence_wait_list;
   struct vrend_fence *fence_waiting;

   int gl_major_ver;
   int gl_minor_ver;

   mtx_t fence_mutex;
   thrd_t sync_thread;
   virgl_gl_context sync_context;

   cnd_t fence_cond;

   /* only used with async fence callback */
   atomic_bool has_waiting_queries;
   bool polling;
   mtx_t poll_mutex;
   cnd_t poll_cond;

   float tess_factors[6];
   int eventfd;

   uint32_t max_draw_buffers;
   uint32_t max_texture_buffer_size;
   uint32_t max_texture_2d_size;
   uint32_t max_texture_3d_size;
   uint32_t max_texture_cube_size;
   uint32_t max_shader_patch_varyings;
   uint32_t max_vertex_attributes;
   uint32_t max_texture_units;

   /* inferred GL caching type */
   uint32_t inferred_gl_caching_type;

   uint64_t features[feat_last / 64 + 1];

   bool finishing : 1;
   bool use_gles : 1;
   bool use_core_profile : 1;
   bool use_external_blob : 1;
   bool use_integer : 1;
   /* these appeared broken on at least one driver */
   bool use_explicit_locations : 1;
   /* threaded sync */
   bool stop_sync_thread : 1;
   /* async fence callback */
   bool use_async_fence_cb : 1;

#ifdef HAVE_EPOXY_EGL_H
   bool use_egl_fence : 1;
#endif
   bool d3d_share_texture : 1;
   bool gbm_layout_feat : 1;
};

struct sysval_uniform_block {
   GLfloat clipp[VIRGL_NUM_CLIP_PLANES][4];
   GLuint stipple_pattern[VREND_POLYGON_STIPPLE_SIZE][4];
   GLfloat winsys_adjust_y;
   GLfloat alpha_ref_val;
   GLfloat clip_plane_enabled;
   GLint drawid_base;
};

static struct global_renderer_state vrend_state;

static inline bool has_feature(enum features_id feature_id)
{
   int slot = feature_id / 64;
   uint64_t mask = 1ull << (feature_id & 63);
   bool retval = vrend_state.features[slot] & mask ? true : false;
   VREND_DEBUG(dbg_feature_use, NULL, "Try using feature %s:%d\n",
               feature_list[feature_id].log_name,
               retval);
   return retval;
}


static inline void set_feature(enum features_id feature_id)
{
   int slot = feature_id / 64;
   uint64_t mask = 1ull << (feature_id & 63);
   vrend_state.features[slot] |= mask;
}

static inline void clear_feature(enum features_id feature_id)
{
   int slot = feature_id / 64;
   uint64_t mask = 1ull << (feature_id & 63);
   vrend_state.features[slot] &= ~mask;
}


struct vrend_linked_shader_program {
   struct list_head head;
   struct list_head sl[PIPE_SHADER_TYPES];
   bool is_pipeline;
   union {
       GLuint program;
       GLuint pipeline;
   } id;

   bool dual_src_linked;
   struct vrend_shader *ss[PIPE_SHADER_TYPES];
   uint64_t vs_fs_key;

   uint32_t ubo_used_mask[PIPE_SHADER_TYPES];
   uint32_t samplers_used_mask[PIPE_SHADER_TYPES];
   // a subset of samplers_used_mask
   uint32_t shadow_samp_mask[PIPE_SHADER_TYPES];

   // compact arrays of uniform locations for each set bit in samplers_used_mask
   GLuint *sampler_locs[PIPE_SHADER_TYPES];
   // ignore elements corresponding to unset bits of shadow_samp_mask
   GLuint *shadow_samp_mask_locs[PIPE_SHADER_TYPES];
   GLuint *shadow_samp_add_locs[PIPE_SHADER_TYPES];

   GLint const_location[PIPE_SHADER_TYPES];

   GLuint *attrib_locs;

   GLuint separate_virgl_block_id[PIPE_SHADER_TYPES];
   GLuint virgl_block_bind;
   uint32_t sysvalue_data_cookie;
   GLuint ubo_sysval_buffer_id;

   uint32_t images_used_mask[PIPE_SHADER_TYPES];
   GLint *img_locs[PIPE_SHADER_TYPES];

   uint32_t ssbo_used_mask[PIPE_SHADER_TYPES];

   int32_t tex_levels_uniform_id[PIPE_SHADER_TYPES];

   struct vrend_sub_context *ref_context;

   uint32_t gles_use_query_texturelevel_mask;

   bool reads_drawid;
};

struct vrend_shader {
   struct vrend_shader *next_variant;
   struct vrend_shader_selector *sel;

   struct vrend_variable_shader_info var_sinfo;

   struct vrend_strarray glsl_strings;
   GLuint id;
   GLuint program_id; /* only used for separable shaders */
   GLuint last_pipeline_id;
   bool is_compiled;
   bool is_linked; /* only used for separable shaders */
   struct vrend_shader_key key;
   struct list_head programs;
};

struct vrend_shader_selector {
   struct pipe_reference reference;

   enum pipe_shader_type type;
   struct vrend_shader_info sinfo;

   struct vrend_shader *current;
   struct tgsi_token *tokens;

   uint32_t req_local_mem;
};

struct vrend_long_shader_buffer {
   uint32_t handle;
   struct vrend_shader_selector *sel;

   char *tmp_buf;
   uint32_t total_length;
   uint32_t current_length;
};

struct vrend_texture {
   struct vrend_resource base;
   struct pipe_sampler_state state;
   GLint cur_swizzle[4];
   GLuint cur_srgb_decode;
   GLuint cur_base, cur_max;
};

struct vrend_surface {
   struct pipe_reference reference;
   GLuint gl_id;
   GLuint format;
   GLuint level;
   GLuint first_layer;
   GLuint last_layer;
   GLuint nr_samples;
   struct vrend_resource *texture;
};

struct vrend_sampler_state {
   struct pipe_sampler_state base;
   struct vrend_sub_context *sub_ctx;
   GLuint ids[2];
};

struct vrend_depth_stencil_alpha_state {
   struct pipe_depth_stencil_alpha_state base;
   struct vrend_sub_context *owning_sub;
};

struct vrend_so_target {
   struct pipe_reference reference;
   GLuint res_handle;
   unsigned buffer_offset;
   unsigned buffer_size;
   struct vrend_resource *buffer;
   struct vrend_sub_context *sub_ctx;
};

struct vrend_sampler_view {
   struct pipe_reference reference;
   GLuint gl_id;
   enum virgl_formats format;
   GLenum target;
   union {
       struct {
           GLuint first_layer:16;     /**< first layer to use for array textures */
           GLuint last_layer:16;      /**< last layer to use for array textures */
           GLuint first_level:8;      /**< first mipmap level to use */
           GLuint last_level:8;       /**< last mipmap level to use */
       } tex;
       struct {
           GLuint first_element;
           GLuint last_element;
       } buf;
   } u;
   GLint gl_swizzle[4];
   GLuint srgb_decode;
   GLuint levels;
   bool emulated_rect;
   struct vrend_resource *texture;
};

struct vrend_image_view {
   GLuint id;
   GLenum access;
   GLenum format;
   uint32_t vformat;
   union {
      struct {
         unsigned first_layer:16;     /**< first layer to use for array textures */
         unsigned last_layer:16;      /**< last layer to use for array textures */
         unsigned level:8;            /**< mipmap level to use */
      } tex;
      struct {
         unsigned offset;   /**< offset in bytes */
         unsigned size;     /**< size of the accessible sub-range in bytes */
      } buf;
   } u;
   struct vrend_resource *texture;
   GLuint view_id;
};

struct vrend_ssbo {
   struct vrend_resource *res;
   unsigned buffer_size;
   unsigned buffer_offset;
};

struct vrend_abo {
   struct vrend_resource *res;
   unsigned buffer_size;
   unsigned buffer_offset;
};

struct vrend_vertex_element {
   struct pipe_vertex_element base;
   GLenum type;
   GLboolean norm;
   GLuint nr_chan;
};

struct vrend_vertex_element_array {
   unsigned count;
   struct vrend_vertex_element elements[PIPE_MAX_ATTRIBS];
   GLuint id;
   uint32_t signed_int_bitmask;
   uint32_t unsigned_int_bitmask;
   uint32_t zyxw_bitmask;
   struct vrend_sub_context *owning_sub;
};

struct vrend_constants {
   unsigned int *consts;
   uint32_t num_consts;
   uint32_t num_allocated_consts;
};

// bound sampler view (texture) state associated with the current
// program or pipeline stage
struct vrend_shader_view {
   // a possibly sparse array of sampler views, with upper limit `max_num_views`
   // on the length. must test each element for non-null.
   int max_num_views;
   struct vrend_sampler_view *views[PIPE_MAX_SHADER_SAMPLER_VIEWS];
   struct vrend_sampler_state *samplers[PIPE_MAX_SAMPLERS];

   // for elements of `views` and 'samplers' indicated by set dirty bits:
   // texture-to-texture-unit binding is needed before next draw
   uint32_t dirty_mask;

   // only used for gles host contexts to emulate textureQueryLevels().
   // compact array corresponding only to valid elements of `views`, with length
   // equal to `num_used_views`
   int32_t texture_levels[PIPE_MAX_SHADER_SAMPLER_VIEWS];
   uint32_t num_used_views;
};

struct vrend_viewport {
   GLint cur_x, cur_y;
   GLsizei width, height;
   GLclampd near_val, far_val;
};

/* create a streamout object to support pause/resume */
struct vrend_streamout_object {
   GLuint id;
   uint32_t num_targets;
   uint32_t handles[16];
   struct list_head head;
   int xfb_state;
   struct vrend_so_target *so_targets[16];
};

#define XFB_STATE_OFF 0
#define XFB_STATE_STARTED_NEED_BEGIN 1
#define XFB_STATE_STARTED 2
#define XFB_STATE_PAUSED 3

struct vrend_vertex_buffer {
   struct pipe_vertex_buffer base;
   uint32_t res_id;
};

#define VREND_PROGRAM_NQUEUES (1 << 8)
#define VREND_PROGRAM_NQUEUE_MASK (VREND_PROGRAM_NQUEUES - 1)

struct vrend_sub_context {
   struct list_head head;

   virgl_gl_context gl_context;

   int sub_ctx_id;

   GLuint vaoid;
   uint32_t enabled_attribs_bitmask;

   /* Using an array of lists only adds VREND_PROGRAM_NQUEUES - 1 list_head
    * structures to the consumed memory, but looking up the program can
    * be spead up by the factor VREND_PROGRAM_NQUEUES which makes this
    * worthwile. */
   struct list_head gl_programs[VREND_PROGRAM_NQUEUES];
   struct list_head cs_programs;
   struct util_hash_table *object_hash;

   struct vrend_vertex_element_array *ve;
   int num_vbos;
   int old_num_vbos; /* for cleaning up */
   struct vrend_vertex_buffer vbo[PIPE_MAX_ATTRIBS];

   struct pipe_index_buffer ib;

   bool vbo_dirty;
   bool shader_dirty;
   bool cs_shader_dirty;
   bool stencil_state_dirty;
   bool image_state_dirty;
   bool blend_state_dirty;

   struct vrend_long_shader_buffer *long_shader_in_progress[PIPE_SHADER_TYPES];
   struct vrend_shader_selector *shaders[PIPE_SHADER_TYPES];
   struct vrend_linked_shader_program *prog;

   GLuint prog_ids[PIPE_SHADER_TYPES];
   struct vrend_shader_view views[PIPE_SHADER_TYPES];

   struct vrend_constants consts[PIPE_SHADER_TYPES];
   bool const_dirty[PIPE_SHADER_TYPES];

   struct pipe_constant_buffer cbs[PIPE_SHADER_TYPES][PIPE_MAX_CONSTANT_BUFFERS];
   uint32_t const_bufs_used_mask[PIPE_SHADER_TYPES];
   uint32_t const_bufs_dirty[PIPE_SHADER_TYPES];

   uint32_t fb_id;
   uint32_t nr_cbufs;
   struct vrend_surface *zsurf;
   struct vrend_surface *surf[PIPE_MAX_COLOR_BUFS];

   struct vrend_viewport vps[PIPE_MAX_VIEWPORTS];
   /* viewport is negative */
   uint32_t scissor_state_dirty;
   uint32_t viewport_state_dirty;
   uint32_t viewport_state_initialized;

   uint32_t fb_height;

   struct pipe_scissor_state ss[PIPE_MAX_VIEWPORTS];

   struct pipe_blend_state blend_state;
   struct pipe_depth_stencil_alpha_state dsa_state;
   struct pipe_rasterizer_state rs_state;

   uint8_t stencil_refs[2];
   bool viewport_is_negative;
   /* this is set if the contents of the FBO look upside down when viewed
      with 0,0 as the bottom corner */
   bool fbo_origin_upper_left;

   GLuint blit_fb_ids[2];

   struct vrend_depth_stencil_alpha_state *dsa;

   struct pipe_clip_state ucp_state;

   bool depth_test_enabled;
   bool alpha_test_enabled;
   bool stencil_test_enabled;
   bool framebuffer_srgb_enabled;

   int last_shader_idx;

   struct pipe_rasterizer_state hw_rs_state;
   struct pipe_blend_state hw_blend_state;

   struct list_head streamout_list;
   struct vrend_streamout_object *current_so;

   struct pipe_blend_color blend_color;

   uint32_t cond_render_q_id;
   GLenum cond_render_gl_mode;

   struct vrend_image_view image_views[PIPE_SHADER_TYPES][PIPE_MAX_SHADER_IMAGES];
   uint32_t images_used_mask[PIPE_SHADER_TYPES];

   struct vrend_ssbo ssbo[PIPE_SHADER_TYPES][PIPE_MAX_SHADER_BUFFERS];
   uint32_t ssbo_used_mask[PIPE_SHADER_TYPES];
   uint32_t ssbo_binding_offset[PIPE_SHADER_TYPES];

   struct vrend_abo abo[PIPE_MAX_HW_ATOMIC_BUFFERS];
   uint32_t abo_used_mask;
   struct vrend_context_tweaks tweaks;
   uint8_t swizzle_output_rgb_to_bgr;
   uint8_t needs_manual_srgb_encode_bitmask;
   int fake_occlusion_query_samples_passed_multiplier;

   int prim_mode;
   bool drawing;
   struct vrend_context *parent;
   struct sysval_uniform_block sysvalue_data;
   uint32_t sysvalue_data_cookie;
   uint32_t current_program_id;
   uint32_t current_pipeline_id;
};

struct vrend_untyped_resource {
   struct virgl_resource *resource;
   struct list_head head;
};

struct vrend_context {
   char debug_name[64];

   struct list_head sub_ctxs;
   struct list_head vrend_resources;

#ifdef ENABLE_VIDEO
   struct vrend_video_context *video;
#endif

   struct vrend_sub_context *sub;
   struct vrend_sub_context *sub0;

   int ctx_id;
   /* has this ctx gotten an error? */
   bool in_error;
   bool ctx_switch_pending;

   enum virgl_ctx_errors last_error;

   /* resource bounds to this context */
   struct util_hash_table *res_hash;

   /*
    * vrend_context only works with typed virgl_resources.  More specifically,
    * it works with vrend_resources that are inherited from pipe_resources
    * wrapped in virgl_resources.
    *
    * Normally, a vrend_resource is created first by
    * vrend_renderer_resource_create.  It is then wrapped in a virgl_resource
    * by virgl_resource_create_from_pipe.  Depending on whether it is a blob
    * resource or not, the two functions can be called from different paths.
    * But we always get both a virgl_resource and a vrend_resource as a
    * result.
    *
    * It is however possible that we encounter untyped virgl_resources that
    * have no pipe_resources.  To work with untyped virgl_resources, we park
    * them in untyped_resources first when they are attached.  We move them
    * into res_hash only after we get the type information and create the
    * vrend_resources in vrend_decode_pipe_resource_set_type.
    */
   struct list_head untyped_resources;
   struct virgl_resource *untyped_resource_cache;

   struct vrend_shader_cfg shader_cfg;

   unsigned debug_flags;

   vrend_context_fence_retire fence_retire;
   void *fence_retire_data;

#ifdef ENABLE_TRACING
   struct hash_table *active_markers;
#endif
};

static int get_glsl_version(void);
static void vrend_pause_render_condition(struct vrend_context *ctx, bool pause);
static void vrend_update_viewport_state(struct vrend_sub_context *sub_ctx);
static void vrend_update_scissor_state(struct vrend_sub_context *sub_ctx);
static void vrend_destroy_query_object(void *obj_ptr);
static void vrend_finish_context_switch(struct vrend_context *ctx);
static void vrend_patch_blend_state(struct vrend_sub_context *sub_ctx);
static void vrend_update_frontface_state(struct vrend_sub_context *ctx);
static void vrend_destroy_program(struct vrend_linked_shader_program *ent);
static void vrend_apply_sampler_state(struct vrend_sub_context *sub_ctx,
                                      struct vrend_resource *res,
                                      struct vrend_sampler_state *sampler_state,
                                      GLuint sampler_id,
                                      struct vrend_sampler_view *tview);
static void vrend_object_bind_dsa_to_sub_context(struct vrend_sub_context *sub_ctx,
                                                 uint32_t handle);
static GLenum tgsitargettogltarget(const enum pipe_texture_target target, int nr_samples);

void vrend_update_stencil_state(struct vrend_sub_context *sub_ctx);

static struct vrend_format_table tex_conv_table[VIRGL_FORMAT_MAX_EXTENDED] =  {0};

static uint32_t vrend_renderer_get_video_memory(void);

static inline bool vrend_format_can_sample(enum virgl_formats format)
{
   if (tex_conv_table[format].bindings & VIRGL_BIND_SAMPLER_VIEW)
      return true;

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   uint32_t gbm_format = 0;
   if (virgl_gbm_convert_format(&format, &gbm_format))
      return false;

   if (!gbm || !gbm->device || !gbm_format)
      return false;

   uint32_t gbm_usage = GBM_BO_USE_TEXTURING;
   return gbm_device_is_format_supported(gbm->device, gbm_format, gbm_usage);
#else
   return false;
#endif
}

static inline bool vrend_format_can_readback(enum virgl_formats format)
{
   return tex_conv_table[format].flags & VIRGL_TEXTURE_CAN_READBACK;
}

static inline bool vrend_format_can_multisample(enum virgl_formats format)
{
   return tex_conv_table[format].flags & VIRGL_TEXTURE_CAN_MULTISAMPLE;
}

static inline bool vrend_format_can_render(enum virgl_formats format)
{
   return tex_conv_table[format].bindings & VIRGL_BIND_RENDER_TARGET;
}

static inline bool vrend_format_is_ds(enum virgl_formats format)
{
   return tex_conv_table[format].bindings & VIRGL_BIND_DEPTH_STENCIL;
}

static inline bool vrend_format_can_scanout(enum virgl_formats format)
{
#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   uint32_t gbm_format = 0;
   if (virgl_gbm_convert_format(&format, &gbm_format))
      return false;

   if (!gbm || !gbm->device || !gbm_format)
      return false;

   return gbm_device_is_format_supported(gbm->device, gbm_format, GBM_BO_USE_SCANOUT);
#else
   (void)format;
   return true;
#endif
}

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
static inline bool vrend_format_can_texture_view(enum virgl_formats format)
{
   return has_feature(feat_texture_view) &&
      tex_conv_table[format].flags & VIRGL_TEXTURE_CAN_TEXTURE_STORAGE;
}
#endif

struct vrend_context_tweaks *vrend_get_context_tweaks(struct vrend_context *ctx)
{
   return &ctx->sub->tweaks;
}

bool vrend_format_is_emulated_alpha(enum virgl_formats format)
{
   if (vrend_state.use_gles || !vrend_state.use_core_profile)
      return false;
   return (format == VIRGL_FORMAT_A8_UNORM ||
           format == VIRGL_FORMAT_A16_UNORM);
}

bool vrend_format_is_bgra(enum virgl_formats format) {
   return (format == VIRGL_FORMAT_B8G8R8X8_UNORM ||
           format == VIRGL_FORMAT_B8G8R8A8_UNORM ||
           format == VIRGL_FORMAT_B8G8R8X8_SRGB  ||
           format == VIRGL_FORMAT_B8G8R8A8_SRGB);
}

static GLuint vrend_resource_get_internal_format_override(const struct vrend_resource *res)
{
   /* Some shared resources imported to guest mesa as EGL images occupy 24bpp instead of more common 32bpp.
    *
    * TODO: perhaps this can be generalized to all alpha-less formats?
    */
   const enum virgl_formats format = res->base.format;
   if (has_bit(res->storage_bits, VREND_STORAGE_EGL_IMAGE)) {
      switch (format) {
      case VIRGL_FORMAT_B8G8R8X8_UNORM:
      case VIRGL_FORMAT_B8G8R8X8_SRGB:
      case VIRGL_FORMAT_R8G8B8X8_UNORM:
      case VIRGL_FORMAT_R8G8B8X8_SRGB:
         return GL_RGB8;
      case VIRGL_FORMAT_R16G16B16X16_FLOAT:
         return GL_RGB16F;
      case VIRGL_FORMAT_R16G16B16X16_UNORM:
         return GL_RGB16;
      default:
         ;
      }
   }
   return GL_NONE;
}

static bool vrend_resource_supports_view(const struct vrend_resource *res,
                                         UNUSED enum virgl_formats view_format)
{
   /* Texture views on eglimage-backed bgr* resources are not supported and
    * lead to unexpected format interpretation since internally allocated
    * bgr* resources use GL_RGBA8 internal format, while eglimage-backed
    * resources use BGRA8, but GL lacks an equivalent internalformat enum.
    *
    * For views that don't require colorspace conversion, we can add swizzles
    * instead. For views that do require colorspace conversion, manual srgb
    * decode/encode is required. */
   return !(vrend_format_is_bgra(res->base.format) &&
            has_bit(res->storage_bits, VREND_STORAGE_EGL_IMAGE)) &&
         (vrend_resource_get_internal_format_override(res) == GL_NONE);
}

static inline bool
vrend_resource_needs_redblue_swizzle(struct vrend_resource *res,
                                     enum virgl_formats view_format)
{
   return !vrend_resource_supports_view(res, view_format) &&
         vrend_format_is_bgra(res->base.format) ^ vrend_format_is_bgra(view_format);
}

static inline bool
vrend_resource_needs_srgb_decode(struct vrend_resource *res,
                                 enum virgl_formats view_format)
{
   return !vrend_resource_supports_view(res, view_format) &&
      util_format_is_srgb(res->base.format) &&
      !util_format_is_srgb(view_format);
}

static inline bool
vrend_resource_needs_srgb_encode(struct vrend_resource *res,
                                 enum virgl_formats view_format)
{
   return !vrend_resource_supports_view(res, view_format) &&
      !util_format_is_srgb(res->base.format) &&
      util_format_is_srgb(view_format);
}

static bool vrend_blit_needs_swizzle(enum virgl_formats src,
                                     enum virgl_formats dst)
{
   for (int i = 0; i < 4; ++i) {
      if (tex_conv_table[src].swizzle[i] != tex_conv_table[dst].swizzle[i])
         return true;
   }
   return false;
}

static inline const char *pipe_shader_to_prefix(enum pipe_shader_type shader_type)
{
   switch (shader_type) {
   case PIPE_SHADER_VERTEX: return "vs";
   case PIPE_SHADER_FRAGMENT: return "fs";
   case PIPE_SHADER_GEOMETRY: return "gs";
   case PIPE_SHADER_TESS_CTRL: return "tc";
   case PIPE_SHADER_TESS_EVAL: return "te";
   case PIPE_SHADER_COMPUTE: return "cs";
   default:
      return NULL;
   };
}

static GLenum translate_blend_func_advanced(enum gl_advanced_blend_mode blend)
{
   switch(blend){
   case BLEND_MULTIPLY: return GL_MULTIPLY_KHR;
   case BLEND_SCREEN: return GL_SCREEN_KHR;
   case BLEND_OVERLAY: return GL_OVERLAY_KHR;
   case BLEND_DARKEN: return GL_DARKEN_KHR;
   case BLEND_LIGHTEN: return GL_LIGHTEN_KHR;
   case BLEND_COLORDODGE: return GL_COLORDODGE_KHR;
   case BLEND_COLORBURN: return GL_COLORBURN_KHR;
   case BLEND_HARDLIGHT: return GL_HARDLIGHT_KHR;
   case BLEND_SOFTLIGHT: return GL_SOFTLIGHT_KHR;
   case BLEND_DIFFERENCE: return GL_DIFFERENCE_KHR;
   case BLEND_EXCLUSION: return GL_EXCLUSION_KHR;
   case BLEND_HSL_HUE: return GL_HSL_HUE_KHR;
   case BLEND_HSL_SATURATION: return GL_HSL_SATURATION_KHR;
   case BLEND_HSL_COLOR: return GL_HSL_COLOR_KHR;
   case BLEND_HSL_LUMINOSITY: return GL_HSL_LUMINOSITY_KHR;
   default:
      assert("invalid blend token()" == NULL);
      return 0;
   }
}

static const char *vrend_ctx_error_strings[] = {
   [VIRGL_ERROR_CTX_NONE]                  = "None",
   [VIRGL_ERROR_CTX_UNKNOWN]               = "Unknown",
   [VIRGL_ERROR_CTX_ILLEGAL_SHADER]        = "Illegal shader",
   [VIRGL_ERROR_CTX_ILLEGAL_HANDLE]        = "Illegal handle",
   [VIRGL_ERROR_CTX_ILLEGAL_RESOURCE]      = "Illegal resource",
   [VIRGL_ERROR_CTX_ILLEGAL_SURFACE]       = "Illegal surface",
   [VIRGL_ERROR_CTX_ILLEGAL_VERTEX_FORMAT] = "Illegal vertex format",
   [VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER]    = "Illegal command buffer",
   [VIRGL_ERROR_CTX_GLES_HAVE_TES_BUT_MISS_TCS] = "On GLES context and shader program has tesselation evaluation shader but no tesselation control shader",
   [VIRGL_ERROR_GL_ANY_SAMPLES_PASSED] = "Query for ANY_SAMPLES_PASSED not supported",
   [VIRGL_ERROR_CTX_ILLEGAL_FORMAT]        = "Illegal format ID",
   [VIRGL_ERROR_CTX_ILLEGAL_SAMPLER_VIEW_TARGET] = "Illegat target for sampler view",
   [VIRGL_ERROR_CTX_TRANSFER_IOV_BOUNDS]   = "IOV data size exceeds resource capacity",
   [VIRGL_ERROR_CTX_ILLEGAL_DUAL_SRC_BLEND]= "Dual source blend not supported",
   [VIRGL_ERROR_CTX_UNSUPPORTED_FUNCTION]  = "Unsupported host function called",
   [VIRGL_ERROR_CTX_ILLEGAL_PROGRAM_PIPELINE] = "Illegal shader program pipeline",
   [VIRGL_ERROR_CTX_TOO_MANY_VERTEX_ATTRIBUTES] = "Too many vertex attributes are requested",
   [VIRGL_ERROR_CTX_UNSUPPORTED_TEX_WRAP] = "Unsupported texture mirror wrapping, default to GL_MIRROR_REPEAT",
   [VIRGL_ERROR_CTX_CUBE_MAP_FACE_OUT_OF_RANGE] = "Cube map face out of range:",
   [VIRGL_ERROR_CTX_BLIT_AREA_OUT_OF_RANGE] = "Blit z-slices out of range;",
   [VIRGL_ERROR_CTX_SSBO_BINDING_RANGE] = "SSBO binding out of range for resource",
   [VIRGL_ERROR_CTX_RESOURCE_OUT_OF_RANGE] = "Resource copy out of range for resource",
};

void vrend_report_context_error_internal(const char *fname, struct vrend_context *ctx,
                                         enum virgl_ctx_errors error, uint32_t value)
{
   ctx->in_error = true;
   ctx->last_error = error;
   virgl_error("%s: context error reported %d \"%s\" %s %d\n", fname,
               ctx->ctx_id, ctx->debug_name, vrend_ctx_error_strings[error],
               value);
}

#define CORE_PROFILE_WARN_NONE 0
#define CORE_PROFILE_WARN_STIPPLE 1
#define CORE_PROFILE_WARN_POLYGON_MODE 2
#define CORE_PROFILE_WARN_TWO_SIDE 3
#define CORE_PROFILE_WARN_CLAMP 4
#define CORE_PROFILE_WARN_SHADE_MODEL 5

static const char *vrend_core_profile_warn_strings[] = {
   [CORE_PROFILE_WARN_NONE]         = "None",
   [CORE_PROFILE_WARN_STIPPLE]      = "Stipple",
   [CORE_PROFILE_WARN_POLYGON_MODE] = "Polygon Mode",
   [CORE_PROFILE_WARN_TWO_SIDE]     = "Two Side",
   [CORE_PROFILE_WARN_CLAMP]        = "Clamping",
   [CORE_PROFILE_WARN_SHADE_MODEL]  = "Shade Model",
};

static void __report_core_warn(const char *fname, struct vrend_context *ctx,
                               enum virgl_ctx_errors error)
{
   virgl_warn("%s: core profile violation reported %d \"%s\" %s\n", fname,
              ctx->ctx_id, ctx->debug_name,
              vrend_core_profile_warn_strings[error]);
}
#define report_core_warn(ctx, error) __report_core_warn(__func__, ctx, error)


#define GLES_WARN_NONE 0
#define GLES_WARN_STIPPLE 1
#define GLES_WARN_POLYGON_MODE 2
#define GLES_WARN_DEPTH_RANGE 3
#define GLES_WARN_POINT_SIZE 4
#define GLES_WARN_SEAMLESS_CUBE_MAP 5
#define GLES_WARN_LOD_BIAS 6
#define GLES_WARN_OFFSET_LINE 8
#define GLES_WARN_OFFSET_POINT 9
//#define GLES_WARN_ free slot 10
#define GLES_WARN_FLATSHADE_FIRST 11
#define GLES_WARN_LINE_SMOOTH 12
#define GLES_WARN_POLY_SMOOTH 13
#define GLES_WARN_DEPTH_CLEAR 14
#define GLES_WARN_LOGIC_OP 15
#define GLES_WARN_TIMESTAMP 16
#define GLES_WARN_IMPLICIT_MSAA_SURFACE 17

ASSERTED
static const char *vrend_gles_warn_strings[] = {
   [GLES_WARN_NONE]                  = "None",
   [GLES_WARN_STIPPLE]               = "Stipple",
   [GLES_WARN_POLYGON_MODE]          = "Polygon Mode",
   [GLES_WARN_DEPTH_RANGE]           = "Depth Range",
   [GLES_WARN_POINT_SIZE]            = "Point Size",
   [GLES_WARN_SEAMLESS_CUBE_MAP]     = "Seamless Cube Map",
   [GLES_WARN_LOD_BIAS]              = "Lod Bias",
   [GLES_WARN_OFFSET_LINE]           = "Offset Line",
   [GLES_WARN_OFFSET_POINT]          = "Offset Point",
   [GLES_WARN_FLATSHADE_FIRST]       = "Flatshade First",
   [GLES_WARN_LINE_SMOOTH]           = "Line Smooth",
   [GLES_WARN_POLY_SMOOTH]           = "Poly Smooth",
   [GLES_WARN_DEPTH_CLEAR]           = "Depth Clear",
   [GLES_WARN_LOGIC_OP]              = "LogicOp",
   [GLES_WARN_TIMESTAMP]             = "GL_TIMESTAMP",
   [GLES_WARN_IMPLICIT_MSAA_SURFACE] = "Implicit MSAA Surface",
};

static void __report_gles_warn(ASSERTED const char *fname,
                               ASSERTED struct vrend_context *ctx,
                               ASSERTED enum virgl_ctx_errors error)
{
   VREND_DEBUG(dbg_gles, ctx, "%s: GLES violation - %s\n", fname, vrend_gles_warn_strings[error]);
}
#define report_gles_warn(ctx, error) __report_gles_warn(__func__, ctx, error)

static void __report_gles_missing_func(ASSERTED const char *fname,
                                       ASSERTED struct vrend_context *ctx,
                                       ASSERTED const char *missf)
{
   VREND_DEBUG(dbg_gles, ctx, "%s: GLES function %s is missing\n", fname, missf);
}

#define report_gles_missing_func(ctx, missf) __report_gles_missing_func(__func__, ctx, missf)

static void buffered_logger(const char *fmt,
                            va_list ap,
                            void *user_data)
{
   struct vrend_strbuf *logger_buffer = user_data;
   strbuf_vappendf(logger_buffer, fmt, ap);
}

static void vrend_dump_tgsi(const struct tgsi_token *tokens,
                            unsigned flags)
{
   struct vrend_strbuf logger_buffer = {0};
   strbuf_alloc(&logger_buffer, STRBUF_MIN_MALLOC);

   tgsi_dump_with_logger(tokens, flags, buffered_logger, &logger_buffer);
   virgl_debug("%s", logger_buffer.buf);
   strbuf_free(&logger_buffer);
}

static void init_features(int gl_ver, int gles_ver)
{
   for (enum features_id id = 0; id < feat_last; id++) {
      if (gl_ver >= feature_list[id].gl_ver ||
          gles_ver >= feature_list[id].gles_ver) {
         set_feature(id);
         VREND_DEBUG(dbg_features, NULL, "Host feature %s provided by %s %3.1f\n",
                     feature_list[id].log_name, (gl_ver > 0 ? "GL" : "GLES"),
                     0.1f * (gl_ver > 0 ? gl_ver : gles_ver));
      } else {
         for (uint32_t i = 0; i < FEAT_MAX_EXTS; i++) {
            if (!feature_list[id].gl_ext[i])
               break;
            if (epoxy_has_gl_extension(feature_list[id].gl_ext[i])) {
               set_feature(id);
               VREND_DEBUG(dbg_features, NULL,
                           "Host feature %s provide by %s\n", feature_list[id].log_name,
                           feature_list[id].gl_ext[i]);
               break;
            }
         }
      }
   }
}

static void vrend_destroy_surface(struct vrend_surface *surf)
{
   if (surf->gl_id != surf->texture->gl_id)
      glDeleteTextures(1, &surf->gl_id);
   vrend_resource_reference(&surf->texture, NULL);
   free(surf);
}

static inline void
vrend_surface_reference(struct vrend_surface **ptr, struct vrend_surface *surf)
{
   struct vrend_surface *old_surf = *ptr;

   if (pipe_reference((struct pipe_reference *)*ptr, (struct pipe_reference *)surf))
      vrend_destroy_surface(old_surf);
   *ptr = surf;
}

static void vrend_destroy_sampler_view(struct vrend_sampler_view *samp)
{
   if (samp->texture->gl_id != samp->gl_id)
      glDeleteTextures(1, &samp->gl_id);
   vrend_resource_reference(&samp->texture, NULL);
   free(samp);
}

static inline void
vrend_sampler_view_reference(struct vrend_sampler_view **ptr, struct vrend_sampler_view *view)
{
   struct vrend_sampler_view *old_view = *ptr;

   if (pipe_reference((struct pipe_reference *)*ptr, (struct pipe_reference *)view))
      vrend_destroy_sampler_view(old_view);
   *ptr = view;
}

static void vrend_destroy_so_target(struct vrend_so_target *target)
{
   vrend_resource_reference(&target->buffer, NULL);
   free(target);
}

static inline void
vrend_so_target_reference(struct vrend_so_target **ptr, struct vrend_so_target *target)
{
   struct vrend_so_target *old_target = *ptr;

   if (pipe_reference((struct pipe_reference *)*ptr, (struct pipe_reference *)target))
      vrend_destroy_so_target(old_target);
   *ptr = target;
}

static void vrend_shader_dump(struct vrend_shader *shader)
{
   const char *prefix = pipe_shader_to_prefix(shader->sel->type);
   if (shader->sel->tokens) {
      virgl_debug("%s: %d TGSI:\n", prefix, shader->id);
      vrend_dump_tgsi(shader->sel->tokens, 0);
   }

   virgl_debug("%s: %d GLSL:\n", prefix, shader->id);
   strarray_dump_with_line_numbers(&shader->glsl_strings);
   virgl_debug("\n");
}

static void vrend_shader_destroy(struct vrend_shader *shader)
{
   list_for_each_entry_safe(struct vrend_linked_shader_program, ent, &shader->programs, sl[shader->sel->type])
      vrend_destroy_program(ent);

   if (shader->sel->sinfo.separable_program)
       glDeleteProgram(shader->program_id);
   glDeleteShader(shader->id);
   strarray_free(&shader->glsl_strings, true);
   free(shader);
}

static void vrend_destroy_shader_selector(struct vrend_shader_selector *sel)
{
   struct vrend_shader *p = sel->current, *c;
   unsigned i;
   while (p) {
      c = p->next_variant;
      vrend_shader_destroy(p);
      p = c;
   }
   if (sel->sinfo.so_names)
      for (i = 0; i < sel->sinfo.so_info.num_outputs; i++)
         free(sel->sinfo.so_names[i]);
   free(sel->sinfo.so_names);
   free(sel->sinfo.sampler_arrays);
   free(sel->sinfo.image_arrays);
   free(sel->tokens);
   free(sel);
}

static inline void
vrend_shader_state_reference(struct vrend_shader_selector **ptr, struct vrend_shader_selector *shader)
{
   struct vrend_shader_selector *old_shader = *ptr;

   if (pipe_reference((struct pipe_reference *)*ptr, (struct pipe_reference *)shader))
      vrend_destroy_shader_selector(old_shader);
   *ptr = shader;
}

static void vrend_destroy_long_shader_buffer(struct vrend_long_shader_buffer *lsbuf)
{
   vrend_shader_state_reference(&lsbuf->sel, NULL);
   free(lsbuf->tmp_buf);
   free(lsbuf);
}

static inline int conv_shader_type(int type)
{
   switch (type) {
   case PIPE_SHADER_VERTEX: return GL_VERTEX_SHADER;
   case PIPE_SHADER_FRAGMENT: return GL_FRAGMENT_SHADER;
   case PIPE_SHADER_GEOMETRY: return GL_GEOMETRY_SHADER;
   case PIPE_SHADER_TESS_CTRL: return GL_TESS_CONTROL_SHADER;
   case PIPE_SHADER_TESS_EVAL: return GL_TESS_EVALUATION_SHADER;
   case PIPE_SHADER_COMPUTE: return GL_COMPUTE_SHADER;
   default:
      return 0;
   };
}

static bool vrend_compile_shader(struct vrend_sub_context *sub_ctx,
                                 struct vrend_shader *shader)
{
   GLint param;
   const char *shader_parts[SHADER_MAX_STRINGS];

   for (int i = 0; i < shader->glsl_strings.num_strings; i++)
      shader_parts[i] = shader->glsl_strings.strings[i].buf;

   shader->id = glCreateShader(conv_shader_type(shader->sel->type));
   glShaderSource(shader->id, shader->glsl_strings.num_strings, shader_parts, NULL);
   glCompileShader(shader->id);
   glGetShaderiv(shader->id, GL_COMPILE_STATUS, &param);
   if (param == GL_FALSE) {
      char infolog[65536];
      int len;
      glGetShaderInfoLog(shader->id, 65536, &len, infolog);
      vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_SHADER, 0);
      virgl_error("Shader failed to compile\n%s\n", infolog);
      vrend_shader_dump(shader);
      return false;
   }

   if (shader->sel->sinfo.separable_program) {
       shader->program_id = glCreateProgram();
       shader->last_pipeline_id = 0xffffffff;
       glProgramParameteri(shader->program_id, GL_PROGRAM_SEPARABLE, GL_TRUE);
       glAttachShader(shader->program_id, shader->id);
   }

   shader->is_compiled = true;
   return true;
}

void
vrend_insert_format(struct vrend_format_table *entry, uint32_t bindings, uint32_t flags)
{
   tex_conv_table[entry->format] = *entry;
   tex_conv_table[entry->format].bindings = bindings;
   tex_conv_table[entry->format].flags = flags;
}

void
vrend_insert_format_swizzle(int override_format, struct vrend_format_table *entry,
                            uint32_t bindings, enum pipe_swizzle swizzle[4], uint32_t flags)
{
   int i;
   tex_conv_table[override_format] = *entry;
   tex_conv_table[override_format].bindings = bindings;
   tex_conv_table[override_format].flags = flags | VIRGL_TEXTURE_NEED_SWIZZLE;
   for (i = 0; i < 4; i++)
      tex_conv_table[override_format].swizzle[i] = swizzle[i];
}

const struct vrend_format_table *
vrend_get_format_table_entry(enum virgl_formats format)
{
   return &tex_conv_table[format];
}

static bool vrend_is_timer_query(GLenum gltype)
{
   return gltype == GL_TIMESTAMP ||
      gltype == GL_TIME_ELAPSED;
}

static void vrend_use_program(struct vrend_linked_shader_program *program)
{
   GLuint id = !program ? 0 :
                          program->is_pipeline ? program->id.pipeline :
                                                 program->id.program;
   if (program && program->is_pipeline) {
      glUseProgram(0);
      glBindProgramPipeline(id);
   } else {
       if (has_feature(feat_separate_shader_objects))
          glBindProgramPipeline(0);
       glUseProgram(id);
   }
}

static void vrend_depth_test_enable(struct vrend_sub_context *sub_ctx, bool depth_test_enable)
{
   if (sub_ctx->depth_test_enabled != depth_test_enable) {
      sub_ctx->depth_test_enabled = depth_test_enable;
      if (depth_test_enable)
         glEnable(GL_DEPTH_TEST);
      else
         glDisable(GL_DEPTH_TEST);
   }
}

static void vrend_alpha_test_enable(struct vrend_sub_context *sub_ctx, bool alpha_test_enable)
{
   if (vrend_state.use_core_profile) {
      /* handled in shaders */
      return;
   }
   if (sub_ctx->alpha_test_enabled != alpha_test_enable) {
      sub_ctx->alpha_test_enabled = alpha_test_enable;
      if (alpha_test_enable)
         glEnable(GL_ALPHA_TEST);
      else
         glDisable(GL_ALPHA_TEST);
   }
}

static void vrend_stencil_test_enable(struct vrend_sub_context *sub_ctx, bool stencil_test_enable)
{
   if (sub_ctx->stencil_test_enabled != stencil_test_enable) {
      sub_ctx->stencil_test_enabled = stencil_test_enable;
      if (stencil_test_enable)
         glEnable(GL_STENCIL_TEST);
      else
         glDisable(GL_STENCIL_TEST);
   }
}

ASSERTED
static void dump_stream_out(struct pipe_stream_output_info *so)
{
   unsigned i;
   if (!so)
      return;
   virgl_debug("streamout: %d\n", so->num_outputs);
   virgl_debug("strides: ");
   for (i = 0; i < 4; i++)
      virgl_debug("%d ", so->stride[i]);
   virgl_debug("\n");
   virgl_debug("outputs:\n");
   for (i = 0; i < so->num_outputs; i++) {
      virgl_debug("\t%d: reg: %d sc: %d, nc: %d ob: %d do: %d st: %d\n",
                  i,
                  so->output[i].register_index,
                  so->output[i].start_component,
                  so->output[i].num_components,
                  so->output[i].output_buffer,
                  so->output[i].dst_offset,
                  so->output[i].stream);
   }
}

static char *get_skip_str(int *skip_val)
{
   char *start_skip = NULL;
   if (*skip_val < 0) {
      *skip_val = 0;
      return NULL;
   }

   if (*skip_val == 1) {
      start_skip = strdup("gl_SkipComponents1");
      *skip_val -= 1;
   } else if (*skip_val == 2) {
      start_skip = strdup("gl_SkipComponents2");
      *skip_val -= 2;
   } else if (*skip_val == 3) {
      start_skip = strdup("gl_SkipComponents3");
      *skip_val -= 3;
   } else if (*skip_val >= 4) {
      start_skip = strdup("gl_SkipComponents4");
      *skip_val -= 4;
   }
   return start_skip;
}

static void set_stream_out_varyings(ASSERTED struct vrend_sub_context *sub_ctx,
                                    int prog_id,
                                    struct vrend_shader_info *sinfo)
{
   struct pipe_stream_output_info *so = &sinfo->so_info;
   char *varyings[PIPE_MAX_SHADER_OUTPUTS*2];
   int j;
   unsigned i, n_outputs = 0;
   int last_buffer = 0;
   char *start_skip;
   int buf_offset = 0;
   int skip;
   if (!so->num_outputs)
      return;

   VREND_DEBUG_EXT(dbg_shader_streamout, sub_ctx->parent, dump_stream_out(so));

   for (i = 0; i < so->num_outputs; i++) {
      if (last_buffer != so->output[i].output_buffer) {

         skip = so->stride[last_buffer] - buf_offset;
         while (skip && n_outputs < ARRAY_SIZE(varyings)) {
            start_skip = get_skip_str(&skip);
            if (start_skip)
               varyings[n_outputs++] = start_skip;
         }
         for (j = last_buffer; j < so->output[i].output_buffer && n_outputs < ARRAY_SIZE(varyings); j++)
            varyings[n_outputs++] = strdup("gl_NextBuffer");
         last_buffer = so->output[i].output_buffer;
         buf_offset = 0;
      }

      skip = so->output[i].dst_offset - buf_offset;
      while (skip && n_outputs < ARRAY_SIZE(varyings)) {
         start_skip = get_skip_str(&skip);
         if (start_skip)
            varyings[n_outputs++] = start_skip;
      }
      buf_offset = so->output[i].dst_offset;

      buf_offset += so->output[i].num_components;
      if (sinfo->so_names[i] && n_outputs < ARRAY_SIZE(varyings))
         varyings[n_outputs++] = strdup(sinfo->so_names[i]);
   }

   skip = so->stride[last_buffer] - buf_offset;
   while (skip && n_outputs < ARRAY_SIZE(varyings)) {
      start_skip = get_skip_str(&skip);
      if (start_skip)
         varyings[n_outputs++] = start_skip;
   }

   glTransformFeedbackVaryings(prog_id, n_outputs,
                               (const GLchar **)varyings, GL_INTERLEAVED_ATTRIBS_EXT);

   for (i = 0; i < n_outputs; i++)
      if (varyings[i])
         free(varyings[i]);
}

static inline int
vrend_get_uniform_location(struct vrend_linked_shader_program *sprog,
                           char *name, int shader_type)
{
    assert(!sprog->is_pipeline || sprog->ss[shader_type]->sel->sinfo.separable_program);

    GLint id = sprog->is_pipeline ?
                  sprog->ss[shader_type]->program_id :
                  sprog->id.program;

    return glGetUniformLocation(id, name);
}

static inline void
vrend_set_active_pipeline_stage(struct vrend_linked_shader_program *sprog, int shader_type)
{
    if (sprog->is_pipeline && sprog->ss[shader_type])
        glActiveShaderProgram(sprog->id.pipeline, sprog->ss[shader_type]->program_id);
}

static int bind_sampler_locs(struct vrend_linked_shader_program *sprog,
                             enum pipe_shader_type shader_type, int next_sampler_id)
{
   const struct vrend_shader_info *sinfo = &sprog->ss[shader_type]->sel->sinfo;

   if (sinfo->samplers_used_mask) {
      unsigned nsamp = util_bitcount(sinfo->samplers_used_mask);
      sprog->sampler_locs[shader_type] = calloc(nsamp, sizeof(GLuint));
      if (sinfo->shadow_samp_mask) {
         sprog->shadow_samp_mask_locs[shader_type] = calloc(nsamp, sizeof(GLuint));
         sprog->shadow_samp_add_locs[shader_type] = calloc(nsamp, sizeof(GLuint));
      } else {
         sprog->shadow_samp_mask_locs[shader_type] = sprog->shadow_samp_add_locs[shader_type] = NULL;
      }

      const char *prefix = pipe_shader_to_prefix(shader_type);
      int sampler_index = 0;
      uint32_t mask = sinfo->samplers_used_mask;
      while(mask) {
         uint32_t i = u_bit_scan(&mask);
         char name[64];
         if (sinfo->num_sampler_arrays) {
            int arr_idx = vrend_shader_lookup_sampler_array(sinfo, i);
            snprintf(name, 32, "%ssamp%d[%d]", prefix, arr_idx, i - arr_idx);
         } else
            snprintf(name, 32, "%ssamp%d", prefix, i);

         vrend_set_active_pipeline_stage(sprog, shader_type);
         sprog->sampler_locs[shader_type][sampler_index] =
            vrend_get_uniform_location(sprog, name, shader_type);

         if (sinfo->shadow_samp_mask & (1 << i)) {
            snprintf(name, 32, "%sshadmask%d", prefix, i);
            sprog->shadow_samp_mask_locs[shader_type][sampler_index] =
               vrend_get_uniform_location(sprog, name, shader_type);
            snprintf(name, 32, "%sshadadd%d", prefix, i);
            sprog->shadow_samp_add_locs[shader_type][sampler_index] =
               vrend_get_uniform_location(sprog, name, shader_type);
         }
         sampler_index++;
      }
   } else {
      sprog->sampler_locs[shader_type] = NULL;
      sprog->shadow_samp_mask_locs[shader_type] = NULL;
      sprog->shadow_samp_add_locs[shader_type] = NULL;
      assert(!sinfo->shadow_samp_mask);
   }
   sprog->samplers_used_mask[shader_type] = sinfo->samplers_used_mask;
   sprog->shadow_samp_mask[shader_type] = sinfo->shadow_samp_mask;

   return next_sampler_id;
}

static void bind_const_locs(struct vrend_linked_shader_program *sprog,
                            enum pipe_shader_type shader_type)
{
  if (sprog->ss[shader_type]->sel->sinfo.num_consts) {
     char name[32];
     snprintf(name, 32, "%sconst0", pipe_shader_to_prefix(shader_type));
     sprog->const_location[shader_type] = vrend_get_uniform_location(sprog, name,
                                                                     shader_type);
  } else
     sprog->const_location[shader_type] = -1;
}

static inline GLuint
vrend_get_uniform_block_index(struct vrend_linked_shader_program *sprog,
                              char *name, int shader_type)
{
    assert(!sprog->is_pipeline || sprog->ss[shader_type]->sel->sinfo.separable_program);

    GLuint id = sprog->is_pipeline ?
                  sprog->ss[shader_type]->program_id :
                  sprog->id.program;

    return glGetUniformBlockIndex(id, name);
}

static inline void
vrend_uniform_block_binding(struct vrend_linked_shader_program *sprog,
                            int shader_type, GLuint loc, GLuint value)
{
    assert(!sprog->is_pipeline || sprog->ss[shader_type]->sel->sinfo.separable_program);

    GLint id = sprog->is_pipeline ?
                  sprog->ss[shader_type]->program_id :
                  sprog->id.program;

    glUniformBlockBinding(id, loc, value);
}

static unsigned bind_ubo_locs(struct vrend_linked_shader_program *sprog,
                              enum pipe_shader_type shader_type, GLuint next_ubo_id)
{
   const struct vrend_shader_info *sinfo = &sprog->ss[shader_type]->sel->sinfo;
   if (sinfo->ubo_used_mask) {
      const char *prefix = pipe_shader_to_prefix(shader_type);

      unsigned mask = sinfo->ubo_used_mask;
      while (mask) {
         uint32_t ubo_idx = u_bit_scan(&mask);
         char name[32];
         if (sinfo->ubo_indirect)
            snprintf(name, 32, "%subo[%d]", prefix, ubo_idx - 1);
         else
            snprintf(name, 32, "%subo%d", prefix, ubo_idx);

         GLuint loc = vrend_get_uniform_block_index(sprog, name, shader_type);
         vrend_uniform_block_binding(sprog, shader_type, loc, next_ubo_id++);
      }
   }

   sprog->ubo_used_mask[shader_type] = sinfo->ubo_used_mask;

   return next_ubo_id;
}

static void bind_virgl_block_loc(struct vrend_linked_shader_program *sprog,
                                 enum pipe_shader_type shader_type,
                                 GLuint virgl_block_ubo_id)
{
   sprog->separate_virgl_block_id[shader_type] =
      vrend_get_uniform_block_index(sprog, "VirglBlock", shader_type);

   if (sprog->separate_virgl_block_id[shader_type] != GL_INVALID_INDEX) {
      bool created_virgl_block_buffer = false;

      if (sprog->virgl_block_bind == GL_INVALID_INDEX) {
         sprog->virgl_block_bind = virgl_block_ubo_id;
         if (sprog->ubo_sysval_buffer_id == GL_INVALID_INDEX) {
             glGenBuffers(1, &sprog->ubo_sysval_buffer_id);
             created_virgl_block_buffer = true;
         }
      }

      vrend_set_active_pipeline_stage(sprog, shader_type);
      vrend_uniform_block_binding(sprog, shader_type,
                                  sprog->separate_virgl_block_id[shader_type],
                                  sprog->virgl_block_bind);

      GLint virgl_block_size;
      int prog_id = sprog->is_pipeline ? sprog->ss[shader_type]->program_id :
                                         sprog->id.program;
      glGetActiveUniformBlockiv(prog_id, sprog->separate_virgl_block_id[shader_type],
                                GL_UNIFORM_BLOCK_DATA_SIZE, &virgl_block_size);
      assert((size_t) virgl_block_size >= sizeof(struct sysval_uniform_block));

      if (created_virgl_block_buffer) {
         glBindBuffer(GL_UNIFORM_BUFFER, sprog->ubo_sysval_buffer_id);
         glBufferData(GL_UNIFORM_BUFFER, virgl_block_size, NULL, GL_DYNAMIC_DRAW);
         glBindBuffer(GL_UNIFORM_BUFFER, 0);
      }
   }
}

static void rebind_ubo_and_sampler_locs(struct vrend_linked_shader_program *sprog,
                                        enum pipe_shader_type last_shader)
{
   int next_sampler_id = 0;
   GLuint next_ubo_id = 0;

   for (enum pipe_shader_type shader_type = PIPE_SHADER_VERTEX;
        shader_type <= last_shader;
        shader_type++) {
      if (!sprog->ss[shader_type])
         continue;

      next_sampler_id = bind_sampler_locs(sprog, shader_type, next_sampler_id);
      next_ubo_id = bind_ubo_locs(sprog, shader_type, next_ubo_id);

      if (sprog->is_pipeline)
         sprog->ss[shader_type]->last_pipeline_id = sprog->id.pipeline;
   }

   /* Now `next_ubo_id` is the last ubo id, which is used for the VirglBlock. */
   sprog->virgl_block_bind = GL_INVALID_INDEX;
   for (enum pipe_shader_type shader_type = PIPE_SHADER_VERTEX;
        shader_type <= last_shader;
        shader_type++) {
      if (!sprog->ss[shader_type])
         continue;

      bind_virgl_block_loc(sprog, shader_type, next_ubo_id);
   }
}

static void bind_ssbo_locs(struct vrend_linked_shader_program *sprog,
                           enum pipe_shader_type shader_type)
{
   if (!has_feature(feat_ssbo))
      return;
   sprog->ssbo_used_mask[shader_type] = sprog->ss[shader_type]->sel->sinfo.ssbo_used_mask;
}

static void bind_image_locs(struct vrend_linked_shader_program *sprog,
                            enum pipe_shader_type shader_type)
{
   int i;
   char name[32];
   const char *prefix = pipe_shader_to_prefix(shader_type);
   const struct vrend_shader_info *sinfo = &sprog->ss[shader_type]->sel->sinfo;

   uint32_t mask = sinfo->images_used_mask;
   if (!mask && !sinfo->num_image_arrays)
      return;

   if (!has_feature(feat_images))
      return;

   int nsamp = util_last_bit(mask);
   if (nsamp) {
      sprog->img_locs[shader_type] = calloc(nsamp, sizeof(GLint));
      if (!sprog->img_locs[shader_type])
         return;
   } else
      sprog->img_locs[shader_type] = NULL;

   if (sinfo->num_image_arrays) {
      for (i = 0; i < sinfo->num_image_arrays; i++) {
         struct vrend_array *img_array = &sinfo->image_arrays[i];
         for (int j = 0; j < img_array->array_size; j++) {
            snprintf(name, 32, "%simg%d[%d]", prefix, img_array->first, j);
            sprog->img_locs[shader_type][img_array->first + j] =
               vrend_get_uniform_location(sprog, name, shader_type);
            if (sprog->img_locs[shader_type][img_array->first + j] == -1)
               virgl_error("Failed to get uniform loc for image %s\n", name);
         }
      }
   } else if (mask) {
      for (i = 0; i < nsamp; i++) {
         if (mask & (1 << i)) {
            snprintf(name, 32, "%simg%d", prefix, i);
            sprog->img_locs[shader_type][i] =
               vrend_get_uniform_location(sprog, name, shader_type);
            if (sprog->img_locs[shader_type][i] == -1)
               virgl_error("Failed to get uniform loc for image %s\n", name);
         } else {
            sprog->img_locs[shader_type][i] = -1;
         }
      }
   }
   sprog->images_used_mask[shader_type] = mask;
}

static bool vrend_link(GLuint id)
{
   GLint lret;
   glLinkProgram(id);
   glGetProgramiv(id, GL_LINK_STATUS, &lret);
   if (lret == GL_FALSE) {
      char infolog[65536];
      int len;
      glGetProgramInfoLog(id, 65536, &len, infolog);
      virgl_error("Error linking program:\n%s\n", infolog);
      return false;
   }
   return true;
}

static bool vrend_link_separable_shader(struct vrend_sub_context *sub_ctx,
                                        struct vrend_shader *shader, int type)
{
   int i;
   char name[64];

   if (type == PIPE_SHADER_VERTEX || type == PIPE_SHADER_GEOMETRY ||
       type == PIPE_SHADER_TESS_EVAL)
       set_stream_out_varyings(sub_ctx, shader->program_id, &shader->sel->sinfo);

   if (type == PIPE_SHADER_FRAGMENT && shader->sel->sinfo.num_outputs > 1) {
      bool dual_src_linked = util_blend_state_is_dual(&sub_ctx->blend_state, 0);
      if (dual_src_linked) {
         if (has_feature(feat_dual_src_blend)) {
            if (!vrend_state.use_gles) {
               glBindFragDataLocationIndexed(shader->program_id, 0, 0, "fsout_c0");
               glBindFragDataLocationIndexed(shader->program_id, 0, 1, "fsout_c1");
            } else {
               glBindFragDataLocationIndexedEXT(shader->program_id, 0, 0, "fsout_c0");
               glBindFragDataLocationIndexedEXT(shader->program_id, 0, 1, "fsout_c1");
            }
         } else {
            vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_DUAL_SRC_BLEND, 0);
         }
      } else if (!vrend_state.use_gles && has_feature(feat_dual_src_blend)) {
         /* On GLES without dual source blending we emit the layout directly in the shader
          * so there is no need to define the binding here */
         for (int i = 0; i < shader->sel->sinfo.num_outputs; ++i) {
            if (shader->sel->sinfo.fs_output_layout[i] >= 0) {
               char buf[64];
               snprintf(buf, sizeof(buf), "fsout_c%d",
                        shader->sel->sinfo.fs_output_layout[i]);
               glBindFragDataLocationIndexed(shader->program_id,
                                             shader->sel->sinfo.fs_output_layout[i],
                                             0, buf);
            }
         }
      }
   }

   if (type == PIPE_SHADER_VERTEX && has_feature(feat_gles31_vertex_attrib_binding)) {
      uint32_t mask = shader->sel->sinfo.attrib_input_mask;
      while (mask) {
         i = u_bit_scan(&mask);
         snprintf(name, 32, "in_%d", i);
         glBindAttribLocation(shader->program_id, i, name);
      }
   }

   shader->is_linked = vrend_link(shader->program_id);

   if (!shader->is_linked) {
      /* dump shaders */
      vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_SHADER, 0);
      vrend_shader_dump(shader);
   }

   return shader->is_linked;
}

static struct vrend_linked_shader_program *add_cs_shader_program(struct vrend_context *ctx,
                                                                 struct vrend_shader *cs)
{
   struct vrend_linked_shader_program *sprog = CALLOC_STRUCT(vrend_linked_shader_program);
   GLuint prog_id;
   prog_id = glCreateProgram();
   glAttachShader(prog_id, cs->id);

   if (!vrend_link(prog_id)) {
      /* dump shaders */
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SHADER, 0);
      vrend_shader_dump(cs);
      glDeleteProgram(prog_id);
      free(sprog);
      return NULL;
   }
   sprog->ss[PIPE_SHADER_COMPUTE] = cs;

   list_add(&sprog->sl[PIPE_SHADER_COMPUTE], &cs->programs);
   sprog->id.program = prog_id;
   list_addtail(&sprog->head, &ctx->sub->cs_programs);

   vrend_use_program(sprog);

   bind_sampler_locs(sprog, PIPE_SHADER_COMPUTE, 0);
   bind_ubo_locs(sprog, PIPE_SHADER_COMPUTE, 0);
   bind_ssbo_locs(sprog, PIPE_SHADER_COMPUTE);
   bind_const_locs(sprog, PIPE_SHADER_COMPUTE);
   bind_image_locs(sprog, PIPE_SHADER_COMPUTE);
   return sprog;
}

static inline bool
vrend_link_stage(struct vrend_shader *stage) {
   if (!stage->is_linked)
      stage->is_linked = vrend_link(stage->program_id);
   return stage->is_linked;
}

static struct vrend_linked_shader_program *add_shader_program(struct vrend_sub_context *sub_ctx,
                                                              struct vrend_shader *vs,
                                                              struct vrend_shader *fs,
                                                              struct vrend_shader *gs,
                                                              struct vrend_shader *tcs,
                                                              struct vrend_shader *tes,
                                                              bool separable)
{
   struct vrend_linked_shader_program *sprog = CALLOC_STRUCT(vrend_linked_shader_program);
   char name[64];
   GLuint prog_id = 0;
   GLuint pipeline_id = 0;
   GLuint vs_id, fs_id, gs_id, tes_id = 0;
   enum pipe_shader_type last_shader;
   if (!sprog)
      return NULL;

   if (separable) {
       glGenProgramPipelines(1, &pipeline_id);

       vs_id = vs->program_id;
       fs_id = fs->program_id;
       if (gs)
           gs_id = gs->program_id;
       if (tes)
           tes_id = tes->program_id;
   } else { /* inseparable programs */
       prog_id = glCreateProgram();
       glAttachShader(prog_id, vs->id);
       if (tcs && tcs->id > 0)
          glAttachShader(prog_id, tcs->id);
       if (tes && tes->id > 0)
          glAttachShader(prog_id, tes->id);
       if (gs && gs->id > 0)
          glAttachShader(prog_id, gs->id);
       glAttachShader(prog_id, fs->id);

       /* For the non-separable codepath (the usual path), all these shader stages are
        * contained inside a single program. */
       vs_id = prog_id;
       fs_id = prog_id;
       if (gs)
           gs_id = prog_id;
       if (tes)
           tes_id = prog_id;
   }

   if (gs) {
      set_stream_out_varyings(sub_ctx, gs_id, &gs->sel->sinfo);
   } else if (tes)
      set_stream_out_varyings(sub_ctx, tes_id, &tes->sel->sinfo);
   else
      set_stream_out_varyings(sub_ctx, vs_id, &vs->sel->sinfo);

   if (fs->sel->sinfo.num_outputs > 1) {
      sprog->dual_src_linked = util_blend_state_is_dual(&sub_ctx->blend_state, 0);
      if (sprog->dual_src_linked) {
         if (has_feature(feat_dual_src_blend)) {
            if (!vrend_state.use_gles) {
               glBindFragDataLocationIndexed(fs_id, 0, 0, "fsout_c0");
               glBindFragDataLocationIndexed(fs_id, 0, 1, "fsout_c1");
            } else {
               glBindFragDataLocationIndexedEXT(fs_id, 0, 0, "fsout_c0");
               glBindFragDataLocationIndexedEXT(fs_id, 0, 1, "fsout_c1");
            }
         } else {
            vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_DUAL_SRC_BLEND, 0);
         }
      } else if (!vrend_state.use_gles && has_feature(feat_dual_src_blend)) {
         /* On GLES without dual source blending we emit the layout directly in the shader
          * so there is no need to define the binding here */
         for (int i = 0; i < fs->sel->sinfo.num_outputs; ++i) {
            if (fs->sel->sinfo.fs_output_layout[i] >= 0) {
               char buf[64];
               snprintf(buf, sizeof(buf), "fsout_c%d", fs->sel->sinfo.fs_output_layout[i]);
               glBindFragDataLocationIndexed(fs_id, fs->sel->sinfo.fs_output_layout[i], 0, buf);
            }
         }
      }
   } else
      sprog->dual_src_linked = false;

   if (has_feature(feat_gles31_vertex_attrib_binding)) {
      uint32_t mask = vs->sel->sinfo.attrib_input_mask;
      while (mask) {
         int i = u_bit_scan(&mask);
         snprintf(name, 32, "in_%d", i);
         glBindAttribLocation(vs_id, i, name);
      }
   }

   bool link_success;
   if (separable) { /* separable programs */
      link_success = vrend_link_stage(vs);
      link_success &= vrend_link_stage(fs);
      if (gs) link_success &= vrend_link_stage(gs);
      if (tcs) link_success &= vrend_link_stage(tcs);
      if (tes) link_success &= vrend_link_stage(tes);
   } else { /* non-separable programs */
      link_success = vrend_link(prog_id);
   }

   if (!link_success) {
      if (separable) {
         glDeleteProgramPipelines(1, &pipeline_id);
      } else {
         glDeleteProgram(prog_id);
      }

      free(sprog);

      /* dump shaders */
      vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_SHADER, 0);
      vrend_shader_dump(vs);
      if (tcs)
         vrend_shader_dump(tcs);
      if (tes)
         vrend_shader_dump(tes);
      if (gs)
         vrend_shader_dump(gs);
      vrend_shader_dump(fs);
      return NULL;
   }

   if (separable) {
       glUseProgramStages(pipeline_id, GL_VERTEX_SHADER_BIT, vs->program_id);
       if (tcs) glUseProgramStages(pipeline_id, GL_TESS_CONTROL_SHADER_BIT, tcs->program_id);
       if (tes) glUseProgramStages(pipeline_id, GL_TESS_EVALUATION_SHADER_BIT, tes->program_id);
       if (gs) glUseProgramStages(pipeline_id, GL_GEOMETRY_SHADER_BIT, gs->program_id);
       glUseProgramStages(pipeline_id, GL_FRAGMENT_SHADER_BIT, fs->program_id);

       glValidateProgramPipeline(pipeline_id);
       GLint validation_status;
       glGetProgramPipelineiv(pipeline_id, GL_VALIDATE_STATUS, &validation_status);
       if (!validation_status) {
           vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_PROGRAM_PIPELINE, 0);
       }
   }

   sprog->ss[PIPE_SHADER_VERTEX] = vs;
   sprog->ss[PIPE_SHADER_FRAGMENT] = fs;
   sprog->vs_fs_key = (((uint64_t)fs->id) << 32) | (vs->id & ~VREND_PROGRAM_NQUEUE_MASK) |
                      (sprog->dual_src_linked ? 1 : 0);

   sprog->ss[PIPE_SHADER_GEOMETRY] = gs;
   sprog->ss[PIPE_SHADER_TESS_CTRL] = tcs;
   sprog->ss[PIPE_SHADER_TESS_EVAL] = tes;

   list_add(&sprog->sl[PIPE_SHADER_VERTEX], &vs->programs);
   list_add(&sprog->sl[PIPE_SHADER_FRAGMENT], &fs->programs);
   if (gs)
      list_add(&sprog->sl[PIPE_SHADER_GEOMETRY], &gs->programs);
   if (tcs)
      list_add(&sprog->sl[PIPE_SHADER_TESS_CTRL], &tcs->programs);
   if (tes)
      list_add(&sprog->sl[PIPE_SHADER_TESS_EVAL], &tes->programs);

   last_shader = tes ? PIPE_SHADER_TESS_EVAL : (gs ? PIPE_SHADER_GEOMETRY : PIPE_SHADER_FRAGMENT);

   sprog->is_pipeline = separable;
   if (sprog->is_pipeline)
       sprog->id.pipeline = pipeline_id;
   else
       sprog->id.program = prog_id;

   list_addtail(&sprog->head, &sub_ctx->gl_programs[vs->id & VREND_PROGRAM_NQUEUE_MASK]);

   sprog->virgl_block_bind = GL_INVALID_INDEX;
   sprog->ubo_sysval_buffer_id = GL_INVALID_INDEX;
   sprog->sysvalue_data_cookie = UINT32_MAX;

   vrend_use_program(sprog);

   for (enum pipe_shader_type shader_type = PIPE_SHADER_VERTEX;
        shader_type <= last_shader;
        shader_type++) {
      if (!sprog->ss[shader_type])
         continue;

      bind_const_locs(sprog, shader_type);
      bind_image_locs(sprog, shader_type);
      bind_ssbo_locs(sprog, shader_type);

      if (sprog->ss[shader_type]->sel->sinfo.reads_drawid)
         sprog->reads_drawid = true;
   }
   rebind_ubo_and_sampler_locs(sprog, last_shader);

   if (!has_feature(feat_gles31_vertex_attrib_binding)) {
      if (vs->sel->sinfo.num_inputs) {
         sprog->attrib_locs = calloc(vs->sel->sinfo.num_inputs, sizeof(uint32_t));
         if (sprog->attrib_locs) {
            for (int i = 0; i < vs->sel->sinfo.num_inputs; i++) {
               snprintf(name, 32, "in_%d", i);
               sprog->attrib_locs[i] = glGetAttribLocation(vs_id, name);
            }
         }
      } else
         sprog->attrib_locs = NULL;
   }

   return sprog;
}

static struct vrend_linked_shader_program *lookup_cs_shader_program(struct vrend_context *ctx,
                                                                    GLuint cs_id)
{
   list_for_each_entry(struct vrend_linked_shader_program, ent, &ctx->sub->cs_programs, head) {
      if (ent->ss[PIPE_SHADER_COMPUTE]->id == cs_id) {
         list_del(&ent->head);
         list_add(&ent->head, &ctx->sub->cs_programs);
         return ent;
      }
   }
   return NULL;
}

static struct vrend_linked_shader_program *lookup_shader_program(struct vrend_sub_context *sub_ctx,
                                                                 GLuint vs_id,
                                                                 GLuint fs_id,
                                                                 GLuint gs_id,
                                                                 GLuint tcs_id,
                                                                 GLuint tes_id,
                                                                 bool dual_src)
{
   uint64_t vs_fs_key = (((uint64_t)fs_id) << 32) | (vs_id & ~VREND_PROGRAM_NQUEUE_MASK) |
                        (dual_src ? 1 : 0);

   struct list_head *programs = &sub_ctx->gl_programs[vs_id & VREND_PROGRAM_NQUEUE_MASK];
   list_for_each_entry(struct vrend_linked_shader_program, ent, programs, head) {
      if (likely(ent->vs_fs_key != vs_fs_key))
         continue;
      if (ent->ss[PIPE_SHADER_GEOMETRY] &&
          ent->ss[PIPE_SHADER_GEOMETRY]->id != gs_id)
        continue;
      if (ent->ss[PIPE_SHADER_TESS_CTRL] &&
          ent->ss[PIPE_SHADER_TESS_CTRL]->id != tcs_id)
         continue;
      if (ent->ss[PIPE_SHADER_TESS_EVAL] &&
          ent->ss[PIPE_SHADER_TESS_EVAL]->id != tes_id)
         continue;
      /* put the entry in front */
      if (programs->next != &ent->head) {
         list_del(&ent->head);
         list_add(&ent->head, programs);
      }
      return ent;
   }

   return NULL;
}

static void vrend_destroy_program(struct vrend_linked_shader_program *ent)
{
   int i;
   if (ent->ref_context && ent->ref_context->prog == ent)
      ent->ref_context->prog = NULL;

   if (ent->ubo_sysval_buffer_id != GL_INVALID_INDEX) {
       glDeleteBuffers(1, &ent->ubo_sysval_buffer_id);
   }

   if (ent->is_pipeline)
       glDeleteProgramPipelines(1, &ent->id.pipeline);
   else
       glDeleteProgram(ent->id.program);

   list_del(&ent->head);

   for (i = PIPE_SHADER_VERTEX; i <= PIPE_SHADER_COMPUTE; i++) {
      if (ent->ss[i]) {
         list_del(&ent->sl[i]);
         if (ent->ss[i]->last_pipeline_id == ent->id.pipeline)
            ent->ss[i]->last_pipeline_id = 0xffffffff;
      }
      free(ent->sampler_locs[i]);
      free(ent->shadow_samp_mask_locs[i]);
      free(ent->shadow_samp_add_locs[i]);
      free(ent->img_locs[i]);

   }
   free(ent->attrib_locs);
   free(ent);
}

static void vrend_free_programs(struct vrend_sub_context *sub)
{
   if (!list_is_empty(&sub->cs_programs)) {
      list_for_each_entry_safe(struct vrend_linked_shader_program, ent, &sub->cs_programs, head)
         vrend_destroy_program(ent);
   }

   for (unsigned i = 0; i < VREND_PROGRAM_NQUEUES; ++i) {
      if (!list_is_empty(&sub->gl_programs[i])) {
         list_for_each_entry_safe(struct vrend_linked_shader_program, ent, &sub->gl_programs[i], head)
            vrend_destroy_program(ent);
      }
   }
}

static void vrend_destroy_streamout_object(struct vrend_streamout_object *obj)
{
   unsigned i;
   list_del(&obj->head);
   for (i = 0; i < obj->num_targets; i++)
      vrend_so_target_reference(&obj->so_targets[i], NULL);
   if (has_feature(feat_transform_feedback2))
      glDeleteTransformFeedbacks(1, &obj->id);
   FREE(obj);
}

void vrend_sync_make_current(virgl_gl_context gl_cxt) {
   GLsync sync = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
   vrend_clicbs->make_current(gl_cxt);
   glWaitSync(sync, 0, GL_TIMEOUT_IGNORED);
   glDeleteSync(sync);
}

int vrend_create_surface(struct vrend_context *ctx,
                         uint32_t handle, struct vrend_resource *res,
                         enum virgl_formats format, uint32_t level,
                         uint32_t first_layer, uint32_t last_layer,
                         uint32_t nr_samples)
{
   struct vrend_surface *surf;
   uint32_t ret_handle;

   surf = CALLOC_STRUCT(vrend_surface);
   if (!surf)
      return ENOMEM;

   surf->format = format;

   surf->level = level;
   surf->first_layer = first_layer;
   surf->last_layer = last_layer;
   surf->gl_id = res->gl_id;
   surf->nr_samples = nr_samples;

   if (!has_bit(res->storage_bits, VREND_STORAGE_GL_BUFFER) &&
         has_bit(res->storage_bits, VREND_STORAGE_GL_IMMUTABLE) &&
         has_feature(feat_texture_view)) {
      /* We don't need texture views for buffer objects.
       * Otherwise we only need a texture view if the
       * a) formats differ between the surface and base texture
       * b) we need to map a sub range > 1 layer to a surface,
       * GL can make a single layer fine without a view, and it
       * can map the whole texure fine. In those cases we don't
       * create a texture view.
       */
      int first_layer = surf->first_layer;
      int last_layer = surf->last_layer;

      bool needs_view = first_layer != last_layer &&
         (first_layer != 0 || (last_layer != (int)util_max_layer(&res->base, surf->level)));
      if (!needs_view && surf->format != res->base.format)
         needs_view = true;

      if (needs_view && vrend_resource_supports_view(res, surf->format)) {
         GLenum target = res->target;
         GLenum internalformat = tex_conv_table[format].internalformat;

         if (target == GL_TEXTURE_CUBE_MAP && first_layer == last_layer) {
            first_layer = 0;
            last_layer = 5;
         }

         int num_layers = last_layer - first_layer + 1;

         VREND_DEBUG(dbg_tex, ctx, "Create texture view from %s for %s\n",
                     util_format_name(res->base.format),
                     util_format_name(surf->format));

         glGenTextures(1, &surf->gl_id);
         if (vrend_state.use_gles) {
            if (target == GL_TEXTURE_1D)
               target = GL_TEXTURE_2D;
            else if (target == GL_TEXTURE_1D_ARRAY)
               target = GL_TEXTURE_2D_ARRAY;
         }

         if (target == GL_TEXTURE_RECTANGLE_NV &&
             !(tex_conv_table[format].flags & VIRGL_TEXTURE_CAN_TARGET_RECTANGLE)) {
            target = GL_TEXTURE_2D;
         }

         glTextureView(surf->gl_id, target, res->gl_id, internalformat,
                       0, res->base.last_level + 1,
                       first_layer, num_layers);
      }
   }

   pipe_reference_init(&surf->reference, 1);

   vrend_resource_reference(&surf->texture, res);

   ret_handle = vrend_renderer_object_insert(ctx, surf, handle, VIRGL_OBJECT_SURFACE);
   if (ret_handle == 0) {
      vrend_resource_reference(&surf->texture, NULL);
      FREE(surf);
      return ENOMEM;
   }
   return 0;
}

int vrend_create_dsa(struct vrend_context *ctx,
                     uint32_t handle,
                     const struct pipe_depth_stencil_alpha_state *dsa_state)
{
   struct vrend_depth_stencil_alpha_state *vdsa_state;
   uint32_t ret_handle;

   vdsa_state = CALLOC_STRUCT(vrend_depth_stencil_alpha_state);
   if (!vdsa_state)
      return ENOMEM;

   vdsa_state->base = *dsa_state;

   ret_handle = vrend_renderer_object_insert(ctx, vdsa_state, handle, VIRGL_OBJECT_DSA);
   if (ret_handle == 0) {
      FREE(vdsa_state);
      return ENOMEM;
   }

   return 0;
}

static void vrend_destroy_surface_object(void *obj_ptr)
{
   struct vrend_surface *surface = obj_ptr;

   vrend_surface_reference(&surface, NULL);
}

static void vrend_destroy_sampler_view_object(void *obj_ptr)
{
   struct vrend_sampler_view *samp = obj_ptr;

   vrend_sampler_view_reference(&samp, NULL);
}

static void vrend_destroy_so_target_object(void *obj_ptr)
{
   struct vrend_so_target *target = obj_ptr;
   struct vrend_sub_context *sub_ctx = target->sub_ctx;
   unsigned i;

   list_for_each_entry_safe(struct vrend_streamout_object, obj, &sub_ctx->streamout_list, head) {
      for (i = 0; i < obj->num_targets; i++) {
         if (obj->so_targets[i] == target) {
            if (obj == sub_ctx->current_so)
               sub_ctx->current_so = NULL;
            if (obj->xfb_state == XFB_STATE_PAUSED) {
                  if (has_feature(feat_transform_feedback2))
                     glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, obj->id);
                  glEndTransformFeedback();
               if (sub_ctx->current_so && has_feature(feat_transform_feedback2))
                  glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, sub_ctx->current_so->id);
            }
            vrend_destroy_streamout_object(obj);
            break;
         }
      }
   }

   vrend_so_target_reference(&target, NULL);
}

static void vrend_destroy_vertex_elements_object(void *obj_ptr)
{
   struct vrend_vertex_element_array *v = obj_ptr;

   if (v == v->owning_sub->ve)
      v->owning_sub->ve = NULL;

   if (has_feature(feat_gles31_vertex_attrib_binding)) {
      glDeleteVertexArrays(1, &v->id);
   }
   FREE(v);
}

static void vrend_destroy_sampler_state_object(void *obj_ptr)
{
   struct vrend_sampler_state *state = obj_ptr;

   if (has_feature(feat_samplers))
      glDeleteSamplers(2, state->ids);

   if (state->sub_ctx) {
      struct vrend_sub_context *sub_ctx = state->sub_ctx;
      for (enum pipe_shader_type shader_type = PIPE_SHADER_VERTEX;
           shader_type < PIPE_SHADER_TYPES;
           shader_type++) {
         struct vrend_shader_view *shader_view = &sub_ctx->views[shader_type];

         int deleted_samplers = 0;
         for (uint32_t sampler = 0; sampler < PIPE_MAX_SAMPLERS; sampler++) {
            if (shader_view->samplers[sampler] == state) {
               shader_view->samplers[sampler] = NULL;
               shader_view->dirty_mask |= (1u << sampler);
               deleted_samplers++;
            } else if (deleted_samplers) {
               shader_view->samplers[sampler-deleted_samplers] =
                  shader_view->samplers[sampler];
               shader_view->samplers[sampler] = NULL;
               shader_view->dirty_mask |= (1u << sampler);
            }
         }
      }
   }

   FREE(state);
}

static void vrend_destroy_dsa_object(void *obj_ptr)
{
   struct vrend_depth_stencil_alpha_state *state = obj_ptr;

   if (state->owning_sub && state == state->owning_sub->dsa)
      vrend_object_bind_dsa_to_sub_context(state->owning_sub, 0 /* unbind */);

   FREE(state);
}

static GLuint convert_wrap(struct vrend_context *ctx, int wrap)
{
   switch(wrap){
   case PIPE_TEX_WRAP_REPEAT: return GL_REPEAT;
   case PIPE_TEX_WRAP_CLAMP: if (vrend_state.use_core_profile == false) return GL_CLAMP; else return GL_CLAMP_TO_EDGE;

   case PIPE_TEX_WRAP_CLAMP_TO_EDGE: return GL_CLAMP_TO_EDGE;
   case PIPE_TEX_WRAP_CLAMP_TO_BORDER: return GL_CLAMP_TO_BORDER;

   case PIPE_TEX_WRAP_MIRROR_REPEAT: return GL_MIRRORED_REPEAT;
   case PIPE_TEX_WRAP_MIRROR_CLAMP:
      if (has_feature(feat_texture_mirror_clamp))
         return GL_MIRROR_CLAMP_EXT;
      else {
          vrend_report_context_error(ctx, VIRGL_ERROR_CTX_UNSUPPORTED_TEX_WRAP, wrap);
          return GL_MIRRORED_REPEAT;
      }
   case PIPE_TEX_WRAP_MIRROR_CLAMP_TO_EDGE:
      if (has_feature(feat_texture_mirror_clamp_to_edge))
         return GL_MIRROR_CLAMP_TO_EDGE_EXT;
      else {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_UNSUPPORTED_TEX_WRAP, wrap);
         return GL_MIRRORED_REPEAT;
      }
   case PIPE_TEX_WRAP_MIRROR_CLAMP_TO_BORDER:
      if (has_feature(feat_texture_mirror_clamp_to_border)) {
         return GL_MIRROR_CLAMP_TO_BORDER_EXT;
      } else {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_UNSUPPORTED_TEX_WRAP, wrap);
         return GL_MIRRORED_REPEAT;
      }
   default:
      assert(0);
      return -1;
   }
}

static inline GLenum convert_mag_filter(enum pipe_tex_filter filter)
{
   if (filter == PIPE_TEX_FILTER_NEAREST)
      return GL_NEAREST;
   return GL_LINEAR;
}

static inline GLenum convert_min_filter(enum pipe_tex_filter filter, enum pipe_tex_mipfilter mip_filter)
{
   if (mip_filter == PIPE_TEX_MIPFILTER_NONE)
      return convert_mag_filter(filter);
   else if (mip_filter == PIPE_TEX_MIPFILTER_LINEAR) {
      if (filter == PIPE_TEX_FILTER_NEAREST)
         return GL_NEAREST_MIPMAP_LINEAR;
      else
         return GL_LINEAR_MIPMAP_LINEAR;
   } else if (mip_filter == PIPE_TEX_MIPFILTER_NEAREST) {
      if (filter == PIPE_TEX_FILTER_NEAREST)
         return GL_NEAREST_MIPMAP_NEAREST;
      else
         return GL_LINEAR_MIPMAP_NEAREST;
   }
   assert(0);
   return 0;
}

static void apply_sampler_border_color(GLuint sampler,
                                       const GLuint colors[static 4])
{
   if (has_feature(feat_sampler_border_colors)) {
      glSamplerParameterIuiv(sampler, GL_TEXTURE_BORDER_COLOR, colors);
   } else if (colors[0] || colors[1] || colors[2] || colors[3]) {
      virgl_warn("Sampler border color setting requested but not supported\n");
   }
}

int vrend_create_sampler_state(struct vrend_context *ctx,
                               uint32_t handle,
                               struct pipe_sampler_state *templ)
{
   struct vrend_sampler_state *state = CALLOC_STRUCT(vrend_sampler_state);
   int ret_handle;

   if (!state)
      return ENOMEM;

   state->base = *templ;

   if (has_feature(feat_samplers)) {
      glGenSamplers(2, state->ids);

      for (int i = 0; i < 2; ++i) {
         glSamplerParameteri(state->ids[i], GL_TEXTURE_WRAP_S, convert_wrap(ctx, templ->wrap_s));
         glSamplerParameteri(state->ids[i], GL_TEXTURE_WRAP_T, convert_wrap(ctx, templ->wrap_t));
         glSamplerParameteri(state->ids[i], GL_TEXTURE_WRAP_R, convert_wrap(ctx, templ->wrap_r));
         glSamplerParameterf(state->ids[i], GL_TEXTURE_MIN_FILTER, convert_min_filter(templ->min_img_filter, templ->min_mip_filter));
         glSamplerParameterf(state->ids[i], GL_TEXTURE_MAG_FILTER, convert_mag_filter(templ->mag_img_filter));
         glSamplerParameterf(state->ids[i], GL_TEXTURE_MIN_LOD, templ->min_lod);
         glSamplerParameterf(state->ids[i], GL_TEXTURE_MAX_LOD, templ->max_lod);
         glSamplerParameteri(state->ids[i], GL_TEXTURE_COMPARE_MODE, templ->compare_mode ? GL_COMPARE_R_TO_TEXTURE : GL_NONE);
         glSamplerParameteri(state->ids[i], GL_TEXTURE_COMPARE_FUNC, GL_NEVER + templ->compare_func);
         if (vrend_state.use_gles) {
            if (templ->lod_bias)
               report_gles_warn(ctx, GLES_WARN_LOD_BIAS);
         } else
            glSamplerParameterf(state->ids[i], GL_TEXTURE_LOD_BIAS, templ->lod_bias);

         if (vrend_state.use_gles) {
            if (templ->seamless_cube_map != 0) {
               report_gles_warn(ctx, GLES_WARN_SEAMLESS_CUBE_MAP);
            }
         } else {
            if (has_feature(feat_seamless_cubemap_per_texture)) {
               glSamplerParameteri(state->ids[i], GL_TEXTURE_CUBE_MAP_SEAMLESS, templ->seamless_cube_map);
            }
         }

         apply_sampler_border_color(state->ids[i], templ->border_color.ui);
         if (has_feature(feat_texture_srgb_decode))
            glSamplerParameteri(state->ids[i], GL_TEXTURE_SRGB_DECODE_EXT,
                                i == 0 ? GL_SKIP_DECODE_EXT : GL_DECODE_EXT);
      }
   }
   ret_handle = vrend_renderer_object_insert(ctx, state, handle,
                                             VIRGL_OBJECT_SAMPLER_STATE);
   if (!ret_handle) {
      if (has_feature(feat_samplers))
         glDeleteSamplers(2, state->ids);
      FREE(state);
      return ENOMEM;
   }
   return 0;
}

static inline GLenum to_gl_swizzle(enum pipe_swizzle swizzle)
{
   switch (swizzle) {
   case PIPE_SWIZZLE_X: return GL_RED;
   case PIPE_SWIZZLE_Y: return GL_GREEN;
   case PIPE_SWIZZLE_Z: return GL_BLUE;
   case PIPE_SWIZZLE_W: return GL_ALPHA;
   case PIPE_SWIZZLE_0: return GL_ZERO;
   case PIPE_SWIZZLE_1: return GL_ONE;
   default:
      assert(0);
      return 0;
   }
}

int vrend_create_sampler_view(struct vrend_context *ctx,
                              uint32_t handle,
                              struct vrend_resource *res,
                              enum virgl_formats format, enum pipe_texture_target pipe_target,
                              uint32_t val0, uint32_t val1, uint32_t swizzle_packed)
{
   struct vrend_sampler_view *view;
   int ret_handle;
   enum pipe_swizzle swizzle[4];

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_FORMAT, format);
      return EINVAL;
   }

   for (int i = 0; i < 4; ++i) {
      swizzle[i] = (swizzle_packed  >> (3 * i)) & 0x7;
      if (swizzle[i] > PIPE_SWIZZLE_1) {
         return EINVAL;
      }
   }

   view = CALLOC_STRUCT(vrend_sampler_view);
   if (!view)
      return ENOMEM;

   pipe_reference_init(&view->reference, 1);
   view->format = format;

   view->target = tgsitargettogltarget(pipe_target, res->base.nr_samples);

   /* Work around TEXTURE_1D missing on GLES */
   if (vrend_state.use_gles) {
      if (view->target == GL_TEXTURE_1D)
         view->target = GL_TEXTURE_2D;
      else if (view->target == GL_TEXTURE_1D_ARRAY)
         view->target = GL_TEXTURE_2D_ARRAY;
   }

   if (view->target == GL_TEXTURE_RECTANGLE_NV &&
       !(tex_conv_table[view->format].flags & VIRGL_TEXTURE_CAN_TARGET_RECTANGLE)) {
      view->emulated_rect = true;
      view->target = GL_TEXTURE_2D;
   }

   vrend_resource_reference(&view->texture, res);

   view->gl_id = view->texture->gl_id;

   if (view->target == PIPE_BUFFER) {
      view->target = view->texture->target;

      view->u.buf.first_element = val0;
      view->u.buf.last_element = val1;
   } else {
      view->u.tex.first_layer = val0 & 0xffff;
      view->u.tex.last_layer = (val0 >> 16) & 0xffff;
      view->u.tex.first_level = val1 & 0xff;
      view->u.tex.last_level = (val1 >> 8) & 0xff;
   }

   view->srgb_decode = GL_DECODE_EXT;
   if (view->format != view->texture->base.format) {
      if (util_format_is_srgb(view->texture->base.format) &&
          !util_format_is_srgb(view->format))
         view->srgb_decode = GL_SKIP_DECODE_EXT;
   }

   if (!(util_format_has_alpha(view->format) || util_format_is_depth_or_stencil(view->format))) {
      if (swizzle[0] == PIPE_SWIZZLE_W)
          swizzle[0] = PIPE_SWIZZLE_1;
      if (swizzle[1] == PIPE_SWIZZLE_W)
          swizzle[1] = PIPE_SWIZZLE_1;
      if (swizzle[2] == PIPE_SWIZZLE_W)
          swizzle[2] = PIPE_SWIZZLE_1;
      if (swizzle[3] == PIPE_SWIZZLE_W)
          swizzle[3] = PIPE_SWIZZLE_1;
   }

   if (tex_conv_table[view->format].flags & VIRGL_TEXTURE_NEED_SWIZZLE) {
      if (swizzle[0] <= PIPE_SWIZZLE_W)
         swizzle[0] = tex_conv_table[view->format].swizzle[swizzle[0]];
      if (swizzle[1] <= PIPE_SWIZZLE_W)
         swizzle[1] = tex_conv_table[view->format].swizzle[swizzle[1]];
      if (swizzle[2] <= PIPE_SWIZZLE_W)
         swizzle[2] = tex_conv_table[view->format].swizzle[swizzle[2]];
      if (swizzle[3] <= PIPE_SWIZZLE_W)
         swizzle[3] = tex_conv_table[view->format].swizzle[swizzle[3]];
   }

   for (enum pipe_swizzle i = 0; i < 4; ++i)
      view->gl_swizzle[i] = to_gl_swizzle(swizzle[i]);

   if (res->is_imported && vrend_format_is_bgra(view->texture->base.format)) {
      /* Swap R/B channel for vulkan imported texture. */
      GLenum tmp = view->gl_swizzle[0];
      view->gl_swizzle[0] = view->gl_swizzle[2];
      view->gl_swizzle[2] = tmp;

      /* Don't decode vulkan imported texture. */
      view->srgb_decode = GL_SKIP_DECODE_EXT;
   }

   if (!has_bit(view->texture->storage_bits, VREND_STORAGE_GL_BUFFER)) {
      enum virgl_formats format;
      bool needs_view = false;

      /*
       * Need to use a texture view if the gallium
       * view target is different than the underlying
       * texture target.
       */
      if (view->target != view->texture->target)
         needs_view = true;

      /*
       * If the formats are different and this isn't
       * a DS texture a view is required.
       * DS are special as they use different gallium
       * formats for DS views into a combined resource.
       * GL texture views can't be use for this, stencil
       * texturing is used instead. For DS formats
       * aways program the underlying DS format as a
       * view could be required for layers.
       */
      format = view->format;
      if (util_format_is_depth_or_stencil(view->texture->base.format))
         format = view->texture->base.format;
      else if (view->format != view->texture->base.format)
         needs_view = true;

      if (view->u.tex.first_layer > 0 || view->u.tex.first_level > 0)
         needs_view = true;

      if (needs_view &&
          has_bit(view->texture->storage_bits, VREND_STORAGE_GL_IMMUTABLE) &&
          has_feature(feat_texture_view)) {
        GLenum internalformat = tex_conv_table[format].internalformat;
        view->levels = (view->u.tex.last_level - view->u.tex.first_level) + 1;

        int num_layers = view->u.tex.last_layer - view->u.tex.first_layer + 1;

        if (view->levels == 0 || num_layers <= 0) {
            vrend_resource_reference(&view->texture, NULL);
            FREE(view);
            virgl_error("%s: Invalid number of layers (%d) or zero levels requested\n",
                        __func__, num_layers);
            return EINVAL;
        }

        glGenTextures(1, &view->gl_id);

        /* texture views for eglimage-backed bgr* resources are usually not
         * supported since they cause unintended red/blue channel-swapping.
         * Since we have control over the swizzle parameters of the sampler, we
         * can just compensate in this case by swapping the red/blue channels
         * back, and still benefit from automatic srgb decoding.
         * If the red/blue swap is intended, we just let it happen and don't
         * need to explicit change to the sampler's swizzle parameters. */
        if (!vrend_resource_supports_view(view->texture, view->format) &&
            vrend_format_is_bgra(view->format)) {
              VREND_DEBUG(dbg_tex, ctx, "texture view with red/blue swizzle created for EGL-backed texture sampler"
                          " (format: %s; view: %s)\n",
                          util_format_name(view->texture->base.format),
                          util_format_name(view->format));
              GLint temp = view->gl_swizzle[0];
              view->gl_swizzle[0] = view->gl_swizzle[2];
              view->gl_swizzle[2] = temp;
        }

        // Workaround for clients that attempt this non-spec-conformant (or at
        // least non-spec-defined) behavior. Gallium seems to allow this for
        // internally-allocated textures, but textures imported from dmabuf take
        // a different path that imposes stricter enforcement of allowable
        // internalformat for glTextureView().

        GLuint real_internalformat =
              vrend_resource_get_internal_format_override(view->texture);

        if (util_format_has_alpha(view->format) &&
            real_internalformat != GL_NONE) {
           VREND_DEBUG(dbg_tex, ctx, "Fixing view with alpha into EGL-backed texture without alpha (format: %s, view: %s)\n",
                       util_format_name(view->texture->base.format),
                       util_format_name(view->format));
           internalformat = real_internalformat;
        }

        glTextureView(view->gl_id, view->target, view->texture->gl_id, internalformat,
                      view->u.tex.first_level, view->levels,
                      view->u.tex.first_layer, num_layers);

        glBindTexture(view->target, view->gl_id);

        if (util_format_is_depth_or_stencil(view->format)) {
           if (vrend_state.use_core_profile == false) {
              /* setting depth texture mode is deprecated in core profile */
              glTexParameteri(view->target, GL_DEPTH_TEXTURE_MODE, GL_RED);
           }
           if (has_feature(feat_stencil_texturing)) {
              const struct util_format_description *desc = util_format_description(view->format);
              if (!util_format_has_depth(desc)) {
                 glTexParameteri(view->target, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
              } else {
                 glTexParameteri(view->target, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
              }
           }
        }

        if (vrend_state.use_gles) {
           for (unsigned int i = 0; i < 4; ++i) {
              glTexParameteri(view->target, GL_TEXTURE_SWIZZLE_R + i, view->gl_swizzle[i]);
           }
        } else
           glTexParameteriv(view->target, GL_TEXTURE_SWIZZLE_RGBA, view->gl_swizzle);
        if (util_format_is_srgb(view->format) &&
            has_feature(feat_texture_srgb_decode)) {
           glTexParameteri(view->target, GL_TEXTURE_SRGB_DECODE_EXT,
                            view->srgb_decode);
        }
        glBindTexture(view->target, 0);
      } else if (needs_view && view->u.buf.first_element < ARRAY_SIZE(res->aux_plane_egl_image) &&
            res->aux_plane_egl_image[view->u.buf.first_element]) {
        void *image = res->aux_plane_egl_image[view->u.buf.first_element];
        glGenTextures(1, &view->gl_id);
        glBindTexture(view->target, view->gl_id);
        glEGLImageTargetTexture2DOES(view->target, (GLeglImageOES) image);
        glBindTexture(view->target, 0);
      }
   }

   ret_handle = vrend_renderer_object_insert(ctx, view, handle, VIRGL_OBJECT_SAMPLER_VIEW);
   if (ret_handle == 0) {
      vrend_resource_reference(&view->texture, NULL);
      FREE(view);
      return ENOMEM;
   }
   return 0;
}

static void vrend_framebuffer_texture_2d(struct vrend_resource *res,
                                         GLenum target, GLenum attachment,
                                         GLenum textarget, uint32_t texture,
                                         int32_t level, uint32_t samples)
{
   if (samples == 0) {
      glFramebufferTexture2D(target, attachment, textarget, texture, level);
   } else if (!has_feature(feat_implicit_msaa)) {
      /* fallback to non-msaa */
      report_gles_warn(vrend_state.current_ctx, GLES_WARN_IMPLICIT_MSAA_SURFACE);
      glFramebufferTexture2D(target, attachment, textarget, texture, level);
   } else if (attachment == GL_COLOR_ATTACHMENT0){
      glFramebufferTexture2DMultisampleEXT(target, attachment, textarget,
                                           texture, level, samples);
   } else if (attachment == GL_STENCIL_ATTACHMENT || attachment == GL_DEPTH_ATTACHMENT) {
      GLenum internalformat =
              attachment == GL_STENCIL_ATTACHMENT ?  GL_STENCIL_INDEX8 : GL_DEPTH_COMPONENT16;

      glGenRenderbuffers(1, &res->rbo_id);
      glBindRenderbuffer(GL_RENDERBUFFER, res->rbo_id);
      glRenderbufferStorageMultisampleEXT(GL_RENDERBUFFER, samples,
                                          internalformat, res->base.width0,
                                          res->base.height0);
      glFramebufferRenderbuffer(GL_FRAMEBUFFER, attachment,
                                GL_RENDERBUFFER, res->rbo_id);
      glBindRenderbuffer(GL_RENDERBUFFER, 0);
   } else {
      /* unsupported attachment for EXT_multisampled_render_to_texture, fallback to non-msaa */
      report_gles_warn(vrend_state.current_ctx, GLES_WARN_IMPLICIT_MSAA_SURFACE);
      glFramebufferTexture2D(target, attachment, textarget, texture, level);
   }
}

static
void debug_texture(ASSERTED const char *f, const struct vrend_resource *gt)
{
   ASSERTED const struct pipe_resource *pr = &gt->base;
#define PRINT_TARGET(X) case X: virgl_debug( #X); break
   VREND_DEBUG_EXT(dbg_tex, NULL,
               virgl_debug("%s: ", f);
               switch (tgsitargettogltarget(pr->target, pr->nr_samples)) {
               PRINT_TARGET(GL_TEXTURE_RECTANGLE_NV);
               PRINT_TARGET(GL_TEXTURE_1D);
               PRINT_TARGET(GL_TEXTURE_2D);
               PRINT_TARGET(GL_TEXTURE_3D);
               PRINT_TARGET(GL_TEXTURE_1D_ARRAY);
               PRINT_TARGET(GL_TEXTURE_2D_ARRAY);
               PRINT_TARGET(GL_TEXTURE_2D_MULTISAMPLE);
               PRINT_TARGET(GL_TEXTURE_CUBE_MAP);
               PRINT_TARGET(GL_TEXTURE_CUBE_MAP_ARRAY);
               default:
                  virgl_debug("UNKNOWN");
               }
               virgl_debug(" id:%d pipe_type:%d ms:%d format:%s size: %dx%dx%d mip:%d %s\n",
                           gt->gl_id, pr->target, pr->nr_samples, util_format_name(pr->format),
                           pr->width0, pr->height0, pr->depth0, pr->last_level,
                           gt->gbm_bo ? "GBM" : "GL");
               );
#undef PRINT_TARGET
}

void vrend_fb_bind_texture_id(struct vrend_resource *res,
                              int id, GLuint idx, GLint level,
                              GLint layer, uint32_t samples)
{
   const struct util_format_description *desc = util_format_description(res->base.format);
   GLenum attachment = GL_COLOR_ATTACHMENT0 + idx;

   debug_texture(__func__, res);

   if (vrend_format_is_ds(res->base.format)) {
      if (util_format_has_stencil(desc)) {
         if (util_format_has_depth(desc))
            attachment = GL_DEPTH_STENCIL_ATTACHMENT;
         else
            attachment = GL_STENCIL_ATTACHMENT;
      } else
         attachment = GL_DEPTH_ATTACHMENT;
   }

   switch (res->target) {
   case GL_TEXTURE_1D_ARRAY:
   case GL_TEXTURE_2D_ARRAY:
   case GL_TEXTURE_2D_MULTISAMPLE_ARRAY:
   case GL_TEXTURE_CUBE_MAP_ARRAY:
      if (layer < 0)
         glFramebufferTexture(GL_FRAMEBUFFER, attachment,
                              id, level);
      else
         glFramebufferTextureLayer(GL_FRAMEBUFFER, attachment,
                                   id, level, layer);
      break;
   case GL_TEXTURE_3D:
      if (layer < 0)
         glFramebufferTexture(GL_FRAMEBUFFER, attachment,
                              id, level);
      else if (vrend_state.use_gles)
         glFramebufferTexture3DOES(GL_FRAMEBUFFER, attachment,
                                   res->target, id, level, layer);
      else
         glFramebufferTexture3D(GL_FRAMEBUFFER, attachment,
                                res->target, id, level, layer);
      break;
   case GL_TEXTURE_CUBE_MAP:
      if (layer < 0)
         glFramebufferTexture(GL_FRAMEBUFFER, attachment,
                              id, level);
      else
         vrend_framebuffer_texture_2d(res, GL_FRAMEBUFFER, attachment,
                                      GL_TEXTURE_CUBE_MAP_POSITIVE_X + layer,
                                      id, level, samples);
      break;
   case GL_TEXTURE_1D:
      glFramebufferTexture1D(GL_FRAMEBUFFER, attachment,
                             res->target, id, level);
      break;
   case GL_TEXTURE_2D:
   default:
      vrend_framebuffer_texture_2d(res, GL_FRAMEBUFFER, attachment,
                                   res->target, id, level, samples);
      break;
   }

   if (attachment == GL_DEPTH_ATTACHMENT) {
      switch (res->target) {
      case GL_TEXTURE_1D:
         glFramebufferTexture1D(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT,
                                GL_TEXTURE_1D, 0, 0);
         break;
      case GL_TEXTURE_2D:
      default:
         glFramebufferTexture2D(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT,
                                GL_TEXTURE_2D, 0, 0);
         break;
      }
   }
}

void vrend_fb_bind_texture(struct vrend_resource *res,
                           GLuint idx,
                           GLint level, GLint layer)
{
   vrend_fb_bind_texture_id(res, res->gl_id, idx, level, layer, 0);
}

static void vrend_hw_set_zsurf_texture(struct vrend_context *ctx)
{
   struct vrend_surface *surf = ctx->sub->zsurf;

   if (!surf) {
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                             GL_TEXTURE_2D, 0, 0);
   } else {
      if (!surf->texture)
         return;

      vrend_fb_bind_texture_id(surf->texture, surf->gl_id, 0, surf->level,
                               surf->first_layer != surf->last_layer ? -1 :
                               (GLint)surf->first_layer, surf->nr_samples);
   }
}

static void vrend_hw_set_color_surface(struct vrend_sub_context *sub_ctx, GLuint index)
{
   struct vrend_surface *surf = sub_ctx->surf[index];

   if (!surf) {
      GLenum attachment = GL_COLOR_ATTACHMENT0 + index;

      glFramebufferTexture2D(GL_FRAMEBUFFER, attachment,
                             GL_TEXTURE_2D, 0, 0);
   } else {
      uint32_t first_layer = sub_ctx->surf[index]->first_layer;
      uint32_t last_layer = sub_ctx->surf[index]->last_layer;

      vrend_fb_bind_texture_id(surf->texture, surf->gl_id, index, surf->level,
                               first_layer != last_layer ? -1 : (GLint)first_layer,
                               surf->nr_samples);
   }
}

static void vrend_hw_emit_framebuffer_state(struct vrend_sub_context *sub_ctx)
{
   static const GLenum buffers[8] = {
      GL_COLOR_ATTACHMENT0,
      GL_COLOR_ATTACHMENT1,
      GL_COLOR_ATTACHMENT2,
      GL_COLOR_ATTACHMENT3,
      GL_COLOR_ATTACHMENT4,
      GL_COLOR_ATTACHMENT5,
      GL_COLOR_ATTACHMENT6,
      GL_COLOR_ATTACHMENT7,
   };

   if (sub_ctx->nr_cbufs == 0) {
      glReadBuffer(GL_NONE);
      if (has_feature(feat_srgb_write_control)) {
         glDisable(GL_FRAMEBUFFER_SRGB_EXT);
         sub_ctx->framebuffer_srgb_enabled = false;
      }
   } else if (has_feature(feat_srgb_write_control)) {
      struct vrend_surface *surf = NULL;
      bool use_srgb = false;
      for (uint32_t i = 0; i < sub_ctx->nr_cbufs; i++) {
         if (sub_ctx->surf[i]) {
            surf = sub_ctx->surf[i];
            if (util_format_is_srgb(surf->format)) {
               use_srgb = true;
               break;
            }
         }
      }
      if (use_srgb) {
         glEnable(GL_FRAMEBUFFER_SRGB_EXT);
      } else {
         glDisable(GL_FRAMEBUFFER_SRGB_EXT);
      }
      sub_ctx->framebuffer_srgb_enabled = use_srgb;
   }

   sub_ctx->swizzle_output_rgb_to_bgr = 0;
   sub_ctx->needs_manual_srgb_encode_bitmask = 0;
   for (uint32_t i = 0; i < sub_ctx->nr_cbufs; i++) {
      struct vrend_surface *surf = sub_ctx->surf[i];
      if (!surf)
         continue;

      /* glTextureView() is not applied to eglimage-backed surfaces, because it
       * causes unintended format interpretation errors. But a swizzle may still
       * be necessary, e.g. for rgb* views on bgr* resources. Ensure this
       * happens by adding a shader swizzle to the final write of such surfaces.
       */
      if (vrend_resource_needs_redblue_swizzle(surf->texture, surf->format))
         sub_ctx->swizzle_output_rgb_to_bgr |= 1 << i;

      /* glTextureView() on eglimage-backed bgr* textures for is not supported.
       * To work around this for colorspace conversion, views are avoided
       * manual colorspace conversion is instead injected in the fragment
       * shader writing to such surfaces and during glClearColor(). */
      if (util_format_is_srgb(surf->format) &&
          !vrend_resource_supports_view(surf->texture, surf->format)) {
         VREND_DEBUG(dbg_tex, sub_ctx->parent,
                     "manually converting linear->srgb for EGL-backed framebuffer color attachment 0x%x"
                     " (surface format is %s; resource format is %s)\n",
                     i, util_format_name(surf->format), util_format_name(surf->texture->base.format));
         sub_ctx->needs_manual_srgb_encode_bitmask |= 1 << i;
      }
   }

   glDrawBuffers(sub_ctx->nr_cbufs, buffers);
}

void vrend_set_framebuffer_state(struct vrend_context *ctx,
                                 uint32_t nr_cbufs, uint32_t surf_handle[PIPE_MAX_COLOR_BUFS],
                                 uint32_t zsurf_handle)
{
   struct vrend_surface *surf, *zsurf;
   uint32_t old_num;
   GLenum status;
   GLint new_height = -1;
   bool new_fbo_origin_upper_left = false;

   struct vrend_sub_context *sub_ctx = ctx->sub;

   glBindFramebuffer(GL_FRAMEBUFFER, sub_ctx->fb_id);

   if (zsurf_handle) {
      zsurf = vrend_object_lookup(sub_ctx->object_hash, zsurf_handle, VIRGL_OBJECT_SURFACE);
      if (!zsurf) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SURFACE, zsurf_handle);
         return;
      }
   } else
      zsurf = NULL;

   if (sub_ctx->zsurf != zsurf) {
      vrend_surface_reference(&sub_ctx->zsurf, zsurf);
      vrend_hw_set_zsurf_texture(ctx);
   }

   old_num = sub_ctx->nr_cbufs;
   sub_ctx->nr_cbufs = nr_cbufs;

   for (uint32_t i = 0; i < nr_cbufs; i++) {
      if (surf_handle[i] != 0) {
         surf = vrend_object_lookup(sub_ctx->object_hash, surf_handle[i], VIRGL_OBJECT_SURFACE);
         if (!surf) {
            vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SURFACE, surf_handle[i]);
            return;
         }
      } else
         surf = NULL;

      if (sub_ctx->surf[i] != surf) {
         vrend_surface_reference(&sub_ctx->surf[i], surf);
         vrend_hw_set_color_surface(sub_ctx, i);
      }
   }

   if (old_num > sub_ctx->nr_cbufs) {
      for (uint32_t i = sub_ctx->nr_cbufs; i < old_num; i++) {
         vrend_surface_reference(&sub_ctx->surf[i], NULL);
         vrend_hw_set_color_surface(sub_ctx, i);
      }
   }

   /* find a buffer to set fb_height from */
   if (sub_ctx->nr_cbufs == 0 && !sub_ctx->zsurf) {
      new_height = 0;
      new_fbo_origin_upper_left = false;
   } else if (sub_ctx->nr_cbufs == 0) {
      new_height = u_minify(sub_ctx->zsurf->texture->base.height0, sub_ctx->zsurf->level);
      new_fbo_origin_upper_left = sub_ctx->zsurf->texture->y_0_top ? true : false;
   }
   else {
      surf = NULL;
      for (uint32_t i = 0; i < sub_ctx->nr_cbufs; i++) {
         if (sub_ctx->surf[i]) {
            surf = sub_ctx->surf[i];
            break;
         }
      }
      if (surf == NULL) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SURFACE, sub_ctx->nr_cbufs);
         return;
      }
      new_height = u_minify(surf->texture->base.height0, surf->level);
      new_fbo_origin_upper_left = surf->texture->y_0_top ? true : false;
   }

   if (new_height != -1) {
      if (sub_ctx->fb_height != (uint32_t)new_height ||
          sub_ctx->fbo_origin_upper_left != new_fbo_origin_upper_left) {
         sub_ctx->fb_height = new_height;
         sub_ctx->fbo_origin_upper_left = new_fbo_origin_upper_left;
         sub_ctx->viewport_state_dirty = (1 << 0);
      }
   }

   vrend_hw_emit_framebuffer_state(sub_ctx);

   if (sub_ctx->nr_cbufs > 0 || sub_ctx->zsurf) {
      status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
      if (status != GL_FRAMEBUFFER_COMPLETE)
         virgl_error("Failed to complete framebuffer 0x%x %s\n", status, ctx->debug_name);
   }

   sub_ctx->shader_dirty = true;
   sub_ctx->blend_state_dirty = true;
}

void vrend_set_framebuffer_state_no_attach(UNUSED struct vrend_context *ctx,
                                           uint32_t width, uint32_t height,
                                           uint32_t layers, uint32_t samples)
{
   int gl_ver = vrend_state.gl_major_ver * 10 + vrend_state.gl_minor_ver;

   if (has_feature(feat_fb_no_attach)) {
      glFramebufferParameteri(GL_FRAMEBUFFER,
                              GL_FRAMEBUFFER_DEFAULT_WIDTH, width);
      glFramebufferParameteri(GL_FRAMEBUFFER,
                              GL_FRAMEBUFFER_DEFAULT_HEIGHT, height);
      if (!(vrend_state.use_gles && gl_ver <= 31))
         glFramebufferParameteri(GL_FRAMEBUFFER,
                                 GL_FRAMEBUFFER_DEFAULT_LAYERS, layers);
      glFramebufferParameteri(GL_FRAMEBUFFER,
                              GL_FRAMEBUFFER_DEFAULT_SAMPLES, samples);
   }
}

/*
 * if the viewport Y scale factor is > 0 then we are rendering to
 * an FBO already so don't need to invert rendering?
 */
void vrend_set_viewport_states(struct vrend_context *ctx,
                               uint32_t start_slot,
                               uint32_t num_viewports,
                               const struct pipe_viewport_state *state)
{
   /* convert back to glViewport */
   GLint x, y;
   GLsizei width, height;
   GLclampd near_val, far_val;
   bool viewport_is_negative = (state[0].scale[1] < 0) ? true : false;
   unsigned i, idx;

   for (i = 0; i < num_viewports; i++) {
      GLfloat abs_s1 = fabsf(state[i].scale[1]);

      idx = start_slot + i;
      width = state[i].scale[0] * 2.0f;
      height = abs_s1 * 2.0f;
      x = state[i].translate[0] - state[i].scale[0];
      y = state[i].translate[1] - state[i].scale[1];

      if (!ctx->sub->rs_state.clip_halfz) {
         near_val = state[i].translate[2] - state[i].scale[2];
         far_val = near_val + (state[i].scale[2] * 2.0);
      } else {
         near_val = state[i].translate[2];
         far_val = state[i].scale[2] + state[i].translate[2];
      }

      if (ctx->sub->vps[idx].cur_x != x ||
          ctx->sub->vps[idx].cur_y != y ||
          ctx->sub->vps[idx].width != width ||
          ctx->sub->vps[idx].height != height ||
          ctx->sub->vps[idx].near_val != near_val ||
          ctx->sub->vps[idx].far_val != far_val ||
          (!(ctx->sub->viewport_state_initialized &= (1 << idx)))) {
         ctx->sub->vps[idx].cur_x = x;
         ctx->sub->vps[idx].cur_y = y;
         ctx->sub->vps[idx].width = width;
         ctx->sub->vps[idx].height = height;
         ctx->sub->vps[idx].near_val = near_val;
         ctx->sub->vps[idx].far_val = far_val;
         ctx->sub->viewport_state_dirty |= (1 << idx);
      }

      if (idx == 0) {
         if (ctx->sub->viewport_is_negative != viewport_is_negative) {
            ctx->sub->viewport_is_negative = viewport_is_negative;
            ctx->sub->sysvalue_data.winsys_adjust_y =
                  viewport_is_negative ? -1.f : 1.f;
            ctx->sub->sysvalue_data_cookie++;
         }
      }
   }
}

#define UPDATE_INT_SIGN_MASK(fmt, i, signed_mask, unsigned_mask) \
   if (vrend_state.use_integer && \
       util_format_is_pure_integer(fmt)) { \
      if (util_format_is_pure_uint(fmt)) \
         unsigned_mask |= (1 << i); \
      else \
         signed_mask |= (1 << i); \
   }

int vrend_create_vertex_elements_state(struct vrend_context *ctx,
                                       uint32_t handle,
                                       unsigned num_elements,
                                       const struct pipe_vertex_element *elements)
{
   struct vrend_vertex_element_array *v;
   const struct util_format_description *desc;
   GLenum type;
   unsigned i;
   uint32_t ret_handle;

   v = CALLOC_STRUCT(vrend_vertex_element_array);
   if (!v)
      return ENOMEM;

   v->count = num_elements;
   for (i = 0; i < num_elements; i++) {
      memcpy(&v->elements[i].base, &elements[i], sizeof(struct pipe_vertex_element));

      if (unlikely(elements[i].src_format >= PIPE_FORMAT_COUNT)) {
         FREE(v);
         return EINVAL;
      }

      desc = util_format_description(elements[i].src_format);
      type = GL_FALSE;
      switch (desc->channel[0].type) {
      case UTIL_FORMAT_TYPE_FLOAT:
         switch (desc->channel[0].size) {
         case 16: type = GL_HALF_FLOAT; break;
         case 32: type = GL_FLOAT; break;
         case 64: type = GL_DOUBLE; break;
         }
         break;
      case UTIL_FORMAT_TYPE_UNSIGNED:
         switch (desc->channel[0].size) {
         case 8: type = GL_UNSIGNED_BYTE; break;
         case 16: type = GL_UNSIGNED_SHORT; break;
         case 32: type = GL_UNSIGNED_INT; break;
         }
         break;
      case UTIL_FORMAT_TYPE_SIGNED:
         switch (desc->channel[0].size) {
         case 8: type = GL_BYTE; break;
         case 16: type = GL_SHORT; break;
         case 32: type = GL_INT; break;
         }
         break;
      }
      if (type == GL_FALSE) {
         switch (elements[i].src_format) {
         case PIPE_FORMAT_R10G10B10A2_SSCALED:
         case PIPE_FORMAT_R10G10B10A2_SNORM:
         case PIPE_FORMAT_B10G10R10A2_SNORM:
            type = GL_INT_2_10_10_10_REV;
            break;
         case PIPE_FORMAT_R10G10B10A2_USCALED:
         case PIPE_FORMAT_R10G10B10A2_UNORM:
         case PIPE_FORMAT_B10G10R10A2_UNORM:
            type = GL_UNSIGNED_INT_2_10_10_10_REV;
            break;
         case PIPE_FORMAT_R11G11B10_FLOAT:
            type = GL_UNSIGNED_INT_10F_11F_11F_REV;
            break;
         default:
            ;
         }
      }

      if (type == GL_FALSE) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_VERTEX_FORMAT, elements[i].src_format);
         FREE(v);
         return EINVAL;
      }

      v->elements[i].type = type;
      if (desc->channel[0].normalized)
         v->elements[i].norm = GL_TRUE;
      v->elements[i].nr_chan = desc->nr_channels;

      if (desc->nr_channels == 4 && desc->swizzle[0] == PIPE_SWIZZLE_Z)
         v->zyxw_bitmask |= 1 << i;
   }

   ret_handle = vrend_renderer_object_insert(ctx, v, handle,
                                             VIRGL_OBJECT_VERTEX_ELEMENTS);
   if (!ret_handle) {
      FREE(v);
      return ENOMEM;
   }
   v->owning_sub = ctx->sub;
   return 0;
}

void vrend_bind_vertex_elements_state(struct vrend_context *ctx,
                                      uint32_t handle)
{
   struct vrend_vertex_element_array *v;

   if (!handle) {
      ctx->sub->ve = NULL;
      return;
   }
   v = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_VERTEX_ELEMENTS);
   if (!v) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_HANDLE, handle);
      return;
   }

   if (ctx->sub->ve != v)
      ctx->sub->vbo_dirty = true;
   ctx->sub->ve = v;

   if (v->count > vrend_state.max_vertex_attributes) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_TOO_MANY_VERTEX_ATTRIBUTES, handle);
      return;
   }

   if (has_feature(feat_gles31_vertex_attrib_binding) && v->id == 0) {
      glGenVertexArrays(1, &v->id);
      glBindVertexArray(v->id);
      for (uint32_t i = 0; i < v->count; i++) {
         struct vrend_vertex_element *ve = &v->elements[i];
         GLint size = !vrend_state.use_gles && (v->zyxw_bitmask & (1 << i)) ? GL_BGRA : ve->nr_chan;

         if (util_format_is_pure_integer(ve->base.src_format)) {
            UPDATE_INT_SIGN_MASK(ve->base.src_format, i,
                                 v->signed_int_bitmask,
                                 v->unsigned_int_bitmask);
            glVertexAttribIFormat(i, size, ve->type, ve->base.src_offset);
         }
         else
            glVertexAttribFormat(i, size, ve->type, ve->norm, ve->base.src_offset);
         glVertexAttribBinding(i, ve->base.vertex_buffer_index);
         glVertexBindingDivisor(i, ve->base.instance_divisor);
         glEnableVertexAttribArray(i);
      }
   }
}

void vrend_set_constants(struct vrend_context *ctx,
                         uint32_t shader,
                         uint32_t num_constant,
                         const float *data)
{
   struct vrend_constants *consts;

   consts = &ctx->sub->consts[shader];
   ctx->sub->const_dirty[shader] = true;

   /* avoid reallocations by only growing the buffer */
   if (consts->num_allocated_consts < num_constant) {
      free(consts->consts);
      consts->consts = malloc(num_constant * sizeof(float));
      if (!consts->consts) {
         consts->num_allocated_consts = 0;
         return;
      }

      consts->num_allocated_consts = num_constant;
   }

   if (num_constant > 0)
      memcpy(consts->consts, data, num_constant * sizeof(unsigned int));

   consts->num_consts = num_constant;
}

void vrend_set_uniform_buffer(struct vrend_context *ctx,
                              uint32_t shader,
                              uint32_t index,
                              uint32_t offset,
                              uint32_t length,
                              uint32_t res_handle)
{
   struct vrend_resource *res;

   struct pipe_constant_buffer *cbs = &ctx->sub->cbs[shader][index];
   const uint32_t mask = 1u << index;

   if (res_handle) {
      res = vrend_renderer_ctx_res_lookup(ctx, res_handle);

      if (!res || !res->gl_id) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
         return;
      }

      vrend_resource_reference((struct vrend_resource **)&cbs->buffer, res);
      cbs->buffer_offset = offset;
      cbs->buffer_size = length;
      ctx->sub->const_bufs_used_mask[shader] |= mask;
   } else {
      vrend_resource_reference((struct vrend_resource **)&cbs->buffer, NULL);
      cbs->buffer_offset = 0;
      cbs->buffer_size = 0;
      ctx->sub->const_bufs_used_mask[shader] &= ~mask;
   }
   ctx->sub->const_bufs_dirty[shader] |= mask;
}

void vrend_set_index_buffer(struct vrend_context *ctx,
                            uint32_t res_handle,
                            uint32_t index_size,
                            uint32_t offset)
{
   struct vrend_resource *res;

   ctx->sub->ib.index_size = index_size;
   ctx->sub->ib.offset = offset;
   if (res_handle) {
      res = vrend_renderer_ctx_res_lookup(ctx, res_handle);
      if ((struct vrend_resource *)ctx->sub->ib.buffer != res) {
         if (!res || !res->gl_id) {
            vrend_resource_reference((struct vrend_resource **)&ctx->sub->ib.buffer, NULL);
            vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
            return;
         }
         vrend_resource_reference((struct vrend_resource **)&ctx->sub->ib.buffer, res);
      }
   } else {
      vrend_resource_reference((struct vrend_resource **)&ctx->sub->ib.buffer, NULL);
   }
}

void vrend_set_single_vbo(struct vrend_context *ctx,
                          uint32_t index,
                          uint32_t stride,
                          uint32_t buffer_offset,
                          uint32_t res_handle)
{
   struct vrend_resource *res;
   struct vrend_vertex_buffer *vbo = &ctx->sub->vbo[index];

   if (vbo->base.stride != stride ||
       vbo->base.buffer_offset != buffer_offset ||
       vbo->res_id != res_handle)
      ctx->sub->vbo_dirty = true;

   vbo->base.stride = stride;
   vbo->base.buffer_offset = buffer_offset;

   if (res_handle == 0) {
      vrend_resource_reference((struct vrend_resource **)&vbo->base.buffer, NULL);
      vbo->res_id = 0;
   } else if (vbo->res_id != res_handle) {
      res = vrend_renderer_ctx_res_lookup(ctx, res_handle);
      if (!res || !res->gl_id) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
         vbo->res_id = 0;
         return;
      }
      vrend_resource_reference((struct vrend_resource **)&vbo->base.buffer, res);
      vbo->res_id = res_handle;
   }
}

static void vrend_set_num_vbo_sub(struct vrend_sub_context *sub,
                                  int num_vbo)
{
   int old_num = sub->num_vbos;
   int i;

   sub->num_vbos = num_vbo;
   sub->old_num_vbos = old_num;

   if (old_num != num_vbo)
      sub->vbo_dirty = true;

   for (i = num_vbo; i < old_num; i++) {
      vrend_resource_reference((struct vrend_resource **)&sub->vbo[i].base.buffer, NULL);
      sub->vbo[i].res_id = 0;
   }

}

void vrend_set_num_vbo(struct vrend_context *ctx,
                       int num_vbo)
{
   vrend_set_num_vbo_sub(ctx->sub, num_vbo);
}

static GLenum vrend_get_arb_format(enum virgl_formats format)
{
   switch (format) {
   case VIRGL_FORMAT_A8_UNORM: return GL_R8;
   case VIRGL_FORMAT_A8_SINT: return GL_R8I;
   case VIRGL_FORMAT_A8_UINT: return GL_R8UI;
   case VIRGL_FORMAT_A16_FLOAT: return GL_R16F;
   case VIRGL_FORMAT_A32_FLOAT: return GL_R32F;
   case VIRGL_FORMAT_L8_UNORM: return GL_R8;
   case VIRGL_FORMAT_L8_SINT: return GL_R8I;
   case VIRGL_FORMAT_L8_UINT: return GL_R8UI;
   case VIRGL_FORMAT_L16_UNORM: return GL_R16F;
   case VIRGL_FORMAT_L16_SINT: return GL_R16I;
   case VIRGL_FORMAT_L16_UINT: return GL_R16UI;
   case VIRGL_FORMAT_L16_FLOAT: return GL_R16F;
   case VIRGL_FORMAT_L32_SINT: return GL_R32F;
   case VIRGL_FORMAT_L32_UINT: return GL_R32I;
   case VIRGL_FORMAT_L32_FLOAT: return GL_R32UI;
   case VIRGL_FORMAT_L8A8_UNORM: return GL_RG8;
   case VIRGL_FORMAT_L8A8_SINT: return GL_RG8I;
   case VIRGL_FORMAT_L8A8_UINT: return GL_RG8UI;
   case VIRGL_FORMAT_L16A16_UNORM: return GL_RG16;
   case VIRGL_FORMAT_L16A16_SINT: return GL_RG16I;
   case VIRGL_FORMAT_L16A16_UINT: return GL_RG16UI;
   case VIRGL_FORMAT_L16A16_FLOAT: return GL_RG16F;
   case VIRGL_FORMAT_L32A32_FLOAT: return GL_RG32F;
   case VIRGL_FORMAT_L32A32_SINT: return GL_RG32I;
   case VIRGL_FORMAT_L32A32_UINT: return GL_RG32UI;
   case VIRGL_FORMAT_I8_UNORM: return GL_R8;
   case VIRGL_FORMAT_I8_SINT: return GL_R8I;
   case VIRGL_FORMAT_I8_UINT: return GL_R8UI;
   case VIRGL_FORMAT_I16_UNORM: return GL_R16;
   case VIRGL_FORMAT_I16_SINT: return GL_R16I;
   case VIRGL_FORMAT_I16_UINT: return GL_R16UI;
   case VIRGL_FORMAT_I16_FLOAT: return GL_R16F;
   case VIRGL_FORMAT_I32_FLOAT: return GL_R32F;
   case VIRGL_FORMAT_I32_SINT: return GL_R32I;
   case VIRGL_FORMAT_I32_UINT: return GL_R32UI;
   default:
      virgl_warn("Texture format %s unsupported for texture buffers\n", util_format_name(format));
      return GL_R8;
   }
}

void vrend_set_single_sampler_view(struct vrend_context *ctx,
                                   uint32_t shader_type,
                                   uint32_t index,
                                   uint32_t handle)
{
   struct vrend_shader_view *shader_view = &ctx->sub->views[shader_type];
   struct vrend_sampler_view *view = NULL;
   struct vrend_texture *tex;

   if (handle) {
      view = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_SAMPLER_VIEW);
      if (!view) {
         vrend_sampler_view_reference(&shader_view->views[index], NULL);
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_HANDLE, handle);
         return;
      }
      if (shader_view->views[index] == view) {
         return;
      }
      /* we should have a reference to this texture taken at create time */
      tex = (struct vrend_texture *)view->texture;
      if (!tex) {
         virgl_warn("sampler view %u missing texture reference\n", handle);
         return;
      }

      shader_view->dirty_mask |= (1u << index);

      if (!has_bit(view->texture->storage_bits, VREND_STORAGE_GL_BUFFER)) {
         if (view->texture->gl_id == view->gl_id) {
            glBindTexture(view->target, view->gl_id);

            if (util_format_is_depth_or_stencil(view->format)) {
               if (vrend_state.use_core_profile == false) {
                  /* setting depth texture mode is deprecated in core profile */
                  glTexParameteri(view->texture->target, GL_DEPTH_TEXTURE_MODE, GL_RED);
               }
               if (has_feature(feat_stencil_texturing)) {
                  const struct util_format_description *desc = util_format_description(view->format);
                  if (!util_format_has_depth(desc)) {
                     glTexParameteri(view->texture->target, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
                  } else {
                     glTexParameteri(view->texture->target, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
                  }
               }
            }

            view->levels = view->u.tex.last_level - view->u.tex.first_level + 1;

            if (tex->cur_base != view->u.tex.first_level) {
               glTexParameteri(view->texture->target, GL_TEXTURE_BASE_LEVEL, view->u.tex.first_level);
               tex->cur_base = view->u.tex.first_level;
            }
            if (tex->cur_max != view->u.tex.last_level) {
               glTexParameteri(view->texture->target, GL_TEXTURE_MAX_LEVEL, view->u.tex.last_level);
               tex->cur_max = view->u.tex.last_level;
            }
            if (memcmp(tex->cur_swizzle, view->gl_swizzle, 4 * sizeof(GLint))) {
               if (vrend_state.use_gles) {
                  for (unsigned int i = 0; i < 4; ++i) {
                     if (tex->cur_swizzle[i] != view->gl_swizzle[i]) {
                         glTexParameteri(view->texture->target, GL_TEXTURE_SWIZZLE_R + i, view->gl_swizzle[i]);
                     }
                  }
               } else
                  glTexParameteriv(view->texture->target, GL_TEXTURE_SWIZZLE_RGBA, view->gl_swizzle);
               memcpy(tex->cur_swizzle, view->gl_swizzle, 4 * sizeof(GLint));
            }

            if (tex->cur_srgb_decode != view->srgb_decode && util_format_is_srgb(tex->base.base.format)) {
               if (!has_feature(feat_samplers) && has_feature(feat_texture_srgb_decode)) {
                  glTexParameteri(view->texture->target, GL_TEXTURE_SRGB_DECODE_EXT,
                                  view->srgb_decode);
                  tex->cur_srgb_decode = view->srgb_decode;
               }
            }
         }
      } else {
         GLenum internalformat;

         if (!view->texture->tbo_tex_id)
            glGenTextures(1, &view->texture->tbo_tex_id);

         glBindTexture(GL_TEXTURE_BUFFER, view->texture->tbo_tex_id);
         internalformat = tex_conv_table[view->format].internalformat;
         ctx->sub->shader_dirty = true;

         if (internalformat == GL_NONE ||
             (vrend_state.use_gles && internalformat == GL_ALPHA8)) {
            internalformat = vrend_get_arb_format(view->format);
         }

         if (has_feature(feat_texture_buffer_range)) {
            unsigned offset = view->u.buf.first_element;
            unsigned size = view->u.buf.last_element - view->u.buf.first_element + 1;
            int blsize = util_format_get_blocksize(view->format);

            if (offset + size > vrend_state.max_texture_buffer_size)
               size = vrend_state.max_texture_buffer_size - offset;
            offset *= blsize;
            size *= blsize;
            glTexBufferRange(GL_TEXTURE_BUFFER, internalformat, view->texture->gl_id, offset, size);
         } else
            glTexBuffer(GL_TEXTURE_BUFFER, internalformat, view->texture->gl_id);
      }
   }

   vrend_sampler_view_reference(&shader_view->views[index], view);
}

void vrend_set_num_sampler_views(struct vrend_context *ctx,
                                 uint32_t shader_type,
                                 uint32_t start_slot,
                                 uint32_t num_sampler_views)
{
   int last_slot = start_slot + num_sampler_views;
   int i;

   struct vrend_shader_view *shader_view = &ctx->sub->views[shader_type];
   for (i = last_slot; i < shader_view->max_num_views; i++)
      vrend_sampler_view_reference(&shader_view->views[i], NULL);

   shader_view->max_num_views = last_slot;
}

int vrend_set_single_image_view(struct vrend_context *ctx,
                                 uint32_t shader_type,
                                 uint32_t index,
                                 uint32_t format, uint32_t access,
                                 uint32_t layers_or_offset, uint32_t level_or_size,
                                 uint32_t handle)
{
   struct vrend_image_view *iview = &ctx->sub->image_views[shader_type][index];
   struct vrend_resource *res;

   if (handle) {
      if (!has_feature(feat_images))
         return EINVAL;

      if (unlikely(format >= ARRAY_SIZE(tex_conv_table))) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_FORMAT, format);
         return EINVAL;
      }

      res = vrend_renderer_ctx_res_lookup(ctx, handle);
      if (!res || !res->gl_id) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, handle);
         return EINVAL;
      }

      if (has_bit(res->storage_bits, VREND_STORAGE_GL_TEXTURE)) {
         uint16_t first_layer = layers_or_offset & 0xffff;
         uint16_t last_layer = (layers_or_offset >> 16) & 0xffff;
         if (last_layer - first_layer + 1 == 0)
            return EINVAL;

         iview->u.tex.last_layer = last_layer;
         iview->u.tex.first_layer = first_layer;
         iview->u.tex.level = level_or_size;
      } else {
         iview->u.buf.offset = layers_or_offset;
         iview->u.buf.size = level_or_size;
      }


      vrend_resource_reference(&iview->texture, res);
      iview->vformat = format;
      iview->format = tex_conv_table[format].internalformat;
      iview->access = access;
      ctx->sub->images_used_mask[shader_type] |= (1u << index);
   } else {
      vrend_resource_reference(&iview->texture, NULL);
      iview->format = 0;
      ctx->sub->images_used_mask[shader_type] &= ~(1u << index);
   }

   return 0;
}

void vrend_set_single_ssbo(struct vrend_context *ctx,
                           uint32_t shader_type,
                           uint32_t index,
                           uint32_t offset, uint32_t length,
                           uint32_t handle)
{
   struct vrend_ssbo *ssbo = &ctx->sub->ssbo[shader_type][index];
   struct vrend_resource *res;

   if (!has_feature(feat_ssbo))
      return;

   if (handle) {
      res = vrend_renderer_ctx_res_lookup(ctx, handle);
      if (!res || !res->gl_id) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, handle);
         return;
      }

      if (offset > res->base.width0 || length > res->base.width0 - offset) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_SSBO_BINDING_RANGE, handle);
         return;
      }

      vrend_resource_reference(&ssbo->res, res);
      ssbo->buffer_offset = offset;
      ssbo->buffer_size = length;
      ctx->sub->ssbo_used_mask[shader_type] |= (1u << index);
   } else {
      vrend_resource_reference(&ssbo->res, NULL);
      ssbo->buffer_offset = 0;
      ssbo->buffer_size = 0;
      ctx->sub->ssbo_used_mask[shader_type] &= ~(1u << index);
   }
}

void vrend_set_single_abo(struct vrend_context *ctx,
                          uint32_t index,
                          uint32_t offset, uint32_t length,
                          uint32_t handle)
{
   struct vrend_abo *abo = &ctx->sub->abo[index];
   struct vrend_resource *res;

   if (!has_feature(feat_atomic_counters))
      return;

   if (handle) {
      res = vrend_renderer_ctx_res_lookup(ctx, handle);
      if (!res || !res->gl_id) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, handle);
         return;
      }

      vrend_resource_reference(&abo->res, res);
      abo->buffer_offset = offset;
      abo->buffer_size = length;
      ctx->sub->abo_used_mask |= (1u << index);
   } else {
      vrend_resource_reference(&abo->res, NULL);
      abo->buffer_offset = 0;
      abo->buffer_size = 0;
      ctx->sub->abo_used_mask &= ~(1u << index);
   }
}

void vrend_memory_barrier(UNUSED struct vrend_context *ctx,
                          unsigned flags)
{
   GLbitfield gl_barrier = 0;

   if (!has_feature(feat_barrier))
      return;

   if ((flags & PIPE_BARRIER_ALL) == PIPE_BARRIER_ALL)
      gl_barrier = GL_ALL_BARRIER_BITS;
   else {
      if (flags & PIPE_BARRIER_VERTEX_BUFFER)
         gl_barrier |= GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT;
      if (flags & PIPE_BARRIER_INDEX_BUFFER)
         gl_barrier |= GL_ELEMENT_ARRAY_BARRIER_BIT;
      if (flags & PIPE_BARRIER_CONSTANT_BUFFER)
         gl_barrier |= GL_UNIFORM_BARRIER_BIT;
      if (flags & PIPE_BARRIER_TEXTURE)
         gl_barrier |= GL_TEXTURE_FETCH_BARRIER_BIT | GL_PIXEL_BUFFER_BARRIER_BIT;
      if (flags & PIPE_BARRIER_IMAGE)
         gl_barrier |= GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
      if (flags & PIPE_BARRIER_INDIRECT_BUFFER)
         gl_barrier |= GL_COMMAND_BARRIER_BIT;
      if (flags & PIPE_BARRIER_MAPPED_BUFFER)
         gl_barrier |= GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;
      if (flags & PIPE_BARRIER_FRAMEBUFFER)
         gl_barrier |= GL_FRAMEBUFFER_BARRIER_BIT;
      if (flags & PIPE_BARRIER_STREAMOUT_BUFFER)
         gl_barrier |= GL_TRANSFORM_FEEDBACK_BARRIER_BIT;
      if (flags & PIPE_BARRIER_UPDATE_BUFFER)
         gl_barrier |= GL_BUFFER_UPDATE_BARRIER_BIT;
      if (flags & PIPE_BARRIER_UPDATE_TEXTURE)
         gl_barrier |= GL_TEXTURE_UPDATE_BARRIER_BIT;
      if (flags & PIPE_BARRIER_SHADER_BUFFER) {
         gl_barrier |= GL_ATOMIC_COUNTER_BARRIER_BIT;
         if (has_feature(feat_ssbo_barrier))
            gl_barrier |= GL_SHADER_STORAGE_BARRIER_BIT;
      }
      if (has_feature(feat_qbo) && (flags & PIPE_BARRIER_QUERY_BUFFER))
         gl_barrier |= GL_QUERY_BUFFER_BARRIER_BIT;
   }
   glMemoryBarrier(gl_barrier);
}

void vrend_texture_barrier(UNUSED struct vrend_context *ctx,
                           unsigned flags)
{
   if (has_feature(feat_texture_barrier) && (flags & PIPE_TEXTURE_BARRIER_SAMPLER))
      glTextureBarrier();
   if (has_feature(feat_blend_equation_advanced) && (flags & PIPE_TEXTURE_BARRIER_FRAMEBUFFER))
      glBlendBarrierKHR();
}

static void vrend_destroy_shader_object(void *obj_ptr)
{
   struct vrend_shader_selector *state = obj_ptr;

   vrend_shader_state_reference(&state, NULL);
}

static inline bool can_emulate_logicop(enum pipe_logicop op)
{
   if (has_feature(feat_framebuffer_fetch_non_coherent) ||
       has_feature(feat_framebuffer_fetch))
      return true;

   /* These ops don't need to read back from the framebuffer */
   switch (op) {
   case PIPE_LOGICOP_CLEAR:
   case PIPE_LOGICOP_COPY:
   case PIPE_LOGICOP_SET:
   case PIPE_LOGICOP_COPY_INVERTED:
      return true;
   default:
      return false;
   }
}

static inline void vrend_sync_shader_io(struct vrend_sub_context *sub_ctx,
                                        struct vrend_shader_selector *sel,
                                        struct vrend_shader_key *key)
{
   enum pipe_shader_type type = sel->type;

   enum pipe_shader_type prev_type =
      (type != PIPE_SHADER_VERTEX) ? PIPE_SHADER_VERTEX : PIPE_SHADER_INVALID;

   /* Gallium sends and binds the shaders in the reverse order, so if an
    * old shader is still bound we should ignore the "previous" (as in
    * execution order) shader when the key is evaluated, unless the currently
    * bound shader selector is actually refers to the current shader. */
   if (sub_ctx->shaders[type] == sel) {
      switch (type) {
      case PIPE_SHADER_GEOMETRY:
         if (key->tcs_present || key->tes_present)
            prev_type = PIPE_SHADER_TESS_EVAL;
         break;
      case PIPE_SHADER_FRAGMENT:
         if (key->gs_present)
            prev_type = PIPE_SHADER_GEOMETRY;
         else if (key->tcs_present || key->tes_present)
            prev_type = PIPE_SHADER_TESS_EVAL;
         break;
      case PIPE_SHADER_TESS_EVAL:
         if (key->tcs_present)
            prev_type = PIPE_SHADER_TESS_CTRL;
         break;
      default:
         break;
      }
   }


   struct vrend_shader_selector *prev = prev_type != PIPE_SHADER_INVALID ? sub_ctx->shaders[prev_type] : NULL;

   if (prev) {
      if (!prev->sinfo.separable_program || !sel->sinfo.separable_program) {
         key->require_input_arrays = prev->sinfo.has_output_arrays;
         key->in_generic_expected_mask = prev->sinfo.out_generic_emitted_mask;
         key->in_texcoord_expected_mask = prev->sinfo.out_texcoord_emitted_mask;
         key->in_patch_expected_mask = prev->sinfo.out_patch_emitted_mask;
         key->in_arrays = prev->sinfo.output_arrays;

         memcpy(key->force_invariant_inputs, prev->sinfo.invariant_outputs, 4 * sizeof(uint32_t));
      }
      key->ssbo_binding_offset = prev->sinfo.ssbo_last_binding + 1;
      key->image_binding_offset = prev->sinfo.image_last_binding + 1;

      key->num_in_clip = sub_ctx->shaders[prev_type]->current->var_sinfo.num_out_clip;
      key->num_in_cull = sub_ctx->shaders[prev_type]->current->var_sinfo.num_out_cull;

      if (vrend_state.use_gles && type == PIPE_SHADER_FRAGMENT)
         key->fs.available_color_in_bits = sub_ctx->shaders[prev_type]->current->var_sinfo.legacy_color_bits;
   }

   enum pipe_shader_type next_type = PIPE_SHADER_INVALID;

   if (type == PIPE_SHADER_FRAGMENT) {
      key->fs.lower_left_origin = !sub_ctx->fbo_origin_upper_left;
      key->fs.swizzle_output_rgb_to_bgr = sub_ctx->swizzle_output_rgb_to_bgr;
      key->fs.needs_manual_srgb_encode_bitmask = sub_ctx->needs_manual_srgb_encode_bitmask;
      if (vrend_state.use_gles && can_emulate_logicop(sub_ctx->blend_state.logicop_func)) {
         key->fs.logicop_enabled = sub_ctx->blend_state.logicop_enable;
         key->fs.logicop_func = sub_ctx->blend_state.logicop_func;
      }
      int fs_prim_mode = sub_ctx->prim_mode; // inherit draw-call's mode

      // Only use coord_replace if frag shader receives GL_POINTS
      if (prev) {
         switch (prev->type) {
         case PIPE_SHADER_TESS_EVAL:
            if (prev->sinfo.tes_point_mode)
               fs_prim_mode = PIPE_PRIM_POINTS;
            break;
         case PIPE_SHADER_GEOMETRY:
            fs_prim_mode = prev->sinfo.gs_out_prim;
            break;
         default:
            break;
         }
      }

      key->fs.prim_is_points = (fs_prim_mode == PIPE_PRIM_POINTS);
      key->fs.coord_replace = sub_ctx->rs_state.point_quad_rasterization
         && key->fs.prim_is_points
         ? sub_ctx->rs_state.sprite_coord_enable
         : 0x0;

   } else {
      if (sub_ctx->shaders[PIPE_SHADER_FRAGMENT])
         next_type = PIPE_SHADER_FRAGMENT;
  }

   switch (type) {
   case PIPE_SHADER_VERTEX:
     if (key->tcs_present)
       next_type = PIPE_SHADER_TESS_CTRL;
     else if (key->gs_present)
       next_type = PIPE_SHADER_GEOMETRY;
     else if (key->tes_present) {
        if (!vrend_state.use_gles)
           next_type = PIPE_SHADER_TESS_EVAL;
        else
           next_type = PIPE_SHADER_TESS_CTRL;
     }
     break;
   case PIPE_SHADER_TESS_CTRL:
      next_type = PIPE_SHADER_TESS_EVAL;
     break;
   case PIPE_SHADER_TESS_EVAL:
     if (key->gs_present)
       next_type = PIPE_SHADER_GEOMETRY;
   default:
     break;
   }

   if (next_type != PIPE_SHADER_INVALID && sub_ctx->shaders[next_type]) {
      if (!sub_ctx->shaders[next_type]->sinfo.separable_program ||
          !sel->sinfo.separable_program) {
         struct vrend_shader_selector *next = sub_ctx->shaders[next_type];

         key->use_pervertex_in = next->sinfo.use_pervertex_in;
         key->require_output_arrays = next->sinfo.has_input_arrays;
         key->out_generic_expected_mask = next->sinfo.in_generic_emitted_mask;
         key->out_texcoord_expected_mask = next->sinfo.in_texcoord_emitted_mask;

         /* FS gets the clip/cull info in the key from this shader, so
          * we can avoid re-translating this shader by not updating the
          * info in the key */
         if (next_type != PIPE_SHADER_FRAGMENT) {
            key->num_out_clip = sub_ctx->shaders[next_type]->current->var_sinfo.num_in_clip;
            key->num_out_cull = sub_ctx->shaders[next_type]->current->var_sinfo.num_in_cull;
         }

         if (next_type == PIPE_SHADER_FRAGMENT) {
            struct vrend_shader *fs =
                  sub_ctx->shaders[PIPE_SHADER_FRAGMENT]->current;
            key->fs_info = fs->var_sinfo.fs_info;
            if (type == PIPE_SHADER_VERTEX && sub_ctx->shaders[type]) {
               uint32_t fog_input = sub_ctx->shaders[next_type]->sinfo.fog_input_mask;
               uint32_t fog_output = sub_ctx->shaders[type]->sinfo.fog_output_mask;

               // We only want to issue the fixup for inputs not fed by
               // the outputs of the previous stage
               key->vs.fog_fixup_mask = (fog_input ^ fog_output) & fog_input;
            }
         }
      }
   }
}

static bool vrend_get_swizzle(struct vrend_sampler_view *view,
                              enum pipe_swizzle swizzle[4])
{
   static const enum pipe_swizzle OOOR[] = {PIPE_SWIZZLE_0, PIPE_SWIZZLE_0, PIPE_SWIZZLE_0, PIPE_SWIZZLE_X};
   static const enum pipe_swizzle RRR1[] = {PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_1};
   static const enum pipe_swizzle RRRG[] = {PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_Y};
   static const enum pipe_swizzle RRRR[] = {PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_X};
   static const enum pipe_swizzle RG01[] = {PIPE_SWIZZLE_X, PIPE_SWIZZLE_Y, PIPE_SWIZZLE_0, PIPE_SWIZZLE_1};
   static const enum pipe_swizzle R001[] = {PIPE_SWIZZLE_X, PIPE_SWIZZLE_0, PIPE_SWIZZLE_0, PIPE_SWIZZLE_1};

   if (tex_conv_table[view->format].flags & VIRGL_TEXTURE_NEED_SWIZZLE) {
      swizzle[0] = tex_conv_table[view->format].swizzle[0];
      swizzle[1] = tex_conv_table[view->format].swizzle[1];
      swizzle[2] = tex_conv_table[view->format].swizzle[2];
      swizzle[3] = tex_conv_table[view->format].swizzle[3];
      return true;
   } else {
      switch (view->format) {
      case VIRGL_FORMAT_A8_UNORM:
         if (vrend_state.use_gles) {
            memcpy(swizzle, OOOR, sizeof(OOOR));
            return true;
         }
         break;
      case VIRGL_FORMAT_A16_FLOAT:
      case VIRGL_FORMAT_A32_FLOAT:
         if (vrend_state.use_core_profile) {
            memcpy(swizzle, OOOR, sizeof(OOOR));
            return true;
         }
         break;
      case VIRGL_FORMAT_L8_UNORM:
      case VIRGL_FORMAT_L8_SINT:
      case VIRGL_FORMAT_L8_UINT:
      case VIRGL_FORMAT_L16_UNORM:
      case VIRGL_FORMAT_L16_SINT:
      case VIRGL_FORMAT_L16_UINT:
      case VIRGL_FORMAT_L16_FLOAT:
      case VIRGL_FORMAT_L32_SINT:
      case VIRGL_FORMAT_L32_UINT:
      case VIRGL_FORMAT_L32_FLOAT:
         memcpy(swizzle, RRR1, sizeof(RRR1));
         return true;
      case VIRGL_FORMAT_L8A8_SINT:
      case VIRGL_FORMAT_L8A8_UINT:
      case VIRGL_FORMAT_L16A16_SINT:
      case VIRGL_FORMAT_L16A16_UINT:
      case VIRGL_FORMAT_L16A16_FLOAT:
      case VIRGL_FORMAT_L32A32_FLOAT:
      case VIRGL_FORMAT_L32A32_SINT:
      case VIRGL_FORMAT_L32A32_UINT:
         if (!vrend_state.use_core_profile)
            break;
         /* fallthrough */
      case VIRGL_FORMAT_L8A8_UNORM:
      case VIRGL_FORMAT_L16A16_UNORM:
         memcpy(swizzle, RRRG, sizeof(RRRG));
         return true;
      case VIRGL_FORMAT_I8_UNORM:
      case VIRGL_FORMAT_I8_SINT:
      case VIRGL_FORMAT_I8_UINT:
      case VIRGL_FORMAT_I16_UNORM:
      case VIRGL_FORMAT_I16_SINT:
      case VIRGL_FORMAT_I16_UINT:
      case VIRGL_FORMAT_I16_FLOAT:
      case VIRGL_FORMAT_I32_FLOAT:
      case VIRGL_FORMAT_I32_SINT:
      case VIRGL_FORMAT_I32_UINT:
         memcpy(swizzle, RRRR, sizeof(RRRR));
         return true;
      case VIRGL_FORMAT_R32G32_FLOAT:
      case VIRGL_FORMAT_R32G32_UINT:
      case VIRGL_FORMAT_R32G32_SINT:
      case VIRGL_FORMAT_R16G16_FLOAT:
      case VIRGL_FORMAT_R16G16_UINT:
      case VIRGL_FORMAT_R16G16_SINT:
      case VIRGL_FORMAT_R16G16_SNORM:
      case VIRGL_FORMAT_R16G16_UNORM:
      case VIRGL_FORMAT_R8G8_UINT:
      case VIRGL_FORMAT_R8G8_SINT:
      case VIRGL_FORMAT_R8G8_SNORM:
      case VIRGL_FORMAT_R8G8_UNORM:
      case VIRGL_FORMAT_R8G8_SSCALED:
      case VIRGL_FORMAT_R8G8_USCALED:
         memcpy(swizzle, RG01, sizeof(RG01));
         return true;
      case VIRGL_FORMAT_R32_FLOAT:
      case VIRGL_FORMAT_R32_UINT:
      case VIRGL_FORMAT_R32_SINT:
      case VIRGL_FORMAT_R16_FLOAT:
      case VIRGL_FORMAT_R16_UINT:
      case VIRGL_FORMAT_R16_SINT:
      case VIRGL_FORMAT_R16_SNORM:
      case VIRGL_FORMAT_R16_UNORM:
      case VIRGL_FORMAT_R8_UINT:
      case VIRGL_FORMAT_R8_SINT:
      case VIRGL_FORMAT_R8_SNORM:
      case VIRGL_FORMAT_R8_UNORM:
      case VIRGL_FORMAT_R8_SSCALED:
      case VIRGL_FORMAT_R8_USCALED:
         memcpy(swizzle, R001, sizeof(R001));
         return true;
      default:
         break;
      }
   }
   return false;
}

static inline bool
vrend_shader_use_core(struct vrend_context *ctx)
{
   return ctx->shader_cfg.glsl_version >= 140;
}

static inline void vrend_fill_shader_key(struct vrend_sub_context *sub_ctx,
                                         struct vrend_shader_selector *sel,
                                         struct vrend_shader_key *key)
{
   enum pipe_shader_type type = sel->type;

   if (vrend_state.use_core_profile) {
      bool add_alpha_test = true;

      /* Only use integer info when drawing to avoid stale info.
       * Since we can get here from link_shaders before actually drawing anything,
       * we may have no vertex element array */
      if (vrend_state.use_integer && sub_ctx->drawing && sub_ctx->ve &&
          type == PIPE_SHADER_VERTEX) {
         key->vs.attrib_signed_int_bitmask = sub_ctx->ve->signed_int_bitmask;
         key->vs.attrib_unsigned_int_bitmask = sub_ctx->ve->unsigned_int_bitmask;
      }
      if (type == PIPE_SHADER_FRAGMENT) {
         for (uint32_t i = 0; i < sub_ctx->nr_cbufs; i++) {
            if (!sub_ctx->surf[i])
               continue;
            if (vrend_format_is_emulated_alpha(sub_ctx->surf[i]->format))
               key->fs.cbufs_are_a8_bitmask |= (1 << i);
            if (util_format_is_pure_integer(sub_ctx->surf[i]->format)) {
               add_alpha_test = false;
               UPDATE_INT_SIGN_MASK(sub_ctx->surf[i]->format, i,
                                    key->fs.cbufs_signed_int_bitmask,
                                    key->fs.cbufs_unsigned_int_bitmask);
            }
            /* Currently we only use this information if logicop_enable is set */
            if (sub_ctx->blend_state.logicop_enable) {
                key->fs.surface_component_bits[i] = util_format_get_component_bits(sub_ctx->surf[i]->format, UTIL_FORMAT_COLORSPACE_RGB, 0);
            }
         }
         if (add_alpha_test) {
            key->add_alpha_test = sub_ctx->dsa_state.alpha.enabled;
            key->alpha_test = sub_ctx->dsa_state.alpha.func;
         }
      }
   }

   if (vrend_shader_use_core(sub_ctx->parent)) {
      key->pstipple_enabled = sub_ctx->rs_state.poly_stipple_enable;
      key->color_two_side = sub_ctx->rs_state.light_twoside;
      key->flatshade = sub_ctx->rs_state.flatshade ? true : false;
   }

   if (vrend_state.use_gles && sub_ctx->ve && type == PIPE_SHADER_VERTEX) {
      key->vs.attrib_zyxw_bitmask = sub_ctx->ve->zyxw_bitmask;
   }

   key->gs_present = !!sub_ctx->shaders[PIPE_SHADER_GEOMETRY] || type == PIPE_SHADER_GEOMETRY;
   key->tcs_present = !!sub_ctx->shaders[PIPE_SHADER_TESS_CTRL] || type == PIPE_SHADER_TESS_CTRL;
   key->tes_present = !!sub_ctx->shaders[PIPE_SHADER_TESS_EVAL] || type == PIPE_SHADER_TESS_EVAL;

   if (type != PIPE_SHADER_COMPUTE)
      vrend_sync_shader_io(sub_ctx, sel, key);

   if (type == PIPE_SHADER_GEOMETRY)
      key->gs.emit_clip_distance = sub_ctx->rs_state.clip_plane_enable != 0;

   for (int i = 0; i < sub_ctx->views[type].max_num_views; i++) {
      struct vrend_sampler_view *view = sub_ctx->views[type].views[i];
      if (!view)
         continue;

      if (view->emulated_rect) {
         vrend_shader_sampler_views_mask_set(key->sampler_views_emulated_rect_mask, i);
      }

      if (view->texture->target == GL_TEXTURE_BUFFER) {
         enum pipe_swizzle swizzle[4];
         if (vrend_get_swizzle(view, swizzle)) {
            vrend_shader_sampler_views_mask_set(key->sampler_views_lower_swizzle_mask, i);
            key->tex_swizzle[i] = swizzle[0]  |
                                  swizzle[1] << 3 |
                                  swizzle[2] << 6 |
                                  swizzle[3] << 9;
         }
      }
   }
}

static int vrend_shader_create(struct vrend_context *ctx,
                               struct vrend_shader *shader,
                               struct vrend_shader_key *key)
{
   if (shader->sel->tokens) {

      VREND_DEBUG(dbg_shader_tgsi, ctx, "TGSI received:");
      VREND_DEBUG_EXT(dbg_shader_tgsi, ctx, vrend_dump_tgsi(shader->sel->tokens, 0));
      VREND_DEBUG(dbg_shader_tgsi, ctx, "\n");

      bool ret = vrend_convert_shader(ctx, &ctx->shader_cfg, shader->sel->tokens,
                                      shader->sel->req_local_mem, key, &shader->sel->sinfo,
                                      &shader->var_sinfo, &shader->glsl_strings);
      if (!ret) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SHADER, shader->sel->type);
         return -1;
      }
   } else if (!ctx->shader_cfg.use_gles && shader->sel->type != PIPE_SHADER_TESS_CTRL) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SHADER, shader->sel->type);
      return -1;
   }

   shader->key = *key;
   return 0;
}

static int vrend_shader_select(struct vrend_sub_context *sub_ctx,
                               struct vrend_shader_selector *sel,
                               bool *dirty)
{
   struct vrend_shader_key key;
   struct vrend_shader *shader = NULL;
   int r;

   memset(&key, 0, sizeof(key));
   vrend_fill_shader_key(sub_ctx, sel, &key);

   if (sel->current) {
      if (!memcmp(&sel->current->key, &key, sizeof(key)))
         return 0;

      struct vrend_shader *p = sel->current;
      struct vrend_shader *c = p->next_variant;

      while (c && memcmp(&c->key, &key, sizeof(key)) != 0) {
         p = c;
         c = c->next_variant;
      }
      if (c) {
         p->next_variant = c->next_variant;
         shader = c;
      }
   }

   if (!shader) {
      shader = CALLOC_STRUCT(vrend_shader);
      shader->sel = sel;
      list_inithead(&shader->programs);
      strarray_alloc(&shader->glsl_strings, SHADER_MAX_STRINGS);

      r = vrend_shader_create(sub_ctx->parent, shader, &key);
      if (r) {
         sel->current = NULL;
         strarray_free(&shader->glsl_strings, true);
         FREE(shader);
         return r;
      }
   }
   if (dirty)
      *dirty = true;

   shader->next_variant = sel->current;
   sel->current = shader;
   return 0;
}

static void *vrend_create_shader_state(const struct pipe_stream_output_info *so_info,
                                       uint32_t req_local_mem,
                                       enum pipe_shader_type pipe_shader_type)
{
   struct vrend_shader_selector *sel = CALLOC_STRUCT(vrend_shader_selector);

   if (!sel)
      return NULL;

   sel->req_local_mem = req_local_mem;
   sel->type = pipe_shader_type;
   sel->sinfo.so_info = *so_info;
   pipe_reference_init(&sel->reference, 1);

   return sel;
}

static int vrend_finish_shader(struct vrend_context *ctx,
                               struct vrend_shader_selector *sel,
                               const struct tgsi_token *tokens)
{
   sel->tokens = tgsi_dup_tokens(tokens);

   if (!ctx->shader_cfg.use_gles && sel->type != PIPE_SHADER_COMPUTE)
      sel->sinfo.separable_program =
            vrend_shader_query_separable_program(sel->tokens, &ctx->shader_cfg);

   return vrend_shader_select(ctx->sub, sel, NULL) ? EINVAL : 0;
}

static int vrend_shader_assign_tgsi(struct vrend_context *ctx,
                                    struct vrend_shader_selector *sel,
                                    const char *shader_buf,
                                    uint32_t current_length,
                                    uint32_t num_tokens)
{
   struct tgsi_token *tokens;

   /* check for null termination */
   if (current_length < 4 || !memchr(shader_buf + current_length - 4, '\0', 4))
      return EINVAL;

   tokens = calloc(num_tokens + 10, sizeof(struct tgsi_token));
   if (!tokens)
      return ENOMEM;

   if (!tgsi_text_translate(shader_buf, tokens, num_tokens + 10)) {
      free(tokens);
      return EINVAL;
   }

   if (vrend_finish_shader(ctx, sel, tokens)) {
      free(tokens);
      return EINVAL;
   }

   free(tokens);
   return 0;
}

static int vrend_shader_store_long_shader(uint32_t handle,
                                          struct vrend_shader_selector *sel,
                                          uint32_t pkt_length_bytes,
                                          uint32_t expected_token_count,
                                          const char *shd_text,
                                          struct vrend_long_shader_buffer **lsb)
{
   /* We only got a partial shader, start a long shader transfer */
   struct vrend_long_shader_buffer *lsbuf = CALLOC_STRUCT(vrend_long_shader_buffer);
   if (!lsbuf)
      return ENOMEM;

   lsbuf->handle = handle;
   vrend_shader_state_reference(&lsbuf->sel, sel);
   lsbuf->current_length = pkt_length_bytes;
   lsbuf->total_length = expected_token_count * 4;
   lsbuf->tmp_buf = malloc(lsbuf->total_length);
   if (!lsbuf->tmp_buf) {
      vrend_destroy_long_shader_buffer(lsbuf);
      return ENOMEM;
   }

   memcpy(lsbuf->tmp_buf, shd_text, pkt_length_bytes);
   *lsb = lsbuf;
   return 0;
}

int vrend_create_shader(struct vrend_context *ctx,
                        uint32_t handle,
                        const struct pipe_stream_output_info *so_info,
                        uint32_t req_local_mem,
                        const char *shd_text, uint32_t offlen, uint32_t num_tokens,
                        enum pipe_shader_type type, uint32_t pkt_length)
{
   if (type == PIPE_SHADER_GEOMETRY &&
       !has_feature(feat_geometry_shader)) {
      virgl_error("Geometry shader not supported\n");
      return EINVAL;
   }

   if ((type == PIPE_SHADER_TESS_CTRL ||
        type == PIPE_SHADER_TESS_EVAL) &&
       !has_feature(feat_tessellation)) {
      virgl_error("Tesselation shaders not supported\n");
       return EINVAL;
   }

   if (type == PIPE_SHADER_COMPUTE &&
       !has_feature(feat_compute_shader)) {
      virgl_error("Compute shaders not supported\n");
      return EINVAL;
   }

   /* offlen & VIRGL_OBJ_SHADER_OFFSET_CONT declares whether we have a new shader or
    * a shader continuation
    *
    * offlen & ~VIRGL_OBJ_SHADER_OFFSET_CONT
    *  is the total shader length for   a new shader (new_shader == true)
    *  the continuation offset for a shader continuation (new_shader == false) */
   bool new_shader = !(offlen & VIRGL_OBJ_SHADER_OFFSET_CONT);
   struct vrend_sub_context *sub_ctx = ctx->sub;

   /* if we have an in progress one - don't allow a new shader
      of that type or a different handle. */
   if (sub_ctx->long_shader_in_progress[type]) {
      if (new_shader == true) {
         virgl_error("Expected long shader continuation, got new shader\n");
         return EINVAL;
      }
      if (handle != sub_ctx->long_shader_in_progress[type]->handle) {
         virgl_error("Long shader continuation handle invalid\n");
         return EINVAL;
      }
   }

   /* Ensure that we won't hit an overflow */
   if (pkt_length >= (UINT32_MAX >> 2)) {
      virgl_error("Packed length overflow\n");
      return EINVAL;
   }

   const uint32_t pkt_length_bytes = pkt_length * 4;

   if (new_shader) {
      const uint32_t expected_token_count = (offlen + 3) / 4;  /* round up count */
      if (expected_token_count < pkt_length) {
         virgl_error("Invalid expected token count\n");
        return EINVAL;
      }

      struct vrend_shader_selector *sel;
      sel = vrend_create_shader_state(so_info, req_local_mem, type);
      if (sel == NULL) {
         virgl_error("Unable to allocate shader state\n");
         return ENOMEM;
      }

      int ret_handle = vrend_renderer_object_insert(ctx, sel, handle, VIRGL_OBJECT_SHADER);
      if (ret_handle == 0) {
         vrend_destroy_shader_selector(sel);
         return ENOMEM;
      }

      if (expected_token_count > pkt_length) {
         /* We only got a partial shader, start a long shader transfer */
         int ret = vrend_shader_store_long_shader(handle, sel,
                                                  pkt_length_bytes, expected_token_count,
                                                  shd_text,
                                                  &sub_ctx->long_shader_in_progress[type]);
         if (ret != 0) {
            vrend_renderer_object_destroy(ctx, handle);
            virgl_error("Error storing long shader\n");
            return ret;
         }
      } else {
         int ret = vrend_shader_assign_tgsi(ctx, sel,
                                            shd_text, pkt_length_bytes,
                                            num_tokens);
         if (ret != 0) {
            vrend_renderer_object_destroy(ctx, handle);
            virgl_error("Error assigning TGSI\n");
            return ret;
         }
      }
   } else {
      struct vrend_long_shader_buffer *lsbuf = sub_ctx->long_shader_in_progress[type];
      if (!lsbuf) {
         virgl_error("Got continuation without original long shader %u\n", handle);
         vrend_renderer_object_destroy(ctx, handle);
         return EINVAL;
      }

      offlen &= ~VIRGL_OBJ_SHADER_OFFSET_CONT;
      if (offlen != lsbuf->current_length) {
         virgl_error("Got mismatched shader continuation %u vs %u\n",
                 offlen, lsbuf->current_length);
         sub_ctx->long_shader_in_progress[type] = NULL;
         vrend_destroy_long_shader_buffer(lsbuf);
         vrend_renderer_object_destroy(ctx, handle);
         return EINVAL;
      }

      if (lsbuf->total_length - lsbuf->current_length < pkt_length_bytes) {
         virgl_error("Got too large shader continuation %u vs %u\n",
                      pkt_length_bytes + lsbuf->current_length, lsbuf->total_length);
         sub_ctx->long_shader_in_progress[type] = NULL;
         vrend_destroy_long_shader_buffer(lsbuf);
         vrend_renderer_object_destroy(ctx, handle);
         return EINVAL;
      }

      memcpy(lsbuf->tmp_buf + lsbuf->current_length, shd_text, pkt_length_bytes);
      lsbuf->current_length += pkt_length_bytes;
      if (lsbuf->current_length == lsbuf->total_length) {
         int ret = vrend_shader_assign_tgsi(ctx, lsbuf->sel,
                                            lsbuf->tmp_buf, lsbuf->current_length,
                                            num_tokens);
         sub_ctx->long_shader_in_progress[type] = NULL;
         vrend_destroy_long_shader_buffer(lsbuf);
         if (ret != 0) {
            virgl_error("Error assigning TGSI\n");
            vrend_renderer_object_destroy(ctx, handle);
            return ret;
         }
      }
   }

   return 0;
}

void vrend_bind_shader(struct vrend_context *ctx,
                       uint32_t handle,
                       enum pipe_shader_type type)
{
   struct vrend_shader_selector *sel;

   if (type > PIPE_SHADER_COMPUTE)
      return;

   struct vrend_sub_context *sub_ctx = ctx->sub;

   if (handle == 0) {
      if (type == PIPE_SHADER_COMPUTE)
         sub_ctx->cs_shader_dirty = true;
      else
         sub_ctx->shader_dirty = true;
      vrend_shader_state_reference(&sub_ctx->shaders[type], NULL);
      return;
   }

   sel = vrend_object_lookup(sub_ctx->object_hash, handle, VIRGL_OBJECT_SHADER);
   if (!sel)
      return;

   if (sel->type != type)
      return;

   if (sub_ctx->shaders[sel->type] != sel) {
      if (type == PIPE_SHADER_COMPUTE)
         sub_ctx->cs_shader_dirty = true;
      else
         sub_ctx->shader_dirty = true;
      sub_ctx->prog_ids[sel->type] = 0;
   }

   vrend_shader_state_reference(&sub_ctx->shaders[sel->type], sel);
}

static float
vrend_color_encode_as_srgb(float color) {
   return color <= 0.0031308f
      ? 12.92f * color
      : 1.055f * powf(color, (1.f / 2.4f)) - 0.055f;
}

static void vrend_clear_prepare(struct vrend_sub_context *sub_ctx,
                                struct vrend_surface *surf, unsigned buffers,
                                float *colorf, double depth, unsigned stencil) {
   if (surf && util_format_is_srgb(surf->format) &&
       !vrend_resource_supports_view(surf->texture, surf->format)) {
      VREND_DEBUG(dbg_tex, sub_ctx->parent,
                  "manually converting glClearColor from linear->srgb colorspace for EGL-backed framebuffer color attachment"
                  " (surface format is %s; resource format is %s)\n",
                  util_format_name(surf->format),
                  util_format_name(surf->texture->base.format));
      for (int i = 0; i < 3; ++i) // i < 3: don't convert alpha channel
         colorf[i] = vrend_color_encode_as_srgb(colorf[i]);
   }

   if (buffers & PIPE_CLEAR_COLOR) {
      if (surf && vrend_format_is_emulated_alpha(surf->format)) {
         glClearColor(colorf[3], 0.0, 0.0, 0.0);
      } else if (surf && 
                 vrend_resource_needs_redblue_swizzle(surf->texture, surf->format)) {
         VREND_DEBUG(dbg_bgra, sub_ctx->parent, "swizzling glClearColor() since rendering surface is an externally-stored BGR* resource\n");
         glClearColor(colorf[2], colorf[1], colorf[0], colorf[3]);
      } else {
         glClearColor(colorf[0], colorf[1], colorf[2], colorf[3]);
      }

      /* This function implements Gallium's full clear callback (st->pipe->clear) on the host. This
         callback requires no color component be masked. We must unmask all components before
         calling glClear* and restore the previous colormask afterwards, as Gallium expects. */
      if (sub_ctx->hw_blend_state.independent_blend_enable &&
          has_feature(feat_indep_blend)) {
         int i;
         for (i = 0; i < PIPE_MAX_COLOR_BUFS; i++)
            glColorMaskIndexedEXT(i, GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
      } else
         glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
   }

   if (buffers & PIPE_CLEAR_DEPTH) {
      /* gallium clears don't respect depth mask */
      glDepthMask(GL_TRUE);
      if (vrend_state.use_gles) {
         if (0.0f < depth && depth > 1.0f) {
            // Only warn, it is clamped by the function.
            report_gles_warn(sub_ctx->parent, GLES_WARN_DEPTH_CLEAR);
         }
         glClearDepthf(depth);
      } else {
         glClearDepth(depth);
      }
   }

   if (buffers & PIPE_CLEAR_STENCIL) {
      glStencilMask(~0u);
      glClearStencil(stencil);
   }

   if (sub_ctx->hw_rs_state.rasterizer_discard)
      glDisable(GL_RASTERIZER_DISCARD);
}

static void vrend_clear_finish(struct vrend_sub_context *sub_ctx,
                               unsigned buffers) {
   /* Is it really necessary to restore the old states? The only reason we
    * get here is because the guest cleared all those states but gallium
    * didn't forward them before calling the clear command
    */
   if (sub_ctx->hw_rs_state.rasterizer_discard)
       glEnable(GL_RASTERIZER_DISCARD);

   if (buffers & PIPE_CLEAR_DEPTH) {
      if (!sub_ctx->dsa_state.depth.writemask)
         glDepthMask(GL_FALSE);
   }

   /* Restore previous stencil buffer write masks for both front and back faces */
   if (buffers & PIPE_CLEAR_STENCIL) {
      glStencilMaskSeparate(GL_FRONT, sub_ctx->dsa_state.stencil[0].writemask);
      glStencilMaskSeparate(GL_BACK, sub_ctx->dsa_state.stencil[1].writemask);
   }

   /* Restore previous colormask */
   if (buffers & PIPE_CLEAR_COLOR) {
      if (sub_ctx->hw_blend_state.independent_blend_enable &&
          has_feature(feat_indep_blend)) {
         int i;
         for (i = 0; i < PIPE_MAX_COLOR_BUFS; i++) {
            struct pipe_blend_state *blend = &sub_ctx->hw_blend_state;
            glColorMaskIndexedEXT(i, blend->rt[i].colormask & PIPE_MASK_R ? GL_TRUE : GL_FALSE,
                                  blend->rt[i].colormask & PIPE_MASK_G ? GL_TRUE : GL_FALSE,
                                  blend->rt[i].colormask & PIPE_MASK_B ? GL_TRUE : GL_FALSE,
                                  blend->rt[i].colormask & PIPE_MASK_A ? GL_TRUE : GL_FALSE);
         }
      } else {
         glColorMask(sub_ctx->hw_blend_state.rt[0].colormask & PIPE_MASK_R ? GL_TRUE : GL_FALSE,
                     sub_ctx->hw_blend_state.rt[0].colormask & PIPE_MASK_G ? GL_TRUE : GL_FALSE,
                     sub_ctx->hw_blend_state.rt[0].colormask & PIPE_MASK_B ? GL_TRUE : GL_FALSE,
                     sub_ctx->hw_blend_state.rt[0].colormask & PIPE_MASK_A ? GL_TRUE : GL_FALSE);
      }
   }

   /* Restore previous scissor state */
   if (sub_ctx->hw_rs_state.scissor)
      glEnable(GL_SCISSOR_TEST);
   else
      glDisable(GL_SCISSOR_TEST);
}

void vrend_clear(struct vrend_context *ctx, unsigned buffers,
                 const union pipe_color_union *color, double depth,
                 unsigned stencil) {
   GLbitfield bits = 0;
   struct vrend_sub_context *sub_ctx = ctx->sub;

   if (ctx->in_error)
      return;

   if (ctx->ctx_switch_pending)
      vrend_finish_context_switch(ctx);

   vrend_update_frontface_state(sub_ctx);
   if (sub_ctx->stencil_state_dirty)
      vrend_update_stencil_state(sub_ctx);
   if (sub_ctx->scissor_state_dirty)
      vrend_update_scissor_state(sub_ctx);
   if (sub_ctx->viewport_state_dirty)
      vrend_update_viewport_state(sub_ctx);

   vrend_use_program(NULL);

   glDisable(GL_SCISSOR_TEST);

   float colorf[4];
   memcpy(colorf, color->f, sizeof(colorf));

   vrend_clear_prepare(sub_ctx, sub_ctx->nr_cbufs ? sub_ctx->surf[0] : NULL,
                       buffers, colorf, depth, stencil);

   if (buffers & PIPE_CLEAR_COLOR) {
      uint32_t mask = 0;
      for (uint32_t i = 0; i < sub_ctx->nr_cbufs; i++) {
         if (sub_ctx->surf[i])
            mask |= (1 << i);
      }
      if (mask != (buffers >> 2)) {
         mask = buffers >> 2;
         while (mask) {
            int i = u_bit_scan(&mask);
            if (i < PIPE_MAX_COLOR_BUFS && sub_ctx->surf[i] &&
                util_format_is_pure_uint(sub_ctx->surf[i] &&
                                         sub_ctx->surf[i]->format))
                glClearBufferuiv(GL_COLOR, i, (GLuint *)colorf);
            else if (i < PIPE_MAX_COLOR_BUFS && sub_ctx->surf[i] &&
                     util_format_is_pure_sint(sub_ctx->surf[i] &&
                                              sub_ctx->surf[i]->format))
                glClearBufferiv(GL_COLOR, i, (GLint *)colorf);
            else
                glClearBufferfv(GL_COLOR, i, (GLfloat *)colorf);
         }
      } else
         bits |= GL_COLOR_BUFFER_BIT;
   }
   if (buffers & PIPE_CLEAR_DEPTH)
      bits |= GL_DEPTH_BUFFER_BIT;
   if (buffers & PIPE_CLEAR_STENCIL)
      bits |= GL_STENCIL_BUFFER_BIT;

   if (bits)
      glClear(bits);

   vrend_clear_finish(sub_ctx, buffers);
}

int vrend_clear_texture(struct vrend_context* ctx,
                         struct vrend_resource *res, uint32_t level,
                         const struct pipe_box *box,
                         const void * data)
{
   GLenum format, type;

   enum virgl_formats fmt = res->base.format;
   format = tex_conv_table[fmt].glformat;
   type = tex_conv_table[fmt].gltype;

   /* 32-bit BGRA resources are always reordered to RGBA ordering before
    * submission to the host driver. Reorder red/blue color bytes in
    * the clear color to match. */
   if (vrend_state.use_gles && vrend_format_is_bgra(fmt)) {
      assert(util_format_get_blocksizebits(fmt) >= 24);
      VREND_DEBUG(dbg_bgra, ctx, "swizzling clear_texture color for bgra texture\n");
      uint8_t temp = ((uint8_t*)data)[0];
      ((uint8_t*)data)[0] = ((uint8_t*)data)[2];
      ((uint8_t*)data)[2] = temp;
   }

   if (vrend_state.use_gles) {
      glClearTexSubImageEXT(res->gl_id, level,
                            box->x, box->y, box->z,
                            box->width, box->height, box->depth,
                            format, type, data);
   } else {
      glClearTexSubImage(res->gl_id, level,
                         box->x, box->y, box->z,
                         box->width, box->height, box->depth,
                         format, type, data);
   }
   return 0;
}

void vrend_clear_surface(struct vrend_context *ctx, uint32_t surf_handle,
                         unsigned buffers, const union pipe_color_union *color,
                         unsigned dstx, unsigned dsty, unsigned width,
                         unsigned height, bool render_condition_enabled) {
   struct vrend_surface *surf;
   GLbitfield bits = 0;
   struct vrend_sub_context *sub_ctx = ctx->sub;

   surf = vrend_object_lookup(sub_ctx->object_hash, surf_handle,
                              VIRGL_OBJECT_SURFACE);
   if (!surf) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_SURFACE,
                                 surf_handle);
      return;
   }

   if (!vrend_format_can_render(surf->format) &&
       !vrend_format_is_ds(surf->format)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_FORMAT,
                                 surf->format);
      return;
   }

   if (render_condition_enabled == false)
      vrend_pause_render_condition(ctx, true);

   glScissor(dstx, dsty, width, height);
   glEnable(GL_SCISSOR_TEST);
   ctx->sub->scissor_state_dirty = (1 << 0);

   // Do clear on blit framebuffer to avoid messing with main fb
   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);
   vrend_fb_bind_texture_id(
       surf->texture, surf->gl_id, 0, surf->level,
       surf->first_layer != surf->last_layer ? -1 : (GLint)surf->first_layer,
       surf->nr_samples);

   // When doing clear_render_target color->f contains clear color
   float colorf[4];
   memcpy(colorf, color->f, sizeof(colorf));

   // When doing clear_depth_stencil color encodes depth and stencil 
   double depth;
   memcpy(&depth, color->ui, sizeof(double));
   unsigned int stencil = color->ui[3];

   vrend_clear_prepare(sub_ctx, surf, buffers, colorf, depth, stencil);

   if (buffers & PIPE_CLEAR_COLOR0)
      bits |= GL_COLOR_BUFFER_BIT;
   if (buffers & PIPE_CLEAR_DEPTH)
      bits |= GL_DEPTH_BUFFER_BIT;
   if (buffers & PIPE_CLEAR_STENCIL)
      bits |= GL_STENCIL_BUFFER_BIT;

   glClear(bits);

   vrend_clear_finish(sub_ctx, buffers);

   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                          0, 0);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                          GL_TEXTURE_2D, 0, 0);
   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->fb_id);

   if (render_condition_enabled == false)
      vrend_pause_render_condition(ctx, false);
}

static void vrend_update_scissor_state(struct vrend_sub_context *sub_ctx)
{
   struct pipe_scissor_state *ss;
   GLint y;
   GLuint idx;
   unsigned mask = sub_ctx->scissor_state_dirty;

   while (mask) {
      idx = u_bit_scan(&mask);
      if (idx >= PIPE_MAX_VIEWPORTS) {
         vrend_report_buffer_error(sub_ctx->parent, 0);
         break;
      }
      ss = &sub_ctx->ss[idx];
      y = ss->miny;

      if (idx > 0 && has_feature(feat_viewport_array))
         glScissorIndexed(idx, ss->minx, y, ss->maxx - ss->minx, ss->maxy - ss->miny);
      else
         glScissor(ss->minx, y, ss->maxx - ss->minx, ss->maxy - ss->miny);
   }
   sub_ctx->scissor_state_dirty = 0;
}

static void vrend_update_viewport_state(struct vrend_sub_context *sub_ctx)
{
   GLint cy;
   unsigned mask = sub_ctx->viewport_state_dirty;
   int idx;
   while (mask) {
      idx = u_bit_scan(&mask);

      if (sub_ctx->viewport_is_negative)
         cy = sub_ctx->vps[idx].cur_y - sub_ctx->vps[idx].height;
      else
         cy = sub_ctx->vps[idx].cur_y;
      if (idx > 0 && has_feature(feat_viewport_array))
         glViewportIndexedf(idx, sub_ctx->vps[idx].cur_x, cy, sub_ctx->vps[idx].width, sub_ctx->vps[idx].height);
      else
         glViewport(sub_ctx->vps[idx].cur_x, cy, sub_ctx->vps[idx].width, sub_ctx->vps[idx].height);

      if (idx && has_feature(feat_viewport_array))
         if (vrend_state.use_gles) {
            glDepthRangeIndexedfOES(idx, sub_ctx->vps[idx].near_val, sub_ctx->vps[idx].far_val);
         } else
            glDepthRangeIndexed(idx, sub_ctx->vps[idx].near_val, sub_ctx->vps[idx].far_val);
      else
         if (vrend_state.use_gles)
            glDepthRangefOES(sub_ctx->vps[idx].near_val, sub_ctx->vps[idx].far_val);
         else
            glDepthRange(sub_ctx->vps[idx].near_val, sub_ctx->vps[idx].far_val);
   }

   sub_ctx->viewport_state_dirty = 0;
}

static GLenum get_gs_xfb_mode(GLenum mode)
{
   switch (mode) {
   case GL_POINTS:
      return GL_POINTS;
   case GL_LINE_STRIP:
      return GL_LINES;
   case GL_TRIANGLE_STRIP:
      return GL_TRIANGLES;
   default:
      virgl_warn("Illegal gs transform feedback mode %d\n", mode);
      return GL_POINTS;
   }
}

static GLenum get_tess_xfb_mode(int mode, bool is_point_mode)
{
   if (is_point_mode)
       return GL_POINTS;
   switch (mode) {
   case GL_QUADS:
   case GL_TRIANGLES:
      return GL_TRIANGLES;
   case GL_LINES:
      return GL_LINES;
   default:
      virgl_warn("Illegal gs transform feedback mode %d\n", mode);
      return GL_POINTS;
   }
}

static GLenum get_xfb_mode(GLenum mode)
{
   switch (mode) {
   case GL_POINTS:
      return GL_POINTS;
   case GL_TRIANGLES:
   case GL_TRIANGLE_STRIP:
   case GL_TRIANGLE_FAN:
   case GL_QUADS:
   case GL_QUAD_STRIP:
   case GL_POLYGON:
      return GL_TRIANGLES;
   case GL_LINES:
   case GL_LINE_LOOP:
   case GL_LINE_STRIP:
      return GL_LINES;
   default:
      virgl_warn("Failed to translate TFB %d\n", mode);
      return GL_POINTS;
   }
}

static void vrend_draw_bind_vertex_legacy(struct vrend_context *ctx,
                                          struct vrend_vertex_element_array *va)
{
   uint32_t enable_bitmask;
   uint32_t disable_bitmask;
   int i;

   enable_bitmask = 0;
   disable_bitmask = ~((1ull << va->count) - 1);
   for (i = 0; i < (int)va->count; i++) {
      struct vrend_vertex_element *ve = &va->elements[i];
      int vbo_index = ve->base.vertex_buffer_index;
      struct vrend_resource *res;
      GLint loc;

      if (i >= ctx->sub->prog->ss[PIPE_SHADER_VERTEX]->sel->sinfo.num_inputs) {
         /* XYZZY: debug this? */
         break;
      }
      res = (struct vrend_resource *)ctx->sub->vbo[vbo_index].base.buffer;

      if (!res) {
         virgl_warn("Cannot find vbo buf %d %d %d\n", i, va->count, ctx->sub->prog->ss[PIPE_SHADER_VERTEX]->sel->sinfo.num_inputs);
         continue;
      }

      if (vrend_state.use_explicit_locations || has_feature(feat_gles31_vertex_attrib_binding)) {
         loc = i;
      } else {
         if (ctx->sub->prog->attrib_locs) {
            loc = ctx->sub->prog->attrib_locs[i];
         } else loc = -1;

         if (loc == -1) {
            virgl_warn("%s: Cannot find loc %d %d %d\n", ctx->debug_name, i, va->count, ctx->sub->prog->ss[PIPE_SHADER_VERTEX]->sel->sinfo.num_inputs);
            if (i == 0) {
               virgl_warn("%s: Shader probably didn't compile - skipping rendering\n", ctx->debug_name);
               return;
            }
            continue;
         }
      }

      if (ve->type == GL_FALSE) {
         virgl_warn("Failed to translate vertex type - skipping render\n");
         return;
      }

      glBindBuffer(GL_ARRAY_BUFFER, res->gl_id);

      struct vrend_vertex_buffer *vbo = &ctx->sub->vbo[vbo_index];

      if (vbo->base.stride == 0) {
         void *data;
         /* for 0 stride we are kinda screwed */
         data = glMapBufferRange(GL_ARRAY_BUFFER, vbo->base.buffer_offset, ve->nr_chan * sizeof(GLfloat), GL_MAP_READ_BIT);

         switch (ve->nr_chan) {
         case 1:
            glVertexAttrib1fv(loc, data);
            break;
         case 2:
            glVertexAttrib2fv(loc, data);
            break;
         case 3:
            glVertexAttrib3fv(loc, data);
            break;
         case 4:
            glVertexAttrib4fv(loc, data);
            break;
         }
         glUnmapBuffer(GL_ARRAY_BUFFER);
         disable_bitmask |= (1 << loc);
      } else {
         GLint size = !vrend_state.use_gles && (va->zyxw_bitmask & (1 << i)) ? GL_BGRA : ve->nr_chan;

         enable_bitmask |= (1 << loc);
         if (util_format_is_pure_integer(ve->base.src_format)) {
            glVertexAttribIPointer(loc, size, ve->type, vbo->base.stride, (void *)(uintptr_t)(ve->base.src_offset + vbo->base.buffer_offset));
         } else {
            glVertexAttribPointer(loc, size, ve->type, ve->norm, vbo->base.stride, (void *)(uintptr_t)(ve->base.src_offset + vbo->base.buffer_offset));
         }
         glVertexAttribDivisorARB(loc, ve->base.instance_divisor);
      }
   }
   if (ctx->sub->enabled_attribs_bitmask != enable_bitmask) {
      uint32_t mask = ctx->sub->enabled_attribs_bitmask & disable_bitmask;

      while (mask) {
         i = u_bit_scan(&mask);
         glDisableVertexAttribArray(i);
      }
      ctx->sub->enabled_attribs_bitmask &= ~disable_bitmask;

      mask = ctx->sub->enabled_attribs_bitmask ^ enable_bitmask;
      while (mask) {
         i = u_bit_scan(&mask);
         glEnableVertexAttribArray(i);
      }

      ctx->sub->enabled_attribs_bitmask = enable_bitmask;
   }
}

static void vrend_draw_bind_vertex_binding(struct vrend_context *ctx,
                                           struct vrend_vertex_element_array *va)
{
   int i;

   glBindVertexArray(va->id);

   if (ctx->sub->vbo_dirty) {
      struct vrend_vertex_buffer *vbo = &ctx->sub->vbo[0];

      if (has_feature(feat_bind_vertex_buffers)) {
         GLsizei count = MAX2(ctx->sub->num_vbos, ctx->sub->old_num_vbos);

         GLuint buffers[PIPE_MAX_ATTRIBS];
         GLintptr offsets[PIPE_MAX_ATTRIBS];
         GLsizei strides[PIPE_MAX_ATTRIBS];

         for (i = 0; i < ctx->sub->num_vbos; i++) {
            struct vrend_resource *res = (struct vrend_resource *)vbo[i].base.buffer;
            if (res) {
               buffers[i] = res->gl_id;
               offsets[i] = vbo[i].base.buffer_offset;
               strides[i] = vbo[i].base.stride;
            } else {
               buffers[i] = 0;
               offsets[i] = 0;
               strides[i] = 0;
            }
         }

         for (i = ctx->sub->num_vbos; i < ctx->sub->old_num_vbos; i++) {
            buffers[i] = 0;
            offsets[i] = 0;
            strides[i] = 0;
         }

         glBindVertexBuffers(0, count, buffers, offsets, strides);
      } else {
         for (i = 0; i < ctx->sub->num_vbos; i++) {
            struct vrend_resource *res = (struct vrend_resource *)vbo[i].base.buffer;
            if (res)
               glBindVertexBuffer(i, res->gl_id, vbo[i].base.buffer_offset, vbo[i].base.stride);
            else
               glBindVertexBuffer(i, 0, 0, 0);
         }
         for (i = ctx->sub->num_vbos; i < ctx->sub->old_num_vbos; i++)
            glBindVertexBuffer(i, 0, 0, 0);
      }

      ctx->sub->vbo_dirty = false;
   }
}

static GLuint vrend_draw_bind_samplers_shader(struct vrend_sub_context *sub_ctx,
                                              int shader_type,
                                              GLuint next_sampler_id)
{
   struct vrend_linked_shader_program *sprog = sub_ctx->prog;
   struct vrend_shader_view *shader_view = &sub_ctx->views[shader_type];

   int sampler_index = 0;
   uint32_t dirty = shader_view->dirty_mask;
   uint32_t mask = sprog->samplers_used_mask[shader_type];

   while (mask) {
      int i = u_bit_scan(&mask);
      struct vrend_sampler_view *tview = shader_view->views[i];

      if ((dirty & (1 << i)) && tview) {
         glActiveTexture(GL_TEXTURE0 + next_sampler_id);
         glUniform1i(sprog->sampler_locs[shader_type][sampler_index], next_sampler_id);

         if (sprog->shadow_samp_mask[shader_type] & (1 << i)) {
            struct vrend_texture *tex = (struct vrend_texture *)tview->texture;

            /* The modes LUMINANCE, INTENSITY, and ALPHA only apply when a depth texture
             * is used by a sampler that returns an RGBA value, i.e. by sampler*D, if
             * the texture is queries by using sampler*Shadow then these swizzles must
             * not be applied, therefore, reset the swizzled to the default */
            static const GLint swizzle[] = {GL_RED,GL_GREEN,GL_BLUE,GL_ALPHA};
            if (memcmp(tex->cur_swizzle, swizzle, 4 * sizeof(GLint))) {
               if (vrend_state.use_gles) {
                  for (unsigned int i = 0; i < 4; ++i) {
                     glTexParameteri(tview->texture->target, GL_TEXTURE_SWIZZLE_R + i, swizzle[i]);
                  }
               } else {
                  glTexParameteriv(tview->texture->target, GL_TEXTURE_SWIZZLE_RGBA, swizzle);
               }
               memcpy(tex->cur_swizzle, swizzle, 4 * sizeof(GLint));
            }

            glUniform4f(sprog->shadow_samp_mask_locs[shader_type][sampler_index],
                        (tview->gl_swizzle[0] == GL_ZERO || tview->gl_swizzle[0] == GL_ONE) ? 0.0 : 1.0,
                        (tview->gl_swizzle[1] == GL_ZERO || tview->gl_swizzle[1] == GL_ONE) ? 0.0 : 1.0,
                        (tview->gl_swizzle[2] == GL_ZERO || tview->gl_swizzle[2] == GL_ONE) ? 0.0 : 1.0,
                        (tview->gl_swizzle[3] == GL_ZERO || tview->gl_swizzle[3] == GL_ONE) ? 0.0 : 1.0);
            glUniform4f(sprog->shadow_samp_add_locs[shader_type][sampler_index],
                        tview->gl_swizzle[0] == GL_ONE ? 1.0 : 0.0,
                        tview->gl_swizzle[1] == GL_ONE ? 1.0 : 0.0,
                        tview->gl_swizzle[2] == GL_ONE ? 1.0 : 0.0,
                        tview->gl_swizzle[3] == GL_ONE ? 1.0 : 0.0);
         }

         if (tview->texture) {
            GLuint id = tview->gl_id;
            GLenum target = tview->target;

            debug_texture(__func__, tview->texture);

            if (has_bit(tview->texture->storage_bits, VREND_STORAGE_GL_BUFFER)) {
               id = tview->texture->tbo_tex_id;
               target = GL_TEXTURE_BUFFER;
            }

            glBindTexture(target, id);
            vrend_apply_sampler_state(sub_ctx, tview->texture,
                                      shader_view->samplers[i],
                                      next_sampler_id, tview);

            if (vrend_state.use_gles) {
               const unsigned levels = tview->levels ? tview->levels : tview->texture->base.last_level + 1u;
               shader_view->texture_levels[sampler_index] = levels;
            }
         }
      }
      sampler_index++;
      next_sampler_id++;
   }

   shader_view->num_used_views = sampler_index;
   shader_view->dirty_mask = 0;

   // Since we use a dirty mask to elide some unnecessary state update API
   // calls, we must ensure that a later glBindTexture() used for another reason
   // (such as texture allocation) doesn't affect our fragile sampler bindings.
   glActiveTexture(GL_TEXTURE0 + vrend_state.max_texture_units - 1);

   return next_sampler_id;
}

static GLuint vrend_draw_bind_ubo_shader(struct vrend_sub_context *sub_ctx,
                                         int shader_type, GLuint next_ubo_id)
{
   uint32_t mask, dirty, update;
   struct pipe_constant_buffer *cb;
   struct vrend_resource *res;

   mask = sub_ctx->prog->ubo_used_mask[shader_type];
   dirty = sub_ctx->const_bufs_dirty[shader_type];
   update = dirty & sub_ctx->const_bufs_used_mask[shader_type];

   if (!update)
      return next_ubo_id + util_bitcount(mask);

   while (mask) {
      /* The const_bufs_used_mask stores the gallium uniform buffer indices */
      int i = u_bit_scan(&mask);

      if (update & (1 << i)) {
         /* The cbs array is indexed using the gallium uniform buffer index */
         cb = &sub_ctx->cbs[shader_type][i];
         res = (struct vrend_resource *)cb->buffer;

         glBindBufferRange(GL_UNIFORM_BUFFER, next_ubo_id, res->gl_id,
                           cb->buffer_offset, cb->buffer_size);
         dirty &= ~(1 << i);
      }
      next_ubo_id++;
   }
   sub_ctx->const_bufs_dirty[shader_type] = dirty;

   return next_ubo_id;
}

static void vrend_draw_bind_const_shader(struct vrend_sub_context *sub_ctx,
                                         int shader_type, bool new_program)
{
   if (sub_ctx->consts[shader_type].consts &&
       sub_ctx->shaders[shader_type] &&
       (sub_ctx->prog->const_location[shader_type] != -1) &&
       (sub_ctx->const_dirty[shader_type] || new_program)) {
      glUniform4uiv(sub_ctx->prog->const_location[shader_type],
            sub_ctx->shaders[shader_type]->sinfo.num_consts,
            sub_ctx->consts[shader_type].consts);
      sub_ctx->const_dirty[shader_type] = false;
   }
}

static void vrend_draw_bind_ssbo_shader(struct vrend_sub_context *sub_ctx,
                                        int shader_type)
{
   uint32_t mask;
   struct vrend_ssbo *ssbo;
   struct vrend_resource *res;

   if (!has_feature(feat_ssbo))
      return;

   if (!sub_ctx->prog->ssbo_used_mask[shader_type])
      return;

   if (!sub_ctx->ssbo_used_mask[shader_type])
      return;

   uint32_t offset = sub_ctx->shaders[shader_type]->sinfo.ssbo_binding_offset;
   mask = sub_ctx->ssbo_used_mask[shader_type] &
         sub_ctx->prog->ssbo_used_mask[shader_type];

   while (mask) {
      int i = u_bit_scan(&mask);

      ssbo = &sub_ctx->ssbo[shader_type][i];
      res = (struct vrend_resource *)ssbo->res;
      glBindBufferRange(GL_SHADER_STORAGE_BUFFER, i + offset, res->gl_id,
                        ssbo->buffer_offset, ssbo->buffer_size);
   }
}

static void vrend_draw_bind_abo_shader(struct vrend_sub_context *sub_ctx)
{
   uint32_t mask;
   struct vrend_abo *abo;
   struct vrend_resource *res;
   int i;

   if (!has_feature(feat_atomic_counters))
      return;

   mask = sub_ctx->abo_used_mask;
   while (mask) {
      i = u_bit_scan(&mask);

      abo = &sub_ctx->abo[i];
      res = (struct vrend_resource *)abo->res;
      glBindBufferRange(GL_ATOMIC_COUNTER_BUFFER, i, res->gl_id,
                        abo->buffer_offset, abo->buffer_size);
   }
}

static void vrend_draw_bind_images_shader(struct vrend_sub_context *sub_ctx, int shader_type)
{
   GLenum access;
   GLboolean layered;
   struct vrend_image_view *iview;
   uint32_t mask, tex_id, level, first_layer;


   if (!sub_ctx->images_used_mask[shader_type])
      return;

   if (!sub_ctx->prog->img_locs[shader_type])
      return;

   if (!has_feature(feat_images))
      return;

   mask = sub_ctx->images_used_mask[shader_type] & sub_ctx->prog->images_used_mask[shader_type];

   while (mask) {
      unsigned i = u_bit_scan(&mask);
      int image_unit = i + sub_ctx->prog->ss[shader_type]->sel->sinfo.image_binding_offset;
      int binding = sub_ctx->prog->img_locs[shader_type][i];

      if (binding == -1)
          continue;
      iview = &sub_ctx->image_views[shader_type][i];
      tex_id = iview->texture->gl_id;
      if (has_bit(iview->texture->storage_bits, VREND_STORAGE_GL_BUFFER)) {
         if (!iview->texture->tbo_tex_id)
            glGenTextures(1, &iview->texture->tbo_tex_id);

         /* We don't really care about the format we use for binding the buffer to a texture
          * we only care about the per block bitsize, the real format is then passed to the
          * call to glBindImageTexture, and  there it is only important whether the formats
          * are compatible (See OpenGL 4.6 Core Profile, May 14, 2018 , Sect 8.26. p 293) */

         GLenum format;
         switch (util_format_get_blocksizebits(iview->vformat)) {
         case 128:
            format = GL_RGBA32UI;
            break;
         case 64:
            format = GL_RG32UI;
            break;
         case 32:
            format = GL_R32UI;
            break;
         case 16:
            format = GL_R16UI;
            break;
         case 8:
            format = GL_R8UI;
            break;
         default:
            /* This should not be possible, warn and set a default format. */
            virgl_warn("%s: Unsupported format block bit size %d\n", __func__,
                       util_format_get_blocksizebits(iview->vformat));
            format = GL_R8UI;
         }

         glBindBufferARB(GL_TEXTURE_BUFFER, iview->texture->gl_id);
         glBindTexture(GL_TEXTURE_BUFFER, iview->texture->tbo_tex_id);

         if (has_feature(feat_arb_or_gles_ext_texture_buffer)) {
            if (has_feature(feat_texture_buffer_range)) {
               /* Offset and size are given in byte, but the max_texture_buffer_size
                * is given as texels, so we have to take the blocksize into account.
                * To avoid an unsigned int overflow, we divide by blocksize,
                */
               int blsize = util_format_get_blocksize(iview->vformat);
               unsigned offset = iview->u.buf.offset / blsize;
               unsigned size = iview->u.buf.size / blsize;
               if (offset + size > vrend_state.max_texture_buffer_size)
                  size = vrend_state.max_texture_buffer_size - offset;
               glTexBufferRange(GL_TEXTURE_BUFFER, format, iview->texture->gl_id, iview->u.buf.offset,
                                size * blsize);
            } else {
               glTexBuffer(GL_TEXTURE_BUFFER, format, iview->texture->gl_id);
            }
         }

         tex_id = iview->texture->tbo_tex_id;
         level = first_layer = 0;
         layered = GL_TRUE;
      } else {
         level = iview->u.tex.level;
         first_layer = iview->u.tex.first_layer;
         layered = !((iview->texture->base.array_size > 1 ||
                      iview->texture->base.depth0 > 1) &&
                     (first_layer == iview->u.tex.last_layer));

         /* Do we need a texture view? */
         uint32_t num_layers = iview->u.tex.last_layer - first_layer + 1;

         if (layered &&
             (iview->u.tex.first_layer != 0 ||
              num_layers != MAX2(iview->texture->base.array_size,  iview->texture->base.depth0))) {

            if (iview->view_id)
               glDeleteTextures(1, &iview->view_id);

            glGenTextures(1, &iview->view_id);
            glTextureView(iview->view_id, iview->texture->target, iview->texture->gl_id,
                          tex_conv_table[iview->texture->base.format].internalformat, level, 1,
                          first_layer, num_layers);
            tex_id = iview->view_id;
         }
      }

      if (!vrend_state.use_gles)
         glUniform1i(image_unit, binding);

      switch (iview->access) {
      case PIPE_IMAGE_ACCESS_READ:
         access = GL_READ_ONLY;
         break;
      case PIPE_IMAGE_ACCESS_WRITE:
         access = GL_WRITE_ONLY;
         break;
      case PIPE_IMAGE_ACCESS_READ_WRITE:
         access = GL_READ_WRITE;
         break;
      default:
         virgl_warn("Invalid access specified\n");
         return;
      }

      glBindImageTexture(vrend_state.use_gles ? image_unit : binding,
                         tex_id, level, layered, first_layer, access, iview->format);
   }
}

static void
vrend_fill_sysval_uniform_block (struct vrend_sub_context *sub_ctx)
{
   if (sub_ctx->prog->virgl_block_bind == GL_INVALID_INDEX)
      return;

   if (sub_ctx->sysvalue_data_cookie != sub_ctx->prog->sysvalue_data_cookie) {
      glBindBuffer(GL_UNIFORM_BUFFER, sub_ctx->prog->ubo_sysval_buffer_id);
      glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(struct sysval_uniform_block),
                      &sub_ctx->sysvalue_data);
      glBindBuffer(GL_UNIFORM_BUFFER, 0);
      sub_ctx->prog->sysvalue_data_cookie = sub_ctx->sysvalue_data_cookie;
   }
}

static void vrend_draw_bind_objects(struct vrend_sub_context *sub_ctx, bool new_program)
{
   GLuint next_ubo_id = 0, next_sampler_id = 0;
   for (int shader_type = PIPE_SHADER_VERTEX; shader_type <= sub_ctx->last_shader_idx; shader_type++) {
      vrend_set_active_pipeline_stage(sub_ctx->prog, shader_type);

      next_ubo_id = vrend_draw_bind_ubo_shader(sub_ctx, shader_type, next_ubo_id);
      vrend_draw_bind_const_shader(sub_ctx, shader_type, new_program);
      next_sampler_id = vrend_draw_bind_samplers_shader(sub_ctx, shader_type, next_sampler_id);

      vrend_draw_bind_images_shader(sub_ctx, shader_type);
      vrend_draw_bind_ssbo_shader(sub_ctx, shader_type);

      if (vrend_state.use_gles) {
         if (sub_ctx->prog->tex_levels_uniform_id[shader_type] != -1) {
            vrend_set_active_pipeline_stage(sub_ctx->prog, shader_type);
            glUniform1iv(sub_ctx->prog->tex_levels_uniform_id[shader_type],
                         sub_ctx->views[shader_type].num_used_views,
                         sub_ctx->views[shader_type].texture_levels);
         }
      }
   }

   if (sub_ctx->prog->virgl_block_bind != GL_INVALID_INDEX)
      glBindBufferRange(GL_UNIFORM_BUFFER, sub_ctx->prog->virgl_block_bind,
                        sub_ctx->prog->ubo_sysval_buffer_id,
                        0, sizeof(struct sysval_uniform_block));

   vrend_draw_bind_abo_shader(sub_ctx);

   vrend_set_active_pipeline_stage(sub_ctx->prog, PIPE_SHADER_FRAGMENT);
}

static bool
vrend_inject_tcs(struct vrend_sub_context *sub_ctx, uint8_t vertices_per_patch)
{
   struct pipe_stream_output_info so_info;

   memset(&so_info, 0, sizeof(so_info));
   struct vrend_shader_selector *sel = vrend_create_shader_state(&so_info,
                                                                 false, PIPE_SHADER_TESS_CTRL);
   if (!sel)
      return false;

   struct vrend_shader *shader = CALLOC_STRUCT(vrend_shader);
   if (!shader) {
      vrend_destroy_shader_selector(sel);
      return false;
   }

   vrend_fill_shader_key(sub_ctx, sel, &shader->key);

   shader->sel = sel;
   list_inithead(&shader->programs);
   strarray_alloc(&shader->glsl_strings, SHADER_MAX_STRINGS);

   if (!vrend_shader_create_passthrough_tcs(sub_ctx->parent, &sub_ctx->parent->shader_cfg,
                                            sub_ctx->shaders[PIPE_SHADER_VERTEX]->tokens,
                                            &shader->key, vrend_state.tess_factors, &sel->sinfo,
                                            &shader->glsl_strings, vertices_per_patch)) {
      strarray_free(&shader->glsl_strings, true);
      FREE(shader);
      vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_SHADER, sel->type);
      vrend_destroy_shader_selector(sel);
      return false;
   }
   // Need to add inject the selected shader to the shader selector and then the code below
   // can continue
   sel->tokens = NULL;
   sel->current = shader;
   sub_ctx->shaders[PIPE_SHADER_TESS_CTRL] = sel;

   vrend_compile_shader(sub_ctx, shader);
   return true;
}

enum select_program_result {
    PROGRAMM_ERROR,
    PROGRAMM_NO_CHANGE,
    PROGRAMM_NEW
};

static enum select_program_result
vrend_select_program(struct vrend_sub_context *sub_ctx, uint8_t vertices_per_patch)
{
   struct vrend_linked_shader_program *prog;
   bool fs_dirty, vs_dirty, gs_dirty, tcs_dirty, tes_dirty;
   bool dual_src = util_blend_state_is_dual(&sub_ctx->blend_state, 0);


   struct vrend_shader_selector **shaders = sub_ctx->shaders;

   if (!shaders[PIPE_SHADER_VERTEX] || !shaders[PIPE_SHADER_FRAGMENT]) {
      return PROGRAMM_ERROR;
   }

   // For some GPU, we'd like to use integer variable in generated GLSL if
   // the input buffers are integer formats. But we actually don't know the
   // buffer formats when the shader is created, we only know it here.
   // Set it to true so the underlying code knows to use the buffer formats
   // now.

   sub_ctx->drawing = true;
   vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_VERTEX], &vs_dirty);
   sub_ctx->drawing = false;

   if (shaders[PIPE_SHADER_TESS_CTRL] && shaders[PIPE_SHADER_TESS_CTRL]->tokens)
      vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_TESS_CTRL], &tcs_dirty);
   else if (vrend_state.use_gles && shaders[PIPE_SHADER_TESS_EVAL]) {
      VREND_DEBUG(dbg_shader, sub_ctx->parent, "Need to inject a TCS\n");
      if (!vrend_inject_tcs(sub_ctx, vertices_per_patch))
         goto fail;

      vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_VERTEX], &vs_dirty);
   }

   if (shaders[PIPE_SHADER_TESS_EVAL])
      vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_TESS_EVAL], &tes_dirty);
   if (shaders[PIPE_SHADER_GEOMETRY])
      vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_GEOMETRY], &gs_dirty);

   if (vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_FRAGMENT], &fs_dirty))
      goto fail;

   // NOTE: run shader selection again as a workaround to #180 - "duplicated shader compilation"
   if (shaders[PIPE_SHADER_GEOMETRY])
      vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_GEOMETRY], &gs_dirty);
   if (shaders[PIPE_SHADER_TESS_EVAL])
      vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_TESS_EVAL], &tes_dirty);
   if (shaders[PIPE_SHADER_TESS_CTRL] && shaders[PIPE_SHADER_TESS_CTRL]->tokens)
      vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_TESS_CTRL], &tcs_dirty);
   else if (vrend_state.use_gles && shaders[PIPE_SHADER_TESS_EVAL]) {
      VREND_DEBUG(dbg_shader, sub_ctx->parent, "Need to inject a TCS\n");
      if (!vrend_inject_tcs(sub_ctx, vertices_per_patch))
         goto fail;
   }
   sub_ctx->drawing = true;
   vrend_shader_select(sub_ctx, shaders[PIPE_SHADER_VERTEX], &vs_dirty);
   sub_ctx->drawing = false;

   uint8_t gles_emulate_query_texture_levels_mask = 0;

   for (enum pipe_shader_type i = 0; i < PIPE_SHADER_TYPES; i++) {
      struct vrend_shader_selector *sel = shaders[i];
      if (!sel)
         continue;

      struct vrend_shader *shader = sel->current;
      if (shader && !shader->is_compiled) {
         if (!vrend_compile_shader(sub_ctx, shader))
            return PROGRAMM_ERROR;
      }
      if (vrend_state.use_gles && sel->sinfo.gles_use_tex_query_level)
         gles_emulate_query_texture_levels_mask |= 1 << i;
   }

   if (!shaders[PIPE_SHADER_VERTEX]->current ||
       !shaders[PIPE_SHADER_FRAGMENT]->current ||
       (shaders[PIPE_SHADER_GEOMETRY] && !shaders[PIPE_SHADER_GEOMETRY]->current) ||
       (shaders[PIPE_SHADER_TESS_CTRL] && !shaders[PIPE_SHADER_TESS_CTRL]->current) ||
       (shaders[PIPE_SHADER_TESS_EVAL] && !shaders[PIPE_SHADER_TESS_EVAL]->current))
      goto fail;

   struct vrend_shader *vs = shaders[PIPE_SHADER_VERTEX]->current;
   struct vrend_shader *fs = shaders[PIPE_SHADER_FRAGMENT]->current;
   struct vrend_shader *gs = shaders[PIPE_SHADER_GEOMETRY] ? shaders[PIPE_SHADER_GEOMETRY]->current : NULL;
   struct vrend_shader *tcs = shaders[PIPE_SHADER_TESS_CTRL] ? shaders[PIPE_SHADER_TESS_CTRL]->current : NULL;
   struct vrend_shader *tes = shaders[PIPE_SHADER_TESS_EVAL] ? shaders[PIPE_SHADER_TESS_EVAL]->current : NULL;

   GLuint vs_id = vs->id;
   GLuint fs_id = fs->id;
   GLuint gs_id = !gs ? 0 : gs->id;
   GLuint tcs_id = !tcs ? 0 : tcs->id;
   GLuint tes_id = !tes ? 0 : tes->id;

   if (shaders[PIPE_SHADER_FRAGMENT]->current->sel->sinfo.num_outputs <= 1)
      dual_src = false;

   bool same_prog = sub_ctx->prog &&
                    vs_id == sub_ctx->prog_ids[PIPE_SHADER_VERTEX] &&
                    fs_id == sub_ctx->prog_ids[PIPE_SHADER_FRAGMENT] &&
                    gs_id == sub_ctx->prog_ids[PIPE_SHADER_GEOMETRY] &&
                    tcs_id == sub_ctx->prog_ids[PIPE_SHADER_TESS_CTRL] &&
                    tes_id == sub_ctx->prog_ids[PIPE_SHADER_TESS_EVAL] &&
                    sub_ctx->prog->dual_src_linked == dual_src;

   bool separable = vs->sel->sinfo.separable_program &&
                    fs->sel->sinfo.separable_program &&
                    (!gs || gs->sel->sinfo.separable_program) &&
                    (!tcs || tcs->sel->sinfo.separable_program) &&
                    (!tes || tes->sel->sinfo.separable_program);

   if (!same_prog) {
      prog = lookup_shader_program(sub_ctx, vs_id, fs_id, gs_id, tcs_id, tes_id, dual_src);
      if (!prog) {
         prog = add_shader_program(sub_ctx,
                                   sub_ctx->shaders[PIPE_SHADER_VERTEX]->current,
                                   sub_ctx->shaders[PIPE_SHADER_FRAGMENT]->current,
                                   gs_id ? sub_ctx->shaders[PIPE_SHADER_GEOMETRY]->current : NULL,
                                   tcs_id ? sub_ctx->shaders[PIPE_SHADER_TESS_CTRL]->current : NULL,
                                   tes_id ? sub_ctx->shaders[PIPE_SHADER_TESS_EVAL]->current : NULL,
                                   separable);
         if (!prog)
            return PROGRAMM_ERROR;
         prog->gles_use_query_texturelevel_mask = gles_emulate_query_texture_levels_mask;
      } else if (separable) {
          /* UBO block bindings are reset to zero if the programs are
           * re-linked.  With separable shaders, the program can be relinked
           * because it's shared across multiple pipelines and some things like
           * transform feedback require relinking, so we have to make sure the
           * blocks are bound. */
          enum pipe_shader_type last_shader = tes_id ? PIPE_SHADER_TESS_EVAL :
                (gs_id ? PIPE_SHADER_GEOMETRY :
                         PIPE_SHADER_FRAGMENT);
          bool need_rebind = false;

          for (enum pipe_shader_type shader_type = PIPE_SHADER_VERTEX;
               shader_type <= last_shader && !need_rebind;
               shader_type++) {
             if (!prog->ss[shader_type])
                continue;
             need_rebind |= prog->ss[shader_type]->last_pipeline_id != prog->id.pipeline;
          }

          if (need_rebind) {
             vrend_use_program(prog);
             rebind_ubo_and_sampler_locs(prog, last_shader);
          }
      }

      sub_ctx->last_shader_idx = sub_ctx->shaders[PIPE_SHADER_TESS_EVAL] ? PIPE_SHADER_TESS_EVAL : (sub_ctx->shaders[PIPE_SHADER_GEOMETRY] ? PIPE_SHADER_GEOMETRY : PIPE_SHADER_FRAGMENT);
   } else
      prog = sub_ctx->prog;

   enum select_program_result new_program = PROGRAMM_NO_CHANGE;
   if (sub_ctx->prog != prog) {
      new_program = PROGRAMM_NEW;
      sub_ctx->prog_ids[PIPE_SHADER_VERTEX] = vs_id;
      sub_ctx->prog_ids[PIPE_SHADER_FRAGMENT] = fs_id;
      sub_ctx->prog_ids[PIPE_SHADER_GEOMETRY] = gs_id;
      sub_ctx->prog_ids[PIPE_SHADER_TESS_CTRL] = tcs_id;
      sub_ctx->prog_ids[PIPE_SHADER_TESS_EVAL] = tes_id;
      sub_ctx->prog_ids[PIPE_SHADER_COMPUTE] = 0;
      sub_ctx->prog = prog;

      /* mark all constbufs and sampler views as dirty */
      for (int stage = PIPE_SHADER_VERTEX; stage <= PIPE_SHADER_FRAGMENT; stage++) {
         sub_ctx->const_bufs_dirty[stage] = ~0;
         sub_ctx->views[stage].dirty_mask = ~0;
      }

      prog->ref_context = sub_ctx;
   }
   sub_ctx->cs_shader_dirty = true;
   sub_ctx->shader_dirty = false;

   return new_program;

fail:
   virgl_error("Failure to compile shader variants: %s\n", sub_ctx->parent->debug_name);
   return PROGRAMM_ERROR;
}

void vrend_link_program_hook(struct vrend_context *ctx, uint32_t *handles)
{
   /* Pre-compiling compute shaders needs some additional work */
   if (handles[PIPE_SHADER_COMPUTE])
      return;

   struct vrend_shader_selector *vs = vrend_object_lookup(ctx->sub->object_hash,
                                                          handles[PIPE_SHADER_VERTEX],
                                                          VIRGL_OBJECT_SHADER);
   struct vrend_shader_selector *fs = vrend_object_lookup(ctx->sub->object_hash,
                                                          handles[PIPE_SHADER_FRAGMENT],
                                                          VIRGL_OBJECT_SHADER);

   /* If we can't force linking, exit early */
   if ((!handles[PIPE_SHADER_VERTEX] || !handles[PIPE_SHADER_FRAGMENT]) &&
       (!vs || !vs->sinfo.separable_program) && (!fs || !fs->sinfo.separable_program))
       return;

   /* We can't link a pre-link a TCS without a TES, exit early */
   if (handles[PIPE_SHADER_TESS_CTRL] && !handles[PIPE_SHADER_TESS_EVAL])
       return;

   struct vrend_shader_selector *prev_handles[PIPE_SHADER_TYPES];
   memset(prev_handles, 0, sizeof(prev_handles));
   uint32_t prev_shader_ids[PIPE_SHADER_TYPES];
   memcpy(prev_shader_ids, ctx->sub->prog_ids, PIPE_SHADER_TYPES * sizeof(uint32_t));
   struct vrend_linked_shader_program *prev_prog = ctx->sub->prog;

   for (enum pipe_shader_type type = 0; type < PIPE_SHADER_TYPES; ++type) {
      vrend_shader_state_reference(&prev_handles[type], ctx->sub->shaders[type]);
      vrend_bind_shader(ctx, handles[type], type);
   }

   /* Force early-linking for separable shaders, since they don't depend on other stages */
   for (uint32_t type = 0; type < PIPE_SHADER_TYPES; ++type) {
       if (ctx->sub->shaders[type] && ctx->sub->shaders[type]->sinfo.separable_program) {
           if (!ctx->sub->shaders[type]->current->is_compiled)
               vrend_compile_shader(ctx->sub, ctx->sub->shaders[type]->current);
           if (!ctx->sub->shaders[type]->current->is_linked)
               vrend_link_separable_shader(ctx->sub, ctx->sub->shaders[type]->current, type);
       }
   }

   /* Force early-link of the whole shader program. */
   vrend_select_program(ctx->sub, 1);

   ctx->sub->shader_dirty = true;
   ctx->sub->cs_shader_dirty = true;

   /* undo state changes */
   for (enum pipe_shader_type type = 0; type < PIPE_SHADER_TYPES; ++type) {
      vrend_shader_state_reference(&ctx->sub->shaders[type], prev_handles[type]);
      vrend_shader_state_reference(&prev_handles[type], NULL);
   }
   memcpy(ctx->sub->prog_ids, prev_shader_ids, PIPE_SHADER_TYPES * sizeof(uint32_t));
   ctx->sub->prog = prev_prog;
}

int vrend_draw_vbo(struct vrend_context *ctx,
                   const struct pipe_draw_info *info,
                   uint32_t cso, uint32_t indirect_handle,
                   uint32_t indirect_draw_count_handle)
{
   enum select_program_result program_select_result = PROGRAMM_NO_CHANGE;
   struct vrend_resource *indirect_res = NULL;
   struct vrend_resource *indirect_params_res = NULL;
   struct vrend_sub_context *sub_ctx = ctx->sub;

   if (ctx->in_error)
      return ENOTRECOVERABLE;

   if (info->instance_count && !has_feature(feat_draw_instance))
      return EINVAL;

   if (info->start_instance && !has_feature(feat_base_instance))
      return EINVAL;

   if (info->indirect.draw_count > 1 && !has_feature(feat_multi_draw_indirect))
      return EINVAL;

   if (indirect_handle) {
      if (!has_feature(feat_indirect_draw))
         return EINVAL;
      indirect_res = vrend_renderer_ctx_res_lookup(ctx, indirect_handle);
      if (!indirect_res || !indirect_res->gl_id) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, indirect_handle);
         return EINVAL;
      }
   }

   /* this must be zero until we support the feature */
   if (indirect_draw_count_handle) {
      if (!has_feature(feat_indirect_params))
         return EINVAL;

      indirect_params_res = vrend_renderer_ctx_res_lookup(ctx, indirect_draw_count_handle);
      if (!indirect_params_res || !indirect_params_res->gl_id){
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, indirect_draw_count_handle);
         return EINVAL;
      }
   }

   if (ctx->ctx_switch_pending)
      vrend_finish_context_switch(ctx);

   vrend_update_frontface_state(sub_ctx);
   if (ctx->sub->stencil_state_dirty)
      vrend_update_stencil_state(sub_ctx);
   if (ctx->sub->scissor_state_dirty)
      vrend_update_scissor_state(sub_ctx);

   if (ctx->sub->viewport_state_dirty)
      vrend_update_viewport_state(sub_ctx);

   if (ctx->sub->blend_state_dirty)
      vrend_patch_blend_state(sub_ctx);

   // enable primitive-mode-dependent shader variants
   if (sub_ctx->prim_mode != (int)info->mode) {
      // Only refresh shader program when switching in/out of GL_POINTS primitive mode
      if (sub_ctx->prim_mode == PIPE_PRIM_POINTS
          || (int)info->mode == PIPE_PRIM_POINTS)
         sub_ctx->shader_dirty = true;

      sub_ctx->prim_mode = (int)info->mode;
   }

   if (sub_ctx->shader_dirty || sub_ctx->swizzle_output_rgb_to_bgr ||
       sub_ctx->needs_manual_srgb_encode_bitmask || sub_ctx->vbo_dirty)
      program_select_result = vrend_select_program(sub_ctx, info->vertices_per_patch);

   if (!sub_ctx->prog || program_select_result == PROGRAMM_ERROR) {
      virgl_error("Dropping rendering due to missing shaders: %s\n", ctx->debug_name);
      return 0;
   }

   vrend_use_program(sub_ctx->prog);

   if (has_feature(feat_draw_parameters) &&
       sub_ctx->prog->reads_drawid &&
       sub_ctx->sysvalue_data.drawid_base != (int)info->drawid) {
         sub_ctx->sysvalue_data.drawid_base = info->drawid;
         sub_ctx->sysvalue_data_cookie++;
   }

   if (vrend_state.use_gles) {
      /* PIPE_SHADER and TGSI_SHADER have different ordering, so use two
       * different prefix arrays */
      for (enum pipe_shader_type i = PIPE_SHADER_VERTEX; i < PIPE_SHADER_COMPUTE; ++i) {
         if (sub_ctx->prog->gles_use_query_texturelevel_mask & (1 << i)) {
            char loc_name[32];
            snprintf(loc_name, 32, "%s_texlod", pipe_shader_to_prefix(i));
            sub_ctx->prog->tex_levels_uniform_id[i] =
               vrend_get_uniform_location(sub_ctx->prog, loc_name, i);
         } else {
            sub_ctx->prog->tex_levels_uniform_id[i] = -1;
         }

      }
   }

   vrend_draw_bind_objects(sub_ctx, program_select_result == PROGRAMM_NEW);
   vrend_fill_sysval_uniform_block(sub_ctx);

   if (has_feature(feat_gles31_vertex_attrib_binding)) {
      if (sub_ctx->ve) {
         vrend_draw_bind_vertex_binding(ctx, sub_ctx->ve);
      } else {
         glBindVertexArray(sub_ctx->vaoid);
      }
   } else {
      if (sub_ctx->ve) {
         vrend_draw_bind_vertex_legacy(ctx, sub_ctx->ve);
      } else {
         struct vrend_vertex_element_array va;
         va.count = 0;
         vrend_draw_bind_vertex_legacy(ctx, &va);
      }
   }

   if (info->indexed) {
      struct vrend_resource *res = (struct vrend_resource *)sub_ctx->ib.buffer;
      if (!res) {
         virgl_error("VBO missing indexed array buffer\n");
         return 0;
      }

      if (!indirect_handle) {
         uint32_t expected_size = sub_ctx->ib.index_size * info->count + sub_ctx->ib.offset;
         if (expected_size > res->base.width0) {
            virgl_error("Indexed array buffer (%u) not large enough for draw operation "
                        "(req. %u\n", res->base.width0, expected_size);
            return 0;
         }
      }

      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, res->gl_id);
   } else
      glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

   if (sub_ctx->current_so) {
      if (sub_ctx->current_so->xfb_state == XFB_STATE_STARTED_NEED_BEGIN) {
         if (sub_ctx->shaders[PIPE_SHADER_GEOMETRY])
            glBeginTransformFeedback(get_gs_xfb_mode(sub_ctx->shaders[PIPE_SHADER_GEOMETRY]->sinfo.gs_out_prim));
     else if (sub_ctx->shaders[PIPE_SHADER_TESS_EVAL])
            glBeginTransformFeedback(get_tess_xfb_mode(sub_ctx->shaders[PIPE_SHADER_TESS_EVAL]->sinfo.tes_prim,
                               sub_ctx->shaders[PIPE_SHADER_TESS_EVAL]->sinfo.tes_point_mode));
         else
            glBeginTransformFeedback(get_xfb_mode(info->mode));
         sub_ctx->current_so->xfb_state = XFB_STATE_STARTED;
      } else if (sub_ctx->current_so->xfb_state == XFB_STATE_PAUSED) {
         glResumeTransformFeedback();
         sub_ctx->current_so->xfb_state = XFB_STATE_STARTED;
      }
   }

   if (info->primitive_restart) {
      if (vrend_state.use_gles) {
         glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
      } else if (has_feature(feat_gl_prim_restart)) {
         glEnable(GL_PRIMITIVE_RESTART);
         glPrimitiveRestartIndex(info->restart_index);
      } else if (has_feature(feat_nv_prim_restart)) {
         glEnableClientState(GL_PRIMITIVE_RESTART_NV);
         glPrimitiveRestartIndexNV(info->restart_index);
      }
   }

   if (has_feature(feat_indirect_draw)) {
      GLint buf = indirect_res ? indirect_res->gl_id : 0;
      glBindBuffer(GL_DRAW_INDIRECT_BUFFER, buf);

      if (has_feature(feat_indirect_params)) {
         GLint buf = indirect_params_res ? indirect_params_res->gl_id : 0;
         glBindBuffer(GL_PARAMETER_BUFFER_ARB, buf);
      }
   }

   if (info->vertices_per_patch && has_feature(feat_tessellation))
      glPatchParameteri(GL_PATCH_VERTICES, info->vertices_per_patch);

   /* If the host support blend_equation_advanced but not fbfetch,
    * the guest driver will not lower the equation to fbfetch so we need to set up the renderer to
    * accept those blend equations.
    * When we transmit the blend mode through alpha_src_factor, alpha_dst_factor is always 0.
    */
   uint32_t blend_mask_shader = sub_ctx->shaders[PIPE_SHADER_FRAGMENT]->sinfo.fs_blend_equation_advanced;
   uint32_t blend_mode = sub_ctx->blend_state.rt[0].alpha_src_factor;
   uint32_t alpha_dst_factor = sub_ctx->blend_state.rt[0].alpha_dst_factor;
   bool use_advanced_blending = !has_feature(feat_framebuffer_fetch) &&
                                 has_feature(feat_blend_equation_advanced) &&
                                 blend_mask_shader != 0 &&
                                 blend_mode != 0 &&
                                 alpha_dst_factor == 0;
   if (use_advanced_blending) {
      GLenum blend = translate_blend_func_advanced(blend_mode);
      glBlendEquation(blend);
      glEnable(GL_BLEND);
   }

   /* set the vertex state up now on a delay */
   if (!info->indexed) {
      GLenum mode = info->mode;
      int count = cso ? cso : info->count;
      int start = cso ? 0 : info->start;

      if (indirect_handle) {
         if (indirect_params_res)
            glMultiDrawArraysIndirectCountARB(mode, (GLvoid const *)(uintptr_t)info->indirect.offset,
                                              info->indirect.indirect_draw_count_offset, info->indirect.draw_count, info->indirect.stride);
         else if (info->indirect.draw_count > 1)
            glMultiDrawArraysIndirect(mode, (GLvoid const *)(uintptr_t)info->indirect.offset, info->indirect.draw_count, info->indirect.stride);
         else
            glDrawArraysIndirect(mode, (GLvoid const *)(uintptr_t)info->indirect.offset);
      } else if (info->instance_count > 0) {
         if (info->start_instance > 0)
            glDrawArraysInstancedBaseInstance(mode, start, count, info->instance_count, info->start_instance);
         else
            glDrawArraysInstancedARB(mode, start, count, info->instance_count);
      } else
         glDrawArrays(mode, start, count);
   } else {
      GLenum elsz;
      GLenum mode = info->mode;
      switch (sub_ctx->ib.index_size) {
      case 1:
         elsz = GL_UNSIGNED_BYTE;
         break;
      case 2:
         elsz = GL_UNSIGNED_SHORT;
         break;
      case 4:
      default:
         elsz = GL_UNSIGNED_INT;
         break;
      }

      if (indirect_handle) {
         if (indirect_params_res)
            glMultiDrawElementsIndirectCountARB(mode, elsz, (GLvoid const *)(uintptr_t)info->indirect.offset,
                                                info->indirect.indirect_draw_count_offset, info->indirect.draw_count, info->indirect.stride);
         else if (info->indirect.draw_count > 1)
            glMultiDrawElementsIndirect(mode, elsz, (GLvoid const *)(uintptr_t)info->indirect.offset, info->indirect.draw_count, info->indirect.stride);
         else
            glDrawElementsIndirect(mode, elsz, (GLvoid const *)(uintptr_t)info->indirect.offset);
      } else if (info->index_bias) {
         if (info->instance_count > 0) {
            if (info->start_instance > 0)
               glDrawElementsInstancedBaseVertexBaseInstance(mode, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset,
                                                             info->instance_count, info->index_bias, info->start_instance);
            else
               glDrawElementsInstancedBaseVertex(mode, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset, info->instance_count, info->index_bias);


         } else if (info->min_index != 0 || info->max_index != (unsigned)-1)
            glDrawRangeElementsBaseVertex(mode, info->min_index, info->max_index, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset, info->index_bias);
         else
            glDrawElementsBaseVertex(mode, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset, info->index_bias);
      } else if (info->instance_count > 0) {
         if (info->start_instance > 0) {
            glDrawElementsInstancedBaseInstance(mode, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset, info->instance_count, info->start_instance);
         } else
            glDrawElementsInstancedARB(mode, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset, info->instance_count);
      } else if (info->min_index != 0 || info->max_index != (unsigned)-1)
         glDrawRangeElements(mode, info->min_index, info->max_index, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset);
      else
         glDrawElements(mode, info->count, elsz, (void *)(uintptr_t)sub_ctx->ib.offset);
   }

   if (info->primitive_restart) {
      if (vrend_state.use_gles) {
         glDisable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
      } else if (has_feature(feat_gl_prim_restart)) {
         glDisable(GL_PRIMITIVE_RESTART);
      } else if (has_feature(feat_nv_prim_restart)) {
         glDisableClientState(GL_PRIMITIVE_RESTART_NV);
      }
   }

   if (sub_ctx->current_so && has_feature(feat_transform_feedback2)) {
      if (sub_ctx->current_so->xfb_state == XFB_STATE_STARTED) {
         glPauseTransformFeedback();
         sub_ctx->current_so->xfb_state = XFB_STATE_PAUSED;
      }
   }

   if (use_advanced_blending)
      glDisable(GL_BLEND);
   return 0;
}

void vrend_launch_grid(struct vrend_context *ctx,
                       UNUSED uint32_t *block,
                       uint32_t *grid,
                       uint32_t indirect_handle,
                       uint32_t indirect_offset)
{
   bool new_program = false;
   struct vrend_resource *indirect_res = NULL;

   if (!has_feature(feat_compute_shader))
      return;

    struct vrend_sub_context *sub_ctx = ctx->sub;

   if (sub_ctx->cs_shader_dirty) {
      struct vrend_linked_shader_program *prog;
      bool cs_dirty;

      if (!sub_ctx->shaders[PIPE_SHADER_COMPUTE]) {
         virgl_error("Dropping rendering due to missing shaders: %s\n", ctx->debug_name);
         return;
      }

      vrend_shader_select(sub_ctx, sub_ctx->shaders[PIPE_SHADER_COMPUTE], &cs_dirty);
      if (!sub_ctx->shaders[PIPE_SHADER_COMPUTE]->current) {
         virgl_error("Failure to select compute shader variant: %s\n", ctx->debug_name);
         return;
      }
      if (!sub_ctx->shaders[PIPE_SHADER_COMPUTE]->current->is_compiled) {
         if (!vrend_compile_shader(sub_ctx, sub_ctx->shaders[PIPE_SHADER_COMPUTE]->current)) {
            virgl_error("Failure to compile compute shader variant: %s\n", ctx->debug_name);
            return;
         }
      }
      if (sub_ctx->shaders[PIPE_SHADER_COMPUTE]->current->id != (GLuint)sub_ctx->prog_ids[PIPE_SHADER_COMPUTE]) {
         prog = lookup_cs_shader_program(ctx, sub_ctx->shaders[PIPE_SHADER_COMPUTE]->current->id);
         if (!prog) {
            prog = add_cs_shader_program(ctx, sub_ctx->shaders[PIPE_SHADER_COMPUTE]->current);
            if (!prog)
               return;
         }
      } else
         prog = sub_ctx->prog;

      if (sub_ctx->prog != prog) {
         new_program = true;
         sub_ctx->prog_ids[PIPE_SHADER_VERTEX] = 0;
         sub_ctx->prog_ids[PIPE_SHADER_COMPUTE] = sub_ctx->shaders[PIPE_SHADER_COMPUTE]->current->id;
         sub_ctx->prog = prog;
         prog->ref_context = sub_ctx;
      }
      sub_ctx->shader_dirty = true;
      sub_ctx->cs_shader_dirty = false;
   }

   if (!sub_ctx->prog) {
      virgl_error("%s: Skipping compute shader execution due to missing shaders: %s\n",
                   __func__, ctx->debug_name);
      return;
   }

   vrend_use_program(sub_ctx->prog);

   vrend_set_active_pipeline_stage(sub_ctx->prog, PIPE_SHADER_COMPUTE);
   vrend_draw_bind_ubo_shader(sub_ctx, PIPE_SHADER_COMPUTE, 0);
   vrend_draw_bind_const_shader(sub_ctx, PIPE_SHADER_COMPUTE, new_program);
   vrend_draw_bind_samplers_shader(sub_ctx, PIPE_SHADER_COMPUTE, 0);
   vrend_draw_bind_images_shader(sub_ctx, PIPE_SHADER_COMPUTE);
   vrend_draw_bind_ssbo_shader(sub_ctx, PIPE_SHADER_COMPUTE);
   vrend_draw_bind_abo_shader(sub_ctx);

   if (indirect_handle) {
      indirect_res = vrend_renderer_ctx_res_lookup(ctx, indirect_handle);
      if (!indirect_res || !indirect_res->gl_id) {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, indirect_handle);
         return;
      }
   }

   if (indirect_res)
      glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, indirect_res->gl_id);
   else
      glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);

   if (indirect_res) {
      glDispatchComputeIndirect(indirect_offset);
   } else {
      glDispatchCompute(grid[0], grid[1], grid[2]);
   }
}

static GLenum translate_blend_func(uint32_t pipe_blend)
{
   switch(pipe_blend){
   case PIPE_BLEND_ADD: return GL_FUNC_ADD;
   case PIPE_BLEND_SUBTRACT: return GL_FUNC_SUBTRACT;
   case PIPE_BLEND_REVERSE_SUBTRACT: return GL_FUNC_REVERSE_SUBTRACT;
   case PIPE_BLEND_MIN: return GL_MIN;
   case PIPE_BLEND_MAX: return GL_MAX;
   default:
      assert("invalid blend token()" == NULL);
      return 0;
   }
}

static GLenum translate_blend_factor(uint32_t pipe_factor)
{
   switch (pipe_factor) {
   case PIPE_BLENDFACTOR_ONE: return GL_ONE;
   case PIPE_BLENDFACTOR_SRC_COLOR: return GL_SRC_COLOR;
   case PIPE_BLENDFACTOR_SRC_ALPHA: return GL_SRC_ALPHA;

   case PIPE_BLENDFACTOR_DST_COLOR: return GL_DST_COLOR;
   case PIPE_BLENDFACTOR_DST_ALPHA: return GL_DST_ALPHA;

   case PIPE_BLENDFACTOR_CONST_COLOR: return GL_CONSTANT_COLOR;
   case PIPE_BLENDFACTOR_CONST_ALPHA: return GL_CONSTANT_ALPHA;

   case PIPE_BLENDFACTOR_SRC1_COLOR: return GL_SRC1_COLOR;
   case PIPE_BLENDFACTOR_SRC1_ALPHA: return GL_SRC1_ALPHA;
   case PIPE_BLENDFACTOR_SRC_ALPHA_SATURATE: return GL_SRC_ALPHA_SATURATE;
   case PIPE_BLENDFACTOR_ZERO: return GL_ZERO;


   case PIPE_BLENDFACTOR_INV_SRC_COLOR: return GL_ONE_MINUS_SRC_COLOR;
   case PIPE_BLENDFACTOR_INV_SRC_ALPHA: return GL_ONE_MINUS_SRC_ALPHA;

   case PIPE_BLENDFACTOR_INV_DST_COLOR: return GL_ONE_MINUS_DST_COLOR;
   case PIPE_BLENDFACTOR_INV_DST_ALPHA: return GL_ONE_MINUS_DST_ALPHA;

   case PIPE_BLENDFACTOR_INV_CONST_COLOR: return GL_ONE_MINUS_CONSTANT_COLOR;
   case PIPE_BLENDFACTOR_INV_CONST_ALPHA: return GL_ONE_MINUS_CONSTANT_ALPHA;

   case PIPE_BLENDFACTOR_INV_SRC1_COLOR: return GL_ONE_MINUS_SRC1_COLOR;
   case PIPE_BLENDFACTOR_INV_SRC1_ALPHA: return GL_ONE_MINUS_SRC1_ALPHA;

   default:
      assert("invalid blend token()" == NULL);
      return 0;
   }
}

static GLenum
translate_logicop(GLuint pipe_logicop)
{
   switch (pipe_logicop) {
#define CASE(x) case PIPE_LOGICOP_##x: return GL_##x
      CASE(CLEAR);
      CASE(NOR);
      CASE(AND_INVERTED);
      CASE(COPY_INVERTED);
      CASE(AND_REVERSE);
      CASE(INVERT);
      CASE(XOR);
      CASE(NAND);
      CASE(AND);
      CASE(EQUIV);
      CASE(NOOP);
      CASE(OR_INVERTED);
      CASE(COPY);
      CASE(OR_REVERSE);
      CASE(OR);
      CASE(SET);
   default:
      assert("invalid logicop token()" == NULL);
      return 0;
   }
#undef CASE
}

static GLenum
translate_stencil_op(GLuint op)
{
   switch (op) {
#define CASE(x) case PIPE_STENCIL_OP_##x: return GL_##x
      CASE(KEEP);
      CASE(ZERO);
      CASE(REPLACE);
      CASE(INCR);
      CASE(DECR);
      CASE(INCR_WRAP);
      CASE(DECR_WRAP);
      CASE(INVERT);
   default:
      assert("invalid stencilop token()" == NULL);
      return 0;
   }
#undef CASE
}

static inline bool is_dst_blend(int blend_factor)
{
   return (blend_factor == PIPE_BLENDFACTOR_DST_ALPHA ||
           blend_factor == PIPE_BLENDFACTOR_INV_DST_ALPHA);
}

static inline int conv_a8_blend(int blend_factor)
{
   if (blend_factor == PIPE_BLENDFACTOR_DST_ALPHA)
      return PIPE_BLENDFACTOR_DST_COLOR;
   if (blend_factor == PIPE_BLENDFACTOR_INV_DST_ALPHA)
      return PIPE_BLENDFACTOR_INV_DST_COLOR;
   return blend_factor;
}

static inline int conv_dst_blend(int blend_factor)
{
   if (blend_factor == PIPE_BLENDFACTOR_DST_ALPHA)
      return PIPE_BLENDFACTOR_ONE;
   if (blend_factor == PIPE_BLENDFACTOR_INV_DST_ALPHA)
      return PIPE_BLENDFACTOR_ZERO;
   return blend_factor;
}

static inline bool is_const_blend(int blend_factor)
{
   return (blend_factor == PIPE_BLENDFACTOR_CONST_COLOR ||
           blend_factor == PIPE_BLENDFACTOR_CONST_ALPHA ||
           blend_factor == PIPE_BLENDFACTOR_INV_CONST_COLOR ||
           blend_factor == PIPE_BLENDFACTOR_INV_CONST_ALPHA);
}

static void vrend_hw_emit_blend(struct vrend_sub_context *sub_ctx, struct pipe_blend_state *state)
{
   bool logicop_changed = false;
   if (state->logicop_enable != sub_ctx->hw_blend_state.logicop_enable) {
      sub_ctx->hw_blend_state.logicop_enable = state->logicop_enable;
      logicop_changed = true;
   }

   if (state->logicop_enable &&
       state->logicop_func != sub_ctx->hw_blend_state.logicop_func) {
      sub_ctx->hw_blend_state.logicop_func = state->logicop_func;
      logicop_changed = true;
   }

   if (logicop_changed) {
      if (vrend_state.use_gles) {
         if (can_emulate_logicop(state->logicop_func))
            sub_ctx->shader_dirty = true;
         else
            report_gles_warn(sub_ctx->parent, GLES_WARN_LOGIC_OP);
      } else if (state->logicop_enable) {
         glEnable(GL_COLOR_LOGIC_OP);
         glLogicOp(translate_logicop(state->logicop_func));
      } else {
         glDisable(GL_COLOR_LOGIC_OP);
      }
   }

   if (state->independent_blend_enable &&
       has_feature(feat_indep_blend) &&
       has_feature(feat_indep_blend_func)) {
      /* ARB_draw_buffers_blend is required for this */
      int i;

      for (i = 0; i < PIPE_MAX_COLOR_BUFS; i++) {

         if (state->rt[i].blend_enable) {
            bool dual_src = util_blend_state_is_dual(&sub_ctx->blend_state, i);
            if (dual_src && !has_feature(feat_dual_src_blend)) {
               virgl_error("Dual src blend requested but not supported for rt %d\n", i);
               continue;
            }

            glBlendFuncSeparateiARB(i, translate_blend_factor(state->rt[i].rgb_src_factor),
                                    translate_blend_factor(state->rt[i].rgb_dst_factor),
                                    translate_blend_factor(state->rt[i].alpha_src_factor),
                                    translate_blend_factor(state->rt[i].alpha_dst_factor));
            glBlendEquationSeparateiARB(i, translate_blend_func(state->rt[i].rgb_func),
                                        translate_blend_func(state->rt[i].alpha_func));
            glEnableIndexedEXT(GL_BLEND, i);
         } else
            glDisableIndexedEXT(GL_BLEND, i);

         if (state->rt[i].colormask != sub_ctx->hw_blend_state.rt[i].colormask) {
            sub_ctx->hw_blend_state.rt[i].colormask = state->rt[i].colormask;
            glColorMaskIndexedEXT(i, state->rt[i].colormask & PIPE_MASK_R ? GL_TRUE : GL_FALSE,
                                  state->rt[i].colormask & PIPE_MASK_G ? GL_TRUE : GL_FALSE,
                                  state->rt[i].colormask & PIPE_MASK_B ? GL_TRUE : GL_FALSE,
                                  state->rt[i].colormask & PIPE_MASK_A ? GL_TRUE : GL_FALSE);
         }
      }
   } else {
      if (state->rt[0].blend_enable) {
         bool dual_src = util_blend_state_is_dual(&sub_ctx->blend_state, 0);
         if (dual_src && !has_feature(feat_dual_src_blend)) {
            virgl_error("Dual src blend requested but not supported for rt 0\n");
         }
         glBlendFuncSeparate(translate_blend_factor(state->rt[0].rgb_src_factor),
                             translate_blend_factor(state->rt[0].rgb_dst_factor),
                             translate_blend_factor(state->rt[0].alpha_src_factor),
                             translate_blend_factor(state->rt[0].alpha_dst_factor));
         glBlendEquationSeparate(translate_blend_func(state->rt[0].rgb_func),
                                 translate_blend_func(state->rt[0].alpha_func));
         glEnable(GL_BLEND);
      }
      else
         glDisable(GL_BLEND);

      if (state->rt[0].colormask != sub_ctx->hw_blend_state.rt[0].colormask ||
          (sub_ctx->hw_blend_state.independent_blend_enable &&
           !state->independent_blend_enable)) {
         glColorMask(state->rt[0].colormask & PIPE_MASK_R ? GL_TRUE : GL_FALSE,
                     state->rt[0].colormask & PIPE_MASK_G ? GL_TRUE : GL_FALSE,
                     state->rt[0].colormask & PIPE_MASK_B ? GL_TRUE : GL_FALSE,
                     state->rt[0].colormask & PIPE_MASK_A ? GL_TRUE : GL_FALSE);
         for (int i = 0; i < PIPE_MAX_COLOR_BUFS; i++)
            sub_ctx->hw_blend_state.rt[i].colormask = state->rt[0].colormask;
      }
   }
   sub_ctx->hw_blend_state.independent_blend_enable = state->independent_blend_enable;

   if (has_feature(feat_multisample)) {
      if (state->alpha_to_coverage)
         glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
      else
         glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);

      if (!vrend_state.use_gles) {
         if (state->alpha_to_one)
            glEnable(GL_SAMPLE_ALPHA_TO_ONE);
         else
            glDisable(GL_SAMPLE_ALPHA_TO_ONE);
      }
   }

   if (state->dither)
      glEnable(GL_DITHER);
   else
      glDisable(GL_DITHER);
}

/* there are a few reasons we might need to patch the blend state.
   a) patching blend factors for dst with no alpha
   b) patching colormask/blendcolor/blendfactors for A8/A16 format
   emulation using GL_R8/GL_R16.
*/
static void vrend_patch_blend_state(struct vrend_sub_context *sub_ctx)
{
   struct pipe_blend_state new_state = sub_ctx->blend_state;
   struct pipe_blend_state *state = &sub_ctx->blend_state;
   bool swizzle_blend_color = false;
   struct pipe_blend_color blend_color = sub_ctx->blend_color;

   if (sub_ctx->nr_cbufs == 0) {
      sub_ctx->blend_state_dirty = false;
      return;
   }

   for (uint32_t i = 0; i < (state->independent_blend_enable ? PIPE_MAX_COLOR_BUFS : 1); i++) {
      if (i < sub_ctx->nr_cbufs && sub_ctx->surf[i]) {
         if (vrend_format_is_emulated_alpha(sub_ctx->surf[i]->format)) {
            if (state->rt[i].blend_enable) {
               new_state.rt[i].rgb_src_factor = conv_a8_blend(state->rt[i].alpha_src_factor);
               new_state.rt[i].rgb_dst_factor = conv_a8_blend(state->rt[i].alpha_dst_factor);
               new_state.rt[i].alpha_src_factor = PIPE_BLENDFACTOR_ZERO;
               new_state.rt[i].alpha_dst_factor = PIPE_BLENDFACTOR_ZERO;
            }
            new_state.rt[i].colormask = 0;
            if (state->rt[i].colormask & PIPE_MASK_A)
               new_state.rt[i].colormask |= PIPE_MASK_R;
            if (is_const_blend(new_state.rt[i].rgb_src_factor) ||
                is_const_blend(new_state.rt[i].rgb_dst_factor)) {
               swizzle_blend_color = true;
            }
         } else if (!util_format_has_alpha(sub_ctx->surf[i]->format)) {
            if (!(is_dst_blend(state->rt[i].rgb_src_factor) ||
                  is_dst_blend(state->rt[i].rgb_dst_factor) ||
                  is_dst_blend(state->rt[i].alpha_src_factor) ||
                  is_dst_blend(state->rt[i].alpha_dst_factor)))
               continue;
            new_state.rt[i].rgb_src_factor = conv_dst_blend(state->rt[i].rgb_src_factor);
            new_state.rt[i].rgb_dst_factor = conv_dst_blend(state->rt[i].rgb_dst_factor);
            new_state.rt[i].alpha_src_factor = conv_dst_blend(state->rt[i].alpha_src_factor);
            new_state.rt[i].alpha_dst_factor = conv_dst_blend(state->rt[i].alpha_dst_factor);
         }
      }
   }

   vrend_hw_emit_blend(sub_ctx, &new_state);

   if (swizzle_blend_color) {
      blend_color.color[0] = blend_color.color[3];
      blend_color.color[1] = 0.0f;
      blend_color.color[2] = 0.0f;
      blend_color.color[3] = 0.0f;
   }

   glBlendColor(blend_color.color[0],
                blend_color.color[1],
                blend_color.color[2],
                blend_color.color[3]);

   sub_ctx->blend_state_dirty = false;
}

void vrend_object_bind_blend(struct vrend_context *ctx,
                             uint32_t handle)
{
   struct pipe_blend_state *state;

   if (handle == 0) {
      memset(&ctx->sub->blend_state, 0, sizeof(ctx->sub->blend_state));
      glDisable(GL_BLEND);
      return;
   }
   state = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_BLEND);
   if (!state) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_HANDLE, handle);
      return;
   }

   ctx->sub->shader_dirty = true;
   ctx->sub->blend_state = *state;

   ctx->sub->blend_state_dirty = true;
}

static void vrend_hw_emit_dsa(struct vrend_sub_context *sub_ctx)
{
   struct pipe_depth_stencil_alpha_state *state = &sub_ctx->dsa_state;

   if (state->depth.enabled) {
      vrend_depth_test_enable(sub_ctx, true);
      glDepthFunc(GL_NEVER + state->depth.func);
      if (state->depth.writemask)
         glDepthMask(GL_TRUE);
      else
         glDepthMask(GL_FALSE);
   } else
      vrend_depth_test_enable(sub_ctx, false);

   if (state->alpha.enabled) {
      vrend_alpha_test_enable(sub_ctx, true);
      if (!vrend_state.use_core_profile)
         glAlphaFunc(GL_NEVER + state->alpha.func, state->alpha.ref_value);
   } else
      vrend_alpha_test_enable(sub_ctx, false);


}

static void vrend_object_bind_dsa_to_sub_context(struct vrend_sub_context *sub_ctx,
                                                 uint32_t handle)
{
   struct vrend_depth_stencil_alpha_state *state;

   if (handle == 0) {
      if (sub_ctx->dsa) {
         // unbind and set default state
         memset(&sub_ctx->dsa_state, 0, sizeof(sub_ctx->dsa_state));
         sub_ctx->dsa->owning_sub = NULL;
         sub_ctx->dsa = NULL;
         sub_ctx->stencil_state_dirty = true;
         sub_ctx->shader_dirty = true;
         vrend_hw_emit_dsa(sub_ctx);
      }

      return;
   }

   state = vrend_object_lookup(sub_ctx->object_hash, handle, VIRGL_OBJECT_DSA);
   if (!state) {
      vrend_report_context_error(sub_ctx->parent, VIRGL_ERROR_CTX_ILLEGAL_HANDLE, handle);
      return;
   }

   if (sub_ctx->dsa != state) {
      sub_ctx->stencil_state_dirty = true;
      sub_ctx->shader_dirty = true;
   }

   sub_ctx->dsa_state = state->base;
   sub_ctx->dsa = state;
   state->owning_sub = sub_ctx;

   if (sub_ctx->sysvalue_data.alpha_ref_val != state->base.alpha.ref_value) {
      sub_ctx->sysvalue_data.alpha_ref_val = state->base.alpha.ref_value;
      sub_ctx->sysvalue_data_cookie++;
   }

   vrend_hw_emit_dsa(sub_ctx);
}

void vrend_object_bind_dsa(struct vrend_context *ctx,
                           uint32_t handle)
{
   vrend_object_bind_dsa_to_sub_context (ctx->sub, handle);
}

static void vrend_update_frontface_state(struct vrend_sub_context *sub_ctx)
{
   struct pipe_rasterizer_state *state = &sub_ctx->rs_state;
   int front_ccw = state->front_ccw;

   front_ccw ^= (sub_ctx->fbo_origin_upper_left ? 0 : 1);
   if (front_ccw)
      glFrontFace(GL_CCW);
   else
      glFrontFace(GL_CW);
}

void vrend_update_stencil_state(struct vrend_sub_context *sub_ctx)
{
   struct vrend_depth_stencil_alpha_state *state = sub_ctx->dsa;
   int i;
   if (!state)
      return;

   if (!state->base.stencil[1].enabled) {
      if (state->base.stencil[0].enabled) {
         vrend_stencil_test_enable(sub_ctx, true);

         glStencilOp(translate_stencil_op(state->base.stencil[0].fail_op),
                     translate_stencil_op(state->base.stencil[0].zfail_op),
                     translate_stencil_op(state->base.stencil[0].zpass_op));

         glStencilFunc(GL_NEVER + state->base.stencil[0].func,
                       sub_ctx->stencil_refs[0],
                       state->base.stencil[0].valuemask);
         glStencilMask(state->base.stencil[0].writemask);
      } else
         vrend_stencil_test_enable(sub_ctx, false);
   } else {
      vrend_stencil_test_enable(sub_ctx, true);

      for (i = 0; i < 2; i++) {
         GLenum face = (i == 1) ? GL_BACK : GL_FRONT;
         glStencilOpSeparate(face,
                             translate_stencil_op(state->base.stencil[i].fail_op),
                             translate_stencil_op(state->base.stencil[i].zfail_op),
                             translate_stencil_op(state->base.stencil[i].zpass_op));

         glStencilFuncSeparate(face, GL_NEVER + state->base.stencil[i].func,
                               sub_ctx->stencil_refs[i],
                               state->base.stencil[i].valuemask);
         glStencilMaskSeparate(face, state->base.stencil[i].writemask);
      }
   }
   sub_ctx->stencil_state_dirty = false;
}

static inline GLenum translate_fill(uint32_t mode)
{
   switch (mode) {
   case PIPE_POLYGON_MODE_POINT:
      return GL_POINT;
   case PIPE_POLYGON_MODE_LINE:
      return GL_LINE;
   case PIPE_POLYGON_MODE_FILL:
      return GL_FILL;
   default:
      return GL_NONE;
   }
}

static void vrend_hw_emit_rs(struct vrend_context *ctx)
{
   struct pipe_rasterizer_state *state = &ctx->sub->rs_state;
   int i;

   if (has_feature(feat_depth_clamp)) {
      if (state->depth_clip)
         glDisable(GL_DEPTH_CLAMP);
      else
         glEnable(GL_DEPTH_CLAMP);
   }

   if (vrend_state.use_gles) {
      /* guest send invalid glPointSize parameter */
      if (!state->point_size_per_vertex &&
          state->point_size != 1.0f &&
          state->point_size != 0.0f) {
         report_gles_warn(ctx, GLES_WARN_POINT_SIZE);
      }
   } else if (state->point_size_per_vertex) {
      glEnable(GL_PROGRAM_POINT_SIZE);
   } else {
      glDisable(GL_PROGRAM_POINT_SIZE);
      if (state->point_size) {
         glPointSize(state->point_size);
      }
   }

   /* line_width < 0 is invalid, the guest sometimes forgot to set it. */
   glLineWidth(state->line_width <= 0 ? 1.0f : state->line_width);

   if (state->rasterizer_discard != ctx->sub->hw_rs_state.rasterizer_discard) {
      ctx->sub->hw_rs_state.rasterizer_discard = state->rasterizer_discard;
      if (state->rasterizer_discard)
         glEnable(GL_RASTERIZER_DISCARD);
      else
         glDisable(GL_RASTERIZER_DISCARD);
   }


   if (vrend_state.use_gles == true) {
      if (state->fill_front != PIPE_POLYGON_MODE_FILL) {
         report_gles_warn(ctx, GLES_WARN_POLYGON_MODE);
      }
      if (state->fill_back != PIPE_POLYGON_MODE_FILL) {
         report_gles_warn(ctx, GLES_WARN_POLYGON_MODE);
      }
   } else if (vrend_state.use_core_profile == false) {
      glPolygonMode(GL_FRONT, translate_fill(state->fill_front));
      glPolygonMode(GL_BACK, translate_fill(state->fill_back));
   } else if (state->fill_front == state->fill_back) {
      glPolygonMode(GL_FRONT_AND_BACK, translate_fill(state->fill_front));
   } else
      report_core_warn(ctx, CORE_PROFILE_WARN_POLYGON_MODE);

   if (state->offset_tri) {
      glEnable(GL_POLYGON_OFFSET_FILL);
   } else {
      glDisable(GL_POLYGON_OFFSET_FILL);
   }

   if (vrend_state.use_gles) {
      if (state->offset_line) {
         report_gles_warn(ctx, GLES_WARN_OFFSET_LINE);
      }
   } else if (state->offset_line) {
      glEnable(GL_POLYGON_OFFSET_LINE);
   } else {
      glDisable(GL_POLYGON_OFFSET_LINE);
   }

   if (vrend_state.use_gles) {
      if (state->offset_point) {
         report_gles_warn(ctx, GLES_WARN_OFFSET_POINT);
      }
   } else if (state->offset_point) {
      glEnable(GL_POLYGON_OFFSET_POINT);
   } else {
      glDisable(GL_POLYGON_OFFSET_POINT);
   }


   if (state->flatshade != ctx->sub->hw_rs_state.flatshade) {
      ctx->sub->hw_rs_state.flatshade = state->flatshade;
      if (vrend_state.use_core_profile == false) {
         if (state->flatshade) {
            glShadeModel(GL_FLAT);
         } else {
            glShadeModel(GL_SMOOTH);
         }
      }
   }

   if (state->clip_halfz != ctx->sub->hw_rs_state.clip_halfz) {
       if (has_feature(feat_clip_control)) {
          /* We only need to handle clip_halfz here, the bottom_edge_rule is
           * already handled via Gallium */
          GLenum depthrule = state->clip_halfz ? GL_ZERO_TO_ONE : GL_NEGATIVE_ONE_TO_ONE;
          glClipControl(GL_LOWER_LEFT, depthrule);
          ctx->sub->hw_rs_state.clip_halfz = state->clip_halfz;
       } else {
          virgl_warn("No clip control supported\n");
       }
   }
   if (state->flatshade_first != ctx->sub->hw_rs_state.flatshade_first) {
      ctx->sub->hw_rs_state.flatshade_first = state->flatshade_first;
      if (vrend_state.use_gles) {
         if (state->flatshade_first) {
            report_gles_warn(ctx, GLES_WARN_FLATSHADE_FIRST);
         }
      } else if (state->flatshade_first) {
         glProvokingVertexEXT(GL_FIRST_VERTEX_CONVENTION_EXT);
      } else {
         glProvokingVertexEXT(GL_LAST_VERTEX_CONVENTION_EXT);
      }
   }

   if (!vrend_state.use_gles && has_feature(feat_polygon_offset_clamp))
       glPolygonOffsetClampEXT(state->offset_scale, state->offset_units, state->offset_clamp);
   else
       glPolygonOffset(state->offset_scale, state->offset_units);

   if (!vrend_shader_use_core(ctx)) {
      if (state->poly_stipple_enable)
         glEnable(GL_POLYGON_STIPPLE);
      else
         glDisable(GL_POLYGON_STIPPLE);
   }

   if (state->point_quad_rasterization) {
      if (vrend_state.use_core_profile == false &&
          vrend_state.use_gles == false) {
         glEnable(GL_POINT_SPRITE);
      }

      if (vrend_state.use_gles == false) {
         glPointParameteri(GL_POINT_SPRITE_COORD_ORIGIN, state->sprite_coord_mode ? GL_UPPER_LEFT : GL_LOWER_LEFT);
      }
   } else {
      if (vrend_state.use_core_profile == false &&
          vrend_state.use_gles == false) {
         glDisable(GL_POINT_SPRITE);
      }
   }

   if (state->cull_face != PIPE_FACE_NONE) {
      switch (state->cull_face) {
      case PIPE_FACE_FRONT:
         glCullFace(GL_FRONT);
         break;
      case PIPE_FACE_BACK:
         glCullFace(GL_BACK);
         break;
      case PIPE_FACE_FRONT_AND_BACK:
         glCullFace(GL_FRONT_AND_BACK);
         break;
      default:
         virgl_warn("Unhandled cull-face: %x\n", state->cull_face);
      }
      glEnable(GL_CULL_FACE);
   } else
      glDisable(GL_CULL_FACE);

   /* two sided lighting handled in shader for core profile */
   if (vrend_state.use_core_profile == false) {
      if (state->light_twoside)
         glEnable(GL_VERTEX_PROGRAM_TWO_SIDE);
      else
         glDisable(GL_VERTEX_PROGRAM_TWO_SIDE);
   }

   if (state->clip_plane_enable != ctx->sub->hw_rs_state.clip_plane_enable) {
      ctx->sub->hw_rs_state.clip_plane_enable = state->clip_plane_enable;
      for (i = 0; i < 8; i++) {
         if (state->clip_plane_enable & (1 << i))
            glEnable(GL_CLIP_PLANE0 + i);
         else
            glDisable(GL_CLIP_PLANE0 + i);
      }

      ctx->sub->sysvalue_data_cookie++;
      if (ctx->sub->rs_state.clip_plane_enable) {
         ctx->sub->sysvalue_data.clip_plane_enabled = 1.f;
      } else {
         ctx->sub->sysvalue_data.clip_plane_enabled = 0.f;
      }
   }
   if (vrend_state.use_core_profile == false) {
      glLineStipple(state->line_stipple_factor, state->line_stipple_pattern);
      if (state->line_stipple_enable)
         glEnable(GL_LINE_STIPPLE);
      else
         glDisable(GL_LINE_STIPPLE);
   } else if (state->line_stipple_enable) {
      if (vrend_state.use_gles)
         report_core_warn(ctx, GLES_WARN_STIPPLE);
      else
         report_core_warn(ctx, CORE_PROFILE_WARN_STIPPLE);
   }


   if (vrend_state.use_gles) {
      if (state->line_smooth) {
         report_gles_warn(ctx, GLES_WARN_LINE_SMOOTH);
      }
   } else if (state->line_smooth) {
      glEnable(GL_LINE_SMOOTH);
   } else {
      glDisable(GL_LINE_SMOOTH);
   }

   if (vrend_state.use_gles) {
      if (state->poly_smooth) {
         report_gles_warn(ctx, GLES_WARN_POLY_SMOOTH);
      }
   } else if (state->poly_smooth) {
      glEnable(GL_POLYGON_SMOOTH);
   } else {
      glDisable(GL_POLYGON_SMOOTH);
   }

   if (vrend_state.use_core_profile == false) {
      if (state->clamp_vertex_color)
         glClampColor(GL_CLAMP_VERTEX_COLOR_ARB, GL_TRUE);
      else
         glClampColor(GL_CLAMP_VERTEX_COLOR_ARB, GL_FALSE);

      if (state->clamp_fragment_color)
         glClampColor(GL_CLAMP_FRAGMENT_COLOR_ARB, GL_TRUE);
      else
         glClampColor(GL_CLAMP_FRAGMENT_COLOR_ARB, GL_FALSE);
   } else {
      if (state->clamp_vertex_color || state->clamp_fragment_color)
         report_core_warn(ctx, CORE_PROFILE_WARN_CLAMP);
   }

   if (has_feature(feat_multisample)) {
      if (has_feature(feat_sample_mask)) {
         if (state->multisample)
            glEnable(GL_SAMPLE_MASK);
         else
            glDisable(GL_SAMPLE_MASK);
      }

      /* GLES doesn't have GL_MULTISAMPLE */
      if (!vrend_state.use_gles) {
         if (state->multisample)
            glEnable(GL_MULTISAMPLE);
         else
            glDisable(GL_MULTISAMPLE);
      }

      if (has_feature(feat_sample_shading)) {
         if (state->force_persample_interp)
            glEnable(GL_SAMPLE_SHADING);
         else
            glDisable(GL_SAMPLE_SHADING);
      }
   }

   if (state->scissor)
      glEnable(GL_SCISSOR_TEST);
   else
      glDisable(GL_SCISSOR_TEST);
   ctx->sub->hw_rs_state.scissor = state->scissor;

}

void vrend_object_bind_rasterizer(struct vrend_context *ctx,
                                  uint32_t handle)
{
   struct pipe_rasterizer_state *state;

   if (handle == 0) {
      memset(&ctx->sub->rs_state, 0, sizeof(ctx->sub->rs_state));
      return;
   }

   state = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_RASTERIZER);

   if (!state) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_HANDLE, handle);
      return;
   }

   ctx->sub->rs_state = *state;
   ctx->sub->shader_dirty = true;
   vrend_hw_emit_rs(ctx);
}

void vrend_bind_sampler_states(struct vrend_context *ctx,
                               enum pipe_shader_type shader_type,
                               uint32_t start_slot,
                               uint32_t num_states,
                               const uint32_t *handles)
{
   uint32_t i;
   struct vrend_sampler_state *state;

   if (shader_type >= PIPE_SHADER_TYPES) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, shader_type);
      return;
   }

   if (num_states > PIPE_MAX_SAMPLERS ||
       start_slot > (PIPE_MAX_SAMPLERS - num_states)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, num_states);
      return;
   }

   for (i = 0; i < num_states; i++) {
      if (handles[i] == 0)
         state = NULL;
      else
         state = vrend_object_lookup(ctx->sub->object_hash, handles[i], VIRGL_OBJECT_SAMPLER_STATE);

      if (!state && handles[i])
         virgl_warn("Failed to bind sampler state (handle=%d)\n", handles[i]);

      if (state)
         state->sub_ctx = ctx->sub;
      ctx->sub->views[shader_type].samplers[start_slot + i] = state;
      ctx->sub->views[shader_type].dirty_mask |= (1u << (start_slot + i));
   }
}

static void vrend_apply_sampler_state(struct vrend_sub_context *sub_ctx,
                                      struct vrend_resource *res,
                                      struct vrend_sampler_state *sampler_state,
                                      GLuint sampler_id,
                                      struct vrend_sampler_view *tview)
{
   struct vrend_texture *tex = (struct vrend_texture *)res;
   struct pipe_sampler_state *state = &sampler_state->base;
   bool set_all = false;
   GLenum target = tex->base.target;

   assert(offsetof(struct vrend_sampler_state, base) == 0);
   if (!state)
      return;

   if (res->base.nr_samples > 1) {
      tex->state = *state;
      return;
   }

   if (has_bit(tex->base.storage_bits, VREND_STORAGE_GL_BUFFER)) {
      tex->state = *state;
      return;
   }

   /*
    * If we emulate alpha format with red, we need to tell
    * the sampler to use the red channel and not the alpha one
    * by swizzling the GL_TEXTURE_BORDER_COLOR parameter.
    */
   bool is_emulated_alpha = vrend_format_is_emulated_alpha(tview->format);
   if (has_feature(feat_samplers)) {
      int sampler = sampler_state->ids[tview->srgb_decode == GL_SKIP_DECODE_EXT ? 0 : 1];
      if (is_emulated_alpha) {
         union pipe_color_union border_color;
         border_color = state->border_color;
         border_color.ui[0] = border_color.ui[3];
         border_color.ui[3] = 0;
         apply_sampler_border_color(sampler, border_color.ui);
      }

      glBindSampler(sampler_id, sampler);
      return;
   }

   if (tex->state.max_lod == -1)
      set_all = true;

   if (tex->state.wrap_s != state->wrap_s || set_all)
      glTexParameteri(target, GL_TEXTURE_WRAP_S, convert_wrap(sub_ctx->parent, state->wrap_s));
   if (tex->state.wrap_t != state->wrap_t || set_all)
      glTexParameteri(target, GL_TEXTURE_WRAP_T, convert_wrap(sub_ctx->parent, state->wrap_t));
   if (tex->state.wrap_r != state->wrap_r || set_all)
      glTexParameteri(target, GL_TEXTURE_WRAP_R, convert_wrap(sub_ctx->parent, state->wrap_r));
   if (tex->state.min_img_filter != state->min_img_filter ||
       tex->state.min_mip_filter != state->min_mip_filter || set_all)
      glTexParameterf(target, GL_TEXTURE_MIN_FILTER, convert_min_filter(state->min_img_filter, state->min_mip_filter));
   if (tex->state.mag_img_filter != state->mag_img_filter || set_all)
      glTexParameterf(target, GL_TEXTURE_MAG_FILTER, convert_mag_filter(state->mag_img_filter));
   if (res->target != GL_TEXTURE_RECTANGLE) {
      if (tex->state.min_lod != state->min_lod || set_all)
         glTexParameterf(target, GL_TEXTURE_MIN_LOD, state->min_lod);
      if (tex->state.max_lod != state->max_lod || set_all)
         glTexParameterf(target, GL_TEXTURE_MAX_LOD, state->max_lod);
      if (tex->state.lod_bias != state->lod_bias || set_all) {
         if (vrend_state.use_gles) {
            if (state->lod_bias)
               report_gles_warn(sub_ctx->parent, GLES_WARN_LOD_BIAS);
         } else {
            glTexParameterf(target, GL_TEXTURE_LOD_BIAS, state->lod_bias);
         }
      }
   }

   if (tex->state.compare_mode != state->compare_mode || set_all)
      glTexParameteri(target, GL_TEXTURE_COMPARE_MODE, state->compare_mode ? GL_COMPARE_R_TO_TEXTURE : GL_NONE);
   if (tex->state.compare_func != state->compare_func || set_all)
      glTexParameteri(target, GL_TEXTURE_COMPARE_FUNC, GL_NEVER + state->compare_func);
   if (has_feature(feat_anisotropic_filter) && (tex->state.max_anisotropy != state->max_anisotropy || set_all))
      glTexParameterf(target, GL_TEXTURE_MAX_ANISOTROPY, state->max_anisotropy);

   /*
    * Oh this is a fun one. On GLES 2.0 all cubemap MUST NOT be seamless.
    * But on GLES 3.0 all cubemaps MUST be seamless. Either way there is no
    * way to toggle between the behaviour when running on GLES. And adding
    * warnings will spew the logs quite bad. Ignore and hope for the best.
    */
   if (!vrend_state.use_gles) {
      if (state->seamless_cube_map) {
         glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
      } else {
         glDisable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
      }
   }

   if (memcmp(&tex->state.border_color, &state->border_color, 16) || set_all ||
       is_emulated_alpha) {
      if (is_emulated_alpha) {
         union pipe_color_union border_color;
         border_color = state->border_color;
         border_color.ui[0] = border_color.ui[3];
         border_color.ui[3] = 0;
         glTexParameterIuiv(target, GL_TEXTURE_BORDER_COLOR, border_color.ui);
      } else {
         glTexParameterIuiv(target, GL_TEXTURE_BORDER_COLOR, state->border_color.ui);
      }

   }
   tex->state = *state;
}

static GLenum tgsitargettogltarget(const enum pipe_texture_target target, int nr_samples)
{
   switch(target) {
   case PIPE_TEXTURE_1D:
      return GL_TEXTURE_1D;
   case PIPE_TEXTURE_2D:
      return (nr_samples > 1) ? GL_TEXTURE_2D_MULTISAMPLE : GL_TEXTURE_2D;
   case PIPE_TEXTURE_3D:
      return GL_TEXTURE_3D;
   case PIPE_TEXTURE_RECT:
      return GL_TEXTURE_RECTANGLE_NV;
   case PIPE_TEXTURE_CUBE:
      return GL_TEXTURE_CUBE_MAP;

   case PIPE_TEXTURE_1D_ARRAY:
      return GL_TEXTURE_1D_ARRAY;
   case PIPE_TEXTURE_2D_ARRAY:
      return (nr_samples > 1) ? GL_TEXTURE_2D_MULTISAMPLE_ARRAY : GL_TEXTURE_2D_ARRAY;
   case PIPE_TEXTURE_CUBE_ARRAY:
      return GL_TEXTURE_CUBE_MAP_ARRAY;
   case PIPE_BUFFER:
   default:
      return PIPE_BUFFER;
   }
   return PIPE_BUFFER;
}

static void vrend_free_sync_thread(void)
{
   if (!vrend_state.sync_thread)
      return;

   mtx_lock(&vrend_state.fence_mutex);
   vrend_state.stop_sync_thread = true;
   cnd_signal(&vrend_state.fence_cond);
   mtx_unlock(&vrend_state.fence_mutex);

   thrd_join(vrend_state.sync_thread, NULL);
   vrend_state.sync_thread = 0;

   cnd_destroy(&vrend_state.fence_cond);
   mtx_destroy(&vrend_state.fence_mutex);
   cnd_destroy(&vrend_state.poll_cond);
   mtx_destroy(&vrend_state.poll_mutex);
}

static void free_fence_locked(struct vrend_fence *fence)
{
   list_del(&fence->fences);
#ifdef HAVE_EPOXY_EGL_H
   if (vrend_state.use_egl_fence) {
      virgl_egl_fence_destroy(egl, fence->eglsyncobj);
   } else
#endif
   {
      glDeleteSync(fence->glsyncobj);
   }
   free(fence);
}

static void vrend_free_fences(void)
{
   /* this is called after vrend_free_sync_thread */
   assert(!vrend_state.sync_thread);

   list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_list, fences)
      free_fence_locked(fence);
   list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_wait_list, fences)
      free_fence_locked(fence);
}

static void vrend_free_fences_for_context(struct vrend_context *ctx)
{
   if (vrend_state.sync_thread) {
      mtx_lock(&vrend_state.fence_mutex);
      list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_list, fences) {
         if (fence->ctx == ctx)
            free_fence_locked(fence);
      }
      list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_wait_list, fences) {
         if (fence->ctx == ctx)
            free_fence_locked(fence);
      }
      if (vrend_state.fence_waiting) {
         /* mark the fence invalid as the sync thread is still waiting on it */
         vrend_state.fence_waiting->ctx = NULL;
      }
      mtx_unlock(&vrend_state.fence_mutex);
   } else {
      list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_list, fences) {
         if (fence->ctx == ctx)
            free_fence_locked(fence);
      }
   }
}

static bool do_wait(struct vrend_fence *fence, bool can_block)
{
#ifdef HAVE_EPOXY_EGL_H
   if (vrend_state.use_egl_fence)
      return virgl_egl_client_wait_fence(egl, fence->eglsyncobj, can_block);
#endif

   bool done = false;
   int timeout = can_block ? 1000000000 : 0;
   do {
      GLenum glret = glClientWaitSync(fence->glsyncobj, 0, timeout);
      if (glret == GL_WAIT_FAILED) {
         virgl_warn("Wait sync failed: illegal fence object %p\n", (void*) fence->glsyncobj);
      }
      done = glret != GL_TIMEOUT_EXPIRED;
   } while (!done && can_block);

   return done;
}

static void vrend_renderer_check_queries(void);

void vrend_renderer_poll(void) {
   if (vrend_state.use_async_fence_cb) {
      flush_eventfd(vrend_state.eventfd);
      mtx_lock(&vrend_state.poll_mutex);

      /* queries must be checked before fences are retired. */
      vrend_renderer_check_queries();

      /* wake up the sync thread to keep doing work */
      vrend_state.polling = false;
      cnd_signal(&vrend_state.poll_cond);
      mtx_unlock(&vrend_state.poll_mutex);
   } else {
      vrend_renderer_check_fences();
   }
}

static void wait_sync(struct vrend_fence *fence)
{
   struct vrend_context *ctx = fence->ctx;

   bool signal_poll = atomic_load(&vrend_state.has_waiting_queries);
   do_wait(fence, /* can_block */ true);

   mtx_lock(&vrend_state.fence_mutex);
   if (vrend_state.use_async_fence_cb) {
      /* to be able to call free_fence_locked without locking */
      list_inithead(&fence->fences);
   } else {
      list_addtail(&fence->fences, &vrend_state.fence_list);
   }
   vrend_state.fence_waiting = NULL;
   mtx_unlock(&vrend_state.fence_mutex);

   if (!vrend_state.use_async_fence_cb) {
      if (write_eventfd(vrend_state.eventfd, 1))
         perror("failed to write to eventfd\n");
      return;
   }

   /* If the current GL fence completed while one or more query was pending,
    * check queries on the main thread before notifying the caller about fence
    * completion.
    * TODO: store seqno of first query in waiting_query_list and compare to
    * current fence to avoid polling when it (and all later queries) are after
    * the current fence. */
   if (signal_poll) {
      mtx_lock(&vrend_state.poll_mutex);
      if (write_eventfd(vrend_state.eventfd, 1))
         perror("failed to write to eventfd\n");

      struct timespec ts;
      int ret;
      vrend_state.polling = true;
      do {
         ret = timespec_get(&ts, TIME_UTC);
         assert(ret);
         ts.tv_sec += 5;
         ret = cnd_timedwait(&vrend_state.poll_cond, &vrend_state.poll_mutex, &ts);
         if (ret)
            virgl_warn("timeout (5s) waiting for renderer poll() to finish.");
      } while (vrend_state.polling && ret);
   }

   /* vrend_free_fences_for_context might have marked the fence invalid
    * by setting fence->ctx to NULL
    */
   if (ctx) {
      ctx->fence_retire(fence->fence_id, ctx->fence_retire_data);
   }

   free_fence_locked(fence);

   if (signal_poll)
      mtx_unlock(&vrend_state.poll_mutex);
}

static int thread_sync(UNUSED void *arg)
{
   virgl_gl_context gl_context = vrend_state.sync_context;

   u_thread_setname("vrend-sync");

   mtx_lock(&vrend_state.fence_mutex);
   vrend_clicbs->make_current_surfaceless(gl_context);

   while (!vrend_state.stop_sync_thread) {
      if (list_is_empty(&vrend_state.fence_wait_list) &&
          cnd_wait(&vrend_state.fence_cond, &vrend_state.fence_mutex) != 0) {
         virgl_warn("Error while waiting on condition\n");
         break;
      }

      list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_wait_list, fences) {
         if (vrend_state.stop_sync_thread)
            break;
         list_del(&fence->fences);
         vrend_state.fence_waiting = fence;
         mtx_unlock(&vrend_state.fence_mutex);
         wait_sync(fence);
         mtx_lock(&vrend_state.fence_mutex);
      }
   }

   vrend_clicbs->make_current_surfaceless(0);
   vrend_clicbs->destroy_gl_context_surfaceless(vrend_state.sync_context);
   mtx_unlock(&vrend_state.fence_mutex);
   return 0;
}

static void vrend_renderer_use_threaded_sync(void)
{
   struct virgl_gl_ctx_param ctx_params = {0};

   ctx_params.shared = true;
   ctx_params.major_ver = vrend_state.gl_major_ver;
   ctx_params.minor_ver = vrend_state.gl_minor_ver;

   vrend_state.stop_sync_thread = false;

   vrend_state.sync_context = vrend_clicbs->create_gl_context_surfaceless(0, &ctx_params);
   if (vrend_state.sync_context == NULL) {
      virgl_error("Failed to create sync opengl context\n");
      return;
   }

   if (vrend_state.eventfd == -1) {
      vrend_state.eventfd = create_eventfd(0);
      if (vrend_state.eventfd == -1) {
         virgl_error("Failed to create eventfd\n");
         vrend_clicbs->destroy_gl_context_surfaceless(vrend_state.sync_context);
         return;
      }
   }

   cnd_init(&vrend_state.fence_cond);
   mtx_init(&vrend_state.fence_mutex, mtx_plain);
   cnd_init(&vrend_state.poll_cond);
   mtx_init(&vrend_state.poll_mutex, mtx_plain);
   vrend_state.polling = false;

   assert(!vrend_state.sync_thread);

   vrend_state.sync_thread = u_thread_create(thread_sync, NULL);
   if (!vrend_state.sync_thread) {
      close(vrend_state.eventfd);
      vrend_state.eventfd = -1;
      vrend_clicbs->destroy_gl_context_surfaceless(vrend_state.sync_context);
      cnd_destroy(&vrend_state.fence_cond);
      mtx_destroy(&vrend_state.fence_mutex);
      cnd_destroy(&vrend_state.poll_cond);
      mtx_destroy(&vrend_state.poll_mutex);
   }
}

static void vrend_debug_cb(UNUSED GLenum source, GLenum type, UNUSED GLuint id,
                           UNUSED GLenum severity, UNUSED GLsizei length,
                           UNUSED const GLchar* message, UNUSED const void* userParam)
{
   if (type != GL_DEBUG_TYPE_ERROR) {
      return;
   }

   virgl_error("ERROR: %s\n", message);
}

static void vrend_pipe_resource_unref(struct pipe_resource *pres,
                                      UNUSED void *data)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;

   if (vrend_state.finishing || pipe_reference(&res->base.reference, NULL))
      vrend_renderer_resource_destroy(res);
}

static void vrend_pipe_resource_attach_iov(struct pipe_resource *pres,
                                           const struct iovec *iov,
                                           int iov_count,
                                           UNUSED void *data)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;

   res->iov = iov;
   res->num_iovs = iov_count;

   if (has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) {
      vrend_write_to_iovec(res->iov, res->num_iovs, 0,
            res->ptr, res->base.width0);
   }
}

static void vrend_pipe_resource_detach_iov(struct pipe_resource *pres,
                                           UNUSED void *data)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;

   if (has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) {
      vrend_read_from_iovec(res->iov, res->num_iovs, 0,
            res->ptr, res->base.width0);
   }

   res->iov = NULL;
   res->num_iovs = 0;
}

static enum virgl_resource_fd_type vrend_pipe_resource_export_fd(UNUSED struct pipe_resource *pres,
                                                                 UNUSED int *fd,
                                                                 UNUSED void *data)
{
#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   struct vrend_resource *res = (struct vrend_resource *)pres;

   if (res->storage_bits & VREND_STORAGE_GBM_BUFFER) {
      int ret = virgl_gbm_export_fd(gbm->device,
                                    gbm_bo_get_handle(res->gbm_bo).u32, fd);
      if (!ret)
         return VIRGL_RESOURCE_FD_DMABUF;
   }
#endif

   return VIRGL_RESOURCE_FD_INVALID;
}

bool vrend_check_no_error(struct vrend_context *ctx)
{
   GLenum err;

   err = glGetError();
   if (err == GL_NO_ERROR)
      return true;

   while (err != GL_NO_ERROR) {
#ifdef CHECK_GL_ERRORS
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_UNKNOWN, err);
#else
      virgl_warn("GL error reported (%d) for context %d\n", err, ctx->ctx_id);
#endif
      err = glGetError();
   }

#ifdef CHECK_GL_ERRORS
   return false;
#else
   return true;
#endif
}

const struct virgl_resource_pipe_callbacks *
vrend_renderer_get_pipe_callbacks(void)
{
   static const struct virgl_resource_pipe_callbacks callbacks = {
      .unref = vrend_pipe_resource_unref,
      .attach_iov = vrend_pipe_resource_attach_iov,
      .detach_iov = vrend_pipe_resource_detach_iov,
      .export_fd = vrend_pipe_resource_export_fd,
   };

   return &callbacks;
}

static bool use_integer(void) {
   if (getenv("VIRGL_USE_INTEGER"))
      return true;

   const char * a = (const char *) glGetString(GL_VENDOR);
   if (!a)
       return false;
   if (strcmp(a, "ARM") == 0)
      return true;
   return false;
}

static bool vrend_use_gbm_layout_feature(UNUSED uint32_t flags)
{
#if defined(ENABLE_GBM_ALLOCATION) && !defined(MINIGBM)
   if (!gbm || !gbm->device)
      return false;

   if (debug_get_bool_option("VIRGL_GBM_LAYOUT_FORCE_ENABLE", false))
      return true;

   if (!debug_get_bool_option("VIRGL_GBM_LAYOUT_ENABLE", false))
      return false;

   if (!(flags & VREND_USE_GBM_LAYOUT))
      return false;

   /* gbm-layout needs a linear modifier quirk today, enable it by default only for
    * drivers known to work properly */
   if (!strstr((const char *) glGetString(GL_VERSION), "Mesa"))
      return false;

   return true;
#else
   return false;
#endif
}

int vrend_renderer_init(const struct vrend_if_cbs *cbs, uint32_t flags)
{
   bool gles;
   int gl_ver;
   virgl_gl_context gl_context;
   struct virgl_gl_ctx_param ctx_params = {0};

   vrend_clicbs = cbs;

   /* Give some defaults to be able to run the tests */
   vrend_state.max_texture_2d_size =
         vrend_state.max_texture_3d_size =
         vrend_state.max_texture_cube_size = 16384;

   if (VREND_DEBUG_ENABLED) {
      vrend_init_debug_flags();
   }

   ctx_params.shared = false;
   if (flags & VREND_USE_COMPAT_CONTEXT) {
      ctx_params.compat_ctx = true;
   }

   for (uint32_t i = 0; i < ARRAY_SIZE(gl_versions); i++) {
      ctx_params.major_ver = gl_versions[i].major;
      ctx_params.minor_ver = gl_versions[i].minor;

      gl_context = vrend_clicbs->create_gl_context(0, &ctx_params);
      if (gl_context)
         break;
   }

   if (!gl_context) {
      virgl_error("Unable to create %s context >= 3.0\n",
                  flags & VREND_USE_GLES ? "GLES" : "OpenGL");
      goto fail;
   }

   vrend_clicbs->make_current(gl_context);
   gl_ver = epoxy_gl_version();

   /* enable error output as early as possible */
   if (vrend_debug(NULL, dbg_khr) && epoxy_has_gl_extension("GL_KHR_debug")) {
      glDebugMessageCallback(vrend_debug_cb, NULL);
      glEnable(GL_DEBUG_OUTPUT);
      glDisable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
      set_feature(feat_debug_cb);
   }

   /* make sure you have the latest version of libepoxy */
   gles = epoxy_is_desktop_gl() == 0;

   vrend_state.gl_major_ver = gl_ver / 10;
   vrend_state.gl_minor_ver = gl_ver % 10;

   if (gles) {
      virgl_info("gl_version %d - es profile enabled\n", gl_ver);
      vrend_state.use_gles = true;
      /* for now, makes the rest of the code use the most GLES 3.x like path */
      vrend_state.use_core_profile = true;
   } else if (gl_ver > 30 && !epoxy_has_gl_extension("GL_ARB_compatibility")) {
      virgl_info("gl_version %d - core profile enabled\n", gl_ver);
      vrend_state.use_core_profile = true;
   } else {
      virgl_info("gl_version %d - compat profile\n", gl_ver);
   }

   vrend_state.use_integer = use_integer();

   init_features(gles ? 0 : gl_ver,
                 gles ? gl_ver : 0);

   if (!vrend_winsys_has_gl_colorspace())
      clear_feature(feat_srgb_write_control) ;

   glGetIntegerv(GL_MAX_DRAW_BUFFERS, (GLint *) &vrend_state.max_draw_buffers);

   /* For testing we need to know maximum */
   glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, (GLint*)&vrend_state.max_vertex_attributes);

   glGetIntegerv(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS,
                 (GLint*)&vrend_state.max_texture_units);

   /* Mesa clamps this value to 8 anyway, so just make sure that this side
    * doesn't exceed the number to be on the save side when using 8-bit masks
    * for the color buffers */
   if (vrend_state.max_draw_buffers > 8)
      vrend_state.max_draw_buffers = 8;

   if (!has_feature(feat_arb_robustness) &&
       !has_feature(feat_gles_khr_robustness)) {
      virgl_warn("Running without ARB/KHR robustness in place may crash\n");
   }

   /* callbacks for when we are cleaning up the object table */
   vrend_object_set_destroy_callback(VIRGL_OBJECT_QUERY, vrend_destroy_query_object);
   vrend_object_set_destroy_callback(VIRGL_OBJECT_SURFACE, vrend_destroy_surface_object);
   vrend_object_set_destroy_callback(VIRGL_OBJECT_SHADER, vrend_destroy_shader_object);
   vrend_object_set_destroy_callback(VIRGL_OBJECT_SAMPLER_VIEW, vrend_destroy_sampler_view_object);
   vrend_object_set_destroy_callback(VIRGL_OBJECT_STREAMOUT_TARGET, vrend_destroy_so_target_object);
   vrend_object_set_destroy_callback(VIRGL_OBJECT_SAMPLER_STATE, vrend_destroy_sampler_state_object);
   vrend_object_set_destroy_callback(VIRGL_OBJECT_VERTEX_ELEMENTS, vrend_destroy_vertex_elements_object);
   vrend_object_set_destroy_callback(VIRGL_OBJECT_DSA, vrend_destroy_dsa_object);

   /* disable for format testing, spews a lot of errors */
   if (has_feature(feat_debug_cb)) {
      glDisable(GL_DEBUG_OUTPUT);
   }

   vrend_build_format_list_common();

   if (vrend_state.use_gles) {
      vrend_build_format_list_gles();
   } else {
      vrend_build_format_list_gl();
   }

   vrend_check_texture_storage(tex_conv_table);

   if (has_feature(feat_multisample)) {
      vrend_check_texture_multisample(tex_conv_table,
                                      has_feature(feat_storage_multisample));
   }

   /* disable for format testing */
   if (has_feature(feat_debug_cb)) {
      glEnable(GL_DEBUG_OUTPUT);
   }

   vrend_clicbs->destroy_gl_context(gl_context);
   list_inithead(&vrend_state.fence_list);
   list_inithead(&vrend_state.fence_wait_list);
   list_inithead(&vrend_state.waiting_query_list);
   atomic_store(&vrend_state.has_waiting_queries, false);

   /* create 0 context */
   vrend_state.ctx0 = vrend_create_context(0, strlen("HOST"), "HOST");
   if (!vrend_state.ctx0) {
      virgl_error("Unable to create %s vrend context\n",
                  flags & VREND_USE_GLES ? "GLES" : "OpenGL");
      goto fail;
   }

   vrend_state.eventfd = -1;
   if (flags & VREND_USE_THREAD_SYNC) {
      if (flags & VREND_USE_ASYNC_FENCE_CB)
         vrend_state.use_async_fence_cb = true;
      vrend_renderer_use_threaded_sync();
   }
   if (flags & VREND_USE_EXTERNAL_BLOB)
      vrend_state.use_external_blob = true;

#ifdef HAVE_EPOXY_EGL_H
   vrend_state.use_egl_fence = virgl_egl_supports_fences(egl);
#endif

   if (!vrend_check_no_error(vrend_state.ctx0)) {
      virgl_error("vrend context creation resulted in errors\n");
      goto cleanup_and_fail;
   }

   if (!has_feature(feat_ubo)) {
      virgl_error("Host context missing support for required extension ARB_uniform_buffer_object\n");
      goto cleanup_and_fail;
   }

#ifdef ENABLE_VIDEO
   if (flags & VREND_USE_VIDEO) {
        if (vrend_clicbs->get_drm_fd)
            vrend_video_init(vrend_clicbs->get_drm_fd());
        else
            virgl_warn("Video disabled due to missing get_drm_fd\n");
   }
#endif

   vrend_state.d3d_share_texture = flags & VREND_D3D11_SHARE_TEXTURE;

   vrend_state.gbm_layout_feat = vrend_use_gbm_layout_feature(flags);

   return 0;
cleanup_and_fail:
   vrend_renderer_fini();
fail:
   return EINVAL;
}

void
vrend_renderer_fini(void)
{
   vrend_state.finishing = true;

   if (vrend_state.eventfd != -1) {
      close(vrend_state.eventfd);
      vrend_state.eventfd = -1;
   }

   vrend_free_fences();
   vrend_blitter_fini();

#ifdef ENABLE_VIDEO
   vrend_video_fini();
#endif

   vrend_destroy_context(vrend_state.ctx0);

   vrend_state.current_ctx = NULL;
   vrend_state.current_hw_ctx = NULL;

   vrend_state.finishing = false;
}

static void vrend_destroy_sub_context(struct vrend_sub_context *sub)
{
   vrend_clicbs->make_current(sub->gl_context);

   if (has_feature(feat_images)) {
      for (int shader_type = PIPE_SHADER_VERTEX;
           shader_type < PIPE_SHADER_TYPES;
           shader_type++) {
         uint32_t mask = sub->images_used_mask[shader_type];
         while (mask) {
            uint32_t i = u_bit_scan(&mask);
            struct vrend_image_view *iview = &sub->image_views[shader_type][i];
            vrend_resource_reference(&iview->texture, NULL);
         }
      }
   }

   if (has_feature(feat_atomic_counters)) {
      uint32_t mask = sub->abo_used_mask;
      while (mask) {
         uint32_t i = u_bit_scan(&mask);
         struct vrend_abo *abo = &sub->abo[i];
         abo->buffer_offset = 0;
         abo->buffer_size = 0;
         vrend_resource_reference(&abo->res, NULL);
      }
   }

   if (has_feature(feat_ssbo)) {
      for (int shader_type = PIPE_SHADER_VERTEX;
           shader_type < PIPE_SHADER_TYPES;
           shader_type++) {
         uint32_t mask = sub->ssbo_used_mask[shader_type];
         while (mask) {
            uint32_t i = u_bit_scan(&mask);
            struct vrend_ssbo *ssbo = &sub->ssbo[shader_type][i];
            ssbo->buffer_offset = 0;
            ssbo->buffer_size = 0;
            vrend_resource_reference(&ssbo->res, NULL);
         }
      }
   }

   if (sub->fb_id)
      glDeleteFramebuffers(1, &sub->fb_id);

   if (sub->blit_fb_ids[0])
      glDeleteFramebuffers(2, sub->blit_fb_ids);

   glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

   if (!has_feature(feat_gles31_vertex_attrib_binding)) {
      while (sub->enabled_attribs_bitmask) {
         uint32_t i = u_bit_scan(&sub->enabled_attribs_bitmask);

         glDisableVertexAttribArray(i);
      }
   }
   glDeleteVertexArrays(1, &sub->vaoid);
   glBindVertexArray(0);

   if (sub->current_so)
      glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, 0);

   list_for_each_entry_safe(struct vrend_streamout_object, obj, &sub->streamout_list, head)
      vrend_destroy_streamout_object(obj);

   vrend_shader_state_reference(&sub->shaders[PIPE_SHADER_VERTEX], NULL);
   vrend_shader_state_reference(&sub->shaders[PIPE_SHADER_FRAGMENT], NULL);
   vrend_shader_state_reference(&sub->shaders[PIPE_SHADER_GEOMETRY], NULL);
   vrend_shader_state_reference(&sub->shaders[PIPE_SHADER_TESS_CTRL], NULL);
   vrend_shader_state_reference(&sub->shaders[PIPE_SHADER_TESS_EVAL], NULL);
   vrend_shader_state_reference(&sub->shaders[PIPE_SHADER_COMPUTE], NULL);

   if (sub->prog)
      sub->prog->ref_context = NULL;

   vrend_free_programs(sub);
   for (enum pipe_shader_type type = 0; type < PIPE_SHADER_TYPES; type++) {
      free(sub->consts[type].consts);
      sub->consts[type].consts = NULL;
      while (sub->const_bufs_used_mask[type]) {
         uint32_t i = u_bit_scan(&sub->const_bufs_used_mask[type]);
         vrend_resource_reference((struct vrend_resource **)&sub->cbs[type][i].buffer, NULL);
      }

      for (unsigned i = 0; i < PIPE_MAX_SHADER_SAMPLER_VIEWS; i++) {
         vrend_sampler_view_reference(&sub->views[type].views[i], NULL);
      }
      for (unsigned i = 0; i < PIPE_MAX_SHADER_IMAGES; i++) {
         glDeleteTextures(1, &sub->image_views[type][i].view_id);
      }

      if (sub->long_shader_in_progress[type])
         vrend_destroy_long_shader_buffer(sub->long_shader_in_progress[type]);
   }

   if (sub->zsurf)
      vrend_surface_reference(&sub->zsurf, NULL);

   for (uint32_t i = 0; i < sub->nr_cbufs; i++) {
      if (!sub->surf[i])
         continue;
      vrend_surface_reference(&sub->surf[i], NULL);
   }

   vrend_set_num_vbo_sub(sub, 0);
   vrend_resource_reference((struct vrend_resource **)&sub->ib.buffer, NULL);

   vrend_object_fini_ctx_table(sub->object_hash);
   vrend_clicbs->destroy_gl_context(sub->gl_context);

   list_del(&sub->head);
   FREE(sub);

}

#ifdef ENABLE_TRACING
static void destroy_active_markers_entry(struct hash_entry *entry)
{
    TRACE_SCOPE_END(entry->data);
}
#endif

void vrend_destroy_context(struct vrend_context *ctx)
{
   bool switch_0 = (ctx == vrend_state.current_ctx);
   struct vrend_context *cur = vrend_state.current_ctx;
   if (switch_0) {
      vrend_state.current_ctx = NULL;
      vrend_state.current_hw_ctx = NULL;
   }

   vrend_clicbs->make_current(ctx->sub->gl_context);
   /* reset references on framebuffers */
   vrend_set_framebuffer_state(ctx, 0, NULL, 0);

   vrend_set_num_sampler_views(ctx, PIPE_SHADER_VERTEX, 0, 0);
   vrend_set_num_sampler_views(ctx, PIPE_SHADER_FRAGMENT, 0, 0);
   vrend_set_num_sampler_views(ctx, PIPE_SHADER_GEOMETRY, 0, 0);
   vrend_set_num_sampler_views(ctx, PIPE_SHADER_TESS_CTRL, 0, 0);
   vrend_set_num_sampler_views(ctx, PIPE_SHADER_TESS_EVAL, 0, 0);
   vrend_set_num_sampler_views(ctx, PIPE_SHADER_COMPUTE, 0, 0);

   vrend_set_streamout_targets(ctx, 0, 0, NULL);

   vrend_set_index_buffer(ctx, 0, 0, 0);

   list_for_each_entry_safe_rev(struct vrend_sub_context, sub, &ctx->sub_ctxs, head) {
      ctx->sub = sub;
      vrend_destroy_sub_context(sub);
   }
   ctx->sub = NULL;
   ctx->sub0 = NULL;

   if (ctx->ctx_id)
      vrend_renderer_force_ctx_0();

   vrend_free_fences_for_context(ctx);

#ifdef ENABLE_VIDEO
   vrend_video_destroy_context(ctx->video);
#endif

   list_for_each_entry_safe(struct vrend_resource, res, &ctx->vrend_resources, head) {
      free(res);
   }

   list_for_each_entry_safe(struct vrend_untyped_resource, untyped_res, &ctx->untyped_resources, head)
      free(untyped_res);
   vrend_ctx_resource_fini_table(ctx->res_hash);

#ifdef ENABLE_TRACING
   _mesa_hash_table_destroy(ctx->active_markers, destroy_active_markers_entry);
#endif

   FREE(ctx);

   if (!switch_0 && cur)
      vrend_hw_switch_context(cur, true);
}

struct vrend_context *vrend_create_context(int id, uint32_t nlen, const char *debug_name)
{

   struct vrend_context *grctx = CALLOC_STRUCT(vrend_context);

   if (!grctx)
      return NULL;

   if (nlen && debug_name) {
      strncpy(grctx->debug_name, debug_name,
              nlen < sizeof(grctx->debug_name) - 1 ?
              nlen : sizeof(grctx->debug_name) - 1);
      grctx->debug_name[sizeof(grctx->debug_name) - 1] = 0;
   }

   VREND_DEBUG(dbg_caller, grctx, "create context\n");

   grctx->ctx_id = id;

   list_inithead(&grctx->sub_ctxs);
   list_inithead(&grctx->vrend_resources);

#ifdef ENABLE_VIDEO
   grctx->video = vrend_video_create_context(grctx);
#endif

   grctx->res_hash = vrend_ctx_resource_init_table();
   list_inithead(&grctx->untyped_resources);

   grctx->shader_cfg.max_shader_patch_varyings = vrend_state.max_shader_patch_varyings;
   grctx->shader_cfg.use_gles = vrend_state.use_gles;
   grctx->shader_cfg.use_core_profile = vrend_state.use_core_profile;
   grctx->shader_cfg.use_explicit_locations = vrend_state.use_explicit_locations;
   grctx->shader_cfg.max_draw_buffers = vrend_state.max_draw_buffers;
   grctx->shader_cfg.has_arrays_of_arrays = has_feature(feat_arrays_of_arrays);
   grctx->shader_cfg.has_gpu_shader5 = has_feature(feat_gpu_shader5);
   grctx->shader_cfg.has_es31_compat = has_feature(feat_gles31_compatibility);
   grctx->shader_cfg.has_conservative_depth = has_feature(feat_conservative_depth);
   grctx->shader_cfg.use_integer = vrend_state.use_integer;
   grctx->shader_cfg.has_dual_src_blend = has_feature(feat_dual_src_blend);
   grctx->shader_cfg.has_fbfetch_coherent = has_feature(feat_framebuffer_fetch);
   grctx->shader_cfg.has_cull_distance = has_feature(feat_cull_distance);
   grctx->shader_cfg.has_nopersective = has_feature(feat_shader_noperspective_interpolation);
   grctx->shader_cfg.has_texture_shadow_lod = has_feature(feat_texture_shadow_lod);
   grctx->shader_cfg.has_vs_layer = has_feature(feat_vs_layer_viewport);
   grctx->shader_cfg.has_vs_viewport_index = has_feature(feat_vs_viewport_index);

   vrend_renderer_create_sub_ctx(grctx, 0);
   vrend_renderer_set_sub_ctx(grctx, 0);

   int glver = get_glsl_version();
   if (glver < 0) {
      virgl_error("Unable to query GL version\n");
      vrend_destroy_context(grctx);
      return NULL;
   }

   grctx->shader_cfg.glsl_version = glver;

   if (!grctx->ctx_id)
      grctx->fence_retire = vrend_clicbs->ctx0_fence_retire;

#ifdef ENABLE_TRACING
   grctx->active_markers = _mesa_hash_table_create(NULL, _mesa_hash_string, _mesa_key_string_equal);
#endif

   return grctx;
}

static int check_resource_valid(const struct vrend_renderer_resource_create_args *args,
                                char errmsg[256])
{
   /* limit the target */
   if (args->target >= PIPE_MAX_TEXTURE_TYPES) {
      snprintf(errmsg, 256, "Invalid texture target %d (>= %d)",
               args->target, PIPE_MAX_TEXTURE_TYPES);
      return -1;
   }

   if (args->format >= VIRGL_FORMAT_MAX) {
      snprintf(errmsg, 256, "Invalid texture format %d (>=%d)",
               args->format, VIRGL_FORMAT_MAX);
      return -1;
   }

   bool format_can_texture_storage = has_feature(feat_texture_storage) &&
         (tex_conv_table[args->format].flags & VIRGL_TEXTURE_CAN_TEXTURE_STORAGE);

   /* only texture 2d and 2d array can have multiple samples */
   if (args->nr_samples > 1) {
      if (!vrend_format_can_multisample(args->format)) {
         snprintf(errmsg, 256, "Unsupported multisample texture format %s",
                  util_format_name(args->format));
         return -1;
      }

      if (args->target != PIPE_TEXTURE_2D && args->target != PIPE_TEXTURE_2D_ARRAY) {
         snprintf(errmsg, 256, "Multisample textures not 2D (target:%d)", args->target);
         return -1;
      }
      /* multisample can't have miplevels */
      if (args->last_level > 0) {
         snprintf(errmsg, 256, "Multisample textures don't support mipmaps");
         return -1;
      }
   }

   if (args->last_level > 0) {
      /* buffer and rect textures can't have mipmaps */
      if (args->target == PIPE_BUFFER) {
         snprintf(errmsg, 256, "Buffers don't support mipmaps");
         return -1;
      }

      if (args->target == PIPE_TEXTURE_RECT) {
         snprintf(errmsg, 256, "RECT textures don't support mipmaps");
         return -1;
      }

      if (args->last_level > (floor(log2(MAX2(args->width, args->height))) + 1)) {
         snprintf(errmsg, 256, "Mipmap levels %d too large for texture size (%d, %d)",
                  args->last_level, args->width, args->height);
         return -1;
      }
   }

   if (args->flags != 0) {
      uint32_t supported_mask = VIRGL_RESOURCE_Y_0_TOP | VIRGL_RESOURCE_FLAG_MAP_PERSISTENT
                                | VIRGL_RESOURCE_FLAG_MAP_COHERENT;

      if (args->flags & ~supported_mask) {
         snprintf(errmsg, 256, "Resource flags 0x%x not supported", args->flags);
         return -1;
      }
   }

   if (args->flags & VIRGL_RESOURCE_Y_0_TOP) {
      if (args->target != PIPE_TEXTURE_2D && args->target != PIPE_TEXTURE_RECT) {
         snprintf(errmsg, 256, "VIRGL_RESOURCE_Y_0_TOP only supported for 2D or RECT textures");
         return -1;
      }
   }

   /* array size for array textures only */
   if (args->target == PIPE_TEXTURE_CUBE) {
      if (args->array_size != 6) {
         snprintf(errmsg, 256, "Cube map: unexpected array size %d", args->array_size);
         return -1;
      }
   } else if (args->target == PIPE_TEXTURE_CUBE_ARRAY) {
      if (!has_feature(feat_cube_map_array)) {
         snprintf(errmsg, 256, "Cube map arrays not supported");
         return -1;
      }
      if (args->array_size % 6) {
         snprintf(errmsg, 256, "Cube map array: unexpected array size %d", args->array_size);
         return -1;
      }
   } else if (args->array_size > 1) {
      if (args->target != PIPE_TEXTURE_2D_ARRAY &&
          args->target != PIPE_TEXTURE_1D_ARRAY) {
         snprintf(errmsg, 256, "Texture target %d can't be an array ", args->target);
         return -1;
      }

      if (!has_feature(feat_texture_array)) {
         snprintf(errmsg, 256, "Texture arrays are not supported");
         return -1;
      }
   }

   if (args->target != PIPE_BUFFER && !args->width) {
      snprintf(errmsg, 256, "Texture width must be >0");
      return -1;
   }

   if (args->bind == 0 ||
       args->bind == VIRGL_BIND_CUSTOM ||
       args->bind == VIRGL_BIND_STAGING ||
       args->bind == VIRGL_BIND_INDEX_BUFFER ||
       args->bind == VIRGL_BIND_STREAM_OUTPUT ||
       args->bind == VIRGL_BIND_VERTEX_BUFFER ||
       args->bind == VIRGL_BIND_CONSTANT_BUFFER ||
       args->bind == VIRGL_BIND_QUERY_BUFFER ||
       args->bind == VIRGL_BIND_COMMAND_ARGS ||
       args->bind == VIRGL_BIND_SHADER_BUFFER) {
      if (args->target != PIPE_BUFFER) {
         snprintf(errmsg, 256, "Buffer bind flags requre the buffer target but this is target %d", args->target);
         return -1;
      }
      if (args->height != 1 || args->depth != 1) {
         snprintf(errmsg, 256, "Buffer target: Got height=%u, depth=%u, expect (1,1)", args->height, args->depth);
         return -1;
      }
      if (args->bind == VIRGL_BIND_QUERY_BUFFER && !has_feature(feat_qbo)) {
         snprintf(errmsg, 256, "Query buffers are not supported");
         return -1;
      }
      if (args->bind == VIRGL_BIND_COMMAND_ARGS && !has_feature(feat_indirect_draw)) {
         snprintf(errmsg, 256, "Command args buffer requested but indirect draw is not supported");
         return -1;
      }
   } else {
      if (!((args->bind & VIRGL_BIND_SAMPLER_VIEW) ||
            (args->bind & VIRGL_BIND_DEPTH_STENCIL) ||
            (args->bind & VIRGL_BIND_RENDER_TARGET) ||
            (args->bind & VIRGL_BIND_CURSOR) ||
            (args->bind & VIRGL_BIND_SHARED) ||
            (args->bind & VIRGL_BIND_LINEAR))) {
         snprintf(errmsg, 256, "Invalid texture bind flags 0x%x", args->bind);
         return -1;
      }

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
      if (!virgl_gbm_gpu_import_required(args->bind)) {
         return 0;
      }
#endif

      if (args->target == PIPE_TEXTURE_2D ||
          args->target == PIPE_TEXTURE_RECT ||
          args->target == PIPE_TEXTURE_CUBE ||
          args->target == PIPE_TEXTURE_2D_ARRAY ||
          args->target == PIPE_TEXTURE_CUBE_ARRAY) {
         if (args->depth != 1) {
            snprintf(errmsg, 256, "2D texture target with depth=%u != 1", args->depth);
            return -1;
         }
         if (format_can_texture_storage && !args->height) {
            snprintf(errmsg, 256, "2D Texture storage requires non-zero height");
            return -1;
         }
      }
      if (args->target == PIPE_TEXTURE_1D ||
          args->target == PIPE_TEXTURE_1D_ARRAY) {
         if (args->height != 1 || args->depth != 1) {
            snprintf(errmsg, 256, "Got height=%u, depth=%u, expect (1,1)",
                     args->height, args->depth);
            return -1;
         }
         if (args->width > vrend_state.max_texture_2d_size) {
            snprintf(errmsg, 256, "1D Texture width (%u) exceeds supported value (%u)",
                     args->width, vrend_state.max_texture_2d_size);
            return -1;
         }
      }

      if (args->target == PIPE_TEXTURE_2D ||
          args->target == PIPE_TEXTURE_RECT ||
          args->target == PIPE_TEXTURE_2D_ARRAY) {
         if (args->width > vrend_state.max_texture_2d_size ||
             args->height > vrend_state.max_texture_2d_size) {
            snprintf(errmsg, 256, "2D Texture size components (%u, %u) exceeds supported value (%u)",
                     args->width, args->height, vrend_state.max_texture_2d_size);
            return -1;
         }
      }

      if (args->target == PIPE_TEXTURE_3D) {
         if (format_can_texture_storage &&
             (!args->height || !args->depth)) {
            snprintf(errmsg, 256, "Texture storage expects non-zero height (%u) and depth (%u)",
                     args->height, args->depth);
            return -1;
         }
         if (args->width > vrend_state.max_texture_3d_size ||
             args->height > vrend_state.max_texture_3d_size ||
             args->depth > vrend_state.max_texture_3d_size) {
            snprintf(errmsg, 256, "3D Texture sizes (%u, %u, %u) exceeds supported value (%u)",
                     args->width, args->height, args->depth,
                     vrend_state.max_texture_3d_size);
            return -1;
         }
      }
      if (args->target == PIPE_TEXTURE_2D_ARRAY ||
          args->target == PIPE_TEXTURE_CUBE_ARRAY ||
          args->target == PIPE_TEXTURE_1D_ARRAY) {
         if (format_can_texture_storage &&
             !args->array_size) {
            snprintf(errmsg, 256, "Texture arrays require a non-zero arrays size "
                                  "when allocated with glTexStorage");
            return -1;
         }
      }
      if (args->target == PIPE_TEXTURE_CUBE ||
          args->target == PIPE_TEXTURE_CUBE_ARRAY) {
         if (args->width != args->height) {
            snprintf(errmsg, 256, "Cube maps require width (%u) == height (%u)",
                     args->width, args->height);
            return -1;
         }
         if (args->width > vrend_state.max_texture_cube_size) {
            snprintf(errmsg, 256, "Cube maps size (%u) exceeds supported value (%u)",
                     args->width, vrend_state.max_texture_cube_size);
            return -1;
         }
      }
   }
   return 0;
}

static void vrend_create_buffer(struct vrend_resource *gr, uint32_t width, uint32_t flags)
{

   GLbitfield buffer_storage_flags = 0;
   if (flags & VIRGL_RESOURCE_FLAG_MAP_PERSISTENT) {
      buffer_storage_flags |= GL_MAP_PERSISTENT_BIT;
      /* Gallium's storage_flags_to_buffer_flags seems to drop some information, but we have to
       * satisfy the following:
       *
       * "If flags contains GL_MAP_PERSISTENT_BIT, it must also contain at least one of
       *  GL_MAP_READ_BIT or GL_MAP_WRITE_BIT."
       */
      buffer_storage_flags |= GL_MAP_READ_BIT | GL_MAP_WRITE_BIT;
   }
   if (flags & VIRGL_RESOURCE_FLAG_MAP_COHERENT)
      buffer_storage_flags |= GL_MAP_COHERENT_BIT;

   gr->storage_bits |= VREND_STORAGE_GL_BUFFER;
   glGenBuffersARB(1, &gr->gl_id);
   glBindBufferARB(gr->target, gr->gl_id);

   if (buffer_storage_flags) {
      if (has_feature(feat_arb_buffer_storage) && !vrend_state.use_external_blob) {
         glBufferStorage(gr->target, width, NULL, buffer_storage_flags);
         gr->map_info = vrend_state.inferred_gl_caching_type;
      }
#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
      else if (has_feature(feat_memory_object_fd) && has_feature(feat_memory_object)) {
         GLuint memobj = 0;
         int fd = -1;
         int ret;

         /* Could use VK too. */
         struct gbm_bo *bo = gbm_bo_create(gbm->device, width, 1,
                                           GBM_FORMAT_R8, GBM_BO_USE_LINEAR);
         if (!bo) {
            virgl_error("Failed to allocate emulated GL buffer backing storage");
            return;
         }

         ret = virgl_gbm_export_fd(gbm->device, gbm_bo_get_handle(bo).u32, &fd);
         if (ret || fd < 0) {
            virgl_error("Failed to get file descriptor\n");
            return;
         }

         glCreateMemoryObjectsEXT(1, &memobj);
         glImportMemoryFdEXT(memobj, width, GL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
         glBufferStorageMemEXT(gr->target, width, memobj, 0);
         gr->gbm_bo = bo;
         gr->memobj = memobj;
         gr->storage_bits |= VREND_STORAGE_GBM_BUFFER | VREND_STORAGE_GL_MEMOBJ;
         gr->map_info = virgl_gbm_get_map_info(bo);
      }
#endif
      else {
         virgl_error("Missing buffer storage and interop extensions\n");
         return;
      }

      gr->storage_bits |= VREND_STORAGE_GL_IMMUTABLE;
      gr->buffer_storage_flags = buffer_storage_flags;
      gr->size = width;
   } else
      glBufferData(gr->target, width, NULL, GL_STREAM_DRAW);

   glBindBufferARB(gr->target, 0);
}

static int
vrend_resource_alloc_buffer(struct vrend_resource *gr, uint32_t flags)
{
   const uint32_t bind = gr->base.bind;
   const uint32_t size = gr->base.width0;

   if (bind == VIRGL_BIND_CUSTOM) {
      /* use iovec directly when attached */
      gr->storage_bits |= VREND_STORAGE_HOST_SYSTEM_MEMORY;
      gr->ptr = calloc(1, size);
      if (!gr->ptr)
         return -ENOMEM;
   } else if (bind == VIRGL_BIND_STAGING) {
     /* staging buffers only use guest memory -- nothing to do. */
   } else if (bind == VIRGL_BIND_INDEX_BUFFER) {
      gr->target = GL_ELEMENT_ARRAY_BUFFER_ARB;
      vrend_create_buffer(gr, size, flags);
   } else if (bind == VIRGL_BIND_STREAM_OUTPUT) {
      gr->target = GL_TRANSFORM_FEEDBACK_BUFFER;
      vrend_create_buffer(gr, size, flags);
   } else if (bind == VIRGL_BIND_VERTEX_BUFFER) {
      gr->target = GL_ARRAY_BUFFER_ARB;
      vrend_create_buffer(gr, size, flags);
   } else if (bind == VIRGL_BIND_CONSTANT_BUFFER) {
      gr->target = GL_UNIFORM_BUFFER;
      vrend_create_buffer(gr, size, flags);
   } else if (bind == VIRGL_BIND_QUERY_BUFFER) {
      gr->target = GL_QUERY_BUFFER;
      vrend_create_buffer(gr, size, flags);
   } else if (bind == VIRGL_BIND_COMMAND_ARGS) {
      gr->target = GL_DRAW_INDIRECT_BUFFER;
      vrend_create_buffer(gr, size, flags);
   } else if (bind == 0 || bind == VIRGL_BIND_SHADER_BUFFER) {
      gr->target = GL_ARRAY_BUFFER_ARB;
      vrend_create_buffer(gr, size, flags);
   } else if (bind & VIRGL_BIND_SAMPLER_VIEW) {
      /*
    * On Desktop we use GL_ARB_texture_buffer_object on GLES we use
    * GL_EXT_texture_buffer (it is in the ANDRIOD extension pack).
    */
#if GL_TEXTURE_BUFFER != GL_TEXTURE_BUFFER_EXT
#error "GL_TEXTURE_BUFFER enums differ, they shouldn't."
#endif

   /* need to check GL version here */
      if (has_feature(feat_arb_or_gles_ext_texture_buffer)) {
         gr->target = GL_TEXTURE_BUFFER;
      } else {
         gr->target = GL_PIXEL_PACK_BUFFER_ARB;
      }
      vrend_create_buffer(gr, size, flags);
   } else {
      virgl_error("%s: Illegal buffer binding flags 0x%x\n", __func__, bind);
      return -EINVAL;
   }

   return 0;
}

static inline void
vrend_renderer_resource_copy_args(const struct vrend_renderer_resource_create_args *args,
                                  struct vrend_resource *gr)
{
   assert(gr);
   assert(args);

   gr->base.bind = args->bind;
   gr->base.width0 = args->width;
   gr->base.height0 = args->height;
   gr->base.depth0 = args->depth;
   gr->base.format = args->format;
   gr->base.target = args->target;
   gr->base.last_level = args->last_level;
   gr->base.nr_samples = args->nr_samples;
   gr->base.array_size = args->array_size;
}

#ifdef WIN32
struct dxgi_format_conversion {
   uint32_t virgl_format;
   DXGI_FORMAT dxgi_format;
};

static bool virgl_format_to_dxgi_format(uint32_t format, DXGI_FORMAT *dxgi)
{
   static const struct dxgi_format_conversion conversions[] = {
      { VIRGL_FORMAT_R8G8B8A8_UNORM, DXGI_FORMAT_R8G8B8A8_UNORM },
      { VIRGL_FORMAT_R8G8B8A8_SRGB, DXGI_FORMAT_R8G8B8A8_UNORM_SRGB },
      { VIRGL_FORMAT_B8G8R8X8_UNORM, DXGI_FORMAT_R8G8B8A8_UNORM }, /*?*/
      { VIRGL_FORMAT_B8G8R8A8_UNORM, DXGI_FORMAT_R8G8B8A8_UNORM }, /*?*/
      { VIRGL_FORMAT_B8G8R8A8_SRGB, DXGI_FORMAT_B8G8R8A8_UNORM_SRGB },
      { VIRGL_FORMAT_R8G8B8A8_UNORM, DXGI_FORMAT_R8G8B8A8_TYPELESS },
      { VIRGL_FORMAT_B8G8R8A8_UNORM, DXGI_FORMAT_B8G8R8A8_TYPELESS },
      { VIRGL_FORMAT_R16G16B16A16_FLOAT, DXGI_FORMAT_R16G16B16A16_FLOAT },
      { VIRGL_FORMAT_R32G32B32A32_FLOAT, DXGI_FORMAT_R32G32B32A32_FLOAT },
      { VIRGL_FORMAT_R10G10B10A2_UNORM, DXGI_FORMAT_R10G10B10A2_UNORM },
      { VIRGL_FORMAT_R8_UNORM, DXGI_FORMAT_R8_UNORM },
      { VIRGL_FORMAT_R16_UNORM, DXGI_FORMAT_R16_UNORM },
      { VIRGL_FORMAT_R8G8_UNORM, DXGI_FORMAT_R8G8_UNORM },
      { VIRGL_FORMAT_R16G16_UNORM, DXGI_FORMAT_R16G16_UNORM },
      { VIRGL_FORMAT_NV12, DXGI_FORMAT_NV12 },
      { VIRGL_FORMAT_P010, DXGI_FORMAT_P010 },
      { VIRGL_FORMAT_P016, DXGI_FORMAT_P016 },
   };

    for (uint32_t i = 0; i < ARRAY_SIZE(conversions); i++) {
       if (conversions[i].virgl_format == format) {
          *dxgi = conversions[i].dxgi_format;
          return true;
       }
    }

   return false;
}

static unsigned virgl_bind_to_d3d_bind_flags(uint32_t flags)
{
   unsigned ret = 0;

   if (flags & PIPE_BIND_VERTEX_BUFFER)
      ret |= D3D11_BIND_VERTEX_BUFFER;
   if (flags & PIPE_BIND_INDEX_BUFFER)
      ret |= D3D11_BIND_INDEX_BUFFER;
   if (flags & PIPE_BIND_CONSTANT_BUFFER)
      ret |= D3D11_BIND_CONSTANT_BUFFER;
   if (flags & PIPE_BIND_SHADER_RESOURCE)
      ret |= D3D11_BIND_SHADER_RESOURCE;
   if (flags & PIPE_BIND_STREAM_OUTPUT)
      ret |= D3D11_BIND_STREAM_OUTPUT;
   if (flags & PIPE_BIND_RENDER_TARGET)
      ret |= D3D11_BIND_RENDER_TARGET;
   if (flags & PIPE_BIND_DEPTH_STENCIL)
      ret |= D3D11_BIND_DEPTH_STENCIL;

   return ret;
}

static unsigned virgl_usage_to_d3d_usage(uint32_t usage)
{
   switch (usage) {
   case PIPE_USAGE_DEFAULT:
      return D3D11_USAGE_DEFAULT;
   case PIPE_USAGE_IMMUTABLE:
      return D3D11_USAGE_IMMUTABLE;
   case PIPE_USAGE_DYNAMIC:
      return D3D11_USAGE_DYNAMIC;
   case PIPE_USAGE_STAGING:
      return D3D11_USAGE_STAGING;
   case PIPE_USAGE_STREAM:
      return D3D11_USAGE_DEFAULT;
   }

   return D3D11_USAGE_DEFAULT;
}

static bool vrend_resource_d3d_acquire(ID3D11Texture2D* d3d_tex2d)
{
   IDXGIKeyedMutex* dxgiMutex = NULL;
   HRESULT hr;

   hr = d3d_tex2d->lpVtbl->QueryInterface(d3d_tex2d,
                                          &IID_IDXGIKeyedMutex, (void**)&dxgiMutex);
   if (FAILED(hr))
      return false;

   hr = dxgiMutex->lpVtbl->AcquireSync(dxgiMutex, 0, INFINITE);

   dxgiMutex->lpVtbl->Release(dxgiMutex);

   return SUCCEEDED(hr);
}
#endif

/*
 * When using ANGLE/D3D, this function creates a D3D Texture and
 * EGL image given certain flags.
 */
static void vrend_resource_d3d_init(UNUSED struct vrend_resource *gr, UNUSED uint32_t format)
{
#if defined(WIN32) && defined(HAVE_EPOXY_EGL_H)
   D3D11_TEXTURE2D_DESC desc = {
      .Width = gr->base.width0,
      .Height = gr->base.height0,
      .MipLevels = 1,
      .ArraySize = 1,
      .SampleDesc = { .Count = 1 },
   };
   ID3D11Texture2D* d3d_tex2d = NULL;

   if (!vrend_state.d3d_share_texture)
      return;

   if ((gr->base.bind & VIRGL_RES_BIND_SCANOUT) == 0)
      return;

   if (gr->base.depth0 != 1 || gr->base.last_level != 0 || gr->base.nr_samples > 1)
      return;

   if (!virgl_format_to_dxgi_format(format, &desc.Format))
      return;

   desc.BindFlags = virgl_bind_to_d3d_bind_flags(gr->base.bind);
   desc.BindFlags |= D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE;
   desc.Usage = virgl_usage_to_d3d_usage(gr->base.usage);
   desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED_NTHANDLE | D3D11_RESOURCE_MISC_SHARED_KEYEDMUTEX;

   if (!virgl_egl_win32_create_d3d11_texture2d(egl, &desc, &d3d_tex2d))
      goto fail;

   if (!vrend_resource_d3d_acquire(d3d_tex2d))
      goto fail;

   gr->egl_image = virgl_egl_win32_image_from_d3d11_texture2d(egl, d3d_tex2d);
   if (!gr->egl_image)
      goto fail;

   gr->d3d_tex2d = d3d_tex2d;

   gr->storage_bits |= VREND_STORAGE_D3D_TEXTURE;
   gr->storage_bits |= VREND_STORAGE_EGL_IMAGE;
   return;

fail:
   if (d3d_tex2d)
      d3d_tex2d->lpVtbl->Release(gr->d3d_tex2d);
   gr->d3d_tex2d = NULL;
#endif
}

/*
 * When GBM allocation is enabled, this function creates a GBM buffer and
 * EGL image given certain flags.
 */
static void vrend_resource_gbm_init(struct vrend_resource *gr, uint32_t format)
{
#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   uint32_t gbm_flags = virgl_gbm_convert_flags(gr->base.bind);
   uint32_t gbm_format = 0;
   if (virgl_gbm_convert_format(&format, &gbm_format))
      return;
   if (vrend_winsys_different_gpu())
      gbm_flags |= GBM_BO_USE_LINEAR;

   if (gr->base.depth0 != 1 || gr->base.last_level != 0 || gr->base.nr_samples > 1)
      return;

   if (!gbm || !gbm->device || !gbm_format || !gbm_flags)
      return;

   if (!virgl_gbm_external_allocation_preferred(gr->base.bind))
      return;

#if !defined(MINIGBM)
   /*
    * GBM allocation may be less optimal compared to a regular GL allocation.
    * Skip GBM allocation when we don't actually need it.
    */
   if (!vrend_state.gbm_layout_feat)
      return;

   /*
    * Kernel virtio-gpu driver doesn't support modifiers other than linear and
    * venus needs a real modifier that backs GBM buffer when it imports vrend
    * resource. Linear allocation is needed when venus works in KMS mode on guest
    * and it wants to present vrend resources.
    */
   if (gr->base.bind & VIRGL_BIND_SHARED)
      gbm_flags |= GBM_BO_USE_LINEAR;
#endif

   if (!gbm_device_is_format_supported(gbm->device, gbm_format, gbm_flags))
      return;

   struct gbm_bo *bo = gbm_bo_create(gbm->device, gr->base.width0, gr->base.height0,
                                     gbm_format, gbm_flags);
   if (!bo)
      return;

   gr->gbm_bo = bo;
   gr->storage_bits |= VREND_STORAGE_GBM_BUFFER;

   gr->map_info = virgl_gbm_get_map_info(bo);

   if (!virgl_gbm_gpu_import_required(gr->base.bind))
      return;

   gr->egl_image = virgl_egl_image_from_gbm_bo(egl, bo);
   if (!gr->egl_image) {
      gr->gbm_bo = NULL;
      gbm_bo_destroy(bo);
      return;
   }

   gr->storage_bits |= VREND_STORAGE_EGL_IMAGE;

#else
   (void)format;
   (void)gr;
#endif
}

static int vrend_resource_alloc_texture(struct vrend_resource *gr,
                                        enum virgl_formats format,
                                        void *image_oes)
{
   unsigned level;
   GLenum internalformat, glformat, gltype;
   struct vrend_texture *gt = (struct vrend_texture *)gr;
   struct pipe_resource *pr = &gr->base;

   const bool format_can_texture_storage = has_feature(feat_texture_storage) &&
        (tex_conv_table[format].flags & VIRGL_TEXTURE_CAN_TEXTURE_STORAGE);

   if (format_can_texture_storage)
      gr->storage_bits |= VREND_STORAGE_GL_IMMUTABLE;

   if (!image_oes) {
      vrend_resource_d3d_init(gr, format);
      vrend_resource_gbm_init(gr, format);
      if (gr->gbm_bo && !has_bit(gr->storage_bits, VREND_STORAGE_EGL_IMAGE))
         return 0;

      image_oes = gr->egl_image;
   }

   gr->target = tgsitargettogltarget(pr->target, pr->nr_samples);
   gr->storage_bits |= VREND_STORAGE_GL_TEXTURE;

   /* ugly workaround for texture rectangle incompatibility */
   if (gr->target == GL_TEXTURE_RECTANGLE_NV &&
       !(tex_conv_table[format].flags & VIRGL_TEXTURE_CAN_TARGET_RECTANGLE)) {
      /* for some guests this is the only usage of rect */
      if (pr->width0 != 1 || pr->height0 != 1) {
         virgl_warn("Specifying format incompatible with GL_TEXTURE_RECTANGLE_NV\n");
      }
      gr->target = GL_TEXTURE_2D;
   }

   /* fallback for 1D textures */
   if (vrend_state.use_gles && gr->target == GL_TEXTURE_1D) {
      gr->target = GL_TEXTURE_2D;
   }

   /* fallback for 1D array textures */
   if (vrend_state.use_gles && gr->target == GL_TEXTURE_1D_ARRAY) {
      gr->target = GL_TEXTURE_2D_ARRAY;
   }

   glGenTextures(1, &gr->gl_id);
   glBindTexture(gr->target, gr->gl_id);

   debug_texture(__func__, gr);

   if (image_oes) {
      if (has_bit(gr->storage_bits, VREND_STORAGE_GL_IMMUTABLE) &&
          has_feature(feat_egl_image_storage)) {
         glEGLImageTargetTexStorageEXT(gr->target, (GLeglImageOES) image_oes, NULL);
      } else if (has_feature(feat_egl_image)) {
         gr->storage_bits &= ~VREND_STORAGE_GL_IMMUTABLE;
         assert(gr->target == GL_TEXTURE_2D);
         glEGLImageTargetTexture2DOES(gr->target, (GLeglImageOES) image_oes);
         if ((format == VIRGL_FORMAT_NV12 ||
              format == VIRGL_FORMAT_NV21 ||
              format == VIRGL_FORMAT_YV12 ||
              format == VIRGL_FORMAT_P010) && glGetError() != GL_NO_ERROR) {
            virgl_warn("glEGLImageTargetTexture2DOES maybe fail\n");
         }
      } else {
         virgl_error("Missing GL_OES_EGL_image extensions\n");
         glBindTexture(gr->target, 0);
         return EINVAL;
      }
      gr->storage_bits |= VREND_STORAGE_EGL_IMAGE;
   } else {
      internalformat = tex_conv_table[format].internalformat;
      glformat = tex_conv_table[format].glformat;
      gltype = tex_conv_table[format].gltype;

      if (internalformat == 0) {
         virgl_error("Unknown format is %d\n", pr->format);
         glBindTexture(gr->target, 0);
         return EINVAL;
      }

      if (pr->nr_samples > 1) {
         if (format_can_texture_storage) {
            if (gr->target == GL_TEXTURE_2D_MULTISAMPLE) {
               glTexStorage2DMultisample(gr->target, pr->nr_samples,
                                         internalformat, pr->width0, pr->height0,
                                         GL_TRUE);
            } else {
               glTexStorage3DMultisample(gr->target, pr->nr_samples,
                                         internalformat, pr->width0, pr->height0, pr->array_size,
                                         GL_TRUE);
            }
         } else {
            if (gr->target == GL_TEXTURE_2D_MULTISAMPLE) {
               glTexImage2DMultisample(gr->target, pr->nr_samples,
                                       internalformat, pr->width0, pr->height0,
                                       GL_TRUE);
            } else {
               glTexImage3DMultisample(gr->target, pr->nr_samples,
                                       internalformat, pr->width0, pr->height0, pr->array_size,
                                       GL_TRUE);
            }
         }
      } else if (gr->target == GL_TEXTURE_CUBE_MAP) {
            int i;
            if (format_can_texture_storage)
               glTexStorage2D(GL_TEXTURE_CUBE_MAP, pr->last_level + 1, internalformat, pr->width0, pr->height0);
            else {
               for (i = 0; i < 6; i++) {
                  GLenum ctarget = GL_TEXTURE_CUBE_MAP_POSITIVE_X + i;
                  for (level = 0; level <= pr->last_level; level++) {
                     unsigned mwidth = u_minify(pr->width0, level);
                     unsigned mheight = u_minify(pr->height0, level);

                     glTexImage2D(ctarget, level, internalformat, mwidth, mheight, 0, glformat,
                                  gltype, NULL);
                  }
               }
            }
      } else if (gr->target == GL_TEXTURE_3D ||
                 gr->target == GL_TEXTURE_2D_ARRAY ||
                 gr->target == GL_TEXTURE_CUBE_MAP_ARRAY) {
         if (format_can_texture_storage) {
            unsigned depth_param = (gr->target == GL_TEXTURE_2D_ARRAY || gr->target == GL_TEXTURE_CUBE_MAP_ARRAY) ?
                                      pr->array_size : pr->depth0;
            glTexStorage3D(gr->target, pr->last_level + 1, internalformat, pr->width0, pr->height0, depth_param);
         } else {
            for (level = 0; level <= pr->last_level; level++) {
               unsigned depth_param = (gr->target == GL_TEXTURE_2D_ARRAY || gr->target == GL_TEXTURE_CUBE_MAP_ARRAY) ?
                                         pr->array_size : u_minify(pr->depth0, level);
               unsigned mwidth = u_minify(pr->width0, level);
               unsigned mheight = u_minify(pr->height0, level);
               glTexImage3D(gr->target, level, internalformat, mwidth, mheight,
                            depth_param, 0, glformat, gltype, NULL);
            }
         }
      } else if (gr->target == GL_TEXTURE_1D && vrend_state.use_gles) {
         report_gles_missing_func(NULL, "glTexImage1D");
      } else if (gr->target == GL_TEXTURE_1D) {
         if (format_can_texture_storage) {
            glTexStorage1D(gr->target, pr->last_level + 1, internalformat, pr->width0);
         } else {
            for (level = 0; level <= pr->last_level; level++) {
               unsigned mwidth = u_minify(pr->width0, level);
               glTexImage1D(gr->target, level, internalformat, mwidth, 0,
                            glformat, gltype, NULL);
            }
         }
      } else {
         if (format_can_texture_storage)
            glTexStorage2D(gr->target, pr->last_level + 1, internalformat, pr->width0,
                           gr->target == GL_TEXTURE_1D_ARRAY ? pr->array_size : pr->height0);
         else {
            for (level = 0; level <= pr->last_level; level++) {
               unsigned mwidth = u_minify(pr->width0, level);
               unsigned mheight = u_minify(pr->height0, level);
               glTexImage2D(gr->target, level, internalformat, mwidth,
                            gr->target == GL_TEXTURE_1D_ARRAY ? pr->array_size : mheight,
                            0, glformat, gltype, NULL);
            }
         }
      }
   }

   if (!format_can_texture_storage) {
      glTexParameteri(gr->target, GL_TEXTURE_BASE_LEVEL, 0);
      glTexParameteri(gr->target, GL_TEXTURE_MAX_LEVEL, pr->last_level);
   }

   glBindTexture(gr->target, 0);

   if (image_oes && gr->gbm_bo) {
#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
      if (!has_bit(gr->storage_bits, VREND_STORAGE_GL_BUFFER) &&
            !vrend_format_can_texture_view(gr->base.format)) {
         for (int i = 0; i < gbm_bo_get_plane_count(gr->gbm_bo); i++) {
            gr->aux_plane_egl_image[i] =
                  virgl_egl_aux_plane_image_from_gbm_bo(egl, gr->gbm_bo, i);
         }
      }
#endif
   }

   gt->state.max_lod = -1;
   gt->cur_swizzle[0] = gt->cur_swizzle[1] = gt->cur_swizzle[2] = gt->cur_swizzle[3] = -1;
   gt->cur_base = -1;
   gt->cur_max = 10000;
   return 0;
}

static struct vrend_resource *
vrend_resource_create(const struct vrend_renderer_resource_create_args *args)
{
   struct vrend_resource *gr;
   int ret;
   char error_string[256];

   ret = check_resource_valid(args, error_string);
   if (ret) {
      virgl_error("%s, Illegal resource parameters, error: %s\n", __func__, error_string);
      return NULL;
   }

   gr = (struct vrend_resource *)CALLOC_STRUCT(vrend_texture);
   if (!gr)
      return NULL;

   vrend_renderer_resource_copy_args(args, gr);
   gr->storage_bits = VREND_STORAGE_GUEST_MEMORY;

   if (args->flags & VIRGL_RESOURCE_Y_0_TOP)
      gr->y_0_top = true;

   pipe_reference_init(&gr->base.reference, 1);

   return gr;
}

struct pipe_resource *
vrend_renderer_resource_create(const struct vrend_renderer_resource_create_args *args,
                               void *image_oes)
{
   struct vrend_resource *gr;
   int ret;

   gr = vrend_resource_create(args);
   if (!gr)
      return NULL;

   if (args->target == PIPE_BUFFER) {
      ret = vrend_resource_alloc_buffer(gr, args->flags);
   } else {
      const enum virgl_formats format = gr->base.format;
      ret = vrend_resource_alloc_texture(gr, format, image_oes);
   }

   if (ret) {
      FREE(gr);
      return NULL;
   }

   return &gr->base;
}

void vrend_renderer_resource_destroy(struct vrend_resource *res)
{
   if (has_bit(res->storage_bits, VREND_STORAGE_GL_TEXTURE)) {
      glDeleteTextures(1, &res->gl_id);
   } else if (has_bit(res->storage_bits, VREND_STORAGE_GL_BUFFER)) {
      glDeleteBuffers(1, &res->gl_id);
      if (res->tbo_tex_id)
         glDeleteTextures(1, &res->tbo_tex_id);
   } else if (has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) {
      free(res->ptr);
   }

   if (res->rbo_id) {
      glDeleteRenderbuffers(1, &res->rbo_id);
   }

   if (has_bit(res->storage_bits, VREND_STORAGE_GL_MEMOBJ)) {
      glDeleteMemoryObjectsEXT(1, &res->memobj);
   }

#ifdef ENABLE_GBM
   if (res->egl_image) {
      virgl_egl_image_destroy(egl, res->egl_image);
      for (unsigned i = 0; i < ARRAY_SIZE(res->aux_plane_egl_image); i++) {
         if (res->aux_plane_egl_image[i]) {
            virgl_egl_image_destroy(egl, res->aux_plane_egl_image[i]);
         }
      }
   }
#endif
#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   if (res->gbm_bo)
      gbm_bo_destroy(res->gbm_bo);
#endif
#ifdef WIN32
   if (res->d3d_tex2d)
      res->d3d_tex2d->lpVtbl->Release(res->d3d_tex2d);
#endif
   free(res);
}

struct virgl_sub_upload_data {
   GLenum target;
   struct pipe_box *box;
};

static void iov_buffer_upload(void *cookie, uint32_t doff, void *src, int len)
{
   struct virgl_sub_upload_data *d = cookie;
   glBufferSubData(d->target, d->box->x + doff, len, src);
}

static void vrend_scale_depth(void *ptr, int size, float scale_val)
{
   GLuint *ival = ptr;
   const GLfloat myscale = 1.0f / 0xffffff;
   int i;
   for (i = 0; i < size / 4; i++) {
      GLuint value = ival[i];
      GLfloat d = ((float)(value >> 8) * myscale) * scale_val;
      d = CLAMP(d, 0.0F, 1.0F);
      ival[i] = (GLuint)(d / myscale) << 8;
   }
}

static void read_transfer_data(const struct iovec *iov,
                               unsigned int num_iovs,
                               char *data,
                               enum virgl_formats format,
                               uint64_t offset,
                               uint32_t src_stride,
                               uint32_t src_layer_stride,
                               struct pipe_box *box,
                               bool invert)
{
   int blsize = util_format_get_blocksize(format);
   uint32_t size = vrend_get_iovec_size(iov, num_iovs);
   uint32_t send_size = util_format_get_nblocks(format, box->width,
                                              box->height) * blsize * box->depth;
   uint32_t bwx = util_format_get_nblocksx(format, box->width) * blsize;
   int32_t bh = util_format_get_nblocksy(format, box->height);
   int d, h;

   if ((send_size == size || bh == 1) && !invert && box->depth == 1)
      vrend_read_from_iovec(iov, num_iovs, offset, data, send_size);
   else {
      if (invert) {
         for (d = 0; d < box->depth; d++) {
            uint32_t myoffset = offset + d * src_layer_stride;
            for (h = bh - 1; h >= 0; h--) {
               void *ptr = data + (h * bwx) + d * (bh * bwx);
               vrend_read_from_iovec(iov, num_iovs, myoffset, ptr, bwx);
               myoffset += src_stride;
            }
         }
      } else {
         for (d = 0; d < box->depth; d++) {
            uint32_t myoffset = offset + d * src_layer_stride;
            for (h = 0; h < bh; h++) {
               void *ptr = data + (h * bwx) + d * (bh * bwx);
               vrend_read_from_iovec(iov, num_iovs, myoffset, ptr, bwx);
               myoffset += src_stride;
            }
         }
      }
   }
}

static void write_transfer_data(struct pipe_resource *res,
                                const struct iovec *iov,
                                unsigned num_iovs,
                                char *data,
                                uint32_t dst_stride,
                                struct pipe_box *box,
                                uint32_t level,
                                uint64_t offset,
                                bool invert)
{
   int blsize = util_format_get_blocksize(res->format);
   uint32_t size = vrend_get_iovec_size(iov, num_iovs);
   uint32_t send_size = util_format_get_nblocks(res->format, box->width,
                                                box->height) * blsize * box->depth;
   uint32_t bwx = util_format_get_nblocksx(res->format, box->width) * blsize;
   int32_t bh = util_format_get_nblocksy(res->format, box->height);
   int d, h;
   uint32_t stride = dst_stride ? dst_stride : util_format_get_nblocksx(res->format, u_minify(res->width0, level)) * blsize;

   if ((send_size == size || bh == 1) && !invert && box->depth == 1) {
      vrend_write_to_iovec(iov, num_iovs, offset, data, send_size);
   } else if (invert) {
      for (d = 0; d < box->depth; d++) {
         uint32_t myoffset = offset + d * stride * u_minify(res->height0, level);
         for (h = bh - 1; h >= 0; h--) {
            void *ptr = data + (h * bwx) + d * (bh * bwx);
            vrend_write_to_iovec(iov, num_iovs, myoffset, ptr, bwx);
            myoffset += stride;
         }
      }
   } else {
      for (d = 0; d < box->depth; d++) {
         uint32_t myoffset = offset + d * stride * u_minify(res->height0, level);
         for (h = 0; h < bh; h++) {
            void *ptr = data + (h * bwx) + d * (bh * bwx);
            vrend_write_to_iovec(iov, num_iovs, myoffset, ptr, bwx);
            myoffset += stride;
         }
      }
   }
}

static bool check_transfer_iovec(struct vrend_resource *res,
                                 const struct vrend_transfer_info *info)
{
   return (info->iovec && info->iovec_cnt) || res->iov;
}

static inline bool resource_contains_box(struct vrend_resource *res,
                                         const struct pipe_box *box,
                                         uint32_t level)
{
   int64_t end_x, end_y, end_z;
   int64_t width, height, depth;

   /* check mipmap level is in bounds */
   if (unlikely(level > res->base.last_level))
      return false;

   width = u_minify(res->base.width0, level);
   height = u_minify(res->base.height0, level);

   /* The z value has two meanings depending of the texture type */
   switch (res->base.target) {
   case PIPE_TEXTURE_CUBE:
   case PIPE_TEXTURE_1D_ARRAY:
   case PIPE_TEXTURE_2D_ARRAY:
   case PIPE_TEXTURE_CUBE_ARRAY:
      depth = res->base.array_size;
      break;
   case PIPE_TEXTURE_3D:
      depth = u_minify(res->base.depth0, level);
      break;
   default:
      depth = 1;
      break;
   }

   /* check that the starting point is not outside of the range */
   if (unlikely(box->x < 0 || box->y < 0 || box->z < 0 ||
                box->x > width || box->y > height || box->z > depth))
      return false;

   end_x = (int64_t) box->x + (int64_t) box->width;
   end_y = (int64_t) box->y + (int64_t) box->height;
   end_z = (int64_t) box->z + (int64_t) box->depth;

   /* check that the end point is not outside of the range */
   if (unlikely(end_x < 0 || end_y < 0 || end_z < 0 ||
                end_x > width || end_y > height  || end_z > depth ))
      return false;

   return true;
}

/* Calculate the size of the memory needed to hold all the data of a
 * transfer for particular stride values.
 */
static uint64_t vrend_transfer_size(struct vrend_resource *vres,
                                    const struct vrend_transfer_info *info,
                                    uint32_t stride, uint32_t layer_stride)
{
   struct pipe_resource *pres = &vres->base;
   struct pipe_box *box = info->box;
   uint64_t size;
   /* For purposes of size calculation, assume that invalid dimension values
    * correspond to 1.
    */
   int w = box->width > 0 ? box->width : 1;
   int h = box->height > 0 ? box->height : 1;
   uint64_t d = box->depth > 0 ? box->depth : 1;
   uint64_t nblocksx = util_format_get_nblocksx(pres->format, w);
   uint64_t nblocksy = util_format_get_nblocksy(pres->format, h);

   /* Calculate the box size, not including the last layer. The last layer
    * is the only one which may be incomplete, and is the only layer for
    * non 3d/2d-array formats.
    */
   size = (d - 1) * layer_stride;
   /* Calculate the size of the last (or only) layer, not including the last
    * block row. The last block row is the only one which may be incomplete and
    * is the only block row for non 2d/1d-array formats.
    */
   size += (nblocksy - 1) * stride;
   /* Calculate the size of the the last (or only) block row. */
   size += nblocksx * util_format_get_blocksize(pres->format);

   return size;
}

static bool check_iov_bounds(struct vrend_resource *res,
                             const struct vrend_transfer_info *info,
                             const struct iovec *iov, int num_iovs)
{
   GLuint transfer_size;
   GLuint iovsize = vrend_get_iovec_size(iov, num_iovs);
   GLuint valid_stride, valid_layer_stride;

   /* If the transfer specifies a stride, verify that it's at least as large as
    * the minimum required for the transfer. If no stride is specified use the
    * image stride for the specified level.
    */
   if (info->stride) {
      GLuint min_stride = util_format_get_stride(res->base.format, info->box->width);
      if (info->stride < min_stride)
         return false;
      valid_stride = info->stride;
   } else {
      valid_stride = util_format_get_stride(res->base.format,
                                            u_minify(res->base.width0, info->level));
   }

   /* If the transfer specifies a layer_stride, verify that it's at least as
    * large as the minimum required for the transfer. If no layer_stride is
    * specified use the image layer_stride for the specified level.
    */
   if (info->layer_stride) {
      GLuint min_layer_stride = util_format_get_2d_size(res->base.format,
                                                        valid_stride,
                                                        info->box->height);
      if (info->layer_stride < min_layer_stride)
         return false;
      valid_layer_stride = info->layer_stride;
   } else {
      valid_layer_stride =
         util_format_get_2d_size(res->base.format, valid_stride,
                                 u_minify(res->base.height0, info->level));
   }

   /* Calculate the size required for the transferred data, based on the
    * calculated or provided strides, and ensure that the iov, starting at the
    * specified offset, is able to hold at least that size.
    */
   transfer_size = vrend_transfer_size(res, info,
                                       valid_stride,
                                       valid_layer_stride);
   if (iovsize < info->offset)
      return false;
   if (iovsize < transfer_size)
      return false;
   if (iovsize < info->offset + transfer_size)
      return false;

   return true;
}

static void vrend_swizzle_data_bgra(uint64_t size, void *data) {
   const size_t bpp = 4;
   const size_t num_pixels = size / bpp;
   for (size_t i = 0; i < num_pixels; ++i) {
      unsigned char *pixel = ((unsigned char*)data) + i * bpp;
      unsigned char first  = *pixel;
      *pixel = *(pixel + 2);
      *(pixel + 2) = first;
   }
}

static void vrend_swizzle_and_collapse_data_bgrx(uint64_t size, void *data) {
   const size_t in_bpp = 4;
   const size_t out_bpp = 3;

   uint8_t *in_pixel, *out_pixel;
   in_pixel = out_pixel = data;

   // in-place modification, so output cursor must not lead
   assert(in_bpp >= out_bpp);

   uint8_t r, g, b;
   const size_t num_pixels = size / in_bpp;
   for (size_t i = 0; i < num_pixels; ++i) {
      b = *(in_pixel + 0);
      g = *(in_pixel + 1);
      r = *(in_pixel + 2);

      *(out_pixel + 0) = r;
      *(out_pixel + 1) = g;
      *(out_pixel + 2) = b;

      in_pixel += in_bpp;
      out_pixel += out_bpp;
   }
}

static void vrend_collapse_data_r8g8b8x8(uint64_t size, void *data) {
   const size_t in_bpp = 4;
   const size_t out_bpp = 3;

   uint8_t *in_pixel, *out_pixel;
   in_pixel = out_pixel = data;

   // in-place modification, so output cursor must not lead
   assert(in_bpp >= out_bpp);

   const size_t num_pixels = size / in_bpp;
   for (size_t i = 0; i < num_pixels; ++i) {
      *(out_pixel + 0) = *(in_pixel + 0);
      *(out_pixel + 1) = *(in_pixel + 1);
      *(out_pixel + 2) = *(in_pixel + 2);

      in_pixel += in_bpp;
      out_pixel += out_bpp;
   }
}

static void vrend_collapse_data_r16g16b16x16(uint64_t size, void *data) {
   const size_t in_channels = 4;
   const size_t out_channels = 3;

   uint16_t *in_pixel, *out_pixel;
   in_pixel = out_pixel = data;

   // in-place modification, so output cursor must not lead
   assert(in_channels >= out_channels);

   const size_t num_pixels = size / in_channels / 2;
   for (size_t i = 0; i < num_pixels; ++i) {
      *(out_pixel + 0) = *(in_pixel + 0);
      *(out_pixel + 1) = *(in_pixel + 1);
      *(out_pixel + 2) = *(in_pixel + 2);

      in_pixel += in_channels;
      out_pixel += out_channels;
   }
}

static int vrend_renderer_transfer_write_iov(struct vrend_context *ctx,
                                             struct vrend_resource *res,
                                             const struct iovec *iov, int num_iovs,
                                             const struct vrend_transfer_info *info)
{
   void *data;

   if ((is_only_bit(res->storage_bits, VREND_STORAGE_GUEST_MEMORY) ||
       has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) && res->iov) {
      return vrend_copy_iovec(iov, num_iovs, info->offset,
                              res->iov, res->num_iovs, info->box->x,
                              info->box->width, res->ptr);
   }

   if (has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) {
      assert(!res->iov);
      vrend_read_from_iovec(iov, num_iovs, info->offset,
                            res->ptr + info->box->x, info->box->width);
      return 0;
   }

   if (has_bit(res->storage_bits, VREND_STORAGE_GL_BUFFER)) {
      GLuint map_flags = GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_WRITE_BIT;
      struct virgl_sub_upload_data d;
      d.box = info->box;
      d.target = res->target;

      if (!info->synchronized)
         map_flags |= GL_MAP_UNSYNCHRONIZED_BIT;

      glBindBufferARB(res->target, res->gl_id);
      data = glMapBufferRange(res->target, info->box->x, info->box->width, map_flags);
      if (data == NULL) {
         virgl_error("Map failed for element buffer\n");
         vrend_read_from_iovec_cb(iov, num_iovs, info->offset, info->box->width, &iov_buffer_upload, &d);
      } else {
         vrend_read_from_iovec(iov, num_iovs, info->offset, data, info->box->width);
         glUnmapBuffer(res->target);
      }
      glBindBufferARB(res->target, 0);
   } else {
      GLenum glformat;
      GLenum gltype;
      int need_temp = 0;
      int elsize = util_format_get_blocksize(res->base.format);
      int x = 0, y = 0;
      bool compressed;
      bool invert = false;
      float depth_scale;
      uint64_t send_size = 0;
      uint32_t stride = info->stride;
      uint32_t layer_stride = info->layer_stride;

      vrend_use_program(NULL);

      if (!stride)
         stride = util_format_get_nblocksx(res->base.format, u_minify(res->base.width0, info->level)) * elsize;

      if (!layer_stride)
         layer_stride = util_format_get_2d_size(res->base.format, stride,
                                                u_minify(res->base.height0, info->level));

      compressed = util_format_is_compressed(res->base.format);
      if (num_iovs > 1 || compressed) {
         need_temp = true;
      }

      if (vrend_state.use_gles && vrend_format_is_bgra(res->base.format))
         need_temp = true;

      if (vrend_state.use_core_profile == true &&
          (res->y_0_top || (res->base.format == VIRGL_FORMAT_Z24X8_UNORM))) {
         need_temp = true;
         if (res->y_0_top)
            invert = true;
      }

      send_size = util_format_get_nblocks(res->base.format, info->box->width,
                                          info->box->height);
      send_size *= elsize;

      if (res->target == GL_TEXTURE_3D ||
          res->target == GL_TEXTURE_1D_ARRAY ||
          res->target == GL_TEXTURE_2D_ARRAY ||
          res->target == GL_TEXTURE_2D_MULTISAMPLE_ARRAY ||
          res->target == GL_TEXTURE_CUBE_MAP_ARRAY)
          send_size *= info->box->depth;
      else if (need_temp && info->box->depth != 1)
         return EINVAL;

      if (need_temp) {
         /* functions like glCompressedTexSubImage3D only support
          * a buffer size of GLsizei = uint32_t, anything larger
          * is bogous */
         if (send_size > UINT_MAX) {
            virgl_error("Used write size out of range %"PRIu64"\n", send_size);
            return EINVAL;
         }

         data = malloc(send_size);
         if (!data) {
            virgl_error("Memory allocation failed for %"PRIu64"\n", send_size);
            return ENOMEM;
         }
         read_transfer_data(iov, num_iovs, data, res->base.format, info->offset,
                            stride, layer_stride, info->box, invert);
      } else {
         if (send_size > iov[0].iov_len - info->offset)
            return EINVAL;
         data = (char*)iov[0].iov_base + info->offset;
      }

      if (!need_temp) {
         assert(stride);
         glPixelStorei(GL_UNPACK_ROW_LENGTH, stride / elsize);
         glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, layer_stride / stride);
      } else
         glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);

      switch (elsize) {
      case 1:
      case 3:
         glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
         break;
      case 2:
      case 6:
         glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
         break;
      case 4:
      default:
         glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
         break;
      case 8:
         glPixelStorei(GL_UNPACK_ALIGNMENT, 8);
         break;
      }

      glformat = tex_conv_table[res->base.format].glformat;
      gltype = tex_conv_table[res->base.format].gltype;

      if ((!vrend_state.use_core_profile) && (res->y_0_top)) {
         GLuint buffers;
         GLuint fb_id;

         glGenFramebuffers(1, &fb_id);
         glBindFramebuffer(GL_FRAMEBUFFER, fb_id);
         vrend_fb_bind_texture(res, 0, info->level, 0);

         buffers = GL_COLOR_ATTACHMENT0;
         glDrawBuffers(1, &buffers);
         glDisable(GL_BLEND);

         vrend_depth_test_enable(ctx->sub, false);
         vrend_alpha_test_enable(ctx->sub, false);
         vrend_stencil_test_enable(ctx->sub, false);

         glPixelZoom(1.0f, res->y_0_top ? -1.0f : 1.0f);
         glWindowPos2i(info->box->x, res->y_0_top ? (int)res->base.height0 - info->box->y : info->box->y);
         glDrawPixels(info->box->width, info->box->height, glformat, gltype,
                      data);
         glDeleteFramebuffers(1, &fb_id);
      } else {
         uint32_t comp_size;
         glBindTexture(res->target, res->gl_id);

         if (compressed) {
            glformat = tex_conv_table[res->base.format].internalformat;
            comp_size = util_format_get_nblocks(res->base.format, info->box->width,
                                                info->box->height) * util_format_get_blocksize(res->base.format);
         }

         if (glformat == 0) {
            glformat = GL_BGRA;
            gltype = GL_UNSIGNED_BYTE;
         }

         x = info->box->x;
         y = invert ? (int)res->base.height0 - info->box->y - info->box->height : info->box->y;

         /* GLES doesn't allow format conversions, which we need for BGRA resources with RGBA
          * internal format. So we fallback to performing a CPU swizzle before uploading. */
         if (vrend_state.use_gles) {
            if (vrend_resource_get_internal_format_override(res) != GL_NONE) {
               glformat = GL_RGB;
               glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
               glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

               if (vrend_format_is_bgra(res->base.format)) {
                  /* to make matters worse, if the resource is actually backed by a 24bpp memory
                   * layout, but the sent data is 32bpp (with ignored alpha), then we must:
                *   - swizzle r/b channels
                *   - collapse to 24bpp
                * To do so, we reconfigure the unpack params and perform in-place modification of
                * the sent data (and perform the inverse on the readback path).
                */
                  VREND_DEBUG(dbg_bgra, ctx, "manually swizzling+collapsing bgrx(32bpp)->rgb(24bpp) on upload since gles+bgrx\n");
                  vrend_swizzle_and_collapse_data_bgrx(send_size, data);
               } else {
                  const struct util_format_description *descr =
                        util_format_description(res->base.format);
                  switch (descr->channel[0].size) {
                  case 8:
                     vrend_collapse_data_r8g8b8x8(send_size, data);
                     break;
                  case 16:
                     vrend_collapse_data_r16g16b16x16(send_size, data);
                     break;
                  default:
                     assert(0 && "unsupported collaps four channel to three channel");
                  }
               }

            } else if (vrend_format_is_bgra(res->base.format)) {
               VREND_DEBUG(dbg_bgra, ctx, "manually swizzling bgra->rgba on upload since gles+bgra\n");
               vrend_swizzle_data_bgra(send_size, data);
            }
         }

         /* mipmaps are usually passed in one iov, and we need to keep the offset
          * into the data in case we want to read back the data of a surface
          * that can not be rendered. Since we can not assume that the whole texture
          * is filled, we evaluate the offset for origin (0,0,0). Since it is also
          * possible that a resource is reused and resized update the offset every time.
          */
         if (info->level < VR_MAX_TEXTURE_2D_LEVELS) {
            int64_t level_height = u_minify(res->base.height0, info->level);
            res->mipmap_offsets[info->level] = info->offset -
                                               ((info->box->z * level_height + y) * stride + x * elsize);
         }

         if (res->base.format == VIRGL_FORMAT_Z24X8_UNORM) {
            /* we get values from the guest as 24-bit scaled integers
               but we give them to the host GL and it interprets them
               as 32-bit scaled integers, so we need to scale them here */
            depth_scale = 256.0;
            if (!vrend_state.use_core_profile)
               glPixelTransferf(GL_DEPTH_SCALE, depth_scale);
            else
               vrend_scale_depth(data, send_size, depth_scale);
         }
         if (res->target == GL_TEXTURE_CUBE_MAP) {
            GLenum ctarget = GL_TEXTURE_CUBE_MAP_POSITIVE_X + info->box->z;
            if (compressed) {
               glCompressedTexSubImage2D(ctarget, info->level, x, y,
                                         info->box->width, info->box->height,
                                         glformat, comp_size, data);
            } else {
               glTexSubImage2D(ctarget, info->level, x, y, info->box->width, info->box->height,
                               glformat, gltype, data);
            }
         } else if (res->target == GL_TEXTURE_3D || res->target == GL_TEXTURE_2D_ARRAY || res->target == GL_TEXTURE_CUBE_MAP_ARRAY) {
            if (compressed) {
               glCompressedTexSubImage3D(res->target, info->level, x, y, info->box->z,
                                         info->box->width, info->box->height, info->box->depth,
                                         glformat, comp_size, data);
            } else {
               glTexSubImage3D(res->target, info->level, x, y, info->box->z,
                               info->box->width, info->box->height, info->box->depth,
                               glformat, gltype, data);
            }
         } else if (res->target == GL_TEXTURE_1D) {
            if (vrend_state.use_gles) {
               /* Covers both compressed and none compressed. */
               report_gles_missing_func(ctx, "gl[Compressed]TexSubImage1D");
            } else if (compressed) {
               glCompressedTexSubImage1D(res->target, info->level, info->box->x,
                                         info->box->width,
                                         glformat, comp_size, data);
            } else {
               glTexSubImage1D(res->target, info->level, info->box->x, info->box->width,
                               glformat, gltype, data);
            }
         } else {
            if (compressed) {
               glCompressedTexSubImage2D(res->target, info->level, x, res->target == GL_TEXTURE_1D_ARRAY ? info->box->z : y,
                                         info->box->width, info->box->height,
                                         glformat, comp_size, data);
            } else {
               glTexSubImage2D(res->target, info->level, x, res->target == GL_TEXTURE_1D_ARRAY ? info->box->z : y,
                               info->box->width,
                               res->target == GL_TEXTURE_1D_ARRAY ? info->box->depth : info->box->height,
                               glformat, gltype, data);
            }
         }
         if (res->base.format == VIRGL_FORMAT_Z24X8_UNORM) {
            if (!vrend_state.use_core_profile)
               glPixelTransferf(GL_DEPTH_SCALE, 1.0);
         }
      }

      if (stride && !need_temp) {
         glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
         glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
      }

      glPixelStorei(GL_UNPACK_ALIGNMENT, 4);

      if (need_temp)
         free(data);
   }
   return 0;
}

static uint32_t vrend_get_texture_depth(struct vrend_resource *res, uint32_t level)
{
   uint32_t depth = 1;
   if (res->target == GL_TEXTURE_3D)
      depth = u_minify(res->base.depth0, level);
   else if (res->target == GL_TEXTURE_1D_ARRAY || res->target == GL_TEXTURE_2D_ARRAY ||
            res->target == GL_TEXTURE_CUBE_MAP || res->target == GL_TEXTURE_CUBE_MAP_ARRAY)
      depth = res->base.array_size;

   return depth;
}

static int vrend_transfer_send_getteximage(struct vrend_resource *res,
                                           const struct iovec *iov, int num_iovs,
                                           const struct vrend_transfer_info *info)
{
   GLenum format, type;
   char *data;
   int elsize = util_format_get_blocksize(res->base.format);
   int compressed = util_format_is_compressed(res->base.format);
   uint32_t iov_size = vrend_get_iovec_size(iov, num_iovs);
   GLenum target;
   uint32_t send_offset = 0;
   format = tex_conv_table[res->base.format].glformat;
   type = tex_conv_table[res->base.format].gltype;

   if (compressed)
      format = tex_conv_table[res->base.format].internalformat;

   uint64_t tex_size = util_format_get_nblocks(res->base.format,
                                               u_minify(res->base.width0, info->level),
                                               u_minify(res->base.height0, info->level));
   tex_size *= util_format_get_blocksize(res->base.format);
   tex_size *= vrend_get_texture_depth(res, info->level);

   if (tex_size > iov_size)
      return EINVAL;

   if (info->box->z && res->target != GL_TEXTURE_CUBE_MAP) {
      send_offset = util_format_get_nblocks(res->base.format, u_minify(res->base.width0, info->level), u_minify(res->base.height0, info->level)) * util_format_get_blocksize(res->base.format) * info->box->z;
   }

   data = malloc(tex_size);
   if (!data)
      return ENOMEM;

   switch (elsize) {
   case 1:
      glPixelStorei(GL_PACK_ALIGNMENT, 1);
      break;
   case 2:
      glPixelStorei(GL_PACK_ALIGNMENT, 2);
      break;
   case 4:
   default:
      glPixelStorei(GL_PACK_ALIGNMENT, 4);
      break;
   case 8:
      glPixelStorei(GL_PACK_ALIGNMENT, 8);
      break;
   }

   glBindTexture(res->target, res->gl_id);
   if (res->target == GL_TEXTURE_CUBE_MAP) {
      target = GL_TEXTURE_CUBE_MAP_POSITIVE_X + info->box->z;
   } else
      target = res->target;

   if (compressed) {
      if (has_feature(feat_arb_robustness)) {
         glGetnCompressedTexImageARB(target, info->level, tex_size, data);
      } else if (vrend_state.use_gles) {
         report_gles_missing_func(NULL, "glGetCompressedTexImage");
      } else {
         glGetCompressedTexImage(target, info->level, data);
      }
   } else {
      if (has_feature(feat_arb_robustness)) {
         glGetnTexImageARB(target, info->level, format, type, tex_size, data);
      } else if (vrend_state.use_gles) {
         report_gles_missing_func(NULL, "glGetTexImage");
      } else {
         glGetTexImage(target, info->level, format, type, data);
      }
   }

   glPixelStorei(GL_PACK_ALIGNMENT, 4);

   write_transfer_data(&res->base, iov, num_iovs, data + send_offset,
                       info->stride, info->box, info->level, info->offset,
                       false);
   free(data);
   return 0;
}

static void do_readpixels(struct vrend_resource *res,
                          int idx, uint32_t level, uint32_t layer,
                          GLint x, GLint y,
                          GLsizei width, GLsizei height,
                          GLenum format, GLenum type,
                          GLsizei bufSize, void *data)
{
   GLuint fb_id;

   glGenFramebuffers(1, &fb_id);
   glBindFramebuffer(GL_FRAMEBUFFER, fb_id);

   vrend_fb_bind_texture(res, idx, level, layer);

   /* Warn if the driver doesn't agree about the read format and type.
      On desktop GL we can use basically any format and type to glReadPixels,
      so we picked the format and type that matches the native format.

      But on GLES we are limited to a very few set, luckily most GLES
      implementations should return type and format that match the native
      formats, and can be used for glReadPixels acording to the GLES spec.

      But we have found that at least Mesa returned the wrong formats, again
      luckily we are able to change Mesa. But just in case there are more bad
      drivers out there, or we mess up the format somewhere, we warn here. */
   if (vrend_state.use_gles && !vrend_format_is_ds(res->base.format)) {
      GLint imp;
      if (type != GL_UNSIGNED_BYTE && type != GL_UNSIGNED_INT &&
          type != GL_INT && type != GL_FLOAT) {
         glGetIntegerv(GL_IMPLEMENTATION_COLOR_READ_TYPE, &imp);
         if (imp != (GLint)type) {
            virgl_warn("GL_IMPLEMENTATION_COLOR_READ_TYPE is not expected native type 0x%x != imp 0x%x\n", type, imp);
         }
      }
      if (format != GL_RGBA && format != GL_RGBA_INTEGER) {
         glGetIntegerv(GL_IMPLEMENTATION_COLOR_READ_FORMAT, &imp);
         if (imp != (GLint)format) {
            virgl_warn("GL_IMPLEMENTATION_COLOR_READ_FORMAT is not expected native format 0x%x != imp 0x%x\n", format, imp);
         }
      }
   }

   /* read-color clamping is handled in the mesa frontend */
   if (!vrend_state.use_gles) {
       glClampColor(GL_CLAMP_READ_COLOR_ARB, GL_FALSE);
   }

   if (has_feature(feat_arb_robustness))
      glReadnPixelsARB(x, y, width, height, format, type, bufSize, data);
   else if (epoxy_gl_version() >= 45)
      glReadnPixels(x, y, width, height, format, type, bufSize, data);
   else if (has_feature(feat_gles_khr_robustness))
      glReadnPixelsKHR(x, y, width, height, format, type, bufSize, data);
   else
      glReadPixels(x, y, width, height, format, type, data);

   glDeleteFramebuffers(1, &fb_id);
}

static int vrend_transfer_send_readpixels(struct vrend_context *ctx,
                                          struct vrend_resource *res,
                                          const struct iovec *iov, int num_iovs,
                                          const struct vrend_transfer_info *info)
{
   char *myptr = (char*)iov[0].iov_base + info->offset;
   int need_temp = 0;
   char *data;
   bool actually_invert, separate_invert = false;
   GLenum format, type;
   GLint y1;
   uint64_t send_size = 0;
   uint32_t h = u_minify(res->base.height0, info->level);
   int elsize = util_format_get_blocksize(res->base.format);
   float depth_scale;
   int row_stride = info->stride / elsize;
   GLint old_fbo;

   vrend_use_program(NULL);

   enum virgl_formats fmt = res->base.format;

   format = tex_conv_table[fmt].glformat;
   if (vrend_state.use_gles &&
       res->gbm_bo) {
      switch (fmt) {
      case VIRGL_FORMAT_B8G8R8X8_UNORM:
         format = tex_conv_table[VIRGL_FORMAT_B8G8R8A8_UNORM].glformat;
         break;
      case VIRGL_FORMAT_B8G8R8X8_SRGB:
         format = tex_conv_table[VIRGL_FORMAT_B8G8R8A8_SRGB].glformat;
         break;
      case VIRGL_FORMAT_R8G8B8X8_UNORM:
         format = tex_conv_table[VIRGL_FORMAT_R8G8B8A8_UNORM].glformat;
         break;
      case VIRGL_FORMAT_R8G8B8X8_SRGB:
         format = tex_conv_table[VIRGL_FORMAT_R8G8B8A8_SRGB].glformat;
         break;
      default:
         break;
      }
   }

   type = tex_conv_table[fmt].gltype;
   /* if we are asked to invert and reading from a front then don't */

   actually_invert = res->y_0_top;

   if (actually_invert && !has_feature(feat_mesa_invert))
      separate_invert = true;

#if UTIL_ARCH_BIG_ENDIAN
   glPixelStorei(GL_PACK_SWAP_BYTES, 1);
#endif

   if (num_iovs > 1 || separate_invert)
      need_temp = 1;

   if (vrend_state.use_gles && vrend_format_is_bgra(res->base.format))
      need_temp = true;

   if (need_temp) {
      send_size = util_format_get_nblocks(res->base.format, info->box->width, info->box->height);
      send_size *= info->box->depth;
      send_size *= util_format_get_blocksize(res->base.format);

      /* glReadnPixels only supports a buffer size of GLsizei = uint32_t, anything larger
       * is bogous */
      if (send_size > UINT_MAX) {
         virgl_error("Readback size out of range %"PRIu64"\n", send_size);
         return EINVAL;
      }

      data = malloc(send_size);
      if (!data) {
         virgl_error("Memory allocation failed for %"PRIu64"\n", send_size);
         return ENOMEM;
      }
   } else {
      send_size = iov[0].iov_len - info->offset;
      data = myptr;
      if (!row_stride)
         row_stride = util_format_get_nblocksx(res->base.format, u_minify(res->base.width0, info->level));
   }

   glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &old_fbo);

   if (actually_invert)
      y1 = h - info->box->y - info->box->height;
   else
      y1 = info->box->y;

   if (has_feature(feat_mesa_invert) && actually_invert)
      glPixelStorei(GL_PACK_INVERT_MESA, 1);
   if (!need_temp && row_stride)
      glPixelStorei(GL_PACK_ROW_LENGTH, row_stride);

   switch (elsize) {
   case 1:
      glPixelStorei(GL_PACK_ALIGNMENT, 1);
      break;
   case 2:
      glPixelStorei(GL_PACK_ALIGNMENT, 2);
      break;
   case 4:
   default:
      glPixelStorei(GL_PACK_ALIGNMENT, 4);
      break;
   case 8:
      glPixelStorei(GL_PACK_ALIGNMENT, 8);
      break;
   }

   if (res->base.format == VIRGL_FORMAT_Z24X8_UNORM) {
      /* we get values from the guest as 24-bit scaled integers
         but we give them to the host GL and it interprets them
         as 32-bit scaled integers, so we need to scale them here */
      depth_scale = 1.0 / 256.0;
      if (!vrend_state.use_core_profile) {
         glPixelTransferf(GL_DEPTH_SCALE, depth_scale);
      }
   }

   do_readpixels(res, 0, info->level, info->box->z, info->box->x, y1,
                 info->box->width, info->box->height, format, type, send_size, data);

   /* on GLES, texture-backed BGR* resources are always stored with RGB* internal format, but
    * the guest will expect to readback the data in BGRA format.
    * Since the GLES API doesn't allow format conversions like GL, we CPU-swizzle the data
    * on upload and need to do the same on readback.
    * The notable exception is externally-stored (GBM/EGL) BGR* resources, for which BGR*
    * byte-ordering is used instead to match external access patterns. */
   if (vrend_state.use_gles && vrend_format_is_bgra(res->base.format)) {
      VREND_DEBUG(dbg_bgra, ctx, "manually swizzling rgba->bgra on readback since gles+bgra\n");
      vrend_swizzle_data_bgra(send_size, data);
   }

   if (res->base.format == VIRGL_FORMAT_Z24X8_UNORM) {
      if (!vrend_state.use_core_profile)
         glPixelTransferf(GL_DEPTH_SCALE, 1.0);
      else
         vrend_scale_depth(data, send_size, depth_scale);
   }
   if (has_feature(feat_mesa_invert) && actually_invert)
      glPixelStorei(GL_PACK_INVERT_MESA, 0);
   if (!need_temp && row_stride)
      glPixelStorei(GL_PACK_ROW_LENGTH, 0);
   glPixelStorei(GL_PACK_ALIGNMENT, 4);

#if UTIL_ARCH_BIG_ENDIAN
   glPixelStorei(GL_PACK_SWAP_BYTES, 0);
#endif

   if (need_temp) {
      write_transfer_data(&res->base, iov, num_iovs, data,
                          info->stride, info->box, info->level, info->offset,
                          separate_invert);
      free(data);
   }

   glBindFramebuffer(GL_FRAMEBUFFER, old_fbo);

   return 0;
}

static int vrend_transfer_send_readonly(struct vrend_resource *res,
                                        const struct iovec *iov, int num_iovs,
                                        UNUSED const struct vrend_transfer_info *info)
{
   bool same_iov = true;
   unsigned i;

   if (res->num_iovs == (uint32_t)num_iovs) {
      for (i = 0; i < res->num_iovs; i++) {
         if (res->iov[i].iov_len != iov[i].iov_len ||
             res->iov[i].iov_base != iov[i].iov_base) {
            same_iov = false;
         }
      }
   } else {
      same_iov = false;
   }

   /*
    * When we detect that we are reading back to the same iovs that are
    * attached to the resource and we know that the resource can not
    * be rendered to (as this function is only called then), we do not
    * need to do anything more.
    */
   if (same_iov) {
      return 0;
   }

   return -1;
}

static int vrend_renderer_transfer_send_iov(struct vrend_context *ctx,
                                            struct vrend_resource *res,
                                            const struct iovec *iov, int num_iovs,
                                            const struct vrend_transfer_info *info)
{
   if ((is_only_bit(res->storage_bits, VREND_STORAGE_GUEST_MEMORY) ||
       has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) && res->iov) {
      return vrend_copy_iovec(res->iov, res->num_iovs, info->box->x,
                              iov, num_iovs, info->offset,
                              info->box->width, res->ptr);
   }

   if (has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) {
      assert(!res->iov);
      vrend_write_to_iovec(iov, num_iovs, info->offset,
                           res->ptr + info->box->x, info->box->width);
      return 0;
   }

   if (has_bit(res->storage_bits, VREND_STORAGE_GL_BUFFER)) {
      glBindBufferARB(res->target, res->gl_id);
      void *data = glMapBufferRange(res->target, info->box->x, info->box->width, GL_MAP_READ_BIT);
      if (!data)
         virgl_error("Unable to open buffer for reading %d\n", res->target);
      else
         vrend_write_to_iovec(iov, num_iovs, info->offset, data, info->box->width);
      glUnmapBuffer(res->target);
      glBindBufferARB(res->target, 0);
   } else {
      int ret = -1;
      bool can_readpixels = true;

      can_readpixels = vrend_format_can_render(res->base.format) || vrend_format_is_ds(res->base.format);

      if (can_readpixels)
         ret = vrend_transfer_send_readpixels(ctx, res, iov, num_iovs, info);

      /* Can hit this on a non-error path as well. */
      if (ret) {
         if (!vrend_state.use_gles)
            ret = vrend_transfer_send_getteximage(res, iov, num_iovs, info);
         else
            ret = vrend_transfer_send_readonly(res, iov, num_iovs, info);
      }

      return ret;
   }
   return 0;
}

static int vrend_renderer_transfer_internal(struct vrend_context *ctx,
                                            struct vrend_resource *res,
                                            const struct vrend_transfer_info *info,
                                            int transfer_mode)
{
   const struct iovec *iov;
   int num_iovs;

   if (!info->box)
      return EINVAL;

   if (!vrend_hw_switch_context(ctx, true))
      return EINVAL;

   assert(check_transfer_iovec(res, info));
   if (info->iovec && info->iovec_cnt) {
      iov = info->iovec;
      num_iovs = info->iovec_cnt;
   } else {
      iov = res->iov;
      num_iovs = res->num_iovs;
   }

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   if (res->gbm_bo && (transfer_mode == VIRGL_TRANSFER_TO_HOST ||
                       !has_bit(res->storage_bits, VREND_STORAGE_EGL_IMAGE))) {
      const bool success = virgl_gbm_transfer(res->gbm_bo, transfer_mode, iov, num_iovs, info) == 0;
      if (success)
         return 0;
      else
         virgl_warn("GBM upload failed, try GL code path\n");
   }
#endif

   if (!resource_contains_box(res, info->box, info->level)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_TRANSFER_IOV_BOUNDS, res->gl_id);
      return EINVAL;
   }

   if (!check_iov_bounds(res, info, iov, num_iovs)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_TRANSFER_IOV_BOUNDS, res->gl_id);
      return EINVAL;
   }

   switch (transfer_mode) {
   case VIRGL_TRANSFER_TO_HOST:
      return vrend_renderer_transfer_write_iov(ctx, res, iov, num_iovs, info);
   case VIRGL_TRANSFER_FROM_HOST:
      return vrend_renderer_transfer_send_iov(ctx, res, iov, num_iovs, info);

   default:
      assert(0);
   }
   return 0;
}

int vrend_renderer_transfer_iov(struct vrend_context *ctx,
                                uint32_t dst_handle,
                                const struct vrend_transfer_info *info,
                                int transfer_mode)
{
   struct vrend_resource *res;

   res = vrend_renderer_ctx_res_lookup(ctx, dst_handle);
   if (!res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, dst_handle);
      return EINVAL;
   }

   if (!check_transfer_iovec(res, info)) {
      if (has_bit(res->storage_bits, VREND_STORAGE_EGL_IMAGE))
         return 0;
      else {
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, dst_handle);
         return EINVAL;
      }
   }

   return vrend_renderer_transfer_internal(ctx, res, info,
                                           transfer_mode);
}

int vrend_renderer_transfer_pipe(struct pipe_resource *pres,
                                 const struct vrend_transfer_info *info,
                                 int transfer_mode)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;
   if (!check_transfer_iovec(res, info))
      return EINVAL;

   return vrend_renderer_transfer_internal(vrend_state.ctx0, res, info,
                                           transfer_mode);
}

int vrend_transfer_inline_write(struct vrend_context *ctx,
                                uint32_t dst_handle,
                                const struct vrend_transfer_info *info)
{
   struct vrend_resource *res;

   res = vrend_renderer_ctx_res_lookup(ctx, dst_handle);
   if (!res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, dst_handle);
      return EINVAL;
   }

   if (!resource_contains_box(res, info->box, info->level)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, dst_handle);
      return EINVAL;
   }

   if (!check_iov_bounds(res, info, info->iovec, info->iovec_cnt)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, dst_handle);
      return EINVAL;
   }

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   if (res->gbm_bo) {
      assert(!info->synchronized);
      return virgl_gbm_transfer(res->gbm_bo,
                                VIRGL_TRANSFER_TO_HOST,
                                info->iovec,
                                info->iovec_cnt,
                                info);
   }
#endif

   return vrend_renderer_transfer_write_iov(ctx, res, info->iovec, info->iovec_cnt, info);

}

int vrend_renderer_copy_transfer3d(struct vrend_context *ctx,
                                   uint32_t dst_handle,

                                   struct vrend_resource *dst_res,
                                   struct vrend_resource *src_res,
                                   const struct vrend_transfer_info *info)
{
   if (!resource_contains_box(dst_res, info->box, info->level)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, dst_handle);
      return EINVAL;
   }

   if (!check_iov_bounds(dst_res, info, src_res->iov, src_res->num_iovs)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, dst_handle);
      return EINVAL;
   }

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   if (dst_res->gbm_bo && !TRANSFER_NO_GBM_MAPPING(info)) {
      bool use_gbm = true;

      /* The guest uses copy transfers against busy resources to avoid
       * waiting.  The host GL driver is usually smart enough to avoid
       * blocking by putting the data in a staging buffer and doing a
       * pipelined copy.  But when there is a GBM bo, we can only do that when
       * VREND_STORAGE_GL_IMMUTABLE is set because it implies that the
       * internal format is known and is known to be compatible with the
       * subsequence glTexSubImage2D.  Otherwise, we glFinish and use GBM.
       * Also, EGL images with BGRX format are not compatible with
       * glTexSubImage2D, since they are stored with only 3bpp, so gbm
       * transfer is required.
       */
      if (info->synchronized) {
         if (has_bit(dst_res->storage_bits, VREND_STORAGE_GL_IMMUTABLE) &&
             dst_res->base.format != VIRGL_FORMAT_B8G8R8X8_UNORM)
            use_gbm = false;
         else
            glFinish();
      }

      if (use_gbm) {
         bool success = virgl_gbm_transfer(dst_res->gbm_bo,
                                           VIRGL_TRANSFER_TO_HOST,
                                           src_res->iov,
                                           src_res->num_iovs,
                                           info) == 0;
         if (success)
            return 0;
         virgl_warn("GBM copy transfer failed, try GL\n");
      }
   }
#endif

  return vrend_renderer_transfer_write_iov(ctx, dst_res, src_res->iov,
                                           src_res->num_iovs, info);
}

int vrend_renderer_copy_transfer3d_from_host(struct vrend_context *ctx,
                                             uint32_t dst_handle,
                                             uint32_t src_handle,
                                             struct vrend_resource *dst_res,
                                             struct vrend_resource *src_res,
                                             const struct vrend_transfer_info *info)
{
   if (!resource_contains_box(src_res, info->box, info->level)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_RESOURCE_OUT_OF_RANGE, src_handle);
      return EINVAL;
   }

   if (!check_iov_bounds(src_res, info, dst_res->iov, dst_res->num_iovs)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, dst_handle);
      return EINVAL;
   }

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   if (src_res->gbm_bo && !TRANSFER_NO_GBM_MAPPING(info)) {
      bool use_gbm = true;

      /* The guest uses copy transfers against busy resources to avoid
       * waiting. The host GL driver is usually smart enough to avoid
       * blocking by putting the data in a staging buffer and doing a
       * pipelined copy.  But when there is a GBM bo, we can only do
       * that if the format is renderable, because we use glReadPixels,
       * or on OpenGL glGetTexImage.
       * Otherwise, if the format has a gbm bo we glFinish and use GBM.
       * Also, EGL images with BGRX format are not compatible with this
       * transfer type since they are stored with only 3bpp, so gbm transfer
       * is required.
       * For now the guest can knows than a texture is backed by a gbm buffer
       * if it was created with the VIRGL_BIND_SCANOUT flag,
       */
      if (info->synchronized) {
         bool can_readpixels = vrend_format_can_render(src_res->base.format) ||
               vrend_format_is_ds(src_res->base.format);

         if ((can_readpixels || !vrend_state.use_gles) &&
             src_res->base.format != VIRGL_FORMAT_B8G8R8X8_UNORM)
            use_gbm = false;
         else
            glFinish();
      }

      if (use_gbm) {
         bool success = virgl_gbm_transfer(src_res->gbm_bo,
                                           VIRGL_TRANSFER_FROM_HOST,
                                           dst_res->iov,
                                           dst_res->num_iovs,
                                           info) == 0;
         if (success)
            return 0;
         virgl_warn("GBM read failed, try GL\n");
      }
   }
#endif

   return vrend_renderer_transfer_send_iov(ctx, src_res, dst_res->iov,
                                           dst_res->num_iovs, info);
}

void vrend_set_stencil_ref(struct vrend_context *ctx,
                           struct pipe_stencil_ref *ref)
{
   if (ctx->sub->stencil_refs[0] != ref->ref_value[0] ||
       ctx->sub->stencil_refs[1] != ref->ref_value[1]) {
      ctx->sub->stencil_refs[0] = ref->ref_value[0];
      ctx->sub->stencil_refs[1] = ref->ref_value[1];
      ctx->sub->stencil_state_dirty = true;
   }
}

void vrend_set_blend_color(struct vrend_context *ctx,
                           struct pipe_blend_color *color)
{
   ctx->sub->blend_color = *color;
   glBlendColor(color->color[0], color->color[1], color->color[2],
                color->color[3]);
}

void vrend_set_scissor_state(struct vrend_context *ctx,
                             uint32_t start_slot,
                             uint32_t num_scissor,
                             struct pipe_scissor_state *ss)
{
    for (unsigned i = 0; i < num_scissor; i++) {
      unsigned idx = start_slot + i;
      ctx->sub->ss[idx] = ss[i];
      ctx->sub->scissor_state_dirty |= (1 << idx);
    }
}

void vrend_set_polygon_stipple(struct vrend_context *ctx,
                               struct pipe_poly_stipple *ps)
{
   if (vrend_shader_use_core(ctx)) {

      /* std140 aligns array elements at 16 byte */
      for (int i = 0; i < VREND_POLYGON_STIPPLE_SIZE ; ++i)
         ctx->sub->sysvalue_data.stipple_pattern[i][0] = ps->stipple[i];
      ctx->sub->sysvalue_data_cookie++;
   } else {
      glPolygonStipple((const GLubyte *)ps->stipple);
   }
}

void vrend_set_clip_state(struct vrend_context *ctx, struct pipe_clip_state *ucp)
{
   if (vrend_shader_use_core(ctx)) {
      ctx->sub->ucp_state = *ucp;

      ctx->sub->sysvalue_data_cookie++;
      for (int i = 0 ; i < VIRGL_NUM_CLIP_PLANES; i++) {
         memcpy(&ctx->sub->sysvalue_data.clipp[i],
                (const GLfloat *) &ctx->sub->ucp_state.ucp[i], sizeof(GLfloat) * 4);
      }
   } else {
      int i, j;
      GLdouble val[4];

      for (i = 0; i < 8; i++) {
         for (j = 0; j < 4; j++)
            val[j] = ucp->ucp[i][j];
         glClipPlane(GL_CLIP_PLANE0 + i, val);
      }
   }
}

void vrend_set_sample_mask(UNUSED struct vrend_context *ctx, unsigned sample_mask)
{
   if (has_feature(feat_sample_mask))
      glSampleMaski(0, sample_mask);
}

void vrend_set_min_samples(struct vrend_context *ctx, unsigned min_samples)
{
   float min_sample_shading = (float)min_samples;
   if (ctx->sub->nr_cbufs > 0 && ctx->sub->surf[0]) {
      assert(ctx->sub->surf[0]->texture);
      min_sample_shading /= MAX2(1, ctx->sub->surf[0]->texture->base.nr_samples);
   }

   if (has_feature(feat_sample_shading))
      glMinSampleShading(min_sample_shading);
}

void vrend_set_tess_state(UNUSED struct vrend_context *ctx, const float tess_factors[6])
{
   if (has_feature(feat_tessellation)) {
      if (!vrend_state.use_gles) {
         glPatchParameterfv(GL_PATCH_DEFAULT_OUTER_LEVEL, tess_factors);
         glPatchParameterfv(GL_PATCH_DEFAULT_INNER_LEVEL, &tess_factors[4]);
      } else {
         memcpy(vrend_state.tess_factors, tess_factors, 6 * sizeof (float));
      }
   }
}

static void vrend_hw_emit_streamout_targets(UNUSED struct vrend_context *ctx, struct vrend_streamout_object *so_obj)
{
   unsigned i;

   for (i = 0; i < so_obj->num_targets; i++) {
      if (!so_obj->so_targets[i])
         glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, i, 0);
      else if (so_obj->so_targets[i]->buffer_offset || so_obj->so_targets[i]->buffer_size < so_obj->so_targets[i]->buffer->base.width0)
         glBindBufferRange(GL_TRANSFORM_FEEDBACK_BUFFER, i, so_obj->so_targets[i]->buffer->gl_id, so_obj->so_targets[i]->buffer_offset, so_obj->so_targets[i]->buffer_size);
      else
         glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, i, so_obj->so_targets[i]->buffer->gl_id);
   }
}

void vrend_set_streamout_targets(struct vrend_context *ctx,
                                 UNUSED uint32_t append_bitmask,
                                 uint32_t num_targets,
                                 uint32_t *handles)
{
   struct vrend_so_target *target;
   unsigned i;

   if (!has_feature(feat_transform_feedback))
      return;

   if (num_targets) {
      list_for_each_entry(struct vrend_streamout_object, obj, &ctx->sub->streamout_list, head) {
         if (obj->num_targets == num_targets) {
            if (!memcmp(handles, obj->handles, num_targets * 4)) {
               ctx->sub->current_so = obj;
               glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, obj->id);
               return;
            }
         }
      }

      struct vrend_streamout_object *obj = CALLOC_STRUCT(vrend_streamout_object);
      if (has_feature(feat_transform_feedback2)) {
         glGenTransformFeedbacks(1, &obj->id);
         glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, obj->id);
      }
      obj->num_targets = num_targets;
      for (i = 0; i < num_targets; i++) {
         obj->handles[i] = handles[i];
         if (handles[i] == 0)
            continue;
         target = vrend_object_lookup(ctx->sub->object_hash, handles[i], VIRGL_OBJECT_STREAMOUT_TARGET);
         if (!target) {
            /* Remove the reference to the already bound targets because we will destroy the obj */
            for (unsigned j = 0; j < i; ++j)
               vrend_so_target_reference(&obj->so_targets[j], NULL);
            vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_HANDLE, handles[i]);
            free(obj);
            return;
         }
         vrend_so_target_reference(&obj->so_targets[i], target);
      }
      vrend_hw_emit_streamout_targets(ctx, obj);
      list_addtail(&obj->head, &ctx->sub->streamout_list);
      ctx->sub->current_so = obj;
      obj->xfb_state = XFB_STATE_STARTED_NEED_BEGIN;
   } else {
      if (has_feature(feat_transform_feedback2))
         glBindTransformFeedback(GL_TRANSFORM_FEEDBACK, 0);
      ctx->sub->current_so = NULL;
   }
}

static void vrend_resource_buffer_copy(UNUSED struct vrend_context *ctx,
                                       struct vrend_resource *src_res,
                                       struct vrend_resource *dst_res,
                                       uint32_t dstx, uint32_t srcx,
                                       uint32_t width)
{
   glBindBuffer(GL_COPY_READ_BUFFER, src_res->gl_id);
   glBindBuffer(GL_COPY_WRITE_BUFFER, dst_res->gl_id);

   glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, srcx, dstx, width);
   glBindBuffer(GL_COPY_READ_BUFFER, 0);
   glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
}

static void vrend_resource_copy_fallback(struct vrend_resource *src_res,
                                         struct vrend_resource *dst_res,
                                         uint32_t dst_level,
                                         uint32_t dstx, uint32_t dsty,
                                         uint32_t dstz, uint32_t src_level,
                                         const struct pipe_box *src_box)
{
   char *tptr;
   uint32_t total_size, src_stride, dst_stride, src_layer_stride;
   GLenum glformat, gltype;
   int elsize = util_format_get_blocksize(dst_res->base.format);
   int compressed = util_format_is_compressed(dst_res->base.format);
   int cube_slice = 1;
   uint32_t slice_size, slice_offset;
   int i;
   struct pipe_box box;

   if (src_res->target == GL_TEXTURE_CUBE_MAP)
      cube_slice = 6;

   if (src_res->base.format != dst_res->base.format) {
      virgl_error("Copy fallback failed due to mismatched formats %d %d\n", src_res->base.format, dst_res->base.format);
      return;
   }

   box = *src_box;
   box.depth = vrend_get_texture_depth(src_res, src_level);
   dst_stride = util_format_get_stride(dst_res->base.format, dst_res->base.width0);

   /* this is ugly need to do a full GetTexImage */
   slice_size = util_format_get_nblocks(src_res->base.format, u_minify(src_res->base.width0, src_level), u_minify(src_res->base.height0, src_level)) *
                util_format_get_blocksize(src_res->base.format);
   total_size = slice_size * vrend_get_texture_depth(src_res, src_level);

   tptr = malloc(total_size);
   if (!tptr)
      return;

   glformat = tex_conv_table[src_res->base.format].glformat;
   gltype = tex_conv_table[src_res->base.format].gltype;

   if (compressed)
      glformat = tex_conv_table[src_res->base.format].internalformat;

   /* If we are on gles we need to rely on the textures backing
    * iovec to have the data we need, otherwise we can use glGetTexture
    */
   if (vrend_state.use_gles) {
      uint64_t src_offset = 0;
      uint64_t dst_offset = 0;
      if (src_level < VR_MAX_TEXTURE_2D_LEVELS) {
         src_offset = src_res->mipmap_offsets[src_level];
         dst_offset = dst_res->mipmap_offsets[src_level];
      }

      src_stride = util_format_get_nblocksx(src_res->base.format,
                                            u_minify(src_res->base.width0, src_level)) * elsize;
      src_layer_stride = util_format_get_2d_size(src_res->base.format,
                                                 src_stride,
                                                 u_minify(src_res->base.height0, src_level));
      read_transfer_data(src_res->iov, src_res->num_iovs, tptr,
                         src_res->base.format, src_offset,
                         src_stride, src_layer_stride, &box, false);
      /* When on GLES sync the iov that backs the dst resource because
       * we might need it in a chain copy A->B, B->C */
      write_transfer_data(&dst_res->base, dst_res->iov, dst_res->num_iovs, tptr,
                          dst_stride, &box, src_level, dst_offset, false);
      /* we get values from the guest as 24-bit scaled integers
         but we give them to the host GL and it interprets them
         as 32-bit scaled integers, so we need to scale them here */
      if (dst_res->base.format == VIRGL_FORMAT_Z24X8_UNORM) {
         float depth_scale = 256.0;
         vrend_scale_depth(tptr, total_size, depth_scale);
      }

      /* if this is a BGR* resource on GLES, the data needs to be manually swizzled to RGB* before
       * storing in a texture. Iovec data is assumed to have the original byte-order, namely BGR*,
       * and needs to be reordered when storing in the host's texture memory as RGB*.
       * On the contrary, externally-stored BGR* resources are assumed to remain in BGR* format at
       * all times.
       */
      if (vrend_state.use_gles && vrend_format_is_bgra(dst_res->base.format))
         vrend_swizzle_data_bgra(total_size, tptr);
   } else {
      uint32_t read_chunk_size;
      switch (elsize) {
      case 1:
      case 3:
         glPixelStorei(GL_PACK_ALIGNMENT, 1);
         break;
      case 2:
      case 6:
         glPixelStorei(GL_PACK_ALIGNMENT, 2);
         break;
      case 4:
      default:
         glPixelStorei(GL_PACK_ALIGNMENT, 4);
         break;
      case 8:
         glPixelStorei(GL_PACK_ALIGNMENT, 8);
         break;
      }
      glBindTexture(src_res->target, src_res->gl_id);
      slice_offset = 0;
      read_chunk_size = (src_res->target == GL_TEXTURE_CUBE_MAP) ? slice_size : total_size;
      for (i = 0; i < cube_slice; i++) {
         GLenum ctarget = src_res->target == GL_TEXTURE_CUBE_MAP ?
                            (GLenum)(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i) : src_res->target;
         if (compressed) {
            if (has_feature(feat_arb_robustness))
               glGetnCompressedTexImageARB(ctarget, src_level, read_chunk_size, tptr + slice_offset);
            else
               glGetCompressedTexImage(ctarget, src_level, tptr + slice_offset);
         } else {
            if (has_feature(feat_arb_robustness))
               glGetnTexImageARB(ctarget, src_level, glformat, gltype, read_chunk_size, tptr + slice_offset);
            else
               glGetTexImage(ctarget, src_level, glformat, gltype, tptr + slice_offset);
         }
         slice_offset += slice_size;
      }
   }

   glPixelStorei(GL_PACK_ALIGNMENT, 4);
   switch (elsize) {
   case 1:
   case 3:
      glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
      break;
   case 2:
   case 6:
      glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
      break;
   case 4:
   default:
      glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
      break;
   case 8:
      glPixelStorei(GL_UNPACK_ALIGNMENT, 8);
      break;
   }

   glBindTexture(dst_res->target, dst_res->gl_id);
   slice_offset = src_box->z * slice_size;
   cube_slice = (src_res->target == GL_TEXTURE_CUBE_MAP) ? src_box->z + src_box->depth : cube_slice;
   i = (src_res->target == GL_TEXTURE_CUBE_MAP) ? src_box->z : 0;
   if (slice_offset + src_box->width * src_box->height + cube_slice * slice_size > total_size) {
      virgl_error("Offset out of bound: %d\n", src_box->z);
      goto cleanup;
   }

   for (; i < cube_slice; i++) {
      GLenum ctarget = dst_res->target == GL_TEXTURE_CUBE_MAP ?
                          (GLenum)(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i) : dst_res->target;
      if (compressed) {
         if (ctarget == GL_TEXTURE_1D) {
            glCompressedTexSubImage1D(ctarget, dst_level, dstx,
                                      src_box->width,
                                      glformat, slice_size, tptr + slice_offset);
         } else {
            glCompressedTexSubImage2D(ctarget, dst_level, dstx, dsty,
                                      src_box->width, src_box->height,
                                      glformat, slice_size, tptr + slice_offset);
         }
      } else {
         if (ctarget == GL_TEXTURE_1D) {
            glTexSubImage1D(ctarget, dst_level, dstx, src_box->width, glformat, gltype, tptr + slice_offset);
         } else if (ctarget == GL_TEXTURE_3D ||
                    ctarget == GL_TEXTURE_2D_ARRAY ||
                    ctarget == GL_TEXTURE_CUBE_MAP_ARRAY) {
            glTexSubImage3D(ctarget, dst_level, dstx, dsty, dstz, src_box->width, src_box->height, src_box->depth, glformat, gltype, tptr + slice_offset);
         } else {
            glTexSubImage2D(ctarget, dst_level, dstx, dsty, src_box->width, src_box->height, glformat, gltype, tptr + slice_offset);
         }
      }
      slice_offset += slice_size;
   }

cleanup:
   glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
   free(tptr);
   glBindTexture(dst_res->target, 0);
}

static inline void
vrend_copy_sub_image(struct vrend_resource* src_res, struct vrend_resource * dst_res,
                     uint32_t src_level, const struct pipe_box *src_box,
                     uint32_t dst_level, uint32_t dstx, uint32_t dsty, uint32_t dstz)
{
   glCopyImageSubData(src_res->gl_id, src_res->target, src_level,
                      src_box->x, src_box->y, src_box->z,
                      dst_res->gl_id, dst_res->target, dst_level,
                      dstx, dsty, dstz,
                      src_box->width, src_box->height,src_box->depth);

   // temporarily added to disable strict error checking and fix guests that are still using pre 20.x
   // mesa/virgl drivers that generate an error here during window resizes:
   //   "ERROR: GL_INVALID_VALUE in glCopyImageSubData(srcX or srcWidth exceeds image bounds)"
   if (has_bit(src_res->storage_bits, VREND_STORAGE_GBM_BUFFER) &&
       glGetError() != GL_NO_ERROR) {
      virgl_warn("glCopyImageSubData maybe fail\n");
   }
}

static bool resource_dest_contains_box(struct vrend_resource *src_res,
                                       struct vrend_resource *dst_res,
                                       uint32_t dst_level,
                                       uint32_t dstx,
                                       uint32_t dsty,
                                       uint32_t dstz,
                                       const struct pipe_box *src_box)
{
   enum pipe_format src_format;
   enum pipe_format dst_format;
   struct pipe_box dst_box = *src_box;
   unsigned src_bw, dst_bw, src_bh, dst_bh;

   src_format = src_res->base.format;
   dst_format = dst_res->base.format;

   /* init dst box */
   dst_box.x = dstx;
   dst_box.y = dsty;
   dst_box.z = dstz;

   src_bw = util_format_get_blockwidth(src_format);
   src_bh = util_format_get_blockheight(src_format);
   dst_bw = util_format_get_blockwidth(dst_format);
   dst_bh = util_format_get_blockheight(dst_format);

   /* Note: all box positions and sizes are in pixels */
   if (src_bw > 1 && dst_bw == 1) {
      /* Copy from compressed to uncompressed.
       * Shrink dest box by the src block size.
       */
      dst_box.width /= src_bw;
      dst_box.height /= src_bh;
   }
   else if (src_bw == 1 && dst_bw > 1) {
      /* Copy from uncompressed to compressed.
       * Expand dest box by the dest block size.
       */
      dst_box.width *= dst_bw;
      dst_box.height *= dst_bh;
   }

   return resource_contains_box(dst_res, &dst_box, dst_level);
}

void vrend_renderer_resource_copy_region(struct vrend_context *ctx,
                                         uint32_t dst_handle, uint32_t dst_level,
                                         uint32_t dstx, uint32_t dsty, uint32_t dstz,
                                         uint32_t src_handle, uint32_t src_level,
                                         const struct pipe_box *src_box)
{
   struct vrend_resource *src_res, *dst_res;
   GLbitfield glmask = 0;
   GLint sy1, sy2, dy1, dy2;
   unsigned int comp_flags;

   if (ctx->in_error)
      return;

   src_res = vrend_renderer_ctx_res_lookup(ctx, src_handle);
   dst_res = vrend_renderer_ctx_res_lookup(ctx, dst_handle);

   if (!src_res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, src_handle);
      return;
   }
   if (!dst_res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, dst_handle);
      return;
   }

   if (!resource_contains_box(src_res, src_box, src_level)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, src_handle);
      return;
   }

   if (!resource_dest_contains_box(src_res, dst_res, dst_level,
                                   dstx, dsty, dstz, src_box)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_RESOURCE_OUT_OF_RANGE, dst_handle);
      return;
   }

   VREND_DEBUG(dbg_copy_resource, ctx, "COPY_REGION: From %s ms:%d [%d, %d, %d]+[%d, %d, %d] lvl:%d "
                                   "To %s ms:%d [%d, %d, %d]\n",
                                   util_format_name(src_res->base.format), src_res->base.nr_samples,
                                   src_box->x, src_box->y, src_box->z,
                                   src_box->width, src_box->height, src_box->depth,
                                   src_level,
                                   util_format_name(dst_res->base.format), dst_res->base.nr_samples,
                                   dstx, dsty, dstz);

   if (src_res->base.target == PIPE_BUFFER && dst_res->base.target == PIPE_BUFFER) {
      /* do a buffer copy */
      VREND_DEBUG(dbg_copy_resource, ctx, "COPY_REGION: buffer copy %d+%d\n",
                  src_box->x, src_box->width);
      vrend_resource_buffer_copy(ctx, src_res, dst_res, dstx,
                                 src_box->x, src_box->width);
      return;
   }

   comp_flags = VREND_COPY_COMPAT_FLAG_ALLOW_COMPRESSED;
   if (src_res->egl_image)
      comp_flags |= VREND_COPY_COMPAT_FLAG_ONE_IS_EGL_IMAGE;
   if (dst_res->egl_image)
      comp_flags ^= VREND_COPY_COMPAT_FLAG_ONE_IS_EGL_IMAGE;

   if (has_feature(feat_copy_image) &&
       format_is_copy_compatible(src_res->base.format,dst_res->base.format, comp_flags) &&
       src_res->base.nr_samples == dst_res->base.nr_samples) {
      VREND_DEBUG(dbg_copy_resource, ctx, "COPY_REGION: use glCopyImageSubData\n");
      vrend_copy_sub_image(src_res, dst_res, src_level, src_box,
                           dst_level, dstx, dsty, dstz);
      return;
   }

   if (!vrend_format_can_render(src_res->base.format) ||
       !vrend_format_can_render(dst_res->base.format)) {
      VREND_DEBUG(dbg_copy_resource, ctx, "COPY_REGION: use resource_copy_fallback\n");
      vrend_resource_copy_fallback(src_res, dst_res, dst_level, dstx,
                                   dsty, dstz, src_level, src_box);
      return;
   }

   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);
   VREND_DEBUG(dbg_copy_resource, ctx, "COPY_REGION: use glBlitFramebuffer\n");

   /* clean out fb ids */
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                          GL_TEXTURE_2D, 0, 0);
   vrend_fb_bind_texture(src_res, 0, src_level, src_box->z);

   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[1]);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                          GL_TEXTURE_2D, 0, 0);
   vrend_fb_bind_texture(dst_res, 0, dst_level, dstz);
   glBindFramebuffer(GL_DRAW_FRAMEBUFFER, ctx->sub->blit_fb_ids[1]);

   glBindFramebuffer(GL_READ_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);

   glmask = GL_COLOR_BUFFER_BIT;
   glDisable(GL_SCISSOR_TEST);

   if (!src_res->y_0_top) {
      sy1 = src_box->y;
      sy2 = src_box->y + src_box->height;
   } else {
      sy1 = src_res->base.height0 - src_box->y - src_box->height;
      sy2 = src_res->base.height0 - src_box->y;
   }

   if (!dst_res->y_0_top) {
      dy1 = dsty;
      dy2 = dsty + src_box->height;
   } else {
      dy1 = dst_res->base.height0 - dsty - src_box->height;
      dy2 = dst_res->base.height0 - dsty;
   }

   glBlitFramebuffer(src_box->x, sy1,
                     src_box->x + src_box->width,
                     sy2,
                     dstx, dy1,
                     dstx + src_box->width,
                     dy2,
                     glmask, GL_NEAREST);

   glBindFramebuffer(GL_READ_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                          GL_TEXTURE_2D, 0, 0);
   glBindFramebuffer(GL_READ_FRAMEBUFFER, ctx->sub->blit_fb_ids[1]);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                          GL_TEXTURE_2D, 0, 0);

   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->fb_id);

   if (ctx->sub->rs_state.scissor)
      glEnable(GL_SCISSOR_TEST);
}


static inline bool texture_view_compatible(enum virgl_formats src, enum virgl_formats dst)
{
   return (tex_conv_table[src].view_class != view_class_unsupported) &&
         (tex_conv_table[src].view_class == tex_conv_table[dst].view_class);
}

static GLuint vrend_make_view(struct vrend_resource *res, enum virgl_formats format)
{
   GLuint view_id;

   GLenum tex_ifmt = tex_conv_table[res->base.format].internalformat;
   GLenum view_ifmt = tex_conv_table[format].internalformat;

   if (tex_ifmt == view_ifmt)
      return res->gl_id;

   if (!texture_view_compatible(res->base.format, format))
      return res->gl_id;

   /* If the format doesn't support TextureStorage it is not immutable, so no TextureView*/
   if (!has_bit(res->storage_bits, VREND_STORAGE_GL_IMMUTABLE))
      return res->gl_id;

   assert(vrend_resource_supports_view(res, format));

   VREND_DEBUG(dbg_blit, NULL, "Create texture view from %s as %s\n",
               util_format_name(res->base.format),
               util_format_name(format));

   if (vrend_state.use_gles) {
      assert(res->target != GL_TEXTURE_RECTANGLE_NV);
      assert(res->target != GL_TEXTURE_1D);
      assert(res->target != GL_TEXTURE_1D_ARRAY);
   }

   glGenTextures(1, &view_id);
   glTextureView(view_id, res->target, res->gl_id, view_ifmt, 0, res->base.last_level + 1,
                 0, res->base.array_size);
   return view_id;
}

static bool vrend_blit_needs_redblue_swizzle(struct vrend_resource *src_res,
                                             struct vrend_resource *dst_res,
                                             const struct pipe_blit_info *info)
{
   /* EGL-backed bgr* resources are always stored with BGR* internal format,
    * despite Virgl's use of the GL_RGBA8 internal format, so special care must
    * be taken when determining the swizzling. */
   bool src_needs_swizzle = vrend_resource_needs_redblue_swizzle(src_res, info->src.format);
   bool dst_needs_swizzle = vrend_resource_needs_redblue_swizzle(dst_res, info->dst.format);
   return src_needs_swizzle ^ dst_needs_swizzle;
}

static void vrend_renderer_prepare_blit_extra_info(struct vrend_context *ctx,
                                                   struct vrend_resource *src_res,
                                                   struct vrend_resource *dst_res,
                                                   struct vrend_blit_info *info)
{
   info->can_fbo_blit = true;

   info->gl_filter = convert_mag_filter(info->b.filter);

   if (!dst_res->y_0_top) {
      info->dst_y1 = info->b.dst.box.y + info->b.dst.box.height;
      info->dst_y2 = info->b.dst.box.y;
   } else {
      info->dst_y1 = dst_res->base.height0 - info->b.dst.box.y - info->b.dst.box.height;
      info->dst_y2 = dst_res->base.height0 - info->b.dst.box.y;
   }

   if (!src_res->y_0_top) {
      info->src_y1 = info->b.src.box.y + info->b.src.box.height;
      info->src_y2 = info->b.src.box.y;
   } else {
      info->src_y1 = src_res->base.height0 - info->b.src.box.y - info->b.src.box.height;
      info->src_y2 = src_res->base.height0 - info->b.src.box.y;
   }

   if (vrend_blit_needs_swizzle(info->b.dst.format, info->b.src.format)) {
      info->needs_swizzle = true;
      info->can_fbo_blit = false;
   }

   if (info->needs_swizzle && vrend_get_format_table_entry(dst_res->base.format)->flags & VIRGL_TEXTURE_NEED_SWIZZLE)
      memcpy(info->swizzle, tex_conv_table[dst_res->base.format].swizzle, sizeof(info->swizzle));

   if (vrend_blit_needs_redblue_swizzle(src_res, dst_res, &info->b)) {
      VREND_DEBUG(dbg_blit, ctx, "Applying red/blue swizzle during blit involving an external BGR* resource\n");
      uint8_t temp = info->swizzle[0];
      info->swizzle[0] = info->swizzle[2];
      info->swizzle[2] = temp;
      info->can_fbo_blit = false;
   }

   /* for scaled MS blits we either need extensions or hand roll */
   if (info->b.mask & PIPE_MASK_RGBA &&
       src_res->base.nr_samples > 1 &&
       src_res->base.nr_samples != dst_res->base.nr_samples &&
       (info->b.src.box.width != info->b.dst.box.width ||
        info->b.src.box.height != info->b.dst.box.height)) {
      if (has_feature(feat_ms_scaled_blit))
         info->gl_filter = GL_SCALED_RESOLVE_NICEST_EXT;
      else
         info->can_fbo_blit = false;
   }

   /* need to apply manual gamma correction in the blitter for external
    * resources that don't support colorspace conversion via views
    * (EGL-image bgr* textures). */
   if (vrend_resource_needs_srgb_decode(src_res, info->b.src.format)) {
      info->needs_manual_srgb_decode = true;
      info->can_fbo_blit = false;
   }
   if (vrend_resource_needs_srgb_encode(dst_res, info->b.dst.format)) {
      info->needs_manual_srgb_encode = true;
      info->can_fbo_blit = false;
   }
}

/* Prepare the extra blit info and return true if a FBO blit can be used. */
static bool vrend_renderer_prepare_blit(struct vrend_context *ctx,
                                        struct vrend_resource *src_res,
                                        struct vrend_resource *dst_res,
                                        const struct vrend_blit_info *info)
{
   if (!info->can_fbo_blit)
      return false;

   /* if we can't make FBO's use the fallback path */
   if (!vrend_format_can_render(src_res->base.format) &&
       !vrend_format_is_ds(src_res->base.format))
      return false;

   if (!vrend_format_can_render(src_res->base.format) &&
       !vrend_format_is_ds(src_res->base.format))
      return false;

   /* different depth formats */
   if (vrend_format_is_ds(src_res->base.format) &&
       vrend_format_is_ds(dst_res->base.format)) {
      if (src_res->base.format != dst_res->base.format) {
         if (!(src_res->base.format == PIPE_FORMAT_S8_UINT_Z24_UNORM &&
               (dst_res->base.format == PIPE_FORMAT_Z24X8_UNORM))) {
            return false;
         }
      }
   }
   /* glBlitFramebuffer - can support depth stencil with NEAREST
      which we use for mipmaps */
   if ((info->b.mask & (PIPE_MASK_Z | PIPE_MASK_S)) && info->gl_filter != GL_NEAREST)
      return false;

   /* since upstream mesa change
    * https://gitlab.freedesktop.org/mesa/mesa/-/merge_requests/5034
    * an imported RGBX texture uses GL_RGB8 as internal format while
    * in virgl_formats, we use GL_RGBA8 internal format for RGBX texutre.
    * on GLES host, glBlitFramebuffer doesn't work in such case. */
   if (vrend_state.use_gles &&
       info->b.mask & PIPE_MASK_RGBA &&
       src_res->base.format == VIRGL_FORMAT_R8G8B8X8_UNORM &&
       dst_res->base.format == VIRGL_FORMAT_R8G8B8X8_UNORM &&
       has_bit(src_res->storage_bits, VREND_STORAGE_EGL_IMAGE) !=
       has_bit(dst_res->storage_bits, VREND_STORAGE_EGL_IMAGE) &&
       (src_res->base.nr_samples || dst_res->base.nr_samples)) {
      return false;
   }

   /* GLES generally doesn't support blitting to a multi-sample FB, and also not
    * from a multi-sample FB where the regions are not exatly the same or the
    * source and target format are different. For
    * downsampling DS blits to zero samples we solve this by doing two blits */
   if (vrend_state.use_gles &&
       ((dst_res->base.nr_samples > 1) ||
        ((info->b.mask & PIPE_MASK_RGBA) &&
         (src_res->base.nr_samples > 1) &&
         (info->b.src.box.x != info->b.dst.box.x ||
          info->b.src.box.width != info->b.dst.box.width ||
          info->dst_y1 != info->src_y1 || info->dst_y2 != info->src_y2 ||
          info->b.src.format != info->b.dst.format))
        )) {
      VREND_DEBUG(dbg_blit, ctx, "Use GL fallback because dst:ms:%d src:ms:%d (%d %d %d %d) -> (%d %d %d %d)\n",
                  dst_res->base.nr_samples, src_res->base.nr_samples, info->b.src.box.x, info->b.src.box.x + info->b.src.box.width,
                  info->src_y1, info->src_y2, info->b.dst.box.x, info->b.dst.box.x + info->b.dst.box.width, info->dst_y1, info->dst_y2);
         return false;
   }

   /* for 3D mipmapped blits - hand roll time */
   if (info->b.src.box.depth != info->b.dst.box.depth)
      return false;

   return true;
}

static void vrend_renderer_blit_fbo(struct vrend_context *ctx,
                                    struct vrend_resource *src_res,
                                    struct vrend_resource *dst_res,
                                    const struct vrend_blit_info *info)
{
   GLbitfield glmask = 0;
   if (info->b.mask & PIPE_MASK_Z)
      glmask |= GL_DEPTH_BUFFER_BIT;
   if (info->b.mask & PIPE_MASK_S)
      glmask |= GL_STENCIL_BUFFER_BIT;
   if (info->b.mask & PIPE_MASK_RGBA)
      glmask |= GL_COLOR_BUFFER_BIT;


   if (info->b.scissor_enable) {
      glScissor(info->b.scissor.minx, info->b.scissor.miny,
                info->b.scissor.maxx - info->b.scissor.minx,
                info->b.scissor.maxy - info->b.scissor.miny);
      ctx->sub->scissor_state_dirty = (1 << 0);
      glEnable(GL_SCISSOR_TEST);
   } else
      glDisable(GL_SCISSOR_TEST);

   /* An GLES GL_INVALID_OPERATION is generated if one wants to blit from a
    * multi-sample fbo to a non multi-sample fbo and the source and destination
    * rectangles are not defined with the same (X0, Y0) and (X1, Y1) bounds.
    *
    * Since stencil data can only be written in a fragment shader when
    * ARB_shader_stencil_export is available, the workaround using GL as given
    * above is usually not available. Instead, to work around the blit
    * limitations on GLES first copy the full frame to a non-multisample
    * surface and then copy the according area to the final target surface.
    */
   bool make_intermediate_copy = false;
   GLuint intermediate_fbo = 0;
   struct vrend_resource *intermediate_copy = 0;

   if (vrend_state.use_gles &&
       (info->b.mask & PIPE_MASK_ZS) &&
       ((src_res->base.nr_samples > 1) &&
        (src_res->base.nr_samples != dst_res->base.nr_samples)) &&
        ((info->b.src.box.x != info->b.dst.box.x) ||
         (info->src_y1 != info->dst_y1) ||
         (info->b.src.box.width != info->b.dst.box.width) ||
         (info->src_y2 != info->dst_y2))) {

      make_intermediate_copy = true;

      /* Create a texture that is the same like the src_res texture, but
       * without multi-sample */
      struct vrend_renderer_resource_create_args args;
      memset(&args, 0, sizeof(struct vrend_renderer_resource_create_args));
      args.width = src_res->base.width0;
      args.height = src_res->base.height0;
      args.depth = src_res->base.depth0;
      args.format = info->b.src.format;
      args.target = src_res->base.target;
      args.last_level = src_res->base.last_level;
      args.array_size = src_res->base.array_size;
      intermediate_copy = (struct vrend_resource *)CALLOC_STRUCT(vrend_texture);
      vrend_renderer_resource_copy_args(&args, intermediate_copy);
      /* this is PIPE_MASK_ZS and bgra fixup is not needed */
      ASSERTED int r = vrend_resource_alloc_texture(intermediate_copy, args.format, NULL);
      assert(!r);

      glGenFramebuffers(1, &intermediate_fbo);
   } else {
      /* If no intermediate copy is needed make the variables point to the
       * original source to simplify the code below.
       */
      intermediate_fbo = ctx->sub->blit_fb_ids[0];
      intermediate_copy = src_res;
   }

   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);
   if (info->b.mask & PIPE_MASK_RGBA)
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                             GL_TEXTURE_2D, 0, 0);
   else
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                             GL_TEXTURE_2D, 0, 0);
   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[1]);
   if (info->b.mask & PIPE_MASK_RGBA)
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                             GL_TEXTURE_2D, 0, 0);
   else if (info->b.mask & (PIPE_MASK_Z | PIPE_MASK_S))
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                             GL_TEXTURE_2D, 0, 0);

   int n_layers = info->b.src.box.depth == info->b.dst.box.depth ? info->b.dst.box.depth : 1;
   for (int i = 0; i < n_layers; i++) {
      glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);
      vrend_fb_bind_texture_id(src_res, info->src_view, 0, info->b.src.level, info->b.src.box.z + i, 0);

      if (make_intermediate_copy) {
         int level_width = u_minify(src_res->base.width0, info->b.src.level);
         int level_height = u_minify(src_res->base.width0, info->b.src.level);
         glBindFramebuffer(GL_FRAMEBUFFER, intermediate_fbo);
         glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                GL_TEXTURE_2D, 0, 0);
         vrend_fb_bind_texture(intermediate_copy, 0, info->b.src.level, info->b.src.box.z + i);

         glBindFramebuffer(GL_DRAW_FRAMEBUFFER, intermediate_fbo);
         glBindFramebuffer(GL_READ_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);
         glBlitFramebuffer(0, 0, level_width, level_height,
                           0, 0, level_width, level_height,
                           glmask, info->gl_filter);
      }

      glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[1]);
      vrend_fb_bind_texture_id(dst_res, info->dst_view, 0, info->b.dst.level, info->b.dst.box.z + i, 0);
      glBindFramebuffer(GL_DRAW_FRAMEBUFFER, ctx->sub->blit_fb_ids[1]);

      if (has_feature(feat_srgb_write_control)) {
         if (util_format_is_srgb(info->b.dst.format) ||
             util_format_is_srgb(info->b.src.format))
            glEnable(GL_FRAMEBUFFER_SRGB);
         else
            glDisable(GL_FRAMEBUFFER_SRGB);
      }

      glBindFramebuffer(GL_READ_FRAMEBUFFER, intermediate_fbo);

      glBlitFramebuffer(info->b.src.box.x,
                        info->src_y1,
                        info->b.src.box.x + info->b.src.box.width,
                        info->src_y2,
                        info->b.dst.box.x,
                        info->dst_y1,
                        info->b.dst.box.x + info->b.dst.box.width,
                        info->dst_y2,
                        glmask, info->gl_filter);
   }

   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[1]);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                          GL_TEXTURE_2D, 0, 0);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                          GL_TEXTURE_2D, 0, 0);

   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->blit_fb_ids[0]);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                          GL_TEXTURE_2D, 0, 0);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                          GL_TEXTURE_2D, 0, 0);

   glBindFramebuffer(GL_FRAMEBUFFER, ctx->sub->fb_id);

   if (has_feature(feat_srgb_write_control)) {
      if (ctx->sub->framebuffer_srgb_enabled)
         glEnable(GL_FRAMEBUFFER_SRGB);
      else
         glDisable(GL_FRAMEBUFFER_SRGB);
   }

   if (make_intermediate_copy) {
      vrend_renderer_resource_destroy(intermediate_copy);
      glDeleteFramebuffers(1, &intermediate_fbo);
   }

   if (ctx->sub->rs_state.scissor)
      glEnable(GL_SCISSOR_TEST);
   else
      glDisable(GL_SCISSOR_TEST);

}

static void vrend_renderer_blit_int(struct vrend_context *ctx,
                                    struct vrend_resource *src_res,
                                    struct vrend_resource *dst_res,
                                    const struct pipe_blit_info *info)
{
   struct vrend_blit_info blit_info = {
      .b = *info,
      .src_view = src_res->gl_id,
      .dst_view = dst_res->gl_id,
      .swizzle =  {0, 1, 2, 3}
   };

   /* We create the texture views in this function instead of doing it in
    * vrend_renderer_prepare_blit_extra_info because we also delete them here */
   if ((src_res->base.format != info->src.format) && has_feature(feat_texture_view) &&
       vrend_resource_supports_view(src_res, info->src.format))
      blit_info.src_view = vrend_make_view(src_res, info->src.format);

   if ((dst_res->base.format != info->dst.format) && has_feature(feat_texture_view) &&
       vrend_resource_supports_view(dst_res, info->dst.format))
      blit_info.dst_view = vrend_make_view(dst_res, info->dst.format);

   vrend_renderer_prepare_blit_extra_info(ctx, src_res, dst_res, &blit_info);

   if (vrend_renderer_prepare_blit(ctx, src_res, dst_res, &blit_info)) {
      VREND_DEBUG(dbg_blit, ctx, "BLIT_INT: use FBO blit\n");
      vrend_renderer_blit_fbo(ctx, src_res, dst_res, &blit_info);
   } else {
      blit_info.has_srgb_write_control = has_feature(feat_texture_srgb_decode);
      blit_info.has_texture_srgb_decode = has_feature(feat_srgb_write_control);

      VREND_DEBUG(dbg_blit, ctx, "BLIT_INT: use GL fallback\n");
      vrend_renderer_blit_gl(ctx, src_res, dst_res, &blit_info);
      vrend_sync_make_current(ctx->sub->gl_context);
   }

   if (blit_info.src_view != src_res->gl_id)
      glDeleteTextures(1, &blit_info.src_view);

   if (blit_info.dst_view != dst_res->gl_id)
      glDeleteTextures(1, &blit_info.dst_view);
}

void vrend_renderer_blit(struct vrend_context *ctx,
                         uint32_t dst_handle, uint32_t src_handle,
                         const struct pipe_blit_info *info)
{
   unsigned int comp_flags = 0;
   struct vrend_resource *src_res, *dst_res;
   int src_width, src_height, dst_width, dst_height;
   src_res = vrend_renderer_ctx_res_lookup(ctx, src_handle);
   dst_res = vrend_renderer_ctx_res_lookup(ctx, dst_handle);

   if (!src_res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, src_handle);
      return;
   }
   if (!dst_res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, dst_handle);
      return;
   }

   if (ctx->in_error)
      return;

   if (unlikely(info->src.format >= VIRGL_FORMAT_MAX ||
                tex_conv_table[info->src.format].format == VIRGL_FORMAT_NONE)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_FORMAT, info->src.format);
      return;
   }

   if (unlikely(info->dst.format >= VIRGL_FORMAT_MAX ||
                tex_conv_table[info->dst.format].format == VIRGL_FORMAT_NONE)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_FORMAT, info->dst.format);
      return;
   }

   if ((src_res->base.target == PIPE_TEXTURE_CUBE) && (info->src.box.depth + info->src.box.z  - 1) >= 6) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_CUBE_MAP_FACE_OUT_OF_RANGE,
                                 info->src.box.depth + info->src.box.z - 1);
      return;
   }

   if (dst_res->base.target == PIPE_TEXTURE_CUBE && info->dst.box.depth + info->dst.box.z - 1 >= 6) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_CUBE_MAP_FACE_OUT_OF_RANGE,
                                 info->src.box.depth + info->src.box.z - 1);
      return;
   }

   /* Check that we are actually blitting into the destination texture, and make sure that we
    * don't end up in a long loop because we have an invalid dst.box.z.
    */
   int dst_depth = MAX2(u_minify(dst_res->base.depth0, info->dst.level), dst_res->base.array_size);
   if (info->dst.box.depth > dst_depth || info->dst.box.z > dst_depth) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_BLIT_AREA_OUT_OF_RANGE,
                                 info->dst.box.depth);
      return;
   }

   if (info->render_condition_enable == false)
      vrend_pause_render_condition(ctx, true);

   VREND_DEBUG(dbg_blit, ctx, "BLIT: rc:%d scissor:%d filter:%d alpha:%d mask:0x%x\n"
                                   "  From %s(%s) ms:%d egl:%d gbm:%d [%d, %d, %d]+[%d, %d, %d] lvl:%d\n"
                                   "  To   %s(%s) ms:%d egl:%d gbm:%d [%d, %d, %d]+[%d, %d, %d] lvl:%d\n",
                                   info->render_condition_enable, info->scissor_enable,
                                   info->filter, info->alpha_blend, info->mask,
                                   util_format_name(src_res->base.format),
                                   util_format_name(info->src.format),
                                   src_res->base.nr_samples,
                                   has_bit(src_res->storage_bits, VREND_STORAGE_EGL_IMAGE),
                                   has_bit(src_res->storage_bits, VREND_STORAGE_GBM_BUFFER),
                                   info->src.box.x, info->src.box.y, info->src.box.z,
                                   info->src.box.width, info->src.box.height, info->src.box.depth,
                                   info->src.level,
                                   util_format_name(dst_res->base.format),
                                   util_format_name(info->dst.format),
                                   dst_res->base.nr_samples,
                                   has_bit(dst_res->storage_bits, VREND_STORAGE_EGL_IMAGE),
                                   has_bit(dst_res->storage_bits, VREND_STORAGE_GBM_BUFFER),
                                   info->dst.box.x, info->dst.box.y, info->dst.box.z,
                                   info->dst.box.width, info->dst.box.height, info->dst.box.depth,
                                   info->dst.level);

   if (src_res->egl_image)
      comp_flags |= VREND_COPY_COMPAT_FLAG_ONE_IS_EGL_IMAGE;
   if (dst_res->egl_image)
      comp_flags ^= VREND_COPY_COMPAT_FLAG_ONE_IS_EGL_IMAGE;

   /* resources that don't support texture views but require colorspace conversion
    * must have it applied manually in a shader, i.e. require following the
    * vrend_renderer_blit_int() path. */
   bool eglimage_copy_compatible =
      !(vrend_resource_needs_srgb_decode(src_res, info->src.format) ||
        vrend_resource_needs_srgb_encode(dst_res, info->dst.format));

   src_width  = u_minify(src_res->base.width0,  info->src.level);
   src_height = u_minify(src_res->base.height0, info->src.level);
   dst_width  = u_minify(dst_res->base.width0,  info->dst.level);
   dst_height = u_minify(dst_res->base.height0, info->dst.level);

   /* The Gallium blit function can be called for a general blit that may
    * scale, convert the data, and apply some rander states, or it is called via
    * glCopyImageSubData. If the src or the dst image are equal, or the two
    * images formats are the same, then Galliums such calles are redirected
    * to resource_copy_region, in this case and if no render states etx need
    * to be applied, forward the call to glCopyImageSubData, otherwise do a
    * normal blit. */
   if (has_feature(feat_copy_image) &&
       (!info->render_condition_enable || !ctx->sub->cond_render_gl_mode) &&
       format_is_copy_compatible(info->src.format,info->dst.format, comp_flags) &&
       eglimage_copy_compatible &&
       !info->scissor_enable && (info->filter == PIPE_TEX_FILTER_NEAREST) &&
       !info->alpha_blend && (info->mask == PIPE_MASK_RGBA) &&
       src_res->base.nr_samples == dst_res->base.nr_samples &&
       info->src.box.x + info->src.box.width  <= src_width &&
       info->dst.box.x + info->dst.box.width  <= dst_width &&
       info->src.box.y + info->src.box.height <= src_height &&
       info->dst.box.y + info->dst.box.height <= dst_height &&
       info->src.box.width == info->dst.box.width &&
       info->src.box.height == info->dst.box.height &&
       info->src.box.depth == info->dst.box.depth) {
      VREND_DEBUG(dbg_blit, ctx,  "  Use glCopyImageSubData\n");
      vrend_copy_sub_image(src_res, dst_res, info->src.level, &info->src.box,
                           info->dst.level, info->dst.box.x, info->dst.box.y,
                           info->dst.box.z);
   } else {
      VREND_DEBUG(dbg_blit, ctx, "  Use blit_int\n");
      vrend_renderer_blit_int(ctx, src_res, dst_res, info);
   }

   if (info->render_condition_enable == false)
      vrend_pause_render_condition(ctx, false);
}

void vrend_renderer_set_fence_retire(struct vrend_context *ctx,
                                     vrend_context_fence_retire retire,
                                     void *retire_data)
{
   assert(ctx->ctx_id);
   ctx->fence_retire = retire;
   ctx->fence_retire_data = retire_data;
}

int vrend_renderer_create_fence(struct vrend_context *ctx,
                                uint32_t flags,
                                uint64_t fence_id)
{
   struct vrend_fence *fence;

   fence = malloc(sizeof(struct vrend_fence));
   if (!fence)
      return ENOMEM;

   fence->ctx = ctx;
   fence->flags = flags;
   fence->fence_id = fence_id;

#ifdef HAVE_EPOXY_EGL_H
   if (vrend_state.use_egl_fence) {
      fence->eglsyncobj = virgl_egl_fence_create(egl);
   } else
#endif
   {
      fence->glsyncobj = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
   }
   glFlush();

   if (fence->glsyncobj == NULL)
      goto fail;

   if (vrend_state.sync_thread) {
      mtx_lock(&vrend_state.fence_mutex);
      list_addtail(&fence->fences, &vrend_state.fence_wait_list);
      cnd_signal(&vrend_state.fence_cond);
      mtx_unlock(&vrend_state.fence_mutex);
   } else {
      list_addtail(&fence->fences, &vrend_state.fence_list);
   }

#ifdef HAVE_EPOXY_EGL_H
   int fence_fd = -1;
   if (vrend_renderer_export_ctx0_fence(fence_id, &fence_fd) == 0 &&
       virgl_fence_set_fd(fence_id, fence_fd))
      virgl_error("failed to export fence sync object\n");
   if (fence_fd != -1)
      close(fence_fd);
#endif
   return 0;

 fail:
   virgl_error("Failed to create fence sync object\n");
   free(fence);
   return ENOMEM;
}

static bool need_fence_retire_signal_locked(struct vrend_fence *fence,
                                            const struct list_head *signaled_list)
{
   struct vrend_fence *next;

   /* last fence */
   if (fence->fences.next == signaled_list)
      return true;

   /* next fence belongs to a different context */
   next = LIST_ENTRY(struct vrend_fence, fence->fences.next, fences);
   if (next->ctx != fence->ctx)
      return true;

   /* not mergeable */
   if (!(fence->flags & VIRGL_RENDERER_FENCE_FLAG_MERGEABLE))
      return true;

   return false;
}

void vrend_renderer_check_fences(void)
{
   struct list_head retired_fences;

   assert(!vrend_state.use_async_fence_cb);

   list_inithead(&retired_fences);

   if (vrend_state.sync_thread) {
      flush_eventfd(vrend_state.eventfd);
      mtx_lock(&vrend_state.fence_mutex);
      list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_list, fences) {
         /* vrend_free_fences_for_context might have marked the fence invalid
          * by setting fence->ctx to NULL
          */
         if (!fence->ctx) {
            free_fence_locked(fence);
            continue;
         }

         if (need_fence_retire_signal_locked(fence, &vrend_state.fence_list)) {
            list_del(&fence->fences);
            list_addtail(&fence->fences, &retired_fences);
         } else {
            free_fence_locked(fence);
         }
      }
      mtx_unlock(&vrend_state.fence_mutex);
   } else {
      vrend_renderer_force_ctx_0();

      list_for_each_entry_safe(struct vrend_fence, fence, &vrend_state.fence_list, fences) {
         if (do_wait(fence, /* can_block */ false)) {
            list_del(&fence->fences);
            list_addtail(&fence->fences, &retired_fences);
         } else {
            /* don't bother checking any subsequent ones */
            break;
         }
      }

      list_for_each_entry_safe(struct vrend_fence, fence, &retired_fences, fences) {
         if (!need_fence_retire_signal_locked(fence, &retired_fences))
            free_fence_locked(fence);
      }
   }

   if (list_is_empty(&retired_fences))
      return;

   vrend_renderer_check_queries();

   list_for_each_entry_safe(struct vrend_fence, fence, &retired_fences, fences) {
      struct vrend_context *ctx = fence->ctx;
      ctx->fence_retire(fence->fence_id, ctx->fence_retire_data);

      free_fence_locked(fence);
   }
}

static bool vrend_get_one_query_result(GLuint query_id, bool use_64, uint64_t *result)
{
   GLuint ready;
   GLuint passed;
   GLuint64 pass64;

   glGetQueryObjectuiv(query_id, GL_QUERY_RESULT_AVAILABLE_ARB, &ready);

   if (!ready)
      return false;

   if (use_64) {
      glGetQueryObjectui64v(query_id, GL_QUERY_RESULT_ARB, &pass64);
      *result = pass64;
   } else {
      glGetQueryObjectuiv(query_id, GL_QUERY_RESULT_ARB, &passed);
      *result = passed;
   }
   return true;
}

static inline void
vrend_update_oq_samples_multiplier(struct vrend_context *ctx)
{
   if (!ctx->sub->fake_occlusion_query_samples_passed_multiplier) {
      uint32_t multiplier = 0;
      bool tweaked = vrend_get_tweak_is_active_with_params(vrend_get_context_tweaks(ctx),
                                                           virgl_tweak_gles_tf3_samples_passes_multiplier, &multiplier);
      ctx->sub->fake_occlusion_query_samples_passed_multiplier =
            tweaked ? multiplier: fake_occlusion_query_samples_passed_default;
   }
}


static bool vrend_check_query(struct vrend_query *query)
{
   struct virgl_host_query_state state;
   bool ret;

   state.result_size = vrend_is_timer_query(query->gltype) ? 8 : 4;
   ret = vrend_get_one_query_result(query->id, state.result_size == 8,
         &state.result);
   if (ret == false)
      return false;

   /* We got a boolean, but the client wanted the actual number of samples
    * blow the number up so that the client doesn't think it was just one pixel
    * and discards an object that might be bigger */
   if (query->fake_samples_passed) {
      vrend_update_oq_samples_multiplier(query->ctx);
      state.result *= query->ctx->sub->fake_occlusion_query_samples_passed_multiplier;
   }

   state.query_state = VIRGL_QUERY_STATE_DONE;

   if (query->res->iov) {
      if (vrend_write_to_iovec(query->res->iov, query->res->num_iovs, 0,
                               (const void *) &state, sizeof(state)) != sizeof(state))
         virgl_error("Query state does not fit IOV size\n");
   } else {
      if (query->res->base.width0 >= sizeof(state) )
         *((struct virgl_host_query_state *) query->res->ptr) = state;
      else
         virgl_error("Query state does not fit buffer size\n");
   }

   return true;
}

static struct vrend_sub_context *vrend_renderer_find_sub_ctx(struct vrend_context *ctx,
                                                             int sub_ctx_id)
{
   if (ctx->sub && ctx->sub->sub_ctx_id == sub_ctx_id)
      return ctx->sub;

   list_for_each_entry_safe(struct vrend_sub_context, sub, &ctx->sub_ctxs, head) {
      if (sub->sub_ctx_id == sub_ctx_id)
         return sub;
   }

   return NULL;
}

static bool vrend_hw_switch_context_with_sub(struct vrend_context *ctx, int sub_ctx_id)
{
   if (!ctx)
      return false;

   if (ctx == vrend_state.current_ctx && sub_ctx_id == ctx->sub->sub_ctx_id &&
       ctx->ctx_switch_pending == false) {
      return true;
   }

   if (ctx->ctx_id != 0 && ctx->in_error)
      return false;

   struct vrend_sub_context *sub = vrend_renderer_find_sub_ctx(ctx, sub_ctx_id);
   if (!sub)
      return false;

   /* force the gl context switch to occur */
   if (ctx->sub != sub) {
      vrend_state.current_hw_ctx = NULL;
      ctx->sub = sub;
   }

   ctx->ctx_switch_pending = true;
   vrend_finish_context_switch(ctx);

   vrend_state.current_ctx = ctx;
   return true;
}

static void vrend_renderer_check_queries(void)
{
   list_for_each_entry_safe(struct vrend_query, query, &vrend_state.waiting_query_list, waiting_queries) {
      if (!vrend_hw_switch_context_with_sub(query->ctx, query->sub_ctx_id)) {
         virgl_warn("Failed to switch to context (%d) with sub (%d) for query %u\n",
                      query->ctx->ctx_id, query->sub_ctx_id, query->id);
      }
      else if (!vrend_check_query(query)) {
         continue;
      }

      list_delinit(&query->waiting_queries);
   }

   atomic_store(&vrend_state.has_waiting_queries,
                !list_is_empty(&vrend_state.waiting_query_list));
}

bool vrend_hw_switch_context(struct vrend_context *ctx, bool now)
{
   if (!ctx)
      return false;

   if (ctx == vrend_state.current_ctx && ctx->ctx_switch_pending == false)
      return true;

   if (ctx->ctx_id != 0 && ctx->in_error) {
      return false;
   }

   ctx->ctx_switch_pending = true;
   if (now == true) {
      vrend_finish_context_switch(ctx);
   }
   vrend_state.current_ctx = ctx;
   return true;
}

static void vrend_finish_context_switch(struct vrend_context *ctx)
{
   if (ctx->ctx_switch_pending == false)
      return;
   ctx->ctx_switch_pending = false;

   if (vrend_state.current_hw_ctx == ctx)
      return;

   vrend_state.current_hw_ctx = ctx;

   vrend_clicbs->make_current(ctx->sub->gl_context);
}

void
vrend_renderer_object_destroy(struct vrend_context *ctx, uint32_t handle)
{
   vrend_object_remove(ctx->sub->object_hash, handle, 0);
}

uint32_t vrend_renderer_object_insert(struct vrend_context *ctx, void *data,
                                      uint32_t handle, enum virgl_object_type type)
{
   return vrend_object_insert(ctx->sub->object_hash, data, handle, type);
}

static uint32_t query_stats_index_to_gl_map[] = {
   [VIRGL_STAT_QUERY_IA_VERTICES] = GL_VERTICES_SUBMITTED_ARB,
   [VIRGL_STAT_QUERY_IA_PRIMITIVES] = GL_PRIMITIVES_SUBMITTED_ARB,
   [VIRGL_STAT_QUERY_VS_INVOCATIONS] = GL_VERTEX_SHADER_INVOCATIONS_ARB,
   [VIRGL_STAT_QUERY_GS_INVOCATIONS] = GL_GEOMETRY_SHADER_INVOCATIONS,
   [VIRGL_STAT_QUERY_GS_PRIMITIVES] = GL_GEOMETRY_SHADER_PRIMITIVES_EMITTED_ARB,
   [VIRGL_STAT_QUERY_C_INVOCATIONS] = GL_CLIPPING_INPUT_PRIMITIVES_ARB,
   [VIRGL_STAT_QUERY_C_PRIMITIVES] = GL_CLIPPING_OUTPUT_PRIMITIVES_ARB,
   [VIRGL_STAT_QUERY_PS_INVOCATIONS] = GL_FRAGMENT_SHADER_INVOCATIONS_ARB,
   [VIRGL_STAT_QUERY_HS_INVOCATIONS] = GL_TESS_CONTROL_SHADER_PATCHES_ARB,
   [VIRGL_STAT_QUERY_DS_INVOCATIONS] = GL_TESS_EVALUATION_SHADER_INVOCATIONS_ARB,
   [VIRGL_STAT_QUERY_CS_INVOCATIONS] = GL_COMPUTE_SHADER_INVOCATIONS_ARB,
};

int vrend_create_query(struct vrend_context *ctx, uint32_t handle,
                       uint32_t query_type, uint32_t query_index,
                       uint32_t res_handle, UNUSED uint32_t offset)
{
   bool fake_samples_passed = false;
   struct vrend_resource *res = vrend_renderer_ctx_res_lookup(ctx, res_handle);
   if (!res || !has_bit(res->storage_bits, VREND_STORAGE_HOST_SYSTEM_MEMORY)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
      return EINVAL;
   }

   /* If we don't have ARB_occlusion_query, at least try to fake GL_SAMPLES_PASSED
    * by using GL_ANY_SAMPLES_PASSED (i.e. EXT_occlusion_query_boolean) */
   if (!has_feature(feat_occlusion_query) && query_type == PIPE_QUERY_OCCLUSION_COUNTER) {
      VREND_DEBUG(dbg_query, ctx, "GL_SAMPLES_PASSED not supported will try GL_ANY_SAMPLES_PASSED\n");
      query_type = PIPE_QUERY_OCCLUSION_PREDICATE;
      fake_samples_passed = true;
   }

   if (query_type == PIPE_QUERY_OCCLUSION_PREDICATE &&
       !has_feature(feat_occlusion_query_boolean)) {
      vrend_report_context_error(ctx, VIRGL_ERROR_GL_ANY_SAMPLES_PASSED, res_handle);
      return EINVAL;
   }

   struct vrend_query *q = CALLOC_STRUCT(vrend_query);
   if (!q)
      return ENOMEM;

   int err = 0;

   list_inithead(&q->waiting_queries);
   q->type = query_type;
   q->index = query_index;
   q->ctx = ctx;
   q->sub_ctx_id = ctx->sub->sub_ctx_id;
   q->fake_samples_passed = fake_samples_passed;

   vrend_resource_reference(&q->res, res);

   switch (q->type) {
   case PIPE_QUERY_OCCLUSION_COUNTER:
      q->gltype = GL_SAMPLES_PASSED_ARB;
      break;
   case PIPE_QUERY_OCCLUSION_PREDICATE:
      if (has_feature(feat_occlusion_query_boolean))
         q->gltype = GL_ANY_SAMPLES_PASSED;
      else
         err = EINVAL;
      break;
   case PIPE_QUERY_TIMESTAMP:
      if (has_feature(feat_timer_query))
         q->gltype = GL_TIMESTAMP;
      else
         err = EINVAL;
      break;
   case PIPE_QUERY_TIME_ELAPSED:
      if (has_feature(feat_timer_query))
         q->gltype = GL_TIME_ELAPSED;
      else
         err = EINVAL;
      break;
   case PIPE_QUERY_PRIMITIVES_GENERATED:
      q->gltype = GL_PRIMITIVES_GENERATED;
      break;
   case PIPE_QUERY_PRIMITIVES_EMITTED:
      q->gltype = GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN;
      break;
   case PIPE_QUERY_OCCLUSION_PREDICATE_CONSERVATIVE:
      q->gltype = GL_ANY_SAMPLES_PASSED_CONSERVATIVE;
      break;
   case PIPE_QUERY_SO_OVERFLOW_PREDICATE:
      if (has_feature(feat_transform_feedback_overflow_query))
         q->gltype = GL_TRANSFORM_FEEDBACK_STREAM_OVERFLOW_ARB;
      else
         err = EINVAL;
      break;
   case PIPE_QUERY_SO_OVERFLOW_ANY_PREDICATE:
      if (has_feature(feat_transform_feedback_overflow_query))
         q->gltype = GL_TRANSFORM_FEEDBACK_OVERFLOW_ARB;
      else
         err = EINVAL;
      break;
   case PIPE_QUERY_PIPELINE_STATISTICS:
      if (has_feature(feat_pipeline_statistics_query)) {
         /* The guest sends the GL pipeline statistics query type in the index */
         if (q->index >= ARRAY_SIZE(query_stats_index_to_gl_map)) {
            err = EINVAL;
            break;
         }
         q->gltype = query_stats_index_to_gl_map[q->index];
         /* Reset index to avoid calling the indexed versions of the query calls
          * which refer to true indices and not to pipeline stats */
         q->index = 0;
      } else
         err = EINVAL;
      break;
   default:
      virgl_warn("Unknown query object received %d\n", q->type);
      break;
   }

   if (!err) {
      glGenQueries(1, &q->id);
      if (!vrend_renderer_object_insert(ctx, q, handle, VIRGL_OBJECT_QUERY)) {
         glDeleteQueries(1, &q->id);
         err = ENOMEM;
      }
   }

   if (err) {
      vrend_resource_reference(&q->res, NULL);
      FREE(q);
   }

   return err;
}

static void vrend_destroy_query(struct vrend_query *query)
{
   vrend_resource_reference(&query->res, NULL);
   list_del(&query->waiting_queries);
   glDeleteQueries(1, &query->id);
   free(query);
}

static void vrend_destroy_query_object(void *obj_ptr)
{
   struct vrend_query *query = obj_ptr;
   vrend_destroy_query(query);
}

int vrend_begin_query(struct vrend_context *ctx, uint32_t handle)
{
   struct vrend_query *q;

   q = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_QUERY);
   if (!q)
      return EINVAL;

   if (q->index > 0 && !has_feature(feat_transform_feedback3))
      return EINVAL;

   list_delinit(&q->waiting_queries);

   if (q->gltype == GL_TIMESTAMP)
      return 0;

   if (q->index > 0)
      glBeginQueryIndexed(q->gltype, q->index, q->id);
   else
      glBeginQuery(q->gltype, q->id);
   return 0;
}

int vrend_end_query(struct vrend_context *ctx, uint32_t handle)
{
   struct vrend_query *q;
   q = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_QUERY);
   if (!q)
      return EINVAL;

   if (q->index > 0 && !has_feature(feat_transform_feedback3))
      return EINVAL;

   if (vrend_is_timer_query(q->gltype)) {
      if (q->gltype == GL_TIMESTAMP && !has_feature(feat_timer_query)) {
         report_gles_warn(ctx, GLES_WARN_TIMESTAMP);
      } else if (q->gltype == GL_TIMESTAMP) {
         glQueryCounter(q->id, q->gltype);
      } else {
         /* remove from active query list for this context */
         glEndQuery(q->gltype);
      }
      return 0;
   }

   if (q->index > 0)
      glEndQueryIndexed(q->gltype, q->index);
   else
      glEndQuery(q->gltype);
   return 0;
}

int vrend_get_query_result(struct vrend_context *ctx, uint32_t handle,
                            UNUSED uint32_t wait)
{
   struct vrend_query *q;
   bool ret;

   q = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_QUERY);
   if (!q)
      return EINVAL;

   ret = vrend_check_query(q);
   if (ret) {
      list_delinit(&q->waiting_queries);
   } else if (list_is_empty(&q->waiting_queries)) {
      list_addtail(&q->waiting_queries, &vrend_state.waiting_query_list);
   }

   atomic_store(&vrend_state.has_waiting_queries,
                !list_is_empty(&vrend_state.waiting_query_list));
   return 0;
}

#define COPY_QUERY_RESULT_TO_BUFFER(resid, offset, pvalue, size, multiplier) \
    glBindBuffer(GL_QUERY_BUFFER, resid); \
    value *= multiplier; \
    void* buf = glMapBufferRange(GL_QUERY_BUFFER, offset, size, GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT); \
    if (buf) memcpy(buf, &value, size); \
    glUnmapBuffer(GL_QUERY_BUFFER);

static inline void *buffer_offset(intptr_t i)
{
   return (void *)i;
}

int vrend_get_query_result_qbo(struct vrend_context *ctx, uint32_t handle,
                                uint32_t qbo_handle,
                                uint32_t wait, uint32_t result_type, uint32_t offset,
                                int32_t index)
{
  struct vrend_query *q;
  struct vrend_resource *res;

  if (!has_feature(feat_qbo))
     return EINVAL;

  q = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_QUERY);
  if (!q)
     return EINVAL;

  res = vrend_renderer_ctx_res_lookup(ctx, qbo_handle);
  if (!res || !res->gl_id) {
     vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, qbo_handle);
     return EINVAL;
  }

  VREND_DEBUG(dbg_query, ctx, "Get query result from Query:%d\n", q->id);

  GLenum qtype;

  if (index == -1)
     qtype = GL_QUERY_RESULT_AVAILABLE;
  else
     qtype = wait ? GL_QUERY_RESULT : GL_QUERY_RESULT_NO_WAIT;

  if (!q->fake_samples_passed) {
     glBindBuffer(GL_QUERY_BUFFER, res->gl_id);
     switch ((enum pipe_query_value_type)result_type) {
     case PIPE_QUERY_TYPE_I32:
        glGetQueryObjectiv(q->id, qtype, buffer_offset(offset));
        break;
     case PIPE_QUERY_TYPE_U32:
        glGetQueryObjectuiv(q->id, qtype, buffer_offset(offset));
        break;
     case PIPE_QUERY_TYPE_I64:
        glGetQueryObjecti64v(q->id, qtype, buffer_offset(offset));
        break;
     case PIPE_QUERY_TYPE_U64:
        glGetQueryObjectui64v(q->id, qtype, buffer_offset(offset));
        break;
     }
  } else {
     VREND_DEBUG(dbg_query, ctx, "Was emulating GL_PIXELS_PASSED by GL_ANY_PIXELS_PASSED, artifically upscaling the result\n");
     /* The application expects a sample count but we have only a boolean
      * so we blow the result up by 1/10 of the screen space to make sure the
      * app doesn't think only one sample passed. */
     vrend_update_oq_samples_multiplier(ctx);
     switch ((enum pipe_query_value_type)result_type) {
     case PIPE_QUERY_TYPE_I32: {
        GLint value;
        glGetQueryObjectiv(q->id, qtype, &value);
        COPY_QUERY_RESULT_TO_BUFFER(q->id, offset, value, 4, ctx->sub->fake_occlusion_query_samples_passed_multiplier);
        break;
     }
     case PIPE_QUERY_TYPE_U32: {
        GLuint value;
        glGetQueryObjectuiv(q->id, qtype, &value);
        COPY_QUERY_RESULT_TO_BUFFER(q->id, offset, value, 4, ctx->sub->fake_occlusion_query_samples_passed_multiplier);
        break;
     }
     case PIPE_QUERY_TYPE_I64: {
        GLint64 value;
        glGetQueryObjecti64v(q->id, qtype, &value);
        COPY_QUERY_RESULT_TO_BUFFER(q->id, offset, value, 8, ctx->sub->fake_occlusion_query_samples_passed_multiplier);
        break;
     }
     case PIPE_QUERY_TYPE_U64: {
        GLuint64 value;
        glGetQueryObjectui64v(q->id, qtype, &value);
        COPY_QUERY_RESULT_TO_BUFFER(q->id, offset, value, 8, ctx->sub->fake_occlusion_query_samples_passed_multiplier);
        break;
     }
     }


  }

  glBindBuffer(GL_QUERY_BUFFER, 0);

  return 0;
}

static void vrend_pause_render_condition(struct vrend_context *ctx, bool pause)
{
   if (pause) {
      if (ctx->sub->cond_render_q_id) {
         if (has_feature(feat_gl_conditional_render))
            glEndConditionalRender();
         else if (has_feature(feat_nv_conditional_render))
            glEndConditionalRenderNV();
      }
   } else {
      if (ctx->sub->cond_render_q_id) {
         if (has_feature(feat_gl_conditional_render))
            glBeginConditionalRender(ctx->sub->cond_render_q_id,
                                     ctx->sub->cond_render_gl_mode);
         else if (has_feature(feat_nv_conditional_render))
            glBeginConditionalRenderNV(ctx->sub->cond_render_q_id,
                                       ctx->sub->cond_render_gl_mode);
      }
   }
}

void vrend_render_condition(struct vrend_context *ctx,
                            uint32_t handle,
                            bool condition,
                            uint32_t mode)
{
   struct vrend_query *q;
   GLenum glmode = 0;

   if (handle == 0) {
      if (ctx->sub->cond_render_q_id != 0) {
         if (has_feature(feat_gl_conditional_render))
            glEndConditionalRender();
         else if (has_feature(feat_nv_conditional_render))
            glEndConditionalRenderNV();
      }
      ctx->sub->cond_render_q_id = 0;
      ctx->sub->cond_render_gl_mode = 0;
      return;
   }

   q = vrend_object_lookup(ctx->sub->object_hash, handle, VIRGL_OBJECT_QUERY);
   if (!q)
      return;

   if (condition && !has_feature(feat_conditional_render_inverted))
      return;
   switch (mode) {
   case PIPE_RENDER_COND_WAIT:
      glmode = condition ? GL_QUERY_WAIT_INVERTED : GL_QUERY_WAIT;
      break;
   case PIPE_RENDER_COND_NO_WAIT:
      glmode = condition ? GL_QUERY_NO_WAIT_INVERTED : GL_QUERY_NO_WAIT;
      break;
   case PIPE_RENDER_COND_BY_REGION_WAIT:
      glmode = condition ? GL_QUERY_BY_REGION_WAIT_INVERTED : GL_QUERY_BY_REGION_WAIT;
      break;
   case PIPE_RENDER_COND_BY_REGION_NO_WAIT:
      glmode = condition ? GL_QUERY_BY_REGION_NO_WAIT_INVERTED : GL_QUERY_BY_REGION_NO_WAIT;
      break;
   default:
      virgl_warn("Unhandled condition %x\n", mode);
   }

   ctx->sub->cond_render_q_id = q->id;
   ctx->sub->cond_render_gl_mode = glmode;
   if (has_feature(feat_gl_conditional_render))
      glBeginConditionalRender(q->id, glmode);
   else if (has_feature(feat_nv_conditional_render))
      glBeginConditionalRenderNV(q->id, glmode);
}

int vrend_create_so_target(struct vrend_context *ctx,
                           uint32_t handle,
                           uint32_t res_handle,
                           uint32_t buffer_offset,
                           uint32_t buffer_size)
{
   struct vrend_so_target *target;
   struct vrend_resource *res;
   int ret_handle;
   res = vrend_renderer_ctx_res_lookup(ctx, res_handle);
   if (!res || !res->gl_id) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
      return EINVAL;
   }

   target = CALLOC_STRUCT(vrend_so_target);
   if (!target)
      return ENOMEM;

   pipe_reference_init(&target->reference, 1);
   target->res_handle = res_handle;
   target->buffer_offset = buffer_offset;
   target->buffer_size = buffer_size;
   target->sub_ctx = ctx->sub;
   vrend_resource_reference(&target->buffer, res);

   ret_handle = vrend_renderer_object_insert(ctx, target, handle,
                                             VIRGL_OBJECT_STREAMOUT_TARGET);
   if (ret_handle == 0) {
      vrend_resource_reference(&target->buffer, NULL);
      FREE(target);
      return ENOMEM;
   }
   return 0;
}

static int get_glsl_version(void)
{
   int major_local = 0, minor_local = 0;
   const GLubyte *version_str;
   int count;

   version_str = glGetString(GL_SHADING_LANGUAGE_VERSION);
   if (!version_str) {
      virgl_error("GL_SHADING_LANGUAGE_VERSION query failed with empty output.");
      return -1;
   }

   if (vrend_state.use_gles) {
      count = sscanf((const char *)version_str, "%*s %*s %*s %*s %i.%i",
                          &major_local, &minor_local);
   } else {
      count = sscanf((const char *)version_str, "%i.%i",
                          &major_local, &minor_local);
   }

   if (count != 2) {
      virgl_error("GL_SHADING_LANGUAGE_VERSION query failed with unexpected version format.");
      return -1;
   }

   return (major_local * 100) + minor_local;
}

static void vrend_fill_caps_glsl_version(int gl_ver, int gles_ver,
                                         union virgl_caps *caps)
{
   if (gles_ver > 0) {
      caps->v1.glsl_level = 120;

      if (gles_ver >= 31)
         caps->v1.glsl_level = 310;
      else if (gles_ver >= 30)
         caps->v1.glsl_level = 130;
   }

   if (gl_ver > 0) {
      caps->v1.glsl_level = 130;

      if (gl_ver == 31)
         caps->v1.glsl_level = 140;
      else if (gl_ver == 32)
         caps->v1.glsl_level = 150;
      else if (gl_ver >= 33)
         caps->v1.glsl_level = 10 * gl_ver;
   }

   if (caps->v1.glsl_level < 400) {
      if (has_feature(feat_tessellation) &&
          has_feature(feat_geometry_shader) &&
          has_feature(feat_gpu_shader5)) {
         /* This is probably a lie, but Gallium enables
          * OES_geometry_shader and ARB_gpu_shader5
          * based on this value, apart from that it doesn't
          * seem to be a crucial value */
         caps->v1.glsl_level = 400;

         /* Let's lie a bit more */
         if (has_feature(feat_separate_shader_objects)) {
            caps->v1.glsl_level = 410;

            /* Compute shaders require GLSL 4.30 unless the shader explicitely
             * specifies GL_ARB_compute_shader as required. However, on OpenGL ES
             * they are already supported with version 3.10, so if we already
             * advertise a feature level of 410, just lie a bit more to make
             * compute shaders available to GL programs that don't specify the
             * extension within the shaders. */
            if (has_feature(feat_compute_shader))
               caps->v1.glsl_level =  430;
         }
      }
   }
   virgl_info("GLSL feature level %d\n", caps->v1.glsl_level);
}

static void set_format_bit(struct virgl_supported_format_mask *mask, enum virgl_formats fmt)
{
   assert(fmt < VIRGL_FORMAT_MAX);
   unsigned val = (unsigned)fmt;
   unsigned idx = val / 32;
   unsigned bit = val % 32;
   assert(idx < ARRAY_SIZE(mask->bitmask));
   mask->bitmask[idx] |= 1u << bit;
}

/*
 * Does all of the common caps setting,
 * if it dedects a early out returns true.
 */
static void vrend_renderer_fill_caps_v1(int gl_ver, int gles_ver, union virgl_caps *caps)
{
   int i;
   GLint max;

   /*
    * We can't fully support this feature on GLES,
    * but it is needed for OpenGL 2.1 so lie.
    */
   caps->v1.bset.occlusion_query = 1;

   /* Set supported prims here as we now know what shaders we support. */
   caps->v1.prim_mask = (1 << PIPE_PRIM_POINTS) | (1 << PIPE_PRIM_LINES) |
                        (1 << PIPE_PRIM_LINE_STRIP) | (1 << PIPE_PRIM_LINE_LOOP) |
                        (1 << PIPE_PRIM_TRIANGLES) | (1 << PIPE_PRIM_TRIANGLE_STRIP) |
                        (1 << PIPE_PRIM_TRIANGLE_FAN);

   if (gl_ver > 0 && !vrend_state.use_core_profile) {
      caps->v1.bset.poly_stipple = 1;
      caps->v1.prim_mask |= (1 << PIPE_PRIM_QUADS) |
                            (1 << PIPE_PRIM_QUAD_STRIP) |
                            (1 << PIPE_PRIM_POLYGON);
   }

   if (caps->v1.glsl_level >= 150) {
      caps->v1.prim_mask |= (1 << PIPE_PRIM_LINES_ADJACENCY) |
                            (1 << PIPE_PRIM_LINE_STRIP_ADJACENCY) |
                            (1 << PIPE_PRIM_TRIANGLES_ADJACENCY) |
                            (1 << PIPE_PRIM_TRIANGLE_STRIP_ADJACENCY);
   }
   if (caps->v1.glsl_level >= 400 || has_feature(feat_tessellation))
      caps->v1.prim_mask |= (1 << PIPE_PRIM_PATCHES);

   if (epoxy_has_gl_extension("GL_ARB_vertex_type_10f_11f_11f_rev"))
      set_format_bit(&caps->v1.vertexbuffer, VIRGL_FORMAT_R11G11B10_FLOAT);

   if (has_feature(feat_nv_conditional_render) ||
       has_feature(feat_gl_conditional_render))
      caps->v1.bset.conditional_render = 1;

   if (has_feature(feat_indep_blend))
      caps->v1.bset.indep_blend_enable = 1;

   if (has_feature(feat_draw_instance))
      caps->v1.bset.instanceid = 1;

   if (has_feature(feat_ubo)) {
      glGetIntegerv(GL_MAX_VERTEX_UNIFORM_BLOCKS, &max);
      /* GL_MAX_VERTEX_UNIFORM_BLOCKS is omitting the ordinary uniform block, add it
       * also reduce by 1 as we might generate a VirglBlock helper uniform block */
      caps->v1.max_uniform_blocks = max + 1 - 1;
   }

   if (has_feature(feat_depth_clamp))
      caps->v1.bset.depth_clip_disable = 1;

   if (gl_ver >= 32) {
      caps->v1.bset.fragment_coord_conventions = 1;
      caps->v1.bset.seamless_cube_map = 1;
   } else {
      if (epoxy_has_gl_extension("GL_ARB_fragment_coord_conventions"))
         caps->v1.bset.fragment_coord_conventions = 1;
      if (epoxy_has_gl_extension("GL_ARB_seamless_cube_map") || gles_ver >= 30)
         caps->v1.bset.seamless_cube_map = 1;
   }

   if (has_feature(feat_seamless_cubemap_per_texture))
      caps->v1.bset.seamless_cube_map_per_texture = 1;

   if (has_feature(feat_texture_multisample))
      caps->v1.bset.texture_multisample = 1;

   if (has_feature(feat_tessellation))
      caps->v1.bset.has_tessellation_shaders = 1;

   if (has_feature(feat_sample_shading))
      caps->v1.bset.has_sample_shading = 1;

   if (has_feature(feat_indirect_draw))
      caps->v1.bset.has_indirect_draw = 1;

   if (has_feature(feat_indep_blend_func))
      caps->v1.bset.indep_blend_func = 1;

   if (has_feature(feat_cube_map_array))
      caps->v1.bset.cube_map_array = 1;

   if (has_feature(feat_texture_query_lod))
      caps->v1.bset.texture_query_lod = 1;

   if (gl_ver >= 40) {
      caps->v1.bset.has_fp64 = 1;
   } else {
      /* need gpu shader 5 for bitfield insert */
      if (epoxy_has_gl_extension("GL_ARB_gpu_shader_fp64") &&
          epoxy_has_gl_extension("GL_ARB_gpu_shader5"))
         caps->v1.bset.has_fp64 = 1;
   }

   if (has_feature(feat_base_instance))
      caps->v1.bset.start_instance = 1;

   if (epoxy_has_gl_extension("GL_ARB_shader_stencil_export")) {
      caps->v1.bset.shader_stencil_export = 1;
   }

   if (has_feature(feat_conditional_render_inverted))
      caps->v1.bset.conditional_render_inverted = 1;

   if (gl_ver >= 45) {
      caps->v1.bset.has_cull = 1;
      caps->v1.bset.derivative_control = 1;
   } else {
     if (has_feature(feat_cull_distance))
        caps->v1.bset.has_cull = 1;
     if (epoxy_has_gl_extension("GL_ARB_derivative_control"))
        caps->v1.bset.derivative_control = 1;
   }

   if (has_feature(feat_polygon_offset_clamp))
      caps->v1.bset.polygon_offset_clamp = 1;

   if (has_feature(feat_transform_feedback_overflow_query))
     caps->v1.bset.transform_feedback_overflow_query = 1;

   if (has_feature(feat_texture_mirror_clamp) ||
       has_feature(feat_texture_mirror_clamp_to_edge))
      caps->v1.bset.mirror_clamp = true;

   if (has_feature(feat_texture_array)) {
      glGetIntegerv(GL_MAX_ARRAY_TEXTURE_LAYERS, &max);
      caps->v1.max_texture_array_layers = max;
   }

   /* we need tf3 so we can do gallium skip buffers */
   if (has_feature(feat_transform_feedback)) {
      if (has_feature(feat_transform_feedback2))
         caps->v1.bset.streamout_pause_resume = 1;

      if (has_feature(feat_transform_feedback3)) {
         glGetIntegerv(GL_MAX_TRANSFORM_FEEDBACK_BUFFERS, &max);
         caps->v1.max_streamout_buffers = max;
      } else if (gles_ver > 0) {
         glGetIntegerv(GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS, &max);
         /* As with the earlier version of transform feedback this min 4. */
         if (max >= 4) {
            caps->v1.max_streamout_buffers = 4;
         }
      } else
         caps->v1.max_streamout_buffers = 4;
   }

   if (has_feature(feat_dual_src_blend)) {
      glGetIntegerv(GL_MAX_DUAL_SOURCE_DRAW_BUFFERS, &max);
      caps->v1.max_dual_source_render_targets = max;
   }

   if (has_feature(feat_arb_or_gles_ext_texture_buffer)) {
      glGetIntegerv(GL_MAX_TEXTURE_BUFFER_SIZE, &max);
      vrend_state.max_texture_buffer_size = caps->v1.max_tbo_size = max;
   }

   if (has_feature(feat_texture_gather)) {
      if (gl_ver > 0) {
         glGetIntegerv(GL_MAX_PROGRAM_TEXTURE_GATHER_COMPONENTS_ARB, &max);
         caps->v1.max_texture_gather_components = max;
      } else {
         caps->v1.max_texture_gather_components = 4;
      }
   }

   if (has_feature(feat_viewport_array)) {
      glGetIntegerv(GL_MAX_VIEWPORTS, &max);
      caps->v1.max_viewports = max;
   } else {
      caps->v1.max_viewports = 1;
   }

   if (has_feature(feat_timer_query)) {
      caps->v1.bset.timer_query = 1;
   }

   /* Common limits for all backends. */
   caps->v1.max_render_targets = vrend_state.max_draw_buffers;

   glGetIntegerv(GL_MAX_SAMPLES, &max);
   caps->v1.max_samples = max;

   /* All of the formats are common. */
   for (i = 0; i < VIRGL_FORMAT_MAX; i++) {
      enum virgl_formats fmt = (enum virgl_formats)i;
      if (tex_conv_table[i].internalformat != 0 || fmt == VIRGL_FORMAT_YV12 ||
          fmt == VIRGL_FORMAT_NV12) {
         if (vrend_format_can_sample(fmt)) {
            set_format_bit(&caps->v1.sampler, fmt);
            if (vrend_format_can_render(fmt))
               set_format_bit(&caps->v1.render, fmt);
         }
      }
   }

   /* These are filled in by the init code, so are common. */
   if (has_feature(feat_nv_prim_restart) ||
       has_feature(feat_gl_prim_restart)) {
      caps->v1.bset.primitive_restart = 1;
   }
}

static void vrend_renderer_fill_caps_v2(int gl_ver, int gles_ver,  union virgl_caps *caps)
{
   GLint max;
   GLfloat range[2];
   uint32_t video_memory;
   const char *renderer = (const char *)glGetString(GL_RENDERER);

   /* Count this up when you add a feature flag that is used to set a CAP in
    * the guest that was set unconditionally before. Then check that flag and
    * this value to avoid regressions when a guest with a new mesa version is
    * run on an old virgl host. Use it also to indicate non-cap fixes on the
    * host that help enable features in the guest. */
   caps->v2.host_feature_check_version = 23;

   /* Forward host GL_RENDERER to the guest. */
   strncpy(caps->v2.renderer, renderer, sizeof(caps->v2.renderer) - 1);

   /* glamor reject llvmpipe, and since the renderer string is
    * composed of "virgl" and this renderer string we have to
    * hide the "llvmpipe" part */
   char *llvmpipe_string = strstr(caps->v2.renderer, "llvmpipe");
   if (llvmpipe_string)
      memcpy(llvmpipe_string, "LLVMPIPE", 8);

   glGetFloatv(GL_ALIASED_POINT_SIZE_RANGE, range);
   caps->v2.min_aliased_point_size = range[0];
   caps->v2.max_aliased_point_size = range[1];

   glGetFloatv(GL_ALIASED_LINE_WIDTH_RANGE, range);
   caps->v2.min_aliased_line_width = range[0];
   caps->v2.max_aliased_line_width = range[1];

   if (gl_ver > 0) {
      glGetFloatv(GL_SMOOTH_POINT_SIZE_RANGE, range);
      caps->v2.min_smooth_point_size = range[0];
      caps->v2.max_smooth_point_size = range[1];

      glGetFloatv(GL_SMOOTH_LINE_WIDTH_RANGE, range);
      caps->v2.min_smooth_line_width = range[0];
      caps->v2.max_smooth_line_width = range[1];
   }

   glGetFloatv(GL_MAX_TEXTURE_LOD_BIAS, &caps->v2.max_texture_lod_bias);

   caps->v2.max_vertex_attribs = vrend_state.max_vertex_attributes;

   if (gl_ver >= 32 || gles_ver >= 30)
      glGetIntegerv(GL_MAX_VERTEX_OUTPUT_COMPONENTS, &max);
   else
      max = 64; // minimum required value

   caps->v2.max_vertex_outputs = max / 4;

   glGetIntegerv(GL_MIN_PROGRAM_TEXEL_OFFSET, &caps->v2.min_texel_offset);
   glGetIntegerv(GL_MAX_PROGRAM_TEXEL_OFFSET, &caps->v2.max_texel_offset);

   glGetIntegerv(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT, (GLint*)&caps->v2.uniform_buffer_offset_alignment);

   glGetIntegerv(GL_MAX_TEXTURE_SIZE, (GLint*)&caps->v2.max_texture_2d_size);
   glGetIntegerv(GL_MAX_3D_TEXTURE_SIZE, (GLint*)&caps->v2.max_texture_3d_size);
   glGetIntegerv(GL_MAX_CUBE_MAP_TEXTURE_SIZE, (GLint*)&caps->v2.max_texture_cube_size);
   vrend_state.max_texture_2d_size = caps->v2.max_texture_2d_size;
   vrend_state.max_texture_3d_size = caps->v2.max_texture_3d_size;
   vrend_state.max_texture_cube_size = caps->v2.max_texture_cube_size;
   VREND_DEBUG(dbg_features, NULL, "Texture limits: 2D:%u 3D:%u Cube:%u\n",
               vrend_state.max_texture_2d_size, vrend_state.max_texture_3d_size,
               vrend_state.max_texture_cube_size);

   if (has_feature(feat_geometry_shader)) {
      glGetIntegerv(GL_MAX_GEOMETRY_OUTPUT_VERTICES, (GLint*)&caps->v2.max_geom_output_vertices);
      glGetIntegerv(GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS, (GLint*)&caps->v2.max_geom_total_output_components);
   }

   if (has_feature(feat_tessellation)) {
      glGetIntegerv(GL_MAX_TESS_PATCH_COMPONENTS, &max);
      caps->v2.max_shader_patch_varyings = max / 4;
   } else
      caps->v2.max_shader_patch_varyings = 0;

   vrend_state.max_shader_patch_varyings = caps->v2.max_shader_patch_varyings;

   if (has_feature(feat_texture_gather)) {
       glGetIntegerv(GL_MIN_PROGRAM_TEXTURE_GATHER_OFFSET, &caps->v2.min_texture_gather_offset);
       glGetIntegerv(GL_MAX_PROGRAM_TEXTURE_GATHER_OFFSET, &caps->v2.max_texture_gather_offset);
   }

   if (has_feature(feat_texture_buffer_range)) {
      glGetIntegerv(GL_TEXTURE_BUFFER_OFFSET_ALIGNMENT, (GLint*)&caps->v2.texture_buffer_offset_alignment);
   }

   if (has_feature(feat_ssbo)) {
      glGetIntegerv(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT, (GLint*)&caps->v2.shader_buffer_offset_alignment);

      glGetIntegerv(GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS, &max);
      caps->v2.max_shader_buffer_other_stages = MIN2(max, PIPE_MAX_SHADER_BUFFERS);
      caps->v2.max_shader_storage_blocks[PIPE_SHADER_VERTEX] = caps->v2.max_shader_buffer_other_stages;
      glGetIntegerv(GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS, &max);
      caps->v2.max_shader_buffer_frag_compute = MIN2(max, PIPE_MAX_SHADER_BUFFERS);
      caps->v2.max_shader_storage_blocks[PIPE_SHADER_FRAGMENT] = caps->v2.max_shader_buffer_frag_compute;

      if (has_feature(feat_geometry_shader)) {
         glGetIntegerv(GL_MAX_GEOMETRY_SHADER_STORAGE_BLOCKS, &max);
         caps->v2.max_shader_storage_blocks[PIPE_SHADER_GEOMETRY] = MIN2(max, PIPE_MAX_SHADER_BUFFERS);
      }

      if (has_feature(feat_tessellation)) {
         glGetIntegerv(GL_MAX_TESS_CONTROL_SHADER_STORAGE_BLOCKS, &max);
         caps->v2.max_shader_storage_blocks[PIPE_SHADER_TESS_CTRL] = MIN2(max, PIPE_MAX_SHADER_BUFFERS);
         glGetIntegerv(GL_MAX_TESS_EVALUATION_SHADER_STORAGE_BLOCKS, &max);
         caps->v2.max_shader_storage_blocks[PIPE_SHADER_TESS_EVAL] = MIN2(max, PIPE_MAX_SHADER_BUFFERS);
      }

      if (has_feature(feat_compute_shader)) {
         glGetIntegerv(GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS, &max);
         caps->v2.max_shader_storage_blocks[PIPE_SHADER_COMPUTE] = MIN2(max, PIPE_MAX_SHADER_BUFFERS);
      }

      glGetIntegerv(GL_MAX_COMBINED_SHADER_STORAGE_BLOCKS, &max);
      /* We use a 32 bit mask for the binding points and the binding points
       * must be sufficient for all shader stages combined. */
      caps->v2.max_combined_shader_buffers = MIN2(max, VREND_MAX_COMBINED_SSBO_BINDING_POINTS);
   }

   if (has_feature(feat_images)) {
      glGetIntegerv(GL_MAX_VERTEX_IMAGE_UNIFORMS, &max);
      if (max > PIPE_MAX_SHADER_IMAGES)
         max = PIPE_MAX_SHADER_IMAGES;
      caps->v2.max_shader_image_other_stages = max;
      glGetIntegerv(GL_MAX_FRAGMENT_IMAGE_UNIFORMS, &max);
      if (max > PIPE_MAX_SHADER_IMAGES)
         max = PIPE_MAX_SHADER_IMAGES;
      caps->v2.max_shader_image_frag_compute = max;

      if (gl_ver > 0) /* Seems GLES doesn't support multisample images */
         glGetIntegerv(GL_MAX_IMAGE_SAMPLES, (GLint*)&caps->v2.max_image_samples);
   }

   if (has_feature(feat_storage_multisample))
      caps->v1.max_samples = vrend_renderer_query_multisample_caps(caps->v1.max_samples, &caps->v2);

   caps->v2.capability_bits |= VIRGL_CAP_TGSI_INVARIANT | VIRGL_CAP_SET_MIN_SAMPLES |
                               VIRGL_CAP_TGSI_PRECISE | VIRGL_CAP_APP_TWEAK_SUPPORT;

   /* If attribute isn't supported, assume 2048 which is the minimum allowed
      by the specification. */
   if (gl_ver >= 44 || gles_ver >= 31)
      glGetIntegerv(GL_MAX_VERTEX_ATTRIB_STRIDE, (GLint*)&caps->v2.max_vertex_attrib_stride);
   else
      caps->v2.max_vertex_attrib_stride = 2048;

   if (has_feature(feat_compute_shader) && (vrend_state.use_gles || gl_ver >= 33)) {
      glGetIntegerv(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, (GLint*)&caps->v2.max_compute_work_group_invocations);
      glGetIntegerv(GL_MAX_COMPUTE_SHARED_MEMORY_SIZE, (GLint*)&caps->v2.max_compute_shared_memory_size);
      glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0, (GLint*)&caps->v2.max_compute_grid_size[0]);
      glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1, (GLint*)&caps->v2.max_compute_grid_size[1]);
      glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2, (GLint*)&caps->v2.max_compute_grid_size[2]);
      glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0, (GLint*)&caps->v2.max_compute_block_size[0]);
      glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1, (GLint*)&caps->v2.max_compute_block_size[1]);
      glGetIntegeri_v(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2, (GLint*)&caps->v2.max_compute_block_size[2]);

      caps->v2.capability_bits |= VIRGL_CAP_COMPUTE_SHADER;
   }

   if (has_feature(feat_atomic_counters)) {

      /* On GLES hosts we want atomics to be lowered to SSBOs */
      if (gl_ver > 0) {
         glGetIntegerv(GL_MAX_VERTEX_ATOMIC_COUNTERS,
                       (GLint*)(caps->v2.max_atomic_counters + PIPE_SHADER_VERTEX));
         glGetIntegerv(GL_MAX_FRAGMENT_ATOMIC_COUNTERS,
                       (GLint*)(caps->v2.max_atomic_counters + PIPE_SHADER_FRAGMENT));

         if (has_feature(feat_geometry_shader)) {
            glGetIntegerv(GL_MAX_GEOMETRY_ATOMIC_COUNTERS,
                          (GLint*)(caps->v2.max_atomic_counters + PIPE_SHADER_GEOMETRY));
         }

         if (has_feature(feat_tessellation)) {
            glGetIntegerv(GL_MAX_TESS_CONTROL_ATOMIC_COUNTERS,
                          (GLint*)(caps->v2.max_atomic_counters + PIPE_SHADER_TESS_CTRL));
            glGetIntegerv(GL_MAX_TESS_EVALUATION_ATOMIC_COUNTERS,
                          (GLint*)(caps->v2.max_atomic_counters + PIPE_SHADER_TESS_EVAL));
         }

         if (has_feature(feat_compute_shader)) {
            glGetIntegerv(GL_MAX_COMPUTE_ATOMIC_COUNTERS,
                          (GLint*)(caps->v2.max_atomic_counters + PIPE_SHADER_COMPUTE));
         }

         glGetIntegerv(GL_MAX_COMBINED_ATOMIC_COUNTERS,
                       (GLint*)&caps->v2.max_combined_atomic_counters);
      }

      glGetIntegerv(GL_MAX_VERTEX_ATOMIC_COUNTER_BUFFERS,
                    (GLint*)(caps->v2.max_atomic_counter_buffers + PIPE_SHADER_VERTEX));

      glGetIntegerv(GL_MAX_FRAGMENT_ATOMIC_COUNTER_BUFFERS,
                    (GLint*)(caps->v2.max_atomic_counter_buffers + PIPE_SHADER_FRAGMENT));

      if (has_feature(feat_geometry_shader))
         glGetIntegerv(GL_MAX_GEOMETRY_ATOMIC_COUNTER_BUFFERS,
                       (GLint*)(caps->v2.max_atomic_counter_buffers + PIPE_SHADER_GEOMETRY));

      if (has_feature(feat_tessellation)) {
         glGetIntegerv(GL_MAX_TESS_CONTROL_ATOMIC_COUNTER_BUFFERS,
                       (GLint*)(caps->v2.max_atomic_counter_buffers + PIPE_SHADER_TESS_CTRL));
         glGetIntegerv(GL_MAX_TESS_EVALUATION_ATOMIC_COUNTER_BUFFERS,
                       (GLint*)(caps->v2.max_atomic_counter_buffers + PIPE_SHADER_TESS_EVAL));

         glGetIntegerv(GL_MAX_TESS_CONTROL_TOTAL_OUTPUT_COMPONENTS, &max);
         caps->v2.max_tcs_outputs = max / 4;

         glGetIntegerv(GL_MAX_TESS_EVALUATION_OUTPUT_COMPONENTS, &max);
         caps->v2.max_tes_outputs = max / 4;
      }

      if (has_feature(feat_compute_shader)) {
         glGetIntegerv(GL_MAX_COMPUTE_ATOMIC_COUNTER_BUFFERS,
                       (GLint*)(caps->v2.max_atomic_counter_buffers + PIPE_SHADER_COMPUTE));
      }

      glGetIntegerv(GL_MAX_COMBINED_ATOMIC_COUNTER_BUFFERS,
                    (GLint*)&caps->v2.max_combined_atomic_counter_buffers);
   }

   if (has_feature(feat_fb_no_attach))
      caps->v2.capability_bits |= VIRGL_CAP_FB_NO_ATTACH;

   if (has_feature(feat_texture_view))
      caps->v2.capability_bits |= VIRGL_CAP_TEXTURE_VIEW;

   if (has_feature(feat_txqs))
      caps->v2.capability_bits |= VIRGL_CAP_TXQS;

   if (has_feature(feat_barrier))
      caps->v2.capability_bits |= VIRGL_CAP_MEMORY_BARRIER;

   if (has_feature(feat_copy_image))
      caps->v2.capability_bits |= VIRGL_CAP_COPY_IMAGE;

   if (has_feature(feat_robust_buffer_access))
      caps->v2.capability_bits |= VIRGL_CAP_ROBUST_BUFFER_ACCESS;

   if (has_feature(feat_framebuffer_fetch))
      caps->v2.capability_bits |= VIRGL_CAP_TGSI_FBFETCH;

   if (has_feature(feat_shader_clock))
      caps->v2.capability_bits |= VIRGL_CAP_SHADER_CLOCK;

   if (has_feature(feat_texture_barrier))
      caps->v2.capability_bits |= VIRGL_CAP_TEXTURE_BARRIER;

   caps->v2.capability_bits |= VIRGL_CAP_TGSI_COMPONENTS;

   if (has_feature(feat_srgb_write_control))
      caps->v2.capability_bits |= VIRGL_CAP_SRGB_WRITE_CONTROL;

   if (has_feature(feat_transform_feedback3))
         caps->v2.capability_bits |= VIRGL_CAP_TRANSFORM_FEEDBACK3;
   /* Enable feature use just now otherwise we just get a lot noise because
    * of the caps setting */
   if (vrend_debug(NULL, dbg_features))
      vrend_debug_add_flag(dbg_feature_use);

   /* always enable, only indicates that the CMD is supported */
   caps->v2.capability_bits |= VIRGL_CAP_GUEST_MAY_INIT_LOG;

   if (has_feature(feat_qbo))
      caps->v2.capability_bits |= VIRGL_CAP_QBO;

   caps->v2.capability_bits |= VIRGL_CAP_TRANSFER;

   if (vrend_check_framebuffer_mixed_color_attachements())
      caps->v2.capability_bits |= VIRGL_CAP_FBO_MIXED_COLOR_FORMATS;

   /* We want to expose ARB_gpu_shader_fp64 when running on top of ES */
   if (vrend_state.use_gles) {
      caps->v2.capability_bits |= VIRGL_CAP_HOST_IS_GLES;
   }

   if (has_feature(feat_indirect_draw))
      caps->v2.capability_bits |= VIRGL_CAP_BIND_COMMAND_ARGS;

   if (has_feature(feat_multi_draw_indirect))
      caps->v2.capability_bits |= VIRGL_CAP_MULTI_DRAW_INDIRECT;

   if (has_feature(feat_indirect_params))
      caps->v2.capability_bits |= VIRGL_CAP_INDIRECT_PARAMS;

   for (int i = 0; i < VIRGL_FORMAT_MAX; i++) {
      enum virgl_formats fmt = (enum virgl_formats)i;
      if (tex_conv_table[i].internalformat != 0) {
         const char *readback_str = "";
         const char *multisample_str = "";
         bool log_texture_feature = false;
         if (vrend_format_can_readback(fmt)) {
            log_texture_feature = true;
            readback_str = "readback";
            set_format_bit(&caps->v2.supported_readback_formats, fmt);
         }
         if (vrend_format_can_multisample(fmt)) {
            log_texture_feature = true;
            multisample_str = "multisample";
            set_format_bit(&caps->v2.supported_multisample_formats, fmt);
         }
         if (log_texture_feature)
            VREND_DEBUG(dbg_features, NULL, "%s: Supports %s %s tv_class:%d\n",
                        util_format_name(fmt), readback_str, multisample_str,
                        tex_conv_table[i].view_class);
      }

      if (vrend_format_can_scanout(fmt))
         set_format_bit(&caps->v2.scanout, fmt);
   }

   /* Needed for framebuffer_no_attachment */
   set_format_bit(&caps->v2.supported_multisample_formats, VIRGL_FORMAT_NONE);

   if (has_feature(feat_clear_texture))
      caps->v2.capability_bits |= VIRGL_CAP_CLEAR_TEXTURE;

   if (has_feature(feat_clip_control))
      caps->v2.capability_bits |= VIRGL_CAP_CLIP_HALFZ;

   if (epoxy_has_gl_extension("GL_KHR_texture_compression_astc_sliced_3d"))
      caps->v2.capability_bits |= VIRGL_CAP_3D_ASTC;

   caps->v2.capability_bits |= VIRGL_CAP_INDIRECT_INPUT_ADDR;

   caps->v2.capability_bits |= VIRGL_CAP_COPY_TRANSFER;


   if (has_feature(feat_arb_buffer_storage) && !vrend_state.use_external_blob) {
      const char *vendor = (const char *)glGetString(GL_VENDOR);
      bool is_mesa = ((strstr(renderer, "Mesa") != NULL) || (strstr(renderer, "DRM") != NULL) ||
                      (strstr(renderer, "llvmpipe") != NULL));
      /*
       * Intel GPUs (aside from Atom, which doesn't expose GL4.5) are cache-coherent.
       * Mesa AMDGPUs use write-combine mappings for coherent/persistent memory (see
       * RADEON_FLAG_GTT_WC in si_buffer.c/r600_buffer_common.c). For Nvidia, we can guess and
       * check.  Long term, maybe a GL extension or using VK could replace these heuristics.
       *
       * Note Intel VMX ignores the caching type returned from virglrenderer, while AMD SVM and
       * ARM honor it.
       */
      if (is_mesa) {
         if (strstr(vendor, "Intel") != NULL)
            vrend_state.inferred_gl_caching_type = VIRGL_RENDERER_MAP_CACHE_CACHED;
         else if (strstr(vendor, "AMD") != NULL)
            vrend_state.inferred_gl_caching_type = VIRGL_RENDERER_MAP_CACHE_WC;
         else if (strstr(vendor, "Mesa") != NULL)
            vrend_state.inferred_gl_caching_type = VIRGL_RENDERER_MAP_CACHE_CACHED;
      } else {
         /* This is an educated guess since things don't explode with VMX + Nvidia. */
         if (strstr(renderer, "Quadro K2200") != NULL)
            vrend_state.inferred_gl_caching_type = VIRGL_RENDERER_MAP_CACHE_CACHED;
      }

      if (vrend_state.inferred_gl_caching_type)
         caps->v2.capability_bits |= VIRGL_CAP_ARB_BUFFER_STORAGE;
   }

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   if (gbm) {
      if (has_feature(feat_memory_object) && has_feature(feat_memory_object_fd)) {
         if ((!strcmp(gbm_device_get_backend_name(gbm->device), "i915") ||
              !strcmp(gbm_device_get_backend_name(gbm->device), "amdgpu")) &&
             !vrend_winsys_different_gpu())
            caps->v2.capability_bits |= VIRGL_CAP_ARB_BUFFER_STORAGE;
      }
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_SCANOUT_USES_GBM;
   }
#endif

   if (has_feature(feat_blend_equation_advanced))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_BLEND_EQUATION;

#ifdef HAVE_EPOXY_EGL_H
   if (egl)
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_UNTYPED_RESOURCE;
#endif

   video_memory = vrend_renderer_get_video_memory();
   if (video_memory) {
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_VIDEO_MEMORY;
      caps->v2.max_video_memory = video_memory;
   }

   if (has_feature(feat_ati_meminfo) || has_feature(feat_nvx_gpu_memory_info)) {
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_MEMINFO;
   }

   if (has_feature(feat_khr_debug))
       caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_STRING_MARKER;

   if (has_feature(feat_implicit_msaa))
       caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_IMPLICIT_MSAA;

   if (vrend_winsys_different_gpu())
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_DIFFERENT_GPU;

   if (has_feature(feat_texture_shadow_lod))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_TEXTURE_SHADOW_LOD;

   // we use capability bits (not a version of protocol), because
   // we disable this on client side if virglrenderer is used under
   // vtest. vtest can't support this, because size of resource
   // is used to create shmem. On drm path, we can use this, because
   // size of drm resource (bo) is not passed to virglrenderer and
   // we can pass "1" as size on drm path, but not on vtest.
   caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_COPY_TRANSFER_BOTH_DIRECTIONS;

   if (has_feature(feat_anisotropic_filter)) {
      float max_aniso;
      glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY, &max_aniso);
      caps->v2.max_anisotropy = MIN2(max_aniso, 16.0);
   }

   /* BUGFIX: original support for this cap was broken. It was _supposed_ to
    * inform a guest-side client's query for GL_MAX_TEXTURE_IMAGE_UNITS, but
    * only ever informed gallium's internal query for
    * PIPE_SHADER_CAP_MAX_TEXTURE_SAMPLERS. This caused crashes on Mali.
    *
    * Now we send the "correct" value, to ensure good behavior from old guest
    * drivers, but it is not "useful" for any other purpose.
    */
   caps->v2.max_texture_samplers = PIPE_MAX_SAMPLERS;

   if (has_feature(feat_ubo)) {
      glGetIntegerv(GL_MAX_UNIFORM_BLOCK_SIZE, &max);
      caps->v2.max_uniform_block_size = max;
   }

   /* Propagate the max of Uniform Components */
   glGetIntegerv(GL_MAX_VERTEX_UNIFORM_COMPONENTS, &max);
   caps->v2.max_const_buffer_size[PIPE_SHADER_VERTEX] = max * 4;

   glGetIntegerv(GL_MAX_FRAGMENT_UNIFORM_COMPONENTS, &max);
   caps->v2.max_const_buffer_size[PIPE_SHADER_FRAGMENT] = max * 4;

   if (has_feature(feat_geometry_shader)) {
      glGetIntegerv(GL_MAX_GEOMETRY_UNIFORM_COMPONENTS, &max);
      caps->v2.max_const_buffer_size[PIPE_SHADER_GEOMETRY] = max * 4;
   }

   if (has_feature(feat_tessellation)) {
      glGetIntegerv(GL_MAX_TESS_CONTROL_UNIFORM_COMPONENTS, &max);
      caps->v2.max_const_buffer_size[PIPE_SHADER_TESS_CTRL] = max * 4;
      glGetIntegerv(GL_MAX_TESS_EVALUATION_UNIFORM_COMPONENTS, &max);
      caps->v2.max_const_buffer_size[PIPE_SHADER_TESS_EVAL] = max * 4;
   }

   if (has_feature(feat_compute_shader)) {
      glGetIntegerv(GL_MAX_COMPUTE_UNIFORM_COMPONENTS, &max);
      caps->v2.max_const_buffer_size[PIPE_SHADER_COMPUTE] = max * 4;
   }

   if (has_feature(feat_separate_shader_objects))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_SSO;

   if (has_feature(feat_vs_layer_viewport))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_VS_VERTEX_LAYER;

   if (has_feature(feat_vs_viewport_index))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_VS_VIEWPORT_INDEX;

   if (has_feature(feat_pipeline_statistics_query))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_PIPELINE_STATISTICS_QUERY;

   if (has_feature(feat_draw_parameters))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_DRAW_PARAMETERS;

   if (has_feature(feat_group_vote))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_GROUP_VOTE;

   if (has_feature(feat_texture_mirror_clamp_to_edge))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_MIRROR_CLAMP_TO_EDGE;

   if (has_feature(feat_texture_mirror_clamp))
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_MIRROR_CLAMP;

#ifdef ENABLE_VIDEO
   vrend_video_fill_caps(caps);
#else
   caps->v2.num_video_caps = 0;
#endif
   if (vrend_state.gbm_layout_feat)
      caps->v2.capability_bits_v2 |= VIRGL_CAP_V2_RESOURCE_LAYOUT;
}

void vrend_renderer_fill_caps(uint32_t set, uint32_t version,
                              union virgl_caps *caps)
{
   int gl_ver, gles_ver;
   GLenum err;
   bool fill_capset2 = false;

   if (!caps)
      return;

   switch (set) {
   case VIRTGPU_DRM_CAPSET_VIRGL:
      if (version > VREND_CAPSET_VIRGL_MAX_VERSION)
         return;
      memset(caps, 0, sizeof(struct virgl_caps_v1));
      caps->max_version = VREND_CAPSET_VIRGL_MAX_VERSION;
      break;
   case VIRTGPU_DRM_CAPSET_VIRGL2:
      if (version > VREND_CAPSET_VIRGL2_MAX_VERSION)
         return;
      memset(caps, 0, sizeof(*caps));
      caps->max_version = VREND_CAPSET_VIRGL2_MAX_VERSION;
      fill_capset2 = true;
      break;
   default:
      return;
   }

   /* We don't want to deal with stale error states that the caller might not
    * have cleaned up propperly, so read the error state until we are okay.
    */
   while ((err = glGetError()) != GL_NO_ERROR)
      virgl_warn("%s: Entering with stale GL error: %d\n", __func__, err);

   if (vrend_state.use_gles) {
      gles_ver = epoxy_gl_version();
      gl_ver = 0;
   } else {
      gles_ver = 0;
      gl_ver = epoxy_gl_version();
   }

   vrend_fill_caps_glsl_version(gl_ver, gles_ver, caps);
   VREND_DEBUG(dbg_features, NULL, "GLSL support level: %d", caps->v1.glsl_level);

   vrend_renderer_fill_caps_v1(gl_ver, gles_ver, caps);

   if (!fill_capset2)
      return;

   vrend_renderer_fill_caps_v2(gl_ver, gles_ver, caps);
}

GLint64 vrend_renderer_get_timestamp(void)
{
   GLint64 v;
   glGetInteger64v(GL_TIMESTAMP, &v);
   return v;
}

void *vrend_renderer_get_cursor_contents(struct pipe_resource *pres,
                                         uint32_t *width,
                                         uint32_t *height)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;
   GLenum format, type;
   int blsize;
   char *data, *data2;
   int size;
   unsigned h;

   if (res->base.width0 > 128 || res->base.height0 > 128)
      return NULL;

   if (res->target != GL_TEXTURE_2D)
      return NULL;

   if (!width || !height)
      return NULL;

   *width = res->base.width0;
   *height = res->base.height0;

   format = tex_conv_table[res->base.format].glformat;
   type = tex_conv_table[res->base.format].gltype;
   blsize = util_format_get_blocksize(res->base.format);
   size = util_format_get_nblocks(res->base.format, res->base.width0, res->base.height0) * blsize;
   data = malloc(size);
   data2 = malloc(size);

   if (!data || !data2) {
      free(data);
      free(data2);
      return NULL;
   }

   if (has_feature(feat_arb_robustness)) {
      glBindTexture(res->target, res->gl_id);
      glGetnTexImageARB(res->target, 0, format, type, size, data);
   } else if (vrend_state.use_gles) {
      do_readpixels(res, 0, 0, 0, 0, 0, *width, *height, format, type, size, data);
   } else {
      glBindTexture(res->target, res->gl_id);
      glGetTexImage(res->target, 0, format, type, data);
   }

   for (h = 0; h < res->base.height0; h++) {
      uint32_t doff = (res->base.height0 - h - 1) * res->base.width0 * blsize;
      uint32_t soff = h * res->base.width0 * blsize;

      memcpy(data2 + doff, data + soff, res->base.width0 * blsize);
   }
   free(data);
   glBindTexture(res->target, 0);
   return data2;
}


void vrend_renderer_force_ctx_0(void)
{
   TRACE_FUNC();
   vrend_state.current_ctx = NULL;
   vrend_state.current_hw_ctx = NULL;
   vrend_hw_switch_context(vrend_state.ctx0, true);
}

void vrend_renderer_get_rect(struct pipe_resource *pres,
                             const struct iovec *iov, unsigned int num_iovs,
                             uint32_t offset,
                             int x, int y, int width, int height)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;
   struct vrend_transfer_info transfer_info;
   struct pipe_box box;
   int elsize;

   memset(&transfer_info, 0, sizeof(transfer_info));

   elsize = util_format_get_blocksize(res->base.format);
   box.x = x;
   box.y = y;
   box.z = 0;
   box.width = width;
   box.height = height;
   box.depth = 1;

   transfer_info.box = &box;

   transfer_info.stride = util_format_get_nblocksx(res->base.format, res->base.width0) * elsize;
   transfer_info.offset = offset;
   transfer_info.iovec = iov;
   transfer_info.iovec_cnt = num_iovs;

   vrend_renderer_transfer_pipe(pres, &transfer_info,
                                VIRGL_TRANSFER_FROM_HOST);
}

static struct vrend_untyped_resource *
vrend_renderer_find_untyped_resource(struct vrend_context *ctx, uint32_t res_id)
{
   list_for_each_entry(struct vrend_untyped_resource, iter, &ctx->untyped_resources, head) {
      if (iter->resource->res_id == res_id)
         return iter;
   }
   return NULL;
}

void vrend_renderer_attach_res_ctx(struct vrend_context *ctx,
                                   struct virgl_resource *res)
{
   if (!res->pipe_resource) {
      struct virgl_resource *last = ctx->untyped_resource_cache;

      if (last == res || vrend_renderer_find_untyped_resource(ctx, res->res_id))
         return;

      /* move the last untyped resource from cache to list */
      if (unlikely(last)) {
         struct vrend_untyped_resource *wrapper = malloc(sizeof(*wrapper));
         if (wrapper) {
            wrapper->resource = last;
            list_add(&wrapper->head, &ctx->untyped_resources);
         } else {
            virgl_warn("Dropping attached resource %d due to OOM\n", last->res_id);
         }
      }

      ctx->untyped_resource_cache = res;
      /* defer to vrend_renderer_pipe_resource_set_type */
      return;
   }

   vrend_ctx_resource_insert(ctx->res_hash,
                             res->res_id,
                             (struct vrend_resource *)res->pipe_resource);
}

void vrend_renderer_detach_res_ctx(struct vrend_context *ctx,
                                   struct virgl_resource *res)
{
   if (!res->pipe_resource) {
      if (ctx->untyped_resource_cache == res) {
         ctx->untyped_resource_cache = NULL;
      } else {
         struct vrend_untyped_resource *untyped =
            vrend_renderer_find_untyped_resource(ctx, res->res_id);
         if (untyped) {
            list_del(&untyped->head);
            free(untyped);
         }
      }

      return;
   }

   vrend_ctx_resource_remove(ctx->res_hash, res->res_id);
}

struct vrend_resource *vrend_renderer_ctx_res_lookup(struct vrend_context *ctx, int res_handle)
{
   return vrend_ctx_resource_lookup(ctx->res_hash, res_handle);
}

void vrend_context_set_debug_flags(struct vrend_context *ctx, const char *flagstring)
{
   if (vrend_debug_can_override()) {
      ctx->debug_flags |= vrend_get_debug_flags(flagstring);
      if (ctx->debug_flags & dbg_features)
         vrend_debug_add_flag(dbg_feature_use);
   }
}

void vrend_renderer_resource_get_info(struct pipe_resource *pres,
                                      struct vrend_renderer_resource_info *info)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;
   int elsize;

   elsize = util_format_get_blocksize(res->base.format);

   info->tex_id = res->gl_id;
   info->width = res->base.width0;
   info->height = res->base.height0;
   info->depth = res->base.depth0;
   info->format = res->base.format;
   info->flags = res->y_0_top ? VIRGL_RESOURCE_Y_0_TOP : 0;
   info->stride = util_format_get_nblocksx(res->base.format, u_minify(res->base.width0, 0)) * elsize;
}

int
vrend_renderer_resource_d3d11_texture2d(struct pipe_resource *pres, void **d3d_tex2d)
{
#ifdef WIN32
   struct vrend_resource *res = (struct vrend_resource *)pres;

   if (!vrend_state.d3d_share_texture)
      return 0;

   if (!res->d3d_tex2d)
      return EINVAL;

   *d3d_tex2d = res->d3d_tex2d;
   return 0;
#else
   (void)pres;
   (void)d3d_tex2d;
   return ENOTSUP;
#endif
}

void vrend_renderer_get_cap_set(uint32_t cap_set, uint32_t *max_ver,
                                uint32_t *max_size)
{
   switch (cap_set) {
   case VIRTGPU_DRM_CAPSET_VIRGL:
      *max_ver = VREND_CAPSET_VIRGL_MAX_VERSION;
      *max_size = sizeof(struct virgl_caps_v1);
      break;
   case VIRTGPU_DRM_CAPSET_VIRGL2:
      *max_ver = VREND_CAPSET_VIRGL2_MAX_VERSION;
      *max_size = sizeof(struct virgl_caps_v2);
      break;
   default:
      *max_ver = 0;
      *max_size = 0;
      break;
   }
}

void vrend_renderer_create_sub_ctx(struct vrend_context *ctx, int sub_ctx_id)
{
   struct virgl_gl_ctx_param ctx_params;
   GLuint i;

   list_for_each_entry(struct vrend_sub_context, sub, &ctx->sub_ctxs, head) {
      if (sub->sub_ctx_id == sub_ctx_id) {
         return;
      }
   }

   struct vrend_sub_context *sub = CALLOC_STRUCT(vrend_sub_context);
   if (!sub)
      return;

   ctx_params.shared = (ctx->ctx_id == 0 && sub_ctx_id == 0) ? false : true;
   ctx_params.major_ver = vrend_state.gl_major_ver;
   ctx_params.minor_ver = vrend_state.gl_minor_ver;
   ctx_params.compat_ctx = !vrend_state.use_core_profile && !vrend_state.use_gles;
   sub->gl_context = vrend_clicbs->create_gl_context(0, &ctx_params);
   sub->parent = ctx;
   vrend_clicbs->make_current(sub->gl_context);

   /* enable if vrend_renderer_init function has done it as well */
   if (has_feature(feat_debug_cb)) {
      glDebugMessageCallback(vrend_debug_cb, NULL);
      glEnable(GL_DEBUG_OUTPUT);
      glDisable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
   }

   sub->sub_ctx_id = sub_ctx_id;

   /* initialize the depth far_val to 1 */
   for (i = 0; i < PIPE_MAX_VIEWPORTS; i++) {
      sub->vps[i].far_val = 1.0;
   }

   /* Default is enabled, so set the initial hardware state accordingly */
   for (int i = 0; i < PIPE_MAX_COLOR_BUFS; ++i) {
      sub->hw_blend_state.rt[i].colormask = 0xf;
   }

   glGenVertexArrays(1, &sub->vaoid);
   if (!has_feature(feat_gles31_vertex_attrib_binding)) {
      glBindVertexArray(sub->vaoid);
   }

   glGenFramebuffers(1, &sub->fb_id);
   glBindFramebuffer(GL_FRAMEBUFFER, sub->fb_id);
   glGenFramebuffers(2, sub->blit_fb_ids);

   for (int i = 0; i < VREND_PROGRAM_NQUEUES; ++i)
      list_inithead(&sub->gl_programs[i]);
   list_inithead(&sub->cs_programs);
   list_inithead(&sub->streamout_list);

   sub->object_hash = vrend_object_init_ctx_table();

   sub->sysvalue_data.winsys_adjust_y = 1.f;

   ctx->sub = sub;
   list_add(&sub->head, &ctx->sub_ctxs);
   if (sub_ctx_id == 0)
      ctx->sub0 = sub;

   vrend_set_tweak_from_env(&ctx->sub->tweaks);
}

unsigned vrend_context_has_debug_flag(const struct vrend_context *ctx, enum virgl_debug_flags flag)
{
   return ctx && (ctx->debug_flags & flag);
}

void vrend_print_context_name(const struct vrend_context *ctx)
{
   if (ctx)
      virgl_debug("%s: ", ctx->debug_name);
   else
      virgl_debug("HOST: ");
}


void vrend_renderer_destroy_sub_ctx(struct vrend_context *ctx, int sub_ctx_id)
{
   /* never destroy sub context id 0 */
   if (sub_ctx_id == 0)
      return;

   list_for_each_entry(struct vrend_sub_context, sub, &ctx->sub_ctxs, head) {
      if (sub->sub_ctx_id == sub_ctx_id) {
         if (ctx->sub == sub) {
            ctx->sub = ctx->sub0;
         }
         vrend_destroy_sub_context(sub);
         vrend_clicbs->make_current(ctx->sub->gl_context);
         break;
      }
   }
}

void vrend_renderer_set_sub_ctx(struct vrend_context *ctx, int sub_ctx_id)
{
   struct vrend_sub_context *sub = vrend_renderer_find_sub_ctx(ctx, sub_ctx_id);
   if (sub && ctx->sub != sub) {
      ctx->sub = sub;
      vrend_clicbs->make_current(sub->gl_context);
   }
}

void vrend_renderer_prepare_reset(void)
{
   /* make sure user contexts are no longer accessed */
   vrend_free_sync_thread();
   vrend_hw_switch_context(vrend_state.ctx0, true);
}

void vrend_renderer_reset(void)
{
   vrend_free_fences();
   vrend_blitter_fini();

   vrend_destroy_context(vrend_state.ctx0);

   vrend_state.ctx0 = vrend_create_context(0, strlen("HOST"), "HOST");

   if (vrend_state.use_async_fence_cb)
      vrend_renderer_use_threaded_sync();
}

int vrend_renderer_get_poll_fd(void)
{
   int fd = vrend_state.eventfd;
   if (vrend_state.use_async_fence_cb && fd < 0)
      virgl_error("Failed to duplicate eventfd: error=%d\n", errno);
   return fd;
}

int vrend_renderer_export_query(struct pipe_resource *pres,
                                struct virgl_renderer_export_query *export_query)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;

#if defined(HAVE_EPOXY_EGL_H) && defined(ENABLE_GBM_ALLOCATION)
   if (res->gbm_bo)
      return virgl_gbm_export_query(res->gbm_bo, export_query);
#else
   (void)res;
#endif

   /*
    * Implementations that support eglExportDMABUFImageMESA can also export certain resources.
    * This is omitted currently since virgl_renderer_get_fd_for_texture supports that use case.
    */
   export_query->out_num_fds = 0;
   export_query->out_fourcc = 0;
   export_query->out_modifier = DRM_FORMAT_MOD_INVALID;
   if (export_query->in_export_fds)
      return -EINVAL;

   return 0;
}

int vrend_renderer_pipe_resource_create(struct vrend_context *ctx, uint32_t blob_id,
                                        const struct vrend_renderer_resource_create_args *args)
{
   struct vrend_resource *res;
   res = (struct vrend_resource *)vrend_renderer_resource_create(args, NULL);
   if (!res)
      return EINVAL;

   res->blob_id = blob_id;
   list_addtail(&res->head, &ctx->vrend_resources);
   return 0;
}

struct pipe_resource *vrend_get_blob_pipe(struct vrend_context *ctx, uint64_t blob_id)
{
   uint32_t id = (uint32_t)blob_id;

   list_for_each_entry_safe(struct vrend_resource, res, &ctx->vrend_resources, head) {
      if (res->blob_id != id)
         continue;

      list_del(&res->head);
      /* Set the blob id to zero, since it won't be used anymore */
      res->blob_id = 0;
      return &res->base;
   }

   return NULL;
}

int
vrend_renderer_pipe_resource_set_type(struct vrend_context *ctx,
                                      uint32_t res_id,
                                      const struct vrend_renderer_resource_set_type_args *args)
{
   struct virgl_resource *res = NULL;

   /* look up the untyped resource */
   if (ctx->untyped_resource_cache &&
       ctx->untyped_resource_cache->res_id == res_id) {
      res = ctx->untyped_resource_cache;
      ctx->untyped_resource_cache = NULL;
   } else {
      /* cache miss */
      struct vrend_untyped_resource *untyped =
         vrend_renderer_find_untyped_resource(ctx, res_id);
      if (untyped) {
         res = untyped->resource;
         list_del(&untyped->head);
         free(untyped);
      }
   }

   /* either a bad res_id or the resource is already typed */
   if (!res) {
      if (vrend_renderer_ctx_res_lookup(ctx, res_id))
         return 0;

      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_id);
      return EINVAL;
   }

   /* resource is still untyped */
   if (!res->pipe_resource) {
      const struct vrend_renderer_resource_create_args create_args = {
         .target = PIPE_TEXTURE_2D,
         .format = args->format,
         .bind = args->bind,
         .width = args->width,
         .height = args->height,
         .depth = 1,
         .array_size = 1,
         .last_level = 0,
         .nr_samples = 0,
         .flags = 0,
      };
      struct vrend_resource *gr;

      if (res->fd_type != VIRGL_RESOURCE_FD_DMABUF)
         return EINVAL;

      gr = vrend_resource_create(&create_args);
      if (!gr)
         return ENOMEM;

#ifdef HAVE_EPOXY_EGL_H
      if (egl) {
#ifdef ENABLE_GBM
         int plane_fds[VIRGL_GBM_MAX_PLANES];
         uint32_t virgl_format;
         uint32_t drm_format;
         int ret;

         for (uint32_t i = 0; i < args->plane_count; i++)
            plane_fds[i] = res->fd;

         virgl_format = gr->base.format;
         drm_format = 0;
         if (virgl_gbm_convert_format(&virgl_format, &drm_format)) {
            virgl_error("%s: unsupported format %d\n", __func__, virgl_format);
            FREE(gr);
            return EINVAL;
         }

         gr->egl_image = virgl_egl_image_from_dmabuf(egl,
                                                     args->width,
                                                     args->height,
                                                     drm_format,
                                                     args->modifier,
                                                     args->plane_count,
                                                     plane_fds,
                                                     args->plane_strides,
                                                     args->plane_offsets);
         if (!gr->egl_image) {
            virgl_error("%s: failed to create egl image\n", __func__);
            FREE(gr);
            return EINVAL;
         }

         gr->storage_bits |= VREND_STORAGE_EGL_IMAGE;

         ret = vrend_resource_alloc_texture(gr, virgl_format, gr->egl_image);
         if (ret) {
            virgl_egl_image_destroy(egl, gr->egl_image);
            FREE(gr);
            return ret;
         }

#else /* ENABLE_GBM */
         FREE(gr);
         virgl_error("%s: no EGL/GBM support \n", __func__);
         return EINVAL;

#endif /* ENABLE_GBM */
      } else {
#else /* HAVE_EPOXY_EGL_H */
      {
#endif /* HAVE_EPOXY_EGL_H */
         int fd = -1;
         GLenum internalformat = tex_conv_table[gr->base.format].internalformat;

         if (!has_feature(feat_memory_object_fd) || !has_feature(feat_memory_object)) {
            FREE(gr);
            return EINVAL;
         }

         enum virgl_resource_fd_type fd_type = virgl_resource_export_fd(res, &fd);
         if (fd_type == VIRGL_RESOURCE_FD_INVALID) {
            FREE(gr);
            return EINVAL;
         }

         /* Create a GL memory object importing memory from a FD */
         GLuint mem_object;
         glCreateMemoryObjectsEXT(1, &mem_object);
         GLint params = GL_TRUE;
         glMemoryObjectParameterivEXT(mem_object, GL_DEDICATED_MEMORY_OBJECT_EXT, &params);
         glImportMemoryFdEXT(mem_object, res->map_size, GL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);

         struct pipe_resource *pr = &gr->base;
         gr->target = tgsitargettogltarget(pr->target, pr->nr_samples);
         gr->memobj = mem_object;
         gr->storage_bits |= VREND_STORAGE_GL_TEXTURE | VREND_STORAGE_GL_MEMOBJ;

         /* Create a GL texture which uses that memory as storage */
         glGenTextures(1, &gr->gl_id);
         glBindTexture(gr->target, gr->gl_id);
         GLsizei width = (GLsizei)args->width;
         GLsizei height = (GLsizei)args->height;
         glTexParameteri(gr->target, GL_TEXTURE_TILING_EXT, GL_LINEAR_TILING_EXT);
         glTexStorageMem2DEXT(gr->target, 1, internalformat, width, height, mem_object, 0);
         glBindTexture(gr->target, 0);
         gr->is_imported = true;
      }
      res->pipe_resource = &gr->base;
   }

   vrend_ctx_resource_insert(ctx->res_hash,
                             res->res_id,
                             (struct vrend_resource *)res->pipe_resource);

   return 0;
}

uint32_t vrend_renderer_resource_get_map_info(struct pipe_resource *pres)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;
   return res->map_info;
}

int vrend_renderer_resource_map(struct pipe_resource *pres, void **map, uint64_t *out_size)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;
   if (!has_bits(res->storage_bits, VREND_STORAGE_GL_BUFFER | VREND_STORAGE_GL_IMMUTABLE))
      return -EINVAL;

   glBindBufferARB(res->target, res->gl_id);
   *map = glMapBufferRange(res->target, 0, res->size, res->buffer_storage_flags);
   if (!*map)
      return -EINVAL;

   glBindBufferARB(res->target, 0);
   *out_size = res->size;
   return 0;
}

int vrend_renderer_resource_unmap(struct pipe_resource *pres)
{
   struct vrend_resource *res = (struct vrend_resource *)pres;
   if (!has_bits(res->storage_bits, VREND_STORAGE_GL_BUFFER | VREND_STORAGE_GL_IMMUTABLE))
      return -EINVAL;

   glBindBufferARB(res->target, res->gl_id);
   glUnmapBuffer(res->target);
   glBindBufferARB(res->target, 0);
   return 0;
}

int vrend_renderer_create_ctx0_fence(uint32_t fence_id)
{
   if (!vrend_state.ctx0)
         return EINVAL;
   return vrend_renderer_create_fence(vrend_state.ctx0,
         VIRGL_RENDERER_FENCE_FLAG_MERGEABLE, fence_id);
}

#ifdef HAVE_EPOXY_EGL_H
static bool find_ctx0_fence_locked(struct list_head *fence_list,
                                   uint64_t fence_id,
                                   bool *seen_first,
                                   struct vrend_fence **fence)
{
   list_for_each_entry(struct vrend_fence, iter, fence_list, fences) {
      /* only consider ctx0 fences */
      if (iter->ctx != vrend_state.ctx0)
         continue;

      if (iter->fence_id == fence_id) {
         *fence = iter;
         return true;
      }

      if (!*seen_first) {
         if (fence_id < iter->fence_id)
            return true;
         *seen_first = true;
      }
   }

   return false;
}
#endif

int vrend_renderer_export_ctx0_fence(uint32_t fence_id, int* out_fd) {
#ifdef HAVE_EPOXY_EGL_H
   int ret = 0;

   if (!vrend_state.use_egl_fence) {
      return -EINVAL;
   }

   if (vrend_state.sync_thread)
      mtx_lock(&vrend_state.fence_mutex);

   bool seen_first = false;
   struct vrend_fence *fence = NULL;
   bool found = find_ctx0_fence_locked(&vrend_state.fence_list,
                                       fence_id,
                                       &seen_first,
                                       &fence);
   if (!found) {
      found = find_ctx0_fence_locked(&vrend_state.fence_wait_list,
                                     fence_id,
                                     &seen_first,
                                     &fence);
      /* consider signaled when no active ctx0 fence at all */
      if (!found && !seen_first)
         found = true;
   }

   if (found) {
      if (fence)
         ret = virgl_egl_export_fence(egl, fence->eglsyncobj, out_fd) ? 0 : -EINVAL;
      else
         ret = virgl_egl_export_signaled_fence(egl, out_fd) ? 0 : -EINVAL;
   }

   if (vrend_state.sync_thread)
      mtx_unlock(&vrend_state.fence_mutex);

   return ret;
#else
   (void)fence_id;
   (void)out_fd;
#endif
   return -EINVAL;
}

void vrend_renderer_get_meminfo(struct vrend_context *ctx, uint32_t res_handle)
{
   struct vrend_resource *res;
   struct virgl_memory_info *info;

   res = vrend_renderer_ctx_res_lookup(ctx, res_handle);
   if (!res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
      return;
   }

   if (!res->iov || res->iov->iov_len < sizeof(struct virgl_memory_info)) {
       vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_handle);
       return;
   }

   info = (struct virgl_memory_info *)res->iov->iov_base;

   if (has_feature(feat_nvx_gpu_memory_info)) {
         GLint i;
         glGetIntegerv(GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX, &i);
         info->total_device_memory = i;
         glGetIntegerv(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX, &i);
         info->total_staging_memory = i - info->total_device_memory;
         glGetIntegerv(GL_GPU_MEMORY_INFO_EVICTION_COUNT_NVX, &i);
         info->nr_device_memory_evictions = i;
         glGetIntegerv(GL_GPU_MEMORY_INFO_EVICTED_MEMORY_NVX, &i);
         info->device_memory_evicted = i;
      }

   if (has_feature(feat_ati_meminfo)) {
      GLint i[4];
      glGetIntegerv(GL_VBO_FREE_MEMORY_ATI, i);
      info->avail_device_memory = i[0];
      info->avail_staging_memory = i[2];
   }
}

static uint32_t vrend_renderer_get_video_memory(void)
{
   GLint video_memory = vrend_winsys_query_video_memory();

   if (!video_memory && has_feature(feat_nvx_gpu_memory_info))
      glGetIntegerv(GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX, &video_memory);

   return video_memory;
}

void vrend_context_emit_string_marker(struct vrend_context *ctx, GLsizei length, const char * message)
{
    VREND_DEBUG(dbg_khr, ctx, "MARKER: '%.*s'\n", length, message);

#ifdef ENABLE_TRACING
    char buf[256];
    if (length > 6 && !strncmp(message, "BEGIN:", 6)) {
       snprintf(buf, 256, "%.*s", length - 6, &message[6]);
       char *scope_name = ralloc_strndup(ctx->active_markers, buf, 256);
       void *scope = TRACE_SCOPE_BEGIN(scope_name);
       _mesa_hash_table_insert(ctx->active_markers, scope_name, (void *)scope);
    } else if (length > 4 && !strncmp(message, "END:", 4)) {
       snprintf(buf, 256, "%.*s", length - 4, &message[4]);
       struct hash_entry *entry = _mesa_hash_table_search(ctx->active_markers, buf);
       void *orig_scope = entry->data;
       TRACE_SCOPE_END(orig_scope);
       ralloc_free((void *)entry->key);
      _mesa_hash_table_remove(ctx->active_markers, entry);
    }
#endif

    if (has_feature(feat_khr_debug))  {
        if (vrend_state.use_gles)
            glDebugMessageInsertKHR(GL_DEBUG_SOURCE_APPLICATION_KHR,
                                    GL_DEBUG_TYPE_MARKER_KHR,
                                    0, GL_DEBUG_SEVERITY_NOTIFICATION,
                                    length, message);
        else
            glDebugMessageInsert(GL_DEBUG_SOURCE_APPLICATION,
                                 GL_DEBUG_TYPE_MARKER,
                                 0, GL_DEBUG_SEVERITY_NOTIFICATION_KHR,
                                 length, message);
    }
}

#ifdef ENABLE_VIDEO
struct vrend_video_context *vrend_context_get_video_ctx(struct vrend_context *ctx)
{
    return ctx->video;
}
#endif

#ifdef ENABLE_GBM
static size_t get_gbm_bo_plane_size(struct gbm_bo *bo, int plane)
{
   off_t size;
   int fd;

   fd = gbm_bo_get_fd_for_plane(bo, plane);
   if (fd < 0)
      return 0;

   size = lseek(fd, 0, SEEK_END);
   close(fd);

   if (size < 0)
      return 0;

   return size;
}

int
vrend_renderer_pipe_resource_get_layout(struct vrend_context *ctx,
                                        uint32_t out_res_id, uint32_t res_id)
{
   struct virgl_resource_layout layout = { 0 };
   struct vrend_resource *out_res, *res;

   out_res = vrend_renderer_ctx_res_lookup(ctx, out_res_id);
   if (!out_res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, out_res_id);
      return EINVAL;
   }

   res = vrend_renderer_ctx_res_lookup(ctx, res_id);
   if (!res) {
      vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, res_id);
      return EINVAL;
   }

   if (res->gbm_bo) {
      int num_planes = gbm_bo_get_plane_count(res->gbm_bo);

      if (layout.num_planes > VIRGL_RESOURCE_MAX_PLANES) {
         virgl_error("resource GBM has too many planes\n");
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_UNKNOWN, res_id);
         return EINVAL;
      }

      layout.num_planes = num_planes;
      layout.modifier = gbm_bo_get_modifier(res->gbm_bo);

      for (uint32_t i = 0; i < layout.num_planes; i++) {
         layout.planes[i].offset = gbm_bo_get_offset(res->gbm_bo, i);
         layout.planes[i].stride = gbm_bo_get_stride_for_plane(res->gbm_bo, i);
         layout.planes[i].size = get_gbm_bo_plane_size(res->gbm_bo, i);

         if (!layout.planes[i].size) {
            virgl_error("failed to get GBM plane size\n");
            vrend_report_context_error(ctx, VIRGL_ERROR_CTX_UNKNOWN, res_id);
            return EINVAL;
         }
      }
   }

   if (out_res->iov) {
      if (vrend_write_to_iovec(out_res->iov, out_res->num_iovs, 0,
                               (const void *) &layout, sizeof(layout)) != sizeof(layout)) {
         virgl_error("resource layout does not fit IOV size\n");
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, out_res_id);
         return EINVAL;
      }
   } else {
      if (out_res->base.width0 >= sizeof(layout)) {
         *((struct virgl_resource_layout *) out_res->ptr) = layout;
      } else {
         virgl_error("resource layout does not fit buffer size\n");
         vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_RESOURCE, out_res_id);
         return EINVAL;
      }
   }

   return 0;
}
#else
int
vrend_renderer_pipe_resource_get_layout(UNUSED struct vrend_context *ctx,
                                        UNUSED uint32_t out_res_id, UNUSED uint32_t res_id)
{
   return EINVAL;
}
#endif
