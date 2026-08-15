package moe.n4tsu.dextop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

internal class AppCatalog(private val context: Context) {
    fun launchableApps(includeIcons: Boolean = true): List<Map<String, Any>> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                mutableMapOf<String, Any>(
                    "label" to it.loadLabel(context.packageManager).toString(),
                    "package" to it.activityInfo.packageName
                ).apply {
                    if (includeIcons) put("icon", iconBytes(it.loadIcon(context.packageManager)))
                }
            }
            .sortedBy { (it["label"] as String).lowercase() }
            .toList()
    }

    fun launchableAppIcons(): Map<String, ByteArray> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .associate { it.activityInfo.packageName to iconBytes(it.loadIcon(context.packageManager)) }
    }

    private fun iconBytes(drawable: Drawable): ByteArray {
        val size = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
        } else {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
                val canvas = Canvas(output)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.toByteArray()
        }
    }

    fun launch(packageName: String, displayId: Int, bounds: Rect? = null) {
        require(displayId >= 0) { "Dextop is not running" }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error(NativeStrings.text("nativeSelectedAppCannotLaunch"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        // Pixel's desk controller can temporarily mark a freshly resized
        // projected display as ineligible. Explicit freeform launch keeps app
        // launching and server-side window decorations available regardless of
        // that asynchronous Shell state.
        runCatching {
            ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            ).invoke(options, 5)
        }
        if (bounds != null) options.launchBounds = bounds
        context.startActivity(intent, options.toBundle())
    }
}
