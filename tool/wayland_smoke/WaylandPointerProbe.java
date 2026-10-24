import java.lang.reflect.*;
import java.util.concurrent.atomic.*;
import org.json.*;
public class WaylandPointerProbe {
 static Object server;static Class<?> type;static AtomicInteger id=new AtomicInteger();
 public static void main(String[]args)throws Exception{
  System.setProperty("moe.n4tsu.dextop.linux_library","/data/local/tmp/dextop-output-probe/libdextop_linux.so");
  type=Class.forName("moe.n4tsu.dextop.linux.WaylandCompositor");Class<?> fn=Class.forName("kotlin.jvm.functions.Function1");
  Object cb=Proxy.newProxyInstance(fn.getClassLoader(),new Class[]{fn},(p,m,a)->{
   if(m.getName().equals("invoke")){
    JSONObject e=(JSONObject)a[0];
    if(e.optString("type").equals("map"))id.set(e.getInt("id"));
    if(e.optString("type").equals("pointer-capture")&&e.optBoolean("capture")){
     Method pointer=type.getMethod("pointer",int.class,int.class,int.class,int.class,int.class);
     pointer.invoke(server,id.get(),10,7,-3,0);pointer.invoke(server,id.get(),11,0,0,1);pointer.invoke(server,id.get(),12,0,0,1);
     type.getMethod("activate",int.class).invoke(server,0);
    }
   }return null;
  });
  server=type.getConstructor(String.class,int.class,int.class,java.io.File.class,fn).newInstance("dextop-wayland-client-probe",640,480,null,cb);
  Process client=null;
  try{
   client=new ProcessBuilder(args.length>0?new String[]{"/system/bin/sh","/data/local/tmp/debian-steam-guest.sh","/tmp/wayland-pointer-client","--confine"}:new String[]{"/system/bin/sh","/data/local/tmp/debian-steam-guest.sh","/tmp/wayland-pointer-client"}).inheritIO().start();
   for(int i=0;i<100&&id.get()==0;i++)Thread.sleep(30);
   if(id.get()==0)throw new AssertionError("no client map");
   type.getMethod("activate",int.class).invoke(server,id.get());
   type.getMethod("pointer",int.class,int.class,int.class,int.class,int.class).invoke(server,id.get(),0,20,20,0);
   if(!client.waitFor(12,java.util.concurrent.TimeUnit.SECONDS)||client.exitValue()!=0)throw new AssertionError("pointer protocol failed");
  }finally{if(client!=null&&client.isAlive())client.destroyForcibly();type.getMethod("stop").invoke(server);}
 }
}
