package com.tans.tmediaplayer.player.model

data class AudioStreamInfo(
    val audioChannels: Int,
    val audioSimpleRate: Int,
    val audioPerSampleBytes: Int,
    val audioDuration: Long,
    val audioCodec: FFmpegCodec
)