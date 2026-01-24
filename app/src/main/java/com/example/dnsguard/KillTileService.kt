package com.guardian.net

import android.service.quicksettings.TileService
import android.content.Context
import android.content.Intent

class KillTileService : TileService() {

    override fun onClick() {
        super.onClick()
        
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
        val nukePrefs = ctx.getSharedPreferences("nuke_prefs", Context.MODE_PRIVATE)

        // 1. Wipe all LockManager States
        prefs.edit().clear().apply()
        
        // 2. Wipe all NukeManager States and Force Disable Protection
        nukePrefs.edit()
            .clear()
            .putBoolean("protection_disabled", true)
            .putLong("protection_disabled_ts", System.currentTimeMillis())
            .apply()

        // 3. Clear WordBank dynamic list (Optional, but clean)
        ctx.getSharedPreferences("word_bank_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        // 4. Force Home Screen to break any existing overlays
        val home = Intent(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)

        // 5. Update Tile Appearance to show it worked
        val tile = qsTile
        tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
        tile.label = "GUARDIAN KILLED"
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        tile.state = android.service.quicksettings.Tile.STATE_ACTIVE
        tile.label = "Emergency Stop"
        tile.updateTile()
    }
}
