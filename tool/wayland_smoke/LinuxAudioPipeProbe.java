import android.os.ParcelFileDescriptor;
import java.io.DataInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Real Android JNI binary pipe: verifies no PTY translation and correct child reaping. */
public class LinuxAudioPipeProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("moe.n4tsu.dextop.linux_library", args[0]);
        Class<?> api = Class.forName("moe.n4tsu.dextop.linux.LinuxPty");
        Method spawn = api.getDeclaredMethod("nativeSpawnPipe", String[].class, String[].class, int[].class);
        Method wait = api.getDeclaredMethod("nativeWaitFor", int.class);
        spawn.setAccessible(true); wait.setAccessible(true);
        String[] environment = System.getenv().entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).toArray(String[]::new);
        for (int n=0; n<8; ++n) {
            int[] pid = new int[1];
            int fd = (int) spawn.invoke(null, new String[]{"/system/bin/sh", "-c", "printf '\\000\\012\\015\\377ABC'"}, environment, pid);
            if (fd < 0) throw new AssertionError("pipe errno="+fd);
            byte[] bytes = new byte[7];
            try (DataInputStream input = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.adoptFd(fd)))) {
                input.readFully(bytes);
                if (input.read() != -1) throw new AssertionError("unexpected trailing PCM bytes");
            }
            if (!Arrays.equals(bytes, new byte[]{0,10,13,(byte)255,65,66,67})) throw new AssertionError(Arrays.toString(bytes));
            if ((int) wait.invoke(null, pid[0]) != 0) throw new AssertionError("child failed");
        }
        System.out.println("PASSED: 8 binary pipe streams, exact NUL/LF/CR/FF bytes, EOF and reaping");
    }
}
