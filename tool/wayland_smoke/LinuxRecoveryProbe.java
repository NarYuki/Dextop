import android.media.ImageReader;
import android.media.Image;
import android.graphics.PixelFormat;
import android.view.Surface;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Headless regression checks; no application, game or desktop UI is launched. */
public class LinuxRecoveryProbe {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static long fds(){return new File("/proc/self/fd").list().length;}
 static long mappings()throws Exception{return Files.readAllLines(Paths.get("/proc/self/maps")).stream().filter(x->x.contains("dextop-x11-shm")).count();}
 static void shared(int display)throws Exception{
  Class<?> xc=Class.forName("moe.n4tsu.dextop.linux.X11Client");Object cp=xc.getField("Companion").get(null);Object x=cp.getClass().getMethod("connect",int.class).invoke(cp,display);
  Class<?> image=Class.forName("moe.n4tsu.dextop.linux.X11SharedImage");Object factory=image.getField("Companion").get(null);Method create=factory.getClass().getMethod("create",xc,long.class);
  try{
   long before=fds();for(int i=0;i<20;i++)check(create.invoke(factory,x,-1L)==null,"negative allocation must fail");check(fds()==before,"failed ftruncate leaked descriptors");
   Object good=create.invoke(factory,x,4096L);check(good!=null,"valid shared mapping");image.getMethod("close").invoke(good);image.getMethod("close").invoke(good);
   Field opcode=xc.getDeclaredField("shmOpcode");opcode.setAccessible(true);int saved=opcode.getInt(x);opcode.setInt(x,250);
   before=fds();long maps=mappings();for(int i=0;i<10;i++)check(create.invoke(factory,x,4096L)==null,"rejected AttachFd must fail");
   check(fds()==before,"rejected AttachFd leaked descriptors");check(mappings()==maps,"rejected AttachFd leaked mappings");opcode.setInt(x,saved);
   System.out.println("SHM_FTRUNCATE_ATTACH_FAILURE_CLEANUP_AND_IDEMPOTENT_CLOSE_OK");
  }finally{xc.getMethod("close").invoke(x);}
 }
 static void runtime()throws Exception{
  Process p=new ProcessBuilder("/system/bin/sh","-c","sleep 60 & echo $!; wait").start();
  int child=Integer.parseInt(new BufferedReader(new InputStreamReader(p.getInputStream())).readLine());
  Class<?> c=Class.forName("moe.n4tsu.dextop.linux.LinuxRuntime");Object rt=c.getField("INSTANCE").get(null);
  c.getMethod("terminate",Process.class).invoke(rt,p);p.waitFor();Thread.sleep(100);
  check(!new File("/proc/"+child).exists()||Files.readString(Paths.get("/proc/"+child+"/stat")).contains(") Z "),"descendant survived");
  System.out.println("ANDROID_PROCESS_TREE_TERMINATION_OK");
 }
 static void frame(String directory)throws Exception{
  System.setProperty("moe.n4tsu.dextop.linux_library",directory+"/libdextop_linux.so");
  Class<?> c=Class.forName("moe.n4tsu.dextop.linux.LinuxFrameBridge");
  Method attach=c.getMethod("nativeAttach",Surface.class,int.class,int.class),stage=c.getMethod("nativeStagePixels",long.class,byte[].class,int.class,int.class,int.class,int.class,int.class,int.class),post=c.getMethod("nativePresent",long.class),detach=c.getMethod("nativeDetach",long.class);
  ImageReader r=ImageReader.newInstance(32,16,PixelFormat.RGBA_8888,2);long target=(Long)attach.invoke(null,r.getSurface(),32,16);
  check(target!=0,"nativeAttach");byte[] initial=new byte[32*16*4];for(int i=0;i<initial.length;i+=4){initial[i]=7;initial[i+1]=11;initial[i+2]=19;}
  try{
   for(int frame=0;frame<8;frame++){
    check((Boolean)stage.invoke(null,target,frame==0?initial:new byte[]{1,2,3,0},0,frame==0?128:4,0,0,frame==0?32:1,frame==0?16:1),"stage");check((Boolean)post.invoke(null,target),"post");
    Image im=null;for(int n=0;n<100&&im==null;n++){im=r.acquireNextImage();if(im==null)Thread.sleep(5);}check(im!=null,"image available");
    Image.Plane plane=im.getPlanes()[0];ByteBuffer b=plane.getBuffer();int stride=plane.getRowStride(),pixel=plane.getPixelStride();int offset=15*stride+31*pixel;
    check((b.get(offset)&255)==19&&(b.get(offset+1)&255)==11&&(b.get(offset+2)&255)==7,"unchanged pixels lost");
    check((b.get(0)&255)==(frame==0?19:3),"updated red channel");im.close();
   }
   System.out.println("ANDROID_SURFACE_MULTI_BUFFER_PARTIAL_UPDATE_PIXELS_OK");
  }finally{detach.invoke(null,target);r.close();}
 }
 static void render(String directory,int display)throws Exception{
  System.setProperty("moe.n4tsu.dextop.linux_library",directory+"/libdextop_linux.so");
  Class<?> xc=Class.forName("moe.n4tsu.dextop.linux.X11Client");Object cp=xc.getField("Companion").get(null);
  Object x=cp.getClass().getMethod("connect",int.class).invoke(cp,display),app=cp.getClass().getMethod("connect",int.class).invoke(cp,display);
  Class<?> sc=Class.forName("moe.n4tsu.dextop.linux.LinuxAppSession"),fn=Class.forName("kotlin.jvm.functions.Function1");
  Object callback=Proxy.newProxyInstance(fn.getClassLoader(),new Class[]{fn},(p,m,args)->null);
  Process process=new ProcessBuilder("/system/bin/sleep","60").start();
  Object session=sc.getConstructor(String.class,int.class,Process.class,xc,fn).newInstance("cRecoveryTest",display,process,x,callback);
  Method begin=sc.getDeclaredMethod("begin");begin.setAccessible(true);begin.invoke(session);
  ImageReader reader=ImageReader.newInstance(32,16,PixelFormat.RGBA_8888,3);
  Method send=xc.getDeclaredMethod("request",ByteBuffer.class,boolean.class,FileDescriptor.class,boolean.class);send.setAccessible(true);
  int id=(Integer)xc.getMethod("allocateId").invoke(app),root=(Integer)xc.getMethod("getRoot").invoke(app);
  ByteBuffer create=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);create.put((byte)1).put((byte)0).putShort((short)9).putInt(id).putInt(root).putShort((short)0).putShort((short)0).putShort((short)32).putShort((short)16).putShort((short)0).putShort((short)1).putInt(0).putInt(2).putInt(0x112233);
  try{
   send.invoke(app,create,false,null,false);xc.getMethod("mapWindow",int.class).invoke(app,id);xc.getMethod("sync").invoke(app);
   boolean attached=false;for(int n=0;n<100&&!attached;n++){attached=(Boolean)sc.getMethod("attachSurface",int.class,Surface.class).invoke(session,id,reader.getSurface());if(!attached)Thread.sleep(20);}check(attached,"managed window");
   awaitPixel(reader,0x11);
   Field shared=sc.getDeclaredField("shared");shared.setAccessible(true);Object segment=shared.get(session);check(segment!=null,"SHM path active");int sid=(Integer)segment.getClass().getMethod("getSegment").invoke(segment);
   xc.getMethod("shmDetach",int.class).invoke(x,sid);xc.getMethod("sync").invoke(x);
   ByteBuffer change=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);change.put((byte)2).put((byte)0).putShort((short)4).putInt(id).putInt(2).putInt(0x445566);send.invoke(app,change,false,null,false);
   clear(send,app,id);awaitPixel(reader,0x44);
   Field failed=sc.getDeclaredField("sharedFailed");failed.setAccessible(true);check(failed.getBoolean(session),"SHM failure did not switch to GetImage");
   Field wins=sc.getDeclaredField("windows");wins.setAccessible(true);Object win=((Map<?,?>)wins.get(session)).get(id);Field pix=win.getClass().getDeclaredField("pixmap");pix.setAccessible(true);int saved=pix.getInt(win);
   Method dirty=sc.getDeclaredMethod("markDirty",win.getClass(),android.graphics.Rect.class);dirty.setAccessible(true);
   pix.setInt(win,0);dirty.invoke(session,win,new android.graphics.Rect(0,0,32,16));Thread.sleep(150);
   Field failures=win.getClass().getDeclaredField("frameFailures");failures.setAccessible(true);check(failures.getInt(win)>0,"capture failure was not retained for retry");
   pix.setInt(win,saved);change.clear();change.put((byte)2).put((byte)0).putShort((short)4).putInt(id).putInt(2).putInt(0x778899);send.invoke(app,change,false,null,false);clear(send,app,id);awaitPixel(reader,0x77);
   System.out.println("APP_RENDER_SHM_TO_GETIMAGE_FALLBACK_FAILED_FRAME_RETRY_RECOVERY_OK");
  }finally{sc.getMethod("stop").invoke(session);reader.close();xc.getMethod("close").invoke(app);}
 }
 static void clear(Method send,Object app,int id)throws Exception{ByteBuffer b=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);b.put((byte)61).put((byte)0).putShort((short)4).putInt(id).putShort((short)0).putShort((short)0).putShort((short)0).putShort((short)0);send.invoke(app,b,false,null,false);}
 static void awaitPixel(ImageReader reader,int red)throws Exception{for(int n=0;n<300;n++){Image im=reader.acquireNextImage();if(im!=null){int value=im.getPlanes()[0].getBuffer().get(0)&255;im.close();if(value==red)return;}Thread.sleep(10);}throw new AssertionError("missing frame red="+red);}
 public static void main(String[] args)throws Exception{
  if(args[0].equals("cleanup")){
   Class<?> c=Class.forName("moe.n4tsu.dextop.linux.LinuxRuntime");Object rt=c.getField("INSTANCE").get(null);
   System.out.println("DELETED_CONTAINER_PROCESSES_TERMINATED="+c.getMethod("forceStop",String.class).invoke(rt,args[1]));
  }else if(args[0].equals("shared"))shared(Integer.parseInt(args[1]));else if(args[0].equals("runtime"))runtime();else if(args[0].equals("frame"))frame(args[1]);else if(args[0].equals("render"))render(args[1],Integer.parseInt(args[2]));
 }
}
