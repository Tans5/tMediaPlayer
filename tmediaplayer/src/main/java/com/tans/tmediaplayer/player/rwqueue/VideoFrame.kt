package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.player.model.ImageRawType

internal class VideoFrame(val nativeFrame: Long) {
    var pts: Long = 0L
    var duration: Long = 0L
    var serial: Int = 0
    var imageType: ImageRawType = ImageRawType.Unknown
    var width: Int = 0
    var height: Int = 0
    var yBuffer: ByteArray? = null
    var uBuffer: ByteArray? = null
    var vBuffer: ByteArray? = null
    var uvBuffer: ByteArray? = null
    var rgbaBuffer: ByteArray? = null
    var isEof: Boolean = false

    override fun toString(): String {
        return "[pts=$pts,duration=${duration},serial=${serial},isEof=${isEof}]"
    }

    override fun hashCode(): Int {
        return nativeFrame.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is VideoFrame && other.nativeFrame == nativeFrame
    }
}