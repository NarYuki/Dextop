package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.GridLayout
import moe.n4tsu.dextop.input.PrivilegedInputClient
import moe.n4tsu.dextop.input.PrivilegedInputProtocol
import moe.n4tsu.dextop.GamepadTriggerView

/** What [VirtualGamepad] needs from the session beyond the shared interfaces. */
internal interface VirtualGamepadHost : DesktopSession, OverlayMenu {
    fun blackBerryActionRow(mainAction: View, edit: () -> Unit): LinearLayout
    val cast: CastController
    val coverBackButtonsActive: Boolean
    val haptics: Haptics
    fun hasKeyboardStyleChoice(): Boolean
    fun isBlackBerryModeAvailable(): Boolean
    fun isLaptopStyleAvailable(): Boolean
    val keyboardDeckStyle: MirrorService.KeyboardDeckStyle
    val laptopTypeface: Typeface
    fun opacityColor(color: Int, opacity: Float): Int
    fun setLaptopMode(enabled: Boolean, requestedStyle: MirrorService.KeyboardDeckStyle = MirrorService.KeyboardDeckStyle.LAPTOP)
    fun showKeyboardStyleMenu(panel: LinearLayout)
}

/** The on-screen gamepad and Game Boy decks and the virtual controller they drive. */
internal class VirtualGamepad(
    private val context: Context,
    private val desk: VirtualGamepadHost,
) {
    internal var virtualGamepadVisible = false
        private set

    private val virtualGamepadButtons = linkedSetOf<Int>()

    private val virtualGamepadAxes = linkedMapOf<Int, Int>()

    internal fun buildVirtualGamepadDeck(content: LinearLayout, palette: LaptopPalette) {
        val title = TextView(context).apply {
            text = NativeStrings.text("nativeVirtualGamepad")
            gravity = Gravity.CENTER_VERTICAL
            typeface = desk.laptopTypeface
            textSize = 12f
            letterSpacing = .08f
            setTextColor(palette.text)
            setPadding(desk.dp(4), 0, desk.dp(4), desk.dp(4))
        }
        content.addView(title, LinearLayout.LayoutParams(-1, desk.dp(28)))

        content.addView(
            XboxGamepadView(
                context,
                onButtonChanged = ::injectVirtualGamepadButton,
                onAxisChanged = ::injectVirtualGamepadAxis,
                onHaptic = { desk.haptics.performLaptopHaptic(null) },
            ).apply {
                contentDescription = NativeStrings.text("nativeVirtualGamepad")
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
    }

    /** Compact Game Boy-style controller: D-pad on the left, A/B on the right. */
    internal fun buildVirtualGameboyDeck(content: LinearLayout, palette: LaptopPalette) {
        val title = TextView(context).apply {
            text = NativeStrings.text("nativeGameBoyStyle")
            gravity = Gravity.CENTER_VERTICAL
            typeface = desk.laptopTypeface
            textSize = 12f
            letterSpacing = .08f
            setTextColor(palette.text)
            setPadding(desk.dp(4), 0, desk.dp(4), desk.dp(4))
        }
        content.addView(title, LinearLayout.LayoutParams(-1, desk.dp(28)))

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val dpadColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(gamepadCaption("D-PAD", palette), LinearLayout.LayoutParams(-1, desk.dp(22)))
            addView(
                buildGamepadDpad(palette),
                LinearLayout.LayoutParams(desk.dp(136), desk.dp(136)),
            )
        }
        val actionColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(gamepadCaption("A / B", palette), LinearLayout.LayoutParams(-1, desk.dp(22)))
            val faceRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addGamepadButton(
                    this,
                    "B",
                    PrivilegedInputProtocol.GAMEPAD_BUTTON_B,
                    palette,
                    LinearLayout.LayoutParams(desk.dp(58), desk.dp(48)).apply {
                        rightMargin = desk.dp(6)
                    },
                )
                addGamepadButton(
                    this,
                    "A",
                    PrivilegedInputProtocol.GAMEPAD_BUTTON_A,
                    palette,
                    LinearLayout.LayoutParams(desk.dp(58), desk.dp(48)),
                )
            }
            addView(faceRow, LinearLayout.LayoutParams(-1, desk.dp(56)))
            addView(gamepadCaption("SELECT / START", palette), LinearLayout.LayoutParams(-1, desk.dp(22)))
            val systemRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addGamepadButton(
                    this,
                    "Select",
                    PrivilegedInputProtocol.GAMEPAD_BUTTON_SELECT,
                    palette,
                    LinearLayout.LayoutParams(desk.dp(72), desk.dp(38)).apply {
                        rightMargin = desk.dp(6)
                    },
                )
                addGamepadButton(
                    this,
                    "Start",
                    PrivilegedInputProtocol.GAMEPAD_BUTTON_START,
                    palette,
                    LinearLayout.LayoutParams(desk.dp(72), desk.dp(38)),
                )
            }
            addView(systemRow, LinearLayout.LayoutParams(-1, desk.dp(46)))
        }
        body.addView(dpadColumn, LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(actionColumn, LinearLayout.LayoutParams(0, -1, 1f))
        content.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun buildGamepadDpad(palette: LaptopPalette): View = GamepadDpadView(
        context,
        onDirectionChanged = { code, pressed ->
            injectVirtualGamepadButton(code, pressed)
            if (pressed) desk.haptics.performLaptopHaptic(null)
        },
        upCode = PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_UP,
        downCode = PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_DOWN,
        leftCode = PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_LEFT,
        rightCode = PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_RIGHT,
        baseColor = palette.trackpad,
        selectedColor = palette.selected,
        ringColor = palette.border,
        textColor = palette.text,
    ).apply {
        contentDescription = NativeStrings.text("nativeVirtualGamepad")
    }

    private fun buildGamepadFaceButtons(palette: LaptopPalette, cell: Int = desk.dp(42)): GridLayout = GridLayout(context).apply {
        rowCount = 3
        columnCount = 3
        fun empty() = addView(View(context), GridLayout.LayoutParams().apply {
            width = cell
            height = cell
        })
        fun face(label: String, code: Int, labelColor: Int) = addGamepadButton(
            this,
            label,
            code,
            palette,
            GridLayout.LayoutParams().apply {
                width = cell
                height = cell
                setMargins(desk.dp(2), desk.dp(2), desk.dp(2), desk.dp(2))
            },
            fillColor = Color.rgb(18, 18, 21),
            strokeColor = Color.rgb(49, 47, 54),
            labelColor = labelColor,
            pressedFillColor = palette.selected,
            pressedStrokeColor = labelColor,
        )
        empty(); face("Y", PrivilegedInputProtocol.GAMEPAD_BUTTON_Y, Color.rgb(255, 226, 42)); empty()
        face("X", PrivilegedInputProtocol.GAMEPAD_BUTTON_X, Color.rgb(35, 151, 222)); empty(); face("B", PrivilegedInputProtocol.GAMEPAD_BUTTON_B, Color.rgb(232, 48, 57))
        empty(); face("A", PrivilegedInputProtocol.GAMEPAD_BUTTON_A, Color.rgb(67, 195, 91)); empty()
    }

    private fun gamepadCaption(text: String, palette: LaptopPalette): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 9f
        letterSpacing = .08f
        setTextColor(palette.trackpadText)
    }

    private fun gamepadTrigger(
        label: String,
        axisCode: Int,
        buttonCode: Int,
        palette: LaptopPalette,
    ): LinearLayout = LinearLayout(context).apply {
        var buttonPressed = false
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(gamepadCaption(label, palette), LinearLayout.LayoutParams(-1, desk.dp(13)))
            addView(
                GamepadTriggerView(
                    context,
                    horizontal = true,
                    onChange = { value ->
                        injectVirtualGamepadAxis(axisCode, value)
                        val nextButtonPressed = value > 0
                        if (nextButtonPressed != buttonPressed) {
                            buttonPressed = nextButtonPressed
                            injectVirtualGamepadButton(buttonCode, buttonPressed)
                        }
                    },
                    onRelease = {
                        injectVirtualGamepadAxis(axisCode, 0)
                        if (buttonPressed) {
                            buttonPressed = false
                            injectVirtualGamepadButton(buttonCode, false)
                        }
                    },
                    baseColor = palette.trackpad,
                    fillColor = palette.selected,
                    ringColor = palette.border,
                ),
                LinearLayout.LayoutParams(-1, desk.dp(30)),
            )
        }

    private fun addGamepadButton(
        parent: ViewGroup,
        label: String,
        code: Int,
        palette: LaptopPalette,
        params: ViewGroup.LayoutParams,
        fillColor: Int = palette.trackpad,
        strokeColor: Int = palette.border,
        labelColor: Int = palette.text,
        pressedFillColor: Int = palette.selected,
        pressedStrokeColor: Int = pressedFillColor,
        contentDescriptionLabel: String = label,
    ): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        typeface = desk.laptopTypeface
        textSize = if (label.length > 2) 10f else 14f
        setTextColor(labelColor)
        contentDescription = contentDescriptionLabel
        background = gamepadButtonBackground(fillColor, strokeColor, palette.radius)
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.background = gamepadButtonBackground(pressedFillColor, pressedStrokeColor, palette.radius)
                    injectVirtualGamepadButton(code, true)
                    desk.haptics.performLaptopHaptic(view)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.background = gamepadButtonBackground(fillColor, strokeColor, palette.radius)
                    injectVirtualGamepadButton(code, false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
        setOnClickListener { }
        parent.addView(this, params)
    }

    private fun gamepadButtonBackground(fill: Int, stroke: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(desk.opacityColor(fill, .92f))
            setStroke(desk.dp(1), stroke)
            cornerRadius = desk.dp(radius.toInt().coerceAtLeast(6)).toFloat()
        }

    private fun injectVirtualGamepadButton(code: Int, pressed: Boolean) {
        if (!virtualGamepadVisible) return
        if (pressed) virtualGamepadButtons.add(code) else virtualGamepadButtons.remove(code)
        desk.privilegedInputClient.injectGamepad(code, if (pressed) 1 else 0)
    }

    private fun injectVirtualGamepadAxis(code: Int, value: Int) {
        if (!virtualGamepadVisible) return
        virtualGamepadAxes[code] = value
        desk.privilegedInputClient.injectGamepad(code, value)
    }

    internal fun replayVirtualGamepadState() {
        if (!virtualGamepadVisible) return
        virtualGamepadButtons.forEach { code -> desk.privilegedInputClient.injectGamepad(code, 1) }
        virtualGamepadAxes.forEach { (code, value) ->
            desk.privilegedInputClient.injectGamepad(code, value)
        }
    }

    internal fun setVirtualGamepadVisible(visible: Boolean) {
        if (visible && !desk.demoMode && !isExperimentalGamepadEnabled()) return
        if (!visible) releaseVirtualGamepadInputs()
        if (visible == virtualGamepadVisible) {
            desk.privilegedInputClient.setGamepadVisible(visible || desk.coverBackButtonsActive)
            return
        }
        virtualGamepadVisible = visible
        desk.privilegedInputClient.setGamepadVisible(visible || desk.coverBackButtonsActive)
        OperationLog.i(
            context,
            "VirtualGamepad",
            if (visible) "uinput gamepad requested" else "uinput gamepad stopped",
        )
    }

    private fun releaseVirtualGamepadInputs() {
        if (virtualGamepadVisible) {
            virtualGamepadButtons.toList().forEach { code ->
                desk.privilegedInputClient.injectGamepad(code, 0)
            }
            virtualGamepadAxes.keys.toList().forEach { code ->
                desk.privilegedInputClient.injectGamepad(code, 0)
            }
        }
        virtualGamepadButtons.clear()
        virtualGamepadAxes.clear()
    }

    /**
     * Registers a real external keyboard with Android's input stack while the
     * laptop deck is visible. Its descriptor is associated with the Dextop
     * display before key output is enabled, so native uinput events follow the
     * physical-keyboard IME path without relying on global keyboard focus.
     */

    internal fun isExperimentalGamepadEnabled(): Boolean =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getBoolean("flutter.experimental_gamepad", false)

    internal fun isGamepadDeckActive(): Boolean = desk.laptopModeActive &&
            (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEPAD ||
                    desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEBOY)

    internal fun isGamepadStyleAvailable(): Boolean = isExperimentalGamepadEnabled() && (
            desk.demoMode || desk.isLaptopStyleAvailable() || desk.isBlackBerryModeAvailable() ||
                    (desk.laptopModeActive && (
                            desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEPAD ||
                                    desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEBOY
                            ))
            )

    internal fun gamepadActionRow(mainAction: View, edit: () -> Unit): LinearLayout =
        desk.blackBerryActionRow(mainAction, edit)

    internal fun showGamepadLayoutEditor(panel: LinearLayout) {
        if (!isExperimentalGamepadEnabled()) {
            desk.showMainMenu(panel)
            return
        }
        if (!desk.demoMode && (!desk.laptopModeActive ||
                    (desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.GAMEPAD &&
                            desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.GAMEBOY))) {
            desk.setLaptopMode(true, MirrorService.KeyboardDeckStyle.GAMEPAD)
        }
        desk.cast.stopCastRouteDiscovery()
        desk.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(desk.menuTitle(
            NativeStrings.text("nativeGamepadLayout"),
            NativeStrings.text("nativeReturn"),
        ) {
            if (desk.hasKeyboardStyleChoice()) desk.showKeyboardStyleMenu(panel)
            else desk.showMainMenu(panel)
        })
        panel.addView(desk.choiceButton(
            NativeStrings.text("nativeVirtualGamepad"),
            desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEPAD,
        ) {
            if (desk.demoMode) {
                desk.demoExplanation(NativeStrings.text("nativeVirtualGamepadDescription"))
            } else {
                desk.setLaptopMode(true, MirrorService.KeyboardDeckStyle.GAMEPAD)
                showGamepadLayoutEditor(panel)
            }
        }, LinearLayout.LayoutParams(-1, desk.dp(48)).apply { bottomMargin = desk.dp(8) })
        panel.addView(desk.choiceButton(
            NativeStrings.text("nativeGameBoyStyle"),
            desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEBOY,
        ) {
            if (desk.demoMode) {
                desk.demoExplanation(NativeStrings.text("nativeGameBoyStyleDescription"))
            } else {
                desk.setLaptopMode(true, MirrorService.KeyboardDeckStyle.GAMEBOY)
                showGamepadLayoutEditor(panel)
            }
        }, LinearLayout.LayoutParams(-1, desk.dp(48)).apply { bottomMargin = desk.dp(8) })
        desk.scheduleMenuHeightUpdate()
    }
}
