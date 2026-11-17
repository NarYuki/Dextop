"""Verify follow-up fixes; mutate disposable temporary fixtures only."""
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import tempfile

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux'
def load(name, file):
    loader = importlib.machinery.SourceFileLoader(name, str(SOURCE / file))
    spec = importlib.util.spec_from_loader(name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module

tmpfiles = load('followup_tmpfiles', 'dextop-tmpfiles')
manager = load('followup_manager', 'dextop-systemd')
results = {}
with tempfile.TemporaryDirectory(prefix='dextop-followup-') as temporary:
    root = Path(temporary).resolve()
    config = root / 'test.conf'
    cache = root / 'cache'
    cache.mkdir()
    keep = cache / 'keep'
    keep.mkdir()
    sentinel = keep / 'sentinel'
    sentinel.write_text('KEEP')
    config.write_text(f'd {cache} 0700 - - 0\nx {keep} - - - -\n')
    assert tmpfiles.main(['--clean', str(config)]) == 0
    assert sentinel.read_text() == 'KEEP'
    results['x_rule_preserves_sentinel'] = True

    sentinel = root / 'boot-only'
    sentinel.write_text('KEEP')
    config.write_text(f'r! {sentinel} - - - -\n')
    assert tmpfiles.main(['--remove', str(config)]) == 0
    assert sentinel.read_text() == 'KEEP'
    assert tmpfiles.main(['--remove', '--boot', str(config)]) == 0
    assert not sentinel.exists()
    results['boot_only_remove_requires_boot'] = True

    sentinel = root / 'file'
    sentinel.write_text('OLD')
    config.write_text(f'f+ {sentinel} 0600 - - - NEW\n')
    assert tmpfiles.main(['--create', str(config)]) == 0
    assert sentinel.read_text() == 'NEW'
    results['f_plus_replaces_existing_contents'] = sentinel.read_text()

    outside = root / 'outside-runtime'
    outside.mkdir(mode=0o700)
    spec = manager.parse_unit('[Service]\nExecStart=/bin/true\nRuntimeDirectory=' + str(outside) + '\nRuntimeDirectoryMode=0777\n')
    try:
        manager.validate_service(spec)
    except ValueError:
        pass
    else:
        raise AssertionError('Absolute RuntimeDirectory accepted')
    supervisor = manager.Supervisor.__new__(manager.Supervisor)
    try:
        supervisor.directories(dict(spec=spec, user=True, environment={'HOME': str(root / 'home'), 'XDG_RUNTIME_DIR': str(root / 'run')}))
    except ValueError:
        pass
    else:
        raise AssertionError('Absolute RuntimeDirectory applied')
    assert outside.stat().st_mode & 0o777 == 0o700
    results['absolute_runtime_directory_refused'] = True

    home = root / 'home'
    (home / '.cache').mkdir(parents=True)
    victim = root / 'symlink-target'
    victim.mkdir(mode=0o700)
    (home / '.cache/alias').symlink_to(victim, target_is_directory=True)
    spec = manager.parse_unit('[Service]\nExecStart=/bin/true\nCacheDirectory=alias\nCacheDirectoryMode=0777\n')
    manager.validate_service(spec)
    try:
        supervisor.directories(dict(spec=spec, user=True, environment={'HOME': str(home)}))
    except OSError:
        pass
    else:
        raise AssertionError('CacheDirectory followed symlink')
    assert victim.stat().st_mode & 0o777 == 0o700
    results['cache_directory_symlink_refused'] = True
print(json.dumps(results, indent=2))
