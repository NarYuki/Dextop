package moe.n4tsu.dextop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display

/**
 * Owns the optional cover-display environment separately from the primary
 * Dextop session.  It never removes another session's overlay specification.
 */
internal class CoverDisplaySessionController(private val context: Context) {
    data class State(
        val androidVisible: Boolean,
        val desktopActive: Boolean,
        val displayId: Int,
        val busy: Boolean,
    )

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val privilegedAccess = PrivilegedAccess("DextopCoverDisplay")
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var androidVisible = false
    @Volatile private var coverDisplayId = Display.INVALID_DISPLAY
    @Volatile private var coverSpec: String? = null
    @Volatile private var lifecycleToken: String? = null
    @Volatile private var busy = false

    fun state() = State(
        androidVisible = androidVisible,
        desktopActive = coverDisplayId != Display.INVALID_DISPLAY,
        displayId = coverDisplayId,
        busy = busy,
    )

    fun ownedDisplayIds(): Set<Int> = coverDisplayId
        .takeIf { it != Display.INVALID_DISPLAY }
        ?.let(::setOf)
        ?: emptySet()

    fun ownedSpecs(): Set<String> = coverSpec?.let(::setOf) ?: emptySet()

    fun setAndroidVisible(visible: Boolean, completion: (Result<State>) -> Unit) {
        if (busy) return completion(Result.failure(IllegalStateException("Cover display is busy")))
        busy = true
        Thread {
            runCatching {
                if (visible) {
                    requireCoverDeviceState()
                    androidVisible = true
                } else {
                    androidVisible = false
                    if (coverDisplayId == Display.INVALID_DISPLAY) resetDeviceState()
                }
                state()
            }.also { result ->
                busy = false
                handler.post { completion(result) }
            }
        }.start()
    }

    fun startDesktop(
        width: Int,
        height: Int,
        density: Int,
        secure: Boolean,
        lifecycleTokenProvider: () -> String,
        completion: (Result<State>) -> Unit,
    ) {
        if (busy) return completion(Result.failure(IllegalStateException("Cover display is busy")))
        if (coverDisplayId != Display.INVALID_DISPLAY) return completion(Result.success(state()))
        busy = true
        Thread {
            runCatching {
                requireCoverDeviceState()
                Thread.sleep(800L)
                val before = displayManager.displays.mapTo(mutableSetOf()) { it.displayId }
                val mode = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.mode
                val panelShort = listOfNotNull(mode?.physicalWidth, mode?.physicalHeight)
                    .minOrNull()?.takeIf { it > 0 }
                val panelLong = listOfNotNull(mode?.physicalWidth, mode?.physicalHeight)
                    .maxOrNull()?.takeIf { it > 0 }
                val landscape = width >= height
                val coverWidth = if (landscape) panelLong else panelShort
                val coverHeight = if (landscape) panelShort else panelLong
                val flags = buildList {
                    add("own_content_only")
                    if (secure) add("secure")
                }.joinToString(",", prefix = ",")
                val spec = "${coverWidth ?: width}x${coverHeight ?: height}/${density}${flags}"
                val current = overlaySpecs()
                check(Settings.Global.putString(
                    context.contentResolver,
                    DisplayMirrorBackend.DISPLAY_SPECIFICATION,
                    (current + spec).joinToString(";"),
                )) { "Unable to request the cover display" }
                coverSpec = spec
                before
            }.onSuccess { before ->
                waitForDisplay(before, 0, lifecycleTokenProvider, completion)
            }.onFailure { error ->
                busy = false
                handler.post { completion(Result.failure(error)) }
            }
        }.start()
    }

    fun stopDesktop(completion: (Result<State>) -> Unit) {
        if (busy) return completion(Result.failure(IllegalStateException("Cover display is busy")))
        busy = true
        Thread {
            val result = runCatching {
                val oldDisplay = coverDisplayId
                removeOwnedSpec()
                coverDisplayId = Display.INVALID_DISPLAY
                coverSpec = null
                lifecycleToken = null
                if (!androidVisible) resetDeviceState()
                OperationLog.i(context, "CoverDisplay", "desktop stopped display=$oldDisplay")
                state()
            }
            busy = false
            handler.post { completion(result) }
        }.start()
    }

