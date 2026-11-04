import importlib.util
from pathlib import Path
import tempfile
import unittest
import json
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("asset_cache", Path(__file__).parents[1] / "macos/asset_catalog_cache.py")
cache = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache)


class CatalogIndexTest(unittest.TestCase):
    def record(self, scale=1):
        return {"AssetType": "Image", "Name": "icon", "RenditionName": "icon.png" if scale == 1 else "icon@2x.png",
                "Scale": scale, "Width": 22 * scale, "Height": 22 * scale}

    def test_variants_keep_point_dimensions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "icon").mkdir()
            for name in ("icon.png", "icon@2x.png"):
                (root / "icon" / name).write_bytes(b"decoded fixture")
            result = cache.catalog_index([self.record(), self.record(2)], root, "/Applications/Test.app", "/Library/Caches/catalog")
            variants = result["Bundles"]["/Applications/Test.app"]["icon"]
            self.assertEqual([v["Width"] for v in variants], [22, 22])
            self.assertEqual([v["Scale"] for v in variants], [1, 2])
            self.assertEqual(variants[0]["Path"], "/Library/Caches/catalog/icon/icon.png")

    def test_rejects_escaping_paths_and_invalid_dimensions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "icon").mkdir()
            (root / "icon/icon.png").write_bytes(b"fixture")
            for changes in ({"Name": "../escape"}, {"RenditionName": "/etc/passwd"},
                            {"RenditionName": "..\\escape"}, {"Scale": 0}, {"Width": 100000},
                            {"Height": float("nan")}):
                with self.subTest(changes=changes), self.assertRaises(ValueError):
                    cache.catalog_index([self.record() | changes], root, "/Applications/Test.app", "/Library/Caches/catalog")

    def test_prepare_reuses_cache_and_invalidates_changed_catalog(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runtime, prefix = root / "runtime", root / "prefix"
            (runtime / "bin").mkdir(parents=True)
            (runtime / "bin/dextop-car").write_bytes(b"decoder version one")
            bundle = prefix / "Applications/Test.app"
            resources = bundle / "Contents/Resources"
            resources.mkdir(parents=True)
            catalog = resources / "Assets.car"
            catalog.write_bytes(b"catalog one")
            def decode(args, **kwargs):
                if args[1] == "info":
                    kwargs["stdout"].write(json.dumps([self.record()]).encode())
                else:
                    output = Path(args[-1]) / "icon"
                    output.mkdir(parents=True)
                    (output / "icon.png").write_bytes(b"decoded image")
            mapper = lambda path: "/" + path.resolve().relative_to(prefix.resolve()).as_posix()
            with patch.object(cache.subprocess, "run", side_effect=decode) as run:
                index = cache.prepare_bundle_catalogs(runtime, prefix, bundle, mapper)
                self.assertEqual(run.call_count, 2)
                stamp = (prefix / index.lstrip("/")).stat().st_mtime_ns
                self.assertEqual(cache.prepare_bundle_catalogs(runtime, prefix, bundle, mapper), index)
                self.assertEqual(run.call_count, 2)
                self.assertEqual((prefix / index.lstrip("/")).stat().st_mtime_ns, stamp)
                catalog.write_bytes(b"catalog two")
                cache.prepare_bundle_catalogs(runtime, prefix, bundle, mapper)
                self.assertEqual(run.call_count, 4)


if __name__ == "__main__":
    unittest.main()
