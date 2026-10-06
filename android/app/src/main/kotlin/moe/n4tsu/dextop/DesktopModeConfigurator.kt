package moe.n4tsu.dextop

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

internal data class StrategyAttempt(val strategy: String, val succeeded: Boolean, val detail: String)

internal class DesktopModeConfigurator(
    private val context: Context,
    private val resolver: ContentResolver,
    private val privilegedAccess: PrivilegedAccess,
    private val environment: DesktopEnvironment,
    private val sessionJournal: SessionJournal
) {
    private data class SavedGlobal(val key: String, val value: String?)
    private val savedGlobals = mutableListOf<SavedGlobal>()
    private var applied = false
    private var forcedFreeformGlobalsApplied = false

    fun applyForCurrentDevice(): List<StrategyAttempt> {
        if (applied) return emptyList()
        applied = true
        if (environment.platformManaged) {
            OperationLog.i(context, "DesktopMode", "platform-managed environment=${environment.id}")
            return listOf(StrategyAttempt("platform_managed", true, environment.id))
        }
        return environment.temporaryGlobalSettings.map { (key, value) ->
            runCatching {
                val previous = Settings.Global.getString(resolver, key)
                savedGlobals += SavedGlobal(key, previous)
                sessionJournal.rememberGlobal(key, previous)
                check(Settings.Global.putString(resolver, key, value)) { "setting rejected" }
                StrategyAttempt("global:$key", true, "$previous -> ${value ?: "<deleted>"}")
            }.getOrElse {
                OperationLog.w(context, "DesktopMode", "optional global setting failed key=$key", it)
                StrategyAttempt("global:$key", false, it.message.orEmpty())
            }
        }
    }

    /** Optional configuration: failure is isolated to this display and never aborts startup. */
    fun configureDisplay(displayId: Int, forceFreeform: Boolean = false): List<StrategyAttempt> {
        if (!environment.configureFreeformWindowing && !forceFreeform) return emptyList()
        val attempts = mutableListOf<StrategyAttempt>()
        if (forceFreeform && !forcedFreeformGlobalsApplied) {
            forcedFreeformGlobalsApplied = true
            listOf("enable_freeform_support", "force_resizable_activities").forEach { key ->
                val previous = Settings.Global.getString(resolver, key)
                savedGlobals += SavedGlobal(key, previous)
                sessionJournal.rememberGlobal(key, previous)
                val result = privilegedAccess.execute(
                    "cmd", "settings", "put", "global", key, "1"
                )
                attempts += StrategyAttempt(
                    "global:$key",
                    result.succeeded,
                    result.error.ifBlank { result.output }
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            val engagement = privilegedAccess.execute(
                "wm", "set-display-engagement-mode", "-d", displayId.toString(), "3"
            )
            attempts += StrategyAttempt(
                "display_engagement",
                engagement.succeeded,
                engagement.error.ifBlank { engagement.output }
            )
        }
        val windowingStrategies = if (forceFreeform) {
            // Electron DEX applies this synchronously, before the display is
            // published back to the app. Keep its exact command order: on
            // several Samsung releases `cmd window` works while the older
            // activity_task command either exits successfully without taking
            // effect or races DeX's initial task creation.
            listOf("cmd_window", "wm")
        } else environment.windowingStrategies
        for (strategy in windowingStrategies) {
            val args = when (strategy) {
                "cmd_window" -> arrayOf(
                    "cmd", "window", "set-display-windowing-mode", "-d",
                    displayId.toString(), "5"
                )
                "wm" -> arrayOf("wm", "set-display-windowing-mode", "-d", displayId.toString(), "5")
                "activity_task_manager" -> arrayOf(
                    "cmd", "activity_task", "set-display-windowing-mode", displayId.toString(), "5"
                )
                else -> continue
            }
            val result = privilegedAccess.execute(*args)
            val attempt = StrategyAttempt(strategy, result.succeeded, result.error.ifBlank { result.output })
            attempts += attempt
            OperationLog.i(context, "DesktopMode", "windowing display=$displayId strategy=$strategy success=${attempt.succeeded} detail=${attempt.detail}")
            if (result.succeeded) break
        }
        // Pixel's projected-display controller initializes asynchronously and
        // can overwrite the first windowing-mode request while registering the
        // new overlay. Reassert it after WMShell has observed the display.
        if (attempts.any { it.strategy != "display_engagement" && it.succeeded }) {
            listOf(250L, 750L).forEach { delay ->
                Handler(Looper.getMainLooper()).postDelayed({
                    if (Build.VERSION.SDK_INT >= 35) {
                        privilegedAccess.execute(
                            "wm", "set-display-engagement-mode", "-d", displayId.toString(), "3"
                        )
                    }
                    val result = privilegedAccess.execute(
                        "wm", "set-display-windowing-mode", "-d", displayId.toString(), "5"
                    )
                    OperationLog.i(
                        context,
                        "DesktopMode",
                        "windowing reapply display=$displayId delay=$delay success=${result.succeeded} " +
                            "detail=${result.error.ifBlank { result.output }}"
                    )
                }, delay)
            }
        }
        return attempts
    }

    fun restore() {
        sessionJournal.restoreSystemSettings()
        OperationLog.i(context, "DesktopMode", "restored settings count=${savedGlobals.size}")
        savedGlobals.clear()
        applied = false
        forcedFreeformGlobalsApplied = false
    }
}
