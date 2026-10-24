#define _GNU_SOURCE 1
#include <stddef.h>
#include <sys/ioctl.h>

struct kgsl_devinfo {
    unsigned int device_id;
    unsigned int chip_id;
    unsigned int mmu_enabled;
    unsigned long gmem_gpubaseaddr;
    unsigned int gpu_id;
    size_t gmem_sizebytes;
};

struct kgsl_device_getproperty {
    unsigned int type;
    void *value;
    size_t sizebytes;
};

#define KGSL_IOC_TYPE 0x09
#define IOCTL_KGSL_DEVICE_GETPROPERTY _IOWR(KGSL_IOC_TYPE, 0x2, struct kgsl_device_getproperty)
#define KGSL_PROP_DEVICE_INFO 0x1

static const struct {
    unsigned int reported;
    unsigned int known;
} CHIP_IDS[] = {
    {0x44050A21, 0x44050A31},
};

void bl_kgsl_chip_id_fixup(unsigned long request, void *arg, int rc) {
    if (rc != 0 || request != IOCTL_KGSL_DEVICE_GETPROPERTY || arg == NULL) return;
    struct kgsl_device_getproperty *prop = arg;
    if (prop->type != KGSL_PROP_DEVICE_INFO || prop->value == NULL
        || prop->sizebytes < sizeof(struct kgsl_devinfo)) {
        return;
    }
    struct kgsl_devinfo *info = prop->value;
    for (size_t i = 0; i < sizeof(CHIP_IDS) / sizeof(CHIP_IDS[0]); i++) {
        if (info->chip_id == CHIP_IDS[i].reported) {
            info->chip_id = CHIP_IDS[i].known;
            return;
        }
    }
}
