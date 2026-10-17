/**************************************************************************
 *
 * Copyright (C) 2019 Chromium.
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

#ifndef VIRGL_GBM_H
#define VIRGL_GBM_H

#include <gbm.h>
#include "vrend_iov.h"
#include "virglrenderer.h"

#ifdef GBM_MAX_PLANES
#define VIRGL_GBM_MAX_PLANES GBM_MAX_PLANES
#else
#define VIRGL_GBM_MAX_PLANES 4
#endif

/* GBM_FORMAT_ABGR16161616F was added since mesa 20.0 */
#ifndef GBM_FORMAT_ABGR16161616F
#define GBM_FORMAT_ABGR16161616F __gbm_fourcc_code('A', 'B', '4', 'H') /* [63:0] A:B:G:R 16:16:16:16 little endian */
#endif

#ifndef MINIGBM

#ifndef GBM_BO_USE_TEXTURING
#define GBM_BO_USE_TEXTURING 0
#endif
#ifndef GBM_BO_USE_CAMERA_WRITE
#define GBM_BO_USE_CAMERA_WRITE 0
#endif
#ifndef GBM_BO_USE_CAMERA_READ
#define GBM_BO_USE_CAMERA_READ 0
#endif
#ifndef GBM_BO_USE_PROTECTED
#define GBM_BO_USE_PROTECTED 0
#endif
#ifndef GBM_BO_USE_SW_READ_OFTEN
#define GBM_BO_USE_SW_READ_OFTEN 0
#endif
#ifndef GBM_BO_USE_SW_READ_RARELY
#define GBM_BO_USE_SW_READ_RARELY 0
#endif
#ifndef GBM_BO_USE_SW_WRITE_OFTEN
#define GBM_BO_USE_SW_WRITE_OFTEN 0
#endif
#ifndef GBM_BO_USE_SW_WRITE_RARELY
#define GBM_BO_USE_SW_WRITE_RARELY 0
#endif
#ifndef GBM_BO_USE_HW_VIDEO_DECODER
#define GBM_BO_USE_HW_VIDEO_DECODER 0
#endif
#ifndef GBM_BO_USE_HW_VIDEO_ENCODER
#define GBM_BO_USE_HW_VIDEO_ENCODER 0
#endif

#endif

#ifdef ENABLE_GBM_ALLOCATION

#define GBM_DEV_TYPE_FLAG_DISCRETE (1u << 0) /* Discrete GPU. Separate chip, dedicated VRAM. */
#define GBM_DEV_TYPE_FLAG_DISPLAY (1u << 1) /* Device capable of display. */
#define GBM_DEV_TYPE_FLAG_3D (1u << 2) /* Device capable or 3D rendering. */
#define GBM_DEV_TYPE_FLAG_ARMSOC (1u << 3) /* Device on ARM SOC. */
#define GBM_DEV_TYPE_FLAG_USB (1u << 4) /* USB device, udl, evdi. */
#define GBM_DEV_TYPE_FLAG_BLOCKED (1u << 5) /* Unsuitable device e.g. vgem, udl, evdi. */
#define GBM_DEV_TYPE_FLAG_INTERNAL_LCD (1u << 6) /* Device is driving internal LCD. */

struct gbm_device_info {
   uint32_t dev_type_flags;
   int dri_node_num; /* DRI node number (0..63), for easy matching of devices. */
   unsigned int connectors;
   unsigned int connected;
};

#define GBM_DETECT_FLAG_CONNECTED (1u << 0) /* Check if any connectors are connected. SLOW! */

uint32_t virgl_gbm_get_map_info(struct gbm_bo *bo);

#ifdef MINIGBM
int gbm_detect_device_info(unsigned int detect_flags, int fd, struct gbm_device_info *info);
int gbm_detect_device_info_path(unsigned int detect_flags, const char *dev_node,
                                struct gbm_device_info *info);

/*
 * Create "default" gbm device. This can pick a different DRM device than
 * gbm_get_default_device_fd and should be preferred in most cases.
 */
struct gbm_device *minigbm_create_default_device(int *out_fd);
#else
#define gbm_detect_device_info(detect_flags, fd, info) -1
#define gbm_detect_device_info_path(detect_flags, dev_node, info) -1
#define minigbm_create_default_device(out_fd) NULL
#endif /* MINIGBM */
#endif /* ENABLE_GBM_ALLOCATION */

/*
 * If fd >= 0, virglrenderer owns the fd since it was opened via a rendernode
 * query. If fd < 0, the gbm device was opened with the fd provided by the
 * (*get_drm_fd) hook.
 */
struct virgl_gbm {
   int fd;
   struct gbm_device *device;
};

struct virgl_gbm *virgl_gbm_init(int fd);

void virgl_gbm_fini(struct virgl_gbm *gbm);

int virgl_gbm_convert_format(uint32_t *virgl_format, uint32_t *gbm_format);

int virgl_gbm_transfer(struct gbm_bo *bo, uint32_t direction, const struct iovec *iovecs,
                       uint32_t num_iovecs, const struct vrend_transfer_info *info);

uint32_t virgl_gbm_convert_flags(uint32_t virgl_bind_flags);

int virgl_gbm_export_fd(struct gbm_device *gbm, uint32_t handle, int32_t *out_fd);

int virgl_gbm_export_query(struct gbm_bo *bo, struct virgl_renderer_export_query *query);

int virgl_gbm_get_plane_width(struct gbm_bo *bo, int plane);
int virgl_gbm_get_plane_height(struct gbm_bo *bo, int plane);
int virgl_gbm_get_plane_bytes_per_pixel(struct gbm_bo *bo, int plane);

bool virgl_gbm_external_allocation_preferred(uint32_t flags);
bool virgl_gbm_gpu_import_required(uint32_t flags);

#endif
