"""Exercise the actual injected diagnostic against a pinned Linux child."""

from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import patch_runtime


@unittest.skipUnless(platform.system() == "Linux" and shutil.which("cc"), "Linux C compiler required")
class ShutdownDiagnosticsTests(unittest.TestCase):
    def test_reports_pinned_child_without_stopping_it(self):
        with patch.object(patch_runtime, "replace_once") as replace:
            patch_runtime.patch_shutdown_diagnostics(Path("unused"))
        injected = replace.call_args_list[0].args[2]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "probe.c"
            source.write_text(r'''
#define _GNU_SOURCE
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <sys/wait.h>
''' + injected + r'''
int main(void) {
 pid_t child = fork();
 assert(child >= 0);
 if (!child) { for (;;) pause(); }
 int handle = syscall(SYS_pidfd_open, child, 0);
 assert(handle >= 0);
 shutdownReportHandle(handle);
 assert(syscall(SYS_pidfd_send_signal, handle, 0, NULL, 0) == 0);
 assert(syscall(SYS_pidfd_send_signal, handle, SIGKILL, NULL, 0) == 0);
 assert(waitpid(child, NULL, 0) == child);
 shutdownReportHandle(handle); /* Exited handles and invalid handles are safe. */
 close(handle);
 shutdownReportHandle(-1);
 return 0;
}
''')
            binary = root / "probe"
            subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", str(source), "-o", str(binary)], check=True)
            result = subprocess.run([str(binary)], capture_output=True, text=True, timeout=10, check=True)
            self.assertIn("Shutdown pinned process", result.stderr)
            self.assertIn("State:", result.stderr)
            self.assertIn("TracerPid:", result.stderr)
            self.assertNotIn("environ", result.stderr)


if __name__ == "__main__":
    unittest.main()
