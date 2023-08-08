package com.tans.tmediaplayer.render.filter

import android.content.Context
import com.tans.tmediaplayer.render.tMediaPlayerView
import java.util.concurrent.atomic.AtomicBoolean

class AsciiArtImageFilter : ImageFilter {

    private val isEnable: AtomicBoolean by lazy { AtomicBoolean(false) }

    override fun enable(enable: Boolean) {
        isEnable.set(enable)
    }

    override fun isEnable(): Boolean = isEnable.get()

    override fun filter(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        input: FilterImageTexture
    ): FilterImageTexture {
        return if (isEnable()) {
            // TODO:
            input
        } else {
            input
        }
    }


    override fun recycle() {
        // TODO:
    }


}