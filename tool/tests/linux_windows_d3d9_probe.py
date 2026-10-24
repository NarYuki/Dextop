from pathlib import Path
import os,subprocess,time,socket
steam=Path('/root/.local/share/Steam');proton=steam/'steamapps/common/Proton Experimental (ARM64)';env=dict(os.environ)
env.update(DISPLAY=':97',VK_ICD_FILENAMES='/opt/dextop-mesa/share/vulkan/icd.d/freedreno_icd.aarch64.json',MESA_VK_WSI_DEBUG='sw',STEAM_COMPAT_DATA_PATH='/tmp/dextop-policy-fixture/fresh-prefix',STEAM_COMPAT_CLIENT_INSTALL_PATH=str(steam),SteamAppId='0',SteamGameId='0',WINESERVER=str(proton/'files/bin-arm64/wineserver'),WINEDEBUG='-all,err+all',WINEDLLOVERRIDES='d3d9=n',DXVK_LOG_LEVEL='info')
for name in ('DEXTOP_XVFB_WSI','VK_INSTANCE_LAYERS','VK_LAYER_PATH','WINEPREFIX'):env.pop(name,None)
result=Path('/tmp/dextop-d3d9-probe.txt');result.unlink(missing_ok=True)
with open('/tmp/dextop-vulkan-probe-xvfb.log','w') as log:
 x=subprocess.Popen(['/usr/bin/Xvfb',':97','-screen','0','640x480x24','-ac','-noreset','-nolisten','tcp'],env=env,stdout=log,stderr=subprocess.STDOUT)
 try:
  for _ in range(100):
   if x.poll() is not None:raise RuntimeError('Probe Xvfb exited')
   try:
    with socket.socket(socket.AF_UNIX) as c:c.connect('/tmp/.X11-unix/X97')
    break
   except OSError:time.sleep(.1)
  else:raise RuntimeError('Probe Xvfb did not become ready')
  subprocess.run(['python3','/tmp/set-xvfb-refresh.py'],env=env,check=True)
  for bits in (32,64):
   result.unlink(missing_ok=True)
   with open('/tmp/dextop-policy-fixture/probe-'+str(bits)+'.log','w') as out:
    r=subprocess.run(['/usr/local/bin/dextop-proton-arm64','waitforexitandrun','/tmp/dextop-policy-fixture/probe'+str(bits)+'.exe','--vsync'],env=env,stdout=out,stderr=subprocess.STDOUT,timeout=150)
   report=result.read_text() if result.exists() else 'NO_PRESENT_REPORT'
   print('FRESH_PREFIX_IMPLICIT_POLICY_NO_OPTIN',bits,'EXIT',r.returncode,flush=True)
   print(report[-250:],flush=True)
   if r.returncode or 'frames=30 hr=00000000' not in report:raise RuntimeError('Fresh prefix graphics probe failed')

 finally:
  x.terminate()
  try:x.wait(timeout=5)
  except subprocess.TimeoutExpired:x.kill()