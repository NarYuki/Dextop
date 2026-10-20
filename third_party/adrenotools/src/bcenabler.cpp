// SPDX-License-Identifier: BSD-2-Clause
// Copyright © 2021 Billy Laws


#include <fstream>
#include <string>
#include <cstring>
#include <sys/mman.h>
#include <unistd.h>
#include <adrenotools/bcenabler.h>
#include "gen/bcenabler_patch.h"

enum adrenotools_bcn_type adrenotools_get_bcn_type(uint32_t major, uint32_t minor, uint32_t vendorId) {
    if (vendorId != 0x5143 || major != 512)
        return ADRENOTOOLS_BCN_INCOMPATIBLE;

    if (minor >= 514)
        return ADRENOTOOLS_BCN_BLOB;

    return ADRENOTOOLS_BCN_PATCH;
}

static void *find_free_page(uintptr_t address) {
    std::ifstream procMaps("/proc/self/maps");

    uintptr_t end{};

    for (std::string line; std::getline(procMaps, line); ) {
        std::size_t addressSeparator{line.find('-')};
        uintptr_t start{std::strtoull(line.substr(0, addressSeparator).c_str(), nullptr, 16)};

        if (end > address && start != end)
            return reinterpret_cast<void *>(end);

        end = std::strtoull(line.substr(addressSeparator + 1, line.find( ' ')).c_str(), nullptr, 16);
    }

    return nullptr;
}

static void *align_ptr(void *ptr) {
    return reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(ptr) & ~(getpagesize() - 1));
}

bool adrenotools_patch_bcn(void *vkGetPhysicalDeviceFormatPropertiesFn) {
    union Branch {
        struct {
            int32_t offset : 26;
            uint8_t sig : 6;
        };

        uint32_t raw{};
    };
    static_assert(sizeof(Branch) == 4, "Branch size is invalid");

    void *patchPage{find_free_page(reinterpret_cast<uintptr_t>(vkGetPhysicalDeviceFormatPropertiesFn))};
    if (!patchPage)
        return false;

    void *ptr{mmap(patchPage, getpagesize(),  PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, 0, 0)};
    if (ptr != patchPage)
        return false;

    if (mprotect(align_ptr(vkGetPhysicalDeviceFormatPropertiesFn), getpagesize() * 2, PROT_WRITE | PROT_READ | PROT_EXEC))
        return false;

    Branch *blInst{reinterpret_cast<Branch *>(vkGetPhysicalDeviceFormatPropertiesFn)};

    constexpr uint8_t BranchLinkSignature{0x25};

    while (blInst->sig != BranchLinkSignature)
        blInst++;

    uint32_t *convFormatFn{reinterpret_cast<uint32_t *>(blInst) + blInst->offset};

    if (mprotect(align_ptr(convFormatFn), getpagesize() * 2, PROT_WRITE | PROT_READ | PROT_EXEC))
        return false;

    constexpr uint32_t ClearResultSignature{0x2a1f03e0};

    uint32_t *clearResultPtr{convFormatFn};
    while (*clearResultPtr != ClearResultSignature)
        clearResultPtr++;

    if (PatchRawData_size > getpagesize())
        return false;

    memcpy(patchPage, PatchRawData, PatchRawData_size);

    constexpr uint32_t PatchReturnFixupMagic{0xffffffff};
    constexpr uint8_t BranchSignature{0x5};

    uint32_t *fixupTargetPtr{clearResultPtr + 1};
    auto *fixupPtr{reinterpret_cast<uint32_t *>(patchPage)};
    for (long unsigned int i{}; i < (PatchRawData_size / sizeof(uint32_t)); i++, fixupPtr++) {
        if (*fixupPtr == PatchReturnFixupMagic) {
            Branch branchToDriver{
                {
                    .offset = static_cast<int32_t>((reinterpret_cast<intptr_t>(fixupTargetPtr) - reinterpret_cast<intptr_t>(fixupPtr)) / sizeof(int32_t)),
                    .sig = BranchSignature,
                }
            };

            *fixupPtr = branchToDriver.raw;
        }
    }

    Branch branchToPatch{
        {
            .offset = static_cast<int32_t>((reinterpret_cast<intptr_t>(patchPage) - reinterpret_cast<intptr_t>(clearResultPtr)) / sizeof(int32_t)),
            .sig = BranchSignature,
        }
    };

    *clearResultPtr = branchToPatch.raw;

    asm volatile("ISB");

    return true;
}
