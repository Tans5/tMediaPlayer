package com.tans.tmediaplayer.player.playerview.texconverter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.R
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.playerview.compileShaderProgram
import com.tans.tmediaplayer.player.playerview.glGenBuffers
import com.tans.tmediaplayer.player.playerview.glGenTextureAndSetDefaultParams
import com.tans.tmediaplayer.player.playerview.glGenVertexArrays
import com.tans.tmediaplayer.player.playerview.offScreenRender
import com.tans.tmediaplayer.player.playerview.toGlBuffer
import java.nio.ByteBuffer

internal class Yuv420spImageTextureConverter : ImageTextureConverter() {

    private var renderData: RenderData? = null

    override fun glSurfaceCreated(context: Context) {
        val program = compileShaderProgram(context, R.raw.t_media_player_yuv420sp_vert, R.raw.t_media_player_yuv420sp_frag)
        if (program != null) {
            val outputTexId = glGenTextureAndSetDefaultParams()
            val yTexId = glGenTextureAndSetDefaultParams()
            val uvTexId = glGenTextureAndSetDefaultParams()
            val vertices = floatArrayOf(
                // 坐标(position 0)   // 纹理坐标
                -1.0f, 1.0f,         0.0f, 1.0f,    // 左上角
                1.0f, 1.0f,          1.0f, 1.0f,   // 右上角
                1.0f, -1.0f,         1.0f, 0.0f,   // 右下角
                -1.0f, -1.0f,        0.0f, 0.0f,   // 左下角
            )
            val vao = glGenVertexArrays()
            val vbo = glGenBuffers()
            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES30.GL_STATIC_DRAW)
            renderData = RenderData(
                yTexId = yTexId,
                uvTexId = uvTexId,
                vao = vao,
                vbo = vbo,
                program = program,
                outputTexId = outputTexId
            )
        } else {
            tMediaPlayerLog.e(TAG) { "Compile program fail" }
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
        imageDataType: ImageRawType
    ): Int {
        return if (imageDataType == ImageRawType.Nv12 || imageDataType == ImageRawType.Nv21) {
            val renderData = this.renderData
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

                    // uv
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.uvTexId)
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE_ALPHA, imageWidth / 2, imageHeight / 2,
                        0, GLES30.GL_LUMINANCE_ALPHA, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(uvBytes!!))
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.program, "uvTexture"), 1)

                    GLES30.glUniform1i(
                        GLES30.glGetUniformLocation(renderData.program, "swapUv"),
                        when (imageDataType) {
                            ImageRawType.Nv12 -> 0
                            else -> 1
                        }
                    )

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

    override fun glSurfaceDestroying() {
        val renderData = this.renderData
        if (renderData != null) {
            this.renderData = null
            GLES30.glDeleteTextures(1, intArrayOf(renderData.yTexId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.uvTexId), 0)
            GLES30.glDeleteBuffers(1, intArrayOf(renderData.vbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.outputTexId), 0)
            GLES30.glDeleteProgram(renderData.program)
        }
        tMediaPlayerLog.d(TAG) { "glSurfaceDestroying" }
    }

    companion object {
        private const val TAG = "Yuv420spImageTextureConverter"
        private data class RenderData(
            val yTexId: Int,
            val uvTexId: Int,
            val vao: Int,
            val vbo: Int,
            val program: Int,
            val outputTexId: Int
        )
    }
}