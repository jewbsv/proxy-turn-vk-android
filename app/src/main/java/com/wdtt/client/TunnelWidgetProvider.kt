package com.wdtt.client

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TunnelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_TOGGLE) {
            val isRunning = TunnelManager.running.value
            if (isRunning) {
                // Stop service
                val stopIntent = Intent(context, TunnelService::class.java).apply { action = "STOP" }
                context.startService(stopIntent)
            } else {
                // Start service from saved settings in background thread
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    val store = SettingsStore(context)
                    val basePeer = store.peer.first()
                    val hashes = store.vkHashes.first()
                    val workers = store.workersPerHash.first()
                    val port = store.listenPort.first()
                    val password = store.connectionPassword.first()
                    val captchaMode = store.captchaMode.first()
                    val captchaMethod = store.captchaSolveMethod.first()
                    val vkAnonPath = store.vkAnonPath.first()
                    val manualPortsEnabled = store.manualPortsEnabled.first()
                    val serverDtlsPort = if (manualPortsEnabled) store.serverDtlsPort.first() else 56000
                    val peerWithPort = if (basePeer.isBlank()) basePeer else PeerAddress.ensurePort(basePeer, serverDtlsPort)
                    
                    if (peerWithPort.isNotBlank() && hashes.isNotBlank() && password.isNotBlank()) {
                        val startIntent = Intent(context, TunnelService::class.java).apply {
                            action = "START_FORCED"
                            putExtra("peer", peerWithPort)
                            putExtra("vk_hashes", hashes)
                            putExtra("secondary_vk_hash", "")
                            putExtra("workers_per_hash", workers)
                            putExtra("port", port)
                            putExtra("sni", store.sni.first())
                            putExtra("connection_password", password)
                            putExtra("captcha_mode", captchaMode)
                            putExtra("captcha_solve_method", captchaMethod)
                            putExtra("vk_anon_path", if (vkAnonPath.equals("legacy", ignoreCase = true)) "legacy" else "vkcalls")
                        }
                        
                        if (Build.VERSION.SDK_INT >= 26) {
                            context.startForegroundService(startIntent)
                        } else {
                            context.startService(startIntent)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "net.qwdtt.client.ACTION_TOGGLE"

        fun updateWidgetState(context: Context, running: Boolean, statsText: String?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, TunnelWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            for (widgetId in allWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.tunnel_widget)
                
                // Update status dot and title
                if (running) {
                    views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_green)
                    views.setTextViewText(R.id.widget_status_title, "qWDTT: Активен")
                    views.setTextViewText(R.id.widget_stats_text, statsText ?: "Туннель запущен")
                    views.setImageViewResource(R.id.widget_toggle_button, android.R.drawable.ic_media_pause)
                    views.setInt(R.id.widget_toggle_button, "setColorFilter", android.graphics.Color.parseColor("#FF5252")) // Red pause button
                } else {
                    views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_gray)
                    views.setTextViewText(R.id.widget_status_title, "qWDTT: Отключен")
                    views.setTextViewText(R.id.widget_stats_text, "Нажмите для подключения")
                    views.setImageViewResource(R.id.widget_toggle_button, android.R.drawable.ic_media_play)
                    views.setInt(R.id.widget_toggle_button, "setColorFilter", android.graphics.Color.parseColor("#3DDC84")) // Green play button
                }
                
                // Set click listener
                val intent = Intent(context, TunnelWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent)
                
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.tunnel_widget)
            val running = TunnelManager.running.value
            val stats = TunnelManager.stats.value
            
            // Set initial state
            if (running) {
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_green)
                views.setTextViewText(R.id.widget_status_title, "qWDTT: Активен")
                views.setTextViewText(R.id.widget_stats_text, stats)
                views.setImageViewResource(R.id.widget_toggle_button, android.R.drawable.ic_media_pause)
                views.setInt(R.id.widget_toggle_button, "setColorFilter", android.graphics.Color.parseColor("#FF5252"))
            } else {
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_gray)
                views.setTextViewText(R.id.widget_status_title, "qWDTT: Отключен")
                views.setTextViewText(R.id.widget_stats_text, "Нажмите для подключения")
                views.setImageViewResource(R.id.widget_toggle_button, android.R.drawable.ic_media_play)
                views.setInt(R.id.widget_toggle_button, "setColorFilter", android.graphics.Color.parseColor("#3DDC84"))
            }
            
            val intent = Intent(context, TunnelWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_button, pendingIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
