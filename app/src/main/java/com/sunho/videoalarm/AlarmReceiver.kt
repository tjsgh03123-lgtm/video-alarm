package com.sunho.videoalarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            if (i.action == AlarmScheduler.ACTION_ALARM) {
                val id = i.getIntExtra("id", -1)
                val a = AlarmStore.get(c, id)
                if (a == null) {
                    CrashLog.note(c, "알람 시각에 알람 정보를 찾지 못함 (id=$id)")
                    return
                }
                if (a.days == 0) {
                    a.enabled = false
                    AlarmStore.save(c, a)
                } else {
                    AlarmScheduler.schedule(c, a)
                }
                // 서비스가 막혀도 소리는 나도록 먼저 울린다
                Ringer.start(c)
                try {
                    c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("id", id))
                } catch (e: Exception) {
                    CrashLog.record(c, "서비스 시작 실패", e)
                    c.getSystemService(NotificationManager::class.java)
                        .notify(AlarmService.NOTIF_ID, AlarmService.buildNotification(c, id))
                }
            } else {
                AlarmScheduler.scheduleAll(c)
            }
        } catch (e: Exception) {
            CrashLog.record(c, "수신기 오류", e)
        }
    }
}
