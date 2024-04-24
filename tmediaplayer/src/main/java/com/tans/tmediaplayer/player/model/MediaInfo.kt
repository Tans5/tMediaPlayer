package com.tans.tmediaplayer.player.model

data class MediaInfo(
    internal val nativePlayer: Long,
    val duration: Long,
    val metadata: Map<String, String>,
    val audioStreamInfo: AudioStreamInfo?,
    val videoStreamInfo: VideoStreamInfo?
)
