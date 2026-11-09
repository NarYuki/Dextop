import importlib.machinery
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-integrate'
loader = importlib.machinery.SourceFileLoader('dextop_localized_integrate', str(SOURCE))
spec = importlib.util.spec_from_loader(loader.name, loader)
module = importlib.util.module_from_spec(spec)
loader.exec_module(module)


class GuestLocalizationTests(unittest.TestCase):
    def test_browser_quality_and_guest_locale_choose_supported_catalogs(self):
        with patch.dict(os.environ, {'LANG': 'ru_RU.UTF-8', 'LC_ALL': '', 'LC_MESSAGES': ''}):
            self.assertEqual(module.ui_language('fr, ja;q=0.8, en;q=0.4'), 'ja')
            self.assertEqual(module.ui_language('pt-BR, pt;q=0.9'), 'pt_BR')
            self.assertEqual(module.ui_language('pt-br, pt;q=0.9'), 'pt_BR')
            self.assertEqual(module.ui_language('ja;q=0, en;q=bad'), 'ru')
            self.assertEqual(module.ui_text('uiAndroidCalendar'), 'Календарь Android')

    def test_desktop_entry_contains_every_supported_language(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(module.Path, 'home', return_value=Path(directory)):
            module.calendar({'token': 'a' * 32, 'port': 8585})
            entry = (Path(directory) / '.local/share/applications/dextop-calendar.desktop').read_text(encoding='utf-8')
            for locale, texts in module.UI_TEXT.items():
                self.assertIn(f'Name[{locale}]={texts["uiAndroidCalendar"]}\n', entry)

    def test_calendar_html_translates_labels_and_escapes_event_text(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(module.Path, 'home', return_value=Path(directory)):
            module.calendar({'token': 'a' * 32, 'port': 8585})
            folder = Path(directory) / '.local/share/dextop/calendar'
            (folder / 'events.json').write_text(json.dumps([
                {'title': '', 'description': '<script>bad</script>'},
            ]), encoding='utf-8')
            with patch.object(module, 'ThreadingHTTPServer') as server:
                module.serve_calendar()
                handler_class = server.call_args.args[1]
            handler = object.__new__(handler_class)
            handler.path = '/' + 'a' * 32 + '/'
            handler.headers = {'Accept-Language': 'ja'}
            handler.wfile = io.BytesIO()
            handler.send_response = lambda *_: None
            handler.send_header = lambda *_: None
            handler.end_headers = lambda: None
            handler.do_GET()
            body = handler.wfile.getvalue().decode('utf-8')
            self.assertIn('<h1>Androidカレンダー</h1>', body)
            self.assertIn('<h2>無題</h2>', body)
            self.assertIn('&lt;script&gt;', body)
            self.assertNotIn('<script>', body)


if __name__ == '__main__':
    unittest.main()
