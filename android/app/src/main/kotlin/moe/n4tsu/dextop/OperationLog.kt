package moe.n4tsu.dextop

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Privacy-filtered diagnostics for only the current and most recent session.
 *
 * Callers are often on the main thread, so filtering and file writes happen on one background
 * thread: lines are buffered and appended together shortly after, and every read or session
 * change first writes out what is pending.
 */
internal object OperationLog {
    private const val MAX_BYTES = 160_000L
    private const val CURRENT_FILE = "dextop-session-current.log"
    private const val LAST_FILE = "dextop-session-last.log"
    private const val LEGACY_FILE = "dextop-operation.log"
    private const val FLUSH_DELAY_MS = 400L

    private val writer = Executors.newSingleThreadScheduledExecutor { Thread(it, "dextop-operation-log").apply { isDaemon = true } }
    // Touched only on the writer thread.
    private val pending = StringBuilder()
    private var pendingFile: File? = null
    private var flushScheduled = false
    private val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    private val SANITIZERS = listOf(
        Regex("(?i)(package|descriptor|serial|email|account|path|uri|url)=[^ ,}]+") to "$1=<redacted>",
        Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b") to "<email>",
        Regex("(?i)\\b(?:https?|content|file)://[^\\s,}]+") to "<uri>",
        Regex("(?i)(?<![A-Za-z0-9_])/(?:data|storage|sdcard|mnt)(?:/[^\\s,}]*)?") to "<path>",
        Regex("(?<![A-Za-z0-9_])(?:[A-Za-z][A-Za-z0-9_]*\\.){2,}[A-Za-z][A-Za-z0-9_]*(?:/[A-Za-z0-9_.$]+)?") to "<component>",
        Regex("(?i)(x|y|rel|pos)=-?[0-9.]+(?:,-?[0-9.]+)?") to "$1=<redacted>",
    )

    fun beginSession(context: Context, summary: String) {
        val dir = context.filesDir
        writer.execute {
            flush()
            val current = File(dir, CURRENT_FILE)
            val last = File(dir, LAST_FILE)
            if (current.exists() && current.length() > 0) current.copyTo(last, overwrite = true)
            current.writeText("DEXTOP SESSION LOG v1\n")
            // Keep the geometry revision ahead of timestamped events for old report parsers.
            current.appendText("geometryInvariant=${GeometryInvariant.discriminator(1)}\n")
        }
        write(context, "I", "Session", "started $summary")
    }

    fun finishSession(context: Context, restored: Boolean) {
        write(context, if (restored) "I" else "W", "Session", "finished restored=$restored")
        val dir = context.filesDir
        writer.execute {
            flush()
            val current = File(dir, CURRENT_FILE)
            if (current.exists()) current.copyTo(File(dir, LAST_FILE), overwrite = true)
            current.delete()
        }
    }

    fun i(context: Context, component: String, message: String) = write(context, "I", component, message)
    fun w(context: Context, component: String, message: String, error: Throwable? = null) =
        write(context, "W", component, "$message${error?.let { " error=${it.javaClass.simpleName}" } ?: ""}")
    fun e(context: Context, component: String, message: String, error: Throwable? = null) =
        write(context, "E", component, "$message${error?.let { " error=${it.javaClass.simpleName}" } ?: ""}")

    private fun write(context: Context, level: String, component: String, rawMessage: String) {
        val dir = context.filesDir
        val time = System.currentTimeMillis()
        writer.execute {
            val message = sanitizeForReport(rawMessage)
            when (level) {
                "E" -> Log.e(component, message)
                "W" -> Log.w(component, message)
                else -> Log.i(component, message)
            }
            val current = File(dir, CURRENT_FILE)
            if (pendingFile != current) {
                flush()
                pendingFile = current
            }
            pending.append(timestamp.format(Date(time))).append(' ').append(level).append('/')
                .append(component).append(' ').append(message).append('\n')
            if (!flushScheduled) {
                flushScheduled = true
                writer.schedule({ flush() }, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Appends the buffered lines to the current session file, if a session is logging. Writer thread only. */
    private fun flush() {
        flushScheduled = false
        val file = pendingFile ?: return
        if (pending.isEmpty()) return
        runCatching {
            if (!file.exists()) return@runCatching
            val room = MAX_BYTES - file.length()
            // Whole lines only, as before: the file stops growing at the limit.
            var end = 0
            while (end < pending.length) {
                val next = pending.indexOf("\n", end).let { if (it < 0) pending.length else it + 1 }
                if (next > room) break
                end = next
            }
            if (end > 0) file.appendText(pending.substring(0, end))
        }
        pending.setLength(0)
    }

    internal fun sanitizeForReport(value: String): String =
        SANITIZERS.fold(value) { text, (pattern, replacement) -> pattern.replace(text, replacement) }.take(2_000)

    fun read(context: Context): String = readLastSession(context)

    fun readLastSession(context: Context): String {
        val dir = context.filesDir
        return writer.submit<String> {
            flush()
            val current = File(dir, CURRENT_FILE)
            val last = File(dir, LAST_FILE)
            when {
                current.exists() && current.length() > 0 -> current.readText()
                last.exists() -> last.readText()
                else -> ""
            }
        }.get()
    }

    fun clear(context: Context) {
        val dir = context.filesDir
        writer.execute {
            pending.setLength(0)
            listOf(CURRENT_FILE, LAST_FILE, LEGACY_FILE).forEach { File(dir, it).delete() }
        }
    }
}
