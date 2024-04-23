package com.tans.tmediaplayer.demo.glide

import com.bumptech.glide.load.Key
import java.nio.charset.Charset
import java.security.MessageDigest

data class MediaImageModel(
    val mediaFilePath: String,
    val targetPosition: Long
) : Key {

    private val keyBytes: ByteArray by lazy {
        this.toString().toByteArray(Charset.defaultCharset())
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(keyBytes)
    }

}