package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.text.TextUtils
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.util.Base64
import android.view.Gravity
import android.view.Display
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import moe.n4tsu.dextop.input.SwipeObservingKeyboardLayout
import kotlin.math.max

/** What [KeyboardDeck] needs from the session beyond the shared interfaces. */
internal interface KeyboardDeckHost : DesktopSession, OverlayMenu {
    fun activateLaptopTrackpad()
    fun applyLaptopGeometryWhenLaidOut(enabled: Boolean, attempt: Int = 0, baseOverride: MirrorService.Config? = null, preserveMenu: Boolean = false)
    val cast: CastController
    fun configForHostGeometry(base: MirrorService.Config, hostWidth: Int, hostHeight: Int, systemDensity: Int): MirrorService.Config
    val cursorView: CursorView?
    val gamepad: VirtualGamepad
    val haptics: Haptics
    fun hasKeyboardStyleChoice(): Boolean
    fun injectKey(keyCode: Int, metaState: Int = 0)
    val inputDispatcher: InputDispatcher
    val keyboardDeckStyle: MirrorService.KeyboardDeckStyle
    val laptopModifiers: LaptopModifierState
    val laptopTheme: LaptopThemeSettings
    val menuWidgets: MenuWidgets
    val mirror: MirrorLink
    val physicalInput: PhysicalInput
    val posture: LaptopPosture
    fun resizeActiveDisplay(next: MirrorService.Config, reason: String)
    fun setLaptopMode(enabled: Boolean, requestedStyle: MirrorService.KeyboardDeckStyle = MirrorService.KeyboardDeckStyle.LAPTOP)
    fun showKeyboardStyleMenu(panel: LinearLayout)
    val swipeTyping: SwipeTyping
    fun trackpad(event: MotionEvent, sourceView: View, forceCursorMode: Boolean = false, allowVirtualPointer: Boolean = false, hapticView: View? = null): Boolean
}

internal data class LaptopKeyPressState(
    val keyCode: Int,
    val metaState: Int,
    var repeatCount: Int = 0,
    var repeater: Runnable? = null
)

