package moe.n4tsu.dextop.input

import android.graphics.Rect

internal object PrivilegedInputProtocol {
    const val VERSION = 1

    const val PROFILE_DISABLED = 0
    const val PROFILE_TOUCHPAD = 1
    const val PROFILE_MOUSE = 2

    const val CONFIG_SIZE = 25
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
        generation: Int
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
    }
}
