package moe.n4tsu.dextop.linux

import java.io.File
import java.util.Locale
import kotlin.math.exp

/**
 * /proc/stat, /proc/uptime and /proc/loadavg for the Android mode, where Android denies them to
 * apps: the CPU time of the processes Dextop can see (its own: the containers), like lxcfs gives a
 * container its own view. Each process's new CPU time is charged to the CPU it last ran on, so
 * every counter only grows, as top, htop, btop and psutil expect; the rest of each second is idle.
 * The run queue (processes in state R) feeds a load average with the kernel's 1, 5 and 15 minute
 * decay.
 */
internal class LinuxCpuSampler(
    private val cpus: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    private val proc: File = File("/proc"),
) {
    /** One process as /proc/<pid>/stat reports it: identity (pid + start time), state, ticks, CPU. */
    data class Process(val key: String, val state: Char, val user: Long, val system: Long, val cpu: Int)

    private val user = LongArray(cpus)
    private val system = LongArray(cpus)
    private val idle = LongArray(cpus)
    private val last = HashMap<String, Long>()
    private val loads = DoubleArray(3)
    private var lastUptime = -1.0
    private var running = 1
    private var total = 0
    private var forks = 0L
    private var lastPid = 0

    /** Advances to [uptime] seconds with [processes] as they are now. */
    @Synchronized
    fun sample(uptime: Double, processes: List<Process>) {
        val elapsed = if (lastUptime < 0) uptime else (uptime - lastUptime).coerceAtLeast(0.0)
        val busy = LongArray(cpus)
        val alive = HashSet<String>(processes.size)
        for (process in processes) {
            alive += process.key
            val ticks = process.user + process.system
            val before = last.put(process.key, ticks)
            if (before == null) forks++
            val delta = (ticks - (before ?: 0L)).coerceAtLeast(0L)
            if (delta == 0L) continue
            val cpu = process.cpu.coerceIn(0, cpus - 1)
            val share = if (ticks > 0) process.user.toDouble() / ticks else 1.0
            val userPart = Math.round(delta * share)
            user[cpu] += userPart
            system[cpu] += delta - userPart
            busy[cpu] += delta
        }
        last.keys.retainAll(alive)
        val ticks = Math.round(elapsed * HZ)
        for (cpu in 0 until cpus) idle[cpu] += (ticks - busy[cpu]).coerceAtLeast(0L)
        running = processes.count { it.state == 'R' }.coerceAtLeast(1)
        total = processes.size
        lastPid = processes.maxOfOrNull { it.key.substringBefore(':').toIntOrNull() ?: 0 } ?: lastPid
        if (lastUptime >= 0 && elapsed > 0) DECAY.forEachIndexed { index, seconds ->
            val factor = exp(-elapsed / seconds)
            loads[index] = loads[index] * factor + running * (1 - factor)
        }
        lastUptime = uptime
    }

    @Synchronized
    fun stat(boot: Long): String = buildString {
        fun line(name: String, u: Long, s: Long, i: Long) = "$name $u 0 $s $i 0 0 0 0 0 0\n"
        append(line("cpu ", user.sum(), system.sum(), idle.sum()))
        for (cpu in 0 until cpus) append(line("cpu$cpu", user[cpu], system[cpu], idle[cpu]))
        append("intr 0\nctxt 0\nbtime $boot\nprocesses $forks\nprocs_running $running\nprocs_blocked 0\nsoftirq 0\n")
    }

    @Synchronized
    fun uptime(uptime: Double): String = String.format(Locale.ROOT, "%.2f %.2f\n", uptime, idle.sum().toDouble() / HZ)

    @Synchronized
    fun loadavg(): String = String.format(Locale.ROOT, "%.2f %.2f %.2f %d/%d %d\n", loads[0], loads[1], loads[2],
        running, total.coerceAtLeast(running), lastPid)

    /** The processes visible in [proc] (an app sees only its own). */
    fun processes(): List<Process> = proc.list().orEmpty().mapNotNull { name ->
        if (name.isEmpty() || !name.all(Char::isDigit)) return@mapNotNull null
        runCatching { parse(name, File(proc, "$name/stat").readText()) }.getOrNull()
    }

    companion object {
        /** USER_HZ: the unit of /proc/stat and /proc/<pid>/stat times on every Linux ABI Android uses. */
        const val HZ = 100L
        private val DECAY = doubleArrayOf(60.0, 300.0, 900.0)

        /** /proc/<pid>/stat → a [Process]; the command name may hold spaces and parentheses. */
        fun parse(pid: String, text: String): Process? {
            val fields = text.substringAfterLast(')', "").trim().split(' ')
            if (fields.size < 37) return null
            return Process("$pid:${fields[19]}", fields[0].firstOrNull() ?: '?', fields[11].toLong(), fields[12].toLong(),
                fields[36].toIntOrNull() ?: 0)
        }
    }
}
