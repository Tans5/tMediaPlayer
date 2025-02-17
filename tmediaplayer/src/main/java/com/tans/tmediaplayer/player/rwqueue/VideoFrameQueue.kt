package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.model.VIDEO_FRAME_QUEUE_SIZE
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicInteger

internal class VideoFrameQueue(private val player: tMediaPlayer) : BaseReadWriteQueue<VideoFrame>() {

    override val maxQueueSize: Int = VIDEO_FRAME_QUEUE_SIZE

    override fun allocBuffer(): VideoFrame {
        val nativeFrame = player.allocVideoBufferInternal()
        frameSize.incrementAndGet()
        tMediaPlayerLog.d(TAG) { "Alloc new video frame, size=${frameSize.get()}" }
        return VideoFrame(nativeFrame)
    }

    override fun recycleBuffer(b: VideoFrame) {
        player.releaseVideoBufferInternal(b.nativeFrame)
        frameSize.decrementAndGet()
        tMediaPlayerLog.d(TAG) { "Recycle video frame, size=${frameSize.get()}" }
    }

    /**
     * Need update serial and eof.
     */
    override fun enqueueReadable(b: VideoFrame) {
        b.pts = player.getVideoPtsInternal(b.nativeFrame)
        b.duration = player.getVideoDurationInternal(b.nativeFrame)
        if (!b.isEof) {
            b.imageType = player.getVideoFrameTypeNativeInternal(b.nativeFrame)
            b.width = player.getVideoWidthNativeInternal(b.nativeFrame)
            b.height = player.getVideoHeightNativeInternal(b.nativeFrame)
            when (b.imageType) {
                ImageRawType.Yuv420p -> {
                    // Y
                    val ySize = player.getVideoFrameYSizeNativeInternal(b.nativeFrame)
                    if (b.yBuffer?.size != ySize) {
                        b.yBuffer = ByteArray(ySize)
                    }
                    player.getVideoFrameYBytesNativeInternal(b.nativeFrame, b.yBuffer!!)

                    // U
                    val uSize = player.getVideoFrameUSizeNativeInternal(b.nativeFrame)
                    if (b.uBuffer?.size != uSize) {
                        b.uBuffer = ByteArray(uSize)
                    }
                    player.getVideoFrameUBytesNativeInternal(b.nativeFrame, b.uBuffer!!)

                    // V
                    val vSize = player.getVideoFrameVSizeNativeInternal(b.nativeFrame)
                    if (b.vBuffer?.size != vSize) {
                        b.vBuffer = ByteArray(vSize)
                    }
                    player.getVideoFrameVBytesNativeInternal(b.nativeFrame, b.vBuffer!!)
                }
                ImageRawType.Nv12, ImageRawType.Nv21 -> {
                    // Y
                    val ySize = player.getVideoFrameYSizeNativeInternal(b.nativeFrame)
                    if (b.yBuffer?.size != ySize) {
                        b.yBuffer = ByteArray(ySize)
                    }
                    player.getVideoFrameYBytesNativeInternal(b.nativeFrame, b.yBuffer!!)

                    // UV/VU
                    val uvSize = player.getVideoFrameUVSizeNativeInternal(b.nativeFrame)
                    if (b.uvBuffer?.size != uvSize) {
                        b.uvBuffer = ByteArray(uvSize)
                    }
                    player.getVideoFrameUVBytesNativeInternal(b.nativeFrame, b.uvBuffer!!)
                }
                ImageRawType.Rgba -> {
                    // Rgba
                    val rgbaSize = player.getVideoFrameRgbaSizeNativeInternal(b.nativeFrame)
                    if (b.rgbaBuffer?.size != rgbaSize) {
                        b.rgbaBuffer = ByteArray(rgbaSize)
                    }
                    player.getVideoFrameRgbaBytesNativeInternal(b.nativeFrame, b.rgbaBuffer!!)
                }
                ImageRawType.Unknown -> {

                }
            }
        }
        super.enqueueReadable(b)
    }

    override fun dequeueWritable(): VideoFrame? {
        return super.dequeueWritable()?.apply { reset() }
    }

    override fun dequeueWriteableForce(): VideoFrame {
        return super.dequeueWriteableForce().apply { reset() }
    }

    companion object {
        private const val TAG = "VideoFrameQueue"
        private val frameSize = AtomicInteger()
    }
}