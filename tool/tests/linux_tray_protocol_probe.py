"""Headless protocol clients for an isolated X display and its StatusNotifier bus."""
from pathlib import Path
import base64,ctypes as C,json,os,select,socket,struct,time
ROOT=Path('/tmp'); EVENTS=ROOT/'tray-probe-events.jsonl'; ACTIONS=ROOT/'tray-probe-actions.jsonl'
DISPLAY=98

def events():
 if not EVENTS.exists():return []
 return [json.loads(s) for s in EVENTS.read_text().splitlines() if s.strip()]
def wait(predicate,description,timeout=12,pump=None):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if pump:pump()
  value=predicate()
  if value:return value
  time.sleep(.03)
 raise AssertionError(description)
def action(key,button=1,**extra):
 with ACTIONS.open('a') as f:f.write(json.dumps(dict(key=key,button=button,x=80,y=80,**extra))+'\n')

def xembed():
 x=C.CDLL('libX11.so.6'); P=C.c_void_p; U=C.c_ulong; I=C.c_int
 def api(name,result,*args):
  fn=getattr(x,name);fn.restype=result;fn.argtypes=list(args);return fn
 d=api('XOpenDisplay',P,C.c_char_p)(b':98');assert d
 root=api('XDefaultRootWindow',U,P)(d)
 atom=lambda name:api('XInternAtom',U,P,C.c_char_p,I)(d,name.encode(),0)
 owner=api('XGetSelectionOwner',U,P,U)(d,atom('_NET_SYSTEM_TRAY_S0'));assert owner
 win=api('XCreateSimpleWindow',U,P,U,I,I,C.c_uint,C.c_uint,C.c_uint,U,U)(d,root,0,0,32,32,0,0,0x33aaff)
 api('XSelectInput',I,P,U,C.c_long)(d,win,(1<<17)|(1<<22)|(1<<15)|(1<<2)|(1<<3))
 change=api('XChangeProperty',I,P,U,U,U,I,I,P,I)
 title=C.create_string_buffer(b'Dextop XEmbed Probe');change(d,win,atom('_NET_WM_NAME'),atom('UTF8_STRING'),8,0,C.cast(title,P),len(title.value))
 info=atom('_XEMBED_INFO')
 def mapped(value):
  data=(U*2)(0,value);change(d,win,info,info,32,0,C.cast(data,P),2);x.XFlush(d)
 # Xlib's long data occupies native longs even though the wire format is CARD32.
 class Data(C.Union):_fields_=[('b',C.c_char*20),('s',C.c_short*10),('l',C.c_long*5)]
 class Message(C.Structure):_fields_=[('type',I),('serial',U),('send_event',I),('display',P),('window',U),('message_type',U),('format',I),('data',Data)]
 class Button(C.Structure):_fields_=[('type',I),('serial',U),('send_event',I),('display',P),('window',U),('root',U),('subwindow',U),('time',U),('x',I),('y',I),('x_root',I),('y_root',I),('state',C.c_uint),('button',C.c_uint),('same_screen',I)]
 class Event(C.Union):_fields_=[('type',I),('message',Message),('button',Button),('pad',C.c_long*24)]
 flush=api('XFlush',I,P);pending=api('XPending',I,P);nxt=api('XNextEvent',I,P,P)
 # Replace ctypes' default argument conversion for all pointer-accepting functions.
 x.XFlush=flush
 mapped(1)
 message=Event();message.message.type=33;message.message.display=d;message.message.window=owner;message.message.message_type=atom('_NET_SYSTEM_TRAY_OPCODE');message.message.format=32;message.message.data.l[1]=0;message.message.data.l[2]=win
 api('XSendEvent',I,P,U,I,C.c_long,P)(d,owner,0,0,C.byref(message));flush(d)
 observed=[]
 def pump():
  while pending(d):
   e=Event();nxt(d,C.byref(e))
   if e.type==33:observed.append(('message',e.message.message_type,e.message.data.l[1],e.message.data.l[3]))
   if e.type in (4,5):observed.append(('button',e.type,e.button.button))
 wait(lambda:any(e[0]=='message' and e[1]==atom('_XEMBED') and e[2]==0 and e[3]!=owner and e[3]!=0 for e in observed),'XEmbed embedded notification',pump=pump)
 api('XClearWindow',I,P,U)(d,win);flush(d)
 key='x11:'+str(win)
 event=wait(lambda:next((e for e in reversed(events()) if e.get('type')=='tray' and e.get('key')==key and e.get('title')=='Dextop XEmbed Probe'),None),'XEmbed tray image and title',pump=pump)
 assert event['probePixel']&0xffffff==0x33aaff,event
 assert event['title']=='Dextop XEmbed Probe',event
 assert not any(e.get('type')=='map' and e.get('xid')==win for e in events()),'Tray incorrectly exposed as app window'
 for button in (1,3):
  action(key,button)
  wait(lambda:('button',5,button) in observed,'XEmbed button '+str(button),pump=pump)
 count=len(events());mapped(0)
 wait(lambda:any(e.get('type')=='tray-remove' and e.get('key')==key for e in events()[count:]),'XEmbed mapped flag removal',pump=pump)
 count=len(events());mapped(1)
 wait(lambda:any(e.get('type')=='tray' and e.get('key')==key for e in events()[count:]),'XEmbed remapping',pump=pump)
 api('XDestroyWindow',I,P,U)(d,win);flush(d)
 wait(lambda:events()[-1].get('type')=='tray-remove' and events()[-1].get('key')==key,'XEmbed destroy cleanup',pump=pump)
 api('XCloseDisplay',I,P)(d)
 print('XEMBED_REGISTER_PIXELS_PRIMARY_CONTEXT_HIDE_REMAP_DESTROY_NO_APP_WINDOW_OK',flush=True)

