/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef PROXY_CLIENT_H
#define PROXY_CLIENT_H

#include "proxy_common.h"

struct proxy_client {
   struct proxy_socket socket;
};

struct proxy_client *
proxy_client_create(struct proxy_server *srv, uint32_t flags);

void
proxy_client_destroy(struct proxy_client *client);

bool
proxy_client_reset(struct proxy_client *client);

bool
proxy_client_create_context(struct proxy_client *client,
                            uint32_t ctx_id,
                            size_t ctx_name_len,
                            const char *ctx_name,
                            int *out_ctx_fd);

bool
proxy_client_destroy_context(struct proxy_client *client, uint32_t ctx_id);

#endif /* PROXY_CLIENT_H */
