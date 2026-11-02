"""Build an own-source Mach-O probe without an Apple SDK or binary payload."""

import argparse
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zig", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=HERE.parent.parent / "build/macos/smoke")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # This interface-only stub names POSIX write; no Apple implementation is embedded.
    (output / "libSystem.tbd").write_text("""--- !tapi-tbd
tbd-version: 4
targets: [ arm64-macos ]
install-name: '/usr/lib/libSystem.B.dylib'
exports:
  - targets: [ arm64-macos ]
    symbols: [ _write ]
...
""", encoding="utf-8")
    binary = output / "hello-macos-arm64"
    subprocess.run([
        str(args.zig.resolve()), "cc", "-target", "aarch64-macos.11.0", "-nostdlib",
        "-O2", str(HERE / "smoke/hello.c"), "-L", str(output), "-lSystem",
        "-Wl,-e,_main", "-o", str(binary),
    ], check=True)
    print(f"Built Mach-O probe: {binary}. Execution under Darling is a separate check.")


if __name__ == "__main__":
    main()
