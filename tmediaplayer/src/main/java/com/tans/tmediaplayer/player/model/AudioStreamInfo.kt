package com.tans.tmediaplayer.player.model

data class AudioStreamInfo(
    val audioChannels: Int,
    val audioSimpleRate: Int,
    val audioPerSampleBytes: Int,
    val audioDuration: Long,
    val audioCodec: FFmpegCodec,
    val audioBitrate: Int,
    val audioSampleBitDepth: Int,
    val audioSampleFormat: AudioSampleFormat,
    val audioDecoderName: String,
    val audioStreamMetadata: Map<String, String>
)