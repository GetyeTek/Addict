package com.guardian.net

import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

class DebugKillTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        
        val updateTs = ctx.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE).getLong("last_update_ts", 0L)
        val elapsed = System.currentTimeMillis() - updateTs
        if (elapsed < 3600000L) { // 1 Hour
            val mins = 60 - (elapsed / 60000)
            DebugLogger.log("ANTI_BYPASS", "Kill switch denied. App updated recently. $mins mins left.")
            android.widget.Toast.makeText(ctx, "Nice try. Wait $mins mins.", android.widget.Toast.LENGTH_LONG).show()
            try {
                val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                ctx.sendBroadcast(closeIntent)
            } catch(e: Exception){}
            return
        }
        
        // 1. Force Nuke Protocol State
        NukeManager.setProtectionDisabled(ctx, true)
        
        // 2. Clear Temporary Session Data Only
        ctx.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE).edit()
            .remove("unlock_ts")
            .remove("last_threshold")
            .apply()
        
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