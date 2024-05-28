package com.tans.tmediaplayer.player

enum class ReadPacketResult {
    ReadVideoSuccess,
    ReadVideoAttachmentSuccess,
    ReadAudioSuccess,
    ReadFail,
    ReadEof,
    UnknownPkt
}

fun Int.toReadPacketResult(): ReadPacketResult {
    return ReadPacketResult.entries.find { it.ordinal == this } ?: ReadPacketResult.ReadFail
}