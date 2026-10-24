import java.nio.file.*;
import java.lang.reflect.*;
public class ExportSteamLauncher {
 public static void main(String[] args)throws Exception {
  Class<?> c=Class.forName("moe.n4tsu.dextop.linux.LinuxSteam");
  for(Method m:c.getMethods())if(m.getName().startsWith("getLauncherScript")){
   Files.writeString(Path.of(args[0]),(String)m.invoke(c.getField("INSTANCE").get(null)));return;
  }
  throw new AssertionError("No packaged launcher getter");
 }
}
