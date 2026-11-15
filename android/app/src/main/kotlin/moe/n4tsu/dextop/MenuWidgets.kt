package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/** What [MenuWidgets] needs from the session beyond the shared interfaces. */
internal interface MenuWidgetsHost : DesktopSession, OverlayMenu {
    fun beginOverlayTextInput(field: EditText)
    val cast: CastController
    fun changeOrientation()
    val requestedPortrait: Boolean
    val secureDisplay: Boolean
    val showSystemDecorations: Boolean
    fun start(config: MirrorService.Config)
    fun stop()
    fun toggleDisplayRotation180()
}

/** Buttons, titles, fields and other widgets the overlay menu pages are built from. */
internal class MenuWidgets(
    private val context: Context,
    private val desk: MenuWidgetsHost,
) {
    internal fun squareControl(id: String): View = when (id) {
        "rotate_180" -> squareTextAction("180°", NativeStrings.text("nativeRotate180")) {
            if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeRotate180"))
            else desk.toggleDisplayRotation180()
        }

        "cast" -> squareAction(R.drawable.ic_cast, NativeStrings.text("nativeCast")) {
            if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeCastDescription"))
            else desk.cast.openCastPicker()
        }

        "stop" -> squareAction(R.drawable.ic_stop, NativeStrings.text("nativeEnd"), true) {
            if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeTerminateYourDextopSession")) else desk.stop()
        }

        "reconnect" -> squareAction(R.drawable.ic_reload, NativeStrings.text("nativeReconnect")) {
            if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeReconnectIfYouHaveDisplayOrConnection"))
            else desk.start(MirrorService.Config(desk.targetWidth, desk.targetHeight, desk.density, desk.secureDisplay, desk.showSystemDecorations))
        }

        else -> squareAction(
            // The live target can be the landscape upper pane while the
            // selected Dextop orientation is portrait. Show the action for
            // the next orientation from the explicit selection instead.
            if (desk.requestedPortrait) R.drawable.ic_landscape else R.drawable.ic_portrait,
            if (desk.requestedPortrait) NativeStrings.text("nativeHorizontalHolding")
            else NativeStrings.text("nativeVerticalHolding")
        ) {
            if (desk.demoMode) desk.demoExplanation(NativeStrings.text("nativeSwitchBetweenPortraitAndLandscapeOrientationOf"))
            else desk.changeOrientation()
        }
    }

    private fun squareTextAction(label: String, description: String, action: () -> Unit) =
        TextView(context).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            contentDescription = description
            setTextColor(Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(Color.rgb(50, 47, 55))
                cornerRadius = desk.dp(16).toFloat()
            }
            setOnClickListener { action() }
        }

    internal fun menuHint(textValue: String): View = TextView(context).apply {
        text = textValue
        textSize = 14f
        setTextColor(Color.rgb(202, 196, 208))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(desk.dp(16), desk.dp(14), desk.dp(16), desk.dp(14))
    }

    private fun squareAction(icon: Int, description: String, destructive: Boolean = false, action: () -> Unit) =
        ImageButton(context).apply {
            setImageResource(icon)
            contentDescription = description
            setColorFilter(if (destructive) Color.rgb(255, 180, 171) else Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(if (destructive) Color.rgb(73, 37, 35) else Color.rgb(50, 47, 55))
                cornerRadius = desk.dp(16).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun routingAction(icon: Int, description: String, enabled: Boolean, action: () -> Unit) =
        squareAction(icon, description, action = action).apply {
            setColorFilter(if (enabled) Color.rgb(226, 196, 255) else Color.rgb(202, 196, 208))
            background = GradientDrawable().apply {
                setColor(if (enabled) Color.rgb(79, 55, 111) else Color.rgb(50, 47, 55))
                cornerRadius = desk.dp(16).toFloat()
            }
            isSelected = enabled
        }

    internal fun editButton(action: () -> Unit): ImageButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_edit)
        setColorFilter(Color.rgb(230, 225, 229))
        contentDescription = NativeStrings.text("nativeEdit")
        background = GradientDrawable().apply { setColor(Color.rgb(50, 47, 55)); cornerRadius = desk.dp(16).toFloat() }
        setOnClickListener { action() }
    }

    internal fun menuTitle(
        title: String,
        action: String? = null,
        actionTag: String? = null,
        onAction: (() -> Unit)? = null
    ): View =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 22f
                setTextColor(Color.rgb(230, 225, 229))
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, desk.dp(54), 1f))
            if (action != null) addView(
                TextView(context).apply {
                    text = action
                    textSize = if (actionTag == "workspace_toggle") 28f else 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(208, 188, 255))
                    tag = actionTag
                    if (actionTag == "workspace_toggle") {
                        contentDescription = NativeStrings.text("nativeExpandWorkspace")
                    }
                    setOnClickListener { onAction?.invoke() }
                }, LinearLayout.LayoutParams(
                    if (actionTag == "workspace_toggle") desk.dp(52) else desk.dp(64),
                    desk.dp(52)
                )
            )
        }

    internal fun animateMenuResize(panel: LinearLayout) {
        if (!panel.isLaidOut) return
        TransitionManager.beginDelayedTransition(panel.parent as? FrameLayout ?: panel, ChangeBounds().apply {
            duration = 260
        })
    }

    internal fun sectionLabel(label: String): TextView = TextView(context).apply {
        text = label
        textSize = 12f
        setTextColor(Color.rgb(202, 196, 208))
        setPadding(desk.dp(4), desk.dp(8), 0, desk.dp(8))
    }

    internal fun actionButton(
        icon: Int = R.drawable.ic_chevron,
        label: String,
        destructive: Boolean = false,
        action: () -> Unit
    ): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(desk.dp(16), 0, desk.dp(16), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawablePadding = desk.dp(14)
            setTextColor(if (destructive) Color.rgb(255, 180, 171) else Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(if (destructive) Color.rgb(73, 37, 35) else Color.rgb(50, 47, 55))
                cornerRadius = desk.dp(16).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animate().scaleX(.97f).scaleY(.97f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    action()
                }.start()
            }
        }.also { it.layoutParams = LinearLayout.LayoutParams(-1, desk.dp(50)).apply { bottomMargin = desk.dp(8) } }

    internal fun choiceButton(label: String, selected: Boolean, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.rgb(45, 38, 52) else Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(if (selected) Color.rgb(232, 222, 248) else Color.rgb(50, 47, 55))
                cornerRadius = desk.dp(14).toFloat()
            }
            setOnClickListener { action() }
        }

    internal fun numberField(value: String, label: String): EditText = EditText(context).apply {
        setText(value)
        hint = label
        // Use the public numeric input type so Samsung IME and hardware
        // keyboards both deliver editable text to the quick-menu fields.
        // The previous literal was interpreted as a non-editable field by
        // some vendor builds.
        inputType = InputType.TYPE_CLASS_NUMBER
        isSingleLine = true
        setSelectAllOnFocus(false)
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(230, 225, 229))
        setHintTextColor(Color.rgb(147, 143, 153))
        background = GradientDrawable().apply {
            setColor(Color.rgb(50, 47, 55))
            setStroke(desk.dp(1), Color.rgb(147, 143, 153))
            cornerRadius = desk.dp(14).toFloat()
        }
        isFocusableInTouchMode = true
        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                desk.beginOverlayTextInput(view as EditText)
            }
        }
        setOnClickListener {
            desk.beginOverlayTextInput(this)
        }
    }
}
