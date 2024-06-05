package com.tans.tmediaplayer.player.playerview.texconverter

import android.content.Context
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView

interface ImageTextureConverter {
    fun convertImageToTexture(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        imageData: tMediaPlayerView.Companion.ImageData): Int

    fun recycle()
}