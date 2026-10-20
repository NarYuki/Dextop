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

/* gallium blitter implementation in GL */
/* for when we can't use glBlitFramebuffer */

#include <stdio.h>

#include "util/hash_table.h"
#include "util/macros.h"
#include "util/u_memory.h"
#include "util/u_format.h"
#include "util/u_pointer.h"
#include "util/u_texture.h"

#include "vrend_shader.h"
#include "vrend_renderer.h"
#include "vrend_blitter.h"

#define XXH_INLINE_ALL
#include "util/xxhash.h"

#define DEST_SWIZZLE_SNIPPET_SIZE 64

#define BLIT_USE_GLES           (1 << 0)
#define BLIT_USE_MSAA           (1 << 1)
#define BLIT_USE_DEPTH          (1 << 2)
#define BLIT_MANUAL_SRGB_DECODE (1 << 3)
#define BLIT_MANUAL_SRGB_ENCODE (1 << 4)

struct vec4 {
   GLfloat x,y,z,w;
};

struct blit_coord {
  struct vec4 pos;
  struct vec4 tex;
};

struct vrend_blitter_ctx {
   virgl_gl_context gl_context;
   bool initialised;
   bool use_gles;

   GLuint vaoid;

   struct hash_table_u64 *blit_programs;

   GLuint vs;
   GLuint fb_id;

   unsigned dst_width;
   unsigned dst_height;

   GLuint vbo_id;
   struct blit_coord vertices[4];
};

static struct vrend_blitter_ctx vrend_blit_ctx;

struct blit_point {
    int x;
    int y;
};

struct blit_swizzle_and_type {
  char *swizzle;
  char *type;
  bool is_array;
};

#pragma pack(push,1)
struct PACKED blit_prog_key {
   bool is_color: 1;
   bool is_msaa: 1;
   bool manual_srgb_decode: 1;
   bool manual_srgb_encode: 1;
   enum pipe_texture_target pipe_tex_target: 4;
   uint8_t num_samples;
   struct {
      bool has_swizzle: 1;
      enum virgl_formats src_format: 9;
      enum pipe_swizzle swizzle1: 3;
      enum pipe_swizzle swizzle2: 3;
      enum pipe_swizzle swizzle3: 3;
      enum pipe_swizzle swizzle4: 3;
   } texcol;
};
#pragma pack(pop)

static_assert(sizeof(struct blit_prog_key) <= sizeof(uint64_t),
              "struct blit_prog_key needs to fit into a hashtable64 key");

static inline uint64_t
prog_key_to_uint64( struct blit_prog_key prog_key )
{
   union {
      struct blit_prog_key prog_key;
      uint64_t u;
   } pu;
   pu.prog_key = prog_key;
   return pu.u;
}

static GLint blit_shader_build_and_check(GLenum shader_type, const char *buf)
{
   GLint param;
   GLint id = glCreateShader(shader_type);
   glShaderSource(id, 1, (const char **)&buf, NULL);
   glCompileShader(id);

   glGetShaderiv(id, GL_COMPILE_STATUS, &param);
   if (param == GL_FALSE) {
      char infolog[65536];
      int len;
      glGetShaderInfoLog(id, 65536, &len, infolog);
      virgl_error("Shader failed to compile\n%s\n", infolog);
      virgl_error("GLSL:\n%s\n", buf);
      glDeleteShader(id);
      return 0;
   }
   return id;
}

static bool blit_shader_link_and_check(GLuint prog_id)
{
   GLint lret;

   glLinkProgram(prog_id);
   glGetProgramiv(prog_id, GL_LINK_STATUS, &lret);
   if (lret == GL_FALSE) {
      char infolog[65536];
      int len;
      glGetProgramInfoLog(prog_id, 65536, &len, infolog);
      virgl_error("Got error linking\n%s\n", infolog);
      /* dump shaders */
      glDeleteProgram(prog_id);
      return false;
   }
   return true;
}

static void create_dest_swizzle_snippet(const enum pipe_swizzle swizzle[4],
                                        char snippet[DEST_SWIZZLE_SNIPPET_SIZE])
{
   static const uint8_t invalid_swizzle = 0xff;
   ssize_t si = 0;
   enum pipe_swizzle inverse[4] = {invalid_swizzle, invalid_swizzle,
                                   invalid_swizzle, invalid_swizzle};

   for (int i = 0; i < 4; ++i) {
      if (swizzle[i] > 3)
         continue;

      if (inverse[swizzle[i]] == invalid_swizzle)
         inverse[swizzle[i]] = i;
   }

   for (int i = 0; i < 4; ++i) {
      int res = -1;
      if (inverse[i] > 3) {
          /* Use 0.0f for unused color values, 1.0f for an unused alpha value */
         res = snprintf(&snippet[si], DEST_SWIZZLE_SNIPPET_SIZE - si,
                        i < 3 ? "0.0f, " : "1.0f");
      } else {
         res = snprintf(&snippet[si], DEST_SWIZZLE_SNIPPET_SIZE - si,
                        "texel.%c%s", "rgba"[inverse[i]], i < 3 ? ", " : "");
      }
      si += res > 0 ? res : 0;
   }
}

