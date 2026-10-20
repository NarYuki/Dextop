/*
 * Copyright 2023 Collabora, Ltd.
 * SPDX-License-Identifier: MIT
 */

#include "virgl_fence.h"

#include "c11/threads.h"
#include "util/hash_table.h"
#include "util/macros.h"
#include "util/os_file.h"

#ifndef WIN32
#include "util/libsync.h"
#endif

#include "virgl_util.h"

#define FENCE_HUNG_CHECK_TIME_SEC   10

struct virgl_fence {
   uint64_t id;
   int fd; /* sync file FD */
   struct timespec timestamp; /* for hung-checking */
};

static struct virgl_fence last_signalled_fence = { .fd = -1 };
static struct hash_table_u64 *virgl_fence_table;
static mtx_t virgl_fence_table_lock;

static void virgl_fence_table_cleanup_cb(struct hash_entry *entry)
{
   struct virgl_fence *fence = entry->data;
   if (fence->fd >= 0)
      close(fence->fd);
   free(fence);
}

void
virgl_fence_table_cleanup(void)
{
   _mesa_hash_table_u64_destroy(virgl_fence_table,
                                virgl_fence_table_cleanup_cb);

   virgl_fence_table = NULL;

   mtx_destroy(&virgl_fence_table_lock);

   if (last_signalled_fence.fd >= 0)
      close(last_signalled_fence.fd);
}

int
virgl_fence_table_init(void)
{
   virgl_fence_table = _mesa_hash_table_u64_create(NULL);
   if (!virgl_fence_table)
      return -ENOMEM;

   mtx_init(&virgl_fence_table_lock, mtx_plain);

   last_signalled_fence.id = 0;
   last_signalled_fence.fd = -1;

   return 0;
}

static void virgl_fence_table_retire_cb(UNUSED const void *key, void *data,
                                        UNUSED void *arg)
{
   struct virgl_fence *fence = data;
   bool retire = true;
   int err = 0;

#ifndef WIN32
   retire = !sync_wait(fence->fd, 0);

   if (!retire) {
      struct timespec now;
      clock_gettime(CLOCK_MONOTONIC, &now);

      if (errno == ETIME && now.tv_sec - fence->timestamp.tv_sec > FENCE_HUNG_CHECK_TIME_SEC) {
         virgl_info("%s: fence_id=%" PRIu64 " stuck for more than %d sec\n",
                    __func__, fence->id, FENCE_HUNG_CHECK_TIME_SEC);
         fence->timestamp = now;
      }

      if (errno != ETIME) {
         retire = true;
         err = -errno;
         virgl_error("%s: sync_wait failed for fence_id=%" PRIu64 " err=%d\n",
                     __func__, fence->id, err);
      }
   }
#endif
   if (retire) {
      if (!err) {
         if (last_signalled_fence.fd >= 0)
            close(last_signalled_fence.fd);

         last_signalled_fence.id = fence->id;
         last_signalled_fence.fd = os_dupfd_cloexec(fence->fd);
      }

      _mesa_hash_table_u64_remove(virgl_fence_table, fence->id);
      close(fence->fd);
      free(fence);
   }
}

static int
virgl_fence_set_fd_locked(uint64_t fence_id, int fd)
{
   struct virgl_fence *fence;

   /* walk up all fences and retire signaled ones */
   hash_table_u64_call_foreach(virgl_fence_table,
                               virgl_fence_table_retire_cb, NULL);

   fence = _mesa_hash_table_u64_search(virgl_fence_table, fence_id);
   if (fence)
      return -EBUSY;

   fence = calloc(1, sizeof(*fence));
   if (!fence)
      return -ENOMEM;

   fence->fd = os_dupfd_cloexec(fd);
   if (fence->fd < 0) {
      free(fence);
      return -errno;
   }

   fence->id = fence_id;
   clock_gettime(CLOCK_MONOTONIC, &fence->timestamp);

   _mesa_hash_table_u64_insert(virgl_fence_table, fence_id, fence);

   return 0;
}

/*
 * This function does not take ownership of the FD, caller is responsible
 * for closing it. Function is thread-safe.
 */
int
virgl_fence_set_fd(uint64_t fence_id, int fd)
{
   int ret;

   mtx_lock(&virgl_fence_table_lock);
   ret = virgl_fence_set_fd_locked(fence_id, fd);
   mtx_unlock(&virgl_fence_table_lock);

   if (ret)
      virgl_error("%s: failed err=%d\n", __func__, ret);

   return ret;
}

/*
 * Returns sync file FD for a given fence_id. Caller of this function
 * takes ownership of the returned FD and is responsible for closing it.
 *
 * If fence for a given fence_id isn't found, -1 is returned.
 *
 * Function is thread-safe.
 */
int
virgl_fence_get_fd(uint64_t fence_id)
{
   struct virgl_fence *fence;
   int fd = -1;

   mtx_lock(&virgl_fence_table_lock);

   fence = _mesa_hash_table_u64_search(virgl_fence_table, fence_id);
   if (fence)
      fd = os_dupfd_cloexec(fence->fd);

   mtx_unlock(&virgl_fence_table_lock);

   return fd;
}

/*
 * Returns sync file FD for the latest signalled fence. Caller of this
 * function  takes ownership of the returned FD and is responsible for
 * closing it.
 *
 * Returns -1 if fence never signalled.
 *
 * Function is thread-safe.
 */
int
virgl_fence_get_last_signalled_fence_fd(void)
{
   int fd = -1;

   mtx_lock(&virgl_fence_table_lock);

   if (last_signalled_fence.fd >= 0)
      fd = os_dupfd_cloexec(last_signalled_fence.fd);

   mtx_unlock(&virgl_fence_table_lock);

   return fd;
}
