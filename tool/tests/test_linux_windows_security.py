import hashlib
import io
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

HELPER = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-windows'
MODULE = runpy.run_path(str(HELPER), run_name='security_test')


class Source(io.BytesIO):
    headers = {'Content-Length': '3'}


class WindowsSecurityTest(unittest.TestCase):
    def test_missing_or_malformed_hash_never_starts_download(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(MODULE['urllib'].request, 'urlopen') as request:
            for digest in (None, '', 'abc', 'g' * 64, '0' * 63):
                with self.subTest(digest=digest), self.assertRaises(RuntimeError):
                    MODULE['download']('https://example.invalid/app', Path(directory) / 'app', digest, 0, 1)
            request.assert_not_called()

    def test_mismatch_is_rejected_and_removed(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(MODULE['urllib'].request, 'urlopen', return_value=Source(b'abc')):
            target = Path(directory) / 'app'
            with self.assertRaises(RuntimeError):
                MODULE['download']('https://example.invalid/app', target, '0' * 64, 0, 1)
            self.assertFalse(target.exists())

    def test_valid_uppercase_hash_is_accepted(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(MODULE['urllib'].request, 'urlopen', return_value=Source(b'abc')):
            target = Path(directory) / 'app'
            MODULE['download']('https://example.invalid/app', target, hashlib.sha256(b'abc').hexdigest().upper(), 0, 1)
            self.assertEqual(target.read_bytes(), b'abc')

    def test_insecure_download_is_rejected(self):
        with patch.object(MODULE['urllib'].request, 'urlopen') as request:
            with self.assertRaises(RuntimeError):
                MODULE['download']('http://example.invalid/app', Path('unused'), '0' * 64, 0, 1)
            request.assert_not_called()

    def test_nested_program_paths_cannot_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path in ('../app.exe', 'bin/../../app.exe', '/tmp/app.exe', r'C:\app.exe', r'..\app.exe', '//host/share/app.exe', 'app.exe:stream'):
                with self.subTest(path=path), self.assertRaises(RuntimeError):
                    MODULE['safe_archive_target'](root, path)
            self.assertEqual(MODULE['safe_archive_target'](root, r'bin\app.exe'), root.resolve() / 'bin/app.exe')

    def test_invalid_spec_does_not_prepare_proton(self):
        with patch.object(MODULE['subprocess'], 'run') as run:
            with self.assertRaises(RuntimeError):
                MODULE['install']({'id': 'Vendor.App', 'sha256': None})
            run.assert_not_called()


if __name__ == '__main__':
    unittest.main()
