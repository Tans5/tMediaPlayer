package com.tans.tmediaplayer.player.playerview.filter

import android.content.Context

interface ImageFilter {

    fun enable(enable: Boolean)

    fun isEnable(): Boolean

    fun filter(
        context: Context,
        surfaceWidth: Int,
        surfaceHeight: Int,
        input: FilterImageTexture,
        output: FilterImageTexture
    )

    fun recycle()
}