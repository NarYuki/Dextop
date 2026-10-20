/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_CS_H
#define VKR_CS_H

#include "vkr_common.h"

/* This is to avoid integer overflows and to catch bogus allocations (e.g.,
 * the guest driver encodes an uninitialized value).  In practice, the largest
 * allocations we've seen are from vkGetPipelineCacheData and are dozens of
 * MBs.
 */
#define VKR_CS_DECODER_TEMP_POOL_MAX_SIZE (1u * 1024 * 1024 * 1024)

struct vkr_cs_encoder {
   bool *fatal_error;

   /* protect stream resource */
   mtx_t mutex;

   struct {
      const struct vkr_resource *resource;
      size_t offset;
      size_t size;
      bool busy;
   } stream;

   uint8_t *cur;
   const uint8_t *end;
};

struct vkr_cs_decoder_saved_state {
   const uint8_t *cur;
   const uint8_t *end;

   uint32_t pool_buffer_count;
   uint8_t *pool_reset_to;
};

/*
 * We usually need many small allocations during decoding.  Those allocations
 * are suballocated from the temp pool.
 *
 * After a command is decoded, vkr_cs_decoder_reset_temp_pool is called to
 * reset pool->cur.  After an entire command stream is decoded,
 * vkr_cs_decoder_gc_temp_pool is called to garbage collect pool->buffers.
 */
struct vkr_cs_decoder_temp_pool {
   uint8_t **buffers;
   uint32_t buffer_count;
   uint32_t buffer_max;
   size_t total_size;

   uint8_t *reset_to;

   uint8_t *cur;
   const uint8_t *end;
};

struct vkr_cs_decoder {
   const struct hash_table *object_table;
   mtx_t *object_mutex;

   bool *fatal_error;
   struct vkr_cs_decoder_temp_pool temp_pool;

   /* Support vkExecuteCommandStreamsMESA for command buffer recording and indirect
    * submission. Only a single level nested decoder state is needed. Base level is
    * always from context or ring submit buffer, and no resource tracking is needed.
    */
   struct vkr_cs_decoder_saved_state saved_state;
   bool saved_state_valid;

   /* protect against resource destroy */
   mtx_t resource_mutex;
   const struct vkr_resource *resource;

   const uint8_t *cur;
   const uint8_t *end;
};

static inline int
vkr_cs_encoder_init(struct vkr_cs_encoder *enc, bool *fatal_error)
{
   memset(enc, 0, sizeof(*enc));
   enc->fatal_error = fatal_error;

   return mtx_init(&enc->mutex, mtx_plain);
}

static inline void
vkr_cs_encoder_fini(struct vkr_cs_encoder *enc)
{
   mtx_destroy(&enc->mutex);
}

static inline void
vkr_cs_encoder_set_fatal(const struct vkr_cs_encoder *enc)
{
   *enc->fatal_error = true;
}

void
vkr_cs_encoder_set_stream_locked(struct vkr_cs_encoder *enc,
                                 const struct vkr_resource *res,
                                 size_t offset,
                                 size_t size);

void
vkr_cs_encoder_seek_stream_locked(struct vkr_cs_encoder *enc, size_t pos);

static inline void
vkr_cs_encoder_set_stream(struct vkr_cs_encoder *enc,
                          const struct vkr_resource *res,
                          size_t offset,
                          size_t size)
{

   mtx_lock(&enc->mutex);
   vkr_cs_encoder_set_stream_locked(enc, res, offset, size);
   mtx_unlock(&enc->mutex);
}

static inline void
vkr_cs_encoder_seek_stream(struct vkr_cs_encoder *enc, size_t pos)
{
   mtx_lock(&enc->mutex);
   vkr_cs_encoder_seek_stream_locked(enc, pos);
   mtx_unlock(&enc->mutex);
}

static inline bool
vkr_cs_encoder_check_stream(struct vkr_cs_encoder *enc, const struct vkr_resource *res)
{
   mtx_lock(&enc->mutex);
   if (enc->stream.resource && enc->stream.resource == res) {
      if (enc->stream.busy) {
         mtx_unlock(&enc->mutex);
         return false;
      }
      /* TODO vkSetReplyCommandStreamMESA should support res_id 0 to unset. Until then,
       * and until we can ignore older guests, treat this as non-fatal. This can happen
       * when the driver side reply shmem has lost its last ref for being used as reply
       * shmem (it can still live in the driver side shmem cache but will be used for
       * other purposes the next time being allocated out).
       */
      vkr_cs_encoder_set_stream_locked(enc, NULL, 0, 0);
   }
   mtx_unlock(&enc->mutex);

   return true;
}

