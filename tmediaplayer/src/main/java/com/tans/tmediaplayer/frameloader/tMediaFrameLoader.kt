package com.tans.tmediaplayer.frameloader

@Suppress("ClassName")
object tMediaFrameLoader {
    init {
        System.loadLibrary("tmediaframeloader")
    }


    private external fun createFrameLoaderNative(): Long

    private external fun prepareNative(nativeFrameLoader: Long, filePath: String): Int

    private external fun getFrameNative(nativeFrameLoader: Long, position: Long, needRealTime: Boolean): Int

    private external fun releaseNative(nativeFrameLoader: Long)
}