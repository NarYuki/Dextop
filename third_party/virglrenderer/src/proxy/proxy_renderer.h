/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef PROXY_RENDERER_H
#define PROXY_RENDERER_H

#include <stddef.h>
#include <stdint.h>

struct iovec;
struct virgl_context;

struct proxy_renderer_cbs {
   int (*get_server_fd)(uint32_t version);
};

#ifdef ENABLE_RENDER_SERVER

int
proxy_renderer_init(const struct proxy_renderer_cbs *cbs, uint32_t flags);

void
proxy_renderer_fini(void);

void
proxy_renderer_reset(void);

size_t
proxy_get_capset(uint32_t set, void *caps);

struct virgl_context *
proxy_context_create(uint32_t ctx_id,
                     uint32_t ctx_flags,
                     size_t debug_len,
                     const char *debug_name);

#else /* ENABLE_RENDER_SERVER */

static inline int
proxy_renderer_init(UNUSED const struct proxy_renderer_cbs *cbs, UNUSED uint32_t flags)
{
   virgl_error("Render server support was not enabled in virglrenderer\n");
   return -1;
}

static inline void
proxy_renderer_fini(void)
{
}

static inline void
proxy_renderer_reset(void)
{
}

static inline size_t
proxy_get_capset(UNUSED uint32_t set, UNUSED void *caps)
{
   return 0;
}

static inline struct virgl_context *
proxy_context_create(UNUSED uint32_t ctx_id,
                     UNUSED uint32_t ctx_flags,
                     UNUSED size_t debug_len,
                     UNUSED const char *debug_name)
{
   return NULL;
}

#endif /* ENABLE_RENDER_SERVER */

#endif /* PROXY_RENDERER_H */
