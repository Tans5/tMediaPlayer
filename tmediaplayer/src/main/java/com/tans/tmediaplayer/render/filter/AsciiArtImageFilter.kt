package com.tans.tmediaplayer.render.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.opengl.GLES30
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
import java.nio.FloatBuffer
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
        AtomicInteger(64)
    }

    private val revertChar: AtomicReference<Boolean> by lazy {
        AtomicReference(false)
    }

    private val showImageColor:  AtomicReference<Boolean> by lazy {
        AtomicReference(false)
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

    fun revertChar(revert: Boolean) {
        revertChar.set(revert)
    }

    fun showImageColor(showColor: Boolean) {
        showImageColor.set(showColor)
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
                    var renderHeightStart = -1.0f
                    var pixelIndex = 0
                    val start = SystemClock.uptimeMillis()
                    GLES30.glUniform3i(GLES30.glGetUniformLocation(renderData.charProgram, "TextColor"), 255, 255, 255)
                    GLES30.glBindVertexArray(renderData.charVao)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.charVbo)
                    for (h in 0 until asciiHeight) {
                        for (w in 0 until asciiWidth) {
                            if (showImageColor.get()) {
                                val r = lumaImageBytes[pixelIndex ++].toUnsignedInt()
                                val g = lumaImageBytes[pixelIndex ++].toUnsignedInt()
                                val b = lumaImageBytes[pixelIndex ++].toUnsignedInt()
                                GLES30.glUniform3i(GLES30.glGetUniformLocation(renderData.charProgram, "TextColor"), r, g, b)
                            } else {
                                pixelIndex += 3
                            }
                            val y = lumaImageBytes[pixelIndex ++].toUnsignedInt()
                            val charIndex = if (revertChar.get()) renderData.asciiIndexRevers[y] else renderData.asciiIndex[y]
                            val widthStart = renderWidthStart
                            val widthEnd = renderWidthStart + charWidthGLStep
                            val heightStart = renderHeightStart + charHeightGLStep
                            val heightEnd = renderHeightStart
                            renderData.charVert[0] = widthStart
                            renderData.charVert[1] = heightStart
                            renderData.charVert[4] = charIndex.toFloat()

                            renderData.charVert[5] = widthEnd
                            renderData.charVert[6] = heightStart
                            renderData.charVert[9] = charIndex.toFloat()

                            renderData.charVert[10] = widthEnd
                            renderData.charVert[11] = heightEnd
                            renderData.charVert[14] = charIndex.toFloat()

                            renderData.charVert[15] = widthStart
                            renderData.charVert[16] = heightEnd
                            renderData.charVert[19] = charIndex.toFloat()

                            renderData.charVertBuffer.clear()
                            renderData.charVertBuffer.put(renderData.charVert)
                            renderData.charVertBuffer.position(0)
                            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 80, renderData.charVertBuffer, GLES30.GL_STREAM_DRAW)
                            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, renderData.charTexturesArray)
                            GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.charProgram, "Texture"), 0)
                            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
                            renderWidthStart += charWidthGLStep
                        }
                        renderWidthStart = -1.0f
                        renderHeightStart += charHeightGLStep
                    }
                    val end = SystemClock.uptimeMillis()
                    GLES30.glDisable(GLES30.GL_BLEND)
                    MediaLog.d(TAG, "Char render cost: ${end - start} ms")
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
            GLES30.glDeleteTextures(1, intArrayOf(renderData.charTexturesArray), 0)
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
                GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 20, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 20, 8)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 80, null, GLES30.GL_STREAM_DRAW)

                val charTexturesArray = IntArray(1)
                GLES30.glGenTextures(1, charTexturesArray, 0)
                val charTextures = charTexturesArray[0]
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, charTextures)
                val charSize = 16
                // GLES30.glTexStorage3D(GLES30.GL_TEXTURE_2D_ARRAY, 1, GLES30.GL_RGBA8, charSize, charSize, asciiChars.length)
                GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_RGBA, charSize, charSize, asciiChars.length, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY)
                charPaint.textSize = charSize.toFloat() * 1.1f
                for ((i, c) in asciiChars.toCharArray().withIndex()) {
                    val bitmap = Bitmap.createBitmap(charSize, charSize, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val metrics = charPaint.fontMetrics
                    val charWidth = charPaint.measureText(c.toString())
                    val x = max((charSize - charWidth) / 2.0f, 0.0f)
                    val y = - metrics.top / (metrics.bottom - metrics.top) * charSize
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    canvas.drawText(c.toString(), x, y, charPaint)
                    val b = ByteBuffer.allocate(charSize * charSize * 4)
                    bitmap.copyPixelsToBuffer(b)
                    b.position(0)
                    GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, charSize, charSize, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, b)
                    bitmap.recycle()
                }

                val asciiIndex = IntArray(256) { i ->
                    ((asciiChars.length - 1).toFloat() * i.toFloat() / 255.0f + 0.5f).toInt()
                }

                val renderData = RenderData(
                    lumaProgram = lumaProgram,
                    lumaVao = lumaVao,
                    lumaVbo = lumaVbo,
                    lumaTexture = lumaTexture,

                    charProgram = charProgram,
                    charVao = charVao,
                    charVbo = charVbo,
                    charTexture = charTexture,
                    charTexturesArray = charTextures,
                    asciiIndex = asciiIndex,
                    asciiIndexRevers = asciiIndex.toList().asReversed().toIntArray()
                )
                this.renderData.set(renderData)
                renderData
            } else {
                null
            }
        }
    }

    private inline fun Byte.toUnsignedInt(): Int = this.toInt() shl 24 ushr 24

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
            val charVert: FloatArray = floatArrayOf(
                // 坐标(position 0) // 纹理坐标
                0.0f, 0.0f,        0.0f, 1.0f, 0.0f,   // 左上角
                0.0f, 0.0f,        1.0f, 1.0f, 0.0f,  // 右上角
                0.0f, 0.0f,        1.0f, 0.0f, 0.0f,  // 右下角
                0.0f, 0.0f,        0.0f, 0.0f, 0.0f,  // 左下角
            ),
            val charTexturesArray: Int,
            val charVertBuffer: FloatBuffer = charVert.toGlBuffer().asFloatBuffer(),
            val asciiIndex: IntArray,
            val asciiIndexRevers: IntArray
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as RenderData

                if (lumaProgram != other.lumaProgram) return false
                if (lumaVao != other.lumaVao) return false
                if (lumaVbo != other.lumaVbo) return false
                if (lumaTexture != other.lumaTexture) return false
                if (charProgram != other.charProgram) return false
                if (charVao != other.charVao) return false
                if (charVbo != other.charVbo) return false
                if (charTexture != other.charTexture) return false
                if (!charVert.contentEquals(other.charVert)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = lumaProgram
                result = 31 * result + lumaVao
                result = 31 * result + lumaVbo
                result = 31 * result + lumaTexture
                result = 31 * result + charProgram
                result = 31 * result + charVao
                result = 31 * result + charVbo
                result = 31 * result + charTexture
                result = 31 * result + charVert.contentHashCode()
                return result
            }
        }

        const val MIN_CHAR_LINE_WIDTH = 16
        const val MAX_CHAR_LINE_WIDTH = 128

        private const val asciiChars = " `.-':_,^=;><+!rc*/z?sLTv)J7(|Fi{C}fI31tlu[neoZ5Yxjya]2ESwqkP6h9d4VpOGbUAKXHm8RD#\$Bg0MNWQ%&@"
    }
}