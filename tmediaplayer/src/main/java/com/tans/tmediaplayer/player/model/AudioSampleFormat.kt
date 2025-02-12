package com.tans.tmediaplayer.player.model

import androidx.annotation.Keep

@Keep
enum class AudioSampleFormat(val formatId: Int) {
    NONE(-1),
    U8(0),
    S16(1),
    S32(2),
    FLT(3),
    DBL(4),
    U8P(5),
    S16P(6),
    S32P(7),
    FLTP(8),
    DBLP(9),
    S64(10),
    S64P(11),
    NB(12),
}