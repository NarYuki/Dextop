import socket,threading,subprocess,os
from pathlib import Path
s=socket.socket();s.bind(('127.0.0.1',0));s.listen();s.settimeout(35)
def serve():
 try:
  c,_=s.accept();c.settimeout(10)
  with c:
   if c.recv(4)==b'ping':c.sendall(b'pong')
 except OSError as e:print('SERVER_ERROR',type(e).__name__)
t=threading.Thread(target=serve,daemon=True);t.start()
p=Path('/root/.local/share/Steam/steamapps/common/Proton Experimental (ARM64)/files/bin-arm64')
e=dict(os.environ,WINEPREFIX='/root/.local/share/Steam/steamapps/compatdata/12210-dextop-arm64/pfx',WINESERVER=str(p/'wineserver'),WINEDEBUG='-all')
r=subprocess.run(['/usr/local/bin/dextop-proton-arm64','--exec',str(p/'wine'),'/tmp/winsock-async-probe.exe',str(s.getsockname()[1])],env=e,timeout=40)
print('WINSOCK_PROBE_EXIT',r.returncode,flush=True);s.close();raise SystemExit(r.returncode)