    /** Ends only this owned session when the physical display topology changes. */
    fun reconcileLifecycle(reason: String, currentLifecycleToken: String) {
        val ownedId = coverDisplayId
        val ownedSpec = coverSpec ?: return
        if (ownedId == Display.INVALID_DISPLAY || busy) return
        val displayExists = displayManager.getDisplay(ownedId) != null
        val specificationExists = ownedSpec in overlaySpecs()
        val physicalStateChanged = lifecycleToken?.let { it != currentLifecycleToken } == true
        if (displayExists && specificationExists && !physicalStateChanged) return

        busy = true
        Thread {
            val result = runCatching { removeOwnedSpec() }
            coverDisplayId = Display.INVALID_DISPLAY
            coverSpec = null
            lifecycleToken = null
            androidVisible = false
            if (result.isSuccess) {
                OperationLog.i(
                    context,
                    "CoverDisplay",
                    "desktop suspended resumable=true reason=$reason displayPresent=$displayExists " +
                        "specPresent=$specificationExists physicalStateChanged=$physicalStateChanged",
                )
            }
            result.exceptionOrNull()?.let {
                OperationLog.e(context, "CoverDisplay", "lifecycle cleanup failed", it)
            }
            busy = false
        }.start()
    }

    private fun waitForDisplay(
        before: Set<Int>,
        attempt: Int,
        lifecycleTokenProvider: () -> String,
        completion: (Result<State>) -> Unit,
    ) {
        val display = displayManager.displays.firstOrNull {
            it.displayId !in before && it.displayId != Display.DEFAULT_DISPLAY && isOverlay(it)
        }
        if (display == null && attempt < 50) {
            handler.postDelayed({ waitForDisplay(before, attempt + 1, lifecycleTokenProvider, completion) }, 100L)
            return
        }
        val result = runCatching {
            val target = display ?: error("Cover display creation timed out")
            coverDisplayId = target.displayId
            lifecycleToken = lifecycleTokenProvider()
            launchHome(target.displayId)
            OperationLog.i(context, "CoverDisplay", "desktop started display=${target.displayId} spec=${coverSpec.orEmpty()}")
            state()
        }
        if (result.isFailure) runCatching { removeOwnedSpec() }
        busy = false
        completion(result)
    }

    private fun overlaySpecs(): List<String> = Settings.Global.getString(
        context.contentResolver,
        DisplayMirrorBackend.DISPLAY_SPECIFICATION,
    ).orEmpty().split(';').filter(String::isNotBlank)

    private fun removeOwnedSpec() {
        val owned = coverSpec ?: return
        var removed = false
        val remaining = overlaySpecs().filter { entry ->
            if (!removed && entry == owned) {
                removed = true
                false
            } else {
                true
            }
        }
        check(Settings.Global.putString(
            context.contentResolver,
            DisplayMirrorBackend.DISPLAY_SPECIFICATION,
            remaining.joinToString(";"),
        )) { "Unable to remove the cover display" }
    }

    private fun requireCoverDeviceState() {
        val result = privilegedAccess.execute("cmd", "device_state", "state", "5")
        check(result.succeeded) { result.error.ifBlank { "Unable to activate the cover display" } }
    }

    private fun resetDeviceState() {
        val result = privilegedAccess.execute("cmd", "device_state", "state", "reset")
        check(result.succeeded) { result.error.ifBlank { "Unable to restore the device state" } }
    }

    private fun launchHome(displayId: Int) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
    }

    private fun isOverlay(display: Display): Boolean = runCatching {
        Display::class.java.getMethod("getType").invoke(display) as Int == 4
    }.getOrDefault(false)
}
