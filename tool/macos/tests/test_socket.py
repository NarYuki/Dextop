"""Exercise the RPC address with real sockets and direct Linux syscalls."""

from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest


HERE = Path(__file__).resolve().parent.parent


@unittest.skipUnless(platform.system() == "Linux" and shutil.which("cc"), "Linux C compiler required")
class SocketTests(unittest.TestCase):
    def test_raw_datagrams_use_the_container_socket_namespace(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copyfile(HERE / "darling_socket.c", root / "darling_socket.c")
            shutil.copyfile(HERE.parent.parent / "android/app/src/main/cpp/linux_unixsock.c",
                            root / "dextop_linux_unixsock.c")
            (root / "test.c").write_text(r'''
#include <assert.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <unistd.h>
void dextop_macos_rpc_address(void *, unsigned);
int dextop_macos_unused_bind(int, const void *, unsigned);

static void exchange(const char *path) {
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    strcpy(address.sun_path, path);
    int receiver = socket(AF_UNIX, SOCK_DGRAM, 0);
    int sender = socket(AF_UNIX, SOCK_DGRAM, 0);
    assert(receiver >= 0 && sender >= 0);
    /* Model Android's refusal to create a filesystem socket by explicitly
       binding the abstract twin. The production naming function is shared. */
    dextop_macos_rpc_address(&address, sizeof(address));
    assert(address.sun_path[0] == 0);
    assert(strncmp(address.sun_path + 1, "dextop-macos-test/", 18) == 0);
    size_t length = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(address.sun_path + 1);
    assert(bind(receiver, (void *)&address, length) == 0);
    char data[] = "RPC";
    struct iovec vector = {.iov_base = data, .iov_len = sizeof(data)};
    struct msghdr message = {.msg_name = &address, .msg_namelen = length,
                            .msg_iov = &vector, .msg_iovlen = 1};
    assert(syscall(SYS_sendmsg, sender, &message, 0) == sizeof(data));
    char received[sizeof(data)];
    assert(recv(receiver, received, sizeof(received), MSG_DONTWAIT) == sizeof(data));
    assert(memcmp(received, data, sizeof(data)) == 0);
    close(sender);
    close(receiver);
}
int main(void) {
    struct sockaddr_un original = {.sun_family = AF_UNIX};
    strcpy(original.sun_path, "/unmodified.sock");
    assert(setenv("DEXTOP_DARLING_ABSTRACT_RPC", "0", 1) == 0);
    dextop_macos_rpc_address(&original, sizeof(original));
    assert(strcmp(original.sun_path, "/unmodified.sock") == 0);
    assert(setenv("DEXTOP_DARLING_ABSTRACT_RPC", "1", 1) == 0);
    assert(setenv("DEXTOP_SOCKET_NS", "dextop-macos-test", 1) == 0);
    exchange("/var/tmp/dextop/prefix/.darlingserver.sock");
    exchange("/var/tmp/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/.darlingserver.sock");
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    strcpy(address.sun_path + 1, "already-abstract");
    dextop_macos_rpc_address(&address, sizeof(address));
    assert(strcmp(address.sun_path + 1, "already-abstract") == 0);
    return 0;
}
''', encoding="utf-8")
            subprocess.run(["cc", "-O2", "-Wall", "-Wextra", str(root / "test.c"),
                            str(root / "darling_socket.c"), "-o", str(root / "test")], check=True)
            subprocess.run([str(root / "test")], check=True, timeout=10)
