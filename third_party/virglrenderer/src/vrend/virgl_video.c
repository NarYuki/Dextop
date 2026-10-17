/**************************************************************************
 *
 * Copyright (C) 2022 Kylin Software Co., Ltd.
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

/**
 * @file
 * Implementation of general video codec interface.
 *
 * This implementation is currently based on VA-API, and other interfaces,
 * such as VDPAU and proprietary interfaces, can also be considered in the
 * future.
 *
 * Two objects are implemented here:
 * virgl_video_buffer:
 *   Buffer for storing raw YUV formatted data. Currently, it is a wrapper
 *   for VASurface.
 * virgl_video_codec:
 *   Represents a video encoder or decoder. It's a wrapper of VAContext and
 *   mainly provides the following methods:
 *   - virgl_video_begin_frame()
 *     It calls vaBeginPicture() to prepare for encoding and decoding. For
 *     encoding, it also needs to upload the raw picture data from the guest
 *     side into the local VASurface.
 *   - virgl_video_decode_bitstream()
 *     It constructs the decoding-related VABuffers according to the picture
 *     description information, and then calls vaRenderPicture() for decoding.
 *   - virgl_video_encode_bitstream()
 *     It constructs the encoding-related VABuffers according to the picture
 *     description information, and then calls vaRenderPicture() for encoding.
 *   - virgl_video_end_frame()
 *     It calls vaEndPicture() to end encoding and decoding. After decoding,
 *     it transmits the raw picture data from VASurface to the guest side,
 *     and after encoding, it transmits the result and the coded data in
 *     VACodedBuffer to the guest side.
 *
 * @author Feng Jiang <jiangfeng@kylinos.cn>
 */


#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <epoxy/gl.h>
#include <epoxy/egl.h>
#include <va/va.h>
#include <va/va_drm.h>
#include <va/va_drmcommon.h>
#include <drm_fourcc.h>

#include "pipe/p_video_state.h"
#include "util/u_formats.h"
#include "util/u_memory.h"
#include "virgl_hw.h"
#include "virgl_video_hw.h"
#include "virgl_util.h"
#include "virgl_video.h"

/*
 * The max size of codec buffer is approximately:
 *   num_of_macroblocks * max_size_of_per_macroblock + size_of_some_headers
 * Now, we only support YUV420 formats, this means that we have a limit of
 * 3200 bits(400 Bytes) per macroblock. To simplify the calculation, we
 * directly use 512 instead of 400.
 */
#define CODED_BUF_DEFAULT_SIZE(width, height) \
    ((width) * (height) / (16 * 16) * 512)

struct virgl_video_buffer {
    enum pipe_format format;
    uint32_t width;
    uint32_t height;
    bool interlanced;
    VASurfaceID va_sfc;
    struct virgl_video_dma_buf *dmabuf;
    void *opaque;                               /* User opaque data */
};


struct virgl_video_codec {
   enum pipe_video_profile profile;
   uint32_t level;
   enum pipe_video_entrypoint entrypoint;
   enum pipe_video_chroma_format chroma_format;
   uint32_t width;
   uint32_t height;
   uint32_t max_references;
   VAContextID va_ctx;
   VAConfigID  va_cfg;
   struct virgl_video_buffer *buffer;
   struct virgl_video_buffer *ref_pic_list[32]; /* Enc: reference pictures */
   VABufferID  va_coded_buf;                    /* Enc: VACodedBuffer */
   void *opaque;                                /* User opaque data */
};

struct virgl_video_supported_entry {
    VAProfile profile;
    VAEntrypoint entrypoints[16];
};

static VADisplay va_dpy;

static struct virgl_video_callbacks *callbacks = NULL;

static enum pipe_video_profile pipe_profile_from_va(VAProfile profile)
{
   switch (profile) {
   case VAProfileMPEG2Simple:
      return PIPE_VIDEO_PROFILE_MPEG2_SIMPLE;
   case VAProfileMPEG2Main:
      return PIPE_VIDEO_PROFILE_MPEG2_MAIN;
   case VAProfileMPEG4Simple:
      return PIPE_VIDEO_PROFILE_MPEG4_SIMPLE;
   case VAProfileMPEG4AdvancedSimple:
      return PIPE_VIDEO_PROFILE_MPEG4_ADVANCED_SIMPLE;
   case VAProfileVC1Simple:
      return PIPE_VIDEO_PROFILE_VC1_SIMPLE;
   case VAProfileVC1Main:
      return PIPE_VIDEO_PROFILE_VC1_MAIN;
   case VAProfileVC1Advanced:
      return PIPE_VIDEO_PROFILE_VC1_ADVANCED;
   case VAProfileH264ConstrainedBaseline:
      return PIPE_VIDEO_PROFILE_MPEG4_AVC_BASELINE;
   case VAProfileH264Main:
      return PIPE_VIDEO_PROFILE_MPEG4_AVC_MAIN;
   case VAProfileH264High:
      return PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH;
   case VAProfileHEVCMain:
      return PIPE_VIDEO_PROFILE_HEVC_MAIN;
   case VAProfileHEVCMain10:
      return PIPE_VIDEO_PROFILE_HEVC_MAIN_10;
   case VAProfileJPEGBaseline:
      return PIPE_VIDEO_PROFILE_JPEG_BASELINE;
   case VAProfileVP9Profile0:
      return PIPE_VIDEO_PROFILE_VP9_PROFILE0;
   case VAProfileVP9Profile2:
      return PIPE_VIDEO_PROFILE_VP9_PROFILE2;
   case VAProfileAV1Profile0:
      return PIPE_VIDEO_PROFILE_AV1_MAIN;
   case VAProfileNone:
       return PIPE_VIDEO_PROFILE_UNKNOWN;
   default:
      return PIPE_VIDEO_PROFILE_UNKNOWN;
   }
}

/* NOTE: mesa va frontend only supports VLD and EncSlice */
static enum pipe_video_entrypoint pipe_entrypoint_from_va(
        VAEntrypoint entrypoint)
{
    switch (entrypoint) {
    case VAEntrypointVLD:
        return PIPE_VIDEO_ENTRYPOINT_BITSTREAM;
    case VAEntrypointIDCT:
        return PIPE_VIDEO_ENTRYPOINT_IDCT;
    case VAEntrypointMoComp:
        return PIPE_VIDEO_ENTRYPOINT_MC;
    case VAEntrypointEncSlice: /* fall through */
    case VAEntrypointEncSliceLP:
        return PIPE_VIDEO_ENTRYPOINT_ENCODE;
    default:
        return PIPE_VIDEO_ENTRYPOINT_UNKNOWN;
    }
}

static enum pipe_format pipe_format_from_va_fourcc(unsigned format)
{
   switch(format) {
   case VA_FOURCC('N','V','1','2'):
      return PIPE_FORMAT_NV12;
   case VA_FOURCC('P','0','1','0'):
      return PIPE_FORMAT_P010;
   case VA_FOURCC('P','0','1','6'):
      return PIPE_FORMAT_P016;
   case VA_FOURCC('I','4','2','0'):
      return PIPE_FORMAT_IYUV;
   case VA_FOURCC('Y','V','1','2'):
      return PIPE_FORMAT_YV12;
   case VA_FOURCC('Y','U','Y','V'):
   case VA_FOURCC('Y','U','Y','2'):
      return PIPE_FORMAT_YUYV;
   case VA_FOURCC('U','Y','V','Y'):
      return PIPE_FORMAT_UYVY;
   case VA_FOURCC('B','G','R','A'):
      return PIPE_FORMAT_B8G8R8A8_UNORM;
   case VA_FOURCC('R','G','B','A'):
      return PIPE_FORMAT_R8G8B8A8_UNORM;
   case VA_FOURCC('B','G','R','X'):
      return PIPE_FORMAT_B8G8R8X8_UNORM;
   case VA_FOURCC('R','G','B','X'):
      return PIPE_FORMAT_R8G8B8X8_UNORM;
   default:
      return PIPE_FORMAT_NONE;
   }
}


static VAProfile va_profile_from_pipe(enum pipe_video_profile profile)
{
   switch (profile) {
   case PIPE_VIDEO_PROFILE_MPEG2_SIMPLE:
      return VAProfileMPEG2Simple;
   case PIPE_VIDEO_PROFILE_MPEG2_MAIN:
      return VAProfileMPEG2Main;
   case PIPE_VIDEO_PROFILE_MPEG4_SIMPLE:
      return VAProfileMPEG4Simple;
   case PIPE_VIDEO_PROFILE_MPEG4_ADVANCED_SIMPLE:
      return VAProfileMPEG4AdvancedSimple;
   case PIPE_VIDEO_PROFILE_VC1_SIMPLE:
      return VAProfileVC1Simple;
   case PIPE_VIDEO_PROFILE_VC1_MAIN:
      return VAProfileVC1Main;
   case PIPE_VIDEO_PROFILE_VC1_ADVANCED:
      return VAProfileVC1Advanced;
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_BASELINE:
      return VAProfileH264ConstrainedBaseline;
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_MAIN:
      return VAProfileH264Main;
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH:
      return VAProfileH264High;
   case PIPE_VIDEO_PROFILE_HEVC_MAIN:
      return VAProfileHEVCMain;
   case PIPE_VIDEO_PROFILE_HEVC_MAIN_10:
      return VAProfileHEVCMain10;
   case PIPE_VIDEO_PROFILE_JPEG_BASELINE:
      return VAProfileJPEGBaseline;
   case PIPE_VIDEO_PROFILE_VP9_PROFILE0:
      return VAProfileVP9Profile0;
   case PIPE_VIDEO_PROFILE_VP9_PROFILE2:
      return VAProfileVP9Profile2;
   case PIPE_VIDEO_PROFILE_AV1_MAIN:
      return VAProfileAV1Profile0;
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_EXTENDED:
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH10:
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH422:
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH444:
   case PIPE_VIDEO_PROFILE_MPEG4_AVC_CONSTRAINED_BASELINE:
   case PIPE_VIDEO_PROFILE_HEVC_MAIN_12:
   case PIPE_VIDEO_PROFILE_HEVC_MAIN_STILL:
   case PIPE_VIDEO_PROFILE_HEVC_MAIN_444:
   case PIPE_VIDEO_PROFILE_UNKNOWN:
      return VAProfileNone;
   default:
      return -1;
   }
}

/*
 * There is no invalid entrypoint defined in libva,
 * so add this definition to make the code clear
 */
#define VAEntrypointNone 0
static int va_entrypoint_from_pipe(enum pipe_video_entrypoint entrypoint)
{
    switch (entrypoint) {
    case PIPE_VIDEO_ENTRYPOINT_BITSTREAM:
        return VAEntrypointVLD;
    case PIPE_VIDEO_ENTRYPOINT_IDCT:
        return VAEntrypointIDCT;
    case PIPE_VIDEO_ENTRYPOINT_MC:
        return VAEntrypointMoComp;
    case PIPE_VIDEO_ENTRYPOINT_ENCODE:
        return VAEntrypointEncSlice;
    default:
        return VAEntrypointNone;
    }
}

static uint32_t va_format_from_pipe(uint32_t format)
{
    uint32_t va_format;

    switch(format) {
    case PIPE_FORMAT_P010:
        va_format = VA_RT_FORMAT_YUV420_10;
        break;
    default:
        va_format = VA_RT_FORMAT_YUV420;
    }

    return va_format;
}

static uint32_t va_format_from_pipe_chroma(
        enum pipe_video_chroma_format chroma_format)
{
    switch (chroma_format) {
    case PIPE_VIDEO_CHROMA_FORMAT_400:
        return VA_RT_FORMAT_YUV400;
    case PIPE_VIDEO_CHROMA_FORMAT_420:
        return VA_RT_FORMAT_YUV420;
    case PIPE_VIDEO_CHROMA_FORMAT_422:
        return VA_RT_FORMAT_YUV422;
    case PIPE_VIDEO_CHROMA_FORMAT_444:
        return VA_RT_FORMAT_YUV444;
    case PIPE_VIDEO_CHROMA_FORMAT_NONE:
    default:
        return 0;
    }
}

static uint32_t drm_format_from_va_fourcc(uint32_t va_fourcc)
{
    switch (va_fourcc) {
    case VA_FOURCC_NV12:
        return DRM_FORMAT_NV12;
    case VA_FOURCC_NV21:
        return DRM_FORMAT_NV21;
    default:
        return DRM_FORMAT_INVALID;
    }
}

static void fill_video_dma_buf(struct virgl_video_dma_buf *dmabuf,
                               const VADRMPRIMESurfaceDescriptor *desc)
{
    unsigned i, j, obj_idx;
    struct virgl_video_dma_buf_plane *plane;

/*
    virgl_debug("surface: fourcc=0x%08x, size=%ux%u, num_objects=%u,
                num_layers=%u\n", desc->fourcc, desc->width, desc->height,
                desc->num_objects, desc->num_layers);

    for (i = 0; i < desc->num_objects; i++)
        virgl_debug("  objects[%u]: fd=%d, size=%u, modifier=0x%lx\n",
                    i, desc->objects[i].fd, desc->objects[i].size,
                    desc->objects[i].drm_format_modifier);

    for (i = 0; i < desc->num_layers; i++)
        virgl_debug("  layers[%u] : format=0x%08x, num_planes=%u, "
                    "obj=%u,%u,%u,%u, offset=%u,%u,%u,%u, pitch=%u,%u,%u,%u\n",
                    i, desc->layers[i].drm_format, desc->layers[i].num_planes,
                    desc->layers[i].object_index[0],
                    desc->layers[i].object_index[1],
                    desc->layers[i].object_index[2],
                    desc->layers[i].object_index[3],
                    desc->layers[i].offset[0],
                    desc->layers[i].offset[1],
                    desc->layers[i].offset[2],
                    desc->layers[i].offset[3],
                    desc->layers[i].pitch[0],
                    desc->layers[i].pitch[1],
                    desc->layers[i].pitch[2],
                    desc->layers[i].pitch[3]);
*/

    dmabuf->drm_format = drm_format_from_va_fourcc(desc->fourcc);
    dmabuf->width = desc->width;
    dmabuf->height = desc->height;

    for (i = 0, dmabuf->num_planes = 0; i < desc->num_layers; i++) {
        for (j = 0; j < desc->layers[i].num_planes &&
                    dmabuf->num_planes < ARRAY_SIZE(dmabuf->planes); j++) {

            obj_idx = desc->layers[i].object_index[j];
            plane = &dmabuf->planes[dmabuf->num_planes++];
            plane->drm_format = desc->layers[i].drm_format;
            plane->offset     = desc->layers[i].offset[j];
            plane->pitch      = desc->layers[i].pitch[j];
            plane->fd         = desc->objects[obj_idx].fd;
            plane->size       = desc->objects[obj_idx].size;
            plane->modifier   = desc->objects[obj_idx].drm_format_modifier;
        }
    }
}