static const char *vec4_type_for_tgsi_ret(enum tgsi_return_type tgsi_ret)
{
   switch (tgsi_ret) {
   case TGSI_RETURN_TYPE_SINT: return "ivec4";
   case TGSI_RETURN_TYPE_UINT: return "uvec4";
   default: return "vec4";
   }
}

static enum tgsi_return_type tgsi_ret_for_format(enum virgl_formats format)
{
   if (util_format_is_pure_uint(format))
      return TGSI_RETURN_TYPE_UINT;
   else if (util_format_is_pure_sint(format))
      return TGSI_RETURN_TYPE_SINT;

   return TGSI_RETURN_TYPE_UNORM;
}

static void blit_get_swizzle(enum tgsi_texture_type tgsi_tex_target, unsigned flags,
                             struct blit_swizzle_and_type *retval)
{
   retval->swizzle = "";
   retval->type = "";
   retval->is_array = false;
   switch (tgsi_tex_target) {
   case TGSI_TEXTURE_1D:
      if ((flags & (BLIT_USE_GLES | BLIT_USE_DEPTH)) == (BLIT_USE_GLES | BLIT_USE_DEPTH)) {
         retval->swizzle = ".xy";
         break;
      }
      /* fallthrough */
   case TGSI_TEXTURE_BUFFER:
      retval->swizzle = ".x";
      break;
   case TGSI_TEXTURE_2D_MSAA:
      if (flags & BLIT_USE_MSAA) {
         retval->type = "ivec2";
      }
      retval->swizzle = ".xy";
      break;
   case TGSI_TEXTURE_1D_ARRAY:
      if (flags & (BLIT_USE_GLES)) {
         retval->swizzle = ".xyz";
         break;
      }
      /* fallthrough */
   case TGSI_TEXTURE_2D:
   case TGSI_TEXTURE_RECT:
      retval->swizzle = ".xy";
      break;
   case TGSI_TEXTURE_2D_ARRAY_MSAA:
      if (flags & BLIT_USE_MSAA) {
         retval->type = "ivec3";
         retval->is_array = true;
      }
      /* fallthrough */
   case TGSI_TEXTURE_SHADOW1D:
   case TGSI_TEXTURE_SHADOW2D:
   case TGSI_TEXTURE_SHADOW1D_ARRAY:
   case TGSI_TEXTURE_SHADOWRECT:
   case TGSI_TEXTURE_3D:
   case TGSI_TEXTURE_CUBE:
   case TGSI_TEXTURE_2D_ARRAY:
      retval->swizzle = ".xyz";
      break;
   case TGSI_TEXTURE_SHADOWCUBE:
   case TGSI_TEXTURE_SHADOW2D_ARRAY:
   case TGSI_TEXTURE_SHADOWCUBE_ARRAY:
   case TGSI_TEXTURE_CUBE_ARRAY:
      retval->swizzle = "";
      break;
   default:
      if (flags & BLIT_USE_MSAA) {
         break;
      }
      retval->swizzle = ".xy";
      break;
   }
}

