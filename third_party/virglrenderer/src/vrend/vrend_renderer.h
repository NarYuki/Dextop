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

#ifndef VREND_RENDERER_H
#define VREND_RENDERER_H

#include "pipe/p_state.h"
#include "util/list.h"
#include "util/u_inlines.h"
#include "virgl_protocol.h"
#include "vrend_debug.h"
#include "vrend_tweaks.h"
#include "vrend_iov.h"
#ifdef ENABLE_GBM
#include "vrend_winsys_gbm.h"
#else
#define VIRGL_GBM_MAX_PLANES 4
#endif
#include "virgl_hw.h"
#include <epoxy/gl.h>
#ifdef WIN32
#include <d3d11.h>
#endif

#ifdef ENABLE_TESTS
/* With this flag set, the transfer will not try to use GBM mappings.
 * Intended to be used in unit tests only. */
#define VIRGL_COPY_TRANSFER3D_FLAGS_DEBUG_TEST_NO_GBM_MAPPING (1 << 2)
#endif


typedef void *virgl_gl_context;
typedef void *virgl_gl_drawable;

struct virgl_gl_ctx_param {
   int major_ver;
   int minor_ver;
   bool shared;
   bool compat_ctx;
};

struct virgl_context;
struct virgl_resource;
struct vrend_context;

/* Number of mipmap levels for which to keep the backing iov offsets.
 * Value mirrored from mesa/virgl
 */
#define VR_MAX_TEXTURE_2D_LEVELS 15

#define VREND_STORAGE_GUEST_MEMORY       BIT(0)
#define VREND_STORAGE_GL_TEXTURE         BIT(1)
#define VREND_STORAGE_GL_BUFFER          BIT(2)
#define VREND_STORAGE_EGL_IMAGE          BIT(3)
#define VREND_STORAGE_GBM_BUFFER         BIT(4)
#define VREND_STORAGE_HOST_SYSTEM_MEMORY BIT(5)
#define VREND_STORAGE_GL_IMMUTABLE       BIT(6)
#define VREND_STORAGE_GL_MEMOBJ          BIT(7)
#define VREND_STORAGE_D3D_TEXTURE        BIT(8)

struct vrend_resource {
   struct pipe_resource base;
   uint32_t storage_bits;
   uint32_t map_info;

   GLuint gl_id;
   GLenum target;

   GLuint tbo_tex_id;/* tbos have two ids to track */
   bool y_0_top;

   /* used for keeping track of multisampled renderbuffer for
    * GL_EXT_multisampled_render_to_texture. */
   GLuint rbo_id;

   /* Pointer to system memory storage for this resource. Only valid for
    * VREND_RESOURCE_STORAGE_GUEST_ELSE_SYSTEM buffer storage.
    */
   char *ptr;
   /* IOV pointing to shared guest memory storage for this resource. */
   const struct iovec *iov;
   uint32_t num_iovs;
   uint64_t mipmap_offsets[VR_MAX_TEXTURE_2D_LEVELS];
   void *gbm_bo, *egl_image;
   void *aux_plane_egl_image[VIRGL_GBM_MAX_PLANES];
#ifdef WIN32
   ID3D11Texture2D *d3d_tex2d;
#endif

   uint64_t size;
   GLbitfield buffer_storage_flags;
   GLuint memobj;

   uint32_t blob_id;
   struct list_head head;
   bool is_imported;
};

#define VIRGL_TEXTURE_NEED_SWIZZLE        (1 << 0)
#define VIRGL_TEXTURE_CAN_TEXTURE_STORAGE (1 << 1)
#define VIRGL_TEXTURE_CAN_READBACK        (1 << 2)
#define VIRGL_TEXTURE_CAN_TARGET_RECTANGLE (1 << 3)
#define VIRGL_TEXTURE_CAN_MULTISAMPLE      (1 << 4)

