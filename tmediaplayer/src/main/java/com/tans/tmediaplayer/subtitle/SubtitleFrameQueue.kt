package com.tans.tmediaplayer.subtitle

import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.rwqueue.BaseReadWriteQueue
import java.util.concurrent.atomic.AtomicInteger

internal class SubtitleFrameQueue(
    private val subtitle: tMediaSubtitle
) : BaseReadWriteQueue<SubtitleFrame>() {

    override val maxQueueSize: Int = 8

    override fun allocBuffer(): SubtitleFrame {
        val nativeFrame = subtitle.allocSubtitleBufferInternal()
        frameSize.incrementAndGet()
        tMediaPlayerLog.d(TAG) { "Alloc new subtitle frame, size=${frameSize.get()}" }
        return SubtitleFrame(nativeFrame)
    }

    override fun recycleBuffer(b: SubtitleFrame) {
        subtitle.releaseSubtitleBufferInternal(b.nativeFrame)
        frameSize.decrementAndGet()
        tMediaPlayerLog.d(TAG) { "Recycle subtitle frame, size=${frameSize.get()}" }
    }

    override fun enqueueReadable(b: SubtitleFrame) {
        b.startPts = subtitle.getSubtitleStartPtsInternal(b.nativeFrame)
        b.endPts = subtitle.getSubtitleEndPtsInternal(b.nativeFrame)
        b.width = subtitle.getSubtitleWidthInternal(b.nativeFrame)
        b.height = subtitle.getSubtitleHeightInternal(b.nativeFrame)
        val contentSize = b.width * b.height * 4
        val bytes = b.rgbaBytes.let {
            if (it == null || it.size < contentSize) {
                val new = ByteArray(contentSize)
                b.rgbaBytes = new
                new
            } else {
                it
            }
        }
        subtitle.getSubtitleFrameRgbaBytesInternal(b.nativeFrame, bytes)
        super.enqueueReadable(b)
    }

    override fun dequeueWritable(): SubtitleFrame? {
        return super.dequeueWritable()?.apply { reset() }
    }

    override fun dequeueWriteableForce(): SubtitleFrame {
        return super.dequeueWriteableForce().apply { reset() }
    }

    companion object {
        private const val TAG = "SubtitleFrameQueue"
        private val frameSize = AtomicInteger()

        private val aasSubtitlePrefixRegex = "^(([^,]*,){8})".toRegex()

        private val assSubtitleCommandRegex = "\\{[^{}]*\\}".toRegex()

        private fun String.fixAssSubtitle(): String {
            return if (this.contains(aasSubtitlePrefixRegex)) {
                this.replace(aasSubtitlePrefixRegex, "")
                    .replace(assSubtitleCommandRegex, "")
                    .replace("\\N", "\n")
                    .replace("\\h", "\t")
            } else {
                this
            }
        }
    }
}