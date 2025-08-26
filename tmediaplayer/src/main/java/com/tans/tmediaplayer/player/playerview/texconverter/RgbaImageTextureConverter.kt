package com.tans.tmediaplayer.player.playerview.texconverter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.player.playerview.ImageDataType
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.playerview.glGenTextureAndSetDefaultParams
import java.nio.ByteBuffer

internal class RgbaImageTextureConverter : ImageTextureConverter() {

    private var renderData: RenderData? = null

    override fun glSurfaceCreated(context: Context) {
        val renderData = this.renderData
        if (renderData != null) {
            renderData
        } else {
            val outputTexId = glGenTextureAndSetDefaultParams()
            val new = RenderData(
                outputTexId = outputTexId
            )
            this.renderData = new
        }
        tMediaPlayerLog.d(TAG) { "glSurfaceCreated" }
    }

    override fun drawFrame(
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
    ): Int {
        val renderData = renderData
        return if (imageDataType == ImageDataType.Rgba && renderData != null) {
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

    override fun glSurfaceDestroying() {
        val renderData = renderData
        if (renderData != null) {
            this.renderData = null
            GLES30.glDeleteTextures(1, intArrayOf(renderData.outputTexId), 0)
        }
        tMediaPlayerLog.d(TAG) { "glSurfaceDestroying" }
    }

    companion object {
        private const val TAG = "RgbaImageTextureConverter"

        private data class RenderData(
            val outputTexId: Int
        )
    }
}