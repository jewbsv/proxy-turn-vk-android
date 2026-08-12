package com.wdtt.client

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity

/**
 * Заставка при запуске приложения в стиле Clash Royale.
 * Воспроизводит видео на весь экран и сразу после его завершения
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
            setOnCompletionListener(onVideoCompletion)
            setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.clash_royale_splash}"))
        }
        videoView?.start()
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
        videoView?.stopPlayback()
        videoView = null
        super.onDestroy()
    }
}
