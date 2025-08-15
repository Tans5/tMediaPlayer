package com.tans.tmediaplayer.player.playerview

import android.graphics.SurfaceTexture
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

internal class OesTextureSurface(val textureId: Int) {
    val surfaceTexture: SurfaceTexture = SurfaceTexture(textureId)
    val surface: Surface = Surface(surfaceTexture)

    private val isReleased = AtomicBoolean(false)

    fun release() {
        if (isReleased.compareAndSet(false, true)) {
            surfaceTexture.release()
            surface.release()
        }
    }
}