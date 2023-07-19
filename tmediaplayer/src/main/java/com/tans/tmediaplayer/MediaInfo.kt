package com.tans.tmediaplayer

data class MediaInfo(
    internal val nativePlayer: Long,

    val duration: Long,

    val videoWidth: Int,
    val videoHeight: Int,
    val videoFps: Double,
    val videoDuration: Long,

    val audioChannels: Int,
    val audioSimpleRate: Int,
    val audioPreSampleBytes: Int,
    val audioDuration: Long,
)
