package moe.n4tsu.dextop

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.Display

/** What [TaskRouting] needs from the session beyond the shared interfaces. */
internal interface TaskRoutingHost : DesktopSession, OverlayMenu {
    val displayFactory: DisplayFactory
    val forceFreeformSession: Boolean
    val recents: RecentsController
    val recentsExecutor: java.util.concurrent.ExecutorService
    val workspaces: Workspaces
}

/** Finds the tasks on the desktop display and moves app and notification tasks onto it. */
internal class TaskRouting(
    private val context: Context,
    private val desk: TaskRoutingHost,
) {
    internal val launchedAppBounds = linkedMapOf<String, Rect>()

    internal var notificationLaunchArmedUntil = 0L

    internal var notificationRouteGeneration = 0

    private var autoOverlapCorrectionGeneration = 0

    internal fun startAutoOverlapCorrection() {
        val generation = ++autoOverlapCorrectionGeneration
        val displayId = desk.targetDisplayId
        val handler = Handler(context.mainLooper)
        val autoTasks = mutableMapOf<Int, moe.n4tsu.dextop.plasma.TaskSnapshot>()
        var lastSplitSnapshot = ""
        // One bridge for the whole session: making one looks up the task service each time.
        val bridge by lazy {
            moe.n4tsu.dextop.plasma.TaskBridge({ name, type -> desk.privilegedAccess.service(name, type) }, desk.logTag)
        }
        val check = object : Runnable {
            override fun run() {
                if (!MirrorService.active || !desk.autoOnlySession || desk.targetDisplayId != displayId ||
                    generation != autoOverlapCorrectionGeneration) return
                val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                val splitCorrection = prefs.getBoolean("flutter.android_auto_correct_split_transfer", false)
                val overlapCorrection = prefs.getBoolean("flutter.android_auto_correct_fullscreen_overlap", false)
                if (desk.forceFreeformSession) {
                    desk.recentsExecutor.execute {
                        runCatching {
                            if (!MirrorService.active || desk.targetDisplayId != displayId ||
                                generation != autoOverlapCorrectionGeneration) return@runCatching
                            val current = bridge.tasks(displayId).filter { it.activityType == 1 }
                            current.forEach { autoTasks[it.taskId] = it }
                            val enabled = splitCorrection
                            // The phone's tasks matter only to the correction.
                            val phoneTasks = if (enabled) bridge.tasks(0) else emptyList()
                            val snapshot = "enabled=$enabled display=$displayId auto=${current.map { it.taskId }} known=${autoTasks.keys} phoneSplit=${phoneTasks.filter { it.windowingMode == 6 }.map { it.taskId }}"
                            if (snapshot != lastSplitSnapshot) {
                                lastSplitSnapshot = snapshot
                                OperationLog.i(context, "CarCompanion", "split correction monitor $snapshot")
                            }
                            if (!enabled) return@runCatching
                            val transferred = phoneTasks.filter {
                                it.taskId in autoTasks && it.visible && it.windowingMode == 6
                            }
                            if (transferred.isNotEmpty()) {
                                val area = desk.workspaces.workspaceLayoutEngine.usableArea(displayId, desk.targetWidth, desk.targetHeight)
                                val left = Rect(area.left, area.top, area.centerX(), area.bottom)
                                val right = Rect(area.centerX(), area.top, area.right, area.bottom)
                                for (task in transferred) {
                                    AutoSplitPlacement.begin(context, bridge, task, displayId, area)
                                    OperationLog.i(context, "CarCompanion",
                                        "split transfer corrected task=${task.taskId} display=$displayId bounds=$left")
                                }
                            }
                        }.onFailure { Log.w(desk.logTag, "Auto split transfer correction failed", it) }
                    }
                }
                if (overlapCorrection) {
                    val full = Rect(0, 0, desk.targetWidth, desk.targetHeight)
                    val usable = runCatching {
                        desk.workspaces.workspaceLayoutEngine.usableArea(displayId, desk.targetWidth, desk.targetHeight)
                    }.getOrNull()
                    if (usable != null && !usable.isEmpty && usable != full) {
                        desk.recentsExecutor.execute {
                            runCatching {
                                if (!MirrorService.active || desk.targetDisplayId != displayId ||
                                    generation != autoOverlapCorrectionGeneration) return@runCatching
                                for (task in bridge.tasks(displayId)) {
                                    if (task.displayId != displayId || task.activityType != 1 || !task.visible) continue
                                    val bounds = task.bounds
                                    // Do not constrain ordinary dragging or intentionally small windows.
                                    if (bounds.width() < usable.width() - 2 || bounds.height() < usable.height() - 2 ||
                                        usable.contains(bounds)) continue
                                    val id = task.taskId
                                    check(bridge.setWindowingMode(task, 5)) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnableToChangeTaskWindowingModeTask0"), id) }
                                    check(bridge.setBounds(task, Rect(usable))) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnableToResizeTask0"), id) }
                                    OperationLog.i(context, "CarCompanion",
                                        "fullscreen overlap corrected task=$id display=$displayId from=$bounds to=$usable")
                                }
                            }.onFailure { Log.w(desk.logTag, "Auto fullscreen overlap correction failed", it) }
                        }
                    }
                }
                // Corrections must follow a transfer at once; with both off this only tracks the car's tasks.
                handler.postDelayed(this, if (splitCorrection || overlapCorrection) 300L else 1_000L)
            }
        }
        handler.postDelayed(check, 1000L)
    }

    internal fun currentDisplayTasks(): Map<String, Rect> {
        if (desk.targetDisplayId < 0) return emptyMap()
        val result = desk.privilegedAccess.execute("sh", "-c", "dumpsys activity activities")
        if (!result.succeeded) {
            Log.w(desk.logTag, "task query failed: ${result.error}")
            return emptyMap()
        }
        val displayHeader = Regex("Display: mDisplayId=${desk.targetDisplayId}(?:\\s|\\()")
        val packagePattern = Regex("(?:A=\\d+:|I=)([A-Za-z0-9._]+)")
        val boundsPattern = Regex("bounds=\\[(-?\\d+),(-?\\d+)]\\[(-?\\d+),(-?\\d+)]")
        val found = linkedMapOf<String, Rect>()
        var inDisplay = false
        var pendingPackage: String? = null
        result.output.lineSequence().forEach { line ->
            if (line.contains("Display: mDisplayId=")) {
                inDisplay = displayHeader.containsMatchIn(line)
                pendingPackage = null
                return@forEach
            }
            if (!inDisplay) return@forEach
            packagePattern.find(line)?.groupValues?.getOrNull(1)?.let { candidate ->
                pendingPackage = candidate.takeIf {
                    !line.contains("type=home") && desk.workspaces.isWorkspaceApp(it)
                }
            }
            val match = boundsPattern.find(line) ?: return@forEach
            val packageName = pendingPackage ?: return@forEach
            val values = match.groupValues.drop(1).mapNotNull(String::toIntOrNull)
            if (values.size == 4 && values[2] > values[0] && values[3] > values[1]) {
                found.putIfAbsent(packageName, Rect(values[0], values[1], values[2], values[3]))
                pendingPackage = null
            }
        }
        OperationLog.i(context, "Workspace", "task query count=${found.size} display=${desk.targetDisplayId}")
        return found
    }

    /**
     * A captured workspace must contain only packages Dextop can restore. Task
     * dumps also include System UI surfaces, launchers, providers and transient
     * activities; those have no meaningful launcher icon and previously became
     * transparent entries in the workspace UI.
     */

    internal fun usesDecoratedDirectAutoDisplay(): Boolean =
        desk.autoOnlySession && desk.forceFreeformSession && desk.displayFactory.autoOwnedDisplay != null && desk.targetDisplayId >= 0

    /**
     * ActivityOptions launch bounds are ignored when the package already has
     * a task. The Electron implementation solves that by finding the task,
     * forcing it back to freeform, then resizing it. Do the same only for the
     * decorated direct Auto display; regular Samsung DeX continues to manage
     * its own windows.
     */

    internal fun placePackageTaskThroughBinder(
        displayId: Int,
        packageName: String,
        wanted: Rect,
    ): Boolean = runCatching {
        val service = desk.privilegedAccess.service("activity_task", "android.app.IActivityTaskManager")
        val taskManagerInterface = Class.forName("android.app.IActivityTaskManager")
        val getTasks = taskManagerInterface.methods.firstOrNull {
            it.name == "getTasks" && it.parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
            )
        } ?: error(moe.n4tsu.dextop.localText("uiIActivityTaskManagerGetTasksIntBooleanBooleanIntUnavailable"))
        val tasks = getTasks.invoke(service, 128, true, false, displayId) as? List<*>
            ?: return@runCatching false
        val task = tasks.firstOrNull { candidate ->
            candidate != null && desk.recents.intField(candidate, "displayId") == displayId &&
                taskPackageName(candidate) == packageName
        } ?: return@runCatching false
        val taskId = desk.recents.intField(task, "taskId")
        taskManagerInterface.methods.firstOrNull {
            it.name == "setTaskWindowingMode" && it.parameterTypes.size == 3
        }?.invoke(service, taskId, 5, false)
        val resizeTask = taskManagerInterface.methods.firstOrNull {
            it.name == "resizeTask" && it.parameterTypes.size == 3
        } ?: error(moe.n4tsu.dextop.localText("uiIActivityTaskManagerResizeTaskUnavailable"))
        resizeTask.invoke(service, taskId, Rect(wanted), 0)
        OperationLog.i(
            context,
            "Workspace",
            "decorated Auto task placed package=$packageName task=$taskId display=$displayId bounds=$wanted",
        )
        true
    }.onFailure {
        OperationLog.w(
            context,
            "Workspace",
            "decorated Auto placement failed package=$packageName display=$displayId",
            it,
        )
    }.getOrDefault(false)

    private fun taskPackageName(task: Any): String? {
        for (fieldName in arrayOf("topActivity", "baseActivity", "origActivity")) {
            val component = runCatching {
                task.javaClass.getField(fieldName).get(task) as? ComponentName
            }.getOrNull()
            if (component != null) return component.packageName
        }
        return null
    }

    internal fun routeNotificationTask(packageName: String, generation: Int, attempt: Int) {
        desk.root?.postDelayed({
            if (!MirrorService.active || desk.targetDisplayId < 0 || generation != notificationRouteGeneration) return@postDelayed
            val query = desk.privilegedAccess.execute("sh", "-c", "dumpsys activity activities")
            val taskId = if (query.succeeded) {
                NotificationTaskLocator.findSystemUiLaunchedTask(query.output, packageName, desk.targetDisplayId)
            } else null
            if (taskId == null) {
                if (attempt < MirrorService.NOTIFICATION_ROUTE_RETRIES) {
                    routeNotificationTask(packageName, generation, attempt + 1)
                } else {
                    Log.w(desk.logTag, "notification task not found package=$packageName")
                }
                return@postDelayed
            }
            val moved = desk.privilegedAccess.execute(
                "am", "display", "move-stack", taskId.toString(), desk.targetDisplayId.toString()
            )
            if (moved.succeeded) {
                OperationLog.i(
                    context,
                    "NotificationRouting",
                    "moved package=$packageName task=$taskId display=${desk.targetDisplayId}"
                )
                Log.i(desk.logTag, "notification task moved package=$packageName task=$taskId display=${desk.targetDisplayId}")
            } else if (attempt < MirrorService.NOTIFICATION_ROUTE_RETRIES) {
                routeNotificationTask(packageName, generation, attempt + 1)
            } else {
                Log.e(desk.logTag, "notification task move failed package=$packageName task=$taskId error=${moved.error}")
            }
        }, MirrorService.NOTIFICATION_ROUTE_RETRY_DELAY_MS)
    }
}
