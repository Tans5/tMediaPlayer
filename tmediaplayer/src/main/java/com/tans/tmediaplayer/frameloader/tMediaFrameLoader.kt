package com.tans.tmediaplayer.frameloader

import android.graphics.Bitmap
import com.tans.tmediaplayer.player.OptResult
import com.tans.tmediaplayer.player.toOptResult
import java.io.File
import java.nio.ByteBuffer

@Suppress("ClassName")
object tMediaFrameLoader {
    init {
        System.loadLibrary("tmediaframeloader")
    }

    fun loadMediaFileFrame(
        mediaFile: String,
        position: Long = 0L,
        needRealTime: Boolean = false
    ): Bitmap? {
        val file = File(mediaFile)
        if (file.isFile && file.canRead()) {
            val nativeLoader = createFrameLoaderNative()
            try {
                var result = prepareNative(nativeLoader, mediaFile).toOptResult()
                if (result != OptResult.Success) {
                    return null
                }
                result = getFrameNative(
                    nativeFrameLoader = nativeLoader,
                    position = position,
                    needRealTime = needRealTime
                ).toOptResult()
                if (result != OptResult.Success) {
                    return null
                }
                val byteSize = getVideoFrameRgbaSizeNative(nativeLoader)
                val bytes = ByteArray(byteSize)
                getVideoFrameRgbaBytesNative(nativeLoader, bytes)
                val width = videoWidthNative(nativeLoader)
                val height = videoHeightNative(nativeLoader)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
                return bitmap
            } finally {
                releaseNative(nativeLoader)
            }
        } else {
            return null
        }
    }

    private external fun createFrameLoaderNative(): Long

    private external fun prepareNative(nativeFrameLoader: Long, filePath: String): Int

    private external fun getFrameNative(nativeFrameLoader: Long, position: Long, needRealTime: Boolean): Int

    private external fun videoWidthNative(nativeFrameLoader: Long): Int

    private external fun videoHeightNative(nativeFrameLoader: Long): Int

    private external fun getVideoFrameRgbaSizeNative(nativeFrameLoader: Long): Int

    private external fun getVideoFrameRgbaBytesNative(nativeFrameLoader: Long, byteArray: ByteArray)

    private external fun releaseNative(nativeFrameLoader: Long)
}