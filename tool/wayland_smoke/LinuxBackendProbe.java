import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import org.json.JSONObject;

/** Backend selection checks only; no application or desktop GUI is launched. */
public class LinuxBackendProbe {
 static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
 public static void main(String[] args) throws Exception {
  System.setProperty("moe.n4tsu.dextop.linux_library",args[0]+"/libdextop_linux.so");
  Class<?> options=Class.forName("moe.n4tsu.dextop.linux.LinuxSetupOptions");
  Object companion=options.getField("Companion").get(null);
  Method from=companion.getClass().getMethod("fromJson",JSONObject.class);
  for(String backend:new String[]{"xvfb","wayland"}) {
   Object value=from.invoke(companion,new JSONObject().put("displayBackend",backend));
   JSONObject saved=(JSONObject)options.getMethod("toJson").invoke(value);
   check(backend.equals(saved.getString("displayBackend")),"selection did not survive serialization");
  }
  Class<?> wl=Class.forName("moe.n4tsu.dextop.linux.WaylandCompositor"),fn=Class.forName("kotlin.jvm.functions.Function1");
  Object cb=Proxy.newProxyInstance(fn.getClassLoader(),new Class[]{fn},(p,m,a)->null);
  String name="dextop-backend-selection-probe";
  Object server=wl.getConstructor(String.class,int.class,int.class,File.class,fn).newInstance(name,640,480,null,cb);
  try {
   check((Boolean)wl.getMethod("getRunning").invoke(server),"compositor failed");
   try(LocalSocket client=new LocalSocket()) {
    client.connect(new LocalSocketAddress(name,LocalSocketAddress.Namespace.ABSTRACT));client.setSoTimeout(3000);
    ByteBuffer request=ByteBuffer.allocate(24).order(ByteOrder.nativeOrder());
    request.putInt(1).putInt((12<<16)|1).putInt(2); // wl_display.get_registry
    request.putInt(1).putInt(12<<16).putInt(3); // wl_display.sync
    client.getOutputStream().write(request.array());
    byte[] reply=new byte[8192];int count=client.getInputStream().read(reply);
    check(count>0,"Wayland registry roundtrip produced no response");
   }
   System.out.println("DISPLAY_SELECTION_SERIALIZATION_AND_WAYLAND_SOCKET_OK");
  } finally { wl.getMethod("stop").invoke(server); }
  Class<?> catalog=Class.forName("moe.n4tsu.dextop.linux.LinuxCatalog");Object instance=catalog.getField("INSTANCE").get(null);
  Method launch=catalog.getMethod("launchScript",int.class,String.class,boolean.class,boolean.class);
  Class<?> proot=Class.forName("moe.n4tsu.dextop.linux.LinuxProot");Object runner=proot.getConstructor(String.class).newInstance(args[0]);
  String command="printf '%s|%s|%s\\n' \"$GDK_BACKEND\" \"$QT_QPA_PLATFORM\" \"${WAYLAND_DISPLAY:-unset}\"";
  for(boolean wayland:new boolean[]{false,true}) {
   String script="exec 3>&1\n"+(String)launch.invoke(instance,10,command+" >&3",wayland,false);
   ProcessBuilder pb=(ProcessBuilder)proot.getMethod("guest",String.class,String.class,boolean.class).invoke(runner,args[1],script,false);
   Process process=pb.redirectErrorStream(true).start();String output=new String(process.getInputStream().readAllBytes());
   check(process.waitFor()==0,"guest environment probe failed: "+output);
   check(output.contains(wayland?"wayland,x11|wayland;xcb|wayland-0":"x11|xcb|unset"),"wrong backend environment: "+output);
  }
  System.out.println("REAL_GUEST_X11_AND_WAYLAND_LAUNCH_ENVIRONMENTS_OK");
 }
}
