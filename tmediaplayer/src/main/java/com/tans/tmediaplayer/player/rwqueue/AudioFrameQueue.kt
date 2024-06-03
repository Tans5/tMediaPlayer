package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.tMediaPlayer2
import java.util.concurrent.atomic.AtomicInteger

internal class AudioFrameQueue(private val player: tMediaPlayer2) : BaseReadWriteQueue<AudioFrame>() {

    override val maxQueueSize: Int = 16

    override fun allocBuffer(): AudioFrame {
        val nativeFrame = player.allocAudioBufferInternal()
        MediaLog.d(TAG, "Alloc new audio frame, size=${frameSize.incrementAndGet()}")
        return AudioFrame(nativeFrame)
    }

    override fun recycleBuffer(b: AudioFrame) {
        player.releaseAudioBufferInternal(b.nativeFrame)
        MediaLog.d(TAG, "Recycle audio frame, size=${frameSize.decrementAndGet()}")
    }

    /**
     * Need update serial and eof.
     */
    override fun enqueueReadable(b: AudioFrame) {
        b.pts = player.getAudioPtsInternal(b.nativeFrame)
        b.duration = player.getAudioDurationInternal(b.nativeFrame)
        super.enqueueReadable(b)
    }

    override fun enqueueWritable(b: AudioFrame) {
        b.pts = 0
        b.duration = 0
        b.serial = 0
        b.isEof = false
        super.enqueueWritable(b)
    }

    companion object {
        private const val TAG = "AudioFrameQueue"
        private val frameSize = AtomicInteger()
    }
}