enum view_class {
   view_class_unsupported,
   view_class_128,
   view_class_96,
   view_class_64,
   view_class_48,
   view_class_32,
   view_class_24,
   view_class_16,
   view_class_8,
   view_class_rgtc1_red,
   view_class_rgtc2_rg,
   view_class_bptc_unorm,
   view_class_bptc_float,
   view_class_dxt1_rgb,
   view_class_dxt1_rgba,
   view_class_dxt3_rgba,
   view_class_dxt5_rgba,
   view_class_eac_r11,
   view_class_eac_rg11,
   view_class_etc2_r11,
   view_class_etc2_rgb,
   view_class_etc2_rgba,
   view_class_etc2_eac_rgba,
   view_class_astc_4x4_rgba,
   view_class_astc_5x4_rgba,
   view_class_astc_5x5_rgba,
   view_class_astc_6x5_rgba,
   view_class_astc_6x6_rgba,
   view_class_astc_8x5_rgba,
   view_class_astc_8x6_rgba,
   view_class_astc_8x8_rgba,
   view_class_astc_10x5_rgba,
   view_class_astc_10x6_rgba,
   view_class_astc_10x8_rgba,
   view_class_astc_10x10_rgba,
   view_class_astc_12x10_rgba,
   view_class_astc_12x12_rgba
};


struct vrend_format_table {
   enum virgl_formats format;
   GLenum internalformat;
   GLenum glformat;
   GLenum gltype;
   enum pipe_swizzle swizzle[4];
   enum view_class view_class;
   uint32_t bindings;
   uint32_t flags;
};

typedef void (*vrend_context_fence_retire)(uint64_t fence_id,
                                           void *retire_data);

struct vrend_if_cbs {
   vrend_context_fence_retire ctx0_fence_retire;

   virgl_gl_context (*create_gl_context)(int scanout, struct virgl_gl_ctx_param *params);
   void (*destroy_gl_context)(virgl_gl_context ctx);
   int (*make_current)(virgl_gl_context ctx);
   int (*get_drm_fd)(void);
   virgl_gl_context (*create_gl_context_surfaceless)(int scanout, struct virgl_gl_ctx_param *params);
   void (*destroy_gl_context_surfaceless)(virgl_gl_context ctx);
   int (*make_current_surfaceless)(virgl_gl_context ctx);
};

#define VREND_USE_THREAD_SYNC (1 << 0)
#define VREND_USE_EXTERNAL_BLOB (1 << 1)
#define VREND_USE_ASYNC_FENCE_CB (1 << 2)
#define VREND_USE_VIDEO          (1 << 3)
#define VREND_D3D11_SHARE_TEXTURE (1 << 4)
#define VREND_USE_COMPAT_CONTEXT (1 << 5)
#define VREND_USE_GLES (1 << 6)
#define VREND_USE_GBM_LAYOUT (1 << 7)

bool vrend_check_no_error(struct vrend_context *ctx);

const struct virgl_resource_pipe_callbacks *
vrend_renderer_get_pipe_callbacks(void);

int vrend_renderer_init(const struct vrend_if_cbs *cbs, uint32_t flags);

void vrend_insert_format(struct vrend_format_table *entry, uint32_t bindings, uint32_t flags);
bool vrend_check_framebuffer_mixed_color_attachements(void);

void vrend_insert_format_swizzle(int override_format, struct vrend_format_table *entry,
                                 uint32_t bindings, enum pipe_swizzle swizzle[4], uint32_t flags);
const struct vrend_format_table *vrend_get_format_table_entry(enum virgl_formats format);

int vrend_create_shader(struct vrend_context *ctx,
                        uint32_t handle,
                        const struct pipe_stream_output_info *stream_output,
                        uint32_t req_local_mem,
                        const char *shd_text, uint32_t offlen, uint32_t num_tokens,
                        enum pipe_shader_type type, uint32_t pkt_length);

void vrend_link_program_hook(struct vrend_context *ctx, uint32_t *handles);

void vrend_bind_shader(struct vrend_context *ctx,
                       uint32_t handle,
                       enum pipe_shader_type type);

void vrend_bind_vs_so(struct vrend_context *ctx,
                      uint32_t handle);
void vrend_clear(struct vrend_context *ctx,
                 unsigned buffers,
                 const union pipe_color_union *color,
                 double depth, unsigned stencil);

int vrend_clear_texture(struct vrend_context* ctx,
                        struct vrend_resource *res, uint32_t level,
                        const struct pipe_box *box,
                        const void * data);

