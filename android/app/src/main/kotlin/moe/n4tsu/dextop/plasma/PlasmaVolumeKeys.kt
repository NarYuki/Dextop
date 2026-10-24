package moe.n4tsu.dextop.plasma

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Handle system volume before forwarding input to an application display. */
internal object PlasmaVolumeKeys {
    fun handle(context: Context, event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            KeyEvent.KEYCODE_VOLUME_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && !event.isCanceled &&
            (direction != AudioManager.ADJUST_TOGGLE_MUTE || event.repeatCount == 0)) {
            context.getSystemService(AudioManager::class.java).adjustStreamVolume(
                AudioManager.STREAM_MUSIC, direction, 0)
        }
        return true
    }
}
