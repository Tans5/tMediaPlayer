package com.tans.tmediaplayer.player.playerview

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import com.tans.tmediaplayer.tMediaPlayerLog
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

    private val requestRenderImageData = RequestRenderImageData()

    private val lastRenderedImageData = LastRenderedImageData()

    private val isWritingRenderImageData = AtomicBoolean(false)

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

    fun requestRenderRgbaFrame(
        width: Int,
        height: Int,
        imageBytes: ByteArray,
        pts: Long,
        callback: ((isRendered: Boolean) -> Unit)?,
    ) {
        requestRender(
            width = width,
            height = height,
            rgbaBytes = imageBytes,
            yBytes = null,
            uBytes = null,
            vBytes = null,
            uvBytes = null,
            pts = pts,
            callback = callback,
            imageDataType = ImageDataType.Rgba
        )
    }

    fun requestRenderYuv420pFrame(
        width: Int,
        height: Int,
        yBytes: ByteArray,
        uBytes: ByteArray,
        vBytes: ByteArray,
        pts: Long,
        callback: ((isRendered: Boolean) -> Unit)?,
    ) {
        requestRender(
            width = width,
            height = height,
            rgbaBytes = null,
            yBytes = yBytes,
            uBytes = uBytes,
            vBytes = vBytes,
            uvBytes = null,
            pts = pts,
            callback = callback,
            imageDataType = ImageDataType.Yuv420p
        )
    }

    fun requestRenderNv12Frame(
        width: Int,
        height: Int,
        yBytes: ByteArray,
        uvBytes: ByteArray,
        pts: Long,
        callback: ((isRendered: Boolean) -> Unit)?,
    ) {
        requestRender(
            width = width,
            height = height,
            rgbaBytes = null,
            yBytes = yBytes,
            uBytes = null,
            vBytes = null,
            uvBytes = uvBytes,
            pts = pts,
            callback = callback,
            imageDataType = ImageDataType.Nv12
        )
    }

    fun requestRenderNv21Frame(
        width: Int,
        height: Int,
        yBytes: ByteArray,
        vuBytes: ByteArray,
        pts: Long,
        callback: ((isRendered: Boolean) -> Unit)?,
    ) {
        requestRender(
            width = width,
            height = height,
            rgbaBytes = null,
            yBytes = yBytes,
            uBytes = null,
            vBytes = null,
            uvBytes = vuBytes,
            pts = pts,
            callback = callback,
            imageDataType = ImageDataType.Nv21
        )
    }

    private fun requestRender(
        width: Int,
        height: Int,
        rgbaBytes: ByteArray?,
        yBytes: ByteArray?,
        uBytes: ByteArray?,
        vBytes: ByteArray?,
        uvBytes: ByteArray?,
        pts: Long,
        callback: ((isRendered: Boolean) -> Unit)?,
        imageDataType: ImageDataType
    ) {
        if (this.isAttachedToWindow && isWritingRenderImageData.compareAndSet(false, true)) {
            if (requestRenderImageData.imageDataType != null) {
                requestRenderImageData.callback?.invoke(false)
                tMediaPlayerLog.e(TAG) { "Drop video frame: ${requestRenderImageData.pts}, because out of date." }
            }
            requestRenderImageData.imageWidth = width
            requestRenderImageData.imageHeight = height
            requestRenderImageData.rgbaBytes = rgbaBytes
            requestRenderImageData.yBytes = yBytes
            requestRenderImageData.uBytes = uBytes
            requestRenderImageData.vBytes = vBytes
            requestRenderImageData.uvBytes = uvBytes
            requestRenderImageData.pts = pts
            requestRenderImageData.callback = callback
            requestRenderImageData.imageDataType = imageDataType
            isWritingRenderImageData.set(false)
            requestRender()
        } else {
            callback?.invoke(false)
            tMediaPlayerLog.e(TAG) { "Drop video frame: $pts, because under rendering." }
        }
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
            tMediaPlayerLog.d(TAG) { "Support gl version: $glVersion" }
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
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 5 * 4 * 4, null, GLES30.GL_STREAM_DRAW)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 5 * 4, 3 * 4)
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

        private val filterInput =  FilterImageTexture()
        private val filterOutput = FilterImageTexture()
        private val pointBuffer1 = Point()
        private val pointBuffer2 = Point()
        private val pointBuffer3 = Point()
        private val pointBuffer4 = Point()
        private val vertexArray = FloatArray(20)
        private val vertexBuffer = vertexArray.toGlBuffer().asFloatBuffer()
        private val matrixBuffer = newGlFloatMatrix()
        override fun onDrawFrame(gl: GL10) {
            val rendererData = this.glRendererData
            val screenSize = sizeCache
            if (rendererData != null && screenSize != null && isWritingRenderImageData.compareAndSet(false, true)) {
                if (!requestRenderImageData.containRenderData() && !lastRenderedImageData.containRenderData()) {
                    isWritingRenderImageData.set(false)
                    return
                }
                val imageWidth: Int
                val imageHeight: Int
                val rgbaBytes: ByteArray?
                val yBytes: ByteArray?
                val uBytes: ByteArray?
                val vBytes: ByteArray?
                val uvBytes: ByteArray?
                val pts: Long?
                val imageDataType: ImageDataType?

                if (requestRenderImageData.containRenderData()) {
                    imageWidth = requestRenderImageData.imageWidth
                    imageHeight = requestRenderImageData.imageHeight
                    rgbaBytes = requestRenderImageData.rgbaBytes
                    yBytes = requestRenderImageData.yBytes
                    uBytes = requestRenderImageData.uBytes
                    vBytes = requestRenderImageData.vBytes
                    uvBytes = requestRenderImageData.uvBytes
                    pts = requestRenderImageData.pts
                    imageDataType = requestRenderImageData.imageDataType
                    requestRenderImageData.callback?.invoke(true)
                    lastRenderedImageData.update(requestRenderImageData)
                    requestRenderImageData.reset()
                } else {
                    imageWidth = lastRenderedImageData.imageWidth
                    imageHeight = lastRenderedImageData.imageHeight
                    rgbaBytes = lastRenderedImageData.rgbaBytes
                    yBytes = lastRenderedImageData.yBytes
                    uBytes = lastRenderedImageData.uBytes
                    vBytes = lastRenderedImageData.vBytes
                    uvBytes = lastRenderedImageData.uvBytes
                    pts = lastRenderedImageData.pts
                    imageDataType = lastRenderedImageData.imageDataType
                }
                isWritingRenderImageData.set(false)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val texConverter = when (imageDataType!!) {
                    ImageDataType.Rgba -> rgbaTexConverter
                    ImageDataType.Yuv420p -> yuv420pTexConverter
                    ImageDataType.Nv12, ImageDataType.Nv21 -> yuv420spTexConverter
                }
                val convertTextureId = texConverter.convertImageToTexture(
                    context = context,
                    surfaceSize = screenSize,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    rgbaBytes = rgbaBytes,
                    yBytes = yBytes,
                    uBytes = uBytes,
                    vBytes = vBytes,
                    uvBytes = uvBytes,
                    imageDataType = imageDataType
                )

                filterInput.width = imageWidth
                filterInput.height = imageHeight
                filterInput.texture = convertTextureId

                asciiArtFilter.filter(
                    context = context,
                    surfaceSize = screenSize,
                    input = filterInput,
                    output = filterOutput
                )

                GLES30.glUseProgram(rendererData.program)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, filterOutput.texture)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(rendererData.program, "Texture"), 0)
                val imageRatio = filterOutput.width.toFloat() / filterOutput.height.toFloat()
                val renderRatio = screenSize.width.toFloat() / screenSize.height.toFloat()
                val scaleType = this@tMediaPlayerView.getScaleType()

                var textureTlX = 0.0f
                var textureTlY = 0.0f

                var textureRbX = 1.0f
                var textureRbY = 1.0f

                when (scaleType) {
                    ScaleType.CenterFit -> {
                        // Do nothing.
                    }
                    ScaleType.CenterCrop -> {
                        val inputTopLeftPoint = pointBuffer1
                        inputTopLeftPoint.x = 0.0f
                        inputTopLeftPoint.y = 0.0f
                        val inputBottomRightPoint = pointBuffer2
                        inputBottomRightPoint.x = 1.0f
                        inputBottomRightPoint.y = 1.0f

                        val outputTopLeftPoint = pointBuffer3
                        val outputBottomRightPoint = pointBuffer4

                        centerCropTextureRect(
                            targetRatio = renderRatio / imageRatio,
                            inputTopLeftPoint = inputTopLeftPoint,
                            inputBottomRightPoint = inputBottomRightPoint,
                            outputTopLeftPoint = outputTopLeftPoint,
                            outputBottomRightPoint = outputBottomRightPoint
                        )
                        textureTlX = outputTopLeftPoint.x
                        textureTlY = outputTopLeftPoint.y
                        textureRbX = outputBottomRightPoint.x
                        textureRbY = outputBottomRightPoint.y
                    }
                }

                var positionTlX = 0.0f
                var positionTlY = 0.0f
                var positionRbX = 0.0f
                var positionRbY = 0.0f

               when (scaleType) {
                    ScaleType.CenterFit -> {
                        val inputTopLeftPoint = pointBuffer1
                        inputTopLeftPoint.x = -1.0f * renderRatio
                        inputTopLeftPoint.y = 1.0f
                        val inputBottomRightPoint = pointBuffer2
                        inputBottomRightPoint.x = 1.0f * renderRatio
                        inputBottomRightPoint.y = -1.0f

                        val outputTopLeftPoint = pointBuffer3
                        val outputBottomRightPoint = pointBuffer4

                        centerCropPositionRect(
                            targetRatio = imageRatio,
                            inputTopLeftPoint = inputTopLeftPoint,
                            inputBottomRightPoint = inputBottomRightPoint,
                            outputTopLeftPoint = outputTopLeftPoint,
                            outputBottomRightPoint = outputBottomRightPoint
                        )
                        positionTlX = outputTopLeftPoint.x
                        positionTlY = outputTopLeftPoint.y
                        positionRbX = outputBottomRightPoint.x
                        positionRbY = outputBottomRightPoint.y
                    }

                    ScaleType.CenterCrop -> {
                        positionTlX = -1.0f * renderRatio
                        positionTlY = 1.0f
                        positionRbX = 1.0f * renderRatio
                        positionRbY = -1.0f
                    }
                }

                val vertex = vertexArray
                vertex[0] = positionTlX; vertex[1] = positionTlY; vertex[2] = 0.0f;         vertex[3] = textureTlX; vertex[4] = textureTlY // 左上
                vertex[5] = positionRbX; vertex[6] = positionTlY; vertex[7] = 0.0f;         vertex[8] = textureRbX; vertex[9] = textureTlY // 右上
                vertex[10] = positionRbX; vertex[11] = positionRbY; vertex[12] = 0.0f;      vertex[13] = textureRbX; vertex[14] = textureRbY // 右下
                vertex[15] = positionTlX; vertex[16] = positionRbY; vertex[17] = 0.0f;      vertex[18] = textureTlX; vertex[19] = textureRbY // 左下
                val vertexBuffer = vertexBuffer
                vertexBuffer.position(0)
                vertexBuffer.put(vertex)
                vertexBuffer.position(0)
                GLES30.glBindVertexArray(rendererData.VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rendererData.VBO)
                GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertex.size * 4, vertexBuffer)

                fun resetMatrix(m: FloatArray) {
                    for (i in m.indices) {
                        val x = i / 4
                        val y = i % 4
                        if (x == y) {
                            m[i] = 1.0f
                        } else {
                            m[i] = 0.0f
                        }
                    }
                }
                // view
                val viewMatrix = matrixBuffer
                resetMatrix(viewMatrix)
                Matrix.scaleM(viewMatrix, 0, 1 / renderRatio, 1.0f, 1.0f)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(rendererData.program, "view"), 1, false, viewMatrix, 0)

                // model
                val modelMatrix = matrixBuffer
                resetMatrix(modelMatrix)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(rendererData.program, "model"), 1, false, modelMatrix, 0)

                // transform
                val transformMatrix = matrixBuffer
                resetMatrix(transformMatrix)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(rendererData.program, "transform"), 1, false, transformMatrix, 0)

                GLES30.glBindVertexArray(rendererData.VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rendererData.VBO)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)


                tMediaPlayerLog.d(TAG) { "Rendered video frame: $pts" }
            } else {
                tMediaPlayerLog.e(TAG) { "Skip render, because is writing render data." }
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
            tryRecycleUnhandledRequestImageData()
        }

    }

    internal fun tryRecycleUnhandledRequestImageData() {
        if (isWritingRenderImageData.compareAndSet(false, true)) {
            requestRenderImageData.callback?.invoke(false)
            requestRenderImageData.reset()
            isWritingRenderImageData.set(false)
        }
    }

    companion object {
        enum class ScaleType {
            CenterFit,
            CenterCrop
        }

        enum class ImageDataType {
            Rgba,
            Yuv420p,
            Nv21,
            Nv12
        }

        class RequestRenderImageData {
            var imageWidth: Int = 0
            var imageHeight: Int = 0
            var callback: ((isRendered: Boolean) -> Unit)? = null
            var rgbaBytes: ByteArray? = null
            var yBytes: ByteArray? = null
            var uBytes: ByteArray? = null
            var vBytes: ByteArray? = null
            var uvBytes: ByteArray? = null
            var pts: Long? = null
            var imageDataType: ImageDataType? = null

            fun reset() {
                imageWidth = 0
                imageHeight = 0
                callback = null
                rgbaBytes = null
                yBytes = null
                uBytes = null
                vBytes = null
                uvBytes = null
                pts = null
                imageDataType = null
            }

            fun containRenderData(): Boolean {
                return imageDataType != null
            }
        }

        class LastRenderedImageData {

            var imageWidth: Int = 0
            var imageHeight: Int = 0
            var rgbaBytes: ByteArray? = null
            var yBytes: ByteArray? = null
            var uBytes: ByteArray? = null
            var vBytes: ByteArray? = null
            var uvBytes: ByteArray? = null
            var pts: Long? = null
            var imageDataType: ImageDataType? = null

            fun containRenderData(): Boolean {
                return imageDataType != null
            }

            fun update(imageData: RequestRenderImageData) {
                imageWidth = imageData.imageWidth
                imageHeight = imageData.imageHeight
                rgbaBytes = imageData.rgbaBytes
                yBytes = imageData.yBytes
                uBytes = imageData.uBytes
                vBytes = imageData.vBytes
                uvBytes = imageData.uvBytes
                pts = imageData.pts
                imageDataType = imageData.imageDataType
            }
        }

        private class Point {
            var x: Float = 0.0f
            var y: Float = 0.0f
        }

        private fun centerCropTextureRect(
            targetRatio: Float,
            inputTopLeftPoint: Point,
            inputBottomRightPoint: Point,
            outputTopLeftPoint: Point,
            outputBottomRightPoint: Point
        ) {
            val oldRectWidth = inputBottomRightPoint.x - inputTopLeftPoint.x
            val oldRectHeight = inputBottomRightPoint.y - inputTopLeftPoint.y
            val oldRectRatio = oldRectWidth / oldRectHeight
            when  {
                oldRectRatio - targetRatio > 0.00001 -> {
                    // 裁剪 x
                    val d = (oldRectWidth - oldRectHeight * targetRatio) / 2.0f
                    val newTopLeftX = inputTopLeftPoint.x + d
                    val newBottomRightX = inputBottomRightPoint.x - d

                    outputTopLeftPoint.x = newTopLeftX
                    outputTopLeftPoint.y = inputTopLeftPoint.y

                    outputBottomRightPoint.x = newBottomRightX
                    outputBottomRightPoint.y = inputBottomRightPoint.y
                }

                targetRatio - oldRectRatio > 0.00001 -> {
                    // 裁剪 y
                    val d = (oldRectHeight - oldRectWidth / targetRatio) / 2.0f
                    val newTopLeftY = inputTopLeftPoint.y + d
                    val newBottomRightY = inputBottomRightPoint.y - d
                    outputTopLeftPoint.x = inputTopLeftPoint.x
                    outputTopLeftPoint.y = newTopLeftY
                    outputBottomRightPoint.x = inputBottomRightPoint.x
                    outputBottomRightPoint.y = newBottomRightY
                }

                else -> {
                    outputTopLeftPoint.x = inputTopLeftPoint.x
                    outputTopLeftPoint.y = inputTopLeftPoint.y
                    outputBottomRightPoint.x = inputBottomRightPoint.x
                    outputBottomRightPoint.y = inputBottomRightPoint.y
                }
            }
        }

        private fun centerCropPositionRect(
            targetRatio: Float,
            inputTopLeftPoint: Point,
            inputBottomRightPoint: Point,
            outputTopLeftPoint: Point,
            outputBottomRightPoint: Point
        ) {
            val oldRectWidth = inputBottomRightPoint.x - inputTopLeftPoint.x
            val oldRectHeight = inputTopLeftPoint.y - inputBottomRightPoint.y
            val oldRectRatio = oldRectWidth / oldRectHeight
            when  {
                oldRectRatio - targetRatio > 0.00001 -> {
                    // 裁剪 x
                    val d = (oldRectWidth - oldRectHeight * targetRatio) / 2.0f
                    val newTopLeftX = inputTopLeftPoint.x + d
                    val newBottomRightX = inputBottomRightPoint.x - d
                    outputTopLeftPoint.x = newTopLeftX
                    outputTopLeftPoint.y = inputTopLeftPoint.y
                    outputBottomRightPoint.x = newBottomRightX
                    outputBottomRightPoint.y = inputBottomRightPoint.y
                }

                targetRatio - oldRectRatio > 0.00001 -> {
                    // 裁剪 y
                    val d = (oldRectHeight - oldRectWidth / targetRatio) / 2.0f
                    val newTopLeftY = inputTopLeftPoint.y - d
                    val newBottomRightY = inputBottomRightPoint.y + d
                    outputTopLeftPoint.x = inputTopLeftPoint.x
                    outputTopLeftPoint.y = newTopLeftY
                    outputBottomRightPoint.x = inputBottomRightPoint.x
                    outputBottomRightPoint.y = newBottomRightY
                }

                else -> {
                    outputTopLeftPoint.x = inputTopLeftPoint.x
                    outputTopLeftPoint.y = inputTopLeftPoint.y
                    outputBottomRightPoint.x = inputBottomRightPoint.x
                    outputBottomRightPoint.y = inputBottomRightPoint.y
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