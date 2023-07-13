package com.tans.tmediaplayer

data class MediaInfo(
    val nativePlayer: Long,

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
