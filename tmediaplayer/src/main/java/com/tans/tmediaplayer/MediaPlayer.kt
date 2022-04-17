package com.tans.tmediaplayer

class MediaPlayer {

    fun setupPlayer(filePath: String) {
        setupPlayerNative(filePath)
    }

    fun releasePlayer() {
        releasePlayerNative()
    }

    private external fun setupPlayerNative(filePath: String)

    private external fun releasePlayerNative()

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
    }
}