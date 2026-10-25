package com.hycnyl.easyoverlay.domain

interface PreparedOverlay : AutoCloseable

interface OverlayEngine {
    suspend fun prepare(asset: OverlayAsset, displayId: Int): PreparedOverlay
    fun mount(target: OverlayTarget, image: PreparedOverlay)
    fun remove(displayId: Int)
    fun targets(): Map<Int, OverlayTarget>
    fun resize()
    fun close()
}
