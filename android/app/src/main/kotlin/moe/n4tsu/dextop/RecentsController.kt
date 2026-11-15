package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Rect
import android.os.Binder
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/** What [RecentsController] needs from the session beyond the shared interfaces. */
internal interface RecentsControllerHost : DesktopSession, OverlayMenu {
    val recentsExecutor: java.util.concurrent.ExecutorService
    val recentsGeneration: AtomicInteger
}

/** Opens Recents on the desktop display and keeps its task fullscreen there. */
internal class RecentsController(
    private val context: Context,
    private val desk: RecentsControllerHost,
) {
    fun showRecentsOnTargetDisplay(): Boolean {
        val displayId = desk.targetDisplayId
        if (!MirrorService.active || displayId < 0) return false
        desk.recentsExecutor.execute {
            val result = desk.privilegedAccess.execute(
                "input", "-d", displayId.toString(), "keyevent", "KEYCODE_APP_SWITCH"
            )
            if (!result.succeeded) {
                Log.w(desk.logTag, "display-specific recents key failed display=$displayId error=${result.error}")
            }
        }
        scheduleRecentsFullscreenCorrection()
        return true
    }

    /** Uses ActivityTaskManager Binder data; no dumpsys parsing or shell resize command. */
    fun scheduleRecentsFullscreenCorrection() {
        val generation = desk.recentsGeneration.incrementAndGet()
        val displayId = desk.targetDisplayId
        val wanted = Rect(0, 0, desk.targetWidth, desk.targetHeight)
        if (displayId < 0 || wanted.isEmpty) return
        desk.recentsExecutor.execute {
            val deadline = SystemClock.uptimeMillis() + 700L
            while (MirrorService.active && desk.targetDisplayId == displayId && generation == desk.recentsGeneration.get()) {
                if (resizeRecentsTaskThroughBinder(displayId, wanted)) return@execute
                if (SystemClock.uptimeMillis() >= deadline) return@execute
                try {
                    Thread.sleep(50L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
            }
        }
    }

    private fun resizeRecentsTaskThroughBinder(displayId: Int, wanted: Rect): Boolean = runCatching {
        val service = desk.privilegedAccess.service("activity_task", "android.app.IActivityTaskManager")
        val taskManagerInterface = Class.forName("android.app.IActivityTaskManager")
        val getTasks = taskManagerInterface.methods.firstOrNull {
            it.name == "getTasks" && it.parameterTypes.contentEquals(
                arrayOf(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            )
        } ?: error(moe.n4tsu.dextop.localText("uiIActivityTaskManagerGetTasksIntBooleanBooleanIntUnavailable"))
        val tasks = getTasks.invoke(service, 64, true, false, displayId)
            as? List<*> ?: return@runCatching false
        val recents = tasks.firstOrNull { task ->
            task != null && intField(task, "displayId") == displayId && activityTypeOf(task) == 3
        } ?: return@runCatching false
        val current = taskBoundsOf(recents)
        if (current == wanted) return@runCatching true
        val resizeTask = taskManagerInterface.methods.firstOrNull {
            it.name == "resizeTask" && it.parameterTypes.size == 3
        } ?: error(moe.n4tsu.dextop.localText("uiIActivityTaskManagerResizeTaskUnavailable"))
        val taskId = intField(recents, "taskId")
        resizeTask.invoke(service, taskId, Rect(wanted), 0)
        OperationLog.i(
            context,
            "Recents",
            "binder fullscreen task=$taskId display=$displayId " +
                    "from=$current to=$wanted"
        )
        true
    }.onFailure {
        Log.w(desk.logTag, "ActivityTaskManager recents correction failed", it)
    }.getOrDefault(false)

    fun intField(instance: Any, name: String): Int =
        instance.javaClass.getField(name).getInt(instance)

    private fun taskConfiguration(task: Any): Any =
        task.javaClass.getField("configuration").get(task)
            ?: error(moe.n4tsu.dextop.localText("uiRunningTaskInfoConfigurationUnavailable"))

    private fun activityTypeOf(task: Any): Int {
        val configuration = taskConfiguration(task)
        val windowConfiguration = configuration.javaClass
            .getDeclaredField("windowConfiguration").apply { isAccessible = true }
            .get(configuration)
        return windowConfiguration.javaClass.getMethod("getActivityType").invoke(windowConfiguration) as Int
    }

    private fun taskBoundsOf(task: Any): Rect {
        val configuration = taskConfiguration(task)
        val windowConfiguration = configuration.javaClass
            .getDeclaredField("windowConfiguration").apply { isAccessible = true }
            .get(configuration)
        return Rect(windowConfiguration.javaClass.getMethod("getBounds").invoke(windowConfiguration) as Rect)
    }
}
