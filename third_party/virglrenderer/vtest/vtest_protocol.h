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

#ifndef VTEST_PROTOCOL
#define VTEST_PROTOCOL

#define VTEST_DEFAULT_SOCKET_NAME "/tmp/.virgl_test"

#define VTEST_PROTOCOL_VERSION 4

/* 32-bit length field */
/* 32-bit cmd field */
#define VTEST_HDR_SIZE 2
#define VTEST_CMD_LEN 0 /* length of data */
#define VTEST_CMD_ID  1
#define VTEST_CMD_DATA_START 2

/* vtest cmds */
#define VCMD_GET_CAPS 1

#define VCMD_RESOURCE_CREATE 2
#define VCMD_RESOURCE_UNREF 3

#define VCMD_TRANSFER_GET 4
#define VCMD_TRANSFER_PUT 5

#define VCMD_SUBMIT_CMD 6

#define VCMD_RESOURCE_BUSY_WAIT 7

/* pass the process cmd line for debugging */
#define VCMD_CREATE_RENDERER 8

#define VCMD_GET_CAPS2 9
/* get caps */
/* 0 length cmd */
/* resp VCMD_GET_CAPS + caps */

#define VCMD_PING_PROTOCOL_VERSION 10

#define VCMD_PROTOCOL_VERSION 11

/* since protocol version 2 */
#define VCMD_RESOURCE_CREATE2 12
#define VCMD_TRANSFER_GET2 13
#define VCMD_TRANSFER_PUT2 14

/* since protocol version 3 */
#define VCMD_GET_PARAM 15
#define VCMD_GET_CAPSET 16
#define VCMD_CONTEXT_INIT 17
#define VCMD_RESOURCE_CREATE_BLOB 18
#define VCMD_SYNC_CREATE 19
#define VCMD_SYNC_UNREF 20
#define VCMD_SYNC_READ 21
#define VCMD_SYNC_WRITE 22
#define VCMD_SYNC_WAIT 23
#define VCMD_SUBMIT_CMD2 24

/* since protocol version 4 */
#define VCMD_DRM_SYNC_CREATE 25
#define VCMD_DRM_SYNC_DESTROY 26
#define VCMD_DRM_SYNC_HANDLE_TO_FD 27
#define VCMD_DRM_SYNC_FD_TO_HANDLE 28
#define VCMD_DRM_SYNC_IMPORT_SYNC_FILE 29
#define VCMD_DRM_SYNC_EXPORT_SYNC_FILE 30
#define VCMD_DRM_SYNC_WAIT 31
#define VCMD_DRM_SYNC_RESET 32
#define VCMD_DRM_SYNC_SIGNAL 33
#define VCMD_DRM_SYNC_TIMELINE_SIGNAL 34
#define VCMD_DRM_SYNC_TIMELINE_WAIT 35
#define VCMD_DRM_SYNC_QUERY 36
#define VCMD_DRM_SYNC_TRANSFER 37
#define VCMD_RESOURCE_EXPORT_FD 38

#define VCMD_RES_CREATE_SIZE 10
#define VCMD_RES_CREATE_RES_HANDLE 0 /* must be 0 since protocol version 3 */
#define VCMD_RES_CREATE_TARGET 1
#define VCMD_RES_CREATE_FORMAT 2
#define VCMD_RES_CREATE_BIND 3
#define VCMD_RES_CREATE_WIDTH 4
#define VCMD_RES_CREATE_HEIGHT 5
#define VCMD_RES_CREATE_DEPTH 6
#define VCMD_RES_CREATE_ARRAY_SIZE 7
#define VCMD_RES_CREATE_LAST_LEVEL 8
#define VCMD_RES_CREATE_NR_SAMPLES 9
/* resp res_id since protocol version 3 */