void vrend_clear_surface(struct vrend_context *ctx,
                         uint32_t surf_handle,
                         unsigned buffers,
                         const union pipe_color_union *color,
                         unsigned dstx, unsigned dsty,
                         unsigned width, unsigned height,
                         bool render_condition_enabled);

int vrend_draw_vbo(struct vrend_context *ctx,
                   const struct pipe_draw_info *info,
                   uint32_t cso, uint32_t indirect_handle, uint32_t indirect_draw_count_handle);

void vrend_set_framebuffer_state(struct vrend_context *ctx,
                                 uint32_t nr_cbufs, uint32_t surf_handle[PIPE_MAX_COLOR_BUFS],
                                 uint32_t zsurf_handle);

struct vrend_context *vrend_create_context(int id, uint32_t nlen, const char *debug_name);
void vrend_destroy_context(struct vrend_context *ctx);
struct virgl_context *vrend_renderer_context_create(uint32_t handle,
                                                    uint32_t nlen,
                                                    const char *name);

struct vrend_renderer_resource_create_args {
   enum pipe_texture_target target;
   uint32_t format;
   uint32_t bind;
   uint32_t width;
   uint32_t height;
   uint32_t depth;
   uint32_t array_size;
   uint32_t last_level;
   uint32_t nr_samples;
   uint32_t flags;
};

/* set the type info of an untyped blob resource */
struct vrend_renderer_resource_set_type_args {
   uint32_t format;
   uint32_t bind;
   uint32_t width;
   uint32_t height;
   uint32_t usage;
   uint64_t modifier;
   uint32_t plane_count;
   uint32_t plane_strides[VIRGL_GBM_MAX_PLANES];
   uint32_t plane_offsets[VIRGL_GBM_MAX_PLANES];
};

struct pipe_resource *
vrend_renderer_resource_create(const struct vrend_renderer_resource_create_args *args,
                               void *image_eos);

int vrend_create_surface(struct vrend_context *ctx, uint32_t handle,
                         struct vrend_resource *res,
                         enum virgl_formats format, uint32_t level, uint32_t first_layer,
                         uint32_t last_layer, uint32_t nr_samples);
int vrend_create_sampler_view(struct vrend_context *ctx, uint32_t handle,
                              struct vrend_resource *res, enum virgl_formats format,
                              enum pipe_texture_target pipe_target, uint32_t val0,
                              uint32_t val1, uint32_t swizzle_packed);

int vrend_create_sampler_state(struct vrend_context *ctx,
                               uint32_t handle,
                               struct pipe_sampler_state *templ);

int vrend_create_so_target(struct vrend_context *ctx,
                           uint32_t handle,
                           uint32_t res_handle,
                           uint32_t buffer_offset,
                           uint32_t buffer_size);

void vrend_set_streamout_targets(struct vrend_context *ctx,
                                 uint32_t append_bitmask,
                                 uint32_t num_targets,
                                 uint32_t *handles);

int vrend_create_vertex_elements_state(struct vrend_context *ctx,
                                       uint32_t handle,
                                       unsigned num_elements,
                                       const struct pipe_vertex_element *elements);
void vrend_bind_vertex_elements_state(struct vrend_context *ctx,
                                      uint32_t handle);

void vrend_set_single_vbo(struct vrend_context *ctx,
                          uint32_t index,
                          uint32_t stride,
                          uint32_t buffer_offset,
                          uint32_t res_handle);
void vrend_set_num_vbo(struct vrend_context *ctx,
                       int num_vbo);

int vrend_transfer_inline_write(struct vrend_context *ctx,
                                uint32_t dst_handle,
                                const struct vrend_transfer_info *info);

int vrend_renderer_copy_transfer3d(struct vrend_context *ctx,
                                   uint32_t dst_handle,
                                   struct vrend_resource *dst_res, struct vrend_resource *src_res,
                                   const struct vrend_transfer_info *info);

int vrend_renderer_copy_transfer3d_from_host(struct vrend_context *ctx, uint32_t dst_handle,
                                             uint32_t src_handle,
                                             struct vrend_resource *src_res, struct vrend_resource *dst_res,
                                             const struct vrend_transfer_info *info);

void vrend_set_viewport_states(struct vrend_context *ctx,
                               uint32_t start_slot, uint32_t num_viewports,
                               const struct pipe_viewport_state *state);
