package com.guardian.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlarmStore {
    private const val PREFS = "alarm_prefs"
    private const val KEY_ALARMS = "saved_alarms"

    fun saveAlarms(ctx: Context, alarms: List<AlarmData>) {
        val array = JSONArray()
        alarms.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("hour", it.hour)
            obj.put("minute", it.minute)
            obj.put("isAm", it.isAm)
            obj.put("name", it.name)
            obj.put("enabled", it.enabled)
            obj.put("days", JSONArray(it.days.toList()))
            array.put(obj)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ALARMS, array.toString()).apply()
        AlarmScheduler.scheduleNext(ctx)
    }

    fun getAlarms(ctx: Context): List<AlarmData> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ALARMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val list = mutableListOf<AlarmData>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val dayArray = obj.getJSONArray("days")
            val daySet = mutableSetOf<Int>()
            for (j in 0 until dayArray.length()) daySet.add(dayArray.getInt(j))
            
            list.add(AlarmData(
                obj.getString("id"),
                obj.getInt("hour"),
                obj.getInt("minute"),
                obj.getBoolean("isAm"),
                obj.getString("name"),
                obj.getBoolean("enabled"),
                daySet
            ))
        }
        return list
    }
}