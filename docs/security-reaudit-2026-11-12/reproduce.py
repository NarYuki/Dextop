"""Confirm remaining findings with disposable files/services only.

These assertions describe the audited, still-vulnerable behavior. They are not
the expected regression-test assertions after future remediation.
"""
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import pwd
import subprocess
import sys
import tempfile
import time

SOURCE = Path(os.environ.get('DEXTOP_REAUDIT_RESOURCES', str(
    Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux')))
loader = importlib.machinery.SourceFileLoader('reaudit_manager', str(SOURCE / 'dextop-systemd'))
spec = importlib.util.spec_from_loader(loader.name, loader)
manager = importlib.util.module_from_spec(spec)
loader.exec_module(manager)
result = {'remaining_directive_gaps': []}
for directive in ('ReadOnlyDirectories=/home', 'ReadWriteDirectories=/var/cache',
                  'InaccessibleDirectories=/home', 'MountFlags=private',
                  'NetworkNamespacePath=/nonexistent-reaudit-namespace'):
    unit = manager.parse_unit('[Service]\nExecStart=/bin/true\n' + directive)
    manager.validate_service(unit)
    assert directive.split('=')[0] in manager.ignored(unit)
    result['remaining_directive_gaps'].append(directive)

with tempfile.TemporaryDirectory(prefix='dextop-reaudit-') as temporary:
    root = Path(temporary).resolve()
    protected = root / 'protected'
    protected.mkdir()
    sentinel = protected / 'sentinel'
    sentinel.write_text('KEEP')
    alias = root / 'cache'
    alias.symlink_to(protected, target_is_directory=True)

    def run_config(name, text, operation):
        config = root / name
        config.write_text(text)
        return subprocess.run([sys.executable, str(SOURCE / 'dextop-tmpfiles'), operation,
            '--exclude-prefix=' + str(protected), str(config)], capture_output=True, text=True, timeout=5)

    clean = run_config('clean.conf', f'd {alias} 0700 - - 0\n', '--clean')
    assert clean.returncode != 0 and sentinel.read_text() == 'KEEP'
    result['fixed_clean_does_not_follow_link'] = True
    remove = run_config('remove.conf', f'D {alias} 0700 - - -\n', '--remove')
    assert remove.returncode == 0 and not sentinel.exists()
    result['remaining_remove_deleted_excluded_sentinel'] = True
    sentinel.write_text('KEEP')
    create = run_config('create.conf', f'F {alias / "sentinel"} 0600 - - - CHANGED\n', '--create')
    assert create.returncode == 0 and sentinel.read_text() == 'CHANGED'
    result['remaining_create_overwrote_excluded_sentinel'] = True

    home, state = root / 'home', root / 'state'
    units = home / '.config/systemd/user'
    units.mkdir(parents=True)
    marker = root / 'executed'
    (units / 'gap.service').write_text('[Service]\nType=oneshot\nRemainAfterExit=yes\n'
        'NetworkNamespacePath=/nonexistent-reaudit-namespace\n'
        f"ExecStart=/bin/sh -c 'echo EXECUTED > {marker}'\n")
    env = dict(os.environ, HOME=str(home), DEXTOP_UNIT_ROOT=str(root),
        DEXTOP_USER_SERVICE_STATE=str(state), DEXTOP_LINUX_USER=pwd.getpwuid(os.getuid()).pw_name)
    process = subprocess.Popen([sys.executable, str(SOURCE / 'dextop-systemd'), 'daemon', '--user'],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        end = time.monotonic() + 5
        while not (state / 'status.json').exists():
            assert process.poll() is None, process.stderr.read().decode() if process.poll() is not None else ''
            assert time.monotonic() < end
            time.sleep(.05)
        reply = manager.rpc(dict(action='start', units=['gap.service'], user=True), state, timeout=5)
        assert reply['ok'], reply
        end = time.monotonic() + 5
        while not marker.exists():
            assert time.monotonic() < end
            time.sleep(.05)
        assert marker.read_text() == 'EXECUTED\n'
        result['remaining_namespace_gap_executed_command'] = True
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        process.stderr.close()

print(json.dumps(result, indent=2))
