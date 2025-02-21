package com.tans.tmediaplayer.player.renderer

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.model.SYNC_FRAMEDUP_THRESHOLD
import com.tans.tmediaplayer.player.model.SYNC_THRESHOLD_MAX
import com.tans.tmediaplayer.player.model.SYNC_THRESHOLD_MIN
import com.tans.tmediaplayer.player.model.SyncType
import com.tans.tmediaplayer.player.model.VIDEO_FRAME_QUEUE_SIZE
import com.tans.tmediaplayer.player.model.VIDEO_REFRESH_RATE
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.VideoFrame
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import com.tans.tmediaplayer.player.tMediaPlayer
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

    private val canRenderStates = arrayOf(
        RendererState.Playing,
        RendererState.Eof,
        RendererState.WaitingReadableFrameBuffer
    )

    private val renderForce: AtomicBoolean = AtomicBoolean(false)

    private val videoRendererHandler: Handler by lazy {
        object : Handler(videoRendererThread.looper) {

            val lastRenderedFrame: LastRenderedFrame = LastRenderedFrame()
            var frameTimer: Long = 0

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@VideoRenderer) {
                    when (msg.what) {
                        RendererHandlerMsg.RequestRender.ordinal -> {
                            if (renderForce.get()) { // Force render a frame don't check state, while in paused state to seeking.
                                val frame = videoFrameQueue.dequeueReadable()
                                if (frame != null) {
                                    if (frame.serial != videoPacketQueue.getSerial()) {
                                        enqueueWriteableFrame(frame)
                                        tMediaPlayerLog.e(TAG) { "Serial changed, skip force render." }
                                        requestRender()
                                        return@synchronized
                                    }
                                    renderForce.set(false)
                                    if (!frame.isEof) {
                                        player.videoClock.setClock(frame.pts, frame.serial)
                                        player.externalClock.syncToClock(player.videoClock)
                                        renderVideoFrame(frame)
                                        tMediaPlayerLog.d(TAG) { "Force render video success." }
                                    } else {
                                        this@VideoRenderer.state.set(RendererState.Eof)
                                        enqueueWriteableFrame(frame)
                                        tMediaPlayerLog.d(TAG) { "Force render video frame eof." }
                                    }
                                } else {
                                    tMediaPlayerLog.d(TAG) { "Force render waiting readable video frame" }
                                }
                            } else {
                                val state = getState()
                                val mediaInfo = player.getMediaInfo()
                                if (mediaInfo != null && state in canRenderStates) { // Can render
                                    val frame = videoFrameQueue.peekReadable()
                                    if (frame != null) { // Has a frame to render
                                        if (frame.serial != videoPacketQueue.getSerial()) { // Frame serial changed cause seeking or change files, skip render this frame.
                                            val frameToCheck = videoFrameQueue.dequeueReadable()
                                            if (frameToCheck === frame) {
                                                enqueueWriteableFrame(frame)
                                            } else {
                                                if (frameToCheck != null) {
                                                    enqueueWriteableFrame(frameToCheck)
                                                }
                                                tMediaPlayerLog.e(TAG) { "Wrong render frame: $frame" }
                                            }
                                            tMediaPlayerLog.d(TAG) { "Serial changed, skip render." }
                                            requestRender()
                                            return@synchronized
                                        }
                                        if (!frame.isEof) { // Render a not eof frame.
                                            if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                                this@VideoRenderer.state.set(RendererState.Playing)
                                            }
                                            if (frame.serial != lastRenderedFrame.serial) {
                                                tMediaPlayerLog.d(TAG) { "Serial changed, reset frame timer." }
                                                frameTimer = SystemClock.uptimeMillis()
                                            }
                                            val lastDuration = frameDuration(
                                                currentSerial = lastRenderedFrame.serial,
                                                currentPts = lastRenderedFrame.pts,
                                                currentDuration = lastRenderedFrame.duration,
                                                nextSerial = frame.serial,
                                                nextPts = frame.pts,
                                            )
                                            val delay = computeTargetDelay(lastDuration)
                                            val time = SystemClock.uptimeMillis()
                                            if (time < frameTimer + delay) {  // Need wait to render.
                                                val remainingTime = min(frameTimer + delay - time, VIDEO_REFRESH_RATE)
                                                tMediaPlayerLog.d(TAG) { "Frame=${frame.pts}, need delay ${remainingTime}ms to display." }
                                                requestRender(remainingTime)
                                                return@synchronized
                                            }

                                            // Right time to render.

                                            val frameToCheck = videoFrameQueue.dequeueReadable()
                                            if (frame !== frameToCheck) {
                                                tMediaPlayerLog.e(TAG) { "Wrong render frame: $frame" }
                                                requestRender()
                                                if (frameToCheck != null) {
                                                    enqueueWriteableFrame(frameToCheck)
                                                }
                                                return@synchronized
                                            }

                                            frameTimer += delay
                                            if (delay > 0 && time - frameTimer > SYNC_THRESHOLD_MAX) {
                                                tMediaPlayerLog.e(TAG) { "Behind time ${time - frameTimer}ms reset frame timer." }
                                                frameTimer = time
                                            }

                                            renderVideoFrame(frame) // render frame
                                            requestRender()
                                        } else { // Eof frame
                                            val frameToCheck = videoFrameQueue.dequeueReadable()
                                            if (frameToCheck === frame) {
                                                try { // Waiting all frames finish rendering.
                                                    Thread.sleep(max(VIDEO_FRAME_QUEUE_SIZE * lastRenderedFrame.duration, 10))
                                                } catch (e: Throwable) {
                                                    tMediaPlayerLog.e(TAG, { "Waiting all frame finish rendering error: ${e.message}" }, { e })
                                                }
                                                this@VideoRenderer.state.set(RendererState.Eof)
                                                enqueueWriteableFrame(frame)
                                                tMediaPlayerLog.d(TAG) { "Render video frame eof." }
                                            } else {
                                                if (frameToCheck != null) {
                                                    enqueueWriteableFrame(frameToCheck)
                                                }
                                                requestRender()
                                            }
                                        }
                                    } else { // No readable frame, waiting for decoder.
                                        if (state == RendererState.Playing) {
                                            this@VideoRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                        }
                                        tMediaPlayerLog.d(TAG) { "Waiting readable video frame." }
                                    }
                                }
                            }
                        }

                        RendererHandlerMsg.Rendered.ordinal -> {
                            val renderedFrame = msg.obj as? LastRenderedFrame
                            if (renderedFrame != null) {
                                lastRenderedFrame.serial = renderedFrame.serial
                                lastRenderedFrame.pts = renderedFrame.pts
                                lastRenderedFrame.duration = renderedFrame.duration
                                player.videoClock.setClock(renderedFrame.pts, renderedFrame.serial)
                                player.externalClock.syncToClock(player.videoClock)

                                val time = SystemClock.uptimeMillis()
                                val nextFrame = videoFrameQueue.peekReadable()
                                if (nextFrame != null && !nextFrame.isEof) { // Drop out of data frames.
                                    val duration = frameDuration(
                                        currentSerial = lastRenderedFrame.serial,
                                        currentPts = lastRenderedFrame.pts,
                                        currentDuration = lastRenderedFrame.duration,
                                        nextSerial = nextFrame.serial,
                                        nextPts = nextFrame.pts
                                    )
                                    if (player.getSyncType() != SyncType.VideoMaster && time > frameTimer + duration) {
                                        tMediaPlayerLog.e(TAG) { "Drop next frame: ${nextFrame.pts}" }
                                        val nextFrameToCheck = videoFrameQueue.dequeueReadable()
                                        if (nextFrameToCheck === nextFrame) {
                                            enqueueWriteableFrame(nextFrame)
                                        } else {
                                            if (nextFrameToCheck != null) {
                                                enqueueWriteableFrame(nextFrameToCheck)
                                            }
                                            tMediaPlayerLog.e(TAG) { "Wrong render frame: $nextFrame" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val renderedFrame: LastRenderedFrame = LastRenderedFrame() // For Message use.
            fun renderVideoFrame(frame: VideoFrame) {
                val playerView = this@VideoRenderer.playerView.get()
                val renderCallback: (isRendered: Boolean) -> Unit = { isRendered ->
                    if (isRendered) {
                        val msg = this.obtainMessage(RendererHandlerMsg.Rendered.ordinal)
                        renderedFrame.serial = frame.serial
                        renderedFrame.pts = frame.pts
                        renderedFrame.duration = frame.duration
                        msg.obj = renderedFrame
                        sendMessage(msg)
                    }
                    enqueueWriteableFrame(frame)
                }
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
                                    vBytes = v,
                                    pts = frame.pts,
                                    callback = renderCallback
                                )
                            } else {
                                tMediaPlayerLog.e(TAG) { "Wrong ${frame.imageType} image." }
                                renderCallback(false)
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
                                    uvBytes = uv,
                                    pts = frame.pts,
                                    callback = renderCallback
                                )
                            } else {
                                tMediaPlayerLog.e(TAG) { "Wrong ${frame.imageType} image." }
                                renderCallback(false)
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
                                    vuBytes = vu,
                                    pts = frame.pts,
                                    callback = renderCallback
                                )
                            } else {
                                tMediaPlayerLog.e(TAG) { "Wrong ${frame.imageType} image." }
                                renderCallback(false)
                            }
                        }
                        ImageRawType.Rgba -> {
                            val rgba = frame.rgbaBuffer
                            if (rgba != null) {
                                playerView.requestRenderRgbaFrame(
                                    width = frame.width,
                                    height = frame.height,
                                    imageBytes = rgba,
                                    pts = frame.pts,
                                    callback = renderCallback
                                )
                            } else {
                                tMediaPlayerLog.e(TAG) { "Wrong ${frame.imageType} image." }
                                renderCallback(false)
                            }
                        }
                        ImageRawType.Unknown -> {
                            renderCallback(false)
                        }
                    }
                } else {
                    renderCallback(false)
                }
            }

            fun frameDuration(
                currentSerial: Int,
                currentPts: Long,
                currentDuration: Long,
                nextSerial: Int,
                nextPts: Long,
            ): Long {
                return if (currentSerial == nextSerial) {
                    val duration = nextPts - currentPts
                    if (duration <= 0) {
                        currentDuration
                    } else {
                        duration
                    }
                } else {
                    0L
                }
            }

            fun computeTargetDelay(frameDuration: Long): Long {
                val syncType = player.getSyncType()
                return if (syncType != SyncType.VideoMaster) {
                    val videoClock = player.videoClock.getClock()
                    val masterClock = player.getMasterClock()
                    val diff = videoClock - masterClock
                    tMediaPlayerLog.d(TAG) { "VideoClock: $videoClock, MasterClock: $masterClock, ClockDiff: $diff, FrameDuration: $frameDuration" }
                    val threshold: Long = max(min(frameDuration, SYNC_THRESHOLD_MAX), SYNC_THRESHOLD_MIN) // Calculate clock diff threshold, In common use frame duration.
                    if (diff <= - threshold) { // VideoClock slow
                        max(0L, frameDuration + diff)
                    } else if (diff >= threshold && frameDuration >= SYNC_FRAMEDUP_THRESHOLD) { // VideoClock faster and frame duration greater than 100ms
                        frameDuration + diff
                    } else if (diff >= threshold) { // VideoClock faster and frame duration smaller than 100ms
                        frameDuration * 2L
                    } else { // Normal
                        frameDuration
                    }
                } else { // No sync.
                    frameDuration
                }
            }
        }
    }

    init {
        videoRendererThread
        while (!isLooperPrepared.get()) {}
        videoRendererHandler
        state.set(RendererState.Paused)
        tMediaPlayerLog.d(TAG) { "Video renderer inited." }
    }

    fun play() {
        synchronized(this) {
            val state = getState()
            if (state == RendererState.Paused ||
                state == RendererState.Eof
            ) {
                this.state.set(RendererState.Playing)
                requestRender()
            } else {
                tMediaPlayerLog.e(TAG) { "Play error, because of state: $state" }
            }
        }
    }

    fun pause() {
        synchronized(this) {
            val state = getState()
            if (state in canRenderStates) {
                this.state.set(RendererState.Paused)
            } else {
                tMediaPlayerLog.e(TAG) { "Pause error, because of state: $state" }
            }
        }
    }

    fun readableFrameReady() {
        val state = getState()
        if (state == RendererState.WaitingReadableFrameBuffer || renderForce.get()) {
            requestRender()
        } else {
            tMediaPlayerLog.d(TAG) { "Skip handle readable video frame ready, because of state: $state" }
        }
    }

    fun requestRenderForce() {
        val state = getState()
        if (state != RendererState.NotInit && state != RendererState.Released) {
            if (renderForce.compareAndSet(false, true)) {
                requestRender()
            } else {
                tMediaPlayerLog.e(TAG) { "Force render error, already have a force render task." }
            }
        } else {
            tMediaPlayerLog.e(TAG) { "Force render error, because of state: $state" }
        }
    }

    fun release() {
        synchronized(this) {
            val state = getState()
            if (state != RendererState.NotInit && state != RendererState.Released) {
                this.state.set(RendererState.Released)
                this.playerView.get()?.tryRecycleUnhandledRequestImageData()
                this.playerView.set(null)
                videoRendererThread.quit()
                videoRendererThread.quitSafely()
                tMediaPlayerLog.d(TAG) { "Video renderer released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release error, because of state: $state" }
            }
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        this.playerView.set(view)
    }

    fun getState(): RendererState = state.get()

    private fun requestRender(delay: Long = 0) {
        val state = getState()
        if (state in canRenderStates || renderForce.get()) {
            videoRendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            videoRendererHandler.sendEmptyMessageDelayed(RendererHandlerMsg.RequestRender.ordinal, delay)
        } else {
            tMediaPlayerLog.e(TAG) { "Request render error, because of state: $state" }
        }
    }

    private fun enqueueWriteableFrame(f: VideoFrame) {
        videoFrameQueue.enqueueWritable(f)
        player.writeableVideoFrameReady()
    }

    companion object {
        private const val TAG = "VideoRenderer"

       private class LastRenderedFrame {
           var pts: Long = 0
           var serial: Int = -1
           var duration: Long = 0
       }
    }
}