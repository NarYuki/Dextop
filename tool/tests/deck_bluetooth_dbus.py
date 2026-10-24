"""Real BlueZ facade + isolated D-Bus + simulated Android request endpoint.

No physical radio, pairing or existing device connection is changed by this test.
Run with guest python3-gi and pass the dextop-bluez script path.
"""
import json, os, pathlib, signal, subprocess, sys, tempfile, threading, time
from gi.repository import Gio, GLib
helper=sys.argv[1]
with tempfile.TemporaryDirectory(prefix='dextop-bluez-test-') as tmp:
 base=pathlib.Path(tmp); state_path=base/'bluetooth.json'; requests=base/'requests'; requests.mkdir()
 adapter='/org/bluez/hci0'; address='AA:BB:CC:DD:EE:FF'; device=adapter+'/dev_AA_BB_CC_DD_EE_FF'
 state=dict(available=True,powered=True,discovering=False,name='Test Android',address='00:11:22:33:44:55',devices=[dict(address=address,name='Test controller',paired=False,connected=False,**{'class':0x508})])
 lock=threading.Lock(); stopped=threading.Event(); operations=[]; fail_connect=False
 def save():
  path=base/'bluetooth.staged'; path.write_text(json.dumps(state)); path.replace(state_path)
 save()
 def android():
  while not stopped.wait(.02):
   for request in requests.glob('*.json'):
    with lock:
     value=json.loads(request.read_text()); op=value['action']; operations.append(op)
     response={'ok':True}; current=state['devices'][0] if state['devices'] else {}
     if op=='startDiscovery': state['discovering']=True
     elif op=='stopDiscovery': state['discovering']=False
     elif op=='power': state['powered']=value['value']
     elif op=='pair': current['pairing']=dict(variant=2,key=123456);response['waitFor']='paired'
     elif op=='confirm': current.update(paired=bool(value['value']));current.pop('pairing',None)
     elif op=='connect':
      if fail_connect: response={'ok':False,'error':'Test denied','errorName':'NotAuthorized'}
      else: current['connected']=True;response['waitFor']='connected'
     elif op=='disconnect': current['connected']=False;response['waitFor']='disconnected'
     elif op=='remove': state['devices']=[];response['waitFor']='unpaired'
     save()
     result=request.with_suffix('.result'); pending=result.with_suffix('.staged');pending.write_text(json.dumps(response));pending.replace(result);request.unlink()
 threading.Thread(target=android,daemon=True).start()
 daemon=subprocess.Popen(['dbus-daemon','--session','--nofork','--print-address=1'],stdout=subprocess.PIPE,text=True)
 service=None
 try:
  bus_address=daemon.stdout.readline().strip()
  code="import pathlib,runpy,sys; n=runpy.run_path(sys.argv[1]); g=n['snapshot'].__globals__; g['SNAPSHOT']=pathlib.Path(sys.argv[2]); g['REQUESTS']=pathlib.Path(sys.argv[3]); n['main']()"
  service=subprocess.Popen([sys.executable,'-c',code,helper,str(state_path),str(requests)],env=dict(os.environ,DBUS_SYSTEM_BUS_ADDRESS=bus_address),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
  bus=Gio.DBusConnection.new_for_address_sync(bus_address,Gio.DBusConnectionFlags.AUTHENTICATION_CLIENT|Gio.DBusConnectionFlags.MESSAGE_BUS_CONNECTION,None,None)
  def call(path,interface,method,args=None,reply='()'):
   return bus.call_sync('org.bluez',path,interface,method,args,GLib.VariantType.new(reply),Gio.DBusCallFlags.NONE,6000,None).unpack()
  def objects():return call('/','org.freedesktop.DBus.ObjectManager','GetManagedObjects',reply='(a{oa{sa{sv}}})')[0]
  def until(check):
   limit=time.monotonic()+8
   while time.monotonic()<limit:
    if service.poll() is not None:raise AssertionError(service.stdout.read())
    while GLib.MainContext.default().pending():GLib.MainContext.default().iteration(False)
    try:
     if check():return
    except GLib.Error:pass
    time.sleep(.02)
   raise AssertionError('BlueZ condition timed out')
  until(lambda:device in objects())
  assert objects()[device]['org.bluez.Device1']['Icon']=='input-gaming'
  call(adapter,'org.bluez.Adapter1','StartDiscovery')
  until(lambda:objects()[adapter]['org.bluez.Adapter1']['Discovering'])
  call(adapter,'org.bluez.Adapter1','StopDiscovery')
  until(lambda:not objects()[adapter]['org.bluez.Adapter1']['Discovering'])
  print('PASS adapter/device enumeration and discovery requests')
  prompts=[]
  agent_info=Gio.DBusNodeInfo.new_for_xml('<node><interface name="org.bluez.Agent1"><method name="RequestConfirmation"><arg type="o" direction="in"/><arg type="u" direction="in"/></method></interface></node>').interfaces[0]
  def agent_call(connection,sender,path,interface,method,args,invocation):
   prompts.append(args.unpack());invocation.return_value(GLib.Variant('()',()))
  bus.register_object('/test/agent',agent_info,agent_call,None,None)
  call('/org/bluez','org.bluez.AgentManager1','RegisterAgent',GLib.Variant('(os)',('/test/agent','KeyboardDisplay')))
  call('/org/bluez','org.bluez.AgentManager1','RequestDefaultAgent',GLib.Variant('(o)',('/test/agent',)))
  completed=[]
  def pair_done(connection,result,data):
   try:completed.append(connection.call_finish(result).unpack())
   except GLib.Error as error:completed.append(error)
  bus.call('org.bluez',device,'org.bluez.Device1','Pair',None,GLib.VariantType.new('()'),Gio.DBusCallFlags.NONE,7000,None,pair_done,None)
  until(lambda:bool(completed))
  assert not isinstance(completed[0],Exception),completed
  assert prompts==[(device,123456)],prompts
  assert 'confirm' in operations
  assert objects()[device]['org.bluez.Device1']['Paired']
  print('PASS Steam-compatible Agent1 confirmation -> Android confirm -> Pair completion')
  call(device,'org.bluez.Device1','Connect')
  assert objects()[device]['org.bluez.Device1']['Connected']
  call(device,'org.bluez.Device1','Disconnect')
  assert not objects()[device]['org.bluez.Device1']['Connected']
  fail_connect=True
  try:call(device,'org.bluez.Device1','Connect');raise AssertionError('Denied operation succeeded')
  except GLib.Error as error:assert 'NotAuthorized' in str(error),error
  print('PASS connect/disconnect and denied-operation error propagation')
  call(adapter,'org.freedesktop.DBus.Properties','Set',GLib.Variant('(ssv)',('org.bluez.Adapter1','Powered',GLib.Variant('b',False))))
  until(lambda:not objects()[adapter]['org.bluez.Adapter1']['Powered'])
  call(adapter,'org.bluez.Adapter1','RemoveDevice',GLib.Variant('(o)',(device,)))
  until(lambda:device not in objects())
  print('PASS power property and unpair/remove-object updates')
 finally:
  stopped.set()
  if service is not None:service.terminate();service.wait(timeout=5)
  daemon.terminate();daemon.wait(timeout=5)
