package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.ImageRawType
import com.tans.tmediaplayer.player.tMediaPlayer2
import java.util.concurrent.atomic.AtomicInteger

internal class VideoFrameQueue(private val player: tMediaPlayer2) : BaseReadWriteQueue<VideoFrame>() {

    override val maxQueueSize: Int = 4

    override fun allocBuffer(): VideoFrame {
        val nativeFrame = player.allocVideoBufferInternal()
        MediaLog.d(TAG, "Alloc new video frame, size=${frameSize.incrementAndGet()}")
        return VideoFrame(nativeFrame)
    }

    override fun recycleBuffer(b: VideoFrame) {
        player.releaseVideoBufferInternal(b.nativeFrame)
        MediaLog.d(TAG, "Recycle video frame, size=${frameSize.decrementAndGet()}")
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

    override fun enqueueWritable(b: VideoFrame) {
        b.pts = 0
        b.duration = 0
        b.serial = 0
        b.imageType = ImageRawType.Unknown
        b.width = 0
        b.height = 0
        b.isEof = false
        super.enqueueWritable(b)
    }

    companion object {
        private const val TAG = "VideoFrameQueue"
        private val frameSize = AtomicInteger()
    }
}