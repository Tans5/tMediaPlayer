package com.tans.tmediaplayer.subtitle

import androidx.annotation.Keep
import com.tans.tmediaplayer.player.tMediaPlayer

@Suppress("ClassName")
@Keep
internal class tMediaSubtitle(val player: tMediaPlayer) {

    private external fun createSubtitleNative(): Long

    private external fun setupSubtitleStreamFromPlayerNative(subtitleNative: Long, playerNative: Long, streamIndex: Int): Int

    private external fun decodeSubtitleNative(subtitleNative: Long, pktNative: Long): Int

    private external fun flushSubtitleDecoderNative(subtitleNative: Long)

    private external fun allocSubtitleBufferNative(): Long

    private external fun moveDecodedSubtitleFrameToBufferNative(subtitleNative: Long, bufferNative: Long)

    private external fun getSubtitleStartPtsNative(bufferNative: Long): Long

    private external fun getSubtitleEndPtsNative(bufferNative: Long): Long

    private external fun getSubtitleStringsNative(bufferNative: Long): Array<String>

    private external fun releaseSubtitleBufferNative(bufferNative: Long)

    private external fun releaseNative(subtitleNative: Long)

    companion object {
        init {
            System.loadLibrary("tmediasubtitle")
        }
    }
}