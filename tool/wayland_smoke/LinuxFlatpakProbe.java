import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Dumps the actual Kotlin compatibility launch plan, or runs a bounded smoke command. */
public class LinuxFlatpakProbe {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Class<?> flatpak = Class.forName("moe.n4tsu.dextop.linux.LinuxFlatpak");
        Object plan = flatpak.getMethod("plan", File.class, String.class)
            .invoke(flatpak.getField("INSTANCE").get(null), new File(args[0]), args[1]);
        List<String> binds = (List<String>) plan.getClass().getMethod("getBinds").invoke(plan);
        List<String> environment = (List<String>) plan.getClass().getMethod("getEnvironment").invoke(plan);
        String command = (String) plan.getClass().getMethod("getCommand").invoke(plan);
        System.out.println(new JSONObject().put("binds", new JSONArray(binds))
            .put("environment", new JSONArray(environment)).put("command", command));
    }
}
