package moe.n4tsu.dextop

import android.content.Context
import android.view.Display
import android.view.WindowManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** What [InputDiagnostics] needs from the session beyond the shared interfaces. */
internal interface InputDiagnosticsHost : DesktopSession, OverlayMenu {
    fun postMainDelayed(delayMs: Long, action: () -> Unit)
}

/** Rate-limited logging of input dispatch, touch routing and window launches. */
internal class InputDiagnostics(
    private val context: Context,
    private val desk: InputDiagnosticsHost,
) {
    val windowDiagnosticExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-window-diagnostics").apply { isDaemon = true }
    }

    val windowDiagnosticGeneration = AtomicInteger()

    var lastInputDiagnosticAt = 0L

    var lastTouchDiagnosticAt = 0L

    var inputDiagnosticSequence = 0L

    /** Samples the system state that decides whether a launch became a desktop window. */
    fun scheduleWindowLaunchDiagnostics(reason: String, delayMs: Long = 450L) {
        val diagnosticDisplayId = desk.targetDisplayId
        val generation = windowDiagnosticGeneration.incrementAndGet()
        desk.postMainDelayed(delayMs) {
            windowDiagnosticExecutor.execute {
                if (generation != windowDiagnosticGeneration.get()) return@execute
                collectWindowLaunchDiagnostics(reason, diagnosticDisplayId)
            }
        }
    }

    private fun collectWindowLaunchDiagnostics(reason: String, displayId: Int) {
        if (displayId < 0) return
        val taskCommand =
            "dumpsys activity activities | " +
                    "grep -E 'Display: mDisplayId=$displayId|mResumedActivity|topResumedActivity|" +
                    "windowingMode=|bounds=' | tail -n 64"
        logDiagnosticCommand("ActivityTaskManager", reason, displayId, taskCommand)

        val windowCommand =
            "dumpsys window displays | " +
                    "grep -E 'Display: mDisplayId=$displayId|mDisplayId=$displayId|mCurrentFocus|" +
                    "mFocusedApp|windowingMode=|mBounds=' | tail -n 64"
        logDiagnosticCommand("WindowManager", reason, displayId, windowCommand)

        val systemLogCommand =
            "logcat -d -v brief -t 160 ActivityTaskManager:I WindowManager:I " +
                    "ShellTaskOrganizer:I DesktopMode:I DesktopTasksController:I '*:S' | tail -n 80"
        logDiagnosticCommand("TaskOrganizer", reason, displayId, systemLogCommand)
    }

    private fun logDiagnosticCommand(
        component: String,
        reason: String,
        displayId: Int,
        command: String
    ) {
        val result = desk.privilegedAccess.execute("sh", "-c", command)
        if (!result.succeeded) {
            OperationLog.w(
                context,
                component,
                "diagnostic unavailable reason=$reason display=$displayId exit=${result.exitCode} " +
                        "detail=${result.error.take(240)}"
            )
            return
        }
        val compact = result.output.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" | ")
            .ifEmpty { "no matching system records" }
        OperationLog.i(
            context,
            component,
            "diagnostic reason=$reason display=$displayId $compact"
        )
    }
}
