#!/bin/sh
set -eu
. /etc/os-release
[ "$ID" = debian ] && [ "$VERSION_CODENAME" = trixie ] || {
    echo "This probe setup supports Debian trixie only" >&2; exit 1;
}
cat >/etc/apt/sources.list.d/dextop-gamescope-backports.sources <<'SOURCE'
Types: deb
URIs: https://deb.debian.org/debian
Suites: trixie-backports
Components: main contrib
Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg
SOURCE
apt-get update
apt-cache policy gamescope xwayland
apt-get -s install gamescope/trixie-backports xwayland
# Debian installs gamescope under /usr/games, outside the service's default PATH.
