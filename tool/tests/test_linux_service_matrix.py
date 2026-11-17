"""Independent security/behavior cases supplement the service regression suite.

Each generated method has a stable name and a separate disposable filesystem.
Case tables describe external behavior, not the implementation's branching.
"""
from pathlib import Path
import tempfile
import unittest
from test_linux_service_security import tmpfiles, manager


class ServiceMatrixTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='dextop-service-matrix-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.config = self.root / 'rules.conf'

    def apply(self, rules, operation='--create', *options):
        self.config.write_text(rules + '\n')
        return tmpfiles.main([operation, *options, str(self.config)])

    def service_job(self, key, value, home=None, runtime=None):
        return dict(user=True, spec=manager.parse_unit(
            '[Service]\nExecStart=/bin/true\n' + key + '=' + value + '\n' + key + 'Mode=0777\n'),
            environment={'HOME': str(home or self.root / 'home'),
                         'XDG_RUNTIME_DIR': str(runtime or self.root / 'run')})


PROTECTIONS = (
    ('ProtectSystem', 'strict'), ('ProtectHome', 'yes'),
    ('ProtectKernelTunables', 'yes'), ('ProtectKernelModules', 'yes'),
    ('ProtectControlGroups', 'yes'), ('ProtectClock', 'yes'),
    ('ProtectHostname', 'yes'), ('PrivateTmp', 'yes'),
    ('PrivateDevices', 'yes'), ('PrivateNetwork', 'yes'),
    ('PrivateUsers', 'yes'), ('RestrictAddressFamilies', 'AF_UNIX'),
    ('RestrictNamespaces', 'yes'), ('RestrictRealtime', 'yes'),
    ('RestrictSUIDSGID', 'yes'), ('NoNewPrivileges', 'yes'),
    ('CapabilityBoundingSet', ''), ('AmbientCapabilities', 'CAP_NET_ADMIN'),
    ('SecureBits', 'noroot'), ('ReadOnlyPaths', '/home'),
    ('ReadWritePaths', '/var/cache'), ('InaccessiblePaths', '/home'),
    ('TemporaryFileSystem', '/tmp'), ('MemoryDenyWriteExecute', 'yes'),
    ('LockPersonality', 'yes'), ('NetworkNamespacePath', '/missing'),
    ('IPCNamespacePath', '/missing'), ('MountFlags', 'private'),
    ('ReadOnlyDirectories', '/home'), ('ReadWriteDirectories', '/cache'),
    ('InaccessibleDirectories', '/home'), ('SystemCallFilter', '@system-service'),
)


def protection_case(key, value):
    def test(self):
        spec = manager.parse_unit('[Service]\nExecStart=/bin/true\n' + key + '=' + value)
        with self.assertRaisesRegex(ValueError, key):
            manager.validate_service(spec)
    return test


INVALID_NAMES = {
    'absolute': '/outside', 'parent': '../outside',
    'nested_parent': 'app/../../outside', 'empty_destination': ':alias',
    'current_directory': '.', 'valid_then_absolute': 'valid /outside',
}
DIRECTORY_KEYS = ('RuntimeDirectory', 'StateDirectory', 'CacheDirectory', 'LogsDirectory', 'ConfigurationDirectory')


def invalid_directory_case(key, label):
    def test(self):
        value = INVALID_NAMES[label]
        # Never give an erroneous implementation a real /outside to modify.
        value = value.replace('/outside', str(self.root / 'outside')) if label in ('absolute', 'valid_then_absolute') else value
        job = self.service_job(key, value)
        with self.assertRaises(ValueError):
            manager.validate_service(job['spec'])
        supervisor = manager.Supervisor.__new__(manager.Supervisor)
        with self.assertRaises(ValueError):
            supervisor.directories(job)
        self.assertFalse((self.root / 'home').exists())
        self.assertFalse((self.root / 'run').exists())
        self.assertFalse((self.root / 'outside').exists())
    return test


