import java.lang.reflect.*;
import java.nio.file.*;
public class WaylandWeztermSmoke {
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
     reader=android.media.ImageReader.newInstance(event.getInt("w"),event.getInt("h"),android.graphics.PixelFormat.RGBA_8888,2);
     type.getMethod("attach",int.class,android.view.Surface.class).invoke(active,event.getInt("id"),reader.getSurface());
    }
   }
   return null;
  });
  Object server=type.getConstructor(String.class,int.class,int.class,fn).newInstance("dextop-c8147ec8bb3fc/tmp/runtime-root/wayland-probe",640,480,listener);
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
     int distinct=0;
     for(int offset=0;offset<pixels.limit()-4;offset+=4) {
      if((pixels.get(offset)&255)!=red || (pixels.get(offset+1)&255)!=green || (pixels.get(offset+2)&255)!=blue) distinct++;
     }
     drawn=distinct>20;
     System.out.println("NONUNIFORM_PIXELS="+distinct);
     int imageWidth=frame.getWidth(), imageHeight=frame.getHeight();
     int rowStride=frame.getPlanes()[0].getRowStride();
     java.nio.ByteBuffer packed=java.nio.ByteBuffer.allocate(imageWidth*imageHeight*4);
     for(int row=0;row<imageHeight;row++) {
      pixels.position(row*rowStride);
      byte[] rowBytes=new byte[imageWidth*4];pixels.get(rowBytes);packed.put(rowBytes);
     }
     packed.flip();
     android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(imageWidth,imageHeight,android.graphics.Bitmap.Config.ARGB_8888);
     bitmap.copyPixelsFromBuffer(packed);
     try(java.io.FileOutputStream output=new java.io.FileOutputStream("/data/local/tmp/dextop-wayland-smoke/wezterm.png")){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
     frame.close();break;
    }
   }
  }
  if(!drawn) throw new AssertionError("WezTerm did not render nonuniform pixels");
  System.out.println("WAYLAND_WEZTERM_PIXELS_OK");
  Thread.sleep(12000);
  type.getMethod("stop").invoke(server);
 }
}
