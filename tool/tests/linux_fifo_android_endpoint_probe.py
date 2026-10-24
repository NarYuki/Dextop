import socket,struct,array,os,stat,sys,uuid,select
name=sys.argv[1];ident=uuid.uuid4().hex.encode()
def request(op,flags=0):
 with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as c:
  c.settimeout(10);c.connect('\0'+name);c.sendall(struct.pack('!III',0x44465031,op,flags)+ident)
  data,anc,fl,addr=c.recvmsg(4,socket.CMSG_SPACE(16))
  while len(data)<4:
   part=c.recv(4-len(data))
   if not part:break
   data+=part
  if len(data)!=4:raise RuntimeError('FIFO endpoint closed without response')
  error=struct.unpack('!I',data)[0]
  if error:raise OSError(error,os.strerror(error))
  fds=[]
  for level,kind,body in anc:
   if level==socket.SOL_SOCKET and kind==socket.SCM_RIGHTS:
    a=array.array('i');a.frombytes(body[:len(body)//a.itemsize*a.itemsize]);fds.extend(a)
  if op==2:
   if len(fds)!=1:raise RuntimeError('Expected one FIFO descriptor')
   return fds[0]
  for fd in fds:os.close(fd)
request(1,0o640)
try:
 r=request(2,os.O_RDONLY|os.O_NONBLOCK|os.O_CLOEXEC)
 try:
  metadata=os.fstat(r);assert stat.S_ISFIFO(metadata.st_mode) and stat.S_IMODE(metadata.st_mode)==0o640
  assert os.read(r,8)==b''
  w=request(2,os.O_WRONLY|os.O_NONBLOCK|os.O_CLOEXEC)
  try:
   try:os.read(r,8);raise AssertionError('Reader did not wait for writer data')
   except BlockingIOError:pass
   assert os.write(w,b'fifo')==4 and os.read(r,8)==b'fifo'
  finally:os.close(w)
  poll=select.poll();poll.register(r,select.POLLIN|select.POLLHUP);assert poll.poll(2000)
  assert os.read(r,8)==b''
  print('ANDROID_FIFO_CROSS_UID_TRANSFER_REAL_MODE_NONBLOCK_DATA_LAST_WRITER_EOF_OK','backend_uid',metadata.st_uid,flush=True)
 finally:os.close(r)
finally:request(3)
