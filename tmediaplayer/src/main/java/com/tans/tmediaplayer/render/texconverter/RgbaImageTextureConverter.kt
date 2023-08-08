package com.tans.tmediaplayer.render.texconverter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.render.glGenTextureAndSetDefaultParams
import com.tans.tmediaplayer.render.tMediaPlayerView
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal class RgbaImageTextureConverter : ImageTextureConverter {

    private val renderData: AtomicReference<RenderData?> by lazy {
        AtomicReference()
    }

    override fun convertImageToTexture(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        imageData: tMediaPlayerView.Companion.ImageData,
    ): Int {
        return if (imageData.imageRawData is tMediaPlayerView.Companion.ImageRawData.RgbaRawData) {
            val renderData = ensureRenderData()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.outputTexId)
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
            renderData.outputTexId
        } else {
            MediaLog.e(TAG, "Wrong image type: ${imageData.imageRawData::class.java.simpleName}")
            0
        }
    }

    override fun recycle() {
        val renderData = renderData.get()
        if (renderData != null) {
            this.renderData.set(null)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.outputTexId), 0)
        }
    }

    private fun ensureRenderData(): RenderData {
        val renderData = renderData.get()
        return if (renderData != null) {
            renderData
        } else {
            val outputTexId = glGenTextureAndSetDefaultParams()
            val result = RenderData(
                outputTexId = outputTexId
            )
            this.renderData.set(result)
            result
        }
    }

    companion object {
        private const val TAG = "RgbaImageTextureConverter"
        data class RenderData(
            val outputTexId: Int
        )
    }
}