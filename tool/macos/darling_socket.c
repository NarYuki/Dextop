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
