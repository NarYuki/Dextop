# Deck direct presentation

Deck-only Turnip: Droid-Deck/Drivers-CI DD-Turnip-v0.3.3, Mesa
`c90ff433291a5c3ff5cea9913bbebeef9cd4e885`, with that release's Linux patches.
Source archive:
https://github.com/Droid-Deck/Drivers-CI/releases/download/DD-Turnip-v0.3.3/DD-Turnip-v0.3.3-source.tar.xz
SHA-256: `834054084604cacb2fe82d9151256bac83fd353d50b602f6824a1ce8ae73a3a8`.
Mesa's licenses are preserved in the payload. DD's patches are GPL-3.0.

The additional gralloc Wayland WSI patch and client protocol are from
The412Banner/Banners-Turnip, commit
`558866f688e9a99b6eac461162a14038b3120964`, `patches/wayland/`:
https://github.com/The412Banner/Banners-Turnip/tree/558866f688e9a99b6eac461162a14038b3120964/patches/wayland
The banner-ahb-v1 protocol retains its MIT copyright and permission notice.
The matching generated server protocol was obtained from DroidDeck commit
`8aa1fdaf708eb44ef7cff7e493bda946989af511`,
`app/src/main/cpp/waylandcomp/generated/`.

Dextop's glibc proxy and Android allocation broker are under the project's
GPL-3.0 license. They transfer native handle FDs and metadata without mapping
or copying image pixels. Android performs allocation and handle serialization;
glibc does not load Android system libraries.

Build with `build-in-debian.sh` in an ARM64 Debian 13 build environment, then
build the glibc proxy with `tool/build_linux_gaming.ps1` and package the two
libraries using `tool/build_deck_direct_gpu.py`.
The private ICD is selected only by `dextop-deck-gamescope`; system drivers
and ordinary Linux launchers are unchanged.
