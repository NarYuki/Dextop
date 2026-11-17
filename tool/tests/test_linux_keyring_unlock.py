import importlib.machinery
import importlib.util
from pathlib import Path
import subprocess
import os
import struct
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-keyring-unlock'
loader = importlib.machinery.SourceFileLoader('keyring_unlock', str(SOURCE))
spec = importlib.util.spec_from_loader(loader.name, loader)
module = importlib.util.module_from_spec(spec)
loader.exec_module(module)


class UnlockTests(unittest.TestCase):
    def exercise(self, locked, alias=b'/', pam=0):
        calls = []
        states = iter(locked)
        last = locked[-1]
        def run(argv, data, timeout):
            calls.append((argv, data, timeout))
            if argv[-1:] == ['string:Locked']:
                state = next(states, last)
                return subprocess.CompletedProcess(argv, 1 if state is None else 0,
                    b'' if state is None else b'variant boolean ' + (b'true' if state else b'false'))
            if module.LOGIN in argv:
                return subprocess.CompletedProcess(argv, pam, b'')
            return subprocess.CompletedProcess(argv, 0, alias)
        with patch.object(module.time, 'sleep'):
            result = module.unlock(b'synthetic-secret', pam_available=True, run=run, control=lambda *args: None)
        return result, calls

    def test_optional_pam_success_without_collection_still_unlocks_directly(self):
        result, calls = self.exercise([None, False])
        self.assertTrue(result)
        self.assertTrue(any('--unlock' in a for a, _, _ in calls))
        self.assertTrue(any('org.freedesktop.Secret.Service.SetAlias' in a for a, _, _ in calls))

    def test_pam_success_with_locked_collection_does_not_hide_failure(self):
        result, calls = self.exercise([True, False])
        self.assertTrue(result)
        self.assertTrue(any('--unlock' in a for a, _, _ in calls))

    def test_existing_unlocked_collection_does_not_replace_the_daemon(self):
        result, calls = self.exercise([False, False], alias=b'/org/freedesktop/secrets/collection/custom')
        self.assertTrue(result)
        self.assertFalse(any('--unlock' in a or '--replace' in a for a, _, _ in calls))
        self.assertFalse(any('org.freedesktop.Secret.Service.SetAlias' in a for a, _, _ in calls))

    def test_wrong_password_never_replaces_daemon_or_changes_alias(self):
        result, calls = self.exercise([True] * 5, pam=2)
        self.assertFalse(result)
        self.assertFalse(any('--replace' in a for a, _, _ in calls))
        self.assertFalse(any('org.freedesktop.Secret.Service.SetAlias' in a for a, _, _ in calls))
        for argv, data, timeout in calls:
            self.assertNotIn('synthetic-secret', ' '.join(argv))
            self.assertLessEqual(timeout, 4.5)

    def test_pam_receives_one_line_and_direct_unlock_receives_the_exact_secret(self):
        result, calls = self.exercise([None, False], pam=2)
        self.assertTrue(result)
        self.assertEqual(next(d for a, d, _ in calls if module.LOGIN in a), b'synthetic-secret\n')
        self.assertEqual(next(d for a, d, _ in calls if '--unlock' in a), b'synthetic-secret')

    def test_empty_secret_never_invokes_a_prompt(self):
        def fail(*args): self.fail('No command should run')
        self.assertFalse(module.unlock(b'', run=fail))


class ControlProtocolTests(unittest.TestCase):
    def exchange(self, reply, uid=None):
        from unittest.mock import MagicMock
        peer = MagicMock()
        peer.__enter__.return_value = peer
        peer.getsockopt.return_value = struct.pack('3i', 123, os.geteuid() if uid is None else uid, 456)
        peer.recv.side_effect = reply
        with patch.object(module.socket, 'socket', return_value=peer), \
             patch.object(module.socket, 'SO_PEERCRED', 17, create=True), \
             patch.object(module.Path, 'is_symlink', return_value=False), \
             patch.object(module, 'kernel_uid', return_value=os.geteuid()), \
             patch.dict(os.environ, {'GNOME_KEYRING_CONTROL': '/synthetic/keyring'}):
            result = module.control_unlock(b'test-only-secret')
        return result, peer

    def test_credentials_precede_the_password_and_fragmented_reply_is_accepted(self):
        result, peer = self.exchange([b'\x00\x00', b'\x00\x08\x00', b'\x00\x00\x00'])
        self.assertTrue(result)
        peer.sendall.assert_called_once_with(b'\0' + struct.pack('>III', 28, 1, 16) + b'test-only-secret')
        names = [call[0] for call in peer.method_calls]
        self.assertLess(names.index('getsockopt'), names.index('sendall'))

    def test_different_uid_never_receives_a_password(self):
        result, peer = self.exchange([], uid=os.geteuid()+1)
        self.assertFalse(result)
        peer.sendall.assert_not_called()

    def test_wrong_password_is_a_denial_not_an_absent_daemon(self):
        result, _ = self.exchange([struct.pack('>II', 8, 1)])
        self.assertIs(result, False)

    def test_truncated_or_invalid_reply_is_not_success(self):
        self.assertIs(self.exchange([b'\0', b''])[0], False)
        self.assertIs(self.exchange([struct.pack('>II', 1024, 0)])[0], False)


if __name__ == '__main__':
    unittest.main()
