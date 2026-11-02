"""Verify private executable mappings preserve bytes and fail on short reads."""

from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest


HERE = Path(__file__).resolve().parent.parent


@unittest.skipUnless(platform.system() == "Linux" and shutil.which("cc"), "Linux C compiler required")
class SegmentTests(unittest.TestCase):
    def test_executable_copy_is_private_and_rejects_truncated_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copyfile(HERE / "darling_segments.h", root / "darling_segments.h")
            (root / "test.c").write_text(r'''
#define _GNU_SOURCE
#include <assert.h>
#include <stdlib.h>
#include <sys/mman.h>
#define xmmap mmap
#include "darling_segments.h"
int main(void) {
    long page = sysconf(_SC_PAGESIZE);
    assert(page > 0);
    char filename[] = "/tmp/dextop-image-XXXXXX";
    int fd = mkstemp(filename);
    assert(fd >= 0);
    assert(unlink(filename) == 0);
    char *bytes = calloc(1, page);
    assert(bytes);
    bytes[0] = 42;
    assert(write(fd, bytes, page) == page);
    void *reserved = mmap(0, page, PROT_NONE, MAP_PRIVATE | MAP_ANON, -1, 0);
    assert(reserved != MAP_FAILED);
    const char *environment[] = {"DEXTOP_DARLING_ANON_EXEC=1", 0};
    char *mapped = dextop_map_segment(reserved, page, PROT_READ | PROT_EXEC, fd, 0, environment);
    assert(mapped == reserved && mapped[0] == 42);
    assert(mprotect(mapped, page, PROT_READ | PROT_WRITE) == 0);
    mapped[0] = 43; /* model the loader's private TLS adaptation */
    __builtin___clear_cache(mapped, mapped + page);
    assert(mprotect(mapped, page, PROT_READ | PROT_EXEC) == 0);
    char original;
    assert(pread(fd, &original, 1, 0) == 1 && original == 42);
    assert(mapped[0] == 43);
    assert(munmap(mapped, page) == 0);
    reserved = mmap(0, page * 2, PROT_NONE, MAP_PRIVATE | MAP_ANON, -1, 0);
    assert(reserved != MAP_FAILED);
    errno = 0;
    assert(dextop_map_segment(reserved, page * 2, PROT_READ | PROT_EXEC, fd, 0, environment) == MAP_FAILED);
    assert(errno == ENOEXEC);
    close(fd);
    free(bytes);
    return 0;
}
''', encoding="utf-8")
            subprocess.run(["cc", "-O2", "-UNDEBUG", "-Wall", "-Wextra", str(root / "test.c"),
                            "-o", str(root / "test")], check=True)
            subprocess.run([str(root / "test")], check=True, timeout=10)
