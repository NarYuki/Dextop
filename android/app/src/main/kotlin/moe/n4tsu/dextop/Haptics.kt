package moe.n4tsu.dextop

import android.media.AudioManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** What [Haptics] needs from the session beyond the shared interfaces. */
internal interface HapticsHost : DesktopSession, OverlayMenu {

}

internal enum class HapticSource { KEYBOARD, POINTER }

/** Key and pointer vibration, honouring the user's strength setting and the ringer mode. */
internal class Haptics(
    private val context: Context,
    private val desk: HapticsHost,
) {
    // Looked up once: every key press and pointer tick asks for haptics.
    private val hapticAudioManager by lazy(LazyThreadSafetyMode.NONE) { context.getSystemService(AudioManager::class.java) }

    private val hapticVibrator by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    }

    fun performLaptopHaptic(
        view: View?,
        strong: Boolean = false,
        source: HapticSource = HapticSource.KEYBOARD,
    ) {
        if (!desk.laptopModeActive) return
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val enabledKey = if (source == HapticSource.POINTER) {
            "flutter.pointer_haptics_enabled"
        } else {
            "flutter.laptop_keyboard_haptics_enabled"
        }
        if (!preferences.getBoolean(enabledKey, true)) return
        val strengthKey = if (source == HapticSource.POINTER) {
            "flutter.pointer_haptics_strength"
        } else {
            "flutter.laptop_keyboard_haptics_strength"
        }
        val configuredStrength = preferences.getLong(strengthKey, 70L).toInt().coerceIn(1, 100)
        // Respect both silent and vibrate-only ringer modes. Direct vibrator
        // calls otherwise bypass the user's mute intent on several One UI
        // builds even though ordinary keyboard feedback is silent.
        if (hapticAudioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        // Only the dedicated laptop surfaces opt into haptics. The upper
        // mirrored phone surface remains a normal touch target and must not
        // vibrate merely because the deck is visible.
        val target = view ?: return
        val vibrator = hapticVibrator
        if (vibrator != null) {
            val amplitude = ((configuredStrength * 255 / 100) * if (strong) 1.2f else 1f)
                .toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(if (strong) 42L else 30L, amplitude)
            vibrator.vibrate(effect)
        } else {
            // Keep a visual/input-device fallback for devices without a
            // vibrator, or for environments where the service cannot access
            // the vibrator manager.
            val constant = if (strong) {
                HapticFeedbackConstants.CONTEXT_CLICK
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            target.performHapticFeedback(constant)
        }
    }
}
