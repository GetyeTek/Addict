package com.example.dnsguard

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
    
    private var lastCpuTime: Long = 0
    private var lastAppTime: Long = 0
    private val startTime = System.currentTimeMillis()

    fun init() {
        lastCpuTime = Process.getElapsedCpuTime()
        lastAppTime = SystemClock.elapsedRealtime()
    }

    fun update() {
        // 1. MEMORY
        val runtime = Runtime.getRuntime()
        currentMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024

        // 2. CPU
        val nowCpu = Process.getElapsedCpuTime()
        val nowApp = SystemClock.elapsedRealtime()
        
        val relTime = nowApp - lastAppTime
        val relCpu = nowCpu - lastCpuTime

        if (relTime > 0) {
            // Calculate percentage (CPU Time / Wall Time)
            // Note: On multi-core, this can theoretically exceed 100%, but for a service it's usually low.
            currentCpu = (relCpu.toFloat() / relTime.toFloat()) * 100f
        }

        lastCpuTime = nowCpu
        lastAppTime = nowApp
        uptimeMs = System.currentTimeMillis() - startTime

        // 3. STORE HISTORY
        synchronized(history) {
            history.add(StatPoint(System.currentTimeMillis(), currentCpu, currentMem))
            if (history.size > 50) history.removeFirst()
        }
    }

    fun getFormattedUptime(): String {
        val seconds = (uptimeMs / 1000) % 60
        val minutes = (uptimeMs / (1000 * 60)) % 60
        val hours = (uptimeMs / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}