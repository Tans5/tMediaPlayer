package com.tans.tmediaplayer

@Suppress("ClassName")
sealed class tMediaPlayerState {

    object NoInit : tMediaPlayerState() {
        fun prepared(mediaInfo: MediaInfo): Prepared = Prepared(mediaInfo)
    }

    data class Error(val msg: String) : tMediaPlayerState() {
        fun prepared(mediaInfo: MediaInfo): Prepared = Prepared(mediaInfo)
    }

    data class Prepared(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
    }

    data class Paused(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
        fun stop(): Stopped = Stopped(mediaInfo)
    }

    data class Playing(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun pause(): Paused = Paused(mediaInfo)
        fun stop(): Stopped = Stopped(mediaInfo)
        fun playEnd(): PlayEnd = PlayEnd(mediaInfo)
    }

    data class PlayEnd(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
    }

    data class Stopped(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
    }
}
