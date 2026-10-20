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
 * General video encoding and decoding interface.
 *
 * This file provides a general video interface, which mainly contains
 * two objects:
 *
 * virgl_video_buffer:
 *   Buffer for storing raw YUV formatted data. In VA-API based
 *   implementations, it is usually associated with a surface.
 *
 * virgl_video_codec:
 *   Represents an encoder or decoder. In VA-API based implementations, it
 *   usually corresponds to a context.
 *
 * @author Feng Jiang <jiangfeng@kylinos.cn>
 */

#ifndef VIRGL_VIDEO_H
#define VIRGL_VIDEO_H

#include <stdint.h>
#include <stdbool.h>

#include "util/u_formats.h"
#include "pipe/p_video_enums.h"
#include "util/u_formats.h"

struct virgl_video_codec;
struct virgl_video_buffer;
union virgl_caps;
union virgl_picture_desc;

struct virgl_video_create_codec_args {
    enum pipe_video_profile profile;
    enum pipe_video_entrypoint entrypoint;
    enum pipe_video_chroma_format chroma_format;
    uint32_t level;
    uint32_t width;
    uint32_t height;
    uint32_t max_references;
    uint32_t flags;
    void *opaque;
};

struct virgl_video_create_buffer_args {
    enum pipe_format format;
    uint32_t width;
    uint32_t height;
    bool interlaced;
    void *opaque;
};

/* flags for virgl_video_dma_buffers */
#define VIRGL_VIDEO_DMABUF_READ_ONLY        0x0001
#define VIRGL_VIDEO_DMABUF_WRITE_ONLY       0x0002
#define VIRGL_VIDEO_DMABUF_READ_WRITE       0x0003

struct virgl_video_dma_buf {
    struct virgl_video_buffer *buf;

    uint32_t drm_format;
    uint32_t width;
    uint32_t height;
    uint32_t flags;

    uint32_t num_planes;
    struct virgl_video_dma_buf_plane {
        uint32_t drm_format;
        int fd;
        uint32_t size;
        int modifier;
        uint32_t offset;
        uint32_t pitch;
    } planes[4];
};

/*
 * Use callback functions instead of directly exporting the video buffer
 * through an interface like virgl_video_export_buffer() is because the
 * underlying implementation may not be VA-API. The callback function can
 * better shield the underlying logic differences.
 */
struct virgl_video_callbacks {
    /* Callback when decoding is complete, used to download the decoded picture
     * from the video buffer */
    void (*decode_completed)(struct virgl_video_codec *codec,
                             const struct virgl_video_dma_buf *dmabuf);

    /* Upload the picture data to be encoded to the video buffer */
    void (*encode_upload_picture)(struct virgl_video_codec *codec,
                                  const struct virgl_video_dma_buf *dmabuf);

    /* Callback when encoding is complete, used to download the encoded data
     * and reference picture */
    void (*encode_completed)(struct virgl_video_codec *codec,
                             const struct virgl_video_dma_buf *src_buf,
                             const struct virgl_video_dma_buf *ref_buf,
                             unsigned num_coded_bufs,
                             const void * const *coded_bufs,
                             const unsigned *coded_sizes);
};

int virgl_video_init(int drm_fd,
                     struct virgl_video_callbacks *cbs,
                     unsigned int flags);
void virgl_video_destroy(void);

int virgl_video_fill_caps(union virgl_caps *caps);

struct virgl_video_codec *virgl_video_create_codec(
        const struct virgl_video_create_codec_args *args);
void virgl_video_destroy_codec(struct virgl_video_codec *codec);
enum pipe_video_profile virgl_video_codec_profile(
        const struct virgl_video_codec *codec);
void *virgl_video_codec_opaque_data(struct virgl_video_codec *codec);

struct virgl_video_buffer *virgl_video_create_buffer(
        const struct virgl_video_create_buffer_args *args);
void virgl_video_destroy_buffer(struct virgl_video_buffer *buffer);
uint32_t virgl_video_buffer_id(const struct virgl_video_buffer *buffer);
void *virgl_video_buffer_opaque_data(struct virgl_video_buffer *buffer);

int virgl_video_begin_frame(struct virgl_video_codec *codec,
                            struct virgl_video_buffer *target);
int virgl_video_decode_bitstream(struct virgl_video_codec *codec,
                                 struct virgl_video_buffer *target,
                                 const union virgl_picture_desc *desc,
                                 unsigned num_buffers,
                                 const void * const *buffers,
                                 const unsigned *sizes);
int virgl_video_encode_bitstream(struct virgl_video_codec *codec,
                                 struct virgl_video_buffer *source,
                                 const union virgl_picture_desc *desc);
int virgl_video_end_frame(struct virgl_video_codec *codec,
                          struct virgl_video_buffer *target);

#endif /* VIRGL_VIDEO_H */

