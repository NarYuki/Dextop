package moe.n4tsu.dextop.linux

/** Per-process toolkit scale. Never changes Android display density or another app's X server. */
internal object LinuxUiScale {
    fun validate(percent: Int): Int {
        require(percent in 50..300) { moe.n4tsu.dextop.localText("uiDisplayScaleMustBeBetween50And300") }
        return percent
    }

    fun resolve(global: Int, app: Int?): Int = validate(app ?: global)

    fun script(percent: Int): String {
        validate(percent)
        if (percent == 100) return "unset GDK_SCALE GDK_DPI_SCALE QT_SCALE_FACTOR\nexport DEXTOP_UI_SCALE=1\n"
        val factor = java.math.BigDecimal(percent).divide(java.math.BigDecimal(100)).stripTrailingZeros().toPlainString()
        val whole = maxOf(1, percent / 100)
        val font = java.math.BigDecimal(percent).divide(java.math.BigDecimal(100 * whole), 6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        // GTK's window scale is integral; its font scale supplies the remainder on GTK3.
        return "export DEXTOP_UI_SCALE=$factor GDK_SCALE=$whole GDK_DPI_SCALE=$font QT_SCALE_FACTOR=$factor\n"
    }
}
