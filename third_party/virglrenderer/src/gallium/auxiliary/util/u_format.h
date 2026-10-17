/**************************************************************************
 *
 * Copyright 2009-2010 Vmware, Inc.
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


#ifndef U_FORMAT_H
#define U_FORMAT_H


#include "util/u_formats.h"
#include "pipe/p_defines.h"
#include "util/u_debug.h"

union pipe_color_union;


#ifdef __cplusplus
extern "C" {
#endif


/**
 * Describe how to pack/unpack pixels into/from the prescribed format.
 *
 * XXX: This could be renamed to something like util_format_pack, or broke down
 * in flags inside util_format_block that said exactly what we want.
 */
enum util_format_layout {
   /**
    * Formats with util_format_block::width == util_format_block::height == 1
    * that can be described as an ordinary data structure.
    */
   UTIL_FORMAT_LAYOUT_PLAIN = 0,

   /**
    * Formats with sub-sampled channels.
    *
    * This is for formats like YVYU where there is less than one sample per
    * pixel.
    */
   UTIL_FORMAT_LAYOUT_SUBSAMPLED = 3,

   /**
    * S3 Texture Compression formats.
    */
   UTIL_FORMAT_LAYOUT_S3TC = 4,

   /**
    * Red-Green Texture Compression formats.
    */
   UTIL_FORMAT_LAYOUT_RGTC = 5,

   /**
    * Ericsson Texture Compression
    */
   UTIL_FORMAT_LAYOUT_ETC = 6,

   /**
    * BC6/7 Texture Compression
    */
   UTIL_FORMAT_LAYOUT_BPTC = 7,

   UTIL_FORMAT_LAYOUT_ASTC = 8,

   UTIL_FORMAT_LAYOUT_ATC = 9,

   /** Formats with 2 or more planes. */
   UTIL_FORMAT_LAYOUT_PLANAR2 = 10,
   UTIL_FORMAT_LAYOUT_PLANAR3 = 11,

   UTIL_FORMAT_LAYOUT_FXT1 = 12,
   /**
    * Everything else that doesn't fit in any of the above layouts.
    */
   UTIL_FORMAT_LAYOUT_OTHER = 13,
};


struct util_format_block
{
   /** Block width in pixels */
   unsigned width;
   
   /** Block height in pixels */
   unsigned height;

   /** Block depth in pixels */
   unsigned depth;

   /** Block size in bits */
   unsigned bits;
};


enum util_format_type {
   UTIL_FORMAT_TYPE_VOID = 0,
   UTIL_FORMAT_TYPE_UNSIGNED = 1,
   UTIL_FORMAT_TYPE_SIGNED = 2,
   UTIL_FORMAT_TYPE_FIXED = 3,
   UTIL_FORMAT_TYPE_FLOAT = 4
};


enum util_format_colorspace {
   UTIL_FORMAT_COLORSPACE_RGB = 0,
   UTIL_FORMAT_COLORSPACE_SRGB = 1,
   UTIL_FORMAT_COLORSPACE_YUV = 2,
   UTIL_FORMAT_COLORSPACE_ZS = 3
};


struct util_format_channel_description
{
   unsigned type:5;        /**< UTIL_FORMAT_TYPE_x */
   unsigned normalized:1;
   unsigned pure_integer:1;
   unsigned size:9;        /**< bits per channel */
   unsigned shift:16;      /** number of bits from lsb */
};


struct util_format_description
{
   enum pipe_format format;

   const char *name;

   /**
    * Short name, striped of the prefix, lower case.
    */
   const char *short_name;

   /**
    * Pixel block dimensions.
    */
   struct util_format_block block;

   enum util_format_layout layout;

   /**
    * The number of channels.
    */
   unsigned nr_channels:3;

   /**
    * Whether all channels have the same number of (whole) bytes and type.
    */
   unsigned is_array:1;

   /**
    * Whether the pixel format can be described as a bitfield structure.
    *
    * In particular:
    * - pixel depth must be 8, 16, or 32 bits;
    * - all channels must be unsigned, signed, or void
    */
   unsigned is_bitmask:1;

