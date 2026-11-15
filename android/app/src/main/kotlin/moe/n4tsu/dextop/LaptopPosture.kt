package moe.n4tsu.dextop

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlin.math.abs

/** What [LaptopPosture] needs from the session beyond the shared interfaces. */
internal interface LaptopPostureHost : DesktopSession, OverlayMenu {
    val externalInputDeck: Boolean
    fun isLaptopAutoDetectionEnabled(): Boolean
    fun isLaptopAutoOrientationEligible(): Boolean
    fun laptopHostIsFullHeight(): Boolean
    fun setLaptopMode(enabled: Boolean, requestedStyle: MirrorService.KeyboardDeckStyle = MirrorService.KeyboardDeckStyle.LAPTOP)
}

internal enum class LaptopFoldProfile {
    FOLD8,
    STANDARD_FOLDABLE
}

/** Reads the hinge and the folding state to decide when a foldable is sitting like a laptop. */
internal class LaptopPosture(
    private val context: Context,
    private val desk: LaptopPostureHost,
) {
    internal var hingeAngle: Float? = null
    internal var laptopAutoActivated = false

    /**
     * Blocks automatic reactivation after the user dismisses an automatically
     * shown deck. It is cleared only after a flat posture is observed or the
     * user explicitly enables laptop mode again.
     */
    internal var laptopAutoSuppressedByUser = false
    internal var laptopManualOverride = false
    internal val laptopModeDebounceMs = 420L
    internal var laptopModeEvaluationGeneration = 0L
    internal var pendingLaptopMode: Boolean? = null
    internal var pendingLaptopModeSince = 0L

    internal var filteredHingeAngle: Float? = null

    internal var laptopPostureReevaluationGeneration = 0L

    internal var laptopHostMismatchSince = 0L

    internal var foldingApiFoldable: Boolean? = null

    internal var foldingApiLaptopPosture: Boolean? = null

    internal var foldingApiHorizontalHinge: Boolean? = null

    private var foldingApiLastProbeAt = 0L

    private var foldingApiLastSuccessAt = 0L

    private val foldingApiFailureGraceMs = 1_500L

    internal val laptopHostMismatchDebounceMs = 1_200L

    private fun isLaptopHingeAngle(angle: Float): Boolean = if (desk.laptopModeActive) {
        angle in 45f..155f
    } else {
        angle in 55f..145f
    }

    /**
     * Resolve posture through the device-specific path. Fold8 keeps the
     * geometry fallback that protects it from stale WindowManager updates;
     * normal-size Fold devices use the FoldingFeature half-open state directly
     * because their hinge orientation flag is not reliable for this layout.
     */

    internal fun currentLaptopPosture(): Boolean? {
        val angle = filteredHingeAngle ?: hingeAngle
        return when (laptopFoldProfile()) {
            LaptopFoldProfile.FOLD8 -> currentFold8LaptopPosture(angle)
            LaptopFoldProfile.STANDARD_FOLDABLE -> currentStandardFoldPosture(angle)
        }
    }

    private fun currentFold8LaptopPosture(angle: Float?): Boolean? {
        // WindowManager is the stable posture source on Samsung foldables.
        // The public hinge sensor can deliver one stale 180° sample while the
        // inner panel is being attached; letting that sample override a
        // confirmed HALF_OPENED feature breaks first-start detection. Use the
        // sensor only when the folding API is unavailable.
        if (foldingApiLaptopPosture == true) {
            // Once the laptop deck is active, a stale HALF_OPENED result must
            // not survive a real return to the full-height host. Samsung can
            // omit the API transition, but the host geometry still exposes
            // that the lower pane has been restored.
            // While the user-dismissal latch is active, the Samsung hinge
            // sensor is not allowed to clear it: this device can keep a stale
            // 180-degree sample while FoldingFeature still says HALF_OPENED.
            // Only a confirmed API transition to flat (the branch below) may
            // release the latch. The geometry fallback remains for an already
            // visible deck whose API transition is delayed.
            if (desk.laptopModeActive && angle != null &&
                !isLaptopHingeAngle(angle) && desk.laptopHostIsFullHeight()
            ) {
                return false
            }
            return true
        }
        // Some Fold8 firmware builds report FLAT or a vertical hinge from the
        // WindowManager extension while the hardware hinge is already in the
        // stable laptop range.  A valid local hinge sample is therefore the
        // fallback for the special Fold8 profile as well; otherwise laptop
        // mode never starts on those builds.  The API still wins for a
        // confirmed HALF_OPENED posture above, and a flat sensor sample still
        // turns the deck off normally.
        if (angle != null && isLaptopHingeAngle(angle)) {
            return true
        }
        foldingApiLaptopPosture?.let { return it }
        return angle?.let(::isLaptopHingeAngle)
    }

    private fun currentStandardFoldPosture(angle: Float?): Boolean? {
        // Fold7 and earlier normal-size Folds can report a vertical
        // FoldingFeature orientation while the device is already in the
        // supported half-open posture. The posture state itself is reliable,
        // so prefer it and only use the sensor when the API is unavailable.
        foldingApiLaptopPosture?.let { return it }
        if (angle != null) return isLaptopHingeAngle(angle)
        return null
    }

    internal fun updateLaptopModeForHinge(angle: Float) {
        if (!MirrorService.active || desk.root == null || desk.suspendedForLockScreen) return
        // The deck belongs to the external screen now; the hinge and the setting do not move it.
        if (desk.externalInputDeck) return
        if (!desk.isLaptopAutoDetectionEnabled()) return
        // Hinge sensors on real devices are noisy around the flex posture
        // thresholds.  Filter small jumps and require the candidate state to
        // remain stable briefly before rebuilding the laptop deck; otherwise
        // the deck repeatedly fades in/out and the virtual display is resized
        // on every sensor fluctuation.
        val filtered = filteredHingeAngle?.let { previous ->
            // TYPE_HINGE_ANGLE is on-change on Samsung. A complete open/close
            // can therefore arrive as a single large jump; smoothing that
            // jump by 25% leaves it on the old side of the threshold forever.
            // Snap large posture changes, while still filtering small sensor
            // noise around the flex boundary.
            if (abs(angle - previous) >= 12f) angle
            else previous + (angle - previous) * 0.25f
        } ?: angle
        filteredHingeAngle = filtered
        hingeAngle = angle
        refreshFoldingApiState("hinge_candidate")
        evaluateLaptopModeForPosture(
            currentLaptopPosture() ?: false,
            "hinge angle=$filtered raw=$angle"
        )
    }

    /** Re-evaluate from the folding API when no new sensor sample was sent. */
    internal fun updateLaptopModeFromCurrentPosture(reason: String) {
        if (!MirrorService.active || desk.root == null || desk.suspendedForLockScreen) return
        if (!desk.isLaptopAutoDetectionEnabled()) return
        val posture = currentLaptopPosture() ?: return
        evaluateLaptopModeForPosture(posture, reason)
    }

    private fun evaluateLaptopModeForPosture(laptopPosture: Boolean, source: String) {
        if (desk.externalInputDeck) return
        // A manual overlay disable is scoped to the current posture. Once the
        // hinge is flat again, the next flex transition may be auto-detected
        // normally. Clear this before computing shouldShow so the same sample
        // cannot re-enable a deck the user just dismissed.
        if (!laptopPosture && laptopAutoSuppressedByUser) {
            laptopAutoSuppressedByUser = false
            OperationLog.i(context, "LaptopMode", "$source flat posture clears manual auto-suppression")
        }
        // A half-open hinge is itself authoritative: inactive inner panels are
        // omitted from DisplayManager on several foldables and the emulator.
        val mainDisplay = laptopPosture || desk.deviceDisplays.isFoldableMainDisplay()
        if (!mainDisplay) laptopAutoActivated = false
        // Automatic laptop mode is limited to portrait holding orientation on
        // Fold8-style devices. Landscape sessions can still be enabled from
        // the overlay; that explicit manual flag is preserved here.
        val autoShouldShow = !laptopAutoSuppressedByUser &&
                desk.isLaptopAutoOrientationEligible() && laptopPosture
        // Posture owns only automatically activated laptop mode. An explicit
        // user enable remains authoritative until the user disables it or the
        // host really moves to the cover display (handled by the independent
        // stable host-mismatch guard). Foldables can transiently omit their
        // inactive inner panel while unfolding, which must not revoke intent.
        val shouldShow = laptopManualOverride || (mainDisplay && autoShouldShow)
        if (shouldShow == desk.laptopModeActive) {
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            return
        }
        val now = SystemClock.uptimeMillis()
        if (pendingLaptopMode != shouldShow) {
            pendingLaptopMode = shouldShow
            pendingLaptopModeSince = now
            val generation = ++laptopModeEvaluationGeneration
            android.os.Handler(context.mainLooper).postDelayed({
                if (generation != laptopModeEvaluationGeneration || !MirrorService.active ||
                    desk.suspendedForLockScreen || pendingLaptopMode != shouldShow
                ) return@postDelayed
                val stableAngle = filteredHingeAngle ?: hingeAngle
                val stablePosture = currentLaptopPosture() ?: return@postDelayed
                if (stablePosture != shouldShow && !laptopManualOverride) {
                    pendingLaptopMode = null
                    pendingLaptopModeSince = 0L
                    return@postDelayed
                }
                pendingLaptopMode = null
                pendingLaptopModeSince = 0L
                laptopAutoActivated = shouldShow && !laptopManualOverride
                OperationLog.i(
                    context,
                    "LaptopMode",
                    "$source timer angle=$stableAngle manual=$laptopManualOverride " +
                            "main=$mainDisplay show=$shouldShow"
                )
                desk.setLaptopMode(shouldShow)
            }, laptopModeDebounceMs)
            return
        }
        if (now - pendingLaptopModeSince >= laptopModeDebounceMs) {
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            laptopAutoActivated = shouldShow && !laptopManualOverride
            OperationLog.i(
                context,
                "LaptopMode",
                "$source manual=$laptopManualOverride main=$mainDisplay show=$shouldShow"
            )
            desk.setLaptopMode(shouldShow)
        }
    }

    internal fun laptopFoldProfile(): LaptopFoldProfile {
        // Build.MODEL may contain a market suffix and Build.DEVICE/PRODUCT may
        // contain an OEM suffix. Normalize all three before matching so that
        // SM-F971, SM-F971N, SM-F971NZ... and SC-57G are handled consistently.
        val modelValue = Build.MODEL.uppercase().filter(Char::isLetterOrDigit)
        val metadataValues = listOf(Build.DEVICE, Build.PRODUCT)
            .map { value -> value.uppercase().filter(Char::isLetterOrDigit) }
        val buildValues = listOf(modelValue) + metadataValues

        fun matchesModel(ids: Set<String>, values: List<String> = buildValues): Boolean = values.any { value ->
            ids.any { id -> value.startsWith(id) }
        }

        fun matchesDevice(prefix: String): Boolean = metadataValues.any { value ->
            value.startsWith(prefix)
        }

        // Check Ultra first. It is the normal-size path, and an explicit q8q /
        // SM-F976 match must never be mistaken for the special-size Fold8.
        return when {
            matchesModel(MirrorService.FOLD8_ULTRA_MODEL_IDS, listOf(modelValue)) ->
                LaptopFoldProfile.STANDARD_FOLDABLE

            matchesModel(MirrorService.FOLD8_SPECIAL_MODEL_IDS, listOf(modelValue)) ->
                LaptopFoldProfile.FOLD8

            matchesModel(MirrorService.FOLD8_ULTRA_MODEL_IDS) || matchesDevice(MirrorService.FOLD8_ULTRA_DEVICE_PREFIX) ->
                LaptopFoldProfile.STANDARD_FOLDABLE

            matchesModel(MirrorService.FOLD8_SPECIAL_MODEL_IDS) || matchesDevice(MirrorService.FOLD8_SPECIAL_DEVICE_PREFIX) ->
                LaptopFoldProfile.FOLD8

            else -> LaptopFoldProfile.STANDARD_FOLDABLE
        }
    }

    internal fun refreshFoldingApiState(reason: String, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - foldingApiLastProbeAt < 180L) return
        foldingApiLastProbeAt = now
        runCatching {
            // The WindowManager extension is window-scoped. Query it with the
            // live control Activity when available; an AccessibilityService
            // context has no window token and an empty result there must not
            // be mistaken for a confirmed non-foldable device.
            val activity = MainActivity.currentActivity()
                ?: throw IllegalStateException(moe.n4tsu.dextop.localText("uiNoActivityWindowForFoldingAPI"))
            val info = WindowInfoTracker.getOrCreate(activity)
                .getCurrentWindowLayoutInfo(activity)
            val features = info.displayFeatures.filterIsInstance<FoldingFeature>()
            val halfOpenedFeature = features.firstOrNull {
                it.state == FoldingFeature.State.HALF_OPENED
            }
            Triple(
                features,
                halfOpenedFeature != null,
                halfOpenedFeature?.orientation == FoldingFeature.Orientation.HORIZONTAL
            )
        }.onSuccess { (features, halfOpened, horizontalHinge) ->
            foldingApiLastSuccessAt = now
            val foldable = features.isNotEmpty()
            if (foldingApiFoldable != foldable ||
                foldingApiLaptopPosture != halfOpened ||
                foldingApiHorizontalHinge != horizontalHinge
            ) {
                OperationLog.i(
                    context,
                    "FoldState",
                    "WindowManager folding API available=true foldable=$foldable " +
                            "halfOpened=$halfOpened hingeHorizontal=$horizontalHinge reason=$reason"
                )
            }
            foldingApiFoldable = foldable
            foldingApiLaptopPosture = halfOpened
            foldingApiHorizontalHinge = horizontalHinge
        }.onFailure {
            // Extension versions before current-window-layout support throw
            // here. Keep the last positive HALF_OPENED result for a short
            // hand-off window: during a fold transition the control Activity
            // can briefly lose its window token, and treating that exception
            // as a flat posture hides the keyboard a few hundred ms after it
            // was shown. Once the grace period expires, the hinge sensor is a
            // valid fallback again.
            if (now - foldingApiLastSuccessAt > foldingApiFailureGraceMs) {
                foldingApiFoldable = null
                foldingApiLaptopPosture = null
                foldingApiHorizontalHinge = null
            }
            if (force) Log.d(desk.logTag, "WindowManager folding API unavailable reason=$reason", it)
        }
    }
}
