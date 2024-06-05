package com.tans.tmediaplayer.player.model

enum class OptResult { Success, Fail }

fun Int.toOptResult(): OptResult {
    return if (OptResult.Success.ordinal == this) {
        OptResult.Success
    } else {
        OptResult.Fail
    }
}