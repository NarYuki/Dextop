import importlib.machinery
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

RESOURCES = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux'


class ArmProtonTest(unittest.TestCase):
    def test_log_history_retains_three_previous_launches_without_mixing_apps(self):
        with tempfile.TemporaryDirectory() as tmp:
            arm = importlib.machinery.SourceFileLoader('arm_log_probe', str(RESOURCES / 'dextop-proton-arm64')).load_module()
            directory = Path(tmp)
            other = directory / 'steam-42.log'
            other.write_text('other app')
            for launch in range(5):
                current = directory / 'steam-391540.log'
                current.write_text(str(launch))
                arm.retain_previous_log({'SteamAppId': '391540', 'PROTON_LOG_DIR': tmp})
                self.assertFalse(current.exists())
            history = list((directory / 'previous').glob('*.log'))
            self.assertEqual(len(history), 3)
            self.assertEqual({p.read_text() for p in history}, {'2', '3', '4'})
            self.assertEqual(other.read_text(), 'other app')

    def setUp(self):
        self.arm = importlib.machinery.SourceFileLoader('arm_proton', str(RESOURCES / 'dextop-proton-arm64')).load_module()
        self.compat = importlib.machinery.SourceFileLoader('arm_compat', str(RESOURCES / 'droiddeck/linuxfs/usr/local/bin/steam-compatibility')).load_module()

    def test_native_environment_preserves_x86_prefix_and_clears_linux_emulator(self):
        with tempfile.TemporaryDirectory() as tmp:
            original = Path(tmp) / '774171'
            original.mkdir()
            sentinel = original / 'x86-save'
            sentinel.write_text('keep')
            source = {'STEAM_COMPAT_DATA_PATH': str(original), 'FEX_ROOTFS': '/old-rootfs',
                      'FEX_APP_CONFIG_LOCATION': '/old-config', 'FEX_MULTIBLOCK': '0',
                      'LD_LIBRARY_PATH': '/opt/steam/steamrtarm64:/opt/gpu:/usr/lib/x86_64-linux-gnu',
                      'LD_PRELOAD': '/x86-overlay.so', 'DISPLAY': ':10'}
            with patch.object(self.arm.Path, 'home', return_value=Path(tmp)):
                env = self.arm.environment(Path('/native-proton'), source)
            self.assertEqual(env['STEAM_COMPAT_DATA_PATH'], str(original) + '-dextop-arm64')
            self.assertEqual(sentinel.read_text(), 'keep')
            self.assertEqual(env['LD_LIBRARY_PATH'], '/opt/gpu')
            self.assertNotIn('FEX_ROOTFS', env)
            self.assertNotIn('FEX_APP_CONFIG_LOCATION', env)
            self.assertEqual(env['FEX_MULTIBLOCK'], '0')
            self.assertNotIn('/x86-overlay.so', env['LD_PRELOAD'])
            self.assertIn('preload/aarch64/', env['LD_PRELOAD'].replace('\\', '/'))
            self.assertEqual(env['DISPLAY'], ':10')
            self.assertEqual(source['FEX_ROOTFS'], '/old-rootfs')

    def test_fresh_install_persists_arm_default_before_first_steam_launch(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.arm.STEAM = Path(tmp) / 'Steam'
            def module(name):
                if name == 'dextop-gaming':
                    return SimpleNamespace(register=lambda: None)
                if name == 'droiddeck-fex':
                    return SimpleNamespace(steam_running=lambda: False)
                return self.compat
            with patch.object(self.arm, 'module', side_effect=module):
                self.arm.register()
                config = self.arm.STEAM / 'config/config.vdf'
                self.assertEqual(self.compat.read_config_mapping(str(config))['0'], self.arm.TOOL)
                self.assertFalse(config.with_name('config.vdf.before-dextop-arm64').exists())
                initial = config.read_bytes()
                self.arm.register()
                self.assertEqual(config.read_bytes(), initial)

    def test_register_migrates_old_default_and_keeps_both_tools_reproducible(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.arm.STEAM = Path(tmp)
            config = Path(tmp) / 'config/config.vdf'
            config.parent.mkdir()
            config.write_text('"InstallConfigStore" { "Software" { "Valve" { "Steam" { "CompatToolMapping" { "0" { "name" "dextop-proton-fex" } "774171" { "name" "dextop-proton-fex" } "42" { "name" "GE-Proton-custom" } } } } } }')
            registered = []
            def module(name):
                if name == 'dextop-gaming':
                    return SimpleNamespace(register=lambda: registered.append('x86'))
                if name == 'droiddeck-fex':
                    return SimpleNamespace(steam_running=lambda: False)
                return self.compat
            with patch.object(self.arm, 'module', side_effect=module):
                self.arm.register()
                before = config.read_text()
                self.arm.register()
            self.assertEqual(registered, ['x86', 'x86'])
            mapping = self.compat.read_config_mapping(str(config))
            self.assertEqual(mapping['0'], self.arm.TOOL)
            self.assertEqual(mapping['774171'], self.arm.TOOL)
            self.assertEqual(mapping['42'], 'GE-Proton-custom')
            self.assertEqual(config.read_text(), before)
            manifest = Path(tmp) / 'compatibilitytools.d' / self.arm.TOOL / 'dextop-proton-arm64'
            self.assertIn('/usr/local/bin/dextop-proton-arm64', manifest.read_text())
            self.assertTrue((RESOURCES / 'dextop-gaming').is_file())


if __name__ == '__main__':
    unittest.main()
