#!/usr/bin/env python3
"""Exercise the shipped systemctl/journalctl clients against an isolated supervisor."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import sys

root=Path(tempfile.mkdtemp(prefix='dextop-systemctl-'))
scope='--system' if '--system' in sys.argv else '--user'
home=root/'home'; home.mkdir()
units=(root/'etc/systemd/system') if scope=='--system' else (home/'.config/systemd/user'); units.mkdir(parents=True)
env=dict(os.environ, HOME=str(home), DEXTOP_UNIT_ROOT=str(root), DEXTOP_SERVICE_STATE=str(root/'state'))
env['DEXTOP_LINUX_USER']=__import__('pwd').getpwuid(os.getuid()).pw_name
source='/usr/local/libexec/dextop-systemd'
manager=None
results=[]
def start_manager():
    global manager
    manager=subprocess.Popen(['python3',source,'daemon'],env=env,stdout=(root/'manager.log').open('a'),stderr=subprocess.STDOUT)
    end=time.monotonic()+10
    while not (root/'state/status.json').exists():
        if manager.poll() is not None: raise RuntimeError((root/'manager.log').read_text())
        if time.monotonic()>end: raise TimeoutError('Supervisor readiness')
        time.sleep(.1)
def ctl(*args, expected=0, journal=False):
    helper='/usr/local/libexec/dextop-journalctl' if journal else '/usr/local/libexec/dextop-systemctl'
    cmd=[helper,scope,*args]
    p=subprocess.run(cmd,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=20)
    results.append(dict(command=cmd,code=p.returncode,expected=expected,output=p.stdout[-5000:],pass_=p.returncode==expected))
    return p
try:
    (units/'worker.service').write_text('[Service]\nType=exec\nExecStartPre=/bin/echo pre-hook\nExecStart=/usr/bin/python3 -u -c "import time; print(\'service-ready\', flush=True); time.sleep(60)"\nExecReload=/bin/echo reload-hook\nExecStopPost=/bin/echo stopped-hook\n')
    (units/'tick.service').write_text('[Service]\nType=oneshot\nExecStart=/bin/echo timer-fired\n')
    (units/'tick.timer').write_text('[Timer]\nOnUnitActiveSec=300ms\nUnit=tick.service\n')
    (units/'unsupported.service').write_text('[Service]\nExecStart=/bin/true\nPrivateTmp=true\n')
    (units/'unsupported.timer').write_text('[Timer]\nOnCalendar=daily\n')
    start_manager()
    ctl('daemon-reload')
    ctl('status','worker.service',expected=3)
    ctl('status','missing.service',expected=4)
    ctl('enable','--now','worker.service')
    ctl('is-enabled','worker.service')
    ctl('is-active','worker.service')
    ctl('status','worker.service')
    quiet=ctl('--quiet','is-active','worker.service')
    results.append(dict(name='quiet-output',pass_=quiet.stdout==''))
    ctl('reload','worker.service')
    time.sleep(.3)
    p=ctl('-u','worker.service','-n','20',journal=True)
    results.append(dict(name='journal-output',pass_='service-ready' in p.stdout and 'reload-hook' in p.stdout))
    ctl('restart','worker.service')
    ctl('stop','worker.service')
    ctl('is-active','worker.service',expected=3)
    ctl('status','worker.service',expected=3)
    ctl('disable','worker.service')
    ctl('is-enabled','worker.service',expected=1)
    ctl('start','tick.timer'); time.sleep(1.2)
    p=ctl('-u','tick.service',journal=True)
    results.append(dict(name='repeating-timer-log',pass_=p.stdout.count('timer-fired')>=2))
    ctl('stop','tick.timer')
    ctl('start','unsupported.service',expected=1)
    ctl('start','unsupported.timer',expected=1)
    ctl('shutdown');manager.wait(timeout=15)
finally:
    if manager is not None and manager.poll() is None:
        try: ctl('shutdown');manager.wait(timeout=15)
        except Exception:manager.kill();manager.wait()
    (root/'results.json').write_text(json.dumps(results,indent=2))
for item in results:print(('PASS' if item['pass_'] else 'FAIL'),item.get('name',str(item.get('command'))),item.get('code',''),item.get('output','')[:100].replace('\n',' '))
print('RESULTS='+str(root/'results.json'))
raise SystemExit(1 if any(not item['pass_'] for item in results) else 0)
