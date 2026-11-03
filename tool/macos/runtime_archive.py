"""Package and install a pinned runtime with an externally trusted archive digest.

The manifest checks content integrity; it is not a signature or a trust source.
Installation requires a SHA-256 supplied independently of the selected archive.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import stat
import struct
import tarfile
import tempfile


MANIFEST = "dextop-runtime.json"
MAX_FILES = 100000
MAX_BYTES = 4 * 1024**3
MAX_MANIFEST = 32 * 1024**2


def rename_noreplace(source, target):
    import ctypes
    libc = ctypes.CDLL(None, use_errno=True)
    rename = getattr(libc, "renameat2", None)
    if rename is None:
        raise OSError("Atomic runtime installation requires Linux renameat2")
    rename.argtypes = (ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p, ctypes.c_uint)
    rename.restype = ctypes.c_int
    if rename(-100, os.fsencode(source), -100, os.fsencode(target), 1) != 0:
        error = ctypes.get_errno()
        raise OSError(error, os.strerror(error), str(target))


def digest_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def safe_path(value):
    if not isinstance(value, str) or not value or "\\" in value or "\x00" in value:
        raise ValueError("Invalid runtime entry path")
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in ("", ".", "..") for part in value.split("/")):
        raise ValueError("Runtime entry escapes its directory")
    return path


def package_runtime(runtime, output, metadata):
    runtime = runtime.resolve(strict=True)
    if output.exists():
        raise ValueError("Archive output already exists")
    if output.resolve().is_relative_to(runtime):
        raise ValueError("Archive output must be outside the staged runtime")
    if metadata.get("architecture") not in ("aarch64", "x86_64") or metadata.get("components") not in ("core", "gui"):
        raise ValueError("Runtime build metadata is incomplete")
    inventory = []
    for directory, dirs, files in os.walk(runtime, followlinks=False):
        for name in sorted(dirs + files):
            path = Path(directory) / name
            relative = path.relative_to(runtime).as_posix()
            safe_path(relative)
            if relative == MANIFEST:
                raise ValueError("Staged runtime already contains a reserved manifest")
            mode = path.lstat().st_mode
            if mode & 0o6000:
                raise ValueError(f"Setuid/setgid runtime entry: {relative}")
            item = {"path": relative, "mode": stat.S_IMODE(mode)}
            if path.is_symlink():
                item.update(type="symlink", target=os.readlink(path))
            elif path.is_dir():
                item.update(type="directory")
            elif path.is_file():
                item.update(type="file", size=path.stat().st_size, sha256=digest_file(path))
            else:
                raise ValueError(f"Unsupported runtime entry: {relative}")
            inventory.append(item)
    inventory.sort(key=lambda item: item["path"])
    manifest = {"schema": 1, "build": metadata, "entries": inventory}
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as stream:
            temporary = Path(stream.name)
        with tarfile.open(temporary, "w:gz", format=tarfile.PAX_FORMAT) as archive:
            import io
            data = json.dumps(manifest, ensure_ascii=False, sort_keys=True).encode()
            header = tarfile.TarInfo(MANIFEST)
            header.size, header.mode = len(data), 0o644
            archive.addfile(header, io.BytesIO(data))
            for item in inventory:
                path = runtime / item["path"]
                header = tarfile.TarInfo(item["path"])
                header.mode = item["mode"]
                if item["type"] == "directory":
                    header.type = tarfile.DIRTYPE
                    archive.addfile(header)
                elif item["type"] == "symlink":
                    header.type, header.linkname = tarfile.SYMTYPE, item["target"]
                    archive.addfile(header)
                else:
                    header.size = item["size"]
                    with path.open("rb") as stream:
                        archive.addfile(header, stream)
        rename_noreplace(temporary, output)
        temporary = None
        return digest_file(output)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def validate_manifest(manifest, architecture, source):
    if not isinstance(manifest, dict) or manifest.get("schema") != 1:
        raise ValueError("Unsupported runtime manifest")
    build = manifest.get("build", {})
    if build.get("architecture") != architecture or build.get("commit") != source:
        raise ValueError("Runtime architecture or source does not match the expected build")
    if build.get("components") not in ("core", "gui"):
        raise ValueError("Unknown runtime components")
    entries = manifest.get("entries")
    if not isinstance(entries, list) or not 1 <= len(entries) <= MAX_FILES:
        raise ValueError("Invalid runtime inventory size")
    inventory = {}
    total = 0
    for item in entries:
        path = str(safe_path(item["path"]))
        if path == MANIFEST or path in inventory:
            raise ValueError("Duplicate or reserved runtime path")
        kind = item.get("type")
        if kind not in ("directory", "file", "symlink"):
            raise ValueError("Unsupported runtime entry type")
        mode = item.get("mode")
        if not isinstance(mode, int) or mode < 0 or mode > 0o1777 or kind != "directory" and mode & 0o1000:
            raise ValueError("Runtime contains unsupported file permissions")
        if kind == "directory" and mode & 0o500 != 0o500:
            raise ValueError("Runtime directories must remain readable and searchable by their owner")
        if kind == "file":
            size = item.get("size")
            if not isinstance(size, int) or not 0 <= size <= MAX_BYTES:
                raise ValueError("Invalid runtime file size")
            if not re.fullmatch("[a-f0-9]{64}", item.get("sha256", "")):
                raise ValueError("Invalid runtime file digest")
            total += size
        if kind == "symlink" and (not isinstance(item.get("target"), str) or not item["target"] or "\x00" in item["target"]):
            raise ValueError("Invalid runtime symbolic link")
        inventory[path] = item
    if total > MAX_BYTES:
        raise ValueError("Runtime exceeds the unpacked size limit")
    for path in inventory:
        for parent in PurePosixPath(path).parents:
            if str(parent) != "." and inventory.get(str(parent), {}).get("type") != "directory":
                raise ValueError("Runtime entry has a missing or symbolic-link parent")
    return inventory


def validate_native_binaries(root, architecture, gui):
    machine = {"aarch64": 183, "x86_64": 62}[architecture]
    for relative in ("bin/darling", "bin/darlingserver", "libexec/darling/bin/mldr"):
        path = root / relative
        if not path.resolve(strict=True).is_relative_to(root) or not os.access(path, os.X_OK):
            raise ValueError(f"Runtime executable unavailable: {relative}")
        with path.open("rb") as stream:
            header = stream.read(20)
        if len(header) != 20 or header[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", header, 18)[0] != machine:
            raise ValueError(f"Runtime executable has the wrong native architecture: {relative}")
    darwin_components = ["libexec/darling/usr/lib/dyld", "libexec/darling/sbin/launchd"]
    if gui:
        darwin_components.append("libexec/darling/System/Library/Frameworks/AppKit.framework/Versions/C/AppKit")
    cpu = {"aarch64": 0x0100000C, "x86_64": 0x01000007}[architecture]
    for relative in darwin_components:
        path = root / relative
        if not path.resolve(strict=True).is_relative_to(root) or not path.is_file():
            raise ValueError(f"Missing Darwin runtime component: {relative}")
        with path.open("rb") as stream:
            header = stream.read(8)
        if len(header) != 8 or header[:4] != b"\xcf\xfa\xed\xfe" or struct.unpack_from("<I", header, 4)[0] != cpu:
            raise ValueError(f"Darwin runtime component has the wrong native architecture: {relative}")


def install_archive(archive_path, target, expected_sha256, architecture, source, require_gui=True):
    if not re.fullmatch("[a-f0-9]{64}", expected_sha256):
        raise ValueError("An independently trusted SHA-256 is required")
    if architecture not in ("aarch64", "x86_64"):
        raise ValueError("Unsupported runtime architecture")
    # Hold one file descriptor from digest verification through extraction.
    with archive_path.open("rb") as source_stream:
        digest = hashlib.sha256()
        for block in iter(lambda: source_stream.read(1024 * 1024), b""):
            digest.update(block)
        if digest.hexdigest() != expected_sha256:
            raise ValueError("Runtime archive SHA-256 mismatch")
        source_stream.seek(0)
        target = target.absolute()
        if os.path.lexists(target):
            raise ValueError("Runtime already exists; refusing to overwrite it")
        target.parent.mkdir(parents=True, exist_ok=True)
        staging = Path(tempfile.mkdtemp(prefix=".dextop-macos-install-", dir=target.parent))
        try:
            with tarfile.open(fileobj=source_stream, mode="r:gz") as archive:
                manifest_member = archive.next()
                if manifest_member is None or manifest_member.name != MANIFEST or not manifest_member.isfile() or not 0 < manifest_member.size <= MAX_MANIFEST:
                    raise ValueError("Runtime manifest must be the first archive entry")
                manifest = json.load(archive.extractfile(manifest_member))
                inventory = validate_manifest(manifest, architecture, source)
                if require_gui and manifest["build"]["components"] != "gui":
                    raise ValueError("A GUI runtime is required")
                seen = set()
                links = []
                for member in archive:
                    # Iteration includes the first entry cached by archive.next().
                    if member is manifest_member:
                        continue
                    name = str(safe_path(member.name))
                    if name in seen or name not in inventory:
                        raise ValueError("Duplicate or unexpected archive entry")
                    seen.add(name)
                    item = inventory[name]
                    destination = staging / name
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    kind = item["type"]
                    if kind == "directory" and member.isdir():
                        destination.mkdir(exist_ok=True)
                    elif kind == "symlink" and member.issym() and member.linkname == item["target"]:
                        # Darwin absolute links are preserved, but never followed
                        # while extracting or validating native executable paths.
                        links.append((destination, item["target"]))
                    elif kind == "file" and member.isfile() and member.size == item["size"]:
                        digest = hashlib.sha256()
                        with archive.extractfile(member) as data, destination.open("xb") as output:
                            for block in iter(lambda: data.read(1024 * 1024), b""):
                                digest.update(block)
                                output.write(block)
                        if digest.hexdigest() != item["sha256"]:
                            raise ValueError(f"Runtime file digest mismatch: {name}")
                        destination.chmod(item["mode"])
                    else:
                        raise ValueError(f"Archive entry does not match its manifest: {name}")
                if seen != inventory.keys():
                    raise ValueError("Runtime archive is missing inventory entries")
                for destination, link in links:
                    destination.symlink_to(link)
                for name in sorted(inventory, key=lambda name: name.count('/'), reverse=True):
                    if inventory[name]["type"] == "directory":
                        (staging / name).chmod(inventory[name]["mode"])
                validate_native_binaries(staging, architecture, require_gui)
                (staging / MANIFEST).write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
                # Refuse replacement even when another installer completed while
                # this one was validating. No merge with an existing runtime.
                if os.path.lexists(target):
                    raise ValueError("Runtime appeared during installation")
                rename_noreplace(staging, target)
        finally:
            if staging.exists():
                shutil.rmtree(staging)
    return {"runtime": str(target), "architecture": architecture, "components": manifest["build"]["components"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    package = sub.add_parser("package")
    package.add_argument("--runtime", required=True, type=Path)
    package.add_argument("--metadata", required=True, type=Path)
    package.add_argument("--output", required=True, type=Path)
    install = sub.add_parser("install")
    install.add_argument("--archive", required=True, type=Path)
    install.add_argument("--target", type=Path, default=Path("/opt/dextop/macos"))
    install.add_argument("--sha256", required=True)
    install.add_argument("--source-commit", required=True)
    install.add_argument("--architecture", choices=("aarch64", "x86_64"), default=platform.machine())
    install.add_argument("--core-only", action="store_true")
    args = parser.parse_args()
    if args.action == "package":
        print(json.dumps({"archive": str(args.output), "sha256": package_runtime(args.runtime, args.output, json.loads(args.metadata.read_text()))}))
    else:
        print(json.dumps(install_archive(args.archive, args.target, args.sha256, args.architecture, args.source_commit, not args.core_only)))


if __name__ == "__main__":
    main()
