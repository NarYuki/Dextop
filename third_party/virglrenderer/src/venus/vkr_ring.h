/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_RING_H
#define VKR_RING_H

#include "vkr_common.h"

#include "venus-protocol/vn_protocol_renderer_defines.h"

/* We read from the ring buffer to a temporary buffer for
 * vkr_context_submit_cmd.  Until that is changed, we want to put a limit
 * on the size of the temporary buffer.  It also makes no sense to have huge
 * rings.
 *
 * This must not exceed UINT32_MAX because the ring head and tail are 32-bit.
 */
#define VKR_RING_BUFFER_MAX_SIZE (16u * 1024 * 1024)

/* The layout of a ring in a vkr_resource. This is parsed and
 * discarded by vkr_ring_create.
 */
struct vkr_ring_layout {
   const struct vkr_resource *resource;

   struct vkr_region head;
   struct vkr_region tail;
   struct vkr_region status;
   struct vkr_region buffer;
   struct vkr_region extra;
};

static_assert(ATOMIC_INT_LOCK_FREE == 2 && sizeof(atomic_uint) == 4,
              "vkr_ring_control requires lock-free 32-bit atomic_uint");

/* the control region of a ring */
struct vkr_ring_control {
   /* Pointers to ring head, tail, and status.
    *
    * Clients increment the tail after commands are added.  We increment the
    * head after commands are executed.  The status is updated when there is a
    * status change to the ring thread.
    */
   volatile atomic_uint *head;
   const volatile atomic_uint *tail;
   volatile atomic_uint *status;
};

/* the buffer region of a ring */
struct vkr_ring_buffer {
   uint32_t size;
   uint32_t mask;

   /* The current offset in the buffer region.  It is free-running and must be
    * masked to be between [0, size).
    */
   uint32_t cur;

   const uint8_t *data;
};

/* the extra region of a ring */
struct vkr_ring_extra {
   size_t offset;

   /* used for offset validation */
   struct vkr_region region;

   /* cache the latest offset->pointer result */
   size_t cached_offset;
   volatile atomic_uint *cached_data;
};

struct vkr_ring {
   /* used by the caller */
   vkr_object_id id;
   struct list_head head;

   /* ring regions */
   const struct vkr_resource *resource;
   struct vkr_ring_control control;
   struct vkr_ring_buffer buffer;
   struct vkr_ring_extra extra;

   /* ring cs */
   struct vkr_cs_encoder encoder;
   struct vkr_cs_decoder decoder;
   struct vn_dispatch_context dispatch;

   /* ring thread */
   uint64_t idle_timeout;
   void *cmd;

   mtx_t mutex;
   cnd_t cond;
   thrd_t thread;
   bool prio_valid;
   int prio;
   atomic_bool started;
   atomic_bool pending_notify;
   atomic_bool monitor;
   uint64_t virtqueue_seqno;
};

struct vkr_ring *
vkr_ring_create(const struct vkr_ring_layout *layout,
                struct vkr_context *ctx,
                uint64_t idle_timeout);

void
vkr_ring_destroy(struct vkr_ring *ring);

void
vkr_ring_start(struct vkr_ring *ring);

bool
vkr_ring_stop(struct vkr_ring *ring);

void
vkr_ring_notify(struct vkr_ring *ring);

bool
vkr_ring_write_extra(struct vkr_ring *ring, size_t offset, uint32_t val);

void
vkr_ring_submit_virtqueue_seqno(struct vkr_ring *ring, uint64_t seqno);

bool
vkr_ring_wait_virtqueue_seqno(struct vkr_ring *ring, uint64_t seqno);

static inline uint32_t
vkr_ring_load_head(const struct vkr_ring *ring)
{
   return atomic_load_explicit(ring->control.head, memory_order_acquire);
}

static inline void
vkr_ring_set_status_bits(struct vkr_ring *ring, uint32_t mask)
{
   atomic_fetch_or_explicit(ring->control.status, mask, memory_order_seq_cst);
}

#endif /* VKR_RING_H */
