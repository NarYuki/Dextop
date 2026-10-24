
#ifndef SINGLE_PIXEL_BUFFER_V1_SERVER_PROTOCOL_H
#define SINGLE_PIXEL_BUFFER_V1_SERVER_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>
#include "wayland-server.h"

#ifdef  __cplusplus
extern "C" {
#endif

struct wl_client;
struct wl_resource;

/*
 * @section page_copyright_single_pixel_buffer_v1 Copyright
 *
 * Copyright © 2022 Simon Ser
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




















struct wl_buffer;
struct wp_single_pixel_buffer_manager_v1;

#ifndef WP_SINGLE_PIXEL_BUFFER_MANAGER_V1_INTERFACE
#define WP_SINGLE_PIXEL_BUFFER_MANAGER_V1_INTERFACE
extern const struct wl_interface wp_single_pixel_buffer_manager_v1_interface;
#endif

struct wp_single_pixel_buffer_manager_v1_interface {
	void (*destroy)(struct wl_client *client,
			struct wl_resource *resource);
	void (*create_u32_rgba_buffer)(struct wl_client *client,
				       struct wl_resource *resource,
				       uint32_t id,
				       uint32_t r,
				       uint32_t g,
				       uint32_t b,
				       uint32_t a);
};


#define WP_SINGLE_PIXEL_BUFFER_MANAGER_V1_DESTROY_SINCE_VERSION 1
#define WP_SINGLE_PIXEL_BUFFER_MANAGER_V1_CREATE_U32_RGBA_BUFFER_SINCE_VERSION 1

#ifdef  __cplusplus
}
#endif

#endif
