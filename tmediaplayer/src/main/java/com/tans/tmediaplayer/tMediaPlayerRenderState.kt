package com.tans.tmediaplayer

@Suppress("ClassName")
internal enum class tMediaPlayerRenderState {
    NotInit, Prepared, Rendering, Paused, RenderEnd, WaitingDecoder, Released
}