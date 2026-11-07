import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-npm'


class NpmCompatibilityTests(unittest.TestCase):
    def invoke(self, arguments):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            real = root / 'real-npm'
            real.write_text('#!/bin/sh\nprintf \'%s\\n\' "${DEXTOP_HARDLINK_COPY-unset}" "$@"\n')
            real.chmod(0o755)
            wrapper = root / 'npm'
            wrapper.write_text(SOURCE.read_text().replace('exec /usr/bin/npm', 'exec "' + str(real) + '"'))
            env = dict(os.environ)
            env.pop('DEXTOP_HARDLINK_COPY', None)
            result = subprocess.run(['sh', str(wrapper), *arguments], env=env, check=True, text=True, capture_output=True)
            return result.stdout.splitlines()

    def test_install_commands_get_copy_fallback_and_preserve_arguments(self):
        for args in [['install', './package with spaces'], ['ci'], ['--prefix', '/tmp/my project', 'install'],
                     ['--cache=/tmp/cache', 'add', 'is-number'], ['cache', 'verify']]:
            with self.subTest(args=args):
                self.assertEqual(self.invoke(args), ['1', *args])

    def test_run_and_information_commands_do_not_change_application_semantics(self):
        for args in [['run', 'install'], ['exec', 'program'], ['--version'], ['help', 'install'], ['test']]:
            with self.subTest(args=args):
                self.assertEqual(self.invoke(args), ['unset', *args])


if __name__ == '__main__':
    unittest.main()
