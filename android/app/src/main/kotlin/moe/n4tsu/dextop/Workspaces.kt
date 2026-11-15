package moe.n4tsu.dextop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.animation.ValueAnimator
import org.json.JSONArray
import org.json.JSONObject

/** What [Workspaces] needs from the session beyond the shared interfaces. */
internal interface WorkspacesHost : DesktopSession, OverlayMenu {
    fun currentDisplayTasks(): Map<String, Rect>
    val launchedAppBounds: MutableMap<String, Rect>
    fun menuUsesLandscapeLayout(): Boolean
    fun packageName(): String
    fun placePackageTaskThroughBinder(displayId: Int, packageName: String, wanted: Rect): Boolean
    val recentsExecutor: java.util.concurrent.ExecutorService
    fun usesDecoratedDirectAutoDisplay(): Boolean
}

/** Saved sets of apps: capturing the open tasks, listing them in the menu and relaunching them in place. */
internal class Workspaces(
    private val context: Context,
    private val desk: WorkspacesHost,
) {
    val workspaceLayoutEngine by lazy {
        WorkspaceLayoutEngine(context, desk.desktopEnvironment, desk.logTag)
    }

    var workspaceExpanded = false

    var pendingPausedWorkspace: JSONObject? = null

    private var workspaceSaveError: String? = null

    fun toggleWorkspacePanel() {
        val container = desk.menu ?: return
        if (desk.targetWidth < desk.targetHeight) {
            showWorkspaceMenu(desk.menuPrimary ?: return)
            return
        }
        workspaceExpanded = !workspaceExpanded
        (desk.menuPrimary?.findViewWithTag<View>("workspace_toggle") as? TextView)?.text =
            if (workspaceExpanded) "‹" else "›"
        val startWidth = container.width.coerceAtLeast(desk.dp(340))
        val endWidth = if (desk.menuUsesLandscapeLayout() && workspaceExpanded) desk.dp(680) else desk.dp(340)
        if (workspaceExpanded) {
            val workspacePanel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(desk.dp(14), 0, desk.dp(14), desk.dp(14))
                tag = "workspace_panel"
                setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) = desk.scheduleMenuHeightUpdate()
                    override fun onChildViewRemoved(parent: View?, child: View?) = desk.scheduleMenuHeightUpdate()
                })
            }
            workspacePanel.addView(desk.menuTitle(NativeStrings.text("nativeWorkSpace")))
            workspacePanel.addView(desk.actionButton(R.drawable.ic_add, NativeStrings.text("nativeAddCurrentAppPlacement")) {
                if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeSaveTheCurrentAppArrangementAsA"))
                else {
                    workspaceSaveError = saveCurrentWorkspace()
                    rebuildWorkspacePanel(workspacePanel)
                }
            })
            rebuildWorkspacePanel(workspacePanel)
            container.addView(
                ScrollView(context).apply {
                    tag = "workspace_scroll"
                    isFillViewport = true
                    addView(workspacePanel, FrameLayout.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(
                    if (desk.menuUsesLandscapeLayout()) desk.dp(340) else 0, -1,
                    if (desk.menuUsesLandscapeLayout()) 0f else 1f
                )
            )
        } else {
            container.findViewWithTag<View>("workspace_scroll")?.apply {
                isClickable = false
                postDelayed({ container.removeView(this) }, 240)
            }
        }
        if (desk.menuUsesLandscapeLayout()) {
            ValueAnimator.ofInt(startWidth, endWidth).apply {
                duration = 240
                addUpdateListener { animator ->
                    container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
                        width = animator.animatedValue as Int
                    }
                }
                start()
            }
        }
        desk.scheduleMenuHeightUpdate()
    }

    fun showWorkspaceMenu(panel: LinearLayout) {
        desk.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(desk.menuTitle(NativeStrings.text("nativeWorkSpace"), NativeStrings.text("nativeReturn")) {
            workspaceExpanded = false
            desk.showMainMenu(panel)
        })
        panel.addView(desk.actionButton(R.drawable.ic_add, NativeStrings.text("nativeAddCurrentAppPlacement")) {
            if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeSaveTheCurrentAppArrangementAsA"))
            else {
                workspaceSaveError = saveCurrentWorkspace()
                rebuildWorkspacePanel(panel)
            }
        })
        rebuildWorkspacePanel(panel)
    }

    private fun rebuildWorkspacePanel(panel: LinearLayout) {
        while (panel.childCount > 2) panel.removeViewAt(2)
        val items = workspaceJson()
        if (items.length() == 0) {
            panel.addView(desk.sectionLabel(NativeStrings.text("nativeNoSavedWorkspaces")))
            workspaceSaveError?.let { reason ->
                panel.addView(TextView(context).apply {
                    text = reason
                    textSize = 12f
                    setTextColor(Color.rgb(255, 180, 171))
                    setPadding(desk.dp(8), desk.dp(6), desk.dp(8), desk.dp(10))
                })
            }
            return
        }
        for (index in 0 until items.length()) {
            val workspace = items.optJSONObject(index) ?: continue
            panel.addView(workspaceButton(workspace))
        }
        workspaceSaveError?.let { reason ->
            panel.addView(TextView(context).apply {
                text = reason
                textSize = 12f
                setTextColor(Color.rgb(255, 180, 171))
                setPadding(desk.dp(8), desk.dp(6), desk.dp(8), desk.dp(10))
            })
        }
    }

    private fun workspaceButton(workspace: JSONObject): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(desk.dp(14), 0, desk.dp(14), 0)
        background = GradientDrawable().apply {
            setColor(Color.rgb(50, 47, 55))
            cornerRadius = desk.dp(16).toFloat()
        }
        val name = workspace.optString("name", NativeStrings.text("nativeWorkSpace"))
        addView(TextView(context).apply {
            text = name
            textSize = 15f
            setTextColor(Color.rgb(230, 225, 229))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setHorizontallyScrolling(true)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, desk.dp(50), 1f).apply { rightMargin = desk.dp(8) })
        val apps = workspace.optJSONArray("apps") ?: JSONArray()
        val icons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
        }
        for (index in 0 until minOf(apps.length(), 4)) {
            val packageName = apps.optString(index)
            val icon = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            icons.addView(ImageView(context).apply {
                setImageDrawable(icon)
                contentDescription = packageName
            }, LinearLayout.LayoutParams(desk.dp(28), desk.dp(28)).apply {
                if (index > 0) leftMargin = desk.dp(4)
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            })
        }
        // The icon column itself is anchored to the right. Icons inside that
        // fixed four-slot column always start from its left edge.
        addView(icons, LinearLayout.LayoutParams(desk.dp(4 * 28 + 3 * 4), desk.dp(50)))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeOpenDextopWithYourSavedAppPlacement"))
            else launchOverlayWorkspace(workspace)
        }
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, desk.dp(50)).apply { bottomMargin = desk.dp(8) } }

    fun saveCurrentWorkspace(): String? {
        val captured = captureCurrentWorkspace()
            ?: return NativeStrings.text("nativeFailedToSaveUnableToRetrieveRunning")
        val all = workspaceJson()
        all.put(captured.apply {
            put("id", System.currentTimeMillis().toString())
            put("name", "${NativeStrings.text("nativeWorkSpace")} ${all.length() + 1}")
        })
        val saved = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.workspaces", all.toString()).commit()
        return if (saved) null else NativeStrings.text("nativeFailedToSaveFailedToWriteTo")
    }

    fun captureCurrentWorkspace(): JSONObject? {
        val apps = JSONArray()
        val bounds = JSONObject()
        val currentTasks = desk.currentDisplayTasks()
        currentTasks.forEach { (packageName, rect) ->
            if (!isWorkspaceApp(packageName)) return@forEach
            apps.put(packageName)
            bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
        }
        if (apps.length() == 0) {
            desk.accessibilityService.windows.orEmpty().forEach { window ->
                if (Build.VERSION.SDK_INT >= 30 && window.displayId != desk.targetDisplayId) return@forEach
                val packageName = window.root?.packageName?.toString() ?: return@forEach
                if (!isWorkspaceApp(packageName)) return@forEach
                val rect = Rect()
                window.getBoundsInScreen(rect)
                if (rect.width() < desk.dp(80) || rect.height() < desk.dp(80) || bounds.has(packageName)) return@forEach
                apps.put(packageName)
                bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
        }
        if (apps.length() == 0) {
            desk.launchedAppBounds.forEach { (packageName, rect) ->
                if (!isWorkspaceApp(packageName)) return@forEach
                apps.put(packageName)
                bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
        }
        if (apps.length() == 0) {
            Log.w(desk.logTag, "no app windows available to save")
            return null
        }
        return JSONObject().apply {
            put("apps", apps)
            put("positions", JSONObject())
            put("bounds", bounds)
            put("layout", "captured")
        }
    }

    fun isWorkspaceApp(candidate: String): Boolean {
        if (candidate.isBlank() || candidate == desk.packageName() || candidate == "com.android.systemui") return false
        val launchIntent = context.packageManager.getLaunchIntentForPackage(candidate) ?: return false
        val activity = launchIntent.resolveActivity(context.packageManager) ?: return false
        val info = runCatching {
            context.packageManager.getApplicationInfo(candidate, 0)
        }.getOrNull() ?: return false
        if (!info.enabled) return false

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePackages = context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence().map { it.activityInfo.packageName }.toSet()
        return candidate !in homePackages && activity.packageName == candidate
    }

    fun launchOverlayWorkspace(workspace: JSONObject, closeMenu: Boolean = true) {
        val apps = workspace.optJSONArray("apps") ?: return
        val positions = workspace.optJSONObject("positions") ?: JSONObject()
        val bounds = workspace.optJSONObject("bounds") ?: JSONObject()
        for (index in 0 until apps.length()) {
            val packageName = apps.optString(index)
            val rawBounds = bounds.optJSONArray(packageName)
            val position = positions.optString(packageName).takeIf { it.isNotEmpty() }
            android.os.Handler(context.mainLooper).postDelayed({
                val requestedBounds = when {
                    rawBounds != null && rawBounds.length() == 4 -> workspaceLayoutEngine.fit(
                        desk.targetDisplayId,
                        desk.targetWidth,
                        desk.targetHeight,
                        Rect(rawBounds.optInt(0), rawBounds.optInt(1), rawBounds.optInt(2), rawBounds.optInt(3)),
                    )
                    position != null -> workspaceLayoutEngine.position(
                        desk.targetDisplayId, desk.targetWidth, desk.targetHeight, position,
                    )
                    else -> null
                }
                MirrorService.launchPackage(packageName, requestedBounds)
                if (desk.usesDecoratedDirectAutoDisplay() && requestedBounds != null) {
                    scheduleDecoratedWorkspacePlacement(packageName, requestedBounds)
                }
            }, index * 350L)
        }
        if (closeMenu) desk.toggleMenu()
    }

    private fun scheduleDecoratedWorkspacePlacement(packageName: String, wanted: Rect) {
        val displayId = desk.targetDisplayId
        desk.recentsExecutor.execute {
            val deadline = SystemClock.uptimeMillis() + 1_500L
            while (MirrorService.active && desk.targetDisplayId == displayId && desk.usesDecoratedDirectAutoDisplay()) {
                if (desk.placePackageTaskThroughBinder(displayId, packageName, wanted)) return@execute
                if (SystemClock.uptimeMillis() >= deadline) {
                    OperationLog.w(
                        context,
                        "Workspace",
                        "task did not appear for placement package=$packageName display=$displayId wanted=$wanted",
                        null,
                    )
                    return@execute
                }
                try {
                    Thread.sleep(75L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
            }
        }
    }

    fun workspaceJson(): JSONArray = runCatching {
        JSONArray(
            context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getString("flutter.workspaces", "[]")
        )
    }.getOrDefault(JSONArray())
}
