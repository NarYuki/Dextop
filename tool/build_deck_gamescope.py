"""Build the pinned, Deck-only DroidDeck gamescope payload (requires zstandard)."""
import hashlib, io, json, tarfile
from pathlib import Path
import zstandard
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'android/app/src/main/resources/moe/n4tsu/dextop/linux/gaming'
PINS = {'gamescope': ('dd-gamescope-p12.tzst', '07b69944a1ebf2741d4a867afa6327dcc63ec458f432a8f7483089dce2197010'),
        'runtime': ('dd-linuxfs-r9.tar.zst', 'd3240c1531531615b0dd06b6ad56e92db9015a8641eb18c3a613835ae45992e7')}
files = {}
for name,(filename,digest) in PINS.items():
    path=ROOT/'build'/filename
    with path.open('rb') as f:
        if hashlib.file_digest(f,'sha256').hexdigest()!=digest:raise ValueError('Wrong '+filename)
    with path.open('rb') as f,zstandard.ZstdDecompressor().stream_reader(f) as r,tarfile.open(fileobj=r,mode='r|') as tar:
        for member in tar:
            n=member.name.removeprefix('./');leaf=n.rsplit('/',1)[-1]
            target=None
            if name=='gamescope' and n=='usr/local/bin/gamescope':target='bin/gamescope'
            if name=='runtime' and member.isfile():
                if n.startswith('usr/lib/'):
                    if leaf.startswith('libstdc++.so.6.'):target='lib/libstdc++.so.6'
                    elif leaf=='libgcc_s.so.1':target='lib/libgcc_s.so.1'
                    elif leaf.startswith('libwayland-') and '.so.' in leaf:target='lib/'+leaf.split('.so.')[0]+'.so.'+leaf.split('.so.')[1].split('.')[0]
                if '/licenses/wayland/' in n or '/licenses/libstdc++/' in n:target='licenses/'+n.removeprefix('usr/share/licenses/')
                if n=='usr/share/licenses/common/GPL3':target='licenses/GPL-3.0'
            if target and member.isfile():files[target]=tar.extractfile(member).read()
assert all(n in files for n in ['bin/gamescope','lib/libstdc++.so.6','lib/libgcc_s.so.1','lib/libwayland-server.so.0','lib/libwayland-client.so.0'])
files.setdefault('licenses/GPL-3.0',(ROOT/'LICENSE').read_bytes())
files['licenses/GAMESCOPE-LICENSE']=(ROOT/'third_party/deck-gamescope/GAMESCOPE-LICENSE').read_bytes()
files['NOTICE.md']=(ROOT/'third_party/deck-gamescope/NOTICE.md').read_bytes()
manifest={'version':'gamescope-3.16.29-p12','sources':{k:v[1] for k,v in PINS.items()},
          'files':{k:hashlib.sha256(v).hexdigest() for k,v in sorted(files.items())}}
files['manifest.json']=(json.dumps(manifest,sort_keys=True,indent=2)+'\n').encode()
payload=RES/'deck-gamescope.tar.xz'
with tarfile.open(payload,'w:xz') as tar:
    for n,data in sorted(files.items()):
        info=tarfile.TarInfo(n);info.size=len(data);info.mtime=0;info.mode=0o755 if n.startswith(('bin/','lib/')) else 0o644
        tar.addfile(info,io.BytesIO(data))
manifest['bundleSha256']=hashlib.sha256(payload.read_bytes()).hexdigest()
(RES/'deck-gamescope.json').write_text(json.dumps(manifest,sort_keys=True,indent=2)+'\n',encoding='utf-8',newline='\n')
print('DECK_GAMESCOPE_BUNDLE',payload.stat().st_size,manifest['bundleSha256'])