static struct virgl_video_dma_buf *export_video_dma_buf(
                                        struct virgl_video_buffer *buffer,
                                        unsigned flags)
{
    struct virgl_video_dma_buf *dmabuf;
    uint32_t exp_flags;
    VAStatus va_stat;
    VADRMPRIMESurfaceDescriptor desc;

    exp_flags = VA_EXPORT_SURFACE_SEPARATE_LAYERS;

    if (flags & VIRGL_VIDEO_DMABUF_READ_ONLY)
        exp_flags |= VA_EXPORT_SURFACE_READ_ONLY;

    if (flags & VIRGL_VIDEO_DMABUF_WRITE_ONLY)
        exp_flags |= VA_EXPORT_SURFACE_WRITE_ONLY;

    dmabuf = calloc(1, sizeof(*dmabuf));
    if (!dmabuf)
        return NULL;

    va_stat = vaExportSurfaceHandle(va_dpy, buffer->va_sfc,
                    VA_SURFACE_ATTRIB_MEM_TYPE_DRM_PRIME_2, exp_flags, &desc);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("export surface failed, err = 0x%X\n", va_stat);
        goto free_dmabuf;
    }

    fill_video_dma_buf(dmabuf, &desc);
    dmabuf->flags = flags;
    dmabuf->buf   = buffer;

    return dmabuf;

free_dmabuf:
    free(dmabuf);
    return NULL;
}

static void destroy_video_dma_buf(struct virgl_video_dma_buf *dmabuf)
{
    unsigned i;

    if (dmabuf) {
        for (i = 0; i < dmabuf->num_planes; i++)
            close(dmabuf->planes[i].fd);

        free(dmabuf);
    }
}

static void encode_upload_picture(struct virgl_video_codec *codec,
                                  struct virgl_video_buffer *buffer)
{
    VAStatus va_stat;

    if (!callbacks || !callbacks->encode_upload_picture)
        return;

    va_stat = vaSyncSurface(va_dpy, buffer->va_sfc);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("sync surface failed, err = 0x%x\n", va_stat);
        return;
    }

    if (!buffer->dmabuf)
        buffer->dmabuf = export_video_dma_buf(buffer, VIRGL_VIDEO_DMABUF_WRITE_ONLY);

    if (buffer->dmabuf)
        callbacks->encode_upload_picture(codec, buffer->dmabuf);
}

static void encode_completed(struct virgl_video_codec *codec,
                             struct virgl_video_buffer *buffer)
{
    VAStatus va_stat;
    VACodedBufferSegment *buf, *buf_list;
    void **coded_bufs = NULL;
    unsigned *coded_sizes = NULL;
    unsigned i, num_coded_bufs = 0;

    if (!callbacks || !callbacks->encode_completed)
        return;

    va_stat = vaMapBuffer(va_dpy, codec->va_coded_buf, (void **)(&buf_list));
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("map coded buffer failed, err = 0x%x\n", va_stat);
        return;
    }

    for (buf = buf_list; buf; buf = (VACodedBufferSegment *)buf->next)
        num_coded_bufs++;

    coded_bufs = calloc(num_coded_bufs, sizeof(void *));
    coded_sizes = calloc(num_coded_bufs, sizeof(unsigned));
    if (!coded_bufs || !coded_sizes) {
        virgl_error("alloc memory failed, num_coded_bufs %u\n", num_coded_bufs);
        goto fail_unmap_buffer;
    }

    for (buf = buf_list, i = 0; buf; buf = (VACodedBufferSegment *)buf->next) {
        coded_bufs[i]  = buf->buf;
        coded_sizes[i++] = buf->size;
    }

    callbacks->encode_completed(codec, buffer->dmabuf, NULL, num_coded_bufs,
                                (const void * const*)coded_bufs, coded_sizes);

fail_unmap_buffer:
    vaUnmapBuffer(va_dpy, codec->va_coded_buf);
    free(coded_bufs);
    free(coded_sizes);
}

static void decode_completed(struct virgl_video_codec *codec,
                             struct virgl_video_buffer *buffer)
{
    if (!callbacks || !callbacks->decode_completed)
        return;

    if (!buffer->dmabuf)
        buffer->dmabuf = export_video_dma_buf(buffer, VIRGL_VIDEO_DMABUF_READ_ONLY);

    if (buffer->dmabuf)
        callbacks->decode_completed(codec, buffer->dmabuf);
}

static VASurfaceID get_enc_ref_pic(struct virgl_video_codec *codec,
                                   uint32_t frame_num)
{
    uint32_t idx;
    struct virgl_video_create_buffer_args args;

    if (frame_num == VA_INVALID_ID)
        return VA_INVALID_ID;

    idx = frame_num % ARRAY_SIZE(codec->ref_pic_list);

    if (!codec->ref_pic_list[idx]) {
        args.format = PIPE_FORMAT_NV21;
        args.width = codec->width;
        args.height = codec->height;
        args.interlaced = 0;
        args.opaque = NULL;
        codec->ref_pic_list[idx] = virgl_video_create_buffer(&args);
        if (!codec->ref_pic_list[idx]) {
            virgl_error("create ref pic for frame_num %u failed\n", frame_num);
            return VA_INVALID_ID;
        }
    }

    return codec->ref_pic_list[idx]->va_sfc;
}

int virgl_video_init(int drm_fd,
                     struct virgl_video_callbacks *cbs, unsigned int flags)
{
    VAStatus va_stat;
    int major_ver, minor_ver;
    const char *driver;

    (void)flags;

    if (drm_fd < 0) {
        virgl_error("invalid drm fd: %d\n", drm_fd);
        return -1;
    }

    va_dpy = vaGetDisplayDRM(drm_fd);
    if (!va_dpy) {
        virgl_error("get va display failed\n");
        return -1;
    }

    va_stat = vaInitialize(va_dpy, &major_ver, &minor_ver);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("init va library failed\n");
        virgl_video_destroy();
        return -1;
    }

    virgl_info("VA-API version: %d.%d\n", major_ver, minor_ver);

    driver = vaQueryVendorString(va_dpy);
    virgl_info("Driver version: %s\n", driver ? driver : "<unknown>");

    if (!driver || !strstr(driver, "Mesa Gallium")) {
        virgl_error("only supports mesa va drivers now\n");
        virgl_video_destroy();
        return -1;
    }

    callbacks = cbs;

    return 0;
}

void virgl_video_destroy(void)
{
    if (va_dpy) {
        vaTerminate(va_dpy);
        va_dpy = NULL;
    }

    callbacks = NULL;
}

/* When entrypoint equals VAEntrypointNone, only match profile */
static bool is_supported(VAProfile profile, VAEntrypoint entrypoint)
{
    /* The profiles and entrypoints that virgl video currently supported */
    static const struct virgl_video_supported_entry tbl[] = {
        {VAProfileMPEG2Simple,  {VAEntrypointVLD, 0}},
        {VAProfileMPEG2Main,    {VAEntrypointVLD, 0}},
        {VAProfileH264ConstrainedBaseline,
                                {VAEntrypointVLD, VAEntrypointEncSlice, 0}},
        {VAProfileH264Main,     {VAEntrypointVLD, VAEntrypointEncSlice, 0}},
        {VAProfileH264High,     {VAEntrypointVLD, VAEntrypointEncSlice, 0}},
        {VAProfileHEVCMain,     {VAEntrypointVLD, VAEntrypointEncSlice, 0}},
        {VAProfileHEVCMain10,   {VAEntrypointVLD, VAEntrypointEncSlice, 0}},
        {VAProfileJPEGBaseline, {VAEntrypointVLD, 0}},
        {VAProfileVC1Simple,    {VAEntrypointVLD, 0}},
        {VAProfileVC1Main,      {VAEntrypointVLD, 0}},
        {VAProfileVC1Advanced,  {VAEntrypointVLD, 0}},
        {VAProfileVP9Profile0,  {VAEntrypointVLD, 0}},
        {VAProfileVP9Profile2,  {VAEntrypointVLD, 0}},
        {VAProfileAV1Profile0,  {VAEntrypointVLD, 0}},
        {VAProfileAV1Profile1,  {VAEntrypointVLD, 0}},
    };

    for (unsigned i = 0; i < ARRAY_SIZE(tbl); i++) {
        if (tbl[i].profile == profile) {
            if (entrypoint == VAEntrypointNone)
                return true;

            for (unsigned j = 0; j < ARRAY_SIZE(tbl[i].entrypoints) &&
                 tbl[i].entrypoints[j] != 0; j++)
                if (entrypoint == tbl[i].entrypoints[j])
                    return true;

            return false;
        }
    }

    return false;
}

static int fill_vcaps_entry(VAProfile profile, VAEntrypoint entrypoint,
                            struct virgl_video_caps *vcaps)
{
    VAConfigID cfg;
    VASurfaceAttrib *attrs;
    unsigned i, num_attrs;

    /* FIXME: default values */
    vcaps->profile = pipe_profile_from_va(profile);
    vcaps->entrypoint = pipe_entrypoint_from_va(entrypoint);
    vcaps->max_level = 0;
    vcaps->stacked_frames = 0;
    vcaps->max_width = 0;
    vcaps->max_height = 0;
    vcaps->prefered_format = PIPE_FORMAT_NONE;
    vcaps->max_macroblocks = 1;
    vcaps->npot_texture = 1;
    vcaps->supports_progressive = 1;
    vcaps->supports_interlaced = 0;
    vcaps->prefers_interlaced = 0;
    vcaps->max_temporal_layers = 0;

    vaCreateConfig(va_dpy, profile, entrypoint, NULL, 0, &cfg);

    vaQuerySurfaceAttributes(va_dpy, cfg, NULL, &num_attrs);
    attrs = calloc(num_attrs, sizeof(VASurfaceAttrib));
    if (!attrs)
        return -1;

    vaQuerySurfaceAttributes(va_dpy, cfg, attrs, &num_attrs);
    for (i = 0; i < num_attrs; i++) {
        switch (attrs[i].type) {
        case VASurfaceAttribMaxHeight:
            vcaps->max_height = attrs[i].value.value.i;
            break;
        case VASurfaceAttribMaxWidth:
            vcaps->max_width = attrs[i].value.value.i;
            break;
        case VASurfaceAttribPixelFormat:
            if (PIPE_FORMAT_NONE == vcaps->prefered_format)
                vcaps->prefered_format = \
                    pipe_format_from_va_fourcc(attrs[i].value.value.i);
            break;
        default:
            break;
        }
    }

    free(attrs);

    vaDestroyConfig(va_dpy, cfg);

    return 0;
}

int virgl_video_fill_caps(union virgl_caps *caps)
{
    int i, j;
    int num_profiles, num_entrypoints;
    VAProfile *profiles = NULL;
    VAEntrypoint *entrypoints = NULL;

    if (!va_dpy || !caps)
        return -1;

    num_entrypoints = vaMaxNumEntrypoints(va_dpy);
    entrypoints = calloc(num_entrypoints, sizeof(VAEntrypoint));
    if (!entrypoints)
        return -1;

    num_profiles = vaMaxNumProfiles(va_dpy);
    profiles = calloc(num_profiles, sizeof(VAProfile));
    if (!profiles) {
        free(entrypoints);
        return -1;
    }

    vaQueryConfigProfiles(va_dpy, profiles, &num_profiles);
    for (i = 0, caps->v2.num_video_caps = 0; i < num_profiles; i++) {
        if (!is_supported(profiles[i], VAEntrypointNone))
		continue;

        vaQueryConfigEntrypoints(va_dpy, profiles[i],
                                 entrypoints, &num_entrypoints);
        for (j = 0; j < num_entrypoints &&
             caps->v2.num_video_caps < ARRAY_SIZE(caps->v2.video_caps); j++) {
	    if (!is_supported(profiles[i], entrypoints[j]))
		continue;

            fill_vcaps_entry(profiles[i], entrypoints[j],
                    &caps->v2.video_caps[caps->v2.num_video_caps++]);
        }
    }

    free(profiles);
    free(entrypoints);

    return 0;
}

struct virgl_video_codec *virgl_video_create_codec(
        const struct virgl_video_create_codec_args *args)
{
    VAStatus va_stat;
    VAConfigID cfg;
    VAContextID ctx;
    VAConfigAttrib attr;
    VAProfile profile;
    VAEntrypoint entrypoint;
    uint32_t format;
    struct virgl_video_codec *codec;

    if (!va_dpy || !args)
        return NULL;

    profile = va_profile_from_pipe(args->profile);
    entrypoint = va_entrypoint_from_pipe(args->entrypoint);
    format = va_format_from_pipe_chroma(args->chroma_format);
    if (VAProfileNone == profile || VAEntrypointNone == entrypoint)
        return NULL;

    codec = (struct virgl_video_codec *)calloc(1, sizeof(*codec));
    if (!codec)
        return NULL;

    attr.type = VAConfigAttribRTFormat;
    vaGetConfigAttributes(va_dpy, profile, entrypoint, &attr, 1);
    if (!(attr.value & format)) {
        virgl_error("format 0x%x not supported, supported formats: 0x%x\n",
                  format, attr.value);
        goto err;
    }

    va_stat = vaCreateConfig(va_dpy, profile, entrypoint, &attr, 1, &cfg);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("create config failed, err = 0x%x\n", va_stat);
        goto err;
    }
    codec->va_cfg = cfg;

    va_stat = vaCreateContext(va_dpy, cfg, args->width, args->height,
                                VA_PROGRESSIVE, NULL, 0, &ctx);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("create context failed, err = 0x%x\n", va_stat);
        goto err;
    }
    codec->va_ctx = ctx;

    codec->profile = args->profile;
    codec->level = args->level;
    codec->entrypoint = args->entrypoint;
    codec->chroma_format = args->chroma_format;
    codec->width = args->width;
    codec->height = args->height;
    codec->max_references = args->max_references;
    codec->opaque = args->opaque;

    if (entrypoint == VAEntrypointEncSlice) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VAEncCodedBufferType,
                       CODED_BUF_DEFAULT_SIZE(codec->width, codec->height),
                       1, NULL, &codec->va_coded_buf);
    }

    return codec;

err:
    virgl_video_destroy_codec(codec);

    return NULL;
}

void virgl_video_destroy_codec(struct virgl_video_codec *codec)
{
    unsigned i;

    if (!va_dpy || !codec)
        return;

    if (codec->va_ctx)
        vaDestroyContext(va_dpy, codec->va_ctx);

    if (codec->va_cfg)
        vaDestroyConfig(va_dpy, codec->va_cfg);

    if (codec->va_coded_buf)
        vaDestroyBuffer(va_dpy, codec->va_coded_buf);

    for (i = 0; i < ARRAY_SIZE(codec->ref_pic_list); i++) {
        if (codec->ref_pic_list[i])
            free(codec->ref_pic_list[i]);
    }

    free(codec);
}

