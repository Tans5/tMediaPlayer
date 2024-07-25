package com.tans.tmediaplayer.player.rwqueue

internal class AudioFrame(val nativeFrame: Long) {
    var pts: Long = 0L
    var duration: Long = 0L
    var serial: Int = 0
    var isEof: Boolean = false

    override fun toString(): String {
        return "[pts=$pts,duration=${duration},serial=${serial},isEof=${isEof}]"
    }

    override fun hashCode(): Int {
        return nativeFrame.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is AudioFrame && other.nativeFrame == nativeFrame
    }

    fun reset() {
        pts = 0L
        duration = 0L
        serial = 0
        isEof = false
    }
}