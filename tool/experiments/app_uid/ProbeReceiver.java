package moe.n4tsu.dextop.uidprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Fixed, headless diagnostics; never invokes commands supplied by an Intent. */
public final class ProbeReceiver extends BroadcastReceiver {
    private static final String TAG = "DextopUidProbe";
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        new Thread(() -> {
            JSONObject report = new JSONObject();
            try {
                int uid = Process.myUid();
                String lib = context.getApplicationInfo().nativeLibraryDir;
                File root = new File(context.getFilesDir(), "rootfs");
                root.mkdirs();
                try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("rootfs.zip"))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        File file = new File(root, entry.getName());
                        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + "/")) throw new IOException("Bad asset path");
                        file.getParentFile().mkdirs();
                        try (FileOutputStream out = new FileOutputStream(file)) {
                            byte[] bytes = new byte[32768]; int count;
                            while ((count = zip.read(bytes)) > 0) out.write(bytes, 0, count);
                        }
                        file.setReadable(true, true); file.setExecutable(true, true);
                    }
                }
                for (String directory : new String[]{"root", "tmp", "dev", "proc", "sys"}) new File(root, directory).mkdirs();
                File temp = new File(context.getFilesDir(), "proot-tmp"); temp.mkdirs();
                Files.write(new File(root, "etc/passwd").toPath(), ("root:x:"+uid+":"+uid+":root:/root:/bin/sh\n").getBytes("UTF-8"));
                Files.write(new File(root, "etc/group").toPath(), ("root:x:"+uid+":\n").getBytes("UTF-8"));
                report.put("app_uid", uid);
                report.put("app_native", run(Arrays.asList(lib+"/libuid_probe.so"), null));
                JSONArray guests = new JSONArray();
                JSONArray metadata = new JSONArray();
                for (boolean fakeRoot : new boolean[]{false, true}) {
                    List<String> command = new ArrayList<>(Arrays.asList(lib+"/libproot.so", "--kill-on-exit"));
                    if (fakeRoot) command.add("-0");
                    else { command.add("-i"); command.add(uid+":"+uid); }
                    command.addAll(Arrays.asList("-r",root.getPath(),"-w","/root","-b","/dev","-b","/proc","-b","/sys"));
                    Map<String,String> env = new HashMap<>();
                    env.put("PROOT_LOADER", lib+"/libproot_loader.so");
                    env.put("PROOT_TMP_DIR", temp.getPath());
                    env.put("HOME","/root"); env.put("LANG","C"); env.put("PATH","/usr/bin:/bin");
                    List<String> check = new ArrayList<>(command);
                    check.add("/usr/bin/uid-probe");
                    JSONObject state = run(check, env); state.put("fake_root",fakeRoot); metadata.put(state);
                    command.addAll(Arrays.asList("/usr/bin/ping","-n","-c","1","-W","1","127.0.0.1"));
                    JSONObject result = run(command, env); result.put("fake_root",fakeRoot); guests.put(result);
                }
                report.put("debian_ping", guests);
                report.put("proot_metadata", metadata);
            } catch (Throwable error) {
                try { report.put("error", Log.getStackTraceString(error)); } catch(Exception ignored) {}
            } finally {
                try { Files.write(new File(context.getFilesDir(),"result.json").toPath(), report.toString(2).getBytes("UTF-8")); }
                catch(Exception e) { Log.e(TAG,"write failed",e); }
                Log.i(TAG, report.toString());
                pending.finish();
            }
        },"uid-probe").start();
    }
    private static JSONObject run(List<String> command, Map<String,String> environment) throws Exception {
        File result = File.createTempFile("uid-probe-", ".txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(result);
            if (environment != null) builder.environment().putAll(environment);
            java.lang.Process process = builder.start(); process.getOutputStream().close();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); process.waitFor(1,TimeUnit.SECONDS); }
            JSONObject json = new JSONObject();
            json.put("exit_code", finished ? process.exitValue() : -1);
            json.put("output", new String(Files.readAllBytes(result.toPath()),"UTF-8"));
            return json;
        } finally { result.delete(); }
    }
}
