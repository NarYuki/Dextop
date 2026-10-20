#!/bin/sh
set -eu
export PATH=/usr/games:/usr/local/bin:/usr/bin:/bin
export LD_PRELOAD=/opt/dextop/lib/libdextop_unixsock.so:/opt/dextop-gamescope-probe/kgsl-drm-probe.so
export DEXTOP_SOCKET_NS=dextop-c8147ec8bb3fc
export XDG_RUNTIME_DIR=/tmp/runtime-root WAYLAND_DISPLAY=wayland-probe XDG_SESSION_TYPE=wayland
export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true
unset DISPLAY MESA_VK_WSI_DEBUG
export VK_ICD_FILENAMES=/opt/dextop-gamescope-probe/driver/icd.json
gamescope --version
exec timeout 25 gamescope --backend wayland --expose-wayland --force-composition -W 640 -H 480 -w 640 -h 480 -- sh -c 'glxinfo -B; vblank_mode=0 timeout 10 stdbuf -oL glxgears'
