#!/usr/bin/env python3
"""Run a sample in an isolated X display and capture real pixels using libX11."""
import ctypes as c
from contextlib import contextmanager
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import time
import zlib
import signal


@contextmanager
def virtual_display():
    # Like Dextop's session launcher: fakeroot is needed for Xvfb's -nolock.
    # Start it directly; xvfb-run's SIGUSR1 handshake does not cross the fakeroot wrapper.
    sockets = Path("/proc/net/unix").read_text()
    number = next(n for n in range(180, 240) if f"/tmp/.X11-unix/X{n}\n" not in sockets)
    with tempfile.TemporaryDirectory(prefix="dextop-flutter-xvfb-", dir="/tmp") as temporary:
        auth = Path(temporary) / "Xauthority"
        def field(value):
            return struct.pack(">H", len(value)) + value
        auth.write_bytes(struct.pack(">H", 65535) + field(b"") + field(b"") +
                         field(b"MIT-MAGIC-COOKIE-1") + field(os.urandom(16)))
        auth.chmod(0o600)
        old = {name: os.environ.get(name) for name in ("DISPLAY", "XAUTHORITY")}
        os.environ.update(DISPLAY=f":{number}", XAUTHORITY=str(auth))
        with open("flutter-xvfb.log", "wb") as log:
            server = subprocess.Popen(["fakeroot-tcp", "/usr/bin/Xvfb", f":{number}", "-nolock",
                                       "-screen", "0", "1280x720x24", "-nolisten", "tcp", "-auth", str(auth)],
                                      stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                x = c.CDLL("libX11.so.6")
                x.XOpenDisplay.argtypes = [c.c_char_p]
                x.XOpenDisplay.restype = c.c_void_p
                x.XCloseDisplay.argtypes = [c.c_void_p]
                for _ in range(100):
                    display = x.XOpenDisplay(None)
                    if display:
                        x.XCloseDisplay(display)
                        break
                    if server.poll() is not None:
                        raise RuntimeError("Test X server exited: " + Path("flutter-xvfb.log").read_text(errors="replace"))
                    time.sleep(.1)
                else:
                    raise RuntimeError("Test X server did not become ready")
                yield
            finally:
                # fakeroot may place Xvfb in another process group. Match this
                # probe's unique authority path before stopping that server too.
                for process in Path("/proc").iterdir():
                    if not process.name.isdecimal():
                        continue
                    try:
                        if (process / "comm").read_text().strip() != "Xvfb":
                            continue
                        args = (process / "cmdline").read_bytes().split(b"\0")
                        if os.fsencode(str(auth)) in args and f":{number}".encode() in args:
                            os.kill(int(process.name), signal.SIGTERM)
                    except (OSError, ValueError):
                        pass
                if server.poll() is None:
                    try:
                        os.killpg(server.pid, signal.SIGTERM)
                        server.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        os.killpg(server.pid, signal.SIGKILL)
                        server.wait()
                    except ProcessLookupError:
                        pass
                for name, value in old.items():
                    if value is None:
                        os.environ.pop(name, None)
                    else:
                        os.environ[name] = value


def capture():
    x = c.CDLL("libX11.so.6")
    x.XOpenDisplay.argtypes = [c.c_char_p]
    x.XOpenDisplay.restype = c.c_void_p
    x.XDefaultRootWindow.argtypes = [c.c_void_p]
    x.XDefaultRootWindow.restype = c.c_ulong
    x.XGetImage.argtypes = [c.c_void_p, c.c_ulong, c.c_int, c.c_int, c.c_uint, c.c_uint, c.c_ulong, c.c_int]
    x.XGetImage.restype = c.c_void_p
    x.XGetPixel.argtypes = [c.c_void_p, c.c_int, c.c_int]
    x.XGetPixel.restype = c.c_ulong
    x.XDestroyImage.argtypes = [c.c_void_p]
    x.XCloseDisplay.argtypes = [c.c_void_p]
    display = x.XOpenDisplay(None)
    if not display:
        raise RuntimeError("Cannot open the test X display")
    image = None
    try:
        image = x.XGetImage(display, x.XDefaultRootWindow(display), 0, 0, 1280, 720, c.c_ulong(-1), 2)
        if not image:
            raise RuntimeError("Cannot read the test display pixels")
        colors, rows = set(), []
        for row in range(180):
            data = bytearray([0])
            for column in range(320):
                pixel = x.XGetPixel(image, column * 4, row * 4)
                rgb = ((pixel >> 16) & 255, (pixel >> 8) & 255, pixel & 255)
                colors.add(rgb)
                data.extend(rgb)
            rows.append(data)
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", 320, 180, 8, 2, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b"")
        Path("flutter-linux-preview.png").write_bytes(png)
        print("FLUTTER_SCREEN_COLORS=" + str(len(colors)), flush=True)
        return len(colors) > 5
    finally:
        if image:
            x.XDestroyImage(image)
        x.XCloseDisplay(display)


def run(binary):
    child = subprocess.Popen([binary], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    rendered = False
    try:
        time.sleep(8)
        alive = child.poll() is None
        print("FLUTTER_GUI_ALIVE_AFTER_8S=" + str(alive), flush=True)
        if alive:
            rendered = capture()
    finally:
        if child.poll() is None:
            child.terminate()
        try:
            output, _ = child.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            child.kill()
            output, _ = child.communicate()
        print(output.decode(errors="replace"))
    return int(not rendered)


if __name__ == "__main__":
    if sys.argv[1] == "--xvfb":
        with virtual_display():
            raise SystemExit(run(sys.argv[2]))
    raise SystemExit(run(sys.argv[1]))
