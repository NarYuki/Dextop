/*
 * Copyright (c) 2011-2018, The Linux Foundation. All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef __GR_ADRENO_INFO_H__
#define __GR_ADRENO_INFO_H__

#include <stddef.h>
#include <stdint.h>

namespace gralloc {

typedef enum {
  ADRENO_PIXELFORMAT_UNKNOWN = 0,
  ADRENO_PIXELFORMAT_R16G16B16A16_FLOAT = 10,
  ADRENO_PIXELFORMAT_R10G10B10A2_UNORM = 24,
  ADRENO_PIXELFORMAT_R8G8B8A8 = 28,
  ADRENO_PIXELFORMAT_R8G8B8A8_SRGB = 29,
  ADRENO_PIXELFORMAT_D32_FLOAT = 40,
  ADRENO_PIXELFORMAT_D24_UNORM_S8_UINT = 45,
  ADRENO_PIXELFORMAT_D16_UNORM = 55,
  ADRENO_PIXELFORMAT_B5G6R5 = 85,
  ADRENO_PIXELFORMAT_B5G5R5A1 = 86,
  ADRENO_PIXELFORMAT_B8G8R8A8_UNORM = 87,
  ADRENO_PIXELFORMAT_B8G8R8A8 = 90,
  ADRENO_PIXELFORMAT_B8G8R8A8_SRGB = 91,
  ADRENO_PIXELFORMAT_B8G8R8X8_SRGB = 93,
  ADRENO_PIXELFORMAT_NV12 = 103,
  ADRENO_PIXELFORMAT_P010 = 104,
  ADRENO_PIXELFORMAT_YUY2 = 107,
  ADRENO_PIXELFORMAT_B4G4R4A4 = 115,
  ADRENO_PIXELFORMAT_NV12_EXT = 506,
  ADRENO_PIXELFORMAT_R8G8B8X8 = 507,
  ADRENO_PIXELFORMAT_R8G8B8 = 508,
  ADRENO_PIXELFORMAT_A1B5G5R5 = 519,
  ADRENO_PIXELFORMAT_R8G8B8X8_SRGB = 520,
  ADRENO_PIXELFORMAT_R8G8B8_SRGB = 521,
  ADRENO_PIXELFORMAT_A2B10G10R10_UNORM = 532,
  ADRENO_PIXELFORMAT_R10G10B10X2_UNORM = 537,
  ADRENO_PIXELFORMAT_D24_UNORM_X8_UINT = 548,
  ADRENO_PIXELFORMAT_D24_UNORM = 549,
  ADRENO_PIXELFORMAT_D32_FLOAT_X24S8_UINT = 551,
  ADRENO_PIXELFORMAT_S8_UINT = 552,
  ADRENO_PIXELFORMAT_ASTC_4X4 = 568,
  ADRENO_PIXELFORMAT_ASTC_5X4 = 569,
  ADRENO_PIXELFORMAT_ASTC_5X5 = 570,
  ADRENO_PIXELFORMAT_ASTC_6X5 = 571,
  ADRENO_PIXELFORMAT_ASTC_6X6 = 572,
  ADRENO_PIXELFORMAT_ASTC_8X5 = 573,
  ADRENO_PIXELFORMAT_ASTC_8X6 = 574,
  ADRENO_PIXELFORMAT_ASTC_8X8 = 575,
  ADRENO_PIXELFORMAT_ASTC_10X5 = 576,
  ADRENO_PIXELFORMAT_ASTC_10X6 = 577,
  ADRENO_PIXELFORMAT_ASTC_10X8 = 578,
  ADRENO_PIXELFORMAT_ASTC_10X10 = 579,
  ADRENO_PIXELFORMAT_ASTC_12X10 = 580,
  ADRENO_PIXELFORMAT_ASTC_12X12 = 581,
  ADRENO_PIXELFORMAT_ASTC_4X4_SRGB = 582,
  ADRENO_PIXELFORMAT_ASTC_5X4_SRGB = 583,
  ADRENO_PIXELFORMAT_ASTC_5X5_SRGB = 584,
  ADRENO_PIXELFORMAT_ASTC_6X5_SRGB = 585,
  ADRENO_PIXELFORMAT_ASTC_6X6_SRGB = 586,
  ADRENO_PIXELFORMAT_ASTC_8X5_SRGB = 587,
  ADRENO_PIXELFORMAT_ASTC_8X6_SRGB = 588,
  ADRENO_PIXELFORMAT_ASTC_8X8_SRGB = 589,
  ADRENO_PIXELFORMAT_ASTC_10X5_SRGB = 590,
  ADRENO_PIXELFORMAT_ASTC_10X6_SRGB = 591,
  ADRENO_PIXELFORMAT_ASTC_10X8_SRGB = 592,
  ADRENO_PIXELFORMAT_ASTC_10X10_SRGB = 593,
  ADRENO_PIXELFORMAT_ASTC_12X10_SRGB = 594,
  ADRENO_PIXELFORMAT_ASTC_12X12_SRGB = 595,

  ADRENO_PIXELFORMAT_R5G6B5 = 610,
  ADRENO_PIXELFORMAT_R5G5B5A1 = 611,
  ADRENO_PIXELFORMAT_R4G4B4A4 = 612,
  ADRENO_PIXELFORMAT_UYVY = 614,
  ADRENO_PIXELFORMAT_NV21 = 619,
  ADRENO_PIXELFORMAT_Y8U8V8A8 = 620,
  ADRENO_PIXELFORMAT_Y8 = 625,
  ADRENO_PIXELFORMAT_TP10 = 654,
} ADRENOPIXELFORMAT;

typedef enum {
  SURFACE_TILE_MODE_DISABLE = 0x0,
  SURFACE_TILE_MODE_ENABLE = 0x1
} surface_tile_mode_t;

class AdrenoMemInfo {
 public:
  void GetAlignedWidthAndHeight(int width, int height, int format, int usage,
                                unsigned int* aligned_w, unsigned int* aligned_h, bool ubwc_enabled,
                                bool tile_enabled);

  void AlignUnCompressedRGB(int width, int height, int format, int tileEnabled,
                            unsigned int* aligned_w, unsigned int* aligned_h);

  void AlignCompressedRGB(int width, int height, int format, unsigned int* aligned_w,
                          unsigned int* aligned_h);

  uint32_t GetGpuPixelAlignment();

  bool IsUBWCSupportedByGPU(int format);

  bool IsPISupportedByGPU(int format, uint64_t usage);
  ADRENOPIXELFORMAT GetGpuPixelFormat(int hal_format);

  uint32_t AdrenoGetMetadataBlobSize();

  int AdrenoInitMemoryLayout(void* metadata_blob, int width, int height, int depth, int format,
                             int num_samples, int isUBWC, uint64_t usage, uint32_t num_planes);
  uint32_t AdrenoGetAlignedGpuBufferSize(void* metadata_blob);

  bool AdrenoSizeAPIAvaliable();

  static AdrenoMemInfo* GetInstance();

 private:
  AdrenoMemInfo();
  ~AdrenoMemInfo();
  int (*LINK_adreno_compute_padding)(int width, int bpp, int surface_tile_height,
                                     int screen_tile_height, int padding_threshold) = NULL;
  void (*LINK_adreno_compute_aligned_width_and_height)(int width, int height, int bpp,
                                                       int tile_mode, int raster_mode,
                                                       int padding_threshold, int* aligned_w,
                                                       int* aligned_h) = NULL;
  void (*LINK_adreno_compute_fmt_aligned_width_and_height)(int width, int height, int plane_id,
                                                           int format, int num_samples,
                                                           int tile_mode, int raster_mode,
                                                           int padding_threshold, int* aligned_w,
                                                           int* aligned_h) = NULL;
  void (*LINK_adreno_compute_compressedfmt_aligned_width_and_height)(
      int width, int height, int format, int tile_mode, int raster_mode, int padding_threshold,
      int* aligned_w, int* aligned_h, int* bpp) = NULL;
  int (*LINK_adreno_isUBWCSupportedByGpu)(ADRENOPIXELFORMAT format) = NULL;
  unsigned int (*LINK_adreno_get_gpu_pixel_alignment)() = NULL;

  uint32_t (*LINK_adreno_get_metadata_blob_size)() = NULL;
  int (*LINK_adreno_init_memory_layout)(void* metadata_blob, int width, int height, int depth,
                                        ADRENOPIXELFORMAT format, int num_samples,
                                        surface_tile_mode_t tile_mode, uint64_t usage,
                                        uint32_t num_planes) = NULL;
  uint32_t (*LINK_adreno_get_aligned_gpu_buffer_size)(void* metadata_blob) = NULL;
  int (*LINK_adreno_isPISupportedByGpu)(int format, uint64_t usage) = NULL;

  bool gfx_ubwc_disable_ = false;
  bool gfx_ahardware_buffer_disable_ = false;
  void* libadreno_utils_ = NULL;

  static AdrenoMemInfo* s_instance;
};

}

#endif
