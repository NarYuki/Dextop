"""Boundary checks for the Store helper; no network or package installations."""
import contextlib
import io
import json
import sys
import time
from types import SimpleNamespace
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

    def test_carriage_return_progress_is_streamed_with_fractional_values(self):
        updates = []
        with patch.object(store, 'progress', side_effect=lambda *a, **kw: updates.append((time.monotonic(), a))), contextlib.redirect_stdout(io.StringIO()):
            store.run([sys.executable, '-c', "import os,time; os.write(1,b'Downloading 12.5%\\r'); time.sleep(.3); os.write(1,b'Downloading 78.25%\\n')"])
        self.assertEqual([x[1][0] for x in updates], [12.5, 78.25])
        self.assertGreater(updates[-1][0] - updates[0][0], .15)

    def test_flatpak_uses_api_progress_and_keeps_estimation_and_operation_count(self):
        emitted = []
        class State:
            def set_update_frequency(self, value): self.frequency = value
            def connect(self, *args): pass
            def get_status(self): return 'Downloading'
            def get_progress(self): return 37
            def get_is_estimating(self): return True
        class Transaction:
            def __init__(self): self.handlers = {}; self.install = None
            def set_no_interaction(self, value): self.noninteractive = value
            def connect(self, name, handler): self.handlers[name] = handler
            def get_operations(self): return [1, 2]
            def add_install(self, *args): self.install = args
            def run(self, ignored):
                self.handlers['ready'](self)
                self.handlers['new-operation'](self, SimpleNamespace(get_ref=lambda: 'app/org.example.App/aarch64/stable'), State())
                return True
        tx = Transaction()
        api = SimpleNamespace(Installation=SimpleNamespace(new_user=lambda _: object()), Transaction=SimpleNamespace(new_for_installation=lambda *args: tx))
        with patch.object(store, 'flatpak_api', return_value=api), patch.object(store, 'progress', side_effect=lambda *args: emitted.append(args)):
            store.flatpak_operate('install', 'org.example.App')
        self.assertTrue(tx.noninteractive)
        self.assertEqual(tx.install, ('flathub', 'org.example.App', None))
        update = next(x for x in emitted if len(x) == 4)
        self.assertEqual(update[0], 37)
        self.assertTrue(update[3])
        self.assertIn('1/2', update[2])
        self.assertEqual(emitted[-1][0], 100)
        self.assertFalse(tx.handlers['add-new-remote']())

    def test_catalog_reports_total_and_distinguishes_refresh_from_cached_reads(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(store, 'distro'), patch.object(store, 'Path', side_effect=lambda _: root / 'catalog.json'), patch.object(store, 'catalog', return_value=[{'id':'one'}, {'id':'two'}]) as fetch:
                results = []
                for mode in ['cached', 'cached', 'refresh']:
                    output = io.StringIO()
                    with contextlib.redirect_stdout(output): store.main(['catalog', 'flathub', '0', '80', mode])
                    results.append(json.loads(output.getvalue()))
                self.assertEqual(fetch.call_count, 2)
                self.assertEqual([r['refreshing'] for r in results], [True, False, True])
                self.assertEqual([r['total'] for r in results], [2, 2, 2])


if __name__ == '__main__':
    unittest.main()