static GLuint blit_build_frag_tex_col(struct vrend_blitter_ctx *blit_ctx,
                                      enum tgsi_texture_type tgsi_tex_target,
                                      enum tgsi_return_type tgsi_ret,
                                      const enum pipe_swizzle swizzle[4],
                                      int nr_samples,
                                      uint32_t flags)
{
   char shader_buf[4096];
   struct blit_swizzle_and_type swizzle_and_type;
   unsigned swizzle_flags = 0;
   char dest_swizzle_snippet[DEST_SWIZZLE_SNIPPET_SIZE] = "texel";
   const char *ext_str = "";
   bool msaa = nr_samples > 1;

   if (msaa && !blit_ctx->use_gles)
      ext_str = "#extension GL_ARB_texture_multisample : enable\n";
   else if (tgsi_tex_target == TGSI_TEXTURE_CUBE_ARRAY ||
            tgsi_tex_target == TGSI_TEXTURE_SHADOWCUBE_ARRAY) {
      if (blit_ctx->use_gles)
         ext_str = "#extension GL_EXT_texture_cube_map_array : require\n";
      else
         ext_str = "#extension GL_ARB_texture_cube_map_array : require\n";
   }

   if (blit_ctx->use_gles)
      swizzle_flags |= BLIT_USE_GLES;
   if (msaa)
      swizzle_flags |= BLIT_USE_MSAA;
   blit_get_swizzle(tgsi_tex_target, swizzle_flags, &swizzle_and_type);

   if (swizzle)
      create_dest_swizzle_snippet(swizzle, dest_swizzle_snippet);

   bool needs_manual_srgb_decode = has_bit(flags, BLIT_MANUAL_SRGB_DECODE);
   bool needs_manual_srgb_encode = has_bit(flags, BLIT_MANUAL_SRGB_ENCODE);

   if (msaa)
      snprintf(shader_buf, 4096, blit_ctx->use_gles ?
                                 (swizzle_and_type.is_array ? FS_TEXFETCH_COL_MSAA_ARRAY_GLES
                                                   : FS_TEXFETCH_COL_MSAA_GLES)
                                 : FS_TEXFETCH_COL_MSAA_GL,
         ext_str, vec4_type_for_tgsi_ret(tgsi_ret),
         needs_manual_srgb_decode ? FS_FUNC_COL_SRGB_DECODE : "",
         needs_manual_srgb_encode ? FS_FUNC_COL_SRGB_ENCODE : "",
         needs_manual_srgb_decode ? "srgb_decode" : "",
         needs_manual_srgb_encode ? "srgb_encode" : "",
         vrend_shader_samplerreturnconv(tgsi_ret),
         vrend_shader_samplertypeconv(blit_ctx->use_gles, tgsi_tex_target),
         nr_samples, swizzle_and_type.type, swizzle_and_type.swizzle, dest_swizzle_snippet);
   else
      snprintf(shader_buf, 4096, blit_ctx->use_gles ?
                                 (tgsi_tex_target == TGSI_TEXTURE_1D ?
                                    FS_TEXFETCH_COL_GLES_1D : FS_TEXFETCH_COL_GLES)
                                 : FS_TEXFETCH_COL_GL,
         ext_str, vec4_type_for_tgsi_ret(tgsi_ret),
         needs_manual_srgb_decode ? FS_FUNC_COL_SRGB_DECODE : "",
         needs_manual_srgb_encode ? FS_FUNC_COL_SRGB_ENCODE : "",
         needs_manual_srgb_decode ? "srgb_decode" : "",
         needs_manual_srgb_encode ? "srgb_encode" : "",
         vrend_shader_samplerreturnconv(tgsi_ret),
         vrend_shader_samplertypeconv(blit_ctx->use_gles, tgsi_tex_target),
         swizzle_and_type.swizzle, dest_swizzle_snippet);

   VREND_DEBUG(dbg_blit, NULL, "-- Blit FS color shader MSAA: %d -----------------\n"
               "%s\n---------------------------------------\n", msaa, shader_buf);

   return blit_shader_build_and_check(GL_FRAGMENT_SHADER, shader_buf);
}

static GLuint blit_build_frag_depth(struct vrend_blitter_ctx *blit_ctx, enum tgsi_texture_type tgsi_tex_target, bool msaa)
{
   char shader_buf[4096];
   struct blit_swizzle_and_type swizzle_and_type;
   unsigned flags = BLIT_USE_DEPTH;

   if (msaa)
      flags |= BLIT_USE_MSAA;

   blit_get_swizzle(tgsi_tex_target, flags, &swizzle_and_type);

   if (msaa)
      snprintf(shader_buf, 4096, blit_ctx->use_gles ?
                                 (swizzle_and_type.is_array ?  FS_TEXFETCH_DS_MSAA_ARRAY_GLES :  FS_TEXFETCH_DS_MSAA_GLES)
                                 : FS_TEXFETCH_DS_MSAA_GL,
         vrend_shader_samplertypeconv(blit_ctx->use_gles, tgsi_tex_target), swizzle_and_type.type, swizzle_and_type.swizzle);
   else
      snprintf(shader_buf, 4096, blit_ctx->use_gles ? FS_TEXFETCH_DS_GLES : FS_TEXFETCH_DS_GL,
         vrend_shader_samplertypeconv(blit_ctx->use_gles, tgsi_tex_target), swizzle_and_type.swizzle);

   VREND_DEBUG(dbg_blit, NULL, "-- Blit FS depth shader MSAA: %d -----------------\n"
               "%s\n---------------------------------------\n", msaa, shader_buf);

   return blit_shader_build_and_check(GL_FRAGMENT_SHADER, shader_buf);
}

