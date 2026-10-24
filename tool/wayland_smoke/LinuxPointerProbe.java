import java.io.*;
import java.nio.file.*;
import java.lang.reflect.*;
import org.json.*;
public class LinuxPointerProbe {
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);File actions=new File(root,"tmp/pointer-probe-actions.jsonl"),state=new File(root,"tmp/pointer-probe-state.json"),stop=new File(root,"tmp/pointer-probe-stop");actions.delete();stop.delete();
  Class<?> xc=Class.forName("moe.n4tsu.dextop.linux.X11Client");Object cp=xc.getField("Companion").get(null);Object x=cp.getClass().getMethod("connect",int.class).invoke(cp,98);
  Class<?> gc=Class.forName("moe.n4tsu.dextop.linux.LinuxPointerGrabs");Object grabs=gc.getConstructor(int.class).newInstance(98);
  int consumed=0;long deadline=System.currentTimeMillis()+120000;
  System.out.println("POINTER_PROBE_READY");
  try{while(!stop.exists()&&System.currentTimeMillis()<deadline){
   xc.getMethod("sync").invoke(x);
   boolean requested=(Boolean)gc.getMethod("getRequested").invoke(grabs);
   File next=new File(state+".next");Files.writeString(next.toPath(),new JSONObject().put("requested",requested).toString());Files.move(next.toPath(),state.toPath(),StandardCopyOption.REPLACE_EXISTING);
   if(actions.exists())for(String line:Files.readAllLines(actions.toPath()).subList(consumed,Files.readAllLines(actions.toPath()).size())){
    consumed++;JSONObject a=new JSONObject(line);
    if(a.optString("command").equals("button"))xc.getMethod("fakeInput",int.class,int.class,int.class,int.class).invoke(x,a.getInt("type"),a.getInt("button"),0,0);
    else xc.getMethod("relativeMotion",int.class,int.class).invoke(x,a.getInt("dx"),a.getInt("dy"));
   }
   Thread.sleep(10);
  }}finally{gc.getMethod("stop").invoke(grabs);xc.getMethod("close").invoke(x);}
 }
}
