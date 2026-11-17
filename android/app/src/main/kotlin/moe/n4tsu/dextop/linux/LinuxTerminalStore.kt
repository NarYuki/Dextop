package moe.n4tsu.dextop.linux

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The newest output of a terminal tab, kept in a fixed amount of memory. */
internal class LinuxTerminalScrollback(private val limit: Int = DEFAULT_LIMIT, initial: ByteArray = ByteArray(0)) {
    private val data = ByteArray(limit)
    private var start = 0
    private var size = 0
    private var truncated = false

    init { append(initial, initial.size) }

    @Synchronized fun append(bytes: ByteArray, count: Int) {
        var offset = 0
        var length = count
        if (length > limit) {
            offset = length - limit
            length = limit
            truncated = true
        }
        val overflow = size + length - limit
        if (overflow > 0) {
            start = (start + overflow) % limit
            size -= overflow
            truncated = true
        }
        val end = (start + size) % limit
        val first = minOf(length, limit - end)
        System.arraycopy(bytes, offset, data, end, first)
        System.arraycopy(bytes, offset + first, data, 0, length - first)
        size += length
    }

    /** The kept output. Once older output is gone it starts at a line, never inside an escape sequence. */
    @Synchronized fun snapshot(): ByteArray {
        val bytes = ByteArray(size) { data[(start + it) % limit] }
        if (!truncated) return bytes
        val line = bytes.indexOf('\n'.code.toByte())
        return if (line < 0) bytes else bytes.copyOfRange(line + 1, bytes.size)
    }

    companion object {
        const val DEFAULT_LIMIT = 256 * 1024
    }
}

/** Recognizes the shell's own exit, which unlike a killed process closes its tab. */
internal object LinuxTerminalExit {
    private val MARKER = "\u001b]777;dextop-exit\u001b\\".toByteArray()
    private val PROMPT = "\u001b]7;".toByteArray()

    private fun last(pattern: ByteArray, bytes: ByteArray, count: Int): Int =
        (count - pattern.size downTo 0).firstOrNull { start -> pattern.indices.all { bytes[start + it] == pattern[it] } } ?: -1

    /**
     * Whether the shell has exited after [count] more bytes of output, given [previous]. The EXIT
     * trap's marker means yes; a nested shell prints it too, but then the outer prompt follows.
     */
    fun update(previous: Boolean, bytes: ByteArray, count: Int): Boolean {
        val marker = last(MARKER, bytes, count)
        val prompt = last(PROMPT, bytes, count)
        return when {
            marker > prompt -> true
            prompt >= 0 -> false
            else -> previous
        }
    }
}

/** [rows] and [cols] are the size the saved output was last drawn at; 0 when unknown. */
internal data class LinuxTerminalTab(val id: String, val title: String, val directory: String?, val rows: Int = 0, val cols: Int = 0)

internal data class LinuxTerminalState(val tabs: List<LinuxTerminalTab>, val active: String?) {
    fun encode(): String = JSONObject().apply {
        active?.let { put("active", it) }
        put("tabs", JSONArray().apply {
            tabs.forEach { tab ->
                put(JSONObject().put("id", tab.id).put("title", tab.title).put("rows", tab.rows).put("cols", tab.cols)
                    .apply { tab.directory?.let { put("directory", it) } })
            }
        })
    }.toString()

    companion object {
        private val ID = Regex("t[0-9a-f]{12}")

        fun validId(id: String) = ID.matches(id)

        /** A directory the shell may be started in; the path comes from the terminal's own output. */
        fun directory(path: String?): String? =
            path?.takeIf { it.startsWith("/") && it.length <= 4096 && it.none { c -> c == '\n' || c == '\r' || c == '\u0000' } }

        private fun size(value: Int) = if (value in 2..1000) value else 0

        fun decode(text: String): LinuxTerminalState {
            val root = JSONObject(text)
            val list = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = (0 until list.length()).mapNotNull { index ->
                val tab = list.optJSONObject(index) ?: return@mapNotNull null
                val id = tab.optString("id")
                if (!validId(id)) return@mapNotNull null
                LinuxTerminalTab(id, tab.optString("title"), directory(tab.optString("directory", "")), size(tab.optInt("rows")), size(tab.optInt("cols")))
            }
            return LinuxTerminalState(tabs, root.optString("active").takeIf { active -> tabs.any { it.id == active } })
        }
    }
}

/** The saved tabs of one container: their list and each tab's output. */
internal class LinuxTerminalStore(private val directory: File) {
    private val state get() = File(directory, "state.json")
    private fun output(id: String) = File(directory, "$id.log")

    private fun replace(target: File, contents: ByteArray) {
        directory.mkdirs()
        val staged = File(directory, target.name + ".tmp")
        staged.writeBytes(contents)
        check(staged.renameTo(target))
    }

    fun load(): LinuxTerminalState = runCatching { LinuxTerminalState.decode(state.readText()) }
        .getOrDefault(LinuxTerminalState(emptyList(), null))

    fun loadOutput(id: String): ByteArray = runCatching { output(id).readBytes() }.getOrDefault(ByteArray(0))

    fun saveState(value: LinuxTerminalState) = replace(state, value.encode().toByteArray())

    fun saveOutput(id: String, bytes: ByteArray) = replace(output(id), bytes)

    fun remove(id: String) { output(id).delete() }

    fun clear() { directory.deleteRecursively() }
}
