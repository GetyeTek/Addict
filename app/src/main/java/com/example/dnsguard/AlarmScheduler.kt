package com.guardian.net

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

object AlarmScheduler {
    fun scheduleNext(ctx: Context) {
        val alarms = AlarmStore.getAlarms(ctx).filter { it.enabled }
        if (alarms.isEmpty()) return

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        var nextTime: Long = Long.MAX_VALUE

        alarms.forEach { alarm ->
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR, if(alarm.hour == 12) 0 else alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.AM_PM, if(alarm.isAm) Calendar.AM else Calendar.PM)
            }

            // Calculate for each day in days set
            if (alarm.days.isEmpty()) {
                if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
                if (target.timeInMillis < nextTime) nextTime = target.timeInMillis
            } else {
                alarm.days.forEach { day ->
                    val dayTarget = target.clone() as Calendar
                    // Calendar.MONDAY is 2, etc. Our days are 1-7 (Mon-Sun)
                    val calendarDay = if (day == 7) Calendar.SUNDAY else day + 1
                    dayTarget.set(Calendar.DAY_OF_WEEK, calendarDay)
                    
                    if (dayTarget.before(now)) dayTarget.add(Calendar.WEEK_OF_YEAR, 1)
                    if (dayTarget.timeInMillis < nextTime) nextTime = dayTarget.timeInMillis
                }
            }
        }

        if (nextTime != Long.MAX_VALUE) {
            val intent = Intent(ctx, AlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pending)
            DebugLogger.log("ALARM", "Next alarm scheduled for: ${Date(nextTime)}")
        }
    }

    fun getNextAlarmTime(ctx: Context): Long {
        val alarms = AlarmStore.getAlarms(ctx).filter { it.enabled }
        if (alarms.isEmpty()) return 0L

        val now = Calendar.getInstance()
        var nextTime: Long = Long.MAX_VALUE

        alarms.forEach { alarm ->
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR, if(alarm.hour == 12) 0 else alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.AM_PM, if(alarm.isAm) Calendar.AM else Calendar.PM)
            }
            if (alarm.days.isEmpty()) {
                if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
                if (target.timeInMillis < nextTime) nextTime = target.timeInMillis
            } else {
                alarm.days.forEach { day ->
                    val dayTarget = target.clone() as Calendar
                    val calendarDay = if (day == 7) Calendar.SUNDAY else day + 1
                    dayTarget.set(Calendar.DAY_OF_WEEK, calendarDay)
                    if (dayTarget.before(now)) dayTarget.add(Calendar.WEEK_OF_YEAR, 1)
                    if (dayTarget.timeInMillis < nextTime) nextTime = dayTarget.timeInMillis
                }
            }
        }
        return if (nextTime == Long.MAX_VALUE) 0L else nextTime
    }
}