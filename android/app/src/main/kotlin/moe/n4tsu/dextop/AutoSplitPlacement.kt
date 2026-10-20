package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Rect
import moe.n4tsu.dextop.plasma.TaskBridge
import moe.n4tsu.dextop.plasma.TaskSnapshot
import org.json.JSONArray
import org.json.JSONObject

/** One shared transaction for phone and Auto; neither UI owns the task. */
internal object AutoSplitPlacement {
    private var task: TaskSnapshot? = null
    private var displayId = -1
    private var area = Rect()
    private var bridge: TaskBridge? = null
    private var context: Context? = null
    private var side = ""
    private var page = 0
    private var revision = 0
    private var error = ""
    private var apps = emptyList<Map<String, Any>>()
    private const val PAGE_SIZE = 6
    private val icons = mutableMapOf<String, String>()

    private fun icon(packageName: String): String = icons.getOrPut(packageName) {
        runCatching {
            val drawable = checkNotNull(context).packageManager.getApplicationIcon(packageName)
            val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 48, 48)
            drawable.draw(android.graphics.Canvas(bitmap))
            val output = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        }.getOrDefault("")
    }

    @Synchronized fun begin(context: Context, bridge: TaskBridge, task: TaskSnapshot, displayId: Int, area: Rect) {
        if (this.task != null) return
        // Remove the selected leaf from phone split-screen, not its split root.
        check(bridge.extractToDisplay(task, displayId)) { "Unable to return split task=${task.taskId}" }
        check(bridge.sendToBack(task)) { "Unable to hide split task=${task.taskId}" }
        this.context = context.applicationContext
        this.bridge = bridge
        this.task = task
        this.displayId = displayId
        this.area = Rect(area)
        side = ""; page = 0; error = ""; revision++
        icons.clear()
        apps = AppCatalog(context).launchableApps(false).filter { it["package"] != task.packageName }
    }

    @Synchronized fun clear() { task = null; bridge = null; context = null; icons.clear(); revision++ }

    @Synchronized fun state(): String {
        val task = task ?: return ""
        return JSONObject().put("revision", revision).put("label", task.label ?: task.packageName)
            .put("side", side).put("page", page).put("pages", ((apps.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1))
            .put("error", error).put("apps", JSONArray().apply {
                apps.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEach { app ->
                    put(JSONObject(app).put("icon", icon(app["package"].toString())))
                }
            }).toString()
    }

    @Synchronized fun action(value: String) {
        val task = task ?: return
        val bridge = bridge ?: return
        runCatching {
            when {
                value == "left" || value == "right" -> {
                    val bounds = if (value == "left") Rect(area.left, area.top, area.centerX(), area.bottom)
                        else Rect(area.centerX(), area.top, area.right, area.bottom)
                    check(bridge.setBounds(task, bounds) && bridge.focus(task))
                    side = value
                }
                value == "next" -> page = (page + 1).coerceAtMost(((apps.size - 1) / PAGE_SIZE).coerceAtLeast(0))
                value == "previous" -> page = (page - 1).coerceAtLeast(0)
                value == "cancel" -> {
                    check(bridge.setBounds(task, area) && bridge.focus(task))
                    clear()
                }
                value.startsWith("app:") && side.isNotEmpty() -> {
                    val pkg = value.removePrefix("app:")
                    require(apps.any { it["package"] == pkg })
                    val left = Rect(area.left, area.top, area.centerX(), area.bottom)
                    val right = Rect(area.centerX(), area.top, area.right, area.bottom)
                    val selected = if (side == "left") left else right
                    val opposite = if (side == "left") right else left
                    check(bridge.setBounds(task, selected) && bridge.focus(task))
                    AppCatalog(checkNotNull(context)).launch(pkg, displayId, opposite)
                    val destination = displayId
                    val placementContext = checkNotNull(context)
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    var attempts = 0
                    val placeOther = object : Runnable {
                        override fun run() {
                            if (!MirrorService.isAutoOnlySessionActive() || MirrorService.activeDisplayId() != destination) return
                            val other = bridge.tasks(destination).firstOrNull { it.packageName == pkg }
                            if (other != null && bridge.setWindowingMode(other, 5) && bridge.setBounds(other, opposite)) {
                                OperationLog.i(placementContext, "CarCompanion", "split other app placed package=$pkg display=$destination bounds=$opposite")
                            } else if (++attempts < 10) handler.postDelayed(this, 150L)
                        }
                    }
                    handler.postDelayed(placeOther, 150L)
                    clear()
                }
            }
        }.onFailure { error = it.message ?: "Unable to place app" }
        revision++
    }
}
