package com.wdtt.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuickToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = TunnelManager.running.value
        if (isRunning) {
            // Stop service
            val stopIntent = Intent(this, TunnelService::class.java).apply { action = "STOP" }
            startService(stopIntent)
            updateTile(false)
        } else {
            // Start service from saved settings in background thread
            val context = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
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
                    
                    launch(Dispatchers.Main) {
                        if (Build.VERSION.SDK_INT >= 26) {
                            context.startForegroundService(startIntent)
                        } else {
                            context.startService(startIntent)
                        }
                        updateTile(true)
                    }
                }
            }
        }
    }

    private fun updateTileState() {
        val isRunning = TunnelManager.running.value
        updateTile(isRunning)
    }

    private fun updateTile(running: Boolean) {
        val tile = qsTile ?: return
        if (running) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "qWDTT: Вкл"
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = "Активен"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "qWDTT: Выкл"
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = "Отключен"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    TileService.requestListeningState(
                        context,
                        ComponentName(context, QuickToggleTileService::class.java)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
