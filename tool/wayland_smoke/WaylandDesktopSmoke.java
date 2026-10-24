import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.view.Surface;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/** Runs the Linux compositor independently of Plasma/DeX and captures a nested desktop. */
public class WaylandDesktopSmoke {
    static volatile Object compositor;
    public static void main(String[] args) throws Exception {
        String directory = args[0];
        System.setProperty("moe.n4tsu.dextop.linux_library", directory + "/libdextop_linux.so");
        Class<?> type = Class.forName("moe.n4tsu.dextop.linux.WaylandCompositor");
        Class<?> function = Class.forName("kotlin.jvm.functions.Function1");
        ConcurrentHashMap<Integer, ImageReader> readers = new ConcurrentHashMap<>();
        Object listener = Proxy.newProxyInstance(function.getClassLoader(), new Class[]{function}, (proxy, method, values) -> {
            if (values == null || values.length == 0) return null;
            JSONObject event = (JSONObject) values[0];
            System.out.println("WINDOW " + event);
            if ("map".equals(event.optString("type"))) {
                int id = event.getInt("id");
                ImageReader reader = ImageReader.newInstance(event.getInt("w"), event.getInt("h"), PixelFormat.RGBA_8888, 3);
                ImageReader previous = readers.put(id, reader);
                type.getMethod("attach", int.class, Surface.class).invoke(compositor, id, reader.getSurface());
                if (previous != null) previous.close();
            }
            return null;
        });
        compositor = type.getConstructor(String.class, int.class, int.class, File.class, function)
            .newInstance("dextop-kwin-probe/tmp/runtime-root/wayland-0", 800, 600, null, listener);
        System.out.println("DESKTOP_PROBE_READY");
        int frames = 0;
        boolean captured = false;
        long firstFrame = 0;
        long started = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - started < 120000) {
                for (ImageReader reader : readers.values()) {
                    try (Image frame = reader.acquireLatestImage()) {
                        if (frame == null) continue;
                        frames++;
                        if (firstFrame == 0) firstFrame = System.currentTimeMillis();
                        if (System.currentTimeMillis() - firstFrame < 15000) continue;
                        if (frame.getWidth() < 320 || frame.getHeight() < 240) continue;
                        Image.Plane plane = frame.getPlanes()[0];
                        ByteBuffer pixels = plane.getBuffer();
                        ByteBuffer packed = ByteBuffer.allocate(frame.getWidth() * frame.getHeight() * 4);
                        byte[] row = new byte[frame.getWidth() * 4];
                        for (int y = 0; y < frame.getHeight(); y++) {
                            pixels.position(y * plane.getRowStride());
                            pixels.get(row);
                            packed.put(row);
                        }
                        packed.flip();
                        Bitmap bitmap = Bitmap.createBitmap(frame.getWidth(), frame.getHeight(), Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(packed);
                        try (FileOutputStream out = new FileOutputStream(directory + "/desktop.png")) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        }
                        bitmap.recycle();
                        captured = true;
                    }
                }
                if (captured && System.currentTimeMillis() - firstFrame > 65000) break;
                Thread.sleep(5);
            }
        } finally {
            type.getMethod("stop").invoke(compositor);
            readers.values().forEach(ImageReader::close);
        }
        if (!captured) throw new AssertionError("No nested desktop frame; frames=" + frames);
        System.out.println("DESKTOP_FRAME_CAPTURED frames=" + frames);
    }
}
