package com.tans.tmediaplayer.player.model

data class VideoStreamInfo(
    val videoWidth: Int,
    val videoHeight: Int,
    val videoFps: Double,
    val videoDuration: Long,
    val videoCodec: FFmpegCodec,
    val videoBitrate: Int,
    val videoPixelBitDepth: Int,
    val videoPixelFormat: VideoPixelFormat,
    val isAttachment: Boolean = false,
    val videoDecoderName: String,
    val videoDisplayRotation: Int,
    val videoDisplayRatio: Float,
    val videoStreamMetadata: Map<String, String>
)