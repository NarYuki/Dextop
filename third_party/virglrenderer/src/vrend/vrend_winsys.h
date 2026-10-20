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

#ifndef VREND_WINSYS_H
#define VREND_WINSYS_H

#include "config.h"

#ifdef ENABLE_GBM
#include "vrend_winsys_gbm.h"
#endif
#ifdef HAVE_EPOXY_EGL_H
#include "vrend_winsys_egl.h"
#endif

#include "virglrenderer.h"

#ifndef DRM_FORMAT_MOD_INVALID
#define DRM_FORMAT_MOD_INVALID 0x00ffffffffffffffULL
#endif

struct virgl_gl_ctx_param;

#ifdef HAVE_EPOXY_EGL_H
extern struct virgl_egl *egl;
extern struct virgl_gbm *gbm;
#endif

int vrend_winsys_init(uint32_t flags, int preferred_fd);
void vrend_winsys_cleanup(void);

int vrend_winsys_init_external(void *egl_display);

virgl_renderer_gl_context vrend_winsys_create_context(struct virgl_gl_ctx_param *param);
void vrend_winsys_destroy_context(virgl_renderer_gl_context ctx);
int vrend_winsys_make_context_current(virgl_renderer_gl_context ctx);

int vrend_winsys_has_gl_colorspace(void);

int vrend_winsys_get_attrs_for_texture(uint32_t tex_id, uint32_t format, int *fourcc,
                                       bool *has_dmabuf_export,
                                       int *planes, uint64_t *modifier);
int vrend_winsys_get_fd_for_texture(uint32_t tex_id, int *fd);
int vrend_winsys_get_fd_for_texture2(uint32_t tex_id, int *fd, int *stride, int *offset);

uint32_t vrend_winsys_query_video_memory(void);
bool vrend_winsys_different_gpu(void);

#endif /* VREND_WINSYS_H */
