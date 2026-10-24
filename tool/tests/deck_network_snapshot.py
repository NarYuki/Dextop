"""Exercise the exact NetworkManager properties consumed by Steam (run with guest python3-gi)."""
import runpy,sys
nm=runpy.run_path(sys.argv[1],run_name="dextop_network_test")
N=nm["NAME"]; R=nm["ROOT"]
def value(objects,path,iface,key): return objects[path][iface][key].unpack()
for source,ssid,strength in [("wifi","Test Wi-Fi",75),("cellular","Test carrier",50)]:
    state=dict(connected=True,validated=True,transport="wifi",sourceTransport=source,
               ssid=ssid,strength=strength,wifiEnabled=True,interface="wlan0" if source=="wifi" else "rmnet_data0",
               addresses=[dict(address="192.0.2.2",prefix=24)],gateways=["192.0.2.1"],dns=["192.0.2.1"],metered=source=="cellular")
    obj=nm["objects"](state); device=nm["device_path"](state)
    assert value(obj,device,N+".Device","DeviceType")==2
    ap=value(obj,device,N+".Device.Wireless","ActiveAccessPoint")
    assert bytes(value(obj,ap,N+".AccessPoint","Ssid")).decode()==ssid
    assert value(obj,ap,N+".AccessPoint","Strength")==strength
    assert value(obj,R,N,"Connectivity")==4
    state.update(connected=False,transport="none",addresses=[],gateways=[],dns=[])
    obj=nm["objects"](state)
    assert value(obj,R,N,"ActiveConnections")==[]
    assert value(obj,R,N,"Connectivity")==1
    assert value(obj,nm["device_path"](state),N+".Device.Wireless","ActiveAccessPoint")=="/"
print("PASS Wi-Fi and cellular-as-Wi-Fi SSID, strength, connectivity and disconnect properties")
