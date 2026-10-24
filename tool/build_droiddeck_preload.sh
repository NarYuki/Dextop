#!/bin/sh
# Rebuilds android/app/src/main/jniLibs/arm64-v8a/libdextop_blsession.so:
# DroidDeck's session preload (third_party/droiddeck-preload, from
# Droid-Deck/DroidDeck 26ca374 tools/linuxfs/preload, GPL-3.0) for glibc 2.31
# guests, as DroidDeck's build makes libblsession.so. Steam needs it under
# PRoot: SysV IPC emulation, TracerPid hiding, steamwebhelper arguments.
#
#   tool/build_droiddeck_preload.sh <zig>
set -e
ZIG=${1:?zig executable (0.13.0)}
REPO=$(cd "$(dirname "$0")/.." && pwd)
cd "$REPO/third_party/droiddeck-preload"
"$ZIG" cc -target aarch64-linux-gnu.2.31 -shared -fPIC -DDEXTOP_BINFMT -O2 -Wall -pthread -s \
    -o "$REPO/android/app/src/main/jniLibs/arm64-v8a/libdextop_blsession.so" ./*.c \
    "$REPO/android/app/src/main/cpp/linux_wine_memory.c" \
    "$REPO/android/app/src/main/cpp/linux_socket_markers.c" -ldl
echo "built libdextop_blsession.so"
