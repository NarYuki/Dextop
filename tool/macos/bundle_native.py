"""Bundle pinned host ELF ABIs into a fresh, inactive ARM64 Darling runtime.

Extracts verified packages without installing them into the Linux system.
"""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request


HERE = Path(__file__).resolve().parent


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    args = parser.parse_args()
    runtime = args.runtime.resolve()
    if not (runtime / "bin/darling").is_file():
        parser.error("--runtime must be an already staged Darling runtime")
    target = runtime / "lib/dextop-native"
    if not target.resolve().is_relative_to(runtime) or target.exists():
        parser.error("Native bundle must be new and remain inside the selected runtime")
    work = args.work.resolve()
    if work.exists() and any(work.iterdir()):
        parser.error("--work must be a new or empty directory")
    work.mkdir(parents=True, exist_ok=True)
    lock = json.loads((HERE / "native-deps.lock.json").read_text())
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".native-stage-", dir=target.parent) as temporary:
        stage = Path(temporary) / "bundle"
        stage.mkdir()
        (stage / "licenses").mkdir()
        for package in lock["packages"]:
            archive = work / (package["name"] + ".deb")
            with urllib.request.urlopen(package["url"], timeout=60) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output)
            if digest(archive) != package["sha256"]:
                raise RuntimeError(f"Package checksum mismatch: {package['name']}")
            architecture = subprocess.check_output(["dpkg-deb", "-f", str(archive), "Architecture"], text=True).strip()
            if architecture != package["architecture"]:
                raise RuntimeError(f"Unexpected package architecture: {architecture}")
            for field, expected in (("Package", package["name"]), ("Version", package["version"])):
                actual = subprocess.check_output(["dpkg-deb", "-f", str(archive), field], text=True).strip()
                if actual != expected:
                    raise RuntimeError(f"Unexpected package {field}: {actual}")
            extracted = work / package["name"]
            subprocess.run(["dpkg-deb", "-x", str(archive), str(extracted)], check=True)
            for entry in package.get("libraries", [package]):
                library = extracted / entry["library"]
                if (stage / library.name).exists() or (stage / entry["soname"]).exists():
                    raise RuntimeError(f"Duplicate native library: {entry['soname']}")
                shutil.copy2(library, stage / library.name)
                if package.get("private_runpath"):
                    # Resolve transitive dependencies beside this ELF library.
                    subprocess.run(["patchelf", "--set-rpath", "$ORIGIN", str(stage / library.name)], check=True)
                if entry["soname"] != library.name:
                    (stage / entry["soname"]).symlink_to(library.name)
            shutil.copy2(extracted / package["copyright"], stage / "licenses" / (package["name"] + ".copyright"))
        (stage / "manifest.json").write_text(json.dumps(lock, indent=2) + "\n")
        stage.rename(target)
    print(f"Bundled verified ARM64 native libraries: {target}; system packages unchanged.")


if __name__ == "__main__":
    main()