#define VCMD_RES_CREATE2_SIZE 11
#define VCMD_RES_CREATE2_RES_HANDLE 0 /* must be 0 since protocol version 3 */
#define VCMD_RES_CREATE2_TARGET 1
#define VCMD_RES_CREATE2_FORMAT 2
#define VCMD_RES_CREATE2_BIND 3
#define VCMD_RES_CREATE2_WIDTH 4
#define VCMD_RES_CREATE2_HEIGHT 5
#define VCMD_RES_CREATE2_DEPTH 6
#define VCMD_RES_CREATE2_ARRAY_SIZE 7
#define VCMD_RES_CREATE2_LAST_LEVEL 8
#define VCMD_RES_CREATE2_NR_SAMPLES 9
#define VCMD_RES_CREATE2_DATA_SIZE 10
/* resp res_id since protocol version 3, and fd if data_size >0 */

#define VCMD_RES_UNREF_SIZE 1
#define VCMD_RES_UNREF_RES_HANDLE 0

#define VCMD_TRANSFER_HDR_SIZE 11
#define VCMD_TRANSFER_RES_HANDLE 0
#define VCMD_TRANSFER_LEVEL 1
#define VCMD_TRANSFER_STRIDE 2
#define VCMD_TRANSFER_LAYER_STRIDE 3
#define VCMD_TRANSFER_X 4
#define VCMD_TRANSFER_Y 5
#define VCMD_TRANSFER_Z 6
#define VCMD_TRANSFER_WIDTH 7
#define VCMD_TRANSFER_HEIGHT 8
#define VCMD_TRANSFER_DEPTH 9
#define VCMD_TRANSFER_DATA_SIZE 10

#define VCMD_TRANSFER2_HDR_SIZE 10
#define VCMD_TRANSFER2_RES_HANDLE 0
#define VCMD_TRANSFER2_LEVEL 1
#define VCMD_TRANSFER2_X 2
#define VCMD_TRANSFER2_Y 3
#define VCMD_TRANSFER2_Z 4
#define VCMD_TRANSFER2_WIDTH 5
#define VCMD_TRANSFER2_HEIGHT 6
#define VCMD_TRANSFER2_DEPTH 7
#define VCMD_TRANSFER2_DATA_SIZE 8
#define VCMD_TRANSFER2_OFFSET 9

#define VCMD_BUSY_WAIT_FLAG_WAIT 1

#define VCMD_BUSY_WAIT_SIZE 2
#define VCMD_BUSY_WAIT_HANDLE 0
#define VCMD_BUSY_WAIT_FLAGS 1

#define VCMD_PING_PROTOCOL_VERSION_SIZE 0

#define VCMD_PROTOCOL_VERSION_SIZE 1
#define VCMD_PROTOCOL_VERSION_VERSION 0

enum vcmd_param  {
   VCMD_PARAM_MAX_TIMELINE_COUNT = 1,
   VCMD_PARAM_HAS_TIMELINE_SYNCOBJ = 2,
};
#define VCMD_GET_PARAM_SIZE 1
#define VCMD_GET_PARAM_PARAM 0
/* resp param validity and value */

#define VCMD_GET_CAPSET_SIZE 2
#define VCMD_GET_CAPSET_ID 0
#define VCMD_GET_CAPSET_VERSION 1
/* resp capset validity and contents */

#define VCMD_CONTEXT_INIT_SIZE 1
#define VCMD_CONTEXT_INIT_CAPSET_ID 0

enum vcmd_blob_type {
   VCMD_BLOB_TYPE_GUEST        = 1,
   VCMD_BLOB_TYPE_HOST3D       = 2,
   VCMD_BLOB_TYPE_HOST3D_GUEST = 3,
};

enum vcmd_blob_flag {
   VCMD_BLOB_FLAG_MAPPABLE     = 1 << 0,
   VCMD_BLOB_FLAG_SHAREABLE    = 1 << 1,
   VCMD_BLOB_FLAG_CROSS_DEVICE = 1 << 2,
};

#define VCMD_RES_CREATE_BLOB_SIZE 6
#define VCMD_RES_CREATE_BLOB_TYPE 0
#define VCMD_RES_CREATE_BLOB_FLAGS 1
#define VCMD_RES_CREATE_BLOB_SIZE_LO 2
#define VCMD_RES_CREATE_BLOB_SIZE_HI 3
#define VCMD_RES_CREATE_BLOB_ID_LO 4
#define VCMD_RES_CREATE_BLOB_ID_HI 5
/* resp res_id and mmap'able fd */

