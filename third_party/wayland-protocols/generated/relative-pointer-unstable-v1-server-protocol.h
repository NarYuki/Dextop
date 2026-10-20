
#ifndef RELATIVE_POINTER_UNSTABLE_V1_SERVER_PROTOCOL_H
#define RELATIVE_POINTER_UNSTABLE_V1_SERVER_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>
#include "wayland-server.h"

#ifdef  __cplusplus
extern "C" {
#endif

struct wl_client;
struct wl_resource;

/*
 * @section page_copyright_relative_pointer_unstable_v1 Copyright
 *
 * Copyright © 2014      Jonas Ådahl
 * Copyright © 2015      Red Hat Inc.
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

































struct wl_pointer;
struct zwp_relative_pointer_manager_v1;
struct zwp_relative_pointer_v1;

#ifndef ZWP_RELATIVE_POINTER_MANAGER_V1_INTERFACE
#define ZWP_RELATIVE_POINTER_MANAGER_V1_INTERFACE
extern const struct wl_interface zwp_relative_pointer_manager_v1_interface;
#endif
#ifndef ZWP_RELATIVE_POINTER_V1_INTERFACE
#define ZWP_RELATIVE_POINTER_V1_INTERFACE
extern const struct wl_interface zwp_relative_pointer_v1_interface;
#endif

struct zwp_relative_pointer_manager_v1_interface {
	void (*destroy)(struct wl_client *client,
			struct wl_resource *resource);
	void (*get_relative_pointer)(struct wl_client *client,
				     struct wl_resource *resource,
				     uint32_t id,
				     struct wl_resource *pointer);
};


#define ZWP_RELATIVE_POINTER_MANAGER_V1_DESTROY_SINCE_VERSION 1
#define ZWP_RELATIVE_POINTER_MANAGER_V1_GET_RELATIVE_POINTER_SINCE_VERSION 1

struct zwp_relative_pointer_v1_interface {
	void (*destroy)(struct wl_client *client,
			struct wl_resource *resource);
};

#define ZWP_RELATIVE_POINTER_V1_RELATIVE_MOTION 0

#define ZWP_RELATIVE_POINTER_V1_RELATIVE_MOTION_SINCE_VERSION 1

#define ZWP_RELATIVE_POINTER_V1_DESTROY_SINCE_VERSION 1

static inline void
zwp_relative_pointer_v1_send_relative_motion(struct wl_resource *resource_, uint32_t utime_hi, uint32_t utime_lo, wl_fixed_t dx, wl_fixed_t dy, wl_fixed_t dx_unaccel, wl_fixed_t dy_unaccel)
{
	wl_resource_post_event(resource_, ZWP_RELATIVE_POINTER_V1_RELATIVE_MOTION, utime_hi, utime_lo, dx, dy, dx_unaccel, dy_unaccel);
}

#ifdef  __cplusplus
}
#endif

#endif