void vrend_set_num_sampler_views(struct vrend_context *ctx,
                                 uint32_t shader_type,
                                 uint32_t start_slot,
                                 uint32_t num_sampler_views);
void vrend_set_single_sampler_view(struct vrend_context *ctx,
                                   uint32_t shader_type,
                                   uint32_t index,
                                   uint32_t res_handle);
int vrend_create_dsa(struct vrend_context *ctx,
                     uint32_t handle,
                     const struct pipe_depth_stencil_alpha_state *dsa_state);

void vrend_object_bind_blend(struct vrend_context *ctx,
                             uint32_t handle);
void vrend_object_bind_dsa(struct vrend_context *ctx,
                           uint32_t handle);
void vrend_object_bind_rasterizer(struct vrend_context *ctx,
                                  uint32_t handle);

void vrend_bind_sampler_states(struct vrend_context *ctx,
                               enum pipe_shader_type shader_type,
                               uint32_t start_slot,
                               uint32_t num_states,
                               const uint32_t *handles);
void vrend_set_index_buffer(struct vrend_context *ctx,
                            uint32_t res_handle,
                            uint32_t index_size,
                            uint32_t offset);
int vrend_set_single_image_view(struct vrend_context *ctx,
                                 uint32_t shader_type,
                                 uint32_t index,
                                 uint32_t format, uint32_t access,
                                 uint32_t layer_offset, uint32_t level_size,
                                 uint32_t handle);
void vrend_set_single_ssbo(struct vrend_context *ctx,
                           uint32_t shader_type,
                           uint32_t index,
                           uint32_t offset, uint32_t length,
                           uint32_t handle);
void vrend_set_single_abo(struct vrend_context *ctx,
                          uint32_t index,
                          uint32_t offset, uint32_t length,
                          uint32_t handle);
void vrend_memory_barrier(struct vrend_context *ctx,
                          unsigned flags);
void vrend_launch_grid(struct vrend_context *ctx,
                       uint32_t *block,
                       uint32_t *grid,
                       uint32_t indirect_handle,
                       uint32_t indirect_offset);
void vrend_set_framebuffer_state_no_attach(struct vrend_context *ctx,
                                           uint32_t width, uint32_t height,
                                           uint32_t layers, uint32_t samples);
void vrend_texture_barrier(struct vrend_context *ctx,
                           unsigned flags);

int vrend_renderer_transfer_iov(struct vrend_context *ctx,
                                uint32_t dst_handle,
                                const struct vrend_transfer_info *info,
                                int transfer_mode);

int vrend_renderer_transfer_pipe(struct pipe_resource *pres,
                                 const struct vrend_transfer_info *info,
                                 int transfer_mode);

void vrend_renderer_resource_copy_region(struct vrend_context *ctx,
                                         uint32_t dst_handle, uint32_t dst_level,
                                         uint32_t dstx, uint32_t dsty, uint32_t dstz,
                                         uint32_t src_handle, uint32_t src_level,
                                         const struct pipe_box *src_box);

void vrend_renderer_blit(struct vrend_context *ctx,
                         uint32_t dst_handle, uint32_t src_handle,
                         const struct pipe_blit_info *info);

void vrend_set_stencil_ref(struct vrend_context *ctx, struct pipe_stencil_ref *ref);
void vrend_set_blend_color(struct vrend_context *ctx, struct pipe_blend_color *color);
void vrend_set_scissor_state(struct vrend_context *ctx,
                             uint32_t start_slot,
                             uint32_t num_scissor,
                             struct pipe_scissor_state *ss);

void vrend_set_polygon_stipple(struct vrend_context *ctx, struct pipe_poly_stipple *ps);

void vrend_set_clip_state(struct vrend_context *ctx, struct pipe_clip_state *ucp);
void vrend_set_sample_mask(struct vrend_context *ctx, unsigned sample_mask);
void vrend_set_min_samples(struct vrend_context *ctx, unsigned min_samples);

void vrend_set_constants(struct vrend_context *ctx,
                         uint32_t shader,
                         uint32_t num_constant,
                         const float *data);

void vrend_set_uniform_buffer(struct vrend_context *ctx, uint32_t shader,
                              uint32_t index, uint32_t offset, uint32_t length,
                              uint32_t res_handle);

