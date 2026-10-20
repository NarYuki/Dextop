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

#ifndef _GNU_SOURCE
#define _GNU_SOURCE 1
#endif

#include <stdio.h>
#include <dirent.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <xf86drm.h>
#include <unistd.h>
#include <sys/mman.h>

#include "util/u_math.h"
#include "util/u_memory.h"
#include "pipe/p_state.h"

#include "vrend_winsys.h"
#include "vrend_winsys_gbm.h"
#include "virgl_hw.h"
#include "vrend_debug.h"

struct planar_layout {
    size_t num_planes;
    int horizontal_subsampling[VIRGL_GBM_MAX_PLANES];
    int vertical_subsampling[VIRGL_GBM_MAX_PLANES];
    int bytes_per_pixel[VIRGL_GBM_MAX_PLANES];
};

struct format_conversion {
    uint32_t gbm_format;
    uint32_t virgl_format;
};

static const struct planar_layout packed_1bpp_layout = {
    .num_planes = 1,
    .horizontal_subsampling = { 1 },
    .vertical_subsampling = { 1 },
    .bytes_per_pixel = { 1 }
};

static const struct planar_layout packed_2bpp_layout = {
    .num_planes = 1,
    .horizontal_subsampling = { 1 },
    .vertical_subsampling = { 1 },
    .bytes_per_pixel = { 2 }
};

static const struct planar_layout packed_4bpp_layout = {
    .num_planes = 1,
    .horizontal_subsampling = { 1 },
    .vertical_subsampling = { 1 },
    .bytes_per_pixel = { 4 }
};

static const struct planar_layout packed_8bpp_layout = {
    .num_planes = 1,
    .horizontal_subsampling = { 1 },
    .vertical_subsampling = { 1 },
    .bytes_per_pixel = { 8 }
};

static const struct planar_layout biplanar_yuv_420_layout = {
    .num_planes = 2,
    .horizontal_subsampling = { 1, 2 },
    .vertical_subsampling = { 1, 2 },
    .bytes_per_pixel = { 1, 2 }
};

static const struct planar_layout triplanar_yuv_420_layout = {
    .num_planes = 3,
    .horizontal_subsampling = { 1, 2, 2 },
    .vertical_subsampling = { 1, 2, 2 },
    .bytes_per_pixel = { 1, 1, 1 }
};

static const struct format_conversion conversions[] = {
    { GBM_FORMAT_RGB565, VIRGL_FORMAT_B5G6R5_UNORM },
    { GBM_FORMAT_ARGB8888, VIRGL_FORMAT_B8G8R8A8_UNORM },
    { GBM_FORMAT_XRGB8888, VIRGL_FORMAT_B8G8R8X8_UNORM },
    { GBM_FORMAT_ABGR2101010, VIRGL_FORMAT_R10G10B10A2_UNORM },
    { GBM_FORMAT_XBGR2101010, VIRGL_FORMAT_R10G10B10X2_UNORM },
    { GBM_FORMAT_ARGB2101010, VIRGL_FORMAT_B10G10R10A2_UNORM },
    { GBM_FORMAT_XRGB2101010, VIRGL_FORMAT_B10G10R10X2_UNORM },
    { GBM_FORMAT_ABGR16161616F, VIRGL_FORMAT_R16G16B16A16_FLOAT },
    { GBM_FORMAT_XBGR16161616F, VIRGL_FORMAT_R16G16B16X16_FLOAT },
#ifdef GBM_FORMAT_XBGR16161616
    { GBM_FORMAT_XBGR16161616, VIRGL_FORMAT_R16G16B16X16_UNORM },
    { GBM_FORMAT_ABGR16161616, VIRGL_FORMAT_R16G16B16A16_UNORM },
#endif
    { GBM_FORMAT_NV12, VIRGL_FORMAT_NV12 },
    { GBM_FORMAT_ABGR8888, VIRGL_FORMAT_R8G8B8A8_UNORM},
    { GBM_FORMAT_XBGR8888, VIRGL_FORMAT_R8G8B8X8_UNORM},
    { GBM_FORMAT_R8, VIRGL_FORMAT_R8_UNORM},
    { GBM_FORMAT_YVU420, VIRGL_FORMAT_YV12},
};

