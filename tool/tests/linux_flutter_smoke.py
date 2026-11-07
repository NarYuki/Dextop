#!/usr/bin/env python3
"""Install a pinned official Flutter source SDK and exercise it on a native ARM64 guest."""
import json
import os
from pathlib import Path
import subprocess
import sys

from linux_development_smoke import ROOT, RESULTS, run, shell, write


def main():
    sdk = Path.home() / "develop/flutter-3.47.7"
    sdk.parent.mkdir(parents=True, exist_ok=True)
    print("FLUTTER_SMOKE_ROOT=" + str(ROOT), flush=True)
    if not sdk.exists():
        cloned = run("flutter_clone", ["git", "clone", "--depth", "1", "--branch", "3.47.7",
                                     "https://github.com/flutter/flutter.git", str(sdk)],
                     timeout=600, requires=("git",))
        if cloned["status"] != "pass":
            return 1
    if not (sdk / ".git").is_dir():
        print("Existing SDK directory is not a Git checkout; leaving it unchanged", flush=True)
        return 1
    os.environ.update(CI="true", FLUTTER_SUPPRESS_ANALYTICS="true", DART_SUPPRESS_ANALYTICS="true")
    flutter = str(sdk / "bin/flutter")
    dart = str(sdk / "bin/dart")
    if run("flutter_bootstrap", [flutter, "--version"], timeout=900)["status"] != "pass":
        return 1
    run("flutter_analytics", [flutter, "config", "--no-analytics"], timeout=90)
    bindir = Path.home() / ".local/bin"
    bindir.mkdir(parents=True, exist_ok=True)
    for name in ("flutter", "dart"):
        link = bindir / name
        if not link.exists() and not link.is_symlink():
            link.symlink_to(sdk / "bin" / name)
    doctor = run("flutter_doctor", [flutter, "doctor", "-v"], timeout=180)
    if "[✗]" in doctor.get("output", ""):
        doctor["status"] = "incomplete_setup"
    run("flutter_precache", [flutter, "precache", "--linux", "--web"], timeout=900)
    app = ROOT / "flutter_app"
    if run("flutter_create", [flutter, "create", "--no-pub", "--platforms=linux,web",
                              "--org", "dev.dextop", str(app)], timeout=120)["status"] != "pass":
        return 1
    run("flutter_pub_get", [flutter, "pub", "get"], directory=app, timeout=300)
    run("flutter_analyze", [flutter, "analyze", "--no-pub"], directory=app, timeout=180)
    run("flutter_widget_test", [flutter, "test", "--no-pub", "--reporter", "expanded"], directory=app, timeout=300)
    run("flutter_web_release", [flutter, "build", "web", "--release", "--no-pub"], directory=app, timeout=600)
    linux = run("flutter_linux_release", [flutter, "build", "linux", "--release", "--no-pub"], directory=app, timeout=600)
    write(app, "cli.dart", "import 'dart:io'; import 'dart:isolate';\nFuture<void> main() async {final value=await Isolate.run(()=>6*7);if(value!=42)throw StateError('isolate');File('dart-output').writeAsStringSync('42');print('DART_ISOLATE_FILES_OK');}\n")
    run("dart_cli", [dart, "run", "cli.dart"], directory=app, timeout=120)
    run("dart_compile", [dart, "compile", "exe", "cli.dart", "-o", "dart-native"], directory=app, timeout=120)
    if (app / "dart-native").is_file():
        run("dart_native", [str(app / "dart-native")], directory=app)
    binary = app / "build/linux/arm64/release/bundle/flutter_app"
    if linux["status"] == "pass" and binary.is_file():
        write(app, "gui_probe.py", Path(__file__).with_name("linux_flutter_gui_probe.py").read_text(encoding="utf-8"))
        run("flutter_linux_gui", [sys.executable, "gui_probe.py", "--xvfb", str(binary)], directory=app,
            requires=("Xvfb", "fakeroot-tcp"), timeout=45,
            env={"GDK_BACKEND": "x11", "LIBGL_ALWAYS_SOFTWARE": "1", "GALLIUM_DRIVER": "llvmpipe"})
    print("FLUTTER_RESULTS=" + str(ROOT / "results.json"), flush=True)
    print("FLUTTER_COUNTS=" + json.dumps({s: sum(r["status"] == s for r in RESULTS) for s in {r["status"] for r in RESULTS}}), flush=True)
    return int(any(r["status"] in ("fail", "timeout") for r in RESULTS if r["name"] != "flutter_doctor"))


if __name__ == "__main__":
    raise SystemExit(main())
