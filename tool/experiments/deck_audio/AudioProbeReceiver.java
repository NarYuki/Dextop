package moe.n4tsu.dextop.deckaudio.probe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated SDK36 app-UID test; never changes a Dextop container or its audio server. */
public class AudioProbeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        int requested = intent.getIntExtra("port",49299);
        int port = requested>=1024 && requested<=65535 ? requested : 49299;
        new Thread(() -> {
            Process process = null;
            AtomicBoolean stopping = new AtomicBoolean();
            try {
                if (intent.getBooleanExtra("product",false)) {
                    Class<?> host=Class.forName("moe.n4tsu.dextop.linux.LinuxDeckAudio");
                    Object instance=host.getField("INSTANCE").get(null);
                    java.lang.reflect.Method ensure=host.getMethod("ensure",Context.class,String.class);
                    java.lang.reflect.Method release=host.getMethod("release",String.class);
                    try {
                        int first=(Integer)ensure.invoke(instance,context,"probe-a");
                        int second=(Integer)ensure.invoke(instance,context,"probe-b");
                        if (first!=second) throw new IllegalStateException("Host was not reused");
                        release.invoke(instance,"probe-a");
                        Files.write(new File(context.getFilesDir(),"product-port").toPath(),Integer.toString(second).getBytes(StandardCharsets.UTF_8));
                        Log.i("DeckAudioProbe","PRODUCT_AUDIO_SHARED_READY="+second);
                        if (intent.getBooleanExtra("flap",false)) {
                            java.lang.reflect.Field childField=host.getDeclaredField("process");
                            childField.setAccessible(true);
                            File pidFile=new File(context.getFilesDir(),"deck-audio/runtime/pid");
                            for (int crash=0;crash<4;crash++) {
                                int pid=Integer.parseInt(Files.readString(pidFile.toPath()).trim());
                                android.os.Process.killProcess(pid);
                                Thread.sleep(700);
                                Process active=(Process)childField.get(instance);
                                boolean alive=active!=null && active.isAlive();
                                if (alive!=(crash<3)) throw new IllegalStateException("Recovery budget mismatch at crash "+crash);
                            }
                            Log.i("DeckAudioProbe","PRODUCT_AUDIO_THREE_RESTARTS_THEN_STOP_OK");
                        }
                        if (intent.getBooleanExtra("restart",false)) {
                            File pidFile=new File(context.getFilesDir(),"deck-audio/runtime/pid");
                            int previous=Integer.parseInt(Files.readString(pidFile.toPath()).trim());
                            android.os.Process.killProcess(previous);
                            Thread.sleep(1200);
                            int current=Integer.parseInt(Files.readString(pidFile.toPath()).trim());
                            if (current==previous) throw new IllegalStateException("Audio host was not restarted");
                            int recovered=(Integer)ensure.invoke(instance,context,"probe-b");
                            if (recovered!=second) throw new IllegalStateException("Audio endpoint changed after restart");
                            Log.i("DeckAudioProbe","PRODUCT_AUDIO_RESTART_SAME_PORT_OK="+recovered);
                        }
                        Thread.sleep(4000);
                    } finally { release.invoke(instance,"probe-a");release.invoke(instance,"probe-b"); }
                    Log.i("DeckAudioProbe","PRODUCT_AUDIO_RELEASE_COMPLETE");
                    return;
                }
                File dir = new File(context.getFilesDir(),"pulse"); dir.mkdirs();
                File modules = new File(dir,"modules"); modules.mkdirs();
                for (String module : context.getAssets().list("modules"))
                    try (InputStream in=context.getAssets().open("modules/"+module)) {
                        Files.copy(in,new File(modules,module).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                String config = "load-module module-native-protocol-tcp listen=127.0.0.1 port="+port+" auth-anonymous=1 auth-cookie-enabled=0\n"
                    + "load-module module-directaudio-native-sink sink_name=DirectAudio performance_mode=1 adaptive=1 decay=0 limiter=1 watchdog=1 volume=1.0\n"
                    + "set-default-sink DirectAudio\n";
                Files.write(new File(dir,"default.pa").toPath(),config.getBytes(StandardCharsets.UTF_8));
                String libs=context.getApplicationInfo().nativeLibraryDir;
                ProcessBuilder builder = new ProcessBuilder(libs+"/libpulseaudio.so", "--system=false", "--disable-shm=true",
                    "--fail=true", "-n", "--file="+new File(dir,"default.pa"), "--daemonize=false", "--use-pid-file=false",
                    "--exit-idle-time=-1", "--dl-search-path="+modules, "--log-level=info", "--log-target=stderr");
                builder.directory(dir).redirectErrorStream(true);
                builder.environment().put("LD_LIBRARY_PATH",libs+":"+modules);
                builder.environment().put("HOME",dir.toString()); builder.environment().put("TMPDIR",dir.toString());
                process=builder.start();
                final Process running=process;
                Thread stop=new Thread(() -> {
                    try {Thread.sleep(8000);} catch (InterruptedException ignored) {}
                    stopping.set(true);
                    running.destroy();
                    try {if (!running.waitFor(1,TimeUnit.SECONDS)) running.destroyForcibly();} catch (InterruptedException ignored) {}
                }); stop.setDaemon(true); stop.start();
                try (BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream()));
                     PrintWriter out=new PrintWriter(new File(dir,"probe.log"))) {
                    String line;
                    while ((line=in.readLine())!=null) {out.println(line);out.flush();Log.i("DeckAudioProbe",line);}
                }
                Log.i("DeckAudioProbe","PROBE_EXIT="+process.waitFor());
            } catch (Exception failure) {
                if (stopping.get() && failure instanceof IOException)
                    Log.i("DeckAudioProbe","PROBE_TIMED_STOP");
                else Log.e("DeckAudioProbe","PROBE_FAILED",failure);
            }
            finally {if(process!=null && process.isAlive())process.destroyForcibly();result.finish();}
        },"DeckAudioProbe").start();
    }
}
