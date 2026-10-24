# DroidDeck PRoot fast path

`fastpath.c` is copied without modification from Droid-Deck/DroidDeck,
commit 26ca374 (2026-10-17), tools/proot/fastpath/fastpath.c.
Upstream: https://github.com/Droid-Deck/DroidDeck/tree/26ca374/tools/proot/fastpath
License: GPL-3.0, see LICENSE in this directory.

It must only run with the matching patched PRoot seccomp trampoline, the exact
root/bind mapping, and matching PROOT_FASTPATH / PROOT_FP_KEY values. Outside
that environment it leaves ordinary libc behavior enabled.

The Dextop resource `dextop-wine-sync` derives from the same upstream commit,
`tools/linuxfs/overlay/usr/local/bin/droiddeck-esync` (GPL-3.0). Dextop adds
stock-binary selection, x86 layout support and adoption of existing Wine servers,
and removes application-specific exclusions. No esync replacement packs are bundled.
