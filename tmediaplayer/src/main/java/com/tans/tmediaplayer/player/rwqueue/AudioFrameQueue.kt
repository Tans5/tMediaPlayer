package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.AUDIO_FRAME_QUEUE_SIZE
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicInteger

internal class AudioFrameQueue(private val player: tMediaPlayer) : BaseReadWriteQueue<AudioFrame>() {

    override val maxQueueSize: Int = AUDIO_FRAME_QUEUE_SIZE

    override fun allocBuffer(): AudioFrame {
        val nativeFrame = player.allocAudioBufferInternal()
        frameSize.incrementAndGet()
        tMediaPlayerLog.d(TAG) { "Alloc new audio frame, size=${frameSize.get()}" }
        return AudioFrame(nativeFrame)
    }

    override fun recycleBuffer(b: AudioFrame) {
        player.releaseAudioBufferInternal(b.nativeFrame)
        frameSize.decrementAndGet()
        tMediaPlayerLog.d(TAG) { "Recycle audio frame, size=${frameSize.get()}" }
    }

    /**
     * Need update serial and eof.
     */
    override fun enqueueReadable(b: AudioFrame) {
        b.pts = player.getAudioPtsInternal(b.nativeFrame)
        b.duration = player.getAudioDurationInternal(b.nativeFrame)
        super.enqueueReadable(b)
    }

    override fun dequeueWritable(): AudioFrame? {
        return super.dequeueWritable()?.apply { reset() }
    }

    override fun dequeueWriteableForce(): AudioFrame {
        return super.dequeueWriteableForce().apply { reset() }
    }

    companion object {
        private const val TAG = "AudioFrameQueue"
        private val frameSize = AtomicInteger()
    }
}