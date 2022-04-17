package com.tans.tmediaplayer

class MediaPlayer {

    fun setFilePath(filePath: String) {
        setFilePathNative(filePath)
    }

    private external fun setFilePathNative(filePath: String)

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
    }
}