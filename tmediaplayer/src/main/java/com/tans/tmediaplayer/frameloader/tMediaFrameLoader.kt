package com.tans.tmediaplayer.frameloader

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.annotation.Keep
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.toOptResult
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

@Suppress("ClassName")
@Keep
object tMediaFrameLoader {
    init {
        System.loadLibrary("tmediaframeloader")
    }

    fun loadMediaFileFrame(
        mediaFile: String,
        position: Long = 0L
    ): Bitmap? {
        val file = File(mediaFile)
        if (file.isFile && file.canRead()) {
            val start = SystemClock.uptimeMillis()
            val nativeLoader = createFrameLoaderNative()
            try {
                var result = prepareNative(nativeLoader, mediaFile).toOptResult()
                if (result != OptResult.Success) {
                    return null
                }
                val videoDuration = durationNative(nativeLoader)
                result = getFrameNative(
                    nativeFrameLoader = nativeLoader,
                    position = min(max(0, position), videoDuration),
                ).toOptResult()
                if (result != OptResult.Success) {
                    return null
                }
                val byteSize = getVideoFrameRgbaSizeNative(nativeLoader)
                val rotation = getVideoDisplayRotationNative(nativeLoader)
                val bytes = ByteArray(byteSize)
                getVideoFrameRgbaBytesNative(nativeLoader, bytes)
                val width = videoWidthNative(nativeLoader)
                val height = videoHeightNative(nativeLoader)
                val fixedWidth: Int
                val fixedHeight: Int
                when (rotation) {
                    90, 270 -> {
                        fixedWidth = height
                        fixedHeight = width
                    }
                    else -> {
                        fixedWidth = width
                        fixedHeight = height
                    }
                }
                val rotatedBytes = rotateBitmapRGBA(bytes, width, height, rotation)
                val bitmap = Bitmap.createBitmap(fixedWidth, fixedHeight, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rotatedBytes))
                return bitmap
            } finally {
                releaseNative(nativeLoader)
                val end = SystemClock.uptimeMillis()
                val cost = end - start
                tMediaPlayerLog.d(TAG) { "Load frame $mediaFile: position=$position, cost=${cost}ms" }
            }
        } else {
            return null
        }
    }

    fun rotateBitmapRGBA(data: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
        val pixelSize = 4 // RGBA 每个像素占 4 字节
        val totalPixels = width * height

        // 验证输入数据
        require(data.size >= totalPixels * pixelSize) { "Invalid data size" }

        return when (rotation) {
            90 -> rotate90(data, width, height, pixelSize)
            180 -> rotate180(data, width, height, pixelSize)
            270 -> rotate270(data, width, height, pixelSize)
            else -> data
        }
    }

    private fun rotate270(data: ByteArray, width: Int, height: Int, pixelSize: Int): ByteArray {
        val newWidth = height
        val newHeight = width
        val output = ByteArray(newWidth * newHeight * pixelSize)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = (y * width + x) * pixelSize
                val dstIndex = (x * newWidth + (height - 1 - y)) * pixelSize

                // 复制 RGBA 像素
                for (i in 0 until pixelSize) {
                    output[dstIndex + i] = data[srcIndex + i]
                }
            }
        }

        return output
    }

    private fun rotate180(data: ByteArray, width: Int, height: Int, pixelSize: Int): ByteArray {
        val output = ByteArray(data.size)
        val totalPixels = width * height

        for (i in 0 until totalPixels) {
            val srcIndex = i * pixelSize
            val dstIndex = (totalPixels - 1 - i) * pixelSize

            for (j in 0 until pixelSize) {
                output[dstIndex + j] = data[srcIndex + j]
            }
        }

        return output
    }

    private fun rotate90(data: ByteArray, width: Int, height: Int, pixelSize: Int): ByteArray {
        val newWidth = height
        val newHeight = width
        val output = ByteArray(newWidth * newHeight * pixelSize)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcIndex = (y * width + x) * pixelSize
                val dstIndex = ((width - 1 - x) * newWidth + y) * pixelSize

                for (i in 0 until pixelSize) {
                    output[dstIndex + i] = data[srcIndex + i]
                }
            }
        }

        return output
    }

    private external fun createFrameLoaderNative(): Long

    private external fun prepareNative(nativeFrameLoader: Long, filePath: String): Int

    private external fun getFrameNative(nativeFrameLoader: Long, position: Long): Int

    private external fun durationNative(nativeFrameLoader: Long): Long

    private external fun videoWidthNative(nativeFrameLoader: Long): Int

    private external fun videoHeightNative(nativeFrameLoader: Long): Int

    private external fun getVideoFrameRgbaSizeNative(nativeFrameLoader: Long): Int

    private external fun getVideoFrameRgbaBytesNative(nativeFrameLoader: Long, byteArray: ByteArray)

    private external fun getVideoDisplayRotationNative(nativeFrameLoader: Long): Int

    private external fun getVideoDisplayRatioNative(nativeFrameLoader: Long): Float

    private external fun releaseNative(nativeFrameLoader: Long)

    private const val TAG = "tMediaFrameLoader"
}