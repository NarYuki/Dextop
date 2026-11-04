"""Build own-source AudioToolbox conversion or silent queue probes."""

import argparse
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--clang", required=True, type=Path)
    parser.add_argument("--linker", required=True, type=Path)
    parser.add_argument("--architecture", choices=("aarch64", "x86_64"), default="aarch64")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--probe", choices=("audio", "audioqueue"), default="audio")
    args = parser.parse_args()
    source = args.source.resolve()
    sdk = source / "Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
    sysroot = args.runtime.resolve() / "libexec/darling"
    framework = sysroot / "System/Library/Frameworks/AudioToolbox.framework/AudioToolbox"
    for required in (sdk, source / "framework-include", framework):
        if not required.exists():
            parser.error(f"Missing source SDK or AudioToolbox runtime: {required}")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    arch = "arm64" if args.architecture == "aarch64" else "x86_64"
    obj = output / f"{args.probe}-{arch}.o"
    filename = "audioqueue.cpp" if args.probe == "audioqueue" else "audio.c"
    subprocess.run([str(args.clang.absolute()), "-target", f"{arch}-apple-macosx11.0",
                    "-isysroot", str(sdk), "-I", str(source / "framework-include"),
                    "-Wno-nullability-completeness", "-c", str(HERE / "smoke" / filename),
                    "-o", str(obj)], check=True)
    binary = output / f"{args.probe}-macos-{arch}"
    subprocess.run([str(args.linker.absolute()), "-arch", arch, "-platform_version", "macos", "11.0", "11.0",
                    "-e", "_main", "-syslibroot", str(sysroot), str(obj),
                    "-F", str(sysroot / "System/Library/Frameworks"),
                    "-L", str(sysroot / "usr/lib"), "-L", str(sysroot / "usr/lib/system"),
                    "-framework", "AudioToolbox", "-lSystem", "-o", str(binary)], check=True)
    print(f"Built {binary}; conversion and playback must be verified separately.")


if __name__ == "__main__":
    main()
