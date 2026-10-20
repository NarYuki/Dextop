/*
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef DRM_RENDERER_H_
#define DRM_RENDERER_H_

#include "config.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>

#include "virgl_util.h"

#if defined(ENABLE_DRM)

int drm_renderer_init(int drm_fd);

void drm_renderer_fini(void);

void drm_renderer_reset(void);

size_t drm_renderer_capset(void *capset);

struct virgl_context *drm_renderer_create(size_t debug_len, const char *debug_name, int drm_fd);

#else /* ENABLE_DRM */

static inline int
drm_renderer_init(UNUSED int drm_fd)
{
   virgl_error("DRM native context support was not enabled in virglrenderer\n");
   return -1;
}

static inline void
drm_renderer_fini(void)
{
}

static inline void
drm_renderer_reset(void)
{
}

static inline size_t
drm_renderer_capset(UNUSED void *capset)
{
   return 0;
}

static inline struct virgl_context *
drm_renderer_create(UNUSED size_t debug_len, UNUSED const char *debug_name, UNUSED int drm_fd)
{
   return NULL;
}

#endif /* ENABLE_DRM */

#endif /* DRM_RENDERER_H_ */
