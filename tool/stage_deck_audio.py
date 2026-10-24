"""Stage the verified Android/Pulse13 Deck host, without guest-system libraries."""
import hashlib
import json
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
DD = ROOT / 'build/droiddeck-current'
WORK = ROOT / 'build/deck-directaudio'
REVISION = '8aa1fdaf708eb44ef7cff7e493bda946989af511'
assert subprocess.check_output(['git', '-C', str(DD), 'rev-parse', 'HEAD'], text=True).strip() == REVISION
NOTICE = ROOT / 'third_party/deck-audio'
NOTICE.mkdir(parents=True, exist_ok=True)
files = {}

def stage(source, target):
    data = source.read_bytes()
    assert data.startswith(b'\x7fELF'), source
    # Reject local build paths rather than publishing the builder's user name.
    for prefix in (str(ROOT), ROOT.as_posix()):
        assert prefix.encode() not in data, f'Local build path in {source}; rebuild with -ffile-prefix-map'
    assert b'libc.so.6\0' not in data and b'libpulse.so.0\0' not in data, source
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    files[str(target.relative_to(ROOT)).replace('\\', '/')] = hashlib.sha256(data).hexdigest()

for name in ('libpulseaudio.so', 'libpulsecore-13.0.so', 'libpulsecommon-13.0.so',
             'libpulse.so', 'libltdl.so', 'libsndfile.so', 'libffi.so'):
    stage(DD / 'app/src/main/jniLibs/arm64-v8a' / name,
          ROOT / 'android/app/src/main/jniLibs/arm64-v8a' / name)
for name in ('module-native-protocol-tcp.so', 'libprotocol-native.so', 'module-directaudio-native-sink.so'):
    stage(WORK / 'modules' / name, ROOT / 'android/app/src/main/assets/deck-audio/modules' / name)
for name in ('module-directaudio-native-sink.c', 'da_relay_proto.h', 'COPYING'):
    shutil.copyfile(DD / 'tools/directaudio/sink' / name, NOTICE / name)
shutil.copyfile(WORK / 'source/pulseaudio-13.0/LICENSE', NOTICE / 'PULSEAUDIO-LICENSE')
shutil.copyfile(ROOT / 'third_party/libffi/LICENSE', NOTICE / 'LIBFFI-LICENSE')
(NOTICE / 'manifest.json').write_text(json.dumps({'droiddeck': REVISION, 'files': files}, indent=2) + '\n')
print(f'Staged {len(files)} Deck audio ELF files; hashes in third_party/deck-audio/manifest.json')
