import android.media.Image;
import android.media.ImageReader;
import android.graphics.PixelFormat;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.io.FileDescriptor;
import android.net.LocalServerSocket;
import android.net.LocalSocket;

public class VulkanSurfaceSmoke {
    static native int render(Surface surface, FileDescriptor descriptor, FileDescriptor acquire);
    static native int present(Surface surface, Surface second, FileDescriptor descriptor, FileDescriptor acquire, boolean scene);
    public static void main(String[] args) throws Exception {
        System.load("/data/local/tmp/dextop-wayland-smoke/libvulkan-surface-probe.so");
        boolean scene = args.length > 0 && args[0].equals("--scene");
        try (ImageReader reader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 3);
             ImageReader second = ImageReader.newInstance(96, 80, PixelFormat.RGBA_8888, 3)) {
            LocalServerSocket server = null;
            LocalSocket client = null;
            FileDescriptor descriptor = null;
            FileDescriptor acquire = null;
            int code;
            try {
                if (args.length > 0 && (args[0].equals("--guest") || args[0].equals("--presenter") || scene)) {
                    server = new LocalServerSocket("dextop-gpu-import-guest-probe");
                    System.out.println("GUEST_DMABUF_READY");
                    client = server.accept();
                    client.setSoTimeout(10000);
                    if (client.getInputStream().read() != 1) throw new AssertionError("Invalid guest message");
                    FileDescriptor[] descriptors = client.getAncillaryFileDescriptors();
                    if (descriptors == null || descriptors.length < 1 || descriptors.length > 2) throw new AssertionError("Expected dma-buf and optional sync FD");
                    descriptor = descriptors[0];
                    if (descriptors.length == 2) acquire = descriptors[1];
                }
                code = args.length > 0 && (args[0].equals("--presenter") || scene)
                    ? present(reader.getSurface(), second.getSurface(), descriptor, acquire, scene)
                    : render(reader.getSurface(), descriptor, acquire);
                if (client != null) client.getOutputStream().write(code == 0 ? 1 : 0);
            } finally {
                if (descriptor != null) android.system.Os.close(descriptor);
                if (acquire != null) android.system.Os.close(acquire);
                if (client != null) client.close();
                if (server != null) server.close();
            }
            if (code != 0) throw new AssertionError("GPU surface render failed: " + code);
            verify(reader, scene);
            if (args.length > 0 && (args[0].equals("--presenter") || scene)) verify(second, scene);
        }
    }

    static void verify(ImageReader reader, boolean scene) throws Exception {
            Image frame = null;
            for (int i = 0; i < 100 && frame == null; i++) {
                frame = reader.acquireLatestImage();
                if (frame == null) Thread.sleep(20);
            }
            if (frame == null) throw new AssertionError("No Surface frame");
            try {
                Image.Plane plane = frame.getPlanes()[0];
                ByteBuffer pixels = plane.getBuffer();
                int matches = 0;
                for (int y = 0; y < frame.getHeight(); y++) for (int x = 0; x < frame.getWidth(); x++) {
                    int p = y * plane.getRowStride() + x * plane.getPixelStride();
                    boolean center = scene && x >= frame.getWidth()/4 && x < frame.getWidth()*3/4 &&
                        y >= frame.getHeight()/4 && y < frame.getHeight()*3/4;
                    int red = center ? 127 : 255, blue = center ? 128 : 0;
                    if (Math.abs((pixels.get(p) & 255)-red)<=1 && pixels.get(p + 1) == 0 &&
                        Math.abs((pixels.get(p + 2)&255)-blue)<=1) matches++;
                }
                if (matches != frame.getWidth() * frame.getHeight()) throw new AssertionError("Wrong pixels: " + matches);
                System.out.println((scene ? "GPU_SCENE_BLEND_PIXELS_OK " : "DMABUF_GPU_SURFACE_PIXELS_OK ") + matches);
            } finally { frame.close(); }
    }
}
