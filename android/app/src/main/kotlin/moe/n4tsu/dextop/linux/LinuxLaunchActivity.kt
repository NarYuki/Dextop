package moe.n4tsu.dextop.linux

import android.app.Activity
import android.os.Bundle

/**
 * Entry point for home-screen shortcuts of Linux applications (the generated launcher APKs
 * and the Linux tab): opens the application in the Plasma desktop when one runs, otherwise
 * as Android windows.
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
            LinuxAndroidWindows.launch(this, container, desktop, intent.getStringExtra(EXTRA_LABEL))
        }
        finish()
    }
}
