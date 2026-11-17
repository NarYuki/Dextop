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
    def test_sudo_always_runs_the_real_sudo_under_fakeroot(self):
        from unittest.mock import Mock
        execute = Mock(side_effect=SystemExit)
        with patch.object(sudo.os, 'execve', execute), patch.object(sudo.os, 'geteuid', return_value=2000, create=True), \
             patch.object(sudo.os, 'getuid', return_value=2000, create=True), patch.object(sudo.os, 'getgid', return_value=2000, create=True), \
             patch.object(sudo.shutil, 'which', side_effect=lambda name, path=None: '/usr/bin/' + name if name == 'fakeroot-tcp' else None):
            with self.assertRaises(SystemExit):
                sudo.main(['-n', 'id'])
        program, arguments, environment = execute.call_args.args
        self.assertEqual('/usr/bin/fakeroot-tcp', program)
        self.assertEqual(['/usr/bin/fakeroot-tcp', '--', '/usr/bin/sudo', '-n', 'id'], arguments)
        self.assertEqual('0', environment['FAKEROOTEUID'])

    def test_sudo_has_no_password_check_of_its_own(self):
        self.assertFalse(hasattr(sudo, 'authenticate'))
        source = (RESOURCE / 'dextop-sudo').read_text()
        for unwanted in ('/etc/shadow', 'getpass', 'crypt'):
            self.assertNotIn(unwanted, source)

    def test_setup_sends_only_a_hash_to_chpasswd(self):
        from unittest.mock import Mock
        password_hash = '$6$rounds=100000$' + 'a' * 16 + '$' + 'b' * 86
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'root').mkdir()
            real_path = Path
            def guest_path(value):
                return root / str(value).lstrip('/')
            def run(arguments, **kwargs):
                if arguments[0] == 'useradd':
                    (root / 'home/probeuser').mkdir(parents=True)
            commands = Mock(side_effect=run)
            fake_grp = SimpleNamespace(getgrnam=Mock(side_effect=KeyError))
            with patch.object(setup, 'Path', side_effect=guest_path), \
                 patch.object(setup.pwd, 'getpwnam', side_effect=KeyError, create=True), \
                 patch.dict(sys.modules, {'grp': fake_grp}), \
                 patch.object(setup.subprocess, 'run', commands), \
                 patch.object(setup.os, 'chown', create=True), \
                 patch.object(setup.shutil, 'copy2', side_effect=lambda source, target: real_path(target).write_text('probe')):
                setup.setup('probeuser', 2000, password_hash)
            requests = [call for call in commands.call_args_list if call.args[0][0] == 'chpasswd']
            self.assertEqual(len(requests), 1)
            self.assertEqual(requests[0].args[0], ['chpasswd', '-e'])
            self.assertEqual(requests[0].kwargs['input'], 'probeuser:' + password_hash + '\n')

    def test_invalid_setup_never_runs_account_commands(self):
        with patch.object(setup.subprocess, 'run') as run:
            for name, uid, password in [('root', 2000, 'password'), ('a;id', 2000, 'password'), ('yuuki', 0, 'password'), ('yuuki', 2000, 'password\n'), ('yuuki', 2000, 'plaintext password')]:
                with self.assertRaises(ValueError):
                    setup.setup(name, uid, password)
            run.assert_not_called()


if __name__ == '__main__':
    unittest.main()
