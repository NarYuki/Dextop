package moe.n4tsu.dextop.plasma

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Launcher callbacks shared by Kickoff and KRunner. */
internal interface LauncherHost {
    val palette: PlasmaPalette
    fun apps(): List<PlasmaApp>
    fun favorites(): List<String>
    fun recent(): List<String>
    fun isFavorite(packageName: String): Boolean
    fun setFavorite(packageName: String, favorite: Boolean)
    fun isPinned(packageName: String): Boolean
    fun setPinned(packageName: String, pinned: Boolean)
    fun launch(packageName: String)
    fun openAppInfo(packageName: String)
    fun showMenu(items: List<MenuItem>, rawX: Float, rawY: Float)
    fun dismissPopup()
    fun leave(action: LeaveAction)
    fun openDesktopSettings()
    fun copyToClipboard(text: String)
}

internal enum class LeaveAction { SLEEP, RESTART, SHUTDOWN, LOGOUT, LOCK, SHOW_DIALOG }

internal data class MenuItem(
    val text: String,
    val glyph: Glyph? = null,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val separatorBefore: Boolean = false,
    val header: Boolean = false,
    val action: () -> Unit = {},
)

/** Search matching shared by Kickoff's search field and KRunner. */
internal object PlasmaSearch {
    fun apps(host: LauncherHost, query: String): List<PlasmaApp> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return host.apps()
            .mapNotNull { app ->
                val label = app.label.lowercase()
                val score = when {
                    label == needle -> 0
                    label.startsWith(needle) -> 1
                    label.split(' ', '-', '_').any { it.startsWith(needle) } -> 2
                    label.contains(needle) -> 3
                    app.packageName.lowercase().contains(needle) -> 4
                    fuzzy(label, needle) -> 5
                    else -> null
                }
                score?.let { it to app }
            }
            .sortedWith(compareBy({ it.first }, { it.second.label.lowercase() }))
            .map { it.second }
    }

    private fun fuzzy(haystack: String, needle: String): Boolean {
        var index = 0
        needle.forEach { character ->
            index = haystack.indexOf(character, index)
            if (index < 0) return false
            index += 1
        }
        return true
    }

    /** KRunner's calculator runner: "=2*(3+4)" or a bare arithmetic expression. */
    fun calculate(query: String): String? {
        val expression = query.trim().removePrefix("=").trim()
        if (expression.isEmpty() || expression.none { it.isDigit() }) return null
        if (expression.none { it in "+-*/^%()" }) return null
        return runCatching {
            val value = Calculator(expression.replace('×', '*').replace('÷', '/').replace(',', '.')).parse()
            if (value.isNaN() || value.isInfinite()) null
            else if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString()
            else "%.10g".format(value).trimEnd('0').trimEnd('.')
        }.getOrNull()
    }

    private class Calculator(private val text: String) {
        private var position = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            check(position == text.length) { "trailing input" }
            return value
        }

        private fun skipSpaces() { while (position < text.length && text[position] == ' ') position++ }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when (text.getOrNull(position)) {
                    '+' -> { position++; value + term() }
                    '-' -> { position++; value - term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                value = when (text.getOrNull(position)) {
                    '*' -> { position++; value * power() }
                    '/' -> { position++; value / power() }
                    '%' -> { position++; value % power() }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            skipSpaces()
            if (text.getOrNull(position) == '^') {
                position++
                return Math.pow(base, power())
            }
            return base
        }

        private fun unary(): Double {
            skipSpaces()
            return when (text.getOrNull(position)) {
                '-' -> { position++; -unary() }
                '+' -> { position++; unary() }
                else -> primary()
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (text.getOrNull(position) == '(') {
                position++
                val value = expression()
                skipSpaces()
                check(text.getOrNull(position) == ')') { "missing )" }
                position++
                return value
            }
            val start = position
            while (position < text.length && (text[position].isDigit() || text[position] == '.')) position++
            check(position > start) { "number expected" }
            return text.substring(start, position).toDouble()
        }
    }
}

/**
 * Kickoff, Plasma 6's default application launcher: header with the user and
 * search, a category sidebar, favourites grid / app lists, and the
 * Applications/Places tabs with session buttons in the footer.
 */
@SuppressLint("ViewConstructor")
internal class KickoffView(
    context: Context,
    private val host: LauncherHost,
) : LinearLayout(context) {
    private val palette = host.palette
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).toInt()

    val search = PlasmaSearchField(context, palette, t("nativePlasmaSearch"))
    private val sidebar = verticalLayout(context)
    private val content = FrameLayout(context)
    private var category: String = "favorites"
    private var tab: String = "applications"
    private val results = ArrayList<PlasmaRow>()
    private var selectedResult = 0
    private val sidebarRows = HashMap<String, PlasmaRow>()

    init {
        orientation = VERTICAL
        setPadding(dp(8f), dp(8f), dp(8f), dp(6f))
        addView(header(), LayoutParams(-1, dp(52f)))
        addView(divider(), LayoutParams(-1, 1))
        val body = horizontalLayout(context).apply { gravity = Gravity.TOP }
        val sidebarScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(sidebar)
        }
        body.addView(sidebarScroll, LayoutParams(dp(196f), -1))
        body.addView(View(context).apply { setBackgroundColor(palette.separator) }, LayoutParams(1, -1))
        body.addView(content, LayoutParams(0, -1, 1f))
        addView(body, LayoutParams(-1, 0, 1f))
        addView(divider(), LayoutParams(-1, 1))
        addView(footer(), LayoutParams(-1, dp(46f)))
        buildSidebar()
        showCategory("favorites")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                if (query.isBlank()) showCategory(category) else showSearch(query)
            }
        })
        search.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    results.getOrNull(selectedResult)?.activate()
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> { moveSelection(1); true }
                KeyEvent.KEYCODE_DPAD_UP -> { moveSelection(-1); true }
                KeyEvent.KEYCODE_ESCAPE -> { host.dismissPopup(); true }
                else -> false
            }
        }
    }

    private fun divider() = View(context).apply { setBackgroundColor(palette.separator) }

    private fun header(): View {
        val row = horizontalLayout(context)
        row.setPadding(dp(6f), 0, dp(4f), dp(6f))
        val name = android.os.Build.MODEL.ifBlank { "Dextop" }
        row.addView(AvatarView(context, palette, name.first().uppercaseChar()), LayoutParams(dp(34f), dp(34f)))
        row.addView(
            plasmaLabel(context, name, palette.text, 14f, bold = true).apply { setPadding(dp(10f), 0, dp(10f), 0) },
            LayoutParams(0, -2, 1f),
        )
        row.addView(search, LayoutParams(dp(300f), dp(34f)))
        row.addView(
            PlasmaButton(context, palette, Glyph.SETTINGS, "", flat = true) { host.openDesktopSettings() },
            LayoutParams(dp(34f), dp(34f)).apply { leftMargin = dp(4f) },
        )
        return row
    }

    private fun footer(): View {
        val row = horizontalLayout(context)
        row.setPadding(0, dp(6f), 0, 0)
        listOf("applications" to t("nativePlasmaApplications"), "places" to t("nativePlasmaPlaces")).forEach { (id, label) ->
            row.addView(TabButton(context, palette, label, tab == id) {
                tab = id
                search.setText("")
                buildSidebar()
                showCategory(if (id == "places") "recent" else "favorites")
                (row.getChildAt(0) as TabButton).current = id == "applications"
                (row.getChildAt(1) as TabButton).current = id == "places"
            }, LayoutParams(-2, -1))
        }
        row.addView(View(context), LayoutParams(0, 1, 1f))
        row.addView(PlasmaButton(context, palette, Glyph.SLEEP, t("nativePlasmaSleep")) { host.leave(LeaveAction.SLEEP) }, LayoutParams(-2, -1))
        row.addView(PlasmaButton(context, palette, Glyph.RESTART, t("nativePlasmaRestart")) { host.leave(LeaveAction.RESTART) }, LayoutParams(-2, -1))
        row.addView(PlasmaButton(context, palette, Glyph.POWER, t("nativePlasmaShutDown")) { host.leave(LeaveAction.SHUTDOWN) }, LayoutParams(-2, -1))
        row.addView(PlasmaButton(context, palette, Glyph.LOGOUT, "") { host.leave(LeaveAction.SHOW_DIALOG) }, LayoutParams(dp(34f), -1))
        return row
    }

    private fun buildSidebar() {
        sidebar.removeAllViews()
        sidebarRows.clear()
        val entries: List<Triple<String, Glyph, String>> = if (tab == "places") {
            listOf(
                Triple("recent", Glyph.HISTORY, t("nativePlasmaRecentApplications")),
                Triple("computer", Glyph.COMPUTER, t("nativePlasmaComputer")),
            )
        } else {
            buildList {
                add(Triple("favorites", Glyph.STAR, t("nativePlasmaFavorites")))
                add(Triple("all", Glyph.APPS, t("nativePlasmaAllApplications")))
                val present = host.apps().map { it.category }.toSet()
                PlasmaAppRepository.categories.filter { it in present || it == ApplicationInfo.CATEGORY_UNDEFINED }
                    .forEach { add(Triple("cat:$it", categoryGlyph(it), categoryName(it))) }
            }
        }
        entries.forEach { (id, glyph, label) ->
            val row = PlasmaRow(
                context, palette, GlyphDrawable(glyph, palette.text, 1.5f), label,
                heightDp = 34f, iconDp = 18f,
                onHoverSelect = { if (search.text.isNullOrBlank()) showCategory(id) },
            ) { search.setText(""); showCategory(id) }
            sidebarRows[id] = row
            sidebar.addView(row, LayoutParams(-1, -2))
        }
        sidebarRows[category]?.highlighted = true
    }

    private fun categoryGlyph(category: Int) = when (category) {
        ApplicationInfo.CATEGORY_GAME -> Glyph.STAR
        ApplicationInfo.CATEGORY_AUDIO -> Glyph.VOLUME
        ApplicationInfo.CATEGORY_VIDEO -> Glyph.DESKTOP
        ApplicationInfo.CATEGORY_IMAGE -> Glyph.WALLPAPER
        ApplicationInfo.CATEGORY_SOCIAL -> Glyph.NOTIFICATIONS
        ApplicationInfo.CATEGORY_NEWS -> Glyph.INFO
        ApplicationInfo.CATEGORY_MAPS -> Glyph.PIN
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> Glyph.TILE
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> Glyph.SETTINGS
        else -> Glyph.APPS
    }

    private fun categoryName(category: Int) = when (category) {
        ApplicationInfo.CATEGORY_GAME -> t("nativePlasmaCategoryGames")
        ApplicationInfo.CATEGORY_AUDIO -> t("nativePlasmaCategoryMultimedia")
        ApplicationInfo.CATEGORY_VIDEO -> t("nativePlasmaCategoryVideo")
        ApplicationInfo.CATEGORY_IMAGE -> t("nativePlasmaCategoryGraphics")
        ApplicationInfo.CATEGORY_SOCIAL -> t("nativePlasmaCategoryInternet")
        ApplicationInfo.CATEGORY_NEWS -> t("nativePlasmaCategoryNews")
        ApplicationInfo.CATEGORY_MAPS -> t("nativePlasmaCategoryTravel")
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> t("nativePlasmaCategoryOffice")
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> t("nativePlasmaCategorySystem")
        else -> t("nativePlasmaCategoryLost")
    }

    private fun showCategory(id: String) {
        sidebarRows[category]?.highlighted = false
        category = id
        sidebarRows[id]?.highlighted = true
        content.removeAllViews()
        results.clear()
        when {
            id == "favorites" -> content.addView(grid(host.favorites().mapNotNull { pkg -> host.apps().firstOrNull { it.packageName == pkg } }))
            id == "all" -> content.addView(list(host.apps()))
            id == "recent" -> content.addView(list(host.recent().mapNotNull { pkg -> host.apps().firstOrNull { it.packageName == pkg } }))
            id == "computer" -> content.addView(computerPlaces())
            id.startsWith("cat:") -> {
                val value = id.removePrefix("cat:").toInt()
                content.addView(list(host.apps().filter { it.category == value }))
            }
        }
        content.alpha = 0f
        content.translationX = dp(8f).toFloat()
        content.animate().alpha(1f).translationX(0f).setDuration(PlasmaTheme.LONG_MS).setInterpolator(PlasmaTheme.outCubic).start()
    }

    private fun showSearch(query: String) {
        content.removeAllViews()
        results.clear()
        selectedResult = 0
        val column = verticalLayout(context)
        PlasmaSearch.calculate(query)?.let { answer ->
            val row = PlasmaRow(context, palette, GlyphDrawable(Glyph.TILE, palette.accent), answer,
                subtitle = t("nativePlasmaCalculatorCopy"), heightDp = 40f) {
                host.copyToClipboard(answer)
                host.dismissPopup()
            }
            results += row
            column.addView(row, LayoutParams(-1, -2))
        }
        PlasmaSearch.apps(host, query).take(40).forEach { app ->
            val row = appRow(app, subtitle = t("nativePlasmaApplication"))
            results += row
            column.addView(row, LayoutParams(-1, -2))
        }
        if (results.isEmpty()) {
            column.addView(
                plasmaLabel(context, t("nativePlasmaNoMatches"), palette.inactiveText, 13f).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(40f), 0, 0)
                },
                LayoutParams(-1, -2),
            )
        }
        results.firstOrNull()?.highlighted = true
        content.addView(ScrollView(context).apply { addView(column) })
    }

    private fun moveSelection(delta: Int) {
        if (results.isEmpty()) return
        results.getOrNull(selectedResult)?.highlighted = false
        selectedResult = (selectedResult + delta).coerceIn(0, results.lastIndex)
        results[selectedResult].highlighted = true
    }

    private fun appMenu(app: PlasmaApp, x: Float, y: Float) {
        val favorite = host.isFavorite(app.packageName)
        val pinned = host.isPinned(app.packageName)
        host.showMenu(
            listOf(
                MenuItem(app.label, header = true),
                MenuItem(t("nativePlasmaOpen"), Glyph.CHEVRON_RIGHT) { host.launch(app.packageName) },
                MenuItem(if (favorite) t("nativePlasmaRemoveFromFavorites") else t("nativePlasmaAddToFavorites"), Glyph.STAR, separatorBefore = true) {
                    host.setFavorite(app.packageName, !favorite)
                    if (category == "favorites") showCategory("favorites")
                },
                MenuItem(if (pinned) t("nativePlasmaUnpinFromTaskManager") else t("nativePlasmaPinToTaskManager"), Glyph.PIN) {
                    host.setPinned(app.packageName, !pinned)
                },
                MenuItem(t("nativePlasmaAppInfo"), Glyph.INFO, separatorBefore = true) { host.openAppInfo(app.packageName) },
            ),
            x, y,
        )
    }

    private fun appRow(app: PlasmaApp, subtitle: String? = null) = PlasmaRow(
        context, palette, app.icon, app.label, subtitle = subtitle, heightDp = 38f, iconDp = 26f,
        onRightClick = { x, y -> appMenu(app, x, y) },
    ) { host.launch(app.packageName) }

    private fun list(apps: List<PlasmaApp>): View {
        val column = verticalLayout(context)
        column.setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        if (apps.isEmpty()) {
            column.addView(plasmaLabel(context, t("nativePlasmaNothingHere"), palette.inactiveText, 13f).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(40f), 0, 0)
            }, LayoutParams(-1, -2))
        }
        var lastLetter: Char? = null
        apps.forEach { app ->
            val letter = app.label.firstOrNull()?.uppercaseChar()
            if (category == "all" && letter != lastLetter && letter != null) {
                lastLetter = letter
                column.addView(plasmaLabel(context, letter.toString(), palette.inactiveText, 11f, bold = true).apply {
                    setPadding(dp(12f), dp(8f), 0, dp(2f))
                }, LayoutParams(-1, -2))
            }
            column.addView(appRow(app), LayoutParams(-1, -2))
        }
        return ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(column)
        }
    }

    private fun grid(apps: List<PlasmaApp>): View {
        val tile = dp(104f)
        val grid = GridLayout(context)
        grid.setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        content.post {
            val columns = ((content.width - dp(8f)) / tile).coerceAtLeast(1)
            if (grid.columnCount != columns) {
                grid.columnCount = columns
                grid.requestLayout()
            }
        }
        grid.columnCount = 4
        apps.forEach { app ->
            grid.addView(
                PlasmaGridTile(context, palette, app.icon, app.label, { x, y -> appMenu(app, x, y) }) { host.launch(app.packageName) },
                GridLayout.LayoutParams().apply { width = tile; height = dp(108f) },
            )
        }
        if (apps.isEmpty()) {
            return plasmaLabel(context, t("nativePlasmaNoFavorites"), palette.inactiveText, 13f).apply {
                gravity = Gravity.CENTER
                isSingleLine = false
            }
        }
        return ScrollView(context).apply { addView(grid) }
    }

    private fun computerPlaces(): View {
        val column = verticalLayout(context)
        listOf(
            Triple(Glyph.SETTINGS, t("nativePlasmaSystemSettings"), "com.android.settings"),
            Triple(Glyph.DESKTOP, t("nativePlasmaDextopSettings"), context.packageName),
        ).forEach { (glyph, label, pkg) ->
            column.addView(PlasmaRow(context, palette, GlyphDrawable(glyph, palette.text), label, heightDp = 38f) {
                host.launch(pkg)
            }, LayoutParams(-1, -2))
        }
        return column
    }

    fun focusSearch() {
        search.requestFocus()
    }

    /** Typing while Kickoff is open starts a search even before the field has focus. */
    fun appendSearch(text: String) {
        search.requestFocus()
        search.append(text)
    }
}

