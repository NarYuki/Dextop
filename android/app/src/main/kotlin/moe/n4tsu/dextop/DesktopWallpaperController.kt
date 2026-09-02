package moe.n4tsu.dextop

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper

/** Keeps the wallpaper buffer synchronized with a live Dextop display. */
internal class DesktopWallpaperController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    fun synchronize(
        displayId: Int,
        width: Int,
        height: Int,
        reason: String,
        isCurrent: () -> Boolean,
    ) {
        if (displayId < 0 || width <= 0 || height <= 0) return
        val revision = ++generation
        listOf(0L, 180L, 600L).forEachIndexed { attempt, delay ->
            handler.postDelayed({
                if (revision != generation || !isCurrent()) return@postDelayed
                val display = context.getSystemService(DisplayManager::class.java)
                    .getDisplay(displayId) ?: return@postDelayed
                val wallpaper = WallpaperManager.getInstance(context.createDisplayContext(display))
                runCatching { wallpaper.setDisplayPadding(Rect()) }
                    .onFailure {
                        OperationLog.w(
                            context,
                            "Wallpaper",
                            "display padding reset failed display=$displayId attempt=$attempt",
                            it,
                        )
                    }
                runCatching { wallpaper.suggestDesiredDimensions(width, height) }
                    .onSuccess {
                        OperationLog.i(
                            context,
                            "Wallpaper",
                            "dimensions synchronized display=$displayId size=${width}x$height " +
                                    "attempt=$attempt reason=$reason",
                        )
                    }
                    .onFailure {
                        OperationLog.w(
                            context,
                            "Wallpaper",
                            "dimension synchronization failed display=$displayId " +
                                    "size=${width}x$height attempt=$attempt reason=$reason",
                            it,
                        )
                    }
            }, delay)
        }
    }

    fun cancel() {
        generation += 1
        handler.removeCallbacksAndMessages(null)
    }
}
