#!/bin/sh
# Refresh only the upstream helpers used by Debian's Steam compatibility layer.
# Upstream: Droid-Deck/DroidDeck, GPL-3.0; source.env records the revision.
# Usage: tool/sync_droiddeck_linuxfs.sh <DroidDeck checkout>
set -eu
src=${1:?DroidDeck checkout}
out=android/app/src/main/resources/moe/n4tsu/dextop/linux/droiddeck/linuxfs
for f in \
  'usr/bin/jupiter-biosupdate' \
  'usr/bin/steamos-polkit-helpers/jupiter-biosupdate' \
  'usr/bin/steamos-polkit-helpers/jupiter-dock-updater' \
  'usr/bin/steamos-polkit-helpers/steamos-select-branch' \
  'usr/bin/steamos-polkit-helpers/steamos-update' \
  'usr/bin/steamos-select-branch' \
  'usr/bin/steamos-update' \
  'usr/local/bin/droiddeck-fex' \
  'usr/local/bin/droiddeck-login1' \
  'usr/local/bin/droiddeck-netmanager' \
  'usr/local/bin/steam-compatibility'
do
  mkdir -p "$out/$(dirname "$f")"
  cp "$src/tools/linuxfs/overlay/$f" "$out/$f"
done
printf 'DROIDDECK_COMMIT=%s\n' "$(git -C "$src" rev-parse HEAD)" >"$out/../source.env"
