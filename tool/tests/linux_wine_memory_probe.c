#define _GNU_SOURCE
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Run on Android/arm64 under the same shell-owned rootfs as Wine. The private
 * page must execute copied code without changing the file. Shared pages must
 * retain their backing, and non-PE files retain the kernel's denial. */
static void *page(int fd, int flags, size_t size) {
    return mmap(NULL, size, PROT_READ | PROT_WRITE, flags, fd, 0);
}

int main(void) {
    size_t size = (size_t)sysconf(_SC_PAGESIZE);
    const char *path = "/tmp/dextop-native-memory-probe.dll";
    int fd = open(path, O_RDWR | O_CREAT | O_TRUNC, 0600);
    if (fd < 0 || ftruncate(fd, (off_t)size)) return 2;
    uint32_t original[] = {0x52800220, 0xd65f03c0}; /* mov w0,#17; ret */
    if (write(fd, original, sizeof(original)) != sizeof(original)) return 3;
    uint32_t *code = page(fd, MAP_PRIVATE, size);
    if (code == MAP_FAILED) return 4;
    code[0] = 0x52800540; /* mov w0,#42 */
    setenv("DEXTOP_WINE_ANON_EXEC", "0", 1);
    if (!mprotect(code, size, PROT_READ | PROT_EXEC)) return 5;
    /* No Proton/game launcher opt-in: the session preload provides this. */
    unsetenv("DEXTOP_WINE_ANON_EXEC");
    if (mprotect(code, size, PROT_READ | PROT_EXEC)) return 6;
    if (((int (*)(void))code)() != 42) return 7;
    uint32_t unchanged;
    if (pread(fd, &unchanged, sizeof(unchanged), 0) != sizeof(unchanged) || unchanged != original[0]) return 8;
    void *shared = page(fd, MAP_SHARED, size);
    if (shared == MAP_FAILED) return 9;
    if (!mprotect(shared, size, PROT_READ | PROT_EXEC)) {
        if (mprotect(shared, size, PROT_READ | PROT_WRITE)) return 10;
        ((uint32_t *)shared)[0] = 0x52800420; /* 33 */
        if (msync(shared, size, MS_SYNC) || pread(fd, &unchanged, sizeof(unchanged), 0) != sizeof(unchanged) ||
            unchanged != 0x52800420 || ((int (*)(void))code)() != 42) return 11;
    }
    munmap(shared, size);
    munmap(code, size);
    const char *other = "/tmp/dextop-native-memory-probe.data";
    if (rename(path, other)) return 12;
    uint32_t *data = page(fd, MAP_PRIVATE, size);
    if (data == MAP_FAILED) return 13;
    data[0] = original[0];
    if (!mprotect(data, size, PROT_READ | PROT_EXEC)) return 14;
    munmap(data, size);
    close(fd);
    unlink(other);
    puts("ARM64_PE_EXEC_OK_SOURCE_UNCHANGED_SHARED_PRESERVED_NON_PE_DENIED");
    return 0;
}
