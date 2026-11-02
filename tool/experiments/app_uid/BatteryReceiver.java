package moe.n4tsu.dextop.uidprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Runs the fixed Android-mode battery (assets/battery.sh) inside files/debian under PRoot, as
 * this ordinary app process (untrusted_app), the way Dextop's Android execution mode does.
 * The intent selects only the phase name ("setup" with fake root, or "session"); it never
 * supplies commands. Output goes to files/battery-<phase>.txt.
 */
public final class BatteryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String phase = "setup".equals(intent.getStringExtra("phase")) ? "setup" : "session";
        // A broadcast may not run for minutes; the work continues in this process's thread.
        new Thread(() -> {
            File out = new File(context.getFilesDir(), "battery-" + phase + ".txt");
            try {
                String lib = context.getApplicationInfo().nativeLibraryDir;
                File root = new File(context.getFilesDir(), "debian");
                File temp = new File(context.getFilesDir(), "proot-tmp"); temp.mkdirs();
                File shm = new File(context.getFilesDir(), "shm"); shm.mkdirs();
                File script = new File(root, "root/battery.sh");
                try (InputStream in = context.getAssets().open("battery.sh")) { Files.copy(in, script.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                int uid = android.os.Process.myUid();
                List<String> command = new ArrayList<>(Arrays.asList(lib + "/libproot.so", "--kill-on-exit"));
                if (phase.equals("setup")) { command.add("--link2symlink"); command.add("-0"); }
                else { command.add("-i"); command.add(uid + ":" + uid); }
                command.addAll(Arrays.asList("-r", root.getPath(), "-w", "/root",
                    "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "/dev/urandom:/dev/random",
                    "-b", "/proc/self/fd:/dev/fd", "-b", "/proc/self/fd/0:/dev/stdin", "-b", "/proc/self/fd/1:/dev/stdout", "-b", "/proc/self/fd/2:/dev/stderr", "-b", shm.getPath() + ":/dev/shm",
                    "/usr/bin/env", "-i", "HOME=/root", "USER=root", "TERM=xterm", "LANG=C.UTF-8", "TMPDIR=/tmp",
                    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "APP_UID=" + uid, "PHASE=" + phase, "/bin/bash", "/root/battery.sh", phase));
                ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(out);
                builder.environment().put("PROOT_LOADER", lib + "/libproot_loader.so");
                builder.environment().put("PROOT_TMP_DIR", temp.getPath());
                Process process = builder.start();
                process.getOutputStream().close();
                if (!process.waitFor(40, TimeUnit.MINUTES)) process.destroyForcibly();
                try (FileWriter tail = new FileWriter(out, true)) { tail.write("\n=== battery exit " + process.exitValue() + "\n"); }
            } catch (Throwable error) {
                try (FileWriter tail = new FileWriter(out, true)) { tail.write("\n=== harness error " + Log.getStackTraceString(error)); }
                catch (IOException ignored) {}
            }
        }, "battery").start();
    }
}
