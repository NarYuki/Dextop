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
    parser.add_argument("--probe", choices=("hello", "posix", "socket"), action="append",
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
        command = [str(runtime / "bin/darling")]
        try:
            expected_output = {"hello": "Dextop macOS hello", "posix": "file: OK\nthread: OK\nfork: OK",
                               "socket": "socket: OK"}
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
