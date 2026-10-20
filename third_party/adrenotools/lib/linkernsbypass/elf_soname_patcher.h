// SPDX-License-Identifier: BSD-2-Clause
// Copyright © 2021 Billy Laws


#pragma once

#ifdef __cplusplus
extern "C" {
#endif

bool elf_soname_patch(const char *elfPath, int targetFd, const char *newSoname);

#ifdef __cplusplus
}
#endif
