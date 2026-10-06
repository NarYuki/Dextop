package moe.n4tsu.dextop.plasma

import android.content.ComponentName
import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import moe.n4tsu.dextop.privilege.DistributionPrivilegeRuntime
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.Method

/** One framework task observed on the Dextop display, top-most first. */
internal data class TaskSnapshot(
    val taskId: Int,
    val packageName: String,
    val component: ComponentName?,
    val label: String?,
    val bounds: Rect,
    val windowingMode: Int,
    val activityType: Int,
    val visible: Boolean,
    val focused: Boolean,
    val token: Any?,
    val zIndex: Int,
    val displayId: Int,
)

/**
 * Framework task control used by the Dextop window manager.
 *
 * Every call goes through the privileged runtime (embedded ADB runtime or
 * Shizuku).  Methods are resolved reflectively because their signatures
 * moved between Android 11 and 16; each operation has an ordered list of
 * fallbacks so a vendor fork that removed one path still has a usable one.
 */
internal class TaskBridge(
    private val serviceProvider: (String, String) -> Any,
    private val tag: String = "DextopPlasma",
) {
    private val atmInterface by lazy { Class.forName("android.app.IActivityTaskManager") }
    private val methodCache = HashMap<String, Method?>()
    @Volatile private var organizer: Any? = null
    @Volatile private var organizerUnavailable = false

    private fun atm(): Any = serviceProvider("activity_task", "android.app.IActivityTaskManager")

    private fun method(name: String, predicate: (Method) -> Boolean = { true }): Method? =
        synchronized(methodCache) {
            val key = name + "#" + predicate.hashCode()
            methodCache.getOrPut(key) {
                atmInterface.methods.firstOrNull { it.name == name && predicate(it) }
            }
        }

    fun tasks(displayId: Int): List<TaskSnapshot> {
        val service = atm()
        val getTasks = method("getTasks") {
            it.parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                ),
            )
        }
        val raw = if (getTasks != null) {
            getTasks.invoke(service, 64, false, false, displayId) as? List<*>
        } else {
            method("getTasks") { it.parameterTypes.size == 3 }
                ?.invoke(service, 64, false, false) as? List<*>
        } ?: emptyList<Any>()
        return raw.filterNotNull().mapIndexedNotNull { index, task ->
            runCatching { snapshot(task, index) }.getOrNull()
        }.filter { it.displayId == displayId }
    }

    private fun snapshot(task: Any, index: Int): TaskSnapshot {
        val base = objectField(task, "baseActivity") as? ComponentName
        val top = objectField(task, "topActivity") as? ComponentName
        val component = base ?: top
        val description = objectField(task, "taskDescription")
        val label = description?.let {
            runCatching { it.javaClass.getMethod("getLabel").invoke(it) as? String }.getOrNull()
        }
        val configuration = objectField(task, "configuration")
        val windowConfiguration = configuration?.javaClass
            ?.getDeclaredField("windowConfiguration")?.apply { isAccessible = true }
            ?.get(configuration)
        val bounds = windowConfiguration?.let {
            runCatching { Rect(it.javaClass.getMethod("getBounds").invoke(it) as Rect) }.getOrNull()
        } ?: Rect()
        val mode = windowConfiguration?.let {
            runCatching { it.javaClass.getMethod("getWindowingMode").invoke(it) as Int }.getOrNull()
        } ?: 0
        val type = windowConfiguration?.let {
            runCatching { it.javaClass.getMethod("getActivityType").invoke(it) as Int }.getOrNull()
        } ?: 0
        return TaskSnapshot(
            taskId = intField(task, "taskId"),
            packageName = component?.packageName.orEmpty(),
            component = component,
            label = label,
            bounds = bounds,
            windowingMode = mode,
            activityType = type,
            visible = booleanField(task, "isVisible") ?: true,
            focused = booleanField(task, "isFocused") ?: false,
            token = objectField(task, "token"),
            zIndex = index,
            displayId = runCatching { intField(task, "displayId") }.getOrDefault(-1),
        )
    }

    /** Raises and focuses a task, including one parked behind the desktop. */
    fun focus(task: TaskSnapshot): Boolean {
        val service = atm()
        val attempts = listOf<() -> Unit>(
            { checkNotNull(method("setFocusedTask") { it.parameterTypes.size == 1 }).invoke(service, task.taskId) },
            { reorder(task, toTop = true) },
            {
                checkNotNull(method("moveTaskToFront") { it.parameterTypes.size == 5 })
                    .invoke(service, null, "com.android.shell", task.taskId, 0, null)
            },
            {
                checkNotNull(method("startActivityFromRecents") { it.parameterTypes.size == 2 })
                    .invoke(service, task.taskId, null)
            },
        )
        // setFocusedTask only focuses when the task is already reachable.
        // Reordering first guarantees the task leaves the minimized layer.
        if (!task.visible) runCatching { reorder(task, toTop = true) }
        return attempts.any { attempt -> runCatching { attempt() }.onFailure { log("focus", it) }.isSuccess }
    }

    fun setBounds(task: TaskSnapshot, bounds: Rect): Boolean {
        val resize = method("resizeTask") { it.parameterTypes.size == 3 }
        // resizeTask reports "not allowed" (for example on a multi-window
        // task of some releases) by returning false instead of throwing.
        val direct = runCatching {
            checkNotNull(resize).invoke(atm(), task.taskId, Rect(bounds), RESIZE_MODE_SYSTEM) as? Boolean ?: true
        }.getOrDefault(false)
        if (direct) return true
        return runCatching {
            applyTransaction { wct, token ->
                wct.javaClass.getMethod("setBounds", tokenClass, Rect::class.java).invoke(wct, token, Rect(bounds))
            }(task)
        }.onFailure { log("setBounds", it) }.getOrDefault(false)
    }

    /**
     * Converts a task to a caption-less multi-window root task.  Android draws
     * its own caption only for freeform tasks, so this is what lets Dextop's
     * Breeze decoration be the only title bar.
     */
    fun setWindowingMode(task: TaskSnapshot, mode: Int): Boolean {
        val organized = runCatching {
            applyTransaction { wct, token ->
                wct.javaClass.getMethod(
                    "setWindowingMode",
                    tokenClass,
                    Int::class.javaPrimitiveType,
                ).invoke(wct, token, mode)
            }(task)
        }.getOrDefault(false)
        if (organized) return true
        return runCatching {
            checkNotNull(method("setTaskWindowingMode") { it.parameterTypes.size == 3 })
                .invoke(atm(), task.taskId, mode, false)
            true
        }.onFailure { log("setWindowingMode", it) }.getOrDefault(false)
    }

    /** Moves a task behind the desktop; used for minimize and virtual desktops. */
    fun sendToBack(task: TaskSnapshot): Boolean =
        runCatching { reorder(task, toTop = false) }.onFailure { log("sendToBack", it) }.isSuccess

    fun close(task: TaskSnapshot): Boolean = runCatching {
        checkNotNull(method("removeTask") { it.parameterTypes.size == 1 }).invoke(atm(), task.taskId)
        true
    }.onFailure { log("close", it) }.getOrDefault(false)

    val supportsOrganizer: Boolean get() = runCatching { organizerController() != null }.getOrDefault(false)

    private fun reorder(task: TaskSnapshot, toTop: Boolean) {
        check(
            applyTransaction { wct, token ->
                wct.javaClass.getMethod(
                    "reorder",
                    tokenClass,
                    Boolean::class.javaPrimitiveType,
                ).invoke(wct, token, toTop)
            }(task),
        ) { "WindowContainerTransaction unavailable" }
    }

    private fun applyTransaction(build: (Any, Any) -> Unit): (TaskSnapshot) -> Boolean = { task ->
        val token = task.token ?: error("task token unavailable")
        val controller = organizerController() ?: error("window organizer unavailable")
        val wct = Class.forName("android.window.WindowContainerTransaction").getConstructor().newInstance()
        build(wct, token)
        val apply = controller.javaClass.methods.first {
            it.name == "applyTransaction" && it.parameterTypes.size == 1
        }
        apply.invoke(controller, wct)
        true
    }

    private val tokenClass: Class<*> by lazy { Class.forName("android.window.WindowContainerToken") }

    private fun organizerController(): Any? {
        organizer?.let { return it }
        if (organizerUnavailable) return null
        val binder: IBinder? = runCatching {
            // The embedded runtime resolves this pseudo service inside the
            // shell process, so the controller Binder keeps shell identity.
            DistributionPrivilegeRuntime.serviceBinder(EMBEDDED_ORGANIZER_SERVICE)
                ?.takeIf { DistributionPrivilegeRuntime.available }
        }.getOrNull() ?: runCatching {
            val controller = checkNotNull(method("getWindowOrganizerController")).invoke(atm())
            val raw = controller.javaClass.getMethod("asBinder").invoke(controller) as IBinder
            ShizukuBinderWrapper(raw)
        }.onFailure { log("organizer", it) }.getOrNull()
        if (binder == null) {
            organizerUnavailable = true
            return null
        }
        return runCatching {
            Class.forName("android.window.IWindowOrganizerController\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        }.onFailure {
            organizerUnavailable = true
            log("organizer proxy", it)
        }.getOrNull()?.also { organizer = it }
    }

    /** Called when the privileged runtime changes so cached proxies are rebuilt. */
    fun reset() {
        organizer = null
        organizerUnavailable = false
    }

    private fun log(operation: String, error: Throwable) {
        Log.w(tag, "task bridge $operation failed: ${error.cause?.message ?: error.message}")
    }

    companion object {
        const val WINDOWING_MODE_UNDEFINED = 0
        const val WINDOWING_MODE_FULLSCREEN = 1
        const val WINDOWING_MODE_PINNED = 2
        const val WINDOWING_MODE_FREEFORM = 5
        const val WINDOWING_MODE_MULTI_WINDOW = 6
        const val ACTIVITY_TYPE_UNDEFINED = 0
        const val ACTIVITY_TYPE_STANDARD = 1
        const val ACTIVITY_TYPE_HOME = 2
        const val RESIZE_MODE_SYSTEM = 0
        const val EMBEDDED_ORGANIZER_SERVICE = "dextop.window_organizer"

        private fun intField(target: Any, name: String): Int = target.javaClass.getField(name).getInt(target)

        private fun booleanField(target: Any, name: String): Boolean? =
            runCatching { target.javaClass.getField(name).getBoolean(target) }.getOrNull()

        private fun objectField(target: Any, name: String): Any? =
            runCatching { target.javaClass.getField(name).get(target) }.getOrNull()
    }
}
