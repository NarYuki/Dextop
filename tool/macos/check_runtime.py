"""Run own-source Mach-O probes in an isolated prefix and shut it down."""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import shutil


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--probes", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--abstract-rpc", action="store_true")
    parser.add_argument("--anonymous-exec", action="store_true")
    parser.add_argument("--architecture", choices=("aarch64", "x86_64"), default="aarch64")
    parser.add_argument("--probe", choices=("hello", "posix", "socket", "audio", "audioqueue", "metal", "calendar", "layout_fitting", "eventkit", "view_backing", "controller_appearance", "grid", "layer_render", "unarchiver", "calibrated_rgb", "accessibility_chart", "gradient_layer", "timing_function"), action="append",
                        help="Diagnostic subset/order; default runs the complete sequence")
    args = parser.parse_args()
    runtime = args.runtime.resolve()
    architecture = "arm64" if args.architecture == "aarch64" else "x86_64"
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Keep the Unix socket pathname below sockaddr_un's limit on Linux CI.
    # Preserve a failed-shutdown prefix while its processes may still use it.
    directory = tempfile.mkdtemp(prefix="dextop-mac-")
    print(f"Darling probe prefix: {directory}", flush=True)
    try:
        env = dict(os.environ, DARLING_NONROOT="1", DARLING_INSTALL_PREFIX=str(runtime),
                   DPREFIX=directory + "/prefix", DEXTOP_DARLING_ABSTRACT_RPC="1" if args.abstract_rpc else "0",
                   DEXTOP_DARLING_ANON_EXEC="1" if args.anonymous_exec else "0")
        env.pop("DARLING_PROFILE", None)
        env.setdefault("DEXTOP_DARLING_HOST_LIBRARY_PATH", env.get("LD_LIBRARY_PATH", ""))
        env.setdefault("DEXTOP_DARLING_HOST_PRELOAD", env.get("LD_PRELOAD", ""))
        command = [str(runtime / "bin/darling")]
        try:
            expected_output = {"hello": "Dextop macOS hello", "posix": "file: OK\nthread: OK\nfork: OK",
                               "socket": "socket: OK", "audio": "audio: PCM resample and sample-format conversion OK",
                               "audioqueue": "audioqueue: silent playback buffer consumption OK",
                               "metal": "metal: source compute, readback and negative controls OK",
                               "calendar": "calendar: locale, leap day, DST boundaries and automatic calendar OK",
                               "layout_fitting": "layout fitting PASS",
                               "eventkit": "EventKit search with denied authorization PASS",
                               "view_backing": "view backing: nested drawing, partial invalidation, resize and hosted layers OK",
                               "controller_appearance": "controller appearance: window, hidden ancestors, child insertion/removal and replacement OK",
                               "grid": "grid: model, fitting, baseline, reorder, hiding, padding and RTL OK",
                               "layer_render": "layer render: replacement, contents update and removal readback OK",
                               "unarchiver": "unarchiver: object graphs, NSCoding, nil root and error propagation OK",
                               "calibrated_rgb": "CalRGB: solid paints, gamma, matrix, white adaptation, alpha, copying and invalid inputs OK",
                               "accessibility_chart": "Accessibility chart data OK",
                               "gradient_layer": "Gradient layer: model, copy, axial/radial/conic pixels and invalidation OK",
                               "timing_function": "Timing function: float ABI, guarded output, inverse curve, copy and archive OK"}
            for name in args.probe or ("hello", "posix", "socket", "hello"):
                expected = expected_output[name]
                log = output / f"{name}-{len(list(output.glob('*.log')))}.log"
                # Darling treats /var, /tmp, /usr, etc. as Darwin paths.
                # Probe files are Linux guest files, addressed explicitly.
                program = "/Volumes/SystemRoot" + str(args.probes.resolve() / f"{name}-macos-{architecture}")
                with log.open("wb") as stream:
                    result = subprocess.run(command + ["exec", program],
                                            env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=90)
                content = log.read_text(encoding="utf-8", errors="replace")
                if result.returncode or expected not in content:
                    print("\n".join(content.splitlines()[-40:]), flush=True)
                    raise RuntimeError(f"Mach-O {name} failed: exit {result.returncode}, log {log}")
                print(expected, flush=True)
        finally:
            with (output / "shutdown.log").open("wb") as stream:
                result = subprocess.run(command + ["shutdown"], env=env,
                                        stdout=stream, stderr=subprocess.STDOUT, timeout=30)
            if result.returncode:
                raise RuntimeError(f"Darling did not shut down cleanly; prefix retained at {directory}; inspect shutdown.log")
    except BaseException:
        raise
    else:
        shutil.rmtree(directory)


if __name__ == "__main__":
    main()
