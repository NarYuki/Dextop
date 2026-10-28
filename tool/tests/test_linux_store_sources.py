"""Boundary checks for the Store helper; no network or package installations."""
import importlib.machinery
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

HELPER = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-store'
loader = importlib.machinery.SourceFileLoader('dextop_store', str(HELPER))
spec = importlib.util.spec_from_loader(loader.name, loader)
store = importlib.util.module_from_spec(spec)
loader.exec_module(store)


class StoreSourceTests(unittest.TestCase):
    def test_catalog_never_evaluates_shell_expressions(self):
        self.assertEqual(store.literal('PRETTY_NAME="$(touch /tmp/injected)"', 'PRETTY_NAME', 'fallback'), 'fallback')
        self.assertEqual(store.literal('PRETTY_NAME="`id`"', 'PRETTY_NAME', 'fallback'), 'fallback')
        self.assertEqual(store.literal('PRETTY_NAME="Visual Studio Code"', 'PRETTY_NAME'), 'Visual Studio Code')

    def test_arch_and_distro_filtering(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'revision').write_text(store.COMMIT)
            definitions = root / '01-main.d'
            definitions.mkdir()
            (definitions / 'arm-app').write_text('ARCHS_SUPPORTED="amd64 arm64"\nPRETTY_NAME="ARM app"\n')
            (definitions / 'x86-app').write_text('PRETTY_NAME="x86 app"\n')
            (definitions / 'ubuntu-ppa').write_text('ARCHS_SUPPORTED="arm64"\nPPA="vendor/app"\n')
            (definitions / 'old-release').write_text('ARCHS_SUPPORTED="arm64"\nCODENAMES_SUPPORTED="bookworm"\n')
            with patch.object(store, 'DEBGET', root), patch.object(store, 'ETC', root), \
                 patch.object(store, 'distro', return_value='debian'), \
                 patch.object(store, 'run', side_effect=['arm64\n', 'trixie\n']):
                apps = store.catalog('deb-get')
            self.assertEqual([app['package'] for app in apps], ['deb-get:arm-app'])
            self.assertEqual(apps[0]['architecture'], 'ARM64')

    def test_invalid_operations_cannot_reach_subprocess(self):
        with patch.object(store, 'run') as run:
            for name in ('../code', '--help', 'foo;id', 'code\nwhoami'):
                with self.assertRaises(ValueError):
                    store.operate('deb-get', 'install', name)
            with self.assertRaises(ValueError):
                store.operate('flathub', 'arbitrary-command', 'org.example.App')
            run.assert_not_called()

    def test_flathub_status_excludes_other_remotes(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(store, 'ETC', Path(directory)), \
             patch.object(store, 'Path', side_effect=lambda value: Path(directory) / 'updates.json'), \
             patch.object(store.shutil, 'which', return_value='/usr/bin/flatpak'), \
             patch.object(store, 'run', side_effect=[
                 'org.example.One\tflathub\norg.example.Two\tother\n',
                 'org.example.One\norg.example.Two\n']):
            status = store.status(refresh=True)
        self.assertEqual(status['installed'], ['flathub:org.example.One'])
        self.assertEqual(status['upgradable'], ['flathub:org.example.One'])

    def test_removal_does_not_autoremove_unrelated_packages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'revision').write_text(store.COMMIT)
            (root / '01-main.d').mkdir()
            (root / '01-main.d/bat').write_text('')
            (root / 'installed').write_text('bat 0.26.1\ncodium 1.0\n')
            with patch.object(store, 'DEBGET', root), patch.object(store, 'ETC', root), patch.object(store, 'run') as run:
                store.operate('deb-get', 'remove', 'bat')
            run.assert_called_once_with(['apt-get', 'remove', '-y', '--no-auto-remove', 'bat'])
            self.assertEqual((root / 'installed').read_text(), 'codium 1.0\n')


if __name__ == '__main__':
    unittest.main()
