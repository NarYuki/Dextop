"""Rebuild the pinned legacy x86_64 Swift overlays against Swift 6.3.3.

Uses a dedicated source copy and produces files for review; never replaces the
supplied runtime. The Swift toolchain/resources must be independently verified.
"""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess


HERE = Path(__file__).resolve().parent


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError(f"Pinned Swift build anchor changed: {old[:80]}")
    return text.replace(old, new, 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "runtime", "toolchain", "resource-dir", "linker", "libsystem", "work"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--components", choices=("foundation", "gui"), default="foundation")
    args = parser.parse_args()
    paths = {key: value.resolve() for key, value in vars(args).items() if isinstance(value, Path)}
    # The upstream shell script intentionally expands flag/input word lists.
    if any(any(character.isspace() for character in str(path)) for path in paths.values()):
        parser.error("Upstream overlay build requires paths without whitespace")
    source = paths["source"]
    lock = json.loads((HERE / "darling.lock.json").read_text())
    revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if revision != lock["commit"]:
        parser.error("Source HEAD must match darling.lock.json")
    toolchain = paths["toolchain"]
    version = subprocess.check_output([str(toolchain / "bin/swiftc"), "--version"], text=True)
    if "Swift version 6.3.3 " not in version:
        parser.error("Use the verified Swift 6.3.3 toolchain matching the pinned runtime")
    work = paths["work"]
    if work.exists() and any(work.iterdir()):
        parser.error("--work must be a new or empty dedicated directory")
    sdk = source / "Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
    # Do not expose SDK frameworks through a second plain -I path. Clang then
    # imports some headers outside their framework module/API notes, invalidating
    # Swift overlay members (notably URLResourceValues.allValues).
    headers = work / "header-bridge"
    headers.mkdir(parents=True)
    for entry in (source / "framework-include").iterdir():
        if not (sdk / "System/Library/Frameworks" / (entry.name + ".framework")).exists():
            (headers / entry.name).symlink_to(entry)
    swift = work / "swift"
    shutil.copytree(source / "src/external/swift", swift, symlinks=True,
                    ignore=shutil.ignore_patterns(".git", "build"))
    from patch_os_logging import patch as patch_os_logging
    patch_os_logging(swift / "overlays/os/os.swift")
    from patch_path_overlay import patch as patch_path_overlay
    patch_path_overlay(swift / "overlays")
    from patch_gradient_overlay import patch as patch_gradient_overlay
    patch_gradient_overlay(swift / "overlays")
    from patch_unarchiver_overlay import patch as patch_unarchiver_overlay
    patch_unarchiver_overlay(swift / "overlays")
    from patch_foundation_imports import patch_overlays as patch_foundation_overlay_imports
    patch_foundation_overlay_imports(swift / "overlays")
    script = swift / "overlays/build.sh"
    text = script.read_text()
    # Focused upstream Foundation mode still builds its actual dependencies:
    # Darwin/ObjC/CF/Dispatch/os/XPC, OpenCombine, collections and cmark.
    text = text.replace("arm64-apple-macosx26.0", "x86_64-apple-macosx26.0")
    text = text.replace("-arch arm64", "-arch x86_64")
    text = text.replace('-syslibroot "$DARLING_SDK" \\\n',
                        '-syslibroot "$DARLING_SDK" -syslibroot "$DARLING_ROOT" \\\n')
    text = text.replace('-sdk "$DARLING_SDK" -resource-dir',
                        '-sdk "$DARLING_SDK" -Xcc -I"$DEXTOP_DARLING_HEADERS" -resource-dir')
    # Foundation.framework reexports its Swift overlay. Resolve that dependency
    # explicitly while linking the replacement, as Darling's CMake build does.
    anchor = '"$DARLING_LD" -dylib -arch x86_64 -platform_version macos 26.0 26.0 -syslibroot "$DARLING_SDK" -syslibroot "$DARLING_ROOT" \\\n\t-install_name "/System/Library/Frameworks/Combine.framework/Versions/A/Combine"'
    replacement = anchor.replace('\\\n\t-install_name', '$LD_EXTRA_FLAGS \\\n\t-install_name')
    text = replace_once(text, anchor, replacement)
    if args.components == "gui":
        # Build the real UI overlays through CoreText, without entering the
        # unrelated placeholder GroupActivities pipeline below them.
        marker = "# GroupActivities: Apple's"
        if text.count(marker) != 1:
            raise RuntimeError("Pinned GUI overlay build boundary changed")
        text = text[:text.index(marker)] + '\necho "built GUI Swift overlays through CoreText"\n'
    script.write_text(text)
    for file in (swift / "overlays/Foundation").glob("*.swift"):
        content = file.read_text()
        if "CGFloat" in content and "import CoreFoundation" not in content:
            file.write_text("import CoreFoundation\n" + content)
    root = paths["runtime"] / "libexec/darling"
    mappings = []
    for library in sorted((root / "usr/lib/swift").glob("*.dylib")):
        for name in ("@rpath/" + library.name, "/usr/lib/swift/" + library.name):
            mappings += ["-dylib_file", name + ":" + str(library)]
    env = dict(os.environ, SWIFT_TOOLCHAIN=str(toolchain),
               SWIFT_RESOURCE_DIR=str(paths["resource_dir"]),
               DARLING_SDK=str(sdk),
               DARLING_LD=str(paths["linker"]), DARLING_LIBSYSTEM=str(paths["libsystem"]),
               DARLING_ROOT=str(root), DARLING_SWIFT_FOUNDATION_ONLY="1" if args.components == "foundation" else "0",
               DARLING_SWIFT_APPKIT_ONLY="0", DEXTOP_DARLING_HEADERS=str(headers),
               LD_EXTRA_FLAGS=" ".join(mappings))
    subprocess.run(["bash", str(script)], env=env, check=True)
    output = work / "output"
    output.mkdir()
    built = swift / "overlays/build"
    names = ["Darwin", "ObjectiveC", "CoreFoundation", "Dispatch", "os", "XPC", "Foundation"]
    if args.components == "gui":
        names += ["CoreGraphics", "DeveloperToolsSupport", "AppKit", "QuartzCore", "SceneKit", "UniformTypeIdentifiers", "CoreText"]
    for name in names:
        shutil.copy2(built / f"libswift{name}.dylib", output / f"libswift{name}.dylib")
    shutil.copy2(swift / "Combine.framework/Versions/A/Combine", output / "Combine")
    (work / "build-info.json").write_text(json.dumps({
        "darling_commit": revision, "architecture": "x86_64", "swift_version": "6.3.3",
        "components": args.components,
        "runtime_replaced": False, "execution_verified": False,
    }, indent=2) + "\n")
    print(f"Built source overlays: {output}. Stage into a private test runtime and verify GUI execution.")


if __name__ == "__main__":
    main()
