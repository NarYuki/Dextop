import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Independent off-screen SurfaceControl -> virtual-display ImageReader probe.
 * Avoids a WindowManager session and never creates a phone/UI window. */
public final class DeckSurfaceProbeHost implements AutoCloseable {
    final ImageReader reader = ImageReader.newInstance(32,32,PixelFormat.RGBA_8888,3);
    volatile Surface surface;
    final HandlerThread thread = new HandlerThread("DeckSurfaceProbeHost");
    Handler handler;
    VirtualDisplay display;
    Object control;
    Class<?> sc;
    volatile Throwable failure;
    DeckSurfaceProbeHost() throws Exception {
        thread.start(); handler = new Handler(thread.getLooper());
        java.lang.reflect.Field main = Looper.class.getDeclaredField("sMainLooper");
        main.setAccessible(true); main.set(null,thread.getLooper());
        CountDownLatch ready = new CountDownLatch(1);
        handler.post(() -> {
            try {
                Class<?> activity = Class.forName("android.app.ActivityThread");
                Object at = activity.getMethod("systemMain").invoke(null);
                Context system = (Context)activity.getMethod("getSystemContext").invoke(at);
                Context context = system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
                DisplayManager dm = context.getSystemService(DisplayManager.class);
                display = dm.createVirtualDisplay("Dextop Deck offscreen probe",32,32,160,reader.getSurface(),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
                sc = Class.forName("android.view.SurfaceControl");
                Class<?> builder = Class.forName("android.view.SurfaceControl$Builder");
                Object b = builder.getConstructor().newInstance();
                builder.getMethod("setName",String.class).invoke(b,"Dextop offscreen Deck probe");
                builder.getMethod("setBufferSize",int.class,int.class).invoke(b,32,32);
                control = builder.getMethod("build").invoke(b);
                Object tx = Class.forName("android.view.SurfaceControl$Transaction").getConstructor().newInstance();
                int stack = (int)android.view.Display.class.getMethod("getLayerStack").invoke(display.getDisplay());
                tx.getClass().getMethod("setLayerStack",sc,int.class).invoke(tx,control,stack);
                tx.getClass().getMethod("show",sc).invoke(tx,control);
                tx.getClass().getMethod("apply").invoke(tx);
                tx.getClass().getMethod("close").invoke(tx);
                surface = Surface.class.getConstructor(sc).newInstance(control);
            } catch(Throwable e) { failure=e; }
            finally { ready.countDown(); }
        });
        if (!ready.await(10,TimeUnit.SECONDS)) throw new AssertionError("Offscreen SurfaceControl timed out");
        if (failure!=null) throw new AssertionError("Offscreen SurfaceControl failed",failure);
    }
    public void close() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        handler.post(() -> { try {
            if(surface!=null) surface.release();
            if(control!=null) {
                Object tx = Class.forName("android.view.SurfaceControl$Transaction").getConstructor().newInstance();
                tx.getClass().getMethod("remove",sc).invoke(tx,control);
                tx.getClass().getMethod("apply").invoke(tx); tx.getClass().getMethod("close").invoke(tx);
                sc.getMethod("release").invoke(control);
            }
            if(display!=null) display.release(); reader.close();
        } catch(Exception e) { throw new RuntimeException(e); } finally { done.countDown(); } });
        done.await(5,TimeUnit.SECONDS); thread.quitSafely(); thread.join(2000);
    }
}
