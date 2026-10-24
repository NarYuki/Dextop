import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.media.ImageReader;
import android.graphics.PixelFormat;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;

/** Headless protocol regression: a submitted frame must not be reported as discarded. */
public class WaylandPresentationProbe {
    static native FileDescriptor dmaBuffer();
    static native FileDescriptor[] directBuffer(int[] layout, boolean blue);
    static native FileDescriptor compressedBuffer(int[] layout);
    static ByteBuffer buf(int n) { return ByteBuffer.allocate(n).order(ByteOrder.nativeOrder()); }
    static LocalSocket socket;
    static int next = 4;
    static volatile Object server;
    static volatile ImageReader reader;
    static Class<?> compositor;
    static volatile boolean deckTick;
    static boolean deckProbe;
    static boolean deckLayer;
    static DeckSurfaceProbeHost deckHost;
    static int mappedId;
    static int vsyncFeedback;
    static boolean lateIdentity;
    static volatile boolean lateIdentityObserved;
    static void send(int object, int opcode, byte[] payload) throws Exception {
        socket.getOutputStream().write(buf(8 + payload.length).putInt(object)
            .putInt(((8 + payload.length) << 16) | opcode).put(payload).array());
    }
    static void ints(int object, int opcode, int... values) throws Exception {
        ByteBuffer b = buf(values.length * 4); for (int v : values) b.putInt(v); send(object, opcode, b.array());
    }
    static class Event {
        int id, op; ByteBuffer data;
        Event(int id, int op, ByteBuffer data) { this.id=id; this.op=op; this.data=data; }
    }
    static Event event() throws Exception {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] h = new byte[8]; in.readFully(h); ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.nativeOrder());
        int id=b.getInt(), word=b.getInt(); byte[] p=new byte[(word >>> 16)-8]; in.readFully(p);
        if (id==1 && (word & 65535)==0) throw new AssertionError("Wayland protocol error");
        return new Event(id, word & 65535, ByteBuffer.wrap(p).order(ByteOrder.nativeOrder()));
    }
    static String string(ByteBuffer b) {
        int n=b.getInt(); byte[] s=new byte[n-1]; b.get(s); b.position(b.position()+1+((4-n%4)%4));
        return new String(s, java.nio.charset.StandardCharsets.UTF_8);
    }
    static int bind(int global, String name) throws Exception {
        return bind(global, name, name.equals("zwp_linux_dmabuf_v1") ? 3 : 1);
    }
    static int bind(int global, String name, int version) throws Exception {
        int id=next++; byte[] s=(name+"\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer b=buf(16+((s.length+3)&~3)); b.putInt(global).putInt(s.length).put(s);
        b.position(8+((s.length+3)&~3)); b.putInt(version).putInt(id); send(2,0,b.array()); return id;
    }
    static void expectFrameColor(boolean blue) throws Exception {
        android.media.Image image=null;
        for(int tries=0;tries<100 && image==null;tries++) {
            image=reader.acquireLatestImage(); if(image==null) Thread.sleep(10);
        }
        if(image==null) throw new AssertionError("No committed frame");
        ByteBuffer p=image.getPlanes()[0].getBuffer();
        int r=p.get(0)&255, b=p.get(2)&255; image.close();
        if(blue ? b<240 || r>8 : r<240 || b>8)
            throw new AssertionError("Unexpected committed color: "+r+","+b);
    }
    static void expectDeckColor(boolean blue) throws Exception {
        int lastR=-1,lastB=-1;
        for (int tries=0;tries<300;tries++) {
            android.media.Image image=reader.acquireLatestImage();
            if (image!=null) {
                ByteBuffer pixels=image.getPlanes()[0].getBuffer(); lastR=pixels.get(0)&255; lastB=pixels.get(2)&255;
                image.close();
                if (blue ? lastB>=240 && lastR<=8 : lastR>=240 && lastB<=8) return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Deck layer pixel transfer failed: expectedBlue="+blue+" got="+lastR+","+lastB);
    }
    static void expectAlpha(int edge, int center) throws Exception {
        android.media.Image image=null;
        for(int tries=0;tries<100 && image==null;tries++) {
            image=reader.acquireLatestImage(); if(image==null) Thread.sleep(10);
        }
        if(image==null) throw new AssertionError("No alpha test frame");
        android.media.Image.Plane plane=image.getPlanes()[0]; ByteBuffer p=plane.getBuffer();
        int a=p.get(3)&255, b=p.get(16*plane.getRowStride()+16*plane.getPixelStride()+3)&255;
        image.close();
        System.out.println("OPAQUE_REGION_ALPHA edge="+a+" center="+b);
        if(Math.abs(a-edge)>2 || Math.abs(b-center)>2) throw new AssertionError("Opaque region alpha mismatch");
    }
    static int directAhb(Map<String,Integer> globals, boolean blue) throws Exception {
        int dma=bind(globals.get("zwp_linux_dmabuf_v1"),"zwp_linux_dmabuf_v1");
        int ahb=bind(globals.get("banner_ahb_v1"),"banner_ahb_v1",2);
        int[] layout=new int[1]; FileDescriptor[] fds=directBuffer(layout,blue);
        if(fds==null) throw new AssertionError("AHB allocation failed");
        int params=next++; ints(dma,1,params);
        socket.setFileDescriptorsForSend(new FileDescriptor[]{fds[0]});
        ints(params,1,0,0,layout[0]*4,0,0);socket.setFileDescriptorsForSend(null);
        int buffer=next++;ints(params,3,buffer,32,32,0x34324258,0);
        socket.setFileDescriptorsForSend(new FileDescriptor[]{fds[1]});
        ints(ahb,1,buffer,32,32,layout[0],0,0,2);socket.setFileDescriptorsForSend(null);
        android.system.Os.close(fds[0]);android.system.Os.close(fds[1]);return buffer;
    }
    public static void main(String[] args) throws Exception {
        lateIdentity = Arrays.asList(args).contains("--late-deck-identity");
        deckLayer = Arrays.asList(args).contains("--deck-layer");
        deckProbe = deckLayer || Arrays.asList(args).contains("--deck-vsync");
        if (deckLayer) deckHost = new DeckSurfaceProbeHost();
        System.setProperty("moe.n4tsu.dextop.linux_library", args[0]+"/libdextop_linux.so");
        compositor=Class.forName("moe.n4tsu.dextop.linux.WaylandCompositor");
        Class<?> fn=Class.forName("kotlin.jvm.functions.Function1");
        Object cb=Proxy.newProxyInstance(fn.getClassLoader(),new Class[]{fn},(p,m,a)->{
            if (a!=null && a.length>0) {
                org.json.JSONObject e=(org.json.JSONObject)a[0];
                if (lateIdentity && "class".equals(e.optString("type")) && e.optBoolean("deck")) lateIdentityObserved = true;
                if ("map".equals(e.optString("type"))) {
                    if (e.optBoolean("deck") != deckProbe) throw new AssertionError("Deck pacing scope mismatch");
                    if (deckProbe) {
                        Method tick = compositor.getDeclaredMethod("nativeDeckVsync", long.class, long.class);
                        Field handle = compositor.getDeclaredField("handle"); tick.setAccessible(true); handle.setAccessible(true);
                        deckTick = true;
                        Thread clock = new Thread(() -> {
                            try { while (deckTick) { synchronized(server) { if (deckTick) tick.invoke(null, handle.getLong(server), System.nanoTime()); } Thread.sleep(8); } }
                            catch (Exception failure) { throw new RuntimeException(failure); }
                        });
                        clock.setDaemon(true); clock.start();
                    }
                    mappedId=e.getInt("id");
                    reader=deckLayer ? deckHost.reader : ImageReader.newInstance(32,32,PixelFormat.RGBA_8888,3);
                    compositor.getMethod("attach",int.class,android.view.Surface.class,boolean.class,boolean.class,boolean.class)
                        .invoke(server,e.getInt("id"),deckLayer ? deckHost.surface : reader.getSurface(),deckLayer,Arrays.asList(args).contains("--deck-30fps"),Arrays.asList(args).contains("--no-vsync"));
                }
            }
            return null;
        });
        server=compositor.getConstructor(String.class,int.class,int.class,File.class,fn)
            .newInstance("dextop-presentation-probe",640,480,null,cb);
        try (LocalSocket s=new LocalSocket(); RandomAccessFile shm=new RandomAccessFile(args[0]+"/shm","rw")) {
            socket=s; s.connect(new LocalSocketAddress("dextop-presentation-probe",LocalSocketAddress.Namespace.ABSTRACT)); s.setSoTimeout(4000);
            ints(1,1,2); ints(1,0,3); Map<String,Integer> globals=new HashMap<>(), versions=new HashMap<>();
            for (;;) { Event e=event(); if(e.id==3) break; if(e.id==2 && e.op==0) {
                int id=e.data.getInt(); String name=string(e.data);
                if (globals.put(name,id)!=null && name.equals("zwp_linux_dmabuf_v1")) throw new AssertionError("Duplicate dma-buf global");
                versions.put(name,e.data.getInt());
            } }
            if (Arrays.asList(args).contains("--dmabuf-feedback")) {
                boolean deck="1".equals(System.getenv("DEXTOP_STEAM_INSIDE"));
                if (versions.get("zwp_linux_dmabuf_v1")!=(deck ? 4 : 3)) throw new AssertionError("dma-buf scope/version mismatch");
                if (deck) {
                    int dma=bind(globals.get("zwp_linux_dmabuf_v1"),"zwp_linux_dmabuf_v1",4), feedback=next++;
                    ints(dma,2,feedback); boolean table=false,device=false,tranche=false; int compressed=0,entries=0;
                    for (;;) {
                        Event e=event(); if (e.id!=feedback) continue;
                        if (e.op==1) {
                            int size=e.data.getInt();
                            FileDescriptor[] fds=socket.getAncillaryFileDescriptors();
                            if (fds==null || fds.length!=1 || size<64 || size%16!=0) throw new AssertionError("Invalid feedback table");
                            byte[] bytes=new byte[size];
                            android.system.Os.lseek(fds[0],0,android.system.OsConstants.SEEK_SET);
                            try (DataInputStream in=new DataInputStream(new FileInputStream(fds[0]))) {in.readFully(bytes);}
                            ByteBuffer data=ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
                            Set<String> pairs=new HashSet<>();
                            while (data.hasRemaining()) {
                                int format=data.getInt(), padding=data.getInt(); long modifier=data.getLong();
                                if (padding!=0 || !pairs.add(format+":"+modifier) ||
                                    (modifier!=0 && modifier!=0x0500000000000001L)) throw new AssertionError("Invalid format/modifier entry");
                                if (modifier!=0) compressed++; entries++;
                            }
                            table=true;
                        } else if (e.op==2) device=true;
                        else if (e.op==3) tranche=true;
                        else if (e.op==0) break;
                    }
                    if (!table || !device || !tranche) throw new AssertionError("Incomplete feedback");
                    System.out.println("DECK_DMABUF_V4_FEEDBACK_COMPLETE entries="+entries+" compressed="+compressed);
                } else System.out.println("NORMAL_DMABUF_V3_UNCHANGED");
                return;
            }
            int comp=bind(globals.get("wl_compositor"),"wl_compositor"),
                mem=bind(globals.get("wl_shm"),"wl_shm"), wm=bind(globals.get("xdg_wm_base"),"xdg_wm_base"),
                presentation=bind(globals.get("wp_presentation"),"wp_presentation");
            int surface=next++, xdg=next++, top=next++;
            ints(comp,0,surface); ints(wm,2,xdg,surface); ints(xdg,1,top);
            if (deckProbe) { byte[] app="gamescope\0".getBytes("UTF-8"); ByteBuffer id=buf(4+((app.length+3)&~3)); id.putInt(app.length).put(app); send(top,3,id.array()); }
            ints(surface,6);
            for (;;) { Event e=event(); if(e.id==wm && e.op==0) ints(wm,3,e.data.getInt()); if(e.id==xdg && e.op==0) {ints(xdg,4,e.data.getInt()); break;} }
            shm.setLength(4096); byte[] pixels=new byte[4096]; for(int i=0;i<4096;i+=4) {pixels[i+2]=(byte)255; pixels[i+3]=(byte)255;} shm.write(pixels);
            boolean direct=Arrays.asList(args).contains("--direct-ahb");
            int buffer;
            boolean rejectCompressed = Arrays.asList(args).contains("--reject-compressed");
            boolean compressed = rejectCompressed || Arrays.asList(args).contains("--compressed");
            if (direct) {
                System.load(args[0]+"/libprobe_dma.so");
                buffer=directAhb(globals,false);
            } else if (args.length>1 && (args[1].equals("--dmabuf") || compressed)) {
                System.load(args[0]+"/libprobe_dma.so");
                int dma=bind(globals.get("zwp_linux_dmabuf_v1"),"zwp_linux_dmabuf_v1");
                int params=next++; ints(dma,1,params);
                int[] layout = new int[]{128,0};
                FileDescriptor fd=compressed ? compressedBuffer(layout) : dmaBuffer();
                if(fd==null) throw new AssertionError("DMA producer allocation failed");
                s.setFileDescriptorsForSend(new FileDescriptor[]{fd});
                ints(params,1,0,layout[1],layout[0],compressed ? 0x05000000 : 0,compressed ? 1 : 0);
                s.setFileDescriptorsForSend(null);
                buffer=next++; ints(params,3,buffer,32,32,0x34325258,0); android.system.Os.close(fd);
                if (rejectCompressed) {
                    if ("1".equals(System.getenv("DEXTOP_STEAM_INSIDE"))) throw new AssertionError("Rejection probe must be non-Deck");
                    ints(1,0,next++);
                    try { for (;;) event(); }
                    catch (AssertionError failure) {
                        if (!"Wayland protocol error".equals(failure.getMessage())) throw failure;
                        System.out.println("NORMAL_COMPRESSED_DMABUF_REJECTED");
                        return;
                    }
                }
            } else {
                int pool=next++; buffer=next++;
                s.setFileDescriptorsForSend(new FileDescriptor[]{shm.getFD()}); ints(mem,0,pool,4096); s.setFileDescriptorsForSend(null);
                ints(pool,0,buffer,0,32,32,128,1);
            }
            int deckBlue=0;
            if (deckLayer && direct) deckBlue=directAhb(globals,true);
            if (deckLayer && !direct) {
                shm.setLength(8192); shm.seek(4096);
                byte[] bluePixels = new byte[4096];
                for (int i=0;i<4096;i+=4) { bluePixels[i]=(byte)255; bluePixels[i+3]=(byte)255; }
                shm.write(bluePixels);
                int extraPool=next++; deckBlue=next++;
                s.setFileDescriptorsForSend(new FileDescriptor[]{shm.getFD()}); ints(mem,0,extraPool,8192); s.setFileDescriptorsForSend(null);
                ints(extraPool,0,deckBlue,4096,32,32,128,1);
            }
            for (int frame=0;frame<(deckLayer ? 64 : 4);frame++) {
                if (deckLayer && frame==32) {
                    DeckSurfaceProbeHost previous=deckHost;
                    deckHost=new DeckSurfaceProbeHost(); reader=deckHost.reader;
                    compositor.getMethod("attach",int.class,android.view.Surface.class,boolean.class,boolean.class,boolean.class)
                        .invoke(server,mappedId,deckHost.surface,true,Arrays.asList(args).contains("--deck-30fps"),Arrays.asList(args).contains("--no-vsync"));
                    previous.close();
                    System.out.println("DECK_OUTPUT_REPLACED_WITH_PENDING_RELEASES");
                }
                int feedback=next++; ints(presentation,1,surface,feedback);
                ints(surface,1,deckLayer && frame%2==1 ? deckBlue : buffer,0,0); ints(surface,2,0,0,32,32); ints(surface,6);
                for (;;) { Event e=event(); if(e.id==wm && e.op==0) ints(wm,3,e.data.getInt()); if(e.id==xdg && e.op==0) ints(xdg,4,e.data.getInt());
                    if(e.id==feedback) {
                        if(e.op!=1) throw new AssertionError("Submitted frame was discarded");
                        int flags=e.data.getInt(24);
                        if((flags & 1)!=0) vsyncFeedback++;
                        if(!deckLayer && flags!=0) throw new AssertionError("Ordinary output feedback policy changed");
                        break;
                    } }
                if (deckLayer) {
                    expectDeckColor(frame%2==1);
                    if(frame==63) System.out.println("DECK_AHB_64_ALTERNATING_FRAMES_AND_REPLACEMENT_OK");
                    continue;
                }
                android.media.Image image=null;
                for(int tries=0;tries<100 && image==null;tries++) { if(reader!=null) image=reader.acquireLatestImage(); if(image==null) Thread.sleep(10); }
                if(image==null) throw new AssertionError("No submitted pixels");
                ByteBuffer pixel=image.getPlanes()[0].getBuffer();
                int red=pixel.get(0)&255, green=pixel.get(1)&255, blue=pixel.get(2)&255;
                image.close();
                if(red<240 || green>8 || blue>8) {
                    if (deckLayer) { expectFrameColor(false); }
                    else throw new AssertionError("Red input became "+red+","+green+","+blue);
                }
                System.out.println("PRESENTED_RED_FRAME="+frame);
            }
            if(lateIdentity) {
                byte[] app="gamescope\0".getBytes("UTF-8"); ByteBuffer identity=buf(4+((app.length+3)&~3));
                identity.putInt(app.length).put(app); send(top,3,identity.array());
                for(int tries=0;tries<100 && !lateIdentityObserved;tries++) Thread.sleep(10);
                if(!lateIdentityObserved) throw new AssertionError("Late Deck identity was not published");
                System.out.println("LATE_AUTHENTICATED_DECK_IDENTITY_PUBLISHED");
            }
            if(deckLayer && !Arrays.asList(args).contains("--no-vsync") && vsyncFeedback==0) throw new AssertionError("No Deck VSync-based feedback");
            if(deckLayer) System.out.println("DECK_VSYNC_PRESENTATION_FEEDBACK="+vsyncFeedback);
            System.out.println("WAYLAND_PRESENTATION_FOUR_CONSECUTIVE_FRAMES_OK");
            if (args.length>1 && args[1].equals("--transparent")) {
                shm.setLength(8192); shm.seek(4096);
                byte[] overlay=new byte[4096];
                for(int y=8;y<24;y++) for(int x=8;x<24;x++) {
                    int off=(y*32+x)*4; overlay[off]=(byte)128; overlay[off+3]=(byte)128;
                }
                shm.write(overlay);
                int pool=next++; s.setFileDescriptorsForSend(new FileDescriptor[]{shm.getFD()});
                ints(mem,0,pool,8192); s.setFileDescriptorsForSend(null);
                int alphaBuffer=next++; ints(pool,0,alphaBuffer,4096,32,32,128,0);
                int feedback=next++; ints(presentation,1,surface,feedback);
                ints(surface,1,alphaBuffer,0,0); ints(surface,6);
                for (;;) {Event e=event(); if(e.id==feedback) break;}
                android.media.Image image=null;
                for(int tries=0;tries<100 && image==null;tries++) {
                    image=reader.acquireLatestImage(); if(image==null) Thread.sleep(10);
                }
                if(image==null) throw new AssertionError("No overlay frame");
                android.media.Image.Plane plane=image.getPlanes()[0];
                ByteBuffer p=plane.getBuffer();
                int edgeAlpha=p.get(3)&255;
                int centerOffset=16*plane.getRowStride()+16*plane.getPixelStride();
                int centerBlue=p.get(centerOffset+2)&255, centerAlpha=p.get(centerOffset+3)&255;
                image.close();
                System.out.println("OVERLAY_ALPHA edge="+edgeAlpha+" center="+centerAlpha+" blue="+centerBlue);
                if(edgeAlpha>2 || Math.abs(centerAlpha-128)>2 || Math.abs(centerBlue-128)>2)
                    throw new AssertionError("Overlay transparency was flattened");
                System.out.println("WAYLAND_PREMULTIPLIED_OVERLAY_ALPHA_OK");
                // A translucent parent notification must preserve a game below it.
                int subcomp=bind(globals.get("wl_subcompositor"),"wl_subcompositor");
                int game=next++; ints(comp,0,game);
                int sub=next++; ints(subcomp,1,sub,game,surface); ints(sub,3,surface);
                ints(game,1,buffer,0,0); ints(game,6);
                feedback=next++; ints(presentation,1,surface,feedback); ints(surface,6);
                for (;;) {Event e=event(); if(e.id==feedback) break;}
                image=null;
                for(int tries=0;tries<100 && image==null;tries++) {
                    image=reader.acquireLatestImage(); if(image==null) Thread.sleep(10);
                }
                if(image==null) throw new AssertionError("No game plus overlay frame");
                plane=image.getPlanes()[0]; p=plane.getBuffer();
                int edgeRed=p.get(0)&255; edgeAlpha=p.get(3)&255;
                centerOffset=16*plane.getRowStride()+16*plane.getPixelStride();
                int centerRed=p.get(centerOffset)&255;
                centerBlue=p.get(centerOffset+2)&255; centerAlpha=p.get(centerOffset+3)&255;
                image.close();
                System.out.println("OVERLAY_GAME edgeRed="+edgeRed+" centerRed="+centerRed+" centerBlue="+centerBlue);
                if(edgeRed<253 || edgeAlpha<253 || Math.abs(centerRed-127)>2 ||
                    Math.abs(centerBlue-128)>2 || centerAlpha<253)
                    throw new AssertionError("Notification replaced or hid the game");
                System.out.println("WAYLAND_GAME_BELOW_TRANSLUCENT_OVERLAY_OK");
                ints(game,0); ints(sub,0);
                int region=next++; ints(comp,1,region); ints(region,1,0,0,32,32);
                ints(surface,4,region); ints(region,0);
                feedback=next++; ints(presentation,1,surface,feedback); ints(surface,6);
                for (;;) {Event e=event(); if(e.id==feedback) break;}
                expectAlpha(255,255);
                ints(surface,4,0);
                feedback=next++; ints(presentation,1,surface,feedback); ints(surface,6);
                for (;;) {Event e=event(); if(e.id==feedback) break;}
                expectAlpha(0,128);
                System.out.println("WAYLAND_OPAQUE_REGION_SET_AND_CLEAR_OK");
            }
            if (args.length>1 && args[1].equals("--subsurface-sync")) {
                int subcomp=bind(globals.get("wl_subcompositor"),"wl_subcompositor");
                shm.setLength(8192); shm.seek(4096);
                byte[] bluePixels=new byte[4096];
                for(int i=0;i<4096;i+=4) {bluePixels[i]=(byte)255; bluePixels[i+3]=(byte)255;}
                shm.write(bluePixels);
                int pool=next++;
                s.setFileDescriptorsForSend(new FileDescriptor[]{shm.getFD()});
                ints(mem,0,pool,8192); s.setFileDescriptorsForSend(null);
                int blue=next++;
                ints(pool,0,blue,4096,32,32,128,1);
                int child=next++, sub=next++;
                ints(comp,0,child); ints(subcomp,1,sub,child,surface);
                ints(sub,4); // Explicit set_sync, also the protocol default.
                android.media.Image stale;
                while((stale=reader.acquireLatestImage())!=null) stale.close();
                ints(child,1,blue,0,0); ints(child,2,0,0,32,32); ints(child,6);
                int sync=next++; ints(1,0,sync);
                for (;;) {Event e=event(); if(e.id==sync) break;}
                // Wait through several compositor ticks without committing the parent.
                Thread.sleep(150);
                android.media.Image premature=reader.acquireLatestImage();
                if(premature!=null) {
                    ByteBuffer p=premature.getPlanes()[0].getBuffer();
                    int r=p.get(0)&255, b=p.get(2)&255; premature.close();
                    if(b>240 && r<8) {
                        System.out.println("WAYLAND_SUBSURFACE_SYNC_FAILED: child visible before parent commit");
                        throw new AssertionError("Synchronized child displayed before parent commit");
                    }
                }
                int feedback=next++; ints(presentation,1,surface,feedback); ints(surface,6);
                for (;;) {Event e=event(); if(e.id==feedback) {
                    if(e.op!=1) throw new AssertionError("Parent commit discarded"); break;
                }}
                android.media.Image image=null;
                for(int tries=0;tries<100 && image==null;tries++) {
                    image=reader.acquireLatestImage(); if(image==null) Thread.sleep(10);
                }
                if(image==null) throw new AssertionError("Parent commit produced no frame");
                ByteBuffer p=image.getPlanes()[0].getBuffer();
                int r=p.get(0)&255, b=p.get(2)&255; image.close();
                if(b<240 || r>8) throw new AssertionError("Parent commit did not apply child");
                System.out.println("WAYLAND_SYNCHRONIZED_SUBSURFACE_ATOMIC_OK");
                // Multiple child commits merge before the parent applies them.
                ints(child,1,buffer,0,0); ints(child,6);
                ints(child,1,blue,0,0); ints(child,6); ints(surface,6);
                expectFrameColor(true);
                System.out.println("WAYLAND_CACHED_SUBSURFACE_REPLACEMENT_OK");
                // Desync immediately applies a cached commit and subsequent commits.
                ints(child,1,buffer,0,0); ints(child,6); ints(sub,5);
                expectFrameColor(false);
                ints(child,1,blue,0,0); ints(child,6);
                expectFrameColor(true);
                System.out.println("WAYLAND_SUBSURFACE_DESYNC_FLUSH_OK");
                // A surface can disappear with an unapplied cache and a live role resource.
                ints(sub,4); ints(child,1,buffer,0,0); ints(child,6);
                ints(child,0); ints(sub,0); ints(surface,6);
                expectFrameColor(false);
                System.out.println("WAYLAND_SUBSURFACE_CACHED_TEARDOWN_OK");
            }
            if (Arrays.asList(args).contains("--pipeline-stats")) Thread.sleep(5500);
        } finally { deckTick=false; compositor.getMethod("stop").invoke(server); if(deckHost!=null) deckHost.close(); else if(reader!=null) reader.close(); }
    }
}