static inline bool
vkr_cs_encoder_acquire(struct vkr_cs_encoder *enc)
{
   mtx_lock(&enc->mutex);
   if (unlikely(!enc->stream.resource)) {
      vkr_cs_encoder_set_fatal(enc);
      mtx_unlock(&enc->mutex);
      return false;
   }
   enc->stream.busy = true;
   mtx_unlock(&enc->mutex);
   return true;
}

static inline void
vkr_cs_encoder_release(struct vkr_cs_encoder *enc)
{
   mtx_lock(&enc->mutex);
   assert(enc->stream.resource);
   enc->stream.busy = false;
   mtx_unlock(&enc->mutex);
}

static inline void
vkr_cs_encoder_write(struct vkr_cs_encoder *enc,
                     size_t size,
                     const void *val,
                     size_t val_size)
{
   assert(val_size <= size);

   if (unlikely(size > (size_t)(enc->end - enc->cur))) {
      vkr_log("failed to write the reply stream");
      vkr_cs_encoder_set_fatal(enc);
      return;
   }

   /* we should not rely on the compiler to optimize away memcpy... */
   if (enc->cur != val)
      memcpy(enc->cur, val, val_size);
   enc->cur += size;
}

int
vkr_cs_decoder_init(struct vkr_cs_decoder *dec, struct vkr_context *ctx);

void
vkr_cs_decoder_fini(struct vkr_cs_decoder *dec);

void
vkr_cs_decoder_reset(struct vkr_cs_decoder *dec);

static inline void
vkr_cs_decoder_set_fatal(const struct vkr_cs_decoder *dec)
{
   *((struct vkr_cs_decoder *)dec)->fatal_error = true;
}

static inline bool
vkr_cs_decoder_get_fatal(const struct vkr_cs_decoder *dec)
{
   return *dec->fatal_error;
}

static inline void
vkr_cs_decoder_set_buffer_stream(struct vkr_cs_decoder *dec,
                                 const void *data,
                                 size_t size)
{
   dec->cur = data;
   dec->end = dec->cur + size;
}

bool
vkr_cs_decoder_set_resource_stream(struct vkr_cs_decoder *dec,
                                   struct vkr_context *ctx,
                                   uint32_t res_id,
                                   size_t offset,
                                   size_t size);

static inline bool
vkr_cs_decoder_check_stream(struct vkr_cs_decoder *dec, const struct vkr_resource *res)
{
   mtx_lock(&dec->resource_mutex);
   const bool ok = dec->resource != res;
   mtx_unlock(&dec->resource_mutex);
   return ok;
}

static inline bool
vkr_cs_decoder_has_command(const struct vkr_cs_decoder *dec)
{
   return dec->cur < dec->end;
}

static inline bool
vkr_cs_decoder_has_saved_state(struct vkr_cs_decoder *dec)
{
   return dec->saved_state_valid;
}

void
vkr_cs_decoder_save_state(struct vkr_cs_decoder *dec);

void
vkr_cs_decoder_restore_state(struct vkr_cs_decoder *dec);

static inline bool
vkr_cs_decoder_peek_internal(const struct vkr_cs_decoder *dec,
                             size_t size,
                             void *val,
                             size_t val_size)
{
   assert(val_size <= size);

   if (unlikely(size > (size_t)(dec->end - dec->cur))) {
      vkr_log("failed to peek %zu bytes", size);
      vkr_cs_decoder_set_fatal(dec);
      memset(val, 0, val_size);
      return false;
   }

   /* we should not rely on the compiler to optimize away memcpy... */
   if (dec->cur != val)
      memcpy(val, dec->cur, val_size);
   return true;
}

static inline void
vkr_cs_decoder_read(struct vkr_cs_decoder *dec, size_t size, void *val, size_t val_size)
{
   if (vkr_cs_decoder_peek_internal(dec, size, val, val_size))
      dec->cur += size;
}

static inline void
vkr_cs_decoder_peek(const struct vkr_cs_decoder *dec,
                    size_t size,
                    void *val,
                    size_t val_size)
{
   vkr_cs_decoder_peek_internal(dec, size, val, val_size);
}

