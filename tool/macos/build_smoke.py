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
    parser.add_argument("--probe", choices=("hello", "posix", "socket"), default="hello")
    parser.add_argument("--architecture", choices=("aarch64", "x86_64"), default="aarch64")
    parser.add_argument("--output", type=Path, default=HERE.parent.parent / "build/macos/smoke")
    args = parser.parse_args()
    if args.clang and not args.linker:
        parser.error("--clang requires --linker")
    if args.linker and not args.linker.is_file():
        parser.error(f"Mach-O linker not found: {args.linker}")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Interface-only declarations; no Apple implementation is embedded.
    symbols = ["_write", "_memcpy", "_memset", "dyld_stub_binder"]
    if args.probe == "posix":
        symbols += ["_read", "_lseek", "_close", "_unlink", "_mkstemp", "_pthread_create",
                    "_pthread_join", "_fork", "_waitpid", "__exit"]
    elif args.probe == "socket":
        symbols += ["_read", "_close", "_unlink", "_mkstemp", "_socket", "_bind", "_listen", "_connect", "_accept"]
    architecture = "arm64" if args.architecture == "aarch64" else "x86_64"
    (output / "libSystem.tbd").write_text("""--- !tapi-tbd
tbd-version: 4
targets: [ ARCH-macos ]
install-name: '/usr/lib/libSystem.B.dylib'
exports:
  - targets: [ ARCH-macos ]
    symbols: [ SYMBOLS ]
...
""".replace("SYMBOLS", ", ".join(symbols)).replace("ARCH", architecture), encoding="utf-8")
    binary = output / f"{args.probe}-macos-{architecture}"
    compile_flags = ["-fno-stack-protector", "-O2", str(HERE / f"smoke/{args.probe}.c")]
    if args.zig:
        subprocess.run([str(args.zig.resolve()), "cc", "-target", f"{args.architecture}-macos.11.0",
                        "-nostdlib", *compile_flags, "-L", str(output), "-lSystem",
                        "-Wl,-e,_main", "-o", str(binary)], check=True)
    else:
        object_file = output / f"{args.probe}-{architecture}.o"
        subprocess.run([str(args.clang.resolve()), "-target", f"{architecture}-apple-macosx11.0",
                        *compile_flags, "-c", "-o", str(object_file)], check=True)
        # Invoke the Mach-O driver directly, with explicit platform/architecture.
        # Preserve ld64.lld's basename: LLD selects its driver using argv[0].
        subprocess.run([str(args.linker.absolute()), "-arch", architecture,
                        "-platform_version", "macos", "11.0", "11.0", "-e", "_main",
                        str(object_file), "-L", str(output), "-lSystem", "-o", str(binary)], check=True)
    print(f"Built Mach-O probe: {binary}. Execution under Darling is a separate check.")


if __name__ == "__main__":
    main()
