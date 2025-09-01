package com.tans.tmediaplayer.subtitle

internal class SubtitleFrame(val nativeFrame: Long) {
    var serial: Int = 0
    var startPts: Long = 0L
    var endPts: Long = 0L
    var width: Int = 0
    var height: Int = 0
    var rgbaBytes: ByteArray? = null


    override fun toString(): String {
        return "[startPts=$startPts,endPts=$endPts]"
    }

    override fun hashCode(): Int {
        return nativeFrame.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is SubtitleFrame && other.nativeFrame == nativeFrame
    }

    fun reset() {
        serial = 0
        startPts = 0L
        endPts = 0L
    }
}