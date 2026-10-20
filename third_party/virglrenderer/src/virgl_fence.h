/*
 * Copyright 2023 Collabora, Ltd.
 * SPDX-License-Identifier: MIT
 */

#include <stdint.h>
#include <unistd.h>

int virgl_fence_table_init(void);
void virgl_fence_table_cleanup(void);
int virgl_fence_set_fd(uint64_t fence_id, int fd);
int virgl_fence_get_fd(uint64_t fence_id);
int virgl_fence_get_last_signalled_fence_fd(void);