static GLuint blit_get_frag_tex_writedepth(struct vrend_blitter_ctx *blit_ctx, enum pipe_texture_target pipe_tex_target, unsigned nr_samples)
{
   struct blit_prog_key key = {
         .is_color = false,
         .is_msaa = nr_samples > 1,
         .num_samples = nr_samples,
         .pipe_tex_target = pipe_tex_target,
      };

      void *shader = _mesa_hash_table_u64_search(blit_ctx->blit_programs, prog_key_to_uint64(key));
      GLuint prog_id;
      if (shader) {
         prog_id = pointer_to_uintptr(shader);
      } else {
         prog_id = glCreateProgram();
         glAttachShader(prog_id, blit_ctx->vs);
         enum tgsi_texture_type tgsi_tex = util_pipe_tex_to_tgsi_tex(pipe_tex_target, key.num_samples);
         GLuint fs_id = blit_build_frag_depth(blit_ctx, tgsi_tex, key.is_msaa);
         glAttachShader(prog_id, fs_id);
         if(!blit_shader_link_and_check(prog_id))
            return 0;

         glDeleteShader(fs_id);
         _mesa_hash_table_u64_insert(blit_ctx->blit_programs, prog_key_to_uint64(key), uintptr_to_pointer(prog_id));
      }
      return prog_id;
}

static GLuint blit_get_frag_tex_col(struct vrend_blitter_ctx *blit_ctx,
                                       enum pipe_texture_target pipe_tex_target,
                                       unsigned nr_samples,
                                       const struct vrend_format_table *src_entry,
                                       const enum pipe_swizzle swizzle[static 4],
                                       uint32_t flags)
{
   bool needs_swizzle = false;
   for (unsigned i = 0; i < 4; ++i) {
      if (swizzle[i] != i) {
         needs_swizzle = true;
         break;
      }
   }

   struct blit_prog_key key = {
      .is_color = true,
      .is_msaa = nr_samples > 1,
      .manual_srgb_decode = has_bit(flags, BLIT_MANUAL_SRGB_DECODE),
      .manual_srgb_encode = has_bit(flags, BLIT_MANUAL_SRGB_ENCODE),
      .num_samples = nr_samples,
      .pipe_tex_target  = pipe_tex_target
   };

   key.texcol.src_format = src_entry->format;
   key.texcol.has_swizzle = needs_swizzle;
   if (key.texcol.has_swizzle) {
      key.texcol.swizzle1 = swizzle[0];
      key.texcol.swizzle2 = swizzle[1];
      key.texcol.swizzle3 = swizzle[2];
      key.texcol.swizzle4 = swizzle[3];
   }

   GLuint prog_id = 0;
   void *shader = _mesa_hash_table_u64_search(blit_ctx->blit_programs, prog_key_to_uint64(key));

   if (shader) {
      prog_id = pointer_to_uintptr(shader);
   } else {
      prog_id = glCreateProgram();
      glAttachShader(prog_id, blit_ctx->vs);
      enum tgsi_texture_type tgsi_tex = util_pipe_tex_to_tgsi_tex(pipe_tex_target, key.num_samples);
      enum tgsi_return_type tgsi_ret = tgsi_ret_for_format(src_entry->format);
      int msaa_samples = nr_samples > 1 ? (tgsi_ret == TGSI_RETURN_TYPE_UNORM ? nr_samples : 1) : 0;

      GLuint fs_id = blit_build_frag_tex_col(blit_ctx, tgsi_tex, tgsi_ret,
                                             swizzle, msaa_samples, flags);
      glAttachShader(prog_id, fs_id);
      if(!blit_shader_link_and_check(prog_id))
         return 0;

      glDeleteShader(fs_id);
      _mesa_hash_table_u64_insert(blit_ctx->blit_programs, prog_key_to_uint64(key), uintptr_to_pointer(prog_id));
   }

   return prog_id;
}

static void vrend_renderer_init_blit_ctx(struct vrend_blitter_ctx *blit_ctx)
{
   struct virgl_gl_ctx_param ctx_params;
   int i;
   if (blit_ctx->initialised) {
      vrend_sync_make_current(blit_ctx->gl_context);
      return;
   }

   vrend_blit_ctx.blit_programs = _mesa_hash_table_u64_create(NULL);

   blit_ctx->use_gles = epoxy_is_desktop_gl() == 0;
   ctx_params.shared = true;
   ctx_params.compat_ctx = false;
   for (uint32_t i = 0; i < ARRAY_SIZE(gl_versions); i++) {
      ctx_params.major_ver = gl_versions[i].major;
      ctx_params.minor_ver = gl_versions[i].minor;

      blit_ctx->gl_context = vrend_clicbs->create_gl_context(0, &ctx_params);
      if (blit_ctx->gl_context)
         break;
   }

   if (!blit_ctx->gl_context) {
      virgl_error("virglrenderer: Unable to create blit context");
      abort();
   }

   vrend_sync_make_current(blit_ctx->gl_context);
   glGenVertexArrays(1, &blit_ctx->vaoid);
   glGenFramebuffers(1, &blit_ctx->fb_id);

   glGenBuffers(1, &blit_ctx->vbo_id);
   blit_ctx->vs = blit_shader_build_and_check(GL_VERTEX_SHADER,
        blit_ctx->use_gles ? VS_PASSTHROUGH_GLES : VS_PASSTHROUGH_GL);

   for (i = 0; i < 4; i++) {
      blit_ctx->vertices[i].pos.z = 0;
      blit_ctx->vertices[i].pos.w = 1;
   }

   glBindVertexArray(blit_ctx->vaoid);
   glBindBuffer(GL_ARRAY_BUFFER, blit_ctx->vbo_id);

   if (!blit_ctx->use_gles)
      glEnable(GL_FRAMEBUFFER_SRGB);

   blit_ctx->initialised = true;
}