   /**
    * Whether channels have mixed types (ignoring UTIL_FORMAT_TYPE_VOID).
    */
   unsigned is_mixed:1;

   /**
    * Whether the format contains UNORM channels
    */
   unsigned is_unorm:1;

   /**
    * Whether the format contains SNORM channels
    */
   unsigned is_snorm:1;

   /**
    * Input channel description, in the order XYZW.
    *
    * Only valid for UTIL_FORMAT_LAYOUT_PLAIN formats.
    *
    * If each channel is accessed as an individual N-byte value, X is always
    * at the lowest address in memory, Y is always next, and so on.  For all
    * currently-defined formats, the N-byte value has native endianness.
    *
    * If instead a group of channels is accessed as a single N-byte value,
    * the order of the channels within that value depends on endianness.
    * For big-endian targets, X is the most significant subvalue,
    * otherwise it is the least significant one.
    *
    * For example, if X is 8 bits and Y is 24 bits, the memory order is:
    *
    *                 0  1  2  3
    *  little-endian: X  Yl Ym Yu    (l = lower, m = middle, u = upper)
    *  big-endian:    X  Yu Ym Yl
    *
    * If X is 5 bits, Y is 5 bits, Z is 5 bits and W is 1 bit, the layout is:
    *
    *                        0        1
    *                 msb  lsb msb  lsb
    *  little-endian: YYYXXXXX WZZZZZYY
    *  big-endian:    XXXXXYYY YYZZZZZW
    */
   struct util_format_channel_description channel[4];

   /**
    * Output channel swizzle.
    *
    * The order is either:
    * - RGBA
    * - YUV(A)
    * - ZS
    * depending on the colorspace.
    */
   unsigned char swizzle[4];

   /**
    * Colorspace transformation.
    */
   enum util_format_colorspace colorspace;

   /**
    * For sRGB formats, equivalent linear format; for linear formats,
    * equivalent sRGB format
    */
   union {
      enum pipe_format srgb_equivalent;
      enum pipe_format linear_equivalent;
   };

};


extern const struct util_format_description 
util_format_description_table[];


ATTRIBUTE_RETURNS_NONNULL const struct util_format_description *
util_format_description(enum pipe_format format);


/*
 * Format query functions.
 */

ATTRIBUTE_RETURNS_NONNULL static inline const char *
util_format_name(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT || !desc->name)) {
      return "PIPE_FORMAT_???";
   }

   return desc->name;
}

ATTRIBUTE_RETURNS_NONNULL static inline const char *
util_format_short_name(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT || !desc->short_name)) {
      return "???";
   }

   return desc->short_name;
}

/**
 * Whether this format is plain, see UTIL_FORMAT_LAYOUT_PLAIN for more info.
 */
static inline bool
util_format_is_plain(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return false;
   }

   return desc->layout == UTIL_FORMAT_LAYOUT_PLAIN;
}

static inline bool
util_format_is_compressed(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return false;
   }

   switch (desc->layout) {
   case UTIL_FORMAT_LAYOUT_S3TC:
   case UTIL_FORMAT_LAYOUT_RGTC:
   case UTIL_FORMAT_LAYOUT_ETC:
   case UTIL_FORMAT_LAYOUT_BPTC:
   case UTIL_FORMAT_LAYOUT_ASTC:
   case UTIL_FORMAT_LAYOUT_ATC:
   case UTIL_FORMAT_LAYOUT_FXT1:
      /* XXX add other formats in the future */
      return true;
   default:
      return false;
   }
}

static inline bool
util_format_is_srgb(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return false;
   }

   return desc->colorspace == UTIL_FORMAT_COLORSPACE_SRGB;
}

static inline bool
util_format_has_depth(const struct util_format_description *desc)
{
   return desc->colorspace == UTIL_FORMAT_COLORSPACE_ZS &&
          desc->swizzle[0] != PIPE_SWIZZLE_NONE;
}

