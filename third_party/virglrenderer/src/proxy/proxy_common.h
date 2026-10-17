/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef PROXY_COMMON_H
#define PROXY_COMMON_H

#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "util/hash_table.h"
#include "util/list.h"
#include "util/macros.h"
#include "virgl_util.h"
#include "virglrenderer.h"

#include "proxy_renderer.h"
#include "proxy_socket.h"

struct proxy_client;
struct proxy_context;
struct proxy_server;
struct proxy_socket;

struct proxy_renderer {
   const struct proxy_renderer_cbs *cbs;
   uint32_t flags;

   struct proxy_server *server;
   struct proxy_client *client;
};

extern struct proxy_renderer proxy_renderer;

void
proxy_log(const char *fmt, ...);

#endif /* PROXY_COMMON_H */
