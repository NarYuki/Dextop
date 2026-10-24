#!/bin/sh
set -eu
source_archive=$(realpath "$1")
work=$(realpath "$2")
here=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
printf '%s  %s\n' 834054084604cacb2fe82d9151256bac83fd353d50b602f6824a1ce8ae73a3a8 "$source_archive" | sha256sum -c -
mkdir -p "$work"
tar -xJf "$source_archive" -C "$work"
source_root="$work/DD-Turnip-v0.3.3-source"
python3 "$source_root/drivers/scripts/apply_patches.py" linux "$source_root/mesa"
python3 "$here/banner/banner_ahb_wsi.py" "$source_root/mesa"
meson setup "$work/build" "$source_root/mesa" --buildtype release \
    -Dvulkan-drivers=freedreno -Dfreedreno-kmds=msm,kgsl -Dgallium-drivers= \
    -Dplatforms=wayland,x11 -Dopengl=false -Dgbm=disabled -Dglx=disabled \
    -Degl=disabled -Dllvm=disabled -Dvulkan-layers= -Dtools= -Dvideo-codecs= -Dxmlconfig=disabled
ninja -C "$work/build" -j "${BUILD_JOBS:-6}" src/freedreno/vulkan/libvulkan_freedreno.so
strip -o "$work/libvulkan_freedreno.so" "$work/build/src/freedreno/vulkan/libvulkan_freedreno.so"