static void blitter_set_rectangle(struct vrend_blitter_ctx *blit_ctx,
                                  int x1, int y1, int x2, int y2)
{
   /* set vertex positions */
   blit_ctx->vertices[0].pos.x = (float)x1 / blit_ctx->dst_width * 2.0f - 1.0f; /*v0.x*/
   blit_ctx->vertices[0].pos.y = (float)y1 / blit_ctx->dst_height * 2.0f - 1.0f; /*v0.y*/

   blit_ctx->vertices[1].pos.x = (float)x2 / blit_ctx->dst_width * 2.0f - 1.0f; /*v1.x*/
   blit_ctx->vertices[1].pos.y = (float)y1 / blit_ctx->dst_height * 2.0f - 1.0f; /*v1.y*/

   blit_ctx->vertices[2].pos.x = (float)x2 / blit_ctx->dst_width * 2.0f - 1.0f; /*v2.x*/
   blit_ctx->vertices[2].pos.y = (float)y2 / blit_ctx->dst_height * 2.0f - 1.0f; /*v2.y*/

   blit_ctx->vertices[3].pos.x = (float)x1 / blit_ctx->dst_width * 2.0f - 1.0f; /*v3.x*/
   blit_ctx->vertices[3].pos.y = (float)y2 / blit_ctx->dst_height * 2.0f - 1.0f; /*v3.y*/

   glViewport(0, 0, blit_ctx->dst_width, blit_ctx->dst_height);
}

static void get_texcoords(struct vrend_blitter_ctx *blit_ctx,
                          struct vrend_resource *src_res,
                          int src_level,
                          int x1, int y1, int x2, int y2,
                          float out[4])
{
   bool normalized = (src_res->base.target != PIPE_TEXTURE_RECT || blit_ctx->use_gles) &&
                     src_res->base.nr_samples < 1;

   if (normalized) {
      out[0] = x1 / (float)u_minify(src_res->base.width0,  src_level);
      out[1] = y1 / (float)u_minify(src_res->base.height0, src_level);
      out[2] = x2 / (float)u_minify(src_res->base.width0,  src_level);
      out[3] = y2 / (float)u_minify(src_res->base.height0, src_level);
   } else {
      out[0] = (float) x1;
      out[1] = (float) y1;
      out[2] = (float) x2;
      out[3] = (float) y2;
   }
}

static void set_texcoords_in_vertices(const float coord[4],
                                      float *out, unsigned stride)
{
   out[0] = coord[0]; /*t0.s*/
   out[1] = coord[1]; /*t0.t*/
   out += stride;
   out[0] = coord[2]; /*t1.s*/
   out[1] = coord[1]; /*t1.t*/
   out += stride;
   out[0] = coord[2]; /*t2.s*/
   out[1] = coord[3]; /*t2.t*/
   out += stride;
   out[0] = coord[0]; /*t3.s*/
   out[1] = coord[3]; /*t3.t*/
}

static void blitter_set_texcoords(struct vrend_blitter_ctx *blit_ctx,
                                  struct vrend_resource *src_res,
                                  int level,
                                  float layer, unsigned sample,
                                  int x1, int y1, int x2, int y2)
{
   float coord[4];
   float face_coord[4][2];
   int i;
   get_texcoords(blit_ctx, src_res, level, x1, y1, x2, y2, coord);

   if (src_res->base.target == PIPE_TEXTURE_CUBE ||
       src_res->base.target == PIPE_TEXTURE_CUBE_ARRAY) {
      set_texcoords_in_vertices(coord, &face_coord[0][0], 2);
      util_map_texcoords2d_onto_cubemap((unsigned)layer % 6,
                                        /* pointer, stride in floats */
                                        &face_coord[0][0], 2,
                                        &blit_ctx->vertices[0].tex.x, 8,
                                        false);
   } else {
      set_texcoords_in_vertices(coord, &blit_ctx->vertices[0].tex.x, 8);
   }

   switch (src_res->base.target) {
   case PIPE_TEXTURE_3D:
   {
      float r = layer / (float)u_minify(src_res->base.depth0,
                                        level);
      for (i = 0; i < 4; i++)
         blit_ctx->vertices[i].tex.z = r; /*r*/
   }
   break;

   case PIPE_TEXTURE_1D_ARRAY:
      for (i = 0; i < 4; i++)
         blit_ctx->vertices[i].tex.y = (float) layer; /*t*/
      break;

   case PIPE_TEXTURE_2D_ARRAY:
      for (i = 0; i < 4; i++) {
         blit_ctx->vertices[i].tex.z = (float) layer;  /*r*/
         blit_ctx->vertices[i].tex.w = (float) sample; /*q*/
      }
      break;
   case PIPE_TEXTURE_CUBE_ARRAY:
      for (i = 0; i < 4; i++)
         blit_ctx->vertices[i].tex.w = (float) ((unsigned)layer / 6); /*w*/
      break;
   case PIPE_TEXTURE_2D:
      for (i = 0; i < 4; i++) {
         blit_ctx->vertices[i].tex.w = (float) sample; /*r*/
      }
      break;
   default:;
   }
}