class Writer:
 def __init__(self,start=0):self.data=bytearray();self.start=start
 def align(self,n):self.data.extend(b'\0'*((-(len(self.data)+self.start))%n))
 def put(self,sig,value):
  c=sig[0]
  if c=='y':self.data.append(value&255)
  elif c in 'uib':self.align(4);self.data.extend(struct.pack('<I',int(value)&0xffffffff))
  elif c in 'so':
   value=value.encode();self.put('u',len(value));self.data.extend(value+b'\0')
  elif c=='g':value=value.encode();self.data.extend(bytes([len(value)])+value+b'\0')
  elif c=='v':self.put('g',value[0]);self.put(*value)
  elif c=='a':
   sub=sig[1:];self.align(4);at=len(self.data);self.put('u',0)
   self.align(8 if sub[0] in '({' else 1 if sub[0] in 'ygv' else 4);begin=len(self.data)
   if sub=='y':self.data.extend(value)
   else:
    for element in value.items() if sub[0]=='{' else value:self.put(sub,element)
   struct.pack_into('<I',self.data,at,len(self.data)-begin)
  elif c in '({':
   self.align(8)
   for typ,item in zip(types(sig[1:-1]),value):self.put(typ,item)
  else:raise AssertionError(sig)

def types(sig):
 result=[];i=0
 def end(i):
  if sig[i]=='a':return end(i+1)
  if sig[i] in '({':
   close=')' if sig[i]=='(' else '}';p=i+1
   while sig[p]!=close:p=end(p)
   return p+1
  return i+1
 while i<len(sig):p=end(i);result.append(sig[i:p]);i=p
 return result
class Reader:
 def __init__(self,data,at=0):self.data=data;self.at=at
 def align(self,n):self.at+=(-self.at)%n
 def get(self,sig):
  c=sig[0]
  if c=='y':v=self.data[self.at];self.at+=1;return v
  if c in 'uib':self.align(4);v=struct.unpack_from('<I',self.data,self.at)[0];self.at+=4;return v
  if c in 'so':n=self.get('u');v=self.data[self.at:self.at+n].decode();self.at+=n+1;return v
  if c=='g':n=self.get('y');v=self.data[self.at:self.at+n].decode();self.at+=n+1;return v
  if c=='v':return self.get(self.get('g'))
  raise AssertionError(sig)
class Bus:
 def __init__(self):
  self.s=socket.socket(socket.AF_UNIX);self.s.settimeout(4);self.s.connect('\0/tmp/dextop-dbus-98');self.serial=0
  self.s.sendall(b'\0AUTH EXTERNAL 32303030\r\n');line=b''
  while not line.endswith(b'\r\n'):line+=self.s.recv(1)
  assert line.startswith(b'OK'),line
  self.s.sendall(b'BEGIN\r\n');serial=self.call('/org/freedesktop/DBus','org.freedesktop.DBus','Hello','org.freedesktop.DBus')
  while True:
   msg=self.receive()
   if msg['type']==2 and msg.get(5)==serial:self.unique=msg['reader'].get('s');break
 def send(self,kind,fields,sig='',values=()):
  self.serial+=1;b=Writer()
  for t,v in zip(types(sig),values):b.put(t,v)
  h=Writer(16)
  for code,(t,v) in fields.items():h.align(8);h.put('y',code);h.put('g',t);h.put(t,v)
  header=struct.pack('<BBBBIII',108,kind,0,1,len(b.data),self.serial,len(h.data))+h.data
  header+=b'\0'*((-len(header))%8);self.s.sendall(header+b.data);return self.serial
 def call(self,path,iface,member,dest,sig='',values=()):
  fields={1:('o',path),2:('s',iface),3:('s',member),6:('s',dest)}
  if sig:fields[8]=('g',sig)
  return self.send(1,fields,sig,values)
 def signal(self,iface,member,sig='',values=()):
  fields={1:('o','/StatusNotifierItem'),2:('s',iface),3:('s',member)}
  if sig:fields[8]=('g',sig)
  self.send(4,fields,sig,values)
 def reply(self,msg,sig='',values=()):
  fields={5:('u',msg['serial']),6:('s',msg[7])}
  if sig:fields[8]=('g',sig)
  self.send(2,fields,sig,values)
 def error(self,msg):
  self.send(3,{4:('s','org.freedesktop.DBus.Error.UnknownInterface'),5:('u',msg['serial']),6:('s',msg[7]),8:('g','s')},'s',('Unknown interface',))
 def receive(self):
  def exact(n):
   out=b''
   while len(out)<n:
    chunk=self.s.recv(n-len(out));assert chunk;out+=chunk
   return out
  fixed=exact(16);_,kind,_,_,size,serial,fields=struct.unpack('<BBBBIII',fixed)
  head=fixed+exact(fields+(-(16+fields))%8);r=Reader(head,16);msg={'type':kind,'serial':serial}
  while r.at<16+fields:r.align(8);code=r.get('y');msg[code]=r.get(r.get('g'))
  msg['reader']=Reader(exact(size));return msg

