package com.tans.tmediaplayer

import android.graphics.Bitmap
import androidx.annotation.Keep
import java.nio.ByteBuffer

@Suppress("ClassName")
@Keep
class tMediaPlayer {

    fun prepare(file: String): OptResult {
        val nativePlayer = createPlayerNative()
        val result = prepareNative(nativePlayer, file, true, 2).toOptResult()
        if (result == OptResult.Success) {
            val mediaInfo = getMediaInfo(nativePlayer)
            MediaLog.d(TAG, "Prepare player success: $mediaInfo")
            Thread {
                while (true) {
                    val nativeBuffer = allocDecodeDataNative(nativePlayer)
                    val decodeResult = decodeNative(nativePlayer, nativeBuffer)
//                    if (decodeResult == 0 && isVideoBufferNative(nativeBuffer)) {
//                        val bytes = getVideoFrameBytesNative(nativeBuffer)
//                        val bitmap = Bitmap.createBitmap(
//                            getVideoWidthNative(nativeBuffer),
//                            getVideoHeightNative(nativeBuffer),
//                            Bitmap.Config.ARGB_8888
//                        )
//                        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
//                        println(bitmap)
//                    }
                    MediaLog.d(TAG, "Decode result: $decodeResult")
                    if (decodeResult == 1) {
                        break
                    }
                    freeDecodeDataNative(nativePlayer, nativeBuffer)
                }
                releaseNative(nativePlayer)
            }.start()
        } else {
            releaseNative(nativePlayer)
            MediaLog.e(TAG, "Prepare player fail.")
        }
        return result
    }

    fun release() {

    }

    private fun getMediaInfo(nativePlayer: Long): MediaInfo {
        return MediaInfo(
            nativePlayer = nativePlayer,
            duration = durationNative(nativePlayer),
            videoWidth = videoWidthNative(nativePlayer),
            videoHeight = videoHeightNative(nativePlayer),
            videoFps = videoFpsNative(nativePlayer),
            videoDuration = videoDurationNative(nativePlayer),
            audioChannels = audioChannelsNative(nativePlayer),
            audioSimpleRate = audioSampleRateNative(nativePlayer),
            audioPreSampleBytes = audioPreSampleBytesNative(nativePlayer),
            audioDuration = audioDurationNative(nativePlayer)
        )
    }

    private external fun createPlayerNative(): Long

    private external fun prepareNative(nativePlayer: Long, file: String, requestHw: Boolean, targetAudioChannels: Int): Int

    private external fun durationNative(nativePlayer: Long): Long

    private external fun videoWidthNative(nativePlayer: Long): Int

    private external fun videoHeightNative(nativePlayer: Long): Int

    private external fun videoFpsNative(nativePlayer: Long): Double

    private external fun videoDurationNative(nativePlayer: Long): Long

    private external fun audioChannelsNative(nativePlayer: Long): Int

    private external fun audioPreSampleBytesNative(nativePlayer: Long): Int

    private external fun audioSampleRateNative(nativePlayer: Long): Int

    private external fun audioDurationNative(nativePlayer: Long): Long

    private external fun allocDecodeDataNative(nativePlayer: Long): Long

    private external fun isVideoBufferNative(nativeBuffer: Long): Boolean

    private external fun getVideoWidthNative(nativeBuffer: Long): Int

    private external fun getVideoHeightNative(nativeBuffer: Long): Int

    private external fun getVideoPtsNative(nativeBuffer: Long): Long

    private external fun getVideoFrameBytesNative(nativeBuffer: Long): ByteArray

    private external fun freeDecodeDataNative(nativePlayer: Long, nativeBuffer: Long)

    private external fun decodeNative(nativePlayer: Long, nativeBuffer: Long): Int

    private external fun releaseNative(nativePlayer: Long)

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
        const val TAG = "tMediaPlayer"
        enum class OptResult { Success, Fail }

        private fun Int.toOptResult(): OptResult {
            return if (OptResult.Success.ordinal == this) {
                OptResult.Success
            } else {
                OptResult.Fail
            }
        }
    }
}