package com.tans.tmediaplayer.subtitle

internal class SubtitleFrame(val nativeFrame: Long) {
    var startPts: Long = 0L
    var endPts: Long = 0L
    var subtitles: List<String>? = null

    override fun toString(): String {
        return "[startPts=$startPts,endPts=$endPts,subtitles=${subtitles?.joinToString()},subtitleSize=${subtitles?.size}]"
    }

    override fun hashCode(): Int {
        return nativeFrame.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is SubtitleFrame && other.nativeFrame == nativeFrame
    }

    fun reset() {
        startPts = 0L
        endPts = 0L
        subtitles = null
    }
}