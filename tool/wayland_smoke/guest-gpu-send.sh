#!/bin/sh
set -eu
cd /tmp/dextop-gpu-probe
cc -O2 -Wall -Wextra -DDEXTOP_GUEST_GPU_PROBE guest-gpu-send.c \
  guest-dmabuf-send.c vulkan-dmabuf-roundtrip.c -lvulkan -pthread -o guest-gpu-send
export VK_ICD_FILENAMES=/opt/dextop-mesa/share/vulkan/icd.d/freedreno_icd.aarch64.json
exec ./guest-gpu-send
