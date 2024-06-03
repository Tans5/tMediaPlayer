package com.tans.tmediaplayer.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class PacketReader(
    private val player: tMediaPlayer2,
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

    private val pktReaderHandler: Handler by lazy {
        object : Handler(pktReaderThread.looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this) {
                    val mediaInfo = player.getMediaInfo()
                    val nativePlayer = mediaInfo?.nativePlayer
                    val state = getState()
                    if (nativePlayer != null && (state == ReaderState.Ready || state == ReaderState.WaitingWritableBuffer)) {
                        when (msg.what) {
                            HandlerMsg.RequestReadPkt.ordinal -> {
                                val audioSizeInBytes = audioPacketQueue.getSizeInBytes()
                                val videoSizeInBytes = videoPacketQueue.getSizeInBytes()
                                val audioDuration = audioPacketQueue.getDuration()
                                val videoDuration = videoPacketQueue.getDuration()

                                val audioQueueIsFull = mediaInfo.audioStreamInfo == null || audioDuration > MAX_QUEUE_DURATION
                                val videoQueueIsFull = mediaInfo.videoStreamInfo == null || mediaInfo.videoStreamInfo.isAttachment || videoDuration > MAX_QUEUE_DURATION
                                if (videoSizeInBytes + audioSizeInBytes > MAX_QUEUE_SIZE_IN_BYTES || (audioQueueIsFull && videoQueueIsFull)) {
                                    // queue full
                                    MediaLog.d(TAG, "Packet queue full, audioSize=${String.format(Locale.US, "%.2f", audioSizeInBytes.toFloat() / 1024.0f)}KB, videoSize=${String.format(Locale.US, "%.2f", videoSizeInBytes.toFloat() / 1024.0f)}KB, audioDuration=$audioDuration, videoDuration=$videoDuration")
                                    this@PacketReader.state.set(ReaderState.WaitingWritableBuffer)
                                } else {
                                    when (val result = player.readPacketInternal(nativePlayer)) {
                                        ReadPacketResult.ReadVideoSuccess, ReadPacketResult.ReadVideoAttachmentSuccess -> {
                                            val pkt = videoPacketQueue.dequeueWriteableForce()
                                            player.movePacketRefInternal(nativePlayer, pkt.nativePacket)
                                            videoPacketQueue.enqueueReadable(pkt)
                                            if (result == ReadPacketResult.ReadVideoAttachmentSuccess) {
                                                val eofPkt = videoPacketQueue.dequeueWriteableForce()
                                                eofPkt.isEof = true
                                                videoPacketQueue.enqueueReadable(eofPkt)
                                                MediaLog.d(TAG, "Read video attachment.")
                                            }
                                            player.readableVideoPacketReady()
                                            requestReadPkt()
                                        }
                                        ReadPacketResult.ReadAudioSuccess -> {
                                            val pkt = audioPacketQueue.dequeueWriteableForce()
                                            player.movePacketRefInternal(nativePlayer, pkt.nativePacket)
                                            audioPacketQueue.enqueueReadable(pkt)
                                            player.readableAudioPacketReady()
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
                                            MediaLog.d(TAG, "Read pkt eof.")
                                        }
                                        ReadPacketResult.ReadFail -> {
                                            MediaLog.e(TAG, "Read pkt fail.")
                                            requestReadPkt()
                                        }
                                        ReadPacketResult.UnknownPkt -> {
                                            requestReadPkt()
                                        }
                                    }
                                    if (state == ReaderState.WaitingWritableBuffer) {
                                        this@PacketReader.state.set(ReaderState.Ready)
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
                                        MediaLog.d(TAG, "Seek to $position success, cost $cost ms")
                                    } else {
                                        MediaLog.e(TAG, "Seek to $position fail, cost $cost ms")
                                    }
                                    player.seekResult(position, result)
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
    }

    fun requestReadPkt() {
        val state = getState()
        if (state == ReaderState.Ready || state == ReaderState.WaitingWritableBuffer) {
            pktReaderHandler.removeMessages(HandlerMsg.RequestReadPkt.ordinal)
            pktReaderHandler.sendEmptyMessage(HandlerMsg.RequestReadPkt.ordinal)
        }
    }

    fun requestSeek(targetPosition: Long) {
        val state = getState()
        if (state == ReaderState.Ready || state == ReaderState.WaitingWritableBuffer) {
            pktReaderHandler.removeMessages(HandlerMsg.RequestSeek.ordinal)
            val msg = pktReaderHandler.obtainMessage()
            msg.what = HandlerMsg.RequestSeek.ordinal
            msg.obj = targetPosition
            pktReaderHandler.sendMessage(msg)
        }
    }

    fun writeablePacketBufferReady() {
        val state = getState()
        if (state == ReaderState.WaitingWritableBuffer) {
            pktReaderHandler.removeMessages(HandlerMsg.RequestReadPkt.ordinal)
            pktReaderHandler.sendEmptyMessage(HandlerMsg.RequestReadPkt.ordinal)
        }
    }

    fun getState(): ReaderState = state.get()

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState == ReaderState.Ready || oldState == ReaderState.WaitingWritableBuffer) {
                state.set(ReaderState.Released)
                removeAllHandlerMessages()
                pktReaderThread.quit()
                pktReaderThread.quitSafely()
            }
        }
    }

    fun removeAllHandlerMessages() {
        for (e in HandlerMsg.entries) {
            pktReaderHandler.removeMessages(e.ordinal)
        }
    }

    companion object {
        private enum class HandlerMsg {
            RequestReadPkt,
            RequestSeek
        }

        enum class ReaderState {
            NotInit,
            Ready,
            WaitingWritableBuffer,
            Released
        }

        private const val TAG = "PacketReader"

        // 15 mb
        private const val MAX_QUEUE_SIZE_IN_BYTES = 15L * 1024L * 1024L
        // 1s
        private const val MAX_QUEUE_DURATION = 1000L
    }
}