package com.tans.tmediaplayer

@Suppress("ClassName")
class tMediaPlayer {

    fun prepare(file: String) {
        val nativePlayer = createPlayerNative()
        prepareNative(nativePlayer, file, true, 2)
    }


    private external fun createPlayerNative(): Long

    private external fun prepareNative(nativePlayer: Long, file: String, requestHw: Boolean, targetAudioChannels: Int): Int

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
        const val TAG = "tMediaPlayer"
    }
}