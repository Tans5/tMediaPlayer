package com.tans.tmediaplayer

import android.graphics.Bitmap
import androidx.annotation.Keep
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
@Keep
class tMediaPlayer {

    private val playerView: AtomicReference<tMediaPlayerView?> by lazy {
        AtomicReference(null)
    }

    private val listener: AtomicReference<tMediaPlayerListener?> by lazy {
        AtomicReference(null)
    }

    private val state: AtomicReference<tMediaPlayerState> by lazy {
        AtomicReference(tMediaPlayerState.NoInit)
    }

    fun getState(): tMediaPlayerState = state.get()

    @Synchronized
    fun prepare(file: String): OptResult {
        val lastMediaInfo = getMediaInfo()
        if (lastMediaInfo != null) {
            releaseNative(lastMediaInfo.nativePlayer)
        }
        dispatchNewState(tMediaPlayerState.NoInit)
        val nativePlayer = createPlayerNative()
        val result = prepareNative(nativePlayer, file, true, 2).toOptResult()
        if (result == OptResult.Success) {
            val mediaInfo = getMediaInfo(nativePlayer)
            MediaLog.d(TAG, "Prepare player success: $mediaInfo")
            dispatchNewState(tMediaPlayerState.Prepared(mediaInfo))
//            Thread {
//                while (true) {
//                    val nativeBuffer = allocDecodeDataNative(nativePlayer)
//                    val decodeResult = decodeNative(nativePlayer, nativeBuffer)
//                    if (decodeResult == 0 && isVideoBufferNative(nativeBuffer)) {
//                        val view = playerView.get()
//                        if (view != null) {
//                            val bytes = getVideoFrameBytesNative(nativeBuffer)
//                            view.requestRenderFrame(getVideoWidthNative(nativeBuffer), getVideoHeightNative(nativeBuffer), bytes)
////                        val bitmap = Bitmap.createBitmap(
////                            getVideoWidthNative(nativeBuffer),
////                            getVideoHeightNative(nativeBuffer),
////                            Bitmap.Config.ARGB_8888
////                        )
////                        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
////                        println(bitmap)
//                        }
//
//                    }
////                    if (decodeResult == 0 && !isVideoBufferNative(nativeBuffer)) {
////                        val audioPts = getAudioPtsNative(nativeBuffer)
////                        val audioPcmBytes = getAudioFrameBytesNative(nativeBuffer)
////                        println(audioPcmBytes)
////                    }
//                    MediaLog.d(TAG, "Decode result: $decodeResult")
//                    if (decodeResult == 1) {
//                        break
//                    }
//                    freeDecodeDataNative(nativePlayer, nativeBuffer)
//                }
//                releaseNative(nativePlayer)
//            }.start()
        } else {
            releaseNative(nativePlayer)
            MediaLog.e(TAG, "Prepare player fail.")
            dispatchNewState(tMediaPlayerState.Error("Prepare player fail."))
        }
        return result
    }

    @Synchronized
    fun play() {
        val state = getState()
        val playingState = when (state) {
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Error -> null
            is tMediaPlayerState.Paused -> state.play()
            is tMediaPlayerState.PlayEnd -> {
                resetNative(state.mediaInfo.nativePlayer)
                state.play()
            }
            is tMediaPlayerState.Playing -> null
            is tMediaPlayerState.Prepared -> state.play()
            is tMediaPlayerState.Stopped -> {
                resetNative(state.mediaInfo.nativePlayer)
                state.play()
            }
        }
        if (playingState != null) {
            MediaLog.d(TAG, "Request play.")
            dispatchNewState(playingState)
        } else {
            MediaLog.e(TAG, "Wrong state: $state for play() method.")
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        playerView.set(view)
    }

    fun getMediaInfo(): MediaInfo? {
        return when (val state = getState()) {
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Error -> null
            is tMediaPlayerState.Paused -> state.mediaInfo
            is tMediaPlayerState.PlayEnd -> state.mediaInfo
            is tMediaPlayerState.Playing -> state.mediaInfo
            is tMediaPlayerState.Prepared -> state.mediaInfo
            is tMediaPlayerState.Stopped -> state.mediaInfo
        }
    }

    fun setListener(l: tMediaPlayerListener?) {
        listener.set(l)
        l?.onPlayerState(getState())
    }

    fun release() {
        playerView.set(null)
        val mediaInfo = getMediaInfo()
        if (mediaInfo != null) {
            releaseNative(mediaInfo.nativePlayer)
        }
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

    private fun dispatchNewState(s: tMediaPlayerState) {
        val lastState = getState()
        if (lastState != s) {
            state.set(s)
            listener.get()?.onPlayerState(s)
        }
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

    private external fun getAudioPtsNative(nativeBuffer: Long): Long

    private external fun getAudioFrameBytesNative(nativeBuffer: Long): ByteArray

    private external fun freeDecodeDataNative(nativePlayer: Long, nativeBuffer: Long)

    private external fun resetNative(nativePlayer: Long): Int

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