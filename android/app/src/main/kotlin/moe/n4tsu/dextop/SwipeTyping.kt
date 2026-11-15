package moe.n4tsu.dextop

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.Locale
import moe.n4tsu.dextop.input.LaptopSwipeDecoder
import moe.n4tsu.dextop.input.SwipeObservingKeyboardLayout
import moe.n4tsu.dextop.input.LaptopSwipeImeCoordinator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray

/** What [SwipeTyping] needs from the session beyond the shared interfaces. */
internal interface SwipeTypingHost : DesktopSession, OverlayMenu {
    val haptics: Haptics
    fun injectLaptopKeyboardEvent(keyCode: Int, action: Int, metaState: Int, repeatCount: Int): Boolean
    val laptopKeyboardReady: Boolean
    val laptopKeyboardView: SwipeObservingKeyboardLayout?
    val laptopLegendButtons: MutableList<Pair<LaptopKeyTextView, String>>
    fun laptopPalette(): LaptopPalette
    val laptopTypeface: Typeface
    fun opacityColor(color: Int, opacity: Float): Int
    fun opaqueColor(color: Int): Int
}

internal data class LaptopSwipeAutoCommit(
    val start: Int,
    val value: String,
    val resultingText: String,
)

/** Swipe typing on the on-screen keyboard: decoding traces, showing candidates and committing the chosen word. */
internal class SwipeTyping(
    private val context: Context,
    private val desk: SwipeTypingHost,
) {
    internal var laptopLastEditorBounds: Rect? = null
    internal var laptopLastEditorBoundsAt = 0L

    internal var laptopCandidateBar: LinearLayout? = null

    private var laptopFloatingCandidates: View? = null

    // AccessibilityService is constructed before ContextWrapper receives its
    // base context. Creating the decoder here eagerly makes its resource lookup
    // call getApplicationContext() on a null base and crashes service startup.
    // No dictionary is needed until the first completed swipe, by which point
    // onServiceConnected has run and the service context is valid.
    private val laptopSwipeDecoder by lazy(LazyThreadSafetyMode.NONE) {
        LaptopSwipeDecoder(context.applicationContext)
    }

    internal val laptopSwipeExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-laptop-swipe").apply { isDaemon = true }
    }

    internal val laptopSwipeHandler = Handler(Looper.getMainLooper())

    internal var laptopSwipeGeneration = 0L

    internal val laptopSwipeImeCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        LaptopSwipeImeCoordinator(context.applicationContext, desk.privilegedAccess)
    }

    private var laptopSwipeAutoCommit: LaptopSwipeAutoCommit? = null

    internal fun laptopSwipeLanguageChoices(): List<Pair<LaptopSwipeDecoder.Language, String>> {
        val all = listOf(
            LaptopSwipeDecoder.Language.EN to localText("uiLanguageEnglish"),
            LaptopSwipeDecoder.Language.FR to localText("uiLanguageFrench"),
            LaptopSwipeDecoder.Language.DE to localText("uiLanguageGerman"),
            LaptopSwipeDecoder.Language.ES to localText("uiLanguageSpanish"),
            LaptopSwipeDecoder.Language.IT to localText("uiLanguageItalian"),
            LaptopSwipeDecoder.Language.PT to localText("uiLanguagePortuguese"),
            LaptopSwipeDecoder.Language.RU to localText("uiLanguageRussian"),
            LaptopSwipeDecoder.Language.UK to localText("uiLanguageUkrainian"),
            LaptopSwipeDecoder.Language.KO to localText("uiLanguageKorean"),
            LaptopSwipeDecoder.Language.JA to localText("uiLanguageJapanese"),
            LaptopSwipeDecoder.Language.ZH_PINYIN to localText("uiLanguagePinyin"),
        )
        val raw = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.laptop_swipe_languages_json", null)
        val enabled = runCatching {
            if (raw == null) setOf("en") else buildSet {
                val array = JSONArray(raw)
                for (index in 0 until array.length()) add(array.optString(index))
                add("en")
            }
        }.getOrDefault(setOf("en"))
        return all.filter { it.first.id in enabled }
    }

    internal fun laptopSwipeLanguage(): LaptopSwipeDecoder.Language =
        LaptopSwipeDecoder.Language.parse(
            context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getString("flutter.laptop_swipe_language", "en")
        )

    internal fun isLaptopSwipeEnabled(): Boolean =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getBoolean("flutter.laptop_swipe_enabled", false)

    private fun areLaptopSwipeCandidatesEnabled(): Boolean =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getBoolean("flutter.laptop_swipe_candidates_enabled", false)

    internal fun decodeLaptopSwipe(
        points: List<PointF>,
        capitalizeFirst: Boolean,
        uppercaseAll: Boolean,
    ) {
        if (!isLaptopSwipeEnabled()) return
        val language = laptopSwipeLanguage()
        val keys = laptopSwipeKeys(language)
        if (keys.isEmpty()) return
        val previousWord = laptopSwipePreviousWord()
        val generation = ++laptopSwipeGeneration
        laptopSwipeExecutor.execute {
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val candidates = laptopSwipeDecoder.decode(points, keys, language, previousWord)
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
            OperationLog.i(
                context,
                "LaptopSwipe",
                "decoded language=${language.id} points=${points.size} " +
                        "candidates=${candidates.size} elapsedMs=${"%.2f".format(Locale.US, elapsedMs)}",
            )
            laptopSwipeHandler.post {
                if (generation == laptopSwipeGeneration && desk.laptopModeActive) {
                    showLaptopSwipeCandidates(
                        candidates,
                        language,
                        capitalizeFirst,
                        uppercaseAll,
                    )
                }
            }
        }
    }

    private fun laptopSwipePreviousWord(): String? {
        val focused = focusedLaptopEditor() ?: return null
        val text = focused.text?.toString().orEmpty()
        val cursor = focused.textSelectionStart.takeIf { it >= 0 }
            ?.coerceIn(0, text.length) ?: text.length
        focused.recycle()
        return text.substring(0, cursor).trimEnd()
            .takeLastWhile { it.isLetter() || it == '\'' || it == '’' }
            .takeIf { it.isNotEmpty() }
    }

    internal fun prewarmLaptopSwipeDecoder() {
        val language = laptopSwipeLanguage()
        val keys = laptopSwipeKeys(language)
        if (keys.isEmpty() || laptopSwipeExecutor.isShutdown) return
        laptopSwipeExecutor.execute { laptopSwipeDecoder.prewarm(keys, language) }
    }

    private fun laptopSwipeKeys(language: LaptopSwipeDecoder.Language): List<LaptopSwipeDecoder.Key> {
        val keyboard = desk.laptopKeyboardView ?: return emptyList()
        if (keyboard.width <= 0 || keyboard.height <= 0) return emptyList()
        val keyboardLocation = IntArray(2).also(keyboard::getLocationOnScreen)
        return desk.laptopLegendButtons.mapNotNull { (button, _) ->
            val keyCode = button.tag as? Int ?: return@mapNotNull null
            val symbol = laptopSwipeSymbol(keyCode, language) ?: return@mapNotNull null
            val location = IntArray(2).also(button::getLocationOnScreen)
            LaptopSwipeDecoder.Key(
                symbol,
                PointF(
                    location[0] - keyboardLocation[0] + button.width / 2f,
                    location[1] - keyboardLocation[1] + button.height / 2f,
                )
            )
        }
    }

    internal fun laptopSwipeSymbol(
        keyCode: Int,
        language: LaptopSwipeDecoder.Language,
    ): Char? = LaptopKeyLegends.swipeSymbol(keyCode, language)

    private fun showLaptopSwipeCandidates(
        candidates: List<LaptopSwipeDecoder.Candidate>,
        language: LaptopSwipeDecoder.Language,
        capitalizeFirst: Boolean,
        uppercaseAll: Boolean,
    ) {
        val contextualCandidates = contextualizeLaptopSwipeCandidates(
            candidates,
            language,
            capitalizeFirst,
            uppercaseAll,
        )
        val bar = laptopCandidateBar ?: return
        bar.removeAllViews()
        (bar.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = 0
            bar.layoutParams = params
        }
        if (contextualCandidates.isEmpty()) {
            bar.visibility = View.GONE
            return
        }
        // English swipe behaves like a conventional phone keyboard: commit
        // the best result immediately. The row remains available so choosing
        // another result replaces that exact auto-committed word.
        laptopSwipeAutoCommit = null
        if (language == LaptopSwipeDecoder.Language.EN) {
            commitLaptopSwipeCandidate(
                contextualCandidates.first().value,
                rememberAutoCommit = true,
            )
        }
        if (!areLaptopSwipeCandidatesEnabled()) {
            if (language != LaptopSwipeDecoder.Language.EN) {
                commitLaptopSwipeCandidate(contextualCandidates.first().value)
            }
            bar.visibility = View.GONE
            return
        }
        // Candidates belong to the editor, not to the keyboard. If Android has
        // not exposed an editable focus yet, wait for the next gesture instead
        // of jumping the row back to the top of the laptop keyboard.
        if (!showFloatingLaptopSwipeCandidates(contextualCandidates) &&
            language != LaptopSwipeDecoder.Language.EN
        ) {
            // Some OEM editors do not expose usable bounds. Preserve swipe
            // input itself by committing the best candidate instead of doing
            // nothing or drawing a detached strip above the keyboard.
            commitLaptopSwipeCandidate(contextualCandidates.first().value)
        }
        bar.visibility = View.GONE
    }

    private fun contextualizeLaptopSwipeCandidates(
        candidates: List<LaptopSwipeDecoder.Candidate>,
        language: LaptopSwipeDecoder.Language,
        capitalizeFirst: Boolean,
        uppercaseAll: Boolean,
    ): List<LaptopSwipeDecoder.Candidate> {
        if (language != LaptopSwipeDecoder.Language.EN || candidates.isEmpty()) return candidates
        return candidates.map { candidate ->
            val lower = candidate.value.lowercase(Locale.ENGLISH)
            val canonical = when (lower.replace("’", "'")) {
                "i" -> "I"
                "id", "i'd" -> "I'd"
                "ill", "i'll" -> "I'll"
                "im", "i'm" -> "I'm"
                "ive", "i've" -> "I've"
                else -> lower
            }
            val value = when {
                uppercaseAll -> canonical.uppercase(Locale.ENGLISH)
                capitalizeFirst -> canonical.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
                else -> canonical
            }
            candidate.copy(value = value)
        }
    }

    private fun laptopSwipeCandidateView(value: String, palette: LaptopPalette): TextView =
        TextView(context).apply {
            text = value
            gravity = Gravity.CENTER
            typeface = desk.laptopTypeface
            textSize = 14f
            maxLines = 1
            setPadding(desk.dp(12), 0, desk.dp(12), 0)
            setTextColor(palette.text)
            background = GradientDrawable().apply {
                setColor(desk.opacityColor(palette.key, palette.keyOpacity))
                setStroke(desk.dp(1), palette.border)
                cornerRadius = desk.dp(12).toFloat()
            }
            setOnClickListener {
                desk.haptics.performLaptopHaptic(this, strong = true)
                laptopSwipeDecoder.learn(laptopSwipeLanguage(), value)
                if (!replaceLaptopSwipeAutoCommit(value)) {
                    commitLaptopSwipeCandidate(value)
                }
                hideLaptopSwipeCandidates()
            }
        }

    /** Shows PC-style candidates immediately below the active editable field. */
    private fun showFloatingLaptopSwipeCandidates(
        candidates: List<LaptopSwipeDecoder.Candidate>,
    ): Boolean {
        val host = desk.root ?: return false
        if (host.width <= 0 || host.height <= 0) return false
        laptopFloatingCandidates?.let(host::removeView)
        laptopFloatingCandidates = null
        val focused = focusedLaptopEditor()
        val bounds = if (focused != null) {
            Rect().also(focused::getBoundsInScreen).also {
                laptopLastEditorBounds = Rect(it)
                laptopLastEditorBoundsAt = SystemClock.uptimeMillis()
                focused.recycle()
            }
        } else {
            laptopLastEditorBounds?.takeIf {
                SystemClock.uptimeMillis() - laptopLastEditorBoundsAt < 15_000L
            }?.let(::Rect) ?: return false
        }
        if (bounds.isEmpty) return false
        val palette = desk.laptopPalette()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(desk.dp(3), desk.dp(3), desk.dp(3), desk.dp(3))
            background = GradientDrawable().apply {
                setColor(desk.opaqueColor(palette.background))
                setStroke(desk.dp(1), palette.border)
                cornerRadius = desk.dp(14).toFloat()
            }
        }
        candidates.forEach { candidate ->
            row.addView(
                laptopSwipeCandidateView(candidate.value, palette),
                LinearLayout.LayoutParams(0, -1, 1f).apply {
                    leftMargin = desk.dp(2); rightMargin = desk.dp(2)
                }
            )
        }
        val width = min(host.width - desk.dp(16), max(desk.dp(260), candidates.size * desk.dp(112)))
        val height = desk.dp(48)
        val hostLocation = IntArray(2).also(host::getLocationOnScreen)
        val localLeft = bounds.left - hostLocation[0]
        val localBottom = bounds.bottom - hostLocation[1]
        val x = localLeft.coerceIn(desk.dp(8), max(desk.dp(8), host.width - width - desk.dp(8)))
        val below = localBottom + desk.dp(6)
        // Prefer the requested editor-adjacent position.  Editors close to the
        // bottom edge used to lose the candidate row entirely; place it above
        // the field when there is no room below.
        val y = if (below + height <= host.height - desk.dp(8)) {
            below.coerceAtLeast(desk.dp(8))
        } else {
            (bounds.top - hostLocation[1] - height - desk.dp(6)).coerceAtLeast(desk.dp(8))
        }
        host.addView(row, FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.START).apply {
            leftMargin = x
            topMargin = y
        })
        row.bringToFront()
        row.alpha = 0f
        row.scaleX = .96f
        row.scaleY = .96f
        row.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(120L).start()
        laptopFloatingCandidates = row
        return true
    }

    internal fun hideLaptopSwipeCandidates() {
        laptopSwipeGeneration += 1
        laptopSwipeAutoCommit = null
        laptopFloatingCandidates?.let { floating ->
            (floating.parent as? FrameLayout)?.removeView(floating)
        }
        laptopFloatingCandidates = null
        laptopCandidateBar?.apply {
            removeAllViews()
            visibility = View.GONE
            (layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.height = 0
                layoutParams = params
            }
        }
    }

    /**
     * Commits directly into the editor hosted by the Dextop display.
     *
     * A global `ime set` binds Samsung/AOSP IMEs to display 0 on a number of
     * builds even when the focused editor lives on the virtual display.  Apart
     * from being slow, that made the result device dependent.  Accessibility
     * gives us the actual display-local editor and keeps the user's IME intact.
     */

    private fun commitLaptopSwipeCandidate(
        value: String,
        rememberAutoCommit: Boolean = false,
    ) {
        if (value.isEmpty()) return
        commitLaptopSwipeThroughIme(value, rememberAutoCommit)
    }

    private fun focusedLaptopEditor(): AccessibilityNodeInfo? {
        val preferred = desk.accessibilityService.windows.asSequence()
            .filter { desk.targetDisplayId < 0 || it.displayId == desk.targetDisplayId }
            .mapNotNull { it.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }
            .firstOrNull { it.isEditable && !it.isPassword }
        if (preferred != null) return preferred
        return desk.accessibilityService.windows.asSequence()
            .mapNotNull { it.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }
            .firstOrNull { it.isEditable && !it.isPassword }
    }

    private fun commitLaptopSwipeToFocusedEditor(
        value: String,
        rememberAutoCommit: Boolean,
    ): Boolean {
        val focused = focusedLaptopEditor() ?: return false
        val original = focused.text?.toString().orEmpty()
        val start = focused.textSelectionStart.takeIf { it >= 0 } ?: original.length
        val end = focused.textSelectionEnd.takeIf { it >= 0 } ?: start
        val from = min(start, end).coerceIn(0, original.length)
        val to = max(start, end).coerceIn(from, original.length)
        val separator = if (from > 0 && !original[from - 1].isWhitespace()) " " else ""
        val trailing = if (
            shouldAppendLaptopSwipeSpace() &&
            (to >= original.length || !original[to].isWhitespace())
        ) " " else ""
        val inserted = separator + value + trailing
        val updated = original.replaceRange(from, to, inserted)
        val committed = focused.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
            }
        )
        if (committed) {
            val cursor = from + inserted.length
            focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
                }
            )
            if (rememberAutoCommit) {
                laptopSwipeAutoCommit = LaptopSwipeAutoCommit(from + separator.length, value, updated)
            }
        }
        focused.recycle()
        return committed
    }

    private fun commitLaptopSwipeThroughIme(
        value: String,
        rememberAutoCommit: Boolean,
    ) {
        val fallback = {
            if (!commitLaptopSwipeToFocusedEditor(value, rememberAutoCommit)) {
                commitLaptopSwipeCandidateFallback(value, rememberAutoCommit)
            }
        }
        laptopSwipeImeCoordinator.commit(
            value = value,
            rememberAutoCommit = rememberAutoCommit,
            expectedDisplayId = desk.targetDisplayId,
            appendTrailingSpace = shouldAppendLaptopSwipeSpace(),
            onCommitted = ::rememberLaptopSwipeImeCommit,
            fallback = fallback,
        )
    }

    internal fun restoreDextopSwipeIme() = laptopSwipeImeCoordinator.restore()

    /** Accessibility/clipboard fallback for OEMs that reject shell IME switching. */
    private fun commitLaptopSwipeCandidateFallback(
        value: String,
        rememberAutoCommit: Boolean = false,
    ) {
        // A uinput keyboard can only synthesize key codes; it cannot represent
        // arbitrary CJK/Hangul text.  Keep the user's editor/IME focused and
        // paste Unicode through the already-published laptop keyboard instead.
        // This also works when an OEM does not expose the editor node to this
        // accessibility service (the common reason Asian candidates appeared
        // but tapping them committed nothing).
        if (value.any { it.code !in 0x20..0x7e } && pasteLaptopSwipeUnicode(value)) {
            return
        }
        val focused = focusedLaptopEditor()
        if (focused != null) {
            val original = focused.text?.toString().orEmpty()
            val start = focused.textSelectionStart.takeIf { it >= 0 } ?: original.length
            val end = focused.textSelectionEnd.takeIf { it >= 0 } ?: start
            val from = min(start, end).coerceIn(0, original.length)
            val to = max(start, end).coerceIn(from, original.length)
            val separator = if (from > 0 && !original[from - 1].isWhitespace()) " " else ""
            val trailing = if (
                shouldAppendLaptopSwipeSpace() &&
                (to >= original.length || !original[to].isWhitespace())
            ) " " else ""
            val inserted = separator + value + trailing
            val updated = original.replaceRange(from, to, inserted)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
            }
            if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                if (rememberAutoCommit) {
                    laptopSwipeAutoCommit = LaptopSwipeAutoCommit(from + separator.length, value, updated)
                }
                focused.performAction(
                    AccessibilityNodeInfo.ACTION_SET_SELECTION,
                    Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, from + inserted.length)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, from + inserted.length)
                    }
                )
                focused.recycle()
                return
            }
        }
        // Some WebViews expose an editable node but reject ACTION_SET_TEXT.
        // ACTION_PASTE is the Unicode-safe fallback; restore the user's clipboard
        // immediately after the target has consumed the temporary clip.
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val previous = clipboard.primaryClip
        val fallbackText = value + if (shouldAppendLaptopSwipeSpace()) " " else ""
        clipboard.setPrimaryClip(ClipData.newPlainText("Dextop swipe", fallbackText))
        val pasted = focused?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true
        focused?.recycle()
        laptopSwipeHandler.postDelayed({
            if (previous != null) clipboard.setPrimaryClip(previous) else clipboard.clearPrimaryClip()
        }, 180)
        if (!pasted && fallbackText.all { it.code in 0x20..0x7e }) {
            fallbackText.forEach { character ->
                val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    .getEvents(charArrayOf(character)) ?: return@forEach
                events.forEach { event ->
                    desk.privilegedInputClient.injectKeyboard(
                        event.keyCode, event.action, event.metaState, event.repeatCount
                    )
                }
            }
        }
    }

    private fun shouldAppendLaptopSwipeSpace(): Boolean =
        laptopSwipeLanguage() == LaptopSwipeDecoder.Language.EN

    private fun rememberLaptopSwipeImeCommit(value: String) {
        val focused = focusedLaptopEditor() ?: return
        val current = focused.text?.toString().orEmpty()
        val cursor = focused.textSelectionStart.takeIf { it >= 0 }
            ?.coerceIn(0, current.length) ?: current.length
        val searchEnd = if (cursor > 0 && current[cursor - 1].isWhitespace()) cursor - 1 else cursor
        val start = current.lastIndexOf(value, searchEnd.coerceAtMost(current.length))
        if (start >= 0 && start + value.length <= current.length) {
            laptopSwipeAutoCommit = LaptopSwipeAutoCommit(start, value, current)
        }
        focused.recycle()
    }

    private fun pasteLaptopSwipeUnicode(value: String): Boolean {
        if (!desk.laptopKeyboardReady || value.isEmpty()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val previous = clipboard.primaryClip
        clipboard.setPrimaryClip(ClipData.newPlainText("Dextop swipe", value))

        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val results = booleanArrayOf(
            desk.injectLaptopKeyboardEvent(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_DOWN, ctrl, 0),
            desk.injectLaptopKeyboardEvent(KeyEvent.KEYCODE_V, KeyEvent.ACTION_DOWN, ctrl, 0),
            desk.injectLaptopKeyboardEvent(KeyEvent.KEYCODE_V, KeyEvent.ACTION_UP, ctrl, 0),
            // Always emit key-up even after a failed preceding event so a
            // transient binder failure can never leave Ctrl logically held.
            desk.injectLaptopKeyboardEvent(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.ACTION_UP, 0, 0),
        )
        val injected = results.all { it }

        // Clipboard dispatch is asynchronous on several Samsung/AOSP editors.
        // Restore only after the synthetic paste has had time to consume it.
        laptopSwipeHandler.postDelayed({
            if (previous != null) clipboard.setPrimaryClip(previous) else clipboard.clearPrimaryClip()
        }, 750L)
        OperationLog.i(
            context,
            "LaptopSwipe",
            "unicode candidate paste requested length=${value.length} injected=$injected"
        )
        return injected
    }

    private fun replaceLaptopSwipeAutoCommit(value: String): Boolean {
        val auto = laptopSwipeAutoCommit ?: return false
        val focused = focusedLaptopEditor() ?: return false
        val current = focused.text?.toString().orEmpty()
        val selection = focused.textSelectionStart.takeIf { it >= 0 }
            ?.coerceIn(0, current.length)
        val expectedStart = auto.start.takeIf { start ->
            start >= 0 && start + auto.value.length <= current.length &&
                    current.regionMatches(start, auto.value, 0, auto.value.length)
        }
        val selectionStart = selection?.minus(auto.value.length)?.takeIf { start ->
            start >= 0 && current.regionMatches(start, auto.value, 0, auto.value.length)
        }
        val nearbyStart = current.lastIndexOf(
            auto.value,
            startIndex = (selection ?: current.length).coerceAtMost(current.length),
        ).takeIf { it >= 0 && abs(it - auto.start) <= max(8, auto.value.length + 2) }
        val replaceStart = expectedStart ?: selectionStart ?: nearbyStart
        if (replaceStart == null) {
            focused.recycle()
            laptopSwipeAutoCommit = null
            return false
        }
        val end = replaceStart + auto.value.length
        val updated = current.replaceRange(replaceStart, end, value)
        val replaced = focused.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
            }
        )
        if (replaced) {
            val valueEnd = replaceStart + value.length
            val cursor = valueEnd + if (
                valueEnd < updated.length && updated[valueEnd].isWhitespace()
            ) 1 else 0
            focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
                }
            )
        }
        focused.recycle()
        laptopSwipeAutoCommit = null
        return replaced
    }
}