static inline bool
util_format_has_stencil(const struct util_format_description *desc)
{
   return desc->colorspace == UTIL_FORMAT_COLORSPACE_ZS &&
          desc->swizzle[1] != PIPE_SWIZZLE_NONE;
}

static inline bool
util_format_is_depth_or_stencil(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return false;
   }

   return util_format_has_depth(desc) ||
          util_format_has_stencil(desc);
}

static inline bool
util_format_is_depth_and_stencil(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return false;
   }

   return util_format_has_depth(desc) &&
          util_format_has_stencil(desc);
}


/**
 * Calculates the depth format type based upon the incoming format description.
 */
static inline unsigned
util_get_depth_format_type(const struct util_format_description *desc)
{
   unsigned depth_channel = desc->swizzle[0];
   if (desc->colorspace == UTIL_FORMAT_COLORSPACE_ZS &&
       depth_channel != PIPE_SWIZZLE_NONE) {
      return desc->channel[depth_channel].type;
   } else {
      return UTIL_FORMAT_TYPE_VOID;
   }
}


/**
 * Calculates the MRD for the depth format. MRD is used in depth bias
 * for UNORM and unbound depth buffers. When the depth buffer is floating
 * point, the depth bias calculation does not use the MRD. However, the
 * default MRD will be 1.0 / ((1 << 24) - 1).
 */
double
util_get_depth_format_mrd(const struct util_format_description *desc);


/**
 * Return whether this is an RGBA, Z, S, or combined ZS format.
 * Useful for initializing pipe_blit_info::mask.
 */
static inline unsigned
util_format_get_mask(enum pipe_format format)
{
   const struct util_format_description *desc =
      util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return 0;
   }

   if (util_format_has_depth(desc)) {
      if (util_format_has_stencil(desc)) {
         return PIPE_MASK_ZS;
      } else {
         return PIPE_MASK_Z;
      }
   } else {
      if (util_format_has_stencil(desc)) {
         return PIPE_MASK_S;
      } else {
         return PIPE_MASK_RGBA;
      }
   }
}

bool
util_format_has_alpha(enum pipe_format format);


bool
util_format_is_luminance(enum pipe_format format);

bool
util_format_is_alpha(enum pipe_format format);

bool
util_format_is_luminance_alpha(enum pipe_format format);


bool
util_format_is_intensity(enum pipe_format format);

bool
util_format_is_pure_integer(enum pipe_format format);

bool
util_format_is_pure_sint(enum pipe_format format);

bool
util_format_is_pure_uint(enum pipe_format format);

bool
util_format_is_snorm(enum pipe_format format);

/**
 * Check if the src format can be blitted to the destination format with
 * a simple memcpy.  For example, blitting from RGBA to RGBx is OK, but not
 * the reverse.
 */
bool
util_is_format_compatible(const struct util_format_description *src_desc,
                          const struct util_format_description *dst_desc);

/**
 * Whether this format is a rgab8 variant.
 *
 * That is, any format that matches the
 *
 *   PIPE_FORMAT_?8?8?8?8_UNORM
 */
static inline bool
util_format_is_rgba8_variant(const struct util_format_description *desc)
{
   unsigned chan;

   if(desc->block.width != 1 ||
      desc->block.height != 1 ||
      desc->block.bits != 32)
      return false;

   for(chan = 0; chan < 4; ++chan) {
      if(desc->channel[chan].type != UTIL_FORMAT_TYPE_UNSIGNED &&
         desc->channel[chan].type != UTIL_FORMAT_TYPE_VOID)
         return false;
      if(desc->channel[chan].type == UTIL_FORMAT_TYPE_UNSIGNED &&
         !desc->channel[chan].normalized)
         return false;
      if(desc->channel[chan].size != 8)
         return false;
   }

   return true;
}


/**
 * Return total bits needed for the pixel format per block.
 */
static inline unsigned
util_format_get_blocksizebits(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return 0;
   }

   return desc->block.bits;
}

/**
 * Return bytes per block (not pixel) for the given format.
 */