static int rendernode_open(void)
{
   DIR *dir;
   int ret, fd;
   bool undesired_found;
   drmVersionPtr version;
   char *rendernode_name;
   struct dirent *dir_ent;
   const char *undesired[3] = { "vgem", "pvr", NULL };

   dir = opendir("/dev/dri");
   if (!dir)
      return -1;

   fd = -1;
   while ((dir_ent = readdir(dir))) {
      if (dir_ent->d_type != DT_CHR)
         continue;

      if (strncmp(dir_ent->d_name, "renderD", 7))
         continue;

      ret = asprintf(&rendernode_name, "/dev/dri/%s", dir_ent->d_name);
      if (ret < 0)
         goto out;

      fd = open(rendernode_name, O_RDWR | O_CLOEXEC | O_NOCTTY | O_NONBLOCK);
      free(rendernode_name);

      if (fd < 0)
         continue;

      version = drmGetVersion(fd);
      if (!version) {
         close(fd);
         fd = -1;
         continue;
      }

      undesired_found = false;
      for (uint32_t i = 0; i < ARRAY_SIZE(undesired); i++) {
         if (undesired[i] && !strcmp(version->name, undesired[i]))
            undesired_found = true;
      }

      drmFreeVersion(version);
      if (undesired_found) {
         close(fd);
         fd = -1;
         continue;
      }

      break;
   }

out:
   closedir(dir);
   return fd;
}

static const struct planar_layout *layout_from_format(uint32_t format)
{
   switch (format) {
   case GBM_FORMAT_R8:
      return &packed_1bpp_layout;
   case GBM_FORMAT_YVU420:
      return &triplanar_yuv_420_layout;
   case GBM_FORMAT_NV12:
      return &biplanar_yuv_420_layout;
   case GBM_FORMAT_RGB565:
      return &packed_2bpp_layout;
   case GBM_FORMAT_ARGB8888:
   case GBM_FORMAT_XRGB8888:
   case GBM_FORMAT_ABGR8888:
   case GBM_FORMAT_XBGR8888:
   case GBM_FORMAT_ABGR2101010:
      return &packed_4bpp_layout;
   case GBM_FORMAT_ABGR16161616F:
      return &packed_8bpp_layout;
   default:
      return NULL;
   }
}

#ifdef ENABLE_GBM_ALLOCATION
static void virgl_gbm_transfer_internal(uint32_t planar_bytes_per_pixel,
                                        uint32_t subsampled_width,
                                        uint32_t subsampled_height,
                                        uint32_t guest_plane_stride,
                                        uint32_t guest_resource_offset,
                                        uint32_t host_plane_stride, uint8_t *host_address,
                                        const struct iovec *iovecs, uint32_t num_iovecs,
                                        uint32_t direction)
{
   bool next_iovec, next_line;
   uint32_t current_height, current_iovec, iovec_start_offset;
   current_height = current_iovec = iovec_start_offset = 0;

   while (current_height < subsampled_height && current_iovec < num_iovecs) {
      uint32_t iovec_size = iovecs[current_iovec].iov_len;
      uint32_t iovec_end_offset = iovec_start_offset + iovec_size;

      uint32_t box_start_offset = guest_resource_offset + current_height * guest_plane_stride;
      uint32_t box_end_offset = box_start_offset + subsampled_width * planar_bytes_per_pixel;

      uint32_t max_start = MAX2(iovec_start_offset, box_start_offset);
      uint32_t min_end = MIN2(iovec_end_offset, box_end_offset);

      if (max_start < min_end) {
         uint32_t offset_in_iovec = (max_start > iovec_start_offset) ?
                                    (max_start - iovec_start_offset) : 0;

         uint32_t copy_iovec_size = min_end - max_start;
         if (min_end >= iovec_end_offset) {
            next_iovec = true;
            next_line = false;
         } else {
            next_iovec = false;
            next_line = true;
         }

         uint8_t *guest_start = (uint8_t*)iovecs[current_iovec].iov_base + offset_in_iovec;
         uint8_t *host_start = host_address + (current_height * host_plane_stride) +
                               (max_start - box_start_offset);

         if (direction == VIRGL_TRANSFER_TO_HOST)
            memcpy(host_start, guest_start, copy_iovec_size);
         else
            memcpy(guest_start, host_start, copy_iovec_size);
      } else {
         if (box_start_offset >= iovec_start_offset) {
            next_iovec = true;
            next_line = false;
         } else {
            next_iovec = false;
            next_line = true;
         }
      }

      if (next_iovec) {
         iovec_start_offset += iovec_size;
         current_iovec++;
      }

      if (next_line)
         current_height++;
   }
}
#endif /* ENABLE_GBM_ALLOCATION */

