package com.tans.tmediaplayer.player.playerview.texconverter

import android.content.Context
import com.tans.tmediaplayer.player.playerview.ImageDataType
import java.util.concurrent.atomic.AtomicBoolean

internal abstract class ImageTextureConverter {

    private val isDispatchCreated = AtomicBoolean(false)

    internal fun dispatchGlSurfaceCreated(context: Context) {
        if (isDispatchCreated.compareAndSet(false, true)) {
            glSurfaceCreated(context)
        }
    }

    abstract fun glSurfaceCreated(context: Context)

    abstract fun drawFrame(
        context: Context,
        surfaceWidth: Int,
        surfaceHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        rgbaBytes: ByteArray?,
        yBytes: ByteArray?,
        uBytes: ByteArray?,
        vBytes: ByteArray?,
        uvBytes: ByteArray?,
        imageDataType: ImageDataType
    ): Int

    internal fun dispatchGlSurfaceDestroying() {
        if (isDispatchCreated.compareAndSet(true, false)) {
            glSurfaceDestroying()
        }
    }

    abstract fun glSurfaceDestroying()
}