static inline unsigned
util_format_get_blocksize(enum pipe_format format)
{
   unsigned bits = util_format_get_blocksizebits(format);
   unsigned bytes = bits / 8;

   assert(bits % 8 == 0);
   assert(bytes > 0);
   if (bytes == 0) {
      bytes = 1;
   }

   return bytes;
}

static inline unsigned
util_format_get_blockwidth(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return 1;
   }

   return desc->block.width;
}

static inline unsigned
util_format_get_blockheight(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return 1;
   }

   return desc->block.height;
}

static inline unsigned
util_format_get_blockdepth(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return 1;
   }

   return desc->block.depth;
}

static inline unsigned
util_format_get_nblocksx(enum pipe_format format,
                         unsigned x)
{
   unsigned blockwidth = util_format_get_blockwidth(format);
   return (x + blockwidth - 1) / blockwidth;
}

static inline unsigned
util_format_get_nblocksy(enum pipe_format format,
                         unsigned y)
{
   unsigned blockheight = util_format_get_blockheight(format);
   return (y + blockheight - 1) / blockheight;
}

static inline unsigned
util_format_get_nblocksz(enum pipe_format format,
                         unsigned z)
{
   unsigned blockdepth = util_format_get_blockdepth(format);
   return (z + blockdepth - 1) / blockdepth;
}

static inline unsigned
util_format_get_nblocks(enum pipe_format format,
                        unsigned width,
                        unsigned height)
{
   assert(util_format_get_blockdepth(format) == 1);
   return util_format_get_nblocksx(format, width) * util_format_get_nblocksy(format, height);
}

static inline size_t
util_format_get_stride(enum pipe_format format,
                       unsigned width)
{
   return util_format_get_nblocksx(format, width) * util_format_get_blocksize(format);
}

static inline size_t
util_format_get_2d_size(enum pipe_format format,
                        size_t stride,
                        unsigned height)
{
   return util_format_get_nblocksy(format, height) * stride;
}

static inline unsigned
util_format_get_component_bits(enum pipe_format format,
                               enum util_format_colorspace colorspace,
                               unsigned component)
{
   const struct util_format_description *desc = util_format_description(format);
   enum util_format_colorspace desc_colorspace;

   if (!format) {
      return 0;
   }

   if (component >= 4)
      return 0;

   /* Treat RGB and SRGB as equivalent. */
   if (colorspace == UTIL_FORMAT_COLORSPACE_SRGB) {
      colorspace = UTIL_FORMAT_COLORSPACE_RGB;
   }
   if (desc->colorspace == UTIL_FORMAT_COLORSPACE_SRGB) {
      desc_colorspace = UTIL_FORMAT_COLORSPACE_RGB;
   } else {
      desc_colorspace = desc->colorspace;
   }

   if (desc_colorspace != colorspace) {
      return 0;
   }

   switch (desc->swizzle[component]) {
   case PIPE_SWIZZLE_X:
      return desc->channel[0].size;
   case PIPE_SWIZZLE_Y:
      return desc->channel[1].size;
   case PIPE_SWIZZLE_Z:
      return desc->channel[2].size;
   case PIPE_SWIZZLE_W:
      return desc->channel[3].size;
   default:
      return 0;
   }
}

/**
 * Given a linear RGB colorspace format, return the corresponding SRGB
 * format, or PIPE_FORMAT_NONE if none.
 */
static inline enum pipe_format
util_format_srgb(enum pipe_format format)
{
   if (util_format_is_srgb(format))
      return format;

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return format;
   }

   return util_format_description(format)->srgb_equivalent;
}

/**
 * Given an sRGB format, return the corresponding linear colorspace format.
 * For non sRGB formats, return the format unchanged.
 */
static inline enum pipe_format
util_format_linear(enum pipe_format format)
{
   if (!util_format_is_srgb(format))
      return format;

   assert(format < PIPE_FORMAT_COUNT);
   if (unlikely(format >= PIPE_FORMAT_COUNT)) {
      return format;
   }

   return util_format_description(format)->linear_equivalent;
}

/**
 * Given a depth-stencil format, return the corresponding stencil-only format.
 * For stencil-only formats, return the format unchanged.
 */
