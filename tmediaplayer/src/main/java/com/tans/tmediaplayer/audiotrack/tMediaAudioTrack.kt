package com.tans.tmediaplayer.audiotrack

import androidx.annotation.Keep
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.AudioChannel
import com.tans.tmediaplayer.player.model.AudioSampleBitDepth
import com.tans.tmediaplayer.player.model.AudioSampleRate
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.toOptResult
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
@Keep
internal class tMediaAudioTrack(
    outputChannel: AudioChannel,
    outputSampleRate: AudioSampleRate,
    outputSampleBitDepth: AudioSampleBitDepth,
    bufferQueueSize: Int,
    private val audioTrackQueueCallback: Runnable
) {

    private val nativeAudioTrack: AtomicReference<Long?> = AtomicReference(null)

    init {
        val nativeAudioTrack = createAudioTrackNative()
        val result = prepareNative(
            nativeAudioTrack = nativeAudioTrack,
            bufferQueueSize = bufferQueueSize,
            outputChannels = outputChannel.channel,
            outputSampleRate = outputSampleRate.rate,
            outputSampleBitDepth = outputSampleBitDepth.depth
            ).toOptResult()
        if (result != OptResult.Success) {
            releaseNative(nativeAudioTrack)
            tMediaPlayerLog.e(TAG) { "Prepare audio track fail." }
        } else {
            tMediaPlayerLog.d(TAG) { "Prepare audio track success." }
            this.nativeAudioTrack.set(nativeAudioTrack)
        }
    }

    fun enqueueBuffer(nativeBuffer: Long): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.get()
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            enqueueBufferNative(nativeAudioTrack, nativeBuffer).toOptResult()
        }
        if (result != OptResult.Success) {
            tMediaPlayerLog.e(TAG) { "Enqueue buffer fail." }
        }
        return result
    }

    fun getBufferQueueCount(): Int {
        val nativeAudioTrack = this.nativeAudioTrack.get()
        return if (nativeAudioTrack != null) {
            getBufferQueueCountNative(nativeAudioTrack)
        } else {
            0
        }
    }

    fun clearBuffers(): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.get()
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            clearBuffersNative(nativeAudioTrack).toOptResult()
        }
        if (result != OptResult.Success) {
            tMediaPlayerLog.e(TAG) { "Clear buffers fail." }
        }
        return result
    }

    fun play(): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.get()
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            playNative(nativeAudioTrack).toOptResult()
        }
        if (result != OptResult.Success) {
            tMediaPlayerLog.e(TAG) { "Play fail." }
        }
        return result
    }

    fun pause(): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.get()
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            pauseNative(nativeAudioTrack).toOptResult()
        }
        if (result != OptResult.Success) {
            tMediaPlayerLog.e(TAG) { "Pause fail." }
        }
        return result
    }

    fun stop(): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.get()
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            stopNative(nativeAudioTrack).toOptResult()
        }
        if (result != OptResult.Success) {
            tMediaPlayerLog.e(TAG) { "Stop fail." }
        }
        return result
    }

    fun release(): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.getAndSet(null)
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            releaseNative(nativeAudioTrack)
            tMediaPlayerLog.d(TAG) { "Release audio track." }
            OptResult.Success
        }
        return result
    }

    private external fun createAudioTrackNative(): Long

    private external fun prepareNative(nativeAudioTrack: Long, bufferQueueSize: Int, outputChannels: Int, outputSampleRate: Int, outputSampleBitDepth: Int): Int

    private external fun enqueueBufferNative(nativeAudioTrack: Long, nativeBuffer: Long): Int

    private external fun getBufferQueueCountNative(nativeAudioTrack: Long): Int

    private external fun clearBuffersNative(nativeAudioTrack: Long): Int

    private external fun playNative(nativeAudioTrack: Long): Int

    private external fun pauseNative(nativeAudioTrack: Long): Int

    private external fun stopNative(nativeAudioTrack: Long): Int

    private external fun releaseNative(nativeAudioTrack: Long)


    /**
     * For native call.
     */
    fun audioTrackQueueCallback() {
        audioTrackQueueCallback.run()
    }

    companion object {
        init {
            System.loadLibrary("tmediaaudiotrack")
        }

        private const val TAG = "tMediaAudioTrack"
    }
}