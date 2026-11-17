#!/usr/bin/env python3
"""Benign open-wrapper regressions. Run inside the guest, with isolated TMPDIR.

Uses temporary fixtures only. Does not exercise syscall bypasses or exploits.
"""
import concurrent.futures
import errno
import json
import os
from pathlib import Path
import resource
import subprocess
import tempfile


def main():
    checks = []
    def check(name, action):
        try:
            action()
            checks.append({'name': name, 'status': 'pass'})
        except Exception as error:
            checks.append({'name': name, 'status': 'fail', 'error': repr(error)})
        print(json.dumps(checks[-1]), flush=True)

    with tempfile.TemporaryDirectory(prefix='landlock-open-') as directory:
        base = Path(directory)
        (base / 'file').write_bytes(b'landlock-ok')
        (base / 'absolute').symlink_to(base / 'file')
        (base / 'relative').symlink_to('file')
        (base / 'sub').mkdir()

        def read(path, flags=os.O_RDONLY, dir_fd=None):
            fd = os.open(path, flags, dir_fd=dir_fd)
            try:
                assert os.read(fd, 100) == b'landlock-ok', path
            finally:
                os.close(fd)

        for name in ['file', 'absolute', 'relative', './file', 'sub/../file']:
            check('read:' + name, lambda name=name: read(str(base) + '/' + name))
        check('duplicate slash', lambda: read(str(base) + '//file'))

        def nofollow():
            try:
                fd = os.open(base / 'absolute', os.O_RDONLY | os.O_NOFOLLOW)
            except OSError as error:
                assert error.errno == errno.ELOOP
            else:
                os.close(fd)
                raise AssertionError('O_NOFOLLOW unexpectedly followed symlink')
        check('O_NOFOLLOW final symlink', nofollow)

        def missing():
            try:
                fd = os.open(base / 'missing', os.O_RDONLY)
            except OSError as error:
                assert error.errno == errno.ENOENT
            else:
                os.close(fd)
                raise AssertionError('missing file opened')
        check('ENOENT', missing)

        def creation():
            fd = os.open(base / 'created', os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            os.write(fd, b'created')
            os.close(fd)
            assert (base / 'created').read_bytes() == b'created'
            try:
                fd = os.open(base / 'created', os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            except OSError as error:
                assert error.errno == errno.EEXIST
            else:
                os.close(fd)
                raise AssertionError('O_EXCL ignored')
        check('create and O_EXCL', creation)

        def dirfd():
            fd = os.open(base, os.O_RDONLY | os.O_DIRECTORY)
            try:
                read('file', dir_fd=fd)
            finally:
                os.close(fd)
        check('relative dirfd', dirfd)

        def pathfd():
            fd = os.open(base / 'file', os.O_PATH)
            try:
                assert os.fstat(fd).st_size == 11
            finally:
                os.close(fd)
        check('O_PATH', pathfd)

        def threaded():
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
                list(pool.map(lambda _: read(base / 'file'), range(1000)))
        check('1000 reads on four threads', threaded)

        def forked():
            pid = os.fork()
            if pid == 0:
                try:
                    read(base / 'file')
                except Exception:
                    os._exit(1)
                os._exit(0)
            assert os.waitpid(pid, 0)[1] == 0
        check('fork inheritance', forked)

        def execute():
            child = subprocess.run(['/usr/bin/cat', str(base / 'file')], capture_output=True, timeout=10)
            assert child.returncode == 0 and child.stdout == b'landlock-ok', child.stderr
        check('exec inheritance', execute)

        def descriptors():
            before = len(os.listdir('/proc/self/fd'))
            for _ in range(100):
                read(base / 'file')
                missing()
                read(base / 'absolute')
            assert len(os.listdir('/proc/self/fd')) == before
        check('descriptor leaks across success and fallback', descriptors)

        def pressure():
            pid = os.fork()
            if pid == 0:
                held = []
                try:
                    resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
                    while True:
                        try:
                            held.append(os.open('/dev/null', os.O_RDONLY))
                        except OSError as error:
                            assert error.errno == errno.EMFILE
                            break
                    os.close(held.pop())
                    # Only one slot remains. Directory walking must fall back
                    # without leaking slots or spuriously rejecting this open.
                    read(base / 'file')
                except Exception:
                    os._exit(1)
                os._exit(0)
            assert os.waitpid(pid, 0)[1] == 0
        check('one descriptor slot remaining', pressure)

    print(json.dumps({'summary': {kind: sum(c['status'] == kind for c in checks)
                                  for kind in ['pass', 'fail']}}))
    return int(any(c['status'] == 'fail' for c in checks))


if __name__ == '__main__':
    raise SystemExit(main())