#define VCMD_SYNC_CREATE_SIZE 2
#define VCMD_SYNC_CREATE_VALUE_LO 0
#define VCMD_SYNC_CREATE_VALUE_HI 1
/* resp sync id */

#define VCMD_SYNC_UNREF_SIZE 1
#define VCMD_SYNC_UNREF_ID 0

#define VCMD_SYNC_READ_SIZE 1
#define VCMD_SYNC_READ_ID 0
/* resp sync value */

#define VCMD_SYNC_WRITE_SIZE 3
#define VCMD_SYNC_WRITE_ID 0
#define VCMD_SYNC_WRITE_VALUE_LO 1
#define VCMD_SYNC_WRITE_VALUE_HI 2

enum vcmd_sync_wait_flag {
   VCMD_SYNC_WAIT_FLAG_ANY = 1 << 0,
};
#define VCMD_SYNC_WAIT_SIZE(count) (2 + 3 * count)
#define VCMD_SYNC_WAIT_FLAGS 0
#define VCMD_SYNC_WAIT_TIMEOUT 1
#define VCMD_SYNC_WAIT_ID(n)       (2 + 3 * (n) + 0)
#define VCMD_SYNC_WAIT_VALUE_LO(n) (2 + 3 * (n) + 1)
#define VCMD_SYNC_WAIT_VALUE_HI(n) (2 + 3 * (n) + 2)
/* resp poll'able fd */

enum vcmd_submit_cmd2_flag {
   VCMD_SUBMIT_CMD2_FLAG_RING_IDX = 1 << 0,
   VCMD_SUBMIT_CMD2_FLAG_IN_FENCE_FD = 1 << 1,
   VCMD_SUBMIT_CMD2_FLAG_OUT_FENCE_FD = 1 << 2,
};

struct vcmd_submit_cmd2_batch {
   uint32_t flags;

   uint32_t cmd_offset;
   uint32_t cmd_size;

   /* sync_count pairs of (id, val) starting at sync_offset */
   uint32_t sync_offset;
   uint32_t sync_count;

   /* ignored unless VCMD_SUBMIT_CMD2_FLAG_RING_IDX is set */
   uint32_t ring_idx;

   uint32_t num_in_syncobj;
   uint32_t num_out_syncobj;
};
#define VCMD_SUBMIT_CMD2_BATCH_COUNT 0
#define VCMD_SUBMIT_CMD2_BATCH_FLAGS(n)            (1 + 8 * (n) + 0)
#define VCMD_SUBMIT_CMD2_BATCH_CMD_OFFSET(n)       (1 + 8 * (n) + 1)
#define VCMD_SUBMIT_CMD2_BATCH_CMD_SIZE(n)         (1 + 8 * (n) + 2)
#define VCMD_SUBMIT_CMD2_BATCH_SYNC_OFFSET(n)      (1 + 8 * (n) + 3)
#define VCMD_SUBMIT_CMD2_BATCH_SYNC_COUNT(n)       (1 + 8 * (n) + 4)
#define VCMD_SUBMIT_CMD2_BATCH_RING_IDX(n)         (1 + 8 * (n) + 5)
#define VCMD_SUBMIT_CMD2_BATCH_NUM_IN_SYNCOBJ(n)   (1 + 8 * (n) + 6)
#define VCMD_SUBMIT_CMD2_BATCH_NUM_OUT_SYNCOBJ(n)  (1 + 8 * (n) + 7)

#define VCMD_DRM_SYNC_CREATE_SIZE 1
#define VCMD_DRM_SYNC_CREATE_FLAGS 0
/* resp sync handle */

#define VCMD_DRM_SYNC_DESTROY_SIZE 1
#define VCMD_DRM_SYNC_DESTROY_HANDLE 0

#define VCMD_DRM_SYNC_HANDLE_TO_FD_SIZE 1
#define VCMD_DRM_SYNC_HANDLE_TO_FD_HANDLE 0
/* resp sync fd */

#define VCMD_DRM_SYNC_FD_TO_HANDLE_SIZE 0
/* req sync fd */
/* resp sync handle */

