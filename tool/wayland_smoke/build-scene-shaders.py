import pathlib
import struct
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
source = root / "android/app/src/main/cpp/wayland/shaders"
out = root / "build/wayland-smoke/shaders"
out.mkdir(parents=True, exist_ok=True)
lines = ["/* Generated from scene.vert/scene.frag by build-scene-shaders.py. */", "#include <stdint.h>"]
for stage in ("vert", "frag"):
    target = out / f"scene.{stage}.spv"
    subprocess.run([sys.argv[1], "--target-env=vulkan1.1", str(source / f"scene.{stage}"), "-o", str(target)], check=True)
    data = target.read_bytes()
    words = struct.unpack(f"<{len(data) // 4}I", data)
    lines.append(f"static const uint32_t scene_{stage}[] = {{")
    for i in range(0, len(words), 8):
        lines.append("    " + ", ".join(f"0x{word:08x}" for word in words[i:i + 8]) + ",")
    lines.append("};")
(source / "scene_spv.h").write_text("\n".join(lines) + "\n", encoding="utf-8")
