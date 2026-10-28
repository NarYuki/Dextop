"""Exercise the profile hook in a real POSIX shell, also on Windows Git Bash."""
from pathlib import Path
import shutil
import subprocess
import unittest

SCRIPT = (Path(__file__).resolve().parents[2] /
          'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-path.sh').read_text()
SHELL = shutil.which('sh') or next((str(path) for path in (
    Path('C:/Program Files/Git/bin/bash.exe'), Path('C:/Program Files/Git/usr/bin/sh.exe')) if path.is_file()), None)


@unittest.skipUnless(SHELL, 'POSIX shell unavailable')
class GuestPathTest(unittest.TestCase):
    def execute(self, before, twice=False):
        body = before + '\n' + SCRIPT + '\nprintf "%s\\n" "$PATH"\n'
        if twice:
            body += SCRIPT + '\nprintf "%s\\n" "$PATH"\n'
        return subprocess.check_output([SHELL, '-c', body], text=True).splitlines()

    def test_keeps_vendor_path_precedence_and_deduplicates(self):
        first, second = self.execute("PATH='/opt/vendor/bin:/usr/games:/usr/bin'", twice=True)
        self.assertEqual(first, second)
        self.assertTrue(first.startswith('/opt/vendor/bin:/usr/games:/usr/bin:'))
        self.assertEqual(first.split(':').count('/usr/games'), 1)
        self.assertIn('/usr/local/games', first.split(':'))
        self.assertIn('/root/.local/bin', first.split(':'))
        self.assertIn('/opt/bin', first.split(':'))

    def test_empty_environment_recovers_standard_commands_without_current_directory(self):
        result, = self.execute('unset PATH')
        self.assertIn('/usr/bin', result.split(':'))
        self.assertIn('/usr/games', result.split(':'))
        self.assertNotIn('', result.split(':'))
        self.assertNotIn('.', result.split(':'))


if __name__ == '__main__':
    unittest.main()
