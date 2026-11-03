"""Launch a local macOS bundle and own its Darling lifecycle inside Linux/PRoot.

Keep this supervisor inside the same PRoot until scoped shutdown completes.
This is execution compatibility, not an application security sandbox.
"""

import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import platform
import plistlib
import re
import shlex
import signal
import struct
import subprocess
import sys
from xml.parsers.expat import ExpatError


CPU_TYPES = {"aarch64": 0x0100000C, "arm64": 0x0100000C, "x86_64": 0x01000007}
CORE_COMPONENTS = ("bin/darling", "bin/darlingserver", "libexec/darling/bin/mldr",
                   "libexec/darling/usr/lib/dyld", "libexec/darling/sbin/launchd")


def runtime_status(runtime, home):
    missing = [name for name in CORE_COMPONENTS if not (runtime / name).is_file()]
    gui = runtime / "libexec/darling/System/Library/Frameworks/AppKit.framework/Versions/C/AppKit"
    applications = []
    for record in sorted((home / ".local/share/dextop/macos/apps").glob("*.json")):
        if not re.fullmatch(r"[a-f0-9]{24}", record.stem) or record.stat().st_size > 64 * 1024:
            continue
        try:
            saved = json.loads(record.read_text(encoding="utf-8"))
            bundle = Path(saved["bundle"])
            info = bundle_info(bundle)
            applications.append({"key": record.stem, "name": info["name"], "bundle": str(bundle), "present": True})
        except (OSError, ValueError, TypeError, KeyError, plistlib.InvalidFileException, ExpatError):
            applications.append({"key": record.stem, "name": record.stem, "present": False})
    return {"runtime": str(runtime), "componentsPresent": not missing,
            "guiPresent": gui.is_file(), "missing": missing,
            "architecture": platform.machine(), "applications": applications}


def application_key(info):
    return hashlib.sha256(info.get("bundle", info["executable"]).encode()).hexdigest()[:24]


def registration_paths(home, key):
    if not re.fullmatch(r"[a-f0-9]{24}", key):
        raise ValueError("Invalid registered application id")
    return (home / ".local/share/dextop/macos/apps" / (key + ".json"),
            home / ".local/share/applications" / ("dextop-macos-" + key + ".desktop"))


def write_atomic(path, content):
    import tempfile
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="\n",
                                         dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(content)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def register_bundle(info, runtime, prefix, home):
    key = application_key(info)
    record, desktop = registration_paths(home, key)
    launcher = str(Path(__file__).resolve())
    if any(c in launcher for c in "\n\r%"):
        raise ValueError("Install the launcher in a path without line breaks or percent characters")
    # Bundle names are desktop string values, never shell syntax or new entries.
    name = info["name"].replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
    write_atomic(record, json.dumps({"bundle": info["bundle"], "runtime": str(runtime),
                                     "prefix": str(prefix)}, ensure_ascii=False))
    # No user-controlled bundle path appears in Exec. The fixed id selects JSON
    # metadata, and each launch revalidates the bundle and native architecture.
    write_atomic(desktop, "[Desktop Entry]\nType=Application\n" + f"Name={name}\n" +
                 f"Exec=python3 {shlex.quote(launcher)} --registered {key}\n" +
                 "Icon=application-x-executable\nTerminal=false\nCategories=Utility;\n" +
                 "X-Dextop-DisplayBackend=x11\nX-Dextop-Runtime=macos\n")
    return {"id": "dextop-macos-" + key + ".desktop", "name": info["name"], "bundle": info["bundle"]}


