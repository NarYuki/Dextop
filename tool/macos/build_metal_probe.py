"""Build an own-source Metal compute probe against the source-built runtime."""

import argparse
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "runtime", "clang", "linker", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--architecture", choices=("arm64", "x86_64"), required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    sdk = source / "Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
    root = args.runtime.resolve() / "libexec/darling"
    for path in (sdk, root / "System/Library/Frameworks/Metal.framework/Metal"):
        if not path.exists():
            parser.error(f"Missing SDK or Metal framework: {path}")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    obj = output / f"metal-{args.architecture}.o"
    subprocess.run([str(args.clang.absolute()), "-target", args.architecture + "-apple-macosx11.0",
                    "-isysroot", str(sdk), "-I", str(source / "framework-include"),
                    "-Wno-nullability-completeness", "-Wno-deprecated-declarations", "-Wno-availability",
                    "-fno-objc-arc", "-c", str(HERE / "smoke/metal.m"),
                    "-o", str(obj)], check=True)
    binary = output / f"metal-macos-{args.architecture}"
    subprocess.run([str(args.linker.absolute()), "-arch", args.architecture, "-platform_version", "macos", "11.0", "11.0",
                    "-e", "_main", "-syslibroot", str(root), str(obj), "-F", str(root / "System/Library/Frameworks"),
                    "-L", str(root / "usr/lib"), "-L", str(root / "usr/lib/swift"),
                    "-L", str(root / "usr/lib/system"), "-framework", "Metal",
                    "-framework", "Foundation", "-lobjc", "-lSystem", "-o", str(binary)], check=True)
    print(f"Built {binary}; verify the selected backend and actual readback separately.")


if __name__ == "__main__":
    main()
