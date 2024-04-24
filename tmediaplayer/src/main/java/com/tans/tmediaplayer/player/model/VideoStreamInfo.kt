package com.tans.tmediaplayer.player.model

data class VideoStreamInfo(
    val videoWidth: Int,
    val videoHeight: Int,
    val videoFps: Double,
    val videoDuration: Long,
    val videoCodecId: Int
)