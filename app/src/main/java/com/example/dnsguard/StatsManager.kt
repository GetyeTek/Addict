package com.guardian.net

import android.os.Process
import android.os.SystemClock
import java.util.LinkedList

object StatsManager {

    // DATA HOLDER
    data class StatPoint(val timestamp: Long, val cpuPercent: Float, val memoryMb: Long)

    // HISTORY (Last 50 points for graph)
    val history = LinkedList<StatPoint>()
    
    // CURRENT LIVE STATS
    var currentCpu: Float = 0f
    var currentMem: Long = 0
    var uptimeMs: Long = 0
    
    // BATTERY STATS
    var startBatteryPct: Int = -1
    var currentBatteryPct: Int = -1
    var isCharging: Boolean = false
    
    private var lastCpuTime: Long = 0
    private var lastAppTime: Long = 0
    private val startTime = System.currentTimeMillis()

    fun init(ctx: android.content.Context) {
        lastCpuTime = Process.getElapsedCpuTime()
        lastAppTime = SystemClock.elapsedRealtime()
        
        // Capture initial battery
        val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        startBatteryPct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun update(ctx: android.content.Context) {
        // 1. MEMORY
        val runtime = Runtime.getRuntime()
        currentMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024

        // 2. CPU
        val nowCpu = Process.getElapsedCpuTime()
        val nowApp = SystemClock.elapsedRealtime()
        
        val relTime = nowApp - lastAppTime
        val relCpu = nowCpu - lastCpuTime

        if (relTime > 0) {
            currentCpu = (relCpu.toFloat() / relTime.toFloat()) * 100f
        }

        lastCpuTime = nowCpu
        lastAppTime = nowApp
        uptimeMs = System.currentTimeMillis() - startTime
        
        // 3. BATTERY
        val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        currentBatteryPct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        isCharging = bm.isCharging
        
        // Initialize start if missed
        if (startBatteryPct == -1) startBatteryPct = currentBatteryPct

        // 4. STORE HISTORY
        synchronized(history) {
            history.add(StatPoint(System.currentTimeMillis(), currentCpu, currentMem))
            if (history.size > 50) history.removeFirst()
        }
    }
    
    fun getBatteryImpact(): String {
        // Estimate based on CPU usage typical for background services
        return when {
            currentCpu < 0.5f -> "Negligible (< 1% / day)"
            currentCpu < 2.0f -> "Low (~3% / day)"
            currentCpu < 5.0f -> "Moderate (~8% / day)"
            else -> "High Drain (> 10% / day)"
        }
    }

    fun getFormattedUptime(): String {
        val seconds = (uptimeMs / 1000) % 60
        val minutes = (uptimeMs / (1000 * 60)) % 60
        val hours = (uptimeMs / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}