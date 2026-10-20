/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_COMMON_H
#define VKR_COMMON_H

#include "config.h"

#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "c11/threads.h"
#include "util/hash_table.h"
#include "util/list.h"
#include "util/macros.h"
#include "util/os_file.h"
#include "util/os_misc.h"
#include "util/u_math.h"
#include "util/u_memory.h"
#include "util/u_pointer.h"
#include "util/u_thread.h"
#include "venus-protocol/vulkan.h"
#include "virgl_context.h"
#include "virgl_util.h"
#include "virglrenderer.h"

#include "vkr_renderer.h"

/* cap instance and device api versions to this */
#define VKR_MAX_API_VERSION VK_API_VERSION_1_4

#define VKR_DEBUG(category) (unlikely(vkr_debug_flags & VKR_DEBUG_##category))

/* define a type-safe cast function */
#define VKR_DEFINE_OBJECT_CAST(vkr_type, vk_enum, vk_type)                               \
   static inline struct vkr_##vkr_type *vkr_##vkr_type##_from_handle(vk_type handle)     \
   {                                                                                     \
      struct vkr_##vkr_type *obj = (struct vkr_##vkr_type *)(uintptr_t)handle;           \
      if (obj) {                                                                         \
         assert(obj->base.type == vk_enum);                                              \
         assert(obj->base.id);                                                           \
         assert(obj->base.handle.vkr_type);                                              \
         assert((uintptr_t)obj->base.handle.vkr_type == obj->base.handle.u64);           \
      }                                                                                  \
      return obj;                                                                        \
   }

/* vkr_region_is_valid should be used to check for overflows */
#define VKR_REGION_INIT(offset, size)                                                    \
   {                                                                                     \
      .begin = (offset), .end = (offset) + (size)                                        \
   }

/* below STACK_ARRAY* utils came from mesa/src/vulkan/util/vk_util.h */
#define STACK_ARRAY_SIZE 8

/* Sometimes gcc may claim -Wmaybe-uninitialized for the stack array in some
 * places it can't verify that when size is 0 nobody down the call chain reads
 * the array. Please don't try to fix it by zero-initializing the array here
 * since it's used in a lot of different places. An "if (size == 0) return;"
 * may work for you.
 */
#define STACK_ARRAY(type, name, size)                                                    \
   type _stack_##name[STACK_ARRAY_SIZE];                                                 \
   type *const name =                                                                    \
      ((size) <= STACK_ARRAY_SIZE ? _stack_##name                                        \
                                  : (type *)malloc((size) * sizeof(type)))

