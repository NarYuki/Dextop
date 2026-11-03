"""Apply Dextop's scoped socket adaptation to the pinned Darling source."""

from pathlib import Path
import shutil


HERE = Path(__file__).resolve().parent


def replace_once(path, old, new):
    content = path.read_text(encoding="utf-8")
    if content.count(old) != 1:
        raise RuntimeError(f"Pinned source does not match patch: {path}")
    path.write_text(content.replace(old, new), encoding="utf-8")


def patch_shutdown_diagnostics(source):
    shutdown = Path(source) / "src/startup/container-shutdown.h"
    replace_once(shutdown, "#include <sys/syscall.h>", r'''#include <sys/syscall.h>

/* Inspect only the process already pinned for this shutdown, without signals
 * or a process-name search. Do not log its environment or application data. */
static void shutdownReportHandle(int handle)
{
 char path[128], line[512]; int pid = -1;
 snprintf(path, sizeof(path), "/proc/self/fdinfo/%d", handle);
 FILE *f = fopen(path, "re");
 if (!f) return;
 while (fgets(line, sizeof(line), f))
  if (sscanf(line, "Pid: %d", &pid) == 1) break;
 fclose(f);
 if (pid <= 1) return;
 fprintf(stderr, "Shutdown pinned process %d:\n", pid);
 snprintf(path, sizeof(path), "/proc/%d/status", pid);
 f = fopen(path, "re");
 if (f) {
  while (fgets(line, sizeof(line), f))
   if (!strncmp(line, "State:", 6) || !strncmp(line, "PPid:", 5) ||
       !strncmp(line, "TracerPid:", 10) || !strncmp(line, "NSpid:", 6))
    fputs(line, stderr);
  fclose(f);
 }
 snprintf(path, sizeof(path), "/proc/%d/wchan", pid);
 f = fopen(path, "re");
 if (f) {
  if (fgets(line, sizeof(line), f)) fprintf(stderr, "Wait channel: %s\n", line);
  fclose(f);
 }
}''')
    replace_once(shutdown,
                 "if (fd < 0) { if (errno != ESRCH) ok = false; continue; }",
                 "if (fd < 0) { if (errno != ESRCH) { fprintf(stderr, \"Shutdown pidfd_open(%d): %s\\n\", pid, strerror(errno)); ok = false; } continue; }")
    for signal in ("SIGTERM", "SIGKILL"):
        replace_once(shutdown,
                     f"if (syscall(SYS_pidfd_send_signal, handles[i].fd, {signal}, NULL, 0) < 0 && errno != ESRCH) ok = false;",
                     f"if (syscall(SYS_pidfd_send_signal, handles[i].fd, {signal}, NULL, 0) < 0 && errno != ESRCH) {{ fprintf(stderr, \"Shutdown {signal} pidfd=%d: %s\\n\", handles[i].fd, strerror(errno)); ok = false; }}")
        replace_once(shutdown,
                     f"syscall(SYS_pidfd_send_signal, serverHandle, {signal}, NULL, 0);",
                     f"if (syscall(SYS_pidfd_send_signal, serverHandle, {signal}, NULL, 0) < 0 && errno != ESRCH) fprintf(stderr, \"Shutdown server {signal}: %s\\n\", strerror(errno));")
    replace_once(shutdown,
                 " if (poll(&serverPoll, 1, 1000) <= 0) ok = false;",
                 " int serverState = poll(&serverPoll, 1, 1000);\n"
                 " if (serverState <= 0) { fprintf(stderr, \"Shutdown server %d did not exit: poll=%d errno=%d\\n\", server, serverState, serverState < 0 ? errno : 0); ok = false; }")
    replace_once(shutdown,
                 "  if (poll(&handles[i], 1, 1000) <= 0) ok = false;",
                 "  { int state = poll(&handles[i], 1, 1000); if (state <= 0) { fprintf(stderr, \"Shutdown child pidfd=%d did not exit: poll=%d errno=%d\\n\", handles[i].fd, state, state < 0 ? errno : 0); shutdownReportHandle(handles[i].fd); ok = false; } }")


