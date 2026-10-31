package moe.n4tsu.dextop.linux

import android.os.SystemClock
import java.io.File

/**
 * Stand-ins for /proc files an Android app UID cannot read, for the Android execution mode.
 * Only files the runtime itself cannot read are replaced; the rest stay the kernel's own.
 */
internal object LinuxProcStandIns {
    private fun readable(path: String) = runCatching { File(path).inputStream().use { it.read() } }.isSuccess

    private fun contents(): Map<String, String> {
        val uptime = SystemClock.elapsedRealtime() / 1000.0
        val cpus = Runtime.getRuntime().availableProcessors()
        val boot = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000
        val stat = buildString {
            append("cpu  0 0 0 0 0 0 0 0 0 0\n")
            repeat(cpus) { append("cpu$it 0 0 0 0 0 0 0 0 0 0\n") }
            append("intr 0\nctxt 0\nbtime $boot\nprocesses 0\nprocs_running 1\nprocs_blocked 0\nsoftirq 0\n")
        }
        return mapOf(
            "/proc/stat" to stat,
            "/proc/uptime" to "%.2f %.2f\n".format(java.util.Locale.ROOT, uptime, uptime * cpus),
            "/proc/loadavg" to "0.50 0.50 0.50 1/100 1\n",
            "/proc/version" to "Linux version ${System.getProperty("os.version")} (dextop@android) #1 SMP PREEMPT\n",
            "/proc/vmstat" to "nr_free_pages 0\nnr_inactive_anon 0\nnr_active_anon 0\nnr_inactive_file 0\nnr_active_file 0\n" +
                "pgpgin 0\npgpgout 0\npswpin 0\npswpout 0\npgfault 0\npgmajfault 0\n",
            "/proc/sys/kernel/cap_last_cap" to "40\n",
            "/proc/sys/fs/inotify/max_user_watches" to "4096\n",
            "/proc/sys/kernel/pid_max" to "4194304\n",
            "/proc/filesystems" to "nodev\tsysfs\nnodev\ttmpfs\nnodev\tproc\nnodev\tdevpts\n\text4\n\tf2fs\n\tvfat\n",
        )
    }

    /** proot binds ("stand-in:path") for the denied files, written under [dir]. */
    fun binds(dir: File): List<String> {
        dir.mkdirs()
        return contents().filterKeys { !readable(it) }.map { (path, text) ->
            val file = File(dir, path.removePrefix("/").replace('/', '_'))
            runCatching { file.writeText(text) }
            "${file.absolutePath}:$path"
        }
    }
}