def executable_architectures(path):
    """Read public Mach-O container headers, never instructions or load payloads."""
    with path.open("rb") as stream:
        header = stream.read(8)
        if len(header) != 8:
            raise ValueError("Executable has no Mach-O header")
        magic = header[:4]
        if magic in (b"\xcf\xfa\xed\xfe", b"\xfe\xed\xfa\xcf"):
            endian = "<" if magic[0] == 0xCF else ">"
            return {struct.unpack(endian + "I", header[4:])[0]}
        fat = {b"\xca\xfe\xba\xbe": (">", 20), b"\xbe\xba\xfe\xca": ("<", 20),
               b"\xca\xfe\xba\xbf": (">", 32), b"\xbf\xba\xfe\xca": ("<", 32)}
        if magic not in fat:
            raise ValueError("Executable is not a supported 64-bit Mach-O file")
        endian, size = fat[magic]
        count = struct.unpack(endian + "I", header[4:])[0]
        if not 1 <= count <= 64:
            raise ValueError("Invalid universal Mach-O architecture count")
        table = stream.read(size * count)
        if len(table) != size * count:
            raise ValueError("Truncated universal Mach-O architecture table")
        return {struct.unpack_from(endian + "I", table, i * size)[0] for i in range(count)}


def bundle_info(bundle):
    bundle = bundle.resolve(strict=True)
    if not bundle.is_dir() or bundle.suffix.lower() != ".app":
        raise ValueError("Select a macOS .app directory")
    info_path = bundle / "Contents/Info.plist"
    if not info_path.resolve(strict=True).is_relative_to(bundle):
        raise ValueError("Bundle metadata escapes the application directory")
    if info_path.stat().st_size > 4 * 1024 * 1024:
        raise ValueError("Bundle metadata is too large")
    with info_path.open("rb") as stream:
        info = plistlib.load(stream)
    if not isinstance(info, dict):
        raise ValueError("Bundle metadata must be a dictionary")
    name = info.get("CFBundleExecutable")
    if not isinstance(name, str) or not name or name in (".", "..") or any(c in name for c in "/\\\x00"):
        raise ValueError("Invalid CFBundleExecutable")
    executable = (bundle / "Contents/MacOS" / name).resolve(strict=True)
    if not executable.is_relative_to(bundle) or not executable.is_file():
        raise ValueError("Bundle executable escapes the application directory or is not a file")
    display_name = next((info[key] for key in ("CFBundleDisplayName", "CFBundleName")
                         if isinstance(info.get(key), str) and info[key]), bundle.stem)
    identifier = info.get("CFBundleIdentifier", "")
    return {"bundle": str(bundle), "executable": str(executable),
            "name": display_name,
            "identifier": identifier if isinstance(identifier, str) else "",
            "cpuTypes": sorted(executable_architectures(executable))}


