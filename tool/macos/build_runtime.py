"""Build a pinned Darling runtime on a matching Linux host in a dedicated directory."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
from patch_runtime import apply as patch_runtime
from apply_gui_runtime_patches import PATCHES as GUI_PATCHES


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def run(*args, **kwargs):
    return subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work", type=Path, default=ROOT / "build/macos/runtime")
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--architecture", choices=("aarch64", "x86_64"), default="aarch64")
    parser.add_argument("--components", choices=("core", "gui"), default="core")
    args = parser.parse_args()
    host = "aarch64" if platform.machine() == "arm64" else platform.machine()
    if platform.system() != "Linux" or host != args.architecture:
        parser.error("Use a Linux host matching --architecture; cross-compilation is not configured")
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    cargo = shutil.which("cargo") if args.components == "gui" else None
    if args.components == "gui" and cargo is None:
        parser.error("The GUI asset catalog decoder requires Cargo with Rust 2024 support")
    lock = json.loads((HERE / "darling.lock.json").read_text(encoding="utf-8"))
    work = args.work.resolve()
    if work.exists() and any(work.iterdir()):
        parser.error("--work must be a new or empty dedicated build directory")
    work.mkdir(parents=True, exist_ok=True)
    source = work / "source"
    build = work / "build"
    stage = work / "stage"
    env = dict(os.environ, GIT_LFS_SKIP_SMUDGE="1")
    run("git", "init", source)
    run("git", "-C", source, "remote", "add", "origin", lock["repository"])
    run("git", "-C", source, "fetch", "--depth", "1", "origin", lock["commit"], env=env)
    run("git", "-C", source, "checkout", "--detach", lock["commit"], env=env)
    run("python3", source / "scripts/ci/checkout_submodules.py", cwd=source, env=env)
    if args.components == "gui":
        # The GUI closure includes Swift. Never stage Git LFS pointers as dylibs.
        swift = source / "src/external/swift"
        run("git", "-C", swift, "lfs", "pull")
        run("git", "-C", swift, "lfs", "fsck")
    patch_runtime(source)
    if args.components == "gui":
        run("python3", HERE / "apply_gui_runtime_patches.py", "--source", source)
    (work / "submodules.txt").write_bytes(subprocess.check_output([
        "git", "-C", str(source), "submodule", "status", "--recursive",
    ]))
    run("cmake", "-S", source, "-B", build, "-G", "Ninja",
        "-DCMAKE_BUILD_TYPE=Release", f"-DCMAKE_INSTALL_PREFIX={lock['install_prefix']}",
        "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON",
        f"-DCOMPONENTS={'core' if args.components == 'core' else 'gui_frameworks,gui_stubs'}",
        f"-DTARGET_ARM64={'ON' if host == 'aarch64' else 'OFF'}",
        f"-DTARGET_x86_64={'ON' if host == 'x86_64' else 'OFF'}",
        "-DTARGET_i386=OFF", "-DANDROID=OFF",
        f"-DENABLE_METAL={'ON' if args.components == 'gui' else 'OFF'}",
        "-DCOMPILE_PY2_BYTECODE=OFF")
    run("cmake", "--build", build, "--parallel", args.jobs)
    # A CMake regeneration can leave a new graph pending after an apparently successful build.
    run("cmake", "--build", build, "--parallel", args.jobs)
    run("ninja", "-C", build, "-n")
    run("cmake", "--install", build, env=dict(env, DESTDIR=str(stage)))
    runtime = stage / lock["install_prefix"].lstrip("/")
    if args.components == "gui":
        catalog_lock = json.loads((HERE / "asset_catalog.lock.json").read_text())
        catalog_source = work / "catalog-source"
        run("git", "init", catalog_source)
        run("git", "-C", catalog_source, "remote", "add", "origin", catalog_lock["repository"])
        run("git", "-C", catalog_source, "fetch", "--depth", "1", "origin", catalog_lock["commit"])
        run("git", "-C", catalog_source, "checkout", "--detach", catalog_lock["commit"])
        run("python3", HERE / "build_asset_catalog_tool.py", "--source", catalog_source,
            "--cargo", cargo, "--target", f"{host}-unknown-linux-gnu", "--output", runtime)
        calendar_notice = runtime / "share/dextop/licenses/calendar"
        calendar_notice.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / "src/external/corefoundation/submodules/swift-corelibs-foundation/LICENSE",
                     calendar_notice / "LICENSE")
    if args.components == "gui" and host == "aarch64":
        run("python3", HERE / "bundle_native.py", "--runtime", runtime,
            "--work", work / "native-deps")
    # Upstream installs its launcher setuid; the Dextop payload must remain non-setuid.
    for directory, _, files in os.walk(stage):
        for name in files:
            path = Path(directory) / name
            if not path.is_symlink():
                mode = path.stat().st_mode
                if mode & 0o6000:
                    path.chmod(mode & ~0o6000)
    if host == "aarch64":
        run("sh", HERE / "run.sh", "--check", env=dict(env, DEXTOP_DARLING_ROOT=str(runtime)))
    (work / "build-info.json").write_text(json.dumps({
        **lock, "architecture": host, "build_host": platform.platform(),
        "components": args.components, "dextop_abstract_rpc": True,
        "dextop_anonymous_executable_segments": True,
        "android_execution_verified": False,
        "macos_application_verified": False,
        "tooling_sha256": {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                           for name in ("patch_runtime.py", "darling_segments.h", "darling_socket.c",
                                        "bundle_native.py", "native-deps.lock.json",
                                        *(["apply_gui_runtime_patches.py", "build_asset_catalog_tool.py",
                                           "asset_catalog.lock.json", *GUI_PATCHES,
                                           *(str(p.relative_to(HERE)).replace("\\", "/")
                                             for p in sorted((HERE / "runtime").rglob("*")) if p.is_file())]
                                          if args.components == "gui" else []))},
    }, indent=2) + "\n", encoding="utf-8")
    print(f"Staged experimental {args.components}: {runtime}. Android/Mach-O execution still needs verification.")


if __name__ == "__main__":
    main()
