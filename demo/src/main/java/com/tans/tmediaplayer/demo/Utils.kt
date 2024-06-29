package com.tans.tmediaplayer.demo

import java.util.Locale

fun Long.formatDuration(): String {
    val durationInSeconds = this / 1000
    val minuets = durationInSeconds / 60
    val seconds = durationInSeconds % 60
    return "%02d:%02d".format(minuets, seconds)
}

private const val KB = 1024
private const val MB = KB * 1024
private const val GB = MB * 1024

fun Long.toSizeString(): String {
    return when (this) {
        in 0 until KB -> String.format(Locale.US, "%d B", this)
        in KB until MB -> String.format(Locale.US, "%.2f KB", this.toDouble() / KB)
        in MB until GB -> String.format(Locale.US, "%.2f MB", this.toDouble() / MB)
        in GB until Long.MAX_VALUE -> String.format(Locale.US, "%.2f GB", this.toDouble() / GB)
        else -> ""
    }
}