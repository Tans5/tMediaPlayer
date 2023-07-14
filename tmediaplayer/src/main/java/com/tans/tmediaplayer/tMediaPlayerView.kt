package com.tans.tmediaplayer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Suppress("ClassName")
class tMediaPlayerView : GLSurfaceView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)

    init {
        setEGLContextClientVersion(3)
        setRenderer(FrameRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    private inner class FrameRenderer : Renderer {

        private var sizeCache: SurfaceSizeCache? = null

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            val glVersion = gl.glGetString(GLES31.GL_VERSION)
            MediaLog.d(TAG, "Support gl version: $glVersion")
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            val program = compileShaderProgram(VERTEX_SHADER_SOURCE_CODE, FRAGMENT_SHADER_SOURCE_CODE)
            if (program != null) {

            }
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            sizeCache = SurfaceSizeCache(gl, width, height)
            GLES30.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10) {

        }


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

        private data class SurfaceSizeCache(
            val gl: GL10,
            val width: Int,
            val height: Int
        )
        private const val TAG = "tMediaPlayerView"
    }
}