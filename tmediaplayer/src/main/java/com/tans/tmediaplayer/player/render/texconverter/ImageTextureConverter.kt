package com.tans.tmediaplayer.player.render.texconverter

import android.content.Context
import com.tans.tmediaplayer.player.render.tMediaPlayerView

interface ImageTextureConverter {
    fun convertImageToTexture(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        imageData: tMediaPlayerView.Companion.ImageData): Int

    fun recycle()
}