package com.guardian.net

import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

class DebugKillTileService : TileService() {
    override fun onClick() {
        super.onClick()
        DebugLogger.log("DEBUG", "Kill Switch Tile clicked but is PERMANENTLY DISABLED.")
        // Collapse the shade so the click is acknowledged, but perform no action.
        try {
            val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            applicationContext.sendBroadcast(closeIntent)
        } catch (e: Exception) {}
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile.label = "DEBUG: Kill Guardian (Disabled)"
        qsTile.state = android.service.quicksettings.Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}