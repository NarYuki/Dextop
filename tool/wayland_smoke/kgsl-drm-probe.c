#define _GNU_SOURCE
#include <xf86drm.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

/* User-space identity only: no DRM ioctls are emulated by this probe. */
struct kgsl_device {
    drmDevice device;
    char *nodes[DRM_NODE_MAX];
    drmPlatformBusInfo bus;
    drmPlatformDeviceInfo info;
    struct kgsl_device *next;
};
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static struct kgsl_device *devices;

static int is_kgsl(dev_t id) {
    struct stat info;
    return stat("/dev/kgsl-3d0", &info) == 0 && S_ISCHR(info.st_mode) && info.st_rdev == id;
}

int drmGetDeviceFromDevId(dev_t id, uint32_t flags, drmDevicePtr *result) {
    if (!is_kgsl(id)) {
        int (*original)(dev_t, uint32_t, drmDevicePtr *) = dlsym(RTLD_NEXT, "drmGetDeviceFromDevId");
        return original ? original(id, flags, result) : -ENOSYS;
    }
    if (!result) return -EINVAL;
    struct kgsl_device *device = calloc(1, sizeof(*device));
    if (!device) return -ENOMEM;
    device->nodes[DRM_NODE_PRIMARY] = "/dev/kgsl-3d0";
    device->nodes[DRM_NODE_RENDER] = "/dev/kgsl-3d0";
    device->device.nodes = device->nodes;
    device->device.available_nodes = (1 << DRM_NODE_PRIMARY) | (1 << DRM_NODE_RENDER);
    device->device.bustype = DRM_BUS_PLATFORM;
    strcpy(device->bus.fullname, "kgsl");
    device->device.businfo.platform = &device->bus;
    device->device.deviceinfo.platform = &device->info;
    pthread_mutex_lock(&lock);
    device->next = devices; devices = device;
    pthread_mutex_unlock(&lock);
    *result = &device->device;
    return 0;
}

void drmFreeDevice(drmDevicePtr *pointer) {
    if (!pointer || !*pointer) return;
    pthread_mutex_lock(&lock);
    struct kgsl_device **link = &devices;
    while (*link && &(*link)->device != *pointer) link = &(*link)->next;
    struct kgsl_device *owned = *link;
    if (owned) *link = owned->next;
    pthread_mutex_unlock(&lock);
    if (owned) { free(owned); *pointer = NULL; return; }
    void (*original)(drmDevicePtr *) = dlsym(RTLD_NEXT, "drmFreeDevice");
    if (original) original(pointer);
}
