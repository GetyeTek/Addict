package com.guardian.net

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisabled(context: Context, intent: Intent) {
        // If they try to disable admin, we assume hostility.
        // In a real scenario, you might trigger a lock here.
        super.onDisabled(context, intent)
    }
}