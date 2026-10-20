package moe.n4tsu.dextop.plasma

/** A Dextop-owned desktop shell running on the Dextop display (Plasma v1 or v2). */
internal interface DesktopShell {
    val displayId: Int
    fun start()
    fun stop(restoreWindows: Boolean = true)
    fun handleKey(event: android.view.KeyEvent): Boolean
    fun onWindowsChanged()
    fun onDisplayResized(newWidth: Int, newHeight: Int)
    fun reloadSettings()
}
