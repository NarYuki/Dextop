import java.lang.reflect.*;
import java.nio.file.*;
public class ExportSteamInstaller {
 public static void main(String[] args) throws Exception {
  Class<?> owner=Class.forName("moe.n4tsu.dextop.linux.LinuxSteam");
  Object instance=owner.getField("INSTANCE").get(null);
  Class<?> callback=Class.forName("kotlin.jvm.functions.Function1");
  Object noPackages=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a)->"");
  String script=(String)owner.getMethod("installScript",boolean.class,callback).invoke(instance,true,noPackages);
  Files.writeString(Path.of(args[0]),script);
 }
}
