"""Build the initial TCP Network Swift overlay into a private output directory.

Requires matching, rebuilt Swift/Foundation overlays. Does not replace a runtime
or establish Network.framework ABI compatibility or full API coverage.
"""
import argparse
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("swiftc", "sdk", "resource-dir", "modules", "headers", "modulemap", "linker", "runtime", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--architecture", required=True, choices=("arm64", "x86_64"))
    parser.add_argument("--probe", action="store_true",help="Build the own-source TCP regression executable too")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True,exist_ok=True)
    modules = output / "modules"
    modules.mkdir(exist_ok=True)
    object_file = output / "Network.o"
    command = [str(args.swiftc.absolute()), "-target", args.architecture + "-apple-macosx26.0",
        "-sdk", str(args.sdk.resolve()), "-resource-dir", str(args.resource_dir.resolve()),
        "-I", str(args.modules.resolve()), "-Xcc", "-I" + str(args.headers.resolve()),
        "-Xcc", "-fmodule-map-file=" + str(args.modulemap.resolve()),
        "-module-cache-path", str(output / "module-cache"), "-swift-version", "5",
        "-module-name", "Network", "-module-link-name", "swiftNetwork", "-enable-library-evolution",
        "-parse-as-library", "-whole-module-optimization", "-emit-object", "-emit-module",
        "-emit-module-path", str(modules / "Network.swiftmodule"),
        str(HERE / "runtime/Network.swift"), "-o", str(object_file)]
    subprocess.run(command,check=True)
    root = args.runtime.resolve() / "libexec/darling"
    subprocess.run([str(args.linker.absolute()), "-dylib", "-arch", args.architecture,
        "-platform_version", "macos", "26.0", "26.0", "-syslibroot", str(root),
        "-install_name", "/usr/lib/swift/libswiftNetwork.dylib",
        "-F", str(root / "System/Library/Frameworks"), "-L", str(root / "usr/lib/swift"),
        str(object_file), "-framework", "Foundation", "-lswiftFoundation", "-lswiftDispatch",
        "-lswiftDarwin", "-lswiftCore", "-lswift_Concurrency", "-lSystem", "-lobjc",
        "-o", str(output / "libswiftNetwork.dylib")],check=True)
    if args.probe:
        probe_object = output / "network-probe.o"
        base = command[:command.index("-module-name")]
        subprocess.run([*base,"-I",str(modules),"-emit-object",str(HERE / "smoke/network.swift"),
            "-o",str(probe_object)],check=True)
        subprocess.run([str(args.linker.absolute()),"-arch",args.architecture,
            "-platform_version","macos","26.0","26.0","-syslibroot",str(root),
            "-F",str(root / "System/Library/Frameworks"),"-L",str(root / "usr/lib/swift"),
            "-L",str(output),str(probe_object),"-lswiftNetwork","-framework","Foundation",
            "-lswiftFoundation","-lswiftDispatch","-lswiftDarwin","-lswiftCore","-lswift_Concurrency",
            "-lSystem","-lobjc","-o",str(output / ("network-macos-" + args.architecture))],check=True)
    print("Built initial Network TCP overlay:",output)


if __name__ == "__main__":
    main()
