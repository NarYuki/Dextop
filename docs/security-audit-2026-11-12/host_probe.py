"""Reproduce current findings using only disposable files; no installed services.

Run from any directory with Python 3. These assertions confirm findings in the
audited version, not desired regression-test behavior after remediation.
"""
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

REPO = Path(__file__).resolve().parents[2]
SOURCE = Path(os.environ.get('DEXTOP_AUDIT_RESOURCES',
    str(REPO / 'android/app/src/main/resources/moe/n4tsu/dextop/linux')))
loader = importlib.machinery.SourceFileLoader('audit_systemd', str(SOURCE / 'dextop-systemd'))
spec = importlib.util.spec_from_loader(loader.name, loader)
manager = importlib.util.module_from_spec(spec)
loader.exec_module(manager)
results = {}

directives = []
for directive in ('ProtectSystem=strict', 'ProtectHome=yes', 'PrivateTmp=yes',
                  'NoNewPrivileges=yes', 'RestrictAddressFamilies=AF_UNIX'):
    unit = manager.parse_unit('[Service]\nExecStart=/bin/true\n' + directive + '\n')
    manager.validate_service(unit)
    assert directive.split('=')[0] in manager.ignored(unit)
    directives.append(directive)
results['accepted_without_enforcement'] = directives


def tmpfiles(*args):
    result = subprocess.run([sys.executable, str(SOURCE / 'dextop-tmpfiles'), *map(str, args)],
                            capture_output=True, text=True, timeout=5)
    assert result.returncode == 0, result.stderr


with tempfile.TemporaryDirectory(prefix='dextop-service-audit-') as temporary:
    root = Path(temporary)
    sentinel = root / 'sentinel'
    config = root / 'write.conf'
    config.write_text(f'F {sentinel} 0600 - - - CHANGED\n')
    sentinel.write_text('KEEP')
    tmpfiles('--create', '--dry-run', config)
    assert sentinel.read_text() == 'CHANGED'
    results['dry_run_modified_sentinel'] = True

    sentinel.write_text('KEEP')
    image = root / 'image-root'
    image.mkdir()
    tmpfiles('--create', '--root=' + str(image), config)
    assert sentinel.read_text() == 'CHANGED'
    assert not image.joinpath(*sentinel.parts[1:]).exists()
    results['root_option_modified_outside_image'] = True

    protected = root / 'protected'
    protected.mkdir()
    victim = protected / 'sentinel'
    victim.write_text('KEEP')
    alias = root / 'cache'
    alias.symlink_to(protected, target_is_directory=True)
    config = root / 'clean.conf'
    config.write_text(f'd {alias} 0700 - - 0\n')
    tmpfiles('--clean', '--exclude-prefix=' + str(protected), config)
    assert not victim.exists()
    results['symlink_clean_deleted_excluded_sentinel'] = True

print(json.dumps(results, indent=2))
