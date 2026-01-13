package com.example.dnsguard

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private val logBuffer = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, msg: String) {
        val time = dateFormat.format(Date())
        logBuffer.append("[$time] $tag: $msg\n")
        if (logBuffer.length > 50000) {
            logBuffer.delete(0, 10000)
        }
    }

    @Synchronized
    fun getLogs(): String {
        return logBuffer.toString()
    }

    @Synchronized
    fun clear() {
        logBuffer.setLength(0)
    }
}