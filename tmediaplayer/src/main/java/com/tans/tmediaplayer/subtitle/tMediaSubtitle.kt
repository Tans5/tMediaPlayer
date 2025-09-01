package com.tans.tmediaplayer.subtitle

import android.os.HandlerThread
import androidx.annotation.Keep
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.SUBTITLE_MAX_PKT_SIZE
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
        PacketQueue(player, SUBTITLE_MAX_PKT_SIZE)
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

    fun setupSubtitleStreamFromPlayer(streamIndex: Int): OptResult {
        val mediaInfo = player.getMediaInfo()
        val nativePlayer = mediaInfo?.nativePlayer
        val nativeSubtitle = subtitleNative.get()
        val frameWidth = mediaInfo?.videoStreamInfo?.videoWidth
        val frameHeight = mediaInfo?.videoStreamInfo?.videoHeight
        return if (nativePlayer != null && nativeSubtitle != null && frameWidth != null && frameHeight != null) {
            setupSubtitleStreamFromPlayerNative(
                subtitleNative = nativeSubtitle,
                playerNative = nativePlayer,
                streamIndex = streamIndex,
                frameWidth = frameWidth,
                frameHeight = frameHeight
            ).toOptResult().apply {
                if (this == OptResult.Fail) {
                    tMediaPlayerLog.e(TAG) { "Setup subtitle stream from player fail, nativePlayer=$nativePlayer, nativeSubtitle=$nativeSubtitle, streamIndex=$streamIndex" }
                }
            }
        } else {
            tMediaPlayerLog.e(TAG) { "Setup subtitle stream from player fail, nativePlayer=$nativePlayer, nativeSubtitle=$nativeSubtitle, streamIndex=$streamIndex" }
            OptResult.Fail
        }
    }

    internal fun setupSubtitleStreamFromPktReader(readerNative: Long): OptResult {
        val mediaInfo = player.getMediaInfo()
        val nativeSubtitle = subtitleNative.get()
        val frameWidth = mediaInfo?.videoStreamInfo?.videoWidth
        val frameHeight = mediaInfo?.videoStreamInfo?.videoHeight
        return if (nativeSubtitle != null && frameWidth != null && frameHeight != null) {
            setupSubtitleStreamFromPktReaderNative(
                subtitleNative = nativeSubtitle,
                readerNative = readerNative,
                frameWidth = frameWidth,
                frameHeight = frameHeight
            ).toOptResult().apply {
                if (this == OptResult.Fail) {
                    tMediaPlayerLog.e(TAG) { "Setup subtitle stream from pkt reader fail, readerNative=$readerNative, nativeSubtitle=$nativeSubtitle" }
                }
            }
        } else {
            tMediaPlayerLog.e(TAG) { "Setup subtitle stream from pkt reader fail, readerNative=$readerNative, nativeSubtitle=$nativeSubtitle" }
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
    fun release() {
        synchronized(decoder) {
            synchronized(renderer) {
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
    }

    private external fun createSubtitleNative(): Long

    private external fun setupSubtitleStreamFromPlayerNative(subtitleNative: Long, playerNative: Long, streamIndex: Int, frameWidth: Int, frameHeight: Int): Int

    private external fun setupSubtitleStreamFromPktReaderNative(subtitleNative: Long, readerNative: Long, frameWidth: Int, frameHeight: Int): Int

    internal fun decodeSubtitleInternal(pktNative: Long): DecodeResult {
        val subtitleNative = subtitleNative.get()
        return if (subtitleNative != null) {
            decodeSubtitleNative(subtitleNative = subtitleNative, pktNative = pktNative).toDecodeResult()
        } else {
            DecodeResult.Fail
        }
    }

    private external fun decodeSubtitleNative(subtitleNative: Long, pktNative: Long): Int

    internal fun moveDecodedSubtitleFrameToBufferInternal(subtitleBufferNative: Long): OptResult {
        val subtitleNative = subtitleNative.get()
        return if (subtitleNative != null) {
            moveDecodedSubtitleFrameToBufferNative(subtitleNative = subtitleNative, subtitleBufferNative = subtitleBufferNative).toOptResult()
        } else {
            OptResult.Fail
        }
    }

    private external fun moveDecodedSubtitleFrameToBufferNative(subtitleNative: Long, subtitleBufferNative: Long): Int

    private external fun flushSubtitleDecoderNative(subtitleNative: Long)

    internal fun allocSubtitleBufferInternal(): Long = allocSubtitleBufferNative()

    private external fun allocSubtitleBufferNative(): Long

    internal fun getSubtitleStartPtsInternal(bufferNative: Long): Long = getSubtitleStartPtsNative(bufferNative)

    private external fun getSubtitleStartPtsNative(bufferNative: Long): Long

    internal fun getSubtitleEndPtsInternal(bufferNative: Long): Long = getSubtitleEndPtsNative(bufferNative)

    private external fun getSubtitleEndPtsNative(bufferNative: Long): Long

    internal fun getSubtitleWidthInternal(bufferNative: Long): Int = getSubtitleWidthNative(bufferNative)

    private external fun getSubtitleWidthNative(bufferNative: Long): Int

    internal fun getSubtitleHeightInternal(bufferNative: Long): Int = getSubtitleHeightNative(bufferNative)

    private external fun getSubtitleHeightNative(bufferNative: Long): Int

    internal fun getSubtitleFrameRgbaBytesInternal(bufferNative: Long, buffers: ByteArray) {
        getSubtitleFrameRgbaBytesNative(bufferNative, buffers)
    }

    private external fun getSubtitleFrameRgbaBytesNative(bufferNative: Long, buffers: ByteArray)

    internal fun releaseSubtitleBufferInternal(bufferNative: Long) {
        releaseSubtitleBufferNative(bufferNative)
    }

    private external fun releaseSubtitleBufferNative(bufferNative: Long)

    private external fun releaseNative(subtitleNative: Long)

    companion object {
        init {
            System.loadLibrary("tmediasubtitle")
        }

        private const val TAG = "tMediaSubtitle"
    }
}