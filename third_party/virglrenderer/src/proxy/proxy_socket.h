/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef PROXY_SOCKET_H
#define PROXY_SOCKET_H

#include "proxy_common.h"

struct proxy_socket {
   int fd;
};

bool
proxy_socket_pair(int out_fds[static 2]);

bool
proxy_socket_is_seqpacket(int fd);

void
proxy_socket_init(struct proxy_socket *socket, int fd);

void
proxy_socket_fini(struct proxy_socket *socket);

bool
proxy_socket_is_connected(const struct proxy_socket *socket);

bool
proxy_socket_receive_reply(struct proxy_socket *socket, void *data, size_t size);

bool
proxy_socket_receive_reply_with_fds(struct proxy_socket *socket,
                                    void *data,
                                    size_t size,
                                    int *fds,
                                    int max_fd_count,
                                    int *out_fd_count);

bool
proxy_socket_send_request(struct proxy_socket *socket, const void *data, size_t size);

bool
proxy_socket_send_request_with_fds(struct proxy_socket *socket,
                                   const void *data,
                                   size_t size,
                                   const int *fds,
                                   int fd_count);

#endif /* PROXY_SOCKET_H */