@contextlib.contextmanager
def prefix_lock(prefix):
    import fcntl
    prefix.parent.mkdir(parents=True, exist_ok=True)
    # Keep the lock inode after release: unlinking allows two simultaneous owners.
    with Path(str(prefix) + ".lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("This macOS application is already running") from error
        yield


def launch(runtime, prefix, executable, arguments):
    launcher = runtime / "bin/darling"
    for relative in CORE_COMPONENTS:
        if not (runtime / relative).is_file():
            raise ValueError(f"Missing Darling component: {relative}")
    env = dict(os.environ, DARLING_NONROOT="1", DARLING_INSTALL_PREFIX=str(runtime), DPREFIX=str(prefix))
    env.setdefault("DEXTOP_DARLING_HOST_LIBRARY_PATH", env.get("LD_LIBRARY_PATH", ""))
    env.setdefault("DEXTOP_DARLING_ABSTRACT_RPC", "1")
    env.setdefault("DEXTOP_DARLING_ANON_EXEC", "1")
    env.pop("DARLING_PROFILE", None)
    # The currently supported Darling GUI backend is X11, sharing Book's Xwayland.
    env.pop("WAYLAND_DISPLAY", None)
    stopped = False

    def interrupted(signum, frame):
        nonlocal stopped
        stopped = True

    with prefix_lock(prefix):
        previous = {s: signal.signal(s, interrupted) for s in (signal.SIGTERM, signal.SIGINT)}
        app = None
        try:
            print("macOS: starting application", flush=True)
            app = subprocess.Popen([str(launcher), "exec", "/Volumes/SystemRoot" + str(executable), *arguments], env=env)
            while not stopped:
                try:
                    return_code = app.wait(timeout=0.2)
                    break
                except subprocess.TimeoutExpired:
                    pass
            else:
                return_code = 128 + signal.SIGTERM
        finally:
            try:
                if app is not None:
                    print("macOS: stopping compatibility session", flush=True)
                    result = subprocess.run([str(launcher), "shutdown"], env=env, timeout=30)
                    if result.returncode:
                        raise RuntimeError(f"Darling shutdown failed; prefix retained: {prefix}")
                    # Never destroy the PRoot parent while its own guest is exiting.
                    app.wait(timeout=15)
            finally:
                for signum, handler in previous.items():
                    signal.signal(signum, handler)
        return return_code if return_code >= 0 else 128 - return_code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--bundle", type=Path)
    target.add_argument("--executable", type=Path, help="Standalone Mach-O, for own-source programs")
    target.add_argument("--registered", help="Launch an application registered in the Linux app list")
    target.add_argument("--unregister", help="Remove a registration, retaining the .app and its data")
    target.add_argument("--status", action="store_true", help="Inspect installed components and registrations without starting Darling")
    parser.add_argument("--inspect", action="store_true", help="Read bundle metadata without executing it")
    parser.add_argument("--register", action="store_true", help="Add the bundle to Book and the Linux application list")
    parser.add_argument("--runtime", type=Path, default=Path(os.environ.get("DEXTOP_DARLING_ROOT", "/opt/dextop/macos")))
    parser.add_argument("--prefix", type=Path)
    parser.add_argument("arguments", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    try:
        if args.status:
            print(json.dumps(runtime_status(args.runtime.resolve(), Path.home()), ensure_ascii=False))
            return 0
        if args.unregister:
            record, desktop = registration_paths(Path.home(), args.unregister)
            desktop.unlink(missing_ok=True)
            record.unlink(missing_ok=True)
            return 0
        if args.register and not args.bundle:
            raise ValueError("Registration requires --bundle")
        if args.registered:
            record, _ = registration_paths(Path.home(), args.registered)
            if record.stat().st_size > 64 * 1024:
                raise ValueError("Application registration is too large")
            saved = json.loads(record.read_text(encoding="utf-8"))
            if not isinstance(saved, dict) or any(not isinstance(saved.get(k), str) for k in ("bundle", "runtime", "prefix")):
                raise ValueError("Invalid application registration")
            args.bundle, args.runtime, args.prefix = (Path(saved[k]) for k in ("bundle", "runtime", "prefix"))
        info = bundle_info(args.bundle) if args.bundle else {
            "executable": str(args.executable.resolve(strict=True)),
            "cpuTypes": sorted(executable_architectures(args.executable))}
        if args.inspect:
            print(json.dumps(info, ensure_ascii=False))
            return 0
        cpu = CPU_TYPES.get(platform.machine())
        if cpu is None or cpu not in info["cpuTypes"]:
            raise ValueError("Application does not contain a native architecture; Intel translation is not installed")
        executable = Path(info["executable"])
        key = application_key(info)
        prefix = (args.prefix or Path.home() / ".local/share/dextop/macos/apps" / key).absolute()
        if args.register:
            print(json.dumps(register_bundle(info, args.runtime.resolve(strict=True), prefix, Path.home()), ensure_ascii=False))
            return 0
        arguments = args.arguments[1:] if args.arguments[:1] == ["--"] else args.arguments
        return launch(args.runtime.resolve(strict=True), prefix, executable, arguments)
    except (OSError, ValueError, RuntimeError, plistlib.InvalidFileException, ExpatError, subprocess.TimeoutExpired) as error:
        print(f"macOS: {error}", file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
