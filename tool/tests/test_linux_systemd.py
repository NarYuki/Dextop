import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[2] / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/dextop-systemd'


def load():
    loader = importlib.machinery.SourceFileLoader('dextop_systemd', str(SOURCE))
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


if os.name == 'nt':
    with patch.dict(sys.modules, {'fcntl': SimpleNamespace(), 'pwd': SimpleNamespace()}):
        module = load()
else:
    module = load()


class UnitParserTests(unittest.TestCase):
    def test_dropins_accumulate_reset_and_order_by_filename(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            etc, lib = root/'etc', root/'lib'
            for directory in (etc, lib):
                (directory/'foo.service.d').mkdir(parents=True)
            (etc/'foo.service').write_text('[Service]\nExecStart=/bin/true\nEnvironment=ONE=1\nExecStartPre=/bin/base\n')
            (lib/'foo.service.d/10-add.conf').write_text('[Service]\nEnvironment=THREE=3\n')
            (etc/'foo.service.d/20-add.conf').write_text('[Service]\nEnvironment=TWO=2\nExecStartPre=/bin/dropin\n')
            (lib/'foo.service.d/20-add.conf').write_text('[Service]\nEnvironment=SHADOWED=1\n')
            with patch.object(module, 'paths', return_value=[etc, lib]):
                spec = module.load_unit('foo.service')
                self.assertEqual(spec['Service']['Environment'], ['ONE=1', 'THREE=3', 'TWO=2'])
                self.assertEqual(spec['Service']['ExecStartPre'], ['/bin/base', '/bin/dropin'])
                (etc/'foo.service.d/30-reset.conf').write_text('[Service]\nEnvironment=\nEnvironment=FINAL=1\n')
                self.assertEqual(module.load_unit('foo.service')['Service']['Environment'], ['FINAL=1'])

    def test_units_and_exec_are_not_shell_fragments(self):
        spec = module.parse_unit('[Service]\nType=exec\nExecStart=/bin/echo "hello world" ; whoami\nRestart=on-failure\n')
        module.validate_service(spec)
        args, ignore = module.command(spec['Service']['ExecStart'][0], 'test.service', {})
        self.assertEqual(args, ['/bin/echo', 'hello world', ';', 'whoami'])
        self.assertFalse(ignore)
        with self.assertRaises(ValueError):
            module.unit_name('../../outside')

    def test_unsupported_sandbox_and_daemon_types_are_refused(self):
        for directive in ('ProtectSystem=strict', 'PrivateTmp=true', 'Type=forking', 'Type=notify', 'DynamicUser=true'):
            with self.assertRaises(ValueError):
                module.validate_service(module.parse_unit('[Service]\nExecStart=/bin/true\n'+directive))

    def test_duration_and_multiline_reset(self):
        self.assertEqual(module.duration('1min 500ms'), 60.5)
        self.assertEqual(module.duration('2h'), 7200)
        with self.assertRaises(ValueError):
            module.duration('infinity')
        spec = module.parse_unit('[Service]\nExecStart=/bin/old\nExecStart=\nExecStart=/bin/echo \\\n  new\n')
        self.assertEqual(spec['Service']['ExecStart'], ['/bin/echo  new'])


@unittest.skipIf(os.name == 'nt', 'Process supervisor integration runs on Linux / Galaxy')
class SupervisorTests(unittest.TestCase):
    def test_oneshot_start_timeout_terminates_its_process(self):
        (self.units/'slow.service').write_text('[Service]\nType=oneshot\nExecStart=/bin/sleep 60\nTimeoutStartSec=300ms\n')
        self.start_manager()
        self.ctl('start', 'slow.service')
        row = self.wait_state('slow.service', 'failed')
        self.assertIn('timed out', row['error'])
        self.assertEqual(row['pid'], 0)

    def test_target_dependencies_do_not_require_an_unimplemented_target_manager(self):
        (self.units/'worker.service').write_text('[Unit]\nRequires=network.target\nAfter=network.target\n[Service]\nType=oneshot\nRemainAfterExit=yes\nExecStart=/bin/true\n')
        self.start_manager()
        self.ctl('start', 'worker.service')
        self.wait_state('worker.service', 'active')

    def test_failed_ordered_dependency_prevents_the_dependent_process(self):
        marker = self.root/'must-not-run'
        (self.units/'dependency.service').write_text('[Service]\nType=oneshot\nExecStart=/bin/false\n')
        (self.units/'worker.service').write_text('[Unit]\nRequires=dependency.service\nAfter=dependency.service\n[Service]\nExecStart=/usr/bin/touch '+str(marker)+'\n')
        self.start_manager()
        self.ctl('start', 'worker.service')
        row = self.wait_state('worker.service', 'failed')
        self.assertIn('Dependency failed', row['error'])
        self.assertFalse(marker.exists())

    def test_after_waits_for_oneshot_dependency_and_its_post_hook(self):
        marker = self.root/'dependency-ready'
        script = self.root/'dependency.py'
        script.write_text('import time; from pathlib import Path; time.sleep(.4); Path('+repr(str(marker))+').touch()')
        post = self.root/'post.py'
        post.write_text('from pathlib import Path; assert Path('+repr(str(marker))+').exists(); Path('+repr(str(marker)+'.post')+').touch()')
        worker = self.root/'worker.py'
        worker.write_text('from pathlib import Path; assert Path('+repr(str(marker))+').exists(); assert Path('+repr(str(marker)+'.post')+').exists()')
        (self.units/'dependency.service').write_text('[Service]\nType=oneshot\nExecStart='+sys.executable+' '+str(script)+'\nExecStartPost='+sys.executable+' '+str(post)+'\n')
        (self.units/'worker.service').write_text('[Unit]\nRequires=dependency.service\nAfter=dependency.service\n[Service]\nType=oneshot\nRemainAfterExit=yes\nExecStart='+sys.executable+' '+str(worker)+'\n')
        self.start_manager()
        self.ctl('start', 'worker.service')
        self.wait_state('worker.service', 'active')

    def test_status_exit_codes_distinguish_inactive_and_unknown_units(self):
        (self.units/'worker.service').write_text('[Service]\nExecStart=/bin/sleep 60\n')
        self.start_manager()
        result = module.rpc(dict(action='status', units=['worker.service'], user=True), base=self.state)
        self.assertFalse(result['ok'])
        self.assertEqual(result['code'], 3)
        missing = module.rpc(dict(action='status', units=['missing.service'], user=True), base=self.state)
        self.assertFalse(missing['ok'])
        self.assertEqual(missing['code'], 4)
        self.ctl('start', 'worker.service')
        self.assertTrue(module.rpc(dict(action='status', units=['worker.service'], user=True), base=self.state)['ok'])

    def test_failed_pre_hook_reports_failed_without_process(self):
        (self.units/'pre.service').write_text('[Service]\nExecStartPre=/bin/false\nExecStart=/bin/true\n')
        self.start_manager()
        result = module.rpc(dict(action='start', units=['pre.service'], user=True), base=self.state, timeout=5)
        self.assertFalse(result['ok'])
        row = self.wait_state('pre.service', 'failed')
        self.assertEqual(row['pid'], 0)

    def test_restart_rebuilds_oneshot_commands_and_counts_initial_start(self):
        output = self.root/'commands'
        first = f"/bin/sh -c 'echo first >> {output}'"
        second = f"/bin/sh -c 'echo second >> {output}; exit 1'"
        (self.units/'multi.service').write_text('[Unit]\nStartLimitBurst=2\nStartLimitIntervalSec=10\n[Service]\nType=oneshot\nExecStart='+first+'\nExecStart='+second+'\nRestart=on-failure\nRestartSec=100ms\n')
        self.start_manager()
        self.ctl('start', 'multi.service')
        end = time.monotonic()+5
        while time.monotonic() < end:
            row = next(item for item in self.ctl('list-units')['units'] if item['name'] == 'multi.service')
            if row['error'] == 'Start limit exceeded':
                self.assertEqual(output.read_text().splitlines(), ['first', 'second', 'first', 'second'])
                return
            time.sleep(.05)
        self.fail(str(row))

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.home = self.root/'home'
        self.units = self.home/'.config/systemd/user'
        self.units.mkdir(parents=True)
        self.state = self.root/'state'
        self.env = dict(os.environ, HOME=str(self.home), DEXTOP_UNIT_ROOT=str(self.root), DEXTOP_SERVICE_STATE=str(self.state),
                        DEXTOP_LINUX_USER=__import__('pwd').getpwuid(os.getuid()).pw_name)
        self.process = None

    def start_manager(self):
        self.process = subprocess.Popen([sys.executable, str(SOURCE), 'daemon'], env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        end = time.monotonic()+5
        while time.monotonic() < end:
            if (self.state/'status.json').exists():
                return
            if self.process.poll() is not None:
                self.fail(self.process.communicate()[1].decode())
            time.sleep(.05)
        self.fail('Supervisor did not become ready')

    def ctl(self, action, unit=None, **extra):
        value = dict(action=action, units=[unit] if unit else [], user=True, **extra)
        result = module.rpc(value, base=self.state, timeout=5)
        self.assertTrue(result['ok'], result)
        return result

    def wait_state(self, name, state, timeout=5):
        end = time.monotonic()+timeout
        while time.monotonic() < end:
            rows = self.ctl('list-units')['units']
            row = next(item for item in rows if item['name'] == name)
            if row['state'] == state:
                return row
            time.sleep(.05)
        self.fail(f'{name} did not reach {state}: {rows}')

    def tearDown(self):
        if self.process:
            if self.process.poll() is None:
                try:
                    module.rpc(dict(action='shutdown'), base=self.state, timeout=3)
                    self.process.wait(timeout=10)
                except Exception:
                    self.process.kill()
                    self.process.wait()
            self.process.communicate()
        self.temp.cleanup()

    def test_enable_now_restart_stop_and_persisted_autostart(self):
        (self.units/'worker.service').write_text('[Service]\nType=exec\nExecStart='+sys.executable+' -u -c "import time; print(\'running\', flush=True); time.sleep(60)"\n')
        self.start_manager()
        self.ctl('enable', 'worker.service', now=True)
        first = self.wait_state('worker.service', 'active')['pid']
        self.assertGreater(first, 0)
        self.assertIn('user:worker.service', json.loads((self.state/'enabled.json').read_text()))
        self.ctl('restart', 'worker.service')
        second = self.wait_state('worker.service', 'active')['pid']
        self.assertNotEqual(first, second)
        self.ctl('stop', 'worker.service')
        self.wait_state('worker.service', 'inactive')
        self.ctl('shutdown')
        self.process.communicate(timeout=10)
        self.start_manager()
        self.wait_state('worker.service', 'active')

    def test_oneshot_runs_once_and_timer_retriggers_it(self):
        output = self.root/'count'
        command = sys.executable+' -c "from pathlib import Path; p=Path(\''+str(output)+'\'); p.write_text(p.read_text()+\'x\' if p.exists() else \'x\')"'
        (self.units/'count.service').write_text('[Service]\nType=oneshot\nExecStart='+command+'\nRemainAfterExit=yes\n')
        self.start_manager()
        self.ctl('start', 'count.service')
        self.wait_state('count.service', 'active')
        time.sleep(.4)
        self.assertEqual(output.read_text(), 'x')
        self.ctl('restart', 'count.service')
        self.wait_state('count.service', 'active')
        self.assertEqual(output.read_text(), 'xx')

    def test_failing_process_hits_restart_limit_and_reports_error(self):
        (self.units/'failure.service').write_text('[Unit]\nStartLimitBurst=2\nStartLimitIntervalSec=10\n[Service]\nExecStart=/bin/false\nRestart=on-failure\nRestartSec=100ms\n')
        self.start_manager()
        self.ctl('start', 'failure.service')
        end = time.monotonic()+5
        while time.monotonic() < end:
            row = next(item for item in self.ctl('list-units')['units'] if item['name'] == 'failure.service')
            if row['error'] == 'Start limit exceeded':
                self.assertEqual(row['state'], 'failed')
                return
            time.sleep(.1)
        self.fail(str(row))

    def test_timer_repeatedly_starts_its_oneshot_service(self):
        output = self.root/'timer-count'
        command = sys.executable+' -c "from pathlib import Path; p=Path(\''+str(output)+'\'); p.write_text(p.read_text()+\'x\' if p.exists() else \'x\')"'
        (self.units/'tick.service').write_text('[Service]\nType=oneshot\nExecStart='+command+'\n')
        (self.units/'tick.timer').write_text('[Timer]\nOnUnitActiveSec=300ms\nUnit=tick.service\n')
        self.start_manager()
        self.ctl('start', 'tick.timer')
        end = time.monotonic()+5
        while time.monotonic() < end:
            if output.exists() and len(output.read_text()) >= 2:
                self.ctl('stop', 'tick.timer')
                time.sleep(.5)
                count = output.read_text()
                time.sleep(.5)
                self.assertEqual(output.read_text(), count)
                return
            time.sleep(.1)
        self.fail('Timer did not repeat')


if __name__ == '__main__':
    unittest.main()
