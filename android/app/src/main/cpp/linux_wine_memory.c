/* Native ARM64 Wine maps PE images privately, then adds executable permission.
 * Android denies execmod on shell-owned data files. Preserve private PE pages
 * in anonymous executable mappings instead. Never alter shared/file data or
 * suppress an execution permission that native ARM64 code actually needs.
 */
#define _GNU_SOURCE
#include <sys/mman.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>

static int private_pe_range(uintptr_t start, uintptr_t end) {
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;
    char line[8192], permissions[5];
    uintptr_t lo, hi, covered = start;
    while (covered < end && fgets(line, sizeof(line), maps)) {
        if (sscanf(line, "%lx-%lx %4s", &lo, &hi, permissions) != 3 || hi <= covered) continue;
        if (lo > covered || permissions[0] != 'r' || permissions[3] != 'p') break;
        /* Restrict the fallback to Windows images, not arbitrary host mappings. */
        if (!strcasestr(line, ".dll") && !strcasestr(line, ".exe") &&
            !strcasestr(line, ".drv") && !strcasestr(line, ".ocx") &&
            !strcasestr(line, ".cpl") && !strcasestr(line, ".acm") &&
            !strcasestr(line, ".sys")) break;
        covered = hi;
    }
    fclose(maps);
    return covered >= end;
}

/* Called by the session hook after the real mprotect failed. */
int dextop_private_image_exec(void *address, size_t length, int protection,
                            int (*real)(void *, size_t, int), int rc) {
    int error = errno;
    const char *enabled = getenv("DEXTOP_WINE_ANON_EXEC");
    if (!rc || (error != EACCES && error != EPERM) || !(protection & PROT_EXEC) ||
        (enabled && !strcmp(enabled, "0")) || !length) return rc;
    long page = sysconf(_SC_PAGESIZE);
    if (page <= 0 || (uintptr_t)address % page || length > SIZE_MAX - (size_t)page) return rc;
    size_t rounded = (length + page - 1) & ~((size_t)page - 1);
    uintptr_t end = (uintptr_t)address + rounded;
    if (end < (uintptr_t)address || !private_pe_range((uintptr_t)address, end)) { errno = error; return rc; }
    void *copy = mmap(NULL, rounded, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (copy == MAP_FAILED) { errno = error; return rc; }
    memcpy(copy, address, rounded);
    __builtin___clear_cache(copy, (char *)copy + rounded);
    if (real(copy, rounded, protection)) { munmap(copy, rounded); errno = error; return rc; }
    void *moved = mremap(copy, rounded, rounded, MREMAP_MAYMOVE | MREMAP_FIXED, address);
    if (moved == MAP_FAILED) { munmap(copy, rounded); errno = error; return rc; }
    __builtin___clear_cache(address, (char *)address + rounded);
    return 0;
}
