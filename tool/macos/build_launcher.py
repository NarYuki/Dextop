"""Cross-build the pinned upstream launcher. This does not build a macOS runtime."""

import argparse
import json
from pathlib import Path
import re
import subprocess
from patch_runtime import patch_launcher


HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def git(source, *args):
    return subprocess.check_output(["git", "-C", str(source), *args])


def build(source, zig, output):
    lock = json.loads((HERE / "darling.lock.json").read_text(encoding="utf-8"))
    commit = lock["commit"]
    if git(source, "rev-parse", f"{commit}^{{commit}}").decode().strip() != commit:
        raise RuntimeError("The pinned Darling commit is not available")
    output.mkdir(parents=True, exist_ok=True)
    extracted = output / "source"
    files = git(source, "ls-tree", "-r", "--name-only", commit, "src/startup", "src/shellspawn/shellspawn.h").decode().splitlines()
    files = [name for name in files if name.endswith(".h") and "/mldr/" not in name]
    files.append("src/startup/darling.c")
    for name in files:
        target = extracted / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(git(source, "show", f"{commit}:{name}"))
    patch_launcher(extracted)

    config = git(source, "show", f"{commit}:src/include/darling-config.h.in").decode()
    values = {
        "${CMAKE_INSTALL_PREFIX}": lock["install_prefix"],
        "${CMAKE_INSTALL_LIBDIR}": "lib",
        "@SUFFIX@": "",
        "@GIT_BRANCH@": "dextop-pinned",
        "@GIT_COMMIT_HASH@": commit,
        "#cmakedefine MULTILIB": "/* #undef MULTILIB */",
    }
    for key, value in values.items():
        config = config.replace(key, value)
    if re.search(r"\$\{[^}]+\}|@[A-Z_]+@", config):
        raise RuntimeError("Unresolved upstream launcher configuration")
    generated = output / "include"
    generated.mkdir(exist_ok=True)
    (generated / "darling-config.h").write_text(config, encoding="utf-8")
    binary = output / "darling"
    subprocess.run([
        str(zig), "cc", "-target", "aarch64-linux-gnu.2.31", "-std=gnu11",
        "-D_GNU_SOURCE", "-O2", "-s", "-I", str(generated),
        str(extracted / "src/startup/darling.c"), "-lutil", "-o", str(binary),
    ], check=True)
    (output / "build-info.json").write_text(json.dumps({
        "repository": lock["repository"], "commit": commit,
        "component": "launcher-only", "target": "aarch64-linux-gnu.2.31",
        "runtime_complete": False,
    }, indent=2) + "\n", encoding="utf-8")
    print(f"Built launcher only: {binary}. darlingserver, mldr and Darwin libraries are still required.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="Git repository containing the pinned commit; checkout is unnecessary")
    parser.add_argument("--zig", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/macos/launcher")
    args = parser.parse_args()
    build(args.source.resolve(), args.zig.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
