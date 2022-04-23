package com.tans.tmediaplayer

import android.view.Surface
import java.util.concurrent.Executors

private val playerExecutor = Executors.newCachedThreadPool {
    Thread(it, "tMediaPlayer-Thread")
}

class MediaPlayer {

    fun setupPlayer(filePath: String) {
        setupPlayerNative(filePath)
        playerExecutor.execute {
            startDecode()
        }
    }

    fun setSurface(surface: Surface?) {
        setWindowNative(surface)
    }

    fun releasePlayer() {
        releasePlayerNative()
    }

    private fun startDecode() {
        decodeNative()
    }

    private external fun setupPlayerNative(filePath: String)

    private external fun setWindowNative(surface: Surface?)

    private external fun decodeNative()

    private external fun releasePlayerNative()

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
    }
}