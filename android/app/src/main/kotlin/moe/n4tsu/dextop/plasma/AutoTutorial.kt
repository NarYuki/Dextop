package moe.n4tsu.dextop.plasma

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dextop Auto's landing tutorial, on the real interface: "Welcome to Dextop Auto", then a circle
 * grows from the middle into the actual Home page. Each step lights up the real rail button the
 * user then presses themselves (touches inside the lit part go through to it), and the gesture
 * steps are practice the user does on a panel: tap a dot, scroll a list, pinch a square.
 *
 * Driving gets no touch stream from Android Auto, only taps, scrolls, flings and pinches without
 * a starting point, so Dextop uses the last tap as the point a following gesture acts on (see
 * MirrorService.carGestureStart: within 1.5 s of a tap, renewed while gestures follow each other).
 * The practice teaches exactly that; Parking receives ordinary touch and is told to touch directly.
 *
 * [target] gives the bounds of a named part of DextopAutoView ("page", "home", "rail", "display",
 * "status", "back"); [show] opens one of its pages; DextopAutoView passes every touch to [observe].
 */
@SuppressLint("ViewConstructor")
internal class AutoTutorial(
    context: Context,
    private val colors: AutoColors,
    private val unit: Float,
    private val japanese: Boolean,
    driving: Boolean,
    private val target: (String) -> Rect?,
    private val show: (String) -> Unit,
    private val onDone: () -> Unit,
) : FrameLayout(context) {
    private enum class Gesture { NONE, TAP, SCROLL, PINCH }

    /** What finishes a step: Next, pressing the lit button, or a practice gesture on the panel. */
    private enum class Task { NEXT, PRESS, TAP, SCROLL, PINCH }

    /** [targets]: the first one present is lit; none for a step about everything. */
    private class Step(val targets: List<String>, val page: String?, val task: Task, val gesture: Gesture,
                       val title: String, val text: String, val tip: String = "")

    private fun tr(ja: String, en: String) = moe.n4tsu.dextop.InlineStrings.pick(resources.configuration.locales[0], ja, en)
    private fun dp(n: Int) = (n * unit).roundToInt()
    private fun dpf(n: Float) = n * unit

    private val steps = listOf(
        Step(listOf("page"), "home", Task.NEXT, Gesture.NONE, tr("ホーム画面", "Home"),
            tr("地図と音楽のショートカット、再生中のメディア、お気に入りのアプリが並びます。",
                "Maps and music shortcuts, what is playing and your favorite apps.")),
        Step(listOf("home"), "home", Task.PRESS, Gesture.TAP, tr("ホームボタンを押す", "Press the Home button"),
            tr("光っているボタンを押してください。ホームで押すと、すべてのアプリの一覧が開きます。",
                "Press the lit button. On Home it opens the list of all apps.")),
        Step(listOf("page"), "apps", Task.NEXT, Gesture.NONE, tr("アプリ一覧", "All apps"),
            tr("アプリを押すと車の画面で開きます。開いたアプリは左のバーに並び、押すと切り替わります。",
                "Press an app to open it on the car screen. Open apps line up in the bar; press one to switch.")),
        Step(listOf("display"), "apps", Task.PRESS, Gesture.TAP, tr("画面ボタンを押す", "Press the Screen button"),
            tr("1画面・2画面の切り替え、拡大率、全画面表示、すべて閉じるがここにあります。",
                "Switch between one and two apps, set the zoom, go full screen or close all.")),
        Step(listOf("status"), "home", Task.PRESS, Gesture.TAP, tr("時計を押す", "Press the clock"),
            tr("設定が開きます。テーマやバーの幅、Android Auto での操作を変えられます。",
                "Settings open: theme, bar width and how Android Auto input works.")),
        Step(listOf("back"), "settings", Task.PRESS, Gesture.TAP, tr("戻るボタンを押す", "Press the Back button"),
            tr("開いているページを閉じます。アプリの中では一つ前の画面に戻ります。",
                "Closes the page that is open; in an app, goes back one screen.")),
        Step(emptyList(), "home", Task.TAP, Gesture.TAP, tr("練習: タップ", "Practice: tap"),
            tr("光っている丸をタップしてください。", "Tap the glowing dot.")),
        Step(emptyList(), "home", Task.SCROLL, Gesture.SCROLL, tr("練習: スクロール", "Practice: scroll"),
            if (driving) tr("枠の中を一度タップしてから、1.5秒以内に上へスワイプしてください。",
                "Tap inside the frame once, then swipe up within 1.5 seconds.")
            else tr("枠の中を上へスワイプしてください。", "Swipe up inside the frame."),
            if (driving) tr("タップした場所がスクロールの基点になります。タップはボタンも押すので、アプリでは押しても何も起きない場所を選んでください。",
                "The tapped spot is where the scroll happens. A tap also presses buttons, so in apps pick a spot that does nothing when pressed.") else ""),
        Step(emptyList(), "home", Task.PINCH, Gesture.PINCH, tr("練習: ピンチ", "Practice: pinch"),
            if (driving) tr("四角をタップしてから、2本指で広げて拡大してください。",
                "Tap the square, then spread two fingers to enlarge it.")
            else tr("四角を2本指で広げて拡大してください。", "Spread two fingers on the square to enlarge it.")),
        Step(emptyList(), "home", Task.NEXT, Gesture.NONE, tr("準備ができました", "You're all set"),
            tr("この説明は設定のクイックコントロールの下からいつでも見直せます。",
                "Find this again any time in the button under Quick controls in Settings.")),
    )

    private enum class Phase { WELCOME, REVEAL, COACH, LEAVING }
    private var phase = Phase.WELCOME
    private var phaseStarted = SystemClock.uptimeMillis()
    private var stepStarted = SystemClock.uptimeMillis()
    private var lastFrame = 0L
    private var index = 0
    /** The step's task is done; the next step follows shortly. */
    private var completed = false
    /** A press on the lit button went down inside it. */
    private var pressing = false

    // Practice state: the dot, the list's offset and the square's size.
    private var touched = false
    private var scrolled = 0f
    private var scrollStartY = 0f
    private var lastY = Float.NaN
    private var pinchSpan = 0f
    private var pinchBase = 1f
    private var square = 1f

    /** The lit area as drawn; it follows the step's target. Empty: nothing lit. */
    private val hole = RectF()
    private var holeAlpha = 0f
    private var scrim = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val decelerate = DecelerateInterpolator(1.6f)

    private val welcome = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    private val welcomeTitle = text(30f, colors.text, bold = true, lines = 2).apply {
        text = tr("Dextop Autoへようこそ", "Welcome to Dextop Auto")
        gravity = Gravity.CENTER
    }
    private val welcomeText = text(15f, colors.secondary, lines = 2).apply {
        text = tr("スマホのアプリを車の画面で使えます", "Your phone's apps on the car screen")
        gravity = Gravity.CENTER
    }

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(this@AutoTutorial.colors.card); cornerRadius = dpf(18f) }
        setPadding(dp(18), dp(16), dp(18), dp(12))
        elevation = dpf(8f)
        alpha = 0f
    }
    private val title = text(19f, colors.text, bold = true)
    private val body = text(14f, colors.secondary, lines = 5)
    private val tip = text(12f, colors.secondary, lines = 4)
    private val dots = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val back = button(tr("戻る", "Back"), filled = false) { go(index - 1) }
    private val next = button("", filled = true) { if (index == steps.lastIndex) finish() else go(index + 1) }
    private val skip = button(tr("スキップ", "Skip"), filled = false) { finish() }
    private var cardX = Float.NaN
    private var cardY = Float.NaN

    init {
        setWillNotDraw(false)
        isClickable = true
        // Above the rail and the pages DextopAutoView brings to the front as they open.
        translationZ = 100_000f
        outlineProvider = null
        // The Home page is ready under the welcome screen for the circle to reveal.
        show("home")
        welcome.addView(welcomeTitle, LinearLayout.LayoutParams(-2, -2))
        welcome.addView(welcomeText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        addView(welcome, LayoutParams(-1, -1))
        welcomeTitle.alpha = 0f
        welcomeTitle.scaleX = .92f
        welcomeTitle.scaleY = .92f
        welcomeTitle.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520L).setInterpolator(decelerate).start()
        welcomeText.alpha = 0f
        welcomeText.animate().alpha(1f).setStartDelay(220L).setDuration(420L).start()
        setOnClickListener { if (phase == Phase.WELCOME) reveal() }
        postDelayed({ if (phase == Phase.WELCOME) reveal() }, WELCOME_MS)

        card.addView(title, LinearLayout.LayoutParams(-1, -2))
        card.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        card.addView(tip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        card.addView(dots, LinearLayout.LayoutParams(-1, dp(16)).apply { topMargin = dp(10) })
        val buttons = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        buttons.addView(skip, LinearLayout.LayoutParams(-2, dp(MIN_TOUCH)))
        buttons.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        buttons.addView(back, LinearLayout.LayoutParams(-2, dp(MIN_TOUCH)))
        buttons.addView(next, LinearLayout.LayoutParams(-2, dp(MIN_TOUCH)).apply { leftMargin = dp(6) })
        card.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        card.visibility = INVISIBLE
        addView(card, LayoutParams(dp(340), -2, Gravity.TOP or Gravity.START))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val width = min(dp(340), (w * .46f).roundToInt()).coerceAtLeast(dp(220))
        if (card.layoutParams.width != width) card.layoutParams = (card.layoutParams as LayoutParams).apply { this.width = width }
    }

    private fun reveal() {
        phase = Phase.REVEAL
        phaseStarted = SystemClock.uptimeMillis()
        welcome.animate().alpha(0f).setDuration(220L).start()
        invalidate()
    }

    private fun startCoach() {
        phase = Phase.COACH
        phaseStarted = SystemClock.uptimeMillis()
        removeView(welcome)
        // From the whole screen, which the circle just uncovered, to the first step's target.
        hole.set(0f, 0f, width.toFloat(), height.toFloat())
        holeAlpha = 1f
        scrim = 0f
        card.visibility = VISIBLE
        go(0)
    }

    private fun go(target: Int) {
        index = target.coerceIn(0, steps.lastIndex)
        val step = steps[index]
        step.page?.let(show)
        stepStarted = SystemClock.uptimeMillis()
        title.text = step.title
        body.text = step.text
        tip.text = step.tip
        tip.visibility = if (step.tip.isEmpty()) GONE else VISIBLE
        back.visibility = if (index == 0) INVISIBLE else VISIBLE
        skip.visibility = if (index == steps.lastIndex) INVISIBLE else VISIBLE
        next.text = if (index == steps.lastIndex) tr("始める", "Start") else tr("次へ", "Next")
        completed = false
        pressing = false
        touched = false
        scrolled = 0f
        lastY = Float.NaN
        pinchSpan = 0f
        square = 1f
        next.visibility = if (step.task == Task.NEXT) VISIBLE else GONE
        // Should a press not register, Next appears after a while instead of trapping the user.
        if (step.task != Task.NEXT) {
            val shown = index
            postDelayed({ if (index == shown && !completed && phase == Phase.COACH) next.visibility = VISIBLE }, STUCK_MS)
        }
        dots.removeAllViews()
        dots.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        steps.indices.forEach { i ->
            dots.addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == index) this@AutoTutorial.colors.accent else this@AutoTutorial.colors.separator)
                }
            }, LinearLayout.LayoutParams(dp(if (i == index) 9 else 6), dp(if (i == index) 9 else 6)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        dots.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        card.animate().cancel()
        card.alpha = .35f
        card.animate().alpha(1f).setDuration(260L).start()
        invalidate()
    }

    /** The step's task is done: say so, then move on. */
    private fun complete() {
        if (completed || phase != Phase.COACH) return
        completed = true
        title.text = tr("できました", "Nice")
        val shown = index
        postDelayed({ if (index == shown && phase == Phase.COACH) go(index + 1) }, DONE_MS)
        invalidate()
    }

    /** Every touch on DextopAutoView, before it is dispatched: a press of the lit button ends. */
    fun observe(event: MotionEvent) {
        if (phase != Phase.COACH || steps[index].task != Task.PRESS) return
        val lit = desired(steps[index]) ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressing = lit.contains(event.x, event.y)
            MotionEvent.ACTION_UP -> { if (pressing && lit.contains(event.x, event.y)) complete(); pressing = false }
            MotionEvent.ACTION_CANCEL -> pressing = false
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (phase == Phase.COACH && !completed && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val step = steps[index]
            val inCard = card.visibility == VISIBLE && event.x >= card.translationX && event.x < card.translationX + card.width &&
                event.y >= card.translationY && event.y < card.translationY + card.height
            // The lit button takes the press itself.
            if (!inCard && step.task == Task.PRESS && desired(step)?.contains(event.x, event.y) == true) return false
        }
        if (phase == Phase.COACH && steps[index].task.let { it == Task.TAP || it == Task.SCROLL || it == Task.PINCH } &&
            practice(event)) return true
        return super.dispatchTouchEvent(event)
    }

    /** The practice panel's gestures; false for touches elsewhere (the card's buttons). */
    private fun practice(event: MotionEvent): Boolean {
        val panel = panel() ?: return false
        val x = event.getX(0); val y = event.getY(0)
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !panel.contains(x, y)) return false
        if (completed) return true
        touched = true
        when (steps[index].task) {
            Task.TAP -> if (event.actionMasked == MotionEvent.ACTION_UP &&
                hypot(x - panel.centerX(), y - panel.centerY()) <= dpf(DOT * 2.2f)) complete()
            Task.SCROLL -> when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastY = y; scrollStartY = scrolled }
                MotionEvent.ACTION_MOVE -> if (!lastY.isNaN()) {
                    scrolled = (scrolled + lastY - y).coerceAtLeast(0f)
                    lastY = y
                    if (scrolled - scrollStartY > panel.height() * .5f || scrolled > panel.height() * .9f) complete()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lastY = Float.NaN
            }
            Task.PINCH -> if (event.pointerCount >= 2) {
                val span = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || pinchSpan <= 0f) {
                    pinchSpan = span.coerceAtLeast(1f); pinchBase = square
                } else {
                    square = (pinchBase * span / pinchSpan).coerceIn(.5f, 2.4f)
                    if (square >= 1.7f) complete()
                }
            } else if (event.actionMasked == MotionEvent.ACTION_UP) pinchSpan = 0f
            else -> {}
        }
        invalidate()
        return true
    }

    /** Where the practice happens: the left part of the page area. */
    private fun panel(): RectF? {
        val page = target("page")?.takeIf { !it.isEmpty } ?: Rect(0, 0, width, height)
        val pw = min(page.width() * .5f, dpf(340f))
        val ph = min(page.height() * .72f, dpf(260f))
        val left = page.left + max(dpf(16f), (page.width() * .5f - pw) / 2f)
        val top = page.top + (page.height() - ph) / 2f
        return RectF(left, top, left + pw, top + ph)
    }

    private fun finish() {
        if (phase == Phase.LEAVING) return
        phase = Phase.LEAVING
        isClickable = false
        show("home")
        animate().alpha(0f).setDuration(240L).withEndAction(onDone).start()
    }

    /** The step's target as it is laid out now, with some room around it. */
    private fun desired(step: Step): RectF? {
        if (step.task == Task.TAP || step.task == Task.SCROLL || step.task == Task.PINCH) return panel()
        val rect = step.targets.firstNotNullOfOrNull(target) ?: return null
        if (rect.isEmpty) return null
        val margin = dpf(6f)
        return RectF(rect.left - margin, rect.top - margin, rect.right + margin, rect.bottom + margin).apply {
            intersect(0f, 0f, width.toFloat(), height.toFloat())
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1, 64).toFloat()
        lastFrame = now
        when (phase) {
            Phase.WELCOME -> canvas.drawColor(colors.background)
            Phase.REVEAL -> {
                // A circle grows from the middle and uncovers the Home page.
                val t = ((now - phaseStarted) / REVEAL_MS.toFloat()).coerceIn(0f, 1f)
                val radius = decelerate.getInterpolation(t) * hypot(w, h) / 2f * 1.04f
                path.reset()
                path.fillType = Path.FillType.EVEN_ODD
                path.addRect(0f, 0f, w, h, Path.Direction.CW)
                path.addCircle(w / 2f, h / 2f, radius, Path.Direction.CW)
                paint.style = Paint.Style.FILL
                paint.color = colors.background
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpf(4f)
                paint.color = withAlpha(colors.accent, ((1f - t) * 255).roundToInt())
                canvas.drawCircle(w / 2f, h / 2f, radius, paint)
                if (t >= 1f) startCoach()
            }
            Phase.COACH, Phase.LEAVING -> coach(canvas, w, h, dt, now)
        }
        super.dispatchDraw(canvas)
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun coach(canvas: Canvas, w: Float, h: Float, dt: Float, now: Long) {
        val step = steps[index]
        val want = desired(step)
        val k = min(1f, dt / 90f)
        scrim += (1f - scrim) * min(1f, dt / 140f)
        if (want != null) {
            hole.left += (want.left - hole.left) * k
            hole.top += (want.top - hole.top) * k
            hole.right += (want.right - hole.right) * k
            hole.bottom += (want.bottom - hole.bottom) * k
            holeAlpha += (1f - holeAlpha) * k
        } else {
            holeAlpha += (0f - holeAlpha) * k
        }
        val radius = dpf(14f)
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, w, h, Path.Direction.CW)
        if (holeAlpha > .02f) path.addRoundRect(hole, radius, radius, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(colors.scrim, ((colors.scrim ushr 24) * scrim).roundToInt())
        canvas.drawPath(path, paint)
        if (holeAlpha > .02f) {
            // A slow pulse around the lit part.
            val pulse = (sin((now - stepStarted) / 520.0) * .5 + .5).toFloat()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpf(2.5f)
            paint.color = withAlpha(colors.accent, ((150 + 105 * pulse) * holeAlpha).roundToInt())
            canvas.drawRoundRect(hole, radius, radius, paint)
            paint.strokeWidth = dpf(1.5f)
            paint.color = withAlpha(colors.accent, ((1f - pulse) * 90 * holeAlpha).roundToInt())
            val grow = dpf(4f) + pulse * dpf(6f)
            canvas.drawRoundRect(RectF(hole.left - grow, hole.top - grow, hole.right + grow, hole.bottom + grow),
                radius + grow, radius + grow, paint)
            if (desired(step) != null && step.task in PRACTICE) drawPractice(canvas, step.task, hole, now)
            if (!touched && !completed) gesture(canvas, step.gesture, hole, ((now - stepStarted).toFloat()))
            if (completed) check(canvas, hole)
        }
        placeCard(want, w, h, k)
    }

    /** Beside the lit part where there is room, otherwise in its lower corner; centered without one. */
    private fun placeCard(want: RectF?, w: Float, h: Float, k: Float) {
        val cw = card.width.toFloat().takeIf { it > 0 } ?: return
        val ch = card.height.toFloat()
        val gap = dpf(16f)
        val (x, y) = when {
            want == null -> (w - cw) / 2f to (h - ch) / 2f
            w - want.right >= cw + gap * 2 -> want.right + gap to (want.centerY() - ch / 2f)
            want.left >= cw + gap * 2 -> want.left - gap - cw to (want.centerY() - ch / 2f)
            h - want.bottom >= ch + gap * 2 -> (want.centerX() - cw / 2f) to want.bottom + gap
            want.top >= ch + gap * 2 -> (want.centerX() - cw / 2f) to want.top - gap - ch
            else -> want.right - gap - cw to want.bottom - gap - ch
        }
        val tx = x.coerceIn(gap, max(gap, w - cw - gap))
        val ty = y.coerceIn(gap, max(gap, h - ch - gap))
        if (cardX.isNaN()) { cardX = tx; cardY = ty }
        cardX += (tx - cardX) * k
        cardY += (ty - cardY) * k
        card.translationX = cardX
        card.translationY = cardY
    }

    // region practice

    private fun drawPractice(canvas: Canvas, task: Task, area: RectF, now: Long) {
        val radius = dpf(14f)
        canvas.save()
        path.reset()
        path.addRoundRect(area, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawColor(colors.card)
        when (task) {
            Task.TAP -> {
                val pulse = (sin((now - stepStarted) / 300.0) * .5 + .5).toFloat()
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(colors.accent, (60 + 60 * pulse).roundToInt())
                canvas.drawCircle(area.centerX(), area.centerY(), dpf(DOT) * (1.4f + .4f * pulse), paint)
                paint.color = colors.accent
                canvas.drawCircle(area.centerX(), area.centerY(), dpf(DOT), paint)
            }
            Task.SCROLL -> {
                // A list of rows that moves with the swipe.
                val row = dpf(44f)
                val gap = dpf(8f)
                val first = (scrolled / (row + gap)).toInt()
                var y = area.top + gap - scrolled % (row + gap)
                var i = first
                paint.style = Paint.Style.FILL
                while (y < area.bottom) {
                    paint.color = if (i % 4 == 0) colors.accentSoft else colors.raised
                    canvas.drawRoundRect(RectF(area.left + gap * 2, y, area.right - gap * 2, y + row), dpf(10f), dpf(10f), paint)
                    y += row + gap; i++
                }
                val progress = ((scrolled - scrollStartY) / (area.height() * .5f)).coerceIn(0f, 1f)
                paint.color = colors.accent
                canvas.drawRect(area.left, area.bottom - dpf(4f), area.left + area.width() * if (completed) 1f else progress, area.bottom, paint)
            }
            Task.PINCH -> {
                val s = dpf(34f) * square
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(colors.accent, 200)
                canvas.drawRoundRect(RectF(area.centerX() - s, area.centerY() - s, area.centerX() + s, area.centerY() + s), dpf(10f), dpf(10f), paint)
                // The size to reach.
                val goal = dpf(34f) * 1.7f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpf(2f)
                paint.color = withAlpha(colors.accent, 120)
                canvas.drawRoundRect(RectF(area.centerX() - goal, area.centerY() - goal, area.centerX() + goal, area.centerY() + goal), dpf(12f), dpf(12f), paint)
            }
            else -> {}
        }
        canvas.restore()
    }

    /** A check mark on the lit part once its task is done. */
    private fun check(canvas: Canvas, area: RectF) {
        val r = dpf(22f)
        val cx = area.centerX(); val cy = area.centerY()
        paint.style = Paint.Style.FILL
        paint.color = colors.accent
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(3.5f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = colors.onAccent
        path.reset()
        path.moveTo(cx - r * .42f, cy + r * .02f)
        path.lineTo(cx - r * .1f, cy + r * .34f)
        path.lineTo(cx + r * .44f, cy - r * .3f)
        canvas.drawPath(path, paint)
    }
    // endregion

    // region gestures

    /** A finger acting on the lit part; [ms] since the step began, each gesture loops. */
    private fun gesture(canvas: Canvas, gesture: Gesture, area: RectF, ms: Float) {
        fun loop(period: Float) = (ms % period) / period
        // Away from the card, which sits in the lower right of large areas.
        val cx = area.centerX()
        val cy = area.centerY()
        when (gesture) {
            Gesture.NONE -> {}
            Gesture.TAP -> {
                val t = loop(1600f)
                ripple(canvas, cx, cy, (t - .1f) / .5f)
                finger(canvas, cx, cy, t in .1f..0.3f, t < .85f)
            }
            Gesture.SCROLL -> {
                val t = loop(3600f)
                val startY = area.top + area.height() * .72f
                val travel = area.height() * .38f
                val swipe = when {
                    t < .3f -> 0f
                    t < .6f -> (t - .3f) / .3f
                    t < .65f -> 0f
                    t < .95f -> (t - .65f) / .3f
                    else -> 0f
                }
                ripple(canvas, cx, startY, t / .25f)
                trail(canvas, cx, startY, cx, startY - swipe * travel, t >= .3f && swipe > 0f)
                finger(canvas, cx, startY - swipe * travel, t < .2f || swipe > 0f, true)
            }
            Gesture.PINCH -> {
                val t = loop(3200f)
                ripple(canvas, cx, cy, t / .25f)
                if (t < .25f) finger(canvas, cx, cy, t < .15f, true) else {
                    val spread = dpf(14f) + ((t - .3f) / .4f).coerceIn(0f, 1f) * dpf(36f)
                    finger(canvas, cx - spread, cy + spread * .6f, true, t < .9f)
                    finger(canvas, cx + spread, cy - spread * .6f, true, t < .9f)
                }
            }
        }
    }

    private fun finger(canvas: Canvas, x: Float, y: Float, pressed: Boolean, visible: Boolean) {
        if (!visible) return
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(colors.onAccent, if (pressed) 235 else 170)
        canvas.drawCircle(x, y, dpf(if (pressed) 11f else 13f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(2f)
        paint.color = withAlpha(colors.accent, 255)
        canvas.drawCircle(x, y, dpf(if (pressed) 11f else 13f), paint)
    }

    private fun ripple(canvas: Canvas, x: Float, y: Float, t: Float) {
        if (t !in 0f..1f) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(2.5f)
        paint.color = withAlpha(colors.accent, ((1f - t) * 220).roundToInt())
        canvas.drawCircle(x, y, dpf(12f) + t * dpf(22f), paint)
    }

    private fun trail(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, visible: Boolean) {
        if (!visible) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(6f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = withAlpha(colors.accent, 110)
        canvas.drawLine(x1, y1, x2, y2, paint)
    }
    // endregion

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun text(size: Float, color: Int, bold: Boolean = false, lines: Int = 2) = TextView(context).apply {
        textSize = size * unit / resources.displayMetrics.density
        setTextColor(color)
        maxLines = lines
        typeface = if (bold) PlasmaTheme.sansMedium else PlasmaTheme.sans
    }

    private fun button(label: String, filled: Boolean, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f * unit / resources.displayMetrics.density
        gravity = Gravity.CENTER
        typeface = PlasmaTheme.sansMedium
        setTextColor(if (filled) colors.onAccent else colors.accent)
        minWidth = dp(MIN_TOUCH + 16)
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            setColor(if (filled) this@AutoTutorial.colors.accent else 0)
            cornerRadius = dp(MIN_TOUCH).toFloat()
        }
        isClickable = true
        isFocusable = true
        contentDescription = label
        setOnClickListener { action() }
    }

    private companion object {
        /** Matches DextopAutoView: hit targets stay at least this many units. */
        const val MIN_TOUCH = 40
        const val WELCOME_MS = 1800L
        const val REVEAL_MS = 750L
        const val DONE_MS = 900L
        const val STUCK_MS = 20_000L
        /** The practice dot's radius in units. */
        const val DOT = 16f
        val PRACTICE = setOf(Task.TAP, Task.SCROLL, Task.PINCH)
    }
}
