package com.sunho.videoalarm

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object Ui {
    val BG = Color.parseColor("#101418")
    val CARD = Color.parseColor("#1C232B")
    val ACCENT = Color.parseColor("#FFB300")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, color: Int, r: Int = 16) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(c, r).toFloat()
    }

    fun label(c: Context, t: String, size: Float = 16f, color: Int = Color.WHITE) = TextView(c).apply {
        text = t
        textSize = size
        setTextColor(color)
    }

    fun button(c: Context, t: String, color: Int = ACCENT, onClick: () -> Unit) = Button(c).apply {
        text = t
        isAllCaps = false
        setTextColor(if (color == ACCENT) Color.BLACK else Color.WHITE)
        background = rounded(c, color, 12)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(c, 8) }
    }

    fun ampm(h: Int) = if (h < 12) "오전" else "오후"
    fun hm(h: Int, m: Int) = String.format(Locale.KOREA, "%d:%02d", if (h % 12 == 0) 12 else h % 12, m)

    fun daysString(days: Int): String {
        if (days == 0) return "한 번만"
        if (days == 127) return "매일"
        if (days == 31) return "평일"
        if (days == 96) return "주말"
        val names = listOf("월", "화", "수", "목", "금", "토", "일")
        return names.filterIndexed { i, _ -> (days and (1 shl i)) != 0 }.joinToString(" ")
    }
}
