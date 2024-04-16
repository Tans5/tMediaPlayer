package com.tans.tmediaplayer

@Suppress("ClassName")
internal enum class tMediaPlayerRendererState {
    NotInit, Prepared, Rendering, Paused, RenderEnd, WaitingDecoder, Released
}