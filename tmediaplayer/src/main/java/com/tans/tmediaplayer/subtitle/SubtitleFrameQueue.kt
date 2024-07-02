package com.tans.tmediaplayer.subtitle

import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.rwqueue.BaseReadWriteQueue
import java.util.concurrent.atomic.AtomicInteger

internal class SubtitleFrameQueue(
    private val subtitle: tMediaSubtitle
) : BaseReadWriteQueue<SubtitleFrame>() {

    override val maxQueueSize: Int = 16

    override fun allocBuffer(): SubtitleFrame {
        val nativeFrame = subtitle.allocSubtitleBufferInternal()
        MediaLog.d(TAG, "Alloc new subtitle frame, size=${frameSize.incrementAndGet()}")
        return SubtitleFrame(nativeFrame)
    }

    override fun recycleBuffer(b: SubtitleFrame) {
        subtitle.releaseSubtitleBufferInternal(b.nativeFrame)
        MediaLog.d(TAG, "Recycle subtitle frame, size=${frameSize.decrementAndGet()}")
    }

    override fun enqueueReadable(b: SubtitleFrame) {
        b.subtitles = subtitle.getSubtitleStringsInternal(b.nativeFrame).let { array ->
            array.map { it.fixAssSubtitle() }
        }
        b.startPts = subtitle.getSubtitleStartPtsInternal(b.nativeFrame)
        b.endPts = subtitle.getSubtitleEndPtsInternal(b.nativeFrame)
        super.enqueueReadable(b)
    }

    override fun enqueueWritable(b: SubtitleFrame) {
        b.subtitles = null
        b.startPts = 0L
        b.endPts = 0L
        super.enqueueWritable(b)
    }

    companion object {
        private const val TAG = "SubtitleFrameQueue"
        private val frameSize = AtomicInteger()

        private val aasSubtitlePrefixRegex = "^(([^,]*,){8})".toRegex()

        private val assSubtitleCommandRegex = "\\{.*\\}".toRegex()

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