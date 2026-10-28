import android.os.*;
import android.media.*;
import android.graphics.PixelFormat;
import android.view.Surface;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import org.json.*;

/** Exercise the actual UserService entry path and app buffers without Android UI. */
public class LinuxServiceRenderProbe {
    static Object service;
    static Class<?> type;
    static String container;
    static final ConcurrentHashMap<Integer,ImageReader> readers=new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Integer,String> classes=new ConcurrentHashMap<>();
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        container=args[1];
        System.setProperty("moe.n4tsu.dextop.linux_library",args[0]+"/libdextop_linux.so");
        type=Class.forName("moe.n4tsu.dextop.linux.LinuxContainerService");
        service=type.getConstructor().newInstance();
        Method initialize=null;
        for(Method method:type.getMethods())if(method.getName().equals("initialize"))initialize=method;
        Class<?> callback=initialize.getParameterTypes()[1];
        Binder binder=new Binder();
        Object listener=Proxy.newProxyInstance(callback.getClassLoader(),new Class[]{callback},(proxy,method,values)->{
            if(method.getName().equals("asBinder"))return binder;
            if(values!=null && values.length==2 && values[1] instanceof String && ((String)values[1]).startsWith("{")) {
                JSONObject e=new JSONObject((String)values[1]);
                if(e.optString("type").equals("map") && !e.optBoolean("popup")) {
                    int id=e.getInt("xid");
                    classes.put(id,e.optString("class"));
                    ImageReader reader=ImageReader.newInstance(Math.max(1,e.optInt("w")),Math.max(1,e.optInt("h")),PixelFormat.RGBA_8888,3);
                    readers.put(id,reader);
                    System.out.println("MAP class="+e.optString("class")+" xid="+id+" size="+e.optInt("w")+"x"+e.optInt("h"));
                    System.out.println("ATTACH "+type.getMethod("attachWindow",String.class,int.class,Surface.class).invoke(service,container,id,reader.getSurface()));
                    type.getMethod("configureWindow",String.class,int.class,int.class,int.class,int.class,int.class).invoke(service,container,id,0,0,e.optInt("w"),e.optInt("h"));
                    type.getMethod("activateWindow",String.class,int.class).invoke(service,container,id);
                }
            }
            return null;
        });
        try {
            initialize.invoke(service,args[0],listener);
            JSONArray apps=new JSONArray((String)type.getMethod("listApps",String.class,String.class).invoke(service,container,"en"));
            int icons=0;for(int i=0;i<apps.length();i++)if(!apps.getJSONObject(i).optString("icon").isEmpty())icons++;
            System.out.println("SERVICE_ICONS "+icons+"/"+apps.length());
            if(icons!=apps.length())throw new AssertionError("Missing service launcher icons");
            type.getMethod("startApps",String.class,int.class,int.class,int.class).invoke(service,container,1280,720,160);
            for(int a=2;a<args.length;a++) {
                System.out.println("LAUNCH "+args[a]);
                type.getMethod("launchApp",String.class,String.class).invoke(service,container,args[a]);
                long end=System.currentTimeMillis()+18000;
                java.util.HashSet<Integer> drawn=new java.util.HashSet<>();
                boolean expectedFrame=false;
                String expected=args[a].replace(".desktop","").toLowerCase().replaceAll("[^a-z0-9]","");
                while(System.currentTimeMillis()<end) {
                    for(java.util.Map.Entry<Integer,ImageReader> entry:readers.entrySet()) {
                        Image image=entry.getValue().acquireLatestImage();
                        if(image==null)continue;
                        try {
                            ByteBuffer pixels=image.getPlanes()[0].getBuffer();
                            int stride=image.getPlanes()[0].getRowStride(),step=image.getPlanes()[0].getPixelStride();
                            long color=0;int samples=0;
                            for(int y=0;y<image.getHeight();y+=Math.max(1,image.getHeight()/16))
                                for(int x=0;x<image.getWidth();x+=Math.max(1,image.getWidth()/16)) {
                                    int offset=y*stride+x*step;
                                    color+=(pixels.get(offset)&255)+(pixels.get(offset+1)&255)+(pixels.get(offset+2)&255);samples++;
                                }
                            if((color>0 && drawn.add(entry.getKey())) || !drawn.contains(entry.getKey()))System.out.println("FRAME class="+classes.get(entry.getKey())+" size="+image.getWidth()+"x"+image.getHeight()+" rgb_sum="+color+" samples="+samples);
                            if(color>0 && classes.get(entry.getKey()).toLowerCase().replaceAll("[^a-z0-9]","").contains(expected))expectedFrame=true;
                        }finally {image.close();}
                    }
                    Thread.sleep(80);
                    if(expectedFrame)break;
                }
                if(!expectedFrame)throw new AssertionError("No non-black application frame: "+args[a]);
                System.out.println("APP_NONBLACK_FRAME_OK "+args[a]);
            }
        }finally {
            type.getMethod("stopApps",String.class).invoke(service,container);
            for(ImageReader reader:readers.values())reader.close();
        }
        System.out.println("PROBE_FINISHED");
        System.exit(0);
    }
}