void vrend_fb_bind_texture_id(struct vrend_resource *res,
                              int id, GLuint idx, GLint level,
                              GLint layer, uint32_t samples);

void vrend_set_tess_state(struct vrend_context *ctx, const float tess_factors[6]);

void vrend_renderer_fini(void);

void vrend_renderer_set_fence_retire(struct vrend_context *ctx,
                                     vrend_context_fence_retire retire,
                                     void *retire_data);

int vrend_renderer_create_fence(struct vrend_context *ctx,
                                uint32_t flags,
                                uint64_t fence_id);

void vrend_renderer_check_fences(void);

int vrend_renderer_create_ctx0_fence(uint32_t fence_id);
int vrend_renderer_export_ctx0_fence(uint32_t fence_id, int* out_fd);

bool vrend_hw_switch_context(struct vrend_context *ctx, bool now);
uint32_t vrend_renderer_object_insert(struct vrend_context *ctx, void *data,
                                      uint32_t handle, enum virgl_object_type type);
void vrend_renderer_object_destroy(struct vrend_context *ctx, uint32_t handle);

int vrend_create_query(struct vrend_context *ctx, uint32_t handle,
                       uint32_t query_type, uint32_t query_index,
                       uint32_t res_handle, uint32_t offset);

int vrend_begin_query(struct vrend_context *ctx, uint32_t handle);
int vrend_end_query(struct vrend_context *ctx, uint32_t handle);
int vrend_get_query_result(struct vrend_context *ctx, uint32_t handle,
                            uint32_t wait);
int vrend_get_query_result_qbo(struct vrend_context *ctx, uint32_t handle,
                                uint32_t qbo_handle,
                                uint32_t wait, uint32_t result_type, uint32_t offset,
                                int32_t index);
void vrend_render_condition(struct vrend_context *ctx,
                            uint32_t handle,
                            bool condtion,
                            uint32_t mode);
void *vrend_renderer_get_cursor_contents(struct pipe_resource *pres,
                                         uint32_t *width,
                                         uint32_t *height);

void vrend_renderer_fill_caps(uint32_t set, uint32_t version,
                              union virgl_caps *caps);

GLint64 vrend_renderer_get_timestamp(void);

void vrend_build_format_list_common(void);
void vrend_build_format_list_gl(void);
void vrend_build_format_list_gles(void);
void vrend_build_emulated_format_list_gles(void);
void vrend_check_texture_storage(struct vrend_format_table *table);
void vrend_check_texture_multisample(struct vrend_format_table *table,
                                     bool enable_storage);

struct vrend_resource *vrend_renderer_ctx_res_lookup(struct vrend_context *ctx,
                                                     int res_handle);

void vrend_renderer_resource_destroy(struct vrend_resource *res);

static inline void
vrend_resource_reference(struct vrend_resource **ptr, struct vrend_resource *tex)
{
   struct vrend_resource *old_tex = *ptr;

   /**
    * Check the comment in vrend_sampler_view_reference for more information.
    * Like above here only the address of the first element of *ptr.
    */
   if (pipe_reference((struct pipe_reference *)*ptr, (struct pipe_reference *)tex))
      vrend_renderer_resource_destroy(old_tex);
   *ptr = tex;
}

void vrend_renderer_force_ctx_0(void);

void vrend_renderer_get_rect(struct pipe_resource *pres,
                             const struct iovec *iov, unsigned int num_iovs,
                             uint32_t offset,
                             int x, int y, int width, int height);

void vrend_renderer_attach_res_ctx(struct vrend_context *ctx,
                                   struct virgl_resource *res);
void vrend_renderer_detach_res_ctx(struct vrend_context *ctx,
                                   struct virgl_resource *res);

struct vrend_context_tweaks *vrend_get_context_tweaks(struct vrend_context *ctx);

struct vrend_renderer_resource_info {
   uint32_t handle;
   uint32_t format;
   uint32_t width;
   uint32_t height;
   uint32_t depth;
   uint32_t flags;
   uint32_t tex_id;
   uint32_t stride;
};

