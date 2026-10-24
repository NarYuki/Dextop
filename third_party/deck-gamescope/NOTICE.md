# Deck gamescope

Deck-only distribution of DroidDeck gamescope 3.16.29-p12 and private C++/Wayland dependencies.
Normal Linux gamescope and system libraries are not replaced.

Sources: https://github.com/Droid-Deck/DroidDeck/tree/8aa1fdaf708eb44ef7cff7e493bda946989af511/tools/gamescope
Upstream gamescope: https://github.com/ValveSoftware/gamescope/tree/3.16.29
DroidDeck component: https://github.com/Droid-Deck/DroidDeck-Components/releases/tag/gamescope-3.16.29-p12
Dependency distribution: https://github.com/Droid-Deck/DroidDeck-Components/releases/tag/linuxfs-r9
GCC sources: https://gcc.gnu.org/git/?p=gcc.git;a=shortlog;h=refs/heads/releases/gcc-16
Wayland sources: https://gitlab.freedesktop.org/wayland/wayland

The adjacent patches and build-in-arch.sh reproduce DroidDeck's gamescope build.
Run `python tool/build_deck_gamescope.py` with the pinned archives under build/ to
assemble the managed resource. Archive SHA-256 values and per-file hashes are
recorded in gaming/deck-gamescope.json. Dependencies retain Wayland's COPYING,
GCC's GPLv3 text and its Runtime Library Exception in the installed payload.
The original gamescope license is adjacent; DroidDeck patches are GPL-3.0.