@SuppressLint("ViewConstructor")
private class TabButton(
    context: Context,
    private val palette: PlasmaPalette,
    private val label: String,
    initial: Boolean,
    private val onSelect: () -> Unit,
) : View(context) {
    var current = initial
        set(value) { field = value; invalidate() }
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        paint.textSize = 13f * density
        setMeasuredDimension((paint.measureText(label) + 28 * density).toInt(), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = if (current) palette.text else palette.inactiveText
        paint.textSize = 13f * density
        canvas.drawText(label, width / 2f, height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        if (current) {
            paint.color = palette.accent
            canvas.drawRoundRect(10 * density, 1f, width - 10 * density, 3 * density, density, density, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP) onSelect()
        return true
    }
}

@SuppressLint("ViewConstructor")
internal class AvatarView(context: Context, private val palette: PlasmaPalette, private val letter: Char) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sansMedium }

    override fun onDraw(canvas: Canvas) {
        paint.color = palette.accent
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
        paint.color = palette.accentText
        paint.textSize = height * .45f
        canvas.drawText(letter.toString(), width / 2f, height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
    }
}

/** KRunner: the Alt+Space search bar anchored to the top of the screen. */
@SuppressLint("ViewConstructor")
internal class KRunnerView(context: Context, private val host: LauncherHost) : LinearLayout(context) {
    private val palette = host.palette
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).toInt()
    val search = PlasmaSearchField(context, palette, t("nativePlasmaKrunnerHint"))
    private val resultColumn = verticalLayout(context)
    private val rows = ArrayList<PlasmaRow>()
    private var selected = 0
    var onResultsChanged: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        addView(search, LayoutParams(-1, dp(38f)))
        addView(resultColumn, LayoutParams(-1, -2))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = rebuild(s?.toString().orEmpty())
        })
        search.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { rows.getOrNull(selected)?.activate(); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { select(selected + 1); true }
                KeyEvent.KEYCODE_DPAD_UP -> { select(selected - 1); true }
                KeyEvent.KEYCODE_ESCAPE -> { host.dismissPopup(); true }
                else -> false
            }
        }
    }

    private fun select(index: Int) {
        if (rows.isEmpty()) return
        rows.getOrNull(selected)?.highlighted = false
        selected = index.coerceIn(0, rows.lastIndex)
        rows[selected].highlighted = true
    }

    private fun rebuild(query: String) {
        resultColumn.removeAllViews()
        rows.clear()
        selected = 0
        if (query.isNotBlank()) {
            PlasmaSearch.calculate(query)?.let { answer ->
                rows += PlasmaRow(context, palette, GlyphDrawable(Glyph.TILE, palette.accent), answer,
                    subtitle = t("nativePlasmaCalculatorCopy"), heightDp = 40f) {
                    host.copyToClipboard(answer)
                    host.dismissPopup()
                }
            }
            PlasmaSearch.apps(host, query).take(8).forEach { app ->
                rows += PlasmaRow(context, palette, app.icon, app.label, subtitle = t("nativePlasmaApplication"), heightDp = 38f, iconDp = 26f) {
                    host.launch(app.packageName)
                }
            }
        }
        rows.forEach { resultColumn.addView(it, LayoutParams(-1, -2)) }
        rows.firstOrNull()?.highlighted = true
        onResultsChanged?.invoke()
    }

    fun desiredHeight(): Int = dp(54f) + rows.sumOf { dp(if (it.subtitle != null) 50f else 38f) }
}