def directory_link_case(key, location):
    def test(self):
        victim = self.root / 'victim'
        victim.mkdir(mode=0o700)
        sentinel = victim / 'sentinel'
        sentinel.write_text('KEEP')
        home, runtime = self.root / 'home', self.root / 'run'
        roots = {'RuntimeDirectory': runtime, 'StateDirectory': home / '.local/state',
                 'CacheDirectory': home / '.cache', 'LogsDirectory': home / '.local/state/log',
                 'ConfigurationDirectory': home / '.config'}
        base = roots[key]
        if location == 'root':
            base.parent.mkdir(parents=True, exist_ok=True)
            base.symlink_to(victim, target_is_directory=True)
            value = 'new'
        else:
            base.mkdir(parents=True)
            (base / 'alias').symlink_to(victim, target_is_directory=True)
            value = 'alias' if location == 'leaf' else 'alias/new'
        job = self.service_job(key, value, home, runtime)
        manager.validate_service(job['spec'])
        supervisor = manager.Supervisor.__new__(manager.Supervisor)
        with self.assertRaises(OSError):
            supervisor.directories(job)
        self.assertEqual(victim.stat().st_mode & 0o777, 0o700)
        self.assertEqual(sentinel.read_text(), 'KEEP')
        self.assertEqual(sorted(p.name for p in victim.iterdir()), ['sentinel'])
    return test


LINK_OPERATIONS = ('F', 'f+', 'w', 'w+', 'd', 'D', 'e', 'z', 'Z', 'C', 'r', 'R')


def tmpfiles_link_case(kind, location):
    def test(self):
        victim = self.root / 'victim'
        victim.mkdir(mode=0o700)
        sentinel = victim / 'sentinel'
        sentinel.write_text('KEEP')
        sentinel.chmod(0o600)
        alias = self.root / 'alias'
        if location == 'parent':
            alias.symlink_to(victim, target_is_directory=True)
            target = alias / ('new' if kind == 'C' else 'sentinel')
        else:
            target = alias
            alias.symlink_to(victim if kind in ('d', 'D', 'e', 'Z', 'R') else sentinel)
        source = self.root / 'source'
        source.write_text('COPY')
        argument = str(source) if kind == 'C' else 'CHANGED'
        operation = '--remove' if kind in ('r', 'R', 'D') else '--create'
        # Removing a link itself is allowed; following it is never allowed.
        self.apply(f'{kind} {target} 0777 - - - {argument}', operation,
                   '--exclude-prefix=' + str(victim))
        self.assertEqual(sentinel.read_text(), 'KEEP')
        self.assertEqual(sentinel.stat().st_mode & 0o777, 0o600)
        self.assertEqual(victim.stat().st_mode & 0o777, 0o700)
        self.assertEqual(sorted(p.name for p in victim.iterdir()), ['sentinel'])
    return test


CONTENT_CASES = (
    ('f_missing', 'f', None, 'NEW', 'NEW'),
    ('f_existing', 'f', 'OLD', 'NEW', 'OLD'),
    ('f_plus_missing', 'f+', None, 'NEW', 'NEW'),
    ('f_plus_existing', 'f+', 'LONG OLD', 'NEW', 'NEW'),
    ('F_missing', 'F', None, 'NEW', 'NEW'),
    ('F_existing', 'F', 'LONG OLD', 'NEW', 'NEW'),
    ('w_overwrite', 'w', 'ABCDE', 'XY', 'XYCDE'),
    ('w_append', 'w+', 'ABCDE', 'XY', 'ABCDEXY'),
    ('w_missing', 'w', None, 'NEW', None),
    ('f_utf8', 'f', None, '日本語', '日本語'),
    ('f_plus_escape', 'f+', 'OLD', r'A\nB', 'A\nB'),
    ('multiple_appends', 'w+', 'OLD', 'ONE', 'OLDONETWO'),
)


