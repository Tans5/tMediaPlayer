package com.tans.tmediaplayer.player.playerview.texconverter

import android.content.Context
import com.tans.tmediaplayer.player.playerview.ImageDataType

internal interface ImageTextureConverter {

    fun convertImageToTexture(
        context: Context,
        surfaceWidth: Int,
        surfaceHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        rgbaBytes: ByteArray?,
        yBytes: ByteArray?,
        uBytes: ByteArray?,
        vBytes: ByteArray?,
        uvBytes: ByteArray?,
        imageDataType: ImageDataType
    ): Int

    fun recycle()
}