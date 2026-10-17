/**************************************************************************
 *
 * Copyright (C) 2015 Red Hat Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 **************************************************************************/

#ifndef VTEST_H
#define VTEST_H

#include <errno.h>

struct vtest_context;

struct vtest_buffer {
   const char *buffer;
   int size;
};

struct vtest_input {
   union {
      int fd;
      struct vtest_buffer *buffer;
   } data;
   int (*read)(struct vtest_input *input, void *buf, int size);
};

int vtest_init_renderer(bool multi_clients,
                        int ctx_flags,
                        const char *render_device);
void vtest_cleanup_renderer(void);

int vtest_create_context(struct vtest_input *input, int out_fd,
                         uint32_t length_dw, struct vtest_context **out_ctx);
int vtest_lazy_init_context(struct vtest_context *ctx);
void vtest_destroy_context(struct vtest_context *ctx);

void vtest_poll_context(struct vtest_context *ctx);
int vtest_get_context_poll_fd(struct vtest_context *ctx);

void vtest_set_current_context(struct vtest_context *ctx);

int vtest_send_caps(uint32_t length_dw);
int vtest_send_caps2(uint32_t length_dw);
int vtest_create_resource(uint32_t length_dw);
int vtest_create_resource2(uint32_t length_dw);
int vtest_resource_unref(uint32_t length_dw);
int vtest_submit_cmd(uint32_t length_dw);

int vtest_transfer_get(uint32_t length_dw);
int vtest_transfer_get_nop(uint32_t length_dw);
int vtest_transfer_get2(uint32_t length_dw);
int vtest_transfer_get2_nop(uint32_t length_dw);
int vtest_transfer_put(uint32_t length_dw);
int vtest_transfer_put_nop(uint32_t length_dw);
int vtest_transfer_put2(uint32_t length_dw);
int vtest_transfer_put2_nop(uint32_t length_dw);

int vtest_block_read(struct vtest_input *input, void *buf, int size);
int vtest_buf_read(struct vtest_input *input, void *buf, int size);

int vtest_resource_busy_wait(uint32_t length_dw);
int vtest_resource_busy_wait_nop(uint32_t length_dw);
void vtest_poll_resource_busy_wait(void);

int vtest_ping_protocol_version(uint32_t length_dw);
int vtest_protocol_version(uint32_t length_dw);

/* since protocol version 3 */
int vtest_get_param(uint32_t length_dw);
int vtest_get_capset(uint32_t length_dw);
int vtest_context_init(uint32_t length_dw);
int vtest_resource_create_blob(uint32_t length_dw);

int vtest_sync_create(uint32_t length_dw);
int vtest_sync_unref(uint32_t length_dw);
int vtest_sync_read(uint32_t length_dw);
int vtest_sync_write(uint32_t length_dw);
int vtest_sync_wait(uint32_t length_dw);

int vtest_submit_cmd2(uint32_t length_dw);

/* since protocol version 4 */
int vtest_drm_sync_create(uint32_t length_dw);
int vtest_drm_sync_destroy(uint32_t length_dw);
int vtest_drm_sync_handle_to_fd(uint32_t length_dw);
int vtest_drm_sync_fd_to_handle(uint32_t length_dw);
int vtest_drm_sync_import_sync_file(uint32_t length_dw);
int vtest_drm_sync_export_sync_file(uint32_t length_dw);
int vtest_drm_sync_wait(uint32_t length_dw);
int vtest_drm_sync_reset(uint32_t length_dw);
int vtest_drm_sync_signal(uint32_t length_dw);
int vtest_drm_sync_timeline_signal(uint32_t length_dw);
int vtest_drm_sync_timeline_wait(uint32_t length_dw);
int vtest_drm_sync_query(uint32_t length_dw);
int vtest_drm_sync_transfer(uint32_t length_dw);
int vtest_resource_export_fd(uint32_t length_dw);

void vtest_set_max_length(uint32_t length);

#endif

