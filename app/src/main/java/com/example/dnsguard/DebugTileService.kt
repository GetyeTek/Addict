package com.guardian.net

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Intent

class DebugTileService : TileService() {

    // Called when the tile is added to the Quick Settings panel or when the panel is opened
    override fun onStartListening() {
        updateTileState()
    }

    override fun onClick() {
        val currentlyDisabled = NukeManager.isProtectionDisabled(this)
        val newState = !currentlyDisabled

        // Toggle the master protection switch
        NukeManager.setProtectionDisabled(this, newState)
        
        DebugLogger.log("DEBUG_TILE", "Protection forced to: ${if (newState) "DISABLED" else "ENABLED"}")

        updateTileState()

        if (newState) {
            // If we are killing the guard, force go to home screen to clear any lingering overlays
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivityAndCollapse(home)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isDisabled = NukeManager.isProtectionDisabled(this)
        
        tile.state = if (isDisabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isDisabled) "Guard: OFF" else "Guard: ON"
        
        tile.updateTile()
    }
}