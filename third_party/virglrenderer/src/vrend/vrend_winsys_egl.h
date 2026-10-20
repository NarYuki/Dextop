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
#ifndef VIRGL_EGL_H
#define VIRGL_EGL_H

#include "virglrenderer.h"
#include "vrend_renderer.h"

#include <epoxy/egl.h>

#ifdef WIN32
#include <d3d11.h>
#endif

struct virgl_egl;

#ifdef ENABLE_GBM
struct virgl_gbm;
struct gbm_bo;
#endif

#ifdef ENABLE_GBM
struct virgl_egl *virgl_egl_init(struct virgl_gbm *gbm, bool surfaceless, bool gles);
#else
struct virgl_egl *virgl_egl_init(EGLNativeDisplayType display_id, bool surfaceless, bool gles);
#endif

void virgl_egl_destroy(struct virgl_egl *egl);

struct virgl_egl *virgl_egl_init_external(EGLDisplay egl_display);

virgl_renderer_gl_context virgl_egl_create_context(struct virgl_egl *egl,
                                                   struct virgl_gl_ctx_param *vparams);

void virgl_egl_destroy_context(struct virgl_egl *egl, virgl_renderer_gl_context virglctx);

int virgl_egl_make_context_current(struct virgl_egl *egl, virgl_renderer_gl_context virglctx);

virgl_renderer_gl_context virgl_egl_get_current_context(struct virgl_egl *egl);

bool virgl_has_egl_khr_gl_colorspace(struct virgl_egl *egl);

#ifdef ENABLE_GBM
int virgl_egl_get_attrs_for_texture(struct virgl_egl *egl, uint32_t tex_id, uint32_t format,
                                    int *fourcc, bool *has_dmabuf_export,
                                    int *planes, uint64_t *modifier);

int virgl_egl_get_fd_for_texture(struct virgl_egl *egl, uint32_t tex_id, int *fd);

int virgl_egl_get_fd_for_texture2(struct virgl_egl *egl, uint32_t tex_id, int *fd, int *stride,
                                  int *offset);

void *virgl_egl_image_from_dmabuf(struct virgl_egl *egl,
                                  uint32_t width,
                                  uint32_t height,
                                  uint32_t drm_format,
                                  uint64_t drm_modifier,
                                  uint32_t plane_count,
                                  const int *plane_fds,
                                  const uint32_t *plane_strides,
                                  const uint32_t *plane_offsets);
void virgl_egl_image_destroy(struct virgl_egl *egl, void *image);

void *virgl_egl_image_from_gbm_bo(struct virgl_egl *egl, struct gbm_bo *bo);
void *virgl_egl_aux_plane_image_from_gbm_bo(struct virgl_egl *egl, struct gbm_bo *bo, int plane);
#endif

bool virgl_egl_supports_fences(struct virgl_egl *egl);
EGLSyncKHR virgl_egl_fence_create(struct virgl_egl *egl);
void virgl_egl_fence_destroy(struct virgl_egl *egl, EGLSyncKHR fence);
bool virgl_egl_client_wait_fence(struct virgl_egl *egl, EGLSyncKHR fence, bool blocking);
bool virgl_egl_export_signaled_fence(struct virgl_egl *egl, int *out_fd);
bool virgl_egl_export_fence(struct virgl_egl *egl, EGLSyncKHR fence, int *out_fd);
bool virgl_egl_different_gpu(struct virgl_egl *egl);
const char *virgl_egl_error_string(EGLint error);

#ifdef WIN32
bool virgl_egl_win32_create_d3d11_texture2d(struct virgl_egl *egl,
                                            const D3D11_TEXTURE2D_DESC *desc, ID3D11Texture2D **tex);
EGLImageKHR virgl_egl_win32_image_from_d3d11_texture2d(struct virgl_egl *egl, ID3D11Texture2D *tex);
#endif

#endif
