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
The current probe reports its logical frame, content bounds and backing scale.
The GUI checker derives resize expectations and pointer coordinates from those
values, so different menu font heights do not cause a false resize failure.
Rebuild the probe with the same revision as the checker.

For a private headless X11 control test, install `xvfb`, `xdotool` and ImageMagick:

```sh
python3 tool/macos/check_gui.py --runtime /absolute/path/to/gui-runtime \
  --probes /absolute/path/to/gui-probes --output /absolute/path/to/gui-logs
```

Use `--architecture x86_64` for a native WSL control. The test creates its own X
display and Darling prefix, types into the probe, clicks its report button,
resizes its window, closes it normally, checks the diagnostic events and exit
status, and requests scoped shutdown. It saves PNG captures for visual review.
Xvfb does not establish hardware GPU acceleration or Android presentation.

The ARM64 GUI builder also bundles the pinned native JPEG ABI from
`native-deps.lock.json`. `bundle_native.py` verifies the official package's
SHA-256 and architecture, extracts its library and copyright notice, and stages
them atomically under `runtime/lib/dextop-native` without installing system
packages. It refuses to replace an existing bundle. The MLDR patch searches
this directory for bare ELF SONAMEs before the system loader; explicit paths
keep their original behavior. This is separate from Darwin's dyld search path:
the launcher intentionally filters the host's `LD_LIBRARY_PATH`, and Debian's
`libjpeg.so.62` does not provide Ubuntu's `libjpeg.so.8` ABI.

The wrapper transports the selected host `LD_LIBRARY_PATH` using
`DEXTOP_DARLING_HOST_LIBRARY_PATH`. The Darwin exec bridge restores it only for
the next Linux MLDR invocation; MLDR still removes the standard host loader
variables before entering Darwin userland. This keeps the common Dextop EGL/GL
runtime selected across nested Mach-O launches. On the Android control device,
native EGL gears and AppKit both produced black X11 images with the system
graphics libraries, while the common Dextop library path produced correct
pixels. An explicit transport value overrides the inherited path; an explicit
empty value disables transport. Callers bypassing `run.sh` must provide the
same transport when they select a host graphics runtime.

Keep the PRoot parent session alive through scoped Darling shutdown. The
standalone Android diagnostic reproduced a tracer exit-stop hang when its root
client exited while a different PRoot task shut down its daemon; supervised
tests that retain the parent through cleanup complete normally. Shutdown
diagnostics report pidfd and polling failures without broadening targets.
With the current probe, add `--persistence` to verify that Foundation atomically
saves the entered text plus a Japanese UTF-8 suffix, then reloads it after the
app closes and restarts in the same private prefix. Review `reopened.png` for
Japanese glyph rendering. This verifies storage and display, not Japanese IME
input. `DEXTOP_GUI_DOCUMENT` points to a unique file in this test's temporary
directory, addressed through `/Volumes/SystemRoot`; successful cleanup removes
it. Do not assume that Darwin `/tmp` is isolated between non-root prefixes.
Add `--dialog` to check modal confirmation, return to the main window and that
all dialog controls fit inside its content bounds. The GUI patch removes a
second subtraction of title-bar height in `NSThemeFrame`, which otherwise clips
the alert's message and icon despite successful button interaction.

The pinned x86_64 Swift Foundation overlay comes from Swift 5.2.2 and fails to
load against the bundled Swift 6.3.3 core (`__swift_classIsSwiftMask`). The native
GUI control uses rebuilt source overlays instead:

```sh
python3 tool/macos/build_swift_x86.py --source /pinned/darling-source \
  --runtime /private/staged-runtime --toolchain /verified/linux-toolchain/usr \
  --resource-dir /verified/swift-org-darwin-toolchain/usr/lib/swift \
  --linker /darling-build/src/external/cctools-port/cctools/ld64/src/x86_64-apple-darwin20-ld \
  --libsystem /darling-build/src/external/libsystem/libSystem.B.dylib \
  --work /new/dedicated/swift-build
```

This requires the Swift.org 6.3.3 toolchain and its open-source Darwin runtime
resources, verified before use. It copies the pinned Swift sources, builds
Foundation and its real dependencies, and writes dylibs plus OpenCombine to
`work/output`; it does not modify the supplied runtime. Stage those dylibs under
the private runtime's `libexec/darling/usr/lib/swift` and `Combine` under
`libexec/darling/System/Library/Frameworks/Combine.framework/Versions/A` before
running the GUI control. Preserve the original files separately. This focused
rebuild does not establish full Swift, SwiftUI or application compatibility.

On Android, `run.sh` also enables `DEXTOP_DARLING_ANON_EXEC=1`. The dyld patch
loads executable Mach-O segments into private anonymous writable memory before
the existing ARM64 TLS adaptation and final protection change. Android's
file-backed `execmod` restriction then does not apply. No writable/executable
mapping is requested, and the original file is not modified. Short reads fail
the load. Data segments keep their normal mapping. Set the variable to `0` for
the unmodified mapping path. CI verifies both paths on Linux ARM64.

### Local application lifecycle

`launch_app.py` reads XML or binary `Contents/Info.plist` and launches the bundle's
`CFBundleExecutable` directly, preserving argument boundaries and Unicode paths:

```sh
python3 tool/macos/launch_app.py --bundle '/path/My Application.app' --inspect
python3 tool/macos/launch_app.py --runtime /private/staged-runtime \
  --bundle '/path/My Application.app' -- --example 'argument with spaces'
```

The supervisor must remain **inside the same PRoot** as Darling until scoped
shutdown completes. Unlike `run.sh`, it owns the application lifecycle: after
normal exit or SIGTERM/SIGINT it calls Darling's prefix-scoped shutdown before
returning. It retains application data under a stable per-bundle prefix and
locks that prefix against simultaneous supervisors. It neither deletes user
prefixes nor kills unrelated Linux processes. A failed shutdown is an error.
Use `--prefix /absolute/private/prefix` to select an explicit persistent prefix.

Only a matching native Mach-O architecture is accepted; this does not provide
Intel translation on ARM64. Bundle path checks prevent accidental metadata or
executable symlink traversal during discovery, but do not sandbox applications.
The GUI shares the supplied X11 `DISPLAY` and common GPU environment. This tool
is the lifecycle bridge for Book integration, not an installed Android UI or a
claim that arbitrary macOS applications work.

Android's build copies this same source into Java resources. The common Linux
session staging installs it at `/usr/local/lib/dextop/macos/launch_app.py`; there
is no separately maintained Android launcher implementation. Python 3 and the
compatible native Darling runtime must be installed in the container.

Register a local bundle with the existing Book/Linux application list:

```sh
python3 /usr/local/lib/dextop/macos/launch_app.py --register \
  --bundle '/root/Applications/My Application.app'
```

This writes a user `.desktop` entry and a JSON registration with the bundle,
runtime and persistent prefix paths. Its Exec line contains a fixed registration
id rather than a bundle path or app-supplied command. The normal application
launcher reads the entry and selects X11 for Darling. Registration does not run
the bundle; launching revalidates it. Refresh the application list after adding
or removing registrations. `--registered ID` launches one; `--unregister ID`
removes only its registration and desktop entry, keeping the bundle and prefix.
The id is the 24-character suffix returned by registration, excluding
`dextop-macos-` and `.desktop`. Linux settings → macOS compatibility provides
per-container component status, bundle registration and confirmed registration
removal using the same commands. `--status` inspects files without starting
Darling; component presence is explicitly distinct from verified compatibility.
The runtime installer is still needed; registration alone does not install
Darling or prove app compatibility.