static inline enum pipe_format
util_format_stencil_only(enum pipe_format format)
{
   switch (format) {
   /* mask out the depth component */
   case PIPE_FORMAT_Z24_UNORM_S8_UINT:
      return PIPE_FORMAT_X24S8_UINT;
   case PIPE_FORMAT_S8_UINT_Z24_UNORM:
      return PIPE_FORMAT_S8X24_UINT;
   case PIPE_FORMAT_Z32_FLOAT_S8X24_UINT:
      return PIPE_FORMAT_X32_S8X24_UINT;

   /* stencil only formats */
   case PIPE_FORMAT_X24S8_UINT:
   case PIPE_FORMAT_S8X24_UINT:
   case PIPE_FORMAT_X32_S8X24_UINT:
   case PIPE_FORMAT_S8_UINT:
      return format;

   default:
      return PIPE_FORMAT_NONE;
   }
}

/**
 * Converts PIPE_FORMAT_*I* to PIPE_FORMAT_*R*.
 * This is identity for non-intensity formats.
 */
static inline enum pipe_format
util_format_intensity_to_red(enum pipe_format format)
{
   switch (format) {
   case PIPE_FORMAT_I8_UNORM:
      return PIPE_FORMAT_R8_UNORM;
   case PIPE_FORMAT_I8_SNORM:
      return PIPE_FORMAT_R8_SNORM;
   case PIPE_FORMAT_I16_UNORM:
      return PIPE_FORMAT_R16_UNORM;
   case PIPE_FORMAT_I16_SNORM:
      return PIPE_FORMAT_R16_SNORM;
   case PIPE_FORMAT_I16_FLOAT:
      return PIPE_FORMAT_R16_FLOAT;
   case PIPE_FORMAT_I32_FLOAT:
      return PIPE_FORMAT_R32_FLOAT;
   case PIPE_FORMAT_I8_UINT:
      return PIPE_FORMAT_R8_UINT;
   case PIPE_FORMAT_I8_SINT:
      return PIPE_FORMAT_R8_SINT;
   case PIPE_FORMAT_I16_UINT:
      return PIPE_FORMAT_R16_UINT;
   case PIPE_FORMAT_I16_SINT:
      return PIPE_FORMAT_R16_SINT;
   case PIPE_FORMAT_I32_UINT:
      return PIPE_FORMAT_R32_UINT;
   case PIPE_FORMAT_I32_SINT:
      return PIPE_FORMAT_R32_SINT;
   default:
      assert(!util_format_is_intensity(format));
      return format;
   }
}

/**
 * Converts PIPE_FORMAT_*L* to PIPE_FORMAT_*R*.
 * This is identity for non-luminance formats.
 */
