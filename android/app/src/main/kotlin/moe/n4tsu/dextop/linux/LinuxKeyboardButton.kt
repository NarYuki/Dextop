package moe.n4tsu.dextop.linux

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageButton
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Android-owned keyboard affordance, shared by application and desktop windows. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class LinuxKeyboardButton(
    private val root: FrameLayout,
    action: () -> Unit,
) : ImageButton(root.context), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private fun preferences(context: Context) = context.getSharedPreferences("linux-keyboard-button", Context.MODE_PRIVATE)
        fun visible(context: Context) = preferences(context).getBoolean("visible", true)
        fun setVisible(context: Context, value: Boolean) {
            preferences(context).edit().putBoolean("visible", value).apply()
        }
    }

    private val preferences = preferences(context)
    private val edge = 12 * resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var fractionX = preferences.getFloat("x", 1f).coerceIn(0f, 1f)
    private var fractionY = preferences.getFloat("y", 1f).coerceIn(0f, 1f)
    private var pointer = -1
    private var startX = 0f
    private var startY = 0f
    private var startLeft = 0f
    private var startTop = 0f
    private var dragging = false
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }

    init {
        setImageResource(android.R.drawable.ic_menu_edit)
        setBackgroundColor(0x66000000)
        alpha = .55f
        contentDescription = moe.n4tsu.dextop.localText("uiKeyboard")
        isLongClickable = false
        setOnClickListener { action() }
        val size = (44 * resources.displayMetrics.density).roundToInt()
        root.addView(this, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.LEFT))
    }

    private fun range(): Pair<Float, Float> {
        val params = layoutParams
        return (root.width - root.paddingLeft - root.paddingRight - params.width).coerceAtLeast(0).toFloat() to
            (root.height - root.paddingTop - root.paddingBottom - params.height).coerceAtLeast(0).toFloat()
    }

    /** Fractions retain the chosen edge/position after rotation, IME or task resizing. */
    private fun place() {
        val (w, h) = range()
        val insetX = minOf(edge, w / 2)
        val insetY = minOf(edge, h / 2)
        val params = layoutParams as FrameLayout.LayoutParams
        val left = (insetX + fractionX * (w - 2 * insetX)).roundToInt()
        val top = (insetY + fractionY * (h - 2 * insetY)).roundToInt()
        if (params.leftMargin != left || params.topMargin != top) {
            params.leftMargin = left
            params.topMargin = top
            layoutParams = params
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(this)
        root.addOnLayoutChangeListener(layoutListener)
        onSharedPreferenceChanged(preferences, null)
    }

    override fun onDetachedFromWindow() {
        root.removeOnLayoutChangeListener(layoutListener)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        visibility = if (visible(context)) View.VISIBLE else View.GONE
        fractionX = preferences.getFloat("x", 1f).coerceIn(0f, 1f)
        fractionY = preferences.getFloat("y", 1f).coerceIn(0f, 1f)
        place()
    }

    private fun finishGesture(save: Boolean) {
        if (save && dragging) preferences.edit().putFloat("x", fractionX).putFloat("y", fractionY).apply()
        pointer = -1
        dragging = false
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = event.getPointerId(0)
                startX = event.rawX
                startY = event.rawY
                val params = layoutParams as FrameLayout.LayoutParams
                startLeft = params.leftMargin.toFloat()
                startTop = params.topMargin.toFloat()
                dragging = false
                isPressed = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0) { finishGesture(false); return true }
                val dx = event.getRawX(index) - startX
                val dy = event.getRawY(index) - startY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    isPressed = false
                }
                if (dragging) {
                    val (w, h) = range()
                    val insetX = minOf(edge, w / 2)
                    val insetY = minOf(edge, h / 2)
                    fractionX = if (w > 2 * insetX) ((startLeft + dx - insetX) / (w - 2 * insetX)).coerceIn(0f, 1f) else 1f
                    fractionY = if (h > 2 * insetY) ((startTop + dy - insetY) / (h - 2 * insetY)).coerceIn(0f, 1f) else 1f
                    place()
                }
            }
            MotionEvent.ACTION_UP -> {
                val click = pointer != -1 && !dragging
                finishGesture(true)
                if (click) performClick()
            }
            MotionEvent.ACTION_CANCEL -> finishGesture(false)
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) finishGesture(true)
        }
        return true
    }
}
