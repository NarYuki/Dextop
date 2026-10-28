#!/bin/sh
set -eu
source_directory=${1:?Mesa source directory from the pinned DD-Turnip source archive}
build_directory=${2:?Build output directory}
python3 "$(dirname "$0")/patch_linux_gpu_runtime.py" "$source_directory"
meson setup "$build_directory" "$source_directory" \
    --prefix=/opt/dextop-gpu-direct --buildtype=release -Db_ndebug=true \
    -Dgallium-drivers=zink -Dvulkan-drivers= -Dplatforms=wayland,x11 \
    -Dopengl=true -Dgbm=enabled -Dglx=dri -Degl=enabled -Dglvnd=enabled \
    -Dllvm=disabled -Dvulkan-layers= -Dtools= -Dvideo-codecs= -Dxmlconfig=disabled
ninja -C "$build_directory" -j "${DEXTOP_BUILD_JOBS:-4}"
ninja -C "$build_directory" install
cc -shared -fPIC -O2 "$(dirname "$0")/../android/app/src/main/cpp/linux_kgsl_gbm.c" \
    -o /opt/dextop-gpu-direct/lib/aarch64-linux-gnu/libdextop_kgsl_gbm.so -ldl -pthread
