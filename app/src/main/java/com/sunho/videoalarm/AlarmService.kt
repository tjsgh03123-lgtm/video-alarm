package com.sunho.videoalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopAll() }

    companion object {
        const val CHANNEL = "alarm"
        const val NOTIF_ID = 1
        @Volatile var instance: AlarmService? = null

        fun buildNotification(c: Context, id: Int): Notification {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
            val pi = PendingIntent.getActivity(
                c, 1000 + id,
                Intent(c, AlarmActivity::class.java)
                    .putExtra("id", id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("영상 알람")
                .setContentText("눌러서 알람 끄기")
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("id", -1) ?: -1
        try {
            val n = buildNotification(this, id)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            CrashLog.record(this, "서비스-startForeground", e)
        }
        ringAgain()
        return START_NOT_STICKY
    }

    /** 알람을 끄고 영상이 재생되면 벨소리/진동을 끈다 (서비스는 유지). */
    fun quiet() {
        handler.removeCallbacks(timeout)
        Ringer.stop(this)
    }

    /** 영상이 안 보이는 상태가 되면 다시 울린다. */
    fun ringAgain() {
        Ringer.start(this)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 20 * 60 * 1000L)
    }

    fun stopAll() {
        handler.removeCallbacks(timeout)
        Ringer.stop(this)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
