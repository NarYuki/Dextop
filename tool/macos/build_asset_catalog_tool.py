"""Build a pinned Linux catalog decoder and retain its source/dependency notices."""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--cargo", type=Path, required=True)
    parser.add_argument("--target", choices=("aarch64-unknown-linux-gnu", "x86_64-unknown-linux-gnu"), default="aarch64-unknown-linux-gnu")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source, output = args.source.resolve(), args.output.resolve()
    lock = json.loads(Path(__file__).with_name("asset_catalog.lock.json").read_text())
    git = ["git", "-c", "core.filemode=false", "-c", "core.autocrlf=false", "-C", str(source)]
    revision = subprocess.check_output([*git, "rev-parse", "HEAD"], text=True).strip()
    if revision != lock["commit"]:
        parser.error("Catalog parser source must match asset_catalog.lock.json")
    if subprocess.check_output([*git, "status", "--porcelain", "--untracked-files=no"], text=True).strip():
        parser.error("Catalog parser source has local modifications")
    manifest = source / "Cargo.toml"
    cargo = str(args.cargo.absolute())
    target_dir = Path(os.environ.get("CARGO_TARGET_DIR", output.parent / (output.name + "-catalog-build"))).absolute()
    env = dict(os.environ, CARGO_TARGET_DIR=str(target_dir))
    subprocess.run([cargo, "build", "--locked", "--release", "--target", args.target,
                    "--manifest-path", str(manifest), "-p", "car-cli"], env=env, check=True)
    binary = output / "bin/dextop-car"
    binary.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(target_dir / args.target / "release/car-cli", binary)
    notices = output / "share/dextop/licenses/asset-catalog"
    notices.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source / "LICENSE", notices / "LICENSE")
    metadata = json.loads(subprocess.check_output([cargo, "metadata", "--locked", "--format-version", "1",
                                                  "--manifest-path", str(manifest)], env=env, text=True))
    packages = [{key: package.get(key) for key in ("name", "version", "license", "repository")}
                for package in metadata["packages"]]
    (notices / "dependencies.json").write_text(json.dumps(packages, indent=2) + "\n")
    (notices / "source.json").write_text(json.dumps(lock, indent=2) + "\n")
    print(f"Built {binary}")


if __name__ == "__main__":
    main()
