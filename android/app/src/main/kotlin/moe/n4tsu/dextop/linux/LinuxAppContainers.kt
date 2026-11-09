package moe.n4tsu.dextop.linux

/** App launchers show exactly one installed container, including when no preference is saved. */
internal object LinuxAppContainers {
    fun select(installed: List<String>, preferred: String): String? =
        installed.firstOrNull { it == preferred } ?: installed.firstOrNull()
}
