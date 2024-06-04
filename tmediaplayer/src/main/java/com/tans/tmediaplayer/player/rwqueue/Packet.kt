package com.tans.tmediaplayer.player.rwqueue

internal class Packet(val nativePacket: Long) {
    var pts: Long = 0L
    var duration: Long = 0L
    var sizeInBytes: Int = 0
    var serial: Int = 0
    var isEof: Boolean = false

    override fun toString(): String {
        return "[pts=$pts,duration=${duration},sizeInBytes=${sizeInBytes},serial=${serial},isEof=${isEof}]"
    }
}