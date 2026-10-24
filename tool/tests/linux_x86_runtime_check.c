#include <dlfcn.h>
#include <stdlib.h>
#include <unistd.h>

/* No display or game is opened. Check both guest loader and common libraries. */
int main(void) {
    const char *libraries[] = {"libc.so.6", "libstdc++.so.6", "libX11.so.6", "libGL.so.1"};
    void *allocation = malloc(4096);
    if (!allocation) return 1;
    free(allocation);
    for (unsigned i = 0; i < sizeof(libraries) / sizeof(libraries[0]); ++i) {
        void *handle = dlopen(libraries[i], RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            const char *message = dlerror();
            if (message) {
                const char *end = message;
                while (*end) ++end;
                write(2, message, end - message);
                write(2, "\n", 1);
            }
            return 2;
        }
        dlclose(handle);
    }
    return 0;
}
