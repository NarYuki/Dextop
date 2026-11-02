"""Apply Dextop's scoped socket adaptation to the pinned Darling source."""

from pathlib import Path
import shutil


HERE = Path(__file__).resolve().parent


def replace_once(path, old, new):
    content = path.read_text(encoding="utf-8")
    if content.count(old) != 1:
        raise RuntimeError(f"Pinned source does not match patch: {path}")
    path.write_text(content.replace(old, new), encoding="utf-8")


def apply(source):
    source = Path(source)
    mldr = source / "src/startup/mldr"
    shutil.copyfile(HERE / "darling_socket.c", mldr / "dextop_socket.c")
    shutil.copyfile(HERE.parent.parent / "android/app/src/main/cpp/linux_unixsock.c",
                    mldr / "dextop_linux_unixsock.c")
    replace_once(mldr / "CMakeLists.txt", "\tmldr.c\n", "\tmldr.c\n\tdextop_socket.c\n")
    replace_once(mldr / "mldr.c", "\t\tlr->socket_path = __dserver_socket_address_data.sun_path;",
                 "\t\tlr->socket_path = (char*)str;\n"
                 "\t\textern void dextop_macos_rpc_address(void*, unsigned);\n"
                 "\t\tdextop_macos_rpc_address(&__dserver_socket_address_data, "
                 "sizeof(__dserver_socket_address_data));")
    replace_once(mldr / "resources/dserver-rpc-defs.h",
                 "#define dserver_rpc_hooks_get_server_address_length() sizeof(__dserver_socket_address_data)",
                 "#define dserver_rpc_hooks_get_server_address_length() "
                 "(__dserver_socket_address_data.sun_path[0] ? sizeof(__dserver_socket_address_data) : "
                 "sizeof(__dserver_socket_address_data.sun_family) + 1 + "
                 "strlen(__dserver_socket_address_data.sun_path + 1))")
    xnu_header = source / ("src/external/xnu/darling/src/libsystem_kernel/emulation/"
                           "include/linux_premigration/resources/dserver-rpc-defs.h")
    replace_once(xnu_header,
                 "#define dserver_rpc_hooks_get_server_address_length() sizeof(struct linux_sockaddr_un)",
                 "static size_t dextop_dserver_address_length(void) {\n"
                 "\tconst struct linux_sockaddr_un* address = __dserver_socket_address();\n"
                 "\treturn address->sun_path[0] ? sizeof(*address) : "
                 "offsetof(struct linux_sockaddr_un, sun_path) + 1 + "
                 "__simple_strlen(address->sun_path + 1);\n}\n"
                 "#define dserver_rpc_hooks_get_server_address_length() dextop_dserver_address_length()")
