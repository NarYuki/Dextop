#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
int bl_status_without_tracer(const char *, int);
int main(void) {
    for (int cloexec = 0; cloexec <= 1; cloexec++) {
        int fd = bl_status_without_tracer("/proc/self/status", O_RDONLY | (cloexec ? O_CLOEXEC : 0));
        assert(fd >= 0);
        assert((fcntl(fd, F_GETFL) & O_ACCMODE) == O_RDONLY);
        assert(!!(fcntl(fd, F_GETFD) & FD_CLOEXEC) == cloexec);
        char text[16384]; ssize_t n = read(fd, text, sizeof(text)-1); assert(n > 0); text[n] = 0;
        assert(strstr(text, "\nTracerPid:\t0\n"));
        errno = 0; assert(write(fd, "x", 1) == -1 && errno == EBADF);
        close(fd);
    }
    assert(bl_status_without_tracer("/proc/self/status", O_PATH) == -1);
    FILE *maps = fopen("/proc/self/maps", "r"); assert(maps);
    char line[8192]; int found = 0;
    while (fgets(line, sizeof(line), maps)) {
        char *path = strchr(line, '/');
        if (!path || !strstr(path, "/libc.so.6")) continue;
        path[strcspn(path, "\n")] = 0;
        int fd = open(path, O_RDONLY); assert(fd >= 0); close(fd); found = 1; break;
    }
    fclose(maps); assert(found);
    puts("PASS: status descriptors preserve read-only and close-on-exec semantics");
    puts("PASS: mapped library host paths resolve inside the container");
    return 0;
}