/** The on-screen keyboard deck: building its keys, tracking presses and injecting them as a hardware keyboard. */
internal class KeyboardDeck(
    private val context: Context,
    private val desk: KeyboardDeckHost,
) {
    internal var laptopContent: LinearLayout? = null

    internal var laptopDeck: View? = null

    internal var laptopDeckContent: LinearLayout? = null

    internal var laptopPreviewThemeId: String? = null

    internal var laptopSettingsVisible = false

    private var laptopFunctionRowVisible = false

    internal var laptopKeyboardView: SwipeObservingKeyboardLayout? = null

    internal var laptopLanguagePopup: View? = null

    internal val laptopInputExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-laptop-input").apply { isDaemon = true }
    }

    internal val laptopKeyExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-laptop-keys").apply { isDaemon = true }
    }

    internal var laptopFnButton: TextView? = null

    internal var laptopMenuButton: TextView? = null

    internal var laptopTrackpadView: View? = null

    internal var laptopKeyboardRequested = false

    internal var laptopKeyboardReady = false

    private var laptopKeyboardAssociationPending = false

    internal var laptopKeyboardDeviceId = -1

    internal var laptopKeyboardDisplayId = -1

    private var laptopKeyboardDisplayUniqueId: String? = null

    private var laptopKeyboardDescriptor: String? = null

    internal var laptopKeyboardGeneration = 0L

    /** Modifier state and palette the key caps were last drawn for; -1 forces a redraw. */
    private var drawnLaptopModifierBits = -1

    private var drawnLaptopModifierPalette: LaptopPalette? = null

    internal val laptopTypeface: Typeface by lazy {
        Typeface.createFromAsset(context.assets, "fonts/HarmonyOS_Sans_Medium.ttf")
    }

    private val laptopModifierButtons = mutableMapOf<Int, MutableList<TextView>>()

    private val laptopShortcutButtons = mutableMapOf<Int, TextView>()

    private val laptopKeyPresses = mutableMapOf<View, LaptopKeyPressState>()

    private var blackBerryNavigationRepeater: Runnable? = null

    internal val laptopLegendButtons = mutableListOf<Pair<LaptopKeyTextView, String>>()

    private fun blackBerryDeckFraction(): Float {
        val prefs = context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MirrorService.KEY_BLACKBERRY_HEIGHT_MANUAL, false)) {
            return prefs.getInt(MirrorService.KEY_BLACKBERRY_HEIGHT_PERCENT, 34).coerceIn(30, 50) / 100f
        }
        val frame = laptopContent
        val portrait = (frame?.height ?: context.resources.displayMetrics.heightPixels) >
                (frame?.width ?: context.resources.displayMetrics.widthPixels)
        // BlackBerry mode has a permanently reserved control/function row in
        // addition to its four-row keyboard. Keep the original four-row key
        // area intact and add enough height for that fifth row.
        return if (portrait) .34f else .375f
    }

    internal fun keyboardDeckWeight(): Float {
        if (desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.BLACKBERRY) return 1f
        val fraction = blackBerryDeckFraction().coerceIn(.18f, .45f)
        return fraction / (1f - fraction)
    }

    internal fun desktopPaneFraction(): Float = 1f / (1f + keyboardDeckWeight())

    internal fun buildLaptopDeck(): View {
        // The internal IME is a transient swipe-commit bridge only. A service
        // restart or interrupted commit must not leave it selected while the
        // user performs ordinary key taps.
        if (desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.GAMEPAD &&
            desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.GAMEBOY
        ) {
            desk.swipeTyping.laptopSwipeImeCoordinator.ensureExternalImeSelected()
        }
        desk.swipeTyping.hideLaptopSwipeCandidates()
        laptopModifierButtons.clear()
        laptopShortcutButtons.clear()
        laptopLegendButtons.clear()
        desk.laptopModifiers.reset()
        drawnLaptopModifierBits = -1
        val palette = desk.laptopTheme.laptopPalette()
        val deck = FrameLayout(context).apply {
            setBackgroundColor(desk.laptopTheme.opaqueColor(palette.background))
            // The deck is an opaque interaction surface.  Without a handler
            // on its empty areas, a tap between keys can fall through to the
            // mirrored Android surface underneath the keyboard.
            isClickable = true
            setOnTouchListener { _, _ -> true }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) {
                setPadding(0, 0, 0, desk.dp(8))
            } else {
                setPadding(desk.dp(8), desk.dp(8), desk.dp(8), desk.dp(8))
            }
        }
        palette.imageBase64?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    // The theme opacity controls the keyboard-area veil below
                    // the keys. Keep the image itself opaque so changing the
                    // slider actually changes the gaps between keys instead
                    // of fading the entire image twice.
                    alpha = 1f
                    setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && palette.blur > 0f) {
                        setRenderEffect(
                            RenderEffect.createBlurEffect(
                                palette.blur, palette.blur, Shader.TileMode.CLAMP
                            )
                        )
                    }
                }
            }.getOrNull()?.let { image ->
                deck.addView(image, FrameLayout.LayoutParams(-1, -1))
            }
        }
        deck.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.setBackgroundColor(desk.laptopTheme.opacityColor(palette.background, palette.opacity))
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEPAD ||
            desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEBOY
        ) {
            laptopKeyboardView = null
            desk.swipeTyping.laptopCandidateBar = null
            laptopTrackpadView = null
            content.setPadding(desk.dp(10), desk.dp(8), desk.dp(10), desk.dp(8))
            if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEBOY) {
                desk.gamepad.buildVirtualGameboyDeck(content, palette)
            } else {
                desk.gamepad.buildVirtualGamepadDeck(content, palette)
            }
            laptopDeck = deck
            laptopDeckContent = content
            return deck
        }
        val trackpad = TextView(context).apply {
            // The label is a per-theme preference. The entire surface remains
            // an input area whether the label is visible or not.
            text = if (palette.showTrackpadLabel) "TRACKPAD" else ""
            gravity = Gravity.CENTER
            typeface = laptopTypeface
            textSize = 11f
            letterSpacing = .15f
            setTextColor(palette.trackpadText)
            background = GradientDrawable().apply {
                setColor(desk.laptopTheme.opacityColor(palette.trackpad, palette.opacity))
                setStroke(desk.dp(1), palette.border)
                cornerRadius = desk.dp(palette.radius.toInt()).toFloat()
            }
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) desk.activateLaptopTrackpad()
                // The laptop deck is an independent input surface.  It must
                // keep using the kernel touchpad even while the phone surface
                // is in tap/direct-touch mode; only the phone surface is
                // allowed to disconnect the pointer in that mode.
                desk.trackpad(
                    event,
                    sourceView = view,
                    forceCursorMode = true,
                    allowVirtualPointer = true,
                    hapticView = view
                )
            }
        }
        laptopTrackpadView = trackpad
        val candidates = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(desk.dp(4), desk.dp(2), desk.dp(4), desk.dp(2))
        }
        desk.swipeTyping.laptopCandidateBar = candidates
        content.addView(candidates, LinearLayout.LayoutParams(-1, 0).apply {
            bottomMargin = desk.dp(3)
        })
        val keyboard = SwipeObservingKeyboardLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            swipeEnabled = desk.swipeTyping.isLaptopSwipeEnabled()
            twoFingerNavigationEnabled = desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY
            swipeTrailColor = palette.selected
            // Empty space between keys is part of the theme background. It
            // must use the same opacity slider as the rest of the keyboard
            // area, while the opaque deck underneath prevents Android from
            // showing through or receiving those touches.
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnTouchListener { _, _ -> true }
            listener = object : SwipeObservingKeyboardLayout.Listener {
                override fun onSwipeStarted(firstKeyCode: Int, provisionalKeySent: Boolean) {
                    // A deliberately slow swipe can outlive the short deferred
                    // DOWN window. Only that uncommon path needs cleanup;
                    // normal swipes never inject a provisional character.
                    if (provisionalKeySent) desk.injectKey(KeyEvent.KEYCODE_DEL)
                    desk.swipeTyping.hideLaptopSwipeCandidates()
                }

                override fun onSwipeFinished(points: List<PointF>) {
                    val uppercaseAll = desk.laptopModifiers.uppercaseAll
                    val capitalizeFirst = desk.laptopModifiers.shift && !uppercaseAll
                    desk.swipeTyping.decodeLaptopSwipe(points, capitalizeFirst, uppercaseAll)
                    // A tapped Shift is one-shot. A Shift finger physically
                    // held during the swipe remains active until its ACTION_UP.
                    desk.laptopModifiers.releaseLatchedShift()
                    refreshLaptopModifierKeys()
                }

                override fun onSwipeCancelled() {
                    desk.swipeTyping.hideLaptopSwipeCandidates()
                }

                override fun onTwoFingerNavigationStarted(firstKeyCode: Int) {
                    // The first finger's DOWN already reached its child key.
                    // Remove that provisional printable character when the
                    // second finger turns the sequence into navigation.
                    if (firstKeyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
                        firstKeyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9
                    ) {
                        desk.injectKey(KeyEvent.KEYCODE_DEL)
                    }
                }

                override fun onTwoFingerDirectionChanged(keyCode: Int) {
                    if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                        stopBlackBerryNavigationRepeat()
                    } else {
                        startBlackBerryNavigationRepeat(keyCode)
                    }
                }

                override fun onTwoFingerNavigationFinished() {
                    stopBlackBerryNavigationRepeat()
                }
            }
        }
        laptopKeyboardView = keyboard
        val keyboardRows = if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) {
            LaptopKeyboardLayout.blackBerryRows(laptopFunctionRowVisible)
        } else {
            LaptopKeyboardLayout.laptopRows(laptopFunctionRowVisible)
        }
        keyboardRows.forEachIndexed { index, keys ->
            val row = buildLaptopKeyboardRow(keys)
            if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) {
                row.setPadding(desk.dp(8), 0, desk.dp(8), 0)
                if (index == 0) {
                    row.tag = "blackberry_control_row"
                    styleBlackBerryTopRow(row, management = !laptopFunctionRowVisible)
                }
            }
            if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.LAPTOP &&
                laptopFunctionRowVisible && index == 0
            ) row.tag = "laptop_function_row"
            val rowWeight = if (
                desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY && index == 0
            ) .7f else 1f
            keyboard.addView(row, LinearLayout.LayoutParams(-1, 0, rowWeight))
        }
        content.addView(
            keyboard,
            LinearLayout.LayoutParams(
                -1,
                0,
                if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) 1f else .66f,
            ).apply {
                bottomMargin = if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) 0 else desk.dp(7)
            },
        )
        if (desk.swipeTyping.isLaptopSwipeEnabled()) keyboard.post { desk.swipeTyping.prewarmLaptopSwipeDecoder() }
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) {
            laptopDeck = deck
            laptopDeckContent = content
            return deck
        }
        val trackpadArea = FrameLayout(context).apply {
            addView(trackpad, FrameLayout.LayoutParams(-1, -1))
            // These are deck controls, not keyboard keys.  Keeping them as
            // siblings of the raw-touchpad surface preserves their original
            // lower-corner positions and prevents the EventHub bridge from
            // treating their taps as pointer gestures.
            addView(TextView(context).apply {
                text = localText("uiKeyFn")
                typeface = laptopTypeface
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(235, 231, 239))
                background = laptopKeyBackground(laptopFunctionRowVisible, LaptopKeyboardLayout.ALT)
                setOnLongClickListener {
                    if (desk.demoMode) return@setOnLongClickListener false
                    desk.laptopTheme.showLaptopKeyboardSettings()
                    true
                }
                setOnClickListener {
                    desk.haptics.performLaptopHaptic(this)
                    setLaptopFunctionRowVisible(!laptopFunctionRowVisible)
                }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> view.animate()
                            .scaleX(.92f).scaleY(.92f).alpha(.72f)
                            .setDuration(55).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                            .scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(110).start()
                    }
                    false
                }
                laptopFnButton = this
            }, FrameLayout.LayoutParams(desk.dp(58), desk.dp(42), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = desk.dp(10)
                bottomMargin = desk.dp(10)
            })
            addView(TextView(context).apply {
                text = localText("uiKeyMenu")
                typeface = laptopTypeface
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(235, 231, 239))
                background = laptopKeyBackground(false, LaptopKeyboardLayout.ALT)
                setOnClickListener {
                    if (!desk.demoMode) {
                        desk.haptics.performLaptopHaptic(this)
                        desk.toggleMenu()
                    }
                }
                setOnLongClickListener {
                    if (desk.demoMode) return@setOnLongClickListener false
                    desk.haptics.performLaptopHaptic(this, strong = true)
                    desk.laptopTheme.showLaptopLanguagePopup(this)
                    true
                }
                laptopMenuButton = this
            }, FrameLayout.LayoutParams(desk.dp(58), desk.dp(42), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = desk.dp(10)
                bottomMargin = desk.dp(10)
            })
        }
        content.addView(trackpadArea, LinearLayout.LayoutParams(-1, 0, .34f))
        laptopDeck = deck
        laptopDeckContent = content
        return deck
    }

    private fun setLaptopFunctionRowVisible(visible: Boolean) {
        if (visible == laptopFunctionRowVisible) return
        val keyboard = laptopKeyboardView ?: return
        laptopFunctionRowVisible = visible
        laptopFnButton?.background = laptopKeyBackground(visible, LaptopKeyboardLayout.FN)
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) {
            val previous = keyboard.findViewWithTag<View>("blackberry_control_row") ?: return
            val index = keyboard.indexOfChild(previous).coerceAtLeast(0)
            val replacement = buildLaptopKeyboardRow(LaptopKeyboardLayout.blackBerryTopRow(laptopFunctionRowVisible)).apply {
                tag = "blackberry_control_row"
                setPadding(desk.dp(8), 0, desk.dp(8), 0)
                styleBlackBerryTopRow(this, management = !visible)
                alpha = 0f
                translationY = -desk.dp(10).toFloat()
            }
            keyboard.removeView(previous)
            keyboard.addView(replacement, index, LinearLayout.LayoutParams(-1, 0, .7f))
            replacement.animate().alpha(1f).translationY(0f).setDuration(160).start()
            refreshLaptopModifierKeys()
            return
        }
        if (visible) {
            TransitionManager.beginDelayedTransition(
                keyboard,
                ChangeBounds().apply { duration = 200 }
            )
            val row = buildLaptopKeyboardRow(LaptopKeyboardLayout.laptopRows(true).first()).apply {
                tag = "laptop_function_row"
                alpha = 0f
                translationY = -desk.dp(24).toFloat()
            }
            keyboard.addView(row, 0, LinearLayout.LayoutParams(-1, 0, 1f))
            row.animate().alpha(1f).translationY(0f).setDuration(200).start()
        } else {
            val row = keyboard.findViewWithTag<View>("laptop_function_row") ?: return
            row.animate().alpha(0f).translationY(-desk.dp(24).toFloat()).setDuration(170)
                .withEndAction {
                    TransitionManager.beginDelayedTransition(
                        keyboard,
                        ChangeBounds().apply { duration = 190 }
                    )
                    keyboard.removeView(row)
                }.start()
        }
    }

    private fun buildLaptopKeyboardRow(keys: List<LaptopKey>): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            keys.forEach { key ->
                addView(
                    laptopKeyButton(key),
                    LinearLayout.LayoutParams(0, -1, key.weight).apply {
                        setMargins(desk.dp(2), desk.dp(2), desk.dp(2), desk.dp(2))
                    }
                )
            }
        }

    internal fun startLaptopHardwareKeyboard() {
        if (!laptopKeyboardRequested) {
            laptopKeyboardRequested = true
            laptopKeyboardGeneration += 1
        }
        desk.privilegedInputClient.setKeyboardVisible(true)
        scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
        OperationLog.i(
            context,
            "LaptopMode",
            "requested native external keyboard; waiting for InputReader publication"
        )
    }

    internal fun scheduleLaptopKeyboardReadyCheck(generation: Long, attempt: Int) {
        val check = Runnable {
            if (generation != laptopKeyboardGeneration || !laptopKeyboardRequested || !MirrorService.active) {
                return@Runnable
            }
            val displayId = desk.targetDisplayId
            val display = if (displayId >= 0) {
                context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            } else {
                null
            }
            val device = findLaptopKeyboardDevice()
            if (device != null && display != null) {
                val displayUniqueId = runCatching {
                    Display::class.java.getMethod("getUniqueId").invoke(display) as String
                }.getOrNull()
                if (laptopKeyboardReady && laptopKeyboardDeviceId == device.id &&
                    laptopKeyboardDisplayId == displayId &&
                    laptopKeyboardDisplayUniqueId == displayUniqueId &&
                    desk.physicalInput.physicalInputRouter.isDeviceRouted(device, display)
                ) {
                    return@Runnable
                }
                if (desk.physicalInput.physicalInputRouter.isDeviceRouted(device, display)) {
                    laptopKeyboardReady = true
                    laptopKeyboardAssociationPending = false
                    laptopKeyboardDeviceId = device.id
                    laptopKeyboardDisplayId = displayId
                    laptopKeyboardDisplayUniqueId = displayUniqueId
                    laptopKeyboardDescriptor = device.descriptor
                    val message = "native keyboard ready deviceId=${device.id} displayId=$displayId " +
                            "vendor=${device.vendorId} product=${device.productId} " +
                            "sources=0x${device.sources.toString(16)}"
                    Log.i(desk.logTag, "$message descriptor=${device.descriptor}")
                    OperationLog.i(context, "LaptopKeyboard", message)
                    return@Runnable
                }
                laptopKeyboardReady = false
                if (!laptopKeyboardAssociationPending) {
                    laptopKeyboardAssociationPending = desk.physicalInput.physicalInputRouter.routeDevice(device, display)
                }
            }
            if (attempt == 0 || attempt == 5 || attempt == 10 || attempt == 20) {
                Log.i(
                    desk.logTag,
                    "waiting for native laptop keyboard attempt=$attempt/20 " +
                            "displayId=$displayId candidates=${laptopKeyboardPublicationCandidates()}"
                )
            }
            if (attempt < 20) {
                scheduleLaptopKeyboardReadyCheck(generation, attempt + 1)
            } else {
                OperationLog.w(
                    context,
                    "LaptopKeyboard",
                    "native keyboard was not associated with display=$displayId after ${attempt + 1} probes"
                )
            }
        }
        desk.root?.postDelayed(check, if (attempt == 0) 40L else 100L)
            ?: Handler(context.mainLooper).postDelayed(check, if (attempt == 0) 40L else 100L)
    }

    private fun findLaptopKeyboardDevice(): InputDevice? = InputDevice.getDeviceIds()
        .asSequence()
        .mapNotNull(InputDevice::getDevice)
        .firstOrNull { device ->
            device.name == MirrorService.LAPTOP_KEYBOARD_NAME &&
                    device.vendorId == MirrorService.LAPTOP_KEYBOARD_VENDOR_ID &&
                    device.productId == MirrorService.LAPTOP_KEYBOARD_PRODUCT_ID &&
                    device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        }

    private fun laptopKeyboardPublicationCandidates(): String {
        val candidates = InputDevice.getDeviceIds().asSequence()
            .mapNotNull(InputDevice::getDevice)
            .filter { it.name == MirrorService.LAPTOP_KEYBOARD_NAME || it.name.startsWith("Dextop Laptop") }
            .toList()
        return if (candidates.isEmpty()) {
            "none"
        } else {
            candidates.joinToString(prefix = "[", postfix = "]") { device ->
                "id=${device.id},name=${device.name},vendor=${device.vendorId}," +
                        "product=${device.productId},sources=0x${device.sources.toString(16)}"
            }
        }
    }

    internal fun clearLaptopKeyboardPublication(reason: String) {
        val descriptor = laptopKeyboardDescriptor
        if (descriptor != null) desk.physicalInput.physicalInputRouter.restoreDeviceDescriptor(descriptor)
        if (laptopKeyboardDeviceId >= 0 || laptopKeyboardReady) {
            Log.i(
                desk.logTag,
                "native laptop keyboard cleared reason=$reason deviceId=$laptopKeyboardDeviceId " +
                        "displayId=$laptopKeyboardDisplayId"
            )
        }
        laptopKeyboardReady = false
        laptopKeyboardAssociationPending = false
        laptopKeyboardDeviceId = -1
        laptopKeyboardDisplayId = -1
        laptopKeyboardDisplayUniqueId = null
        laptopKeyboardDescriptor = null
    }

    internal fun stopLaptopHardwareKeyboard() {
        finishAllLaptopKeyPresses()
        laptopKeyboardRequested = false
        laptopKeyboardGeneration += 1
        clearLaptopKeyboardPublication("laptop_keyboard_stopped")
        desk.privilegedInputClient.setKeyboardVisible(false)
    }

    internal fun refreshLaptopVirtualDisplayAfterLayout(attempt: Int = 0) {
        val expectedMode = desk.laptopModeActive
        desk.root?.postDelayed({
            if (!MirrorService.active || !expectedMode || !desk.laptopModeActive || desk.targetDisplayId < 0) {
                if (attempt < 4 && desk.laptopModeActive) {
                    refreshLaptopVirtualDisplayAfterLayout(attempt + 1)
                }
                return@postDelayed
            }
            val surface = desk.surfaceView ?: return@postDelayed
            if (surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) {
                if (attempt < 4) refreshLaptopVirtualDisplayAfterLayout(attempt + 1)
                return@postDelayed
            }
            val paneConfig = desk.configForHostGeometry(
                MirrorService.Config(desk.targetWidth, desk.targetHeight, desk.density, desk.secureDisplay, desk.showSystemDecorations),
                surface.width,
                surface.height,
                context.resources.configuration.densityDpi
            )
            if (paneConfig.width != desk.targetWidth || paneConfig.height != desk.targetHeight ||
                paneConfig.density != desk.density
            ) {
                desk.resizeActiveDisplay(paneConfig, "laptop pane final synchronization")
            }
            // The pane-layout pass already schedules a mirror update. Avoid a
            // second attach of WindowManager/SurfaceControl mirrors when the
            // measured host has not changed; recreating that layer is visible
            // as a one-frame flash behind the keyboard deck.
            if (desk.mirror.mirrorDisplayId == desk.targetDisplayId &&
                desk.mirror.mirrorHostWidth == surface.width && desk.mirror.mirrorHostHeight == surface.height
            ) {
                return@postDelayed
            }
            runCatching { desk.mirror.attachMirror(surface.width, surface.height, "virtual_display") }
                .onSuccess {
                    desk.mirror.mirrorDisplayId = desk.targetDisplayId
                    Log.i(
                        desk.logTag,
                        "laptop VirtualDisplay refreshed attempt=$attempt " +
                                "host=${surface.width}x${surface.height} content=${desk.targetWidth}x${desk.targetHeight}"
                    )
                    OperationLog.i(
                        context,
                        "LaptopMode",
                        "VirtualDisplay recreated for pane=${surface.width}x${surface.height} " +
                                "content=${desk.targetWidth}x${desk.targetHeight}"
                    )
                }
                .onFailure {
                    Log.e(desk.logTag, "laptop VirtualDisplay refresh failed attempt=$attempt", it)
                    OperationLog.e(context, "LaptopMode", "VirtualDisplay pane refresh failed", it)
                    if (attempt < 4) refreshLaptopVirtualDisplayAfterLayout(attempt + 1)
                }
        }, 700L + attempt * 350L)
    }

    private val laptopShortcutLabels = mapOf(
        KeyEvent.KEYCODE_C to localText("uiCopy"),
        KeyEvent.KEYCODE_V to moe.n4tsu.dextop.localText("uiPaste"),
        KeyEvent.KEYCODE_X to moe.n4tsu.dextop.localText("uiCut"),
        KeyEvent.KEYCODE_A to localText("uiSelectAll"),
        KeyEvent.KEYCODE_Z to moe.n4tsu.dextop.localText("uiUndo"),
        KeyEvent.KEYCODE_S to localText("uiSave"),
        KeyEvent.KEYCODE_N to moe.n4tsu.dextop.localText("uiNew"),
        KeyEvent.KEYCODE_P to localText("uiPrint"),
        KeyEvent.KEYCODE_F to moe.n4tsu.dextop.localText("uiFind"),
        KeyEvent.KEYCODE_W to localText("uiClose")
    )

    private fun laptopKeyButton(key: LaptopKey): TextView = LaptopKeyTextView(
        context,
        desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.BLACKBERRY &&
                (key.code == KeyEvent.KEYCODE_F || key.code == KeyEvent.KEYCODE_J),
        desk.laptopTheme.laptopPalette().text
    ).apply {
        val palette = desk.laptopTheme.laptopPalette()
        // A new key cap has not been drawn for the current modifier state yet.
        drawnLaptopModifierBits = -1
        val blackBerryLegend = if (
            desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY && '\n' in key.label
        ) key.label.split('\n', limit = 2) else null
        val displayedLabel = if (blackBerryLegend != null) {
            // Keep the primary letter on the exact same baseline as every
            // other key. The printed symbol is an independent overlay above
            // it, not a second TextView line that shifts both lines down.
            blackBerryLegend[1]
        } else if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) {
            key.label
        } else {
            desk.swipeTyping.laptopSwipeSymbol(key.code, desk.swipeTyping.laptopSwipeLanguage())?.uppercase() ?: key.label
        }
        text = displayedLabel
        tag = key.code
        val usesCustomGlyph = (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY &&
                key.code == LaptopKeyboardLayout.SHIFT) ||
                (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY &&
                        (key.code == LaptopKeyboardLayout.BACK || key.code == KeyEvent.KEYCODE_ENTER))
        if (key.code != KeyEvent.KEYCODE_META_LEFT && !usesCustomGlyph) {
            laptopLegendButtons += this to displayedLabel
        }
        typeface = laptopTypeface
        textSize = if (displayedLabel.length > 2) 9f else 12f
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY && key.code == LaptopKeyboardLayout.MENU) {
            textSize = 10.8f
        }
        gravity = Gravity.CENTER
        setTextColor(palette.text)
        setTopSecondaryLabel(blackBerryLegend?.firstOrNull(), palette.text)
        background = laptopKeyBackground(false, key.code)
        if (key.code == LaptopKeyboardLayout.SPACER) {
            visibility = View.INVISIBLE
            isClickable = false
            return@apply
        }
        if (key.code == KeyEvent.KEYCODE_META_LEFT) {
            // Do not use a private-use Material Icons code point here.  The
            // Flutter font is not guaranteed to be available to the native
            // overlay on every packaging/runtime combination, and Android's
            // fallback font can render the code point as an unrelated CJK
            // glyph.  The simple 3x3 grid is a deterministic app-launcher
            // affordance and remains legible on every OEM font stack.
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(
                    KeyboardGlyphDrawable.APP_GRID,
                    palette.text
                ),
                if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) .552f else .46f,
            )
        }
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY && key.code == LaptopKeyboardLayout.BACK) {
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(KeyboardGlyphDrawable.BACK, palette.text),
                .552f,
            )
        }
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY &&
            key.code == KeyEvent.KEYCODE_ENTER
        ) {
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(KeyboardGlyphDrawable.ENTER, palette.text),
                .62f,
            )
        }
        if (LaptopModifierState.showsActiveState(key.code)) {
            laptopModifierButtons.getOrPut(key.code) { mutableListOf() } += this
        }
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY && key.code == LaptopKeyboardLayout.SHIFT) {
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(KeyboardGlyphDrawable.SHIFT, palette.text),
                .5f,
            )
        }
        if (key.code == LaptopKeyboardLayout.FN) {
            laptopFnButton = this
            background = laptopKeyBackground(laptopFunctionRowVisible, LaptopKeyboardLayout.FN)
            setOnClickListener {
                desk.haptics.performLaptopHaptic(this)
                setLaptopFunctionRowVisible(!laptopFunctionRowVisible)
            }
            setOnLongClickListener {
                if (desk.demoMode) return@setOnLongClickListener false
                desk.haptics.performLaptopHaptic(this)
                desk.laptopTheme.showLaptopKeyboardSettings()
                true
            }
        } else if (key.code == LaptopKeyboardLayout.MENU) {
            setOnClickListener {
                if (!desk.demoMode) {
                    desk.haptics.performLaptopHaptic(this)
                    desk.toggleMenu()
                }
            }
            setOnLongClickListener {
                if (desk.demoMode) return@setOnLongClickListener false
                desk.haptics.performLaptopHaptic(this, strong = true)
                desk.laptopTheme.showLaptopLanguagePopup(this)
                true
            }
        }
        if (key.code in laptopShortcutLabels.keys) laptopShortcutButtons[key.code] = this
        setOnTouchListener { view, event ->
            if (key.code == LaptopKeyboardLayout.FN || key.code == LaptopKeyboardLayout.MENU) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate()
                        .scaleX(.92f).scaleY(.92f).alpha(.72f)
                        .setDuration(55).start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(110).start()
                }
                // Let TextView dispatch click/long-click. These are Dextop
                // internal actions and must never enter the uinput key path.
                return@setOnTouchListener false
            }
            if (key.code == KeyEvent.KEYCODE_META_LEFT) {
                // Meta is a normal modifier again.  Theme settings are
                // intentionally owned by FN long-press so Meta can be used
                // without an accidental settings transition.
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(.92f).scaleY(.92f).alpha(.72f)
                        .setDuration(55).start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(110).start()
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            desk.haptics.performLaptopHaptic(view)
                            handleLaptopKey(key.code)
                        }
                    }
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Modifiers are latched on press, not release, so a
                    // second finger can press C/V/etc. while Ctrl is still
                    // held. This also makes real multi-touch chords work.
                    desk.haptics.performLaptopHaptic(view)
                    when {
                        LaptopModifierState.latchesOnPress(key.code) -> pressLaptopModifier(key.code)
                        key.code < 0 -> handleLaptopKey(key.code)
                        else -> startLaptopKeyPress(view, key.code)
                    }
                    view.animate()
                        .scaleX(.92f).scaleY(.92f).alpha(.72f)
                        .setDuration(55).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (LaptopModifierState.latchesOnPress(key.code)) {
                        releaseLaptopModifier(key.code)
                    } else if (key.code >= 0) finishLaptopKeyPress(view)
                    view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(110).start()
                }
            }
            true
        }
    }

    private val LAPTOP_SPECIAL_KEYS = setOf(
        KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
    )

    private fun laptopKeyBackground(selected: Boolean, keyCode: Int? = null) = GradientDrawable().apply {
        val palette = desk.laptopTheme.laptopPalette()
        val lockedShift = keyCode == LaptopKeyboardLayout.SHIFT && desk.laptopModifiers.shiftLocked
        val functionKey = keyCode != null &&
                (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ||
                        keyCode == KeyEvent.KEYCODE_FORWARD_DEL)
        val modifierKey = keyCode != null &&
                (keyCode < 0 || keyCode == KeyEvent.KEYCODE_META_LEFT ||
                        keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT)
        val specialKey = keyCode in LAPTOP_SPECIAL_KEYS
        val fill = if (lockedShift) {
            palette.selected
        } else if (selected) {
            palette.selected
        } else {
            when {
                functionKey || modifierKey || specialKey -> palette.keyVariant
                else -> palette.key
            }
        }
        setColor(desk.laptopTheme.opacityColor(fill, palette.keyOpacity))
        setStroke(
            desk.dp(1),
            if (selected || lockedShift) {
                palette.text
            } else {
                palette.border
            }
        )
        cornerRadius = desk.dp(palette.radius.toInt()).toFloat()
    }

    private fun styleBlackBerryTopRow(row: LinearLayout, management: Boolean) {
        val palette = desk.laptopTheme.laptopPalette()
        row.background = if (management) {
            BottomDividerDrawable(
                palette.border,
                desk.dp(1).coerceAtLeast(1),
                desk.dp(2),
            )
        } else {
            null
        }
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            (child.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                if (management) {
                    // Divider directly below the management keys, followed by
                    // 2 dp of space before QWERTY. The divider sits at the top
                    // edge of that space, not in its center.
                    params.topMargin = 0
                    params.bottomMargin = desk.dp(2)
                } else {
                    // The FN row has no divider. Split the normal 4 dp
                    // inter-row spacing evenly so the F keys retain the same
                    // visual gap as every other keyboard row.
                    params.topMargin = desk.dp(2)
                    params.bottomMargin = desk.dp(2)
                }
                child.layoutParams = params
            }
            if (management) child.background = ColorDrawable(Color.TRANSPARENT)
        }
    }

    internal fun rebuildLaptopDeck(showSettings: Boolean = false) {
        val content = laptopContent ?: return
        // Do not fade the whole lower pane: it briefly exposes Android behind
        // the overlay. The replacement is immediate, then the new deck slides in.
        finishAllLaptopKeyPresses()
        desk.laptopTheme.hideLaptopLanguagePopup()
        desk.swipeTyping.hideLaptopSwipeCandidates()
        laptopDeck?.let { content.removeView(it) }
        laptopSettingsVisible = false
        val nextDeck = buildLaptopDeck().apply {
            alpha = 0f
            translationY = desk.dp(28).toFloat()
        }
        content.addView(nextDeck, LinearLayout.LayoutParams(-1, 0, keyboardDeckWeight()))
        nextDeck.post {
            nextDeck.animate()
                .alpha(1f)
                .translationY(0f)
                .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
                .setDuration(320L)
                .start()
        }
        content.requestLayout()
        if (showSettings) content.post { desk.laptopTheme.showLaptopKeyboardSettings() }
    }

    private fun handleLaptopKey(keyCode: Int) {
        if (!desk.laptopModifiers.toggle(keyCode)) {
            if (keyCode == LaptopKeyboardLayout.BACK) {
                injectTargetedBack()
            } else {
                desk.injectKey(keyCode, desk.laptopModifiers.metaState(keyCode))
                desk.laptopModifiers.clearLatched()
            }
        }
        refreshLaptopModifierKeys()
    }

    private fun pressLaptopModifier(keyCode: Int) {
        if (desk.laptopModifiers.press(keyCode)) refreshLaptopModifierKeys()
    }

    private fun releaseLaptopModifier(keyCode: Int) {
        val released = desk.laptopModifiers.release(
            keyCode,
            SystemClock.uptimeMillis(),
            ViewConfiguration.getDoubleTapTimeout().toLong(),
        )
        if (released) refreshLaptopModifierKeys()
    }

    private fun injectTargetedBack() {
        // Binder/input dispatch can block on some One UI builds. Never make a
        // key animation or the rest of the overlay wait for that IPC.
        laptopInputExecutor.execute {
            if (laptopKeyboardReady &&
                injectLaptopKeyboardEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0, 0)
            ) {
                injectLaptopKeyboardEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 0, 0)
                return@execute
            }
            if (desk.targetDisplayId < 0) return@execute
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
            val up = KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
            runCatching {
                check(desk.inputDispatcher.send(down, desk.targetDisplayId))
                check(desk.inputDispatcher.send(up, desk.targetDisplayId))
            }.onFailure { Log.e(desk.logTag, "BlackBerry back injection failed", it) }
        }
    }

    private fun startLaptopKeyPress(view: View, keyCode: Int) {
        desk.swipeTyping.laptopSwipeImeCoordinator.ensureExternalImeSelected()
        finishLaptopKeyPress(view)
        val symbolKey = if (
            desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY && desk.laptopModifiers.symbolMode
        ) LaptopKeyLegends.blackBerrySymbolKey(keyCode) else null
        val effectiveKeyCode = symbolKey?.first ?: keyCode
        var metaState = desk.laptopModifiers.metaState(effectiveKeyCode)
        if (symbolKey?.second == true) metaState = metaState or KeyEvent.META_SHIFT_ON
        if (!injectLaptopKeyboardEvent(
                effectiveKeyCode, KeyEvent.ACTION_DOWN, metaState, 0
            )) return

        // A held modifier is part of a real multi-touch chord and must remain
        // active for every repeat until its own finger is released. A latched
        // modifier that was tapped alone remains one-shot.
        desk.laptopModifiers.consumeForKeyPress(usedSymbol = symbolKey != null)
        refreshLaptopModifierKeys()

        val state = LaptopKeyPressState(effectiveKeyCode, metaState)
        val repeater = object : Runnable {
            override fun run() {
                if (laptopKeyPresses[view] !== state) return
                state.repeatCount += 1
                injectLaptopKeyboardEvent(
                    state.keyCode,
                    KeyEvent.ACTION_DOWN,
                    state.metaState,
                    state.repeatCount
                )
                view.postDelayed(this, ViewConfiguration.getKeyRepeatDelay().toLong())
            }
        }
        state.repeater = repeater
        laptopKeyPresses[view] = state
        view.postDelayed(repeater, ViewConfiguration.getKeyRepeatTimeout().toLong())
    }

    private fun finishLaptopKeyPress(view: View) {
        val state = laptopKeyPresses.remove(view) ?: return
        state.repeater?.let(view::removeCallbacks)
        injectLaptopKeyboardEvent(state.keyCode, KeyEvent.ACTION_UP, state.metaState, 0)
    }

    internal fun finishAllLaptopKeyPresses() {
        laptopKeyPresses.keys.toList().forEach(::finishLaptopKeyPress)
        stopBlackBerryNavigationRepeat()
    }

    private fun startBlackBerryNavigationRepeat(keyCode: Int) {
        stopBlackBerryNavigationRepeat()
        emitBlackBerryNavigationKey(keyCode)
        val repeat = object : Runnable {
            override fun run() {
                if (blackBerryNavigationRepeater !== this) return
                emitBlackBerryNavigationKey(keyCode)
                desk.swipeTyping.laptopSwipeHandler.postDelayed(
                    this,
                    ViewConfiguration.getKeyRepeatDelay().toLong(),
                )
            }
        }
        blackBerryNavigationRepeater = repeat
        desk.swipeTyping.laptopSwipeHandler.postDelayed(
            repeat,
            ViewConfiguration.getKeyRepeatTimeout().toLong(),
        )
    }

    private fun emitBlackBerryNavigationKey(keyCode: Int) {
        if (laptopKeyboardReady &&
            injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_DOWN, 0, 0)
        ) {
            injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_UP, 0, 0)
        } else {
            desk.injectKey(keyCode)
        }
    }

    private fun stopBlackBerryNavigationRepeat() {
        blackBerryNavigationRepeater?.let(desk.swipeTyping.laptopSwipeHandler::removeCallbacks)
        blackBerryNavigationRepeater = null
    }

    /**
     * Redraws the key caps that show modifier state. Every key press ends
     * here, and most leave the modifiers as they were, so an unchanged state
     * and palette skip the pass.
     */

    private fun refreshLaptopModifierKeys() {
        val palette = desk.laptopTheme.laptopPalette()
        val bits = desk.laptopModifiers.visualBits
        if (bits == drawnLaptopModifierBits && palette === drawnLaptopModifierPalette) return
        drawnLaptopModifierBits = bits
        drawnLaptopModifierPalette = palette
        laptopModifierButtons.forEach { (code, buttons) ->
            val selected = desk.laptopModifiers.isActive(code)
            buttons.forEach { it.background = laptopKeyBackground(selected, code) }
        }
        if (desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) {
            laptopModifierButtons[LaptopKeyboardLayout.SHIFT].orEmpty().forEach { button ->
                (button as? LaptopKeyTextView)?.apply {
                    text = ""
                    setCustomGlyph(
                        KeyboardGlyphDrawable(
                            if (desk.laptopModifiers.shiftLocked) KeyboardGlyphDrawable.SHIFT_LOCKED
                            else KeyboardGlyphDrawable.SHIFT,
                            palette.text,
                        ),
                        .5f,
                    )
                }
            }
        }
        refreshLaptopKeyLegends()
        refreshLaptopShortcutLabels()
    }

    private fun refreshLaptopKeyLegends() {
        laptopLegendButtons.forEach { (button, base) ->
            val symbol = LaptopKeyLegends.displayed(base, desk.laptopModifiers)
            if (!TextUtils.equals(button.text, symbol)) button.text = symbol
        }
    }

    private fun refreshLaptopShortcutLabels() {
        val secondaryColor = if (desk.laptopTheme.laptopKeyboardTheme() == "crimson") {
            Color.rgb(230, 143, 105)
        } else Color.rgb(170, 165, 177)
        laptopShortcutButtons.forEach { (keyCode, button) ->
            val primary = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            if (!TextUtils.equals(button.text, primary)) button.text = primary
            (button as? LaptopKeyTextView)?.setSecondaryLabel(
                laptopShortcutLabels[keyCode].takeIf { desk.laptopModifiers.control },
                secondaryColor
            )
        }
    }

    internal fun blackBerryActionRow(mainAction: View, edit: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            mainAction.layoutParams = LinearLayout.LayoutParams(0, desk.dp(50), 1f).apply {
                marginEnd = desk.dp(8)
            }
            addView(mainAction)
            addView(desk.menuWidgets.editButton(edit), LinearLayout.LayoutParams(desk.dp(50), desk.dp(50)))
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, desk.dp(58)).apply { bottomMargin = desk.dp(8) }
        }

    internal fun showBlackBerryLayoutEditor(panel: LinearLayout) {
        if (!desk.demoMode && (!desk.laptopModeActive || desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.BLACKBERRY)) {
            // Opening the editor is also an explicit request to keep the
            // BlackBerry deck visible. Automatic fold-posture evaluation must
            // not replace or dismiss it while the user is editing the layout.
            desk.posture.laptopManualOverride = true
            desk.posture.laptopAutoActivated = false
            desk.posture.pendingLaptopMode = null
            desk.posture.pendingLaptopModeSince = 0L
            desk.posture.laptopModeEvaluationGeneration += 1
            desk.setLaptopMode(true, MirrorService.KeyboardDeckStyle.BLACKBERRY)
        }
        desk.cast.stopCastRouteDiscovery()
        desk.menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(desk.menuWidgets.menuTitle(
            NativeStrings.text("nativeBlackBerryLayout"),
            NativeStrings.text("nativeReturn"),
        ) {
            if (desk.hasKeyboardStyleChoice()) desk.showKeyboardStyleMenu(panel)
            else desk.showMainMenu(panel)
        })

        val prefs = context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE)
        val manual = prefs.getBoolean(MirrorService.KEY_BLACKBERRY_HEIGHT_MANUAL, false)
        panel.addView(desk.menuWidgets.choiceButton(
            NativeStrings.text("nativeAutomaticLayout"),
            !manual,
        ) {
            prefs.edit().putBoolean(MirrorService.KEY_BLACKBERRY_HEIGHT_MANUAL, false).apply()
            applyBlackBerryDeckHeight()
            showBlackBerryLayoutEditor(panel)
        }, LinearLayout.LayoutParams(-1, desk.dp(48)).apply { bottomMargin = desk.dp(8) })
        panel.addView(desk.menuWidgets.choiceButton(
            NativeStrings.text("nativeManualHeight"),
            manual,
        ) {
            prefs.edit().putBoolean(MirrorService.KEY_BLACKBERRY_HEIGHT_MANUAL, true).apply()
            applyBlackBerryDeckHeight()
            showBlackBerryLayoutEditor(panel)
        }, LinearLayout.LayoutParams(-1, desk.dp(48)).apply { bottomMargin = desk.dp(8) })

        if (manual) {
            val percent = prefs.getInt(MirrorService.KEY_BLACKBERRY_HEIGHT_PERCENT, 34).coerceIn(30, 50)
            val valueLabel = TextView(context).apply {
                text = "${NativeStrings.text("nativeKeyboardHeight")}  $percent%"
                textSize = 14f
                setTextColor(Color.rgb(230, 225, 229))
                setPadding(desk.dp(4), desk.dp(8), 0, desk.dp(4))
            }
            panel.addView(valueLabel)
            panel.addView(SeekBar(context).apply {
                max = 20
                progress = percent - 30
                splitTrack = false
                progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(208, 188, 255))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(73, 69, 79))
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(208, 188, 255))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(view: SeekBar?, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val next = value + 30
                        valueLabel.text = "${NativeStrings.text("nativeKeyboardHeight")}  $next%"
                        prefs.edit().putInt(MirrorService.KEY_BLACKBERRY_HEIGHT_PERCENT, next).apply()
                        applyBlackBerryDeckHeight(commitGeometry = false)
                    }
                    override fun onStartTrackingTouch(view: SeekBar?) = Unit
                    override fun onStopTrackingTouch(view: SeekBar?) {
                        applyBlackBerryDeckHeight(commitGeometry = true)
                    }
                })
            }, LinearLayout.LayoutParams(-1, desk.dp(48)).apply { bottomMargin = desk.dp(8) })
        }
        desk.scheduleMenuHeightUpdate()
    }

    private fun applyBlackBerryDeckHeight(commitGeometry: Boolean = true) {
        if (!desk.laptopModeActive || desk.keyboardDeckStyle != MirrorService.KeyboardDeckStyle.BLACKBERRY) return
        val deck = laptopDeck ?: return
        val params = deck.layoutParams as? LinearLayout.LayoutParams ?: return
        params.weight = keyboardDeckWeight()
        deck.layoutParams = params
        desk.cursorView?.contentHeightFraction = desktopPaneFraction()
        laptopContent?.requestLayout()
        if (commitGeometry) {
            laptopContent?.post { desk.applyLaptopGeometryWhenLaidOut(true, preserveMenu = true) }
        }
    }

    private val laptopFallbackKeyDownTimes = ConcurrentHashMap<Int, Long>()

    internal fun injectLaptopKeyboardEvent(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int
    ): Boolean {
        if (desk.desktopEnvironment.directDeckKeyboardToApp &&
            desk.plasmaShell is moe.n4tsu.dextop.plasma.CompositedWindowShell) {
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        // Once a key-down used the targeted path, keep repeats and key-up on
        // that same virtual device even if uinput becomes ready mid-press.
        if (laptopFallbackKeyDownTimes.containsKey(keyCode)) {
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        if (!laptopKeyboardReady) {
            Log.w(
                desk.logTag,
                "native laptop keyboard unavailable; using targeted fallback " +
                        "action=$action repeat=$repeatCount deviceId=$laptopKeyboardDeviceId"
            )
            scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        val injected = desk.privilegedInputClient.injectKeyboard(keyCode, action, metaState, repeatCount)
        if (!injected) {
            Log.w(
                desk.logTag,
                "native laptop keyboard injection failed; using targeted fallback " +
                        "action=$action repeat=$repeatCount deviceId=$laptopKeyboardDeviceId"
            )
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        return true
    }

    /**
     * Keeps the on-screen keyboard usable while a foldable panel hand-off is
     * republishing or re-associating the uinput keyboard. The event is sent
     * directly to Dextop's display instead of depending on global focus.
     */

    private fun injectLaptopKeyboardFallback(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int,
    ): Boolean {
        val displayId = if (desk.desktopEnvironment.directDeckKeyboardToApp) {
            (desk.plasmaShell as? moe.n4tsu.dextop.plasma.CompositedWindowShell)?.hostKeyboardDisplayId() ?: desk.targetDisplayId
        } else desk.targetDisplayId
        if (displayId < 0) return false
        val eventTime = SystemClock.uptimeMillis()
        val downTime = when (action) {
            KeyEvent.ACTION_DOWN -> laptopFallbackKeyDownTimes.computeIfAbsent(keyCode) { eventTime }
            KeyEvent.ACTION_UP -> laptopFallbackKeyDownTimes.remove(keyCode) ?: eventTime
            else -> eventTime
        }
        val event = KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            repeatCount,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            0,
            InputDevice.SOURCE_KEYBOARD,
        )
        if (desk.desktopEnvironment.directDeckKeyboardToApp) {
            // WAIT_FOR_RESULT on the UI thread can wait for this service's own
            // accessibility key callback. Keep DOWN/repeats/UP ordered off-thread.
            return runCatching {
                laptopKeyExecutor.execute {
                    if (!desk.inputDispatcher.send(event, displayId, waitForResult = false)) {
                        Log.e(desk.logTag, "deck key submission failed display=$displayId action=$action")
                    }
                }
                true
            }.getOrDefault(false)
        }
        val accepted = desk.inputDispatcher.send(event, displayId)
        if (!accepted) {
            Log.e(
                desk.logTag,
                "targeted laptop keyboard fallback rejected display=$displayId " +
                        "keyCode=$keyCode action=$action repeat=$repeatCount"
            )
        }
        return accepted
    }
}
