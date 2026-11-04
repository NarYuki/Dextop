"""Verify actual X11 pixels after an AppKit window switches to layers and grows.

Run under a private Xvfb display for CI. Requires python3-xlib. GPU framebuffer
readback alone cannot detect a truncated transfer into the native X11 window.
"""
import argparse
from collections import Counter
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

HERE = Path(__file__).resolve().parent


def main():
    from Xlib import display
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime", "executable", "prefix", "log"):
        parser.add_argument("--" + name,required=True,type=Path)
    args = parser.parse_args()
    directory = Path(tempfile.mkdtemp(prefix="dextop-presentation-"))
    release = directory / "release"
    env = dict(os.environ,DEXTOP_PRESENT_RELEASE="/Volumes/SystemRoot" + str(release))
    args.log.parent.mkdir(parents=True,exist_ok=True)
    process = None
    server = None
    try:
        with args.log.open("wb") as output:
            process = subprocess.Popen([sys.executable,str(HERE / "launch_app.py"),
                "--runtime",str(args.runtime.resolve()),"--prefix",str(args.prefix.resolve()),
                "--executable",str(args.executable.resolve())],env=env,stdout=output,stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 20
            while "presentation: blue layer ready" not in args.log.read_text(errors="replace"):
                if process.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError("Presentation probe did not become ready; see " + str(args.log))
                time.sleep(0.1)
            server = display.Display()
            def descendants(window):
                for child in window.query_tree().children:
                    yield child
                    yield from descendants(child)
            windows = []
            for window in descendants(server.screen().root):
                try:
                    if window.get_wm_name() == "Dextop Presentation Probe": windows.append(window)
                except Exception:
                    pass
            if len(windows) != 1:
                raise RuntimeError("Expected exactly one own presentation test window")
            targets = [windows[0],*descendants(windows[0])]
            captured = 0
            for window in targets:
                if window.get_attributes().map_state != 2: continue
                geometry = window.get_geometry()
                if (geometry.width,geometry.height) != (180,430):
                    raise RuntimeError("Native window did not reach the requested size")
                image = window.get_image(0,0,180,430,2,0xffffffff)
                # Supported ARM64/x86_64 X11 targets use little-endian BGRX pixels.
                if server.display.info.image_byte_order != 0 or len(image.data) != 180 * 430 * 4:
                    raise RuntimeError("Unexpected X11 capture pixel layout")
                counts = Counter(image.data[index:index+3] for index in range(0,len(image.data),4))
                blue = counts.get(b"\xff\x00\x00",0)
                if blue != 180 * 430:
                    raise RuntimeError(f"Native window {window.id:x} has {180*430-blue} stale pixels: {counts.most_common(4)}")
                captured += 1
            if captured < 2:
                raise RuntimeError("The parent and its GPU child were not both captured")
    finally:
        if server is not None: server.close()
        release.touch()
        if process is not None:
            try:
                process.wait(timeout=35)
            except subprocess.TimeoutExpired:
                process.terminate()  # Preserve launch_app's scoped Darling shutdown.
                process.wait(timeout=35)
        release.unlink()
        directory.rmdir()
    if process.returncode != 0:
        raise RuntimeError(f"Presentation probe exited {process.returncode}; see {args.log}")
    print("presentation: native parent and GPU child retain all pixels after resize OK")


if __name__ == "__main__":
    main()
