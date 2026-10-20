# Third-party sources

These sources are built into the APK by `android/app/src/main/cpp/CMakeLists.txt`
for the Linux container feature (arm64-v8a only).

| Directory | Upstream | Version | License |
| --- | --- | --- | --- |
| `proot/` | https://github.com/termux/proot/archive/4dba3afbf3a63af89b4d9c1a59bf2bda10f4d10f.tar.gz (SHA-256 `de94f648547d1b62431073b82dc99d56ef0ddcef6da022e0dfffe2bf661cc34e`) | that commit, `src/` and `COPYING`, with DroidDeck's patches applied in order (`proot/droiddeck/`, from Droid-Deck/DroidDeck `26ca374` `tools/proot`) | GPL-2.0-or-later |
| `talloc/` | https://www.samba.org/ftp/talloc/talloc-2.4.2.tar.gz (SHA-256 `85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6`) | 2.4.2, `talloc.c` and `talloc.h` only | LGPL-3.0-or-later |
| `virglrenderer/` | https://gitlab.freedesktop.org/virgl/virglrenderer/-/archive/virglrenderer-1.3.0/virglrenderer-virglrenderer-1.3.0.tar.gz (SHA-256 `56170f8caa1bb642a2624b649e3bcca095ec2834814e5c308efc8a85a709e4ce`) | 1.3.0; the sources of the vtest server only (`src/` without venus, drm renderers, proxy and video; `vtest/`) | MIT |
| `libepoxy/` | https://github.com/anholt/libepoxy/archive/refs/tags/1.5.10.tar.gz (SHA-256 `a7ced37f4102b745ac86d6a70a9da399cc139ff168ba6b8002b4d8d43c900c15`) | 1.5.10, `src/` and `include/` | MIT |
| `wayland/` | https://gitlab.freedesktop.org/wayland/wayland/-/releases/1.23.1/downloads/wayland-1.23.1.tar.xz (SHA-256 `864fb2a8399e2d0ec39d56e9d9b753c093775beadc6022ce81f441929a81e5ed`) | 1.23.1, server library sources only | MIT |
| `libffi/` | https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz (SHA-256 `b0dea9df23c863a7a50e825440f3ebffabd65df1497108e5d437747843895a4e`) | 3.4.6, generic and aarch64 sources | MIT |
| `wayland-protocols/` | https://gitlab.freedesktop.org/wayland/wayland-protocols/-/releases/1.38/downloads/wayland-protocols-1.38.tar.xz (SHA-256 `ff17292c05159d2b20ce6cacfe42d7e31a28198fa1429a769b03af7c38581dbe`) | 1.38, the XML used and its `wayland-scanner` output | MIT |
| `adrenotools/` | libadrenotools (Billy Laws), as vendored by Droid-Deck/DroidDeck at commit `26ca374c47641fb8db8d1711c188ddb36a4a7877` (`app/src/main/cpp/adrenotools`) | that revision, without its Meson files | BSD-2-Clause |
| `../android/app/src/main/jniLibs/arm64-v8a/libvulkan_dextop_turnip.so` | DroidDeck DD-Turnip v0.2.0 (https://github.com/Droid-Deck/Drivers-CI/releases/tag/DD-Turnip-v0.2.0, `DD-Turnip-v0.2.0.zip` SHA-256 `fc16658953062cfaabda8a5663c498667d78bb4c9c74c819ff580753270a147d`; source `DD-Turnip-v0.2.0-source.tar.xz` in the same release), Mesa 26.3.0-devel `bffd91947efc1932da99aca69fc540d6d4201b0c` with the DD-Turnip patch set | `android/libvulkan_freedreno.so` unchanged (SHA-256 `fc1197cdf6185db09fe8821e4423daf560bba40a4793a96466665d31e19420d0`); notices in `dd-turnip/` | GPL-3.0 (patch set), MIT (Mesa) |

Local changes:
- `wayland-protocols/presentation-time.xml`, `pointer-constraints-unstable-v1.xml`
  and `relative-pointer-unstable-v1.xml`, plus their generated server files,
  are the upstream Wayland protocols as carried by DroidDeck commit
  `26ca374c47641fb8db8d1711c188ddb36a4a7877`. The XML files retain their MIT-style
  copyright/license notices. They are used by the opt-in Linux GPU probe build.
- `wayland-protocols/server-decoration.xml` is KDE's server-decoration protocol
  from https://github.com/KDE/plasma-wayland-protocols/blob/master/src/protocols/server-decoration.xml
  (LGPL-2.1-or-later, Martin Gräßlin). The generated server header and private
  protocol code use wayland-scanner; GTK 3 uses this protocol for server decorations.
- `talloc/replace.h` replaces Samba's `lib/replace` with the few macros `talloc.c`
  needs; Android's bionic libc provides every function it would otherwise replace.
- proot sources are DroidDeck's patched tree (see `proot/droiddeck/PATCHES.md`). `extension/ashmem_memfd/ashmem_memfd.c` is compiled
  with `-include string.h` because it relies on an implicit declaration that
  NDK 28 rejects. `build.h` and `loader-info.c` are generated at build time.

- virglrenderer (VirGL GPU acceleration, built as `libvirgl_server.so` by
  `android/app/src/main/cpp/virgl/CMakeLists.txt`) has these changes:
  - `src/vrend/vrend_winsys.c`: on Android, EGL is initialized on the GPU driver's
    default display (no GBM) and torn down in `vrend_winsys_cleanup`.
  - `src/mesa/util/detect_os.h`: `DETECT_OS_ANDROID` stays off, so Mesa's util does
    not use Android system-component APIs; the server is a plain shell executable.
  - `vtest/vtest_server.c`: a `--socket-path` starting with `@` listens on an
    abstract unix socket, because the shell user may not create socket files
    under `/data/local/tmp`.
- `virglrenderer/generated/` (`config.h`, `virgl-version.h`, `u_format_gen.h`,
  `u_format_table.c`) and `libepoxy/generated/` (`config.h`, the GL/EGL dispatch
  sources and headers) are the files virglrenderer's and libepoxy's Meson builds
  generate (`-Dplatforms=egl -Dvenus=false -Ddrm-renderers=[] -Dvideo=false`;
  libepoxy `-Degl=yes -Dglx=no -Dx11=false`), committed so the Android build needs
  neither Meson nor Python.

- `wayland/generated/` (`config.h`, `wayland-version.h`, the core protocol code) and
  `libffi/generated/` (`fficonfig.h`, `ffi.h`, `ffitarget.h`) come from their projects'
  Meson and configure runs on Debian arm64; `fficonfig.h` disables static
  trampolines and symbol versioning for the static Android build. The protocol
  sources were generated by `wayland-scanner` 1.23.
- `keymap_us.h` (android/app/src/main/cpp/wayland) is the US keymap compiled by
  `xkbcomp` from xkeyboard-config 2.42 (MIT/X11).

To update, replace the directory contents with the new upstream files and update
this table.
