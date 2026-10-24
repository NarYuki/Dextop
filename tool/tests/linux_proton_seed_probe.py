"""Run in the Linux guest with the already-downloaded, verified seed archive."""
import importlib.machinery
from pathlib import Path
import struct
import sys
import tempfile
from unittest.mock import patch

arm = importlib.machinery.SourceFileLoader('seed_arm', '/usr/local/bin/dextop-proton-arm64').load_module()
with tempfile.TemporaryDirectory(prefix='dextop-arm-seed-check-') as tmp:
    arm.STEAM = Path(tmp) / 'Steam'
    with patch.object(arm.urllib.request, 'urlopen', side_effect=lambda *_args, **_kwargs: open(sys.argv[1], 'rb')):
        arm.install()
    proton = arm.find_proton()
    assert proton is not None
    for name in ('wine', 'wineserver'):
        binary = proton / 'files/bin-arm64' / name
        with binary.open('rb') as source:
            header = source.read(20)
        assert header[:4] == b'\x7fELF' and struct.unpack('<H', header[18:20])[0] == 183
        assert binary.stat().st_mode & 0o111
    assert (arm.STEAM / 'steamapps/appmanifest_4427310.acf').is_file()
    print('ARM64_SEED_INSTALLER_HASH_EXTRACTION_NATIVE_ELF_OK')
