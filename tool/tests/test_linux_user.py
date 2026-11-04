import importlib.machinery
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
import sys
from unittest.mock import patch


RESOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux'


def load(name):
    loader = importlib.machinery.SourceFileLoader(name.replace('-', '_'), str(RESOURCE / name))
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


if sys.platform == 'win32':
    # Windows has no pwd; these tests exercise validation/authentication and mock
    # account lookup separately. Linux integration tests use the real module.
    with patch.dict(sys.modules, {'pwd': SimpleNamespace()}):
        sudo = load('dextop-sudo')
        setup = load('dextop-user-setup')
else:
    sudo = load('dextop-sudo')
    setup = load('dextop-user-setup')


class LinuxUserTests(unittest.TestCase):
    def test_sudo_authentication_checks_hash_and_rejects_locked_accounts(self):
        crypt = SimpleNamespace(crypt=SimpleNamespace())
        with tempfile.TemporaryDirectory() as directory:
            shadow = Path(directory) / 'shadow'
            shadow.write_text('yuuki:$6$salt$hash:20000:0:99999:7:::\n')
            from unittest.mock import Mock
            crypt.crypt = Mock(return_value=b'$6$salt$hash')
            with patch.object(sudo.ctypes, 'CDLL', return_value=crypt), patch.object(sudo.ctypes.util, 'find_library', return_value='libcrypt.so.1'):
                self.assertTrue(sudo.authenticate('yuuki', 'my password', shadow))
                crypt.crypt.assert_called_once_with(b'my password', b'$6$salt$hash')
                crypt.crypt.return_value = b'$6$salt$wrong'
                self.assertFalse(sudo.authenticate('yuuki', 'wrong', shadow))
                self.assertFalse(sudo.authenticate('unknown', 'my password', shadow))
                shadow.write_text('yuuki:!locked:20000:0:99999:7:::\n')
                self.assertFalse(sudo.authenticate('yuuki', 'my password', shadow))

    def test_sudo_rejects_unsupported_user_and_missing_password_before_execution(self):
        with patch.object(sudo.os, 'execv') as execute:
            with self.assertRaises(ValueError):
                sudo.main(['-u', 'nobody', 'id'])
            with patch.object(sudo.Path, 'read_text', return_value='yuuki\n'), patch.object(sudo.os, 'geteuid', return_value=2000, create=True):
                with self.assertRaisesRegex(ValueError, 'password is required'):
                    sudo.main(['-n', 'id'])
            execute.assert_not_called()

    def test_invalid_setup_never_runs_account_commands(self):
        with patch.object(setup.subprocess, 'run') as run:
            for name, uid, password in [('root', 2000, 'password'), ('a;id', 2000, 'password'), ('yuuki', 0, 'password'), ('yuuki', 2000, 'password\n')]:
                with self.assertRaises(ValueError):
                    setup.setup(name, uid, password)
            run.assert_not_called()


if __name__ == '__main__':
    unittest.main()
