// SPDX-License-Identifier: BSD-2-Clause
// Copyright © 2021 Billy Laws


#include <string>
#include <string_view>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <android/api-level.h>
#include <android/log.h>
#include <android_linker_ns.h>
#include "hook/kgsl.h"
#include "hook/hook_impl_params.h"
#include <adrenotools/driver.h>
#include <unistd.h>

void *adrenotools_open_libvulkan(int dlopenFlags, int featureFlags, const char *tmpLibDir, const char *hookLibDir, const char *customDriverDir, const char *customDriverName, const char *fileRedirectDir, void **userMappingHandle) {
    if (!linkernsbypass_load_status()) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "Could not load linkernsbypass\n");
        return nullptr;
    }    

    if (android_get_device_api_level() >= 29 && !tmpLibDir)
        tmpLibDir = nullptr;

    if (!(featureFlags & ADRENOTOOLS_DRIVER_FILE_REDIRECT) && fileRedirectDir) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "ADRENOTOOLS_DRIVER_FILE_REDIRECT present but no file redirect folder found\n");
        return nullptr;
    }
    
    if (!(featureFlags & ADRENOTOOLS_DRIVER_CUSTOM) && (customDriverDir || customDriverName)) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "ADRENOTOOLS_DRIVER_CUSTOM present but no custom driver name or folder found\n");
        return nullptr;
    }    

    if (!(featureFlags & ADRENOTOOLS_DRIVER_GPU_MAPPING_IMPORT) && userMappingHandle) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "ADRENOTOOLS_DRIVER_GPU_MAPPING_IMPORT present but no user mapping handle found\n");
        return nullptr;
    }   

    struct stat buf{};

    if (featureFlags & ADRENOTOOLS_DRIVER_CUSTOM) {
        if (!customDriverName || !customDriverDir) {
            __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "ADRENOTOOLS_DRIVER_CUSTOM present but no custom driver name or folder parameter was specified\n");
            return nullptr;
        }
              
        if (stat((std::string(customDriverDir) + customDriverName).c_str(), &buf) != 0) {
            __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "ADRENOTOOLS_DRIVER_CUSTOM present but importable driver doesn't exist'\n");
            return nullptr;
         }   
    }

    if (featureFlags & ADRENOTOOLS_DRIVER_FILE_REDIRECT) {
        if (!fileRedirectDir) {
            __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "ADRENOTOOLS_DRIVER_REDIRECT_DIR present but no folder parameter was found\n");
            return nullptr;
        }
            
        if (stat(fileRedirectDir, &buf) != 0) {
            __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "ADRENOTOOLS_DRIVER_REDIRECT_DIR present but specified redirect folder doesn't exist\n");
            return nullptr;
         }   
    }

    auto hookNs{android_create_namespace("adrenotools-libvulkan", hookLibDir, nullptr, ANDROID_NAMESPACE_TYPE_SHARED, nullptr, nullptr)};

    if (!linkernsbypass_link_namespace_to_default_all_libs(hookNs)) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "Failed to hook our namespace to the default one\n");
        return nullptr;
     }
    auto hookImpl{linkernsbypass_namespace_dlopen("libhook_impl.so", RTLD_NOW, hookNs)};
    if (!hookImpl) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "Couldn't preload the hook implementation\n");
        return nullptr;
    }    

    auto initHookParam{reinterpret_cast<void (*)(const void *)>(dlsym(hookImpl, "init_hook_param"))};
    if (!initHookParam) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "Couldn't init hook params\n");
        return nullptr;
    }   

    auto importMapping{[&]() -> adrenotools_gpu_mapping * {
        if (featureFlags & ADRENOTOOLS_DRIVER_GPU_MAPPING_IMPORT) {
            adrenotools_gpu_mapping *mapping{new adrenotools_gpu_mapping{}};
            *userMappingHandle = mapping;
            return mapping;
        } else {
            __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "Memory mapping flag was not specified\n");
            return nullptr;
        }
    }()};

    initHookParam(new HookImplParams(featureFlags, tmpLibDir, hookLibDir, customDriverDir, customDriverName, fileRedirectDir, importMapping));

    if (!linkernsbypass_namespace_dlopen("libmain_hook.so", RTLD_GLOBAL, hookNs)) {
        __android_log_print( ANDROID_LOG_ERROR, "adrenotools", "Failed to load libvulkan into the isolated namespace\n");
        return nullptr;
   }     

    return linkernsbypass_namespace_dlopen_unique("/system/lib64/libvulkan.so", tmpLibDir, dlopenFlags, hookNs);
}


