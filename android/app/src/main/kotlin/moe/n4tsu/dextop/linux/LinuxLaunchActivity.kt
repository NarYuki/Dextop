package moe.n4tsu.dextop.linux

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle

/**
 * Entry point for home-screen shortcuts of Linux applications (the generated launcher APKs
 * and the Linux tab): opens the application in the Plasma desktop when one runs, otherwise
 * as Android windows named and drawn like the application.
 */
class LinuxLaunchActivity : Activity() {
    companion object {
        const val EXTRA_CONTAINER = "container"
        const val EXTRA_DESKTOP = "desktop"
        const val EXTRA_LABEL = "label"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = intent.getStringExtra(EXTRA_CONTAINER)
        val desktop = intent.getStringExtra(EXTRA_DESKTOP)
        if (!container.isNullOrBlank() && !desktop.isNullOrBlank()) {
            LinuxAndroidWindows.launch(this, container, desktop, intent.getStringExtra(EXTRA_LABEL), launcherIcon(container, desktop))
        }
        finish()
    }

    /** The icon of the application's home-screen launcher, which Dextop made from its desktop entry. */
    private fun launcherIcon(container: String, desktop: String): Bitmap? = runCatching {
        val drawable = packageManager.getApplicationIcon(LinuxShortcutInstaller.packageName(container, desktop))
        val size = 192
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
        }
    }.getOrNull()
}
