package moe.n4tsu.dextop

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DextopTileService : TileService() {
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
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            action = ACTION_OPEN_LAST_WORKSPACE
            DextopIntentToken.attach(this@DextopTileService, this)
        }
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

    companion object {
        const val ACTION_OPEN_LAST_WORKSPACE = "app.freedextop.action.OPEN_LAST_WORKSPACE"
    }
}
