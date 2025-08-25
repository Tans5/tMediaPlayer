package com.tans.tmediaplayer.player.playerview

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.tans.tmediaplayer.R
import com.tans.tmediaplayer.player.playerview.filter.AsciiArtImageFilter
import com.tans.tmediaplayer.player.playerview.filter.FilterImageTexture
import com.tans.tmediaplayer.player.playerview.texconverter.RgbaImageTextureConverter
import com.tans.tmediaplayer.player.playerview.texconverter.Yuv420pImageTextureConverter
import com.tans.tmediaplayer.player.playerview.texconverter.Yuv420spImageTextureConverter
import com.tans.tmediaplayer.tMediaPlayerLog
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class GLRenderer {

    private var renderSurfaceAdapter: RenderSurfaceAdapter? = null

    private val surfaceListener: RenderSurfaceAdapter.Listener by lazy {
        object : RenderSurfaceAdapter.Listener {
            override fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
                tMediaPlayerLog.d(TAG) { "Surface created: ${surface.isValid}, ${width}x$height" }
                glThread.requestAttachSurface(surface)
                glThread.requestSizeChange(width, height)
            }

            override fun onSurfaceSizeChanged(width: Int, height: Int) {
                tMediaPlayerLog.d(TAG) { "Surface size changed: ${width}x$height" }
                glThread.requestSizeChange(width, height)
            }

            override fun onSurfaceDestroyed() {
                tMediaPlayerLog.d(TAG) { "Surface destroyed." }
                glThread.requestDetachSurface()
            }
        }
    }

    private val realRenderer: RealRenderer by lazy {
        RealRenderer()
    }

    private val glThread: GLThread by lazy {
        GLThread()
    }

    private var isReleased: Boolean = false

    private val glContextListeners: LinkedBlockingDeque<GLContextListenerWrapper> by lazy {
        LinkedBlockingDeque()
    }

    // region Opt
    fun setScaleType(scaleType: ScaleType) {
        this.realRenderer.scaleType.set(scaleType)
    }

    fun enableAsciiArtFilter(enable: Boolean) {
        realRenderer.asciiArtFilter.enable(enable)
    }

    fun getAsciiArtImageFilter(): AsciiArtImageFilter {
        return realRenderer.asciiArtFilter
    }

    fun getScaleType(): ScaleType = this.realRenderer.scaleType.get()
    // endregion

    // region RequestRenderImage

    fun refreshFrame() {
        glThread.requestRender()
    }

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
        realRenderer.apply {
            if (!isReleased && glThread.isSurfaceAlive() && isWritingRenderImageData.compareAndSet(false, true)) {
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
                glThread.requestRender()
            } else {
                callback?.invoke(false)
                tMediaPlayerLog.e(TAG) { "Drop video frame: $pts, under rendering or gl surface not ready." }
            }
        }
    }
    // endregion

    // region OptRenderView
    @Synchronized
    fun attachRendererSurface(surfaceView: SurfaceView) {
        if (!isReleased) {
            val new = SurfaceViewAdapter(surfaceView)
            renderSurfaceAdapter?.release()
            new.addListener(surfaceListener)
            renderSurfaceAdapter = new
            if (!glThread.isStarted()) {
                glThread.start()
            }
        }
    }

    @Synchronized
    fun attachRendererSurface(textureView: TextureView) {
        if (!isReleased) {
            val new = TextureViewAdapter(textureView)
            renderSurfaceAdapter?.release()
            new.addListener(surfaceListener)
            renderSurfaceAdapter = new
            if (!glThread.isStarted()) {
                glThread.start()
            }
        }
    }

    @Synchronized
    fun detachRenderSurface() {
        if (!isReleased) {
            renderSurfaceAdapter?.release()
            renderSurfaceAdapter = null
        }
    }
    // endregion

    fun enqueueTask(task: (containGLContext: Boolean) -> Unit) {
        glThread.queueTask(task)
    }

    @Synchronized
    fun release() {
        if (!isReleased) {
            isReleased = true
            if (glThread.isStarted()) {
                glThread.requestQuitAndWait()
            }
            renderSurfaceAdapter?.release()
            renderSurfaceAdapter = null
            glContextListeners.clear()
        }
    }

    fun oesTexture2Texture2D(surfaceTexture: SurfaceTexture, oesTexture: Int, texture2D: Int, width: Int, height: Int): Boolean {
        return realRenderer.oesTexture2Texture2D(surfaceTexture = surfaceTexture, oesTexture = oesTexture, texture2D = texture2D, width = width, height = height)
    }

    fun genHwOesTextureAndBufferTextures(bufferSize: Int): Pair<Int, IntArray> {
        return glGenOesTextureAndSetDefaultParams() to IntArray(bufferSize) {
            glGenTextureAndSetDefaultParams()
        }
    }

    fun addGLContextListener(l: GLContextListener) {
        if (glContextListeners.find { it.l === l } == null) {
            val new = GLContextListenerWrapper(l)
            glContextListeners.add(new)
            if (glThread.isSurfaceAlive()) {
                enqueueTask { new.dispatchGLContextCreated() }
            }
        }
    }

    fun removeGLContextListener(l: GLContextListener) {
        val toRemove = glContextListeners.find { it.l === l }
        if (toRemove != null) {
            if (glThread.isSurfaceAlive()) {
                enqueueTask {
                    toRemove.dispatchGLContextDestroying()
                    glContextListeners.remove(toRemove)
                }
            }
        }
    }

    private inner class RealRenderer {

        val asciiArtFilter: AsciiArtImageFilter by lazy {
            AsciiArtImageFilter()
        }

        val scaleType: AtomicReference<ScaleType> by lazy {
            AtomicReference(ScaleType.CenterFit)
        }

        // region ImageConverters
        private val rgbaTexConverter: RgbaImageTextureConverter by lazy {
            RgbaImageTextureConverter()
        }

        private val yuv420pTexConverter: Yuv420pImageTextureConverter by lazy {
            Yuv420pImageTextureConverter()
        }

        private val yuv420spTexConverter: Yuv420spImageTextureConverter by lazy {
            Yuv420spImageTextureConverter()
        }
        // endregion

        // region RenderImageData
        val requestRenderImageData = RequestRenderImageData()

        val lastRenderedImageData = LastRenderedImageData()

        val isWritingRenderImageData = AtomicBoolean(false)
        // endregion

        private var sizeCache: Pair<Int, Int>? = null

        private var glRendererData: GLRendererData? = null

        fun glContextCreated() {
            val context = RenderSurfaceAdapter.getAndroidApplicationContext()!!
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
            for (l in glContextListeners) {
                l.dispatchGLContextCreated()
            }
        }

        fun glSurfaceCreated() {
            if (lastRenderedImageData.containRenderData() || requestRenderImageData.containRenderData()) {
                glThread.requestRender()
            }
        }

        fun surfaceSizeChanged(width: Int, height: Int) {
            sizeCache = width to height
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
        fun drawFrame() {
            val rendererData = this.glRendererData
            val screenSize = sizeCache
            val context = RenderSurfaceAdapter.getAndroidApplicationContext()!!
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
                    tMediaPlayerLog.d(TAG) { "Draw last frame" }
                }
                tMediaPlayerLog.d(TAG) { "Start render pts=$pts, textureId=$textureId" }
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
                    surfaceWidth = screenSize.first,
                    surfaceHeight = screenSize.second,
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
                    surfaceWidth = screenSize.first,
                    surfaceHeight = screenSize.second,
                    input = filterInput,
                    output = filterOutput
                )

                GLES30.glUseProgram(rendererData.program)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, filterOutput.texture)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(rendererData.program, "Texture"), 0)
                val imageRatio = filterOutput.width.toFloat() / filterOutput.height.toFloat()
                val renderRatio = screenSize.first.toFloat() / screenSize.second.toFloat()
                val scaleType = this.scaleType.get()

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

                tMediaPlayerLog.d(TAG) { "Rendered video frame: pts=$pts, textureId=${textureId}" }
            } else {
                tMediaPlayerLog.e(TAG) { "Skip render, because is writing render data." }
            }
        }

        var oesGlRenderData: OESGLRenderData? = null
        fun oesTexture2Texture2D(surfaceTexture: SurfaceTexture, oesTexture: Int, texture2D: Int, width: Int, height: Int): Boolean {
            var isSuccess = true
            val context = RenderSurfaceAdapter.getAndroidApplicationContext()!!
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

        fun glSurfaceDestroyed() {

        }

        fun glContextDestroying() {
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
            asciiArtFilter.recycle()
            tryRecycleUnhandledRequestImageData()
            for (l in glContextListeners) {
                l.dispatchGLContextDestroying()
            }
        }

        private fun tryRecycleUnhandledRequestImageData() {
            if (isWritingRenderImageData.compareAndSet(false, true)) {
                requestRenderImageData.callback?.invoke(false)
                requestRenderImageData.reset()
                isWritingRenderImageData.set(false)
            }
        }
    }

    private inner class GLThread : Thread("tMediaGLThread") {

        private val isStarted: AtomicBoolean = AtomicBoolean(false)

        @Volatile
        private var requestQuit: Boolean = false

        @Volatile
        private var isQuited: Boolean = false

        @Volatile
        private var surface: Surface? = null

        @Volatile
        private var surfaceSizeChange: Pair<Int, Int>? = null

        @Volatile
        private var requestRender: Boolean = false

        private val tasks: LinkedBlockingDeque<(Boolean) -> Unit> = LinkedBlockingDeque()

        fun isStarted() = isStarted.get()

        override fun start() {
            isStarted.set(true)
            super.start()
        }

        @Synchronized
        fun requestQuitAndWait() {
            if (!isQuited && isStarted.get()) {
                if (!requestQuit) {
                    requestQuit = true
                    (this as Object).notifyAll()
                }
                while (!isQuited) {
                    (this as Object).wait()
                }
            }
        }

        @Synchronized
        fun requestAttachSurface(s: Surface) {
            tMediaPlayerLog.d(TAG) { "Request set surface: $s" }
            surface = s
            (this as Object).notifyAll()
        }

        @Synchronized
        fun requestDetachSurface() {
            if (surface != null) {
                tMediaPlayerLog.d(TAG) { "Request remove surface: $surface" }
                surface = null
                (this as Object).notifyAll()
            }
        }

        @Synchronized
        fun requestSizeChange(width: Int, height: Int) {
            surfaceSizeChange = width to height
            (this as Object).notifyAll()
        }


        @Synchronized
        fun requestRender() {
            if (isSurfaceAlive()) {
                requestRender = true
                (this as Object).notifyAll()
            }
        }

        @Synchronized
        fun queueTask(task: (containGLContext: Boolean) -> Unit) {
            if (isSurfaceAlive()) {
                tasks.add(task)
                if (tasks.size > 100) {
                    tMediaPlayerLog.e(TAG) { "Waiting task size: ${tasks.size}" }
                    tasks.pollFirst()?.invoke(false)
                }
                (this as Object).notifyAll()
            } else {
                task(false)
            }
        }

        fun isThreadAlive(): Boolean = !isQuited && !requestQuit && isStarted.get()

        fun isSurfaceAlive(): Boolean = isThreadAlive() && surface != null

        override fun run() {
            tMediaPlayerLog.d(TAG) { "GLThread start run!!!" }
            // 1. Display
            var display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                error("Init egl display fail.")
            }
            tMediaPlayerLog.d(TAG) { "EGL display inited, major version: ${version[0]}, minor version: ${version[1]}" }


            // 2. Choose configure
            val eglConfigureSpec = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 0,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_STENCIL_SIZE, 0,
                // EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_NONE
            )
            val configNum = intArrayOf(0)
            if (!EGL14.eglChooseConfig(display,
                eglConfigureSpec, 0,
                null, 0, 0,
                configNum, 0
            )) {
                error("eglChooseConfig fail.")
            }
            tMediaPlayerLog.d(TAG) { "All configure size: ${configNum[0]}" }
            val allConfigs = Array<EGLConfig?>(configNum[0]) { null }
            if (!EGL14.eglChooseConfig(display,
                eglConfigureSpec, 0,
                allConfigs, 0, allConfigs.size,
                configNum, 0
            )) {
                error("eglChooseConfig fail.")
            }
            var chooseConfig: EGLConfig? = null
            val v = intArrayOf(0)
            fun eglGetConfigAttr(config: EGLConfig, key: Int): Int {
                if (EGL14.eglGetConfigAttrib(display, config, key, v, 0)) {
                    return v[0]
                } else {
                    return 0
                }
            }
            for (c in allConfigs) {
                if (c != null) {
                    val d = eglGetConfigAttr(c, EGL14.EGL_DEPTH_SIZE)
                    if (d >= 16) {
                        val r = eglGetConfigAttr(c, EGL14.EGL_RED_SIZE)
                        val g = eglGetConfigAttr(c, EGL14.EGL_GREEN_SIZE)
                        val b = eglGetConfigAttr(c, EGL14.EGL_BLUE_SIZE)
                        val a = eglGetConfigAttr(c, EGL14.EGL_ALPHA_SIZE)
                        if (r == 8 && g == 8 && b == 8 && a == 0) {
                            chooseConfig = c
                            break
                        }
                    }
                }
            }

            tMediaPlayerLog.d(TAG) { "Choose egl configure: $chooseConfig" }

            // 3. Create egl context.
            val eglContextAttrs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            var eglContext = EGL14.eglCreateContext(display, chooseConfig, EGL14.EGL_NO_CONTEXT, eglContextAttrs, 0)
            tMediaPlayerLog.d(TAG) { "Create egl context: $eglContext" }

            // 4. Wait create surface.
            var eglSurface = EGL14.EGL_NO_SURFACE

            var isNotifyContextCreated = false

            while (true) {
                synchronized(this) {
                    // Destroy
                    if (requestQuit) {
                        if (isNotifyContextCreated) {
                            isNotifyContextCreated = false
                            realRenderer.glContextDestroying()
                        }

                        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        // Destroy surface
                        if (eglSurface != EGL14.EGL_NO_SURFACE) {
                            EGL14.eglDestroySurface(display, eglSurface)
                            eglSurface = EGL14.EGL_NO_SURFACE
                        }
                        // Destroy context
                        EGL14.eglDestroyContext(display, eglContext)
                        eglContext = EGL14.EGL_NO_CONTEXT

                        // Destroy display
                        EGL14.eglTerminate(display)
                        display = EGL14.EGL_NO_DISPLAY
                        surface = null
                        requestQuit = false
                        isQuited = true
                        (this as Object).notifyAll()
                        tMediaPlayerLog.d(TAG) { "GL thread quited." }
                        break
                    }

                    // Check surface
                    val sur = surface
                    if (sur != null) {
                        if (eglSurface == EGL14.EGL_NO_SURFACE) {
                            // Create new egl surface
                            try {
                                eglSurface = EGL14.eglCreateWindowSurface(display, chooseConfig, sur, intArrayOf(EGL14.EGL_NONE), 0)
                                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)
                                tMediaPlayerLog.d(TAG) { "GL surface created: eglSurface=$eglSurface, surface=$sur" }
                                if (!isNotifyContextCreated) {
                                    isNotifyContextCreated = true
                                    realRenderer.glContextCreated()
                                }
                                realRenderer.glSurfaceCreated()
                            } catch (e: Throwable) {
                                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                                    EGL14.eglDestroySurface(display, eglSurface)
                                    eglSurface = EGL14.EGL_NO_SURFACE
                                }
                                tMediaPlayerLog.e(TAG) { "GL surface create fail: ${e.message}, surface=$sur" }
                            }
                        }
                    } else {
                        if (eglSurface != EGL14.EGL_NO_SURFACE) {
                            // Destroy egl surface.
                            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                            EGL14.eglDestroySurface(display, eglSurface)
                            realRenderer.glSurfaceDestroyed()
                            tMediaPlayerLog.d(TAG) { "GL surface destroyed: eglSurface=$eglSurface" }
                            eglSurface = EGL14.EGL_NO_SURFACE
                        }
                    }

                    if (eglSurface == EGL14.EGL_NO_SURFACE) {
                        tMediaPlayerLog.d(TAG) { "GL waiting surface..." }
                        (this as Object).wait()
                        continue
                    }

                    val size = surfaceSizeChange
                    if (size != null) {
                        tMediaPlayerLog.d(TAG) { "GL surface size changed: ${size.first}x${size.second}"}
                        realRenderer.surfaceSizeChanged(size.first, size.second)
                        surfaceSizeChange = null
                    }

                    if (requestRender) {
                        requestRender = false
                        realRenderer.drawFrame()
                        if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                            tMediaPlayerLog.e(TAG) { "GL surface swap buffers fail: ${EGL14.eglGetError()}" }
                        }
                    }
                    while (tasks.isNotEmpty()) {
                        tasks.pollFirst()?.invoke(true)
                    }

                    (this as Object).wait()
                }
            }

            while (tasks.isNotEmpty()) {
                tasks.pollFirst()?.invoke(false)
            }
        }
    }

    companion object {

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

        private class GLContextListenerWrapper(
            val l: GLContextListener
        ) {
            private val isGLContextCreated: AtomicBoolean = AtomicBoolean(false)

            fun dispatchGLContextCreated() {
                if (isGLContextCreated.compareAndSet(false, true)) {
                    l.glContextCreated()
                }
            }

            fun dispatchGLContextDestroying() {
                if (isGLContextCreated.compareAndSet(true, false)) {
                    l.glContextDestroying()
                }
            }
        }

        interface GLContextListener {

            fun glContextCreated()

            fun glContextDestroying()
        }

        private const val TAG = "GLRenderer"
    }
}