struct virgl_video_buffer *virgl_video_create_buffer(
        const struct virgl_video_create_buffer_args *args)
{
    VAStatus va_stat;
    VASurfaceID sfc;
    uint32_t format;
    struct virgl_video_buffer *buffer;

    if (!va_dpy || !args)
        return NULL;

    format = va_format_from_pipe(args->format);
    if (!format) {
        virgl_error("pipe format %d not supported\n", args->format);
        return NULL;
    }

    buffer = (struct virgl_video_buffer *)calloc(1, sizeof(*buffer));
    if (!buffer)
        return NULL;

    va_stat = vaCreateSurfaces(va_dpy, format,
                               args->width, args->height, &sfc, 1, NULL, 0);
    if (VA_STATUS_SUCCESS != va_stat) {
        free(buffer);
        return NULL;
    }

    buffer->va_sfc = sfc;
    buffer->format = args->format;
    buffer->width  = args->width;
    buffer->height = args->height;
    buffer->opaque = args->opaque;

    return buffer;
}

void virgl_video_destroy_buffer(struct virgl_video_buffer *buffer)
{
    if (!va_dpy || !buffer)
        return;

    if (buffer->dmabuf)
        destroy_video_dma_buf(buffer->dmabuf);

    if (buffer->va_sfc)
        vaDestroySurfaces(va_dpy, &buffer->va_sfc, 1);

    free(buffer);
}

void *virgl_video_codec_opaque_data(struct virgl_video_codec *codec)
{
    return codec ? codec->opaque : NULL;
}

enum pipe_video_profile virgl_video_codec_profile(
        const struct virgl_video_codec *codec)
{
    return codec ? codec->profile : PIPE_VIDEO_PROFILE_UNKNOWN;
}

uint32_t virgl_video_buffer_id(const struct virgl_video_buffer *buffer)
{
    return (uint32_t)(buffer ? buffer->va_sfc : VA_INVALID_SURFACE);
}

void *virgl_video_buffer_opaque_data(struct virgl_video_buffer *buffer)
{
    return buffer ? buffer->opaque : NULL;
}

int virgl_video_begin_frame(struct virgl_video_codec *codec,
                            struct virgl_video_buffer *target)
{
    VAStatus va_stat;

    if (!va_dpy || !codec || !target)
        return -1;

    if (codec->entrypoint == PIPE_VIDEO_ENTRYPOINT_ENCODE)
        encode_upload_picture(codec, target);

    codec->buffer = target;
    va_stat = vaBeginPicture(va_dpy, codec->va_ctx, target->va_sfc);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("begin picture failed, err = 0x%x\n", va_stat);
        return -1;
    }

    return 0;
}


#define ITEM_SET(dest, src, member) \
        (dest)->member = (src)->member

#define ITEM_CPY(dest, src, member) \
        memcpy(&(dest)->member, &(src)->member, sizeof((dest)->member))


static void h264_init_picture(VAPictureH264 *pic)
{
    pic->picture_id           = VA_INVALID_SURFACE;
    pic->frame_idx            = 0;
    pic->flags                = VA_PICTURE_H264_INVALID;
    pic->TopFieldOrderCnt     = 0;
    pic->BottomFieldOrderCnt  = 0;
}

/*
 * Refer to vlVaHandlePictureParameterBufferH264() in mesa,
 * and comment out some unused parameters.
 */
static void h264_fill_picture_param(struct virgl_video_codec *codec,
                            struct virgl_video_buffer *target,
                            const struct virgl_h264_picture_desc *desc,
                            VAPictureParameterBufferH264 *vapp)
{
    unsigned i;
    VAPictureH264 *pic;

    (void)codec;

    /* CurrPic */
    pic = &vapp->CurrPic;
    pic->picture_id = target->va_sfc;
    pic->frame_idx  = desc->frame_num;
    pic->flags = desc->is_reference ? VA_PICTURE_H264_SHORT_TERM_REFERENCE : 0;
    if (desc->field_pic_flag)
        pic->flags |= (desc->bottom_field_flag ? VA_PICTURE_H264_BOTTOM_FIELD
                                               : VA_PICTURE_H264_TOP_FIELD);
    pic->TopFieldOrderCnt = desc->field_order_cnt[0];
    pic->BottomFieldOrderCnt = desc->field_order_cnt[1];


    /* ReferenceFrames */
    for (i = 0; i < ARRAY_SIZE(vapp->ReferenceFrames); i++)
        h264_init_picture(&vapp->ReferenceFrames[i]);

    for (i = 0; i < desc->num_ref_frames; i++) {
        pic = &vapp->ReferenceFrames[i];

        pic->picture_id = desc->buffer_id[i];
        pic->frame_idx  = desc->frame_num_list[i];
        pic->flags = (desc->is_long_term[i]
                      ? VA_PICTURE_H264_LONG_TERM_REFERENCE
                      : VA_PICTURE_H264_SHORT_TERM_REFERENCE);
        if (desc->top_is_reference[i] && desc->bottom_is_reference[i]) {
            // Full frame. This block intentionally left blank. No flags set.
        } else {
            if (desc->top_is_reference[i])
                pic->flags |= VA_PICTURE_H264_TOP_FIELD;
            else
                pic->flags |= VA_PICTURE_H264_BOTTOM_FIELD;
        }
        pic->TopFieldOrderCnt = desc->field_order_cnt_list[i][0];
        pic->BottomFieldOrderCnt = desc->field_order_cnt_list[i][1];
    }

    //vapp->picture_width_in_mbs_minus1  = (codec->width - 1) / 16;
    //vapp->picture_height_in_mbs_minus1 = (codec->height - 1) / 16;
    ITEM_SET(vapp, &desc->pps.sps, bit_depth_luma_minus8);
    ITEM_SET(vapp, &desc->pps.sps, bit_depth_chroma_minus8);
    ITEM_SET(vapp, desc, num_ref_frames);

    ITEM_SET(&vapp->seq_fields.bits, &desc->pps.sps, chroma_format_idc);
    //vapp->seq_fields.bits.residual_colour_transform_flag       = 0;
    //vapp->seq_fields.bits.gaps_in_frame_num_value_allowed_flag = 0;
    ITEM_SET(&vapp->seq_fields.bits, &desc->pps.sps, frame_mbs_only_flag);
    ITEM_SET(&vapp->seq_fields.bits,
             &desc->pps.sps, mb_adaptive_frame_field_flag);
    ITEM_SET(&vapp->seq_fields.bits, &desc->pps.sps, direct_8x8_inference_flag);
    ITEM_SET(&vapp->seq_fields.bits, &desc->pps.sps, MinLumaBiPredSize8x8);
    ITEM_SET(&vapp->seq_fields.bits, &desc->pps.sps, log2_max_frame_num_minus4);
    ITEM_SET(&vapp->seq_fields.bits, &desc->pps.sps, pic_order_cnt_type);
    ITEM_SET(&vapp->seq_fields.bits,
             &desc->pps.sps, log2_max_pic_order_cnt_lsb_minus4);
    ITEM_SET(&vapp->seq_fields.bits,
             &desc->pps.sps, delta_pic_order_always_zero_flag);

    //ITEM_SET(vapp, &desc->pps, num_slice_groups_minus1);
    //ITEM_SET(vapp, &desc->pps, slice_group_map_type);
    //ITEM_SET(vapp, &desc->pps, slice_group_change_rate_minus1);
    ITEM_SET(vapp, &desc->pps, pic_init_qp_minus26);
    ITEM_SET(vapp, &desc->pps, pic_init_qs_minus26);
    ITEM_SET(vapp, &desc->pps, chroma_qp_index_offset);
    ITEM_SET(vapp, &desc->pps, second_chroma_qp_index_offset);

    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, entropy_coding_mode_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, weighted_pred_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, weighted_bipred_idc);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, transform_8x8_mode_flag);
    ITEM_SET(&vapp->pic_fields.bits, desc,       field_pic_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, constrained_intra_pred_flag);
    vapp->pic_fields.bits.pic_order_present_flag =
             desc->pps.bottom_field_pic_order_in_frame_present_flag;
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps, deblocking_filter_control_present_flag);
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps, redundant_pic_cnt_present_flag);
    vapp->pic_fields.bits.reference_pic_flag = desc->is_reference;

    ITEM_SET(vapp, desc, frame_num);
}


 /* Refer to vlVaHandleIQMatrixBufferH264() in mesa */
static void h264_fill_iq_matrix(const struct virgl_h264_picture_desc *desc,
                                VAIQMatrixBufferH264 *vaiqm)
{
    ITEM_CPY(vaiqm, &desc->pps, ScalingList4x4);
    ITEM_CPY(vaiqm, &desc->pps, ScalingList8x8);
}

/*
 * Refer to vlVaHandleSliceParameterBufferH264() in mesa,
 * and comment out some unused parameters.
 */
static void h264_fill_slice_param(const struct virgl_h264_picture_desc *desc,
                                  VASliceParameterBufferH264 *vasp)
{
    //vasp->slice_data_size;
    //vasp->slice_data_offset;
    //vasp->slice_data_flag;
    //vasp->slice_data_bit_offset;
    //vasp->first_mb_in_slice;
    //vasp->slice_type;
    //vasp->direct_spatial_mv_pred_flag;
    ITEM_SET(vasp, desc, num_ref_idx_l0_active_minus1);
    ITEM_SET(vasp, desc, num_ref_idx_l1_active_minus1);
    //vasp->cabac_init_idc;
    //vasp->slice_qp_delta;
    //vasp->disable_deblocking_filter_idc;
    //vasp->slice_alpha_c0_offset_div2;
    //vasp->slice_beta_offset_div2;
    //vasp->RefPicList0[32];
    //vasp->RefPicList1[32];

    /* see pred_weight_table */
    //vasp->luma_log2_weight_denom;
    //vasp->chroma_log2_weight_denom;
    //vasp->luma_weight_l0_flag;
    //vasp->luma_weight_l0[32];
    //vasp->luma_offset_l0[32];
    //vasp->chroma_weight_l0_flag;
    //vasp->chroma_weight_l0[32][2];
    //vasp->chroma_offset_l0[32][2];
    //vasp->luma_weight_l1_flag;
    //vasp->luma_weight_l1[32];
    //vasp->luma_offset_l1[32];
    //vasp->chroma_weight_l1_flag;
    //vasp->chroma_weight_l1[32][2];
    //vasp->chroma_offset_l1[32][2];
}

/*
 * Refer to vlVaHandleVAEncPictureParameterBufferTypeH264() in mesa,
 * and comment out some unused parameters.
 */
static void h264_fill_enc_picture_param(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc,
                            VAEncPictureParameterBufferH264 *param)
{
    unsigned i;

    (void)codec;
    (void)source;

    /* CurrPic */
    param->CurrPic.picture_id = get_enc_ref_pic(codec, desc->frame_num);
    //CurrPic.frame_idx;
    //CurrPic.flags;
    param->CurrPic.TopFieldOrderCnt = desc->pic_order_cnt;
    //CurrPic.BottomFieldOrderCnt;

    /* ReferenceFrames */
    for (i = 0; i < ARRAY_SIZE(param->ReferenceFrames); i++)
        h264_init_picture(&param->ReferenceFrames[i]);

    /* coded_buf */
    param->coded_buf = codec->va_coded_buf;

    //pic_parameter_set_id;
    //seq_parameter_set_id;
    //last_picture;
    //frame_num
    param->pic_init_qp = desc->quant_i_frames;
    param->num_ref_idx_l0_active_minus1 = desc->num_ref_idx_l0_active_minus1;
    param->num_ref_idx_l1_active_minus1 = desc->num_ref_idx_l1_active_minus1;
    //chroma_qp_index_offset;
    //second_chroma_qp_index_offset;

    /* pic_fields */
    param->pic_fields.bits.idr_pic_flag =
                      (desc->picture_type == PIPE_H2645_ENC_PICTURE_TYPE_IDR);
    param->pic_fields.bits.reference_pic_flag = !desc->not_referenced;
    param->pic_fields.bits.entropy_coding_mode_flag = desc->pic_ctrl.enc_cabac_enable;
    //pic_fields.bits.weighted_pred_flag
    //pic_fields.bits.weighted_bipred_idc
    //pic_fields.bits.constrained_intra_pred_flag
    //pic_fields.bits.transform_8x8_mode_flag
    //pic_fields.bits.deblocking_filter_control_present_flag
    //pic_fields.bits.redundant_pic_cnt_present_flag
    //pic_fields.bits.pic_order_present_flag
    //pic_fields.bits.pic_scaling_matrix_present_flag

}

/*
 * Refer to vlVaHandleVAEncSliceParameterBufferTypeH264() in mesa,
 * and comment out some unused parameters.
 */
static void h264_fill_enc_slice_param(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc,
                            VAEncSliceParameterBufferH264 *param)
{
    unsigned i;
    const struct virgl_h264_slice_descriptor *sd;

    (void)codec;
    (void)source;

    /* Get the lastest slice descriptor */
    if (desc->num_slice_descriptors &&
        desc->num_slice_descriptors <= ARRAY_SIZE(desc->slices_descriptors)) {
        sd = &desc->slices_descriptors[desc->num_slice_descriptors - 1];
        param->macroblock_address = sd->macroblock_address;
        param->num_macroblocks    = sd->num_macroblocks;
        //macroblock_info;
    }

    switch (desc->picture_type) {
    case PIPE_H2645_ENC_PICTURE_TYPE_P:
        param->slice_type = 0;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_B:
        param->slice_type = 1;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_I:
    case PIPE_H2645_ENC_PICTURE_TYPE_IDR: /* fall through */
        param->slice_type = 2;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_SKIP:
    default:
        break;
    }

    //pic_parameter_set_id;
    //idr_pic_id;
    //pic_order_cnt_lsb;
    //delta_pic_order_cnt_bottom;
    //delta_pic_order_cnt[2];
    //direct_spatial_mv_pred_flag;

    /*
     * Sine num_ref_idx_l0_active_minus1 and num_ref_idx_l1_active_minus1
     * have been passed by VAEncPictureParameterBufferH264,
     * num_ref_idx_active_override_flag is always set to 0.
     */
    param->num_ref_idx_active_override_flag = 0;
    //num_ref_idx_l0_active_minus1
    //num_ref_idx_l1_active_minus1

    /* Reference List */
    for (i = 0; i < 32; i++) {
        h264_init_picture(&param->RefPicList0[i]);
        h264_init_picture(&param->RefPicList1[i]);

        param->RefPicList0[i].picture_id =
                    get_enc_ref_pic(codec, desc->ref_idx_l0_list[i]);
        param->RefPicList1[i].picture_id =
                    get_enc_ref_pic(codec, desc->ref_idx_l1_list[i]);

        if (param->RefPicList0[i].picture_id != VA_INVALID_ID)
            param->RefPicList0[i].flags = VA_PICTURE_H264_SHORT_TERM_REFERENCE;

        if (param->RefPicList1[i].picture_id != VA_INVALID_ID)
            param->RefPicList1[i].flags = VA_PICTURE_H264_SHORT_TERM_REFERENCE;
    }

    //luma_log2_weight_denom;
    //chroma_log2_weight_denom;
    //luma_weight_l0_flag;
    //luma_weight_l0[32];
    //luma_offset_l0[32];
    //chroma_weight_l0_flag;
    //chroma_weight_l0[32][2];
    //chroma_offset_l0[32][2];
    //luma_weight_l1_flag;
    //luma_weight_l1[32];
    //luma_offset_l1[32];
    //chroma_weight_l1_flag;
    //chroma_weight_l1[32][2];
    //chroma_offset_l1[32][2];
    param->cabac_init_idc = desc->pic_ctrl.enc_cabac_init_idc;
    //slice_qp_delta;
    //disable_deblocking_filter_idc;
    //slice_alpha_c0_offset_div2;
    //slice_beta_offset_div2;

}

