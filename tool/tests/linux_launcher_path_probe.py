"""Read-only audit of native desktop Exec resolution inside a running container.

Does not run application executables or modify links/package files.
"""
import configparser
import os
from pathlib import Path
import shlex
import shutil

paths = ['/usr/share/applications', '/usr/local/share/applications',
         '/root/.local/share/applications']
extra = ['/usr/local/games', '/usr/games', '/root/.local/bin', '/opt/bin']
old_path = os.environ.get('PATH', '')
new_path = ':'.join(dict.fromkeys(old_path.split(':') + extra))
entries = {}
for directory in paths:
    for path in Path(directory).glob('*.desktop'):
        entries[path.name] = path


def executable(line):
    words = shlex.split(line)
    if not words:
        return None
    if Path(words[0]).name == 'env':
        words.pop(0)
        while words:
            if words[0] in ('-u', '--unset'):
                del words[:2]
            elif words[0].startswith('-') or '=' in words[0]:
                words.pop(0)
            else:
                break
    return words[0] if words else None


fixed, unresolved, absolute = 0, 0, 0
for name, path in sorted(entries.items()):
    config = configparser.ConfigParser(interpolation=None, strict=False)
    try:
        config.read(path)
        entry = config['Desktop Entry']
        if entry.get('Type') != 'Application' or entry.get('Hidden') == 'true' or entry.get('NoDisplay') == 'true':
            continue
        command = executable(entry.get('Exec', ''))
        if not command:
            continue
        before = shutil.which(command, path=old_path)
        after = shutil.which(command, path=new_path)
        if not before and after:
            print('PATH_FIXED', name, command, '->', after)
            fixed += 1
        elif not after:
            print('UNRESOLVED', name, command)
            unresolved += 1
        elif command.startswith('/opt/'):
            print('OPT_EXEC_OK', name, command)
            absolute += 1
    except (OSError, ValueError, configparser.Error):
        print('INVALID_ENTRY', name)
print('SUMMARY', 'path_fixes=' + str(fixed), 'unresolved=' + str(unresolved), 'opt_launchers=' + str(absolute))
