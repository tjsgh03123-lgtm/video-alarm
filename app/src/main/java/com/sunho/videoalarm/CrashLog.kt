package com.sunho.videoalarm

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            CrashLog.record(this, "강제종료", e)
            prev?.uncaughtException(t, e)
        }
    }
}

object CrashLog {
    private fun sp(c: Context) = c.getSharedPreferences("crash", Context.MODE_PRIVATE)

    fun record(c: Context, tag: String, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            append(c, "[$tag] " + sw.toString().take(1200))
        } catch (x: Exception) {
        }
    }

    fun note(c: Context, msg: String) = append(c, msg)

    private fun append(c: Context, s: String) {
        val t = SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA).format(Date())
        val old = sp(c).getString("log", "") ?: ""
        sp(c).edit().putString("log", ("$t $s\n\n$old").take(5000)).commit()
    }

    fun get(c: Context): String = sp(c).getString("log", "") ?: ""
    fun clear(c: Context) = sp(c).edit().clear().commit()
}
