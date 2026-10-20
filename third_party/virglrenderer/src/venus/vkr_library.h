/*
 * Copyright 2025 Collabora
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_LIBRARY_H
#define VKR_LIBRARY_H

#include <stdbool.h>

#include "venus-protocol/vulkan.h"

struct vulkan_library {
   void *handle;
   PFN_vkGetInstanceProcAddr GetInstanceProcAddr;
};

void
vkr_library_preload_icd(void);

#if defined(ENABLE_VULKAN_DLOAD)

bool
vkr_library_load(struct vulkan_library *lib);

void
vkr_library_unload(struct vulkan_library *lib);

#else

static inline bool
vkr_library_load(struct vulkan_library *lib)
{
   lib->handle = NULL;
   lib->GetInstanceProcAddr = vkGetInstanceProcAddr;
   return true;
}

static inline void
vkr_library_unload(struct vulkan_library *lib)
{
   lib->GetInstanceProcAddr = NULL;
}

#endif /* ENABLE_VULKAN_DLOAD */

#endif /* VKR_LIBRARY_H */
