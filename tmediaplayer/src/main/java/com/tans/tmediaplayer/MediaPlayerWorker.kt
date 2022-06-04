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

    fun postOpt(runnable: Runnable) {
        mediaPlayerOptHandler.post(runnable)
    }

    fun postDecode(runnable: Runnable) {
        mediaPlayerDecodeHandler.post(runnable)
    }

    fun release() {
        mediaPlayerOptThread.quitSafely()
        mediaPlayerDecodeThread.quitSafely()
    }

    private fun newHandlerThread(threadName: String): HandlerThread {
        val result = HandlerThread(threadName)
        result.start()
        return result
    }
}