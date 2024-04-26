package com.tans.tmediaplayer.demo.glide

import com.bumptech.glide.load.Key
import java.nio.charset.Charset
import java.security.MessageDigest

data class MediaImageModel(
    val mediaFilePath: String,
    val targetPosition: Long,
    val keyId: Long
) : Key {

    private val keyBytes: ByteArray by lazy {
        "$targetPosition$keyId".toByteArray(Charset.defaultCharset())
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(keyBytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaImageModel

        if (targetPosition != other.targetPosition) return false
        if (keyId != other.keyId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + targetPosition.hashCode()
        return result
    }

}