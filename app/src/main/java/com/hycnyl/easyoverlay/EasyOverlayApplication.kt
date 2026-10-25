package com.hycnyl.easyoverlay

import android.app.Application

class EasyOverlayApplication:Application() {
    lateinit var container:AppContainer
        private set
    override fun onCreate() {super.onCreate();container=AppContainer(this)}
}
