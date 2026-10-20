/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_INSTANCE_H
#define VKR_INSTANCE_H

#include "vkr_common.h"

#include "venus-protocol/vn_protocol_renderer_util.h"

struct vkr_instance {
   struct vkr_object base;

   PFN_vkGetInstanceProcAddr get_proc_addr;
   struct vn_instance_proc_table proc_table;

   uint32_t api_version;
   PFN_vkCreateDebugUtilsMessengerEXT create_debug_utils_messenger;
   PFN_vkDestroyDebugUtilsMessengerEXT destroy_debug_utils_messenger;
   VkDebugUtilsMessengerEXT validation_messenger;

   uint32_t physical_device_count;
   VkPhysicalDevice *physical_device_handles;
   struct vkr_physical_device **physical_devices;
};
VKR_DEFINE_OBJECT_CAST(instance, VK_OBJECT_TYPE_INSTANCE, VkInstance)

void
vkr_context_init_instance_dispatch(struct vkr_context *ctx);

void
vkr_instance_destroy(struct vkr_context *ctx,
                     struct vkr_instance *instance,
                     bool destroy_vk);

#endif /* VKR_INSTANCE_H */
