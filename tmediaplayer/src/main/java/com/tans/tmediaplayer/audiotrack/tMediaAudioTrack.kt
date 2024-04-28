package com.tans.tmediaplayer.audiotrack

import androidx.annotation.Keep
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.OptResult
import com.tans.tmediaplayer.player.toOptResult
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
@Keep
class tMediaAudioTrack(queueBufferSize: Int, private val audioTrackQueueCallback: Runnable) {

    private val nativeAudioTrack: AtomicReference<Long?> = AtomicReference(null)

    init {
        val nativeAudioTrack = createAudioTrackNative()
        val result = prepareNative(nativeAudioTrack, queueBufferSize).toOptResult()
        if (result != OptResult.Success) {
            releaseNative(nativeAudioTrack)
            MediaLog.e(TAG, "Prepare audio track fail.")
        } else {
            MediaLog.d(TAG, "Prepare audio track success.")
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
            MediaLog.e(TAG, "Enqueue buffer fail.")
        }
        return result
    }

    fun clearBuffers(): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.get()
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            clearBuffersNative(nativeAudioTrack).toOptResult()
        }
        if (result != OptResult.Success) {
            MediaLog.e(TAG, "Clear buffers fail.")
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
            MediaLog.e(TAG, "Play fail.")
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
            MediaLog.e(TAG, "Pause fail.")
        }
        return result
    }

    fun release(): OptResult {
        val nativeAudioTrack = this.nativeAudioTrack.getAndSet(null)
        val result = if (nativeAudioTrack == null) {
            OptResult.Fail
        } else {
            releaseNative(nativeAudioTrack)
            MediaLog.d(TAG, "Release audio track.")
            OptResult.Success
        }
        return result
    }

    private external fun createAudioTrackNative(): Long

    private external fun prepareNative(nativeAudioTrack: Long, bufferQueueSize: Int): Int

    private external fun enqueueBufferNative(nativeAudioTrack: Long, nativeBuffer: Long): Int

    private external fun clearBuffersNative(nativeAudioTrack: Long): Int

    private external fun playNative(nativeAudioTrack: Long): Int

    private external fun pauseNative(nativeAudioTrack: Long): Int

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