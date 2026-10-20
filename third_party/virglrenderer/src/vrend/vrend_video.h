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
 * The video interface of the vrend renderer.
 *
 * These interfaces are mainly called by the vrend deocode submodule
 * to process the corresponding virgl context video command.
 *
 * @author Feng Jiang <jiangfeng@kylinos.cn>
 */

#ifndef VREND_VIDEO_H
#define VREND_VIDEO_H

#include <virgl_hw.h>

#define VREND_VIDEO_BUFFER_PLANE_NUM  3

struct vrend_video_context;

int vrend_video_init(int drm_fd);
void vrend_video_fini(void);

int vrend_video_fill_caps(union virgl_caps *caps);

struct vrend_video_context *vrend_video_create_context(struct vrend_context *ctx);
void vrend_video_destroy_context(struct vrend_video_context *ctx);

int vrend_video_create_codec(struct vrend_video_context *ctx,
                             uint32_t handle,
                             uint32_t profile,
                             uint32_t entrypoint,
                             uint32_t chroma_format,
                             uint32_t level,
                             uint32_t width,
                             uint32_t height,
                             uint32_t max_ref,
                             uint32_t flags);
void vrend_video_destroy_codec(struct vrend_video_context *ctx,
                               uint32_t handle);

int vrend_video_create_buffer(struct vrend_video_context *ctx,
                              uint32_t handle,
                              uint32_t format,
                              uint32_t width,
                              uint32_t height,
                              uint32_t *res_handles,
                              unsigned int num_res);
void vrend_video_destroy_buffer(struct vrend_video_context *ctx,
                                uint32_t handle);

int vrend_video_begin_frame(struct vrend_video_context *ctx,
                            uint32_t cdc_handle,
                            uint32_t tgt_handle);
int vrend_video_decode_bitstream(struct vrend_video_context *ctx,
                                 uint32_t cdc_handle,
                                 uint32_t tgt_handle,
                                 uint32_t desc_handle,
                                 unsigned num_buffers,
                                 const uint32_t *buffer_handles,
                                 const uint32_t *buffer_sizes);
int vrend_video_encode_bitstream(struct vrend_video_context *ctx,
                                 uint32_t cdc_handle,
                                 uint32_t src_handle,
                                 uint32_t dest_handle,
                                 uint32_t desc_handle,
                                 uint32_t feed_handle);
int vrend_video_end_frame(struct vrend_video_context *ctx,
                          uint32_t cdc_handle,
                          uint32_t tgt_handle);

#endif /* VREND_VIDEO_H */
