package com.tans.tmediaplayer

@Suppress("ClassName")
internal enum class tMediaPlayerDecoderState {
    NotInit, Prepared, Decoding, Paused, DecodingEnd, WaitingRender, Released
}