package com.tans.tmediaplayer.player

enum class DecodeResult2 {
    Success,
    SuccessAndSkipNextPkt,
    Fail,
    FailAndNeedMorePkt,
    DecodeEnd
}
fun Int.toDecodeResult2(): DecodeResult2 {
    return DecodeResult2.entries.find { it.ordinal == this } ?: DecodeResult2.Fail
}