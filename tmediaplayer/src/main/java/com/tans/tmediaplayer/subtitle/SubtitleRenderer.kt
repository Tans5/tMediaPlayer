package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.tans.tmediaplayer.player.playerview.GLRenderer
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.renderer.RendererHandlerMsg
import com.tans.tmediaplayer.player.renderer.RendererState
import com.tans.tmediaplayer.player.rwqueue.ReadWriteQueueListener
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

internal class SubtitleRenderer(
    private val player: tMediaPlayer,
    private val subtitle: tMediaSubtitle,
    looper: Looper
) {
    private val state: AtomicReference<RendererState> = AtomicReference(RendererState.Paused)

    private val frameQueue: SubtitleFrameQueue = subtitle.frameQueue

    private val canRenderStates = arrayOf(
        RendererState.Playing,
        RendererState.Eof,
        RendererState.WaitingReadableFrameBuffer
    )

    private val frameListener: ReadWriteQueueListener = object : ReadWriteQueueListener {
        override fun onNewWriteableFrame() {}
        override fun onNewReadableFrame() {
            readableFrameReady()
        }
    }

    private val waitingRendererFrames: ConcurrentHashMap<SubtitleFrame, Unit> = ConcurrentHashMap()

    private val frameOutOfDateListener = object : GLRenderer.Companion.SubtitleFrameOutOfDataListener {
        override fun onFrameOutOfDate(subtitleFrame: SubtitleFrame) {
            if (waitingRendererFrames.remove(subtitleFrame) != null) {
                frameQueue.enqueueWritable(subtitleFrame)
            }
        }
    }

    private val rendererHandler: Handler = object : Handler(looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            synchronized(this@SubtitleRenderer) {
                when (msg.what) {
                    RendererHandlerMsg.RequestRender.ordinal -> {
                        val state = getState()
                        if (state in canRenderStates) {
                            val frame = frameQueue.peekReadable()
                            if (frame != null) {
                                if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                    this@SubtitleRenderer.state.set(RendererState.Playing)
                                }
                                val playerPts = player.getProgress()
                                if (frame.serial != subtitle.packetQueue.getSerial() || frame.endPts < playerPts) { // frame out of date.
                                    tMediaPlayerLog.e(TAG) { "Skip render frame: $frame, packetQueueSerial=${subtitle.packetQueue.getSerial()}, playerPts=$playerPts" }
                                    val f = frameQueue.dequeueReadable()
                                    if (f == frame) {
                                        frameQueue.enqueueWritable(frame)
                                    } else {
                                        if (f != null) {
                                            frameQueue.enqueueWritable(f)
                                        }
                                    }
                                    requestRender()
                                    return@synchronized
                                }
                                if (playerPts < frame.startPts) { // need to delay to render
                                    val delay = min(frame.startPts - playerPts, 3000)
                                    tMediaPlayerLog.d(TAG) { "Need to delay ${delay}ms to render $frame, playerPts=$playerPts" }
                                    requestRender(delay)
                                    return@synchronized
                                }
                                val f = frameQueue.dequeueReadable()
                                if (f != frame) {
                                    tMediaPlayerLog.e(TAG) { "Wrong frame $frame" }
                                    if (f != null) {
                                        frameQueue.enqueueWritable(f)
                                    }
                                    requestRender()
                                    return@synchronized
                                }
                                waitingRendererFrames[frame] = Unit
                                player.getGLRenderer().requestRenderSubtitleFrame(frame)
                                requestRender()
                            } else {
                                if (state == RendererState.Playing) {
                                    this@SubtitleRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                }
                                // tMediaPlayerLog.d(TAG) { "Waiting readable subtitle frame." }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        frameQueue.addListener(frameListener)
        player.getGLRenderer().addSubtitleOutOfDateListener(frameOutOfDateListener)
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

    fun release() {
        synchronized(this) {
            val state = getState()
            if (state != RendererState.NotInit && state != RendererState.Released) {
                this.state.set(RendererState.Released)
                frameQueue.removeListener(frameListener)
                player.getGLRenderer().removeSubtitleOutOfDateListener(frameOutOfDateListener)
                val iterator = waitingRendererFrames.iterator()
                while (iterator.hasNext()) {
                    val keyValue = try {
                        iterator.next()
                    } catch (_: Throwable) {
                        null
                    }
                    if (keyValue != null) {
                        try {
                            iterator.remove()
                            frameQueue.enqueueWritable(keyValue.key)
                        } catch (_: Throwable) {
                        }
                    }
                }
                tMediaPlayerLog.d(TAG) { "Subtitle renderer released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release error, because of state: $state" }
            }
        }
    }

    fun readableFrameReady() {
        val state = getState()
        if (state == RendererState.WaitingReadableFrameBuffer) {
            requestRender()
        }
    }

    fun getState(): RendererState = state.get()

    private fun requestRender(delay: Long = 0) {
        val state = getState()
        if (state in canRenderStates) {
            rendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            rendererHandler.sendEmptyMessageDelayed(RendererHandlerMsg.RequestRender.ordinal, delay)
        } else {
            tMediaPlayerLog.e(TAG) { "Request render error, because of state: $state" }
        }
    }

    companion object {
        private const val TAG = "SubtitleRenderer"
    }
}