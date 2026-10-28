import importlib.machinery
import importlib.util
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-keyring-repair'
loader = importlib.machinery.SourceFileLoader('keyring_repair', str(SOURCE))
spec = importlib.util.spec_from_loader(loader.name, loader)
module = importlib.util.module_from_spec(spec)
loader.exec_module(module)
MAGIC = b'GnomeKeyring\n\r\x00\n'


class KeyringRecoveryTests(unittest.TestCase):
    def test_recovers_existing_encrypted_collection_without_changing_its_data(self):
        with tempfile.TemporaryDirectory() as home:
            directory = Path(home) / '.local/share/keyrings'
            directory.mkdir(parents=True)
            (directory / 'default').write_text('Default_Keyring\n')
            (directory / 'Default_Keyring.keyring').write_bytes(b'')
            payload = MAGIC + b'encrypted-test-data'
            collection = directory / 'Default_Keyring_1.keyring'
            collection.write_bytes(payload)
            self.assertEqual(module.repair(home), 'Default_Keyring_1')
            self.assertEqual(collection.read_bytes(), payload)
            self.assertEqual((directory / 'default').read_text(), 'Default_Keyring_1\n')
            self.assertEqual((directory / 'default.before-dextop-repair').read_text(), 'Default_Keyring\n')
            self.assertIsNone(module.repair(home))

    def test_does_not_select_unrelated_or_plaintext_collections(self):
        with tempfile.TemporaryDirectory() as home:
            directory = Path(home) / '.local/share/keyrings'
            directory.mkdir(parents=True)
            (directory / 'default').write_text('Default_Keyring\n')
            (directory / 'Other_1.keyring').write_bytes(MAGIC)
            (directory / 'Default_Keyring_1.keyring').write_text('[keyring]\n')
            self.assertIsNone(module.repair(home))
            self.assertEqual((directory / 'default').read_text(), 'Default_Keyring\n')

    def test_preserves_nonempty_unknown_default_format(self):
        with tempfile.TemporaryDirectory() as home:
            directory = Path(home) / '.local/share/keyrings'
            directory.mkdir(parents=True)
            (directory / 'default').write_text('Default_Keyring\n')
            (directory / 'Default_Keyring.keyring').write_bytes(b'user-owned-format')
            (directory / 'Default_Keyring_1.keyring').write_bytes(MAGIC)
            self.assertIsNone(module.repair(home))


if __name__ == '__main__':
    unittest.main()