struct vrend_blit_info {
   const struct pipe_blit_info b;
   GLuint src_view;
   GLuint dst_view;
   enum pipe_swizzle swizzle[4];
   int src_y1, src_y2, dst_y1, dst_y2;
   GLenum gl_filter;
   bool needs_swizzle;
   bool can_fbo_blit;
   bool has_texture_srgb_decode;
   bool has_srgb_write_control;
   bool needs_manual_srgb_decode;
   bool needs_manual_srgb_encode;
};

void vrend_renderer_resource_get_info(struct pipe_resource *pres,
                                      struct vrend_renderer_resource_info *info);

void vrend_renderer_get_cap_set(uint32_t cap_set, uint32_t *max_ver,
                                uint32_t *max_size);

void vrend_renderer_create_sub_ctx(struct vrend_context *ctx, int sub_ctx_id);
void vrend_renderer_destroy_sub_ctx(struct vrend_context *ctx, int sub_ctx_id);
void vrend_renderer_set_sub_ctx(struct vrend_context *ctx, int sub_ctx_id);

void vrend_report_context_error_internal(const char *fname, struct vrend_context *ctx,
                                   enum virgl_ctx_errors error, uint32_t value);

#define vrend_report_context_error(ctx, error, value) \
    vrend_report_context_error_internal(__func__, ctx, error, value)

#define vrend_report_buffer_error(ctx, cmd) \
    vrend_report_context_error(ctx, VIRGL_ERROR_CTX_ILLEGAL_CMD_BUFFER, cmd)

void vrend_fb_bind_texture(struct vrend_resource *res,
                           GLuint idx,
                           GLint level, GLint layer);
bool vrend_format_is_emulated_alpha(enum virgl_formats format);
bool vrend_format_is_bgra(enum virgl_formats format);

#define VREND_COPY_COMPAT_FLAG_ALLOW_COMPRESSED (1u << 0)
#define VREND_COPY_COMPAT_FLAG_ONE_IS_EGL_IMAGE (1u << 1)
bool format_is_copy_compatible(enum virgl_formats src, enum virgl_formats dst,
                               unsigned int flags);

void vrend_renderer_prepare_reset(void);
void vrend_renderer_reset(void);
void vrend_renderer_poll(void);
int vrend_renderer_get_poll_fd(void);

unsigned vrend_context_has_debug_flag(const struct vrend_context *ctx,
                                      enum virgl_debug_flags flag);

unsigned vrend_renderer_query_multisample_caps(unsigned max_samples,
                                               struct virgl_caps_v2 *caps);

struct gl_version {
   uint32_t major;
   uint32_t minor;
};

static const struct gl_version gl_versions[] = { {4,6}, {4,5}, {4,4}, {4,3}, {4,2}, {4,1}, {4,0},
                                                 {3,3}, {3,2}, {3,1}, {3,0} };

extern const struct vrend_if_cbs *vrend_clicbs;

int vrend_renderer_export_query(struct pipe_resource *pres,
                                struct virgl_renderer_export_query *export_query);

void vrend_sync_make_current(virgl_gl_context);

int
vrend_renderer_pipe_resource_create(struct vrend_context *ctx, uint32_t blob_id,
                                    const struct vrend_renderer_resource_create_args *args);

struct pipe_resource *vrend_get_blob_pipe(struct vrend_context *ctx, uint64_t blob_id);

int
vrend_renderer_pipe_resource_set_type(struct vrend_context *ctx,
                                      uint32_t res_id,
                                      const struct vrend_renderer_resource_set_type_args *args);

uint32_t vrend_renderer_resource_get_map_info(struct pipe_resource *pres);

int vrend_renderer_resource_map(struct pipe_resource *pres, void **map, uint64_t *out_size);

int vrend_renderer_resource_unmap(struct pipe_resource *pres);

void vrend_renderer_get_meminfo(struct vrend_context *ctx, uint32_t res_handle);

void vrend_context_emit_string_marker(struct vrend_context *ctx, GLsizei length, const char * message);

struct vrend_video_context *vrend_context_get_video_ctx(struct vrend_context *ctx);

int
vrend_renderer_resource_d3d11_texture2d(struct pipe_resource *res, void **handle);

int
vrend_renderer_pipe_resource_get_layout(struct vrend_context *ctx,
                                        uint32_t out_res_id, uint32_t res_id);

#endif
