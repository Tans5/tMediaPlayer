package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import androidx.annotation.Keep
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.ReadPacketResult
import com.tans.tmediaplayer.player.model.toOptResult
import com.tans.tmediaplayer.player.model.toReadPacketResult
import com.tans.tmediaplayer.player.pktreader.ReaderState
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Keep
internal class ExternalSubtitle(val player: tMediaPlayer) {

    private val state: AtomicReference<ReaderState> = AtomicReference(ReaderState.NotInit)

    private val externalSubtitlePktReaderNative: AtomicReference<Long?> = AtomicReference(null)

    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    private val loadedFile: AtomicReference<String?> = AtomicReference(null)

    private val pktReaderThread: HandlerThread by lazy {
        object : HandlerThread("tMP_ExtSubPktReader", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val activeStates = arrayOf(ReaderState.Ready, ReaderState.WaitingWritableBuffer)

    private val subtitle: tMediaSubtitle by lazy {
        tMediaSubtitle(
            player = player,
            writeablePktReady = {
                writeablePktReady()
            }
        )
    }

    private val readerHandler: Handler by lazy {
        object : Handler(pktReaderThread.looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@ExternalSubtitle) {
                    val readerNative = externalSubtitlePktReaderNative.get()
                    val state = getState()
                    if (readerNative != null && state in activeStates) {
                        when (msg.what) {
                            HandlerMsg.RequestLoadFile.ordinal -> {
                                val filePath = msg.obj
                                if (filePath is String) {
                                    val loadResult = loadFileNative(readerNative = readerNative, file = filePath).toOptResult()
                                    if (loadResult == OptResult.Success) {
                                        MediaLog.d(TAG, "Load subtitle file: $filePath, success.")
                                        val pts = player.getProgress()
                                        val seekResult = seekToNative(readerNative = readerNative, position = pts).toOptResult()
                                        MediaLog.d(TAG, "Seek to $pts result: $seekResult")
                                        requestReadPkt()
                                        subtitle.decoder.requestSetupExternalSubtitleStream(readerNative)
                                    } else {
                                        readerHandler.removeMessages(HandlerMsg.RequestReadPkt.ordinal)
                                        MediaLog.e(TAG, "Load subtitle file: $filePath, fail.")
                                    }
                                }
                            }
                            HandlerMsg.RequestSeek.ordinal -> {
                                val position = msg.obj
                                if (position is Long) {
                                    val result = seekToNative(readerNative = readerNative, position = position).toOptResult()
                                    MediaLog.d(TAG, "Seek to $position result: $result")
                                    subtitle.decoder.requestFlushDecoder()
                                    requestReadPkt()
                                }
                            }
                            HandlerMsg.RequestReadPkt.ordinal -> {
                                val pktQueue = subtitle.packetQueue
                                val queueSize = pktQueue.readableQueueSize()
                                if (queueSize >= MAX_PKT_SIZE) {
                                    MediaLog.d(TAG, "Packet queue full, queueSize=$queueSize.")
                                    this@ExternalSubtitle.state.set(ReaderState.WaitingWritableBuffer)
                                } else {
                                    when (readPacketNative(readerNative).toReadPacketResult()) {
                                        ReadPacketResult.ReadSubtitleSuccess -> {
                                            val pkt = pktQueue.dequeueWriteableForce()
                                            movePacketRefNative(readerNative = readerNative, packetNative = pkt.nativePacket)
                                            pktQueue.enqueueReadable(pkt)
                                            subtitle.decoder.readablePacketReady()
                                            requestReadPkt()
                                            MediaLog.d(TAG, "Read subtitle pkt: $pkt")
                                        }
                                        ReadPacketResult.ReadFail -> {
                                            MediaLog.e(TAG, "Read subtitle pkt fail.")
                                            requestReadPkt(100L)
                                        }
                                        ReadPacketResult.ReadEof -> {
                                            MediaLog.d(TAG, "Read subtitle pkt eof.")
                                        }
                                        ReadPacketResult.ReadVideoSuccess,
                                        ReadPacketResult.ReadVideoAttachmentSuccess,
                                        ReadPacketResult.ReadAudioSuccess,
                                        ReadPacketResult.UnknownPkt -> {
                                            requestReadPkt()
                                        }
                                    }

                                    if (state == ReaderState.WaitingWritableBuffer) {
                                        this@ExternalSubtitle.state.set(ReaderState.Ready)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        externalSubtitlePktReaderNative.set(createExternalSubtitlePktReaderNative())
        pktReaderThread
        while (!isLooperPrepared.get()) {}
        readerHandler
        subtitle
        state.set(ReaderState.Ready)
        MediaLog.d(TAG, "Subtitle packet reader inited.")
    }

    fun getState(): ReaderState = state.get()

    fun requestLoadFile(file: String) {
        val lastLoadedFile = loadedFile.get()
        if (lastLoadedFile != file) {
            val state = getState()
            if (state in activeStates) {
                loadedFile.set(file)
                readerHandler.removeMessages(HandlerMsg.RequestLoadFile.ordinal)
                val msg = readerHandler.obtainMessage(HandlerMsg.RequestLoadFile.ordinal, file)
                readerHandler.sendMessage(msg)
            } else {
                MediaLog.e(TAG, "Request load file: $file fail, wrong state: $state")
            }
        }
    }

    fun getLoadedFile(): String? = loadedFile.get()

    fun requestSeek(targetPosition: Long) {
        val state = getState()
        if (state in activeStates) {
            readerHandler.removeMessages(HandlerMsg.RequestSeek.ordinal)
            val msg = readerHandler.obtainMessage(HandlerMsg.RequestSeek.ordinal, targetPosition)
            readerHandler.sendMessage(msg)
        } else {
            MediaLog.e(TAG, "Request seek fail, wrong state: $state")
        }
    }

    fun play() {
        subtitle.play()
    }

    fun pause() {
        subtitle.pause()
    }

    fun playerProgressUpdated() {
        subtitle.playerProgressUpdated()
    }

    fun release() {
        synchronized(this) {
            val readerNative = externalSubtitlePktReaderNative.get()
            val state = state.get()
            if (state != ReaderState.NotInit && state != ReaderState.Released) {
                this.state.set(ReaderState.Released)
                externalSubtitlePktReaderNative.set(null)
                subtitle.release()
                pktReaderThread.quit()
                pktReaderThread.quitSafely()
                if (readerNative != null) {
                    releaseNative(readerNative)
                }
                loadedFile.set(null)
            }
        }
    }

    private fun writeablePktReady() {
        val state = getState()
        if (state == ReaderState.WaitingWritableBuffer) {
            requestReadPkt()
        }
    }

    private fun requestReadPkt(delay: Long = 0L) {
        val state = getState()
        if (state in activeStates) {
            readerHandler.removeMessages(HandlerMsg.RequestReadPkt.ordinal)
            readerHandler.sendEmptyMessageDelayed(HandlerMsg.RequestReadPkt.ordinal, delay)
        } else {
            MediaLog.e(TAG, "Request read pkt fail, wrong state: $state")
        }
    }


    private external fun createExternalSubtitlePktReaderNative(): Long

    private external fun loadFileNative(readerNative: Long, file: String): Int

    private external fun seekToNative(readerNative: Long, position: Long): Int

    private external fun readPacketNative(readerNative: Long): Int

    private external fun movePacketRefNative(readerNative: Long, packetNative: Long)

    private external fun releaseNative(readerNative: Long)

    companion object {

        init {
            System.loadLibrary("tmediasubtitlepktreader")
        }

        private enum class HandlerMsg {
            RequestReadPkt,
            RequestSeek,
            RequestLoadFile
        }

        private const val TAG = "ExternalSubtitle"

        private const val MAX_PKT_SIZE = 8
    }
}