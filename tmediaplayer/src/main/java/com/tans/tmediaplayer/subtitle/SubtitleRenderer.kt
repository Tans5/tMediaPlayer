package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.renderer.RendererHandlerMsg
import com.tans.tmediaplayer.player.renderer.RendererState
import com.tans.tmediaplayer.player.rwqueue.ReadWriteQueueListener
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicReference

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

    private val rendererHandler: Handler = object : Handler(looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            synchronized(this@SubtitleRenderer) {
                when (msg.what) {
                    RendererHandlerMsg.RequestRender.ordinal -> {
                        val state = getState()
                        if (state in canRenderStates) {
                            val frame = frameQueue.dequeueReadable()
                            if (frame != null) {
                                if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                    this@SubtitleRenderer.state.set(RendererState.Playing)
                                }
                                if (frame.serial != subtitle.packetQueue.getSerial()) {
                                    tMediaPlayerLog.d(TAG) { "Skip render frame: $frame, serial changed." }
                                    frameQueue.enqueueWritable(frame)
                                    requestRender()
                                    return@synchronized
                                }
                                val playerPts = player.getProgress()
                                // TODO: render subtitle.
                                frameQueue.enqueueWritable(frame)
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