#!/usr/bin/env bash
# Rebuilds the prebuilt arm64 native libraries that used to be 4 KB-aligned
# (Deck PulseAudio host and DD-Turnip) with 16 KB ELF segment alignment, as
# Google Play requires for Android 15+ targets.
#
#   NDK=/path/to/android-ndk-r28c tool/build_16k_native_libs.sh <download-dir>
#
# <download-dir> must hold the source archives below; their SHA-256 values are checked.
# Needs meson, ninja, mako, pyyaml (Python), pkg-config, bison, flex, git and a Linux x86_64 host.
set -euo pipefail
DL=$(cd "${1:?usage: $0 <download-dir>}" && pwd)
: "${NDK:?set NDK to an Android NDK r28 or later}"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
W=$ROOT/build/16k
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
API=28
CC=$TC/aarch64-linux-android$API-clang
PREFIX=$W/sysroot
OUT=$W/out
PAGE="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
JNI=$ROOT/android/app/src/main/jniLibs/arm64-v8a
ASSETS=$ROOT/android/app/src/main/assets/deck-audio/modules

check() { echo "$2  $DL/$1" | sha256sum -c --quiet; }
check pulseaudio-13.0.tar.xz 961b23ca1acfd28f2bc87414c27bb40e12436efcf2158d29721b1e89f3f28057
check libsndfile-1.0.31.tar.bz2 a8cfb1c09ea6e90eff4ca87322d4168cdbe5035cb48717b40bf77e751cc02163
check libtool-2.4.7.tar.xz 4f7f217f057ce655ff22559ad221a0fd8ef84ad1fc5fcb6990cecc333aa1635d
check DD-Turnip-v0.2.0-source.tar.xz f35e476bef5a6649fc66bb3231d5be4330b969fc0ee6032ec4ef6df2c6cae172

rm -rf "$W/src" "$PREFIX" "$OUT" "$W/pa-build" "$W/pa-dest"
mkdir -p "$W/src" "$PREFIX/lib" "$OUT/modules"
for a in pulseaudio-13.0.tar.xz libsndfile-1.0.31.tar.bz2 libtool-2.4.7.tar.xz DD-Turnip-v0.2.0-source.tar.xz; do
  tar -C "$W/src" -xf "$DL/$a"
done

export CC AR=$TC/llvm-ar RANLIB=$TC/llvm-ranlib
export CFLAGS="-O2 -fPIC -ffile-prefix-map=$W=/dextop-16k" LDFLAGS="$PAGE"
shlib() { # <out> <soname> <archive> [extra...]
  local out=$1 soname=$2 archive=$3; shift 3
  "$CC" -shared $PAGE -Wl,-soname,"$soname" -Wl,--whole-archive "$archive" -Wl,--no-whole-archive "$@" -o "$out"
}

# libltdl 2.4.7 and libsndfile 1.0.31, unversioned sonames as before.
(cd "$W/src/libtool-2.4.7/libltdl" && ./configure -q --host=aarch64-linux-android --prefix="$PREFIX" \
  --enable-ltdl-install --disable-shared --enable-static && make -j"$(nproc)" && make install) >"$W/ltdl.log" 2>&1
shlib "$OUT/libltdl.so" libltdl.so "$PREFIX/lib/libltdl.a" -ldl
(cd "$W/src/libsndfile-1.0.31" && ./configure -q --host=aarch64-linux-android --prefix="$PREFIX" \
  --disable-shared --enable-static --disable-external-libs --disable-sqlite --disable-alsa --disable-full-suite &&
  make -j"$(nproc)" src/libsndfile.la &&
  make install-libLTLIBRARIES install-includeHEADERS install-nodist_includeHEADERS install-pkgconfigDATA) >"$W/sndfile.log" 2>&1
shlib "$OUT/libsndfile.so" libsndfile.so "$PREFIX/lib/libsndfile.a" \
  -Wl,--version-script="$W/src/libsndfile-1.0.31/src/Symbols.gnu-binutils" -lm
cp "$OUT/libltdl.so" "$OUT/libsndfile.so" "$PREFIX/lib/"

