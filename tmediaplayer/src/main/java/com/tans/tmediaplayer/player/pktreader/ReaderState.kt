package com.tans.tmediaplayer.player.pktreader

internal enum class ReaderState {
    NotInit,
    Ready,
    WaitingWritableBuffer,
    Released
}