package com.tans.tmediaplayer.player.rwqueue

internal class Packet(val nativePacket: Long) {
    var streamIndex: Int = -1
    var pts: Long = 0L
    var duration: Long = 0L
    var sizeInBytes: Int = 0
    var serial: Int = 0
    var isEof: Boolean = false

    override fun toString(): String {
        return "[streamIndex=${streamIndex},pts=$pts,duration=${duration},sizeInBytes=${sizeInBytes},serial=${serial},isEof=${isEof}]"
    }

    override fun hashCode(): Int {
        return nativePacket.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is Packet && other.nativePacket == nativePacket
    }

    fun reset() {
        streamIndex = -1
        pts = 0L
        duration = 0L
        sizeInBytes = 0
        serial = 0
        isEof = false
    }
}