package com.tans.tmediaplayer

import android.os.SystemClock
import androidx.annotation.Keep
import com.tans.tmediaplayer.render.tMediaPlayerView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max

@Suppress("ClassName")
@Keep
class tMediaPlayer {

    private val listener: AtomicReference<tMediaPlayerListener?> by lazy {
        AtomicReference(null)
    }

    private val state: AtomicReference<tMediaPlayerState> by lazy {
        AtomicReference(tMediaPlayerState.NoInit)
    }

    private val bufferManager: tMediaPlayerBufferManager by lazy {
        tMediaPlayerBufferManager(this, 40)
    }

    private val decoder: tMediaPlayerDecoder by lazy {
        tMediaPlayerDecoder(this, bufferManager)
    }

    private val render: tMediaPlayerRender by lazy {
        tMediaPlayerRender(this, bufferManager)
    }

    private val progress: AtomicLong by lazy {
        AtomicLong(0)
    }

    private val ptsBaseTime: AtomicLong by lazy {
        AtomicLong(0)
    }

    private val basePts: AtomicLong by lazy {
        AtomicLong(0)
    }

    private val lastUpdateProgress: AtomicLong by lazy {
        AtomicLong(0L)
    }

    fun getState(): tMediaPlayerState = state.get()

    @Synchronized
    fun prepare(file: String, requestHw: Boolean = true): OptResult {
        val lastState = getState()
        if (lastState == tMediaPlayerState.Released) {
            MediaLog.e(TAG, "Prepare fail, player has released.")
            return OptResult.Fail
        }
        val lastMediaInfo = getMediaInfo()
        if (lastMediaInfo != null) {
            releaseNative(lastMediaInfo.nativePlayer)
        }
        dispatchNewState(tMediaPlayerState.NoInit)
        resetProgressAndBaseTime()
        bufferManager.prepare()
        bufferManager.clearRenderData()
        decoder.prepare()
        render.prepare()
        render.audioTrackFlush()
        render.removeRenderMessages()
        val nativePlayer = createPlayerNative()
        val result = prepareNative(nativePlayer, file, requestHw, 2).toOptResult()
        dispatchProgress(0L)
        if (result == OptResult.Success) {
            val mediaInfo = getMediaInfo(nativePlayer)
            MediaLog.d(TAG, "Prepare player success: $mediaInfo")
            dispatchNewState(tMediaPlayerState.Prepared(mediaInfo))
        } else {
            releaseNative(nativePlayer)
            MediaLog.e(TAG, "Prepare player fail.")
            dispatchNewState(tMediaPlayerState.Error("Prepare player fail."))
        }
        return result
    }

    @Synchronized
    fun play(): OptResult {
        val state = getState()
        val playingState = when (state) {
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Error -> null
            is tMediaPlayerState.Paused -> state.play()
            is tMediaPlayerState.PlayEnd -> {
                progress.set(0L)
                state.play()
            }
            is tMediaPlayerState.Playing -> null
            is tMediaPlayerState.Prepared -> {
                progress.set(0L)
                state.play()
            }
            is tMediaPlayerState.Stopped -> {
                progress.set(0L)
                state.play()
            }
            is tMediaPlayerState.Seeking -> null
            tMediaPlayerState.Released -> null
        }
        return if (playingState != null) {
            MediaLog.d(TAG, "Request play.")
            decoder.decode()
            render.render()
            render.audioTrackPlay()
            ptsBaseTime.set(SystemClock.uptimeMillis())
            basePts.set(getProgress())
            dispatchNewState(playingState)
            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for play() method.")
            OptResult.Fail
        }
    }

    @Synchronized
    fun pause(): OptResult {
        val state = getState()
        val pauseState = when (state) {
            is tMediaPlayerState.Error -> null
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Paused -> null
            is tMediaPlayerState.PlayEnd -> null
            is tMediaPlayerState.Playing -> state.pause()
            is tMediaPlayerState.Prepared -> null
            is tMediaPlayerState.Stopped -> null
            is tMediaPlayerState.Seeking -> null
            tMediaPlayerState.Released -> null
        }
        return if (pauseState != null) {
            MediaLog.d(TAG, "Request pause.")
            dispatchNewState(pauseState)
            decoder.pause()
            render.pause()
            render.audioTrackPause()
            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for pause() method.")
            OptResult.Fail
        }
    }

