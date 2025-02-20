package com.tans.tmediaplayer.player.playerview.texconverter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.playerview.glGenTextureAndSetDefaultParams
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView.Companion.ImageDataType
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal class RgbaImageTextureConverter : ImageTextureConverter {

    private val renderData: AtomicReference<RenderData?> by lazy {
        AtomicReference()
    }

    override fun convertImageToTexture(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        imageWidth: Int,
        imageHeight: Int,
        rgbaBytes: ByteArray?,
        yBytes: ByteArray?,
        uBytes: ByteArray?,
        vBytes: ByteArray?,
        uvBytes: ByteArray?,
        imageDataType: ImageDataType
    ): Int {
        return if (imageDataType == tMediaPlayerView.Companion.ImageDataType.Rgba) {
            val renderData = ensureRenderData()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.outputTexId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                imageWidth,
                imageHeight,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(rgbaBytes!!)
            )
            renderData.outputTexId
        } else {
            tMediaPlayerLog.e(TAG) { "Wrong image type: $imageDataType" }
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