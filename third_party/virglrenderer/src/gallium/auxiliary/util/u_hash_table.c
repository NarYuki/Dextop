/**************************************************************************
 *
 * Copyright 2008 VMware, Inc.
 * All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sub license, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice (including the
 * next paragraph) shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
 * IN NO EVENT SHALL VMWARE AND/OR ITS SUPPLIERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 **************************************************************************/

/**
 * @file
 * General purpose hash table implementation.
 * 
 * Just uses the cso_hash for now, but it might be better switch to a linear 
 * probing hash table implementation at some point -- as it is said they have 
 * better lookup and cache performance and it appears to be possible to write 
 * a lock-free implementation of such hash tables . 
 * 
 * @author José Fonseca <jfonseca@vmware.com>
 */


#include "pipe/p_compiler.h"
#include "util/u_debug.h"

#include "cso_cache/cso_hash.h"

#include "util/u_memory.h"
#include "util/u_pointer.h"
#include "util/u_hash_table.h"
#include "util/hash_table.h"
#include "ralloc.h"


struct util_hash_table
{
   struct hash_table table;
   
   /** free value */
   void (*destroy)(void *value);
};

struct util_hash_table *
util_hash_table_create(uint32_t (*hash)(const void *key),
                       bool (*equal)(const void *key1, const void *key2),
                       void (*destroy)(void *value))
{
   struct util_hash_table *ht;
   
   ht = ralloc(NULL, struct util_hash_table);
   if(!ht)
      return NULL;
   
   if (!_mesa_hash_table_init(&ht->table, ht, hash, equal)) {
      ralloc_free(ht);
      return NULL;
   }
   
   ht->destroy = destroy;
   
   return ht;
}

enum pipe_error
util_hash_table_set(struct util_hash_table *ht,
                    void *key,
                    void *value)
{
   uint32_t key_hash;
   struct hash_entry *item;

   assert(ht);
   if (!ht)
      return PIPE_ERROR_BAD_INPUT;

   if (!key)
      return PIPE_ERROR_BAD_INPUT;

   key_hash = ht->table.key_hash_function(key);

   item = _mesa_hash_table_search_pre_hashed(&ht->table, key_hash, key);
   if(item) {
      ht->destroy(item->data);
      item->data = value;
      return PIPE_OK;
   }

   item = _mesa_hash_table_insert_pre_hashed(&ht->table, key_hash, key, value);
   if(!item)
      return PIPE_ERROR_OUT_OF_MEMORY;

   return PIPE_OK;
}


void *
util_hash_table_get(struct util_hash_table *ht,
                    void *key)
{
   struct hash_entry *item;

   assert(ht);
   if (!ht)
      return NULL;

   if (!key)
      return NULL;

   item = _mesa_hash_table_search(&ht->table, key);
   if(!item)
      return NULL;

   return item->data;
}


void
util_hash_table_remove(struct util_hash_table *ht,
                       void *key)
{
   struct hash_entry *item;

   assert(ht);
   if (!ht)
      return;

   if (!key)
      return;

   item = _mesa_hash_table_search(&ht->table, key);
   if (!item)
      return;

   ht->destroy(item->data);
   _mesa_hash_table_remove(&ht->table, item);
}


void 
util_hash_table_clear(struct util_hash_table *ht)
{
   assert(ht);
   if (!ht)
      return;

   hash_table_foreach(&ht->table, item) {
      ht->destroy(item->data);
   }

   _mesa_hash_table_clear(&ht->table, NULL);
}


enum pipe_error
util_hash_table_foreach(struct util_hash_table *ht,
                     enum pipe_error (*callback)
                        (void *key, void *value, void *data),
                     void *data)
{
   assert(ht);
   if (!ht)
      return PIPE_ERROR_BAD_INPUT;

   hash_table_foreach(&ht->table, item) {
      enum pipe_error result = callback((void *)item->key, item->data, data);
      if (result != PIPE_OK)
         return result;
   }

   return PIPE_OK;
}


void
util_hash_table_destroy(struct util_hash_table *ht)
{
   assert(ht);
   if (!ht)
      return;

   hash_table_foreach(&ht->table, item) {
      ht->destroy(item->data);
   }

   ralloc_free(ht);
}
