# DroidDeck PRoot fast path

`fastpath.c` is copied from Droid-Deck/DroidDeck, commit 26ca374 (2026-10-17),
tools/proot/fastpath/fastpath.c, with Dextop changes marked `Dextop:` (2026-11-14):
activation through PRoot's synthetic prctl probe instead of reading the tracer's
environ, and chmod/utimes/inotify/accept/socket names routed to PRoot, because
PRoot's Landlock-confined broad prologue does not exempt them.
Upstream: https://github.com/Droid-Deck/DroidDeck/tree/26ca374/tools/proot/fastpath
License: GPL-3.0, see LICENSE in this directory.

Dextop builds both libraries for **App information → Experimental features →
Fast mode**, with the right-hand selector choosing the implementation:

- Protected + fast: `landlock_fastpath.c`, narrow openat acceleration after
  applying Landlock ABI >= 6. Uses the synthetic prctl errno activation probe.
- Unprotected ("broad"): this `fastpath.c`, with PRoot's broad prologue that
  exempts only Landlock-restricted file operations and metadata lookups. Landlock
  is applied as in the protected mode; failure falls back to normal PRoot.

The single selector offers Disabled, Protected + fast and Unprotected, and
defaults to Disabled. Libraries are
staged separately and preloaded per launch. No device/kernel-release blacklist
or global fast-path preload is installed. Landlock capability or application
failure falls back to normal PRoot in both modes.

The Dextop resource `dextop-wine-sync` derives from the same upstream commit,
`tools/linuxfs/overlay/usr/local/bin/droiddeck-esync` (GPL-3.0). Dextop adds
stock-binary selection, x86 layout support and adoption of existing Wine servers,
and removes application-specific exclusions. No esync replacement packs are bundled.
