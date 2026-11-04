import configparser
import importlib.machinery
import importlib.util
from pathlib import Path
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-toolkit-theme'
loader = importlib.machinery.SourceFileLoader('toolkit_theme', str(SOURCE))
spec = importlib.util.spec_from_loader(loader.name, loader)
module = importlib.util.module_from_spec(spec)
loader.exec_module(module)


def read_ini(path):
    config = configparser.ConfigParser(interpolation=None)
    config.read(path)
    return config


class ToolkitThemeTests(unittest.TestCase):
    def test_switches_both_qt_generations_and_preserves_unrelated_preferences(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for generation in ('qt5ct', 'qt6ct'):
                for palette in ('airy', 'darker'):
                    path = root / f'usr/share/{generation}/colors/{palette}.conf'
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text('[ColorScheme]\n')
            gtk = root / 'root/.config/gtk-3.0/settings.ini'
            gtk.parent.mkdir(parents=True)
            gtk.write_text('[Settings]\ngtk-font-name=User Font 12\ngtk-enable-animations=false\n')
            module.apply(root, 'dark')
            self.assertEqual(read_ini(gtk)['Settings']['gtk-theme-name'], 'Adwaita-dark')
            self.assertEqual(read_ini(gtk)['Settings']['gtk-font-name'], 'User Font 12')
            self.assertEqual(read_ini(gtk)['Settings']['gtk-enable-animations'], 'false')
            for generation in ('qt5ct', 'qt6ct'):
                path = root / f'root/.config/{generation}/{generation}.conf'
                config = read_ini(path)['Appearance']
                self.assertEqual(config['style'], 'Fusion')
                self.assertTrue(config['color_scheme_path'].endswith('/darker.conf'))
            module.apply(root, 'light')
            for generation in ('qt5ct', 'qt6ct'):
                path = root / f'root/.config/{generation}/{generation}.conf'
                self.assertTrue(read_ini(path)['Appearance']['color_scheme_path'].endswith('/airy.conf'))
            before = {p: (p.read_bytes(), p.stat().st_mtime_ns) for p in root.rglob('*') if p.is_file()}
            module.apply(root, 'light')
            self.assertEqual(before, {p: (p.read_bytes(), p.stat().st_mtime_ns) for p in before})

    def test_keeps_user_qt_style_palette_and_custom_home(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'home/user/.config/qt6ct/qt6ct.conf'
            path.parent.mkdir(parents=True)
            path.write_text('[Appearance]\nstyle=Kvantum\ncolor_scheme_path=/home/user/custom.conf\ncustom_palette=true\n[Interface]\ndialog_buttons_have_icons=2\n')
            module.apply(root, 'dark', Path('/home/user'))
            config = read_ini(path)
            self.assertEqual(config['Appearance']['style'], 'Kvantum')
            self.assertEqual(config['Appearance']['color_scheme_path'], '/home/user/custom.conf')
            self.assertEqual(config['Interface']['dialog_buttons_have_icons'], '2')
            self.assertFalse((root / 'root/.config').exists())

    def test_missing_qt_palette_is_not_written_as_a_broken_path(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module.apply(root, 'dark')
            for generation in ('qt5ct', 'qt6ct'):
                config = read_ini(root / f'root/.config/{generation}/{generation}.conf')
                self.assertNotIn('color_scheme_path', config['Appearance'])


if __name__ == '__main__':
    unittest.main()
