package com.tans.tmediaplayer.player

@Suppress("ClassName")
internal enum class tMediaPlayerRendererState {
    NotInit, Prepared, Rendering, Paused, RenderEnd, WaitingDecoder, Released
}