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
#include <epoxy/gl.h>

#include "vrend_renderer.h"
#include "util/u_memory.h"
#include "util/u_format.h"

#define SWIZZLE_INVALID 0xff
#define NO_SWIZZLE { SWIZZLE_INVALID, SWIZZLE_INVALID, SWIZZLE_INVALID, SWIZZLE_INVALID }
#define RRR1_SWIZZLE { PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_1 }
#define RRRG_SWIZZLE { PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_X, PIPE_SWIZZLE_Y }
#define RGB1_SWIZZLE { PIPE_SWIZZLE_X, PIPE_SWIZZLE_Y, PIPE_SWIZZLE_Z, PIPE_SWIZZLE_1 }
#define OOOR_SWIZZLE { PIPE_SWIZZLE_0, PIPE_SWIZZLE_0, PIPE_SWIZZLE_0, PIPE_SWIZZLE_X  }

#define BGR1_SWIZZLE { PIPE_SWIZZLE_Z, PIPE_SWIZZLE_Y, PIPE_SWIZZLE_X, PIPE_SWIZZLE_1 }
#define BGRA_SWIZZLE { PIPE_SWIZZLE_Z, PIPE_SWIZZLE_Y, PIPE_SWIZZLE_X, PIPE_SWIZZLE_W }

#ifdef __GNUC__
/* The warning missing-field-initializers is misleading: If at least one field
 * is initialized, then the un-initialized fields will be filled with zero.
 * Silencing the warning by manually adding the zeros that the compiler will add
 * anyway doesn't improve the code, and initializing the files by using a named
 * notation will make it worse, because then he remaining fields truely be
 * un-initialized.
 */
#ifdef __clang__
#pragma clang diagnostic ignored "-Wmissing-field-initializers"
#else
#pragma GCC diagnostic ignored "-Wmissing-field-initializers"
#endif
#endif

/* fill the format table */
static struct vrend_format_table base_rgba_formats[] =
  {
    { VIRGL_FORMAT_R8G8_R8B8_UNORM, GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },
    { VIRGL_FORMAT_R8G8B8X8_UNORM, GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, RGB1_SWIZZLE, view_class_32 },
    { VIRGL_FORMAT_R8G8B8A8_UNORM, GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },

    { VIRGL_FORMAT_A8R8G8B8_UNORM, GL_RGBA8, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8, NO_SWIZZLE, view_class_32 },
    { VIRGL_FORMAT_X8R8G8B8_UNORM, GL_RGBA8, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8, NO_SWIZZLE, view_class_32 },

    { VIRGL_FORMAT_A8B8G8R8_UNORM, GL_RGBA8, GL_ABGR_EXT, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },

    { VIRGL_FORMAT_B4G4R4X4_UNORM, GL_RGBA4, GL_BGRA, GL_UNSIGNED_SHORT_4_4_4_4_REV, RGB1_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_A4B4G4R4_UNORM, GL_RGBA4, GL_RGBA, GL_UNSIGNED_SHORT_4_4_4_4, NO_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_B5G5R5X1_UNORM, GL_RGB5_A1, GL_BGRA, GL_UNSIGNED_SHORT_1_5_5_5_REV, RGB1_SWIZZLE, view_class_unsupported },

    { VIRGL_FORMAT_B5G6R5_UNORM, GL_RGB565, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, NO_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_B2G3R3_UNORM, GL_R3_G3_B2, GL_RGB, GL_UNSIGNED_BYTE_3_3_2, NO_SWIZZLE, view_class_unsupported },

    { VIRGL_FORMAT_R16G16B16X16_UNORM, GL_RGBA16, GL_RGBA, GL_UNSIGNED_SHORT, RGB1_SWIZZLE, view_class_64 },

    { VIRGL_FORMAT_R16G16B16A16_UNORM, GL_RGBA16, GL_RGBA, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_64 },
  };

static struct vrend_format_table gl_base_rgba_formats[] =
  {
    { VIRGL_FORMAT_B4G4R4A4_UNORM, GL_RGBA4, GL_BGRA, GL_UNSIGNED_SHORT_4_4_4_4_REV, NO_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_B5G5R5A1_UNORM, GL_RGB5_A1, GL_BGRA, GL_UNSIGNED_SHORT_1_5_5_5_REV, NO_SWIZZLE, view_class_unsupported },
  };

static struct vrend_format_table base_depth_formats[] =
  {
    { VIRGL_FORMAT_Z16_UNORM, GL_DEPTH_COMPONENT16, GL_DEPTH_COMPONENT, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_S8_UINT_Z24_UNORM, GL_DEPTH24_STENCIL8_EXT, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, NO_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_Z24X8_UNORM, GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_Z32_FLOAT, GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, NO_SWIZZLE, view_class_unsupported },
    /* this is probably a separate format */
    { VIRGL_FORMAT_Z32_FLOAT_S8X24_UINT, GL_DEPTH32F_STENCIL8, GL_DEPTH_STENCIL, GL_FLOAT_32_UNSIGNED_INT_24_8_REV, NO_SWIZZLE, view_class_unsupported },
    { VIRGL_FORMAT_X24S8_UINT, GL_STENCIL_INDEX8, GL_STENCIL_INDEX, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported },
  };

static struct vrend_format_table gl_z32_format[] = {
   { VIRGL_FORMAT_Z32_UNORM, GL_DEPTH_COMPONENT32, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_unsupported },
};

static struct vrend_format_table gles_z32_format[] = {
   { VIRGL_FORMAT_Z32_UNORM, GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_unsupported },
};

