import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run.sh"


@unittest.skipIf(os.name == "nt", "Run shell integration tests on the Linux build host or device")
class RunTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="dextop-darling-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        tools = self.root / "tools"
        tools.mkdir()
        uname = tools / "uname"
        uname.write_text("#!/bin/sh\nprintf 'aarch64\\n'\n")
        uname.chmod(0o755)
        self.runtime = self.root / "runtime"
        self.env = dict(os.environ, HOME=str(self.root / "home"),
                        PATH=str(tools) + ":" + os.environ["PATH"],
                        DEXTOP_DARLING_ROOT=str(self.runtime))

    def invoke(self, *args):
        return subprocess.run(["sh", str(SCRIPT), *args], env=self.env, capture_output=True, text=True)

    def populate(self):
        for name in ("bin/darling", "bin/darlingserver", "libexec/darling/bin/mldr",
                     "libexec/darling/usr/lib/dyld", "libexec/darling/sbin/launchd"):
            path = self.runtime / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("#!/bin/sh\nexit 1\n")
            path.chmod(0o755)

    def test_incomplete_payload_fails_without_creating_a_prefix(self):
        result = self.invoke("--check")
        self.assertEqual(result.returncode, 78)
        self.assertIn("Missing Darling component: bin/darlingserver", result.stderr)
        self.assertFalse((self.root / "home").exists())

    def test_nonroot_launch_preserves_argument_boundaries_and_owns_its_prefix(self):
        self.populate()
        # A shell recorder tests the wrapper contract, not Mach-O execution.
        (self.runtime / "bin/darling").write_text(
            '#!/bin/sh\nprintf "%s\\n" "$DARLING_NONROOT" "$DARLING_INSTALL_PREFIX" '
            '"$DPREFIX" "${DARLING_PROFILE-unset}" "$@"\n')
        self.env["DARLING_PROFILE"] = "inherited-profile"
        self.env["DPREFIX"] = "/unrelated-existing-prefix"
        result = self.invoke("exec", "/Applications/Space App", "argument with spaces")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(), [
            "1", str(self.runtime), str(self.root / "home/.local/share/dextop/macos/default"),
            "unset", "exec", "/Applications/Space App", "argument with spaces",
        ])

    def test_relative_runtime_is_rejected(self):
        self.env["DEXTOP_DARLING_ROOT"] = "relative/path"
        self.assertEqual(self.invoke("--check").returncode, 64)


if __name__ == "__main__":
    unittest.main()
