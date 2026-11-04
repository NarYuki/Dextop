"""Build the own-source GUI probe against a source-built Darling SDK/runtime."""

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
    parser.add_argument("--probe", choices=("appkit", "calendar", "layout_fitting", "eventkit", "view_backing", "controller_appearance", "grid", "layer_render", "unarchiver", "presentation", "calibrated_rgb", "accessibility_chart", "gradient_layer", "timing_function"), default="appkit")
    args = parser.parse_args()
    source = args.source.resolve()
    sdk = source / "Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
    sysroot = args.runtime.resolve() / "libexec/darling"
    for required in (sdk, source / "framework-include", sysroot / "System/Library/Frameworks/AppKit.framework/AppKit"):
        if not required.exists():
            parser.error(f"Missing SDK or staged GUI runtime: {required}")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    arch = "arm64" if args.architecture == "aarch64" else "x86_64"
    object_file = output / f"{args.probe}-{arch}.o"
    subprocess.run([str(args.clang.absolute()), "-target", f"{arch}-apple-macosx11.0",
                    "-isysroot", str(sdk), "-I", str(source / "framework-include"),
                    "-Wno-nullability-completeness", "-Wno-deprecated-declarations",
                    "-Wno-availability", "-fno-objc-arc", "-c",
                    str(HERE / "smoke" / (args.probe + ".m")), "-o", str(object_file)], check=True)
    binary = output / f"{args.probe}-macos-{arch}"
    subprocess.run([str(args.linker.absolute()), "-arch", arch,
                    "-platform_version", "macos", "11.0", "11.0", "-e", "_main",
                    "-syslibroot", str(sysroot), str(object_file),
                    "-F", str(sysroot / "System/Library/Frameworks"),
                    "-L", str(sysroot / "usr/lib"), "-L", str(sysroot / "usr/lib/system"),
                    "-L", str(sysroot / "usr/lib/swift"),
                    "-framework", "AppKit", "-framework", "Foundation", "-lobjc", "-lSystem",
                    *(["-framework", "EventKit"] if args.probe == "eventkit" else []),
                    *(["-framework", "QuartzCore"] if args.probe in ("view_backing", "layer_render", "gradient_layer", "timing_function") else []),
                    *(["-framework", "OpenGL"] if args.probe == "layer_render" else []),
                    *(["-framework", "CoreGraphics"] if args.probe == "calibrated_rgb" else []),
                    *(["-lAccessibility"] if args.probe == "accessibility_chart" else []),
                    "-o", str(binary)], check=True)
    print(f"Built {binary}; GUI rendering/input must be verified separately.")


if __name__ == "__main__":
    main()
