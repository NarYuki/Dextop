"""Package the separately built ARM64 Mesa runtime and its upstream licenses."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import tarfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--archive', type=Path, required=True)
licenses = parser.add_mutually_exclusive_group(required=True)
licenses.add_argument('--distribution', type=Path)
licenses.add_argument('--license-bundle', type=Path)
args = parser.parse_args()
if args.distribution:
    assert hashlib.sha256(args.distribution.read_bytes()).hexdigest() == 'c5e3d7cfb62c81835892a0fa8996e1c1e666f47d8a5d84d3b88689861ed68636'
files = {}
with tarfile.open(args.archive) as archive:
    for member in archive:
        if member.isfile() and (member.name.startswith('lib/aarch64-linux-gnu/') and '/pkgconfig/' not in member.name or member.name.startswith('licenses/')):
            assert '..' not in Path(member.name).parts
            files[member.name] = archive.extractfile(member).read()
if args.distribution:
    with zipfile.ZipFile(args.distribution) as archive:
        for name in archive.namelist():
            if name.startswith('LICENSES/mesa/') and not name.endswith('/'):
                files['licenses/mesa/' + name.removeprefix('LICENSES/mesa/')] = archive.read(name)
else:
    with tarfile.open(args.license_bundle) as archive:
        manifest = json.load(archive.extractfile('manifest.json'))
        assert manifest['sourceSha256'] == '834054084604cacb2fe82d9151256bac83fd353d50b602f6824a1ce8ae73a3a8'
        for name, digest in manifest['files'].items():
            if name.startswith('licenses/mesa/'):
                data = archive.extractfile(name).read()
                assert hashlib.sha256(data).hexdigest() == digest
                files[name] = data
assert any(name.startswith('licenses/mesa/') for name in files)
root = Path(__file__).resolve().parents[1]
files['licenses/GPL-3.0'] = (root / 'LICENSE').read_bytes()
files['share/glvnd/egl_vendor.d/50_mesa.json'] = b'{"file_format_version":"1.0.0","ICD":{"library_path":"libEGL_mesa.so.0"}}\n'
links = {
    'lib/aarch64-linux-gnu/libEGL_mesa.so.0': 'libEGL_mesa.so.0.0.0',
    'lib/aarch64-linux-gnu/libGLX_mesa.so.0': 'libGLX_mesa.so.0.0.0',
    'lib/aarch64-linux-gnu/libgbm.so.1': 'libgbm.so.1.0.0',
    'lib/aarch64-linux-gnu/dri/zink_dri.so': 'libdril_dri.so',
}
for name in links:
    files.pop(name, None)
metadata = {'mesa': 'c90ff433291a5c3ff5cea9913bbebeef9cd4e885',
            'sourceSha256': '834054084604cacb2fe82d9151256bac83fd353d50b602f6824a1ce8ae73a3a8',
            'glibcMinimum': '2.38',
            'patches': {'tool/patch_linux_gpu_runtime.py': hashlib.sha256((root/'tool/patch_linux_gpu_runtime.py').read_bytes()).hexdigest()},
            'files': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(files.items())},
            'links': links}
files['manifest.json'] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
output = root / 'android/app/src/main/resources/moe/n4tsu/dextop/linux/gpu'
output.mkdir(exist_ok=True)
payload = output / 'mesa-direct.tar.xz'
with tarfile.open(payload, 'w:xz') as archive:
    for name, data in sorted(files.items()):
        entry = tarfile.TarInfo(name)
        entry.size = len(data)
        entry.mode = 0o644
        archive.addfile(entry, io.BytesIO(data))
    for name, target in sorted(links.items()):
        entry = tarfile.TarInfo(name)
        entry.type = tarfile.SYMTYPE
        entry.linkname = target
        entry.mode = 0o777
        archive.addfile(entry)
metadata['bundleSha256'] = hashlib.sha256(payload.read_bytes()).hexdigest()
(output / 'mesa-direct.json').write_text(json.dumps(metadata, indent=2, sort_keys=True) + '\n', encoding='utf-8', newline='\n')
print(payload.stat().st_size, metadata['bundleSha256'])
