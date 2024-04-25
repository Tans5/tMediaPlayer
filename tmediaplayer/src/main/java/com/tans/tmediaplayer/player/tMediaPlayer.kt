package com.tans.tmediaplayer.player

import android.os.SystemClock
import androidx.annotation.Keep
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.model.AudioStreamInfo
import com.tans.tmediaplayer.player.model.FFmpegCodec
import com.tans.tmediaplayer.player.model.MediaInfo
import com.tans.tmediaplayer.player.model.VideoStreamInfo
import com.tans.tmediaplayer.player.render.tMediaPlayerView
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
        tMediaPlayerBufferManager(this)
    }

    private val decoder: tMediaPlayerDecoder by lazy {
        tMediaPlayerDecoder(this, bufferManager)
    }

    private val renderer: tMediaPlayerRenderer by lazy {
        tMediaPlayerRenderer(this, bufferManager)
    }

    // Play progress.
    private val progress: AtomicLong by lazy {
        AtomicLong(0)
    }

    // Base time, use to compute frame render time.
    private val ptsBaseTime: AtomicLong by lazy {
        AtomicLong(0)
    }

    // Base frame's pts, use to compute frame render time.
    private val basePts: AtomicLong by lazy {
        AtomicLong(0)
    }

    // Last notify to observer progress.
    private val lastUpdateProgress: AtomicLong by lazy {
        AtomicLong(0L)
    }

    // region Player public methods
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
            // Release last nativePlayer.
            releaseNative(lastMediaInfo.nativePlayer)
        }
        dispatchNewState(tMediaPlayerState.NoInit)
        resetProgressAndBaseTime()
        bufferManager.prepare()
        // Clear last render data.
        bufferManager.clearRenderData()
        decoder.prepare()
        renderer.prepare()
        // Clear last waiting to render data.
        renderer.audioTrackFlush()
        renderer.removeRenderMessages()


        if (File(file).let { it.isFile && it.canRead() }) {
            // Create native player.
            val nativePlayer = createPlayerNative()
            // Load media file by native player.
            val result = prepareNative(nativePlayer, file, requestHw, 2).toOptResult()
            dispatchProgress(0L, true)
            if (result == OptResult.Success) {
                // Load media file success.
                val mediaInfo = getMediaInfo(nativePlayer)
                MediaLog.d(TAG, "Prepare player success: $mediaInfo")
                dispatchNewState(tMediaPlayerState.Prepared(mediaInfo))
            } else {
                // Load media file fail.
                releaseNative(nativePlayer)
                MediaLog.e(TAG, "Prepare player fail.")
                dispatchNewState(tMediaPlayerState.Error("Prepare player fail."))
            }
            return result
        } else {
            MediaLog.e(TAG, "$file can't read.")
            dispatchNewState(tMediaPlayerState.Error("$file can't read."))
            return OptResult.Fail
        }
    }

    /**
     * Start to play.
     */
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
            renderer.render()
            renderer.audioTrackPlay()
            ptsBaseTime.set(SystemClock.uptimeMillis())
            basePts.set(getProgress())
            dispatchNewState(playingState)
            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for play() method.")
            OptResult.Fail
        }
    }

    /**
     * Pause play.
     */
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
            renderer.pause()
            renderer.audioTrackPause()
            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for pause() method.")
            OptResult.Fail
        }
    }

    /**
     * Do seek.
     */
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
                renderer.pause()
                renderer.audioTrackPause()
                renderer.removeRenderMessages()
                renderer.audioTrackFlush()
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


    /**
     * Stop play.
     */
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
            renderer.pause()
            renderer.audioTrackFlush()
            renderer.removeRenderMessages()
            resetProgressAndBaseTime()
            bufferManager.clearRenderData()
            // Reset native player decode progress.
            resetNative(stopState.mediaInfo.nativePlayer)
            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for stop() method.")
            OptResult.Fail
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        renderer.attachPlayerView(view)
    }

    fun getMediaInfo(): MediaInfo? {
        return getMediaInfoByState(getState())
    }

    fun setListener(l: tMediaPlayerListener?) {
        listener.set(l)
        l?.onPlayerState(getState())
    }

    /**
     * Release player.
     */
    @Synchronized
    fun release(): OptResult {
        val lastState = getState()
        if (lastState == tMediaPlayerState.NoInit || lastState == tMediaPlayerState.Released) {
            return OptResult.Fail
        }
        decoder.release()
        renderer.release()
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
    // endregion


    // region Player internal methods.
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

    private fun getMediaInfo(nativePlayer: Long): MediaInfo {
        val metadata = mutableMapOf<String, String>()
        val metaDataArray = getMetadataNative(nativePlayer)
        repeat(metaDataArray.size / 2) {
            val key = metaDataArray[it * 2]
            val value = metaDataArray[it * 2 + 1]
            metadata[key] = value
        }
        val audioStreamInfo: AudioStreamInfo? = if (containAudioStreamNative(nativePlayer)) {
            val codecId = audioCodecIdNative(nativePlayer)
            AudioStreamInfo(
                audioChannels = audioChannelsNative(nativePlayer),
                audioSimpleRate = audioSampleRateNative(nativePlayer),
                audioPerSampleBytes = audioPerSampleBytesNative(nativePlayer),
                audioDuration = audioDurationNative(nativePlayer),
                audioCodec = FFmpegCodec.entries.find { it.codecId == codecId } ?: FFmpegCodec.UNKNOWN
            )
        } else {
            null
        }
        val videoStreamInfo: VideoStreamInfo? = if (containVideoStreamNative(nativePlayer)) {
            val codecId = videoCodecIdNative(nativePlayer)
            VideoStreamInfo(
                videoWidth = videoWidthNative(nativePlayer),
                videoHeight = videoHeightNative(nativePlayer),
                videoFps = videoFpsNative(nativePlayer),
                videoDuration = videoDurationNative(nativePlayer),
                videoCodec = FFmpegCodec.entries.find { it.codecId == codecId } ?: FFmpegCodec.UNKNOWN
            )
        } else {
            null
        }
        return MediaInfo(
            nativePlayer = nativePlayer,
            duration = durationNative(nativePlayer),
            metadata = metadata,
            audioStreamInfo = audioStreamInfo,
            videoStreamInfo = videoStreamInfo
        )
    }

    /**
     * Last frame rendered, no buffer to render and no buffer to decode.
     */
    internal fun dispatchPlayEnd() {
        val s = getState()
        if (s is tMediaPlayerState.Playing) {
            dispatchNewState(s.playEnd())
        }
        resetProgressAndBaseTime()
        bufferManager.clearRenderData()
        renderer.removeRenderMessages()
        val np = getMediaInfo()?.nativePlayer
        if (np != null) {
            // Reset native player decode progress.
            resetNative(np)
        }
    }

    /**
     * Decoder do seeking finished.
     */
    internal fun handleSeekingBuffer(
        videoBuffer: tMediaPlayerBufferManager.Companion.MediaBuffer,
        audioBuffer: tMediaPlayerBufferManager.Companion.MediaBuffer,
        result: OptResult,
        targetProgress: Long) {
        val s = getState()
        if (s == tMediaPlayerState.Released) { return }
        when (result) {
            // Seeking success.
            OptResult.Success -> {
                // Get seek buffer's pts.
                val videoBufferResult = getBufferResultNative(videoBuffer.nativeBuffer).toOptResult()
                val audioBufferResult = getBufferResultNative(audioBuffer.nativeBuffer).toOptResult()
                val seekPts = when {
                    // Video and audio both success.
                    videoBufferResult == OptResult.Success && audioBufferResult == OptResult.Success -> {
                        val videoPts = getPtsNative(videoBuffer.nativeBuffer)
                        val audioPts = getPtsNative(audioBuffer.nativeBuffer)
                        MediaLog.d(TAG, "Seek audioPts=$audioPts, videoPts=$videoPts")
                        min(videoPts, audioPts)
                    }
                    // Only video success
                    videoBufferResult == OptResult.Success -> {
                        val videoPts = getPtsNative(videoBuffer.nativeBuffer)
                        MediaLog.d(TAG, "Seek videoPts=$videoPts, but audio seek fail.")
                        videoPts
                    }
                    // Only audio success
                    else -> {
                        val audioPts = getPtsNative(audioBuffer.nativeBuffer)
                        MediaLog.d(TAG, "Seek audioPts=$audioPts, but video seek fail.")
                        audioPts
                    }
                }
                MediaLog.d(TAG, "Seek targetPts=$targetProgress, seekPts=$seekPts")

                // Remove waiting to render data.
                renderer.removeRenderMessages()
                // Clear audio track cache.
                renderer.audioTrackFlush()
                // Clear last render data.
                bufferManager.clearRenderData()
                // Update base pts.
                basePts.set(seekPts)
                // Update base time.
                ptsBaseTime.set(SystemClock.uptimeMillis())
                dispatchProgress(seekPts, true)
                if (isLastFrameBufferNative(audioBuffer.nativeBuffer)) {
                    // Current seek frame is last fame.
                    val info = getMediaInfo()
                    if (info != null) {
                        dispatchNewState(tMediaPlayerState.PlayEnd(info))
                        resetNative(info.nativePlayer)
                    }
                    resetProgressAndBaseTime()
                    bufferManager.enqueueAudioNativeEncodeBuffer(audioBuffer)
                    bufferManager.enqueueVideoNativeEncodeBuffer(videoBuffer)
                } else {
                    // Not last frame.
                    if (s is tMediaPlayerState.Seeking) {
                        val lastState = s.lastState
                        if (lastState is tMediaPlayerState.Playing) {
                            // If last state is playing, notify to decoder and renderer.
                            decoder.decode()
                            renderer.render()
                            renderer.audioTrackPlay()
                            bufferManager.enqueueVideoNativeEncodeBuffer(videoBuffer)
                            bufferManager.enqueueAudioNativeEncodeBuffer(audioBuffer)
                        } else {
                            // Notify renderer to handle seeking buffer, if current state not playing.
                            renderer.handleSeekingBuffer(videoBuffer = videoBuffer, audioBuffer = audioBuffer)
                        }
                        dispatchNewState(lastState)
                    } else {
                        bufferManager.enqueueVideoNativeEncodeBuffer(videoBuffer)
                        bufferManager.enqueueAudioNativeEncodeBuffer(audioBuffer)
                        MediaLog.e(TAG, "Expect seeking state, but now is $s")
                    }
                }
            }
            OptResult.Fail -> {
                // Seeking fail.
                dispatchNewState(tMediaPlayerState.Error("Seek error."))
                bufferManager.enqueueVideoNativeEncodeBuffer(videoBuffer)
                bufferManager.enqueueAudioNativeEncodeBuffer(audioBuffer)
            }
        }
    }

    /**
     * Calculate [pts] frame render delay.
     */
    internal fun calculateRenderDelay(pts: Long): Long {
        val ptsLen = pts - basePts.get()
        val timeLen = SystemClock.uptimeMillis() - ptsBaseTime.get()
        return max(0, ptsLen - timeLen)
    }

    internal fun dispatchProgress(progress: Long, updateForce: Boolean = false) {
        val state = getState()
        if (state !is tMediaPlayerState.PlayEnd && state !is tMediaPlayerState.Error && state !is tMediaPlayerState.Stopped) {
            val currentProcess = this.progress.get()
            if (currentProcess <= progress || updateForce) {
                val info = getMediaInfo()
                val lp = lastUpdateProgress.get()
                this.progress.set(progress)
                if (info != null && abs(progress - lp) > 80) {
                    lastUpdateProgress.set(progress)
                    callbackExecutor.execute {
                        listener.get()?.onProgressUpdate(progress, info.duration)
                    }
                }
            } else {
                MediaLog.e(TAG, "Skip update progress, updateProgress=$progress, currentProgress=$currentProcess")
            }
        }
    }

    /**
     * Contain new buffer to decode.
     */
    internal fun renderSuccess() {
        decoder.checkDecoderBufferIfWaiting()
    }

    /**
     * Contain new buffer to render.
     */
    internal fun decodeSuccess() {
        renderer.checkRenderBufferIfWaiting()
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
        progress.set(0L)
        lastUpdateProgress.set(0L)
        basePts.set(0L)
        ptsBaseTime.set(0L)
    }
    // endregion


    // region Native player control methods.
    private external fun createPlayerNative(): Long

    private external fun prepareNative(nativePlayer: Long, file: String, requestHw: Boolean, targetAudioChannels: Int): Int

    private external fun resetNative(nativePlayer: Long): Int

    internal fun decodeNativeInternal(nativePlayer: Long): Long {
        return decodeNative(nativePlayer)
    }

    private external fun decodeNative(nativePlayer: Long): Long

    internal fun seekToNativeInternal(nativePlayer: Long, videoNativeBuffer: Long, audioNativeBuffer: Long, targetPtsInMillis: Long): Int = seekToNative(nativePlayer, videoNativeBuffer, audioNativeBuffer, targetPtsInMillis)

    private external fun seekToNative(nativePlayer: Long, videoNativeBuffer: Long, audioNativeBuffer: Long, targetPtsInMillis: Long): Int

    private external fun releaseNative(nativePlayer: Long)
    // endregion


    // region Native buffer alloc and free.
    internal fun allocAudioDecodeDataNativeInternal(): Long {
        val bufferSize = bufferSize.incrementAndGet()
        MediaLog.d(TAG, "BufferSize: $bufferSize")
        return allocAudioDecodeDataNative()
    }

    private external fun allocAudioDecodeDataNative(): Long

    internal fun allocVideoDecodeDataNativeInternal(): Long {
        val bufferSize = bufferSize.incrementAndGet()
        MediaLog.d(TAG, "BufferSize: $bufferSize")
        return allocVideoDecodeDataNative()
    }

    private external fun allocVideoDecodeDataNative(): Long

    internal fun freeDecodeDataNativeInternal(nativeBuffer: Long) {
        val bufferSize = bufferSize.decrementAndGet()
        MediaLog.d(TAG, "BufferSize: $bufferSize")
        freeDecodeDataNative(nativeBuffer)
    }

    private external fun freeDecodeDataNative(nativeBuffer: Long)
    // endregion


    // region Native common buffer info
    internal fun getBufferResultNativeInternal(nativeBuffer: Long): Int {
        return getBufferResultNative(nativeBuffer)
    }

    private external fun getBufferResultNative(nativeBuffer: Long): Int

    internal fun getPtsNativeInternal(nativeBuffer: Long): Long = getPtsNative(nativeBuffer)

    private external fun getPtsNative(nativeBuffer: Long): Long
    // endregion


    // region Native video buffer info
    internal fun isVideoBufferNativeInternal(nativeBuffer: Long): Boolean = isVideoBufferNative(nativeBuffer)

    private external fun isVideoBufferNative(nativeBuffer: Long): Boolean

    internal fun getVideoWidthNativeInternal(nativeBuffer: Long): Int = getVideoWidthNative(nativeBuffer)

    private external fun getVideoWidthNative(nativeBuffer: Long): Int

    internal fun getVideoHeightNativeInternal(nativeBuffer: Long): Int = getVideoHeightNative(nativeBuffer)

    private external fun getVideoHeightNative(nativeBuffer: Long): Int

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
    // endregion


    // region Native audio buffer info
    internal fun isLastFrameBufferNativeInternal(nativeBuffer: Long): Boolean = isLastFrameBufferNative(nativeBuffer)

    private external fun isLastFrameBufferNative(nativeBuffer: Long): Boolean

    internal fun getAudioFrameBytesNativeInternal(nativeBuffer: Long, bytes: ByteArray) = getAudioFrameBytesNative(nativeBuffer, bytes)

    private external fun getAudioFrameBytesNative(nativeBuffer: Long, bytes: ByteArray)

    internal fun getAudioFrameSizeNativeInternal(nativeBuffer: Long): Int = getAudioFrameSizeNative(nativeBuffer)

    private external fun getAudioFrameSizeNative(nativeBuffer: Long): Int
    // endregion


    // region Native media file info
    private external fun durationNative(nativePlayer: Long): Long

    private external fun containVideoStreamNative(nativePlayer: Long): Boolean

    private external fun containAudioStreamNative(nativePlayer: Long): Boolean

    private external fun getMetadataNative(nativePlayer: Long): Array<String>
    // endregion


    // region Native video stream info
    private external fun videoWidthNative(nativePlayer: Long): Int

    private external fun videoHeightNative(nativePlayer: Long): Int

    private external fun videoFpsNative(nativePlayer: Long): Double

    private external fun videoDurationNative(nativePlayer: Long): Long

    private external fun videoCodecIdNative(nativePlayer: Long): Int
    // endregion


    // region Native audio stream info
    private external fun audioChannelsNative(nativePlayer: Long): Int

    private external fun audioPerSampleBytesNative(nativePlayer: Long): Int

    private external fun audioSampleRateNative(nativePlayer: Long): Int

    private external fun audioDurationNative(nativePlayer: Long): Long

    private external fun audioCodecIdNative(nativePlayer: Long): Int
    // endregion


    // region Native player call java
    /**
     * Call by native code, request video decode buffer on decode thread.
     */
    fun requestVideoDecodeBufferFromNative(): Long {
        return bufferManager.requestVideoNativeDecodeBufferForce()
    }

    /**
     * Call by native code, enqueue video decode buffer on decode thread.
     */
    fun enqueueVideoEncodeBufferFromNative(nativeBuffer: Long) {
        return bufferManager.enqueueVideoNativeEncodeBuffer(
            tMediaPlayerBufferManager.Companion.MediaBuffer(
                nativeBuffer
            )
        )
    }

    /**
     * Call by native code, request audio decode buffer on decode thread.
     */
    fun requestAudioDecodeBufferFromNative(): Long {
        return bufferManager.requestAudioNativeDecodeBufferForce()
    }

    /**
     * Call by native code, enqueue audio decode buffer on decode thread.
     */
    fun enqueueAudioEncodeBufferFromNative(nativeBuffer: Long) {
        return bufferManager.enqueueAudioNativeEncodeBuffer(
            tMediaPlayerBufferManager.Companion.MediaBuffer(
                nativeBuffer
            )
        )
    }
    // endregion

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

        private val bufferSize: AtomicInteger = AtomicInteger(0)
    }
}