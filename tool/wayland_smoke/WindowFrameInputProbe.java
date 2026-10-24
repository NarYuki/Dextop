import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Headless event-dispatch regression: no overlay, Surface, screenshot or UI automation. */
public class WindowFrameInputProbe {
    static final Map<String, Integer> calls = new HashMap<>();
    static int behind;
    static float lastX, lastY;
    static long down = SystemClock.uptimeMillis();

    static MotionEvent event(int action, float x, float y, int buttons, int changed) throws Exception {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x; coords.y = y; coords.pressure = 1;
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coords},
            0, buttons, 1, 1, 4, 0, InputDevice.SOURCE_MOUSE, 0);
        MotionEvent.class.getDeclaredMethod("setActionButton", int.class).invoke(e, changed);
        return e;
    }

    static void generic(View root, int action, float x, float y, int buttons, int changed) throws Exception {
        MotionEvent e = event(action, x, y, buttons, changed);
        try { if (!root.dispatchGenericMotionEvent(e)) throw new AssertionError("generic not consumed"); }
        finally { e.recycle(); }
    }

    static void touch(View root, int action, float x, float y, int buttons) throws Exception {
        MotionEvent e = event(action, x, y, buttons, 0);
        try { if (!root.dispatchTouchEvent(e)) throw new AssertionError("touch not consumed"); }
        finally { e.recycle(); }
    }

    static int count(String name) { return calls.getOrDefault(name, 0); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        try { run(); }
        catch (Throwable error) { error.printStackTrace(System.out); System.exit(1); }
    }

    static void run() throws Exception {
        if (Looper.myLooper() == null) Looper.prepareMainLooper();
        Class<?> activity = Class.forName("android.app.ActivityThread");
        Object thread = activity.getMethod("systemMain").invoke(null);
        Context system = (Context) activity.getMethod("getSystemContext").invoke(thread);
        Context context = system.createPackageContext("moe.n4tsu.dextop", Context.CONTEXT_IGNORE_SECURITY);
        // app_process bypasses zygote's font preload (including Samsung's
        // default-font fields). This only initializes this headless process.
        android.graphics.fonts.Font baseFont = new android.graphics.fonts.Font.Builder(new java.io.File("/system/fonts/Roboto-Regular.ttf")).build();
        android.graphics.fonts.FontFamily family = new android.graphics.fonts.FontFamily.Builder(baseFont).build();
        long familyPointer = (long) family.getClass().getDeclaredMethod("getNativePtr").invoke(family);
        java.lang.reflect.Method nativeCreate = android.graphics.Typeface.class.getDeclaredMethod("nativeCreateFromArray", long[].class, long.class, int.class, int.class);
        nativeCreate.setAccessible(true);
        long nativeFont = (long) nativeCreate.invoke(null, new long[]{familyPointer}, 0L, 400, -1);
        java.lang.reflect.Constructor<android.graphics.Typeface> fontConstructor = android.graphics.Typeface.class.getDeclaredConstructor(long.class);
        fontConstructor.setAccessible(true);
        android.graphics.Typeface font = fontConstructor.newInstance(nativeFont);
        for (java.lang.reflect.Field field : android.graphics.Typeface.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == android.graphics.Typeface.class) {
                field.setAccessible(true);
                if (field.get(null) == null) field.set(null, font);
            }
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == android.graphics.Typeface[].class) {
                field.setAccessible(true);
                if (field.get(null) == null) field.set(null, new android.graphics.Typeface[]{font, font, font, font});
            }
        }
        Class<?> hostType = Class.forName("moe.n4tsu.dextop.plasma.V2WindowHost");
        Class<?> theme = Class.forName("moe.n4tsu.dextop.plasma.PlasmaTheme");
        Object palette = theme.getMethod("getBreezeDark").invoke(theme.getField("INSTANCE").get(null));
        Object host = Proxy.newProxyInstance(hostType.getClassLoader(), new Class[]{hostType}, (p, m, a) -> {
            if (m.getName().equals("getPalette")) return palette;
            if (m.getName().equals("isActive")) return true;
            calls.merge(m.getName(), 1, Integer::sum);
            if (m.getName().equals("move")) { lastX = (float) a[1]; lastY = (float) a[2]; }
            return null;
        });
        Class<?> model = Class.forName("moe.n4tsu.dextop.plasma.V2Window");
        Object window = model.getConstructor(int.class, String.class, Context.class, hostType, int.class, int.class)
            .newInstance(1, "linux:test", context, host, 32, 7);
        model.getMethod("setLinuxClientDecorated", boolean.class).invoke(window, true);
        model.getMethod("setLinuxXid", int.class).invoke(window, 5);
        android.graphics.Rect frame = (android.graphics.Rect) model.getMethod("getFrame").invoke(window);
        frame.set(107, 107, 507, 387);
        FrameLayout front = (FrameLayout) model.getMethod("getView").invoke(window);
        front.getClass().getMethod("refreshDecorations").invoke(front);
        Class<?> function = Class.forName("kotlin.jvm.functions.Function1");
        Object capture = Proxy.newProxyInstance(function.getClassLoader(), new Class[]{function}, (p, m, a) -> {
            if (!m.getName().equals("invoke")) return null;
            return (boolean) front.getClass().getMethod("ownsFramePointer", MotionEvent.class).invoke(front, a[0]) ? front : null;
        });
        FrameLayout root = (FrameLayout) Class.forName("moe.n4tsu.dextop.plasma.V2WindowInputRoot")
            .getConstructor(Context.class, function).newInstance(context, capture);
        View back = new View(context);
        back.setOnTouchListener((v, e) -> { behind++; return true; });
        back.setOnGenericMotionListener((v, e) -> { behind++; return true; });
        back.setOnHoverListener((v, e) -> { behind++; return true; });
        root.addView(back, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(414, 294);
        params.leftMargin = 100; params.topMargin = 100;
        root.addView(front, params);
        View panel = new View(context);
        panel.setOnGenericMotionListener((v, e) -> { behind++; return true; });
        panel.setOnHoverListener((v, e) -> { behind++; return true; });
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, 100);
        panelParams.topMargin = 600;
        root.addView(panel, panelParams);
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1000, 800);

        generic(root, MotionEvent.ACTION_HOVER_MOVE, 101, 180, 0, 0);
        generic(root, MotionEvent.ACTION_SCROLL, 101, 180, 0, 0);
        check(behind == 0 && count("forwardInput") == 0, "frame leaked hover/scroll");
        generic(root, MotionEvent.ACTION_BUTTON_PRESS, 307, 103, 1, 1);
        generic(root, MotionEvent.ACTION_HOVER_MOVE, 900, 650, 1, 0);
        generic(root, MotionEvent.ACTION_HOVER_MOVE, 900, 650, 0, 0);
        generic(root, MotionEvent.ACTION_BUTTON_RELEASE, 900, 650, 0, 1);
        check(count("beginMove") == 1 && count("move") == 2 && count("endMove") == 1, "generic drag was not captured");
        check(lastX == 900 && lastY == 650, "raw coordinates changed");
        check(behind == 0 && count("forwardInput") == 0, "drag leaked behind or into client");

        touch(root, MotionEvent.ACTION_DOWN, 101, 180, 1);
        generic(root, MotionEvent.ACTION_BUTTON_PRESS, 101, 180, 1, 1);
        touch(root, MotionEvent.ACTION_MOVE, 115, 190, 1);
        touch(root, MotionEvent.ACTION_UP, 115, 190, 0);
        generic(root, MotionEvent.ACTION_BUTTON_RELEASE, 115, 190, 0, 1);
        check(count("beginResize") == 1 && count("endResize") == 1, "mixed stream duplicated resize");
        check(behind == 0, "resize leaked behind");

        touch(root, MotionEvent.ACTION_DOWN, 200, 250, 1);
        touch(root, MotionEvent.ACTION_UP, 200, 250, 0);
        check(count("forwardInput") > 0 && behind == 0, "client input blocked or leaked");
        model.getMethod("setLinuxClientDecorated", boolean.class).invoke(window, false);
        front.getClass().getMethod("refreshDecorations").invoke(front);
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1000, 800);
        generic(root, MotionEvent.ACTION_BUTTON_PRESS, 200, 120, 1, 1);
        generic(root, MotionEvent.ACTION_HOVER_MOVE, 900, 650, 1, 0);
        generic(root, MotionEvent.ACTION_BUTTON_RELEASE, 900, 650, 0, 1);
        check(count("beginMove") == 2 && count("endMove") == 2, "normal titlebar drag failed");
        View title = (View) front.getClass().getMethod("getTitleBar").invoke(front);
        float density = context.getResources().getDisplayMetrics().density;
        float closeX = 107 + title.getWidth() - 17 * density;
        float closeY = 107 + title.getHeight() / 2f;
        java.lang.reflect.Field hovered = title.getClass().getDeclaredField("hovered");
        hovered.setAccessible(true);
        for (int index = 0; index < 3; index++) {
            generic(root, MotionEvent.ACTION_HOVER_MOVE, closeX - index * 26 * density, closeY, 0, 0);
            check(hovered.get(title) != null, "titlebar button hover did not reach the button");
        }
        generic(root, MotionEvent.ACTION_HOVER_MOVE, 101, 180, 0, 0);
        check(hovered.get(title) == null, "leaving the titlebar left a stale hover");
        check(behind == 0, "hover leaked through the window frame");
        touch(root, MotionEvent.ACTION_DOWN, closeX, closeY, 1);
        generic(root, MotionEvent.ACTION_BUTTON_PRESS, closeX, closeY, 1, 1);
        touch(root, MotionEvent.ACTION_UP, closeX, closeY, 0);
        generic(root, MotionEvent.ACTION_BUTTON_RELEASE, closeX, closeY, 0, 1);
        check(count("close") == 1 && behind == 0, "titlebar button duplicated or leaked");
        System.out.println("FRAME_INPUT_OK_NO_CLICK_THROUGH_DRAG_CAPTURE_COORDINATES_RESIZE_CLIENT_INPUT");
        System.exit(0);
    }
}
