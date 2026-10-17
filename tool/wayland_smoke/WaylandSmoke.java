import java.lang.reflect.*;
import java.nio.file.*;
public class WaylandSmoke {
 static volatile Object active;
 static volatile android.media.ImageReader reader;
 public static void main(String[] args) throws Exception {
  System.setProperty("moe.n4tsu.dextop.linux_library", "/data/local/tmp/dextop-wayland-smoke/libdextop_linux.so");
  Class<?> type=Class.forName("moe.n4tsu.dextop.linux.WaylandCompositor");
  Class<?> fn=Class.forName("kotlin.jvm.functions.Function1");
  Object callback=Proxy.newProxyInstance(fn.getClassLoader(), new Class[]{fn}, (proxy,method,a)->null);
  for(int i=0;i<3;i++) {
   String socket="dextop-wayland-smoke-"+i;
   Object instance=type.getConstructor(String.class,int.class,int.class,fn).newInstance(socket,640,480,callback);
   if(!((Boolean)type.getMethod("getRunning").invoke(instance))) throw new AssertionError("failed to start");
   System.out.println("STARTED "+socket);
   Thread.sleep(150);
   if(!new String(Files.readAllBytes(java.nio.file.Paths.get("/proc/net/unix"))).contains("@"+socket)) throw new AssertionError("missing socket");
   type.getMethod("stop").invoke(instance);
   type.getMethod("stop").invoke(instance);
   type.getMethod("key",int.class,boolean.class).invoke(instance,30,true);
   if(((Boolean)type.getMethod("getRunning").invoke(instance))) throw new AssertionError("stale handle");
   Thread.sleep(200);
   if(new String(Files.readAllBytes(java.nio.file.Paths.get("/proc/net/unix"))).contains("@"+socket)) throw new AssertionError("socket leaked");
   System.out.println("STOPPED "+socket);
  }
  System.out.println("WAYLAND_LIFECYCLE_OK");
  Object listener=Proxy.newProxyInstance(fn.getClassLoader(), new Class[]{fn}, (proxy,method,a)->{
   if(a!=null && a.length>0) {
    System.out.println("WINDOW_EVENT "+a[0]);
    org.json.JSONObject event=(org.json.JSONObject)a[0];
    if("map".equals(event.optString("type"))) {
     reader=android.media.ImageReader.newInstance(128,64,android.graphics.PixelFormat.RGBA_8888,2);
     type.getMethod("attach",int.class,android.view.Surface.class).invoke(active,event.getInt("id"),reader.getSurface());
    }
   }
   return null;
  });
  Object server=type.getConstructor(String.class,int.class,int.class,fn).newInstance("dextop-wayland-client-probe",640,480,listener);
  active=server;
  System.out.println("CLIENT_PROBE_READY");
  boolean drawn=false;
  for(int wait=0;wait<900;wait++) {
   Thread.sleep(100);
   if(reader!=null) {
    android.media.Image frame=reader.acquireLatestImage();
    if(frame!=null) {
     java.nio.ByteBuffer pixels=frame.getPlanes()[0].getBuffer();
     int red=pixels.get(0)&255,green=pixels.get(1)&255,blue=pixels.get(2)&255;
     System.out.println("PIXEL_RGB="+red+","+green+","+blue);
     drawn=red==255 && green==0 && blue==0;
     frame.close();break;
    }
   }
  }
  if(!drawn) throw new AssertionError("red buffer did not reach Android Surface");
  System.out.println("WAYLAND_SURFACE_PIXELS_OK");
  int frames=0;
  long until=System.currentTimeMillis()+2500;
  while(System.currentTimeMillis()<until){
   android.media.Image frame=reader.acquireLatestImage();
   if(frame!=null){
    java.nio.ByteBuffer pixels=frame.getPlanes()[0].getBuffer();
    boolean correct=(pixels.get(0)&255)==255&&pixels.get(1)==0&&pixels.get(2)==0;
    frame.close();
    if(!correct)throw new AssertionError("wrong streamed pixel");
    frames++;
   }
   Thread.sleep(2);
  }
  System.out.println("WAYLAND_STREAM_FRAMES="+frames);
  if(args.length>0&&args[0].equals("--stream")&&frames<10)throw new AssertionError("too few streamed frames");
  type.getMethod("stop").invoke(server);
 }
}