static inline struct vkr_object *
vkr_cs_decoder_lookup_object(const struct vkr_cs_decoder *dec,
                             vkr_object_id id,
                             VkObjectType type)
{
   struct vkr_object *obj;

   if (!id)
      return NULL;

   mtx_lock(dec->object_mutex);
   const struct hash_entry *entry =
      _mesa_hash_table_search((struct hash_table *)dec->object_table, &id);
   obj = likely(entry) ? entry->data : NULL;
   mtx_unlock(dec->object_mutex);
   if (unlikely(!obj || obj->type != type)) {
      if (obj)
         vkr_log("object %" PRIu64 " has type %d, not %d", id, obj->type, type);
      else
         vkr_log("failed to look up object %" PRIu64 " of type %d", id, type);
      vkr_cs_decoder_set_fatal(dec);
   }

   return obj;
}

static inline void
vkr_cs_decoder_reset_temp_pool(struct vkr_cs_decoder *dec)
{
   struct vkr_cs_decoder_temp_pool *pool = &dec->temp_pool;
   pool->cur = pool->reset_to;
}

bool
vkr_cs_decoder_alloc_temp_internal(struct vkr_cs_decoder *dec, size_t size);

static inline void *
vkr_cs_decoder_alloc_temp(struct vkr_cs_decoder *dec, size_t size)
{
   struct vkr_cs_decoder_temp_pool *pool = &dec->temp_pool;

   if (unlikely(size > (size_t)(pool->end - pool->cur))) {
      if (!vkr_cs_decoder_alloc_temp_internal(dec, size)) {
         vkr_log("failed to suballocate %zu bytes from the temp pool", size);
         vkr_cs_decoder_set_fatal(dec);
         return NULL;
      }
   }

   /* align to 64-bit after we know size is at most
    * VKR_CS_DECODER_TEMP_POOL_MAX_SIZE and cannot overflow
    */
   size = align64(size, 8);
   assert(size <= (size_t)(pool->end - pool->cur));

   void *ptr = pool->cur;
   pool->cur += size;
   return ptr;
}

static inline void *
vkr_cs_decoder_alloc_temp_array(struct vkr_cs_decoder *dec, size_t size, size_t count)
{
   size_t alloc_size;
   if (unlikely(__builtin_mul_overflow(size, count, &alloc_size))) {
      vkr_log("overflow in array allocation of %zu * %zu bytes", size, count);
      vkr_cs_decoder_set_fatal(dec);
      return NULL;
   }

   return vkr_cs_decoder_alloc_temp(dec, alloc_size);
}

static inline void *
vkr_cs_decoder_get_blob_storage(struct vkr_cs_decoder *dec, size_t size)
{
   return unlikely(size > (size_t)(dec->end - dec->cur)) ? NULL : (void *)dec->cur;
}

static inline void *
vkr_cs_encoder_get_blob_storage(struct vkr_cs_encoder *enc, size_t offset, size_t size)
{
   return unlikely(offset + size > (size_t)(enc->end - enc->cur))
             ? NULL
             : (void *)(enc->cur + offset);
}

static inline bool
vkr_cs_handle_indirect_id(VkObjectType type)
{
   /* Dispatchable handles may or may not have enough bits to store
    * vkr_object_id.  Non-dispatchable handles always have enough bits to
    * store vkr_object_id.
    *
    * This should compile to a constant after inlining.
    */
   switch (type) {
   case VK_OBJECT_TYPE_INSTANCE:
   case VK_OBJECT_TYPE_PHYSICAL_DEVICE:
   case VK_OBJECT_TYPE_DEVICE:
   case VK_OBJECT_TYPE_QUEUE:
   case VK_OBJECT_TYPE_COMMAND_BUFFER:
      return sizeof(VkInstance) < sizeof(vkr_object_id);
   default:
      return false;
   }
}

static inline vkr_object_id
vkr_cs_handle_load_id(const void **handle, VkObjectType type)
{
   const vkr_object_id *p = vkr_cs_handle_indirect_id(type)
                               ? *(const vkr_object_id **)handle
                               : (const vkr_object_id *)handle;
   return *p;
}

static inline void
vkr_cs_handle_store_id(void **handle, vkr_object_id id, VkObjectType type)
{
   vkr_object_id *p = vkr_cs_handle_indirect_id(type) ? *(vkr_object_id **)handle
                                                      : (vkr_object_id *)handle;
   *p = id;
}

#endif /* VKR_CS_H */
