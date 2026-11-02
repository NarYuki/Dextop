package moe.n4tsu.dextop.plasma

/** Bounded display history; terminal colour/control sequences must not reach the UI. */
internal class LaunchLogTail {
    private val lines = ArrayDeque<String>()
    private val escape = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

    fun append(value: String): String {
        escape.replace(value, "").split('\n', '\r').forEach { raw ->
            val line = raw.filter { it >= ' ' || it == '\t' }.take(512).trim()
            if (line.isNotEmpty() && line != lines.lastOrNull()) {
                lines.addLast(line)
                while (lines.size > 5) lines.removeFirst()
            }
        }
        return lines.joinToString("\n")
    }

    fun clear() = lines.clear()
}
