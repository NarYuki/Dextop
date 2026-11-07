@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package moe.n4tsu.dextop.plasma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Dextop Book: the compositor laid out like a Pixel laptop and built from Material 3 Expressive
 * (Jetpack Compose material3): the time and status along the top with a live activity, a
 * full-width taskbar, the launcher above it, shortcuts and widgets on the desktop, and windows
 * as rounded tonal surfaces. Colours are the device's dynamic Material You scheme.
 */
internal class BookShell(
    service: Context,
    displayId: Int,
    displayWidth: Int,
    displayHeight: Int,
    densityDpi: Int,
    host: PlasmaShell.Host,
    fullscreenAppDisplays: Boolean = false,
    passiveShellFocus: Boolean = false,
    maxRateAppDisplays: Boolean = false,
    applicationOverlay: Boolean = false,
    privateAppDisplays: Boolean = false,
) : CompositedWindowShell(
    service, displayId, displayWidth, displayHeight, densityDpi, host,
    fullscreenAppDisplays, passiveShellFocus, maxRateAppDisplays,
    applicationOverlay, privateAppDisplays, autoMode = false, book = true,
)

internal object BookTheme {
    fun dark(context: Context, settings: PlasmaSettings): Boolean = when (settings.colorScheme) {
        "light" -> false
        "dark" -> true
        else -> context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** The device's dynamic scheme (Material You), or the baseline one before Android 12. */
    fun scheme(context: Context, dark: Boolean): ColorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme() else lightColorScheme()

    /** The same scheme in the roles the compositor's own views (windows, menus, applets) draw with. */
    fun palette(context: Context, settings: PlasmaSettings): PlasmaPalette {
        val dark = dark(context, settings)
        val c = scheme(context, dark)
        return PlasmaPalette(
            dark = dark,
            windowBackground = c.surfaceContainer.toArgbInt(),
            viewBackground = c.surface.toArgbInt(),
            headerActive = c.surfaceContainerHigh.toArgbInt(),
            headerInactive = c.surfaceContainer.toArgbInt(),
            text = c.onSurface.toArgbInt(),
            inactiveText = c.onSurfaceVariant.toArgbInt(),
            disabledText = c.outline.toArgbInt(),
            accent = c.primary.toArgbInt(),
            accentText = c.onPrimary.toArgbInt(),
            negative = c.error.toArgbInt(),
            positive = if (dark) 0xFF6DD58C.toInt() else 0xFF146C2E.toInt(),
            neutral = c.tertiary.toArgbInt(),
            panel = c.surfaceContainerHigh.toArgbInt(),
            popup = c.surfaceContainer.toArgbInt(),
            separator = c.outlineVariant.toArgbInt(),
            hover = PlasmaTheme.withAlpha(c.onSurface.toArgbInt(), 0x14),
            pressed = PlasmaTheme.withAlpha(c.primary.toArgbInt(), 0x29),
            frameOutline = PlasmaTheme.withAlpha(c.outlineVariant.toArgbInt(), 0x80),
            shadow = 0x8C000000.toInt(),
        )
    }

    private fun Color.toArgbInt() = toArgb()

    /** Deep navy with a prism of light sweeping across, after the Pixel laptop wallpaper. */
    fun wallpaper(width: Int, height: Int, dark: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w, h,
            if (dark) intArrayOf(0xFF0A1530.toInt(), 0xFF0B1E4A.toInt(), 0xFF02040C.toInt())
            else intArrayOf(0xFFD7E3FF.toInt(), 0xFFAFC6FF.toInt(), 0xFFE8EDFA.toInt()),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        // The band of light: a broad arc from the lower left to the upper right.
        val band = Path().apply {
            moveTo(-w * .05f, h * 1.05f)
            cubicTo(w * .25f, h * .78f, w * .48f, h * .45f, w * .66f, -h * .05f)
            lineTo(w * .74f, -h * .05f)
            cubicTo(w * .58f, h * .5f, w * .36f, h * .86f, w * .08f, h * 1.05f)
            close()
        }
        paint.shader = LinearGradient(w * .2f, h * .9f, w * .72f, 0f,
            intArrayOf(0x002B6BFF, 0xCC2B6BFF.toInt(), 0xE600C2D1.toInt(), 0xE6FFE15A.toInt(), 0xE6FF7A3D.toInt(), 0xCCC04BFF.toInt(), 0x00C04BFF),
            floatArrayOf(0f, .18f, .38f, .55f, .7f, .86f, 1f), Shader.TileMode.CLAMP)
        paint.maskFilter = BlurMaskFilter((w * .018f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(band, paint)
        paint.maskFilter = null
        // A bright core along its inner edge.
        val core = Path().apply {
            moveTo(w * .02f, h * 1.05f)
            cubicTo(w * .3f, h * .8f, w * .5f, h * .48f, w * .7f, -h * .05f)
        }
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * .004f
        paint.color = 0x99FFFFFF.toInt()
        paint.maskFilter = BlurMaskFilter((w * .004f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(core, paint)
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(w * .7f, h * .1f, w * .45f, intArrayOf(0x33FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        return bitmap
    }
}

/** Compose needs a lifecycle and saved state; the compositor's overlay has no activity to lend them. */
internal class BookOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

