// SPDX-License-Identifier: BSD-2-Clause
// Copyright © 2021 Billy Laws


#pragma once

#ifdef __cplusplus
extern "C" {
#else
#include <stdbool.h>
#endif

#include <stdint.h>

enum adrenotools_bcn_type {
    ADRENOTOOLS_BCN_INCOMPATIBLE,
    ADRENOTOOLS_BCN_BLOB,
    ADRENOTOOLS_BCN_PATCH
};

enum adrenotools_bcn_type adrenotools_get_bcn_type(uint32_t major, uint32_t minor, uint32_t vendorId);

bool adrenotools_patch_bcn(void *vkGetPhysicalDeviceFormatPropertiesFn);

#ifdef __cplusplus
}
#endif
