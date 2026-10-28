package moe.n4tsu.dextop.linux

/** A title alone cannot transfer a window whose class identifies another application. */
internal object LinuxWindowIdentity {
    private val generic = setOf("org", "com", "net", "desktop", "app", "gtk", "gtk3", "gtk4", "qt5", "qt6",
        "kde", "gnome", "debian", "x11", "linux", "the")
    private fun normalized(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun names(value: String) = value.lowercase().split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in generic }.toSet()

    fun score(wmClass: String, title: String, desktopId: String?, label: String?, startupClass: String = ""): Double {
        val id = desktopId?.removeSuffix(".desktop") ?: return 0.0
        val app = listOfNotNull(id, id.substringAfterLast('.'), label).filter { it.isNotBlank() }
        fun level(value: String): Int {
            if (value.isBlank()) return 0
            val words = names(value)
            return app.maxOf { name ->
                val appWords = names(name)
                when {
                    normalized(value) == normalized(name) -> 3
                    appWords.isNotEmpty() && words.isNotEmpty() &&
                        (words.containsAll(appWords) || appWords.containsAll(words)) -> 2
                    words.any { it in appWords } -> 1
                    else -> 0
                }
            }
        }
        val byClass = if (startupClass.isNotBlank() && startupClass.equals(wmClass, true)) 4 else level(wmClass)
        val byTitle = level(title).let { if (byClass < 2) minOf(it, 1) else it }
        return maxOf(byClass, byTitle) + minOf(byClass, byTitle) / 10.0
    }
}
