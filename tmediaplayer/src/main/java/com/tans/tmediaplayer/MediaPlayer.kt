package com.tans.tmediaplayer

import android.view.Surface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val playerExecutor = Executors.newCachedThreadPool {
    Thread(it, "tMediaPlayer-Thread")
}

class MediaPlayer {

    private val playerId: AtomicReference<Long?> by lazy {
        AtomicReference(null)
    }

    fun setupPlayer(filePath: String) {
        playerId.set(setupPlayerNative(filePath))
        startDecode()
    }

    fun setSurface(surface: Surface?) {
        val id = playerId.get()
        if (id != null) {
            setWindowNative(id, surface)
        }
    }

    fun releasePlayer() {
        val id = playerId.get()
        if (id != null) {
            releasePlayerNative(id)
        }
    }

    private fun startDecode() {
        val id = playerId.get()
        if (id != null) {
            decodeNative(id)
        }
    }

    private external fun setupPlayerNative(filePath: String): Long

    private external fun setWindowNative(playerId: Long, surface: Surface?)

    private external fun decodeNative(playerId: Long)

    private external fun releasePlayerNative(playerId: Long)

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
    }
}