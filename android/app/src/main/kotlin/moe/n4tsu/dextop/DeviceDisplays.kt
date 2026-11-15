package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Display
import android.view.WindowManager

/** What [DeviceDisplays] needs from the session beyond the shared interfaces. */
internal interface DeviceDisplaysHost : DesktopSession, OverlayMenu {
    val posture: LaptopPosture
}

/** Facts about the phone's own displays: whether it folds and which display is the main one. */
internal class DeviceDisplays(
    internal val context: Context,
    internal val desk: DeviceDisplaysHost,
) {
    internal fun defaultDisplayUniqueId(): String? = runCatching {
        Display::class.java.getMethod("getUniqueId")
            .invoke(context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)) as String
    }.getOrNull()

    internal fun isFoldableMainDisplay(): Boolean {
        val manager = context.getSystemService(DisplayManager::class.java)
        val internalDisplays = internalDisplays(manager)
        if (internalDisplays.size < 2) return false
        fun area(display: Display): Long {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            return metrics.widthPixels.toLong() * metrics.heightPixels
        }

        val current = manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        return area(current) >= internalDisplays.maxOf(::area)
    }

    /**
     * WindowManager's folding API is authoritative when the OEM extension is
     * present. Older devices may not expose it, so the legacy display/sensor
     * probe remains a fallback rather than being treated as a second signal.
     */

    internal fun isFoldableDevice(): Boolean {
        desk.posture.refreshFoldingApiState("capability", force = true)
        // Fold8 can expose only its active internal panel while the session
        // starts, and a few One UI builds do not publish TYPE_HINGE_ANGLE to
        // third-party services.  Its known model/profile is therefore a
        // stable hardware capability signal, not an automatic-posture signal.
        // Without it, even the explicit overlay action is rejected as a
        // phone-sized non-foldable before the laptop deck is created.
        if (desk.posture.laptopFoldProfile() == LaptopFoldProfile.FOLD8) return true
        // A fold transition can make WindowManager briefly publish an empty
        // feature list while the inactive panel is being attached.  That is
        // not proof that the device stopped being foldable. Keep the positive
        // API result, but fall back to stable hardware signals before ever
        // returning false so a transient result cannot disable auto detection.
        if (desk.posture.foldingApiFoldable == true) return true
        val manager = context.getSystemService(DisplayManager::class.java)
        val hardwareFoldable = internalDisplays(manager).size >= 2 ||
                context.getSystemService(SensorManager::class.java)
                    .getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
        return hardwareFoldable || desk.posture.foldingApiFoldable == true
    }

    /**
     * Laptop mode needs enough surface for two usable panes. Foldables are
     * explicitly supported even when one panel is narrower than a tablet;
     * otherwise require a tablet-class display (600dp smallest width).
     */

    internal fun internalDisplays(manager: DisplayManager): List<Display> =
        manager.displays.filter { display ->
            runCatching { Display::class.java.getMethod("getType").invoke(display) as Int == 1 }
                .getOrDefault(display.displayId == Display.DEFAULT_DISPLAY)
        }
}