def patch_launcher(source):
    source = Path(source)
    shutdown = source / "src/startup/container-shutdown.h"
    replace_once(shutdown,
                 "static inline bool shutdownContainer(pid_t server, int serverHandle, pid_t shellspawn, int shellHandle)",
                 "static inline bool shutdownContainer(pid_t server, int serverHandle, pid_t shellspawn, int shellHandle, bool nonroot)")
    replace_once(shutdown,
                 "if (!shutdownNamespace(server, serverNs, sizeof(serverNs))) return false;",
                 "if (!nonroot && !shutdownNamespace(server, serverNs, sizeof(serverNs))) return false;")
    replace_once(shutdown, "if (pid > 1 && shutdownParent(pid) == server &&",
                 "if (!nonroot && pid > 1 && shutdownParent(pid) == server &&")
    replace_once(source / "src/startup/darling.c",
                 "shutdownContainer(pidInit, handle, shellspawn, shellHandle);",
                 "shutdownContainer(pidInit, handle, shellspawn, shellHandle, g_nonroot);")
    patch_shutdown_diagnostics(source)


def patch_gui(source):
    # NSWindow's frame conversion already reserves the title bar. Subtracting
    # it again makes real content smaller than AppKit's reported content rect.
    replace_once(Path(source) / "src/external/cocotron/AppKit/NSThemeFrame.m",
                 "    /* The title bar lives above the content; the window's frame conversion\n"
                 "     * reserves the extra height, so take it out of the content view here. */\n"
                 "    CGFloat titleHeight = [NSThemeFrame titleBarHeightForStyleMask:\n"
                 "                                            [[self window] styleMask]];\n"
                 "    if (titleHeight > 0.0 && contentFrame.size.height > titleHeight) {\n"
                 "        contentFrame.size.height -= titleHeight;\n"
                 "        contentFrame.origin.y += titleHeight;\n"
                 "    }\n", "")


def patch_native_libraries(source):
    source = Path(source)
    # The launcher's host LD_LIBRARY_PATH is deliberately excluded from the
    # Darwin environment. Resolve explicitly bundled ELF ABIs from the selected
    # runtime, rather than aliasing incompatible system SONAMEs.
    replace_once(source / "src/startup/mldr/elfcalls/elfcalls.c",
                 "\tvoid* rv = dlopen(name, RTLD_LAZY);",
                 "\tvoid* rv;\n"
                 "\tconst char* dextop_root = getenv(\"DARLING_INSTALL_PREFIX\");\n"
                 "\tif (dextop_root && dextop_root[0] == '/' && !strchr(name, '/')) {\n"
                 "\t\tchar dextop_path[4096];\n"
                 "\t\tint length = snprintf(dextop_path, sizeof(dextop_path), \"%s/lib/dextop-native/%s\", dextop_root, name);\n"
                 "\t\tif (length > 0 && (size_t)length < sizeof(dextop_path)) {\n"
                 "\t\t\trv = dlopen(dextop_path, RTLD_LAZY);\n"
                 "\t\t\tif (rv) return rv;\n"
                 "\t\t}\n"
                 "\t}\n"
                 "\trv = dlopen(name, RTLD_LAZY);")
    patch_host_library_path(source)


def patch_host_library_path(source):
    source = Path(source)
    execve = source / "src/external/xnu/darling/src/libsystem_kernel/emulation/src/xnu_syscall/bsd/impl/process/execve.c"
    replace_once(execve, "\t\tif (is_android) {",
                 "\t\tconst char* dextop_host_ld = getenv(\"DEXTOP_DARLING_HOST_LIBRARY_PATH\");\n"
                 "\t\tif (dextop_host_ld && dextop_host_ld[0]) {\n"
                 "\t\t\ttermux_ld_env = (char*)__builtin_alloca(strlen(dextop_host_ld) + sizeof(\"LD_LIBRARY_PATH=\"));\n"
                 "\t\t\tstrcpy(termux_ld_env, \"LD_LIBRARY_PATH=\");\n"
                 "\t\t\tstrcat(termux_ld_env, dextop_host_ld);\n"
                 "\t\t\tadd_termux_ld = 1;\n"
                 "\t\t}\n"
                 "\t\tif (!add_termux_ld && is_android) {")