struct virgl_gbm *virgl_gbm_init(int fd)
{
   struct virgl_gbm *gbm = calloc(1, sizeof(struct virgl_gbm));
   if (!gbm)
      return NULL;

   gbm->fd = -1;
   if (fd < 0) {
#ifdef ENABLE_GBM_ALLOCATION
      gbm->device = minigbm_create_default_device(&gbm->fd);
      if (gbm->device)
         return gbm;
#endif
      gbm->fd = rendernode_open();
      if (gbm->fd < 0)
         goto out_error;

      gbm->device = gbm_create_device(gbm->fd);
      if (!gbm->device) {
         close(gbm->fd);
         goto out_error;
      }
   } else {
      gbm->device = gbm_create_device(fd);
      if (!gbm->device)
         goto out_error;
      gbm->fd = fd;
   }

   return gbm;

out_error:
   free(gbm);
   return NULL;
}

void virgl_gbm_fini(struct virgl_gbm *gbm)
{
   gbm_device_destroy(gbm->device);
   if (gbm->fd >= 0)
      close(gbm->fd);
   free(gbm);
}

int virgl_gbm_convert_format(uint32_t *virgl_format, uint32_t *gbm_format)
{

    if (!virgl_format || !gbm_format)
      return -1;

    if (*virgl_format != 0 && *gbm_format != 0)
      return -1;

    for (uint32_t i = 0; i < ARRAY_SIZE(conversions); i++) {
      if (conversions[i].gbm_format == *gbm_format ||
          conversions[i].virgl_format == *virgl_format) {
         *gbm_format = conversions[i].gbm_format;
         *virgl_format = conversions[i].virgl_format;
         return 0;
      }
    }

    return -1;
}

