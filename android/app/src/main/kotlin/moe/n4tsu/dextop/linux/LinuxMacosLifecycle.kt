package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One private control directory per PRoot; no signals to unrelated guest processes. */
internal class LinuxMacosLifecycle {
    class Control(val directory: File) {
        val stop = File(directory, "stop")
        val binds get() = listOf("${directory.absolutePath}:/run/dextop-macos-control")
        val environment get() = listOf("DEXTOP_DARLING_STOP_FILE=/run/dextop-macos-control/stop")
        fun cleanup() { stop.delete(); directory.delete() }
    }

    private val controls = ConcurrentHashMap<Process, Control>()

    fun prepare(base: File): Control {
        check(base.isDirectory || base.mkdirs()) { "Cannot create macOS session controls" }
        return Control(Files.createTempDirectory(base.toPath(), "session-").toFile())
    }

    fun attach(process: Process, control: Control) { controls[process] = control }
    fun finished(process: Process) { controls.remove(process)?.cleanup() }

    fun stop(processes: List<Process>, timeoutMillis: Long = 60_000) {
        val owned = processes.mapNotNull { process -> controls[process]?.let { process to it } }
        owned.forEach { (process, control) ->
            if (process.isAlive) runCatching { control.stop.writeText("stop\n") }.getOrElse {
                if (process.isAlive) throw it
            }
        }
        // All supervisors receive the request before waiting; the deadline is
        // shared so multiple apps do not multiply the session shutdown delay.
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        owned.forEach { (process, _) ->
            val remaining = (deadline - System.nanoTime()).coerceAtLeast(0)
            check(!process.isAlive || process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                "macOS is still stopping; the Linux session has been kept running. Try again after startup completes."
            }
            finished(process)
        }
    }
}
