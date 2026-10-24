from pathlib import Path
import os,subprocess,time,json
out=Path('/tmp/dextop-wine-tray-probe.log');out.unlink(missing_ok=True)
e=dict(os.environ,DISPLAY=':98',DBUS_SESSION_BUS_ADDRESS='unix:abstract=/tmp/dextop-dbus-98',WINEPREFIX='/tmp/dextop-policy-fixture/fresh-prefix-dextop-arm64/pfx',WINESERVER='/root/.local/share/Steam/steamapps/common/Proton Experimental (ARM64)/files/bin-arm64/wineserver',WINEDEBUG='-all,err+all')
event_path=Path('/tmp/tray-probe-events.jsonl')
start_index=len(event_path.read_text().splitlines()) if event_path.exists() else 0
subprocess.run(['/usr/local/bin/dextop-proton-arm64','--exec',e['WINESERVER'],'-k'],env=e,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=10)
subprocess.run(['sh','/tmp/display-480-640-120.sh'],env=e,check=True)
start=time.monotonic()
with open('/tmp/tray-probe-wine-launch.log','w') as log:
 p=subprocess.Popen(['/usr/local/bin/dextop-proton-arm64','--exec','/root/.local/share/Steam/steamapps/common/Proton Experimental (ARM64)/files/bin-arm64/wine','/tmp/windows-tray-probe32.exe'],env=e,stdout=log,stderr=subprocess.STDOUT)
 key=None
 while time.monotonic()-start<65:
  path=Path('/tmp/tray-probe-events.jsonl')
  entries=[json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []
  icons=[v for v in entries[start_index:] if v.get('type')=='tray' and v.get('key','').startswith('x11:') and v.get('title')=='Dextop Wine Tray Probe']
  second=[v for v in entries[start_index:] if v.get('type')=='tray' and v.get('title')=='Dextop Wine Second Icon']
  if icons and second and icons[-1].get('probeCorner',-1)==0 and not key:
   assert icons[-1]['probePixel']&0xffffff==0xff0000,icons[-1]
   assert second[-1]['probePixel']&0xffffff==0x00ff00,second[-1]
   assert second[-1]['probeCorner']==0,second[-1]
   key=icons[-1]['key']
   with open('/tmp/tray-probe-actions.jsonl','a') as f:
    for button in [1,3]:f.write(json.dumps(dict(key=key,button=button,x=80,y=80))+'\n')
  if p.poll() is not None:break
  time.sleep(.05)
 if p.poll() is None:p.kill();raise RuntimeError('Wine tray probe timed out')
 assert p.returncode==0,(p.returncode,out.read_text() if out.exists() else 'NO_REPORT')
 assert key and 'WINE_TRAY_ALL_OK' in out.read_text()
 assert 'WINE_SESSION_120HZ_OK' in out.read_text()
 assert not any(v.get('type')=='map' and v.get('title')=='Wine System Tray' for v in entries)
 print('WINE_SHELL_NOTIFYICON_CORRECT_TITLE_AGENT_SHUTDOWN_PRIMARY_CONTEXT_OK')
