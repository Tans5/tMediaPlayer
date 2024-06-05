package com.tans.tmediaplayer.player.renderer

internal enum class RendererState {
    NotInit,
    WaitingReadableFrameBuffer,
    Playing,
    Paused,
    Eof,
    Released
}