/*
 * Refer to vlVaHandleVAEncSequenceParameterBufferTypeH264() in mesa,
 * and comment out some unused parameters.
 */
static void h264_fill_enc_seq_param(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc,
                            VAEncSequenceParameterBufferH264 *param)
{
    (void)codec;
    (void)source;

    //seq_parameter_set_id;
    param->level_idc = codec->level;
    //intra_period;
    param->intra_idr_period = desc->intra_idr_period;
    //ip_period;
    //bits_per_second;
    param->max_num_ref_frames = codec->max_references;
    //picture_width_in_mbs;
    //picture_height_in_mbs;

    /* seq_fields.bits */
    //seq_fields.bits.chroma_format_idc
    //seq_fields.bits.frame_mbs_only_flag
    //seq_fields.bits.mb_adaptive_frame_field_flag
    //seq_fields.bits.seq_scaling_matrix_present_flag
    //seq_fields.bits.direct_8x8_inference_flag
    //seq_fields.bits.log2_max_frame_num_minus4
    ITEM_SET(&param->seq_fields.bits, &desc->seq, pic_order_cnt_type);
    //seq_fields.bit.log2_max_pic_order_cnt_lsb_minus4
    //seq_fields.bit.delta_pic_order_always_zero_flag

    //bit_depth_luma_minus8;
    //bit_depth_chroma_minus8;

    //num_ref_frames_in_pic_order_cnt_cycle;
    //offset_for_non_ref_pic;
    //offset_for_top_to_bottom_field;
    //offset_for_ref_frame[256];
    if (desc->seq.enc_frame_cropping_flag) {
        param->frame_cropping_flag      = desc->seq.enc_frame_cropping_flag;
        param->frame_crop_left_offset   = desc->seq.enc_frame_crop_left_offset;
        param->frame_crop_right_offset  = desc->seq.enc_frame_crop_right_offset;
        param->frame_crop_top_offset    = desc->seq.enc_frame_crop_top_offset;
        param->frame_crop_bottom_offset = desc->seq.enc_frame_crop_bottom_offset;
    }

    ITEM_SET(param, &desc->seq, vui_parameters_present_flag);

    // vui_fields.bits
    if (desc->seq.vui_parameters_present_flag) {
        ITEM_SET(&param->vui_fields.bits, &desc->seq.vui_flags,
                 aspect_ratio_info_present_flag);
        ITEM_SET(&param->vui_fields.bits, &desc->seq.vui_flags,
                 timing_info_present_flag);
    }
    //vui_fields.bits.bitstream_restriction_flag
    //vui_fields.bits.log2_max_mv_length_horizontal
    //vui_fields.bits.log2_max_mv_length_vertical
    //vui_fields.bits.fixed_frame_rate_flag
    //vui_fields.bits.low_delay_hrd_flag
    //vui_fields.bits.motion_vectors_over_pic_boundaries_flag

    if (desc->seq.vui_parameters_present_flag) {
        ITEM_SET(param, &desc->seq, aspect_ratio_idc);
        ITEM_SET(param, &desc->seq, sar_width);
        ITEM_SET(param, &desc->seq, sar_height);
    }
    ITEM_SET(param, &desc->seq, num_units_in_tick);
    ITEM_SET(param, &desc->seq, time_scale);
}

/*
 * Refer to vlVaHandleVAEncMiscParameterTypeRateControlH264() in mesa,
 * and comment out some unused parameters.
 */
static void h264_fill_enc_misc_param_rate_ctrl(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc,
                            VAEncMiscParameterRateControl *param)
{
    unsigned temporal_id = 0; /* always 0 now */
    const struct virgl_h264_enc_rate_control *rc = &desc->rate_ctrl[temporal_id];

    (void)codec;
    (void)source;

    param->bits_per_second = rc->peak_bitrate;
    if (desc->rate_ctrl[0].rate_ctrl_method !=
        PIPE_H2645_ENC_RATE_CONTROL_METHOD_CONSTANT) {
        param->target_percentage = rc->target_bitrate *
                                   param->bits_per_second / 100.0;
    }
    //window_size;
    //initial_qp;
    param->min_qp = rc->min_qp;
    //basic_unit_size;

    /* rc_flags */
    //rc_flags.bits.reset
    param->rc_flags.bits.disable_frame_skip = !rc->skip_frame_enable;
    param->rc_flags.bits.disable_bit_stuffing = !rc->fill_data_enable;
    //rc_flags.bits.mb_rate_control
    param->rc_flags.bits.temporal_id = temporal_id;
    //rc_flags.bits.cfs_I_frames
    //rc_flags.bits.enable_parallel_brc
    //rc_flags.bits.enable_dynamic_scaling
    //rc_flags.bits.frame_tolerance_mode

    //ICQ_quality_factor;
    param->max_qp = rc->max_qp;
    //quality_factor;
    //target_frame_size;
}

/*
 * Refer to vlVaHandleVAEncMiscParameterTypeFrameRateH264() in mesa,
 * and comment out some unused parameters.
 */
static void h264_fill_enc_misc_param_frame_rate(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc,
                            VAEncMiscParameterFrameRate *param)
{
    unsigned temporal_id = 0; /* always 0 now */
    const struct virgl_h264_enc_rate_control *rc = &desc->rate_ctrl[temporal_id];

    (void)codec;
    (void)source;

    param->framerate = rc->frame_rate_num | (rc->frame_rate_den << 16);
    param->framerate_flags.bits.temporal_id = temporal_id;
}

static int h264_decode_bitstream(struct virgl_video_codec *codec,
                                 struct virgl_video_buffer *target,
                                 const struct virgl_h264_picture_desc *desc,
                                 unsigned num_buffers,
                                 const void * const *buffers,
                                 const unsigned *sizes)
{
    unsigned i;
    int err = 0;
    VAStatus va_stat;
    VABufferID *slice_data_buf, pic_param_buf, iq_matrix_buf, slice_param_buf;
    VAPictureParameterBufferH264 pic_param;
    VAIQMatrixBufferH264 iq_matrix;
    VASliceParameterBufferH264 slice_param;

    slice_data_buf = calloc(num_buffers, sizeof(VABufferID));
    if (!slice_data_buf) {
        virgl_error("alloc slice data buffer id failed\n");
        return -1;
    }

    h264_fill_picture_param(codec, target, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    h264_fill_iq_matrix(desc, &iq_matrix);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAIQMatrixBufferType,
                   sizeof(iq_matrix), 1, &iq_matrix, &iq_matrix_buf);

    h264_fill_slice_param(desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VASliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    for (i = 0; i < num_buffers; i++) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VASliceDataBufferType,
                      sizes[i], 1, (void *)(buffers[i]), &slice_data_buf[i]);
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render picture param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &iq_matrix_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render iq matrix failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    for (i = 0; i < num_buffers; i++) {
        va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_data_buf[i], 1);

        if (VA_STATUS_SUCCESS != va_stat) {
            virgl_error("render slice data failed, err = 0x%x\n", va_stat);
            err = -1;
        }
    }

err:
    vaDestroyBuffer(va_dpy, pic_param_buf);
    vaDestroyBuffer(va_dpy, iq_matrix_buf);
    vaDestroyBuffer(va_dpy, slice_param_buf);
    for (i = 0; i < num_buffers; i++)
        vaDestroyBuffer(va_dpy, slice_data_buf[i]);
    free(slice_data_buf);

    return err;
}

static int h264_encode_render_sequence(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc)
{
    int err = 0;
    VAStatus va_stat;
    VAEncSequenceParameterBufferH264 seq_param;
    VAEncMiscParameterBuffer *misc_param;
    VABufferID seq_param_buf, rc_param_buf, fr_param_buf;

    memset(&seq_param, 0, sizeof(seq_param));
    h264_fill_enc_seq_param(codec, source, desc, &seq_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncSequenceParameterBufferType,
                   sizeof(seq_param), 1, &seq_param, &seq_param_buf);

    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncMiscParameterBufferType,
                   sizeof(VAEncMiscParameterBuffer) +
                   sizeof(VAEncMiscParameterRateControl), 1, NULL, &rc_param_buf);
    vaMapBuffer(va_dpy, rc_param_buf, (void **)&misc_param);
    misc_param->type = VAEncMiscParameterTypeRateControl;
    h264_fill_enc_misc_param_rate_ctrl(codec, source, desc,
                    (VAEncMiscParameterRateControl *)misc_param->data);
    vaUnmapBuffer(va_dpy, rc_param_buf);

    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncMiscParameterBufferType,
                   sizeof(VAEncMiscParameterBuffer) +
                   sizeof(VAEncMiscParameterFrameRate), 1, NULL, &fr_param_buf);
    vaMapBuffer(va_dpy, fr_param_buf, (void **)&misc_param);
    misc_param->type = VAEncMiscParameterTypeFrameRate;
    h264_fill_enc_misc_param_frame_rate(codec, source, desc,
                    (VAEncMiscParameterFrameRate *)misc_param->data);
    vaUnmapBuffer(va_dpy, fr_param_buf);

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &seq_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h264 sequence param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto error;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &rc_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h264 rate control param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto error;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &fr_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h264 frame rate param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto error;
    }

error:
    vaDestroyBuffer(va_dpy, seq_param_buf);
    vaDestroyBuffer(va_dpy, rc_param_buf);
    vaDestroyBuffer(va_dpy, fr_param_buf);

    return err;
}

static int h264_encode_render_picture(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc)
{
    VAStatus va_stat;
    VABufferID pic_param_buf;
    VAEncPictureParameterBufferH264 pic_param;

    memset(&pic_param, 0, sizeof(pic_param));
    h264_fill_enc_picture_param(codec, source, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    vaDestroyBuffer(va_dpy, pic_param_buf);

    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h264 picture param failed, err = 0x%x\n", va_stat);
        return -1;
    }

    return 0;
}

static int h264_encode_render_slice(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc)
{
    VAStatus va_stat;
    VABufferID slice_param_buf;
    VAEncSliceParameterBufferH264 slice_param;

    memset(&slice_param, 0, sizeof(slice_param));
    h264_fill_enc_slice_param(codec, source, desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncSliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    vaDestroyBuffer(va_dpy, slice_param_buf);

    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h264 slice param failed, err = 0x%x\n", va_stat);
        return -1;
    }

    return 0;
}

static int h264_encode_bitstream(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h264_enc_picture_desc *desc)
{
    if (desc->picture_type == PIPE_H2645_ENC_PICTURE_TYPE_IDR) {
        h264_encode_render_sequence(codec, source, desc);
    }

    h264_encode_render_picture(codec, source, desc);
    h264_encode_render_slice(codec, source, desc);

    return 0;
}

static void h265_init_picture(VAPictureHEVC *pic)
{
    pic->picture_id     = VA_INVALID_SURFACE;
    pic->pic_order_cnt  = 0;
    pic->flags          = VA_PICTURE_HEVC_INVALID;
}

/*
 * Refer to vlVaHandlePictureParameterBufferHEVC() in mesa,
 * and comment out some unused parameters.
 */
