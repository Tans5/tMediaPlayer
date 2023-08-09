package com.tans.tmediaplayer.render.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES30
import android.opengl.GLUtils
import android.os.SystemClock
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

    private val lumaImageCache: AtomicReference<ByteArray> by lazy {
        AtomicReference(null)
    }

    private val charLineWidth: AtomicInteger by lazy {
        AtomicInteger(85)
    }

    private val charPaint: Paint by lazy {
        Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            // typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
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
                val asciiHeight = (asciiWidth.toFloat() * input.height.toFloat() / input.width.toFloat()).toInt()
                val lumaImageBytes = ensureLumaImageCache(asciiHeight * asciiWidth * 4)
                offScreenRender(
                    outputTexId = renderData.lumaTexture,
                    outputTexWidth = asciiWidth,
                    outputTexHeight = asciiHeight
                ) {
                    GLES30.glUseProgram(renderData.lumaProgram)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, input.texture)
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.lumaProgram, "Texture"), 0)

                    GLES30.glBindVertexArray(renderData.lumaVao)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.lumaVbo)
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)

                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderData.lumaTexture)
                    GLES30.glReadPixels(
                        0, 0,
                        asciiWidth, asciiHeight,
                        GLES30.GL_RGBA,
                        GLES30.GL_UNSIGNED_BYTE,
                        ByteBuffer.wrap(lumaImageBytes)
                    )
                }
                offScreenRender(
                    outputTexId = renderData.charTexture,
                    outputTexWidth = input.width,
                    outputTexHeight = input.height
                ) {
                    GLES30.glEnable(GLES30.GL_BLEND)
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                    GLES30.glUseProgram(renderData.charProgram)
                    val charWidthGLStep = 2.0f / asciiWidth.toFloat()
                    val charHeightGLStep = 2.0f / asciiHeight.toFloat()
                    var renderWidthStart = -1.0f
                    var renderHeightStart = 1.0f
                    var pixelIndex = 0
                    val start = SystemClock.uptimeMillis()
                    for (h in 0 until asciiHeight) {
                        for (w in 0 until asciiWidth) {
                            val r = lumaImageBytes[pixelIndex ++].toUnsignedInt().toFloat() / 255.0f
                            val g = lumaImageBytes[pixelIndex ++].toUnsignedInt().toFloat() / 255.0f
                            val b = lumaImageBytes[pixelIndex ++].toUnsignedInt().toFloat() / 255.0f
                            val y = lumaImageBytes[pixelIndex ++].toUnsignedInt()
                            val chars = renderData.asciiArtChars.asReversed()
                            val charIndex = ((chars.size - 1).toFloat() * y.toFloat() / 255.0f + 0.5).toInt()
                            val char = chars[charIndex]
                            val widthStart = renderWidthStart
                            val widthEnd = widthStart + charWidthGLStep
                            val heightStart = renderHeightStart
                            val heightEnd = heightStart - charHeightGLStep
                            val vertices = floatArrayOf(
                                // 坐标(position 0)              // 纹理坐标
                                widthStart, heightStart,        0.0f, 1.0f,    // 左上角
                                widthEnd, heightStart,          1.0f, 1.0f,   // 右上角
                                widthEnd, heightEnd,            1.0f, 0.0f,   // 右下角
                                widthStart, heightEnd,          0.0f, 0.0f,   // 左下角
                            )
                            GLES30.glUniform3f(GLES30.glGetUniformLocation(renderData.charProgram, "TextColor"), r, g, b)
                            GLES30.glBindVertexArray(renderData.charVao)
                            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.charVbo)
                            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertices.size * 4, vertices.toGlBuffer())
                            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, char.texture)
                            GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.charProgram, "Texture"), 0)
                            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
                            renderWidthStart += charWidthGLStep
                        }
                        renderWidthStart = -1.0f
                        renderHeightStart -= charHeightGLStep
                    }
                    val end = SystemClock.uptimeMillis()
                    MediaLog.d(TAG, "Char render cost: ${end - start} ms")
                    GLES30.glDisable(GLES30.GL_BLEND)
                }
                FilterImageTexture(
                    width = input.width,
                    height = input.height,
                    texture = renderData.charTexture
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

            GLES30.glDeleteProgram(renderData.charProgram)
            GLES30.glDeleteTextures(1, intArrayOf(renderData.charTexture), 0)
            GLES30.glDeleteBuffers(1, intArrayOf(renderData.charVbo), 0)
            val charTextures = renderData.asciiArtChars.map { it.texture }.toIntArray()
            GLES30.glDeleteTextures(charTextures.size, charTextures, 0)
        }
    }

    private fun ensureLumaImageCache(size: Int): ByteArray {
        val array = lumaImageCache.get()
        return if (array == null || array.size != size) {
            val new = ByteArray(size)
            lumaImageCache.set(new)
            new
        } else {
            array
        }
    }

    private fun ensureRenderData(context: Context): RenderData? {
        val lastRenderData = renderData.get()
        return if (lastRenderData != null) {
            lastRenderData
        } else {
            val lumaProgram = compileShaderProgram(context, R.raw.t_media_player_ascii_filter_luma_vert, R.raw.t_media_player_ascii_filter_luma_frag)
            val charProgram = compileShaderProgram(context, R.raw.t_media_player_ascii_filter_char_vert, R.raw.t_media_player_ascii_filter_char_frag)
            if (lumaProgram != null && charProgram != null) {
                val lumaTexture = glGenTextureAndSetDefaultParams()
                val vertices = floatArrayOf(
                    // 坐标(position 0)   // 纹理坐标
                    -1.0f, 1.0f,        0.0f, 1.0f,    // 左上角
                    1.0f, 1.0f,         1.0f, 1.0f,   // 右上角
                    1.0f, -1.0f,        1.0f, 0.0f,   // 右下角
                    -1.0f, -1.0f,       0.0f, 0.0f,   // 左下角
                )
                val lumaVao = glGenVertexArrays()
                val lumaVbo = glGenBuffers()
                GLES30.glBindVertexArray(lumaVao)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, lumaVbo)
                GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES30.GL_STATIC_DRAW)

                val charTexture = glGenTextureAndSetDefaultParams()
                val charVao = glGenVertexArrays()
                val charVbo = glGenBuffers()
                GLES30.glBindVertexArray(charVao)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, charVbo)
                GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, null, GLES30.GL_STREAM_DRAW)

                val asciiArtChars = asciiChars.toCharArray().map { c -> createCharTexture(c) }
                val renderData = RenderData(
                    lumaProgram = lumaProgram,
                    lumaVao = lumaVao,
                    lumaVbo = lumaVbo,
                    lumaTexture = lumaTexture,

                    charProgram = charProgram,
                    charVao = charVao,
                    charVbo = charVbo,
                    charTexture = charTexture,
                    asciiArtChars = asciiArtChars
                )
                this.renderData.set(renderData)
                renderData
            } else {
                null
            }
        }
    }

    private inline fun Byte.toUnsignedInt(): Int = this.toInt() shl 24 ushr 24

    private fun createCharTexture(c: Char, width: Int = 16, height: Int = 16): CharTexture {
        val texture = glGenTextureAndSetDefaultParams()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        charPaint.textSize = max(width, height).toFloat() * 1.1f
        val metrics = charPaint.fontMetrics
        val charWidth = charPaint.measureText(c.toString())
        val x = max((width - charWidth) / 2.0f, 0.0f)
        val y = - metrics.top / (metrics.bottom - metrics.top) * height
        canvas.drawText(c.toString(), x, y, charPaint)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return CharTexture(texture, width, height)
    }

    companion object {
        private const val TAG = "AsciiArtImageFilter"
        private data class RenderData(
            val lumaProgram: Int,
            val lumaVao: Int,
            val lumaVbo: Int,
            val lumaTexture: Int,

            val charProgram: Int,
            val charVao: Int,
            val charVbo: Int,
            val charTexture: Int,
            val asciiArtChars: List<CharTexture>
        )

        private data class CharTexture(
            val texture: Int,
            val width: Int,
            val height: Int
        )

        const val MIN_CHAR_LINE_WIDTH = 16
        const val MAX_CHAR_LINE_WIDTH = 128

        private const val asciiChars = " `.-':_,^=;><+!rc*/z?sLTv)J7(|Fi{C}fI31tlu[neoZ5Yxjya]2ESwqkP6h9d4VpOGbUAKXHm8RD#\$Bg0MNWQ%&@"
    }
}