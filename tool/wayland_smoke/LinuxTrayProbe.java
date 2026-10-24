import java.io.File;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import android.graphics.*;

/** Isolated X/DBus protocol integration probe; never attaches an Android window or Surface. */
public class LinuxTrayProbe {
 public static void main(String[] args) throws Exception {
  int display=Integer.parseInt(args[0]); File root=new File(args[1]);
  File events=new File(root,"tmp/tray-probe-events.jsonl"),actions=new File(root,"tmp/tray-probe-actions.jsonl"),stop=new File(root,"tmp/tray-probe-stop");
  events.delete();actions.delete();stop.delete();
  Class<?> metadataType=Class.forName("moe.n4tsu.dextop.linux.LinuxWineTrayMetadata"),prootType=Class.forName("moe.n4tsu.dextop.linux.LinuxProot");
  Object metadata=metadataType.getConstructor().newInstance();
  Object proot=args.length>2?prootType.getConstructor(String.class).newInstance(args[2]):null;
  java.util.concurrent.atomic.AtomicReference<Object> xref=new java.util.concurrent.atomic.AtomicReference<>();
  Class<?> fn=Class.forName("kotlin.jvm.functions.Function1");
  Object unit=Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
  Object callback=Proxy.newProxyInstance(fn.getClassLoader(),new Class<?>[]{fn},(p,m,a)->{
   if(m.getName().equals("invoke")) {
    JSONObject e=(JSONObject)a[0];
    if(e.optString("type").equals("tray-wine-metadata")&&proot!=null) {
     new Thread(()->{try {metadataType.getMethod("ensure",String.class,int.class,prootType).invoke(metadata,root.getParentFile().getName(),e.optInt("pid"),proot);}catch(Exception error){error.printStackTrace();}}).start();
     return unit;
    }
    if(e.optString("type").equals("tray")&&e.optString("key").startsWith("x11:")) {
     Object prop=xref.get().getClass().getMethod("property",int.class,int.class,int.class,int.class,boolean.class).invoke(xref.get(),Integer.parseInt(e.getString("key").substring(4)),39,0,4096,false);
     if(prop!=null)e.put("probeTitle",new String((byte[])prop.getClass().getMethod("getThird").invoke(prop),java.nio.charset.StandardCharsets.UTF_8));
    }
    if(e.optString("type").equals("tray")&&!e.optString("icon").isEmpty()) {
     byte[] bytes=android.util.Base64.decode(e.getString("icon"),0);
     Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
     if(b==null)throw new AssertionError("Missing tray pixels");
     e.put("probePixel",b.getPixel(b.getWidth()/2,b.getHeight()/2));e.put("probeCorner",b.getPixel(0,0));b.recycle();
    }
    synchronized(events){Files.writeString(events.toPath(),e.toString()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   }
   return unit;
  });
  Class<?> xc=Class.forName("moe.n4tsu.dextop.linux.X11Client");
  Object companion=xc.getField("Companion").get(null);
  Object x=companion.getClass().getMethod("connect",int.class).invoke(companion,display);
  xref.set(x);
  Class<?> sc=Class.forName("moe.n4tsu.dextop.linux.LinuxAppSession");
  Constructor<?> ctor=sc.getDeclaredConstructors()[0];
  for(Constructor<?> c:sc.getDeclaredConstructors())if(c.getParameterCount()==5)ctor=c;
  ctor.setAccessible(true);
  Process dummy=new ProcessBuilder("/system/bin/sleep","120").start();
  Object session=ctor.newInstance("ctrayprobe",display,dummy,x,callback);
  Method begin=sc.getDeclaredMethod("begin");begin.setAccessible(true);begin.invoke(session);
  Class<?> sn=Class.forName("moe.n4tsu.dextop.linux.LinuxStatusNotifier");
  Object status=sn.getConstructor(int.class,File.class,fn).newInstance(display,root,callback);
  Field field=sc.getDeclaredField("statusTray");field.setAccessible(true);field.set(session,status);
  Method action=sc.getMethod("trayAction",String.class,int.class,int.class,int.class);
  int consumed=0;
  try {
   System.out.println("TRAY_PROTOCOL_SERVER_READY");
   long deadline=android.os.SystemClock.uptimeMillis()+110000;
   while(!stop.exists()&&android.os.SystemClock.uptimeMillis()<deadline) {
    if(actions.isFile()) {
     java.util.List<String> lines=Files.readAllLines(actions.toPath());
     while(consumed<lines.size()) {
      JSONObject a=new JSONObject(lines.get(consumed++));
      if(a.optString("command").equals("replay"))sc.getMethod("replay").invoke(session);
      else action.invoke(session,a.getString("key"),a.optInt("button",1),a.optInt("x",80),a.optInt("y",80));
     }
    }
    Thread.sleep(30);
   }
  } finally {sc.getMethod("stop").invoke(session);metadataType.getMethod("stopAll").invoke(metadata);}
  System.out.println("TRAY_PROTOCOL_SERVER_STOPPED");System.exit(0);
 }
}
