package com.tans.tmediaplayer.subtitle

import androidx.annotation.Keep
import com.tans.tmediaplayer.player.tMediaPlayer

@Suppress("ClassName")
@Keep
internal class tMediaSubtitle(private val player: tMediaPlayer) {


    private external fun createSubtitleNative(): Long

    private external fun setupSubtitleStreamFromPlayerNative(
        subtitleNative: Long,
        playerNative: Long,
        streamIndex: Int
    ): Int

    private external fun decodeSubtitleNative(subtitleNative: Long, pktNative: Long): Int

    private external fun flushSubtitleDecoderNative(subtitleNative: Long)

    private external fun allocSubtitleBufferNative(): Long

    private external fun moveDecodedSubtitleFrameToBufferNative(subtitleNative: Long, bufferNative: Long)

    private external fun releaseSubtitleBufferNative(bufferNative: Long)

    private external fun releaseNative(subtitleNative: Long)

    companion object {
        init {
            System.loadLibrary("tmediasubtitle")
        }
    }
}