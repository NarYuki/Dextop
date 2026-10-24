import base64,mmap,os,shlex,subprocess,tempfile
from pathlib import Path
lines=Path('/tmp/steam-persisted-installer.sh').read_text().splitlines()
with tempfile.TemporaryDirectory(prefix='dextop-install-repair-',dir='/tmp') as directory:
 commands=[];checks=[]
 for i,line in enumerate(lines):
  if '| base64 -d > /usr/local/' not in line:continue
  assert line.endswith('.staged')
  tokens=shlex.split(line);target=tokens[-1].removesuffix('.staged')
  path=Path(directory+target);path.parent.mkdir(parents=True,exist_ok=True)
  old=b'old-mapped-runtime'*512;path.write_bytes(old)
  f=path.open('rb');mapping=mmap.mmap(f.fileno(),0,access=mmap.ACCESS_READ)
  checks.append((path,f,mapping,old,base64.b64decode(tokens[2]),os.fstat(f.fileno()).st_ino))
  commands.extend(x.replace(target,str(path)) for x in lines[i:i+3])
 assert len(checks)==10,len(checks)
 script=Path(directory)/'setup.sh'
 script.write_text('set -e\n'+'\n'.join(commands))
 assert script.stat().st_size>131072
 subprocess.run(['sh','-c','exec /bin/sh '+str(script)],check=True)
 for path,f,mapping,old,new,inode in checks:
  assert mapping[:]==old
  assert os.fstat(f.fileno()).st_ino==inode
  assert path.stat().st_ino!=inode
  assert path.read_bytes()==new
  assert path.stat().st_mode&0o777==0o755
  assert not Path(str(path)+'.staged').exists()
  mapping.close();f.close()
 print('INSTALLER_FILE_EXEC_NO_ARGUMENT_LIMIT_ALL_10_RUNTIME_FILES_MAPPED_CONTENT_AND_PERMISSIONS_OK')