#ifdef ENABLE_GBM_ALLOCATION
int virgl_gbm_transfer(struct gbm_bo *bo, uint32_t direction, const struct iovec *iovecs,
                       uint32_t num_iovecs, const struct vrend_transfer_info *info)
{
   void *map_data = NULL;
   uint32_t guest_plane_offset, guest_stride0, host_map_stride0;

   uint32_t width = gbm_bo_get_width(bo);
   uint32_t height = gbm_bo_get_height(bo);
   uint32_t format = gbm_bo_get_format(bo);
   int plane_count = gbm_bo_get_plane_count(bo);
   const struct planar_layout *layout = layout_from_format(format);
   if (!layout)
      return -1;

   host_map_stride0 = 0;
   uint32_t map_flags = (direction == VIRGL_TRANSFER_TO_HOST) ? GBM_BO_TRANSFER_WRITE :
                                                                GBM_BO_TRANSFER_READ;
   /* XXX remove this and map just the region when single plane and GBM honors the region */
   if (direction == VIRGL_TRANSFER_TO_HOST &&
       !(info->box->x == 0 && info->box->y == 0 &&
         info->box->width == (int)width && info->box->height == (int)height))
      map_flags |= GBM_BO_TRANSFER_READ;

   void *addr = gbm_bo_map(bo, 0, 0, width, height, map_flags, &host_map_stride0, &map_data);
   if (!addr || addr == MAP_FAILED)
      return -1;

   guest_plane_offset = info->offset;
   guest_stride0 = 0;

   /*
    * Unfortunately, the kernel doesn't actually pass the guest layer_stride and
    * guest stride to the host (compare virtio_gpu.h and virtgpu_drm.h). We can use
    * the level (always zero for 2D images) to work around this.
    */
   if (info->stride || info->level) {
      guest_stride0 = info->stride ? info->stride : info->level;
      if (guest_stride0 < (uint32_t)info->box->width * layout->bytes_per_pixel[0])
         return -1;
   } else {
      guest_stride0 = width * layout->bytes_per_pixel[0];
   }

   if (guest_stride0 > host_map_stride0)
      return -1;

   for (int plane = 0; plane < plane_count; plane++) {
      uint32_t host_plane_offset = gbm_bo_get_offset(bo, plane);

      uint32_t subsampled_x = info->box->x / layout->horizontal_subsampling[plane];
      uint32_t subsampled_y = info->box->y / layout->vertical_subsampling[plane];
      uint32_t subsampled_width = info->box->width / layout->horizontal_subsampling[plane];
      uint32_t subsampled_height = info->box->height / layout->vertical_subsampling[plane];
      uint32_t plane_height = height / layout->vertical_subsampling[plane];

      uint32_t plane_byte_ratio = layout->bytes_per_pixel[plane] / layout->bytes_per_pixel[0];
      uint32_t guest_plane_stride = (guest_stride0 * plane_byte_ratio)
            / layout->horizontal_subsampling[plane];
      uint32_t host_plane_stride = plane == 0
            ? host_map_stride0 : gbm_bo_get_stride_for_plane(bo, plane);

      uint32_t guest_resource_offset = guest_plane_offset;
      uint32_t host_resource_offset = host_plane_offset + (subsampled_y * host_plane_stride)
                                       + subsampled_x * layout->bytes_per_pixel[plane];

      uint8_t *host_address = (uint8_t*)addr + host_resource_offset;

      /*
       * Here we apply another hack. info->offset does not account for
       * info->box for planar resources and we need to make adjustments.
       */
      if (plane_count > 1) {
         guest_resource_offset += (subsampled_y * guest_plane_stride)
            + subsampled_x * layout->bytes_per_pixel[plane];
      }

      virgl_gbm_transfer_internal(layout->bytes_per_pixel[plane], subsampled_width,
                                  subsampled_height, guest_plane_stride, guest_resource_offset,
                                  host_plane_stride, host_address, iovecs, num_iovecs, direction);

      if (info->layer_stride) {
         guest_plane_offset += (info->layer_stride * plane_byte_ratio)
            / (layout->horizontal_subsampling[plane] * layout->vertical_subsampling[plane]);
      } else {
         guest_plane_offset += plane_height * guest_plane_stride;
      }
   }

   gbm_bo_unmap(bo, map_data);
   return 0;
}

