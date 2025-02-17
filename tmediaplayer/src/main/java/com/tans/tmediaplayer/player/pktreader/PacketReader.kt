package com.tans.tmediaplayer.player.pktreader

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.ReadPacketResult
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class PacketReader(
    private val player: tMediaPlayer,
    private val audioPacketQueue: PacketQueue,
    private val videoPacketQueue: PacketQueue
) {

    private val state: AtomicReference<ReaderState> = AtomicReference(ReaderState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Reader thread.
    private val pktReaderThread: HandlerThread by lazy {
        object : HandlerThread("tMP_PktReader", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val activeStates = arrayOf(ReaderState.Ready, ReaderState.WaitingWritableBuffer, ReaderState.Eof)

    private val requestAttachment: AtomicBoolean = AtomicBoolean(true)

    private val pktReaderHandler: Handler by lazy {
        object : Handler(pktReaderThread.looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@PacketReader) {
                    val mediaInfo = player.getMediaInfo()
                    val nativePlayer = mediaInfo?.nativePlayer
                    val state = getState()
                    if (nativePlayer != null && state in activeStates) {
                        when (msg.what) {
                            HandlerMsg.RequestReadPkt.ordinal -> {
                                val audioSizeInBytes = audioPacketQueue.getSizeInBytes()
                                val videoSizeInBytes = videoPacketQueue.getSizeInBytes()
                                val audioDuration = audioPacketQueue.getDuration()
                                val videoDuration = videoPacketQueue.getDuration()

                                val audioQueueIsFull = mediaInfo.audioStreamInfo == null || audioDuration > MAX_QUEUE_DURATION
                                val videoQueueIsFull = mediaInfo.videoStreamInfo == null || mediaInfo.videoStreamInfo.isAttachment || videoDuration > MAX_QUEUE_DURATION
                                if (!mediaInfo.isRealTime && (videoSizeInBytes + audioSizeInBytes > MAX_QUEUE_SIZE_IN_BYTES || (audioQueueIsFull && videoQueueIsFull))) {
                                    // queue full
                                    tMediaPlayerLog.d(TAG) {
                                        "Packet queue full, audioSize=${String.format(Locale.US, "%.2f", audioSizeInBytes.toFloat() / 1024.0f)}KB, videoSize=${String.format(Locale.US, "%.2f", videoSizeInBytes.toFloat() / 1024.0f)}KB, audioDuration=$audioDuration, videoDuration=$videoDuration"
                                    }
                                    this@PacketReader.state.set(ReaderState.WaitingWritableBuffer)
                                } else {
                                    if (state == ReaderState.WaitingWritableBuffer) {
                                        this@PacketReader.state.set(ReaderState.Ready)
                                    }
                                    when (player.readPacketInternal(nativePlayer)) {
                                        ReadPacketResult.ReadVideoAttachmentSuccess -> {
                                            if (requestAttachment.compareAndSet(true, false)) {
                                                val pkt = videoPacketQueue.dequeueWriteableForce()
                                                player.movePacketRefInternal(nativePlayer, pkt.nativePacket)
                                                videoPacketQueue.enqueueReadable(pkt)
                                                val eofPkt = videoPacketQueue.dequeueWriteableForce()
                                                eofPkt.isEof = true
                                                videoPacketQueue.enqueueReadable(eofPkt)
                                                player.readableVideoPacketReady()
                                                tMediaPlayerLog.d(TAG) { "Read video attachment." }
                                            } else {
                                                tMediaPlayerLog.d(TAG) { "Skip handle video attachment." }
                                            }
                                            requestReadPkt()
                                        }
                                        ReadPacketResult.ReadVideoSuccess -> {
                                            val pkt = videoPacketQueue.dequeueWriteableForce()
                                            player.movePacketRefInternal(nativePlayer, pkt.nativePacket)
                                            videoPacketQueue.enqueueReadable(pkt)
                                            tMediaPlayerLog.d(TAG) { "Read video pkt: $pkt" }
                                            player.readableVideoPacketReady()
                                            requestReadPkt()

                                        }
                                        ReadPacketResult.ReadAudioSuccess -> {
                                            val pkt = audioPacketQueue.dequeueWriteableForce()
                                            player.movePacketRefInternal(nativePlayer, pkt.nativePacket)
                                            audioPacketQueue.enqueueReadable(pkt)
                                            tMediaPlayerLog.d(TAG) { "Read audio pkt: $pkt" }
                                            player.readableAudioPacketReady()
                                            requestReadPkt()
                                        }
                                        ReadPacketResult.ReadSubtitleSuccess -> {
                                            tMediaPlayerLog.d(TAG) { "Read subtitle pkt." }
                                            player.getInternalSubtitle()?.enqueueSubtitlePacket()
                                            requestReadPkt()
                                        }
                                        ReadPacketResult.ReadEof -> {
                                            if (mediaInfo.videoStreamInfo != null && !mediaInfo.videoStreamInfo.isAttachment) {
                                                val videoEofPkt = videoPacketQueue.dequeueWriteableForce()
                                                videoEofPkt.isEof = true
                                                videoPacketQueue.enqueueReadable(videoEofPkt)
                                                player.readableVideoPacketReady()
                                            }
                                            if (mediaInfo.audioStreamInfo != null) {
                                                val audioEofPkt = audioPacketQueue.dequeueWriteableForce()
                                                audioEofPkt.isEof = true
                                                audioPacketQueue.enqueueReadable(audioEofPkt)
                                                player.readableAudioPacketReady()
                                            }
                                            tMediaPlayerLog.d(TAG) { "Read pkt eof." }
                                            this@PacketReader.state.set(ReaderState.Eof)
                                        }
                                        ReadPacketResult.ReadFail -> {
                                            tMediaPlayerLog.e(TAG) { "Read pkt fail." }
                                            requestReadPkt()
                                        }
                                        ReadPacketResult.UnknownPkt -> {
                                            requestReadPkt()
                                        }
                                    }
                                }
                            }

                            HandlerMsg.RequestSeek.ordinal -> {
                                val position = msg.obj
                                if (position is Long) {
                                    val start = SystemClock.uptimeMillis()
                                    val result = player.seekToInternal(nativePlayer, position)
                                    val end = SystemClock.uptimeMillis()
                                    val cost = end - start
                                    if (result == OptResult.Success) {
                                        audioPacketQueue.flushReadableBuffer()
                                        videoPacketQueue.flushReadableBuffer()
                                        player.getExternalSubtitle()?.requestSeek(position)
                                        tMediaPlayerLog.d(TAG) { "Seek to $position success, cost $cost ms" }
                                        this@PacketReader.state.compareAndSet(ReaderState.Eof, ReaderState.Ready)
                                    } else {
                                        tMediaPlayerLog.e(TAG) { "Seek to $position fail, cost $cost ms" }
                                    }
                                    player.seekResult(position, result)
                                    player.getInternalSubtitle()?.flushDecoder()
                                    requestReadPkt()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        pktReaderThread
        while (!isLooperPrepared.get()) {}
        pktReaderHandler
        state.set(ReaderState.Ready)
        tMediaPlayerLog.d(TAG) { "Packet reader inited." }
    }

    fun requestReadPkt() {
        val state = getState()
        if (state in activeStates) {
            pktReaderHandler.removeMessages(HandlerMsg.RequestReadPkt.ordinal)
            pktReaderHandler.sendEmptyMessage(HandlerMsg.RequestReadPkt.ordinal)
        } else {
            tMediaPlayerLog.e(TAG) { "Request read pkt fail, wrong state: $state" }
        }
    }

    fun requestSeek(targetPosition: Long) {
        val state = getState()
        if (state in activeStates) {
            pktReaderHandler.removeMessages(HandlerMsg.RequestSeek.ordinal)
            val msg = pktReaderHandler.obtainMessage()
            msg.what = HandlerMsg.RequestSeek.ordinal
            msg.obj = targetPosition
            pktReaderHandler.sendMessage(msg)
        } else {
            tMediaPlayerLog.e(TAG) { "Request seek fail, wrong state: $state" }
        }
    }

    fun requestAttachment() {
        requestAttachment.set(true)
    }

    fun writeablePacketBufferReady() {
        val state = getState()
        if (state == ReaderState.WaitingWritableBuffer) {
            requestReadPkt()
        }
    }

    fun getState(): ReaderState = state.get()

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState != ReaderState.NotInit && oldState != ReaderState.Released) {
                state.set(ReaderState.Released)
                pktReaderThread.quit()
                pktReaderThread.quitSafely()
                tMediaPlayerLog.d(TAG) { "Package reader released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release fail, wrong state: $state" }
            }
        }
    }

    companion object {

        private enum class HandlerMsg {
            RequestReadPkt,
            RequestSeek
        }

        private const val TAG = "PacketReader"

        // 15 mb
        private const val MAX_QUEUE_SIZE_IN_BYTES = 15L * 1024L * 1024L
        // 1s
        private const val MAX_QUEUE_DURATION = 1000L
    }
}