package com.hycnyl.easyoverlay

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.hycnyl.easyoverlay.data.ThemeChoice
import com.hycnyl.easyoverlay.platform.HardwareCapture
import com.hycnyl.easyoverlay.ui.*

class MainActivity:ComponentActivity() {
    private val model:EasyOverlayViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            val dark=when(settings.theme){ThemeChoice.SYSTEM->isSystemInDarkTheme();ThemeChoice.LIGHT->false;ThemeChoice.DARK->true}
            SideEffect {
                WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars=!dark
                    isAppearanceLightNavigationBars=!dark
                }
            }
            EasyOverlayTheme(settings.theme) {EasyOverlayApp(model)}
        }
    }
    override fun onStart() {
        super.onStart()
        (application as EasyOverlayApplication).container.appVisible=true
        com.hycnyl.easyoverlay.platform.OverlaySessionService.restoreIfWanted(this,"APP_OPEN")
    }
    override fun onStop() {(application as EasyOverlayApplication).container.appVisible=false;super.onStop()}
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean=if(HardwareCapture.handle(event))true else super.onKeyDown(keyCode,event)
    override fun onKeyUp(keyCode:Int,event:KeyEvent):Boolean=if(HardwareCapture.handle(event))true else super.onKeyUp(keyCode,event)
}