bool adrenotools_import_user_mem(void *handle, void *hostPtr, uint64_t size) {
    auto importMapping{reinterpret_cast<adrenotools_gpu_mapping *>(handle)};

    kgsl_gpuobj_import_useraddr addr{
        .virtaddr = reinterpret_cast<uint64_t>(hostPtr),
    };

    kgsl_gpuobj_import userMemImport{
        .priv = reinterpret_cast<uint64_t>(&addr),
        .priv_len = size,
        .flags = KGSL_CACHEMODE_WRITEBACK << KGSL_CACHEMODE_SHIFT | KGSL_MEMFLAGS_IOCOHERENT,
        .type = KGSL_USER_MEM_TYPE_ADDR,

    };

    kgsl_gpuobj_info info{};

    int kgslFd{open("/dev/kgsl-3d0", O_RDWR)};
    if (kgslFd < 0)
        return false;

    int ret{ioctl(kgslFd, IOCTL_KGSL_GPUOBJ_IMPORT, &userMemImport)};
    if (ret)
        goto err;

    info.id = userMemImport.id;
    ret = ioctl(kgslFd, IOCTL_KGSL_GPUOBJ_INFO, &info);
    if (ret)
        goto err;

    importMapping->host_ptr = hostPtr;
    importMapping->gpu_addr = info.gpuaddr;
    importMapping->size = size;
    importMapping->flags = 0xc2600;

    close(kgslFd);
    return true;

err:
    close(kgslFd);
    return false;
}

bool adrenotools_mem_gpu_allocate(void *handle, uint64_t *size) {
    auto mapping{reinterpret_cast<adrenotools_gpu_mapping *>(handle)};

    kgsl_gpuobj_alloc gpuobjAlloc{
        .size = *size,
        .flags = KGSL_CACHEMODE_WRITEBACK << KGSL_CACHEMODE_SHIFT | KGSL_MEMFLAGS_IOCOHERENT,
    };

    kgsl_gpuobj_info info{};

    int kgslFd{open("/dev/kgsl-3d0", O_RDWR)};
    if (kgslFd < 0)
        return false;

    int ret{ioctl(kgslFd, IOCTL_KGSL_GPUOBJ_ALLOC, &gpuobjAlloc)};
    if (ret)
        goto err;

    *size = gpuobjAlloc.mmapsize;

    info.id = gpuobjAlloc.id;

    ret = ioctl(kgslFd, IOCTL_KGSL_GPUOBJ_INFO, &info);
    if (ret)
        goto err;

    mapping->host_ptr = nullptr;
    mapping->gpu_addr = info.gpuaddr;
    mapping->size = *size;
    mapping->flags = 0xc2600;

    close(kgslFd);
    return true;

err:
    close(kgslFd);
    return false;
}


bool adrenotools_mem_cpu_map(void *handle, void *hostPtr, uint64_t size) {
    auto mapping{reinterpret_cast<adrenotools_gpu_mapping *>(handle)};

    int kgslFd{open("/dev/kgsl-3d0", O_RDWR)};
    if (kgslFd < 0)
        return false;

    mapping->host_ptr = mmap(hostPtr, size, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_FIXED, kgslFd, mapping->gpu_addr);
    close(kgslFd);
    return mapping->host_ptr != nullptr;
}

bool adrenotools_validate_gpu_mapping(void *handle) {
    auto importMapping{reinterpret_cast<adrenotools_gpu_mapping *>(handle)};
    return importMapping->gpu_addr == ADRENOTOOLS_GPU_MAPPING_SUCCEEDED_MAGIC;
}

void adrenotools_set_turbo(bool turbo) {
    uint32_t enable{turbo ? 0U : 1U};
    kgsl_device_getproperty prop{
        .type = KGSL_PROP_PWRCTRL,
        .value = reinterpret_cast<void *>(&enable),
        .sizebytes = sizeof(enable),
    };

    int kgslFd{open("/dev/kgsl-3d0", O_RDWR)};
    if (kgslFd < 0)
        return;

    ioctl(kgslFd, IOCTL_KGSL_SETPROPERTY, &prop);
    close (kgslFd);
}
