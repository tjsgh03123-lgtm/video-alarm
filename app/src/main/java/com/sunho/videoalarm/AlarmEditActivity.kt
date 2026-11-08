package com.sunho.videoalarm

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AlarmEditActivity : Activity() {
    private lateinit var alarm: Alarm
    private var isNew = false
    private lateinit var videoText: TextView
    private lateinit var labelEdit: EditText
    private lateinit var clearBtn: android.widget.Button
    private val dayViews = mutableListOf<TextView>()

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        val id = intent.getIntExtra("id", -1)
        isNew = id < 0
        alarm = if (isNew) Alarm(id = AlarmStore.newId(this)) else (AlarmStore.get(this, id) ?: Alarm(id = AlarmStore.newId(this)).also { isNew = true })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }
        root.addView(Ui.label(this, if (isNew) "알람 추가" else "알람 편집", 24f, Ui.ACCENT))

        fun picker(min: Int, max: Int, names: Array<String>?, value: Int, wrap: Boolean, onChange: (Int) -> Unit) =
            NumberPicker(this).apply {
                minValue = min
                maxValue = max
                if (names != null) displayedValues = names
                this.value = value
                wrapSelectorWheel = wrap
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                if (android.os.Build.VERSION.SDK_INT >= 29) textSize = 24f * resources.displayMetrics.scaledDensity
                setOnValueChangedListener { _, _, n -> onChange(n) }
            }
        var ap = alarm.hour / 12
        var hIdx = alarm.hour % 12
        val wheels = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(8))
        }
        val lp = { LinearLayout.LayoutParams(dp(90), dp(160)).apply { marginStart = dp(6); marginEnd = dp(6) } }
        wheels.addView(
            picker(0, 1, arrayOf("오전", "오후"), ap, false) { ap = it; alarm.hour = ap * 12 + hIdx }, lp()
        )
        wheels.addView(
            picker(0, 11, Array(12) { if (it == 0) "12" else "$it" }, hIdx, true) { hIdx = it; alarm.hour = ap * 12 + hIdx }, lp()
        )
        wheels.addView(
            picker(0, 59, Array(60) { String.format("%02d", it) }, alarm.minute, true) { alarm.minute = it }, lp()
        )
        root.addView(wheels)

        val row = LinearLayout(this).apply { setPadding(0, dp(12), 0, dp(4)) }
        listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, n ->
            val tv = TextView(this).apply {
                text = n
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    alarm.days = alarm.days xor (1 shl i)
                    refresh()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(2); marginEnd = dp(2) }
            }
            dayViews.add(tv)
            row.addView(tv)
        }
        root.addView(row)
        root.addView(Ui.label(this, "요일을 모두 끄면 한 번만 울려요", 12f, Color.GRAY).apply {
            gravity = Gravity.CENTER
        })

        labelEdit = EditText(this).apply {
            hint = "알람 이름 (선택)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine()
            setText(alarm.label)
        }
        root.addView(labelEdit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        val vbox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@AlarmEditActivity, Ui.CARD)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }
        vbox.addView(Ui.label(this, "알람을 끈 뒤 재생할 영상", 14f, Color.LTGRAY))
        videoText = Ui.label(this, "", 16f).apply { setPadding(0, dp(4), 0, dp(8)) }
        vbox.addView(videoText)
        vbox.addView(Ui.button(this, "폰에서 영상 선택") { pickFile() })
        vbox.addView(Ui.button(this, "유튜브 링크 입력") { askYoutube() })
        clearBtn = Ui.button(this, "영상 지우기", Color.parseColor("#37424D")) {
            alarm.mode = ""
            refresh()
        }
        vbox.addView(clearBtn)
        vbox.addView(Ui.button(this, "영상 미리보기 (뒤로가기 가능)", Color.parseColor("#37424D")) {
            if (alarm.mode.isEmpty()) {
                Toast.makeText(this, "먼저 영상을 선택하세요", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(this, AlarmActivity::class.java)
                        .putExtra("test", true)
                        .putExtra("mode", alarm.mode)
                        .putExtra("fileUri", alarm.fileUri)
                        .putExtra("youtubeUrl", alarm.youtubeUrl)
                )
            }
        })
        root.addView(vbox)

        val bottom = LinearLayout(this).apply { setPadding(0, dp(16), 0, 0) }
        bottom.addView(
            Ui.button(this, "취소", Color.parseColor("#37424D")) { finish() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        )
        bottom.addView(
            Ui.button(this, "저장") { save() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) }
        )
        root.addView(bottom)
        if (!isNew) {
            root.addView(Ui.button(this, "알람 삭제", Color.parseColor("#7A2E2E")) {
                AlertDialog.Builder(this)
                    .setMessage("이 알람을 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        AlarmScheduler.cancel(this, alarm.id)
                        AlarmStore.delete(this, alarm.id)
                        finish()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            })
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(root)
        })
        refresh()
    }

    private fun refresh() {
        dayViews.forEachIndexed { i, tv ->
            val on = (alarm.days and (1 shl i)) != 0
            tv.background = Ui.rounded(this, if (on) Ui.ACCENT else Ui.CARD, 24)
            tv.setTextColor(if (on) Color.BLACK else Color.WHITE)
        }
        videoText.text = if (alarm.mode.isEmpty()) "영상 없음 (소리만 울려요)" else alarm.videoLabel()
        clearBtn.visibility = if (alarm.mode.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun save() {
        alarm.label = labelEdit.text.toString().trim()
        alarm.enabled = true
        AlarmStore.save(this, alarm)
        val t = AlarmScheduler.schedule(this, alarm)
        if (t > 0) {
            val mins = ((t - System.currentTimeMillis()) / 60000).toInt() + 1
            val d = mins / 1440
            val h = (mins % 1440) / 60
            val m = mins % 60
            val msg = (if (d > 0) "${d}일 " else "") + (if (h > 0) "${h}시간 " else "") + "${m}분 후에 알람이 울려요"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(i, 20)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 20 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            var name = "선택한 영상"
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
            }
            alarm.mode = "file"
            alarm.fileUri = uri.toString()
            alarm.fileName = name
            refresh()
        }
    }

    private fun askYoutube() {
        val et = EditText(this).apply {
            hint = "https://youtu.be/..."
            setText(alarm.youtubeUrl)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("유튜브 링크")
            .setView(et)
            .setPositiveButton("확인") { _, _ ->
                val s = et.text.toString().trim()
                if (extractYoutubeId(s) == null) {
                    Toast.makeText(this, "올바른 유튜브 링크가 아니에요", Toast.LENGTH_SHORT).show()
                } else {
                    alarm.mode = "youtube"
                    alarm.youtubeUrl = s
                    refresh()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
