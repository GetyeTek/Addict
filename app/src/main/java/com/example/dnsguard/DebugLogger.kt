package com.guardian.net

import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {
    private val logs = LinkedList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private const val MAX_LOGS = 500

    @Synchronized
    fun log(tag: String, msg: String) {
        val time = dateFormat.format(Date())
        logs.addFirst("[$time] $tag: $msg")
        if (logs.size > MAX_LOGS) {
            logs.removeLast()
        }
    }

    @Synchronized
    fun getLogs(): String {
        return logs.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        logs.clear()
    }

    fun logCrash(ex: Throwable) {
        val trace = android.util.Log.getStackTraceString(ex)
        log("CRASH", "Unhandled Exception: $trace")
    }
}