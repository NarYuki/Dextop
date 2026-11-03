import importlib.util
import json
import os
from pathlib import Path
import platform
import plistlib
import struct
import subprocess
import sys
import tempfile
import time
import unittest


spec = importlib.util.spec_from_file_location("launch_app", Path(__file__).parents[1] / "launch_app.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BundleTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.bundle = Path(self.directory.name) / "日本語 App.app"
        (self.bundle / "Contents/MacOS").mkdir(parents=True)
        self.binary = self.bundle / "Contents/MacOS/program"
        self.binary.write_bytes(struct.pack("<II", 0xFEEDFACF, 0x0100000C))
        self.write_info("program")

    def write_info(self, executable):
        (self.bundle / "Contents/Info.plist").write_bytes(plistlib.dumps({
            "CFBundleExecutable": executable, "CFBundleName": "日本語",
            "CFBundleIdentifier": "org.dextop.own-probe"}, fmt=plistlib.FMT_BINARY))

    def test_binary_plist_and_unicode_path(self):
        info = module.bundle_info(self.bundle)
        self.assertEqual(info["name"], "日本語")
        self.assertEqual(info["cpuTypes"], [0x0100000C])
        self.assertEqual(info["executable"], str(self.binary.resolve()))

    def test_executable_traversal_rejected(self):
        for name in ("../outside", "/bin/sh", "..", "a\\b", "x\x00y"):
            with self.subTest(name=name):
                self.write_info(name)
                with self.assertRaises(ValueError):
                    module.bundle_info(self.bundle)

    def test_non_macho_rejected(self):
        self.binary.write_bytes(b"#!/bin/sh\necho unexpected")
        with self.assertRaises(ValueError):
            module.bundle_info(self.bundle)

    def test_registration_does_not_put_bundle_path_in_exec(self):
        info = module.bundle_info(self.bundle)
        info['name'] = 'Name\nExec=unexpected\n[Desktop Entry]'
        home = Path(self.directory.name) / 'home'
        registration = module.register_bundle(info, Path('/runtime'), Path('/prefix'), home)
        key = registration['id'].removeprefix('dextop-macos-').removesuffix('.desktop')
        record, desktop = module.registration_paths(home, key)
        self.assertEqual(json.loads(record.read_text())['bundle'], str(self.bundle.resolve()))
        lines = desktop.read_text().splitlines()
        self.assertEqual(len([line for line in lines if line.startswith('Exec=')]), 1)
        self.assertNotIn(str(self.bundle), next(line for line in lines if line.startswith('Exec=')))
        self.assertEqual(lines.count('[Desktop Entry]'), 1)
        self.assertIn('X-Dextop-DisplayBackend=x11', lines)

    def test_registration_id_cannot_escape_directory(self):
        for key in ('../outside', 'A' * 24, '0' * 23, '0' * 24 + '/file'):
            with self.assertRaises(ValueError):
                module.registration_paths(Path(self.directory.name), key)

    def test_universal_architectures_and_truncated_table(self):
        table = struct.pack(">II", 0xCAFEBABE, 2)
        table += struct.pack(">IIIII", 0x0100000C, 0, 4096, 100, 12)
        table += struct.pack(">IIIII", 0x01000007, 0, 8192, 100, 12)
        self.binary.write_bytes(table)
        self.assertEqual(module.bundle_info(self.bundle)["cpuTypes"], [0x01000007, 0x0100000C])
        self.binary.write_bytes(table[:-1])
        with self.assertRaises(ValueError):
            module.bundle_info(self.bundle)

    @unittest.skipUnless(__import__("os").name == "posix", "POSIX file locks and symlinks")
    def test_symlink_outside_bundle_rejected(self):
        outside = Path(self.directory.name) / "outside"
        self.binary.rename(outside)
        self.binary.symlink_to(outside)
        with self.assertRaises(ValueError):
            module.bundle_info(self.bundle)

    @unittest.skipUnless(__import__("os").name == "posix", "POSIX file locks")
    def test_second_prefix_owner_rejected(self):
        prefix = Path(self.directory.name) / "prefix"
        with module.prefix_lock(prefix):
            with self.assertRaises(RuntimeError):
                with module.prefix_lock(prefix):
                    self.fail("Second owner acquired the runtime prefix")
        with module.prefix_lock(prefix):
            pass


@unittest.skipUnless(os.name == "posix", "Linux supervisor lifecycle")
class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.runtime = self.root / "runtime"
        for component in ("bin/darling", "bin/darlingserver", "libexec/darling/bin/mldr",
                          "libexec/darling/usr/lib/dyld", "libexec/darling/sbin/launchd"):
            path = self.runtime / component
            path.parent.mkdir(parents=True, exist_ok=True)
            path.touch()
        launcher = self.runtime / "bin/darling"
        launcher.write_text("#!" + sys.executable + "\n" + '''
import json, os, signal, sys, time
with open(os.environ['OWN_LIFECYCLE_LOG'], 'a') as stream:
    stream.write(json.dumps(sys.argv[1:]) + '\\n')
if sys.argv[1] == 'shutdown':
    if os.environ.get('OWN_APP_WAIT'):
        with open(os.environ['OWN_LIFECYCLE_LOG'] + '.pid') as stream:
            os.kill(int(stream.read()), signal.SIGTERM)
    sys.exit(int(os.environ.get('OWN_SHUTDOWN_CODE', '0')))
if os.environ.get('OWN_APP_WAIT'):
    with open(os.environ['OWN_LIFECYCLE_LOG'] + '.pid', 'w') as stream:
        stream.write(str(os.getpid()))
    time.sleep(30)
sys.exit(int(os.environ.get('OWN_APP_CODE', '0')))
''')
        launcher.chmod(0o755)
        binary = self.root / "App with spaces"
        binary.write_bytes(struct.pack("<II", 0xFEEDFACF, module.CPU_TYPES[platform.machine()]))
        self.log = self.root / "lifecycle.jsonl"
        self.env = dict(os.environ, OWN_LIFECYCLE_LOG=str(self.log))
        self.command = [sys.executable, str(Path(module.__file__)), '--runtime', str(self.runtime),
                        '--prefix', str(self.root / 'prefix'), '--executable', str(binary),
                        '--', 'one argument', "quote'and;metacharacters"]

    def events(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_app_failure_still_shuts_down_and_preserves_arguments(self):
        result = subprocess.run(self.command, env=dict(self.env, OWN_APP_CODE='7'), capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 7)
        events = self.events()
        self.assertEqual(events[0][-2:], ['one argument', "quote'and;metacharacters"])
        self.assertEqual(events[-1], ['shutdown'])

    def test_shutdown_failure_is_not_success(self):
        result = subprocess.run(self.command, env=dict(self.env, OWN_SHUTDOWN_CODE='9'), capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 1)
        self.assertIn(b'shutdown failed', result.stderr)

    def test_registered_launch_and_removal_preserve_bundle_and_prefix(self):
        bundle = self.root / '日本語 App.app'
        binary = bundle / 'Contents/MacOS/program'
        binary.parent.mkdir(parents=True)
        binary.write_bytes(struct.pack('<II', 0xFEEDFACF, module.CPU_TYPES[platform.machine()]))
        (bundle / 'Contents/Info.plist').write_bytes(plistlib.dumps({'CFBundleExecutable': 'program'}))
        env = dict(self.env, HOME=str(self.root))
        register = [sys.executable, module.__file__, '--register', '--bundle', str(bundle),
                    '--runtime', str(self.runtime), '--prefix', str(self.root / 'prefix')]
        result = subprocess.run(register, env=env, capture_output=True, text=True, check=True, timeout=10)
        key = json.loads(result.stdout)['id'].removeprefix('dextop-macos-').removesuffix('.desktop')
        self.assertFalse(self.log.exists())
        result = subprocess.run([sys.executable, module.__file__, '--registered', key], env=env,
                                capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.events()[-1], ['shutdown'])
        data = self.root / 'prefix/user-file'
        data.parent.mkdir(exist_ok=True)
        data.write_text('retain')
        subprocess.run([sys.executable, module.__file__, '--unregister', key], env=env, check=True, timeout=10)
        self.assertTrue(binary.is_file())
        self.assertEqual(data.read_text(), 'retain')
        record, desktop = module.registration_paths(self.root, key)
        self.assertFalse(record.exists())
        self.assertFalse(desktop.exists())

    def test_sigterm_requests_scoped_shutdown(self):
        app = subprocess.Popen(self.command, env=dict(self.env, OWN_APP_WAIT='1'),
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            deadline = time.monotonic() + 5
            while not Path(str(self.log) + '.pid').exists():
                if time.monotonic() > deadline:
                    self.fail('Supervisor did not start its guest')
                time.sleep(.02)
            app.terminate()
            self.assertEqual(app.wait(timeout=5), 143)
            self.assertEqual(self.events()[-1], ['shutdown'])
        finally:
            # Reap this fixture's exact child; never enumerate real guests.
            app.kill()
            app.wait(timeout=5)


if __name__ == "__main__":
    unittest.main()
