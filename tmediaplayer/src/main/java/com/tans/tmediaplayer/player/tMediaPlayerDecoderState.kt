package com.tans.tmediaplayer.player

@Suppress("ClassName")
internal enum class tMediaPlayerDecoderState {
    NotInit, Prepared, Decoding, Paused, DecodingEnd, WaitingRender, Released
}