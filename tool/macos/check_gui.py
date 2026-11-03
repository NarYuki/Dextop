"""Exercise the own-source AppKit probe on a private Xvfb display."""

import argparse
import os
from pathlib import Path
import secrets
import shutil
import socket
import struct
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--probes", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--architecture", choices=("aarch64", "x86_64"), default="aarch64")
    parser.add_argument("--abstract-rpc", action="store_true")
    parser.add_argument("--anonymous-exec", action="store_true")
    parser.add_argument("--persistence", action="store_true", help="Verify UTF-8 save and reload across app restart")
    parser.add_argument("--dialog", action="store_true", help="Verify a modal dialog and return to the main window")
    args = parser.parse_args()
    for executable in ("Xvfb", "xdotool", "import"):
        if not shutil.which(executable):
            parser.error(f"Required GUI test tool missing: {executable}")
    runtime = args.runtime.resolve()
    arch = "arm64" if args.architecture == "aarch64" else "x86_64"
    binary = args.probes.resolve() / f"appkit-macos-{arch}"
    launcher = runtime / "bin/darling"
    if not binary.is_file() or not launcher.is_file():
        parser.error("Build and stage the GUI runtime and own-source probe first")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    directory = tempfile.mkdtemp(prefix="dextop-gui-")
    env = dict(os.environ, DARLING_NONROOT="1", DARLING_INSTALL_PREFIX=str(runtime),
               DPREFIX=directory + "/prefix",
               DEXTOP_GUI_DOCUMENT="/Volumes/SystemRoot" + directory + "/document.txt",
               DEXTOP_DARLING_ABSTRACT_RPC="1" if args.abstract_rpc else "0",
               DEXTOP_DARLING_ANON_EXEC="1" if args.anonymous_exec else "0")
    env.pop("WAYLAND_DISPLAY", None)
    env.pop("DARLING_PROFILE", None)
    env.setdefault("DEXTOP_DARLING_HOST_LIBRARY_PATH", env.get("LD_LIBRARY_PATH", ""))
    print(f"GUI probe prefix: {directory}", flush=True)
    success = False
    app = None
    server = None
    # WSLg's read-only X11 directory prevents Xvfb's -displayfd allocator from
    # selecting a display even when abstract Unix sockets are available.
    env["DISPLAY"] = ":" + str(100 + secrets.randbelow(20000))
    try:
        with (output / "xvfb.log").open("wb") as server_log, (output / "app.log").open("wb") as app_log:
            server = subprocess.Popen(["Xvfb", env["DISPLAY"], "-screen", "0",
                                       "1024x768x24", "-nolisten", "tcp"],
                                      stdout=server_log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 15
            while True:
                if server.poll() is not None:
                    raise RuntimeError("Xvfb startup failed; inspect xvfb.log")
                ready = subprocess.run(["xdotool", "getdisplaygeometry"], env=env,
                                       capture_output=True, timeout=5)
                if ready.returncode == 0:
                    # Never send synthetic input to a pre-existing display if
                    # a randomly selected display number happened to collide.
                    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as peer:
                        peer.connect("\0/tmp/.X11-unix/X" + env["DISPLAY"][1:])
                        credentials = peer.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12)
                    if struct.unpack("3i", credentials)[0] != server.pid:
                        raise RuntimeError("X display is not owned by this test's Xvfb")
                    break
                if time.monotonic() > deadline:
                    raise RuntimeError("Private Xvfb display did not become ready")
                time.sleep(0.2)

            def xdo(*arguments, check=True):
                return subprocess.run(["xdotool", *arguments], env=env,
                                      capture_output=True, text=True, check=check, timeout=10)

            def capture(name):
                subprocess.run(["import", "-display", env["DISPLAY"], "-window", "root",
                                str(output / name)], check=True, timeout=15)

            def wait_for_event(message, offset=0):
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    if message in (output / "app.log").read_text(errors="replace")[offset:]:
                        return
                    if app.poll() is not None:
                        break
                    time.sleep(0.1)
                raise RuntimeError(f"AppKit event not received: {message}")

            try:
                app = subprocess.Popen([str(launcher), "exec", "/Volumes/SystemRoot" + str(binary)],
                                       env=env, stdout=app_log, stderr=subprocess.STDOUT)
                window = None
                deadline = time.monotonic() + 60
                while time.monotonic() < deadline:
                    found = xdo("search", "--name", "^Dextop macOS AppKit probe$", check=False)
                    if found.returncode == 0 and found.stdout.strip():
                        window = found.stdout.splitlines()[-1]
                        break
                    if app.poll() is not None:
                        raise RuntimeError(f"App exited before creating a window: {app.returncode}")
                    time.sleep(0.2)
                if window is None:
                    raise RuntimeError("AppKit window did not appear")
                time.sleep(1)
                capture("window.png")
                if args.dialog:
                    xdo("mousemove", "--window", window, "240", "292", "click", "1")
                    dialog = xdo("search", "--sync", "--name", "^Dextop dialog probe$").stdout.splitlines()[-1]
                    wait_for_event("appkit: dialog layout OK")
                    time.sleep(0.5)
                    capture("dialog.png")
                    xdo("windowfocus", "--sync", dialog)
                    xdo("key", "Return")
                    wait_for_event("appkit: dialog accepted")
                # Coordinates refer to this probe's fixed initial layout, never a user display.
                xdo("mousemove", "--window", window, "120", "245", "click", "1")
                xdo("key", "ctrl+a")
                xdo("type", "--clearmodifiers", "Dextop GUI input")
                xdo("mousemove", "--window", window, "390", "245", "click", "1")
                wait_for_event("appkit: input=Dextop GUI input")
                xdo("windowsize", window, "640", "430")
                wait_for_event("appkit: resized=640x380")
                capture("resized.png")
                xdo("mousemove", "--window", window, "27", "14", "click", "1")
                app.wait(timeout=15)
                content = (output / "app.log").read_text(errors="replace")
                expected = ("appkit: window requested", "appkit: input=Dextop GUI input",
                            "appkit: resized=640x380")
                if app.returncode or any(message not in content for message in expected):
                    raise RuntimeError(f"GUI input/resize/close verification failed: exit {app.returncode}")
                if args.persistence:
                    if "appkit: saved=Dextop GUI input 日本語" not in content:
                        raise RuntimeError("Foundation did not save the UTF-8 document")
                    offset = len(content)
                    app = subprocess.Popen([str(launcher), "exec", "/Volumes/SystemRoot" + str(binary)],
                                           env=env, stdout=app_log, stderr=subprocess.STDOUT)
                    wait_for_event("appkit: loaded=Dextop GUI input 日本語", offset=offset)
                    found = xdo("search", "--sync", "--name", "^Dextop macOS AppKit probe$")
                    window = found.stdout.splitlines()[-1]
                    time.sleep(1)
                    capture("reopened.png")
                    xdo("mousemove", "--window", window, "27", "14", "click", "1")
                    app.wait(timeout=15)
                    if app.returncode:
                        raise RuntimeError(f"Reopened app did not close normally: {app.returncode}")
            finally:
                with (output / "shutdown.log").open("wb") as shutdown_log:
                    result = subprocess.run([str(launcher), "shutdown"], env=env,
                                            stdout=shutdown_log, stderr=subprocess.STDOUT, timeout=30)
                if result.returncode:
                    raise RuntimeError(f"Scoped shutdown failed: exit {result.returncode}")
                if app is not None:
                    app.wait(timeout=10)
            success = True
            if args.persistence:
                print("Foundation UTF-8 save/reload across app restart: OK", flush=True)
            print("AppKit window/input/resize/close and scoped shutdown: OK. Inspect captured rendering separately.", flush=True)
    finally:
        if server is not None:
            server.terminate()
            server.wait(timeout=5)
        if success:
            shutil.rmtree(directory)
        else:
            print(f"Failed GUI prefix retained: {directory}; inspect {output}", flush=True)


if __name__ == "__main__":
    main()