static void set_dsa_write_depth_keep_stencil(void)
{
   glDisable(GL_STENCIL_TEST);
   glEnable(GL_DEPTH_TEST);
   glDepthFunc(GL_ALWAYS);
   glDepthMask(GL_TRUE);
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

/* Calculate the delta required to keep 'v' within [0, max] */
static int calc_delta_for_bound(int v, int max)
{
    int delta = 0;

    if (v < 0)
        delta = -v;
    else if (v > max)
        delta = - (v - max);

    return delta;
}

/* Calculate the deltas for the source blit region points in order to bound
 * them within the source resource extents */
static void calc_src_deltas_for_bounds(struct vrend_resource *src_res,
                                       const struct pipe_blit_info *info,
                                       struct blit_point *src0_delta,
                                       struct blit_point *src1_delta)
{
   int max_x = u_minify(src_res->base.width0, info->src.level) - 1;
   int max_y = u_minify(src_res->base.height0, info->src.level) - 1;

   /* Whether the bounds for the coordinates of a point are inclusive or
    * exclusive depends on the direction of the blit read. Adjust the max
    * bounds accordingly, with an adjustment of 0 for inclusive, and 1 for
    * exclusive. */
   int src0_x_excl = info->src.box.width < 0;
   int src0_y_excl = info->src.box.height < 0;

   src0_delta->x = calc_delta_for_bound(info->src.box.x, max_x + src0_x_excl);
   src0_delta->y = calc_delta_for_bound(info->src.box.y, max_y + src0_y_excl);

   src1_delta->x = calc_delta_for_bound(info->src.box.x + info->src.box.width,
                                         max_x + !src0_x_excl);
   src1_delta->y = calc_delta_for_bound(info->src.box.y + info->src.box.height,
                                         max_y + !src0_y_excl);
}

/* Calculate dst delta values to adjust the dst points for any changes in the
 * src points */
static void calc_dst_deltas_from_src(const struct pipe_blit_info *info,
                                     const struct blit_point *src0_delta,
                                     const struct blit_point *src1_delta,
                                     struct blit_point *dst0_delta,
                                     struct blit_point *dst1_delta)
{
   float scale_x = (float)info->dst.box.width / (float)info->src.box.width;
   float scale_y = (float)info->dst.box.height / (float)info->src.box.height;

   dst0_delta->x = src0_delta->x * scale_x;
   dst0_delta->y = src0_delta->y * scale_y;

   dst1_delta->x = src1_delta->x * scale_x;
   dst1_delta->y = src1_delta->y * scale_y;
}

static void blitter_set_points(struct vrend_blitter_ctx *blit_ctx,
                               const struct pipe_blit_info *info,
                               struct vrend_resource *src_res,
                               struct vrend_resource *dst_res,
                               struct blit_point *src0,
                               struct blit_point *src1)
{
   struct blit_point dst0, dst1;
   struct blit_point src0_delta, src1_delta, dst0_delta, dst1_delta;

   blit_ctx->dst_width = u_minify(dst_res->base.width0, info->dst.level);
   blit_ctx->dst_height = u_minify(dst_res->base.height0, info->dst.level);

   /* Calculate src and dst points taking deltas into account */
   calc_src_deltas_for_bounds(src_res, info, &src0_delta, &src1_delta);
   calc_dst_deltas_from_src(info, &src0_delta, &src1_delta, &dst0_delta, &dst1_delta);

   src0->x = info->src.box.x + src0_delta.x;
   src0->y = info->src.box.y + src0_delta.y;
   src1->x = info->src.box.x + info->src.box.width + src1_delta.x;
   src1->y = info->src.box.y + info->src.box.height + src1_delta.y;

   dst0.x = info->dst.box.x + dst0_delta.x;
   dst0.y = info->dst.box.y + dst0_delta.y;
   dst1.x = info->dst.box.x + info->dst.box.width + dst1_delta.x;
   dst1.y = info->dst.box.y + info->dst.box.height + dst1_delta.y;

   VREND_DEBUG(dbg_blit, NULL, "Blitter src:[%3d, %3d] - [%3d, %3d] to dst:[%3d, %3d] - [%3d, %3d]\n",
               src0->x, src0->y, src1->x, src1->y,
               dst0.x, dst0.y, dst1.x, dst1.y);

   blitter_set_rectangle(blit_ctx, dst0.x, dst0.y, dst1.x, dst1.y);
}

static void vrend_set_tex_param(struct vrend_resource *src_res,
                                const struct pipe_blit_info *info,
                                bool has_texture_srgb_decode)
{
   const struct vrend_format_table *src_entry =
      vrend_get_format_table_entry(info->src.format);

   if (src_entry->flags & VIRGL_TEXTURE_NEED_SWIZZLE) {
      glTexParameteri(src_res->target, GL_TEXTURE_SWIZZLE_R,
                      to_gl_swizzle(src_entry->swizzle[0]));
      glTexParameteri(src_res->target, GL_TEXTURE_SWIZZLE_G,
                      to_gl_swizzle(src_entry->swizzle[1]));
      glTexParameteri(src_res->target, GL_TEXTURE_SWIZZLE_B,
                      to_gl_swizzle(src_entry->swizzle[2]));
      glTexParameteri(src_res->target, GL_TEXTURE_SWIZZLE_A,
                      to_gl_swizzle(src_entry->swizzle[3]));
   }

   /* Just make sure that no stale state disabled decoding */
   if (has_texture_srgb_decode && util_format_is_srgb(info->src.format) &&
       src_res->base.nr_samples < 1)
      glTexParameteri(src_res->target, GL_TEXTURE_SRGB_DECODE_EXT, GL_DECODE_EXT);

   if (src_res->base.nr_samples < 1) {
      glTexParameteri(src_res->target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
      glTexParameteri(src_res->target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
      glTexParameteri(src_res->target, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
   }

   glTexParameteri(src_res->target, GL_TEXTURE_BASE_LEVEL, info->src.level);
   glTexParameteri(src_res->target, GL_TEXTURE_MAX_LEVEL, info->src.level);

   if (src_res->base.nr_samples < 1) {
      GLenum filter = info->filter == PIPE_TEX_FILTER_NEAREST ?
                                       GL_NEAREST : GL_LINEAR;
      glTexParameteri(src_res->target, GL_TEXTURE_MAG_FILTER, filter);
      glTexParameteri(src_res->target, GL_TEXTURE_MIN_FILTER, filter);
   }
}

static void vrend_set_vertex_param(GLuint prog_id)
{
   GLuint pos_loc, tc_loc;

   pos_loc = glGetAttribLocation(prog_id, "arg0");
   tc_loc = glGetAttribLocation(prog_id, "arg1");

   glVertexAttribPointer(pos_loc, 4, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void *)0);
   glVertexAttribPointer(tc_loc, 4, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void *)(4 * sizeof(float)));

   glEnableVertexAttribArray(pos_loc);
   glEnableVertexAttribArray(tc_loc);
}

/* implement blitting using OpenGL. */
void vrend_renderer_blit_gl(ASSERTED struct vrend_context *ctx,
                            struct vrend_resource *src_res,
                            struct vrend_resource *dst_res,
                            const struct vrend_blit_info *info)
{
   struct vrend_blitter_ctx *blit_ctx = &vrend_blit_ctx;

   int dst_z;
   struct blit_point src0, src1;
   const struct util_format_description *src_desc =
      util_format_description(src_res->base.format);
   const struct util_format_description *dst_desc =
      util_format_description(dst_res->base.format);
   const struct vrend_format_table *orig_src_entry = vrend_get_format_table_entry(info->b.src.format);

   bool blit_depth = util_format_has_depth(src_desc) &&
         util_format_has_depth(dst_desc) &&
         (info->b.mask & PIPE_MASK_Z);

   vrend_renderer_init_blit_ctx(blit_ctx);
   blitter_set_points(blit_ctx, &info->b, src_res, dst_res, &src0, &src1);

   GLuint prog_id;

   if (blit_depth) {
      prog_id = blit_get_frag_tex_writedepth(blit_ctx, src_res->base.target,
                                             src_res->base.nr_samples);
   } else {
      VREND_DEBUG(dbg_blit, ctx, "BLIT: applying swizzle during blit: (%d %d %d %d)\n",
                  info->swizzle[0], info->swizzle[1], info->swizzle[2], info->swizzle[3]);

      if (info->needs_manual_srgb_decode)
         VREND_DEBUG(dbg_blit, ctx,
                     "BLIT: applying manual srgb->linear conversion for src %s(%s)\n",
                     util_format_name(src_res->base.format),
                     util_format_name(info->b.src.format));

      if (info->needs_manual_srgb_encode)
         VREND_DEBUG(dbg_blit, ctx,
                     "BLIT: applying manual linear->srgb conversion for dst %s(%s)\n",
                     util_format_name(dst_res->base.format),
                     util_format_name(info->b.dst.format));

      uint32_t flags = 0;
      flags |= info->needs_manual_srgb_decode ? BLIT_MANUAL_SRGB_DECODE : 0;
      flags |= info->needs_manual_srgb_encode ? BLIT_MANUAL_SRGB_ENCODE : 0;
      prog_id = blit_get_frag_tex_col(blit_ctx, src_res->base.target,
                                      src_res->base.nr_samples,
                                      orig_src_entry,
                                      info->swizzle,
                                      flags);
   }
   if (!prog_id) {
      virgl_error("Blitter: unable to create or find shader program\n");
      return;
   }

   glUseProgram(prog_id);

   glBindFramebuffer(GL_FRAMEBUFFER, blit_ctx->fb_id);
   vrend_fb_bind_texture_id(dst_res, info->dst_view, 0, info->b.dst.level, info->b.dst.box.z, 0);

   GLuint buffers = GL_COLOR_ATTACHMENT0;
   glDrawBuffers(1, &buffers);

   glBindTexture(src_res->target, info->src_view);
   vrend_set_tex_param(src_res, &info->b,
                       info->has_texture_srgb_decode &&
                       !info->needs_manual_srgb_decode);
   vrend_set_vertex_param(prog_id);

   set_dsa_write_depth_keep_stencil();

   if (info->b.scissor_enable) {
      glScissor(info->b.scissor.minx, info->b.scissor.miny,
                info->b.scissor.maxx - info->b.scissor.minx,
                info->b.scissor.maxy - info->b.scissor.miny);
      glEnable(GL_SCISSOR_TEST);
   } else
      glDisable(GL_SCISSOR_TEST);

   if (info->has_srgb_write_control) {
      if (!info->needs_manual_srgb_encode &&
          (util_format_is_srgb(info->b.dst.format) || util_format_is_srgb(info->b.src.format))) {
         VREND_DEBUG(dbg_blit, ctx, "%s: Enable GL_FRAMEBUFFER_SRGB\n", __func__);
         glEnable(GL_FRAMEBUFFER_SRGB);
      } else {
         VREND_DEBUG(dbg_blit, ctx, "%s: Disable GL_FRAMEBUFFER_SRGB\n", __func__);
         glDisable(GL_FRAMEBUFFER_SRGB);
      }
   }

   for (dst_z = 0; dst_z < info->b.dst.box.depth; dst_z++) {
      float dst2src_scale = info->b.src.box.depth / (float)info->b.dst.box.depth;
      float dst_offset = ((info->b.src.box.depth - 1) -
                          (info->b.dst.box.depth - 1) * dst2src_scale) * 0.5;
      float src_z = (dst_z + dst_offset) * dst2src_scale;

      uint32_t layer = (dst_res->target == GL_TEXTURE_CUBE_MAP ||
                        dst_res->target == GL_TEXTURE_1D_ARRAY ||
                        dst_res->target == GL_TEXTURE_2D_ARRAY) ? info->b.dst.box.z : dst_z;

      vrend_fb_bind_texture_id(dst_res, info->dst_view, 0, info->b.dst.level, layer, 0);

      blitter_set_texcoords(blit_ctx, src_res, info->b.src.level,
                            info->b.src.box.z + src_z, 0,
                            src0.x, src0.y, src1.x, src1.y);

      glBufferData(GL_ARRAY_BUFFER, sizeof(blit_ctx->vertices), blit_ctx->vertices, GL_STATIC_DRAW);
      glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
   }

   glUseProgram(0);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                             GL_TEXTURE_2D, 0, 0);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                             GL_TEXTURE_2D, 0, 0);
   glBindTexture(src_res->target, 0);
}

static void delete_program_cb(struct hash_entry *entry)
{
   glDeleteProgram(pointer_to_uintptr(entry->data));
}

void vrend_blitter_fini(void)
{
   vrend_blit_ctx.initialised = false;
   if (vrend_blit_ctx.blit_programs)
      _mesa_hash_table_u64_destroy(vrend_blit_ctx.blit_programs, delete_program_cb);
   vrend_clicbs->destroy_gl_context(vrend_blit_ctx.gl_context);
   memset(&vrend_blit_ctx, 0, sizeof(vrend_blit_ctx));
}

