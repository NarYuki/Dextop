package moe.n4tsu.dextop.linux

/**
 * Turns an input method's edits into what a terminal understands: appended text and Backspace.
 * Keyboards correct a word by asking to replace text they typed earlier, so what they typed on
 * this line is remembered. Text being composed is only previewed until it is committed.
 */
internal class LinuxTerminalIme(private val send: (String) -> Unit, private val preview: (String) -> Unit) {
    private val typed = StringBuilder()
    private var composing = ""
    private var regionStart = -1
    private var regionEnd = -1

    fun commit(text: CharSequence) {
        clearPreview()
        replaceRegion()
        append(text.toString())
    }

    fun compose(text: CharSequence) {
        composing = text.toString()
        preview(composing)
    }

    fun region(start: Int, end: Int) {
        regionStart = minOf(start, end)
        regionEnd = maxOf(start, end)
    }

    /** The IME is done with the composing text, which stays as typed. */
    fun finish() {
        val text = composing
        clearPreview()
        if (text.isEmpty()) { clearRegion(); return }
        replaceRegion()
        append(text)
    }

    fun delete(before: Int) {
        if (before <= 0) return
        val known = minOf(before, typed.length)
        val removed = typed.substring(typed.length - known)
        typed.setLength(typed.length - known)
        val presses = removed.codePointCount(0, removed.length) + before - known
        send(BACKSPACE.repeat(presses.coerceAtMost(MAX_BACKSPACES)))
    }

    /** A key that went to the terminal without passing through here. */
    fun key(character: Int, backspace: Boolean) {
        clearRegion()
        when {
            backspace -> if (typed.isNotEmpty()) typed.setLength(typed.offsetByCodePoints(typed.length, -1))
            character >= 0x20 && character != 0x7f -> typed.appendCodePoint(character)
            else -> typed.setLength(0)
        }
    }

    /** The line can have changed in a way the keyboard did not cause. */
    fun reset() {
        typed.setLength(0)
        clearRegion()
    }

    private fun append(text: String) {
        if (text.isEmpty()) return
        val line = text.replace("\n", "\r")
        send(line)
        if (line.any { it < ' ' || it == '\u007f' }) typed.setLength(0) else typed.append(line)
    }

    private fun replaceRegion() {
        val start = regionStart
        val end = regionEnd
        clearRegion()
        val width = end - start
        if (start < 0 || width <= 0 || width > typed.length) return
        // A keyboard counting from an older line still means the end of what it just typed.
        if (end in 0 until typed.length) return
        val removed = typed.substring(typed.length - width)
        typed.setLength(typed.length - width)
        send(BACKSPACE.repeat(removed.codePointCount(0, removed.length).coerceAtMost(MAX_BACKSPACES)))
    }

    private fun clearPreview() {
        composing = ""
        preview("")
    }

    private fun clearRegion() {
        regionStart = -1
        regionEnd = -1
    }

    private companion object {
        const val BACKSPACE = "\u007f"
        const val MAX_BACKSPACES = 256
    }
}
