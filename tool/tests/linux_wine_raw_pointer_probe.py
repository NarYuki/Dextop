from pathlib import Path
import os,subprocess,time,json
out=Path('/tmp/dextop-raw-pointer.log');out.unlink(missing_ok=True)
e=dict(os.environ,DISPLAY=':98',DBUS_SESSION_BUS_ADDRESS='unix:abstract=/tmp/dextop-dbus-98',WINEPREFIX='/tmp/dextop-policy-fixture/fresh-prefix-dextop-arm64/pfx',WINESERVER='/root/.local/share/Steam/steamapps/common/Proton Experimental (ARM64)/files/bin-arm64/wineserver',WINEDEBUG='-all')
with open('/tmp/dextop-raw-pointer-launch.log','w')as log:
 p=subprocess.Popen(['/usr/local/bin/dextop-proton-arm64','--exec','/root/.local/share/Steam/steamapps/common/Proton Experimental (ARM64)/files/bin-arm64/wine','/tmp/windows-raw-pointer-probe32.exe'],env=e,stdout=log,stderr=subprocess.STDOUT)
 sent=0;start=time.monotonic()
 while p.poll() is None and time.monotonic()-start<25:
  if out.exists()and 'WINE_RAW_READY' in out.read_text()and sent<10:
   with open('/tmp/pointer-probe-actions.jsonl','a')as f:f.write(json.dumps(dict(dx=7,dy=-3))+'\n')
   sent+=1
  time.sleep(.1)
 if p.poll()is None:p.kill();raise RuntimeError('Wine raw probe timeout')
 assert p.returncode==0,(p.returncode,out.read_text()if out.exists()else 'NO_REPORT')
 print(out.read_text(),end='')
