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
        if (container.isNullOrBlank() || desktop.isNullOrBlank()) {
            unusable(moe.n4tsu.dextop.localText("このショートカットは無効です", "This shortcut is not valid"))
        } else when (availability(container, desktop)) {
            Availability.NO_CONTAINER -> unusable(moe.n4tsu.dextop.localText(
                "このショートカットのコンテナは存在しません", "This shortcut's container no longer exists"))
            Availability.NO_APP -> unusable(moe.n4tsu.dextop.localText(
                "このアプリはコンテナにインストールされていません", "This app is no longer installed in its container"))
            else -> if (LinuxShortcutInstaller.validLaunch(this, container, desktop, intent.getStringExtra(EXTRA_TOKEN))) {
                LinuxAndroidWindows.launch(this, container, desktop, intent.getStringExtra(EXTRA_LABEL), launcherIcon(container, desktop))
            } else if (LinuxShortcutInstaller.outdatedLauncher(this, container, desktop)) {
                // A launcher made before the launch proof: rebuilt with it, then tapped again.
                LinuxShortcutInstaller.upgradeIfOld(this, container, desktop)
                android.widget.Toast.makeText(this, moe.n4tsu.dextop.localText("古いランチャーを作り直しています。完了後にもう一度開いてください", "Rebuilding this old launcher. Open it again once it is done"), android.widget.Toast.LENGTH_LONG).show()
            } else {
                unusable(moe.n4tsu.dextop.localText(
                    "このショートカットは無効です。Dextop からホーム画面に追加し直してください",
                    "This shortcut is not valid. Add the app to the home screen again from Dextop"))
            }
        }
        finish()
    }

    private enum class Availability { OK, UNKNOWN, NO_CONTAINER, NO_APP }

    /** Whether the container and its application still exist; UNKNOWN while Linux is not connected. */
    private fun availability(container: String, desktop: String): Availability {
        val service = LinuxContainerClient.service() ?: return Availability.UNKNOWN
        return runCatching {
            val result = java.util.concurrent.Executors.newSingleThreadExecutor().let { pool ->
                try {
                    pool.submit<Availability> {
                        val list = org.json.JSONArray(service.listContainers())
                        if ((0 until list.length()).none { list.getJSONObject(it).optString("id") == container })
                            return@submit Availability.NO_CONTAINER
                        val apps = org.json.JSONArray(service.listApps(container, java.util.Locale.getDefault().toLanguageTag()))
                        if ((0 until apps.length()).any { apps.getJSONObject(it).optString("id") == desktop })
                            Availability.OK else Availability.NO_APP
                    }.get(5, java.util.concurrent.TimeUnit.SECONDS)
                } finally { pool.shutdownNow() }
            }
            result
        }.getOrDefault(Availability.UNKNOWN)
    }

    private fun unusable(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
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
