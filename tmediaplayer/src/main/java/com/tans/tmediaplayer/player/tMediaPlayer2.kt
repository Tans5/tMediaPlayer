package com.tans.tmediaplayer.player

import androidx.annotation.Keep
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.model.AudioSampleFormat
import com.tans.tmediaplayer.player.model.AudioStreamInfo
import com.tans.tmediaplayer.player.model.FFmpegCodec
import com.tans.tmediaplayer.player.model.MediaInfo
import com.tans.tmediaplayer.player.model.VideoPixelFormat
import com.tans.tmediaplayer.player.model.VideoStreamInfo
import com.tans.tmediaplayer.player.render.tMediaPlayerView
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
@Keep
class tMediaPlayer2(
    private val audioOutputChannel: AudioChannel = AudioChannel.Stereo,
    private val audioOutputSampleRate: AudioSampleRate = AudioSampleRate.Rate48000,
    private val audioOutputSampleBitDepth: AudioSampleBitDepth = AudioSampleBitDepth.SixteenBits,
    private val enableVideoHardwareDecoder: Boolean = true,
) : IPlayer {

    private val listener: AtomicReference<tMediaPlayerListener?> by lazy {
        AtomicReference(null)
    }

    private val state: AtomicReference<tMediaPlayerState> by lazy {
        AtomicReference(tMediaPlayerState.NoInit)
    }

    private val audioPacketQueue: PacketQueue by lazy {
        PacketQueue(this)
    }

    private val videoPacketQueue: PacketQueue by lazy {
        PacketQueue(this)
    }


    // region public methods
    @Synchronized
    override fun prepare(file: String): OptResult {
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
        audioPacketQueue.flushReadableBuffer()
        videoPacketQueue.flushReadableBuffer()
        val nativePlayer = createPlayerNative()
        val result = prepareNative(
            nativePlayer = nativePlayer,
            file = file,
            requestHw = enableVideoHardwareDecoder,
            targetAudioChannels = audioOutputChannel.channel,
            targetAudioSampleRate = audioOutputSampleRate.rate,
            targetAudioSampleBitDepth = audioOutputSampleBitDepth.depth
        ).toOptResult()
        dispatchProgress(0L)
        if (result == OptResult.Success) {
            // Load media file success.
            val mediaInfo = getMediaInfo(nativePlayer)
            MediaLog.d(tMediaPlayer.TAG, "Prepare player success: $mediaInfo")
            dispatchNewState(tMediaPlayerState.Prepared(mediaInfo))
        } else {
            // Load media file fail.
            releaseNative(nativePlayer)
            MediaLog.e(tMediaPlayer.TAG, "Prepare player fail.")
            dispatchNewState(tMediaPlayerState.Error("Prepare player fail."))
        }
        return result
    }

    @Synchronized
    override fun play(): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun pause(): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun seekTo(position: Long): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun stop(): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun release(): OptResult {
        val lastState = getState()
        if (lastState == tMediaPlayerState.NoInit || lastState == tMediaPlayerState.Released) {
            return OptResult.Fail
        }
        val mediaInfo = getMediaInfo()
        if (mediaInfo != null) {
            releaseNative(mediaInfo.nativePlayer)
        }
        dispatchNewState(tMediaPlayerState.Released)
        listener.set(null)
        audioPacketQueue.release()
        videoPacketQueue.release()
        return OptResult.Success
    }

    override fun getProgress(): Long {
        TODO("Not yet implemented")
    }

    override fun getState(): tMediaPlayerState = state.get()

    override fun getMediaInfo(): MediaInfo? {
        return getMediaInfoByState(getState())
    }

    override fun setListener(l: tMediaPlayerListener?) {
        listener.set(l)
        l?.onPlayerState(getState())
    }

    override fun attachPlayerView(view: tMediaPlayerView?) {
        TODO("Not yet implemented")
    }
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
            val sampleFormatId = audioSampleFmtNative(nativePlayer)
            AudioStreamInfo(
                audioChannels = audioChannelsNative(nativePlayer),
                audioSimpleRate = audioSampleRateNative(nativePlayer),
                audioPerSampleBytes = audioPerSampleBytesNative(nativePlayer),
                audioDuration = audioDurationNative(nativePlayer),
                audioCodec = FFmpegCodec.entries.find { it.codecId == codecId } ?: FFmpegCodec.UNKNOWN,
                audioBitrate = audioBitrateNative(nativePlayer),
                audioSampleBitDepth = audioSampleBitDepthNative(nativePlayer),
                audioSampleFormat = AudioSampleFormat.entries.find { it.formatId == sampleFormatId } ?: AudioSampleFormat.UNKNOWN
            )
        } else {
            null
        }
        val videoStreamInfo: VideoStreamInfo? = if (containVideoStreamNative(nativePlayer)) {
            val codecId = videoCodecIdNative(nativePlayer)
            val pixelFormatId = videoPixelFmtNative(nativePlayer)
            VideoStreamInfo(
                videoWidth = videoWidthNative(nativePlayer),
                videoHeight = videoHeightNative(nativePlayer),
                videoFps = videoFpsNative(nativePlayer),
                videoDuration = videoDurationNative(nativePlayer),
                videoCodec = FFmpegCodec.entries.find { it.codecId == codecId } ?: FFmpegCodec.UNKNOWN,
                videoBitrate = videoBitrateNative(nativePlayer),
                videoPixelBitDepth = videoPixelBitDepthNative(nativePlayer),
                videoPixelFormat = VideoPixelFormat.entries.find { it.formatId == pixelFormatId } ?: VideoPixelFormat.UNKNOWN
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

    private fun dispatchNewState(s: tMediaPlayerState) {
        val lastState = getState()
        if (lastState != s) {
            state.set(s)
            callbackExecutor.execute {
                listener.get()?.onPlayerState(s)
            }
        }
    }

    internal fun dispatchProgress(progress: Long) {
        val state = getState()
        if (state !is tMediaPlayerState.PlayEnd &&
            state !is tMediaPlayerState.Error &&
            state !is tMediaPlayerState.Stopped &&
            state !is tMediaPlayerState.Seeking &&
            state !is tMediaPlayerState.NoInit) {
            val info = getMediaInfo()
            if (info != null) {
                callbackExecutor.execute {
                    listener.get()?.onProgressUpdate(progress, info.duration)
                }
            }
        } else {
            MediaLog.e(TAG, "Ignore progress update, because of state: $state")
        }
    }
    // endregion

    // region Native player control methods.
    private external fun createPlayerNative(): Long

    private external fun prepareNative(
        nativePlayer: Long,
        file: String,
        requestHw: Boolean,
        targetAudioChannels: Int,
        targetAudioSampleRate: Int,
        targetAudioSampleBitDepth: Int): Int

    private external fun releaseNative(nativePlayer: Long)
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

    private external fun videoBitrateNative(nativePlayer: Long): Int

    private external fun videoPixelBitDepthNative(nativePlayer: Long): Int

    private external fun videoPixelFmtNative(nativePlayer: Long): Int


    private external fun videoFpsNative(nativePlayer: Long): Double

    private external fun videoDurationNative(nativePlayer: Long): Long

    private external fun videoCodecIdNative(nativePlayer: Long): Int
    // endregion

    // region Native audio stream info
    private external fun audioChannelsNative(nativePlayer: Long): Int

    private external fun audioPerSampleBytesNative(nativePlayer: Long): Int

    private external fun audioBitrateNative(nativePlayer: Long): Int

    private external fun audioSampleBitDepthNative(nativePlayer: Long): Int

    private external fun audioSampleFmtNative(nativePlayer: Long): Int

    private external fun audioSampleRateNative(nativePlayer: Long): Int

    private external fun audioDurationNative(nativePlayer: Long): Long

    private external fun audioCodecIdNative(nativePlayer: Long): Int
    // endregion

    // region Native packet buffer
    internal fun allocPacketInternal(): Long = allocPacketNative()

    private external fun allocPacketNative(): Long
    internal fun getPacketPtsInternal(nativeBuffer: Long): Long = getPacketPtsNative(nativeBuffer)
    private external fun getPacketPtsNative(nativeBuffer: Long): Long
    internal fun getPacketDurationInternal(nativeBuffer: Long): Long = getPacketDurationNative(nativeBuffer)
    private external fun getPacketDurationNative(nativeBuffer: Long): Long
    internal fun getPacketBytesSizeInternal(nativeBuffer: Long): Int = getPacketBytesSizeNative(nativeBuffer)
    private external fun getPacketBytesSizeNative(nativeBuffer: Long): Int
    internal fun releasePacketInternal(nativeBuffer: Long) = releasePacketNative(nativeBuffer)
    private external fun releasePacketNative(nativeBuffer: Long)
    // endregion

    companion object {
        private const val TAG = "tMediaPlayer"

        init {
            System.loadLibrary("tmediaplayer2")
        }

        private val callbackExecutor by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "tMediaPlayerCallbackThread")
            }
        }
    }
}