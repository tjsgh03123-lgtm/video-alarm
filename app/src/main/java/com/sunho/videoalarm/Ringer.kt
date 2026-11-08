package com.sunho.videoalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** 벨소리 + 진동. 서비스든 액티비티든 같은 프로세스에서 한 번만 울린다. */
object Ringer {
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var tone: ToneGenerator? = null
    private var active = false
    private val handler = Handler(Looper.getMainLooper())
    private val beep = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
            handler.postDelayed(this, 1200)
        }
    }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun alarmUri(c: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    @Synchronized
    fun start(ctx: Context) {
        if (active) return
        active = true
        val c = ctx.applicationContext
        val uri = try { alarmUri(c) } catch (e: Exception) { null }
        var ok = false

        if (uri != null) {
            try {
                val mp = MediaPlayer()
                player = mp
                mp.setAudioAttributes(attrs)
                mp.setDataSource(c, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                ok = true
            } catch (e: Exception) {
                CrashLog.record(c, "벨소리-MediaPlayer", e)
                try { player?.release() } catch (x: Exception) {}
                player = null
            }
        }
        if (!ok && uri != null) {
            try {
                val r = RingtoneManager.getRingtone(c, uri)
                r.audioAttributes = attrs
                if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
                r.play()
                ringtone = r
                ok = r.isPlaying
            } catch (e: Exception) {
                CrashLog.record(c, "벨소리-Ringtone", e)
            }
        }
        if (!ok) {
            try {
                tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                handler.post(beep)
            } catch (e: Exception) {
                CrashLog.record(c, "벨소리-Tone", e)
            }
        }
        try {
            c.getSystemService(Vibrator::class.java)
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun stop(ctx: Context) {
        active = false
        handler.removeCallbacks(beep)
        try { tone?.release() } catch (e: Exception) {}
        tone = null
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        try { ringtone?.stop() } catch (e: Exception) {}
        ringtone = null
        try { ctx.applicationContext.getSystemService(Vibrator::class.java).cancel() } catch (e: Exception) {}
    }
}
