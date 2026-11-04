import importlib.util
import io
import json
import os
from pathlib import Path
import struct
import tarfile
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("runtime_archive", Path(__file__).parents[1] / "runtime_archive.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


@unittest.skipUnless(os.name == 'posix', 'Linux archive permissions and atomic installation')
class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.runtime = self.root / 'source'
        self.source = 'ab' * 20
        self.metadata = {'commit': self.source, 'architecture': 'aarch64', 'components': 'gui',
                         'android_execution_verified': False, 'macos_application_verified': False}
        for name in ('bin/darling', 'bin/darlingserver', 'libexec/darling/bin/mldr', 'bin/dextop-car'):
            path = self.runtime / name
            path.parent.mkdir(parents=True, exist_ok=True)
            header = bytearray(20)
            header[:6] = b'\x7fELF\x02\x01'
            struct.pack_into('<H', header, 18, 183)
            path.write_bytes(header)
            path.chmod(0o755)
        for name in ('libexec/darling/usr/lib/dyld', 'libexec/darling/sbin/launchd',
                     'libexec/darling/System/Library/Frameworks/AppKit.framework/Versions/C/AppKit'):
            path = self.runtime / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(struct.pack('<II', 0xFEEDFACF, 0x0100000C))
        self.archive = self.root / 'runtime.tar.gz'
        self.target = self.root / 'installed'

    def package(self):
        return module.package_runtime(self.runtime, self.archive, self.metadata)

    def test_gui_rejects_missing_catalog_decoder(self):
        (self.runtime / 'bin/dextop-car').unlink()
        with self.assertRaises((ValueError, FileNotFoundError)):
            self.install(self.package())
        self.assertFalse(self.target.exists())

    def test_gui_rejects_wrong_catalog_decoder_architecture(self):
        path = self.runtime / 'bin/dextop-car'
        header = bytearray(path.read_bytes())
        struct.pack_into('<H', header, 18, 62)
        path.write_bytes(header)
        with self.assertRaises(ValueError):
            self.install(self.package())
        self.assertFalse(self.target.exists())

    def install(self, digest):
        return module.install_archive(self.archive, self.target, digest, 'aarch64', self.source)

    def rewrite(self, transform):
        with tarfile.open(self.archive) as archive:
            entries = [(member, archive.extractfile(member).read() if member.isfile() else None) for member in archive]
        entries = transform(entries)
        with tarfile.open(self.archive, 'w:gz') as archive:
            for member, content in entries:
                archive.addfile(member, io.BytesIO(content) if content is not None else None)
        return module.digest_file(self.archive)

    def test_install_preserves_files_virtual_links_and_unverified_metadata(self):
        (self.runtime / 'darwin-link').symlink_to('/usr/lib/libSystem.B.dylib')
        temporary = self.runtime / 'private-tmp'
        temporary.mkdir()
        temporary.chmod(0o1777)
        result = self.install(self.package())
        self.assertEqual(result['components'], 'gui')
        self.assertEqual(os.readlink(self.target / 'darwin-link'), '/usr/lib/libSystem.B.dylib')
        self.assertEqual((self.target / 'bin/darling').read_bytes(), (self.runtime / 'bin/darling').read_bytes())
        manifest = json.loads((self.target / module.MANIFEST).read_text())
        self.assertFalse(manifest['build']['android_execution_verified'])
        self.assertEqual((self.target / 'private-tmp').stat().st_mode & 0o7777, 0o1777)
        self.assertEqual(list(self.root.glob('.dextop-macos-install-*')), [])

    def test_external_digest_mismatch_never_creates_target(self):
        self.package()
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            self.install('0' * 64)
        self.assertFalse(self.target.exists())

    def test_file_tampering_rolls_back_staging(self):
        self.package()
        def change(entries):
            return [(member, b'x' * len(data) if member.name == 'bin/darling' else data) for member, data in entries]
        with self.assertRaisesRegex(ValueError, 'file digest mismatch'):
            self.install(self.rewrite(change))
        self.assertFalse(self.target.exists())
        self.assertEqual(list(self.root.glob('.dextop-macos-install-*')), [])

    def test_unexpected_entry_and_traversal_rejected(self):
        self.package()
        for name in ('extra-file', '../outside', '/absolute'):
            with self.subTest(name=name):
                def change(entries):
                    header = tarfile.TarInfo(name)
                    header.size = 3
                    return entries + [(header, b'bad')]
                with self.assertRaises(ValueError):
                    self.install(self.rewrite(change))
                self.assertFalse((self.root / 'outside').exists())
                self.assertFalse(self.target.exists())

    def test_symlink_parent_inventory_cannot_write_outside(self):
        manifest = {'schema': 1, 'build': self.metadata, 'entries': [
            {'path': 'escape', 'type': 'symlink', 'target': '../outside', 'mode': 0o777},
            {'path': 'escape/payload', 'type': 'file', 'size': 1, 'sha256': '0' * 64, 'mode': 0o644}]}
        with self.assertRaisesRegex(ValueError, 'symbolic-link parent'):
            module.validate_manifest(manifest, 'aarch64', self.source)

    def test_native_binary_architecture_checked_independently_of_manifest(self):
        path = self.runtime / 'bin/darlingserver'
        header = bytearray(path.read_bytes())
        struct.pack_into('<H', header, 18, 62)
        path.write_bytes(header)
        with self.assertRaisesRegex(ValueError, 'wrong native architecture'):
            self.install(self.package())
        self.assertFalse(self.target.exists())

    def test_existing_runtime_and_atomic_race_are_never_overwritten(self):
        digest = self.package()
        self.target.mkdir()
        saved = self.target / 'user-data'
        saved.write_text('keep')
        with self.assertRaises(ValueError):
            self.install(digest)
        self.assertEqual(saved.read_text(), 'keep')
        other = self.root / 'other'
        other.mkdir()
        with self.assertRaises(FileExistsError):
            module.rename_noreplace(other, self.target)
        self.assertEqual(saved.read_text(), 'keep')

    def test_darwin_architecture_must_match_native_host_components(self):
        (self.runtime / 'libexec/darling/usr/lib/dyld').write_bytes(struct.pack('<II', 0xFEEDFACF, 0x01000007))
        with self.assertRaisesRegex(ValueError, 'Darwin runtime component has the wrong native architecture'):
            self.install(self.package())
        self.assertFalse(self.target.exists())

    def test_incomplete_inventory_is_not_installed(self):
        self.package()
        digest = self.rewrite(lambda entries: [(member, data) for member, data in entries if member.name != 'bin/darling'])
        with self.assertRaisesRegex(ValueError, 'missing inventory'):
            self.install(digest)
        self.assertFalse(self.target.exists())


if __name__ == '__main__':
    unittest.main()
