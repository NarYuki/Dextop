package moe.n4tsu.dextop.linux

/** Package managers report progress within a phase, not a time-weighted job percentage. */
internal data class LinuxStoreProgress(val percent: Double, val phase: String, val line: String) {
    companion object {
        private val status = Regex("^(dlstatus|pmstatus):[^:]*:([0-9]+(?:\\.[0-9]+)?):(.*)$")
        fun apt(line: String): LinuxStoreProgress? {
            val match = status.matchEntire(line) ?: return null
            val percent = match.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
            return LinuxStoreProgress(percent.coerceIn(0.0, 100.0),
                if (match.groupValues[1] == "dlstatus") "download" else "install", match.groupValues[3])
        }
    }
}
