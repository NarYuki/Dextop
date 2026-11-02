/* Build the same RPC address as Dextop's container-wide bind() fallback.
 * Mach-O code issues raw Linux syscalls, bypassing LD_PRELOAD interposition.
 * Reuse the naming implementation, including long-path hashing, unchanged.
 */
#define bind dextop_macos_unused_bind
#define connect dextop_macos_unused_connect
#include "dextop_linux_unixsock.c"
#undef bind
#undef connect

#include <string.h>
#include <stdlib.h>

void dextop_macos_rpc_address(void *address, unsigned length) {
    const char *enabled = getenv("DEXTOP_DARLING_ABSTRACT_RPC");
    if (!enabled || strcmp(enabled, "1") != 0) return;
    struct sockaddr_un_compat twin = {0};
    unsigned mapped = abstract_twin(address, length, &twin);
    if (mapped) memcpy(address, &twin, sizeof(twin));
}

/* Called through MLDR's ELF bridge after Darwin's sockaddr/path conversion.
 * Return Linux negative errno, as expected by libsystem_kernel.
 */
long dextop_macos_bind(int fd, const void *address, unsigned length) {
    int result = dextop_macos_unused_bind(fd, address, length);
    return result < 0 ? -*__errno_location() : result;
}

long dextop_macos_connect(int fd, const void *address, unsigned length) {
    int result = dextop_macos_unused_connect(fd, address, length);
    return result < 0 ? -*__errno_location() : result;
}
