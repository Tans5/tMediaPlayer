package com.tans.tmediaplayer.render.filter

import android.content.Context
import android.opengl.GLES30
import com.tans.tmediaplayer.R
import com.tans.tmediaplayer.render.compileShaderProgram
import com.tans.tmediaplayer.render.glGenFrameBuffer
import com.tans.tmediaplayer.render.glGenTextureAndSetDefaultParams
import com.tans.tmediaplayer.render.glGenVertexArrays
import com.tans.tmediaplayer.render.offScreenRender
import com.tans.tmediaplayer.render.tMediaPlayerView
import com.tans.tmediaplayer.render.toGlBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

class AsciiArtImageFilter : ImageFilter {

    private val isEnable: AtomicBoolean by lazy { AtomicBoolean(false) }

    private val renderData: AtomicReference<RenderData?> by lazy {
        AtomicReference(null)
    }

    private val charLineWidth: AtomicInteger by lazy {
        AtomicInteger(128)
    }

    fun setCharLineWidth(width: Int) {
        charLineWidth.set(min(max(MIN_CHAR_LINE_WIDTH, width), MAX_CHAR_LINE_WIDTH))
    }

    override fun enable(enable: Boolean) {
        isEnable.set(enable)
    }

    override fun isEnable(): Boolean = isEnable.get()

    override fun filter(
        context: Context,
        surfaceSize: tMediaPlayerView.Companion.SurfaceSizeCache,
        input: FilterImageTexture
    ): FilterImageTexture {
        return if (isEnable()) {
            val renderData = ensureRenderData(context)
            if (renderData != null) {
                val asciiWidth = charLineWidth.get()
                val asciiHeight = (asciiWidth.toFloat() * input.height.toFloat() / input.width.toFloat() + 0.5f).toInt()
                offScreenRender(
                    outputTexId = renderData.lumaTexture,
                    outputTexWidth = asciiWidth,
                    outputTexHeight = asciiHeight
                ) {
                    GLES30.glUseProgram(renderData.lumaProgram)
                    GLES30.glBindVertexArray(renderData.lumaVao)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.lumaVbo)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, input.texture)
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
                }
                FilterImageTexture(
                    width = asciiWidth,
                    height = asciiHeight,
                    texture = renderData.lumaTexture
                )
            } else {
                input
            }
        } else {
            input
        }
    }


    override fun recycle() {
        val renderData = renderData.get()
        if (renderData != null) {
            this.renderData.set(null)
            GLES30.glDeleteProgram(renderData.lumaProgram)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.lumaTexture), 0)
            GLES30.glDeleteBuffers(1, intArrayOf(renderData.lumaVbo), 0)
        }
    }

    private fun ensureRenderData(context: Context): RenderData? {
        val lastRenderData = renderData.get()
        return if (lastRenderData != null) {
            lastRenderData
        } else {
            val lumaProgram = compileShaderProgram(context, R.raw.t_media_player_ascii_filter_luma_vert, R.raw.t_media_player_ascii_filter_luma_frag)
            if (lumaProgram != null) {
                val lumaTexture = glGenTextureAndSetDefaultParams()
                val vertices = floatArrayOf(
                    // 坐标(position 0)   // 纹理坐标
                    -1.0f, 1.0f, 0.0f,   0.0f, 1.0f,    // 左上角
                    1.0f, 1.0f, 0.0f,    1.0f, 1.0f,   // 右上角
                    1.0f, -1.0f, 0.0f,   1.0f, 0.0f,   // 右下角
                    -1.0f, -1.0f, 0.0f,  0.0f, 0.0f,   // 左下角
                )
                val lumaVao = glGenVertexArrays()
                val lumaVbo = glGenFrameBuffer()
                GLES30.glBindVertexArray(lumaVao)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, lumaVbo)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES30.GL_STATIC_DRAW)
                val renderData = RenderData(
                    lumaProgram = lumaProgram,
                    lumaVao = lumaVao,
                    lumaVbo = lumaVbo,
                    lumaTexture = lumaTexture
                )
                this.renderData.set(renderData)
                renderData
            } else {
                null
            }
        }
    }

    companion object {
        private data class RenderData(
            val lumaProgram: Int,
            val lumaVao: Int,
            val lumaVbo: Int,
            val lumaTexture: Int
        )

        const val MIN_CHAR_LINE_WIDTH = 64
        const val MAX_CHAR_LINE_WIDTH = 256
    }
}