/* Metadata for KGSL-backed GBM clients. Buffer allocation/export is performed
 * by Zink/Turnip; unsupported DRM requests still reach the kernel unchanged. */
#define _GNU_SOURCE
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <stdarg.h>
#include <dlfcn.h>
#include <pthread.h>
#include <string.h>
#include <errno.h>

struct drm_version {
    int major, minor, patch;
    size_t name_len;
    char *name;
    size_t date_len;
    char *date;
    size_t desc_len;
    char *desc;
};
#define DRM_VERSION _IOWR('d', 0, struct drm_version)
static pthread_once_t once = PTHREAD_ONCE_INIT;
static int (*next_ioctl)(int, unsigned long, ...);
static void resolve(void) { next_ioctl = dlsym(RTLD_NEXT, "ioctl"); }
static void copy_string(char *dest, size_t *length, const char *text) {
    size_t n = strlen(text);
    if (dest) memcpy(dest, text, *length < n ? *length : n);
    *length = n;
}
int ioctl(int fd, unsigned long request, ...) {
    va_list args;
    va_start(args, request);
    void *arg = va_arg(args, void *);
    va_end(args);
    if (request == DRM_VERSION && arg) {
        struct stat actual, kgsl;
        if (!fstat(fd, &actual) && !stat("/dev/kgsl-3d0", &kgsl) &&
            S_ISCHR(actual.st_mode) && actual.st_rdev == kgsl.st_rdev) {
            struct drm_version *version = arg;
            version->major = 0;
            version->minor = 0;
            version->patch = 0;
            copy_string(version->name, &version->name_len, "kgsl");
            copy_string(version->date, &version->date_len, "");
            copy_string(version->desc, &version->desc_len, "Dextop KGSL Vulkan/GBM compatibility");
            return 0;
        }
    }
    pthread_once(&once, resolve);
    if (!next_ioctl) { errno = ENOSYS; return -1; }
    return next_ioctl(fd, request, arg);
}
