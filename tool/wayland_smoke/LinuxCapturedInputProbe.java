import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.lang.reflect.*;
import java.util.*;

/** Runs the shared production forwarder with real Android MotionEvents in app_process. */
public class LinuxCapturedInputProbe {
    static final List<String> sent = new ArrayList<>();
    static Object forwarder, state, motion, send, scroll;
    static Method captured;
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message + ": " + sent); }
    static Object callback(String type, String prefix) throws Exception {
        Class<?> fn = Class.forName("kotlin.jvm.functions." + type);
        return Proxy.newProxyInstance(fn.getClassLoader(), new Class[]{fn}, (p, m, a) -> {
            if (m.getName().equals("invoke")) sent.add(prefix + Arrays.toString(a));
            return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        });
    }
    static MotionEvent event(int source, int action, float x, float y, int buttons, float dx, float dy) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0; properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x; coords.y = y;
        coords.setAxisValue(MotionEvent.AXIS_RELATIVE_X, dx);
        coords.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, dy);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, dy);
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, 1, new MotionEvent.PointerProperties[]{properties},
            new MotionEvent.PointerCoords[]{coords}, 0, buttons, 1, 1, 1, 0, source, 0);
    }
    static void forward(MotionEvent event) throws Exception {
        try { captured.invoke(forwarder, event, state, motion, send, scroll); }
        finally { event.recycle(); }
    }
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("moe.n4tsu.dextop.plasma.LinuxPointerForwarder");
        forwarder = type.getField("INSTANCE").get(null);
        state = Class.forName("moe.n4tsu.dextop.plasma.LinuxPointerState").getConstructor().newInstance();
        motion = Class.forName("moe.n4tsu.dextop.linux.LinuxRelativeMotion").getConstructor().newInstance();
        send = callback("Function4", "pointer"); scroll = callback("Function2", "scroll");
        for (Method method : type.getMethods()) if (method.getName().equals("captured")) captured = method;
        for (int i = 0; i < 4; i++) forward(event(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_MOVE, .25f, -.25f, 0, 0, 0));
        check(sent.equals(Arrays.asList("pointer[0, 1, -1, 0]")), "fractional mouse movement");
        sent.clear();
        for (int i = 0; i < 2; i++) forward(event(InputDevice.SOURCE_TOUCHPAD, MotionEvent.ACTION_MOVE, 1000, 500, 0, .5f, -.5f));
        check(sent.equals(Arrays.asList("pointer[0, 1, -1, 0]")), "touchpad must use relative axes");
        sent.clear();
        MotionEvent history = event(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_MOVE, 2, -3, 0, 0, 0);
        history.addBatch(SystemClock.uptimeMillis() + 1, 4, -5, 1, 1, 0);
        forward(history);
        check(sent.equals(Arrays.asList("pointer[0, 2, -3, 0]", "pointer[0, 4, -5, 0]")), "historical deltas");
        sent.clear();
        forward(event(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_BUTTON_PRESS, 0, 0, 3, 0, 0));
        forward(event(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_CANCEL, 0, 0, 3, 0, 0));
        forward(event(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_CANCEL, 0, 0, 0, 0, 0));
        check(sent.equals(Arrays.asList("pointer[1, 0, 0, 1]", "pointer[1, 0, 0, 3]", "pointer[2, 0, 0, 1]", "pointer[2, 0, 0, 3]")), "cancel releases buttons once");
        sent.clear();
        for (int i = 0; i < 2; i++) forward(event(InputDevice.SOURCE_MOUSE_RELATIVE, MotionEvent.ACTION_SCROLL, 0, 0, 0, 0, .5f));
        check(sent.equals(Arrays.asList("scroll[0, -1]")), "fractional wheel");
        System.out.println("CAPTURED_MOUSE_TOUCHPAD_HISTORY_BUTTONS_AND_WHEEL_OK");
    }
}
