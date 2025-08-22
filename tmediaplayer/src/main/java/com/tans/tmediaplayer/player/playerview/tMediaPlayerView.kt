package com.tans.tmediaplayer.player.playerview

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
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
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Suppress("ClassName")
class tMediaPlayerView : GLSurfaceView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)

    private val renderer: FrameRenderer = FrameRenderer()

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

    @Volatile
    private var isDetachedFromWindow: Boolean = false

    private val renderListeners: LinkedBlockingDeque<RenderListenerWrapper> by lazy {
        LinkedBlockingDeque()
    }

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
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
            textureId = null,
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
            textureId = null,
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
            textureId = null,
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
            textureId = null,
            pts = pts,
            callback = callback,
            imageDataType = ImageDataType.Nv21
        )
    }

    fun requestRenderGlTexture(
        width: Int,
        height: Int,
        textureId: Int,
        pts: Long,
        callback: ((isRendered: Boolean) -> Unit)?,
    ) {
        requestRender(
            width = width,
            height = height,
            rgbaBytes = null,
            yBytes = null,
            uBytes = null,
            vBytes = null,
            uvBytes = null,
            textureId = textureId,
            pts = pts,
            callback = callback,
            imageDataType = ImageDataType.GlTexture
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
        textureId: Int?,
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
            requestRenderImageData.textureId = textureId
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

    @Synchronized
    internal fun addRenderListener(l: RenderListener, waitAttached: Boolean = false) {
        if (!isDetachedFromWindow) {
            val wrapper = RenderListenerWrapper(l)
            renderListeners.add(wrapper)
            if (renderer.isSurfaceCreated) {
                queueEvent {
                    wrapper.dispatchAttached(this)
                }
            }
            if (waitAttached) {
                synchronized(wrapper) {
                    while (!wrapper.isAttached) {
                        (wrapper as Object).wait()
                    }
                }
            }
        }
    }

    @Synchronized
    internal fun removeRenderListener(l: RenderListener, waitDetached: Boolean = false) {
        val wrapper = renderListeners.find { it.listener == l }
        if (wrapper != null) {
            if (!isDetachedFromWindow) {
                queueEvent {
                    wrapper.dispatchDetached(this)
                    renderListeners.remove(wrapper)
                }
            } else {
                renderListeners.remove(wrapper)
            }
            if (waitDetached) {
                synchronized(wrapper) {
                    while (wrapper.isAttached) {
                        (wrapper as Object).wait()
                    }
                }
            }
        }
    }

    @Synchronized
    override fun onDetachedFromWindow() {
        if (!isDetachedFromWindow) {
            val recycleFinishLock = Any()
            queueEvent {
                renderer.recycle()
                for (l in renderListeners) {
                    l.dispatchDetached(this)
                }
                isDetachedFromWindow = true
                synchronized(recycleFinishLock) {
                    (recycleFinishLock as Object).notifyAll()
                }
            }
            synchronized(recycleFinishLock) {
                while (!isDetachedFromWindow) {
                    (recycleFinishLock as Object).wait()
                }
            }
            super.onDetachedFromWindow()
        } else {
            super.onDetachedFromWindow()
        }
    }

    private inner class FrameRenderer : Renderer {

        private var sizeCache: SurfaceSizeCache? = null

        private var glRendererData: GLRendererData? = null

        var isSurfaceCreated: Boolean = false

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
            synchronized(this@tMediaPlayerView) {
                isSurfaceCreated = true
                for (l in renderListeners) {
                    l.dispatchAttached(this@tMediaPlayerView)
                }
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
                val textureId: Int?
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
                    textureId = requestRenderImageData.textureId
                    pts = requestRenderImageData.pts
                    imageDataType = requestRenderImageData.imageDataType
                } else {
                    imageWidth = lastRenderedImageData.imageWidth
                    imageHeight = lastRenderedImageData.imageHeight
                    rgbaBytes = lastRenderedImageData.rgbaBytes
                    yBytes = lastRenderedImageData.yBytes
                    uBytes = lastRenderedImageData.uBytes
                    vBytes = lastRenderedImageData.vBytes
                    uvBytes = lastRenderedImageData.uvBytes
                    textureId = lastRenderedImageData.textureId
                    pts = lastRenderedImageData.pts
                    imageDataType = lastRenderedImageData.imageDataType
                }
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val texConverter = when (imageDataType!!) {
                    ImageDataType.Rgba -> rgbaTexConverter
                    ImageDataType.Yuv420p -> yuv420pTexConverter
                    ImageDataType.Nv12, ImageDataType.Nv21 -> yuv420spTexConverter
                    else -> {
                        null
                    }
                }
                val convertTextureId = texConverter?.convertImageToTexture(
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
                ) ?: textureId!!

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

                if (requestRenderImageData.containRenderData()) {
                    requestRenderImageData.callback?.invoke(true)
                    lastRenderedImageData.update(requestRenderImageData)
                    requestRenderImageData.reset()
                }
                isWritingRenderImageData.set(false)

                tMediaPlayerLog.d(TAG) { "Rendered video frame: $pts" }
            } else {
                tMediaPlayerLog.e(TAG) { "Skip render, because is writing render data." }
            }
        }

        var oesGlRenderData: OESGLRenderData? = null

        fun oesTexture2Texture2D(surfaceTexture: SurfaceTexture, oesTexture: Int, texture2D: Int, width: Int, height: Int): Boolean {
            var isSuccess = true
            offScreenRender(texture2D, width, height) {
                val renderData = oesGlRenderData.let {
                    if (it != null) {
                        it
                    } else {
                        val program = compileShaderProgram(context, R.raw.t_media_player_oes_vert, R.raw.t_media_player_oes_frag)
                        if (program == null) {
                            isSuccess = false
                            return@offScreenRender
                        }
                        val VAO = glGenVertexArrays()
                        val VBO = glGenBuffers()
                        // TODO：传统的 GL texture 的原点是左下角，MediaCodec 的输出流的原点是左上角，没有有搞太懂.
                        val vertices = floatArrayOf(
                            // 顶点(position 0)   // 纹理坐标(position 1)
                            -1.0f, 1.0f,        0.0f, 0.0f,    // 左上角
                            1.0f, 1.0f,         1.0f, 0.0f,   // 右上角
                            1.0f, -1.0f,        1.0f, 1.0f,   // 右下角
                            -1.0f, -1.0f,       0.0f, 1.0f,   // 左下角
                        )
                        GLES30.glBindVertexArray(VAO)
                        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, VBO)
                        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
                        GLES30.glEnableVertexAttribArray(0)
                        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
                        GLES30.glEnableVertexAttribArray(1)
                        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(),
                            GLES30.GL_STATIC_DRAW)
                        val r = OESGLRenderData(
                            program = program,
                            VAO = VAO,
                            VBO = VBO,
                            transformLocation = GLES30.glGetUniformLocation(program, "transform"),
                            oesTextureLocation = GLES30.glGetUniformLocation(program, "oesTex")
                        )
                        oesGlRenderData = r
                        r
                    }
                }
                GLES30.glUseProgram(renderData.program)
                surfaceTexture.getTransformMatrix(renderData.transformMat)
                /**
                 * [1.0, 0.0, 0.0, 0.0,
                 *  0.0, -1.0, 0.0, 0.0,
                 *  0.0, 0.0, 1.0, 0.0,
                 *  0.0, 1.0, 0.0, 1.0]
                 */
                GLES30.glUniformMatrix4fv(renderData.transformLocation, 1, false, renderData.transformMat, 0)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
//                GLES30.glUniform1i(renderData.oesTextureLocation, oesTexture)
                GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)

                GLES30.glBindVertexArray(renderData.VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderData.VBO)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
            }
            return isSuccess
        }

        fun recycle() {
            isSurfaceCreated = false
            sizeCache = null
            val data = glRendererData
            if (data != null) {
                GLES30.glDeleteBuffers(1, intArrayOf(data.VBO), 0)
            }
            glRendererData = null
            val oesData = oesGlRenderData
            if (oesData != null) {
                GLES30.glDeleteBuffers(1, intArrayOf(oesData.VBO), 0)
            }
            oesGlRenderData = null
            rgbaTexConverter.recycle()
            yuv420pTexConverter.recycle()
            yuv420spTexConverter.recycle()
            tryRecycleUnhandledRequestImageData()
        }
    }

    internal fun oesTexture2Texture2D(surfaceTexture: SurfaceTexture, oesTexture: Int, texture2D: Int, width: Int, height: Int): Boolean {
        return renderer.oesTexture2Texture2D(surfaceTexture = surfaceTexture, oesTexture = oesTexture, texture2D = texture2D, width = width, height = height)
    }

    internal fun genHwOesTextureAndBufferTextures(bufferSize: Int): Pair<Int, IntArray> {
        return glGenOesTextureAndSetDefaultParams() to IntArray(bufferSize) {
            glGenTextureAndSetDefaultParams()
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
            Nv12,
            GlTexture
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
            var textureId: Int? = null
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
                textureId = null
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
            var textureId: Int? = null
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
                textureId = imageData.textureId
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

        private data class OESGLRenderData(
            val program: Int,
            val VAO: Int,
            val VBO: Int,
            val transformMat: FloatArray = FloatArray(16),
            val transformLocation: Int,
            val oesTextureLocation: Int
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as OESGLRenderData

                if (program != other.program) return false
                if (VAO != other.VAO) return false
                if (VBO != other.VBO) return false
                if (!transformMat.contentEquals(other.transformMat)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = program
                result = 31 * result + VAO
                result = 31 * result + VBO
                result = 31 * result + transformMat.contentHashCode()
                return result
            }
        }

        data class SurfaceSizeCache(
            val gl: GL10,
            val width: Int,
            val height: Int
        )

        internal interface RenderListener {

            fun onSurfaceAttached(playerView: tMediaPlayerView) {

            }

            fun onSurfaceDetached(playerView: tMediaPlayerView) {

            }
        }

        private class RenderListenerWrapper(
            val listener: RenderListener
        ) {

            @Volatile
            var isAttached: Boolean  = false

            // invoke on gl thread.
            fun dispatchAttached(playerView: tMediaPlayerView) {
                if (!isAttached) {
                    listener.onSurfaceAttached(playerView)
                    isAttached = true
                }
                synchronized(this) {
                    (this as Object).notifyAll()
                }
            }

            // invoke on gl thread.
            fun dispatchDetached(playerView: tMediaPlayerView) {
                if (isAttached) {
                    listener.onSurfaceDetached(playerView)
                    isAttached = false
                }
                synchronized(this) {
                    (this as Object).notifyAll()
                }
            }
        }

        private const val TAG = "tMediaPlayerView"
    }
}