package com.tans.tmediaplayer.player

@Suppress("ClassName")
interface tMediaPlayerListener {

    fun onPlayerState(state: tMediaPlayerState)

    fun onProgressUpdate(progress: Long, duration: Long)
}