def content_case(label, kind, initial, argument, expected):
    def test(self):
        target = self.root / 'file'
        if initial is not None:
            target.write_text(initial)
            target.chmod(0o600)
        rules = f'{kind} {target} - - - - {argument}'
        if label == 'multiple_appends':
            rules += f'\nw+ {target} - - - - TWO'
        self.assertEqual(self.apply(rules), 0)
        if expected is None:
            self.assertFalse(target.exists())
        else:
            self.assertEqual(target.read_text(), expected)
            self.assertEqual(target.stat().st_mode & 0o777, 0o600 if initial is not None else 0o644)
    return test


def cleanup_case(kind, policy):
    def test(self):
        cache = self.root / 'cache-one'
        keep = cache / 'keep'
        keep.mkdir(parents=True)
        sentinel = keep / 'sentinel'
        sentinel.write_text('KEEP')
        (cache / 'delete').write_text('DELETE')
        target = str(self.root / 'cache-*') if kind == 'e' else str(cache)
        rules = f'{kind} {target} - - - 0'
        options = ()
        if policy in ('x', 'X'):
            rules += f'\n{policy} {cache}/k* - - - -'
        elif policy == 'cli':
            options = ('--exclude-prefix=' + str(keep),)
        else:
            rules += f'\nd {keep} - - - -'
        self.assertEqual(self.apply(rules, '--clean', *options), 0)
        self.assertTrue(keep.is_dir())
        self.assertEqual(sentinel.exists(), policy != 'X')
        self.assertFalse((cache / 'delete').exists())
    return test


AGES = (
    ('zero', '0', 0), ('second', '1s', 1), ('milliseconds', '250ms', .25),
    ('microseconds', '2us', .000002), ('composite', '1h30min', 5400),
    ('days', '2days', 172800), ('fractional', '0.5h', 1800), ('week', '1w', 604800),
    ('garbage', 'garbage', None), ('negative', '-1', None),
    ('unknown_unit', '1typo', None), ('suffix', '1h-junk', None),
    ('mixed_invalid', '1h2bogus', None), ('infinity', 'inf', None),
    ('exponent', '1e309', None), ('punctuation', '1h!', None),
)


def age_case(label, text, expected):
    def test(self):
        if expected is not None:
            self.assertAlmostEqual(tmpfiles.age_seconds(text), expected)
        else:
            cache = self.root / 'cache'
            cache.mkdir()
            sentinel = cache / 'sentinel'
            sentinel.write_text('KEEP')
            with self.assertRaises(ValueError):
                tmpfiles.age_seconds(text)
            self.assertNotEqual(self.apply(f'd {cache} - - - {text}', '--clean'), 0)
            self.assertEqual(sentinel.read_text(), 'KEEP')
    return test


def register(name, test):
    test.__name__ = 'test_' + name
    if hasattr(ServiceMatrixTests, test.__name__):
        raise RuntimeError('Duplicate matrix test: ' + name)
    setattr(ServiceMatrixTests, test.__name__, test)


for key, value in PROTECTIONS:
    register('protection_' + key, protection_case(key, value))
for key in DIRECTORY_KEYS:
    for label in INVALID_NAMES:
        register('directory_name_' + key + '_' + label, invalid_directory_case(key, label))
    for location in ('root', 'parent', 'leaf'):
        register('directory_link_' + key + '_' + location, directory_link_case(key, location))
for kind in LINK_OPERATIONS:
    for location in ('parent', 'leaf'):
        register('tmpfiles_link_' + kind.replace('+', '_plus') + '_' + location, tmpfiles_link_case(kind, location))
for row in CONTENT_CASES:
    register('content_' + row[0], content_case(*row))
for kind in ('d', 'D', 'e'):
    for policy in ('x', 'X', 'cli', 'nested'):
        register('cleanup_' + kind + '_' + policy, cleanup_case(kind, policy))
for row in AGES:
    register('age_' + row[0], age_case(*row))


if __name__ == '__main__':
    unittest.main()
