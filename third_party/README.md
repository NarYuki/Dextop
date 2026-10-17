# Third-party sources

These sources are built into the APK by `android/app/src/main/cpp/CMakeLists.txt`
for the Linux container feature (arm64-v8a only).

| Directory | Upstream | Version | License |
| --- | --- | --- | --- |
| `proot/` | https://github.com/termux/proot | commit `a179d3e` (2026-10-01), `src/` and `COPYING` only | GPL-2.0-or-later |
| `talloc/` | https://www.samba.org/ftp/talloc/talloc-2.4.2.tar.gz (SHA-256 `85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6`) | 2.4.2, `talloc.c` and `talloc.h` only | LGPL-3.0-or-later |
| `virglrenderer/` | https://gitlab.freedesktop.org/virgl/virglrenderer/-/archive/virglrenderer-1.3.0/virglrenderer-virglrenderer-1.3.0.tar.gz (SHA-256 `56170f8caa1bb642a2624b649e3bcca095ec2834814e5c308efc8a85a709e4ce`) | 1.3.0; the sources of the vtest server only (`src/` without venus, drm renderers, proxy and video; `vtest/`) | MIT |
| `libepoxy/` | https://github.com/anholt/libepoxy/archive/refs/tags/1.5.10.tar.gz (SHA-256 `a7ced37f4102b745ac86d6a70a9da399cc139ff168ba6b8002b4d8d43c900c15`) | 1.5.10, `src/` and `include/` | MIT |

Local changes:
- `talloc/replace.h` replaces Samba's `lib/replace` with the few macros `talloc.c`
  needs; Android's bionic libc provides every function it would otherwise replace.
- proot sources are unmodified. `extension/ashmem_memfd/ashmem_memfd.c` is compiled
  with `-include string.h` because it relies on an implicit declaration that
  NDK 28 rejects. `build.h` and `loader-info.c` are generated at build time.

- virglrenderer (VirGL GPU acceleration, built as `libvirgl_server.so` by
  `android/app/src/main/cpp/virgl/CMakeLists.txt`) has these changes:
  - `src/vrend/vrend_winsys.c`: on Android, EGL is initialized on the GPU driver's
    default display (no GBM) and torn down in `vrend_winsys_cleanup`.
  - `src/mesa/util/detect_os.h`: `DETECT_OS_ANDROID` stays off, so Mesa's util does
    not use Android system-component APIs; the server is a plain shell executable.
  - `src/vrend/vrend_renderer.c`: dual-source blending is reported as unsupported
    on Android (broken on Adreno, virglrenderer issue 223).
  - `vtest/vtest_server.c`: a `--socket-path` starting with `@` listens on an
    abstract unix socket, because the shell user may not create socket files
    under `/data/local/tmp`.
- `virglrenderer/generated/` (`config.h`, `virgl-version.h`, `u_format_gen.h`,
  `u_format_table.c`) and `libepoxy/generated/` (`config.h`, the GL/EGL dispatch
  sources and headers) are the files virglrenderer's and libepoxy's Meson builds
  generate (`-Dplatforms=egl -Dvenus=false -Ddrm-renderers=[] -Dvideo=false`;
  libepoxy `-Degl=yes -Dglx=no -Dx11=false`), committed so the Android build needs
  neither Meson nor Python.

To update, replace the directory contents with the new upstream files and update
this table.
