package moe.n4tsu.dextop

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Privacy-filtered diagnostics for only the current and most recent session. */
internal object OperationLog {
    private const val MAX_BYTES = 160_000L
    private const val CURRENT_FILE = "dextop-session-current.log"
    private const val LAST_FILE = "dextop-session-last.log"
    private const val LEGACY_FILE = "dextop-operation.log"
    private val lock = Any()

    fun beginSession(context: Context, summary: String) = synchronized(lock) {
        val current = File(context.filesDir, CURRENT_FILE)
        val last = File(context.filesDir, LAST_FILE)
        if (current.exists() && current.length() > 0) current.copyTo(last, overwrite = true)
        current.writeText("DEXTOP SESSION LOG v1\n")
        write(context, "I", "Session", "started $summary")
    }

    fun finishSession(context: Context, restored: Boolean) = synchronized(lock) {
        write(context, if (restored) "I" else "W", "Session", "finished restored=$restored")
        val current = File(context.filesDir, CURRENT_FILE)
        if (current.exists()) current.copyTo(File(context.filesDir, LAST_FILE), overwrite = true)
        current.delete()
    }

    fun i(context: Context, component: String, message: String) = write(context, "I", component, message)
    fun w(context: Context, component: String, message: String, error: Throwable? = null) =
        write(context, "W", component, "$message${error?.let { " error=${it.javaClass.simpleName}" } ?: ""}")
    fun e(context: Context, component: String, message: String, error: Throwable? = null) =
        write(context, "E", component, "$message${error?.let { " error=${it.javaClass.simpleName}" } ?: ""}")

    private fun write(context: Context, level: String, component: String, rawMessage: String) {
        val current = File(context.filesDir, CURRENT_FILE)
        if (!current.exists()) return
        val message = sanitize(rawMessage)
        val line = "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())} $level/$component $message\n"
        if (level == "E") Log.e(component, message) else if (level == "W") Log.w(component, message)
        synchronized(lock) {
            if (current.length() + line.length > MAX_BYTES) return
            current.appendText(line)
        }
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(package|descriptor|serial|email|account|path|uri|url)=[^ ,}]+"), "$1=<redacted>")
        .replace(Regex("(?i)(x|y|rel|pos)=-?[0-9.]+(?:,-?[0-9.]+)?"), "$1=<redacted>")
        .take(2_000)

    fun read(context: Context): String = synchronized(lock) {
        val current = File(context.filesDir, CURRENT_FILE)
        val last = File(context.filesDir, LAST_FILE)
        when {
            current.exists() && current.length() > 0 -> current.readText()
            last.exists() -> last.readText()
            else -> ""
        }
    }

    fun readLastSession(context: Context): String = synchronized(lock) {
        val current = File(context.filesDir, CURRENT_FILE)
        val last = File(context.filesDir, LAST_FILE)
        when {
            current.exists() && current.length() > 0 -> current.readText()
            last.exists() -> last.readText()
            else -> ""
        }
    }

    fun clear(context: Context) = synchronized(lock) {
        listOf(CURRENT_FILE, LAST_FILE, LEGACY_FILE).forEach { File(context.filesDir, it).delete() }
    }
}
