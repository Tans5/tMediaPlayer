package com.tans.tmediaplayer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    init {
        setEGLContextClientVersion(3)
        setRenderer(FrameRenderer().apply { this@tMediaPlayerView.renderer = this })
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setScaleType(scaleType: ScaleType) {
        this.scaleType.set(scaleType)
    }

    fun getScaleType(): ScaleType = this.scaleType.get()

    internal fun requestRenderFrame(width: Int, height: Int, imageBytes: ByteArray) {
        val imageData = ImageData(
            imageWidth = width,
            imageHeight = height,
            imageBytes = imageBytes
        )
        nextRenderFrame.set(imageData)
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
            val program = compileShaderProgram(VERTEX_SHADER_SOURCE_CODE, FRAGMENT_SHADER_SOURCE_CODE)
            if (program != null) {
                val textureIdArray =IntArray(1)
                GLES30.glGenTextures(1, textureIdArray, 0)
                val textureId = textureIdArray[0]
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

                val VAOArray = IntArray(1)
                GLES30.glGenVertexArrays(1, VAOArray, 0)
                val VAO = VAOArray[0]
                val VBOArray = IntArray(1)
                GLES30.glGenBuffers(1, VBOArray, 0)
                val VBO = VBOArray[0]

                glRendererData = GLRendererData(
                    program = program,
                    textureId = textureId,
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
            val imageData = this@tMediaPlayerView.nextRenderFrame.get()
            if (rendererData != null && screenSize != null && imageData != null) {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rendererData.textureId)
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    0,
                    GLES30.GL_RGBA,
                    imageData.imageWidth,
                    imageData.imageHeight,
                    0,
                    GLES30.GL_RGBA,
                    GLES30.GL_UNSIGNED_BYTE,
                    ByteBuffer.wrap(imageData.imageBytes)
                )

                GLES30.glUseProgram(rendererData.program)
                val imageRatio = imageData.imageWidth.toFloat() / imageData.imageHeight.toFloat()
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
                    positionTl.x, positionTl.y, 1.0f,    textureTl.x, textureTl.y,      // 左上
                    positionRb.x, positionTl.y, 1.0f,    textureRb.x, textureTl.y,      // 右上
                    positionRb.x, positionRb.y, 1.0f,    textureRb.x, textureRb.y,      // 右下
                    positionTl.x, positionRb.y, 1.0f,    textureTl.x,  textureRb.y,     // 左下
                    0.0f
                )
                GLES30.glBindVertexArray(rendererData.VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rendererData.VBO)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertex.size * 4, vertex.toGlBuffer(), GLES31.GL_STREAM_DRAW)
                GLES30.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 5 * 4, 0)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 5 * 4, 3 * 4)
                GLES30.glEnableVertexAttribArray(1)

                // view
                val viewMatrix = newGlFloatMatrix()
                Matrix.scaleM(viewMatrix, 0, 1 / renderRatio, 1.0f, 1.0f)
                GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(rendererData.program, "view"), 1, false, viewMatrix, 0)

                // model
                val modelMatrix = newGlFloatMatrix()
                GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(rendererData.program, "model"), 1, false, modelMatrix, 0)

                // transform
                val transformMatrix = newGlFloatMatrix()
                GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(rendererData.program, "transform"), 1, false, transformMatrix, 0)

                GLES30.glBindVertexArray(rendererData.VAO)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rendererData.VBO)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0, 4)
            }
        }


        fun recycle() {
            sizeCache = null
            val data = glRendererData
            if (data != null) {
                GLES30.glDeleteTextures(1, intArrayOf(data.textureId), 0)
                GLES30.glDeleteBuffers(1, intArrayOf(data.VBO), 0)
            }
            glRendererData = null
        }

        @Suppress("SameParameterValue")
        private fun compileShaderProgram(
            vertexShaderSource: String,
            fragmentShaderSource: String): Int? {

            /**
             * 编译顶点着色器
             */
            val vertexShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
            GLES30.glShaderSource(vertexShader, vertexShaderSource)
            GLES30.glCompileShader(vertexShader)
            val vertexCompileState = ByteBuffer.allocateDirect(4).let {
                it.order(ByteOrder.nativeOrder())
                it.asIntBuffer()
            }
            GLES30.glGetShaderiv(vertexShader, GLES30.GL_COMPILE_STATUS, vertexCompileState)
            vertexCompileState.position(0)
            if (vertexCompileState.get() <= 0) {
                val log = GLES30.glGetShaderInfoLog(vertexShader)
                GLES30.glDeleteShader(vertexShader)
                MediaLog.e(TAG, "Compile vertex shader fail: $log")
                return null
            }

            /**
             * 编译片段着色器
             */
            val fragmentShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
            GLES30.glShaderSource(fragmentShader, fragmentShaderSource)
            GLES30.glCompileShader(fragmentShader)
            val fragmentCompileState = ByteBuffer.allocateDirect(4).let {
                it.order(ByteOrder.nativeOrder())
                it.asIntBuffer()
            }
            GLES30.glGetShaderiv(fragmentShader, GLES30.GL_COMPILE_STATUS, fragmentCompileState)
            fragmentCompileState.position(0)
            if (fragmentCompileState.get() <= 0) {
                val log = GLES30.glGetShaderInfoLog(fragmentShader)
                GLES30.glDeleteShader(vertexShader)
                GLES30.glDeleteShader(fragmentShader)
                MediaLog.e(TAG, "Compile fragment shader fail: $log")
                return null
            }

            /**
             * 链接着色器程序
             */
            val shaderProgram = GLES30.glCreateProgram()
            GLES30.glAttachShader(shaderProgram, vertexShader)
            GLES30.glAttachShader(shaderProgram, fragmentShader)
            GLES30.glLinkProgram(shaderProgram)
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            val linkProgramState = ByteBuffer.allocateDirect(4).let {
                it.order(ByteOrder.nativeOrder())
                it.asIntBuffer()
            }
            GLES30.glGetProgramiv(shaderProgram, GLES30.GL_LINK_STATUS, linkProgramState)
            linkProgramState.position(0)
            if (linkProgramState.get() <= 0) {
                val log = GLES30.glGetProgramInfoLog(shaderProgram)
                GLES30.glDeleteProgram(shaderProgram)
                Log.e(TAG, "Link program fail: $log")
                return null
            }
            Log.d(TAG, "Compile program success!!")
            return shaderProgram
        }

    }

    companion object {

        private const val VERTEX_SHADER_SOURCE_CODE = """#version 300 es
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform mat4 transform;
            uniform mat4 model;
            uniform mat4 view;
            out vec2 TexCoord;

            void main() {
                gl_Position = view * model * transform * vec4(aPos, 1.0);
                TexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER_SOURCE_CODE = """#version 300 es
            precision highp float;
            uniform sampler2D Texture;
            
            in vec2 TexCoord;
            out vec4 FragColor;
            void main() {
                FragColor = texture(Texture, TexCoord);
            }
        """

        enum class ScaleType {
            CenterFit,
            CenterCrop
        }
        data class ImageData(
            val imageWidth: Int,
            val imageHeight: Int,
            val imageBytes: ByteArray
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ImageData

                if (imageWidth != other.imageWidth) return false
                if (imageHeight != other.imageHeight) return false
                if (!imageBytes.contentEquals(other.imageBytes)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = imageWidth
                result = 31 * result + imageHeight
                result = 31 * result + imageBytes.contentHashCode()
                return result
            }
        }

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

        private fun FloatArray.toGlBuffer(): ByteBuffer {
            return ByteBuffer.allocateDirect(size * 4).let {
                it.order(ByteOrder.nativeOrder())
                it.asFloatBuffer().put(this)
                it.position(0)
                it
            }
        }

        private fun IntArray.toGlBuffer(): ByteBuffer {
            return ByteBuffer.allocateDirect(size * 4).let {
                it.order(ByteOrder.nativeOrder())
                it.asIntBuffer().put(this)
                it.position(0)
                it
            }
        }

        private data class GLRendererData(
            val program: Int,
            val textureId: Int,
            val VAO: Int,
            val VBO: Int,
        )

        private data class SurfaceSizeCache(
            val gl: GL10,
            val width: Int,
            val height: Int
        )

        private fun newGlFloatMatrix(n: Int = 4): FloatArray {
            return FloatArray(n * n) {
                val x = it / n
                val y = it % n
                if (x == y) {
                    1.0f
                } else {
                    0.0f
                }
            }
        }

        private const val TAG = "tMediaPlayerView"
    }
}