package com.tans.tmediaplayer.player.model

internal enum class ReadPacketResult {
    ReadVideoSuccess,
    ReadVideoAttachmentSuccess,
    ReadAudioSuccess,
    ReadFail,
    ReadEof,
    UnknownPkt
}

internal fun Int.toReadPacketResult(): ReadPacketResult {
    return ReadPacketResult.entries.find { it.ordinal == this } ?: ReadPacketResult.ReadFail
}