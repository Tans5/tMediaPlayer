package com.tans.tmediaplayer

import android.os.SystemClock
import androidx.annotation.Keep
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
        tMediaPlayerBufferManager(this, 15)
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
            is tMediaPlayerState.PlayEnd -> state.play()
            is tMediaPlayerState.Playing -> null
            is tMediaPlayerState.Prepared -> state.play()
            is tMediaPlayerState.Stopped -> state.play()
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

    internal fun handleSeekingBuffer(b: tMediaPlayerBufferManager.Companion.MediaBuffer) {
        synchronized(b) {
            val s = getState()
            if (s == tMediaPlayerState.Released) { return@synchronized }
            val pts = getPtsNativeInternal(b.nativeBuffer)
            basePts.set(pts)
            ptsBaseTime.set(SystemClock.uptimeMillis())
            dispatchProgress(pts)
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

    internal fun calculateRenderDelay(pts: Long): Long {
        val ptsLen = pts - basePts.get()
        val timeLen = SystemClock.uptimeMillis() - ptsBaseTime.get()
        return max(0, ptsLen - timeLen)
    }

    internal fun dispatchProgress(progress: Long) {
        val info = getMediaInfo()
        val lp = lastUpdateProgress.get()
        this.progress.set(progress)
        if (info != null && abs(progress - lp) > 200) {
            lastUpdateProgress.set(progress)
            listener.get()?.onProgressUpdate(progress, info.duration)
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
            listener.get()?.onPlayerState(s)
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

    internal fun getVideoFrameBytesNativeInternal(nativeBuffer: Long): ByteArray = getVideoFrameBytesNative(nativeBuffer)

    private external fun getVideoFrameBytesNative(nativeBuffer: Long): ByteArray

    internal fun getAudioFrameBytesNativeInternal(nativeBuffer: Long): ByteArray = getAudioFrameBytesNative(nativeBuffer)

    private external fun getAudioFrameBytesNative(nativeBuffer: Long): ByteArray

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
    }
}