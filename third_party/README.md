# Third-party sources

These sources are built into the APK by `android/app/src/main/cpp/CMakeLists.txt`
for the Linux container feature (arm64-v8a only).

| Directory | Upstream | Version | License |
| --- | --- | --- | --- |
| `proot/` | https://github.com/termux/proot | commit `a179d3e` (2026-10-01), `src/` and `COPYING` only | GPL-2.0-or-later |
| `talloc/` | https://www.samba.org/ftp/talloc/talloc-2.4.2.tar.gz (SHA-256 `85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6`) | 2.4.2, `talloc.c` and `talloc.h` only | LGPL-3.0-or-later |

Local changes:
- `talloc/replace.h` replaces Samba's `lib/replace` with the few macros `talloc.c`
  needs; Android's bionic libc provides every function it would otherwise replace.
- proot sources are unmodified. `extension/ashmem_memfd/ashmem_memfd.c` is compiled
  with `-include string.h` because it relies on an implicit declaration that
  NDK 28 rejects. `build.h` and `loader-info.c` are generated at build time.

To update, replace the directory contents with the new upstream files and update
this table.
