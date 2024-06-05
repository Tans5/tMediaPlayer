package com.tans.tmediaplayer.player

import androidx.annotation.Keep
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.decoder.AudioFrameDecoder
import com.tans.tmediaplayer.player.decoder.VideoFrameDecoder
import com.tans.tmediaplayer.player.model.SyncType.*
import com.tans.tmediaplayer.player.model.AudioChannel
import com.tans.tmediaplayer.player.model.AudioSampleBitDepth
import com.tans.tmediaplayer.player.model.AudioSampleFormat
import com.tans.tmediaplayer.player.model.AudioSampleRate
import com.tans.tmediaplayer.player.model.AudioStreamInfo
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.FFmpegCodec
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.model.MediaInfo
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.ReadPacketResult
import com.tans.tmediaplayer.player.model.SyncType
import com.tans.tmediaplayer.player.model.VideoPixelFormat
import com.tans.tmediaplayer.player.model.VideoStreamInfo
import com.tans.tmediaplayer.player.model.toDecodeResult
import com.tans.tmediaplayer.player.model.toImageRawType
import com.tans.tmediaplayer.player.model.toOptResult
import com.tans.tmediaplayer.player.model.toReadPacketResult
import com.tans.tmediaplayer.player.pktreader.PacketReader
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView
import com.tans.tmediaplayer.player.renderer.AudioRenderer
import com.tans.tmediaplayer.player.renderer.RendererState
import com.tans.tmediaplayer.player.renderer.VideoRenderer
import com.tans.tmediaplayer.player.rwqueue.AudioFrame
import com.tans.tmediaplayer.player.rwqueue.AudioFrameQueue
import com.tans.tmediaplayer.player.rwqueue.Packet
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.VideoFrame
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
@Keep
class tMediaPlayer(
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

    private val audioFrameQueue: AudioFrameQueue by lazy {
        AudioFrameQueue(this)
    }

    private val videoFrameQueue: VideoFrameQueue by lazy {
        VideoFrameQueue(this)
    }

    private val packetReader: PacketReader by lazy {
        PacketReader(
            player = this,
            audioPacketQueue = audioPacketQueue,
            videoPacketQueue = videoPacketQueue
        )
    }

    private val audioDecoder: AudioFrameDecoder by lazy {
        AudioFrameDecoder(
            player = this,
            audioPacketQueue = audioPacketQueue,
            audioFrameQueue = audioFrameQueue
        )
    }

    private val videoDecoder: VideoFrameDecoder by lazy {
        VideoFrameDecoder(
            player = this,
            videoPacketQueue = videoPacketQueue,
            videoFrameQueue = videoFrameQueue
        )
    }

    private val syncType: SyncType = AudioMaster

    internal val videoClock: Clock by lazy {
        Clock()
    }

    internal val audioClock: Clock by lazy {
        Clock()
    }

    internal val externalClock: Clock by lazy {
        Clock()
    }

    private val audioRenderer: AudioRenderer by lazy {
        AudioRenderer(
            outputChannel = audioOutputChannel,
            outputSampleRate = audioOutputSampleRate,
            outputSampleBitDepth = audioOutputSampleBitDepth,
            audioFrameQueue = audioFrameQueue,
            audioPacketQueue = audioPacketQueue,
            player = this
        )
    }

    private val videoRenderer: VideoRenderer by lazy {
        VideoRenderer(
            videoFrameQueue = videoFrameQueue,
            videoPacketQueue = videoPacketQueue,
            player = this
        )
    }


    // region public methods
    @Synchronized
    override fun prepare(file: String): OptResult {
        synchronized(packetReader) {
            synchronized(audioDecoder) {
                synchronized(videoDecoder) {
                    val lastState = getState()
                    if (lastState == tMediaPlayerState.Released) {
                        MediaLog.e(TAG, "Prepare fail, player has released.")
                        return OptResult.Fail
                    }
                    val lastMediaInfo = getMediaInfo()
                    dispatchNewState(tMediaPlayerState.NoInit)
                    if (lastMediaInfo != null) {
                        // Release last nativePlayer.
                        releaseNative(lastMediaInfo.nativePlayer)
                    }

                    // Flush pkt and frame queues.
                    audioPacketQueue.flushReadableBuffer()
                    videoPacketQueue.flushReadableBuffer()
                    audioFrameQueue.flushReadableBuffer()
                    videoFrameQueue.flushReadableBuffer()
                    audioRenderer.flush()

                    // Remove reader and decoders jobs.
                    packetReader.removeAllHandlerMessages()
                    audioDecoder.removeAllHandlerMessages()
                    videoDecoder.removeAllHandlerMessages()

                    // Reset clocks
                    videoClock.initClock(videoPacketQueue)
                    audioClock.initClock(audioPacketQueue)
                    externalClock.initClock(null)
                    val nativePlayer = createPlayerNative()
                    val result = prepareNative(
                        nativePlayer = nativePlayer,
                        file = file,
                        requestHw = enableVideoHardwareDecoder,
                        targetAudioChannels = audioOutputChannel.channel,
                        targetAudioSampleRate = audioOutputSampleRate.rate,
                        targetAudioSampleBitDepth = audioOutputSampleBitDepth.depth
                    ).toOptResult()
                    if (result == OptResult.Success) {
                        // Load media file success.
                        val mediaInfo = getMediaInfo(nativePlayer)
                        MediaLog.d(TAG, "Prepare player success: $mediaInfo")
                        dispatchNewState(tMediaPlayerState.Prepared(mediaInfo))

                        // Start reader and decoders
                        packetReader.requestReadPkt()
                        audioDecoder.requestDecode()
                        videoDecoder.requestDecode()

                        // Renderers do init if not.
                        audioRenderer
                        videoRenderer
                    } else {
                        // Load media file fail.
                        releaseNative(nativePlayer)
                        MediaLog.e(TAG, "Prepare player fail.")
                        dispatchNewState(tMediaPlayerState.Error("Prepare player fail."))
                    }
                    return result
                }
            }
        }

    }

    @Synchronized
    override fun play(): OptResult {
        val state = getState()
        val playingState = when (state) {
            tMediaPlayerState.NoInit -> null
            is tMediaPlayerState.Error -> null
            is tMediaPlayerState.Paused -> state.play()
            is tMediaPlayerState.Playing -> null
            is tMediaPlayerState.Prepared -> state.play()
            is tMediaPlayerState.Stopped  -> {
                packetReader.requestSeek(0L)
                state.play()
            }
            is tMediaPlayerState.PlayEnd -> {
                packetReader.requestSeek(0L)
                state.play()
            }
            is tMediaPlayerState.Seeking -> null
            tMediaPlayerState.Released -> null
        }
        return if (playingState != null) {
            MediaLog.d(TAG, "Request play.")

            playReadPacketNative(playingState.mediaInfo.nativePlayer)
            dispatchNewState(playingState)

            // Play clocks
            videoClock.play()
            audioClock.play()
            externalClock.play()

            // Play renderers
            audioRenderer.play()
            videoRenderer.play()

            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for play() method.")
            OptResult.Fail
        }
    }

    @Synchronized
    override fun pause(): OptResult {
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
            pauseReadPacketNative(pauseState.mediaInfo.nativePlayer)
            dispatchNewState(pauseState)

            // Pause clocks
            videoClock.pause()
            audioClock.pause()
            externalClock.pause()

            // Pause renders
            audioRenderer.pause()
            videoRenderer.pause()

            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for pause() method.")
            OptResult.Fail
        }
    }

    @Synchronized
    override fun seekTo(position: Long): OptResult {
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
                packetReader.requestSeek(position)
                dispatchNewState(seekingState)
                OptResult.Success
            }
        } else {
            MediaLog.e(TAG, "Wrong state: $state for seekTo() method.")
            OptResult.Fail
        }
    }

    @Synchronized
    override fun stop(): OptResult {
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
            videoClock.setClock(stopState.mediaInfo.duration, -1)
            videoClock.pause()
            audioClock.setClock(stopState.mediaInfo.duration, -1)
            audioClock.pause()
            externalClock.setClock(stopState.mediaInfo.duration, -1)
            externalClock.pause()
            audioRenderer.pause()
            videoRenderer.pause()
            dispatchProgress(stopState.mediaInfo.duration, false)
            OptResult.Success
        } else {
            MediaLog.e(TAG, "Wrong state: $state for stop() method.")
            OptResult.Fail
        }
    }

    @Synchronized
    override fun release(): OptResult {
        synchronized(packetReader) {
            synchronized(audioDecoder) {
                synchronized(videoDecoder) {
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
                    audioFrameQueue.release()
                    videoFrameQueue.release()
                    packetReader.release()
                    audioDecoder.release()
                    videoDecoder.release()
                    audioRenderer.release()
                    videoRenderer.release()
                }
            }
        }
        return OptResult.Success
    }

    override fun getProgress(): Long {
        val info = getMediaInfo()
        return if (info != null) {
            if (info.audioStreamInfo != null) {
                audioClock.getClock()
            } else if (info.videoStreamInfo != null) {
                videoClock.getClock()
            } else {
                0L
            }
        } else {
            0L
        }
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
        videoRenderer.attachPlayerView(view)
    }
    // endregion

    // region Player internal methods.

    internal fun seekResult(position: Long, result: OptResult) {
        val state = getState()
        if (result == OptResult.Success) {
            audioRenderer.flush()
            audioPacketQueue.flushReadableBuffer()
            videoPacketQueue.flushReadableBuffer()
            audioFrameQueue.flushReadableBuffer()
            videoFrameQueue.flushReadableBuffer()
            audioDecoder.requestDecode()
            videoDecoder.requestDecode()

            videoClock.setClock(position, videoPacketQueue.getSerial())
            audioClock.setClock(position, audioPacketQueue.getSerial())
            externalClock.setClock(position, audioPacketQueue.getSerial())
            dispatchProgress(position, false)
        }
        val mediaInfo = getMediaInfo()
        if (state is tMediaPlayerState.Seeking && mediaInfo != null) {
            val lastState = state.lastState
            if (result == OptResult.Success) {
                if (lastState !is tMediaPlayerState.Playing) {
                    dispatchNewState(tMediaPlayerState.Paused(mediaInfo))
                    if (mediaInfo.videoStreamInfo != null && !mediaInfo.videoStreamInfo.isAttachment) {
                        videoRenderer.requestRenderForce()
                    }
                } else {
                    dispatchNewState(lastState)
                }
            } else {
                dispatchNewState(lastState)
            }
        } else {
            MediaLog.d(TAG, "Wrong state for handing seeking result: $state")
        }
    }

    internal fun getSyncType(): SyncType {
        val mediaInfo = getMediaInfo()
        return if (mediaInfo == null) {
            ExternalClock
        } else if (syncType == VideoMaster) {
            if (mediaInfo.videoStreamInfo != null && !mediaInfo.videoStreamInfo.isAttachment) {
                VideoMaster
            } else {
                AudioMaster
            }
        } else if (syncType == AudioMaster) {
            if (mediaInfo.audioStreamInfo != null) {
                AudioMaster
            } else {
                ExternalClock
            }
        } else {
            ExternalClock
        }
    }

    internal fun getMasterClock(): Long {
        return when (getSyncType()) {
            VideoMaster -> videoClock.getClock()
            AudioMaster -> audioClock.getClock()
            ExternalClock -> externalClock.getClock()
        }
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
            ).apply {
                MediaLog.d(TAG, "Find audio stream: $this")
            }
        } else {
            MediaLog.d(TAG, "Don't find audio stream")
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
                videoPixelFormat = VideoPixelFormat.entries.find { it.formatId == pixelFormatId } ?: VideoPixelFormat.UNKNOWN,
                isAttachment = videoStreamIsAttachmentNative(nativePlayer)
            ).apply {
                MediaLog.d(TAG, "Find video stream: $this")
            }
        } else {
            MediaLog.d(TAG, "Don't find video stream")
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

    private fun dispatchProgress(progress: Long, checkState: Boolean = true) {
        val state = getState()
        if (!checkState || state is tMediaPlayerState.Playing) {
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

    internal fun readableVideoPacketReady() {
        videoDecoder.readablePacketReady()
    }
    internal fun readableAudioPacketReady() {
        audioDecoder.readablePacketReady()
    }

    internal fun writeableVideoPacketReady() {
        packetReader.writeablePacketBufferReady()
    }

    internal fun writeableAudioPacketReady() {
        packetReader.writeablePacketBufferReady()
    }

    internal fun readableVideoFrameReady() {
        videoRenderer.readableFrameReady()
    }

    internal fun readableAudioFrameReady() {
        audioRenderer.readableFrameReady()
    }

    internal fun writeableVideoFrameReady() {
        videoDecoder.writeableFrameReady()
        if (getSyncType() != AudioMaster || audioRenderer.getState() == RendererState.Eof) {
            dispatchProgress(videoClock.getClock())
        }
        checkPlayEnd()
    }

    internal fun writeableAudioFrameReady() {
        audioDecoder.writeableFrameReady()
        if (getSyncType() != VideoMaster || videoRenderer.getState() == RendererState.Eof) {
            dispatchProgress(audioClock.getClock())
        }
        checkPlayEnd()
    }

    private fun checkPlayEnd() {
        val state = getState()
        if (state is tMediaPlayerState.Playing) {
            val mediaInfo = state.mediaInfo
            if ((mediaInfo.videoStreamInfo == null || videoRenderer.getState() == RendererState.Eof) &&
                (mediaInfo.audioStreamInfo == null || audioRenderer.getState() == RendererState.Eof)
            ) {
                MediaLog.d(TAG, "Render end.")
                dispatchNewState(tMediaPlayerState.PlayEnd(mediaInfo))
                videoClock.setClock(mediaInfo.duration, videoPacketQueue.getSerial())
                videoClock.pause()
                audioClock.setClock(mediaInfo.duration, audioPacketQueue.getSerial())
                audioClock.pause()
                externalClock.setClock(mediaInfo.duration, audioPacketQueue.getSerial())
                externalClock.pause()
                audioRenderer.pause()
                videoRenderer.pause()
            }
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

    internal fun readPacketInternal(nativePlayer: Long): ReadPacketResult = readPacketNative(nativePlayer).toReadPacketResult()

    private external fun readPacketNative(nativePlayer: Long): Int

    private external fun pauseReadPacketNative(nativePlayer: Long): Int

    private external fun playReadPacketNative(nativePlayer: Long): Int

    internal fun movePacketRefInternal(nativePlayer: Long, nativePacket: Long) = movePacketRefNative(nativePlayer, nativePacket)

    private external fun movePacketRefNative(nativePlayer: Long, nativePacket: Long)

    internal fun seekToInternal(nativePlayer: Long, targetPosInMillis: Long): OptResult = seekToNative(nativePlayer, targetPosInMillis).toOptResult()

    private external fun seekToNative(nativePlayer: Long, targetPosInMillis: Long): Int

    internal fun decodeVideoInternal(nativePlayer: Long, pkt: Packet?): DecodeResult {
        return decodeVideoNative(nativePlayer, pkt?.nativePacket ?: 0L).toDecodeResult()
    }

    private external fun decodeVideoNative(nativePlayer: Long, nativeBuffer: Long): Int

    internal fun flushVideoCodecBufferInternal(nativePlayer: Long) = flushVideoCodecBufferNative(nativePlayer)

    private external fun flushVideoCodecBufferNative(nativePlayer: Long)

    internal fun moveDecodedVideoFrameToBufferInternal(nativePlayer: Long, videoFrame: VideoFrame): OptResult {
        return moveDecodedVideoFrameToBufferNative(nativePlayer, videoFrame.nativeFrame).toOptResult()
    }

    private external fun moveDecodedVideoFrameToBufferNative(nativePlayer: Long, nativeBuffer: Long): Int

    internal fun decodeAudioInternal(nativePlayer: Long, pkt: Packet?): DecodeResult {
        return decodeAudioNative(nativePlayer, pkt?.nativePacket ?: 0L).toDecodeResult()
    }

    private external fun decodeAudioNative(nativePlayer: Long, nativeBuffer: Long): Int

    internal fun flushAudioCodecBufferInternal(nativePlayer: Long) = flushAudioCodecBufferNative(nativePlayer)

    private external fun flushAudioCodecBufferNative(nativePlayer: Long)

    internal fun moveDecodedAudioFrameToBufferInternal(nativePlayer: Long, audioFrame: AudioFrame): OptResult {
        return moveDecodedAudioFrameToBufferNative(nativePlayer, audioFrame.nativeFrame).toOptResult()
    }

    private external fun moveDecodedAudioFrameToBufferNative(nativePlayer: Long, nativeBuffer: Long): Int

    private external fun releaseNative(nativePlayer: Long)
    // endregion

    // region Native media file info
    private external fun durationNative(nativePlayer: Long): Long

    private external fun containVideoStreamNative(nativePlayer: Long): Boolean

    private external fun containAudioStreamNative(nativePlayer: Long): Boolean

    private external fun getMetadataNative(nativePlayer: Long): Array<String>
    // endregion

    // region Native video stream info

    private external fun videoStreamIsAttachmentNative(nativeBuffer: Long): Boolean

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

    // region Native video buffer
    internal fun allocVideoBufferInternal(): Long = allocVideoBufferNative()

    private external fun allocVideoBufferNative(): Long

    internal fun getVideoPtsInternal(nativeBuffer: Long): Long = getVideoPtsNative(nativeBuffer)

    private external fun getVideoPtsNative(nativeBuffer: Long): Long

    internal fun getVideoDurationInternal(nativeBuffer: Long): Long = getVideoDurationNative(nativeBuffer)

    private external fun getVideoDurationNative(nativeBuffer: Long): Long

    internal fun getVideoWidthNativeInternal(nativeBuffer: Long): Int = getVideoWidthNative(nativeBuffer)

    private external fun getVideoWidthNative(nativeBuffer: Long): Int

    internal fun getVideoHeightNativeInternal(nativeBuffer: Long): Int = getVideoHeightNative(nativeBuffer)

    private external fun getVideoHeightNative(nativeBuffer: Long): Int

    internal fun getVideoFrameTypeNativeInternal(nativeBuffer: Long): ImageRawType = getVideoFrameTypeNative(nativeBuffer).toImageRawType()

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

    internal fun releaseVideoBufferInternal(nativeBuffer: Long) = releaseVideoBufferNative(nativeBuffer)

    private external fun releaseVideoBufferNative(nativeBuffer: Long)
    // endregion

    // region Native audio buffer
    internal fun allocAudioBufferInternal(): Long = allocAudioBufferNative()

    private external fun allocAudioBufferNative(): Long

    internal fun getAudioPtsInternal(nativeBuffer: Long): Long = getAudioPtsNative(nativeBuffer)

    private external fun getAudioPtsNative(nativeBuffer: Long): Long

    internal fun getAudioDurationInternal(nativeBuffer: Long): Long = getAudioDurationNative(nativeBuffer)

    private external fun getAudioDurationNative(nativeBuffer: Long): Long

    private external fun getAudioFrameBytesNative(nativeBuffer: Long, bytes: ByteArray)

    private external fun getAudioFrameSizeNative(nativeBuffer: Long): Int

    internal fun releaseAudioBufferInternal(nativeBuffer: Long) = releaseAudioBufferNative(nativeBuffer)

    private external fun releaseAudioBufferNative(nativeBuffer: Long)
    // endregion


    companion object {
        private const val TAG = "tMediaPlayer"

        init {
            System.loadLibrary("tmediaplayer")
        }

        private val callbackExecutor by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "tMediaPlayerCallbackThread")
            }
        }
    }
}