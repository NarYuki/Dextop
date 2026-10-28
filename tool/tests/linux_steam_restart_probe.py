"""Execute the packaged Steam launcher with synthetic client exit statuses."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

launcher = Path(sys.argv[1]).read_text()
cases = [([42, 0], [1, 1]), ([42, 42, 0], [1, 1, 1]), ([0], [1]),
         ([5], [1]), ([42, 5], [1, 1]), ([42, 42, 42], [1, 1, 1]),
         ([42, 42, 42, 0], [1, 60, 1, 1])]
for statuses, durations in cases:
    with tempfile.TemporaryDirectory(prefix="dextop-steam-restart-") as directory:
        root = Path(directory)
        client = root / "Steam/steamrtarm64/steam"
        client.parent.mkdir(parents=True)
        (root / 'Steam/package').mkdir()
        (root / 'Steam/package/beta').write_text('steamdeck_publicbeta\n')
        client.write_text("""#!/usr/bin/python3
import json, os, pathlib, sys
root = pathlib.Path(os.environ['XDG_DATA_HOME'])
count_file = root / 'count'
count = int(count_file.read_text()) if count_file.exists() else 0
count_file.write_text(str(count + 1))
with (root / 'arguments').open('a') as out:
    out.write(json.dumps(sys.argv[1:]) + '\\n')
statuses = json.loads(os.environ['FAKE_STEAM_STATUSES'])
if count == 0 and statuses[count] == 42:
    (root / 'Steam/package/beta').write_text('')
sys.exit(statuses[count] if count < len(statuses) else 99)
""")
        client.chmod(0o755)
        prepare = root / "prepare"
        prepare.write_text('#!/bin/sh\necho "$@" >>"$XDG_DATA_HOME/prepares"\n')
        prepare.chmod(0o755)
        clock = root / 'bin/date'
        clock.parent.mkdir()
        clock.write_text("""#!/usr/bin/python3
import json, os, pathlib
root = pathlib.Path(os.environ['XDG_DATA_HOME'])
path = root / 'clock'
count = int(path.read_text()) if path.exists() else 0
path.write_text(str(count + 1))
durations = json.loads(os.environ['FAKE_DURATIONS'])
print(1000 + sum(durations[:count // 2]) + (durations[count // 2] if count % 2 else 0))
""")
        clock.chmod(0o755)
        preload = root / "preload-present"
        preload.touch()
        installer = root / 'installer'
        installer.write_text('#!/bin/sh\necho "$@" >>"$XDG_DATA_HOME/syncs"\n')
        installer.chmod(0o755)
        # Replace external dependencies only; keep the production launch/restart control flow.
        script = launcher.replace('/usr/local/bin/dextop-proton-arm64', str(prepare)).replace('/usr/local/lib/libblsession.so', str(preload))
        script = script.replace('/usr/local/bin/dextop-steam-install', str(installer))
        script = script.replace('/usr/local/bin/dextop-steam-state', '/bin/true')
        wrapper = root / 'launcher'
        wrapper.write_text(script)
        arguments = ['steam://open/library', 'argument with spaces', '', "quote'argument"]
        env = dict(os.environ, HOME=directory, XDG_DATA_HOME=directory, FAKE_STEAM_STATUSES=json.dumps(statuses),
                   FAKE_DURATIONS=json.dumps(durations), PATH=str(clock.parent) + os.pathsep + os.environ["PATH"])
        result = subprocess.run(['/bin/sh', str(wrapper), *arguments], env=env, capture_output=True, text=True, timeout=12)
        assert result.returncode == (1 if statuses == [42, 42, 42] else statuses[-1]), (statuses, result.returncode, result.stderr)
        assert int((root / 'count').read_text()) == len(statuses), statuses
        assert (root / 'prepares').read_text().splitlines() == ['prepare'] * len(statuses)
        calls = [json.loads(line) for line in (root / 'arguments').read_text().splitlines()]
        assert calls[0][-len(arguments):] == arguments, calls
        restarted = calls[0][:-len(arguments)]
        restarted[restarted.index('-clientbeta') + 1] = ''
        assert all(call == restarted for call in calls[1:]), calls
        assert not (root / 'syncs').exists(), 'Update restarts must be handled by Steam itself'
        assert all('-skipinitialbootstrap' not in call for call in calls), calls
        print('STEAM_RESTART_SEQUENCE_OK', statuses, flush=True)
print('STEAM_DD_UPDATE_RESTART_AND_60_SECOND_RESET_OK')