static void h265_fill_picture_param(struct virgl_video_codec *codec,
                            struct virgl_video_buffer *target,
                            const struct virgl_h265_picture_desc *desc,
                            VAPictureParameterBufferHEVC *vapp)
{
    unsigned i;

    (void)codec;
    (void)target;

    //vapp->CurrPic.picture_id
    vapp->CurrPic.pic_order_cnt = desc->CurrPicOrderCntVal;
    //vapp->CurrPic.flags

    for (i = 0; i < 15; i++) {
        vapp->ReferenceFrames[i].pic_order_cnt = desc->PicOrderCntVal[i];
        vapp->ReferenceFrames[i].picture_id = desc->ref[i];
        vapp->ReferenceFrames[i].flags = VA_INVALID_SURFACE == desc->ref[i]
                                       ? VA_PICTURE_HEVC_INVALID : 0;
    }
    for (i = 0; i < desc->NumPocStCurrBefore; i++)
        vapp->ReferenceFrames[desc->RefPicSetStCurrBefore[i]].flags |= \
                VA_PICTURE_HEVC_RPS_ST_CURR_BEFORE;
    for (i = 0; i < desc->NumPocStCurrAfter; i++)
        vapp->ReferenceFrames[desc->RefPicSetStCurrAfter[i]].flags |= \
                VA_PICTURE_HEVC_RPS_ST_CURR_AFTER;
    for (i = 0; i < desc->NumPocLtCurr; i++)
        vapp->ReferenceFrames[desc->RefPicSetLtCurr[i]].flags |= \
                VA_PICTURE_HEVC_RPS_LT_CURR;

    ITEM_SET(vapp, &desc->pps.sps, pic_width_in_luma_samples);
    ITEM_SET(vapp, &desc->pps.sps, pic_height_in_luma_samples);

    ITEM_SET(&vapp->pic_fields.bits, &desc->pps.sps, chroma_format_idc);
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps.sps, separate_colour_plane_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps.sps, pcm_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps.sps, scaling_list_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps, transform_skip_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps.sps, amp_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps.sps, strong_intra_smoothing_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, sign_data_hiding_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, constrained_intra_pred_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, cu_qp_delta_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, weighted_pred_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, weighted_bipred_flag);
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps, transquant_bypass_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps, tiles_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits,
             &desc->pps, entropy_coding_sync_enabled_flag);
    ITEM_SET(&vapp->pic_fields.bits, &desc->pps,
             pps_loop_filter_across_slices_enabled_flag);
    if (desc->pps.tiles_enabled_flag)
        ITEM_SET(&vapp->pic_fields.bits,
                 &desc->pps, loop_filter_across_tiles_enabled_flag);
    if (desc->pps.sps.pcm_enabled_flag)
        ITEM_SET(&vapp->pic_fields.bits,
                 &desc->pps.sps, pcm_loop_filter_disabled_flag);
    //ITEM_SET(vapp->pic_fields.bits, desc->pps.sps, NoPicReorderingFlag);
    //ITEM_SET(vapp->pic_fields.bits, desc->pps.sps, NoBiPredFlag);

    ITEM_SET(vapp, &desc->pps.sps, sps_max_dec_pic_buffering_minus1);
    ITEM_SET(vapp, &desc->pps.sps, bit_depth_luma_minus8);
    ITEM_SET(vapp, &desc->pps.sps, bit_depth_chroma_minus8);
    if (desc->pps.sps.pcm_enabled_flag) {
        ITEM_SET(vapp, &desc->pps.sps, pcm_sample_bit_depth_luma_minus1);
        ITEM_SET(vapp, &desc->pps.sps, pcm_sample_bit_depth_chroma_minus1);
    }
    ITEM_SET(vapp, &desc->pps.sps, log2_min_luma_coding_block_size_minus3);
    ITEM_SET(vapp, &desc->pps.sps, log2_diff_max_min_luma_coding_block_size);
    ITEM_SET(vapp, &desc->pps.sps, log2_min_transform_block_size_minus2);
    ITEM_SET(vapp, &desc->pps.sps, log2_diff_max_min_transform_block_size);
    if (desc->pps.sps.pcm_enabled_flag) {
        ITEM_SET(vapp, &desc->pps.sps,
                 log2_min_pcm_luma_coding_block_size_minus3);
        ITEM_SET(vapp, &desc->pps.sps,
                 log2_diff_max_min_pcm_luma_coding_block_size);
    }
    ITEM_SET(vapp, &desc->pps.sps, max_transform_hierarchy_depth_intra);
    ITEM_SET(vapp, &desc->pps.sps, max_transform_hierarchy_depth_inter);
    ITEM_SET(vapp, &desc->pps, init_qp_minus26);
    ITEM_SET(vapp, &desc->pps, diff_cu_qp_delta_depth);
    ITEM_SET(vapp, &desc->pps, pps_cb_qp_offset);
    ITEM_SET(vapp, &desc->pps, pps_cr_qp_offset);
    ITEM_SET(vapp, &desc->pps, log2_parallel_merge_level_minus2);
    if (desc->pps.tiles_enabled_flag) {
        ITEM_SET(vapp, &desc->pps, num_tile_columns_minus1);
        ITEM_SET(vapp, &desc->pps, num_tile_rows_minus1);
        ITEM_CPY(vapp, &desc->pps, column_width_minus1);
        ITEM_CPY(vapp, &desc->pps, row_height_minus1);
    }

    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps, lists_modification_present_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps.sps, long_term_ref_pics_present_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps.sps, sps_temporal_mvp_enabled_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps, cabac_init_present_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps, output_flag_present_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps, dependent_slice_segments_enabled_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps, pps_slice_chroma_qp_offsets_present_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps.sps, sample_adaptive_offset_enabled_flag);
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps, deblocking_filter_override_enabled_flag);
    vapp->slice_parsing_fields.bits.pps_disable_deblocking_filter_flag = \
             desc->pps.pps_deblocking_filter_disabled_flag;
    ITEM_SET(&vapp->slice_parsing_fields.bits,
             &desc->pps, slice_segment_header_extension_present_flag);
    vapp->slice_parsing_fields.bits.RapPicFlag = desc->RAPPicFlag;
    vapp->slice_parsing_fields.bits.IdrPicFlag = desc->IDRPicFlag;
    //vapp->slice_parsing_fields.bits.IntraPicFlag

    ITEM_SET(vapp, &desc->pps.sps, log2_max_pic_order_cnt_lsb_minus4);
    ITEM_SET(vapp, &desc->pps.sps, num_short_term_ref_pic_sets);
    vapp->num_long_term_ref_pic_sps = desc->pps.sps.num_long_term_ref_pics_sps;
    ITEM_SET(vapp, &desc->pps, num_ref_idx_l0_default_active_minus1);
    ITEM_SET(vapp, &desc->pps, num_ref_idx_l1_default_active_minus1);
    ITEM_SET(vapp, &desc->pps, pps_beta_offset_div2);
    ITEM_SET(vapp, &desc->pps, pps_tc_offset_div2);
    ITEM_SET(vapp, &desc->pps, num_extra_slice_header_bits);

    ITEM_SET(vapp, &desc->pps, st_rps_bits);
}

/*
 * Refer to vlVaHandleSliceParameterBufferHEVC() in mesa,
 * and comment out some unused parameters.
 */
static void h265_fill_slice_param(const struct virgl_h265_picture_desc *desc,
                                  VASliceParameterBufferHEVC *vapp)
{
    unsigned i, j;

    //slice_data_size;
    //slice_data_offset;
    //slice_data_flag;
    //slice_data_byte_offset;
    //slice_segment_address;
    for (i = 0; i < 2; i++) {
        for (j = 0; j < 15; j++)
            vapp->RefPicList[i][j] = desc->RefPicList[i][j];
    }
    //LongSliceFlags;
    //collocated_ref_idx;
    //num_ref_idx_l0_active_minus1;
    //num_ref_idx_l1_active_minus1;
    //slice_qp_delta;
    //slice_cb_qp_offset;
    //slice_cr_qp_offset;
    //slice_beta_offset_div2;
    //slice_tc_offset_div2;
    //luma_log2_weight_denom;
    //delta_chroma_log2_weight_denom;
    //delta_luma_weight_l0[15];
    //luma_offset_l0[15];
    //delta_chroma_weight_l0[15][2];
    //ChromaOffsetL0[15][2];
    //delta_luma_weight_l1[15];
    //luma_offset_l1[15];
    //delta_chroma_weight_l1[15][2];
    //ChromaOffsetL1[15][2];
    //five_minus_max_num_merge_cand;
    //num_entry_point_offsets;
    //entry_offset_to_subset_array;
    //slice_data_num_emu_prevn_bytes;
    //va_reserved[VA_PADDING_LOW - 2];
}

/*
 * Refer to vlVaHandleVAEncSequenceParameterBufferTypeHEVC() in mesa,
 * and comment out some unused parameters.
 */
static void h265_fill_enc_seq_param(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc,
                            VAEncSequenceParameterBufferHEVC *param)
{
    (void)codec;
    (void)source;

    ITEM_SET(param, &desc->seq, general_profile_idc);
    ITEM_SET(param, &desc->seq, general_level_idc);
    ITEM_SET(param, &desc->seq, general_tier_flag);
    ITEM_SET(param, &desc->seq, intra_period);
    //intra_idr_period
    ITEM_SET(param, &desc->seq, ip_period);
    //bits_per_second
    ITEM_SET(param, &desc->seq, pic_width_in_luma_samples);
    ITEM_SET(param, &desc->seq, pic_height_in_luma_samples);

    /* seq_fields.bits */
    ITEM_SET(&param->seq_fields.bits, &desc->seq, chroma_format_idc);
    //seq_fields.bits.separate_colour_plane_flag
    ITEM_SET(&param->seq_fields.bits, &desc->seq, bit_depth_luma_minus8);
    ITEM_SET(&param->seq_fields.bits, &desc->seq, bit_depth_chroma_minus8);
    //seq_fields.bits.scaling_list_enabled_flag
    ITEM_SET(&param->seq_fields.bits, &desc->seq, strong_intra_smoothing_enabled_flag);
    ITEM_SET(&param->seq_fields.bits, &desc->seq, amp_enabled_flag);
    ITEM_SET(&param->seq_fields.bits, &desc->seq, sample_adaptive_offset_enabled_flag);
    ITEM_SET(&param->seq_fields.bits, &desc->seq, pcm_enabled_flag);
    //seq_fields.bits.pcm_loop_filter_disabled_flag
    ITEM_SET(&param->seq_fields.bits, &desc->seq, sps_temporal_mvp_enabled_flag);
    //seq_fields.bits.low_delay_seq
    //seq_fields.bits.hierachical_flag
    //seq_fields.bits.reserved_bits

    ITEM_SET(param, &desc->seq, log2_min_luma_coding_block_size_minus3);
    ITEM_SET(param, &desc->seq, log2_diff_max_min_luma_coding_block_size);
    ITEM_SET(param, &desc->seq, log2_min_transform_block_size_minus2);
    ITEM_SET(param, &desc->seq, log2_diff_max_min_transform_block_size);
    ITEM_SET(param, &desc->seq, max_transform_hierarchy_depth_inter);
    ITEM_SET(param, &desc->seq, max_transform_hierarchy_depth_intra);
    //pcm_sample_bit_depth_luma_minus1
    //pcm_sample_bit_depth_chroma_minus1
    //log2_min_pcm_luma_coding_block_size_minus3
    //log2_max_pcm_luma_coding_block_size_minus3
    ITEM_SET(param, &desc->seq, vui_parameters_present_flag);

    /* vui_fields.bits */
    if (desc->seq.vui_parameters_present_flag) {
        ITEM_SET(&param->vui_fields.bits, &desc->seq.vui_flags,
                 aspect_ratio_info_present_flag);
    }
    //vui_fields.bits.neutral_chroma_indication_flag
    //vui_fields.bits.field_seq_flag
    if (desc->seq.vui_parameters_present_flag) {
        param->vui_fields.bits.vui_timing_info_present_flag =
            desc->seq.vui_flags.timing_info_present_flag;
    }
    //vui_fields.bits.bitstream_restriction_flag
    //vui_fields.bits.tiles_fixed_structure_flag
    //vui_fields.bits.motion_vectors_over_pic_boundaries_flag
    //vui_fields.bits.restricted_ref_pic_lists_flag
    //vui_fields.bits.log2_max_mv_length_horizontal
    //vui_fields.bits.log2_max_mv_length_vertical

    if (desc->seq.vui_parameters_present_flag) {
        ITEM_SET(param, &desc->seq, aspect_ratio_idc);
        ITEM_SET(param, &desc->seq, sar_width);
        ITEM_SET(param, &desc->seq, sar_height);
    }
    param->vui_num_units_in_tick = desc->seq.num_units_in_tick;
    param->vui_time_scale = desc->seq.time_scale;
    //min_spatial_segmentation_idc
    //max_bytes_per_pic_denom
    //max_bits_per_min_cu_denom

    //scc_fields.bits.palette_mode_enabled_flag
}

/*
 * Refer to vlVaHandleVAEncPictureParameterBufferTypeHEVC() in mesa,
 * and comment out some unused parameters.
 */
static void h265_fill_enc_picture_param(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc,
                            VAEncPictureParameterBufferHEVC *param)
{
    unsigned i;

    (void)source;

    param->decoded_curr_pic.picture_id = get_enc_ref_pic(codec, desc->frame_num);
    param->decoded_curr_pic.pic_order_cnt = desc->pic_order_cnt;

    for (i = 0; i < 15; i++) {
        h265_init_picture(&param->reference_frames[i]);
    }

    param->coded_buf = codec->va_coded_buf;
    //collocated_ref_pic_index
    //last_picture
    param->pic_init_qp = desc->rc.quant_i_frames;
    //diff_cu_qp_delta_depth
    //pps_cb_qp_offset
    //pps_cr_qp_offset
    //num_tile_columns_minus1
    //num_tile_rows_minus1
    //column_width_minus1[19]
    //row_height_minus1[21]
    ITEM_SET(param, &desc->pic, log2_parallel_merge_level_minus2);
    //ctu_max_bitsize_allowed
    param->num_ref_idx_l0_default_active_minus1 = desc->num_ref_idx_l0_active_minus1;
    param->num_ref_idx_l1_default_active_minus1 = desc->num_ref_idx_l1_active_minus1;
    //slice_pic_parameter_set_id
    ITEM_SET(param, &desc->pic, nal_unit_type);

    param->pic_fields.bits.idr_pic_flag =
        (desc->picture_type == PIPE_H2645_ENC_PICTURE_TYPE_IDR);
    switch (desc->picture_type) {
    case PIPE_H2645_ENC_PICTURE_TYPE_IDR: /* fallthrough */
    case PIPE_H2645_ENC_PICTURE_TYPE_I:
        param->pic_fields.bits.coding_type = 1;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_P:
        param->pic_fields.bits.coding_type = 2;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_B:
        param->pic_fields.bits.coding_type = 3;
        break;
    default:
        break;
    }

    param->pic_fields.bits.reference_pic_flag = !desc->not_referenced;
    //pic_fields.bits.dependent_slice_segments_enabled_flag
    //pic_fields.bits.sign_data_hiding_enabled_flag
    ITEM_SET(&param->pic_fields.bits, &desc->pic, constrained_intra_pred_flag);
    ITEM_SET(&param->pic_fields.bits, &desc->pic, transform_skip_enabled_flag);
    //pic_fields.bits.cu_qp_delta_enabled_flag
    //pic_fields.bits.weighted_pred_flag
    //pic_fields.bits.weighted_bipred_flag
    //pic_fields.bits.transquant_bypass_enabled_flag
    //pic_fields.bits.tiles_enabled_flag
    //pic_fields.bits.entropy_coding_sync_enabled_flag
    //pic_fields.bits.loop_filter_across_tiles_enabled_flag
    ITEM_SET(&param->pic_fields.bits, &desc->pic,
             pps_loop_filter_across_slices_enabled_flag);
    //pic_fields.bits.scaling_list_data_present_flag
    //pic_fields.bits.screen_content_flag
    //pic_fields.bits.enable_gpu_weighted_prediction
    //pic_fields.bits.no_output_of_prior_pics_flag

    //hierarchical_level_plus1
    //scc_fields.bits.pps_curr_pic_ref_enabled_flag
}

/*
 * Refer to vlVaHandleVAEncSliceParameterBufferTypeHEVC() in mesa,
 * and comment out some unused parameters.
 */
