package moe.n4tsu.dextop

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.widget.TextView

internal class PerformanceHud(
    context: Context,
    private val inputMode: () -> String
) : TextView(context), Choreographer.FrameCallback {
    private var frames = 0
    private var windowStarted = SystemClock.elapsedRealtime()
    private var measuredFps = 0.0
    private val battery = context.getSystemService(BatteryManager::class.java)

    init {
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.START
        setPadding(20, 12, 20, 12)
        background = GradientDrawable().apply {
            setColor(Color.argb(190, 20, 20, 24))
            cornerRadius = 18f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        frames++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - windowStarted
        if (elapsed >= 1000) {
            measuredFps = frames * 1000.0 / elapsed
            frames = 0
            windowStarted = now
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576
            val batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .coerceIn(0, 100)
            val currentMicroamps = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val voltageMillivolts = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val watts = kotlin.math.abs(currentMicroamps.toDouble()) * voltageMillivolts / 1_000_000_000.0
            val power = if (currentMicroamps != Int.MIN_VALUE && voltageMillivolts > 0) {
                "%.2f W".format(watts)
            } else {
                "-- W"
            }
            val localizedInputMode = when (val mode = inputMode()) {
                "mouse" -> NativeStrings.text("nativeInputMouse")
                "touch" -> NativeStrings.text("nativeInputTouch")
                "trackpad" -> NativeStrings.text("nativeInputTrackpad")
                "idle" -> NativeStrings.text("nativeInputIdle")
                else -> mode
            }
            text = NativeStrings.text("nativePerformanceHudFormat").format(
                measuredFps,
                usedMemory,
                batteryPercent,
                power,
                localizedInputMode,
                CpuTemperature.formatted()
            )
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun fps(): Double = measuredFps
}
