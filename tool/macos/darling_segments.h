/* Dextop's opt-in mapping for executable Mach-O segments.
 * ARM64 TLS adaptation modifies private executable pages before restoring RX.
 * Android refuses execmod on the resulting file-backed COW pages. Load those
 * segments into anonymous RW storage instead, retaining the normal final
 * protection step. Data segments and the on-disk image remain unchanged.
 */
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>

static void *dextop_map_segment(void *address, size_t length, int protection,
                                int fd, off_t offset, const char **environment) {
    int enabled = 0;
    for (const char **entry = environment; entry && *entry; ++entry)
        if (!strcmp(*entry, "DEXTOP_DARLING_ANON_EXEC=1")) enabled = 1;
    if (!enabled || !(protection & PROT_EXEC))
        return xmmap(address, length, protection, MAP_FIXED | MAP_PRIVATE, fd, offset);

    void *mapped = xmmap(address, length, PROT_READ | PROT_WRITE,
                         MAP_FIXED | MAP_PRIVATE | MAP_ANON, -1, 0);
    if (mapped == MAP_FAILED) return mapped;
    size_t copied = 0;
    while (copied < length) {
        ssize_t count = pread(fd, (char *)mapped + copied, length - copied, offset + copied);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) {
            int error = count == 0 ? ENOEXEC : errno;
            munmap(mapped, length);
            errno = error;
            return MAP_FAILED;
        }
        copied += count;
    }
    __builtin___clear_cache((char *)mapped, (char *)mapped + length);
    if (mprotect(mapped, length, protection)) {
        int error = errno;
        munmap(mapped, length);
        errno = error;
        return MAP_FAILED;
    }
    return mapped;
}
