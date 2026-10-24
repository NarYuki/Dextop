"""Verify the real Deck installer without touching a system installation."""
import json, os, runpy, shutil, sys, tempfile
from pathlib import Path
helper, resources = map(Path, sys.argv[1:])
with tempfile.TemporaryDirectory(prefix='deck-gamescope-install-') as folder:
    root = Path(folder)
    payload = root/'deck-gamescope.tar.xz'; metadata = root/'deck-gamescope.json'
    shutil.copyfile(resources/payload.name, payload); shutil.copyfile(resources/metadata.name, metadata)
    namespace = runpy.run_path(str(helper)); prepare = namespace['prepare']; scope = prepare.__globals__
    scope.update(PAYLOAD=payload,METADATA=metadata,INSTALL=root/'install')
    before = dict(os.environ)
    installed = prepare(); inode = (installed/'bin/gamescope').stat().st_ino
    assert os.access(installed/'bin/gamescope',os.X_OK)
    assert (installed/'lib/libstdc++.so.6').is_file()
    assert (installed/'licenses/GPL-3.0').is_file()
    assert prepare()==installed and (installed/'bin/gamescope').stat().st_ino==inode
    assert dict(os.environ)==before
    scope['INSTALL']=root/'invalid-install'
    data=bytearray(payload.read_bytes());data[-1]^=1;payload.write_bytes(data)
    try:prepare();raise AssertionError('Corrupt payload was accepted')
    except ValueError as error:assert 'checksum' in str(error)
    assert not list(scope['INSTALL'].glob('.staged-*'))
    assert not (scope['INSTALL']/installed.name).exists()
    print('PASS verified fresh install, repeat install, isolated environment and corrupt-payload rejection')
