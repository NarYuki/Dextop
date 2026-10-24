"""Run on Linux (also inside the Android guest); no GUI or game is launched."""
import importlib.machinery
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

RESOURCE = Path(os.environ['DEXTOP_SYNC_RESOURCE']) if 'DEXTOP_SYNC_RESOURCE' in os.environ else Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-wine-sync'

@unittest.skipUnless(os.name == "posix", "Wine synchronization requires POSIX fcntl")
class WineSyncTest(unittest.TestCase):
    def setUp(self):
        self.module = importlib.machinery.SourceFileLoader('sync_test', str(RESOURCE)).load_module()
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.tool = self.root / 'proton'
        for path, data in ((self.module.NTDLL, b'ntdll'), (self.module.WINESERVER, b'fsync: up and running /dev/ntsync')):
            target = self.tool / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        self.env = {'HOME': str(self.root), 'STEAM_COMPAT_DATA_PATH': str(self.root / 'data')}

    def configure(self, env=None):
        with patch.object(self.module.Path, 'iterdir', return_value=iter([])):
            return self.module.configure(self.tool, self.env if env is None else env)

    def test_supported_stock_fsync(self):
        env = self.configure()
        self.assertEqual(env['BL_FSYNC'], '1')
        self.assertEqual(env['WINEFSYNC'], '1')
        self.assertEqual(env['BL_SYNC'], '0')
        self.assertNotIn('WINESERVER', env)

    def test_explicit_disables_are_respected(self):
        env = self.configure(dict(self.env, PROTON_NO_FSYNC='1', PROTON_NO_NTSYNC='1', PROTON_NO_ESYNC='1'))
        self.assertNotEqual(env.get('BL_FSYNC'), '1')
        self.assertNotEqual(env.get('BL_SYNC'), '1')

    def test_unsupported_build_does_not_enable_sync_emulation(self):
        (self.tool / self.module.WINESERVER).write_bytes(b'ordinary Wine')
        env = self.configure()
        self.assertNotEqual(env.get('BL_FSYNC'), '1')
        self.assertNotEqual(env.get('BL_SYNC'), '1')

    def test_ntsync_fallback(self):
        (self.tool / self.module.WINESERVER).write_bytes(b'/dev/ntsync')
        self.assertEqual(self.configure()['BL_SYNC'], '1')

    def test_x86_stock_layout(self):
        for path, data in (('files/lib64/wine/x86_64-unix/ntdll.so', b'ntdll64'), ('files/bin/wineserver', b'fsync: up and running')):
            target = self.tool / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        with patch.object(self.module.Path, 'iterdir', return_value=iter([])):
            env = self.module.configure(self.tool, self.env, arm64=False)
        self.assertEqual(env['BL_FSYNC'], '1')

    def test_explicit_winefsync_zero_uses_ntsync_instead(self):
        env = self.configure(dict(self.env, WINEFSYNC='0'))
        self.assertNotEqual(env.get('BL_FSYNC'), '1')
        self.assertEqual(env['BL_SYNC'], '1')

    def test_existing_server_before_policy_is_preserved(self):
        proc = self.root / '123'
        proc.mkdir()
        (proc / 'comm').write_text('wineserver\n')
        prefix = str(self.root / 'data/pfx')
        (proc / 'environ').write_bytes(('WINEPREFIX=' + prefix + '\0PROTON_NO_FSYNC=1\0PROTON_NO_ESYNC=1\0').encode())
        with patch.object(self.module.Path, 'iterdir', return_value=iter([proc])):
            env = self.module.configure(self.tool, dict(self.env, BL_FSYNC='1'))
        self.assertEqual(env['PROTON_NO_FSYNC'], '1')
        self.assertNotIn('BL_FSYNC', env)
        self.assertNotIn('WINEFSYNC', env)

if __name__ == '__main__':
    unittest.main()
