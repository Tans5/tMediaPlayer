package com.tans.tmediaplayer.render

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLES31
import android.util.Log
import com.tans.tmediaplayer.MediaLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

private const val TAG = "GLUtil"

internal fun Context.getRenderSourceCodeFromRawRes(id: Int): String {
    return try {
        val bytes = resources.openRawResource(id).use {
            it.readBytes()
        }
        String(bytes, Charsets.UTF_8)
    } catch (e: Throwable) {
        e.printStackTrace()
        ""
    }
}

internal fun compileShaderProgram(context: Context, vertexId: Int, fragId: Int): Int? {
    return compileShaderProgram(context.getRenderSourceCodeFromRawRes(vertexId), context.getRenderSourceCodeFromRawRes(fragId))
}

@Suppress("SameParameterValue")
internal fun compileShaderProgram(
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

internal fun newGlFloatMatrix(n: Int = 4): FloatArray {
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


internal fun FloatArray.toGlBuffer(): ByteBuffer {
    return ByteBuffer.allocateDirect(size * 4).let {
        it.order(ByteOrder.nativeOrder())
        it.asFloatBuffer().put(this)
        it.position(0)
        it
    }
}

internal fun IntArray.toGlBuffer(): ByteBuffer {
    return ByteBuffer.allocateDirect(size * 4).let {
        it.order(ByteOrder.nativeOrder())
        it.asIntBuffer().put(this)
        it.position(0)
        it
    }
}

internal fun newGlIntBuffer(): IntBuffer {
    return ByteBuffer.allocateDirect(4).let {
        it.order(ByteOrder.nativeOrder())
        it.asIntBuffer()
    }
}

internal fun glGenFrameBuffer(): Int {
    val buffer = newGlIntBuffer()
    GLES30.glGenFramebuffers(1, buffer)
    buffer.position(0)
    return buffer.get()
}

fun glGenTexture(): Int {
    val buffer = newGlIntBuffer()
    GLES30.glGenTextures(1, buffer)
    buffer.position(0)
    return buffer.get()
}

internal fun offScreenRender(
    outputTexId: Int,
    outputTexWidth: Int,
    outputTexHeight: Int,
    render: () -> Unit
) {

    // 生成帧缓冲
    val fbo = glGenFrameBuffer()
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)

    // 生成帧缓冲的附件纹理
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, outputTexId)
    // 纹理的数据设置为空，大小为图片大小, 光申请内存不填充，渲染时填充
    GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
        outputTexWidth, outputTexHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

    // 为帧缓冲添加纹理附件(颜色)
    GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
        GLES30.GL_TEXTURE_2D, outputTexId, 0)

    val frameBufferStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
    if (frameBufferStatus != GLES30.GL_FRAMEBUFFER_COMPLETE) {
        MediaLog.e(TAG, "Create frame buffer fail: $frameBufferStatus")
        return
    }

    // 获取当前的 view port, 离屏渲染完成后，需要还原
    val lastViewPort = IntArray(4)
    GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, lastViewPort, 0)

    // 创建离屏渲染的 view port
    GLES30.glViewport(0, 0, outputTexWidth, outputTexHeight)
    GLES30.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    render()
    GLES30.glBindVertexArray(GLES30.GL_NONE)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE)

    GLES30.glUseProgram(0)
    GLES30.glFinish()

//    val imageBytes = ByteArray(outputTexWidth * outputTexHeight * 4)
//    GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, outputTexId)
//    GLES31.glReadPixels(
//        0, 0,
//        outputTexWidth, outputTexHeight,
//        GLES31.GL_RGBA,
//        GLES31.GL_UNSIGNED_BYTE,
//        ByteBuffer.wrap(imageBytes)
//    )
//    val b = Bitmap.createBitmap(outputTexWidth, outputTexHeight, Bitmap.Config.ARGB_8888)
//    b.copyPixelsFromBuffer(ByteBuffer.wrap(imageBytes))
//    println(b)

    GLES30.glViewport(lastViewPort[0], lastViewPort[1], lastViewPort[2], lastViewPort[3])
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)

    // 激活默认缓冲
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    GLES30.glDeleteFramebuffers(1, intArrayOf(fbo) , 0)
}

internal fun glGenTextureAndSetDefaultParams(): Int {
    val tex = glGenTexture()
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
    GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    return tex
}

internal fun glGenBuffers(): Int {
    val buffer = newGlIntBuffer()
    GLES30.glGenBuffers(1, buffer)
    buffer.position(0)
    return buffer.get()
}

internal fun glGenVertexArrays(): Int {
    val buffer = newGlIntBuffer()
    GLES30.glGenVertexArrays(1, buffer)
    buffer.position(0)
    return buffer.get()
}