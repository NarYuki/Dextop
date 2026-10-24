import android.media.Image;
import android.media.ImageReader;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Surface;
import java.lang.reflect.*;
import java.nio.*;
import java.io.*;

/** Synthetic backend checks on an isolated display; no desktop or application GUI. */
public class LinuxXwaylandProbe {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 static void apps(String[] args)throws Exception {
  Class<?> ac=Class.forName("moe.n4tsu.dextop.linux.LinuxAppSession"),fn=Class.forName("kotlin.jvm.functions.Function1");
  java.util.concurrent.atomic.AtomicInteger mapped=new java.util.concurrent.atomic.AtomicInteger();
  Object cb=Proxy.newProxyInstance(fn.getClassLoader(),new Class[]{fn},(p,m,a)->{if(a!=null&&a.length>0&&a[0] instanceof org.json.JSONObject){org.json.JSONObject e=(org.json.JSONObject)a[0];if("map".equals(e.optString("type"))&&!e.optBoolean("popup"))mapped.set(e.optInt("xid"));}return null;});
  Class<?> pc=Class.forName("moe.n4tsu.dextop.linux.LinuxProot");Object runner=pc.getConstructor(String.class).newInstance(args[0]);
  Object factory=ac.getField("Companion").get(null);
  Object session=factory.getClass().getMethod("start",pc,String.class,int.class,int.class,int.class,int.class,boolean.class,boolean.class,boolean.class,fn).invoke(factory,runner,args[1],98,640,480,160,false,true,true,cb);
  Class<?> xc=Class.forName("moe.n4tsu.dextop.linux.X11Client");Object cf=xc.getField("Companion").get(null);Object x=cf.getClass().getMethod("connect",int.class).invoke(cf,98);
  ImageReader reader=ImageReader.newInstance(64,32,PixelFormat.RGBA_8888,3);
  try {
   int root=(Integer)xc.getMethod("getRoot").invoke(x),id=(Integer)xc.getMethod("allocateId").invoke(x);
   Method request=xc.getDeclaredMethod("request",ByteBuffer.class,boolean.class,FileDescriptor.class,boolean.class);request.setAccessible(true);
   ByteBuffer create=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
   create.put((byte)1).put((byte)0).putShort((short)9).putInt(id).putInt(root).putShort((short)0).putShort((short)0).putShort((short)64).putShort((short)32).putShort((short)0).putShort((short)1).putInt(0).putInt(2).putInt(0x0000ff00);
   request.invoke(x,create,false,null,true);xc.getMethod("mapWindow",int.class).invoke(x,id);xc.getMethod("sync").invoke(x);
   for(int i=0;i<100&&mapped.get()!=id;i++)Thread.sleep(20);
   check(mapped.get()==id,"Dextop did not manage the Xwayland test window");
   check((Boolean)ac.getMethod("attachSurface",int.class,Surface.class).invoke(session,id,reader.getSurface()),"app surface attach failed");
   boolean green=false;
   for(int i=0;i<200&&!green;i++){Image image=reader.acquireLatestImage();if(image!=null){ByteBuffer bytes=image.getPlanes()[0].getBuffer();green=bytes.get(0)==0&&(bytes.get(1)&255)==255&&bytes.get(2)==0;image.close();}if(!green)Thread.sleep(15);}
   check(green,"Dextop Xwayland app capture did not produce the expected pixels");
   System.out.println("XWAYLAND_PER_APP_WM_AND_SURFACE_PIXELS_OK");
  } finally {xc.getMethod("close").invoke(x);ac.getMethod("stop").invoke(session);reader.close();}
 }
 public static void main(String[] args)throws Exception {
  System.setProperty("moe.n4tsu.dextop.linux_library",args[0]+"/libdextop_linux.so");
  Class<?> hc=Class.forName("moe.n4tsu.dextop.linux.LinuxXwaylandHost");Object hf=hc.getField("Companion").get(null);
  Object host=hf.getClass().getMethod("start",String.class,int.class,int.class,int.class).invoke(hf,args[1],98,640,480);
  Class<?> pc=Class.forName("moe.n4tsu.dextop.linux.LinuxProot");Object runner=pc.getConstructor(String.class).newInstance(args[0]);
  Class<?> cat=Class.forName("moe.n4tsu.dextop.linux.LinuxCatalog");
  String script=(String)cat.getMethod("appsScript",int.class,int.class,int.class,int.class,boolean.class).invoke(cat.getField("INSTANCE").get(null),98,640,480,160,true);
  ProcessBuilder pb=(ProcessBuilder)pc.getMethod("guest",String.class,String.class,boolean.class).invoke(runner,args[1],script,false);
  Process process=pb.redirectErrorStream(true).start();
  Thread drain=new Thread(()->{try{process.getInputStream().transferTo(System.out);}catch(Exception ignored){}});drain.setDaemon(true);drain.start();
  ImageReader reader=ImageReader.newInstance(640,480,PixelFormat.RGBA_8888,3);
  Class<?> xc=Class.forName("moe.n4tsu.dextop.linux.X11Client");Object x=null;
  try {
   check((Boolean)hc.getMethod("attachSurface",Surface.class).invoke(host,reader.getSurface()),"surface attach failed");
   Object factory=xc.getField("Companion").get(null);
   for(int i=0;i<100&&x==null;i++){try{x=factory.getClass().getMethod("connect",int.class).invoke(factory,98);}catch(Exception e){Thread.sleep(100);}}
   check(x!=null,"Xwayland connection failed");
   int root=(Integer)xc.getMethod("getRoot").invoke(x);
   xc.getMethod("changeWindowAttributes",int.class,int.class,int[].class).invoke(x,root,2,new int[]{0x00ff0000});
   Method request=xc.getDeclaredMethod("request",ByteBuffer.class,boolean.class,FileDescriptor.class,boolean.class);request.setAccessible(true);
   ByteBuffer clear=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);clear.put((byte)61).put((byte)0).putShort((short)4).putInt(root).putLong(0);
   request.invoke(x,clear,false,null,true);xc.getMethod("sync").invoke(x);
   boolean red=false;
   for(int i=0;i<200&&!red;i++){Image image=reader.acquireLatestImage();if(image!=null){Image.Plane plane=image.getPlanes()[0];ByteBuffer bytes=plane.getBuffer();int pos=100*plane.getRowStride()+100*plane.getPixelStride();red=(bytes.get(pos)&255)==255&&bytes.get(pos+1)==0&&bytes.get(pos+2)==0;image.close();}if(!red)Thread.sleep(15);}
   check(red,"Xwayland root pixel did not reach the Wayland Android surface");
   System.out.println("XWAYLAND_X11_TO_WAYLAND_SURFACE_PIXELS_OK");
   Class<?> dc=Class.forName("moe.n4tsu.dextop.linux.LinuxDisplayConfiguration");
   Object config=dc.getConstructor(int.class,int.class,int.class,int.class).newInstance(480,640,160,120000);
   hc.getMethod("updateDisplay",dc).invoke(host,config);
   boolean resized=false;
   for(int i=0;i<100&&!resized;i++){Rect bounds=(Rect)xc.getMethod("geometry",int.class).invoke(x,root);resized=bounds!=null&&bounds.width()==480&&bounds.height()==640;if(!resized)Thread.sleep(20);}
   check(resized,"Xwayland root did not follow Wayland output/configure resize");
   System.out.println("XWAYLAND_OUTPUT_ROTATION_RESIZE_OK");
  } finally {
   if(x!=null)xc.getMethod("close").invoke(x);
   Class<?> rt=Class.forName("moe.n4tsu.dextop.linux.LinuxRuntime");rt.getMethod("terminate",Process.class).invoke(rt.getField("INSTANCE").get(null),process);
   hc.getMethod("stop").invoke(host);reader.close();
  }
  for(boolean backend:new boolean[]{false,true}) {
   for(String generated:new String[]{
    (String)cat.getMethod("appsScript",int.class,int.class,int.class,int.class,boolean.class).invoke(cat.getField("INSTANCE").get(null),98,640,480,160,backend),
    (String)cat.getMethod("desktopScript",int.class,int.class,int.class,int.class,String.class,boolean.class).invoke(cat.getField("INSTANCE").get(null),98,640,480,160,"icewm",backend),
   }) {
    Process syntax=new ProcessBuilder("/system/bin/sh","-n").inheritIO().redirectInput(ProcessBuilder.Redirect.PIPE).start();
    syntax.getOutputStream().write(generated.getBytes(java.nio.charset.StandardCharsets.UTF_8));syntax.getOutputStream().close();
    check(syntax.waitFor()==0,"generated X server script has invalid shell syntax");
   }
  }
  System.out.println("XVFB_AND_XWAYLAND_APPS_DESKTOP_SCRIPT_SYNTAX_OK");
  apps(args);
 }
}
