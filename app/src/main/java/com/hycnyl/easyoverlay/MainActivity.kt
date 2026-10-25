package com.hycnyl.easyoverlay

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.hycnyl.easyoverlay.platform.HardwareCapture
import com.hycnyl.easyoverlay.ui.*

class MainActivity:ComponentActivity() {
    private val model:EasyOverlayViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            EasyOverlayTheme(settings.theme) {EasyOverlayApp(model)}
        }
    }
    override fun onStart() {super.onStart();(application as EasyOverlayApplication).container.appVisible=true}
    override fun onStop() {(application as EasyOverlayApplication).container.appVisible=false;super.onStop()}
    override fun dispatchKeyEvent(event:KeyEvent):Boolean=if(HardwareCapture.handle(event))true else super.dispatchKeyEvent(event)
}