static void h265_fill_enc_slice_param(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc,
                            VAEncSliceParameterBufferHEVC *param)
{
    unsigned i;
    const struct virgl_h265_slice_descriptor *sd;

    (void)source;

    /* Get the lastest slice descriptor */
    if (desc->num_slice_descriptors &&
        desc->num_slice_descriptors <= ARRAY_SIZE(desc->slices_descriptors)) {
        sd = &desc->slices_descriptors[desc->num_slice_descriptors - 1];
        ITEM_SET(param, sd, slice_segment_address);
        ITEM_SET(param, sd, num_ctu_in_slice);
    }

    switch (desc->picture_type) {
    case PIPE_H2645_ENC_PICTURE_TYPE_P:
        param->slice_type = 0;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_B:
        param->slice_type = 1;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_I:
    case PIPE_H2645_ENC_PICTURE_TYPE_IDR: /* fall through */
        param->slice_type = 2;
        break;
    case PIPE_H2645_ENC_PICTURE_TYPE_SKIP:
    default:
        break;
    }

    //slice_pic_parameter_set_id

    //num_ref_idx_l0_active_minus1
    //num_ref_idx_l1_active_minus1

    for (i = 0; i < 15; i++) {
        h265_init_picture(&param->ref_pic_list0[i]);
        h265_init_picture(&param->ref_pic_list1[i]);

        param->ref_pic_list0[i].picture_id =
                    get_enc_ref_pic(codec, desc->ref_idx_l0_list[i]);
        param->ref_pic_list1[i].picture_id =
                    get_enc_ref_pic(codec, desc->ref_idx_l1_list[i]);

        if (param->ref_pic_list0[i].picture_id != VA_INVALID_ID)
            param->ref_pic_list0[i].flags = VA_PICTURE_HEVC_RPS_ST_CURR_BEFORE;

        if (param->ref_pic_list1[i].picture_id != VA_INVALID_ID)
            param->ref_pic_list1[i].flags = VA_PICTURE_HEVC_RPS_ST_CURR_BEFORE;
    }

    //luma_log2_weight_denom
    //delta_chroma_log2_weight_denom
    //delta_luma_weight_l0[15]
    //luma_offset_l0[15]
    //delta_chroma_weight_l0[15][2]
    //chroma_offset_l0[15][2]
    //delta_luma_weight_l1[15]
    //luma_offset_l1[15]
    //delta_chroma_weight_l1[15][2]
    //chroma_offset_l1[15][2]
    ITEM_SET(param, &desc->slice, max_num_merge_cand);
    //slice_qp_delta
    ITEM_SET(param, &desc->slice, slice_cb_qp_offset);
    ITEM_SET(param, &desc->slice, slice_cr_qp_offset);
    ITEM_SET(param, &desc->slice, slice_beta_offset_div2);
    ITEM_SET(param, &desc->slice, slice_tc_offset_div2);

    //slice_fields.bits.last_slice_of_pic_flag
    //slice_fields.bits.dependent_slice_segment_flag
    //slice_fields.bits.colour_plane_id
    //slice_fields.bits.slice_temporal_mvp_enabled_flag
    //slice_fields.bits.slice_sao_luma_flag
    //slice_fields.bits.slice_sao_chroma_flag
    /*
     * Sine num_ref_idx_l0_active_minus1 and num_ref_idx_l1_active_minus1
     * have been passed by VAEncPictureParameterBufferHEVC,
     * num_ref_idx_active_override_flag is always set to 0.
     */
    param->slice_fields.bits.num_ref_idx_active_override_flag = 0;
    //slice_fields.bits.mvd_l1_zero_flag
    ITEM_SET(&param->slice_fields.bits, &desc->slice, cabac_init_flag);
    ITEM_SET(&param->slice_fields.bits, &desc->slice,
             slice_deblocking_filter_disabled_flag);
    ITEM_SET(&param->slice_fields.bits,
             &desc->slice, slice_loop_filter_across_slices_enabled_flag);
    //slice_fields.bits.collocated_from_l0_flag

    //pred_weight_table_bit_offset
    //pred_weight_table_bit_length;
}

/*
 * Refer to vlVaHandleVAEncMiscParameterTypeRateControlHEVC() in mesa,
 * and comment out some unused parameters.
 */
static void h265_fill_enc_misc_param_rate_ctrl(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc,
                            VAEncMiscParameterRateControl *param)
{
    (void)codec;
    (void)source;

    param->bits_per_second = desc->rc.peak_bitrate;
    if (desc->rc.rate_ctrl_method !=
        PIPE_H2645_ENC_RATE_CONTROL_METHOD_CONSTANT) {
        param->target_percentage = desc->rc.target_bitrate *
                                   param->bits_per_second / 100.0;
    }
    //window_size;
    //initial_qp;
    param->min_qp = desc->rc.min_qp;
    //basic_unit_size;

    /* rc_flags */
    //rc_flags.bits.reset
    param->rc_flags.bits.disable_frame_skip = !desc->rc.skip_frame_enable;
    param->rc_flags.bits.disable_bit_stuffing = !desc->rc.fill_data_enable;
    //rc_flags.bits.mb_rate_control
    //rc_flags.bits.temporal_id
    //rc_flags.bits.cfs_I_frames
    //rc_flags.bits.enable_parallel_brc
    //rc_flags.bits.enable_dynamic_scaling
    //rc_flags.bits.frame_tolerance_mode

    //ICQ_quality_factor;
    param->max_qp = desc->rc.max_qp;
    //quality_factor;
    //target_frame_size;
}

/*
 * Refer to vlVaHandleVAEncMiscParameterTypeFrameRateHEVC() in mesa,
 * and comment out some unused parameters.
 */
static void h265_fill_enc_misc_param_frame_rate(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc,
                            VAEncMiscParameterFrameRate *param)
{
    (void)codec;
    (void)source;

    param->framerate = desc->rc.frame_rate_num | (desc->rc.frame_rate_den << 16);
    //framerate_flags
}

static int h265_decode_bitstream(struct virgl_video_codec *codec,
                                 struct virgl_video_buffer *target,
                                 const struct virgl_h265_picture_desc *desc,
                                 unsigned num_buffers,
                                 const void * const *buffers,
                                 const unsigned *sizes)
{
    unsigned i;
    int err = 0;
    VAStatus va_stat;
    VABufferID *slice_data_buf, pic_param_buf, slice_param_buf;
    VAPictureParameterBufferHEVC pic_param = {0};
    VASliceParameterBufferHEVC slice_param = {0};

    slice_data_buf = calloc(num_buffers, sizeof(VABufferID));
    if (!slice_data_buf) {
        virgl_error("alloc slice data buffer id failed\n");
        return -1;
    }

    h265_fill_picture_param(codec, target, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    h265_fill_slice_param(desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VASliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    for (i = 0; i < num_buffers; i++) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VASliceDataBufferType,
                      sizes[i], 1, (void *)(buffers[i]), &slice_data_buf[i]);
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render picture param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    for (i = 0; i < num_buffers; i++) {
        va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_data_buf[i], 1);

        if (VA_STATUS_SUCCESS != va_stat) {
            virgl_error("render slice data failed, err = 0x%x\n", va_stat);
            err = -1;
        }
    }

err:
    vaDestroyBuffer(va_dpy, pic_param_buf);
    vaDestroyBuffer(va_dpy, slice_param_buf);
    for (i = 0; i < num_buffers; i++)
        vaDestroyBuffer(va_dpy, slice_data_buf[i]);
    free(slice_data_buf);

    return err;
}

static int h265_encode_render_sequence(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc)
{
    int err = 0;
    VAStatus va_stat;
    VAEncSequenceParameterBufferHEVC seq_param;
    VAEncMiscParameterBuffer *misc_param;
    VABufferID seq_param_buf, rc_param_buf, fr_param_buf;

    memset(&seq_param, 0, sizeof(seq_param));
    h265_fill_enc_seq_param(codec, source, desc, &seq_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncSequenceParameterBufferType,
                   sizeof(seq_param), 1, &seq_param, &seq_param_buf);

    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncMiscParameterBufferType,
                   sizeof(VAEncMiscParameterBuffer) +
                   sizeof(VAEncMiscParameterRateControl), 1, NULL, &rc_param_buf);
    vaMapBuffer(va_dpy, rc_param_buf, (void **)&misc_param);
    misc_param->type = VAEncMiscParameterTypeRateControl;
    h265_fill_enc_misc_param_rate_ctrl(codec, source, desc,
                    (VAEncMiscParameterRateControl *)misc_param->data);
    vaUnmapBuffer(va_dpy, rc_param_buf);

    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncMiscParameterBufferType,
                   sizeof(VAEncMiscParameterBuffer) +
                   sizeof(VAEncMiscParameterFrameRate), 1, NULL, &fr_param_buf);
    vaMapBuffer(va_dpy, fr_param_buf, (void **)&misc_param);
    misc_param->type = VAEncMiscParameterTypeFrameRate;
    h265_fill_enc_misc_param_frame_rate(codec, source, desc,
                    (VAEncMiscParameterFrameRate *)misc_param->data);
    vaUnmapBuffer(va_dpy, fr_param_buf);

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &seq_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h265 sequence param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto error;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &rc_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h265 rate control param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto error;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &fr_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h265 frame rate param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto error;
    }

error:
    vaDestroyBuffer(va_dpy, seq_param_buf);
    vaDestroyBuffer(va_dpy, rc_param_buf);
    vaDestroyBuffer(va_dpy, fr_param_buf);

    return err;
}

static int h265_encode_render_picture(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc)
{
    VAStatus va_stat;
    VABufferID pic_param_buf;
    VAEncPictureParameterBufferHEVC pic_param;

    memset(&pic_param, 0, sizeof(pic_param));
    h265_fill_enc_picture_param(codec, source, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    vaDestroyBuffer(va_dpy, pic_param_buf);

    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h265 picture param failed, err = 0x%x\n", va_stat);
        return -1;
    }

    return 0;
}

static int h265_encode_render_slice(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc)
{
    VAStatus va_stat;
    VABufferID slice_param_buf;
    VAEncSliceParameterBufferHEVC slice_param;

    memset(&slice_param, 0, sizeof(slice_param));
    h265_fill_enc_slice_param(codec, source, desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAEncSliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    vaDestroyBuffer(va_dpy, slice_param_buf);

    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render h265 slice param failed, err = 0x%x\n", va_stat);
        return -1;
    }

    return 0;
}

static int h265_encode_bitstream(
                            struct virgl_video_codec *codec,
                            struct virgl_video_buffer *source,
                            const struct virgl_h265_enc_picture_desc *desc)
{
    if (desc->picture_type == PIPE_H2645_ENC_PICTURE_TYPE_IDR) {
        h265_encode_render_sequence(codec, source, desc);
    }

    h265_encode_render_picture(codec, source, desc);
    h265_encode_render_slice(codec, source, desc);

    return 0;
}


static void mpeg12_fill_picture_param(struct virgl_video_codec *codec,
                                      struct virgl_video_buffer *target,
                                      const struct virgl_mpeg12_picture_desc *desc,
                                      VAPictureParameterBufferMPEG2 *vapp)
{
    (void)codec;
    (void)target;
    vapp->forward_reference_picture = desc->ref[0];
    vapp->backward_reference_picture = desc->ref[1];
    vapp->f_code = (desc->f_code[0][0] + 1) <<12;
    vapp->f_code |= (desc->f_code[0][1] + 1) <<8;
    vapp->f_code |= (desc->f_code[1][0] + 1) <<4;
    vapp->f_code |= (desc->f_code[1][1] + 1) <<0;
    ITEM_SET(vapp, desc, picture_coding_type);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, intra_dc_precision);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, picture_structure);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, top_field_first);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, frame_pred_frame_dct);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, concealment_motion_vectors);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, q_scale_type);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, intra_vlc_format);
    ITEM_SET(&vapp->picture_coding_extension.bits, desc, alternate_scan);
}

static void mpeg12_fill_slice_param(const struct virgl_mpeg12_picture_desc *desc,
                                    VASliceParameterBufferMPEG2 *vasp)
{
    (void)desc;
    (void)vasp;
}

static int mpeg12_decode_bitstream(struct virgl_video_codec *codec,
                                   struct virgl_video_buffer *target,
                                   const struct virgl_mpeg12_picture_desc *desc,
                                   unsigned num_buffers,
                                   const void * const *buffers,
                                   const unsigned *sizes)
{
    unsigned i;
    int err = 0;
    VAStatus va_stat;
    VABufferID *slice_data_buf, pic_param_buf, slice_param_buf;
    VAPictureParameterBufferMPEG2 pic_param = {0};
    VASliceParameterBufferMPEG2 slice_param = {0};

    slice_data_buf = calloc(num_buffers, sizeof(VABufferID));
    if (!slice_data_buf) {
        virgl_error("alloc slice data buffer id failed\n");
        return -1;
    }

    mpeg12_fill_picture_param(codec, target, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    mpeg12_fill_slice_param(desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VASliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    for (i = 0; i < num_buffers; i++) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VASliceDataBufferType,
                       sizes[i], 1, (void *)(buffers[i]), &slice_data_buf[i]);
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    for (i = 0; i < num_buffers; i++) {
        va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_data_buf[i], 1);

        if (VA_STATUS_SUCCESS != va_stat) {
            virgl_error("render slice data failed, err = 0x%x\n", va_stat);
            err = -1;
        }
    }

err:
    vaDestroyBuffer(va_dpy, pic_param_buf);
    vaDestroyBuffer(va_dpy, slice_param_buf);
    for (i = 0; i < num_buffers; i++)
        vaDestroyBuffer(va_dpy, slice_data_buf[i]);
    free(slice_data_buf);

    return err;
}

static void mjpeg_fill_picture_param(struct virgl_video_codec *codec,
                                     struct virgl_video_buffer *target,
                                     const struct virgl_mjpeg_picture_desc *desc,
                                     VAPictureParameterBufferJPEGBaseline *vapp)
{
    int i;
    (void)codec;
    (void)target;

    ITEM_SET(vapp, &desc->picture_parameter, picture_width);
    ITEM_SET(vapp, &desc->picture_parameter, picture_height);

    for (i = 0; i < desc->picture_parameter.num_components; ++i) {
        ITEM_SET(&vapp->components[i], &desc->picture_parameter.components[i], component_id);
        ITEM_SET(&vapp->components[i], &desc->picture_parameter.components[i], h_sampling_factor);
        ITEM_SET(&vapp->components[i], &desc->picture_parameter.components[i], v_sampling_factor);
        ITEM_SET(&vapp->components[i], &desc->picture_parameter.components[i], quantiser_table_selector);
    }

    ITEM_SET(vapp, &desc->picture_parameter, num_components);
}

static void mjpeg_fill_slice_param(const struct virgl_mjpeg_picture_desc *desc,
                                   VASliceParameterBufferJPEGBaseline *vasp)
{
    int i;

    ITEM_SET(vasp, &desc->slice_parameter, slice_data_size);
    ITEM_SET(vasp, &desc->slice_parameter, slice_data_offset);
    ITEM_SET(vasp, &desc->slice_parameter, slice_data_flag);
    ITEM_SET(vasp, &desc->slice_parameter, slice_horizontal_position);
    ITEM_SET(vasp, &desc->slice_parameter, slice_vertical_position);

    for (i = 0; i < desc->slice_parameter.num_components; ++i) {
        ITEM_SET(&vasp->components[i], &desc->slice_parameter.components[i], component_selector);
        ITEM_SET(&vasp->components[i], &desc->slice_parameter.components[i], dc_table_selector);
        ITEM_SET(&vasp->components[i], &desc->slice_parameter.components[i], ac_table_selector);
    }

    ITEM_SET(vasp, &desc->slice_parameter, num_components);
    ITEM_SET(vasp, &desc->slice_parameter, restart_interval);
    ITEM_SET(vasp, &desc->slice_parameter, num_mcus);
}

static void mjpeg_fill_iq_matrix(const struct virgl_mjpeg_picture_desc *desc,
                                VAIQMatrixBufferJPEGBaseline *vaiqm)
{
    ITEM_CPY(vaiqm, &desc->quantization_table, load_quantiser_table);
    ITEM_CPY(vaiqm, &desc->quantization_table, quantiser_table);
}

