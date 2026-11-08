package com.sunho.videoalarm

import android.app.Activity
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView

class AlarmActivity : Activity() {
    private var test = false
    private var phase = 0 // 0 = 알람 울리는 중, 1 = 영상 재생
    private var mode = ""
    private var fileUri = ""
    private var youtubeUrl = ""
    private var ringView: View? = null
    private var started = false
    private var errorShown = false
    private lateinit var container: FrameLayout
    private var videoView: VideoView? = null
    private var web: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val startTimeout = Runnable {
        if (!started) showError("영상이 시작되지 않아요")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        test = intent.getBooleanExtra("test", false)
        var label = ""
        var hour = 0
        var minute = 0
        if (test) {
            mode = intent.getStringExtra("mode") ?: ""
            fileUri = intent.getStringExtra("fileUri") ?: ""
            youtubeUrl = intent.getStringExtra("youtubeUrl") ?: ""
            phase = 1
        } else {
            val a = AlarmStore.get(this, intent.getIntExtra("id", -1))
            if (a == null) {
                AlarmService.instance?.stopAll()
                finish()
                return
            }
            mode = a.mode
            fileUri = a.fileUri
            youtubeUrl = a.youtubeUrl
            label = a.label
            hour = a.hour
            minute = a.minute
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(container)
        hideBars()
        boostVolume()
        if (!test) {
            buildRing(label, hour, minute)
            Ringer.start(this)
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun buildRing(label: String, hour: Int, minute: Int) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Ui.BG)
        }
        box.addView(TextView(this).apply {
            text = Ui.ampm(hour) + " " + Ui.hm(hour, minute)
            textSize = 64f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        if (label.isNotEmpty()) {
            box.addView(TextView(this).apply {
                text = label
                textSize = 24f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            })
        }
        box.addView(Button(this).apply {
            text = "알람 끄기"
            textSize = 24f
            setTextColor(Color.BLACK)
            background = Ui.rounded(this@AlarmActivity, Ui.ACCENT, 40)
            setPadding(0, 48, 0, 48)
            setOnClickListener { dismissRing() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 160 }
        })
        ringView = box
        container.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun dismissRing() {
        if (mode.isEmpty()) {
            finishAlarm()
            return
        }
        phase = 1
        ringView?.let { container.removeView(it) }
        ringView = null
        Ringer.stop(this)
        startPlayback()
    }

    override fun onStart() {
        super.onStart()
        if (phase == 1 && !errorShown) startPlayback()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && phase == 1 && !errorShown) {
            stopPlayback()
            if (!test) Ringer.start(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (test) {
            stopPlayback()
            finish()
        }
        // 실제 알람: 뒤로가기 차단
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    private fun boostVolume() {
        val am = getSystemService(AudioManager::class.java)
        val aMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) < aMax * 0.8) {
            am.setStreamVolume(AudioManager.STREAM_ALARM, (aMax * 0.8).toInt(), 0)
        }
        if (mode == "youtube") {
            val mMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < mMax * 0.6) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (mMax * 0.6).toInt(), 0)
            }
        }
    }

    private fun startPlayback() {
        stopPlayback()
        started = false
        handler.postDelayed(startTimeout, 20_000L)
        when (mode) {
            "file" -> playFile()
            "youtube" -> playYoutube()
            else -> showError("선택된 영상이 없어요")
        }
    }

    private fun playFile() {
        val vv = VideoView(this)
        vv.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        vv.setOnPreparedListener {
            vv.start()
            markPlaying()
        }
        vv.setOnCompletionListener { finishAlarm() }
        vv.setOnErrorListener { _, _, _ ->
            showError("영상을 재생할 수 없어요")
            true
        }
        container.addView(
            vv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
        videoView = vv
        try {
            vv.setVideoURI(Uri.parse(fileUri))
        } catch (e: Exception) {
            showError("영상을 열 수 없어요")
        }
    }

    private inner class Bridge {
        @JavascriptInterface fun playing() { runOnUiThread { markPlaying() } }
        @JavascriptInterface fun ended() { runOnUiThread { finishAlarm() } }
        @JavascriptInterface fun error(code: Int) {
            runOnUiThread { showError("유튜브 영상을 재생할 수 없어요 (오류 $code)") }
        }
    }

    private val bindJs = """
        (function(){
          if(!document.getElementById('va_css')){
            var st=document.createElement('style'); st.id='va_css';
            st.textContent='html,body{background:#000!important;overflow:hidden!important}'+
              'video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important}';
            document.head.appendChild(st);
          }
          var v=document.querySelector('video');
          if(!v) return;
          if(!v.__va){
            v.__va=1;
            v.addEventListener('playing',function(){Android.playing();});
            v.addEventListener('ended',function(){
              if(!document.querySelector('.ad-showing')) Android.ended();
            });
          }
          var mp=document.getElementById('movie_player');
          if(mp && mp.isMuted && mp.isMuted()){ try{ mp.unMute(); mp.setVolume(100); }catch(e){} }
          if(v.muted){ v.muted=false; }
          if(v.volume<1){ v.volume=1; }
          if(v.paused && !v.ended){ v.muted=false; var p=v.play(); if(p&&p.catch)p.catch(function(){}); }
        })();
    """.trimIndent()

    private val pollJs = object : Runnable {
        override fun run() {
            web?.evaluateJavascript(bindJs, null)
            handler.postDelayed(this, 1000L)
        }
    }

    private fun playYoutube() {
        val id = extractYoutubeId(youtubeUrl)
        if (id == null) {
            showError("유튜브 링크가 올바르지 않아요")
            return
        }
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        w.setBackgroundColor(Color.BLACK)
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return true
                return !(u.startsWith("http://") || u.startsWith("https://"))
            }
        }
        w.setOnTouchListener { _, _ -> true } // 터치로 일시정지/건너뛰기 방지
        w.setOnLongClickListener { true }
        w.addJavascriptInterface(Bridge(), "Android")
        container.addView(
            w,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        web = w
        w.loadUrl("https://m.youtube.com/watch?v=$id")
        handler.postDelayed(pollJs, 1000L)
    }

    private fun markPlaying() {
        started = true
        handler.removeCallbacks(startTimeout)
        if (!test) Ringer.stop(this)
    }

    private fun stopPlayback() {
        handler.removeCallbacks(startTimeout)
        handler.removeCallbacks(pollJs)
        videoView?.let {
            it.stopPlayback()
            container.removeView(it)
        }
        videoView = null
        web?.let {
            container.removeView(it)
            it.loadUrl("about:blank")
            it.destroy()
        }
        web = null
    }

    private fun showError(msg: String) {
        if (errorShown) return
        errorShown = true
        stopPlayback()
        if (!test) Ringer.start(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        box.addView(TextView(this).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        })
        box.addView(Button(this).apply {
            text = "알람 끄기"
            setOnClickListener { finishAlarm() }
        })
        container.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun finishAlarm() {
        stopPlayback()
        if (!test) {
            Ringer.stop(this)
            AlarmService.instance?.stopAll()
        }
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