def apply(source):
    source = Path(source)
    patch_launcher(source)
    patch_gui(source)
    patch_native_libraries(source)
    mldr = source / "src/startup/mldr"
    shutil.copyfile(HERE / "darling_socket.c", mldr / "dextop_socket.c")
    shutil.copyfile(HERE.parent.parent / "android/app/src/main/cpp/linux_unixsock.c",
                    mldr / "dextop_linux_unixsock.c")
    replace_once(mldr / "CMakeLists.txt", "\tmldr.c\n", "\tmldr.c\n\tdextop_socket.c\n")
    replace_once(mldr / "mldr.c", "static void process_special_env(struct load_results* lr) {",
                 "struct sockaddr_un dextop_dserver_rpc_address_data;\n"
                 "static void process_special_env(struct load_results* lr) {")
    replace_once(mldr / "mldr.c", "\t\tlr->socket_path = __dserver_socket_address_data.sun_path;",
                 "\t\tlr->socket_path = __dserver_socket_address_data.sun_path;\n"
                 "\t\tdextop_dserver_rpc_address_data = __dserver_socket_address_data;\n"
                 "\t\textern void dextop_macos_rpc_address(void*, unsigned);\n"
                 "\t\tdextop_macos_rpc_address(&dextop_dserver_rpc_address_data, "
                 "sizeof(dextop_dserver_rpc_address_data));")
    rpc_header = mldr / "resources/dserver-rpc-defs.h"
    replace_once(rpc_header, "extern struct sockaddr_un __dserver_socket_address_data;",
                 "extern struct sockaddr_un __dserver_socket_address_data;\n"
                 "extern struct sockaddr_un dextop_dserver_rpc_address_data;")
    replace_once(rpc_header,
                 "#define dserver_rpc_hooks_get_server_address() ((void*)&__dserver_socket_address_data)",
                 "#define dserver_rpc_hooks_get_server_address() ((void*)&dextop_dserver_rpc_address_data)")
    replace_once(mldr / "resources/dserver-rpc-defs.h",
                 "#define dserver_rpc_hooks_get_server_address_length() sizeof(__dserver_socket_address_data)",
                 "#define dserver_rpc_hooks_get_server_address_length() "
                 "(dextop_dserver_rpc_address_data.sun_path[0] ? sizeof(dextop_dserver_rpc_address_data) : "
                 "sizeof(dextop_dserver_rpc_address_data.sun_family) + 1 + "
                 "strlen(dextop_dserver_rpc_address_data.sun_path + 1))")
    xnu_header = source / ("src/external/xnu/darling/src/libsystem_kernel/emulation/"
                           "include/linux_premigration/resources/dserver-rpc-defs.h")
    replace_once(xnu_header,
                 "#define dserver_rpc_hooks_get_server_address_length() sizeof(struct linux_sockaddr_un)",
                 "static size_t dextop_dserver_address_length(void) {\n"
                 "\tconst struct linux_sockaddr_un* address = __dextop_rpc_address();\n"
                 "\treturn address->sun_path[0] ? sizeof(*address) : "
                 "offsetof(struct linux_sockaddr_un, sun_path) + 1 + "
                 "__simple_strlen(address->sun_path + 1);\n}\n"
                 "#define dserver_rpc_hooks_get_server_address_length() dextop_dserver_address_length()")
    replace_once(xnu_header,
                 "#define dserver_rpc_hooks_get_server_address() ((void*)__dserver_socket_address())",
                 "#define dserver_rpc_hooks_get_server_address() ((void*)__dextop_rpc_address())")
    # Darwin's bind/connect also bypass LD_PRELOAD. Route them through the
    # existing ELF bridge, keeping the Linux address conversion in one place.
    replace_once(mldr / "elfcalls/elfcalls.h",
                 "\tvoid* (*arm64_darling_tsd_base)(void);",
                 "\tvoid* (*arm64_darling_tsd_base)(void);\n"
                 "\tlong (*dextop_socket_bind)(int, const void*, unsigned);\n"
                 "\tlong (*dextop_socket_connect)(int, const void*, unsigned);\n"
                 "\tconst void* (*dextop_rpc_address)(void);")
    replace_once(mldr / "elfcalls/elfcalls.c", "void elfcalls_make(struct elf_calls* calls)",
                 "extern struct sockaddr_un dextop_dserver_rpc_address_data;\n"
                 "static const void* dextop_rpc_address(void) { return &dextop_dserver_rpc_address_data; }\n"
                 "extern long dextop_macos_bind(int, const void*, unsigned);\n"
                 "extern long dextop_macos_connect(int, const void*, unsigned);\n"
                 "void elfcalls_make(struct elf_calls* calls)")
    replace_once(mldr / "elfcalls/elfcalls.c", "\tcalls->native_tsd_base = __darling_native_tsd_base;",
                 "\tcalls->dextop_rpc_address = dextop_rpc_address;\n"
                 "\tcalls->dextop_socket_bind = dextop_macos_bind;\n"
                 "\tcalls->dextop_socket_connect = dextop_macos_connect;\n"
                 "\tcalls->native_tsd_base = __darling_native_tsd_base;")
    emulation = source / "src/external/xnu/darling/src/libsystem_kernel/emulation"
    replace_once(emulation / "src/xnu_syscall/bsd/impl/process/execve.c",
                 "struct linux_sockaddr_un* server_socket_address = dserver_rpc_hooks_get_server_address();",
                 "const struct linux_sockaddr_un* server_socket_address = __dserver_socket_address();")
    replace_once(emulation / "include/linux_premigration/elfcalls_wrapper.h",
                 "const void* __dserver_socket_address(void);",
                 "const void* __dextop_rpc_address(void);\n"
                 "long __dextop_socket_bind(int, const void*, unsigned);\n"
                 "long __dextop_socket_connect(int, const void*, unsigned);\n"
                 "const void* __dserver_socket_address(void);")
    replace_once(emulation / "src/linux_premigration/elfcalls_wrapper.c",
                 "const void* __dserver_socket_address(void) {",
                 "const void* __dextop_rpc_address(void) {\n"
                 "\treturn ELFCALLS_HAS_FIELD(dextop_rpc_address) && _elfcalls->dextop_rpc_address "
                 "? _elfcalls->dextop_rpc_address() : __dserver_socket_address();\n}\n"
                 "long __dextop_socket_bind(int fd, const void* address, unsigned length) {\n"
                 "\treturn ELFCALLS_HAS_FIELD(dextop_socket_bind) && _elfcalls->dextop_socket_bind "
                 "? _elfcalls->dextop_socket_bind(fd, address, length) : -38;\n}\n"
                 "long __dextop_socket_connect(int fd, const void* address, unsigned length) {\n"
                 "\treturn ELFCALLS_HAS_FIELD(dextop_socket_connect) && _elfcalls->dextop_socket_connect "
                 "? _elfcalls->dextop_socket_connect(fd, address, length) : -38;\n}\n"
                 "const void* __dserver_socket_address(void) {")
    for operation in ("bind", "connect"):
        path = emulation / f"src/xnu_syscall/bsd/impl/network/{operation}.c"
        replace_once(path, "#include <sys/socket.h>",
                     "#include <sys/socket.h>\n"
                     "#include <darling/emulation/linux_premigration/elfcalls_wrapper.h>")
        original = ("#ifdef __NR_socketcall\n"
                    f"\tret = LINUX_SYSCALL(__NR_socketcall, LINUX_SYS_{operation.upper()}, "
                    "((long[6]) { fd, fixed, socklen }));\n#else\n"
                    f"\tret = LINUX_SYSCALL(__NR_{operation}, fd, fixed, socklen);\n#endif")
        replace_once(path, original,
                     f"\tret = __dextop_socket_{operation}(fd, fixed, socklen);\n"
                     "\tif (ret == -38) { /* older MLDR without Dextop bridge */\n"
                     + original + "\n\t}")
    # Modern malloc is fork-locked on every architecture. Complete the child's
    # allocation-using server registration only after libSystem unlocks malloc.
    fork_source = emulation / "src/xnu_syscall/bsd/impl/process/fork.c"
    replace_once(fork_source,
                 "#if defined(__arm64__) || defined(__aarch64__)\nstatic int postfork_child_wdfd",
                 "#if defined(DARLING)\nstatic int postfork_child_wdfd")
    replace_once(fork_source,
                 "#if defined(__arm64__) || defined(__aarch64__)\n\t\t// Modern malloc",
                 "#if defined(DARLING)\n\t\t// Modern malloc")
    replace_once(emulation / "include/xnu_syscall/bsd/impl/process/fork.h",
                 "#if defined(__arm64__) || defined(__aarch64__)", "#if defined(DARLING)")
    replace_once(emulation / "src/linux_premigration/elfcalls_wrapper.c",
                 '#if defined(__arm64__) || defined(__aarch64__)\n__attribute__((visibility("default")))',
                 '#if defined(DARLING)\n__attribute__((visibility("default")))')
    libsystem = source / "src/external/libsystem/init.c"
    content = libsystem.read_text(encoding="utf-8")
    condition = "#if defined(DARLING) && (defined(__arm64__) || defined(__aarch64__))"
    if content.count(condition) != 3:
        raise RuntimeError("Pinned libSystem postfork hooks do not match")
    libsystem.write_text(content.replace(condition, "#if defined(DARLING)"), encoding="utf-8")
    dyld = source / "src/external/dyld/src"
    shutil.copyfile(HERE / "darling_segments.h", dyld / "dextop_segments.h")
    replace_once(dyld / "ImageLoaderMachO.cpp", '#include "dyld2.h"',
                 '#include "dyld2.h"\n#include "dextop_segments.h"')
    replace_once(dyld / "ImageLoaderMachO.cpp",
                 "void* loadAddress = xmmap((void*)requestedLoadAddress, size, protection, MAP_FIXED | MAP_PRIVATE, fd, fileOffset);",
                 "void* loadAddress = dextop_map_segment((void*)requestedLoadAddress, size, protection, fd, fileOffset, context.envp);")
