package com.tans.tmediaplayer.render.texconverter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.R
import com.tans.tmediaplayer.render.compileShaderProgram
import com.tans.tmediaplayer.render.glGenBuffers
import com.tans.tmediaplayer.render.glGenTextureAndSetDefaultParams
import com.tans.tmediaplayer.render.glGenVertexArrays
import com.tans.tmediaplayer.render.offScreenRender
import com.tans.tmediaplayer.render.tMediaPlayerView
import com.tans.tmediaplayer.render.toGlBuffer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal class Yuv420spImageTextureConverter : ImageTextureConverter {

    private val renderData: AtomicReference<RenderData?> by lazy {
        AtomicReference()
    }

    override fun convertImageToTexture(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        imageData: tMediaPlayerView.Companion.ImageData
    ): Int {
        return if (imageData.imageRawData is tMediaPlayerView.Companion.ImageRawData.Yuv420spRawData) {
            val renderData = ensureRenderData(context)
            if (renderData != null) {
                val rawImageData = imageData.imageRawData
                offScreenRender(
                    outputTexId = renderData.outputTexId,
                    outputTexWidth = imageData.imageWidth,
                    outputTexHeight = imageData.imageHeight
                ) {
                    GLES30.glUseProgram(renderData.program)
                    // y
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.yTexId)
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, imageData.imageWidth, imageData.imageHeight,
                        0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(rawImageData.yBytes))
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.program, "yTexture"), 0)

                    // uv
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.uvTexId)
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE_ALPHA, imageData.imageWidth / 2, imageData.imageHeight / 2,
                        0, GLES30.GL_LUMINANCE_ALPHA, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(rawImageData.uvBytes))
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.program, "uvTexture"), 1)

                    GLES30.glUniform1i(
                        GLES30.glGetUniformLocation(renderData.program, "swapUv"),
                        when (rawImageData.yuv420spType) {
                            tMediaPlayerView.Companion.Yuv420spType.Nv12 -> 0
                            tMediaPlayerView.Companion.Yuv420spType.Nv21 -> 1
                        }
                    )

                    GLES30.glBindVertexArray(renderData.vao)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.vbo)
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
                }
                renderData.outputTexId
            } else {
                MediaLog.e(TAG, "Render data is null.")
                0
            }
        } else {
            MediaLog.e(TAG, "Wrong image type: ${imageData.imageRawData::class.java.simpleName}")
            0
        }
    }

    override fun recycle() {
        val renderData = this.renderData.get()
        if (renderData != null) {
            this.renderData.set(null)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.yTexId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.uvTexId), 0)
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
            val program = compileShaderProgram(context, R.raw.t_media_player_yuv420sp_vert, R.raw.t_media_player_yuv420sp_frag) ?: return null
            val outputTexId = glGenTextureAndSetDefaultParams()
            val yTexId = glGenTextureAndSetDefaultParams()
            val uvTexId = glGenTextureAndSetDefaultParams()
            val vertices = floatArrayOf(
                // 坐标(position 0)   // 纹理坐标
                -1.0f, 1.0f, 0.0f,   0.0f, 1.0f,    // 左上角
                1.0f, 1.0f, 0.0f,    1.0f, 1.0f,   // 右上角
                1.0f, -1.0f, 0.0f,   1.0f, 0.0f,   // 右下角
                -1.0f, -1.0f, 0.0f,  0.0f, 0.0f,   // 左下角
            )
            val vao = glGenVertexArrays()
            val vbo = glGenBuffers()
            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES30.GL_STATIC_DRAW)
            val result = RenderData(
                yTexId = yTexId,
                uvTexId = uvTexId,
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
        private const val TAG = "Yuv420spImageTextureConverter"
        data class RenderData(
            val yTexId: Int,
            val uvTexId: Int,
            val vao: Int,
            val vbo: Int,
            val program: Int,
            val outputTexId: Int
        )
    }
}