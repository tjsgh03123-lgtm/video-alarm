package com.sunho.videoalarm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var permBox: LinearLayout
    private lateinit var permText: TextView

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(
            Ui.label(this, "알람", 28f, Ui.ACCENT),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(TextView(this).apply {
            text = "+"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = Ui.rounded(this@MainActivity, Ui.ACCENT, 28)
            setOnClickListener { startActivity(Intent(this@MainActivity, AlarmEditActivity::class.java)) }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        })
        root.addView(header)

        statusText = Ui.label(this, "", 14f, Color.LTGRAY).apply { setPadding(0, dp(8), 0, dp(12)) }
        root.addView(statusText)

        permBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@MainActivity, Color.parseColor("#4A2C2C"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        permText = Ui.label(this, "", 14f)
        permBox.addView(permText)
        permBox.addView(Ui.button(this, "권한 설정") { fixPermission() })
        root.addView(permBox)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(root)
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.scheduleAll(this)
        refresh()
        showCrashLog()
    }

    private fun showCrashLog() {
        val log = CrashLog.get(this)
        if (log.isEmpty()) return
        val tv = TextView(this).apply {
            text = log
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("오류 기록")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("복사 후 닫기") { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log", log))
                CrashLog.clear(this)
            }
            .setNegativeButton("지우기") { _, _ -> CrashLog.clear(this) }
            .show()
    }

    private fun updateStatus() {
        val t = AlarmScheduler.nextOverall(this)
        statusText.text = if (t > 0) {
            "다음 알람: " + SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREA).format(Date(t))
        } else "예정된 알람 없음"
    }

    private fun refresh() {
        updateStatus()
        val miss = missingPermission()
        permBox.visibility = if (miss == null) View.GONE else View.VISIBLE
        permText.text = miss ?: ""

        listBox.removeAllViews()
        val alarms = AlarmStore.all(this).sortedBy { it.hour * 60 + it.minute }
        if (alarms.isEmpty()) {
            listBox.addView(Ui.label(this, "오른쪽 위 + 버튼으로 알람을 추가하세요", 15f, Color.GRAY).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            })
            return
        }
        alarms.forEach { a -> listBox.addView(row(a)) }
    }

    private fun row(a: Alarm): View {
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(this@MainActivity, Ui.CARD)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AlarmEditActivity::class.java).putExtra("id", a.id))
            }
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            alpha = if (a.enabled) 1f else 0.45f
        }
        val timeRow = LinearLayout(this).apply { gravity = Gravity.BOTTOM }
        timeRow.addView(Ui.label(this, Ui.ampm(a.hour) + " ", 16f, Color.LTGRAY))
        timeRow.addView(Ui.label(this, Ui.hm(a.hour, a.minute), 38f))
        left.addView(timeRow)
        val sub = (if (a.label.isNotEmpty()) a.label + " · " else "") + Ui.daysString(a.days)
        left.addView(Ui.label(this, sub, 14f, Color.LTGRAY))
        if (a.mode.isNotEmpty()) left.addView(Ui.label(this, a.videoLabel(), 12f, Color.GRAY).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        card.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(Switch(this).apply {
            isChecked = a.enabled
            setOnCheckedChangeListener { _, on ->
                a.enabled = on
                AlarmStore.save(this@MainActivity, a)
                AlarmScheduler.schedule(this@MainActivity, a)
                left.alpha = if (on) 1f else 0.45f
                updateStatus()
            }
        })
        return card
    }

    private fun missingPermission(): String? {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return "알림 권한이 필요해요. 없으면 알람 화면이 뜨지 않아요."
        if (Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) return "전체 화면 알림 권한이 필요해요. 잠금화면에서 알람 화면이 바로 뜨려면 켜야 해요."
        return null
    }

    private fun fixPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        } else {
            try {
                startActivity(
                    Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        refresh()
    }
}
