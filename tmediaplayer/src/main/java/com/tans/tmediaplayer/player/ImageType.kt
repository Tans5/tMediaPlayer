package com.tans.tmediaplayer.player

enum class ImageRawType {
    Yuv420p,
    Nv12,
    Nv21,
    Rgba,
    Unknown
}

internal fun Int.toImageRawType(): ImageRawType {
    return when (this) {
        ImageRawType.Yuv420p.ordinal -> ImageRawType.Yuv420p
        ImageRawType.Nv12.ordinal -> ImageRawType.Nv12
        ImageRawType.Nv21.ordinal -> ImageRawType.Nv21
        ImageRawType.Rgba.ordinal -> ImageRawType.Rgba
        else -> ImageRawType.Unknown
    }
}