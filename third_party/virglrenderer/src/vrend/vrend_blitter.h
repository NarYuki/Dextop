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
#ifndef VREND_BLITTER_H
#define VREND_BLITTER_H

#include "util/os_misc.h"
#include "util/macros.h"

/* shaders for blitting */

#define FS_HEADER_GL                               \
   "#version 130\n"                             \
   "// Blitter\n"                               \
   "%s"                                         \

#define FS_HEADER_GLES                             \
   "#version 310 es\n"                          \
   "// Blitter\n"                               \
   "%s"                                         \
   "precision mediump float;\n"                 \

#define FS_HEADER_GLES_MS_ARRAY                             \
   "#version 310 es\n"                          \
   "// Blitter\n"                               \
   "#extension GL_OES_texture_storage_multisample_2d_array: require\n" \
   "%s"                                         \
   "precision mediump float;\n"                 \

#define HEADER_GL                               \
   "#version 130\n"                             \
   "// Blitter\n"                               \

#define HEADER_GLES                             \
   "#version 310 es\n"                          \
   "// Blitter\n"                               \
   "precision mediump float;\n"                 \

#define HEADER_GLES_MS_ARRAY                             \
   "#version 310 es\n"                          \
   "// Blitter\n"                               \
   "#extension GL_OES_texture_storage_multisample_2d_array: require\n" \
   "precision mediump float;\n"                 \


#define FS_FUNC_COL_SRGB_DECODE                                         \
   "cvec4 srgb_decode(cvec4 col) {\n"                                   \
   "   vec3 temp = vec3(col.rgb);\n"                                    \
   "   bvec3 thresh = lessThanEqual(temp, vec3(0.04045));\n"            \
   "   vec3 a = temp / vec3(12.92);\n"                                  \
   "   vec3 b = pow((temp + vec3(0.055)) / vec3(1.055), vec3(2.4));\n"  \
   "   return cvec4(clamp(mix(b, a, thresh), 0.0, 1.0), col.a);\n"      \
   "}\n"

#define FS_FUNC_COL_SRGB_ENCODE                                               \
   "cvec4 srgb_encode(cvec4 col) {\n"                                         \
   "   vec3 temp = vec3(col.rgb);\n"                                          \
   "   bvec3 thresh = lessThanEqual(temp, vec3(0.0031308));\n"                \
   "   vec3 a = temp * vec3(12.92);\n"                                        \
   "   vec3 b = (vec3(1.055) * pow(temp, vec3(1.0 / 2.4))) - vec3(0.055);\n"  \
   "   return cvec4(mix(b, a, thresh), col.a);\n"                             \
   "}\n"


#define VS_PASSTHROUGH_BODY                     \
   "in vec4 arg0;\n"                            \
   "in vec4 arg1;\n"                            \
   "out vec4 tc;\n"                             \
   "void main() {\n"                            \
   "   gl_Position = arg0;\n"                   \
   "   tc = arg1;\n"                            \
   "}\n"

#define VS_PASSTHROUGH_GL HEADER_GL VS_PASSTHROUGH_BODY
#define VS_PASSTHROUGH_GLES HEADER_GLES VS_PASSTHROUGH_BODY

#define FS_TEXFETCH_COL_BODY                                 \
   "#define cvec4 %s\n"                                      \
   "%s\n" /* conditional decode() */                         \
   "%s\n" /* conditional encode() */                         \
   "#define decode %s\n"                                     \
   "#define encode %s\n"                                     \
   "uniform mediump %csampler%s samp;\n"                     \
   "in vec4 tc;\n"                                           \
   "out cvec4 FragColor;\n"                                  \
   "void main() {\n"                                         \
   "   cvec4 texel = decode(cvec4(texture(samp, tc%s)));\n"  \
   "   FragColor = encode(cvec4(%s));\n"                     \
   "}\n"

#define FS_TEXFETCH_COL_GLES_1D_BODY                              \
   "#define cvec4 %s\n"                                           \
   "%s\n" /* conditional decode() */                              \
   "%s\n" /* conditional encode() */                              \
   "#define decode %s\n"                                          \
   "#define encode %s\n"                                          \
   "uniform mediump %csampler%s samp;\n"                          \
   "in vec4 tc;\n"                                                \
   "out cvec4 FragColor;\n"                                       \
   "void main() {\n"                                              \
   "   cvec4 texel = decode(texture(samp, vec2(tc%s, 0.5)));\n"   \
   "   FragColor = encode(cvec4(%s));\n"                          \
   "}\n"

