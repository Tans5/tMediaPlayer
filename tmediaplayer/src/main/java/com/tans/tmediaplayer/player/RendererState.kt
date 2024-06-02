package com.tans.tmediaplayer.player

internal enum class RendererState {
    NotInit,
    WaitingReadableFrameBuffer,
    Playing,
    Paused,
    Eof,
    Released
}