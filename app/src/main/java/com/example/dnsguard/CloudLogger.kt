package com.example.dnsguard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object CloudLogger {

    private const val FILE_NAME = "suspects.log"
    
    // SUPABASE CREDENTIALS
    private const val SUPABASE_URL = "https://xvldfsmxskhemkslsbym.supabase.co/rest/v1/scraped_content"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh2bGRmc214c2toZW1rc2xzYnltIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjI2ODgxNzksImV4cCI6MjA3ODI2NDE3OX0.5arqrx8Tt7v-hpXpo_ncoK4IX8th9IibxAuv93SSoOU"

    // 1. Save locally first (Fail-safe)
    suspend fun logViolation(ctx: Context, content: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(ctx.filesDir, FILE_NAME)
                // Append content with a delimiter
                file.appendText("$content<END_LOG>")
                
                // Trigger Sync
                syncToCloud(ctx)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 2. Upload to Supabase
    private suspend fun syncToCloud(ctx: Context) {
        val file = File(ctx.filesDir, FILE_NAME)
        if (!file.exists()) return

        val content = file.readText()
        if (content.isBlank()) return

        val logs = content.split("<END_LOG>").filter { it.isNotBlank() }

        for (log in logs) {
            val success = upload(log)
            if (!success) return // Stop if internet fails, retry later
        }

        // If we reached here, all uploaded. Clear file.
        file.writeText("")
    }

    private fun upload(text: String): Boolean {
        try {
            val url = URL(SUPABASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true

            val json = JSONObject()
            json.put("content", text)
            
            val os = conn.outputStream
            os.write(json.toString().toByteArray())
            os.flush()
            os.close()

            val code = conn.responseCode
            return code in 200..299
        } catch (e: Exception) {
            return false
        }
    }
}