#!/bin/sh
# Rebuilds android/app/src/main/jniLibs/arm64-v8a/libvulkan_dextop_turnip.so:
# Mesa's Turnip Vulkan driver for Android (bionic, KGSL), which the Linux
# Wayland compositor loads through adrenotools to present dma-bufs on the GPU.
#
# Requirements: the Android NDK (r28), Python 3 with meson, ninja, mako,
# pyyaml and packaging; glslangValidator and flex/bison on PATH (on Windows,
# Khronos' glslang release and win_flex_bison).
#
#   tool/build_android_turnip.sh <ndk-dir> <work-dir>
set -e
NDK=${1:?NDK directory}
WORK=${2:?work directory}
VERSION=25.0.7
REPO=$(cd "$(dirname "$0")/.." && pwd)

mkdir -p "$WORK" && cd "$WORK"
[ -f mesa-$VERSION.tar.xz ] || curl -fsSLO https://archive.mesa3d.org/mesa-$VERSION.tar.xz
echo "592272df3cf01e85e7db300c449df5061092574d099da275d19e97ef0510f8a6  mesa-$VERSION.tar.xz" | sha256sum -c -
rm -rf mesa-$VERSION && tar -xf mesa-$VERSION.tar.xz && cd mesa-$VERSION

# Dextop's KGSL GPU timestamp, shared with the container build (LinuxMesaPatches.kt).
python3 - "$REPO/android/app/src/main/kotlin/moe/n4tsu/dextop/linux/LinuxMesaPatches.kt" <<'PY' | patch -p1
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
patch = re.search(r"<<'DEXTOP_MESA_PATCH'\n(.*?)\nDEXTOP_MESA_PATCH", text, re.S).group(1)
# Only the Turnip part applies to the Android driver.
print(patch.split("--- a/src/egl")[0])
PY

BIN="$NDK/toolchains/llvm/prebuilt/$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)/bin"
EXE=""; [ -f "$BIN/clang.exe" ] && EXE=".exe"
CC="$BIN/aarch64-linux-android30-clang"; [ -f "$CC.cmd" ] && CC="$CC.cmd"
CXX="$BIN/aarch64-linux-android30-clang++"; [ -f "$CXX.cmd" ] && CXX="$CXX.cmd"
cat > ../cross.ini <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$BIN/llvm-ar$EXE'
strip = '$BIN/llvm-strip$EXE'
nm = '$BIN/llvm-nm$EXE'
pkg-config = 'false'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8'
endian = 'little'
EOF

# Windows flex output includes io.h; Android has what it needs in unistd.h.
mkdir -p ../iostub
printf '#include <stdio.h>\n#include <unistd.h>\n#define _isatty isatty\n#define _fileno fileno\n' > ../iostub/io.h

meson setup ../android-build . --cross-file ../cross.ini \
    -Dbuildtype=release -Db_ndebug=true -Dplatforms=android -Dplatform-sdk-version=30 \
    -Dandroid-stub=true -Dandroid-libbacktrace=disabled \
    -Dgallium-drivers= -Dvulkan-drivers=freedreno -Dfreedreno-kmds=kgsl \
    -Dllvm=disabled -Dglx=disabled -Degl=disabled -Dgbm=disabled -Dopengl=false \
    -Dgles1=disabled -Dgles2=disabled -Dzstd=disabled -Dvalgrind=disabled -Dlibunwind=disabled \
    -Dlmsensors=disabled -Dbuild-tests=false -Dvideo-codecs= -Dexpat=disabled \
    -Dc_args="-I$WORK/iostub" -Dcpp_args="-I$WORK/iostub" -Dcpp_link_args=-static-libstdc++
ninja -C ../android-build src/freedreno/vulkan/libvulkan_freedreno.so
"$BIN/llvm-strip$EXE" -o "$REPO/android/app/src/main/jniLibs/arm64-v8a/libvulkan_dextop_turnip.so" \
    ../android-build/src/freedreno/vulkan/libvulkan_freedreno.so
echo "built libvulkan_dextop_turnip.so"
