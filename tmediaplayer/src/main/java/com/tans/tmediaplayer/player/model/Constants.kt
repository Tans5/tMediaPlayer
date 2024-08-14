package com.tans.tmediaplayer.player.model

// 10s
internal const val NO_SYNC_THRESHOLD = 10000L

internal const val SYNC_THRESHOLD_MIN = 40L

internal const val SYNC_THRESHOLD_MAX = 100L

internal const val SYNC_FRAMEDUP_THRESHOLD = 100L

internal const val VIDEO_REFRESH_RATE = 10L

internal const val VIDEO_FRAME_QUEUE_SIZE = 4

internal const val VIDEO_EOF_MAX_CHECK_TIMES = VIDEO_FRAME_QUEUE_SIZE * 2

internal const val AUDIO_FRAME_QUEUE_SIZE = 12

internal const val AUDIO_TRACK_QUEUE_SIZE = 14

internal const val AUDIO_EOF_MAX_CHECK_TIMES = AUDIO_FRAME_QUEUE_SIZE * 2