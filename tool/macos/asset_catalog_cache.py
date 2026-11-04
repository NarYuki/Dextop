"""Index decoded catalog PNG variants without modifying their application bundle."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import plistlib
import subprocess
import tempfile


def safe_relative(value):
    if not isinstance(value, str) or not value or "\\" in value:
        raise ValueError("Invalid asset path")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or "\0" in value:
        raise ValueError("Asset path escapes the cache")
    return path


def catalog_index(records, decoded, bundle_path, darwin_cache):
    decoded = decoded.resolve(strict=True)
    if not isinstance(records, list) or len(records) > 100000:
        raise ValueError("Invalid catalog metadata")
    if not bundle_path.startswith("/") or not darwin_cache.startswith("/"):
        raise ValueError("Darwin bundle and cache paths must be absolute")
    images = {}
    for record in records:
        if record.get("AssetType") != "Image" or record.get("ThemeAppearance", 0) != 0:
            continue
        name = record.get("Name")
        relative = safe_relative(name) / safe_relative(record.get("RenditionName"))
        if relative.suffix.lower() != ".png":
            continue
        path = decoded.joinpath(*relative.parts).resolve(strict=True)
        if not path.is_relative_to(decoded) or not path.is_file():
            raise ValueError("Decoded asset escapes the cache")
        scale, width, height = (record.get(key, 0) for key in ("Scale", "Width", "Height"))
        if not all(isinstance(v, (int, float)) and 0 < v <= 16384 for v in (scale, width, height)):
            raise ValueError("Invalid catalog dimensions")
        if width * height > 16777216 or scale > 16:
            raise ValueError("Catalog image exceeds limits")
        images.setdefault(name, []).append({
            "Path": str(PurePosixPath(darwin_cache) / relative),
            "Width": width / scale, "Height": height / scale, "Scale": scale,
        })
    return {"Version": 1, "Bundles": {bundle_path: images}}


def file_digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def prepare_bundle_catalogs(runtime, prefix, bundle, darwin_path):
    """Decode catalogs once per content/tool version; return the aggregate index."""
    bundle = bundle.resolve(strict=True)
    catalogs = sorted({path.resolve() for path in bundle.rglob("Assets.car") if path.is_file()})
    if not catalogs:
        return None
    helper = runtime / "bin/dextop-car"
    if not helper.is_file():
        raise ValueError("The macOS runtime is missing its asset catalog decoder")
    tool_hash = file_digest(helper)
    base = prefix / "Library/Caches/DextopAssetCatalogs"
    base.mkdir(parents=True, exist_ok=True)
    combined = {"Version": 1, "Bundles": {}}
    for catalog in catalogs:
        if not catalog.is_relative_to(bundle) or catalog.stat().st_size > 256 * 1024 * 1024:
            raise ValueError("Invalid bundle asset catalog")
        owner = next((p for p in catalog.parents if p.suffix in (".app", ".bundle", ".framework")), bundle)
        bundle_name = darwin_path(owner)
        identity = "v1:" + file_digest(catalog) + ":" + tool_hash + ":" + bundle_name
        destination = base / hashlib.sha256(identity.encode()).hexdigest()
        index_path = destination / "index.plist"
        if not index_path.is_file():
            print("macOS: preparing application images", flush=True)
            with tempfile.TemporaryDirectory(dir=base) as temporary:
                stage = Path(temporary)
                decoded = stage / "decoded"
                with (stage / "info.json").open("wb") as output:
                    subprocess.run([str(helper), "info", str(catalog)], stdout=output, check=True, timeout=120)
                if (stage / "info.json").stat().st_size > 32 * 1024 * 1024:
                    raise ValueError("Catalog metadata is too large")
                with (stage / "extract.log").open("wb") as output:
                    subprocess.run([str(helper), "extract", str(catalog), "-o", str(decoded)], stdout=output, check=True, timeout=120)
                records = json.loads((stage / "info.json").read_text())
                index = catalog_index(records, decoded, bundle_name, darwin_path(destination / "decoded"))
                (stage / "index.plist").write_bytes(plistlib.dumps(index))
                # The launcher holds the prefix lock throughout this operation.
                stage.rename(destination)
        index = plistlib.loads(index_path.read_bytes())
        for owner_name, images in index["Bundles"].items():
            combined["Bundles"].setdefault(owner_name, {}).update(images)
    name = hashlib.sha256(str(bundle).encode()).hexdigest() + ".plist"
    target = base / name
    content = plistlib.dumps(combined)
    if not target.is_file() or target.read_bytes() != content:
        with tempfile.NamedTemporaryFile(dir=base, delete=False) as output:
            output.write(content)
            temporary = Path(output.name)
        temporary.replace(target)
    return darwin_path(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--info", type=Path, required=True)
    parser.add_argument("--decoded", type=Path, required=True)
    parser.add_argument("--bundle-path", required=True)
    parser.add_argument("--darwin-cache", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    index = catalog_index(json.loads(args.info.read_text()), args.decoded, args.bundle_path, args.darwin_cache)
    args.output.write_bytes(plistlib.dumps(index))


if __name__ == "__main__":
    main()
