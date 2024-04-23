package com.tans.tmediaplayer.player

@Suppress("ClassName")
sealed class tMediaPlayerState {

    data object NoInit : tMediaPlayerState()

    data class Error(val msg: String) : tMediaPlayerState()

    data class Prepared(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
        fun seek(targetProgress: Long): Seeking = Seeking(Paused(mediaInfo), targetProgress)
    }

    data class Paused(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
        fun stop(): Stopped = Stopped(mediaInfo)
        fun seek(targetProgress: Long): Seeking = Seeking(this, targetProgress)
    }

    data class Playing(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun pause(): Paused = Paused(mediaInfo)
        fun stop(): Stopped = Stopped(mediaInfo)
        fun playEnd(): PlayEnd = PlayEnd(mediaInfo)
        fun seek(targetProgress: Long): Seeking = Seeking(this, targetProgress)
    }

    data class PlayEnd(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
        fun seek(targetProgress: Long): Seeking = Seeking(Paused(mediaInfo), targetProgress)
    }

    data class Stopped(val mediaInfo: MediaInfo) : tMediaPlayerState() {
        fun play(): Playing = Playing(mediaInfo)
        fun seek(targetProgress: Long): Seeking = Seeking(Paused(mediaInfo), targetProgress)
    }

    data class Seeking(val lastState: tMediaPlayerState, val targetProgress: Long) : tMediaPlayerState()

    data object Released : tMediaPlayerState()
}