/** Popup menu used for context menus and the window operations menu. */
@SuppressLint("ViewConstructor")
internal class PlasmaMenuView(
    context: Context,
    palette: PlasmaPalette,
    items: List<MenuItem>,
    dismiss: () -> Unit,
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
        items.forEach { item ->
            if (item.separatorBefore) {
                addView(View(context).apply { setBackgroundColor(palette.separator) }, LayoutParams(-1, 1).apply {
                    topMargin = (4 * density).toInt(); bottomMargin = (4 * density).toInt()
                })
            }
            if (item.header) {
                addView(plasmaLabel(context, item.text, palette.inactiveText, 12f, bold = true).apply {
                    setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                }, LayoutParams(-1, -2))
            } else {
                addView(PlasmaRow(
                    context, palette,
                    item.glyph?.let { GlyphDrawable(it, palette.text, 1.5f) },
                    item.text, heightDp = 32f, iconDp = 16f, checked = item.checked,
                ) {
                    dismiss()
                    item.action()
                }.apply { enabledRow = item.enabled }, LayoutParams(-1, -2))
            }
        }
    }

    fun desiredSize(): Pair<Int, Int> {
        measure(
            View.MeasureSpec.makeMeasureSpec((260 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return (260 * density).toInt() to measuredHeight
    }
}

internal fun ViewGroup.clearAndAdd(view: View) {
    removeAllViews()
    addView(view)
}
