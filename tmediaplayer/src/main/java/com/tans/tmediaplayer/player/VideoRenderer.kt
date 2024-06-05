package com.tans.tmediaplayer.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.render.tMediaPlayerView
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.VideoFrame
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

internal class VideoRenderer(
    private val videoFrameQueue: VideoFrameQueue,
    private val videoPacketQueue: PacketQueue,
    private val player: tMediaPlayer
) {
    private val playerView: AtomicReference<tMediaPlayerView?> = AtomicReference()

    private val state: AtomicReference<RendererState> = AtomicReference(RendererState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Video renderer thread.
    private val videoRendererThread: HandlerThread by lazy {
        object : HandlerThread("tMP_VideoRenderer", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val canRenderStates = arrayOf(RendererState.Playing, RendererState.Eof, RendererState.WaitingReadableFrameBuffer)

    private val videoRendererHandler: Handler by lazy {
        object : Handler(videoRendererThread.looper) {

            var lastRenderFrame: LastRenderFrame = LastRenderFrame(duration = 0L, pts = 0L, serial = -1)
            var frameTimer: Long = 0

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@VideoRenderer) {
                    when (msg.what) {
                        RendererHandlerMsg.RequestRender.ordinal -> {
                            val playerState = player.getState()
                            val state = getState()
                            val mediaInfo = player.getMediaInfo()
                            if (mediaInfo != null && state in canRenderStates) {
                                if (state == RendererState.WaitingReadableFrameBuffer && playerState is tMediaPlayerState.Playing) {
                                    this@VideoRenderer.state.set(RendererState.Playing)
                                    requestRender()
                                    return@synchronized
                                }
                                if (state == RendererState.WaitingReadableFrameBuffer && playerState is tMediaPlayerState.Paused) {
                                    this@VideoRenderer.state.set(RendererState.Paused)
                                    return@synchronized
                                }
                                val frame = videoFrameQueue.peekReadable()
                                if (frame != null) {
                                    if (!frame.isEof) {
                                        val lastFrame = lastRenderFrame
                                        if (frame.serial != videoPacketQueue.getSerial()) {
                                            lastRenderFrame = LastRenderFrame(frame)
                                            videoFrameQueue.dequeueReadable()
                                            videoFrameQueue.enqueueWritable(frame)
                                            player.writeableVideoFrameReady()
                                            MediaLog.d(TAG, "Serial changed, skip render.")
                                            requestRender()
                                            return@synchronized
                                        }
                                        if (frame.serial != lastFrame.serial) {
                                            MediaLog.d(TAG, "Serial changed, reset frame timer.")
                                            frameTimer = SystemClock.uptimeMillis()
                                        }
                                        val lastDuration = frameDuration(lastFrame, frame)
                                        val delay = computeTargetDelay(lastDuration)
                                        val time = SystemClock.uptimeMillis()
                                        if (time < frameTimer + delay) {
                                            val remainingTime = min(frameTimer + delay - time, VIDEO_REFRESH_RATE)
                                            MediaLog.d(TAG, "Frame=${frame.pts}, need delay ${remainingTime}ms to display.")
                                            requestRender(remainingTime)
                                            return@synchronized
                                        }
                                        lastRenderFrame = LastRenderFrame(frame)
                                        videoFrameQueue.dequeueReadable()
                                        frameTimer += delay
                                        if (delay > 0 && time - frameTimer > SYNC_THRESHOLD_MAX) {
                                            MediaLog.d(TAG, "Behind time ${time - frameTimer}ms reset frame timer.")
                                            frameTimer = time
                                        }
                                        player.videoClock.setClock(frame.pts, frame.serial)
                                        player.externalClock.syncToClock(player.videoClock)

                                        val nextFrame = videoFrameQueue.peekReadable()
                                        if (nextFrame != null) {
                                            val duration = frameDuration(lastRenderFrame, nextFrame)
                                            if (player.getSyncType() != SyncType.VideoMaster && time > frameTimer + duration) {
                                                MediaLog.e(TAG, "Drop next frame: ${nextFrame.pts}")
                                                videoFrameQueue.dequeueReadable()
                                                videoFrameQueue.enqueueWritable(nextFrame)
                                                player.writeableVideoFrameReady()
                                            }
                                        }

                                        val playerView = this@VideoRenderer.playerView.get()
                                        if (playerView != null) {
                                            when (frame.imageType) {
                                                ImageRawType.Yuv420p -> {
                                                    val y = frame.yBuffer
                                                    val u = frame.uBuffer
                                                    val v = frame.vBuffer
                                                    if (y != null && u != null && v != null) {
                                                        playerView.requestRenderYuv420pFrame(
                                                            width = frame.width,
                                                            height = frame.height,
                                                            yBytes = y,
                                                            uBytes = u,
                                                            vBytes = v
                                                        ) {
                                                            videoFrameQueue.enqueueWritable(frame)
                                                            player.writeableVideoFrameReady()
                                                        }
                                                    } else {
                                                        MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                                        videoFrameQueue.enqueueWritable(frame)
                                                        player.writeableVideoFrameReady()
                                                    }
                                                }
                                                ImageRawType.Nv12 -> {
                                                    val y = frame.yBuffer
                                                    val uv = frame.uvBuffer
                                                    if (y != null && uv != null) {
                                                        playerView.requestRenderNv12Frame(
                                                            width = frame.width,
                                                            height = frame.height,
                                                            yBytes = y,
                                                            uvBytes = uv
                                                        ) {
                                                            videoFrameQueue.enqueueWritable(frame)
                                                            player.writeableVideoFrameReady()
                                                        }
                                                    } else {
                                                        MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                                        videoFrameQueue.enqueueWritable(frame)
                                                        player.writeableVideoFrameReady()
                                                    }
                                                }
                                                ImageRawType.Nv21 -> {
                                                    val y = frame.yBuffer
                                                    val vu = frame.uvBuffer
                                                    if (y != null && vu != null) {
                                                        playerView.requestRenderNv21Frame(
                                                            width = frame.width,
                                                            height = frame.height,
                                                            yBytes = y,
                                                            vuBytes = vu
                                                        ) {
                                                            videoFrameQueue.enqueueWritable(frame)
                                                            player.writeableVideoFrameReady()
                                                        }
                                                    } else {
                                                        MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                                        videoFrameQueue.enqueueWritable(frame)
                                                        player.writeableVideoFrameReady()
                                                    }
                                                }
                                                ImageRawType.Rgba -> {
                                                    val rgba = frame.rgbaBuffer
                                                    if (rgba != null) {
                                                        playerView.requestRenderRgbaFrame(
                                                            width = frame.width,
                                                            height = frame.height,
                                                            imageBytes = rgba
                                                        ) {
                                                            videoFrameQueue.enqueueWritable(frame)
                                                            player.writeableVideoFrameReady()
                                                        }
                                                    } else {
                                                        MediaLog.e(TAG, "Wrong ${frame.imageType} image.")
                                                        videoFrameQueue.enqueueWritable(frame)
                                                        player.writeableVideoFrameReady()
                                                    }
                                                }
                                                ImageRawType.Unknown -> {
                                                    videoFrameQueue.enqueueWritable(frame)
                                                    player.writeableVideoFrameReady()
                                                }
                                            }
                                        } else {
                                            videoFrameQueue.enqueueWritable(frame)
                                            player.writeableVideoFrameReady()
                                        }
                                        requestRender(VIDEO_REFRESH_RATE)
                                    } else {
                                        videoFrameQueue.dequeueReadable()
                                        this@VideoRenderer.state.set(RendererState.Eof)
                                        videoFrameQueue.enqueueWritable(frame)
                                        MediaLog.d(TAG, "render video frame eof.")
                                        player.writeableVideoFrameReady()
                                    }
                                } else {
                                    this@VideoRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                    MediaLog.d(TAG, "Waiting readable video frame.")
                                }
                            }
                        }
                    }
                }
            }

            fun frameDuration(current: LastRenderFrame, next: VideoFrame): Long {
                return if (current.serial == next.serial) {
                    val duration = next.pts - current.pts
                    if (duration <= 0) {
                        current.duration
                    } else {
                        duration
                    }
                } else {
                    0L
                }
            }

            fun computeTargetDelay(delay: Long): Long {
                val syncType = player.getSyncType()
                return if (syncType != SyncType.VideoMaster) {
                    val diff = player.videoClock.getClock() - player.getMasterClock()
                    val threshold: Long = max(min(delay, SYNC_THRESHOLD_MAX), SYNC_THRESHOLD_MIN)
                    if (diff <= - threshold) {
                        max(0L, delay + diff)
                    } else if (diff >= threshold && delay >= SYNC_FRAMEDUP_THRESHOLD) {
                        delay + diff
                    } else if (diff >= threshold) {
                        delay * 2L
                    } else {
                        delay
                    }
                } else {
                    delay
                }
            }
        }
    }

    init {
        videoRendererThread
        while (!isLooperPrepared.get()) {}
        videoRendererHandler
        state.set(RendererState.Paused)
    }

    fun play() {
        val state = getState()
        if (state == RendererState.Paused ||
            state == RendererState.Eof ||
            state == RendererState.WaitingReadableFrameBuffer) {
            if (state == RendererState.Paused || state == RendererState.Eof) {
                this.state.set(RendererState.Playing)
            }
            requestRender()
        } else {
            MediaLog.e(TAG, "Play error, because of state: $state")
        }
    }

    fun pause() {
        val state = getState()
        if (state == RendererState.Playing ||
            state == RendererState.Eof ||
            state == RendererState.WaitingReadableFrameBuffer) {
            if (state == RendererState.Playing || state == RendererState.Eof) {
                this.state.set(RendererState.Paused)
            }
        } else {
            MediaLog.e(TAG, "Pause error, because of state: $state")
        }
    }

    fun readableFrameReady() {
        val state = getState()
        if (state == RendererState.WaitingReadableFrameBuffer) {
            requestRender()
        }
    }

    fun release() {
        synchronized(this) {
            val state = getState()
            if (state != RendererState.NotInit && state != RendererState.Released) {
                this.state.set(RendererState.Released)
                this.playerView.set(null)
            } else {
                MediaLog.e(TAG, "Release error, because of state: $state")
            }
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        this.playerView.set(view)
    }

    fun getState(): RendererState = state.get()

    private fun requestRender(delay: Long = 0) {
        val state = getState()
        if (state in canRenderStates) {
            videoRendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            videoRendererHandler.sendEmptyMessageDelayed(RendererHandlerMsg.RequestRender.ordinal, delay)
        } else {
            MediaLog.e(TAG, "Request render error, because of state: $state")
        }
    }

    companion object {
        private const val TAG = "VideoRenderer"

       private class LastRenderFrame {
           val pts: Long
           val serial: Int
           val duration: Long

           constructor(frame: VideoFrame) {
               pts = frame.pts
               serial = frame.serial
               duration = frame.duration
           }

           constructor(pts: Long, serial: Int, duration: Long) {
               this.pts = pts
               this.serial = serial
               this.duration = duration
           }
       }
    }
}