uint32_t virgl_gbm_convert_flags(uint32_t virgl_bind_flags)
{
   uint32_t flags = 0;
   if (virgl_bind_flags & VIRGL_BIND_SAMPLER_VIEW)
      flags |= GBM_BO_USE_TEXTURING;
   if (virgl_bind_flags & VIRGL_BIND_RENDER_TARGET)
      flags |= GBM_BO_USE_RENDERING;
   if (virgl_bind_flags & VIRGL_BIND_SCANOUT)
      flags |= GBM_BO_USE_SCANOUT;
   if (virgl_bind_flags & VIRGL_BIND_CURSOR)
      flags |= GBM_BO_USE_CURSOR;
   if (virgl_bind_flags & VIRGL_BIND_LINEAR)
      flags |= GBM_BO_USE_LINEAR;

   if (virgl_bind_flags & VIRGL_BIND_MINIGBM_CAMERA_WRITE)
      flags |= GBM_BO_USE_CAMERA_WRITE;
   if (virgl_bind_flags & VIRGL_BIND_MINIGBM_CAMERA_READ)
      flags |= GBM_BO_USE_CAMERA_READ;
   if (virgl_bind_flags & VIRGL_BIND_MINIGBM_HW_VIDEO_DECODER)
      flags |= GBM_BO_USE_HW_VIDEO_DECODER;
   if (virgl_bind_flags & VIRGL_BIND_MINIGBM_HW_VIDEO_ENCODER)
      flags |= GBM_BO_USE_HW_VIDEO_ENCODER;

   if ((virgl_bind_flags & VIRGL_BIND_MINIGBM_PROTECTED) ==
       (uint32_t)VIRGL_BIND_MINIGBM_PROTECTED) {
      flags |= GBM_BO_USE_PROTECTED;
   } else {
      if (virgl_bind_flags & VIRGL_BIND_MINIGBM_SW_READ_OFTEN)
         flags |= GBM_BO_USE_SW_READ_OFTEN;
      if (virgl_bind_flags & VIRGL_BIND_MINIGBM_SW_READ_RARELY)
         flags |= GBM_BO_USE_SW_READ_RARELY;
      if (virgl_bind_flags & VIRGL_BIND_MINIGBM_SW_WRITE_OFTEN)
         flags |= GBM_BO_USE_SW_WRITE_OFTEN;
      if (virgl_bind_flags & VIRGL_BIND_MINIGBM_SW_WRITE_RARELY)
         flags |= GBM_BO_USE_SW_WRITE_RARELY;
   }

   return flags;
}

int virgl_gbm_export_query(struct gbm_bo *bo, struct virgl_renderer_export_query *query)
{
   int ret = -1;
   uint32_t handles[VIRGL_GBM_MAX_PLANES] = { 0 };
   struct gbm_device *gbm = gbm_bo_get_device(bo);
   int num_planes = gbm_bo_get_plane_count(bo);
   if (num_planes < 0 || num_planes > VIRGL_GBM_MAX_PLANES)
      return ret;

   query->out_num_fds = 0;
   query->out_fourcc = 0;
   query->out_modifier = DRM_FORMAT_MOD_INVALID;
   for (int plane = 0; plane < VIRGL_GBM_MAX_PLANES; plane++) {
      query->out_fds[plane] = -1;
      query->out_strides[plane] = 0;
      query->out_offsets[plane] = 0;
   }

   for (int plane = 0; plane < num_planes; plane++) {
      uint32_t i, handle;
      query->out_strides[plane] = gbm_bo_get_stride_for_plane(bo, plane);
      query->out_offsets[plane] = gbm_bo_get_offset(bo, plane);
      handle = gbm_bo_get_handle_for_plane(bo, plane).u32;

      for (i = 0; i < query->out_num_fds; i++) {
         if (handles[i] == handle)
            break;
      }

      if (i == query->out_num_fds) {
         if (query->in_export_fds) {
            ret = virgl_gbm_export_fd(gbm, handle, &query->out_fds[query->out_num_fds]);
            if (ret)
               goto err_close;
         }
         handles[query->out_num_fds] = handle;
         query->out_num_fds++;
      }
   }

   query->out_modifier = gbm_bo_get_modifier(bo);
   query->out_fourcc = gbm_bo_get_format(bo);
   return 0;

err_close:
   for (int plane = 0; plane < VIRGL_GBM_MAX_PLANES; plane++) {
      if (query->out_fds[plane] >= 0) {
         close(query->out_fds[plane]);
         query->out_fds[plane] = -1;
      }

      query->out_strides[plane] = 0;
      query->out_offsets[plane] = 0;
   }

   query->out_num_fds = 0;
   return ret;
}

