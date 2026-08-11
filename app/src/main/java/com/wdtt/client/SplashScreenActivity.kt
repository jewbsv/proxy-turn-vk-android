package com.wdtt.client

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity

/**
 * Заставка при запуске приложения в стиле Clash Royale.
 * Показывает полноэкранное изображение и через заданную задержку
 * переходит в [MainActivity].
 */
class SplashScreenActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val goToMain = Runnable {
        if (!isFinishing && !isDestroyed) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(goToMain, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(goToMain)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2500L
    }
}