    @Synchronized
    fun seekTo(position: Long): OptResult {
        val state = getState()
        val seekingState: tMediaPlayerState.Seeking? = when (state) {
            is tMediaPlayerState.Error -> null
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Paused -> state.seek(position)
            is tMediaPlayerState.PlayEnd -> state.seek(position)
            is tMediaPlayerState.Playing -> state.seek(position)
            is tMediaPlayerState.Prepared -> state.seek(position)
            is tMediaPlayerState.Stopped -> state.seek(position)
            is tMediaPlayerState.Seeking -> null
            tMediaPlayerState.Released -> null
        }
        val mediaInfo = getMediaInfo()
        return if (mediaInfo != null && seekingState != null) {
            if (position !in 0 .. mediaInfo.duration) {
                MediaLog.e(TAG, "Wrong seek position: $position, for duration: ${mediaInfo.duration}")
                OptResult.Fail
            } else {
                decoder.pause()
                render.pause()
                render.audioTrackPause()
                render.removeRenderMessages()
                render.audioTrackFlush()
                bufferManager.clearRenderData()
                decoder.seekTo(position)
                dispatchNewState(seekingState)
                OptResult.Success
            }
        } else {
            MediaLog.e(TAG, "Wrong state: $state for seekTo() method.")
            OptResult.Fail
        }
    }

