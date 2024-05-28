package com.tans.tmediaplayer.player.rwqueue

internal class Packet(val nativePacket: Long) {
    var pts: Long = 0L
    var duration: Long = 0L
    var sizeInBytes: Int = 0
    var serial: Int = 0
}