import importlib.machinery
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-print-backend'
loader = importlib.machinery.SourceFileLoader('dextop_print_backend', str(SOURCE))
spec = importlib.util.spec_from_loader(loader.name, loader)
module = importlib.util.module_from_spec(spec)
loader.exec_module(module)


class PrintSpoolTests(unittest.TestCase):
    def test_pdf_handoff_is_published_atomically_with_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root/'document.pdf'
            payload = b'%PDF-1.4\nsynthetic spool transport test\n'
            source.write_bytes(payload)
            spool = root/'spool'
            self.assertEqual(module.queue(['7', 'yuuki', 'A document', '2', '', str(source)], spool), 0)
            metadata = list(spool.glob('*.json'))
            self.assertEqual(len(metadata), 1)
            job = json.loads(metadata[0].read_text())
            self.assertEqual(job['title'], 'A document')
            self.assertEqual(job['copies'], 2)
            self.assertEqual((spool/(job['id']+'.pdf')).read_bytes(), payload)
            self.assertEqual(list(spool.glob('*.pending')), [])
            self.assertEqual(list(spool.glob('*.input')), [])

    def test_invalid_input_is_not_published(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root/'document.txt'
            source.write_text('not a print document')
            spool = root/'spool'
            with self.assertRaises(ValueError):
                module.queue(['1', 'user', 'title', '1', '', str(source)], spool)
            self.assertEqual(list(spool.glob('*')), [])


if __name__ == '__main__':
    unittest.main()
