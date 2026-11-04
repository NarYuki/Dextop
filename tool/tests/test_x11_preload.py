"""Exercise the X property hook with late, locally loaded Xlib symbols."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class X11PreloadTest(unittest.TestCase):
    def test_late_local_and_global_xlib(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            (base / "xlib.c").write_text(r'''
typedef unsigned long U;
U XInternAtom(void *d, const char *s, int only) { return 99; }
int XChangeProperty(void *d, U w, U p, U t, int f, int m, const unsigned char *v, int n) {
    return w == 7 && t == 41 && f == 32 && m == 0 && n == 4 && v ? 73 : -1;
}
int invoke(U property, long first) {
    long values[4] = {first, 179, 255, 0};
    return XChangeProperty((void *)1, 7, property, 41, 32, 0, (unsigned char *)values, 4);
}
''')
            (base / "main.c").write_text(r'''
#include <dlfcn.h>
#include <stdlib.h>
int main(int argc, char **argv) {
    void *h = dlopen(argv[1], RTLD_LAZY | (atoi(argv[2]) ? RTLD_GLOBAL : RTLD_LOCAL));
    if (!h) return 2;
    int (*invoke)(unsigned long, long) = dlsym(h, "invoke");
    if (!invoke || invoke(40, 0) != 73 || invoke(99, 1) != 73) return 3;
    int expected = getenv("BL_FOLLOW_OUTPUT")[0] == '1' ? 1 : 73;
    return invoke(99, 0) == expected ? 0 : 4;
}
''')
            def run(*args, **kwargs):
                subprocess.run(args, check=True, **kwargs)
            run("cc", "-shared", "-fPIC", "-Wl,-soname,libX11.so.6", str(base / "xlib.c"), "-o", str(base / "libX11.so.6"))
            run("cc", "-shared", "-fPIC", str(ROOT / "third_party/droiddeck-preload/xmode.c"), "-ldl", "-o", str(base / "hook.so"))
            run("cc", str(base / "main.c"), "-ldl", "-o", str(base / "probe"))
            for global_scope in ("0", "1"):
                for follow in ("0", "1"):
                    with self.subTest(global_scope=global_scope, follow=follow):
                        env = dict(os.environ, LD_PRELOAD=str(base / "hook.so"), BL_FOLLOW_OUTPUT=follow)
                        run(str(base / "probe"), str(base / "libX11.so.6"), global_scope, env=env)


if __name__ == "__main__":
    unittest.main()
