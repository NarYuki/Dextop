#!/bin/sh
# Copies DroidDeck's session scripts (GPL-3.0, Droid-Deck/DroidDeck) into the
# APK resources with the layout SessionFiles.stage() reads them from:
#   tools/linuxfs/overlay  usr/local/bin/droiddeck-*, steam-compatibility, usr/bin/**
#   tools/linuxfs/desktop  droiddeck-desktop, droiddeck-gpu, droiddeck-desktop-gpu,
#                          kwin_wayland_wrapper, firefox-droiddeck.js
# Usage: tool/sync_droiddeck_linuxfs.sh <DroidDeck checkout>
set -eu
src=${1:?DroidDeck checkout}
out=android/app/src/main/resources/moe/n4tsu/dextop/linux/droiddeck/linuxfs
rm -rf "$out"
mkdir -p "$out"
( cd "$src/tools/linuxfs/overlay" && find usr/local/bin -maxdepth 1 -type f \( -name 'droiddeck-*' -o -name steam-compatibility \) && find usr/bin -type f ) |
  sort | while read -r f; do mkdir -p "$out/$(dirname "$f")"; cp "$src/tools/linuxfs/overlay/$f" "$out/$f"; done
mkdir -p "$out/usr/local/bin" "$out/usr/lib/firefox/defaults/pref"
for f in droiddeck-desktop droiddeck-gpu droiddeck-desktop-gpu kwin_wayland_wrapper; do
  cp "$src/tools/linuxfs/desktop/$f" "$out/usr/local/bin/$f"
done
cp "$src/tools/linuxfs/desktop/firefox-droiddeck.js" "$out/usr/lib/firefox/defaults/pref/droiddeck.js"
( cd "$out" && find . -type f ! -name index.txt | sed 's|^\./||' | sort ) >"$out/index.txt"
echo "DROIDDECK_COMMIT=$(git -C "$src" rev-parse HEAD 2>/dev/null || echo unknown)" >"$out/../source.env"
wc -l <"$out/index.txt"
