"""Run own-source Mach-O probes in an isolated prefix and shut it down."""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--probes", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--abstract-rpc", action="store_true")
    args = parser.parse_args()
    runtime = args.runtime.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Keep the Unix socket pathname below sockaddr_un's limit on Linux CI.
    with tempfile.TemporaryDirectory(prefix="dextop-mac-") as directory:
        env = dict(os.environ, DARLING_NONROOT="1", DARLING_INSTALL_PREFIX=str(runtime),
                   DPREFIX=directory + "/prefix", DEXTOP_DARLING_ABSTRACT_RPC="1" if args.abstract_rpc else "0")
        env.pop("DARLING_PROFILE", None)
        command = [str(runtime / "bin/darling")]
        try:
            for name, expected in (("hello", "Dextop macOS ARM64 hello"),
                                   ("posix", "file: OK\nthread: OK\nfork: OK"),
                                   ("hello", "Dextop macOS ARM64 hello")):
                log = output / f"{name}-{len(list(output.glob('*.log')))}.log"
                with log.open("wb") as stream:
                    result = subprocess.run(command + ["exec", str(args.probes.resolve() / f"{name}-macos-arm64")],
                                            env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=90)
                content = log.read_text(encoding="utf-8", errors="replace")
                print(content, end="", flush=True)
                if result.returncode or expected not in content:
                    raise RuntimeError(f"Mach-O {name} failed: exit {result.returncode}, log {log}")
        finally:
            with (output / "shutdown.log").open("wb") as stream:
                result = subprocess.run(command + ["shutdown"], env=env,
                                        stdout=stream, stderr=subprocess.STDOUT, timeout=30)
            if result.returncode:
                raise RuntimeError("Darling did not shut down cleanly; inspect shutdown.log")


if __name__ == "__main__":
    main()
