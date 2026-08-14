package moe.n4tsu.dextop

/** Extracts a notification-launched task without depending on OEM-specific task object layouts. */
internal object NotificationTaskLocator {
    private val displayHeader = Regex("(?:Display #|Display: mDisplayId=)(\\d+)")
    private val activity = Regex("ActivityRecord\\{[^}]+ u\\d+ ([A-Za-z0-9._]+)/[^ ]+ t(\\d+)")

    fun findSystemUiLaunchedTask(dump: String, packageName: String, desktopDisplayId: Int): Int? {
        var displayId = -1
        var candidateTask: Int? = null
        var candidatePackage: String? = null
        var candidateDisplay = -1

        dump.lineSequence().forEach { line ->
            displayHeader.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                displayId = it
                candidateTask = null
                candidatePackage = null
            }
            activity.find(line)?.let { match ->
                candidatePackage = match.groupValues[1]
                candidateTask = match.groupValues[2].toIntOrNull()
                candidateDisplay = displayId
            }
            if (
                candidatePackage == packageName &&
                candidateDisplay >= 0 &&
                candidateDisplay != desktopDisplayId &&
                line.contains("launchedFromPackage=com.android.systemui")
            ) {
                return candidateTask
            }
        }
        return null
    }
}
