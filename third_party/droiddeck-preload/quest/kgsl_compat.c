/* SPDX-License-Identifier: GPL-3.0-or-later */
#include "kgsl_compat.h"
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#if __SIZEOF_POINTER__ == 8
_Static_assert(sizeof(struct kgsl_gpumem_alloc_id) == 48, "KGSL alloc ABI");
_Static_assert(sizeof(struct kgsl_gpuobj_alloc) == 48, "GPUOBJ alloc ABI");
_Static_assert(sizeof(struct kgsl_gpuobj_free) == 32, "GPUOBJ free ABI");
_Static_assert(sizeof(struct kgsl_gpuobj_info) == 48, "GPUOBJ info ABI");
#endif

static void report(const char *operation, int error) {
    static unsigned successes, failures;
    unsigned *counter = error ? &failures : &successes;
    if (__atomic_fetch_add(counter, 1, __ATOMIC_RELAXED) >= (error ? 8u : 1u)) return;
    int saved = errno;
#ifdef __ANDROID__
    __android_log_print(error ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, "QuestKGSL",
                        "%s: GPUOBJ compatibility path, errno=%d", operation, error);
#else
    fprintf(stderr, "[QuestKGSL] %s: GPUOBJ compatibility path, errno=%d\n", operation, error);
#endif
    errno = saved;
}

int quest_kgsl_ioctl(quest_ioctl_fn real, int fd, unsigned long request, void *arg) {
    int rc = real(fd, request, arg);
    if (rc != -1 || (errno != EACCES && errno != EPERM) || arg == NULL) return rc;

    if (request == IOCTL_KGSL_GPUMEM_ALLOC_ID) {
        struct kgsl_gpumem_alloc_id *old = arg;
        struct kgsl_gpuobj_alloc alloc = {.size = old->size, .flags = old->flags};
        rc = real(fd, IOCTL_KGSL_GPUOBJ_ALLOC, &alloc);
        if (rc != 0) { report("alloc failed", errno); return rc; }
        struct kgsl_gpuobj_info info = {.id = alloc.id};
        rc = real(fd, IOCTL_KGSL_GPUOBJ_INFO, &info);
        int error = errno;
        if (rc == 0 && (info.size > SIZE_MAX || alloc.mmapsize > SIZE_MAX ||
                       info.gpuaddr > ULONG_MAX || info.flags > UINT_MAX)) {
            rc = -1;
            error = EOVERFLOW;
        }
        if (rc != 0) {
            struct kgsl_gpuobj_free release = {.id = alloc.id};
            if (real(fd, IOCTL_KGSL_GPUOBJ_FREE, &release) != 0)
                report("cleanup failed", errno);
            errno = error;
            report("info failed", error);
            return rc;
        }
        old->id = alloc.id;
        old->flags = (unsigned int)info.flags;
        old->size = info.size;
        old->mmapsize = alloc.mmapsize;
        old->gpuaddr = info.gpuaddr;
        report("allocation succeeded", 0);
        return 0;
    }
    if (request == IOCTL_KGSL_GPUMEM_FREE_ID) {
        const struct kgsl_gpumem_free_id *old = arg;
        struct kgsl_gpuobj_free release = {.id = old->id};
        rc = real(fd, IOCTL_KGSL_GPUOBJ_FREE, &release);
        if (rc != 0) report("free failed", errno);
    }
    return rc;
}
