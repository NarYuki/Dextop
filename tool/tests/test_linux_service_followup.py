"""Follow-up regressions: destructive operations use disposable fixtures only."""
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from test_linux_service_security import tmpfiles, manager


class TmpfilesFollowupTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.config = self.root / 'config'

    def run_rules(self, text, *options):
        self.config.write_text(text)
        return tmpfiles.main([*options, str(self.config)])

    def test_x_glob_preserves_subtree_regardless_of_rule_order(self):
        cache = self.root / 'cache'
        keep = cache / 'keep-one'
        keep.mkdir(parents=True)
        (keep / 'sentinel').write_text('KEEP')
        (cache / 'old').write_text('DELETE')
        self.assertEqual(self.run_rules(f'd {cache} - - - 0\nx {cache}/keep-* - - - -\n', '--clean'), 0)
        self.assertEqual((keep / 'sentinel').read_text(), 'KEEP')
        self.assertFalse((cache / 'old').exists())
        # A separate cleanup entry starting inside x must also be protected.
        self.assertEqual(self.run_rules(f'x {cache}/keep-* - - - -\nd {keep} - - - 0\n', '--clean'), 0)
        self.assertTrue((keep / 'sentinel').exists())

    def test_X_preserves_directory_but_cleans_contents(self):
        cache = self.root / 'cache'
        keep = cache / 'keep'
        keep.mkdir(parents=True)
        (keep / 'old').write_text('DELETE')
        self.assertEqual(self.run_rules(f'd {cache} - - - 0\nX {keep} - - - -\n', '--clean'), 0)
        self.assertTrue(keep.is_dir())
        self.assertFalse((keep / 'old').exists())

    def test_boot_modifier_applies_to_remove_and_clean(self):
        for index, (kind, operation) in enumerate((('r', '--remove'), ('R', '--remove'), ('D', '--remove'), ('d', '--clean'))):
            with self.subTest(kind=kind):
                directory = self.root / ('case-%d' % index)
                directory.mkdir()
                sentinel = directory / 'sentinel'
                sentinel.write_text('KEEP')
                target = sentinel if kind == 'r' else directory
                rules = f'{kind}! {target} - - - 0\n'
                self.assertEqual(self.run_rules(rules, operation), 0)
                self.assertTrue(sentinel.exists())
                self.assertEqual(self.run_rules(rules, operation, '--boot'), 0)
                self.assertFalse(sentinel.exists())

    def test_suppressed_boot_entry_does_not_hide_regular_entry(self):
        target = self.root / 'file'
        self.assertEqual(self.run_rules(f'f! {target} - - - - BOOT\nf {target} - - - - NORMAL\n', '--create'), 0)
        self.assertEqual(target.read_text(), 'NORMAL')

    def test_f_plus_truncates_and_preserves_unicode_and_escapes(self):
        target = self.root / 'file'
        target.write_text('LONG OLD CONTENT')
        self.assertEqual(self.run_rules(f'f+ {target} - - - - 日本語\\nNEXT\n', '--create'), 0)
        self.assertEqual(target.read_text(), '日本語\nNEXT')
        self.assertEqual(self.run_rules(f'f {target} - - - - KEEP OLD\n', '--create'), 0)
        self.assertEqual(target.read_text(), '日本語\nNEXT')

    def test_write_overwrites_at_start_and_plus_appends(self):
        target = self.root / 'file'
        target.write_text('ABCDE')
        self.assertEqual(self.run_rules(f'w {target} - - - - XY\n', '--create'), 0)
        self.assertEqual(target.read_text(), 'XYCDE')
        self.assertEqual(self.run_rules(f'w+ {target} - - - - Z\n', '--create'), 0)
        self.assertEqual(target.read_text(), 'XYCDEZ')

    def test_repeated_append_rules_are_all_applied(self):
        target = self.root / 'file'
        target.write_text('START')
        self.assertEqual(self.run_rules(f'w+ {target} - - - - ONE\nw+ {target} - - - - TWO\n', '--create'), 0)
        self.assertEqual(target.read_text(), 'STARTONETWO')

    def test_unknown_owner_fails_before_overwriting_file(self):
        target = self.root / 'file'
        target.write_text('KEEP')
        for owner, group in (('dextop-test-missing-user', '-'), ('-', 'dextop-test-missing-group')):
            with self.subTest(owner=owner, group=group):
                self.assertNotEqual(self.run_rules(f'F {target} 0600 {owner} {group} - CHANGED\n', '--create'), 0)
                self.assertEqual(target.read_text(), 'KEEP')

    def test_missing_mode_does_not_reset_existing_permissions(self):
        target = self.root / 'file'
        target.write_text('KEEP')
        target.chmod(0o600)
        directory = self.root / 'directory'
        directory.mkdir(mode=0o700)
        for kind, path in (('f', target), ('f+', target), ('z', target), ('Z', directory), ('d', directory), ('e', directory)):
            with self.subTest(kind=kind):
                old = path.stat().st_mode & 0o777
                self.assertEqual(self.run_rules(f'{kind} {path} - - - -\n', '--create'), 0)
                self.assertEqual(path.stat().st_mode & 0o777, old)

    def test_invalid_age_fails_without_deleting(self):
        directory = self.root / 'cache'
        directory.mkdir()
        sentinel = directory / 'sentinel'
        sentinel.write_text('KEEP')
        for age in ('garbage', '-1', '1typo', '1h-junk', '1h2bogus'):
            with self.subTest(age=age):
                self.assertNotEqual(self.run_rules(f'd {directory} - - - {age}\n', '--clean'), 0)
                self.assertTrue(sentinel.exists())
        self.assertEqual(tmpfiles.age_seconds('1h 30min'), 5400)
        self.assertEqual(tmpfiles.age_seconds('2days'), 172800)

    def test_child_directory_cleanup_policy_overrides_parent(self):
        directory = self.root / 'cache'
        keep = directory / 'keep'
        keep.mkdir(parents=True)
        (keep / 'sentinel').write_text('KEEP')
        (directory / 'old').write_text('DELETE')
        self.assertEqual(self.run_rules(f'd {directory} - - - 0\nd {keep} - - - -\n', '--clean'), 0)
        self.assertTrue((keep / 'sentinel').exists())
        self.assertFalse((directory / 'old').exists())

    def test_copy_into_source_subtree_is_rejected_without_recursive_creation(self):
        source = self.root / 'source'
        source.mkdir()
        (source / 'sentinel').write_text('KEEP')
        # A short recursion limit bounds the unfixed implementation's work.
        import sys
        old_limit = sys.getrecursionlimit()
        try:
            sys.setrecursionlimit(100)
            result = self.run_rules(f'C {source}/copy - - - - {source}\n', '--create')
        finally:
            sys.setrecursionlimit(old_limit)
        self.assertNotEqual(result, 0)
        self.assertFalse((source / 'copy').exists())
        self.assertEqual((source / 'sentinel').read_text(), 'KEEP')

    def test_e_glob_cleans_existing_directories(self):
        directory = self.root / 'cache-one'
        directory.mkdir()
        (directory / 'old').write_text('DELETE')
        self.assertEqual(self.run_rules(f'e {self.root}/cache-* - - - 0\n', '--clean'), 0)
        self.assertFalse((directory / 'old').exists())


class ServiceDirectoryFollowupTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.home = self.root / 'home'
        self.supervisor = manager.Supervisor.__new__(manager.Supervisor)

    def job(self, directive):
        return dict(spec=manager.parse_unit('[Service]\nExecStart=/bin/true\n' + directive), user=True,
                    environment={'HOME': str(self.home), 'XDG_RUNTIME_DIR': str(self.root / 'run')})

    def test_absolute_parent_and_empty_names_rejected_before_mutating(self):
        for name in (str(self.root / 'outside'), '../outside', 'child/../../outside', ':alias', '.'):
            for key in manager.DIRECTORY_KEYS:
                job = self.job(key + '=valid ' + name)
                with self.subTest(key=key, name=name), self.assertRaises(ValueError):
                    manager.validate_service(job['spec'])
                with self.assertRaises(ValueError):
                    self.supervisor.directories(job)
                self.assertFalse(self.home.exists())

    def test_symlink_at_leaf_or_parent_cannot_change_target_permissions(self):
        victim = self.root / 'victim'
        victim.mkdir(mode=0o700)
        (self.home / '.cache').mkdir(parents=True)
        alias = self.home / '.cache/alias'
        alias.symlink_to(victim, target_is_directory=True)
        for name in ('alias', 'alias/child'):
            with self.subTest(name=name), self.assertRaises(OSError):
                self.supervisor.directories(self.job('CacheDirectory=' + name + '\nCacheDirectoryMode=0777'))
            self.assertEqual(victim.stat().st_mode & 0o777, 0o700)
            self.assertFalse((victim / 'child').exists())

    def test_directory_swap_to_symlink_before_open_is_rejected(self):
        victim = self.root / 'victim'
        victim.mkdir(mode=0o700)
        cache = self.home / '.cache'
        cache.mkdir(parents=True)
        alias = cache / 'alias'
        alias.mkdir()
        original = manager.os.open
        def swap(path, flags, *args, **kwargs):
            if path == 'alias' and kwargs.get('dir_fd') is not None:
                alias.rename(cache / 'moved')
                alias.symlink_to(victim, target_is_directory=True)
            return original(path, flags, *args, **kwargs)
        with patch.object(manager.os, 'open', side_effect=swap), self.assertRaises(OSError):
            self.supervisor.directories(self.job('CacheDirectory=alias\nCacheDirectoryMode=0777'))
        self.assertEqual(victim.stat().st_mode & 0o777, 0o700)

    def test_nested_directory_created_with_requested_mode(self):
        job = self.job('RuntimeDirectory=app/child\nRuntimeDirectoryMode=0700')
        manager.validate_service(job['spec'])
        self.supervisor.directories(job)
        self.assertEqual((self.root / 'run/app/child').stat().st_mode & 0o777, 0o700)


if __name__ == '__main__':
    unittest.main()
