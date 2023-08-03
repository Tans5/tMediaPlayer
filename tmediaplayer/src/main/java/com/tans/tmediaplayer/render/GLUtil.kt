package com.tans.tmediaplayer.render

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import com.tans.tmediaplayer.MediaLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

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