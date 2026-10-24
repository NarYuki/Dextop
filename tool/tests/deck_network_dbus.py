"""Run the real NetworkManager service on an isolated bus; never change the live snapshot."""
import json,os,pathlib,subprocess,sys,tempfile,time
from gi.repository import Gio,GLib
helper=sys.argv[1]
with tempfile.TemporaryDirectory(prefix="dextop-nm-test-") as tmp:
    snapshot=pathlib.Path(tmp)/"network.json"
    actual=json.loads(pathlib.Path('/etc/droiddeck-network.json').read_text())
    snapshot.write_text(json.dumps(actual))
    config=pathlib.Path(tmp)/"system-bus.conf"
    config.write_text('<busconfig><type>system</type><listen>unix:tmpdir=/tmp</listen><auth>EXTERNAL</auth><policy context="default"><allow user="*"/><allow own="*"/><allow send_destination="*"/><allow receive_sender="*"/></policy></busconfig>')
    address='unix:path='+str(pathlib.Path(tmp)/'deck-system.bus')
    daemon=subprocess.Popen(['dbus-daemon','--config-file='+str(config),'--address='+address,'--nofork','--print-address=1'],stdout=subprocess.PIPE,text=True)
    worker=None
    try:
        address=daemon.stdout.readline().strip()
        assert address,'private D-Bus failed to start'
        code="import pathlib,runpy,sys; n=runpy.run_path(sys.argv[1]); n['read_snapshot'].__globals__['SNAPSHOT']=pathlib.Path(sys.argv[2]); n['main']()"
        worker=subprocess.Popen([sys.executable,'-c',code,helper,str(snapshot)],env=dict(os.environ,DBUS_SYSTEM_BUS_ADDRESS=address),stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,text=True)
        bus=Gio.DBusConnection.new_for_address_sync(address,Gio.DBusConnectionFlags.AUTHENTICATION_CLIENT|Gio.DBusConnectionFlags.MESSAGE_BUS_CONNECTION,None,None)
        name='org.freedesktop.NetworkManager'; root='/org/freedesktop/NetworkManager'
        def objects():
            return bus.call_sync(name,root,'org.freedesktop.DBus.ObjectManager','GetManagedObjects',None,GLib.VariantType.new('(a{oa{sa{sv}}})'),Gio.DBusCallFlags.NONE,1000,None).unpack()[0]
        def until(check):
            limit=time.monotonic()+8
            while time.monotonic()<limit:
                if worker.poll() is not None: raise AssertionError(worker.stderr.read())
                while GLib.MainContext.default().pending(): GLib.MainContext.default().iteration(False)
                try:
                    if check(): return
                except GLib.Error: pass
                time.sleep(.05)
            raise AssertionError('NetworkManager update timed out')
        def matches(ssid,strength,online=True):
            result=objects(); state=result[root][name]
            if not online: return state['Connectivity']==1 and state['ActiveConnections']==[]
            device=root+'/Devices/Wifi'; props=result[device][name+'.Device.Wireless']
            ap=result[props['ActiveAccessPoint']][name+'.AccessPoint']
            return bytes(ap['Ssid']).decode()==ssid and ap['Strength']==strength
        until(lambda: matches(actual['ssid'],actual['strength']))
        print('PASS live Android snapshot -> NetworkManager D-Bus SSID and strength')
        signals=[]
        bus.signal_subscribe(name,'org.freedesktop.DBus.Properties','PropertiesChanged',None,None,Gio.DBusSignalFlags.NONE,lambda *a: signals.append(a),None)
        state=dict(actual,sourceTransport='cellular',transport='wifi',ssid='Test carrier',strength=50,metered=True)
        snapshot.write_text(json.dumps(state))
        until(lambda: matches('Test carrier',50) and bool(signals))
        print('PASS cellular-as-Wi-Fi carrier, strength and live PropertiesChanged notification')
        state.update(connected=False,transport='none',addresses=[],gateways=[],dns=[])
        snapshot.write_text(json.dumps(state))
        until(lambda: matches('',0,False))
        print('PASS disconnect removes active connection and reports offline over D-Bus')
    finally:
        if worker is not None:
            worker.terminate(); worker.wait(timeout=5)
        daemon.terminate(); daemon.wait(timeout=5)
