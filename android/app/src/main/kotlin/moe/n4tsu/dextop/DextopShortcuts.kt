package moe.n4tsu.dextop

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.flutter.plugin.common.MethodChannel

/**
 * Launcher shortcuts that toggle the Dextop virtual display without opening
 * the app first.
 *
 * The long-press launcher shortcut and the pinned home screen shortcut share
 * one shortcut id and one intent action, so both entry points reach
 * [MainActivity] with the same contract: the Flutter side starts the display
 * with the saved settings, or stops a running session.
 */
internal object DextopShortcuts {
    private const val TAG = "DextopShortcuts"
    private const val SHORTCUT_ID = "dextop_start_display"
    private const val PIN_RESULT_ACTION = "moe.n4tsu.dextop.action.SHORTCUT_PIN_RESULT"
    private const val PIN_TIMEOUT_MS = 60_000L

    /** Action carried by the shortcut intent that toggles the session. */
    const val ACTION_START_DISPLAY = "app.freedextop.action.START_DISPLAY"

    /** Set on the shortcut intent when the activity must not stay on screen. */
    private const val EXTRA_CLOSE_AFTER_START = "app.freedextop.extra.CLOSE_AFTER_START"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pinContext: Context? = null
    private var pinReceiver: BroadcastReceiver? = null
    private var pinChannel: MethodChannel? = null
    private val pinTimeout = Runnable {
        // A launcher that never answers must not keep the request open: report
        // the outcome as unknown and drop the receiver.
        Log.i(TAG, "shortcut pin confirmation timed out")
        reportPinResult(null)
    }

    fun isStartDisplayAction(action: String?): Boolean = action == ACTION_START_DISPLAY

    /** True when the intent asks the activity to close itself after starting. */
    fun closesAfterStart(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_CLOSE_AFTER_START, false) == true

    fun startDisplayIntent(context: Context, closeAfterStart: Boolean = false): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_DISPLAY
            putExtra(EXTRA_CLOSE_AFTER_START, closeAfterStart)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    /** Publishes the long-press launcher shortcut once per label change. */
    fun publish(context: Context) {
        val shortcut = buildShortcut(context)
        val existing = ShortcutManagerCompat.getDynamicShortcuts(context)
            .firstOrNull { it.id == SHORTCUT_ID }
        runCatching {
            when {
                existing == null ->
                    ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
                existing.shortLabel != shortcut.shortLabel ||
                    existing.longLabel != shortcut.longLabel ->
                    ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
            }
        }.onFailure { Log.w(TAG, "publishing the launcher shortcut failed", it) }
    }

    private fun buildShortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(NativeStrings.text("nativeShortcutStartDisplay"))
            .setLongLabel(NativeStrings.text("nativeShortcutStartDisplayLong"))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_dextop))
            .setIntent(startDisplayIntent(context))
            .build()

    /**
     * Asks the launcher to place the shortcut on the home screen.
     *
     * [result] only reports whether the launcher accepted the request; the
     * confirmed outcome arrives as a `shortcutPinResult` method call once the
     * user answers the confirmation dialog (or when it times out).
     */
    fun requestPin(activity: Activity, channel: MethodChannel, result: MethodChannel.Result) {
        val context = activity.applicationContext
        if (pinReceiver != null) {
            result.error(
                "shortcut_pending",
                "A launcher shortcut request is still waiting for confirmation",
                null,
            )
            return
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            result.error(
                "shortcut_unsupported",
                "This launcher does not support pinned shortcuts",
                null,
            )
            return
        }
        val callback = pinCallbackIntent(context)
        pinContext = context
        pinChannel = channel
        registerPinReceiver(context)
        mainHandler.postDelayed(pinTimeout, PIN_TIMEOUT_MS)
        val accepted = runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, buildShortcut(context), callback)
        }.getOrDefault(false)
        if (!accepted) {
            // The launcher refused the request before showing any confirmation.
            release()
            result.error(
                "shortcut_unsupported",
                "This launcher rejected the pinned shortcut request",
                null,
            )
            return
        }
        result.success(true)
    }

    /** Releases a pending pin request, for example when Flutter goes away. */
    fun release() {
        mainHandler.removeCallbacks(pinTimeout)
        val context = pinContext
        val receiver = pinReceiver
        if (context != null && receiver != null) {
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { Log.w(TAG, "unregistering the shortcut pin receiver failed", it) }
        }
        pinContext = null
        pinReceiver = null
        pinChannel = null
    }

    private fun registerPinReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // ShortcutManager invokes this callback only after successful pinning.
                // Its result code is 0, not Activity.RESULT_OK; cancellation has no callback.
                Log.i(TAG, "shortcut pinned")
                reportPinResult(true)
            }
        }
        val filter = IntentFilter(PIN_RESULT_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        pinReceiver = receiver
    }

    private fun pinCallbackIntent(context: Context): IntentSender {
        val intent = Intent(PIN_RESULT_ACTION).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                },
        )
        return pending.intentSender
    }

    private fun reportPinResult(pinned: Boolean?) {
        val channel = pinChannel
        release()
        if (channel == null) return
        runCatching { channel.invokeMethod("shortcutPinResult", mapOf("pinned" to pinned)) }
            .onFailure { Log.w(TAG, "reporting the shortcut pin result failed", it) }
    }
}
