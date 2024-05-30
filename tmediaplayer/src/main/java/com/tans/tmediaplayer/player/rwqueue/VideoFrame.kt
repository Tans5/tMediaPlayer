package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.player.ImageRawType

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
}