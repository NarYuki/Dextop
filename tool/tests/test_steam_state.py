import importlib.machinery
from pathlib import Path
import tempfile
import unittest
import zlib

STATE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-steam-state'

class SteamStateTest(unittest.TestCase):
    def setUp(self):
        self.state = importlib.machinery.SourceFileLoader('steam_state', str(STATE)).load_module()
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.root = self.base / 'root'
        self.source = self.base / 'verified'
        (self.root / 'package').mkdir(parents=True)
        (self.root / 'package/beta').write_text('steamdeck_publicbeta\n')
        for tree in (self.root, self.source):
            (tree / 'steamrtarm64').mkdir(parents=True)
            (tree / 'steamrtarm64/steam').write_bytes(b'verified client')
        self.target = self.root / 'package/steam_client_steamdeck_publicbeta_linuxarm64.installed'

    def test_records_actual_size_crc_and_preserves_other_files(self):
        (self.root / 'user-config').write_text('preserve')
        self.assertTrue(self.state.record(self.root, self.source))
        row = next(row for row in self.target.read_text().splitlines() if row.startswith('steamrtarm64/steam,'))
        name, attributes = row.split(',')
        size, timestamp, crc = attributes.split(';')
        self.assertEqual(name, 'steamrtarm64/steam')
        self.assertEqual(int(size), len(b'verified client'))
        self.assertEqual(int(crc), zlib.crc32(b'verified client'))
        self.assertGreater(int(timestamp), 0)
        self.assertEqual((self.root / 'user-config').read_text(), 'preserve')

    def test_mismatch_does_not_replace_existing_metadata(self):
        self.target.write_text('previous valid metadata')
        (self.root / 'steamrtarm64/steam').write_bytes(b'modified client')
        with self.assertRaises(ValueError):
            self.state.record(self.root, self.source)
        self.assertEqual(self.target.read_text(), 'previous valid metadata')

    def test_longer_installed_file_is_rejected(self):
        (self.root / 'steamrtarm64/steam').write_bytes(b'verified client extra')
        with self.assertRaises(ValueError):
            self.state.record(self.root, self.source)
        self.assertFalse(self.target.exists())

    def test_symlink_uses_valve_sentinel_and_target_crc(self):
        import os
        target = '../steamrtarm64/steam'
        try:
            os.symlink(target, self.source / 'client-link')
            os.symlink(target, self.root / 'client-link')
        except OSError:
            self.skipTest('Host cannot create symlinks')
        self.state.record(self.root, self.source)
        row = next(row for row in self.target.read_text().splitlines() if row.startswith('client-link,'))
        size, timestamp, crc = row.split(',')[1].split(';')
        self.assertEqual(size, '-2')
        self.assertEqual(int(crc), zlib.crc32(os.fsencode(target)))
        self.assertIn('steamrtarm64/,-1;', self.target.read_text())

    def test_empty_payload_is_rejected(self):
        (self.source / 'steamrtarm64/steam').unlink()
        with self.assertRaises(ValueError):
            self.state.record(self.root, self.source)
        self.assertFalse(self.target.exists())

if __name__ == '__main__':
    unittest.main()
