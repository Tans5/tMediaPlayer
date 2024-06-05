package com.tans.tmediaplayer.player.model

internal enum class DecodeResult {
    Success,
    SuccessAndSkipNextPkt,
    Fail,
    FailAndNeedMorePkt,
    DecodeEnd
}
internal fun Int.toDecodeResult(): DecodeResult {
    return DecodeResult.entries.find { it.ordinal == this } ?: DecodeResult.Fail
}