#define FS_TEXFETCH_COL_GL FS_HEADER_GL FS_TEXFETCH_COL_BODY
#define FS_TEXFETCH_COL_GLES FS_HEADER_GLES FS_TEXFETCH_COL_BODY
#define FS_TEXFETCH_COL_GLES_1D FS_HEADER_GLES FS_TEXFETCH_COL_GLES_1D_BODY

#define FS_TEXFETCH_COL_MSAA_BODY                             \
   "#define cvec4 %s\n"                                       \
   "%s\n" /* conditional decode() */                          \
   "%s\n" /* conditional encode() */                          \
   "#define decode %s\n"                                      \
   "#define encode %s\n"                                      \
   "uniform mediump %csampler%s samp;\n"                      \
   "in vec4 tc;\n"                                            \
   "out cvec4 FragColor;\n"                                   \
   "void main() {\n"                                          \
   "   const int num_samples = %d;\n"                         \
   "   cvec4 texel = cvec4(0);\n"                             \
   "   for (int i = 0; i < num_samples; ++i) \n"              \
   "      texel += decode(texelFetch(samp, %s(tc%s), i));\n"  \
   "   texel = texel / cvec4(num_samples);\n"                 \
   "   FragColor = encode(cvec4(%s));\n"                      \
   "}\n"

#define FS_TEXFETCH_COL_MSAA_GL FS_HEADER_GL FS_TEXFETCH_COL_MSAA_BODY
#define FS_TEXFETCH_COL_MSAA_GLES FS_HEADER_GLES FS_TEXFETCH_COL_MSAA_BODY
#define FS_TEXFETCH_COL_MSAA_ARRAY_GLES FS_HEADER_GLES_MS_ARRAY FS_TEXFETCH_COL_MSAA_BODY

#define FS_TEXFETCH_DS_BODY                             \
   "uniform mediump sampler%s samp;\n"                          \
   "in vec4 tc;\n"                                      \
   "void main() {\n"                                    \
   "   gl_FragDepth = float(texture(samp, tc%s).x);\n"  \
   "}\n"

#define FS_TEXFETCH_DS_GL HEADER_GL FS_TEXFETCH_DS_BODY
#define FS_TEXFETCH_DS_GLES HEADER_GLES FS_TEXFETCH_DS_BODY

#define FS_TEXFETCH_DS_MSAA_BODY                                         \
   "#extension GL_ARB_texture_multisample : enable\n"                    \
   "uniform sampler%s samp;\n"                                           \
   "in vec4 tc;\n"                                                       \
   "void main() {\n"                                                     \
   "   gl_FragDepth = float(texelFetch(samp, %s(tc%s), 0).x);\n" \
   "}\n"

#define FS_TEXFETCH_DS_MSAA_BODY_GLES                                     \
   "uniform mediump sampler%s samp;\n"                                           \
   "in vec4 tc;\n"                                                       \
   "void main() {\n"                                                     \
   "   gl_FragDepth = float(texelFetch(samp, %s(tc%s), 0).x);\n" \
   "}\n"


struct vrend_context;
struct vrend_resource;
struct vrend_blit_info;
#define FS_TEXFETCH_DS_MSAA_GL HEADER_GL FS_TEXFETCH_DS_MSAA_BODY
#define FS_TEXFETCH_DS_MSAA_GLES HEADER_GLES FS_TEXFETCH_DS_MSAA_BODY_GLES
#define FS_TEXFETCH_DS_MSAA_ARRAY_GLES HEADER_GLES_MS_ARRAY FS_TEXFETCH_DS_MSAA_BODY_GLES

/* implement blitting using OpenGL. */
void vrend_renderer_blit_gl(ASSERTED struct vrend_context *ctx,
                            struct vrend_resource *src_res,
                            struct vrend_resource *dst_res,
                            const struct vrend_blit_info *info);
void vrend_blitter_fini(void);

#endif