uint32_t virgl_gbm_get_map_info(struct gbm_bo *bo) {
   uint32_t map_info;
#if defined(MINIGBM) && defined(MINIGBM_HAS_GBM_BO_GET_MAP_INFO)
   enum gbm_bo_map_cache_mode cache_mode = gbm_bo_get_map_info(bo);
   switch(cache_mode) {
   /* case GBM_BO_MAP_CACHE_NONE: */
   /*    map_info = VIRGL_RENDERER_MAP_CACHE_NONE; */
   /*    break; */
   case GBM_BO_MAP_CACHE_CACHED:
      map_info = VIRGL_RENDERER_MAP_CACHE_CACHED;
      break;
   /* case GBM_BO_MAP_CACHE_UNCACHED: */
   /*    map_info = VIRGL_RENDERER_MAP_CACHE_UNCACHED; */
   /*    break; */
   case GBM_BO_MAP_CACHE_WC:
   default:
      map_info = VIRGL_RENDERER_MAP_CACHE_WC;
      break;
   }
#else
   /* That GPU allocations on all intel devices are CACHED is famously untrue
    * on Intel devices that don't have a shared last-level-cache architecture to
    * ensure memory coherency between CPU/GPU cores (e.g. GLK). Thus, mistakenly
    * reporting CACHED mapping type would cause stale cached CPU access to GPU
    * writes that are resident only in system memory.
    *
    * Still this heuristic is retained as a fallback for the missing minigbm API
    */
   struct gbm_device *gbm = gbm_bo_get_device(bo);
   if (!strcmp(gbm_device_get_backend_name(gbm), "i915"))
      map_info = VIRGL_RENDERER_MAP_CACHE_CACHED;
   else
      map_info = VIRGL_RENDERER_MAP_CACHE_WC;

   (void)bo;
#endif
   return map_info;
}
#endif // #ifdef ENABLE_GBM_ALLOCATION

int virgl_gbm_export_fd(struct gbm_device *gbm, uint32_t handle, int32_t *out_fd)
{
   int ret;
   ret = drmPrimeHandleToFD(gbm_device_get_fd(gbm), handle, DRM_CLOEXEC | DRM_RDWR, out_fd);
   // Kernels with older DRM core versions block DRM_RDWR but give a
   // read/write mapping anyway.
   if (ret)
      ret = drmPrimeHandleToFD(gbm_device_get_fd(gbm), handle, DRM_CLOEXEC, out_fd);

   return ret;
}

int virgl_gbm_get_plane_width(struct gbm_bo *bo, int plane) {
   uint32_t format = gbm_bo_get_format(bo);
   const struct planar_layout *layout = layout_from_format(format);
   if (!layout)
      return -1;
   return gbm_bo_get_width(bo) / layout->horizontal_subsampling[plane];
}

int virgl_gbm_get_plane_height(struct gbm_bo *bo, int plane) {
   uint32_t format = gbm_bo_get_format(bo);
   const struct planar_layout *layout = layout_from_format(format);
   if (!layout)
      return -1;
   return gbm_bo_get_height(bo) / layout->vertical_subsampling[plane];
}

int virgl_gbm_get_plane_bytes_per_pixel(struct gbm_bo *bo, int plane) {
   uint32_t format = gbm_bo_get_format(bo);
   const struct planar_layout *layout = layout_from_format(format);
   if (!layout)
      return -1;
   return layout->bytes_per_pixel[plane];
}

bool virgl_gbm_external_allocation_preferred(uint32_t flags) {
   return (flags & (VIRGL_RES_BIND_SCANOUT | VIRGL_RES_BIND_SHARED)) != 0;
}

bool virgl_gbm_gpu_import_required(uint32_t flags) {
   return !virgl_gbm_external_allocation_preferred(flags) ||
          (flags & (VIRGL_BIND_RENDER_TARGET | VIRGL_BIND_SAMPLER_VIEW)) != 0;
}
