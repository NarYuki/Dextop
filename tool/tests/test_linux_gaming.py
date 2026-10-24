import importlib.machinery
import io
import os
import signal
import sys
import tarfile
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

RESOURCES = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux'


def load(name, path):
    return importlib.machinery.SourceFileLoader(name, str(path)).load_module()


class GamingTest(unittest.TestCase):
    def setUp(self):
        self.gaming = load('dextop_gaming', RESOURCES / 'dextop-gaming')
        self.compat = load('upstream_steam_compat', RESOURCES / 'droiddeck/linuxfs/usr/local/bin/steam-compatibility')

    def test_native_runtime_chain_preserves_game_arguments(self):
        args = ['/steam/FEX-Emu/fex-compat-tool', 'waitforexitandrun', '--',
                '/steam/SteamLinuxRuntime_soldier/_v2-entry-point', '--verb=waitforexitandrun', '--',
                '/steam/SteamLinuxRuntime/scout-on-soldier-entry-point-v2', '--',
                '/games/a game/runner', '--fullscreen', 'argument with spaces']
        self.assertEqual(self.gaming.linux_command(args), args[-3:])
        self.assertEqual(args[1], 'waitforexitandrun')
        with self.assertRaises(RuntimeError):
            self.gaming.linux_command([args[0], 'unknown', '--', '/game'])

    def test_native_environment_filters_host_architecture_and_keeps_scout(self):
        with tempfile.TemporaryDirectory() as tmp:
            scout = Path(tmp) / 'steamapps/common/SteamLinuxRuntime/steam-runtime/lib/i386-linux-gnu'
            scout.mkdir(parents=True)
            dd = SimpleNamespace(guest_environment=lambda f, e: e, elf_arch=lambda p: 'i386',
                                 libraries=lambda: [tmp])
            env = self.gaming.linux_environment(dd, {'rootfs': '/runtime'},
                {'LD_LIBRARY_PATH': '/steam/linuxarm64:/game/lib:/host/aarch64-linux-gnu'}, '/game/runner')
            self.assertEqual(env['LD_LIBRARY_PATH'], ':'.join(
                ['/game/lib', '/lib/i386-linux-gnu', '/usr/lib/i386-linux-gnu', str(scout)]))
            self.assertNotIn('/runtime/', env['LD_LIBRARY_PATH'])
            self.assertEqual(env['PROOT_FP_OFF'], '1')

    def test_fex_disables_guest_fast_path_without_mutating_session_environment(self):
        source = {'PROOT_FP_KEY': 'session-mount-key', 'PROOT_FP_ROOT': '/container'}
        dd = SimpleNamespace(guest_environment=lambda f, e: e)
        env = self.gaming.fex_environment(dd, {}, source)
        self.assertEqual(env['PROOT_FP_OFF'], '1')
        self.assertEqual(env['PROOT_FP_KEY'], source['PROOT_FP_KEY'])
        self.assertNotIn('PROOT_FP_OFF', source)

    def test_failed_preflight_does_not_complete_setup(self):
        with tempfile.TemporaryDirectory() as tmp:
            marker = Path(tmp) / 'ready'
            with patch.object(self.gaming, 'current_fex'), patch.object(self.gaming, 'install_runtime'), \
                 patch.object(self.gaming, 'prepare', return_value=(object(), {})), \
                 patch.object(self.gaming, 'check_runtime', side_effect=RuntimeError('broken i386')), \
                 patch.object(self.gaming, 'RUNTIME_MARKER', marker):
                with self.assertRaisesRegex(RuntimeError, 'broken i386'):
                    self.gaming.main(['install'])
            self.assertFalse(marker.exists())

    def test_fex_audio_uses_socket_transport_and_keeps_the_selected_server(self):
        dd = SimpleNamespace(guest_environment=lambda f, e: e)
        source = {'PULSE_SERVER': 'unix:/tmp/dextop-pulse/native'}
        env = self.gaming.fex_environment(dd, {}, source)
        self.assertEqual(env['PULSE_SERVER'], source['PULSE_SERVER'])
        self.assertEqual(env['PULSE_CLIENTCONFIG'], self.gaming.BASE + '/pulse-client.conf')
        config = (RESOURCES / 'gaming/pulse-client.conf').read_text()
        self.assertIn('enable-shm = no', config)
        self.assertIn('enable-memfd = no', config)
        self.assertNotIn('PULSE_CLIENTCONFIG', source)

    def test_debian_payload_extraction_does_not_require_dpkg(self):
        with tempfile.TemporaryDirectory() as tmp:
            payload = io.BytesIO()
            with tarfile.open(fileobj=payload, mode='w:xz') as tar:
                entry = tarfile.TarInfo('usr/bin/FEX')
                entry.size = 3
                tar.addfile(entry, io.BytesIO(b'elf'))
            data = payload.getvalue()
            header = f'{"data.tar.xz/":<16}{0:<12}{0:<6}{0:<6}{"100644":<8}{len(data):<10}`\n'.encode('ascii')
            package = Path(tmp) / 'fex.deb'
            package.write_bytes(b'!<arch>\n' + header + data + (b'\n' if len(data) % 2 else b''))
            self.gaming.extract_deb(package, Path(tmp) / 'out')
            self.assertEqual((Path(tmp) / 'out/usr/bin/FEX').read_bytes(), b'elf')

    def test_native_exit_status_and_output_are_preserved_in_diagnostic_log(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(Path, 'home', return_value=Path(tmp)), \
                 patch.object(signal, 'SIGHUP', getattr(signal, 'SIGHUP', signal.SIGTERM), create=True), \
                 patch.object(sys, 'stderr', io.StringIO()):
                status = self.gaming.run_linux({'bin': sys.executable}, '-c',
                    ['import sys; print("loader diagnostic"); sys.exit(7)'],
                    dict(os.environ, STEAM_APP_USER='do-not-log'))
            self.assertEqual(status, 7)
            log = next(Path(tmp).rglob('native-*.log')).read_text()
            self.assertIn('loader diagnostic', log)
            self.assertIn('Exit status: 7', log)
            self.assertNotIn('do-not-log', log)

    def test_register_preserves_custom_tools_and_other_settings(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.gaming.STEAM = tmp
            config = Path(tmp) / 'config/config.vdf'
            config.parent.mkdir()
            original = '"InstallConfigStore" { "Software" { "Valve" { "Steam" { "Unrelated" "keep" "CompatToolMapping" { "0" { "name" "proton_experimental" } "774171" { "name" "proton_experimental" } "42" { "name" "custom-ge" } } } } } }'
            config.write_text(original)
            def module(name):
                return SimpleNamespace(steam_running=lambda: False) if name == 'droiddeck-fex' else self.compat
            with patch.object(self.gaming, 'module', side_effect=module):
                self.gaming.register()
                first = config.read_text()
                self.gaming.register()
            self.assertEqual(first, config.read_text())
            mapping = self.compat.read_config_mapping(str(config))
            self.assertEqual(mapping, {'0': self.gaming.TOOL, '774171': self.gaming.TOOL, '42': 'custom-ge'})
            self.assertIn('"keep"', first)
            self.assertEqual(Path(str(config) + '.before-dextop-gaming').read_text(), original)
            # A later manual Proton selection belongs to the user.
            config.write_text(original)
            with patch.object(self.gaming, 'module', side_effect=module):
                self.gaming.register()
            self.assertEqual(config.read_text(), original)

    def test_running_client_config_is_left_alone(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.gaming.STEAM = tmp
            config = Path(tmp) / 'config/config.vdf'
            config.parent.mkdir()
            config.write_text('running client config')
            with patch.object(self.gaming, 'module', return_value=SimpleNamespace(steam_running=lambda: True)):
                self.gaming.register()
            self.assertEqual(config.read_text(), 'running client config')

    def test_runtime_4_uses_newest_complete_platform(self):
        with tempfile.TemporaryDirectory() as tmp:
            common = Path(tmp) / 'steamapps/common/SteamLinuxRuntime_4'
            for name in ('steamrt4_platform_4.0.20260901.1', 'steamrt4_platform_4.0.20261001.2'):
                platform = common / name
                platform.mkdir(parents=True)
                (platform / 'usr-mtree.txt.gz').touch()
            dd = SimpleNamespace(libraries=lambda: [tmp], platform_version=lambda p: Path(p).name,
                                 original_runtime=lambda: 'old-runtime')
            self.assertEqual(Path(self.gaming.runtime(dd)).name, 'steamrt4_platform_4.0.20261001.2')


if __name__ == '__main__':
    unittest.main()