# PulseAudio 13.0 with the bionic patch (no NLS, no libcap/execinfo/cpuid,
# memfd detection, unversioned libpulse.so without RUNPATH).
(cd "$W/src/pulseaudio-13.0" && patch -p1 -s <"$ROOT/third_party/deck-audio/pulseaudio-13.0-android.patch")
cat >"$W/pa-cross.ini" <<EOF
[binaries]
c = '$CC'
cpp = '$TC/aarch64-linux-android$API-clang++'
ar = '$TC/llvm-ar'
strip = '$TC/llvm-strip'
pkg-config = ['env', 'PKG_CONFIG_LIBDIR=$PREFIX/lib/pkgconfig', 'pkg-config']
[built-in options]
c_args = ['-Wno-error=int-conversion', '-O2', '-fPIC', '-ffile-prefix-map=$W=/dextop-16k', '-I$PREFIX/include']
c_link_args = ['-Wl,--undefined-version', '-L$PREFIX/lib', '-Wl,-z,max-page-size=16384', '-Wl,-z,common-page-size=16384']
[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8'
endian = 'little'
EOF
off=(alsa asyncns avahi dbus fftw glib gsettings gtk jack lirc openssl orc samplerate soxr speex systemd udev x11 webrtc-aec)
meson setup "$W/pa-build" "$W/src/pulseaudio-13.0" --cross-file "$W/pa-cross.ini" --prefix=/dextop-pa \
  -Dbuildtype=release -Dman=false -Dtests=false -Ddatabase=simple -Drunning-from-build-tree=false \
  -Dbluez5=false -Dhal-compat=false -Dipv6=true -Dadrian-aec=true \
  $(printf -- '-D%s=disabled ' "${off[@]}") >"$W/pa-setup.log"
ninja -C "$W/pa-build" >"$W/pa-ninja.log"
DESTDIR="$W/pa-dest" meson install -C "$W/pa-build" --no-rebuild >"$W/pa-install.log"
D=$W/pa-dest/dextop-pa/lib
cp "$D/libpulse.so" "$D/pulseaudio/libpulsecommon-13.0.so" "$D/pulseaudio/libpulsecore-13.0.so" "$OUT/"
cp "$W/pa-dest/dextop-pa/bin/pulseaudio" "$OUT/libpulseaudio.so"
cp "$D/pulse-13.0/modules/module-native-protocol-tcp.so" "$D/pulse-13.0/modules/libprotocol-native.so" "$OUT/modules/"

# DroidDeck's DirectAudio sink (third_party/deck-audio), against the rebuilt core.
S=$ROOT/third_party/deck-audio
"$TC/aarch64-linux-android28-clang" -O2 -shared -fPIC -Wall -Wno-unused-parameter -Wno-error=int-conversion \
  -DHAVE_CONFIG_H -ffile-prefix-map="$W=/dextop-16k" -ffile-prefix-map="$S=/dextop-16k/deck-audio" $PAGE \
  -I"$W/pa-build" -I"$W/pa-build/src" -I"$W/src/pulseaudio-13.0/src" -I"$PREFIX/include" -I"$S" \
  "$S/module-directaudio-native-sink.c" -L"$OUT" -l:libpulsecore-13.0.so -l:libpulsecommon-13.0.so -l:libpulse.so \
  -laaudio -o "$OUT/modules/module-directaudio-native-sink.so"
for f in "$OUT"/*.so "$OUT"/modules/*.so; do "$TC/llvm-strip" --strip-unneeded "$f"; done

# DD-Turnip v0.2.0 (Mesa bffd91947e + DD patch set) with 16 KB pages.
T=$W/src/DD-Turnip-v0.2.0-source
(cd "$T/mesa" && git init -q && git add -A && git -c user.name=build -c user.email=build@localhost commit -qm source)
sed -e "s#^\[host_machine\]#[built-in options]\nc_link_args = ['-Wl,-z,max-page-size=16384', '-Wl,-z,common-page-size=16384']\ncpp_link_args = ['-Wl,-z,max-page-size=16384', '-Wl,-z,common-page-size=16384']\n\n[host_machine]#" \
  "$T/drivers/scripts/build_android.sh" >"$T/drivers/scripts/build_android_16k.sh"
ANDROID_NDK_HOME=$NDK MESA_GIT_SHA1_OVERRIDE=bffd91947efc1932da99aca69fc540d6d4201b0c \
  bash "$T/drivers/scripts/build_android_16k.sh" "$T/mesa" >"$W/turnip-build.log" 2>&1
cp "$T/drivers/out/android/libvulkan_freedreno.so" "$OUT/libvulkan_dextop_turnip.so"

# Verify and install.
for f in "$OUT"/*.so "$OUT"/modules/*.so; do
  "$TC/llvm-readelf" -lW "$f" | awk -v f="$f" '/LOAD/ { if (strtonum($NF) < 16384) { print "4 KB segment in " f; bad = 1 } } END { exit bad }'
  if "$TC/llvm-readelf" -dW "$f" | grep -qE 'RUNPATH|RPATH'; then echo "RUNPATH left in $f"; exit 1; fi
  if grep -q "$ROOT" "$f"; then echo "Local build path in $f"; exit 1; fi
done
cp "$OUT"/*.so "$JNI/"
cp "$OUT"/modules/*.so "$ASSETS/"
python3 - "$ROOT" <<'EOF'
import hashlib, json, pathlib, sys
root = pathlib.Path(sys.argv[1])
names = [f'android/app/src/main/jniLibs/arm64-v8a/{n}' for n in (
    'libpulseaudio.so', 'libpulsecore-13.0.so', 'libpulsecommon-13.0.so', 'libpulse.so',
    'libltdl.so', 'libsndfile.so', 'libffi.so')]
names += [f'android/app/src/main/assets/deck-audio/modules/{n}' for n in (
    'module-native-protocol-tcp.so', 'libprotocol-native.so', 'module-directaudio-native-sink.so')]
manifest = {
    'build': 'tool/build_16k_native_libs.sh',
    'pulseaudio': '13.0', 'libsndfile': '1.0.31', 'libltdl': '2.4.7', 'pageSize': 16384,
    'files': {n: hashlib.sha256((root / n).read_bytes()).hexdigest() for n in names},
}
(root / 'third_party/deck-audio/manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
EOF
echo "16 KB native libraries installed into $JNI and $ASSETS"
