#!/usr/bin/bash
set -euxo pipefail
VERSION=${GAMESCOPE_VERSION:-3.16.29}
WORK=/work
cd "$WORK"
grep -q '^DisableSandbox' /etc/pacman.conf || sed -i 's/^\[options\]/[options]\nDisableSandbox/' /etc/pacman.conf
{ for m in https://ca.us.mirror.archlinuxarm.org https://fl.us.mirror.archlinuxarm.org https://de3.mirror.archlinuxarm.org https://nl.mirror.archlinuxarm.org; do echo "Server = $m/\$arch/\$repo"; done; cat /etc/pacman.d/mirrorlist; } > /etc/pacman.d/mirrorlist.new
mv /etc/pacman.d/mirrorlist.new /etc/pacman.d/mirrorlist
pacman -Syu --noconfirm --needed git sudo zstd binutils
id builder >/dev/null 2>&1 || useradd -m builder
echo 'builder ALL=(ALL) NOPASSWD: ALL' > /etc/sudoers.d/builder
rm -rf pkg && mkdir pkg && cp -r tools/gamescope/patches pkg/ && chown -R builder pkg
sudo -u builder bash -c "
set -euxo pipefail
cd pkg
for rel in 1 2 3 4; do
  if git clone -q --depth 1 --branch ${VERSION}-\$rel https://gitlab.archlinux.org/archlinux/packaging/packages/gamescope.git arch 2>/dev/null; then break; fi
done
test -f arch/PKGBUILD
cd arch
grep -q '^pkgver=${VERSION}$' PKGBUILD
cp ../patches/*.patch .
PATCHES=\$(ls *.patch | sort)
{ echo; echo 'source+=('; for p in \$PATCHES; do echo \"  \$p\"; done; echo ')'; for p in \$PATCHES; do echo \"sha256sums+=('SKIP')\"; done; } >> PKGBUILD
cat >> PKGBUILD <<'PREP'

_droiddeck_prepare() {
  cd \"\$srcdir/gamescope\"
  for p in \$(ls \"\$srcdir\"/*.patch | sort); do
    echo \"applying \$(basename \"\$p\")\"
    patch -p1 --no-backup-if-mismatch < \"\$p\"
  done
}
if declare -f prepare >/dev/null; then
  eval \"\$(declare -f prepare | sed 's/^prepare ()/_arch_prepare ()/')\"
  prepare() { _arch_prepare; _droiddeck_prepare; }
else
  prepare() { _droiddeck_prepare; }
fi
PREP
makepkg -A -s --noconfirm --skipchecksums --skippgpcheck
meson test -C src/build --no-rebuild --print-errorlogs cursor_routing
ls -l *.pkg.tar.*
"
cd "$WORK"
rm -rf out && mkdir -p out/usr/local/bin
PKG=$(ls pkg/arch/gamescope-*.pkg.tar.* | grep -v -- '-debug-' | head -1)
tar --use-compress-program=unzstd -xf "$PKG" -C out --strip-components=2 usr/bin/gamescope
mv out/gamescope out/usr/local/bin/gamescope
chmod 755 out/usr/local/bin/gamescope
strip --strip-unneeded out/usr/local/bin/gamescope || true
NEEDED=$(readelf -d out/usr/local/bin/gamescope | sed -n 's/.*NEEDED.*\[\(.*\)\]/\1/p')
echo "NEEDED: $NEEDED"
MISSING=""
for lib in $NEEDED; do grep -qx "$lib" tools/gamescope/runtime-sonames.txt || MISSING="$MISSING $lib"; done
if [ -n "$MISSING" ]; then echo "ERROR: not in the runtime:$MISSING"; exit 3; fi
(cd out && tar --use-compress-program='zstd -19' -cf ../gamescope.tzst usr)
sha256sum gamescope.tzst | tee gamescope.tzst.sha256
ls -l gamescope.tzst out/usr/local/bin/gamescope
