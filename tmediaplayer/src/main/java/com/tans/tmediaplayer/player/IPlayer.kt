package com.tans.tmediaplayer.player

import com.tans.tmediaplayer.player.model.MediaInfo
import com.tans.tmediaplayer.player.render.tMediaPlayerView


interface IPlayer {

    fun prepare(file: String): OptResult

    fun play(): OptResult

    fun pause(): OptResult

    fun seekTo(position: Long): OptResult

    fun stop(): OptResult

    fun release(): OptResult

    fun getProgress(): Long

    fun getState(): tMediaPlayerState

    fun getMediaInfo(): MediaInfo?

    fun setListener(l: tMediaPlayerListener?)

    fun attachPlayerView(view: tMediaPlayerView?)
}