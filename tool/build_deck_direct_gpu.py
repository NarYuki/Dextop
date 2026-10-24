"""Package the pinned Deck WSI driver and glibc AHB allocation proxy."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA = '834054084604cacb2fe82d9151256bac83fd353d50b602f6824a1ce8ae73a3a8'
parser = argparse.ArgumentParser()
parser.add_argument('--driver', type=Path, required=True)
parser.add_argument('--proxy', type=Path, required=True)
parser.add_argument('--source', type=Path, required=True)
parser.add_argument('--distribution', type=Path, required=True)
args = parser.parse_args()
with args.source.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == SOURCE_SHA
driver = args.driver.read_bytes()
assert b'banner_ahb_v1' in driver and b'BANNER_WSI_AHB' in driver
files = {
    'lib/libvulkan_freedreno.so': driver,
    'lib/libnativewindow.so': args.proxy.read_bytes(),
    'icd.json': (json.dumps({'file_format_version': '1.0.0', 'ICD': {
        'library_path': './lib/libvulkan_freedreno.so', 'api_version': '1.4.363'}}) + '\n').encode(),
    'NOTICE.md': (ROOT/'third_party/deck-direct/NOTICE.md').read_bytes(),
    'licenses/GPL-3.0': (ROOT/'LICENSE').read_bytes(),
    'licenses/banner-ahb-v1.xml': (ROOT/'third_party/deck-direct/banner/banner-ahb-v1.xml').read_bytes(),
}
with args.distribution.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest() == 'c5e3d7cfb62c81835892a0fa8996e1c1e666f47d8a5d84d3b88689861ed68636'
with zipfile.ZipFile(args.distribution) as archive:
    for name in archive.namelist():
        if name.startswith('LICENSES/mesa/') and not name.endswith('/'):
            files['licenses/mesa/'+name.removeprefix('LICENSES/mesa/')] = archive.read(name)
assert any(name.startswith('licenses/mesa/') for name in files)
metadata = {
    'version': 'dd-turnip-0.3.3-deck-ahb-1',
    'mesa': 'c90ff433291a5c3ff5cea9913bbebeef9cd4e885',
    'sourceSha256': SOURCE_SHA,
    'bannerCommit': '558866f688e9a99b6eac461162a14038b3120964',
    'files': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(files.items())},
}
files['manifest.json'] = (json.dumps(metadata, sort_keys=True, indent=2)+'\n').encode()
out = ROOT/'android/app/src/main/resources/moe/n4tsu/dextop/linux/gaming'
payload = out/'deck-direct-gpu.tar.xz'
with tarfile.open(payload, 'w:xz') as archive:
    for name, data in sorted(files.items()):
        entry = tarfile.TarInfo(name)
        entry.size = len(data)
        entry.mtime = 0
        entry.mode = 0o755 if name.startswith('lib/') else 0o644
        archive.addfile(entry, io.BytesIO(data))
metadata['bundleSha256'] = hashlib.sha256(payload.read_bytes()).hexdigest()
(out/'deck-direct-gpu.json').write_text(json.dumps(metadata, sort_keys=True, indent=2)+'\n', encoding='utf-8', newline='\n')
print('Deck AHB GPU bundle', payload.stat().st_size, metadata['bundleSha256'])
