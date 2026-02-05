package com.guardian.net

import android.content.Intent
import android.service.quicksettings.TileService

class WhisperTileService : TileService() {
    override fun onClick() {
        super.onClick()
        LockManager.startWhisperMode(applicationContext)
        val intent = Intent(applicationContext, LockdownActivity::class.java).apply {
            putExtra("BLOCK_TYPE", "WHISPER_PROTOCOL")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivityAndCollapse(intent)
    }
}