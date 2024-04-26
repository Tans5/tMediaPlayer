package com.tans.tmediaplayer.demo

fun Long.formatDuration(): String {
    val durationInSeconds = this / 1000
    val minuets = durationInSeconds / 60
    val seconds = durationInSeconds % 60
    return "%02d:%02d".format(minuets, seconds)
}