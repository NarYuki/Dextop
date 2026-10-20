/**************************************************************************
 *
 * Copyright 2022 Younes Manton.
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

#ifndef PIPE_VIDEO_STATE_H
#define PIPE_VIDEO_STATE_H

#ifdef __cplusplus
extern "C" {
#endif

enum pipe_h264_slice_type
{
   PIPE_H264_SLICE_TYPE_P = 0x0,
   PIPE_H264_SLICE_TYPE_B = 0x1,
   PIPE_H264_SLICE_TYPE_I = 0x2,
   PIPE_H264_SLICE_TYPE_SP = 0x3,
   PIPE_H264_SLICE_TYPE_SI = 0x4
};

/* Same enum for h264/h265 */
enum pipe_h2645_enc_picture_type
{
   PIPE_H2645_ENC_PICTURE_TYPE_P = 0x00,
   PIPE_H2645_ENC_PICTURE_TYPE_B = 0x01,
   PIPE_H2645_ENC_PICTURE_TYPE_I = 0x02,
   PIPE_H2645_ENC_PICTURE_TYPE_IDR = 0x03,
   PIPE_H2645_ENC_PICTURE_TYPE_SKIP = 0x04
};

enum pipe_h2645_enc_rate_control_method
{
   PIPE_H2645_ENC_RATE_CONTROL_METHOD_DISABLE = 0x00,
   PIPE_H2645_ENC_RATE_CONTROL_METHOD_CONSTANT_SKIP = 0x01,
   PIPE_H2645_ENC_RATE_CONTROL_METHOD_VARIABLE_SKIP = 0x02,
   PIPE_H2645_ENC_RATE_CONTROL_METHOD_CONSTANT = 0x03,
   PIPE_H2645_ENC_RATE_CONTROL_METHOD_VARIABLE = 0x04
};

#ifdef __cplusplus
}
#endif

#endif /* PIPE_VIDEO_STATE_H */
