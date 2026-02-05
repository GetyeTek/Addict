package com.guardian.net

import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

class DebugKillTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        
        // 1. Force Nuke Protocol State
        NukeManager.setProtectionDisabled(ctx, true)
        
        // 2. Clear Session Data
        ctx.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        
        // 3. Navigate Home
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // 4. Close notification shade and apply
        try {
            startActivityAndCollapse(home)
        } catch (e: Exception) {
            // Fallback for older APIs
            val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            ctx.sendBroadcast(closeIntent)
        }
        
        DebugLogger.log("DEBUG", "Emergency Kill Tile Triggered. Protections Dropped.")
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile.label = "DEBUG: Kill Guardian"
        qsTile.updateTile()
    }
}