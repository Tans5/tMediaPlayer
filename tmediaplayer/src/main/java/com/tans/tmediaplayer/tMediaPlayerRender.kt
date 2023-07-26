package com.tans.tmediaplayer

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
internal class tMediaPlayerRender(
    private val player: tMediaPlayer,
    private val bufferManager: tMediaPlayerBufferManager
) {

    private val playerView: AtomicReference<tMediaPlayerView?> by lazy {
        AtomicReference(null)
    }

    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    private val renderThread: HandlerThread by lazy {
        object : HandlerThread("tMediaPlayerRenderThread", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val state: AtomicReference<tMediaPlayerRenderState> by lazy { AtomicReference(tMediaPlayerRenderState.NotInit) }

    private val renderHandler: Handler by lazy {
        while (!isLooperPrepared.get()) {}
        object : Handler(renderThread.looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                val mediaInfo = player.getMediaInfo()
                if (mediaInfo == null) {
                    MediaLog.e(TAG, "RenderHandler render error, media info is null.")
                    return
                }
                val state = getState()
                if (state == tMediaPlayerRenderState.Released || state == tMediaPlayerRenderState.NotInit) {
                    MediaLog.e(TAG, "RenderHandler wrong state: $state")
                    return
                }
                when (msg.what) {
                    CALCULATE_RENDER_MEDIA_FRAME -> {
                        if (state == tMediaPlayerRenderState.Rendering) {
                            val buffer = bufferManager.requestRenderBuffer()
                            if (buffer != null) {
                                synchronized(buffer) {
                                    if (getState() == tMediaPlayerRenderState.Released) { return@synchronized }
                                    if (player.isLastFrameBufferNativeInternal(buffer.nativeBuffer)) {
                                        player.dispatchPlayEnd()
                                        bufferManager.enqueueDecodeBuffer(buffer)
                                        this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.RenderEnd)
                                    } else {
                                        if (player.isVideoBufferNativeInternal(buffer.nativeBuffer)) {
                                            // VIDEO
                                            val delay = player.calculateRenderDelay(player.getVideoPtsNativeInternal(buffer.nativeBuffer))
                                            val m = Message.obtain()
                                            m.what = RENDER_VIDEO
                                            m.obj = buffer
                                            this.sendMessageDelayed(m, delay)
                                        } else {
                                            // AUDIO
                                            val delay = player.calculateRenderDelay(player.getAudioPtsNativeInternal(buffer.nativeBuffer))
                                            val m = Message.obtain()
                                            m.what = RENDER_AUDIO
                                            m.obj = buffer
                                            this.sendMessageDelayed(m, delay)
                                        }
                                        this.sendEmptyMessage(CALCULATE_RENDER_MEDIA_FRAME)
                                    }
                                }
                            } else {
                                this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.WaitingDecoder)
                                MediaLog.d(TAG, "Waiting decoder buffer.")
                            }
                        } else {
                            MediaLog.d(TAG, "Skip render frame, because of state: $state")
                        }
                    }
                    REQUEST_RENDER -> {
                        if (state in listOf(
                                tMediaPlayerRenderState.RenderEnd,
                                tMediaPlayerRenderState.WaitingDecoder,
                                tMediaPlayerRenderState.Paused,
                                tMediaPlayerRenderState.Prepared
                            )
                        ) {
                            this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.Rendering)
                            this.sendEmptyMessage(CALCULATE_RENDER_MEDIA_FRAME)
                        } else {
                            MediaLog.d(TAG, "Skip request render, because of state: $state")
                        }
                    }
                    REQUEST_PAUSE -> {
                        if (state == tMediaPlayerRenderState.Rendering || state == tMediaPlayerRenderState.WaitingDecoder) {
                            this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.Paused)
                        } else {
                            MediaLog.d(TAG, "Skip request pause, because of state: $state")
                        }
                    }
                    RENDER_VIDEO -> {
                        val buffer = msg.obj as? tMediaPlayerBufferManager.Companion.MediaBuffer
                        if (buffer != null) {
                            synchronized(buffer) {
                                if (getState() == tMediaPlayerRenderState.Released) { return }
                                val progress = player.getVideoPtsNativeInternal(buffer.nativeBuffer)
                                player.dispatchProgress(progress)
                                val view = playerView.get()
                                view?.requestRenderFrame(
                                    width = player.getVideoWidthNativeInternal(buffer.nativeBuffer),
                                    height = player.getVideoHeightNativeInternal(buffer.nativeBuffer),
                                    imageBytes = player.getVideoFrameBytesNativeInternal(buffer.nativeBuffer)
                                )
                                bufferManager.enqueueDecodeBuffer(buffer)
                            }
                        }
                        Unit
                    }
                    RENDER_AUDIO -> {
                        val buffer = msg.obj as? tMediaPlayerBufferManager.Companion.MediaBuffer
                        if (buffer != null) {
                            synchronized(buffer) {
                                if (getState() == tMediaPlayerRenderState.Released) { return }
                                val progress = player.getAudioPtsNativeInternal(buffer.nativeBuffer)
                                player.dispatchProgress(progress)
                                // TODO: RENDER AUDIO
                                bufferManager.enqueueDecodeBuffer(buffer)
                            }
                        }
                        Unit
                    }
                    else -> {}
                }
            }
        }
    }

    fun prepare() {
        val lastState = getState()
        if (lastState == tMediaPlayerRenderState.Released) {
            MediaLog.e(TAG, "Prepare fail, render has released.")
            return
        }
        renderThread
        renderHandler
        renderHandler.removeMessages(CALCULATE_RENDER_MEDIA_FRAME)
        renderHandler.removeMessages(REQUEST_RENDER)
        renderHandler.removeMessages(REQUEST_PAUSE)
        renderHandler.removeMessages(RENDER_VIDEO)
        renderHandler.removeMessages(RENDER_AUDIO)
        state.set(tMediaPlayerRenderState.Prepared)
    }

    fun render() {
        val state = getState()
        if (state != tMediaPlayerRenderState.Released && state != tMediaPlayerRenderState.NotInit) {
            renderHandler.sendEmptyMessage(REQUEST_RENDER)
        }
    }

    fun pause() {
        val state = getState()
        if (state != tMediaPlayerRenderState.Released && state != tMediaPlayerRenderState.NotInit) {
            renderHandler.sendEmptyMessage(REQUEST_PAUSE)
        }
    }

    fun checkRenderBufferIfWaiting() {
        if (getState() == tMediaPlayerRenderState.WaitingDecoder) {
            render()
        }
    }

    fun release() {
        renderHandler.removeMessages(CALCULATE_RENDER_MEDIA_FRAME)
        renderHandler.removeMessages(REQUEST_RENDER)
        renderHandler.removeMessages(REQUEST_PAUSE)
        renderHandler.removeMessages(RENDER_VIDEO)
        renderHandler.removeMessages(RENDER_AUDIO)
        renderThread.quit()
        renderThread.quitSafely()
        this.state.set(tMediaPlayerRenderState.Released)
        playerView.set(null)
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        playerView.set(view)
    }

    fun getState(): tMediaPlayerRenderState = state.get()

    companion object {
        private const val CALCULATE_RENDER_MEDIA_FRAME = 0
        private const val REQUEST_RENDER = 1
        private const val REQUEST_PAUSE = 2
        private const val RENDER_VIDEO = 3
        private const val RENDER_AUDIO = 4
        private const val TAG = "tMediaPlayerRender"
    }

}