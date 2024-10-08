package com.tans.tmediaplayer.player.model

data class MediaInfo(
    internal val nativePlayer: Long,
    val file: String,
    val duration: Long,
    val metadata: Map<String, String>,
    val containerName: String,
    val isRealTime: Boolean,
    val isSeekable: Boolean,
    val startTime: Long,
    val audioStreamInfo: AudioStreamInfo?,
    val videoStreamInfo: VideoStreamInfo?,
    val subtitleStreams: List<SubtitleStreamInfo>
)
