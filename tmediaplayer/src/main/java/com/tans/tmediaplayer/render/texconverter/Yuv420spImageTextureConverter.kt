package com.tans.tmediaplayer.render.texconverter

import android.content.Context
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.render.tMediaPlayerView

class Yuv420spImageTextureConverter : ImageTextureConverter {

    override fun convertImageToTexture(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        imageData: tMediaPlayerView.Companion.ImageData,
        outputTexId: Int
    ) {
        if (imageData.imageRawData is tMediaPlayerView.Companion.ImageRawData.Yuv420spRawData) {
            val rawData = imageData.imageRawData
            // TODO:
        } else {
            MediaLog.e(TAG, "Wrong image type: ${imageData.imageRawData::class.java.simpleName}")
        }
    }

    override fun recycle() {

    }

    companion object {
        private const val TAG = "Yuv420spImageTextureConverter"
    }
}