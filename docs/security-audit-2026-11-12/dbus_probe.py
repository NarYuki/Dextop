import importlib.machinery, os, pathlib, subprocess, sys, tempfile, time
import gi
gi.require_version('Gio','2.0')
from gi.repository import Gio
base=pathlib.Path(__file__).parent
os.environ['DEXTOP_SYSTEMD']=str(base/'dextop-systemd')
m=importlib.machinery.SourceFileLoader('audit_bus',str(base/'dextop-systemd-bus')).load_module()
if len(sys.argv)>1:
 c=m.Connection(sys.argv[1]);s=m.Service(c);s.rows=lambda:{};s.run();raise SystemExit
with tempfile.TemporaryDirectory(prefix='dbus-audit-') as tmp:
 cfg=pathlib.Path(tmp)/'bus.conf'; addr='unix:path='+tmp+'/bus'
 cfg.write_text('<busconfig><type>system</type><listen>'+addr+'</listen><auth>EXTERNAL</auth><auth>ANONYMOUS</auth><allow_anonymous/><policy context="default"><allow user="*"/><allow own="*"/><allow send_destination="*"/><allow receive_sender="*"/></policy></busconfig>')
 bus=subprocess.Popen(['dbus-daemon','--config-file='+str(cfg),'--nofork','--print-address=1'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 service=None
 try:
  address=bus.stdout.readline().strip();assert address,address
  service=subprocess.Popen([sys.executable,__file__,address],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
  c=m.Connection(address)
  for _ in range(30):
   try:
    before=c.call(m.BUS_NAME,m.ROOT_PATH,m.MANAGER,'GetDefaultTarget');break
   except m.Error:time.sleep(.1)
  else:raise RuntimeError('test service unavailable')
  print('CONTROL normal call:',before,flush=True)
  # Encode using GLib, not the implementation under audit.
  attack=Gio.DBusMessage.new_method_call(m.BUS_NAME,m.ROOT_PATH,m.MANAGER,'GetDefaultTarget')
  attack.set_byte_order(Gio.DBusMessageByteOrder.BIG_ENDIAN);attack.set_serial(1000)
  blob=bytes(attack.to_blob(Gio.DBusCapabilityFlags.NONE))
  assert blob[:1]==b'B'; print('GIO big-endian valid message bytes:',len(blob),flush=True)
  c.socket.sendall(blob);time.sleep(.3)
  c.send(m.Message(1,path=m.ROOT_PATH,interface=m.MANAGER,member='GetDefaultTarget',destination=m.BUS_NAME))
  messages=[]
  for _ in range(3):messages+=c.read_socket(.4)
  replies=[x for x in messages if x.reply in (1000,c.serial)]
  print('RESULT follow-up replies:',len(replies),'service alive:',service.poll() is None,flush=True)
  assert not replies and service.poll() is None
  print('CONFIRMED one valid big-endian call stalls the connection',flush=True)
 finally:
  for p in (service,bus):
   if p:
    p.terminate()
    try:p.wait(timeout=3)
    except subprocess.TimeoutExpired:p.kill();p.wait()
