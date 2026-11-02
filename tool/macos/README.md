# Experimental macOS compatibility

This branch builds a pinned VibeDarling ARM64 core for Dextop's glibc Linux
guest. It does not yet provide working macOS application support in the app UI.
The core excludes Swift, AppKit and SwiftUI. The pinned upstream OpenSwiftUI
diagnostic does not report a usable SwiftUI framework.

## Build

The `macOS compatibility ARM64 core` workflow runs on `feat/macos-compat`.
It fetches the exact revision in `darling.lock.json`, checks out pinned
submodules, applies `patch_runtime.py`, and builds `COMPONENTS=core` on
Ubuntu 24.04 ARM64. The archive contains a private runtime tree; it must not
be extracted over a guest's `/usr` or installed using the upstream setuid
installer. The build removes setuid and setgid bits from the staging tree.

For a Linux ARM64 build host with the workflow's build dependencies:

```sh
python3 tool/macos/build_runtime.py --jobs 2
```

An x86_64 Linux host, including WSL, can build the native comparison runtime with
`--architecture x86_64 --work /absolute/path/to/new/work --jobs 8`.
Use the same architecture option for `build_smoke.py` and `check_runtime.py`.
This provides a local baseline; it does not make Intel Mach-O programs run on
an ARM64 Android device. Keep the build directory in WSL's Linux filesystem.

The work directory must be new or empty. `build_launcher.py` builds only the
ELF launcher; its output is insufficient to run a Mach-O application.

## Execution and validation

Keep the runtime and its writable Darwin prefix separate from normal Linux
applications. Set `DEXTOP_DARLING_ROOT` to the absolute extracted runtime path
and invoke `tool/macos/run.sh`. The wrapper uses upstream's non-root path,
with a private prefix under `$HOME/.local/share/dextop/macos/default`.
`--check` checks required files; it does not verify Mach-O execution.

Dextop's Android shell environment cannot create filesystem Unix sockets at
the runtime location. Its normal Linux socket shim substitutes container-scoped
abstract sockets. Mach-O code uses raw Linux syscalls, so it needs the matching
Darling adaptation in `patch_runtime.py`. This reuses the existing naming
implementation, including long-path hashing. The RPC sockaddr length must
exclude trailing padding for abstract sockets. Darwin bind/connect use
appended, size-checked ELF bridge callbacks after normal sockaddr conversion.
Existing ELF bridge fields retain their offsets.

The original filesystem socket path is retained separately from the mapped RPC
address. `execve` serializes the original path into `__mldr_sockpath`;
serializing an abstract address as a C string loses its leading-NUL name.
Both dyld's embedded kernel code and `libsystem_kernel.dylib` require rebuilding
when the RPC bridge changes.

For incremental ARM64 loader builds on WSL, configure the Darling build with
`CMAKE_EXPORT_COMPILE_COMMANDS=ON`, build its generated headers, then run
`build_loader.py --build <build> --zig <Linux Zig executable> --output <directory>`.
The helper cross-compiles the six ELF loader/RPC sources using their configured
include paths. The Mach-O libraries and dyld are separate build targets; this
helper does not produce a complete runtime.

Non-root shutdown uses prefix-validated pidfds and process ancestry. It does not
require reading PID namespace links, which Android can deny even for the calling
user. It never selects other processes merely because they share the host's PID
namespace. The Linux baseline fixture tests abstract server RPC and denial of
the launcher's namespace reads; it is not included in the installed payload.

The wrapper enables `DEXTOP_DARLING_ABSTRACT_RPC=1`. Set it to `0` for a normal
Linux ARM64 baseline without Dextop's socket shim. Dextop's existing namespace
environment/file and socket-marker companion are required on Android.

Build the own-source probes without an Apple SDK:

```sh
python3 tool/macos/build_smoke.py --clang /usr/bin/clang --linker "$(llvm-config --bindir)/ld64.lld" --probe hello
python3 tool/macos/build_smoke.py --clang /usr/bin/clang --linker "$(llvm-config --bindir)/ld64.lld" --probe posix
```

Alternatively, use `--zig /absolute/path/to/zig` with the same `--linker`.
Zig's bundled ARM64 Mach-O linker emits zero lazy-binding offsets; the pinned
upstream also documents this in `tools/fix-macho-stub-helper.py`. These probes
use LLVM's linker for both frontends, without patching linked executable bytes.
The generated TAPI file contains interface declarations, not implementations.
The hello probe checks output and exit status. The POSIX probe checks file
contents, thread creation/join and fork/wait. Compilation success is distinct
from execution success.

```sh
python3 tool/macos/check_runtime.py \
  --runtime /absolute/path/to/runtime \
  --probes /absolute/path/to/probes \
  --output /absolute/path/to/logs
```

Add `--abstract-rpc --anonymous-exec` inside Dextop. The check creates its own temporary
prefix, runs hello/POSIX/socket/hello, and requests prefix-specific shutdown. It fails
on missing output, a nonzero exit, a timeout or a shutdown failure. GUI, input,
GPU, audio and general application compatibility require separate validation.

The separate GUI probe uses Darling's source-built SDK headers and a staged GUI
runtime, never an Apple SDK installation:

```sh
python3 tool/macos/build_appkit_probe.py \
  --source /absolute/path/to/pinned-darling-source \
  --runtime /absolute/path/to/staged-runtime \
  --clang /usr/bin/clang --linker "$(llvm-config --bindir)/ld64.lld" \
  --output /absolute/path/to/gui-probes
```

For the pinned source, the GUI build needs `COMPONENTS=gui_frameworks,gui_stubs`
and `ENABLE_METAL=ON`: AppKit depends on UniformTypeIdentifiers from the former
stub component, and Metal's source needs its enabled shader compiler dependency.
Use `build_runtime.py --components gui --work /new/dedicated/directory` to build
that configuration. This also fetches and checks the pinned Swift Git LFS assets;
Git LFS and the upstream GUI development dependencies must be installed first.
The default core-only builder does not produce this GUI payload. The probe requests a
window containing text, an editable field and an input-report button, and exits
when the last window closes. Button actions and window resizes print diagnostic
events. Its initial console message proves only that the window was requested; visible rendering, input,
resizing and closing still need independent runtime verification.

On Android, `run.sh` also enables `DEXTOP_DARLING_ANON_EXEC=1`. The dyld patch
loads executable Mach-O segments into private anonymous writable memory before
the existing ARM64 TLS adaptation and final protection change. Android's
file-backed `execmod` restriction then does not apply. No writable/executable
mapping is requested, and the original file is not modified. Short reads fail
the load. Data segments keep their normal mapping. Set the variable to `0` for
the unmodified mapping path. CI verifies both paths on Linux ARM64.