static void mjpeg_fill_huffman_table(const struct virgl_mjpeg_picture_desc *desc,
                                    VAHuffmanTableBufferJPEGBaseline *vahftb)
{
    int i;
    for (i = 0; i < 2; ++i) {
        ITEM_SET(vahftb, &desc->huffman_table, load_huffman_table[i]);
        ITEM_CPY(&vahftb->huffman_table[i], &desc->huffman_table.table[i], num_dc_codes);
        ITEM_CPY(&vahftb->huffman_table[i], &desc->huffman_table.table[i], dc_values);
        ITEM_CPY(&vahftb->huffman_table[i], &desc->huffman_table.table[i], num_ac_codes);
        ITEM_CPY(&vahftb->huffman_table[i], &desc->huffman_table.table[i], ac_values);
    }

}

static int mjpeg_decode_bitstream(struct virgl_video_codec *codec,
                                  struct virgl_video_buffer *target,
                                  const struct virgl_mjpeg_picture_desc *desc,
                                  unsigned num_buffers,
                                  const void * const *buffers,
                                  const unsigned *sizes)
{
    unsigned i;
    int err = 0;
    VAStatus va_stat;
    VABufferID *slice_data_buf, pic_param_buf, slice_param_buf, iq_matrix_buf, huffman_table_buf;
    VAPictureParameterBufferJPEGBaseline pic_param = {0};
    VASliceParameterBufferJPEGBaseline slice_param = {0};
    VAIQMatrixBufferJPEGBaseline iq_matrix = {0};
    VAHuffmanTableBufferJPEGBaseline huffman_table = {0};

    slice_data_buf = calloc(num_buffers, sizeof(VABufferID));
    if (!slice_data_buf) {
        virgl_error("alloc slice data buffer id failed\n");
        return -1;
    }

    mjpeg_fill_picture_param(codec, target, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    mjpeg_fill_iq_matrix(desc, &iq_matrix);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAIQMatrixBufferType,
                   sizeof(iq_matrix), 1, &iq_matrix, &iq_matrix_buf);

    mjpeg_fill_huffman_table(desc, &huffman_table);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAHuffmanTableBufferType,
                   sizeof(iq_matrix), 1, &huffman_table, &huffman_table_buf);

    mjpeg_fill_slice_param(desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VASliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    for (i = 0; i < num_buffers; i++) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VASliceDataBufferType,
                       sizes[i], 1, (void *)(buffers[i]), &slice_data_buf[i]);
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render picture param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &huffman_table_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render huffman_table_buf failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &iq_matrix_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render iq_matrix_buf failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }
    
    for (i = 0; i < num_buffers; i++) {
        va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_data_buf[i], 1);

        if (VA_STATUS_SUCCESS != va_stat) {
            virgl_error("render slice data failed, err = 0x%x\n", va_stat);
            err = -1;
        }
    }

err:
    vaDestroyBuffer(va_dpy, pic_param_buf);
    vaDestroyBuffer(va_dpy, slice_param_buf);
    for (i = 0; i < num_buffers; i++)
        vaDestroyBuffer(va_dpy, slice_data_buf[i]);
    free(slice_data_buf);

    return err;
}


static void vc1_fill_picture_param(struct virgl_video_codec *codec,
                            struct virgl_video_buffer *target,
                            const struct virgl_vc1_picture_desc *desc,
                            VAPictureParameterBufferVC1 *vapp)
{
    (void)codec;
    (void)target;
    vapp->forward_reference_picture = desc->ref[0];
    vapp->backward_reference_picture = desc->ref[1];

    ITEM_SET(&vapp->picture_fields.bits, desc, picture_type);
    ITEM_SET(&vapp->picture_fields.bits, desc, frame_coding_mode);
    ITEM_SET(&vapp->sequence_fields.bits, desc, pulldown);
    ITEM_SET(&vapp->sequence_fields.bits, desc, interlace);
    ITEM_SET(&vapp->sequence_fields.bits, desc, tfcntrflag);
    ITEM_SET(&vapp->sequence_fields.bits, desc, finterpflag);
    ITEM_SET(&vapp->sequence_fields.bits, desc, psf);
    ITEM_SET(&vapp->pic_quantizer_fields.bits, desc, dquant);
    ITEM_SET(&vapp->entrypoint_fields.bits, desc, panscan_flag);
    vapp->reference_fields.bits.reference_distance_flag = desc->refdist_flag;
    ITEM_SET(&vapp->pic_quantizer_fields.bits, desc, quantizer);
    vapp->mv_fields.bits.extended_mv_flag = desc->extended_mv;
    vapp->mv_fields.bits.extended_dmv_flag = desc->extended_dmv;
    ITEM_SET(&vapp->sequence_fields.bits, desc, overlap);
    vapp->transform_fields.bits.variable_sized_transform_flag = desc->vstransform;
    ITEM_SET(&vapp->entrypoint_fields.bits, desc, loopfilter);
    vapp->fast_uvmc_flag = desc->fastuvmc;
    vapp->range_mapping_fields.bits.luma_flag = desc->range_mapy_flag;
    vapp->range_mapping_fields.bits.luma = desc->range_mapy;
    vapp->range_mapping_fields.bits.chroma_flag = desc->range_mapuv_flag;
    vapp->range_mapping_fields.bits.chroma = desc->range_mapuv;
    vapp->sequence_fields.bits.multires = desc->multires;
    ITEM_SET(&vapp->sequence_fields.bits, desc, syncmarker);
    ITEM_SET(&vapp->sequence_fields.bits, desc, rangered);
    vapp->sequence_fields.bits.max_b_frames = desc->maxbframes;
    vapp->pic_quantizer_fields.bits.pic_quantizer_scale = desc->pquant;
}

static void vc1_fill_slice_param(const struct virgl_vc1_picture_desc *desc,
                                 VASliceParameterBufferVC1 *vasp)
{
    (void)desc;
    (void)vasp;
}

static int vc1_decode_bitstream(struct virgl_video_codec *codec,
                                struct virgl_video_buffer *target,
                                const struct virgl_vc1_picture_desc *desc,
                                unsigned num_buffers,
                                const void * const *buffers,
                                const unsigned *sizes)
{
    unsigned i;
    int err = 0;
    VAStatus va_stat;
    VABufferID *slice_data_buf, pic_param_buf, slice_param_buf;
    VAPictureParameterBufferVC1 pic_param = {0};
    VASliceParameterBufferVC1 slice_param = {0};

    slice_data_buf = calloc(num_buffers, sizeof(VABufferID));
    if (!slice_data_buf) {
        virgl_error("alloc slice data buffer id failed\n");
        return -1;
    }

    vc1_fill_picture_param(codec, target, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    vc1_fill_slice_param(desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VASliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    for (i = 0; i < num_buffers; i++) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VASliceDataBufferType,
                       sizes[i], 1, (void *)(buffers[i]), &slice_data_buf[i]);
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render picture param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    for (i = 0; i < num_buffers; i++) {
        va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_data_buf[i], 1);

        if (VA_STATUS_SUCCESS != va_stat) {
            virgl_error("render slice data failed, err = 0x%x\n", va_stat);
            err = -1;
        }
    }

err:
    vaDestroyBuffer(va_dpy, pic_param_buf);
    vaDestroyBuffer(va_dpy, slice_param_buf);
    for (i = 0; i < num_buffers; i++)
        vaDestroyBuffer(va_dpy, slice_data_buf[i]);
    free(slice_data_buf);

    return err;
}

static void vp9_fill_picture_param(struct virgl_video_codec *codec,
                                   struct virgl_video_buffer *target,
                                   const struct virgl_vp9_picture_desc *desc,
                                   VADecPictureParameterBufferVP9 *vapp)
{
    unsigned i;
    (void)codec;
    (void)target;

    for (i = 0; i < 8; i++)
        vapp->reference_frames[i] = desc->ref[i];

    ITEM_SET(vapp, &desc->picture_parameter, frame_width);
    ITEM_SET(vapp, &desc->picture_parameter, frame_height);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, subsampling_x);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, subsampling_y);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, frame_type);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, show_frame);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, error_resilient_mode);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, intra_only);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, allow_high_precision_mv);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, mcomp_filter_type);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, frame_parallel_decoding_mode);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, reset_frame_context);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, refresh_frame_context);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, frame_context_idx);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, segmentation_enabled);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, segmentation_temporal_update);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, segmentation_update_map);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, last_ref_frame);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, last_ref_frame_sign_bias);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, golden_ref_frame);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, golden_ref_frame_sign_bias);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, alt_ref_frame);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, alt_ref_frame_sign_bias);
    ITEM_SET(&vapp->pic_fields.bits, &desc->picture_parameter.pic_fields, lossless_flag);
    ITEM_SET(vapp, &desc->picture_parameter, filter_level);
    ITEM_SET(vapp, &desc->picture_parameter, sharpness_level);
    ITEM_SET(vapp, &desc->picture_parameter, log2_tile_rows);
    ITEM_SET(vapp, &desc->picture_parameter, log2_tile_columns);
    ITEM_SET(vapp, &desc->picture_parameter, frame_header_length_in_bytes);
    ITEM_SET(vapp, &desc->picture_parameter, first_partition_size);
    ITEM_CPY(vapp, &desc->picture_parameter, mb_segment_tree_probs);
    ITEM_CPY(vapp, &desc->picture_parameter, segment_pred_probs);
    ITEM_SET(vapp, &desc->picture_parameter, profile);
    ITEM_SET(vapp, &desc->picture_parameter, bit_depth);
}

static void vp9_fill_slice_param(const struct virgl_vp9_picture_desc *desc,
                                  VASliceParameterBufferVP9 *vasp)
{
    int i;

    ITEM_SET(vasp, &desc->slice_parameter, slice_data_size);
    ITEM_SET(vasp, &desc->slice_parameter, slice_data_offset);
    ITEM_SET(vasp, &desc->slice_parameter, slice_data_flag);
    for (i = 0; i < 8; i++) {
        vasp->seg_param[i].segment_flags.fields.segment_reference_enabled =
            desc->slice_parameter.seg_param[i].segment_flags.segment_reference_enabled;
        vasp->seg_param[i].segment_flags.fields.segment_reference =
            desc->slice_parameter.seg_param[i].segment_flags.segment_reference;
        vasp->seg_param[i].segment_flags.fields.segment_reference_skipped =
            desc->slice_parameter.seg_param[i].segment_flags.segment_reference_skipped;
        ITEM_CPY(vasp, &desc->slice_parameter, seg_param[i].filter_level);
        ITEM_SET(vasp, &desc->slice_parameter, seg_param[i].luma_ac_quant_scale);
        ITEM_SET(vasp, &desc->slice_parameter, seg_param[i].luma_dc_quant_scale);
        ITEM_SET(vasp, &desc->slice_parameter, seg_param[i].chroma_ac_quant_scale);
        ITEM_SET(vasp, &desc->slice_parameter, seg_param[i].chroma_dc_quant_scale);
    }
}

static int vp9_decode_bitstream(struct virgl_video_codec *codec,
                                 struct virgl_video_buffer *target,
                                 const struct virgl_vp9_picture_desc *desc,
                                 unsigned num_buffers,
                                 const void * const *buffers,
                                 const unsigned *sizes)
{
    unsigned i;
    int err = 0;
    VAStatus va_stat;
    VABufferID *slice_data_buf, pic_param_buf, slice_param_buf;
    VADecPictureParameterBufferVP9 pic_param = {0};
    VASliceParameterBufferVP9 slice_param = {0};

    slice_data_buf = calloc(num_buffers, sizeof(VABufferID));
    if (!slice_data_buf) {
        virgl_error("alloc slice data buffer id failed\n");
        return -1;
    }

    vp9_fill_picture_param(codec, target, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    vp9_fill_slice_param(desc, &slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VASliceParameterBufferType,
                   sizeof(slice_param), 1, &slice_param, &slice_param_buf);

    for (i = 0; i < num_buffers; i++) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VASliceDataBufferType,
                       sizes[i], 1, (void *)(buffers[i]), &slice_data_buf[i]);
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render picture param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    for (i = 0; i < num_buffers; i++) {
        va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_data_buf[i], 1);

        if (VA_STATUS_SUCCESS != va_stat) {
            virgl_error("render slice data failed, err = 0x%x\n", va_stat);
            err = -1;
        }
    }

err:
    vaDestroyBuffer(va_dpy, pic_param_buf);
    vaDestroyBuffer(va_dpy, slice_param_buf);
    for (i = 0; i < num_buffers; i++)
        vaDestroyBuffer(va_dpy, slice_data_buf[i]);
    free(slice_data_buf);

    return err;
}

/*
 * Refer to vlVaHandlePictureParameterBufferAV1() in mesa,
 * and comment out some unused parameters.
 */
