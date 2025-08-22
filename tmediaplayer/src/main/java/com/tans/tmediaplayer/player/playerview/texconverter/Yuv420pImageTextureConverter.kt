package com.tans.tmediaplayer.player.playerview.texconverter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.R
import com.tans.tmediaplayer.player.playerview.ImageDataType
import com.tans.tmediaplayer.player.playerview.compileShaderProgram
import com.tans.tmediaplayer.player.playerview.glGenBuffers
import com.tans.tmediaplayer.player.playerview.glGenTextureAndSetDefaultParams
import com.tans.tmediaplayer.player.playerview.glGenVertexArrays
import com.tans.tmediaplayer.player.playerview.offScreenRender
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView
import com.tans.tmediaplayer.player.playerview.toGlBuffer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal class Yuv420pImageTextureConverter : ImageTextureConverter {

    private val renderData: AtomicReference<RenderData?> by lazy {
        AtomicReference()
    }

    override fun convertImageToTexture(
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
        return if (imageDataType == ImageDataType.Yuv420p) {
            val renderData = ensureRenderData(context)
            if (renderData != null) {
                offScreenRender(
                    outputTexId = renderData.outputTexId,
                    outputTexWidth = imageWidth,
                    outputTexHeight = imageHeight
                ) {
                    GLES30.glUseProgram(renderData.program)
                    // y
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.yTexId)
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, imageWidth, imageHeight,
                        0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(yBytes!!))
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.program, "yTexture"), 0)

                    // u
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.uTexId)
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, imageWidth / 2, imageHeight / 2,
                        0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(uBytes!!))
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.program, "uTexture"), 1)

                    // v
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.vTexId)
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, imageWidth / 2, imageHeight / 2,
                        0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(vBytes!!))
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.program, "vTexture"), 2)

                    GLES30.glBindVertexArray(renderData.vao)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.vbo)
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
                }
                renderData.outputTexId
            } else {
                tMediaPlayerLog.e(TAG) { "Render data is null." }
                0
            }
        } else {
            tMediaPlayerLog.e(TAG) { "Wrong image type: $imageDataType" }
            0
        }
    }

    override fun recycle() {
        val renderData = this.renderData.get()
        if (renderData != null) {
            this.renderData.set(null)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.yTexId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.uTexId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.vTexId), 0)
            GLES30.glDeleteBuffers(1, intArrayOf(renderData.vbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.outputTexId), 0)
            GLES30.glDeleteProgram(renderData.program)
        }
    }

    private fun ensureRenderData(context: Context): RenderData? {
        val renderData = renderData.get()
        if (renderData != null) {
            return renderData
        } else {
            val program = compileShaderProgram(context, R.raw.t_media_player_yuv420p_vert, R.raw.t_media_player_yuv420p_frag) ?: return null
            val outputTexId = glGenTextureAndSetDefaultParams()
            val yTexId = glGenTextureAndSetDefaultParams()
            val uTexId = glGenTextureAndSetDefaultParams()
            val vTexId = glGenTextureAndSetDefaultParams()
            val vertices = floatArrayOf(
                // 坐标(position 0)   // 纹理坐标
                -1.0f, 1.0f,        0.0f, 1.0f,    // 左上角
                1.0f, 1.0f,         1.0f, 1.0f,   // 右上角
                1.0f, -1.0f,        1.0f, 0.0f,   // 右下角
                -1.0f, -1.0f,       0.0f, 0.0f,   // 左下角
            )
            val vao = glGenVertexArrays()
            val vbo = glGenBuffers()
            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES30.GL_STATIC_DRAW)
            val result = RenderData(
                yTexId = yTexId,
                uTexId = uTexId,
                vTexId = vTexId,
                vao = vao,
                vbo = vbo,
                program = program,
                outputTexId = outputTexId
            )
            this.renderData.set(result)
            return result
        }
    }

    companion object {
        private const val TAG = "Yuv420pImageTextureConverter"
        data class RenderData(
            val yTexId: Int,
            val uTexId: Int,
            val vTexId: Int,
            val vao: Int,
            val vbo: Int,
            val program: Int,
            val outputTexId: Int
        )
    }
}