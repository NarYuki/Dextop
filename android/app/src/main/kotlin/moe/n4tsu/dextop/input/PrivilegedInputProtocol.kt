package moe.n4tsu.dextop.input

import android.graphics.Rect

internal object PrivilegedInputProtocol {
    const val VERSION = 3

    // Linux input-event codes used by Dextop Virtual Gamepad. Keeping these
    // in the app-side protocol makes the UI independent from Android's
    // private KeyEvent/gamepad constants while the native side can expose a
    // normal evdev gamepad to Android and Linux games.
    const val GAMEPAD_BUTTON_A = 0x130 // BTN_SOUTH
    const val GAMEPAD_BUTTON_B = 0x131 // BTN_EAST
    // Vendor_045e_Product_028e.kl follows the Xbox 360 layout, where the
    // Android X/Y labels are carried by the evdev NORTH/WEST codes in the
    // opposite order from the generic BTN_* names.
    const val GAMEPAD_BUTTON_X = 0x133 // Xbox BUTTON_X
    const val GAMEPAD_BUTTON_Y = 0x134 // Xbox BUTTON_Y
    const val GAMEPAD_BUTTON_L = 0x136 // BTN_TL
    const val GAMEPAD_BUTTON_R = 0x137 // BTN_TR
    const val GAMEPAD_BUTTON_L2 = 0x138 // BTN_TL2
    const val GAMEPAD_BUTTON_R2 = 0x139 // BTN_TR2
    const val GAMEPAD_BUTTON_SELECT = 0x13a // BTN_SELECT
    const val GAMEPAD_BUTTON_START = 0x13b // BTN_START
    const val GAMEPAD_BUTTON_HOME = 0x13c // BTN_MODE
    const val GAMEPAD_BUTTON_L3 = 0x13d // BTN_THUMBL
    const val GAMEPAD_BUTTON_R3 = 0x13e // BTN_THUMBR
    // Generic.kl maps the ordinary evdev arrow keys to Android DPAD key
    // events. BTN_DPAD_* (0x220..0x223) is only covered by some vendor
    // layouts, so use the portable KEY_* codes for the on-screen D-pad.
    const val GAMEPAD_BUTTON_DPAD_UP = 103 // KEY_UP
    const val GAMEPAD_BUTTON_DPAD_DOWN = 108 // KEY_DOWN
    const val GAMEPAD_BUTTON_DPAD_LEFT = 105 // KEY_LEFT
    const val GAMEPAD_BUTTON_DPAD_RIGHT = 106 // KEY_RIGHT

    const val GAMEPAD_AXIS_LEFT_X = 0x00 // ABS_X
    const val GAMEPAD_AXIS_LEFT_Y = 0x01 // ABS_Y
    // The virtual device uses Android's built-in Xbox 360 key layout. In
    // that layout ABS_Z/ABS_RZ are the two analog triggers and ABS_RX/ABS_RY
    // are the right stick axes (exposed as Android Z/RZ).
    const val GAMEPAD_AXIS_TRIGGER_L = 0x02 // ABS_Z / LTRIGGER
    const val GAMEPAD_AXIS_RIGHT_X = 0x03 // ABS_RX
    const val GAMEPAD_AXIS_RIGHT_Y = 0x04 // ABS_RY
    const val GAMEPAD_AXIS_TRIGGER_R = 0x05 // ABS_RZ / RTRIGGER
    const val GAMEPAD_AXIS_DPAD_X = 0x10 // ABS_HAT0X
    const val GAMEPAD_AXIS_DPAD_Y = 0x11 // ABS_HAT0Y

    const val PROFILE_DISABLED = 0
    const val PROFILE_TOUCHPAD = 1
    const val PROFILE_MOUSE = 2

