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
