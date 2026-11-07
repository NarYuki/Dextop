package moe.n4tsu.dextop.plasma

/** Steam's blank top caption, excluding its menu, account and window buttons. */
internal object LinuxClientCaption {
    fun steam(wmClass: String, title: String, width: Int, x: Float, y: Float): Boolean =
        wmClass.equals("steam", ignoreCase = true) && title == "Steam" && width >= 700 &&
            y >= 0f && y < 28f && x >= width * .30f && x < width * .58f
}