    const val CONFIG_SIZE = 29
    const val CONFIG_VERSION = 0
    const val CONFIG_PROFILE = 1
    const val CONFIG_ROTATION = 2
    const val CONFIG_HOST_WIDTH = 3
    const val CONFIG_HOST_HEIGHT = 4
    const val CONFIG_FULLSCREEN_LEFT = 5
    const val CONFIG_FULLSCREEN_TOP = 6
    const val CONFIG_FULLSCREEN_RIGHT = 7
    const val CONFIG_FULLSCREEN_BOTTOM = 8
    const val CONFIG_TRACKPAD_LEFT = 9
    const val CONFIG_TRACKPAD_TOP = 10
    const val CONFIG_TRACKPAD_RIGHT = 11
    const val CONFIG_TRACKPAD_BOTTOM = 12
    const val CONFIG_DIRECT_TOUCH = 13
    const val CONFIG_LAPTOP_MODE = 14
    const val CONFIG_TOUCHPAD_MAX_X = 15
    const val CONFIG_TOUCHPAD_MAX_Y = 16
    const val CONFIG_TOUCHPAD_RESOLUTION = 17
    const val CONFIG_DEBUG_ALL_EVENTS = 18
    const val CONFIG_NATURAL_SCROLL = 19
    const val CONFIG_MOUSE_SENSITIVITY_MILLI = 20
    const val CONFIG_TAP_TIMEOUT_MS = 21
    const val CONFIG_DOUBLE_TAP_TIMEOUT_MS = 22
    const val CONFIG_TAP_SLOP_MILLI = 23
    const val CONFIG_GENERATION = 24
    const val CONFIG_IME_LEFT = 25
    const val CONFIG_IME_TOP = 26
    const val CONFIG_IME_RIGHT = 27
    const val CONFIG_IME_BOTTOM = 28

    fun buildConfig(
        profile: Int,
        rotation: Int,
        hostWidth: Int,
        hostHeight: Int,
        fullscreen: Rect?,
        trackpad: Rect?,
        directTouch: Boolean,
        laptopMode: Boolean,
        touchpadMaxX: Int,
        touchpadMaxY: Int,
        touchpadResolution: Int,
        debugAllEvents: Boolean,
        naturalScroll: Boolean,
        mouseSensitivity: Float,
        generation: Int,
        imeTouch: Rect?
    ): IntArray = IntArray(CONFIG_SIZE).apply {
        this[CONFIG_VERSION] = VERSION
        this[CONFIG_PROFILE] = profile
        this[CONFIG_ROTATION] = rotation
        this[CONFIG_HOST_WIDTH] = hostWidth.coerceAtLeast(1)
        this[CONFIG_HOST_HEIGHT] = hostHeight.coerceAtLeast(1)
        this[CONFIG_FULLSCREEN_LEFT] = fullscreen?.left ?: 0
        this[CONFIG_FULLSCREEN_TOP] = fullscreen?.top ?: 0
        this[CONFIG_FULLSCREEN_RIGHT] = fullscreen?.right ?: 0
        this[CONFIG_FULLSCREEN_BOTTOM] = fullscreen?.bottom ?: 0
        this[CONFIG_TRACKPAD_LEFT] = trackpad?.left ?: 0
        this[CONFIG_TRACKPAD_TOP] = trackpad?.top ?: 0
        this[CONFIG_TRACKPAD_RIGHT] = trackpad?.right ?: 0
        this[CONFIG_TRACKPAD_BOTTOM] = trackpad?.bottom ?: 0
        this[CONFIG_DIRECT_TOUCH] = if (directTouch) 1 else 0
        this[CONFIG_LAPTOP_MODE] = if (laptopMode) 1 else 0
        this[CONFIG_TOUCHPAD_MAX_X] = touchpadMaxX
        this[CONFIG_TOUCHPAD_MAX_Y] = touchpadMaxY
        this[CONFIG_TOUCHPAD_RESOLUTION] = touchpadResolution
        this[CONFIG_DEBUG_ALL_EVENTS] = if (debugAllEvents) 1 else 0
        this[CONFIG_NATURAL_SCROLL] = if (naturalScroll) 1 else 0
        this[CONFIG_MOUSE_SENSITIVITY_MILLI] = (mouseSensitivity * 1_000f).toInt()
        this[CONFIG_TAP_TIMEOUT_MS] = 250
        this[CONFIG_DOUBLE_TAP_TIMEOUT_MS] = 300
        this[CONFIG_TAP_SLOP_MILLI] = 18
        this[CONFIG_GENERATION] = generation
        this[CONFIG_IME_LEFT] = imeTouch?.left ?: 0
        this[CONFIG_IME_TOP] = imeTouch?.top ?: 0
        this[CONFIG_IME_RIGHT] = imeTouch?.right ?: 0
        this[CONFIG_IME_BOTTOM] = imeTouch?.bottom ?: 0
    }
}
