package com.tans.tmediaplayer

enum class DecodeResult(val code: Int) {
    DecodeSuccess(0),
    DecodeEnd(1),
    DecodeFail(2)
}