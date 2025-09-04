package com.tans.tmediaplayer.player.playerview.filter

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

abstract class ImageFilter {

    private val isEnabled = AtomicBoolean(true)

    private val isDispatchCreated = AtomicBoolean(false)

    internal fun dispatchGlSurfaceCreated(context: Context) {
        if (isDispatchCreated.compareAndSet(false, true)) {
            glSurfaceCreated(context)
        }
    }

    abstract fun glSurfaceCreated(context: Context)

    fun enable(enable: Boolean) {
        isEnabled.set(enable)
    }

    fun isEnable(): Boolean {
        return isEnabled.get()
    }

    internal fun dispatchDrawFrame(
        context: Context,
        surfaceWidth: Int,
        surfaceHeight: Int,
        input: FilterImageTexture,
        output: FilterImageTexture
    ) {
        if (isEnable()) {
            drawFrame(
                context,
                surfaceWidth,
                surfaceHeight,
                input,
                output
            )
        } else {
            output.width = input.width
            output.height = input.height
            output.texture = input.texture
            output.rotation = input.rotation
        }
    }

    abstract fun drawFrame(
        context: Context,
        surfaceWidth: Int,
        surfaceHeight: Int,
        input: FilterImageTexture,
        output: FilterImageTexture
    )

    internal fun dispatchGlSurfaceDestroying() {
        if (isDispatchCreated.compareAndSet(true, false)) {
            glSurfaceDestroying()
        }
    }

    abstract fun glSurfaceDestroying()
}