#define STACK_ARRAY_FINISH(name)                                                         \
   if (name != _stack_##name)                                                            \
   free(name)

struct vn_info_extension_table;
struct vkr_context;
struct vkr_ring;
struct vkr_instance;
struct vkr_physical_device;
struct vkr_device;
struct vkr_queue;
struct vkr_fence;
struct vkr_semaphore;
struct vkr_event;
struct vkr_device_memory;
struct vkr_buffer;
struct vkr_buffer_view;
struct vkr_image;
struct vkr_image_view;
struct vkr_sampler;
struct vkr_sampler_ycbcr_conversion;
struct vkr_descriptor_set_layout;
struct vkr_descriptor_pool;
struct vkr_descriptor_set;
struct vkr_descriptor_update_template;
struct vkr_render_pass;
struct vkr_framebuffer;
struct vkr_query_pool;
struct vkr_shader_module;
struct vkr_pipeline_layout;
struct vkr_pipeline_cache;
struct vkr_pipeline;
struct vkr_command_pool;
struct vkr_command_buffer;
struct vkr_acceleration_structure;

typedef uint64_t vkr_object_id;

enum vkr_debug_flags {
   VKR_DEBUG_VALIDATE = 1 << 0,
   VKR_DEBUG_UDMABUF = 1 << 1,
};

/* base class for all objects */
struct vkr_object {
   VkObjectType type;
   vkr_object_id id;

   union {
      uint64_t u64;

      VkInstance instance;
      VkPhysicalDevice physical_device;
      VkDevice device;
      VkQueue queue;
      VkCommandBuffer command_buffer;

      VkBuffer buffer;
      VkImage image;
      VkSemaphore semaphore;
      VkFence fence;
      VkDeviceMemory device_memory;
      VkEvent event;
      VkQueryPool query_pool;
      VkBufferView buffer_view;
      VkImageView image_view;
      VkShaderModule shader_module;
      VkPipelineCache pipeline_cache;
      VkPipelineLayout pipeline_layout;
      VkPipeline pipeline;
      VkRenderPass render_pass;
      VkDescriptorSetLayout descriptor_set_layout;
      VkSampler sampler;
      VkDescriptorSet descriptor_set;
      VkDescriptorPool descriptor_pool;
      VkFramebuffer framebuffer;
      VkCommandPool command_pool;
      VkSamplerYcbcrConversion sampler_ycbcr_conversion;
      VkDescriptorUpdateTemplate descriptor_update_template;

      VkAccelerationStructureKHR acceleration_structure;
   } handle;

   struct list_head track_head;
};

struct object_array {
   uint32_t count;
   void **objects;
   void *handle_storage;

   /* true if the ownership of the objects has been transferred (to
    * vkr_context::object_table)
    */
   bool objects_stolen;
};

struct vkr_region {
   size_t begin;
   size_t end;
};

extern uint32_t vkr_debug_flags;

void
vkr_debug_init(void);

void
vkr_log(const char *fmt, ...);

static inline uint32_t
vkr_api_version_cap_minor(uint32_t version, uint32_t cap)
{
   assert(VK_API_VERSION_MAJOR(version) == VK_API_VERSION_MAJOR(cap));
   if (VK_API_VERSION_MINOR(version) > VK_API_VERSION_MINOR(cap))
      version = cap - VK_API_VERSION_PATCH(cap) + VK_API_VERSION_PATCH(version);
   return version;
}

void
vkr_extension_table_init(struct vn_info_extension_table *table,
                         const char *const *exts,
                         uint32_t count);

uint32_t
vkr_extension_get_spec_version(const char *name);

bool
object_array_init(struct vkr_context *ctx,
                  struct object_array *arr,
                  uint32_t count,
                  VkObjectType obj_type,
                  size_t obj_size,
                  size_t handle_size,
                  const void *handles);

void
object_array_fini(struct object_array *arr);

static inline void *
vkr_find_struct(const void *chain, VkStructureType type)
{
   VkBaseOutStructure *s = (VkBaseOutStructure *)chain;
   while (s) {
      if (s->sType == type)
         return s;
      s = s->pNext;
   }
   return NULL;
}

/*
 * Find struct in the pNext of chain and return its previous struct.
 */
static inline void *
vkr_find_prev_struct(const void *chain, VkStructureType type)
{
   VkBaseOutStructure *prev = (VkBaseOutStructure *)chain;
   while (prev->pNext) {
      if (prev->pNext->sType == type)
         return prev;
      prev = prev->pNext;
   }
   return NULL;
}

static inline bool
vkr_is_recognized_object_type(VkObjectType type)
{
   switch (type) {
   /* VK_VERSION_1_0 */
   case VK_OBJECT_TYPE_INSTANCE:
   case VK_OBJECT_TYPE_PHYSICAL_DEVICE:
   case VK_OBJECT_TYPE_DEVICE:
   case VK_OBJECT_TYPE_QUEUE:
   case VK_OBJECT_TYPE_SEMAPHORE:
   case VK_OBJECT_TYPE_COMMAND_BUFFER:
   case VK_OBJECT_TYPE_FENCE:
   case VK_OBJECT_TYPE_DEVICE_MEMORY:
   case VK_OBJECT_TYPE_BUFFER:
   case VK_OBJECT_TYPE_IMAGE:
   case VK_OBJECT_TYPE_EVENT:
   case VK_OBJECT_TYPE_QUERY_POOL:
   case VK_OBJECT_TYPE_BUFFER_VIEW:
   case VK_OBJECT_TYPE_IMAGE_VIEW:
   case VK_OBJECT_TYPE_SHADER_MODULE:
   case VK_OBJECT_TYPE_PIPELINE_CACHE:
   case VK_OBJECT_TYPE_PIPELINE_LAYOUT:
   case VK_OBJECT_TYPE_RENDER_PASS:
   case VK_OBJECT_TYPE_PIPELINE:
   case VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT:
   case VK_OBJECT_TYPE_SAMPLER:
   case VK_OBJECT_TYPE_DESCRIPTOR_POOL:
   case VK_OBJECT_TYPE_DESCRIPTOR_SET:
   case VK_OBJECT_TYPE_FRAMEBUFFER:
   case VK_OBJECT_TYPE_COMMAND_POOL:
   /* VK_VERSION_1_1 */
   case VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION:
   case VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE:
   /* VK_KHR_acceleration_structure */
   case VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_KHR:
      return true;
   default:
      vkr_log("unrecognized object type %u", type);
      return false;
   }
}

static inline void *
vkr_object_alloc(size_t size, VkObjectType type, vkr_object_id id)
{
   assert(size >= sizeof(struct vkr_object));
   assert(vkr_is_recognized_object_type(type));

   struct vkr_object *obj = calloc(1, size);
   if (!obj)
      return NULL;

   /* obj is only half-initialized */
   obj->type = type;
   obj->id = id;

   return obj;
}

static inline bool
vkr_region_is_valid(const struct vkr_region *region)
{
   return region->begin <= region->end;
}

static inline size_t
vkr_region_size(const struct vkr_region *region)
{
   return region->end - region->begin;
}

static inline bool
vkr_region_is_aligned(const struct vkr_region *region, size_t align)
{
   assert(util_is_power_of_two_nonzero(align));
   return !((region->begin | region->end) & (align - 1));
}

static inline bool
vkr_region_is_disjoint(const struct vkr_region *region, const struct vkr_region *other)
{
   return region->begin >= other->end || region->end <= other->begin;
}

static inline bool
vkr_region_is_within(const struct vkr_region *region, const struct vkr_region *other)
{
   /* note that when region regresses to a point at other->end, both this
    * function and vkr_region_is_disjoint return true
    */
   return region->begin >= other->begin && region->end <= other->end;
}

static inline struct vkr_region
vkr_region_make_relative(const struct vkr_region *region)
{
   return (struct vkr_region){
      .end = region->end - region->begin,
   };
}

static inline bool
vkr_seqno_ge(uint32_t a, uint32_t b)
{
   /* a >= b, but deal with wrapping as well */
   return (a - b) <= INT32_MAX;
}

#endif /* VKR_COMMON_H */
