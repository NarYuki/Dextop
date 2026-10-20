
#ifndef POINTER_CONSTRAINTS_UNSTABLE_V1_SERVER_PROTOCOL_H
#define POINTER_CONSTRAINTS_UNSTABLE_V1_SERVER_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>
#include "wayland-server.h"

#ifdef  __cplusplus
extern "C" {
#endif

struct wl_client;
struct wl_resource;

/*
 * @section page_copyright_pointer_constraints_unstable_v1 Copyright
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
struct wl_region;
struct wl_surface;
struct zwp_confined_pointer_v1;
struct zwp_locked_pointer_v1;
struct zwp_pointer_constraints_v1;

#ifndef ZWP_POINTER_CONSTRAINTS_V1_INTERFACE
#define ZWP_POINTER_CONSTRAINTS_V1_INTERFACE
extern const struct wl_interface zwp_pointer_constraints_v1_interface;
#endif
#ifndef ZWP_LOCKED_POINTER_V1_INTERFACE
#define ZWP_LOCKED_POINTER_V1_INTERFACE
extern const struct wl_interface zwp_locked_pointer_v1_interface;
#endif
#ifndef ZWP_CONFINED_POINTER_V1_INTERFACE
#define ZWP_CONFINED_POINTER_V1_INTERFACE
extern const struct wl_interface zwp_confined_pointer_v1_interface;
#endif

#ifndef ZWP_POINTER_CONSTRAINTS_V1_ERROR_ENUM
#define ZWP_POINTER_CONSTRAINTS_V1_ERROR_ENUM
enum zwp_pointer_constraints_v1_error {
	ZWP_POINTER_CONSTRAINTS_V1_ERROR_ALREADY_CONSTRAINED = 1,
};
#endif

#ifndef ZWP_POINTER_CONSTRAINTS_V1_ERROR_ENUM_IS_VALID
#define ZWP_POINTER_CONSTRAINTS_V1_ERROR_ENUM_IS_VALID
static inline bool
zwp_pointer_constraints_v1_error_is_valid(uint32_t value, uint32_t version) {
	switch (value) {
	case ZWP_POINTER_CONSTRAINTS_V1_ERROR_ALREADY_CONSTRAINED:
		return version >= 1;
	default:
		return false;
	}
}
#endif

#ifndef ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ENUM
#define ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ENUM
enum zwp_pointer_constraints_v1_lifetime {
	ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ONESHOT = 1,
	ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT = 2,
};
#endif

#ifndef ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ENUM_IS_VALID
#define ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ENUM_IS_VALID
static inline bool
zwp_pointer_constraints_v1_lifetime_is_valid(uint32_t value, uint32_t version) {
	switch (value) {
	case ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ONESHOT:
		return version >= 1;
	case ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_PERSISTENT:
		return version >= 1;
	default:
		return false;
	}
}
#endif

struct zwp_pointer_constraints_v1_interface {
	void (*destroy)(struct wl_client *client,
			struct wl_resource *resource);
	void (*lock_pointer)(struct wl_client *client,
			     struct wl_resource *resource,
			     uint32_t id,
			     struct wl_resource *surface,
			     struct wl_resource *pointer,
			     struct wl_resource *region,
			     uint32_t lifetime);
	void (*confine_pointer)(struct wl_client *client,
				struct wl_resource *resource,
				uint32_t id,
				struct wl_resource *surface,
				struct wl_resource *pointer,
				struct wl_resource *region,
				uint32_t lifetime);
};


#define ZWP_POINTER_CONSTRAINTS_V1_DESTROY_SINCE_VERSION 1
#define ZWP_POINTER_CONSTRAINTS_V1_LOCK_POINTER_SINCE_VERSION 1
#define ZWP_POINTER_CONSTRAINTS_V1_CONFINE_POINTER_SINCE_VERSION 1

struct zwp_locked_pointer_v1_interface {
	void (*destroy)(struct wl_client *client,
			struct wl_resource *resource);
	void (*set_cursor_position_hint)(struct wl_client *client,
					 struct wl_resource *resource,
					 wl_fixed_t surface_x,
					 wl_fixed_t surface_y);
	void (*set_region)(struct wl_client *client,
			   struct wl_resource *resource,
			   struct wl_resource *region);
};

#define ZWP_LOCKED_POINTER_V1_LOCKED 0
#define ZWP_LOCKED_POINTER_V1_UNLOCKED 1

#define ZWP_LOCKED_POINTER_V1_LOCKED_SINCE_VERSION 1
#define ZWP_LOCKED_POINTER_V1_UNLOCKED_SINCE_VERSION 1

#define ZWP_LOCKED_POINTER_V1_DESTROY_SINCE_VERSION 1
#define ZWP_LOCKED_POINTER_V1_SET_CURSOR_POSITION_HINT_SINCE_VERSION 1
#define ZWP_LOCKED_POINTER_V1_SET_REGION_SINCE_VERSION 1

static inline void
zwp_locked_pointer_v1_send_locked(struct wl_resource *resource_)
{
	wl_resource_post_event(resource_, ZWP_LOCKED_POINTER_V1_LOCKED);
}

static inline void
zwp_locked_pointer_v1_send_unlocked(struct wl_resource *resource_)
{
	wl_resource_post_event(resource_, ZWP_LOCKED_POINTER_V1_UNLOCKED);
}

struct zwp_confined_pointer_v1_interface {
	void (*destroy)(struct wl_client *client,
			struct wl_resource *resource);
	void (*set_region)(struct wl_client *client,
			   struct wl_resource *resource,
			   struct wl_resource *region);
};

#define ZWP_CONFINED_POINTER_V1_CONFINED 0
#define ZWP_CONFINED_POINTER_V1_UNCONFINED 1

#define ZWP_CONFINED_POINTER_V1_CONFINED_SINCE_VERSION 1
#define ZWP_CONFINED_POINTER_V1_UNCONFINED_SINCE_VERSION 1

#define ZWP_CONFINED_POINTER_V1_DESTROY_SINCE_VERSION 1
#define ZWP_CONFINED_POINTER_V1_SET_REGION_SINCE_VERSION 1

static inline void
zwp_confined_pointer_v1_send_confined(struct wl_resource *resource_)
{
	wl_resource_post_event(resource_, ZWP_CONFINED_POINTER_V1_CONFINED);
}

static inline void
zwp_confined_pointer_v1_send_unconfined(struct wl_resource *resource_)
{
	wl_resource_post_event(resource_, ZWP_CONFINED_POINTER_V1_UNCONFINED);
}

#ifdef  __cplusplus
}
#endif

#endif
