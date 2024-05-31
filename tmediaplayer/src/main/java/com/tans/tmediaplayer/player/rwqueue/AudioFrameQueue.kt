package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.player.tMediaPlayer2

internal class AudioFrameQueue(private val player: tMediaPlayer2) : BaseReadWriteQueue<AudioFrame>() {

//    var lastDecodedAudioFrame: LastDecodedAudioFrame? = null
//        private set

    override val maxQueueSize: Int = 16

    override fun allocBuffer(): AudioFrame {
        val nativeFrame = player.allocAudioBufferInternal()
        return AudioFrame(nativeFrame)
    }

    override fun recycleBuffer(b: AudioFrame) {
        player.releaseAudioBufferInternal(b.nativeFrame)
    }

    /**
     * Need update serial and eof.
     */
    override fun enqueueReadable(b: AudioFrame) {
        b.pts = player.getAudioPtsInternal(b.nativeFrame)
        b.duration = player.getAudioDurationInternal(b.nativeFrame)
//        lastDecodedAudioFrame = LastDecodedAudioFrame(
//            pts = b.pts,
//            duration = b.duration,
//            serial = b.serial,
//            isEof = b.isEof
//        )
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
//        data class LastDecodedAudioFrame(
//            val pts: Long,
//            val duration: Long,
//            val serial: Int,
//            val isEof: Boolean
//        )
    }
}