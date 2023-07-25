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
                synchronized(player) {
                    when (msg.what) {
                        CALCULATE_RENDER_MEDIA_FRAME -> {

                        }
                        REQUEST_RENDER -> {

                        }
                        REQUEST_PAUSE -> {

                        }
                        RENDER_VIDEO -> {

                        }
                        RENDER_AUDIO -> {

                        }
                        else -> {}
                    }
                }
            }
        }
    }

    @Synchronized
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
    }

    @Synchronized
    fun release() {
        renderHandler.removeMessages(CALCULATE_RENDER_MEDIA_FRAME)
        renderHandler.removeMessages(REQUEST_RENDER)
        renderHandler.removeMessages(REQUEST_PAUSE)
        renderHandler.removeMessages(RENDER_VIDEO)
        renderHandler.removeMessages(RENDER_AUDIO)
        renderThread.quit()
        renderThread.quitSafely()
        this.state.set(tMediaPlayerRenderState.Released)
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