import importlib.machinery,os,pathlib,socket,struct,tempfile,threading
base=pathlib.Path(__file__).parent
m=importlib.machinery.SourceFileLoader('audit_unlock',str(base/'dextop-keyring-unlock')).load_module()
with tempfile.TemporaryDirectory(prefix='keyring-audit-') as tmp:
 capture=[];server=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);server.bind(tmp+'/control');server.listen(1)
 def receive():
  c,_=server.accept()
  with c:
   c.settimeout(2);data=b''
   while len(data)<13:data+=c.recv(13-len(data))
   length,operation,size=struct.unpack('>III',data[1:13]);secret=b''
   while len(secret)<size:secret+=c.recv(size-len(secret))
   capture.append(secret);c.sendall(struct.pack('>II',8,0))
 thread=threading.Thread(target=receive);thread.start()
 os.environ['GNOME_KEYRING_CONTROL']=tmp
 result=m.control_unlock(b'SYNTHETIC-ONLY-AUDIT-PASSWORD');thread.join(timeout=3);server.close()
 assert result is True and capture==[b'SYNTHETIC-ONLY-AUDIT-PASSWORD']
 print('CONFIRMED same-kernel-UID replacement socket receives synthetic password and is accepted')
 print('Structural same-UID limitation; not proof of access to Android Keystore')
