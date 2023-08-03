package com.tans.tmediaplayer.render.texconverter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.render.tMediaPlayerView
import java.nio.ByteBuffer

internal class RgbaImageTextureConverter : ImageTextureConverter {

    override fun convertImageToTexture(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        imageData: tMediaPlayerView.Companion.ImageData,
        outputTexId: Int
    ) {
        if (imageData.imageRawData is tMediaPlayerView.Companion.ImageRawData.RgbaRawData) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, outputTexId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                imageData.imageWidth,
                imageData.imageHeight,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(imageData.imageRawData.rgbaBytes)
            )
        } else {
            MediaLog.e(TAG, "Wrong image type: ${imageData.imageRawData::class.java.simpleName}")
        }
    }

    override fun recycle() {

    }

    companion object {
        private const val TAG = "RgbaImageTextureConverter"
    }
}