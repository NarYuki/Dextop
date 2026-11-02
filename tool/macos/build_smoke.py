"""Build an own-source Mach-O probe without an Apple SDK or binary payload."""

import argparse
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    compiler = parser.add_mutually_exclusive_group(required=True)
    compiler.add_argument("--zig", type=Path)
    compiler.add_argument("--clang", type=Path)
    parser.add_argument("--linker", type=Path, help="Mach-O linker for the Clang build")
    parser.add_argument("--probe", choices=("hello", "posix"), default="hello")
    parser.add_argument("--output", type=Path, default=HERE.parent.parent / "build/macos/smoke")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Interface-only declarations; no Apple implementation is embedded.
    symbols = ["_write"]
    if args.probe == "posix":
        symbols += ["_read", "_lseek", "_close", "_unlink", "_mkstemp", "_pthread_create",
                    "_pthread_join", "_fork", "_waitpid", "__exit"]
    (output / "libSystem.tbd").write_text("""--- !tapi-tbd
tbd-version: 4
targets: [ arm64-macos ]
install-name: '/usr/lib/libSystem.B.dylib'
exports:
  - targets: [ arm64-macos ]
    symbols: [ SYMBOLS ]
...
""".replace("SYMBOLS", ", ".join(symbols)), encoding="utf-8")
    binary = output / f"{args.probe}-macos-arm64"
    command = ([str(args.zig.resolve()), "cc", "-target", "aarch64-macos.11.0"] if args.zig else
               [str(args.clang.resolve()), "-target", "arm64-apple-macos11.0"])
    if args.clang and args.linker:
        command.append(f"-fuse-ld={args.linker.resolve()}")
    subprocess.run(command + [
        "-nostdlib", "-fno-stack-protector", "-O2", str(HERE / f"smoke/{args.probe}.c"), "-L", str(output), "-lSystem",
        "-Wl,-e,_main", "-o", str(binary),
    ], check=True)
    print(f"Built Mach-O probe: {binary}. Execution under Darling is a separate check.")


if __name__ == "__main__":
    main()