static inline enum pipe_format
util_format_luminance_to_red(enum pipe_format format)
{
   switch (format) {
   case PIPE_FORMAT_L8_UNORM:
      return PIPE_FORMAT_R8_UNORM;
   case PIPE_FORMAT_L8_SNORM:
      return PIPE_FORMAT_R8_SNORM;
   case PIPE_FORMAT_L16_UNORM:
      return PIPE_FORMAT_R16_UNORM;
   case PIPE_FORMAT_L16_SNORM:
      return PIPE_FORMAT_R16_SNORM;
   case PIPE_FORMAT_L16_FLOAT:
      return PIPE_FORMAT_R16_FLOAT;
   case PIPE_FORMAT_L32_FLOAT:
      return PIPE_FORMAT_R32_FLOAT;
   case PIPE_FORMAT_L8_UINT:
      return PIPE_FORMAT_R8_UINT;
   case PIPE_FORMAT_L8_SINT:
      return PIPE_FORMAT_R8_SINT;
   case PIPE_FORMAT_L16_UINT:
      return PIPE_FORMAT_R16_UINT;
   case PIPE_FORMAT_L16_SINT:
      return PIPE_FORMAT_R16_SINT;
   case PIPE_FORMAT_L32_UINT:
      return PIPE_FORMAT_R32_UINT;
   case PIPE_FORMAT_L32_SINT:
      return PIPE_FORMAT_R32_SINT;

   case PIPE_FORMAT_LATC1_UNORM:
      return PIPE_FORMAT_RGTC1_UNORM;
   case PIPE_FORMAT_LATC1_SNORM:
      return PIPE_FORMAT_RGTC1_SNORM;

   case PIPE_FORMAT_L4A4_UNORM:
      return PIPE_FORMAT_R4A4_UNORM;

   case PIPE_FORMAT_L8A8_UNORM:
      return PIPE_FORMAT_R8A8_UNORM;
   case PIPE_FORMAT_L8A8_SNORM:
      return PIPE_FORMAT_R8A8_SNORM;
   case PIPE_FORMAT_L16A16_UNORM:
      return PIPE_FORMAT_R16A16_UNORM;
   case PIPE_FORMAT_L16A16_SNORM:
      return PIPE_FORMAT_R16A16_SNORM;
   case PIPE_FORMAT_L16A16_FLOAT:
      return PIPE_FORMAT_R16A16_FLOAT;
   case PIPE_FORMAT_L32A32_FLOAT:
      return PIPE_FORMAT_R32A32_FLOAT;
   case PIPE_FORMAT_L8A8_UINT:
      return PIPE_FORMAT_R8A8_UINT;
   case PIPE_FORMAT_L8A8_SINT:
      return PIPE_FORMAT_R8A8_SINT;
   case PIPE_FORMAT_L16A16_UINT:
      return PIPE_FORMAT_R16A16_UINT;
   case PIPE_FORMAT_L16A16_SINT:
      return PIPE_FORMAT_R16A16_SINT;
   case PIPE_FORMAT_L32A32_UINT:
      return PIPE_FORMAT_R32A32_UINT;
   case PIPE_FORMAT_L32A32_SINT:
      return PIPE_FORMAT_R32A32_SINT;

   /* We don't have compressed red-alpha variants for these. */
   case PIPE_FORMAT_LATC2_UNORM:
   case PIPE_FORMAT_LATC2_SNORM:
      return PIPE_FORMAT_NONE;

   default:
      assert(!util_format_is_luminance(format) &&
	     !util_format_is_luminance_alpha(format));
      return format;
   }
}

/**
 * Return the number of components stored.
 * Formats with block size != 1x1 will always have 1 component (the block).
 */
static inline unsigned
util_format_get_nr_components(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);
   assert(desc->nr_channels <= 4);

   return desc->nr_channels;
}

/**
 * Return the index of the first non-void channel
 * -1 if no non-void channels
 */
static inline int
util_format_get_first_non_void_channel(enum pipe_format format)
{
   const struct util_format_description *desc = util_format_description(format);
   int i;

   for (i = 0; i < 4; i++)
      if (desc->channel[i].type != UTIL_FORMAT_TYPE_VOID)
         break;

   if (i == 4)
       return -1;

   return i;
}

/*
 * Generic format conversion;
 */

bool
util_format_fits_8unorm(const struct util_format_description *format_desc);

/*
 * Swizzle operations.
 */

/* Compose two sets of swizzles.
 * If V is a 4D vector and the function parameters represent functions that
 * swizzle vector components, this holds:
 *     swz2(swz1(V)) = dst(V)
 */
void util_format_compose_swizzles(const unsigned char swz1[4],
                                  const unsigned char swz2[4],
                                  unsigned char dst[4]);

/* Apply the swizzle provided in \param swz (which is one of PIPE_SWIZZLE_x)
 * to \param src and store the result in \param dst.
 * \param is_integer determines the value written for PIPE_SWIZZLE_ONE.
 */
void util_format_apply_color_swizzle(union pipe_color_union *dst,
                                     const union pipe_color_union *src,
                                     const unsigned char swz[4],
                                     const bool is_integer);

void util_format_swizzle_4f(float *dst, const float *src,
                            const unsigned char swz[4]);

void util_format_unswizzle_4f(float *dst, const float *src,
                              const unsigned char swz[4]);

#ifdef __cplusplus
} // extern "C" {
#endif

#endif /* ! U_FORMAT_H */
