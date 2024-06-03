package com.tans.tmediaplayer.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.render.tMediaPlayerView
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class VideoRenderer(
    private val videoFrameQueue: VideoFrameQueue,
    private val videoPacketQueue: PacketQueue,
    private val player: tMediaPlayer2
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
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@VideoRenderer) {
                    when (msg.what) {
                        RendererHandlerMsg.RequestRender.ordinal -> {
                            // TODO: render video
                        }
                    }
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

    private fun requestRender() {
        val state = getState()
        if (state in canRenderStates) {
            videoRendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            videoRendererHandler.sendEmptyMessage(RendererHandlerMsg.RequestRender.ordinal)
        } else {
            MediaLog.e(TAG, "Request render error, because of state: $state")
        }
    }

    companion object {
        private const val TAG = "VideoRenderer"
    }
}