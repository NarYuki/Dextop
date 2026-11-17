"""Regression tests for the 2026-11-12 service audit, using disposable files."""
import errno
import importlib.machinery
import importlib.util
import os
import pwd
from pathlib import Path
import socket
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

SOURCE = Path(os.environ.get('DEXTOP_SERVICE_TEST_RESOURCES', str(
    Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux')))


def load(name, filename):
    loader = importlib.machinery.SourceFileLoader(name, str(SOURCE / filename))
    spec = importlib.util.spec_from_loader(name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


tmpfiles = load('security_tmpfiles', 'dextop-tmpfiles')
manager = load('security_systemd', 'dextop-systemd')
with patch.dict(os.environ, {'DEXTOP_SYSTEMD': str(SOURCE / 'dextop-systemd')}):
    bus = load('security_bus', 'dextop-systemd-bus')


class TmpfilesSecurityTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()

    def test_unsupported_operation_options_fail_before_mutating(self):
        target = self.root / 'sentinel'
        target.write_text('KEEP')
        config = self.root / 'config'
        config.write_text(f'F {target} 0600 - - - CHANGED\n')
        for option in ('--dry-run', '--root=/somewhere', '--root', '--image=/somewhere',
                       '--replace=/somewhere', '--purge', '--graceful', '-E'):
            with self.subTest(option=option):
                result = subprocess.run([sys.executable, str(SOURCE / 'dextop-tmpfiles'),
                    '--create', str(config), option], capture_output=True, text=True, timeout=5)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('Unsupported option', result.stderr)
                self.assertEqual(target.read_text(), 'KEEP')

    def test_create_and_clean_still_work(self):
        target = self.root / 'directory'
        config = self.root / 'config'
        config.write_text(f'd {target} 0700 - - 0\n')
        self.assertEqual(tmpfiles.main(['--create', str(config)]), 0)
        (target / 'old').write_text('delete')
        self.assertEqual(tmpfiles.main(['--clean', str(config)]), 0)
        self.assertTrue(target.is_dir())
        self.assertFalse((target / 'old').exists())

    def test_starting_and_parent_symlinks_are_never_followed(self):
        protected = self.root / 'protected'
        protected.mkdir()
        nested = protected / 'nested'
        nested.mkdir()
        victim = nested / 'sentinel'
        victim.write_text('KEEP')
        alias = self.root / 'alias'
        alias.symlink_to(protected, target_is_directory=True)
        for path in (alias, alias / 'nested'):
            with self.subTest(path=path), self.assertRaises(OSError):
                tmpfiles.clean(str(path), 0, False, [str(protected)])
        self.assertEqual(victim.read_text(), 'KEEP')

    def test_recursive_clean_preserves_excluded_subtree_and_link_targets(self):
        allowed = self.root / 'cache'
        allowed.mkdir()
        protected = allowed / 'keep'
        protected.mkdir()
        sentinel = protected / 'sentinel'
        sentinel.write_text('KEEP')
        outside = self.root / 'outside'
        outside.mkdir()
        other = outside / 'sentinel'
        other.write_text('KEEP')
        (allowed / 'link').symlink_to(outside, target_is_directory=True)
        (allowed / 'delete').write_text('delete')
        tmpfiles.clean(str(allowed), 0, False, [str(protected)])
        self.assertEqual(sentinel.read_text(), 'KEEP')
        self.assertEqual(other.read_text(), 'KEEP')
        self.assertFalse((allowed / 'delete').exists())

    def test_directory_swap_between_stat_and_open_cannot_reach_target(self):
        allowed = self.root / 'cache'
        allowed.mkdir()
        child = allowed / 'child'
        child.mkdir()
        outside = self.root / 'outside'
        outside.mkdir()
        sentinel = outside / 'sentinel'
        sentinel.write_text('KEEP')
        original = tmpfiles.os.open
        swapped = False

        def swap(path, flags, *args, **kwargs):
            nonlocal swapped
            if path == 'child' and kwargs.get('dir_fd') is not None and not swapped:
                child.rename(allowed / 'moved')
                child.symlink_to(outside, target_is_directory=True)
                swapped = True
            return original(path, flags, *args, **kwargs)

        with patch.object(tmpfiles.os, 'open', side_effect=swap):
            tmpfiles.clean(str(allowed), 0, False)
        self.assertTrue(swapped)
        self.assertEqual(sentinel.read_text(), 'KEEP')

    def test_create_remove_and_metadata_do_not_follow_links(self):
        protected = self.root / 'protected'
        protected.mkdir()
        sentinel = protected / 'sentinel'
        sentinel.write_text('KEEP')
        alias = self.root / 'alias'
        alias.symlink_to(protected, target_is_directory=True)
        leaf = self.root / 'leaf'
        leaf.symlink_to(sentinel)
        for rule, operation in ((f'F {alias}/sentinel 0600 - - - CHANGED', '--create'),
                                (f'D {alias} 0700 - - -', '--remove'),
                                (f'F {leaf} 0600 - - - CHANGED', '--create'),
                                (f'z {leaf} 0000 - - -', '--create'),
                                (f'R {alias}/sentinel - - - -', '--remove')):
            with self.subTest(rule=rule):
                config = self.root / 'config'
                config.write_text(rule + '\n')
                self.assertNotEqual(tmpfiles.main([operation, '--exclude-prefix=' + str(protected), str(config)]), 0)
                self.assertEqual(sentinel.read_text(), 'KEEP')

    def test_recursive_remove_and_globs_respect_exclusions(self):
        directory = self.root / 'cache'
        directory.mkdir()
        keep = directory / 'keep'
        keep.mkdir()
        sentinel = keep / 'sentinel'
        sentinel.write_text('KEEP')
        for rule, operation in ((f'w {directory}/*/sentinel - - - - CHANGED', '--create'),
                                (f'R {directory} - - - -', '--remove'),
                                (f'D {directory} - - - -', '--remove'),
                                (f'L+ {directory} - - - - /elsewhere', '--create')):
            config = self.root / 'config'
            config.write_text(rule + '\n')
            tmpfiles.main([operation, '--exclude-prefix=' + str(keep), str(config)])
            self.assertEqual(sentinel.read_text(), 'KEEP')

    def test_regular_create_write_copy_and_remove_remain_functional(self):
        directory = self.root / 'data'
        config = self.root / 'config'
        source = self.root / 'source'
        source.mkdir()
        (source / 'child').write_text('COPY')
        config.write_text(f'd {directory} 0700 - - -\n'
                          f'f {directory}/file 0600 - - - ONE\n'
                          f'w+ {directory}/file - - - - TWO\n'
                          f'C {directory}/copy - - - - {source}\n'
                          f'L {directory}/link - - - - {source}\n')
        self.assertEqual(tmpfiles.main(['--create', str(config)]), 0)
        self.assertEqual((directory / 'file').read_text(), 'ONETWO')
        self.assertEqual((directory / 'copy/child').read_text(), 'COPY')
        self.assertTrue((directory / 'link').is_symlink())
        config.write_text(f'R {directory} - - - -\n')
        self.assertEqual(tmpfiles.main(['--remove', str(config)]), 0)
        self.assertFalse(directory.exists())
        self.assertEqual((source / 'child').read_text(), 'COPY')

    def test_hardlink_cannot_overwrite_excluded_file(self):
        sentinel = self.root / 'sentinel'
        sentinel.write_text('KEEP')
        target = self.root / 'alias'
        try:
            os.link(sentinel, target)
        except OSError as error:
            if error.errno in (errno.EPERM, errno.EOPNOTSUPP, errno.ENOSYS):
                self.skipTest('Hardlink creation unavailable in this test environment')
            raise
        config = self.root / 'config'
        config.write_text(f'F {target} 0600 - - - CHANGED\n')
        self.assertNotEqual(tmpfiles.main(['--create', '--exclude-prefix=' + str(sentinel), str(config)]), 0)
        self.assertEqual(sentinel.read_text(), 'KEEP')

    def test_file_swap_before_open_cannot_truncate_link_target(self):
        sentinel = self.root / 'sentinel'
        sentinel.write_text('KEEP')
        target = self.root / 'target'
        target.write_text('old')
        config = self.root / 'config'
        config.write_text(f'F {target} 0600 - - - CHANGED\n')
        original = tmpfiles.os.open
        def swap(path, flags, *args, **kwargs):
            if path == 'target' and kwargs.get('dir_fd') is not None:
                target.unlink()
                target.symlink_to(sentinel)
            return original(path, flags, *args, **kwargs)
        with patch.object(tmpfiles.os, 'open', side_effect=swap):
            self.assertNotEqual(tmpfiles.main(['--create', str(config)]), 0)
        self.assertEqual(sentinel.read_text(), 'KEEP')


class ServicePolicyTests(unittest.TestCase):
    def test_security_directives_fail_closed_including_empty_capability_set(self):
        for directive in ('ProtectSystem=strict', 'ProtectHome=yes', 'PrivateTmp=yes',
                          'NoNewPrivileges=yes', 'CapabilityBoundingSet=',
                          'RestrictAddressFamilies=AF_UNIX', 'SystemCallFilter=@system-service',
                          'ReadOnlyPaths=/home', 'SupplementaryGroups=wheel', 'LimitAS=1G',
                          'NetworkNamespacePath=/missing', 'IPCNamespacePath=/missing',
                          'ReadOnlyDirectories=/home', 'ReadWriteDirectories=/cache',
                          'InaccessibleDirectories=/home', 'MountFlags=private'):
            with self.subTest(directive=directive), self.assertRaises(ValueError):
                manager.validate_service(manager.parse_unit('[Service]\nExecStart=/bin/true\n' + directive))

    def test_supported_daemon_types_remain_supported(self):
        for kind in ('simple', 'forking', 'notify'):
            manager.validate_service(manager.parse_unit('[Service]\nExecStart=/bin/true\nType=' + kind))

    def test_live_supervisor_refuses_protected_unit_but_runs_ordinary_unit(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            home, state = root / 'home', root / 'state'
            units = home / '.config/systemd/user'
            units.mkdir(parents=True)
            for name, extra in (('blocked', 'NetworkNamespacePath=/missing\n'), ('ordinary', '')):
                (units / (name + '.service')).write_text(
                    '[Service]\nType=oneshot\nRemainAfterExit=yes\n' + extra +
                    f"ExecStart=/bin/sh -c 'echo run > {root / name}'\n")
            environment = dict(os.environ, HOME=str(home), DEXTOP_UNIT_ROOT=str(root),
                DEXTOP_USER_SERVICE_STATE=str(state), DEXTOP_LINUX_USER=pwd.getpwuid(os.getuid()).pw_name)
            process = subprocess.Popen([sys.executable, str(SOURCE / 'dextop-systemd'), 'daemon', '--user'],
                env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            try:
                deadline = time.monotonic() + 5
                while not (state / 'status.json').exists():
                    if process.poll() is not None:
                        self.fail(process.stderr.read().decode())
                    self.assertLess(time.monotonic(), deadline)
                    time.sleep(.05)
                result = manager.rpc(dict(action='start', units=['blocked.service'], user=True), state, timeout=5)
                self.assertFalse(result['ok'])
                self.assertIn('NetworkNamespacePath', result['error'])
                self.assertFalse((root / 'blocked').exists())
                result = manager.rpc(dict(action='start', units=['ordinary.service'], user=True), state, timeout=5)
                self.assertTrue(result['ok'], result)
                deadline = time.monotonic() + 5
                while not (root / 'ordinary').exists():
                    self.assertLess(time.monotonic(), deadline)
                    time.sleep(.05)
                self.assertEqual((root / 'ordinary').read_text(), 'run\n')
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                process.stderr.close()


def big_endian_reply():
    # Independently marshal a D-Bus reply containing a string. Header variants
    # exercise uint32 and signature decoding as well as the string body.
    fields = bytes([5, 1, ord('u'), 0]) + struct.pack('>I', 12)
    fields += bytes([8, 1, ord('g'), 0, 1, ord('s'), 0])
    body = struct.pack('>I', 6) + b'answer\0'
    header = struct.pack('>cBBBIII', b'B', 2, 0, 1, len(body), 7, len(fields))
    return header + fields + b'\0' + body


class DBusFramingTests(unittest.TestCase):
    def connection(self):
        first, second = socket.socketpair()
        self.addCleanup(first.close)
        self.addCleanup(second.close)
        connection = bus.Connection.__new__(bus.Connection)
        connection.socket = first
        connection.buffer = bytearray()
        return connection, second

    def test_big_endian_reply_body(self):
        message, length = bus.Message.decode(big_endian_reply())
        self.assertEqual(message.body, ['answer'])
        self.assertEqual(message.reply, 12)
        self.assertEqual(length, len(big_endian_reply()))

    def test_mixed_endian_stream_keeps_processing_followups(self):
        connection, sender = self.connection()
        little = bus.Message(2, reply=13, signature='s', body=['next']).encode()
        sender.sendall(big_endian_reply() + little)
        messages = connection.read_socket(1)
        self.assertEqual([message.body for message in messages], [['answer'], ['next']])
        self.assertEqual(connection.buffer, b'')

    def test_fragmented_big_endian_frame(self):
        connection, sender = self.connection()
        data = big_endian_reply()
        sender.sendall(data[:20])
        self.assertEqual(connection.read_socket(1), [])
        sender.sendall(data[20:])
        self.assertEqual(connection.read_socket(1)[0].body, ['answer'])

    def test_invalid_and_oversized_headers_close_connection(self):
        for marker, version, length in ((b'X', 1, 0), (b'l', 2, 0),
                                         (b'l', 1, bus.MAX_MESSAGE_BYTES + 1)):
            connection, sender = self.connection()
            sender.sendall(struct.pack('<cBBBIII', marker, 1, 0, version, length, 1, 0))
            with self.assertRaises(OSError):
                connection.read_socket(1)
            self.assertEqual(connection.socket.fileno(), -1)


if __name__ == '__main__':
    unittest.main()
