package com.tans.tmediaplayer.player

enum class OptResult { Success, Fail }

fun Int.toOptResult(): OptResult {
    return if (OptResult.Success.ordinal == this) {
        OptResult.Success
    } else {
        OptResult.Fail
    }
}