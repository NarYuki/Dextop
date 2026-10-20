package moe.n4tsu.dextop

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that toggles the Dextop virtual display without opening
 * the app: tapping starts a session with the settings saved in the app and
 * then steps back, or stops a running one.
 *
 * Separate from [DextopTileService], which opens the last workspace.
 */
class DextopDisplayTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (MirrorService.isActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (MirrorService.isActive()) NativeStrings.text("nativeTapToExit") else NativeStrings.text("nativeTapToOpen")
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (MirrorService.isActive()) {
            MirrorService.stopActive()
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
            return
        }
        val intent = DextopShortcuts.startDisplayIntent(this, closeAfterStart = true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
