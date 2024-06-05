package com.tans.tmediaplayer.player

enum class DecodeResult {
    Success,
    SuccessAndSkipNextPkt,
    Fail,
    FailAndNeedMorePkt,
    DecodeEnd
}
fun Int.toDecodeResult(): DecodeResult {
    return DecodeResult.entries.find { it.ordinal == this } ?: DecodeResult.Fail
}