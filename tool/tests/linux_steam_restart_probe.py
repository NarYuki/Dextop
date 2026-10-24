"""Execute the packaged Steam launcher with synthetic client exit statuses."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

launcher = Path(sys.argv[1]).read_text()
for statuses in ([42, 0], [42, 42, 0], [0], [5], [42, 5]):
    with tempfile.TemporaryDirectory(prefix="dextop-steam-restart-") as directory:
        root = Path(directory)
        client = root / "Steam/steamrtarm64/steam"
        client.parent.mkdir(parents=True)
        client.write_text("""#!/usr/bin/python3
import json, os, pathlib, sys
root = pathlib.Path(os.environ['XDG_DATA_HOME'])
count_file = root / 'count'
count = int(count_file.read_text()) if count_file.exists() else 0
count_file.write_text(str(count + 1))
with (root / 'arguments').open('a') as out:
    out.write(json.dumps(sys.argv[1:]) + '\\n')
statuses = json.loads(os.environ['FAKE_STEAM_STATUSES'])
sys.exit(statuses[count] if count < len(statuses) else 99)
""")
        client.chmod(0o755)
        prepare = root / "prepare"
        prepare.write_text('#!/bin/sh\necho "$@" >>"$XDG_DATA_HOME/prepares"\n')
        prepare.chmod(0o755)
        preload = root / "preload-present"
        preload.touch()
        # Replace external dependencies only; keep the production launch/restart control flow.
        script = launcher.replace('/usr/local/bin/dextop-proton-arm64', str(prepare)).replace('/usr/local/lib/libblsession.so', str(preload))
        wrapper = root / 'launcher'
        wrapper.write_text(script)
        arguments = ['steam://open/library', 'argument with spaces', '', "quote'argument"]
        env = dict(os.environ, HOME=directory, XDG_DATA_HOME=directory, FAKE_STEAM_STATUSES=json.dumps(statuses))
        result = subprocess.run(['/bin/sh', str(wrapper), *arguments], env=env, capture_output=True, text=True, timeout=12)
        assert result.returncode == statuses[-1], (statuses, result.returncode, result.stderr)
        assert int((root / 'count').read_text()) == len(statuses), statuses
        assert (root / 'prepares').read_text().splitlines() == ['prepare'] * len(statuses)
        calls = [json.loads(line) for line in (root / 'arguments').read_text().splitlines()]
        assert all(call[-len(arguments):] == arguments for call in calls), calls
        print('STEAM_RESTART_SEQUENCE_OK', statuses, flush=True)
print('STEAM_UPDATE_RESTART_ARGUMENTS_AND_ERROR_EXIT_OK')
