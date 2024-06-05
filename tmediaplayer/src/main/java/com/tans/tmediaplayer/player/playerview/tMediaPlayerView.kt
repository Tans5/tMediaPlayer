package com.tans.tmediaplayer.player.playerview

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.R
import com.tans.tmediaplayer.player.playerview.filter.AsciiArtImageFilter
import com.tans.tmediaplayer.player.playerview.filter.FilterImageTexture
import com.tans.tmediaplayer.player.playerview.texconverter.RgbaImageTextureConverter
import com.tans.tmediaplayer.player.playerview.texconverter.Yuv420pImageTextureConverter
import com.tans.tmediaplayer.player.playerview.texconverter.Yuv420spImageTextureConverter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Suppress("ClassName")
class tMediaPlayerView : GLSurfaceView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)

    private var renderer: FrameRenderer? = null

    private val nextRenderFrame: AtomicReference<ImageData?> by lazy {
        AtomicReference(null)
    }

    private val scaleType: AtomicReference<ScaleType> by lazy {
        AtomicReference(ScaleType.CenterFit)
    }

    private val rgbaTexConverter: RgbaImageTextureConverter by lazy {
        RgbaImageTextureConverter()
    }

    private val yuv420pTexConverter: Yuv420pImageTextureConverter by lazy {
        Yuv420pImageTextureConverter()
    }

    private val yuv420spTexConverter: Yuv420spImageTextureConverter by lazy {
        Yuv420spImageTextureConverter()
    }

    private val asciiArtFilter: AsciiArtImageFilter by lazy {
        AsciiArtImageFilter()
    }

    init {
        setEGLContextClientVersion(3)
        setRenderer(FrameRenderer().apply { this@tMediaPlayerView.renderer = this })
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setScaleType(scaleType: ScaleType) {
        this.scaleType.set(scaleType)
    }

    fun enableAsciiArtFilter(enable: Boolean) {
        asciiArtFilter.enable(enable)
    }

    fun getAsciiArtImageFilter(): AsciiArtImageFilter {
        return asciiArtFilter
    }

    fun getScaleType(): ScaleType = this.scaleType.get()

    fun requestRenderRgbaFrame(width: Int, height: Int, imageBytes: ByteArray, callback: Runnable? = null) {
        val imageData = ImageData(
            imageWidth = width,
            imageHeight = height,
            imageRawData = ImageRawData.RgbaRawData(rgbaBytes = imageBytes),
            callback
        )
        nextRenderFrame.getAndSet(imageData)?.let {
            if (it.hasInvokedCallback.compareAndSet(false, true)) {
                it.callback?.run()
            }
        }
        requestRender()
    }

    fun requestRenderYuv420pFrame(
        width: Int,
        height: Int,
        yBytes: ByteArray,
        uBytes: ByteArray,
        vBytes: ByteArray,
        callback: Runnable? = null) {
        val imageData = ImageData(
            imageWidth = width,
            imageHeight = height,
            imageRawData = ImageRawData.Yuv420pRawData(
                yBytes = yBytes,
                uBytes = uBytes,
                vBytes = vBytes
            ),
            callback = callback
        )
        nextRenderFrame.getAndSet(imageData)?.let {
            if (it.hasInvokedCallback.compareAndSet(false, true)) {
                it.callback?.run()
            }
        }
        requestRender()
    }

    fun requestRenderNv12Frame(
        width: Int,
        height: Int,
        yBytes: ByteArray,
        uvBytes: ByteArray,
        callback: Runnable? = null
    ) {
        val imageData = ImageData(
            imageWidth = width,
            imageHeight = height,
            imageRawData = ImageRawData.Yuv420spRawData(
                yBytes = yBytes,
                uvBytes = uvBytes,
                yuv420spType = Yuv420spType.Nv12
            ),
            callback = callback
        )
        nextRenderFrame.getAndSet(imageData)?.let {
            if (it.hasInvokedCallback.compareAndSet(false, true)) {
                it.callback?.run()
            }
        }
        requestRender()
    }

    fun requestRenderNv21Frame(
        width: Int,
        height: Int,
        yBytes: ByteArray,
        vuBytes: ByteArray,
        callback: Runnable? = null
    ) {
        val imageData = ImageData(
            imageWidth = width,
            imageHeight = height,
            imageRawData = ImageRawData.Yuv420spRawData(
                yBytes = yBytes,
                uvBytes = vuBytes,
                yuv420spType = Yuv420spType.Nv21
            ),
            callback = callback
        )
        nextRenderFrame.getAndSet(imageData)?.let {
            if (it.hasInvokedCallback.compareAndSet(false, true)) {
                it.callback?.run()
            }
        }
        requestRender()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer?.recycle()
        renderer = null
    }

    private inner class FrameRenderer : Renderer {

        private var sizeCache: SurfaceSizeCache? = null

        private var glRendererData: GLRendererData? = null

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            val glVersion = gl.glGetString(GLES30.GL_VERSION)
            MediaLog.d(TAG, "Support gl version: $glVersion")
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            val program = compileShaderProgram(context, R.raw.t_media_player_vert, R.raw.t_media_player_frag)
            if (program != null) {
                val VAOArray = IntArray(1)
                GLES30.glGenVertexArrays(1, VAOArray, 0)
                val VAO = VAOArray[0]
                val VBOArray = IntArray(1)
                GLES30.glGenBuffers(1, VBOArray, 0)
                val VBO = VBOArray[0]

                GLES30.glBindVertexArray(VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, VBO)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 5 * 4 * 4 + 4, null, GLES30.GL_STREAM_DRAW)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 5 * 4, 3 * 4)
                GLES30.glEnableVertexAttribArray(1)
                glRendererData = GLRendererData(
                    program = program,
                    VAO = VAO,
                    VBO = VBO
                )
            }
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            sizeCache = SurfaceSizeCache(gl, width, height)
            GLES30.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10) {
            val rendererData = this.glRendererData
            val screenSize = sizeCache
            val imageData =  this@tMediaPlayerView.nextRenderFrame.get()
            if (rendererData != null && screenSize != null && imageData != null) {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val texConverter = when (imageData.imageRawData) {
                    is ImageRawData.RgbaRawData -> rgbaTexConverter
                    is ImageRawData.Yuv420pRawData -> yuv420pTexConverter
                    is ImageRawData.Yuv420spRawData -> yuv420spTexConverter
                }
                val convertTextureId = texConverter.convertImageToTexture(context = context, surfaceSize = screenSize, imageData = imageData)

                val filterOutput = asciiArtFilter.filter(
                    context = context,
                    surfaceSize = screenSize,
                    input = FilterImageTexture(
                        texture = convertTextureId,
                        width = imageData.imageWidth,
                        height = imageData.imageHeight
                    )
                )

                GLES30.glUseProgram(rendererData.program)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, filterOutput.texture)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(rendererData.program, "Texture"), 0)
                val imageRatio = filterOutput.width.toFloat() / filterOutput.height.toFloat()
                val renderRatio = screenSize.width.toFloat() / screenSize.height.toFloat()
                val scaleType = this@tMediaPlayerView.getScaleType()

                val (textureTl, textureRb) = when (scaleType) {
                    ScaleType.CenterFit -> {
                        Point(0.0f, 0.0f) to Point(1.0f, 1.0f)
                    }
                    ScaleType.CenterCrop -> {
                        centerCropTextureRect(
                            targetRatio = renderRatio / imageRatio,
                            topLeftPoint = Point(0.0f, 0.0f),
                            bottomRightPoint = Point(1.0f, 1.0f)
                        )
                    }
                }

                val (positionTl, positionRb) = when (scaleType) {
                    ScaleType.CenterFit -> {
                        centerCropPositionRect(
                            targetRatio = imageRatio,
                            topLeftPoint = Point(-1.0f * renderRatio, 1.0f),
                            bottomRightPoint = Point(1.0f * renderRatio, -1.0f)
                        )
                    }

                    ScaleType.CenterCrop -> {
                        Point(-1.0f * renderRatio, 1.0f) to Point(1.0f * renderRatio, -1.0f)
                    }
                }
                val vertex = floatArrayOf(
                    positionTl.x, positionTl.y, 0.0f,    textureTl.x, textureTl.y,      // 左上
                    positionRb.x, positionTl.y, 0.0f,    textureRb.x, textureTl.y,      // 右上
                    positionRb.x, positionRb.y, 0.0f,    textureRb.x, textureRb.y,      // 右下
                    positionTl.x, positionRb.y, 0.0f,    textureTl.x,  textureRb.y,     // 左下
                )
                GLES30.glBindVertexArray(rendererData.VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rendererData.VBO)
                GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertex.size * 4, vertex.toGlBuffer())

                // view
                val viewMatrix = newGlFloatMatrix()
                Matrix.scaleM(viewMatrix, 0, 1 / renderRatio, 1.0f, 1.0f)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(rendererData.program, "view"), 1, false, viewMatrix, 0)

                // model
                val modelMatrix = newGlFloatMatrix()
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(rendererData.program, "model"), 1, false, modelMatrix, 0)

                // transform
                val transformMatrix = newGlFloatMatrix()
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(rendererData.program, "transform"), 1, false, transformMatrix, 0)

                GLES30.glBindVertexArray(rendererData.VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rendererData.VBO)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
                if (imageData.hasInvokedCallback.compareAndSet(false, true)) {
                    imageData.callback?.run()
                }
            }
        }


        fun recycle() {
            sizeCache = null
            val data = glRendererData
            if (data != null) {
                GLES30.glDeleteBuffers(1, intArrayOf(data.VBO), 0)
            }
            glRendererData = null
            rgbaTexConverter.recycle()
            yuv420pTexConverter.recycle()
            yuv420spTexConverter.recycle()
        }

    }

    companion object {
        enum class ScaleType {
            CenterFit,
            CenterCrop
        }

        enum class Yuv420spType { Nv12, Nv21 }

        sealed class ImageRawData {
            class RgbaRawData(
                val rgbaBytes: ByteArray
            ) : ImageRawData()

            class Yuv420pRawData(
                val yBytes: ByteArray,
                val uBytes: ByteArray,
                val vBytes: ByteArray
            ) : ImageRawData()

            class Yuv420spRawData(
                val yBytes: ByteArray,
                val uvBytes: ByteArray,
                val yuv420spType: Yuv420spType
            ) : ImageRawData()
        }

        data class ImageData(
            val imageWidth: Int,
            val imageHeight: Int,
            val imageRawData: ImageRawData,
            val callback: Runnable?,
            val hasInvokedCallback: AtomicBoolean = AtomicBoolean(false)
        )

        private data class Point(
            val x: Float,
            val y: Float
        )

        private fun centerCropTextureRect(targetRatio: Float, topLeftPoint: Point, bottomRightPoint: Point): Pair<Point, Point> {
            val oldRectWidth = bottomRightPoint.x - topLeftPoint.x
            val oldRectHeight = bottomRightPoint.y - topLeftPoint.y
            val oldRectRatio = oldRectWidth / oldRectHeight
            return when  {
                oldRectRatio - targetRatio > 0.00001 -> {
                    // 裁剪 x
                    val d = (oldRectWidth - oldRectHeight * targetRatio) / 2.0f
                    val newTopLeftX = topLeftPoint.x + d
                    val newBottomRightX = bottomRightPoint.x - d
                    Point(x = newTopLeftX, y = topLeftPoint.y) to Point(x = newBottomRightX, y = bottomRightPoint.y)
                }

                targetRatio - oldRectRatio > 0.00001 -> {
                    // 裁剪 y
                    val d = (oldRectHeight - oldRectWidth / targetRatio) / 2.0f
                    val newTopLeftY = topLeftPoint.y + d
                    val newBottomRightY = bottomRightPoint.y - d
                    Point(x = topLeftPoint.x, y = newTopLeftY) to Point(x = bottomRightPoint.x, y = newBottomRightY)
                }

                else -> {
                    topLeftPoint to bottomRightPoint
                }
            }
        }

        private fun centerCropPositionRect(targetRatio: Float, topLeftPoint: Point, bottomRightPoint: Point): Pair<Point, Point> {
            val oldRectWidth = bottomRightPoint.x - topLeftPoint.x
            val oldRectHeight = topLeftPoint.y - bottomRightPoint.y
            val oldRectRatio = oldRectWidth / oldRectHeight
            return when  {
                oldRectRatio - targetRatio > 0.00001 -> {
                    // 裁剪 x
                    val d = (oldRectWidth - oldRectHeight * targetRatio) / 2.0f
                    val newTopLeftX = topLeftPoint.x + d
                    val newBottomRightX = bottomRightPoint.x - d
                    Point(x = newTopLeftX, y = topLeftPoint.y) to Point(x = newBottomRightX, y = bottomRightPoint.y)
                }

                targetRatio - oldRectRatio > 0.00001 -> {
                    // 裁剪 y
                    val d = (oldRectHeight - oldRectWidth / targetRatio) / 2.0f
                    val newTopLeftY = topLeftPoint.y - d
                    val newBottomRightY = bottomRightPoint.y + d
                    Point(x = topLeftPoint.x, y = newTopLeftY) to Point(x = bottomRightPoint.x, y = newBottomRightY)
                }

                else -> {
                    topLeftPoint to bottomRightPoint
                }
            }
        }

        private data class GLRendererData(
            val program: Int,
            val VAO: Int,
            val VBO: Int,
        )

        data class SurfaceSizeCache(
            val gl: GL10,
            val width: Int,
            val height: Int
        )

        private const val TAG = "tMediaPlayerView"
    }
}