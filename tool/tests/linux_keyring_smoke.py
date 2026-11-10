#!/usr/bin/python3
"""Offline Secret Service smoke test, isolated from the user's real keyrings."""
import hashlib
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time


def run_inside():
    home = Path.home()
    if os.environ.get('DEXTOP_KEYRING_SMOKE_HOME') != str(home):
        raise RuntimeError('This smoke test requires its isolated temporary home')
    helper = os.environ.get('DEXTOP_KEYRING_UNLOCK_HELPER', '/usr/local/libexec/dextop-keyring-unlock')
    loader = importlib.machinery.SourceFileLoader('unlock', helper)
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    checks = []
    secret = b'dextop-synthetic-keyring-passphrase'

    def command(args, data=None):
        result = subprocess.run(args, input=data, capture_output=True, timeout=5)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout

    def stop_test_daemon():
        for process in Path('/proc').iterdir():
            if not process.name.isdigit():
                continue
            try:
                if not (process/'comm').read_text().startswith('gnome-keyring'):
                    continue
                environment = (process/'environ').read_bytes().split(b'\0')
                if (b'HOME=' + os.fsencode(home) in environment and
                    b'DBUS_SESSION_BUS_ADDRESS=' + os.fsencode(os.environ['DBUS_SESSION_BUS_ADDRESS']) in environment):
                    os.kill(int(process.name), signal.SIGTERM)
            except (OSError, ValueError):
                pass

    try:
        assert module.unlock(secret, pam_available=False), 'initial unlock failed'
        checks.append('creates an unlocked persistent login collection')
        command(['secret-tool', 'store', '--label=Dextop synthetic audit', 'dextop-audit', 'synthetic'], b'synthetic-stored-value')
        lookup = ['secret-tool', 'lookup', 'dextop-audit', 'synthetic']
        assert command(lookup).strip() == b'synthetic-stored-value'
        checks.append('Secret Service store and lookup')
        collection = home/'.local/share/keyrings/login.keyring'
        assert collection.exists()
        before = hashlib.sha256(collection.read_bytes()).hexdigest()
        command(['dbus-send', '--session', '--reply-timeout=2000', '--print-reply=literal',
                 '--dest=org.freedesktop.secrets', '/org/freedesktop/secrets',
                 'org.freedesktop.Secret.Service.Lock', 'array:objpath:/org/freedesktop/secrets/collection/login'])
        assert not module.unlock(b'incorrect-test-password', pam_available=False), 'wrong password accepted'
        assert hashlib.sha256(collection.read_bytes()).hexdigest() == before
        checks.append('wrong password rejected without replacing encrypted collection')
        assert module.unlock(secret, pam_available=False), 'correct re-unlock failed'
        assert command(lookup).strip() == b'synthetic-stored-value'
        checks.append('correct password unlocks the same saved secret')
        stop_test_daemon()
        time.sleep(.2)
        assert module.unlock(secret, pam_available=False), 'daemon restart unlock failed'
        assert command(lookup).strip() == b'synthetic-stored-value'
        checks.append('daemon restart preserves encrypted data and default alias')
        print(json.dumps({'passed': len(checks), 'checks': checks}))
    finally:
        stop_test_daemon()


def main():
    if '--inside' in sys.argv[1:]:
        run_inside()
        return 0
    directory = Path(tempfile.mkdtemp(prefix='dextop-keyring-audit-'))
    environment = os.environ.copy()
    environment['HOME'] = str(directory)
    environment['DEXTOP_KEYRING_SMOKE_HOME'] = str(directory)
    environment['XDG_RUNTIME_DIR'] = str(directory/'runtime')
    (directory/'runtime').mkdir(mode=0o700)
    environment.pop('GNOME_KEYRING_CONTROL', None)
    try:
        return subprocess.run(['dbus-run-session', '--', sys.executable, __file__, '--inside'],
                              env=environment, timeout=30).returncode
    finally:
        shutil.rmtree(directory)


if __name__ == '__main__':
    sys.exit(main())
