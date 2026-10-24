import java.lang.reflect.Method;

/** Read-only X protocol checks; does not inject input, map windows or capture pixels. */
public class LinuxXWindowProbe {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--modes")) {
            Class<?> catalog = Class.forName("moe.n4tsu.dextop.linux.LinuxCatalog");
            for (Method method : catalog.getDeclaredMethods()) {
                if (method.getName().startsWith("applicationDisplayTiming") && method.getParameterCount() == 3) {
                    System.out.println(method.invoke(catalog.getField("INSTANCE").get(null),
                        Integer.parseInt(args[1]), Integer.parseInt(args[2]), args.length > 3 ? Float.parseFloat(args[3]) : 60f));
                    return;
                }
            }
            throw new AssertionError("Missing timing generator");
        }
        Class<?> client = Class.forName("moe.n4tsu.dextop.linux.X11Client");
        Object companion = client.getField("Companion").get(null);
        Object x = companion.getClass().getMethod("connect", int.class).invoke(companion, Integer.parseInt(args[0]));
        try {
            for (int index = 1; index < args.length; index++) {
                Object attributes = client.getMethod("windowAttributes", int.class).invoke(x, Integer.decode(args[index]));
                if (attributes == null) throw new AssertionError("Window disappeared");
                boolean drawable = (boolean) attributes.getClass().getMethod("getInputOutput").invoke(attributes);
                if (drawable != (index != 1)) throw new AssertionError("Incorrect X window class");
            }
            System.out.println("X11_INPUT_ONLY_AND_DRAWABLE_CLASSIFICATION_OK");
        } finally { client.getMethod("close").invoke(x); }
    }
}
