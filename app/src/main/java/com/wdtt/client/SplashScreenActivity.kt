package com.wdtt.client

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.VideoView
import androidx.activity.ComponentActivity
import kotlin.math.max

/**
 * Заставка при запуске приложения в стиле Clash Royale.
 * Воспроизводит видео на весь экран (centerCrop) и сразу после его завершения
 * плавно переходит в [MainActivity] (без жёсткого таймера).
 */
class SplashScreenActivity : ComponentActivity() {

    private var videoView: VideoView? = null

    private val onVideoCompletion = MediaPlayer.OnCompletionListener {
        goToMain()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        videoView = findViewById<VideoView>(R.id.splash_video).apply {
            setOnPreparedListener { mp ->
                applyCenterCropScale(this, mp)
                mp.start()
            }
            setOnCompletionListener(onVideoCompletion)
            setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.clash_royale_splash}"))
        }
    }

    private fun applyCenterCropScale(videoView: VideoView, mp: MediaPlayer) {
        val videoWidth = mp.videoWidth.toFloat()
        val videoHeight = mp.videoHeight.toFloat()
        if (videoWidth <= 0f || videoHeight <= 0f) return

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val scaleX = screenWidth / videoWidth
        val scaleY = screenHeight / videoHeight
        val scale = max(scaleX, scaleY)

        videoView.scaleX = scale
        videoView.scaleY = scale
    }

    private fun goToMain() {
        if (!isFinishing && !isDestroyed) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        videoView?.pause()
    }

    override fun onResume() {
        super.onResume()
        videoView?.start()
    }

    override fun onDestroy() {
        videoView?.setOnCompletionListener(null)
        videoView?.setOnPreparedListener(null)
        videoView?.stopPlayback()
        videoView = null
        super.onDestroy()
    }
}
