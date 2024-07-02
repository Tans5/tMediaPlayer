package com.tans.tmediaplayer.subtitle

import android.os.HandlerThread
import androidx.annotation.Keep
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.toDecodeResult
import com.tans.tmediaplayer.player.model.toOptResult
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
@Keep
internal class tMediaSubtitle(val player: tMediaPlayer) {

    private val subtitleNative: AtomicReference<Long?> = AtomicReference(null)

    val packetQueue: PacketQueue by lazy {
        PacketQueue(player)
    }

    val frameQueue: SubtitleFrameQueue by lazy {
        SubtitleFrameQueue(this)
    }

    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    private val subtitleThread: HandlerThread by lazy {
        object : HandlerThread("tMP_Subtitle", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    val decoder: SubtitleFrameDecoder

    val renderer: SubtitleRenderer

    init {
        subtitleNative.set(createSubtitleNative())
        subtitleThread
        while (!isLooperPrepared.get()) {}
        decoder = SubtitleFrameDecoder(this, subtitleThread.looper)
        renderer = SubtitleRenderer(player, this, subtitleThread.looper)
    }

    fun getNativeSubtitle(): Long? = subtitleNative.get()


    fun setupSubtitleStreamFromPlayer(streamIndex: Int): OptResult {
        val nativePlayer = player.getMediaInfo()?.nativePlayer
        val nativeSubtitle = subtitleNative.get()
        return if (nativePlayer != null && nativeSubtitle != null) {
            setupSubtitleStreamFromPlayerNative(
                subtitleNative = nativeSubtitle,
                playerNative = nativePlayer,
                streamIndex = streamIndex
            ).toOptResult().apply {
                if (this == OptResult.Fail) {
                    MediaLog.e(TAG, "Setup subtitle stream fail, nativePlayer=$nativePlayer, nativeSubtitle=$nativeSubtitle, streamIndex=$streamIndex")
                }
            }
        } else {
            MediaLog.e(TAG, "Setup subtitle stream fail, nativePlayer=$nativePlayer, nativeSubtitle=$nativeSubtitle, streamIndex=$streamIndex")
            OptResult.Fail
        }
    }

    @Synchronized
    fun flushSubtitleDecoder() {
        val subtitleNative = subtitleNative.get()
        if (subtitleNative != null) {
            flushSubtitleDecoderNative(subtitleNative)
        }
    }

    @Synchronized
    fun play() {
        renderer.play()
    }

    @Synchronized
    fun pause() {
        renderer.pause()
    }

    @Synchronized
    fun playerProgressUpdated() {
        renderer.playerProgressUpdated()
    }

    @Synchronized
    fun release() {
        synchronized(decoder) {
            val native = subtitleNative.get()
            if (native != null) {
                subtitleNative.set(null)
                decoder.release()
                renderer.release()
                packetQueue.release()
                frameQueue.release()
                releaseNative(native)
                subtitleThread.quit()
                subtitleThread.quitSafely()
            }
        }
    }

    fun readableFrameReady() {
        renderer.readableFrameReady()
    }

    fun writeableFrameReady() {
        decoder.writeableFrameReady()
    }

    private external fun createSubtitleNative(): Long

    private external fun setupSubtitleStreamFromPlayerNative(subtitleNative: Long, playerNative: Long, streamIndex: Int): Int

    internal fun decodeSubtitleInternal(pktNative: Long): DecodeResult {
        val subtitleNative = subtitleNative.get()
        return if (subtitleNative != null) {
            decodeSubtitleNative(subtitleNative = subtitleNative, pktNative = pktNative).toDecodeResult()
        } else {
            DecodeResult.Fail
        }
    }

    private external fun decodeSubtitleNative(subtitleNative: Long, pktNative: Long): Int

    private external fun flushSubtitleDecoderNative(subtitleNative: Long)

    internal fun allocSubtitleBufferInternal(): Long = allocSubtitleBufferNative()

    private external fun allocSubtitleBufferNative(): Long

    internal fun moveDecodedSubtitleFrameToBufferInternal(bufferNative: Long) {
        val subtitleNative = subtitleNative.get()
        if (subtitleNative != null) {
            moveDecodedSubtitleFrameToBufferNative(subtitleNative = subtitleNative, bufferNative = bufferNative)
        }
    }

    private external fun moveDecodedSubtitleFrameToBufferNative(subtitleNative: Long, bufferNative: Long)

    internal fun getSubtitleStartPtsInternal(bufferNative: Long): Long = getSubtitleStartPtsNative(bufferNative)

    private external fun getSubtitleStartPtsNative(bufferNative: Long): Long

    internal fun getSubtitleEndPtsInternal(bufferNative: Long): Long = getSubtitleEndPtsNative(bufferNative)

    private external fun getSubtitleEndPtsNative(bufferNative: Long): Long

    internal fun getSubtitleStringsInternal(bufferNative: Long): Array<String> = getSubtitleStringsNative(bufferNative)

    private external fun getSubtitleStringsNative(bufferNative: Long): Array<String>

    internal fun releaseSubtitleBufferInternal(bufferNative: Long) = releaseSubtitleBufferNative(bufferNative)

    private external fun releaseSubtitleBufferNative(bufferNative: Long)

    private external fun releaseNative(subtitleNative: Long)

    companion object {
        init {
            System.loadLibrary("tmediasubtitle")
        }

        private const val TAG = "tMediaSubtitle"
    }
}