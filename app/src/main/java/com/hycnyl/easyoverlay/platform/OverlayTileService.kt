package com.hycnyl.easyoverlay.platform

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hycnyl.easyoverlay.EasyOverlayApplication
import com.hycnyl.easyoverlay.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class OverlayTileService : TileService() {
    private var scope: CoroutineScope? = null
    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { owner -> owner.launch {
            (application as EasyOverlayApplication).container.runtime.collectLatest { runtime ->
                qsTile?.apply { label="EasyOverlay"; state=if (runtime.sessions.isEmpty()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                    subtitle=if (runtime.sessions.isEmpty()) "轻触切换遮罩" else "${runtime.sessions.size} 屏显示中"; updateTile() }
            }
        } }
    }
    override fun onStopListening() { scope?.cancel();scope=null;super.onStopListening() }
    override fun onClick() {
        super.onClick()
        val container=(application as EasyOverlayApplication).container
        if (!Permissions.overlay(this) || (!container.library.ready.value || container.library.state.value.quickSelections.isEmpty()) && container.runtime.value.sessions.isEmpty()) {
            val intent=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        } else unlockAndRun { OverlaySessionService.request(this,OverlaySessionService.TOGGLE_ALL) }
    }
}
