package com.tans.tmediaplayer.player

internal enum class DecoderState {
    NotInit,
    Ready,
    WaitingWritableFrameBuffer,
    WaitingReadablePacketBuffer,
    Eof,
    Released
}