def status_notifier(alt=False):
 bus=Bus();bus.call('/org/freedesktop/DBus','org.freedesktop.DBus','RequestName','org.freedesktop.DBus','su',('org.example.DextopTrayProbe',4))
 time.sleep(.1)
 watcher='org.freedesktop.StatusNotifierWatcher' if alt else 'org.kde.StatusNotifierWatcher'
 iface='org.freedesktop.StatusNotifierItem' if alt else 'org.kde.StatusNotifierItem'
 bus.call('/StatusNotifierWatcher',watcher,'RegisterStatusNotifierItem',watcher,'s',('/StatusNotifierItem' if alt else 'org.example.DextopTrayProbe',))
 key='sni:'+(bus.unique if alt else 'org.example.DextopTrayProbe')+'/StatusNotifierItem';observed=[];status=['Active'];color=[bytes([255,30,200,60])*32*32]
 def pump():
  if not select.select([bus.s],[],[],.04)[0]:return
  m=bus.receive()
  if m['type']!=1:return
  member=m.get(3);r=m['reader']
  if member=='GetAll':
   requested=r.get('s')
   if requested!=iface:bus.error(m);return
   props={'Title':('s','Dextop SNI Probe'),'Id':('s','tray-probe'),'Status':('s',status[0]),'IconPixmap':('a(iiay)',[(32,32,color[0])]),'Menu':('o','/TrayMenu'),'ItemIsMenu':('b',False)}
   bus.reply(m,'a{sv}',(props,))
  elif member in ('Activate','ContextMenu','SecondaryActivate'):observed.append((member,r.get('i'),r.get('i')));bus.reply(m)
  elif member=='AboutToShow':r.get('i');observed.append(('AboutToShow',));bus.reply(m,'b',(True,))
  elif member=='GetLayout':
   child=(42,{'label':('s','_Open'),'enabled':('b',True)},[])
   submenu=(43,{'label':('s','More')},[('(ia{sv}av)',(44,{'label':('s','Checked'),'toggle-type':('s','checkmark'),'toggle-state':('i',1)},[]))])
   layout=(0,{},[('(ia{sv}av)',child),('(ia{sv}av)',submenu)])
   bus.reply(m,'u(ia{sv}av)',(1,layout))
  elif member=='Event':observed.append(('Event',r.get('i'),r.get('s'),r.get('v'),r.get('u')));bus.reply(m)
  else:bus.reply(m)
 event=wait(lambda:next((e for e in reversed(events()) if e.get('type')=='tray' and e.get('key')==key),None),'SNI registration and pixmap',pump=pump)
 assert event['probePixel']&0xffffff==0x1ec83c,event
 action(key,1)
 wait(lambda:any(v[0]=='Activate' for v in observed),'SNI activation',pump=pump)
 action(key,3)
 menu=wait(lambda:next((e for e in reversed(events()) if e.get('type')=='tray-menu' and e.get('key')==key),None),'D-Bus context menu',pump=pump)
 assert menu['items'][0]['id']==42 and menu['items'][1]['children'][0]['checked'],menu
 action(key+'#menu=42')
 wait(lambda:any(v[0]=='Event' and v[1]==42 and v[2]=='clicked' for v in observed),'D-Bus menu activation',pump=pump)
 count=len(events());status[0]='Passive';bus.signal(iface,'NewStatus','s',('Passive',))
 wait(lambda:any(e.get('type')=='tray-remove' and e.get('key')==key for e in events()[count:]),'SNI passive removal',pump=pump)
 count=len(events());status[0]='Active';color[0]=bytes([255,10,20,240])*32*32;bus.signal(iface,'NewIcon')
 event=wait(lambda:next((e for e in reversed(events()[count:]) if e.get('type')=='tray' and e.get('key')==key),None),'SNI update/reappear',pump=pump)
 assert event['probePixel']&0xffffff==0x0a14f0,event
 count=len(events());bus.s.close()
 wait(lambda:any(e.get('type')=='tray-remove' and e.get('key')==key for e in events()[count:]),'SNI service exit cleanup')
 print('SNI_REGISTER_PIXELS_ACTIVATE_DBUS_MENU_NESTED_ACTION_PASSIVE_UPDATE_EXIT_OK', 'PATH_FREE_INTERFACE' if alt else 'SERVICE_KDE_INTERFACE',flush=True)

if __name__=='__main__':
 xembed();status_notifier();status_notifier(alt=True);print('LINUX_TRAY_PROTOCOL_INTEGRATION_OK',flush=True)
