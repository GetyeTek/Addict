package com.guardian.net

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppCache {
    private var cachedApps: List<AppItem>? = null
    private var isFetching = false

    fun getCachedApps(): List<AppItem>? = cachedApps

    suspend fun loadApps(ctx: Context) {
        if (cachedApps != null || isFetching) return
        isFetching = true
        
        withContext(Dispatchers.IO) {
            try {
                val pm = ctx.packageManager
                // Using 0 instead of GET_META_DATA is significantly faster
                val apps = pm.getInstalledApplications(0)
                    .filter { it.packageName != ctx.packageName }
                    .map { AppItem(it.loadLabel(pm).toString(), it.packageName) }
                    .sortedBy { it.name.lowercase() }
                
                cachedApps = apps
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isFetching = false
            }
        }
    }

    fun clear() {
        cachedApps = null
    }
}