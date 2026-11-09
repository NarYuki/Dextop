#!/bin/sh
# Runs inside the proot after guest_build.sh: records and fetches the corresponding source
# for GPL compliance, then removes everything that must not ship in the image.
export DEBIAN_FRONTEND=noninteractive
IMAGE_VERSION="$1"
TAG="$2"
DOC=/usr/share/doc/dextop-image
OUT=/var/tmp/dextop-sources
mkdir -p "$DOC" "$OUT"
. /etc/os-release

cat > /etc/apt/sources.list.d/zz-dextop-src.list <<EOF
deb-src http://deb.debian.org/debian $VERSION_CODENAME main
deb-src http://deb.debian.org/debian $VERSION_CODENAME-updates main
deb-src http://deb.debian.org/debian $VERSION_CODENAME-backports main
deb-src http://deb.debian.org/debian-security $VERSION_CODENAME-security main
EOF
apt-get update || exit 10

printf 'source_package\tsource_version\tbinary_package\tbinary_version\n' > "$DOC/packages.tsv"
dpkg-query -W -f='${db:Status-Status}\t${source:Package}\t${source:Version}\t${Package}\t${Version}\n' |
    awk -F'\t' '$1=="installed"{print $2"\t"$3"\t"$4"\t"$5}' | sort -u >> "$DOC/packages.tsv"

cd "$OUT" || exit 11
: > "$OUT/MISSING.txt"
tail -n +2 "$DOC/packages.tsv" | cut -f1,2 | sort -u | while IFS="$(printf '\t')" read -r name version; do
    apt-get source --download-only -q "$name=$version" >/dev/null 2>&1 || printf '%s %s\n' "$name" "$version" >> "$OUT/MISSING.txt"
done
echo "sources-missing $(wc -l < "$OUT/MISSING.txt")"

rm -f /etc/apt/sources.list.d/zz-dextop-src.list
apt-get update || exit 12

cat > "$DOC/README" <<EOF
Dextop prebuilt Debian image $IMAGE_VERSION

This image contains Debian $VERSION_CODENAME packages, DD-Turnip v0.2.0 and the IceWM theme
Materia-Manjaro-Dark by rainerhg7 (GPL-3.0, https://www.opendesktop.org/p/1393603/, license in
its COPYING file; its archive is in the source bundle).
Each package's license is in /usr/share/doc/<package>/copyright. DD-Turnip's are in
/opt/dextop-mesa/share/doc/DD-Turnip-v0.2.0/. packages.tsv lists every installed package
with the source package and version it was built from.

The complete corresponding source for this image is published next to it:
https://github.com/NarYuki/dextop-linux-images/releases/tag/$TAG

WRITTEN OFFER: for at least three years after the last distribution of this image, the
Dextop project will provide that source to anyone on request, for no more than the cost
of physically performing the transfer, through https://github.com/NarYuki/dextop-linux-images/issues.
EOF

apt-get clean
rm -rf /var/lib/apt/lists/* /var/cache/apt/* /var/cache/debconf/*-old /var/lib/dpkg/*-old
rm -rf /tmp/* /tmp/.[!.]* /var/tmp/*.deb /root/.cache /root/.wget-hsts /var/log/apt/* /var/log/dpkg.log
: > /etc/machine-id
rm -f /var/lib/dbus/machine-id
echo guest-finish-complete
