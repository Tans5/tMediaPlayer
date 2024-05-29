package com.tans.tmediaplayer.player.rwqueue

internal class AudioFrame(val nativeFrame: Long) {
    var pts: Long = 0L
    var serial: Int = 0
    var pcmBuffer: ByteArray? = null
    var isEof: Boolean = false
}