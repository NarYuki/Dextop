import socket,struct,array,os,stat,sys,uuid
with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as c:
 c.settimeout(10);c.connect('\0'+sys.argv[1]);c.sendall(struct.pack('!III',0x44465031,4,0)+uuid.uuid4().hex.encode())
 data,anc,flags,addr=c.recvmsg(4,socket.CMSG_SPACE(16))
 while len(data)<4:
  part=c.recv(4-len(data))
  if not part:raise RuntimeError('Endpoint closed mid-response')
  data+=part
 print('ANONYMOUS_PIPE_TRANSFER_RESPONSE',struct.unpack('!I',data)[0],flush=True)
 for level,kind,body in anc:
  if level==socket.SOL_SOCKET and kind==socket.SCM_RIGHTS:
   fds=array.array('i');fds.frombytes(body[:len(body)//fds.itemsize*fds.itemsize])
   for fd in fds:
    print('ANONYMOUS_PIPE_FD','fifo',stat.S_ISFIFO(os.fstat(fd).st_mode),'eof',os.read(fd,1)==b'',flush=True);os.close(fd)