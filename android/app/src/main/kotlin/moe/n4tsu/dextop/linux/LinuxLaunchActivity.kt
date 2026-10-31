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
        /** LinuxShortcutInstaller.launchToken: only Dextop's own launchers and shortcuts open apps. */
        const val EXTRA_TOKEN = "token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = intent.getStringExtra(EXTRA_CONTAINER)
        val desktop = intent.getStringExtra(EXTRA_DESKTOP)
        if (!container.isNullOrBlank() && !desktop.isNullOrBlank()) {
            if (LinuxShortcutInstaller.validLaunch(this, container, desktop, intent.getStringExtra(EXTRA_TOKEN))) {
                LinuxAndroidWindows.launch(this, container, desktop, intent.getStringExtra(EXTRA_LABEL), launcherIcon(container, desktop))
            } else if (LinuxShortcutInstaller.outdatedLauncher(this, container, desktop)) {
                // A launcher made before the launch proof: rebuilt with it, then tapped again.
                LinuxShortcutInstaller.upgradeIfOld(this, container, desktop)
                android.widget.Toast.makeText(this, moe.n4tsu.dextop.localText("ランチャーを更新しています。更新後にもう一度開いてください", "Updating this launcher. Open it again once it is updated"), android.widget.Toast.LENGTH_LONG).show()
            }
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
