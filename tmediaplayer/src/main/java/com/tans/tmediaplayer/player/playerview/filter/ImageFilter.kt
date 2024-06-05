package com.tans.tmediaplayer.player.playerview.filter

import android.content.Context
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView

interface ImageFilter {

    fun enable(enable: Boolean)

    fun isEnable(): Boolean

    fun filter(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        input: FilterImageTexture
    ): FilterImageTexture

    fun recycle()
}