#define VCMD_DRM_SYNC_IMPORT_SYNC_FILE_SIZE 1
#define VCMD_DRM_SYNC_IMPORT_SYNC_FILE_HANDLE 0
/* req sync fd */

#define VCMD_DRM_SYNC_EXPORT_SYNC_FILE_SIZE 1
#define VCMD_DRM_SYNC_EXPORT_SYNC_FILE_HANDLE 0
/* resp sync fd */

/* Extra flag for VCMD_DRM_SYNC_WAIT/VCMD_DRM_SYNC_TIMELINE_WAIT
 * on top of the existing drm ioctl flags, to indicate that the
 * client expects an fd back from the server to wait on.
 *
 * If the DRM_SYNCOBJ_WAIT_FLAGS_WAIT_FD flag is set, then
 * first_signaled and status is returned via the eventfd
 */
#define DRM_SYNCOBJ_WAIT_FLAGS_WAIT_FD (1 << 31)

#define VCMD_DRM_SYNC_WAIT_SIZE 4
#define VCMD_DRM_SYNC_WAIT_NUM_HANDLES 0
#define VCMD_DRM_SYNC_WAIT_TIMEOUT_LO 1
#define VCMD_DRM_SYNC_WAIT_TIMEOUT_HI 2
#define VCMD_DRM_SYNC_WAIT_FLAGS 3
/* req handles[num_handles]
 * if (flags & DRM_SYNCOBJ_WAIT_FLAGS_WAIT_FD) {
 *    rsp eventfd
 * } else {
 *    rsp first_signaled
 *    rsp status
 * }
 */

#define VCMD_DRM_SYNC_RESET_SIZE 1
#define VCMD_DRM_SYNC_RESET_NUM_HANDLES 0
/* req handles[num_handles] */

#define VCMD_DRM_SYNC_SIGNAL_SIZE 1
#define VCMD_DRM_SYNC_SIGNAL_NUM_HANDLES 0
/* req handles[num_handles] */

#define VCMD_DRM_SYNC_TIMELINE_SIGNAL_SIZE 1
#define VCMD_DRM_SYNC_TIMELINE_SIGNAL_NUM_HANDLES 0
/* req points[num_handles] */
/* req handles[num_handles] */

#define VCMD_DRM_SYNC_TIMELINE_WAIT_SIZE 4
#define VCMD_DRM_SYNC_TIMELINE_WAIT_NUM_HANDLES 0
#define VCMD_DRM_SYNC_TIMELINE_WAIT_TIMEOUT_LO 1
#define VCMD_DRM_SYNC_TIMELINE_WAIT_TIMEOUT_HI 2
#define VCMD_DRM_SYNC_TIMELINE_WAIT_FLAGS 3
/* req points[num_handles]
 * req handles[num_handles]
 * if (flags & DRM_SYNCOBJ_WAIT_FLAGS_WAIT_FD) {
 *    rsp eventfd
 * } else {
 *    rsp first_signaled
 *    rsp status
 * }
 */

#define VCMD_DRM_SYNC_QUERY_SIZE 2
#define VCMD_DRM_SYNC_QUERY_NUM_HANDLES 0
#define VCMD_DRM_SYNC_QUERY_FLAGS 1
/* rsp points[num_handles]*/

#define VCMD_DRM_SYNC_TRANSFER_SIZE 7
#define VCMD_DRM_SYNC_TRANSFER_DST_HANDLE 0
#define VCMD_DRM_SYNC_TRANSFER_DST_POINT_LO 1
#define VCMD_DRM_SYNC_TRANSFER_DST_POINT_HI 2
#define VCMD_DRM_SYNC_TRANSFER_SRC_HANDLE 3
#define VCMD_DRM_SYNC_TRANSFER_SRC_POINT_LO 4
#define VCMD_DRM_SYNC_TRANSFER_SRC_POINT_HI 5
#define VCMD_DRM_SYNC_TRANSFER_FLAGS 6

#define VCMD_RESOURCE_EXPORT_FD_SIZE 1
#define VCMD_RESOURCE_EXPORT_FD_RES_HANDLE 0
/* rsp fd */

#endif /* VTEST_PROTOCOL */
