package com.tans.tmediaplayer

import android.os.Handler
import android.os.HandlerThread

internal class MediaPlayerWorker {
    private val mediaPlayerOptThread: HandlerThread by lazy {
        newHandlerThread("tMediaPlayer-opt-thread")
    }

    private val mediaPlayerOptHandler: Handler by lazy {
        Handler(mediaPlayerOptThread.looper)
    }

    private val mediaPlayerDecodeThread: HandlerThread by lazy {
        newHandlerThread("tMediaPlayer-decode-thread")
    }

    private val mediaPlayerDecodeHandler: Handler by lazy {
        Handler(mediaPlayerDecodeThread.looper)
    }

    fun postOpt(delay: Long = 0L, runnable: Runnable) {
        mediaPlayerOptHandler.postDelayed(runnable, delay)
    }

    fun postDecode(delay: Long = 0L, runnable: Runnable) {
        mediaPlayerDecodeHandler.postDelayed(runnable, delay)
    }

    fun release() {
        mediaPlayerOptThread.quitSafely()
        mediaPlayerDecodeThread.quitSafely()
    }

    private fun newHandlerThread(threadName: String): HandlerThread {
        val result = HandlerThread(threadName)
        result.priority = Thread.MAX_PRIORITY
        result.start()
        return result
    }
}