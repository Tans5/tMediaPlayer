package com.tans.tmediaplayer.player.decoder

internal enum class DecoderState {
    NotInit,
    Ready,
    WaitingWritableFrameBuffer,
    WaitingReadablePacketBuffer,
    Eof,
    Released
}