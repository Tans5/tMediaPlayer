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
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.playerview.filter.FilterImageTexture
import com.tans.tmediaplayer.player.playerview.filter.ImageFilter
import com.tans.tmediaplayer.player.playerview.texconverter.ImageTextureConverter
import com.tans.tmediaplayer.player.playerview.texconverter.RgbaImageTextureConverter
import com.tans.tmediaplayer.player.playerview.texconverter.Yuv420pImageTextureConverter
import com.tans.tmediaplayer.player.playerview.texconverter.Yuv420spImageTextureConverter
import com.tans.tmediaplayer.player.rwqueue.VideoFrame
import com.tans.tmediaplayer.tMediaPlayerLog
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class GLRenderer {

    private var renderSurfaceAdapter: RenderSurfaceAdapter? = null

    private val surfaceListener: RenderSurfaceAdapter.Listener by lazy {
        object : RenderSurfaceAdapter.Listener {
            override fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
                tMediaPlayerLog.d(TAG) { "Android surface created: ${surface.isValid}, ${width}x$height" }
                glThread.requestAttachSurface(surface)
                glThread.requestSizeChange(width, height)
            }

            override fun onSurfaceSizeChanged(width: Int, height: Int) {
                tMediaPlayerLog.d(TAG) { "Android surface size changed: ${width}x$height" }
                glThread.requestSizeChange(width, height)
            }

            override fun onSurfaceDestroyed() {
                tMediaPlayerLog.d(TAG) { "Andorid surface destroyed." }
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

    private val renderListeners: LinkedBlockingDeque<RenderListener> by lazy {
        LinkedBlockingDeque()
    }

    // region Opt
    fun setScaleType(scaleType: ScaleType) {
        this.realRenderer.scaleType.set(scaleType)
    }

    fun setFilter(filter: ImageFilter?) {
        realRenderer.filter.set(filter)
    }

    fun getFilter(): ImageFilter? {
        return realRenderer.filter.get()
    }

    fun getScaleType(): ScaleType = this.realRenderer.scaleType.get()
    // endregion


    fun refreshFrame() {
        glThread.requestRender()
    }

    fun requestRender(frame: VideoFrame) {
        if (frame.imageType == ImageRawType.Unknown || (frame.imageType == ImageRawType.HwSurface && frame.isBadTextureBuffer)) {
            tMediaPlayerLog.e(TAG) { "Drop video frame: ${frame.pts}, bad frame: $frame" }
            dispatchFrameRenderState(frame, false)
            return
        }
        realRenderer.apply {
            if (!isReleased && glThread.isSurfaceAlive()) {
                if (isWritingRequestRenderData.compareAndSet(false, true)) {
                    if (requestRenderData.containsRenderData()) {
                        val lastFrame = requestRenderData.refVideoFrame
                        if (lastFrame != null) {
                            tMediaPlayerLog.e(TAG) { "Drop video frame: ${lastFrame.pts}, because out of date." }
                            dispatchFrameRenderState(lastFrame, false)
                        }
                    }
                    requestRenderData.refVideoFrame = frame
                    isWritingRequestRenderData.set(false)
                    glThread.requestRender()
                } else {
                    tMediaPlayerLog.e(TAG) { "Drop video frame: ${frame.pts}, renderer is writing render data." }
                    dispatchFrameRenderState(frame, false)
                }
            } else {
                tMediaPlayerLog.e(TAG) { "Drop video frame: ${frame.pts}, under rendering or gl surface not ready." }
                dispatchFrameRenderState(frame, false)
            }
        }
    }

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
            realRenderer.tryRecycleUnhandledRequestImageData()
            renderListeners.clear()
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

    fun destroyHwOesTextureAndBufferTextures(textures: Pair<Int, IntArray>) {
        val oesTexture = textures.first
        GLES30.glDeleteTextures(1, intArrayOf(oesTexture), 0)
        val bufferTextures = textures.second
        GLES30.glDeleteTextures(bufferTextures.size, bufferTextures, 0)
    }

    fun addGLContextListener(l: GLContextListener) {
        if (!isReleased) {
            if (glContextListeners.find { it.l === l } == null) {
                val new = GLContextListenerWrapper(l)
                glContextListeners.add(new)
                if (glThread.isSurfaceAlive()) {
                    enqueueTask { new.dispatchGLContextCreated() }
                }
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

    fun addRenderListener(l: RenderListener) {
        if (!isReleased) {
            if (!renderListeners.contains(l)) {
                renderListeners.add(l)
            }
        }
    }

    fun removeRenderListener(l: RenderListener) {
        renderListeners.remove(l)
    }

    private fun dispatchFrameRenderState(frame: VideoFrame, isRendered: Boolean) {
        for (l in renderListeners) {
            l.onFrameRenderStateUpdate(frame, isRendered)
        }
    }

    private inner class RealRenderer {

        val filter: AtomicReference<ImageFilter?> by lazy {
            AtomicReference(null)
        }

        val scaleType: AtomicReference<ScaleType> by lazy {
            AtomicReference(ScaleType.CenterFit)
        }

        private val textureConverters: MutableMap<ImageRawType, ImageTextureConverter> = hashMapOf()

        // region RenderImageData
        val requestRenderData = RequestRenderData()

        val lastRenderedData = LastRenderedData()

        val isWritingRequestRenderData = AtomicBoolean(false)
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
            filter.get()?.dispatchGlSurfaceCreated(context)
            for (l in glContextListeners) {
                l.dispatchGLContextCreated()
            }
        }

        fun glSurfaceCreated() {
            if (requestRenderData.containsRenderData() || lastRenderedData.containsRenderData()) {
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
            if (rendererData == null || screenSize == null) {
                tMediaPlayerLog.e(TAG) { "Skip render, gl surface not active." }
                return
            }
            var frame: VideoFrame? = null
            if (isWritingRequestRenderData.compareAndSet(false, true)) {
                frame = requestRenderData.refVideoFrame
                requestRenderData.refVideoFrame = null
                isWritingRequestRenderData.set(false)
            } else {
                tMediaPlayerLog.d(TAG) { "Skip render, is writing request render data." }
                return
            }

            if (!lastRenderedData.containsRenderData() && frame == null) {
                tMediaPlayerLog.d(TAG) { "Skip render, no data to render." }
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
            val imageDataType: ImageRawType?

            if (frame != null) {
                imageWidth = frame.width
                imageHeight = frame.height
                rgbaBytes = frame.rgbaBuffer
                yBytes = frame.yBuffer
                uBytes = frame.uBuffer
                vBytes = frame.vBuffer
                uvBytes = frame.uvBuffer
                textureId = frame.textureBuffer
                pts = frame.pts
                imageDataType = frame.imageType
            } else {
                imageWidth = lastRenderedData.imageWidth!!
                imageHeight = lastRenderedData.imageHeight!!
                rgbaBytes = lastRenderedData.rgbaBytes
                yBytes = lastRenderedData.yBytes
                uBytes = lastRenderedData.uBytes
                vBytes = lastRenderedData.vBytes
                uvBytes = lastRenderedData.uvBytes
                textureId = lastRenderedData.textureId
                pts = lastRenderedData.pts
                imageDataType = lastRenderedData.imageDataType
                // tMediaPlayerLog.d(TAG) { "Draw last frame" }
            }
            // tMediaPlayerLog.d(TAG) { "Start render pts=$pts, textureId=$textureId" }
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            val texConverter = when (imageDataType!!) {
                ImageRawType.Rgba -> {
                    textureConverters[ImageRawType.Rgba].let {
                        if (it == null) {
                            val new = RgbaImageTextureConverter()
                            new.dispatchGlSurfaceCreated(context)
                            textureConverters[ImageRawType.Rgba] = new
                            new
                        } else {
                            it
                        }
                    }
                }
                ImageRawType.Yuv420p -> {
                    textureConverters[ImageRawType.Yuv420p].let {
                        if (it == null) {
                            val new = Yuv420pImageTextureConverter()
                            new.dispatchGlSurfaceCreated(context)
                            textureConverters[ImageRawType.Yuv420p] = new
                            new
                        } else {
                            it
                        }
                    }
                }
                ImageRawType.Nv12, ImageRawType.Nv21 -> {
                    textureConverters[ImageRawType.Nv12].let {
                        if (it == null) {
                            val new = Yuv420spImageTextureConverter()
                            new.dispatchGlSurfaceCreated(context)
                            textureConverters[ImageRawType.Nv12] = new
                            new
                        } else {
                            it
                        }
                    }
                }

                else -> {
                    null
                }
            }
            val convertTextureId = texConverter?.drawFrame(
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

            filter.get().let {
                if (it != null) {
                    it.dispatchGlSurfaceCreated(context)
                    it.dispatchDrawFrame(
                        context = context,
                        surfaceWidth = screenSize.first,
                        surfaceHeight = screenSize.second,
                        input = filterInput,
                        output = filterOutput
                    )
                } else {
                    filterOutput.width = filterInput.width
                    filterOutput.height = filterInput.height
                    filterOutput.texture = filterInput.texture
                }
            }

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

            if (frame != null) {
                dispatchFrameRenderState(frame, true)
            }
            lastRenderedData.imageWidth = imageWidth
            lastRenderedData.imageHeight = imageHeight
            lastRenderedData.rgbaBytes = rgbaBytes
            lastRenderedData.yBytes = yBytes
            lastRenderedData.uBytes = uBytes
            lastRenderedData.vBytes = vBytes
            lastRenderedData.uvBytes = uvBytes
            lastRenderedData.textureId = textureId
            lastRenderedData.pts = pts
            lastRenderedData.imageDataType = imageDataType
            // tMediaPlayerLog.d(TAG) { "Rendered video frame: pts=$pts, textureId=${textureId}" }
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
            // do nothing
        }

        fun glContextDestroying() {
            sizeCache = null
            val data = glRendererData
            if (data != null) {
                glRendererData = null
                GLES30.glDeleteBuffers(1, intArrayOf(data.VBO), 0)
                GLES30.glDeleteProgram(data.program)
            }

            val oesData = oesGlRenderData
            if (oesData != null) {
                oesGlRenderData = null
                GLES30.glDeleteBuffers(1, intArrayOf(oesData.VBO), 0)
                GLES30.glDeleteProgram(oesData.program)
            }

            for ((_, texConverter) in textureConverters) {
                texConverter.dispatchGlSurfaceDestroying()
            }
            textureConverters.clear()
            filter.get()?.dispatchGlSurfaceDestroying()
            tryRecycleUnhandledRequestImageData()
            for (l in glContextListeners) {
                l.dispatchGLContextDestroying()
            }
        }

        fun tryRecycleUnhandledRequestImageData() {
            if (isWritingRequestRenderData.compareAndSet(false, true)) {
                val frame = requestRenderData.refVideoFrame
                requestRenderData.refVideoFrame = null
                isWritingRequestRenderData.set(false)
                if (frame != null) {
                    dispatchFrameRenderState(frame, false)
                }
            }
        }
    }

    private inner class GLThread : Thread("tMediaGLThread") {

        init {
            priority = MAX_PRIORITY
        }

        private val isStarted: AtomicBoolean = AtomicBoolean(false)

        private var requestQuit: Boolean = false

        @Volatile
        private var isQuited: Boolean = false

        @Volatile
        private var surface: Surface? = null

        private var requestSurfaceSize: Pair<Int, Int>? = null

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
            if (surface == null) {
                tMediaPlayerLog.d(TAG) { "Request set surface: $s" }
                surface = s
                (this as Object).notifyAll()
            }
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
            requestSurfaceSize = width to height
            requestRender = true
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
            tMediaPlayerLog.d(TAG) { "Gl thread start run!!!" }
            // 1. Display
            var display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                error("Gl thread init egl display fail.")
            }
            tMediaPlayerLog.d(TAG) { "Gl thread egl display inited, major version: ${version[0]}, minor version: ${version[1]}" }


            // 2. Choose configure
            val eglConfigureSpec = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
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
                error("Gl thread eglChooseConfig fail.")
            }
            tMediaPlayerLog.d(TAG) { "Gl thread all configure size: ${configNum[0]}" }
            val allConfigs = Array<EGLConfig?>(configNum[0]) { null }
            if (!EGL14.eglChooseConfig(display,
                eglConfigureSpec, 0,
                allConfigs, 0, allConfigs.size,
                configNum, 0
            )) {
                error("Gl thread eglChooseConfig fail.")
            }
            var chooseConfig: EGLConfig? = null
            val v = intArrayOf(0)
            fun eglGetConfigAttr(config: EGLConfig, key: Int): Int {
                return if (EGL14.eglGetConfigAttrib(display, config, key, v, 0)) {
                    v[0]
                } else {
                    0
                }
            }
            for (c in allConfigs) {
                if (c != null) {
                    val d = eglGetConfigAttr(c, EGL14.EGL_DEPTH_SIZE)
                    if (d >= 0) {
                        val r = eglGetConfigAttr(c, EGL14.EGL_RED_SIZE)
                        val g = eglGetConfigAttr(c, EGL14.EGL_GREEN_SIZE)
                        val b = eglGetConfigAttr(c, EGL14.EGL_BLUE_SIZE)
                        val a = eglGetConfigAttr(c, EGL14.EGL_ALPHA_SIZE)
                        if (r == 8 && g == 8 && b == 8 && a == 8) {
                            chooseConfig = c
                            break
                        }
                    }
                }
            }

            tMediaPlayerLog.d(TAG) { "Gl thread choose egl configure: $chooseConfig" }

            // 3. Create egl context.
            val eglContextAttrs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            var eglContext = EGL14.eglCreateContext(display, chooseConfig, EGL14.EGL_NO_CONTEXT, eglContextAttrs, 0)
            tMediaPlayerLog.d(TAG) { "Gl thread create egl context: $eglContext" }

            // 4. Wait create surface.
            var eglSurface = EGL14.EGL_NO_SURFACE

            var isNotifyContextCreated = false
            var surfaceSize: Pair<Int, Int>? = null
            var lastSurfaceSize: Pair<Int, Int>? = null

            var doQuit = false
            var doSurfaceCreate = false
            var doSurfaceDestroy = false
            var doSurfaceSizeChange = false
            var doRender = false
            var doTasks = false
            while (true) {
                synchronized(this) {
                    while (true) {
                        // Request quit
                        if (requestQuit) {
                            requestQuit = false
                            doQuit = true
                            (this as Object).notifyAll()
                            break
                        }

                        // check surface
                        if (eglSurface == EGL14.EGL_NO_SURFACE) {
                            if (surface != null) {
                                doSurfaceCreate = true
                            }
                        } else {
                            if (surface == null) {
                                doSurfaceDestroy = true
                                (this as Object).notifyAll()
                                break
                            }
                        }

                        if (eglSurface != EGL14.EGL_NO_SURFACE || doSurfaceCreate) {
                            // surface size change
                            if (requestSurfaceSize != surfaceSize && requestSurfaceSize != null) {
                                lastSurfaceSize = surfaceSize
                                surfaceSize = requestSurfaceSize
                                requestSurfaceSize = null
                                doSurfaceSizeChange = true
                            }

                            // Render
                            if (requestRender) {
                                requestRender = false
                                doRender = true
                            }

                            // Task
                            if (tasks.isNotEmpty()) {
                                doTasks = true
                            }

                            if (doSurfaceCreate || doSurfaceSizeChange || doRender || doTasks) {
                                (this as Object).notifyAll()
                                break
                            } else {
                                tMediaPlayerLog.d(TAG) { "Gl thread no task to do." }
                                (this as Object).wait()
                            }
                        } else {
                            tMediaPlayerLog.d(TAG) { "Gl thread wait surface." }
                            (this as Object).wait()
                        }
                    }
                } // end synchronized

                // Destroy
                if (doQuit) {
                    if (isNotifyContextCreated) {
                        isNotifyContextCreated = false
                        realRenderer.glContextDestroying()
                    }
                    EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
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
                    tMediaPlayerLog.d(TAG) { "Gl thread quited." }
                    doQuit = false
                    surfaceSize = null
                    lastSurfaceSize = null
                    synchronized(this) {
                        (this as Object).notifyAll()
                    }
                    break
                }

                // Create surface
                if (doSurfaceCreate) {
                    doSurfaceCreate = false
                    val sur = surface
                    if (sur != null) {
                        try {
                            eglSurface = EGL14.eglCreateWindowSurface(
                                display,
                                chooseConfig,
                                sur,
                                intArrayOf(EGL14.EGL_NONE),
                                0
                            )
                            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)
                            tMediaPlayerLog.d(TAG) { "Gl thread surface created: eglSurface=$eglSurface, surface=$sur" }
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
                            tMediaPlayerLog.e(TAG) { "Gl thread surface create fail: ${e.message}, surface=$sur" }
                        }
                    } else {
                        tMediaPlayerLog.e(TAG) { "Gl thread wrong surface, surface is null." }
                    }
                }

                // Surface destroy
                if (doSurfaceDestroy) {
                    doSurfaceDestroy = false
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        surfaceSize = null
                        lastSurfaceSize = null
                        // Destroy egl surface.
                        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        EGL14.eglDestroySurface(display, eglSurface)
                        realRenderer.glSurfaceDestroyed()
                        tMediaPlayerLog.d(TAG) { "Gl thread surface destroyed: eglSurface=$eglSurface" }
                        eglSurface = EGL14.EGL_NO_SURFACE
                    }
                }

                // No surface
                if (eglSurface == EGL14.EGL_NO_SURFACE) {
                    tMediaPlayerLog.d(TAG) { "Gl thread no surface, waiting." }
                    continue
                }

                // Surface size changed
                if (doSurfaceSizeChange) {
                    doSurfaceSizeChange = false
                    if (surfaceSize != null) {
                        val sur = surface
                        if (lastSurfaceSize != null && sur != null) {
                            tMediaPlayerLog.d(TAG) { "Gl thread do recreate surface: lastSurfaceSize=${lastSurfaceSize.first}x${lastSurfaceSize.second}" }
                            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                            EGL14.eglDestroySurface(display, eglSurface)
                            eglSurface = EGL14.eglCreateWindowSurface(display, chooseConfig, sur, intArrayOf(EGL14.EGL_NONE), 0)
                            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)
                        }
                        tMediaPlayerLog.d(TAG) { "Gl thread surface size changed: newSurfaceSize=${surfaceSize.first}x${surfaceSize.second}" }
                        realRenderer.surfaceSizeChanged(surfaceSize.first, surfaceSize.second)
                    }
                }

                // Draw frame
                if (doRender) {
                    doRender = false
                    realRenderer.drawFrame()
                    if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                        tMediaPlayerLog.e(TAG) { "Gl thread surface swap buffers fail: ${EGL14.eglGetError()}" }
                    }
                }

                // Run tasks
                while (tasks.isNotEmpty()) {
                    tasks.pollFirst()?.invoke(true)
                }
                doTasks = false
            }

            while (tasks.isNotEmpty()) {
                tasks.pollFirst()?.invoke(false)
            }
        }
    }

    companion object {

        private class RequestRenderData {
            var refVideoFrame: VideoFrame? = null

            fun containsRenderData(): Boolean = refVideoFrame != null
        }

        private class LastRenderedData {
            var imageWidth: Int? = null
            var imageHeight: Int? = null
            var rgbaBytes: ByteArray? = null
            var yBytes: ByteArray? = null
            var uBytes: ByteArray? = null
            var vBytes: ByteArray? = null
            var uvBytes: ByteArray? = null
            var textureId: Int? = null
            var pts: Long? = null
            var imageDataType: ImageRawType? = null

            fun containsRenderData(): Boolean = imageDataType != null
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

        interface RenderListener {
            fun onFrameRenderStateUpdate(frame: VideoFrame, isRendered: Boolean)
        }

        private const val TAG = "GLRenderer"
    }
}