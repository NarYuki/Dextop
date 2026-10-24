"""Bounded, read-only guest-side snapshots for a user-reproduced CEF hang.

Does not open, navigate, restart, signal or attach to any browser. Run with the
existing guest Python, redirect stdout to a diagnostic log, then reproduce the
browser problem manually. Command-line URLs and account identifiers are omitted.
"""
import argparse
import json
import pathlib
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--duration', type=int, default=90)
args = parser.parse_args()
if not 1 <= args.duration <= 120:
    parser.error('duration must be between 1 and 120 seconds')

def read(path):
    try:
        return path.read_text().strip()
    except (OSError, PermissionError):
        return None

def snapshot(proc):
    command = read(proc / 'cmdline')
    if not command or pathlib.PurePosixPath(command.split('\0')[0]).name != 'steamwebhelper':
        return None
    kind = next((a for a in command.split('\0') if a.startswith('--type=')), 'browser')
    threads = []
    try:
        tasks = list((proc / 'task').iterdir())
    except OSError:
        return None
    for task in tasks:
        name = read(task / 'comm')
        if not name:
            continue
        if task.name != proc.name and not any(p in name for p in ('Chrome', 'CrRenderer', 'Compositor', 'Gpu', 'Render')):
            continue
        stat = read(task / 'stat')
        fields = stat.rsplit(')', 1)[-1].split() if stat else []
        threads.append({'tid': int(task.name), 'name': name, 'wchan': read(task / 'wchan'),
                        'state': fields[0] if fields else None,
                        'user_ticks': int(fields[11]) if len(fields) > 12 else None,
                        'system_ticks': int(fields[12]) if len(fields) > 12 else None,
                        'syscall': read(task / 'syscall')})
    return {'pid': int(proc.name), 'kind': kind, 'threads': threads}

end = time.monotonic() + args.duration
known = set()
next_sample = 0
while time.monotonic() < end:
    now = time.monotonic()
    current = set()
    for proc in pathlib.Path('/proc').glob('[0-9]*'):
        name = read(proc / 'comm')
        if name != 'steamwebhelper':
            continue
        current.add(proc.name)
        if proc.name not in known or now >= next_sample:
            data = snapshot(proc)
            if data:
                print(json.dumps({'monotonic': now, **data}), flush=True)
    for pid in known - current:
        print(json.dumps({'monotonic': now, 'exited_pid': int(pid)}), flush=True)
    known = current
    if now >= next_sample:
        next_sample = now + 15
    time.sleep(1)
print(json.dumps({'complete': True, 'monotonic': time.monotonic()}), flush=True)
