import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.json.JSONArray;
import org.json.JSONObject;

/** Headless decoding/serialization check against actual container assets. */
public class LinuxIconProbe {
    public static void main(String[] args) throws Exception {
        Class<?> decoder = Class.forName("moe.n4tsu.dextop.linux.LinuxIconDecoder");
        Object instance = decoder.getField("INSTANCE").get(null);
        ByteBuffer icon = ByteBuffer.allocate(8 + 64 * 64 * 4).order(ByteOrder.LITTLE_ENDIAN);
        icon.putInt(64).putInt(64);
        for (int i = 0; i < 64 * 64; i++) icon.putInt(0x80ff1234);
        byte[] png = (byte[]) decoder.getMethod("windowPng", byte[].class).invoke(instance, (Object) icon.array());
        Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
        if (bitmap == null || bitmap.getWidth() != 64 || bitmap.getPixel(0, 0) != 0x80ff1234)
            throw new AssertionError("EWMH ARGB/alpha conversion failed");
        bitmap.recycle();
        System.out.println("WINDOW_ICON_PNG_ARGB_ALPHA_OK");

        File root = new File(args[0]);
        Class<?> steam = Class.forName("moe.n4tsu.dextop.linux.LinuxSteam");
        steam.getMethod("stageIcon", File.class).invoke(steam.getField("INSTANCE").get(null), root);
        Class<?> entries = Class.forName("moe.n4tsu.dextop.linux.LinuxDesktopEntries");
        Object reader = entries.getField("INSTANCE").get(null);
        Object list = entries.getMethod("list", File.class, String.class).invoke(reader, root, "en");
        JSONArray apps = new JSONArray((String) entries.getMethod("toJson", File.class, java.util.List.class).invoke(reader, root, list));
        int checked = 0;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            String id = app.getString("id");
            if (!id.equals("pcmanfm.desktop") && !id.equals("dextop-steam.desktop")) continue;
            byte[] bytes = android.util.Base64.decode(app.getString("icon"), android.util.Base64.DEFAULT);
            Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (image == null || image.getWidth() < 48 || image.getHeight() < 48 || image.getWidth() > 96 || image.getHeight() > 96)
                throw new AssertionError("Missing or oversized launcher icon: " + id);
            System.out.println("LAUNCHER_ICON_OK " + id + " " + image.getWidth() + "x" + image.getHeight() + " bytes=" + bytes.length);
            image.recycle();
            checked++;
        }
        if (checked != 2) throw new AssertionError("Expected installed launchers missing");
    }
}