static void av1_fill_picture_param(struct virgl_video_codec *codec,
                            struct virgl_video_buffer *target,
                            const struct virgl_av1_picture_desc *desc,
                            VADecPictureParameterBufferAV1 *param)
{
    unsigned i, j;

    (void)codec;
    (void)target;

    ITEM_SET(param, &desc->picture_parameter, profile);
    ITEM_SET(param, &desc->picture_parameter, order_hint_bits_minus_1);
    ITEM_SET(param, &desc->picture_parameter, bit_depth_idx);
    ITEM_SET(param, &desc->picture_parameter, matrix_coefficients);

    //still_picture;
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, use_128x128_superblock);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_filter_intra);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_intra_edge_filter);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_interintra_compound);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_masked_compound);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_dual_filter);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_order_hint);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_jnt_comp);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, enable_cdef);
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, mono_chrome);
    //color_range
    //subsampling_x
    //subsampling_y
    //chroma_sample_positio
    ITEM_SET(&param->seq_info_fields.fields, &desc->picture_parameter.seq_info_fields, film_grain_params_present);

    param->current_frame = desc->picture_parameter.current_frame_id;
    param->current_display_picture = desc->picture_parameter.current_frame_id;

    //anchor_frames_num
    //anchor_frames_list

    param->frame_width_minus1 = desc->picture_parameter.frame_width - 1;
    param->frame_height_minus1 = desc->picture_parameter.frame_height - 1;

    //output_frame_width_in_tiles_minus_1
    //output_frame_height_in_tiles_minus_1

    for (i = 0; i < ARRAY_SIZE(param->ref_frame_map); i++)
        param->ref_frame_map[i] = desc->ref[i];
    for (i = 0; i < ARRAY_SIZE(param->ref_frame_idx); i++)
        param->ref_frame_idx[i] = desc->picture_parameter.ref_frame_idx[i];

    ITEM_SET(param, &desc->picture_parameter, primary_ref_frame);
    ITEM_SET(param, &desc->picture_parameter, order_hint);

    /* Segmentation Params */
    ITEM_SET(&param->seg_info.segment_info_fields.bits, &desc->picture_parameter.seg_info.segment_info_fields, enabled);
    ITEM_SET(&param->seg_info.segment_info_fields.bits, &desc->picture_parameter.seg_info.segment_info_fields, update_map);
    ITEM_SET(&param->seg_info.segment_info_fields.bits, &desc->picture_parameter.seg_info.segment_info_fields, update_data);
    ITEM_SET(&param->seg_info.segment_info_fields.bits, &desc->picture_parameter.seg_info.segment_info_fields, temporal_update);
    for (i = 0; i < 8; i++) {
        for (j = 0; j < 8; j++)
            param->seg_info.feature_data[i][j] = desc->picture_parameter.seg_info.feature_data[i][j];
        param->seg_info.feature_mask[i] = desc->picture_parameter.seg_info.feature_mask[i];
    }

    /* Film Grain Params */
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, apply_grain);
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, chroma_scaling_from_luma);
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, grain_scaling_minus_8);
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, ar_coeff_lag);
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, ar_coeff_shift_minus_6);
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, grain_scale_shift);
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, overlap_flag);
    ITEM_SET(&param->film_grain_info.film_grain_info_fields.bits, &desc->picture_parameter.film_grain_info.film_grain_info_fields, clip_to_restricted_range);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, grain_seed);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, num_y_points);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, num_cb_points);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, num_cr_points);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.point_y_value); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, point_y_value[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.point_y_scaling); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, point_y_scaling[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.point_cb_value); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, point_cb_value[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.point_cb_scaling); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, point_cb_scaling[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.point_cr_value); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, point_cr_value[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.point_cr_scaling); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, point_cr_scaling[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.ar_coeffs_y); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, ar_coeffs_y[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.ar_coeffs_cb); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, ar_coeffs_cb[i]);
    for (i = 0; i < ARRAY_SIZE(param->film_grain_info.ar_coeffs_cr); i++)
        ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, ar_coeffs_cr[i]);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, cb_mult);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, cb_luma_mult);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, cb_offset);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, cr_mult);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, cr_luma_mult);
    ITEM_SET(&param->film_grain_info, &desc->picture_parameter.film_grain_info, cr_offset);

    ITEM_SET(param, &desc->picture_parameter, tile_cols);
    ITEM_SET(param, &desc->picture_parameter, tile_rows);

    if (!desc->picture_parameter.pic_info_fields.uniform_tile_spacing_flag) {
        for (i = 0; i < ARRAY_SIZE(param->width_in_sbs_minus_1); i++)
            if (desc->picture_parameter.width_in_sbs[i] > 0)
                param->width_in_sbs_minus_1[i] = desc->picture_parameter.width_in_sbs[i] - 1;
        for (i = 0; i < ARRAY_SIZE(param->height_in_sbs_minus_1); i++)
            if (desc->picture_parameter.height_in_sbs[i] > 0)
                param->height_in_sbs_minus_1[i] = desc->picture_parameter.height_in_sbs[i] - 1;
    }

    //tile_count_minus_1

    ITEM_SET(param, &desc->picture_parameter, context_update_tile_id);

    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, frame_type);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, show_frame);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, showable_frame);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, error_resilient_mode);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, disable_cdf_update);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, allow_screen_content_tools);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, force_integer_mv);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, allow_intrabc);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, use_superres);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, allow_high_precision_mv);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, is_motion_mode_switchable);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, use_ref_frame_mvs);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, disable_frame_end_update_cdf);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, uniform_tile_spacing_flag);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, allow_warped_motion);
    ITEM_SET(&param->pic_info_fields.bits, &desc->picture_parameter.pic_info_fields, large_scale_tile);

    ITEM_SET(param, &desc->picture_parameter, superres_scale_denominator);
    ITEM_SET(param, &desc->picture_parameter, interp_filter);
    for (i = 0; i < ARRAY_SIZE(param->filter_level); i++)
        ITEM_SET(param, &desc->picture_parameter, filter_level[i]);
    ITEM_SET(param, &desc->picture_parameter, filter_level_u);
    ITEM_SET(param, &desc->picture_parameter, filter_level_v);

    ITEM_SET(&param->loop_filter_info_fields.bits, &desc->picture_parameter.loop_filter_info_fields, sharpness_level);
    ITEM_SET(&param->loop_filter_info_fields.bits, &desc->picture_parameter.loop_filter_info_fields, mode_ref_delta_enabled);
    ITEM_SET(&param->loop_filter_info_fields.bits, &desc->picture_parameter.loop_filter_info_fields, mode_ref_delta_update);

    for (i = 0; i < ARRAY_SIZE(param->ref_deltas); i++)
        ITEM_SET(param, &desc->picture_parameter, ref_deltas[i]);
    for (i = 0; i < ARRAY_SIZE(param->mode_deltas); i++)
        ITEM_SET(param, &desc->picture_parameter, mode_deltas[i]);

    /* Quantization Params */
    ITEM_SET(param, &desc->picture_parameter, base_qindex);
    ITEM_SET(param, &desc->picture_parameter, y_dc_delta_q);
    ITEM_SET(param, &desc->picture_parameter, u_dc_delta_q);
    ITEM_SET(param, &desc->picture_parameter, u_ac_delta_q);
    ITEM_SET(param, &desc->picture_parameter, v_dc_delta_q);
    ITEM_SET(param, &desc->picture_parameter, v_ac_delta_q);
    ITEM_SET(&param->qmatrix_fields.bits, &desc->picture_parameter.qmatrix_fields, using_qmatrix);
    if (desc->picture_parameter.qmatrix_fields.using_qmatrix) {
        ITEM_SET(&param->qmatrix_fields.bits, &desc->picture_parameter.qmatrix_fields, qm_y);
        ITEM_SET(&param->qmatrix_fields.bits, &desc->picture_parameter.qmatrix_fields, qm_u);
        ITEM_SET(&param->qmatrix_fields.bits, &desc->picture_parameter.qmatrix_fields, qm_v);
    }

    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, delta_q_present_flag);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, log2_delta_q_res);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, delta_lf_present_flag);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, log2_delta_lf_res);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, delta_lf_multi);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, tx_mode);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, reference_select);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, reduced_tx_set_used);
    ITEM_SET(&param->mode_control_fields.bits, &desc->picture_parameter.mode_control_fields, skip_mode_present);

    ITEM_SET(param, &desc->picture_parameter, cdef_damping_minus_3);
    ITEM_SET(param, &desc->picture_parameter, cdef_bits);
    for (i = 0; i < ARRAY_SIZE(param->cdef_y_strengths); i++)
        ITEM_SET(param, &desc->picture_parameter, cdef_y_strengths[i]);
    for (i = 0; i < ARRAY_SIZE(param->cdef_uv_strengths); i++)
        ITEM_SET(param, &desc->picture_parameter, cdef_uv_strengths[i]);

    ITEM_SET(&param->loop_restoration_fields.bits, &desc->picture_parameter.loop_restoration_fields, yframe_restoration_type);
    ITEM_SET(&param->loop_restoration_fields.bits, &desc->picture_parameter.loop_restoration_fields, cbframe_restoration_type);
    ITEM_SET(&param->loop_restoration_fields.bits, &desc->picture_parameter.loop_restoration_fields, crframe_restoration_type);
    ITEM_SET(&param->loop_restoration_fields.bits, &desc->picture_parameter.loop_restoration_fields, lr_unit_shift);
    ITEM_SET(&param->loop_restoration_fields.bits, &desc->picture_parameter.loop_restoration_fields, lr_uv_shift);

    /* Global Motion Params */
    for (i = 0; i < ARRAY_SIZE(param->wm); i++) {
        param->wm[i].wmtype  = desc->picture_parameter.wm[i].wmtype;
        param->wm[i].invalid = desc->picture_parameter.wm[i].invalid;
        for (j = 0; j < ARRAY_SIZE(param->wm[i].wmmat); j++)
            param->wm[i].wmmat[j] = desc->picture_parameter.wm[i].wmmat[j];
    }
}

/*
 * Refer to vlVaHandleSliceParameterBufferAV1() in mesa
 */
static void av1_fill_slice_param(struct virgl_video_codec *codec,
                            struct virgl_video_buffer *target,
                            const struct virgl_av1_picture_desc *desc,
                            unsigned num_param,
                            VASliceParameterBufferAV1 *param)
{
    (void)codec;
    (void)target;

    for (unsigned i = 0; i < num_param; i++) {
        param[i].slice_data_size = desc->slice_parameter.slice_data_size[i];
        param[i].slice_data_offset = desc->slice_parameter.slice_data_offset[i];
        param[i].tile_row = desc->slice_parameter.slice_data_row[i];
        param[i].tile_column = desc->slice_parameter.slice_data_col[i];
        param[i].anchor_frame_idx = desc->slice_parameter.slice_data_anchor_frame_idx[i];
    }
}

static int av1_decode_bitstream(struct virgl_video_codec *codec,
                                struct virgl_video_buffer *target,
                                const struct virgl_av1_picture_desc *desc,
                                unsigned num_buffers,
                                const void * const *buffers,
                                const unsigned *sizes)
{
    unsigned i;
    int err = 0;
    VAStatus va_stat;
    VABufferID *slice_data_buf, pic_param_buf, slice_param_buf;
    VADecPictureParameterBufferAV1 pic_param;
    VASliceParameterBufferAV1 *slice_param;
    unsigned slice_count = desc->slice_parameter.slice_count;

    /* Picture parameters */
    memset(&pic_param, 0, sizeof(pic_param));
    av1_fill_picture_param(codec, target, desc, &pic_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VAPictureParameterBufferType,
                   sizeof(pic_param), 1, &pic_param, &pic_param_buf);

    /* Slice parameters */
    slice_param = calloc(slice_count, sizeof(VASliceParameterBufferAV1));
    av1_fill_slice_param(codec, target, desc, slice_count, slice_param);
    vaCreateBuffer(va_dpy, codec->va_ctx, VASliceParameterBufferType,
                   sizeof(VASliceParameterBufferAV1), slice_count,
                   slice_param, &slice_param_buf);

    /* Slice data */
    slice_data_buf = calloc(num_buffers, sizeof(VABufferID));
    for (i = 0; i < num_buffers; i++) {
        vaCreateBuffer(va_dpy, codec->va_ctx, VASliceDataBufferType,
                      sizes[i], 1, (void *)(buffers[i]), &slice_data_buf[i]);
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &pic_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render picture param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_param_buf, 1);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("render slice param failed, err = 0x%x\n", va_stat);
        err = -1;
        goto err;
    }

    for (i = 0; i < num_buffers; i++) {
        va_stat = vaRenderPicture(va_dpy, codec->va_ctx, &slice_data_buf[i], 1);

        if (VA_STATUS_SUCCESS != va_stat) {
            virgl_error("render slice data failed, err = 0x%x\n", va_stat);
            err = -1;
        }
    }

err:
    vaDestroyBuffer(va_dpy, pic_param_buf);
    vaDestroyBuffer(va_dpy, slice_param_buf);
    for (i = 0; i < num_buffers; i++)
        vaDestroyBuffer(va_dpy, slice_data_buf[i]);
    free(slice_param);
    free(slice_data_buf);

    return err;
}


int virgl_video_decode_bitstream(struct virgl_video_codec *codec,
                                 struct virgl_video_buffer *target,
                                 const union virgl_picture_desc *desc,
                                 unsigned num_buffers,
                                 const void * const *buffers,
                                 const unsigned *sizes)
{
    if (!va_dpy || !codec || !target || !desc
        || !num_buffers || !buffers || !sizes){
        return -1;
    }  

    if (desc->base.profile != codec->profile) {
        virgl_error("profiles not matched, picture: %d, codec: %d\n",
                    desc->base.profile, codec->profile);
        return -1;
    }

    switch (codec->profile) {
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_BASELINE:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_CONSTRAINED_BASELINE:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_MAIN:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_EXTENDED:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH10:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH422:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH444:
        return h264_decode_bitstream(codec, target, &desc->h264,
                                     num_buffers, buffers, sizes);
    case PIPE_VIDEO_PROFILE_HEVC_MAIN:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_10:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_STILL:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_12:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_444:
        return h265_decode_bitstream(codec, target, &desc->h265,
                                     num_buffers, buffers, sizes);
    case PIPE_VIDEO_PROFILE_MPEG2_SIMPLE:
    case PIPE_VIDEO_PROFILE_MPEG2_MAIN:
        return mpeg12_decode_bitstream(codec, target, &desc->mpeg12,
                                       num_buffers, buffers, sizes);
    case PIPE_VIDEO_PROFILE_JPEG_BASELINE:
        return mjpeg_decode_bitstream(codec, target, &desc->mjpeg,
                                       num_buffers, buffers, sizes);
    case PIPE_VIDEO_PROFILE_VC1_SIMPLE:
    case PIPE_VIDEO_PROFILE_VC1_MAIN:
    case PIPE_VIDEO_PROFILE_VC1_ADVANCED:
        return vc1_decode_bitstream(codec, target, &desc->vc1,
                                       num_buffers, buffers, sizes);
    case PIPE_VIDEO_PROFILE_VP9_PROFILE0:
    case PIPE_VIDEO_PROFILE_VP9_PROFILE2:
        return vp9_decode_bitstream(codec, target, &desc->vp9,
                                      num_buffers, buffers, sizes);
    case PIPE_VIDEO_PROFILE_AV1_MAIN:
        return av1_decode_bitstream(codec, target, &desc->av1,
                                    num_buffers, buffers, sizes);
    default:
        break;
    }

    return -1;
}

int virgl_video_encode_bitstream(struct virgl_video_codec *codec,
                                 struct virgl_video_buffer *source,
                                 const union virgl_picture_desc *desc)
{
    if (!va_dpy || !codec || !source || !desc)
        return -1;

    if (desc->base.profile != codec->profile) {
        virgl_error("profiles not matched, picture: %d, codec: %d\n",
                    desc->base.profile, codec->profile);
        return -1;
    }

    switch (codec->profile) {
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_BASELINE:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_CONSTRAINED_BASELINE:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_MAIN:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_EXTENDED:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH10:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH422:
    case PIPE_VIDEO_PROFILE_MPEG4_AVC_HIGH444:
        return h264_encode_bitstream(codec, source, &desc->h264_enc);
    case PIPE_VIDEO_PROFILE_HEVC_MAIN:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_10:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_STILL:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_12:
    case PIPE_VIDEO_PROFILE_HEVC_MAIN_444:
        return h265_encode_bitstream(codec, source, &desc->h265_enc);
    default:
        break;
    }

    return -1;
}

int virgl_video_end_frame(struct virgl_video_codec *codec,
                          struct virgl_video_buffer *target)
{
    VAStatus va_stat;

    if (!va_dpy || !codec || !target)
        return -1;

    va_stat = vaEndPicture(va_dpy, codec->va_ctx);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("end picture failed, err = 0x%x\n", va_stat);
        return -1;
    }

    va_stat = vaSyncSurface(va_dpy, target->va_sfc);
    if (VA_STATUS_SUCCESS != va_stat) {
        virgl_error("sync surface failed, err = 0x%x\n", va_stat);
        return -1;
    }

    if (codec->entrypoint != PIPE_VIDEO_ENTRYPOINT_ENCODE) {
        decode_completed(codec, target);
    } else {
        encode_completed(codec, target);
    }

    return 0;
}

