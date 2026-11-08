package com.sunho.videoalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_ALARM = "com.sunho.videoalarm.ALARM"

    fun nextTrigger(a: Alarm, from: Long = System.currentTimeMillis()): Long {
        for (offset in 0..7) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, a.hour)
                set(Calendar.MINUTE, a.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= from) continue
            val bit = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (a.days == 0 || (a.days and (1 shl bit)) != 0) return cal.timeInMillis
        }
        return -1
    }

    private fun pending(c: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, id,
            Intent(c, AlarmReceiver::class.java).setAction(ACTION_ALARM).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun cancel(c: Context, id: Int) {
        c.getSystemService(AlarmManager::class.java).cancel(pending(c, id))
    }

    fun schedule(c: Context, a: Alarm): Long {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c, a.id)
        am.cancel(pi)
        if (!a.enabled) return -1
        val t = nextTrigger(a)
        if (t < 0) return -1
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi)
        } catch (e: SecurityException) {
            CrashLog.record(c, "알람 예약 실패", e)
            return -1
        }
        return t
    }

    fun scheduleAll(c: Context) {
        AlarmStore.all(c).forEach { schedule(c, it) }
    }

    fun nextOverall(c: Context): Long =
        AlarmStore.all(c).filter { it.enabled }.map { nextTrigger(it) }.filter { it > 0 }.minOrNull() ?: -1L
}
