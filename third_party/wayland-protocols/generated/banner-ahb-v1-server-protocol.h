
#ifndef BANNER_AHB_V1_SERVER_PROTOCOL_H
#define BANNER_AHB_V1_SERVER_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>
#include "wayland-server.h"

#ifdef  __cplusplus
extern "C" {
#endif

struct wl_client;
struct wl_resource;

/*
 * @section page_copyright_banner_ahb_v1 Copyright
 *
 * Copyright 2026 The412Banner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next
 * paragraph) shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */





struct banner_ahb_v1;
struct wl_buffer;

#ifndef BANNER_AHB_V1_INTERFACE
#define BANNER_AHB_V1_INTERFACE
extern const struct wl_interface banner_ahb_v1_interface;
#endif

struct banner_ahb_v1_interface {
	void (*destroy)(struct wl_client *client,
			struct wl_resource *resource);
	void (*attach)(struct wl_client *client,
		       struct wl_resource *resource,
		       struct wl_resource *buffer,
		       int32_t socket,
		       uint32_t width,
		       uint32_t height,
		       uint32_t stride,
		       uint32_t modifier_hi,
		       uint32_t modifier_lo,
		       uint32_t image_count);
};

#define BANNER_AHB_V1_MODE 0

#define BANNER_AHB_V1_MODE_SINCE_VERSION 2

#define BANNER_AHB_V1_DESTROY_SINCE_VERSION 1
#define BANNER_AHB_V1_ATTACH_SINCE_VERSION 1

static inline void
banner_ahb_v1_send_mode(struct wl_resource *resource_, uint32_t enabled)
{
	wl_resource_post_event(resource_, BANNER_AHB_V1_MODE, enabled);
}

#ifdef  __cplusplus
}
#endif

#endif
