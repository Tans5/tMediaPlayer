package com.tans.tmediaplayer.render.filter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.opengl.GLES30
import android.os.SystemClock
import androidx.annotation.FloatRange
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
import kotlin.math.abs
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
        AtomicInteger(128)
    }

    private val reverseChar: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    private val reverseColor: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    private val colorFillRate: AtomicReference<Float> by lazy {
        AtomicReference(0.0f)
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

    fun reverseChar(reverse: Boolean) {
        reverseChar.set(reverse)
    }

    fun reverseColor(reverse: Boolean) {
        reverseColor.set(reverse)
    }

    fun colorFillRate(
        @FloatRange(from = 0.0, to = 1.0)
        rate: Float
    ) {
        colorFillRate.set(min(1.0f, max(0.0f, rate)))
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
                    val pixelSize = asciiWidth * asciiHeight
                    var pixelIndex = 0
                    val start = SystemClock.uptimeMillis()
                    GLES30.glBindVertexArray(renderData.charVao)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.charVertVbo)
                    renderData.charVert.let {
                        it[1] = -1.0f + charHeightGLStep
                        it[4] = -1.0f + charWidthGLStep
                        it[5] = -1.0f + charHeightGLStep
                        it[8] = -1.0f + charWidthGLStep
                    }
                    renderData.charVertBuffer.clear()
                    renderData.charVertBuffer.put(renderData.charVert)
                    renderData.charVertBuffer.position(0)
                    GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, 64, renderData.charVertBuffer)
                    val offsetCache = getCharOffsetCacheArray(pixelSize)
                    val offsetArray = offsetCache.floatArray
                    val colorTexCache = getCharColorAndTextureCacheArray(pixelSize)
                    val colorTexArray = colorTexCache.floatArray
                    var xOffset = 0.0f
                    var yOffset = 0.0f
                    for (h in 0 until asciiHeight) {
                        for (w in 0 until asciiWidth) {
                            offsetArray[pixelIndex * 2] = xOffset
                            offsetArray[pixelIndex * 2 + 1] = yOffset
                            val r = lumaImageBytes[pixelIndex * 4].toUnsignedInt()
                            val g = lumaImageBytes[pixelIndex * 4 + 1].toUnsignedInt()
                            val b = lumaImageBytes[pixelIndex * 4 + 2].toUnsignedInt()
                            val y = lumaImageBytes[pixelIndex * 4 + 3].toUnsignedInt()
                            val charIndex = if (reverseChar.get()) asciiLightLevelIndexReverse[y] else asciiLightLevelIndex[y]
                            colorTexArray[pixelIndex * 4] = r.toFloat()
                            colorTexArray[pixelIndex * 4 + 1] = g.toFloat()
                            colorTexArray[pixelIndex * 4 + 2] = b.toFloat()
                            colorTexArray[pixelIndex * 4 + 3] = charIndex.toFloat()

                            pixelIndex ++
                            xOffset += charWidthGLStep
                        }
                        xOffset = 0.0f
                        yOffset += charHeightGLStep
                    }
                    GLES30.glUniform1i(GLES30.glGetUniformLocation(renderData.charProgram, "reverseColor"), if (reverseColor.get()) 1 else 0)
                    GLES30.glUniform1f(GLES30.glGetUniformLocation(renderData.charProgram, "colorFillRate"), colorFillRate.get())
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.charOffsetVbo)
                    offsetCache.floatBuffer.apply {
                        clear()
                        put(offsetArray)
                        position(0)
                    }
                    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, offsetArray.size * 4, offsetCache.floatBuffer, GLES30.GL_STREAM_DRAW)
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.charColorAndTextureVbo)
                    colorTexCache.floatBuffer.apply {
                        clear()
                        put(colorTexArray)
                        position(0)
                    }
                    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, colorTexArray.size * 4, colorTexCache.floatBuffer, GLES30.GL_STREAM_DRAW)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, renderData.charTexturesArray)
                    GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_FAN, 0, 4, pixelSize)
                    GLES30.glDisable(GLES30.GL_BLEND)
                    val end = SystemClock.uptimeMillis()
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
            GLES30.glDeleteBuffers(3, intArrayOf(renderData.charVertVbo, renderData.charOffsetVbo, renderData.charColorAndTextureVbo), 0)
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
                val charVertVbo = glGenBuffers()
                val charOffsetVbo = glGenBuffers()
                val charColorAndTextureVbo = glGenBuffers()
                GLES30.glBindVertexArray(charVao)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, charVertVbo)
                GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 64, null, GLES30.GL_STREAM_DRAW)

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, charOffsetVbo)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 8, 0)
                GLES30.glEnableVertexAttribArray(1)

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, charColorAndTextureVbo)
                GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, 16, 0)
                GLES30.glEnableVertexAttribArray(2)

                GLES30.glVertexAttribDivisor(1, 1)
                GLES30.glVertexAttribDivisor(2, 1)


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
                    charVertVbo = charVertVbo,
                    charOffsetVbo = charOffsetVbo,
                    charColorAndTextureVbo = charColorAndTextureVbo,
                    charTexture = charTexture,
                    charTexturesArray = charTextures
                )
                this.renderData.set(renderData)
                renderData
            } else {
                null
            }
        }
    }

    private inline fun Byte.toUnsignedInt(): Int = this.toInt() shl 24 ushr 24

    private val lastCharOffsetCacheArray: AtomicReference<FloatArrayBufferCache?> by lazy {
        AtomicReference(null)
    }
    private fun getCharOffsetCacheArray(pixelSize: Int): FloatArrayBufferCache {
        val size = pixelSize shl 1
        val last = lastCharOffsetCacheArray.get()
        return if (last?.size == size) {
            last
        } else {
            val array = FloatArray(size)
            val buffer = array.toGlBuffer().asFloatBuffer()
            val new = FloatArrayBufferCache(
                size = size,
                floatArray = array,
                floatBuffer = buffer
            )
            lastCharOffsetCacheArray.set(new)
            new
        }
    }

    private val lastCharColorAndTextureCacheArray: AtomicReference<FloatArrayBufferCache?> by lazy {
        AtomicReference(null)
    }
    private fun getCharColorAndTextureCacheArray(pixelSize: Int): FloatArrayBufferCache {
        val size = pixelSize shl 2
        val last = lastCharColorAndTextureCacheArray.get()
        return if (last?.size == size) {
            last
        } else {
            val array = FloatArray(size)
            val buffer = array.toGlBuffer().asFloatBuffer()
            val new = FloatArrayBufferCache(
                size = size,
                floatArray = array,
                floatBuffer = buffer
            )
            lastCharColorAndTextureCacheArray.set(new)
            new
        }
    }


    companion object {
        private const val TAG = "AsciiArtImageFilter"

        private data class FloatArrayBufferCache(
            val size: Int,
            val floatArray: FloatArray,
            val floatBuffer: FloatBuffer
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as FloatArrayBufferCache

                if (size != other.size) return false
                if (!floatArray.contentEquals(other.floatArray)) return false
                if (floatBuffer != other.floatBuffer) return false

                return true
            }

            override fun hashCode(): Int {
                var result = size
                result = 31 * result + floatArray.contentHashCode()
                result = 31 * result + floatBuffer.hashCode()
                return result
            }
        }

        private data class RenderData(
            val lumaProgram: Int,
            val lumaVao: Int,
            val lumaVbo: Int,
            val lumaTexture: Int,

            val charProgram: Int,
            val charTexture: Int,
            val charVao: Int,
            val charVertVbo: Int,
            val charVert: FloatArray = floatArrayOf(
                // 坐标(position 0)    // 纹理坐标
                -1.0f, -0.9f,         0.0f, 1.0f,    // 左上角
                -0.9f, -0.9f,         1.0f, 1.0f,   // 右上角
                -0.9f, -1.0f,         1.0f, 0.0f,   // 右下角
                -1.0f, -1.0f,         0.0f, 0.0f,   // 左下角
            ),
            val charVertBuffer: FloatBuffer = charVert.toGlBuffer().asFloatBuffer(),
            val charOffsetVbo: Int,
            val charColorAndTextureVbo: Int,

            val charTexturesArray: Int
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
                if (charTexture != other.charTexture) return false
                if (charVao != other.charVao) return false
                if (charVertVbo != other.charVertVbo) return false
                if (!charVert.contentEquals(other.charVert)) return false
                if (charVertBuffer != other.charVertBuffer) return false
                if (charOffsetVbo != other.charOffsetVbo) return false
                if (charColorAndTextureVbo != other.charColorAndTextureVbo) return false
                if (charTexturesArray != other.charTexturesArray) return false

                return true
            }

            override fun hashCode(): Int {
                var result = lumaProgram
                result = 31 * result + lumaVao
                result = 31 * result + lumaVbo
                result = 31 * result + lumaTexture
                result = 31 * result + charProgram
                result = 31 * result + charTexture
                result = 31 * result + charVao
                result = 31 * result + charVertVbo
                result = 31 * result + charVert.contentHashCode()
                result = 31 * result + charVertBuffer.hashCode()
                result = 31 * result + charOffsetVbo
                result = 31 * result + charColorAndTextureVbo
                result = 31 * result + charTexturesArray
                return result
            }

        }

        const val MIN_CHAR_LINE_WIDTH = 16
        const val MAX_CHAR_LINE_WIDTH = 256

        private const val asciiChars = " `.-':_,^=;><+!rc*/z?sLTv)J7(|Fi{C}fI31tlu[neoZ5Yxjya]2ESwqkP6h9d4VpOGbUAKXHm8RD#\$Bg0MNWQ%&@"

        private val asciiCharsLightLevel = doubleArrayOf(0.00, 0.0751, 0.0829, 0.0848, 0.1227, 0.1403, 0.1559, 0.185, 0.2183, 0.2417, 0.2571, 0.2852, 0.2902, 0.2919, 0.3099, 0.3192, 0.3232, 0.3294, 0.3384, 0.3609, 0.3619, 0.3667, 0.3737, 0.3747, 0.3838, 0.3921, 0.396, 0.3984, 0.3993, 0.4075, 0.4091, 0.4101, 0.42, 0.423, 0.4247, 0.4274, 0.4293, 0.4328, 0.4382, 0.4385, 0.442, 0.4473, 0.4477, 0.4503, 0.4562, 0.458, 0.461, 0.4638, 0.4667, 0.4686, 0.4693, 0.4703, 0.4833, 0.4881, 0.4944, 0.4953, 0.4992, 0.5509, 0.5567, 0.5569, 0.5591, 0.5602, 0.5602, 0.565, 0.5776, 0.5777, 0.5818, 0.587, 0.5972, 0.5999, 0.6043, 0.6049, 0.6093, 0.6099, 0.6465, 0.6561, 0.6595, 0.6631, 0.6714, 0.6759, 0.6809, 0.6816, 0.6925, 0.7039, 0.7086, 0.7235, 0.7302, 0.7332, 0.7602, 0.7834, 0.8037, 0.9999)
        private fun findClosestCharLightLevelIndex(inputLightLevel: Double): Int {
            var targetPreIndex = 0
            var targetNextIndex = -1
            for (i in asciiCharsLightLevel.indices) {
                if (asciiCharsLightLevel[i] < inputLightLevel) {
                    targetPreIndex = i
                }
                if (asciiCharsLightLevel[i] > inputLightLevel) {
                    targetNextIndex = i
                    break
                }
            }
            return if (targetNextIndex < 0) {
                asciiCharsLightLevel.lastIndex
            } else {
                val preValue = asciiCharsLightLevel[targetPreIndex]
                val nextValue = asciiCharsLightLevel[targetNextIndex]
                if (abs(inputLightLevel - preValue) > abs(inputLightLevel - nextValue)) {
                    targetNextIndex
                } else {
                    targetPreIndex
                }
            }
        }

        private val asciiLightLevelIndex: IntArray = IntArray(256) { i ->
            val lightLevel = i.toDouble() / 255.0
            findClosestCharLightLevelIndex(lightLevel)
        }

        private val asciiLightLevelIndexReverse: IntArray = IntArray(256) { i ->
            val lightLevel = (255.0 - i.toDouble()) / 255.0
            findClosestCharLightLevelIndex(lightLevel)
        }

    }
}