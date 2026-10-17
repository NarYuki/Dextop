#!/bin/sh
set -eu
# /tmp/dextop-dd-drm.c is the unchanged DroidDeck GPL-3.0 source noted in README.
cc -shared -fPIC -O2 -Wall -Wextra $(pkg-config --cflags libdrm) \
  /tmp/kgsl-drm-probe.c /tmp/dextop-dd-drm.c -ldl -pthread \
  -o /opt/dextop-gamescope-probe/kgsl-drm-probe.so
