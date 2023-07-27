package com.tans.tmediaplayer

enum class DecodeResult { Success, DecodeEnd, Fail }
fun Int.toDecodeResult(): DecodeResult {
    return when (this) {
        DecodeResult.Success.ordinal -> DecodeResult.Success
        DecodeResult.DecodeEnd.ordinal -> DecodeResult.DecodeEnd
        else -> DecodeResult.Fail
    }
}