import java.lang.reflect.*;
public class WaylandOutputProbe {
 public static void main(String[] args) throws Exception {
  System.setProperty("moe.n4tsu.dextop.linux_library","/data/local/tmp/dextop-output-probe/libdextop_linux.so");
  Class<?> type=Class.forName("moe.n4tsu.dextop.linux.WaylandCompositor"),fn=Class.forName("kotlin.jvm.functions.Function1");
  Object cb=Proxy.newProxyInstance(fn.getClassLoader(),new Class[]{fn},(p,m,a)->null);
  Object server=type.getConstructor(String.class,int.class,int.class,java.io.File.class,fn).newInstance("dextop-wayland-client-probe",640,480,null,cb);
  try {
   System.out.println("WAYLAND_OUTPUT_READY");
   new ProcessBuilder("/system/bin/sh","/data/local/tmp/debian-steam-guest.sh","/tmp/wayland-output-client").inheritIO().start();
   Thread.sleep(4000);
   type.getMethod("updateOutput",int.class,int.class,int.class).invoke(server,480,640,120000);
   Thread.sleep(4000);
   type.getMethod("updateOutput",int.class,int.class,int.class).invoke(server,640,480,60000);
   Thread.sleep(4000);
  } finally {type.getMethod("stop").invoke(server);}
 }
}
