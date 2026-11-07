import importlib.machinery
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-deck-input'


@unittest.skipIf(os.name == 'nt', 'Linux evdev')
class DeckInputTests(unittest.TestCase):
    def test_transient_open_failure_retries_and_split_reads_keep_events(self):
        module = importlib.machinery.SourceFileLoader('deck_input_test', str(SOURCE)).load_module()
        keys = []
        x = SimpleNamespace(key=lambda code, down: keys.append((code, down)), flush=lambda: None)
        events = module.EVENT.pack(0, 0, module.EV_KEY, module.KEY_A, 1) + module.EVENT.pack(0, 0, module.EV_KEY, module.KEY_A, 0)
        with patch.object(module, 'X', return_value=x), \
             patch.object(module.time, 'monotonic', side_effect=[3, 6, 6, 6]), \
             patch.object(module.os, 'listdir', return_value=['event0']), \
             patch.object(module.os, 'open', side_effect=[PermissionError(), 123]) as opened, \
             patch.object(module, 'classify', return_value='keyboard'), \
             patch.object(module, 'name', return_value='test keyboard'), \
             patch.object(module.fcntl, 'ioctl'), \
             patch.object(module.select, 'select', side_effect=[([], [], []), ([123], [], []), ([123], [], []), KeyboardInterrupt()]), \
             patch.object(module.os, 'read', side_effect=[events[:7], events[7:]]):
            with self.assertRaises(KeyboardInterrupt):
                module.main()
        self.assertEqual(opened.call_count, 2)
        self.assertEqual(keys, [(module.KEY_A, True), (module.KEY_A, False)])


if __name__ == '__main__':
    unittest.main()