static struct vrend_format_table rg_base_formats[] = {
  { VIRGL_FORMAT_R8_UNORM, GL_R8, GL_RED, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_8 },
  { VIRGL_FORMAT_R8G8_UNORM, GL_RG8, GL_RG, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_16 },
  { VIRGL_FORMAT_R16_UNORM, GL_R16, GL_RED, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_16 },
  { VIRGL_FORMAT_R16G16_UNORM, GL_RG16, GL_RG, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table integer_base_formats[] = {
  { VIRGL_FORMAT_R8G8B8A8_UINT, GL_RGBA8UI, GL_RGBA_INTEGER, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R8G8B8A8_SINT, GL_RGBA8I, GL_RGBA_INTEGER, GL_BYTE, NO_SWIZZLE, view_class_32 },

  { VIRGL_FORMAT_R16G16B16A16_UINT, GL_RGBA16UI, GL_RGBA_INTEGER, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R16G16B16A16_SINT, GL_RGBA16I, GL_RGBA_INTEGER, GL_SHORT, NO_SWIZZLE, view_class_32 },

  { VIRGL_FORMAT_R32G32B32A32_UINT, GL_RGBA32UI, GL_RGBA_INTEGER, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R32G32B32A32_SINT, GL_RGBA32I, GL_RGBA_INTEGER, GL_INT, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table integer_3comp_formats[] = {
  { VIRGL_FORMAT_R8G8B8X8_UINT, GL_RGBA8UI, GL_RGBA_INTEGER, GL_UNSIGNED_BYTE, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R8G8B8X8_SINT, GL_RGBA8I, GL_RGBA_INTEGER, GL_BYTE, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R16G16B16X16_UINT, GL_RGBA16UI, GL_RGBA_INTEGER, GL_UNSIGNED_SHORT, RGB1_SWIZZLE, view_class_64 },
  { VIRGL_FORMAT_R16G16B16X16_SINT, GL_RGBA16I, GL_RGBA_INTEGER, GL_SHORT, RGB1_SWIZZLE, view_class_64 },
  { VIRGL_FORMAT_R32G32B32_UINT, GL_RGB32UI, GL_RGB_INTEGER, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_96 },
  { VIRGL_FORMAT_R32G32B32_SINT, GL_RGB32I, GL_RGB_INTEGER, GL_INT, NO_SWIZZLE, view_class_96 },
};

static struct vrend_format_table float_base_formats[] = {
  { VIRGL_FORMAT_R16G16B16A16_FLOAT, GL_RGBA16F, GL_RGBA, GL_HALF_FLOAT, NO_SWIZZLE, view_class_64 },
  { VIRGL_FORMAT_R32G32B32A32_FLOAT, GL_RGBA32F, GL_RGBA, GL_FLOAT, NO_SWIZZLE, view_class_128 },
};

static struct vrend_format_table integer_rg_formats[] = {
  { VIRGL_FORMAT_R8_UINT, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_8 },
  { VIRGL_FORMAT_R8G8_UINT, GL_RG8UI, GL_RG_INTEGER, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_16 },
  { VIRGL_FORMAT_R8_SINT, GL_R8I, GL_RED_INTEGER, GL_BYTE, NO_SWIZZLE, view_class_8  },
  { VIRGL_FORMAT_R8G8_SINT, GL_RG8I, GL_RG_INTEGER, GL_BYTE, NO_SWIZZLE, view_class_16  },

  { VIRGL_FORMAT_R16_UINT, GL_R16UI, GL_RED_INTEGER, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_16  },
  { VIRGL_FORMAT_R16G16_UINT, GL_RG16UI, GL_RG_INTEGER, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_32  },
  { VIRGL_FORMAT_R16_SINT, GL_R16I, GL_RED_INTEGER, GL_SHORT, NO_SWIZZLE, view_class_16  },
  { VIRGL_FORMAT_R16G16_SINT, GL_RG16I, GL_RG_INTEGER, GL_SHORT, NO_SWIZZLE, view_class_32  },

  { VIRGL_FORMAT_R32_UINT, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_32  },
  { VIRGL_FORMAT_R32G32_UINT, GL_RG32UI, GL_RG_INTEGER, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_64  },
  { VIRGL_FORMAT_R32_SINT, GL_R32I, GL_RED_INTEGER, GL_INT, NO_SWIZZLE, view_class_32  },
  { VIRGL_FORMAT_R32G32_SINT, GL_RG32I, GL_RG_INTEGER, GL_INT, NO_SWIZZLE, view_class_64  },
};

static struct vrend_format_table float_rg_formats[] = {
  { VIRGL_FORMAT_R16_FLOAT, GL_R16F, GL_RED, GL_HALF_FLOAT, NO_SWIZZLE, view_class_16 },
  { VIRGL_FORMAT_R16G16_FLOAT, GL_RG16F, GL_RG, GL_HALF_FLOAT, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R32_FLOAT, GL_R32F, GL_RED, GL_FLOAT, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R32G32_FLOAT, GL_RG32F, GL_RG, GL_FLOAT, NO_SWIZZLE, view_class_64 },
};

static struct vrend_format_table float_3comp_formats[] = {
  { VIRGL_FORMAT_R16G16B16X16_FLOAT, GL_RGBA16F, GL_RGBA, GL_HALF_FLOAT, RGB1_SWIZZLE, view_class_64 },
  { VIRGL_FORMAT_R32G32B32_FLOAT, GL_RGB32F, GL_RGB, GL_FLOAT, NO_SWIZZLE, view_class_96 },
};


static struct vrend_format_table la_formats_fallback[] = {
  { VIRGL_FORMAT_A8_UNORM, GL_R8, GL_RED, GL_UNSIGNED_BYTE, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8_UNORM, GL_R8, GL_RED, GL_UNSIGNED_BYTE, RRR1_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_A16_UNORM, GL_R16, GL_RED, GL_UNSIGNED_SHORT, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16_UNORM, GL_R16, GL_RED, GL_UNSIGNED_SHORT, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A8_UINT, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8_UINT, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A8_SINT, GL_R8I, GL_RED_INTEGER, GL_BYTE, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8_SINT, GL_R8I, GL_RED_INTEGER, GL_BYTE, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A16_UINT, GL_R16UI, GL_RED_INTEGER, GL_UNSIGNED_SHORT, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16_UINT, GL_R16UI, GL_RED_INTEGER, GL_UNSIGNED_SHORT, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A16_SINT, GL_R16I, GL_RED_INTEGER, GL_SHORT, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16_SINT, GL_R16I, GL_RED_INTEGER, GL_SHORT, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A32_UINT, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32_UINT, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A32_SINT, GL_R32I, GL_RED_INTEGER, GL_INT, OOOR_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32_SINT, GL_R32I, GL_RED_INTEGER, GL_INT, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_L16_FLOAT, GL_R16F, GL_RED, GL_HALF_FLOAT, RRR1_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_L32_FLOAT, GL_R32F, GL_RED, GL_FLOAT, RRR1_SWIZZLE, view_class_unsupported },
};


static struct vrend_format_table la_formats_compat[] = {
  { VIRGL_FORMAT_A8_UNORM, GL_ALPHA8, GL_ALPHA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_A16_UNORM, GL_ALPHA16, GL_ALPHA, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A8_UINT, GL_ALPHA8UI_EXT, GL_ALPHA_INTEGER, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8_UINT, GL_LUMINANCE8UI_EXT, GL_LUMINANCE_INTEGER_EXT, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8A8_UINT, GL_LUMINANCE_ALPHA8UI_EXT, GL_LUMINANCE_ALPHA_INTEGER_EXT, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_A8_SINT, GL_ALPHA8I_EXT, GL_ALPHA_INTEGER, GL_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8_SINT, GL_LUMINANCE8I_EXT, GL_LUMINANCE_INTEGER_EXT, GL_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8A8_SINT, GL_LUMINANCE_ALPHA8I_EXT, GL_LUMINANCE_ALPHA_INTEGER_EXT, GL_BYTE, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A16_UINT, GL_ALPHA16UI_EXT, GL_ALPHA_INTEGER,  GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16_UINT, GL_LUMINANCE16UI_EXT, GL_LUMINANCE_INTEGER_EXT, GL_UNSIGNED_SHORT, RRR1_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16A16_UINT, GL_LUMINANCE_ALPHA16UI_EXT, GL_LUMINANCE_ALPHA_INTEGER_EXT, GL_UNSIGNED_SHORT, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A16_SINT, GL_ALPHA16I_EXT, GL_ALPHA_INTEGER, GL_SHORT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16_SINT, GL_LUMINANCE16I_EXT, GL_LUMINANCE_INTEGER_EXT, GL_SHORT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16A16_SINT, GL_LUMINANCE_ALPHA16I_EXT, GL_LUMINANCE_ALPHA_INTEGER_EXT, GL_SHORT, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A32_UINT, GL_ALPHA32UI_EXT, GL_ALPHA_INTEGER, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32_UINT, GL_R32UI, GL_LUMINANCE_INTEGER_EXT, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32A32_UINT, GL_LUMINANCE_ALPHA32UI_EXT, GL_LUMINANCE_ALPHA_INTEGER_EXT, GL_UNSIGNED_INT, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A32_SINT, GL_ALPHA32I_EXT, GL_ALPHA_INTEGER, GL_INT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32_SINT, GL_LUMINANCE32I_EXT, GL_LUMINANCE_INTEGER_EXT, GL_INT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32A32_SINT, GL_LUMINANCE_ALPHA32I_EXT, GL_LUMINANCE_ALPHA_INTEGER_EXT, GL_INT, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A16_FLOAT, GL_ALPHA16F_ARB, GL_ALPHA, GL_HALF_FLOAT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16_FLOAT, GL_LUMINANCE16F_ARB, GL_LUMINANCE, GL_HALF_FLOAT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16A16_FLOAT, GL_LUMINANCE_ALPHA16F_ARB, GL_LUMINANCE_ALPHA, GL_HALF_FLOAT_ARB, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_A32_FLOAT, GL_ALPHA32F_ARB, GL_ALPHA, GL_FLOAT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32_FLOAT, GL_LUMINANCE32F_ARB, GL_LUMINANCE, GL_FLOAT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L32A32_FLOAT, GL_LUMINANCE_ALPHA32F_ARB, GL_LUMINANCE_ALPHA, GL_FLOAT, NO_SWIZZLE, view_class_unsupported },
};

static struct vrend_format_table snorm_formats[] = {
  { VIRGL_FORMAT_R8_SNORM, GL_R8_SNORM, GL_RED, GL_BYTE, NO_SWIZZLE, view_class_8 },
  { VIRGL_FORMAT_R8G8_SNORM, GL_RG8_SNORM, GL_RG, GL_BYTE, NO_SWIZZLE, view_class_16 },

  { VIRGL_FORMAT_R8G8B8A8_SNORM, GL_RGBA8_SNORM, GL_RGBA, GL_BYTE, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R8G8B8X8_SNORM, GL_RGBA8_SNORM, GL_RGBA, GL_BYTE, RGB1_SWIZZLE, view_class_32 },

  { VIRGL_FORMAT_R16_SNORM, GL_R16_SNORM, GL_RED, GL_SHORT, NO_SWIZZLE, view_class_16 },
  { VIRGL_FORMAT_R16G16_SNORM, GL_RG16_SNORM, GL_RG, GL_SHORT, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R16G16B16A16_SNORM, GL_RGBA16_SNORM, GL_RGBA, GL_SHORT, NO_SWIZZLE, view_class_64 },

  { VIRGL_FORMAT_R16G16B16X16_SNORM, GL_RGBA16_SNORM, GL_RGBA, GL_SHORT, RGB1_SWIZZLE, view_class_64 },
};

static struct vrend_format_table snorm_la_formats[] = {
  { VIRGL_FORMAT_A8_SNORM, GL_ALPHA8_SNORM, GL_ALPHA, GL_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8_SNORM, GL_R8_SNORM, GL_RED, GL_BYTE, RRR1_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L8A8_SNORM, GL_LUMINANCE8_ALPHA8_SNORM, GL_LUMINANCE_ALPHA, GL_BYTE, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_A16_SNORM, GL_ALPHA16_SNORM, GL_ALPHA, GL_SHORT, NO_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16_SNORM, GL_R16_SNORM, GL_RED, GL_SHORT, RRR1_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_L16A16_SNORM, GL_LUMINANCE16_ALPHA16_SNORM, GL_LUMINANCE_ALPHA, GL_SHORT, NO_SWIZZLE, view_class_unsupported },
};

static struct vrend_format_table dxtn_formats[] = {
  { VIRGL_FORMAT_DXT1_RGB, GL_COMPRESSED_RGB_S3TC_DXT1_EXT, GL_RGB, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt1_rgb },
  { VIRGL_FORMAT_DXT1_RGBA, GL_COMPRESSED_RGBA_S3TC_DXT1_EXT, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt1_rgba },
  { VIRGL_FORMAT_DXT3_RGBA, GL_COMPRESSED_RGBA_S3TC_DXT3_EXT, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt3_rgba },
  { VIRGL_FORMAT_DXT5_RGBA, GL_COMPRESSED_RGBA_S3TC_DXT5_EXT, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt5_rgba },
};

static struct vrend_format_table dxtn_srgb_formats[] = {
  { VIRGL_FORMAT_DXT1_SRGB, GL_COMPRESSED_SRGB_S3TC_DXT1_EXT, GL_RGB, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt1_rgb },
  { VIRGL_FORMAT_DXT1_SRGBA, GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT1_EXT, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt1_rgba },
  { VIRGL_FORMAT_DXT3_SRGBA, GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt3_rgba },
  { VIRGL_FORMAT_DXT5_SRGBA, GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_dxt5_rgba },
};

static struct vrend_format_table etc2_formats[] = {
  {VIRGL_FORMAT_ETC2_RGB8, GL_COMPRESSED_RGB8_ETC2, GL_RGB, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_etc2_rgb },
  {VIRGL_FORMAT_ETC2_SRGB8, GL_COMPRESSED_SRGB8_ETC2, GL_RGB, GL_BYTE, NO_SWIZZLE, view_class_etc2_rgb },
  {VIRGL_FORMAT_ETC2_RGB8A1, GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_etc2_rgba },
  {VIRGL_FORMAT_ETC2_SRGB8A1, GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2, GL_RGBA, GL_BYTE, NO_SWIZZLE, view_class_etc2_rgba },
  {VIRGL_FORMAT_ETC2_RGBA8, GL_COMPRESSED_RGBA8_ETC2_EAC, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_etc2_eac_rgba },
  {VIRGL_FORMAT_ETC2_SRGBA8, GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC, GL_RGBA, GL_BYTE, NO_SWIZZLE, view_class_etc2_eac_rgba },
  {VIRGL_FORMAT_ETC2_R11_UNORM, GL_COMPRESSED_R11_EAC, GL_RED, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported},
  {VIRGL_FORMAT_ETC2_R11_SNORM, GL_COMPRESSED_SIGNED_R11_EAC, GL_RED, GL_BYTE, NO_SWIZZLE, view_class_unsupported},
  {VIRGL_FORMAT_ETC2_RG11_UNORM, GL_COMPRESSED_RG11_EAC, GL_RG, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported},
  {VIRGL_FORMAT_ETC2_RG11_SNORM, GL_COMPRESSED_SIGNED_RG11_EAC, GL_RG, GL_BYTE, NO_SWIZZLE, view_class_unsupported},
};

#define ASTC_FORMAT(size) \
  {VIRGL_FORMAT_ASTC_ ## size, GL_COMPRESSED_RGBA_ASTC_ ## size, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_astc_## size ##_rgba}, \
  {VIRGL_FORMAT_ASTC_ ## size, GL_COMPRESSED_SRGB8_ALPHA8_ASTC_ ## size, GL_RGBA, GL_BYTE, NO_SWIZZLE, view_class_astc_## size ##_rgba}

static struct vrend_format_table astc_formats[] = {
   ASTC_FORMAT(4x4),
   ASTC_FORMAT(5x4),
   ASTC_FORMAT(5x5),
   ASTC_FORMAT(6x5),
   ASTC_FORMAT(6x6),
   ASTC_FORMAT(8x5),
   ASTC_FORMAT(8x6),
   ASTC_FORMAT(8x8),
   ASTC_FORMAT(10x5),
   ASTC_FORMAT(10x6),
   ASTC_FORMAT(10x8),
   ASTC_FORMAT(10x10),
   ASTC_FORMAT(12x10),
   ASTC_FORMAT(12x12),
};

static struct vrend_format_table rgtc_formats[] = {
  { VIRGL_FORMAT_RGTC1_UNORM, GL_COMPRESSED_RED_RGTC1, GL_RED, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_rgtc1_red },
  { VIRGL_FORMAT_RGTC1_SNORM, GL_COMPRESSED_SIGNED_RED_RGTC1, GL_RED, GL_BYTE, NO_SWIZZLE, view_class_rgtc1_red },

  { VIRGL_FORMAT_RGTC2_UNORM, GL_COMPRESSED_RG_RGTC2, GL_RG, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_rgtc2_rg },
  { VIRGL_FORMAT_RGTC2_SNORM, GL_COMPRESSED_SIGNED_RG_RGTC2, GL_RG, GL_BYTE, NO_SWIZZLE, view_class_rgtc2_rg },
};

static struct vrend_format_table srgb_formats[] = {
  { VIRGL_FORMAT_R8G8B8X8_SRGB, GL_SRGB8_ALPHA8, GL_RGBA, GL_UNSIGNED_BYTE, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R8G8B8A8_SRGB, GL_SRGB8_ALPHA8, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },

  { VIRGL_FORMAT_L8_SRGB, GL_SR8_EXT, GL_RED, GL_UNSIGNED_BYTE, RRR1_SWIZZLE, view_class_unsupported },
  { VIRGL_FORMAT_R8_SRGB, GL_SR8_EXT, GL_RED, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_unsupported },

  { VIRGL_FORMAT_R8G8_SRGB, GL_SRG8_EXT, GL_RG, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_16 },
};

static struct vrend_format_table bit10_formats[] = {
  { VIRGL_FORMAT_B10G10R10A2_UINT, GL_RGB10_A2UI, GL_BGRA_INTEGER, GL_UNSIGNED_INT_2_10_10_10_REV, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R10G10B10X2_UNORM, GL_RGB10_A2, GL_RGBA, GL_UNSIGNED_INT_2_10_10_10_REV, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R10G10B10A2_UNORM, GL_RGB10_A2, GL_RGBA, GL_UNSIGNED_INT_2_10_10_10_REV, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_R10G10B10A2_UINT, GL_RGB10_A2UI, GL_RGBA_INTEGER, GL_UNSIGNED_INT_2_10_10_10_REV, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table gl_bit10_formats[] = {
   { VIRGL_FORMAT_B10G10R10X2_UNORM, GL_RGB10_A2, GL_BGRA, GL_UNSIGNED_INT_2_10_10_10_REV, RGB1_SWIZZLE, view_class_32 },
   { VIRGL_FORMAT_B10G10R10A2_UNORM, GL_RGB10_A2, GL_BGRA, GL_UNSIGNED_INT_2_10_10_10_REV, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table gles_bit10_formats[] = {
   { VIRGL_FORMAT_B10G10R10X2_UNORM, GL_RGB10_A2, GL_RGBA, GL_UNSIGNED_INT_2_10_10_10_REV, RGB1_SWIZZLE, view_class_32 },
   { VIRGL_FORMAT_B10G10R10A2_UNORM, GL_RGB10_A2, GL_RGBA, GL_UNSIGNED_INT_2_10_10_10_REV, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table packed_float_formats[] = {
  { VIRGL_FORMAT_R11G11B10_FLOAT, GL_R11F_G11F_B10F, GL_RGB, GL_UNSIGNED_INT_10F_11F_11F_REV, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table exponent_float_formats[] = {
  { VIRGL_FORMAT_R9G9B9E5_FLOAT, GL_RGB9_E5, GL_RGB, GL_UNSIGNED_INT_5_9_9_9_REV, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table bptc_formats[] = {
   { VIRGL_FORMAT_BPTC_RGBA_UNORM, GL_COMPRESSED_RGBA_BPTC_UNORM, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_bptc_unorm },
   { VIRGL_FORMAT_BPTC_SRGBA, GL_COMPRESSED_SRGB_ALPHA_BPTC_UNORM, GL_RGBA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_bptc_unorm },
   { VIRGL_FORMAT_BPTC_RGB_FLOAT, GL_COMPRESSED_RGB_BPTC_SIGNED_FLOAT, GL_RGB, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_bptc_float },
   { VIRGL_FORMAT_BPTC_RGB_UFLOAT, GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT, GL_RGB, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_bptc_float },
};

static struct vrend_format_table gl_bgra_formats[] = {
  { VIRGL_FORMAT_B8G8R8X8_UNORM, GL_RGBA8, GL_BGRA, GL_UNSIGNED_BYTE, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_B8G8R8A8_UNORM, GL_RGBA8, GL_BGRA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_B8G8R8X8_SRGB, GL_SRGB8_ALPHA8, GL_BGRA, GL_UNSIGNED_BYTE, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_B8G8R8A8_SRGB, GL_SRGB8_ALPHA8, GL_BGRA, GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },
};

static struct vrend_format_table gles_bgra_formats[] = {
  { VIRGL_FORMAT_B8G8R8X8_UNORM, GL_RGBA8,        GL_RGBA,     GL_UNSIGNED_BYTE, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_B8G8R8A8_UNORM, GL_RGBA8,        GL_RGBA,     GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_B8G8R8X8_SRGB,  GL_SRGB8_ALPHA8, GL_RGBA,     GL_UNSIGNED_BYTE, RGB1_SWIZZLE, view_class_32 },
  { VIRGL_FORMAT_B8G8R8A8_SRGB,  GL_SRGB8_ALPHA8, GL_RGBA,     GL_UNSIGNED_BYTE, NO_SWIZZLE, view_class_32 },
};

static bool color_format_can_readback(struct vrend_format_table *virgl_format, int gles_ver)
{
   GLint imp = 0;

   if (virgl_format->format == VIRGL_FORMAT_R8G8B8A8_UNORM)
      return true;

   if (gles_ver >= 30 &&
        (virgl_format->format == VIRGL_FORMAT_R32G32B32A32_SINT ||
         virgl_format->format == VIRGL_FORMAT_R32G32B32A32_UINT))
       return true;

   if ((virgl_format->format == VIRGL_FORMAT_R32G32B32A32_FLOAT) &&
       (gles_ver >= 32 || epoxy_has_gl_extension("GL_EXT_color_buffer_float")))
      return true;

   /* Hotfix for the CI, on GLES these formats are defined like
    * VIRGL_FORMAT_R10G10B10.2_UNORM, and seems to be incorrect for direct
    * readback but the blit workaround seems to work, so disable the
    * direct readback for these two formats. */
   if (virgl_format->format == VIRGL_FORMAT_B10G10R10A2_UNORM ||
       virgl_format->format == VIRGL_FORMAT_B10G10R10X2_UNORM)
      return false;


   /* Check implementation specific readback formats */
   glGetIntegerv(GL_IMPLEMENTATION_COLOR_READ_TYPE, &imp);
   if (imp == (GLint)virgl_format->gltype) {
      glGetIntegerv(GL_IMPLEMENTATION_COLOR_READ_FORMAT, &imp);
      if (imp == (GLint)virgl_format->glformat)
         return true;
   }
   return false;
}

static bool depth_stencil_formats_can_readback(enum virgl_formats format)
{
   switch (format) {
   case VIRGL_FORMAT_Z16_UNORM:
   case VIRGL_FORMAT_Z32_UNORM:
   case VIRGL_FORMAT_Z32_FLOAT:
   case VIRGL_FORMAT_Z24X8_UNORM:
      return epoxy_has_gl_extension("GL_NV_read_depth");

   case VIRGL_FORMAT_Z24_UNORM_S8_UINT:
   case VIRGL_FORMAT_S8_UINT_Z24_UNORM:
   case VIRGL_FORMAT_Z32_FLOAT_S8X24_UINT:
      return epoxy_has_gl_extension("GL_NV_read_depth_stencil");

   case VIRGL_FORMAT_X24S8_UINT:
   case VIRGL_FORMAT_S8X24_UINT:
   case VIRGL_FORMAT_S8_UINT:
      return epoxy_has_gl_extension("GL_NV_read_stencil");

   default:
      return false;
   }
}

static void vrend_add_formats(struct vrend_format_table *table, int num_entries)
{
  int i;

  const bool is_desktop_gl = epoxy_is_desktop_gl();
  const int gles_ver = is_desktop_gl ? 0 : epoxy_gl_version();

  for (i = 0; i < num_entries; i++) {
    GLenum status;
    bool is_depth = false;
    uint32_t flags = 0;
    uint32_t binding = 0;
    GLuint buffers;
    GLuint tex_id, fb_id;

    if (vrend_get_format_table_entry(table[i].format)->format)
       continue;

    /**/
    glGenTextures(1, &tex_id);
    glGenFramebuffers(1, &fb_id);

    glBindTexture(GL_TEXTURE_2D, tex_id);
    glBindFramebuffer(GL_FRAMEBUFFER, fb_id);

    /* The error state should be clear here */
    status = glGetError();
    assert(status == GL_NO_ERROR);

    glTexImage2D(GL_TEXTURE_2D, 0, table[i].internalformat, 32, 32, 0, table[i].glformat, table[i].gltype, NULL);
    status = glGetError();
    /* Currently possible errors are:
     *  * GL_INVALID_VALUE
     *  * GL_INVALID_ENUM
     *  * GL_INVALID_OPERATION
     *  * GL_OUT_OF_MEMORY
     */
    if (status != GL_NO_ERROR) {
      glDeleteTextures(1, &tex_id);
      glDeleteFramebuffers(1, &fb_id);
      continue;
    }

    if (is_desktop_gl) {
      glTexImage2D(GL_TEXTURE_RECTANGLE_NV, 0, table[i].internalformat, 32, 32, 0, table[i].glformat, table[i].gltype, NULL);
      status = glGetError();
      if (status == GL_NO_ERROR) {
        flags |= VIRGL_TEXTURE_CAN_TARGET_RECTANGLE;
      }
    }

    if (table[i].format < VIRGL_FORMAT_MAX  && util_format_is_depth_or_stencil(table[i].format)) {
      const struct util_format_description *desc = util_format_description(table[i].format);
      GLenum attachment;

      if (util_format_has_stencil(desc))
        attachment = GL_DEPTH_STENCIL_ATTACHMENT;
      else
        attachment = GL_DEPTH_ATTACHMENT;
      glFramebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, tex_id, 0);

      is_depth = true;

      buffers = GL_NONE;
      glDrawBuffers(1, &buffers);
    } else {
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex_id, 0);

      buffers = GL_COLOR_ATTACHMENT0;
      glDrawBuffers(1, &buffers);
    }

    status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    binding = VIRGL_BIND_SAMPLER_VIEW;
    if (status == GL_FRAMEBUFFER_COMPLETE)
       binding |= is_depth ? VIRGL_BIND_DEPTH_STENCIL : VIRGL_BIND_RENDER_TARGET;

    /* On OpenGL all textures can be read back using glGetTexImage, but on GLES
       we have to be able to bind textures to framebuffers, and use glReadPixels
       to get the data. And apart from a few formats where support is required
       (by the GLES version), we have to query the driver to identify additional
       formats that are supported as destination formats by glReadPixels. */
    if (is_desktop_gl ||
        (status == GL_FRAMEBUFFER_COMPLETE &&
         (is_depth ? depth_stencil_formats_can_readback(table[i].format) :
                     color_format_can_readback(&table[i], gles_ver))))
       flags |= VIRGL_TEXTURE_CAN_READBACK;

    glDeleteTextures(1, &tex_id);
    glDeleteFramebuffers(1, &fb_id);

    if (table[i].swizzle[0] != SWIZZLE_INVALID)
       vrend_insert_format_swizzle(table[i].format, &table[i], binding, table[i].swizzle, flags);
    else
       vrend_insert_format(&table[i], binding, flags);
  }
}

static void vrend_add_compressed_formats(struct vrend_format_table *table, int num_entries)
{
   int flags = epoxy_is_desktop_gl() ? VIRGL_TEXTURE_CAN_READBACK : 0;
   for (int i = 0; i < num_entries; i++) {
      vrend_insert_format(&table[i], VIRGL_BIND_SAMPLER_VIEW, flags);
   }
}


#define add_formats(x) vrend_add_formats((x), ARRAY_SIZE((x)))
#define add_compressed_formats(x) vrend_add_compressed_formats((x), ARRAY_SIZE((x)))

void vrend_build_format_list_common(void)
{
  add_formats(base_rgba_formats);
  add_formats(base_depth_formats);

  /* float support */
  add_formats(float_base_formats);
  add_formats(la_formats_compat);
  add_formats(la_formats_fallback);

  add_formats(float_3comp_formats);

  /* texture integer support ? */
  add_formats(integer_base_formats);

  add_formats(integer_3comp_formats);

  /* RG support? */
  add_formats(rg_base_formats);
  /* integer + rg */
  add_formats(integer_rg_formats);
  /* float + rg */
  add_formats(float_rg_formats);

  /* snorm */
  add_formats(snorm_formats);
  add_formats(snorm_la_formats);

  /* compressed */
  if (epoxy_has_gl_extension("GL_S3_s3tc") ||
      epoxy_has_gl_extension("GL_EXT_texture_compression_s3tc") ||
      epoxy_has_gl_extension("GL_ANGLE_texture_compression_dxt")) {
     add_compressed_formats(dxtn_formats);
     add_compressed_formats(dxtn_srgb_formats);
  }

  if (epoxy_has_gl_extension("GL_ARB_texture_compression_rgtc") ||
      epoxy_has_gl_extension("GL_EXT_texture_compression_rgtc") )
     add_compressed_formats(rgtc_formats);

  if (epoxy_has_gl_extension("GL_ARB_texture_compression_bptc") ||
      epoxy_has_gl_extension("GL_EXT_texture_compression_bptc"))
     add_compressed_formats(bptc_formats);

  add_formats(srgb_formats);

  add_formats(bit10_formats);

  add_formats(packed_float_formats);
  add_formats(exponent_float_formats);
}


void vrend_build_format_list_gl(void)
{
  add_formats(gl_z32_format);
  /* GL_BGRA formats aren't as well supported in GLES as in GL, specially in
   * transfer operations. So we only register support for it in GL.
   */
  add_formats(gl_base_rgba_formats);
  add_formats(gl_bgra_formats);
  add_formats(gl_bit10_formats);
}

void vrend_build_format_list_gles(void)
{
  /* The BGR[A|X] formats is required but OpenGL ES does not
   * support it as nicely as OpenGL. We could try to use BGRA_EXT from
   * EXT_texture_format_BGRA8888, but it becomes error prone when mixed
   * with BGR*_SRGB formats and framebuffer multisampling. Instead, on
   * GLES hosts, we always emulate BGR* as GL_RGB* with a swizzle on
   * transfers to/from the host.
   */
  add_formats(gles_bgra_formats);

  /* The Z32 format is required, but OpenGL ES does not support
   * using it as a depth buffer. We just fake support with Z24
   * and hope nobody notices.
   */
  add_formats(gles_z32_format);
  add_formats(gles_bit10_formats);

  if (epoxy_has_gl_extension("GL_KHR_texture_compression_astc_ldr"))
     add_compressed_formats(astc_formats);

  if (epoxy_gl_version() >= 30) {
     add_compressed_formats(etc2_formats);
  }

}

/* glTexStorage may not support all that is supported by glTexImage,
 * so add a flag to indicate when it can be used.
 */
void vrend_check_texture_storage(struct vrend_format_table *table)
{
   int i;
   GLuint tex_id;
   for (i = 0; i < VIRGL_FORMAT_MAX_EXTENDED; i++) {

      if (table[i].internalformat != 0 &&
          !(table[i].flags & VIRGL_TEXTURE_CAN_TEXTURE_STORAGE)) {
         glGenTextures(1, &tex_id);
         glBindTexture(GL_TEXTURE_2D, tex_id);
         glTexStorage2D(GL_TEXTURE_2D, 1, table[i].internalformat, 32, 32);
         if (glGetError() == GL_NO_ERROR)
            table[i].flags |= VIRGL_TEXTURE_CAN_TEXTURE_STORAGE;
         glDeleteTextures(1, &tex_id);
      }
   }
}

void vrend_check_texture_multisample(struct vrend_format_table *table,
                                     bool enable_storage)
{
   bool is_desktop_gl = epoxy_is_desktop_gl();
   for (int i = 0; i < VIRGL_FORMAT_MAX_EXTENDED; i++) {
      bool function_available =
         (table[i].flags & VIRGL_TEXTURE_CAN_TEXTURE_STORAGE) ? enable_storage : is_desktop_gl;

      if (table[i].internalformat != 0 &&
          !(table[i].flags & VIRGL_TEXTURE_CAN_MULTISAMPLE) &&
          function_available) {
         GLuint tex_id;
         glGenTextures(1, &tex_id);
         glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, tex_id);
         if (table[i].flags & VIRGL_TEXTURE_CAN_TEXTURE_STORAGE) {
            glTexStorage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, 2,
                                      table[i].internalformat, 32, 32, GL_TRUE);
         } else {
            glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, 2,
                                    table[i].internalformat, 32, 32, GL_TRUE);
         }
         if (glGetError() == GL_NO_ERROR)
            table[i].flags |= VIRGL_TEXTURE_CAN_MULTISAMPLE;
         glDeleteTextures(1, &tex_id);
      }
   }
}

bool vrend_check_framebuffer_mixed_color_attachements(void)
{
   GLuint tex_id[2];
   GLuint fb_id;
   bool retval = false;

   glGenTextures(2, tex_id);
   glGenFramebuffers(1, &fb_id);

   glBindTexture(GL_TEXTURE_2D, tex_id[0]);
   glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 32, 32, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);

   glBindFramebuffer(GL_FRAMEBUFFER, fb_id);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex_id[0], 0);

   glBindTexture(GL_TEXTURE_2D, tex_id[1]);
   glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, 32, 32, 0, GL_RED, GL_UNSIGNED_BYTE, NULL);
   glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, tex_id[1], 0);


   retval = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;

   glDeleteFramebuffers(1, &fb_id);
   glDeleteTextures(2, tex_id);

   return retval;
}


unsigned vrend_renderer_query_multisample_caps(unsigned max_samples, struct virgl_caps_v2 *caps)
{
   GLuint tex;
   GLuint fbo;
   GLenum status;

   unsigned max_samples_confirmed = 1;
   unsigned test_num_samples[4] = {2,4,8,16};
   int out_buf_offsets[4] = {0,1,2,4};
   int lowest_working_ms_count_idx = -1;

   assert(glGetError() == GL_NO_ERROR &&
          "Stale error state detected, please check for failures in initialization");

   glGenFramebuffers( 1, &fbo );
   memset(caps->sample_locations, 0, 8 * sizeof(uint32_t));

   for (int i = 3; i >= 0; i--) {
      if (test_num_samples[i] > max_samples)
         continue;
      glGenTextures(1, &tex);
      glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, tex);
      glTexStorage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, test_num_samples[i], GL_RGBA32F, 64, 64, GL_TRUE);
      status = glGetError();
      if (status == GL_NO_ERROR) {
         glBindFramebuffer(GL_FRAMEBUFFER, fbo);
         glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D_MULTISAMPLE, tex, 0);
         status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
         if (status == GL_FRAMEBUFFER_COMPLETE) {
            if (max_samples_confirmed < test_num_samples[i])
               max_samples_confirmed = test_num_samples[i];

            for (unsigned k = 0; k < test_num_samples[i]; ++k) {
               float msp[2];
               uint32_t compressed;
               glGetMultisamplefv(GL_SAMPLE_POSITION, k, msp);
               compressed = ((unsigned)(floor(msp[0] * 16.0f)) & 0xf) << 4;
               compressed |= ((unsigned)(floor(msp[1] * 16.0f)) & 0xf);
               caps->sample_locations[out_buf_offsets[i] + (k >> 2)] |= compressed  << (8 * (k & 3));
            }
            lowest_working_ms_count_idx = i;
         } else {
            /* If a framebuffer doesn't support low sample counts,
             * use the sample position from the last working larger count. */
            if (lowest_working_ms_count_idx > 0) {
               for (unsigned k = 0; k < test_num_samples[i]; ++k) {
                  caps->sample_locations[out_buf_offsets[i] + (k >> 2)] =
                        caps->sample_locations[out_buf_offsets[lowest_working_ms_count_idx]  + (k >> 2)];
               }
            }
         }
         glBindFramebuffer(GL_FRAMEBUFFER, 0);
      }
      glDeleteTextures(1, &tex);
   }
   glDeleteFramebuffers(1, &fbo);
   return max_samples_confirmed;
}

/* returns: 1 = compatible, -1 = not compatible, 0 = undecided */
static int format_uncompressed_compressed_copy_compatible(enum virgl_formats src,
                                                          enum virgl_formats dst)
{

   switch (src) {
   case VIRGL_FORMAT_R32G32B32A32_UINT:
   case VIRGL_FORMAT_R32G32B32A32_SINT:
   case VIRGL_FORMAT_R32G32B32A32_FLOAT:
   case VIRGL_FORMAT_R32G32B32A32_SNORM:
   case VIRGL_FORMAT_R32G32B32A32_UNORM:
      switch (dst) {
      case VIRGL_FORMAT_DXT3_RGBA:
      case VIRGL_FORMAT_DXT3_SRGBA:
      case VIRGL_FORMAT_DXT5_RGBA:
      case VIRGL_FORMAT_DXT5_SRGBA:
      case VIRGL_FORMAT_RGTC2_UNORM:
      case VIRGL_FORMAT_RGTC2_SNORM:
      case VIRGL_FORMAT_BPTC_RGBA_UNORM:
      case VIRGL_FORMAT_BPTC_SRGBA:
      case VIRGL_FORMAT_BPTC_RGB_FLOAT:
      case VIRGL_FORMAT_BPTC_RGB_UFLOAT:
      case VIRGL_FORMAT_ETC2_RGBA8:
      case VIRGL_FORMAT_ETC2_SRGBA8:
      case VIRGL_FORMAT_ETC2_RG11_UNORM:
      case VIRGL_FORMAT_ETC2_RG11_SNORM:
         return 1;
      case VIRGL_FORMAT_ASTC_4x4:
      case VIRGL_FORMAT_ASTC_5x4:
      case VIRGL_FORMAT_ASTC_5x5:
      case VIRGL_FORMAT_ASTC_6x5:
      case VIRGL_FORMAT_ASTC_6x6:
      case VIRGL_FORMAT_ASTC_8x5:
      case VIRGL_FORMAT_ASTC_8x6:
      case VIRGL_FORMAT_ASTC_8x8:
      case VIRGL_FORMAT_ASTC_10x5:
      case VIRGL_FORMAT_ASTC_10x6:
      case VIRGL_FORMAT_ASTC_10x8:
      case VIRGL_FORMAT_ASTC_10x10:
      case VIRGL_FORMAT_ASTC_12x10:
      case VIRGL_FORMAT_ASTC_12x12:
      case VIRGL_FORMAT_ASTC_4x4_SRGB:
      case VIRGL_FORMAT_ASTC_5x4_SRGB:
      case VIRGL_FORMAT_ASTC_5x5_SRGB:
      case VIRGL_FORMAT_ASTC_6x5_SRGB:
      case VIRGL_FORMAT_ASTC_6x6_SRGB:
      case VIRGL_FORMAT_ASTC_8x5_SRGB:
      case VIRGL_FORMAT_ASTC_8x6_SRGB:
      case VIRGL_FORMAT_ASTC_8x8_SRGB:
      case VIRGL_FORMAT_ASTC_10x5_SRGB:
      case VIRGL_FORMAT_ASTC_10x6_SRGB:
      case VIRGL_FORMAT_ASTC_10x8_SRGB:
      case VIRGL_FORMAT_ASTC_10x10_SRGB:
      case VIRGL_FORMAT_ASTC_12x10_SRGB:
      case VIRGL_FORMAT_ASTC_12x12_SRGB:
         return epoxy_is_desktop_gl() ? -1 : 1;
      default:
         return -1;
      }
   case VIRGL_FORMAT_R16G16B16A16_UINT:
   case VIRGL_FORMAT_R16G16B16A16_SINT:
   case VIRGL_FORMAT_R16G16B16A16_FLOAT:
   case VIRGL_FORMAT_R16G16B16A16_SNORM:
   case VIRGL_FORMAT_R16G16B16A16_UNORM:
   case VIRGL_FORMAT_R32G32_UINT:
   case VIRGL_FORMAT_R32G32_SINT:
   case VIRGL_FORMAT_R32G32_FLOAT:
   case VIRGL_FORMAT_R32G32_UNORM:
   case VIRGL_FORMAT_R32G32_SNORM:
      switch (dst) {
      case VIRGL_FORMAT_DXT1_RGBA:
      case VIRGL_FORMAT_DXT1_SRGBA:
      case VIRGL_FORMAT_DXT1_RGB:
      case VIRGL_FORMAT_DXT1_SRGB:
      case VIRGL_FORMAT_RGTC1_UNORM:
      case VIRGL_FORMAT_RGTC1_SNORM:
      case VIRGL_FORMAT_ETC2_RGB8:
      case VIRGL_FORMAT_ETC2_SRGB8:
      case VIRGL_FORMAT_ETC2_RGB8A1:
      case VIRGL_FORMAT_ETC2_SRGB8A1:
      case VIRGL_FORMAT_ETC2_R11_UNORM:
      case VIRGL_FORMAT_ETC2_R11_SNORM:
         return 1;
      default:
         return -1;
      }
   default:
      return 0;
   }
}

static bool format_compressed_compressed_copy_compatible(enum virgl_formats src, enum virgl_formats dst)
{
   const bool is_desktop_gl = epoxy_is_desktop_gl();

   if(!is_desktop_gl) {
      if((src == VIRGL_FORMAT_ASTC_4x4 && dst == VIRGL_FORMAT_ASTC_4x4_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_5x4 && dst == VIRGL_FORMAT_ASTC_5x4_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_5x5 && dst == VIRGL_FORMAT_ASTC_5x5_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_6x5 && dst == VIRGL_FORMAT_ASTC_6x5_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_6x6 && dst == VIRGL_FORMAT_ASTC_6x6_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_8x5 && dst == VIRGL_FORMAT_ASTC_8x5_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_8x6 && dst == VIRGL_FORMAT_ASTC_8x6_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_8x8 && dst == VIRGL_FORMAT_ASTC_8x8_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_10x5 && dst == VIRGL_FORMAT_ASTC_10x5_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_10x6 && dst == VIRGL_FORMAT_ASTC_10x6_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_10x8 && dst == VIRGL_FORMAT_ASTC_10x8_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_10x10 && dst == VIRGL_FORMAT_ASTC_10x10_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_12x10 && dst == VIRGL_FORMAT_ASTC_12x10_SRGB) ||
        (src == VIRGL_FORMAT_ASTC_12x12 && dst == VIRGL_FORMAT_ASTC_12x12_SRGB) ||
        (src == VIRGL_FORMAT_ETC2_R11_UNORM && dst == VIRGL_FORMAT_ETC2_R11_SNORM) ||
        (src == VIRGL_FORMAT_ETC2_RG11_UNORM && dst == VIRGL_FORMAT_ETC2_RG11_SNORM) ||
        (src == VIRGL_FORMAT_ETC2_RGBA8 && dst == VIRGL_FORMAT_ETC2_SRGBA8) ||
        (src == VIRGL_FORMAT_ETC2_RGB8A1 && dst == VIRGL_FORMAT_ETC2_SRGB8A1) ||
        (src == VIRGL_FORMAT_ETC2_RGB8 && dst == VIRGL_FORMAT_ETC2_SRGB8))
         return true;
   }

   if ((src == VIRGL_FORMAT_RGTC1_UNORM && dst == VIRGL_FORMAT_RGTC1_SNORM) ||
       (src == VIRGL_FORMAT_RGTC2_UNORM && dst == VIRGL_FORMAT_RGTC2_SNORM) ||
       (src == VIRGL_FORMAT_BPTC_RGBA_UNORM && dst == VIRGL_FORMAT_BPTC_SRGBA) ||
       (src == VIRGL_FORMAT_BPTC_RGB_FLOAT && dst == VIRGL_FORMAT_BPTC_RGB_UFLOAT))
       return true;

   return false;
}

bool format_is_copy_compatible(enum virgl_formats src, enum virgl_formats dst,
                               unsigned int flags)
{
   int r;

   if (src == dst) {
      /* When Mesa imports dma_buf VIRGL_FORMAT_B8G8R8X8_UNORM/DRM|GBM_FORMAT_XRGB8888
       * it uses internal format GL_RGB8.
       * But when virglrenderer creates VIRGL_FORMAT_B8G8R8X8_UNORM texture, it
       * uses internal format GL_RGBA8.
       * So the formats do not match when Mesa checks them internally.
       */
      if (flags & VREND_COPY_COMPAT_FLAG_ONE_IS_EGL_IMAGE &&
          src == VIRGL_FORMAT_B8G8R8X8_UNORM)
         return false;
      return true;
   }

   if (util_format_is_plain(src) && util_format_is_plain(dst))  {
      const struct util_format_description *src_desc = util_format_description(src);
      const struct util_format_description *dst_desc = util_format_description(dst);
      return util_is_format_compatible(src_desc, dst_desc);
   }

   if (!(flags & VREND_COPY_COMPAT_FLAG_ALLOW_COMPRESSED))
      return false;

   /* compressed-uncompressed */
   r = format_uncompressed_compressed_copy_compatible(src, dst);
   if (r)
      return r > 0;

   r = format_uncompressed_compressed_copy_compatible(dst, src);
   if (r)
      return r > 0;

   return format_compressed_compressed_copy_compatible(dst, src) ||
          format_compressed_compressed_copy_compatible(src, dst);
}