    @Synchronized
    fun stop(): OptResult {
        val state = getState()
        val stopState = when (state) {
            is tMediaPlayerState.Error -> null
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Paused -> state.stop()
            is tMediaPlayerState.PlayEnd -> null
            is tMediaPlayerState.Playing -> state.stop()
            is tMediaPlayerState.Prepared -> null
            is tMediaPlayerState.Stopped -> null
            is tMediaPlayerState.Seeking -> null
            tMediaPlayerState.Released -> null
        }
        return if (stopState != null) {
            MediaLog.d(TAG, "Request stop.")
            dispatchNewState(stopState)
            decoder.pause()
            render.pause()
            render.audioTrackFlush()
            render.removeRenderMessages()
            resetProgressAndBaseTime()
            bufferManager.clearRenderData()
            resetNative(stopState.mediaInfo.nativePlayer)
            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for stop() method.")
            OptResult.Fail
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        render.attachPlayerView(view)
    }

    fun getMediaInfo(): MediaInfo? {
        return getMediaInfoByState(getState())
    }

    private fun getMediaInfoByState(state: tMediaPlayerState): MediaInfo? {
        return when (state) {
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Error -> null
            is tMediaPlayerState.Released -> null
            is tMediaPlayerState.Paused -> state.mediaInfo
            is tMediaPlayerState.PlayEnd -> state.mediaInfo
            is tMediaPlayerState.Playing -> state.mediaInfo
            is tMediaPlayerState.Prepared -> state.mediaInfo
            is tMediaPlayerState.Stopped -> state.mediaInfo
            is tMediaPlayerState.Seeking -> getMediaInfoByState(state.lastState)
        }
    }

    fun setListener(l: tMediaPlayerListener?) {
        listener.set(l)
        l?.onPlayerState(getState())
    }

    @Synchronized
    fun release(): OptResult {
        val lastState = getState()
        if (lastState == tMediaPlayerState.NoInit || lastState == tMediaPlayerState.Released) {
            return OptResult.Fail
        }
        decoder.release()
        render.release()
        bufferManager.release()
        resetProgressAndBaseTime()
        val mediaInfo = getMediaInfo()
        if (mediaInfo != null) {
            releaseNative(mediaInfo.nativePlayer)
        }
        dispatchNewState(tMediaPlayerState.Released)
        listener.set(null)
        return OptResult.Success
    }

    fun getProgress(): Long = progress.get()

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

    internal fun dispatchPlayEnd() {
        val s = getState()
        if (s is tMediaPlayerState.Playing) {
            dispatchNewState(s.playEnd())
        }
        resetProgressAndBaseTime()
        bufferManager.clearRenderData()
        val np = getMediaInfo()?.nativePlayer
        if (np != null) {
            resetNative(np)
        }
    }

    internal fun handleSeekingBuffer(b: tMediaPlayerBufferManager.Companion.MediaBuffer, result: OptResult) {
        synchronized(b) {
            val s = getState()
            if (s == tMediaPlayerState.Released) { return@synchronized }
            when (result) {
                OptResult.Success -> {
                    val pts = getPtsNativeInternal(b.nativeBuffer)
                    render.removeRenderMessages()
                    render.audioTrackFlush()
                    bufferManager.clearRenderData()
                    basePts.set(pts)
                    ptsBaseTime.set(SystemClock.uptimeMillis())
                    dispatchProgress(pts)
                    if (isLastFrameBufferNative(b.nativeBuffer)) {
                        val info = getMediaInfo()
                        if (info != null) {
                            dispatchNewState(tMediaPlayerState.PlayEnd(info))
                            resetNative(info.nativePlayer)
                        }
                        resetProgressAndBaseTime()
                        bufferManager.enqueueDecodeBuffer(b)
                    } else {
                        render.handleSeekingBuffer(b)
                        if (s is tMediaPlayerState.Seeking) {
                            val lastState = s.lastState
                            if (lastState is tMediaPlayerState.Playing) {
                                decoder.decode()
                                render.render()
                                render.audioTrackPlay()
                            }
                            dispatchNewState(lastState)
                        } else {
                            MediaLog.e(TAG, "Expect seeking state, but now is $s")
                        }
                    }
                }
                OptResult.Fail -> {
                    if (s is tMediaPlayerState.Seeking) {
                        dispatchNewState(s.lastState)
                    }
                }
            }
        }
    }

    internal fun calculateRenderDelay(pts: Long): Long {
        val ptsLen = pts - basePts.get()
        val timeLen = SystemClock.uptimeMillis() - ptsBaseTime.get()
        return max(0, ptsLen - timeLen)
    }

    internal fun dispatchProgress(progress: Long) {
        val state = getState()
        if (state !is tMediaPlayerState.PlayEnd && state !is tMediaPlayerState.Error && state !is tMediaPlayerState.Stopped) {
            val info = getMediaInfo()
            val lp = lastUpdateProgress.get()
            this.progress.set(progress)
            if (info != null && abs(progress - lp) > 80) {
                lastUpdateProgress.set(progress)
                callbackExecutor.execute {
                    listener.get()?.onProgressUpdate(progress, info.duration)
                }
            }
        }
    }

    internal fun renderSuccess() {
        decoder.checkDecoderBufferIfWaiting()
    }

    internal fun decodeSuccess() {
        render.checkRenderBufferIfWaiting()
    }

    private fun dispatchNewState(s: tMediaPlayerState) {
        val lastState = getState()
        if (lastState != s) {
            state.set(s)
            callbackExecutor.execute {
                listener.get()?.onPlayerState(s)
            }
        }
    }

    private fun resetProgressAndBaseTime() {
        progress.set(0)
        lastUpdateProgress.set(0L)
        basePts.set(0L)
        ptsBaseTime.set(0L)
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

    internal fun allocDecodeDataNativeInternal(): Long {
        return allocDecodeDataNative()
    }

    private external fun allocDecodeDataNative(): Long

    internal fun isVideoBufferNativeInternal(nativeBuffer: Long): Boolean = isVideoBufferNative(nativeBuffer)

    private external fun isVideoBufferNative(nativeBuffer: Long): Boolean

    internal fun isLastFrameBufferNativeInternal(nativeBuffer: Long): Boolean = isLastFrameBufferNative(nativeBuffer)

    private external fun isLastFrameBufferNative(nativeBuffer: Long): Boolean

    internal fun getVideoWidthNativeInternal(nativeBuffer: Long): Int = getVideoWidthNative(nativeBuffer)

    private external fun getVideoWidthNative(nativeBuffer: Long): Int

    internal fun getVideoHeightNativeInternal(nativeBuffer: Long): Int = getVideoHeightNative(nativeBuffer)

    private external fun getVideoHeightNative(nativeBuffer: Long): Int

    internal fun getPtsNativeInternal(nativeBuffer: Long): Long = getPtsNative(nativeBuffer)

    private external fun getPtsNative(nativeBuffer: Long): Long

    internal fun getVideoFrameTypeNativeInternal(nativeBuffer: Long): Int = getVideoFrameTypeNative(nativeBuffer)

    private external fun getVideoFrameTypeNative(nativeBuffer: Long): Int

    internal fun getVideoFrameRgbaBytesNativeInternal(nativeBuffer: Long, bytes: ByteArray) = getVideoFrameRgbaBytesNative(nativeBuffer, bytes)

    private external fun getVideoFrameRgbaBytesNative(nativeBuffer: Long, bytes: ByteArray)

    internal fun getVideoFrameRgbaSizeNativeInternal(nativeBuffer: Long): Int = getVideoFrameRgbaSizeNative(nativeBuffer)

    private external fun getVideoFrameRgbaSizeNative(nativeBuffer: Long): Int

    internal fun getVideoFrameYSizeNativeInternal(nativeBuffer: Long): Int = getVideoFrameYSizeNative(nativeBuffer)

    private external fun getVideoFrameYSizeNative(nativeBuffer: Long): Int

    internal fun getVideoFrameYBytesNativeInternal(nativeBuffer: Long, bytes: ByteArray) = getVideoFrameYBytesNative(nativeBuffer, bytes)

    private external fun getVideoFrameYBytesNative(nativeBuffer: Long, bytes: ByteArray)

    internal fun getVideoFrameUSizeNativeInternal(nativeBuffer: Long): Int = getVideoFrameUSizeNative(nativeBuffer)

    private external fun getVideoFrameUSizeNative(nativeBuffer: Long): Int

    internal fun getVideoFrameUBytesNativeInternal(nativeBuffer: Long, bytes: ByteArray) = getVideoFrameUBytesNative(nativeBuffer, bytes)

    private external fun getVideoFrameUBytesNative(nativeBuffer: Long, bytes: ByteArray)

    internal fun getVideoFrameVSizeNativeInternal(nativeBuffer: Long): Int = getVideoFrameVSizeNative(nativeBuffer)

    private external fun getVideoFrameVSizeNative(nativeBuffer: Long): Int

    internal fun getVideoFrameVBytesNativeInternal(nativeBuffer: Long, bytes: ByteArray) = getVideoFrameVBytesNative(nativeBuffer, bytes)

    private external fun getVideoFrameVBytesNative(nativeBuffer: Long, bytes: ByteArray)

    internal fun getVideoFrameUVSizeNativeInternal(nativeBuffer: Long): Int = getVideoFrameUVSizeNative(nativeBuffer)

    private external fun getVideoFrameUVSizeNative(nativeBuffer: Long): Int

    internal fun getVideoFrameUVBytesNativeInternal(nativeBuffer: Long, bytes: ByteArray) = getVideoFrameUVBytesNative(nativeBuffer, bytes)

    private external fun getVideoFrameUVBytesNative(nativeBuffer: Long, bytes: ByteArray)

    internal fun getAudioFrameBytesNativeInternal(nativeBuffer: Long, bytes: ByteArray) = getAudioFrameBytesNative(nativeBuffer, bytes)

    private external fun getAudioFrameBytesNative(nativeBuffer: Long, bytes: ByteArray)

    internal fun getAudioFrameSizeNativeInternal(nativeBuffer: Long): Int = getAudioFrameSizeNative(nativeBuffer)

    private external fun getAudioFrameSizeNative(nativeBuffer: Long): Int

    internal fun freeDecodeDataNativeInternal(nativeBuffer: Long) {
        freeDecodeDataNative(nativeBuffer)
    }

    private external fun freeDecodeDataNative(nativeBuffer: Long)

    private external fun resetNative(nativePlayer: Long): Int

    internal fun decodeNativeInternal(nativePlayer: Long, nativeBuffer: Long): Int {
        return decodeNative(nativePlayer, nativeBuffer)
    }

    private external fun decodeNative(nativePlayer: Long, nativeBuffer: Long): Int

    internal fun seekToNativeInternal(nativePlayer: Long, videoNativeBuffer: Long, targetPtsInMillis: Long): Int = seekToNative(nativePlayer, videoNativeBuffer, targetPtsInMillis)

    private external fun seekToNative(nativePlayer: Long, videoNativeBuffer: Long, targetPtsInMillis: Long): Int

    private external fun releaseNative(nativePlayer: Long)

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
        const val TAG = "tMediaPlayer"
        private val callbackExecutor by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "tMediaPlayerCallbackThread")
            }
        }
    }
}