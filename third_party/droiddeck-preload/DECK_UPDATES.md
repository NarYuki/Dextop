# Deck-only runtime updates

The message-queue mapping cache and openat2/path helpers in `sysv.c` and
`robust.c` are adapted from Droid-Deck/DroidDeck commit
`8aa1fdaf708eb44ef7cff7e493bda946989af511`,
`tools/linuxfs/preload/{sysv.c,robust.c}` (GPL-3.0).

Source: https://github.com/Droid-Deck/DroidDeck/tree/8aa1fdaf708eb44ef7cff7e493bda946989af511/tools/linuxfs/preload

Dextop gates the new behavior with `DEXTOP_STEAM_INSIDE=1`, retains its
existing non-Deck implementations and shell-UID/socket/PE compatibility hooks,
and resets cached message-queue reference counts after fork. No DD Remote Play
encoder, application-specific fixups, or global FEX defaults were imported.
