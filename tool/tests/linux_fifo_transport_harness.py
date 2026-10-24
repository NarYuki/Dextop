import os,socket,struct,array,threading,subprocess,uuid,sys
from pathlib import Path
name='dextop-fifo-probe-'+uuid.uuid4().hex
server=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);server.bind('\0'+name);server.listen();server.settimeout(.5)
config=Path('/tmp/dextop-fifo-test-bridge');config.write_text(name+'\n');stop=threading.Event();counts={1:0,2:0,3:0};ids=set()
def serve():
 while not stop.is_set():
  try:c,_=server.accept()
  except socket.timeout:continue
  except OSError:break
  with c:
   data=b''
   while len(data)<44:
    part=c.recv(44-len(data))
    if not part:break
    data+=part
   if len(data)!=44:continue
   magic,op,flags=struct.unpack('!III',data[:12]);ident=data[12:]
   if magic!=0x44465031 or op not in counts:c.sendall(struct.pack('!I',22));continue
   counts[op]+=1
   if op==1:ids.add(ident);c.sendall(struct.pack('!I',0))
   elif op==3:ids.discard(ident);c.sendall(struct.pack('!I',0))
   elif ident not in ids:c.sendall(struct.pack('!I',2))
   else:
    r,w=os.pipe2(os.O_NONBLOCK|os.O_CLOEXEC)
    os.write(w,b'fifo');os.close(w)
    if flags & os.O_PATH:
     metadata=os.open('/proc/self/fd/'+str(r),os.O_PATH|os.O_CLOEXEC);os.close(r);r=metadata
    try:c.sendmsg([struct.pack('!I',0)],[(socket.SOL_SOCKET,socket.SCM_RIGHTS,array.array('i',[r]))])
    finally:os.close(r)
t=threading.Thread(target=serve,daemon=True);t.start()
try:
 e=dict(os.environ);arch=sys.argv[1] if len(sys.argv)>1 else 'aarch64'
 if arch=='aarch64':
  e['LD_PRELOAD']='/tmp/libdextop-fifo-test.so:'+e.get('LD_PRELOAD','');command=['/tmp/linux-fifo-probe']
 else:
  e['LD_PRELOAD']='/tmp/libdextop-fifo-test-'+arch+'.so';command=['/usr/local/bin/dextop-gaming','--exec','/tmp/linux-fifo-probe-'+arch]
 r=subprocess.run(command,env=e,timeout=20)
 print('FIFO_PROBE_EXIT',r.returncode,'REQUEST_COUNTS',counts,'LIVE_BACKENDS',len(ids),flush=True)
finally:
 stop.set();server.close();config.unlink(missing_ok=True)
raise SystemExit(r.returncode)
