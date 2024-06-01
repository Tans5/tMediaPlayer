package com.tans.tmediaplayer.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class VideoFrameDecoder(
    private val player: tMediaPlayer2,
    private val videoPacketQueue: PacketQueue,
    private val videoFrameQueue: VideoFrameQueue
) {

    private val state: AtomicReference<DecoderState> = AtomicReference(DecoderState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Video decode thread.
    private val videoDecoderThread: HandlerThread by lazy {
        object : HandlerThread("tMP_VideoDecoder", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val activeStates = arrayOf(DecoderState.Ready, DecoderState.Eof, DecoderState.WaitingWritableFrameBuffer, DecoderState.WaitingReadablePacketBuffer)

    private val videoDecoderHandler: Handler by lazy {
        object : Handler(videoDecoderThread.looper) {

            private var skipNextPktRead: Boolean = false

            private var packetSerial: Int = -1

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this) {
                    val nativePlayer = player.getMediaInfo()?.nativePlayer
                    val state = getState()
                    if (nativePlayer != null && state in activeStates) {
                        when (msg.what) {
                            DecoderHandlerMsg.RequestDecode.ordinal -> {
                                if (skipNextPktRead || videoPacketQueue.isCanRead()) {
                                    if (videoFrameQueue.isCanWrite()) {
                                        val pkt = if (skipNextPktRead) {
                                            null
                                        } else {
                                            videoPacketQueue.dequeueReadable()
                                        }
                                        val serialChanged = if (pkt != null) {
                                            val changed = pkt.serial != packetSerial
                                            packetSerial = pkt.serial
                                            changed
                                        } else {
                                            false
                                        }
                                        if (skipNextPktRead || pkt != null) {
                                            if (pkt?.isEof == true) {
                                                val frame = videoFrameQueue.dequeueWriteableForce()
                                                frame.isEof = true
                                                frame.serial = packetSerial
                                                videoFrameQueue.enqueueReadable(frame)
                                                MediaLog.d(TAG, "Decode video frame eof.")
                                                skipNextPktRead = false
                                                this@VideoFrameDecoder.state.set(DecoderState.Eof)
                                                player.readableVideoFrameReady()
                                            } else {
                                                if (serialChanged) {
                                                    player.flushVideoCodecBufferInternal(nativePlayer)
                                                }
                                                val decodeResult = player.decodeVideoInternal(nativePlayer, pkt)
                                                when (decodeResult) {
                                                    DecodeResult2.Success, DecodeResult2.SuccessAndSkipNextPkt -> {
                                                        val frame = videoFrameQueue.dequeueWriteableForce()
                                                        frame.serial = packetSerial
                                                        val moveResult = player.moveDecodedVideoFrameToBufferInternal(nativePlayer, frame)
                                                        if (moveResult == OptResult.Success) {
                                                            videoFrameQueue.enqueueReadable(frame)
                                                            player.readableVideoFrameReady()
                                                        } else {
                                                            videoFrameQueue.enqueueWritable(frame)
                                                            MediaLog.e(TAG, "Move video frame fail.")
                                                        }
                                                        skipNextPktRead = decodeResult == DecodeResult2.SuccessAndSkipNextPkt
                                                        requestDecode()
                                                    }
                                                    DecodeResult2.Fail, DecodeResult2.FailAndNeedMorePkt, DecodeResult2.DecodeEnd -> {
                                                        if (decodeResult == DecodeResult2.Fail) {
                                                            MediaLog.e(TAG, "Decode video fail.")
                                                        }
                                                        if (decodeResult == DecodeResult2.FailAndNeedMorePkt) {
                                                            MediaLog.d(TAG, "Decode video fail and need more pkt.")
                                                        }
                                                        skipNextPktRead = false
                                                        requestDecode()
                                                    }
                                                }
                                            }
                                            if (pkt != null) {
                                                videoPacketQueue.enqueueWritable(pkt)
                                                player.writeableVideoPacketReady()
                                            }
                                        } else {
                                            requestDecode()
                                        }
                                    } else {
                                        MediaLog.d(TAG, "Waiting frame queue writeable buffer.")
                                        this@VideoFrameDecoder.state.set(DecoderState.WaitingWritableFrameBuffer)
                                    }
                                } else {
                                    MediaLog.d(TAG, "Waiting packet queue readable buffer.")
                                    this@VideoFrameDecoder.state.set(DecoderState.WaitingReadablePacketBuffer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        videoDecoderThread
        while (!isLooperPrepared.get()) {}
        videoDecoderHandler
        state.set(DecoderState.Ready)
    }

    fun requestDecode() {
        val state = getState()
        if (state in activeStates) {
            videoDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            videoDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        }
    }

    fun readablePacketReady() {
        val state = getState()
        if (state == DecoderState.WaitingReadablePacketBuffer) {
            videoDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            videoDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        }
    }

    fun writeableFrameReady() {
        val state = getState()
        if (state == DecoderState.WaitingWritableFrameBuffer) {
            videoDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            videoDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        }
    }

    fun removeAllHandlerMessages() {
        for (e in DecoderHandlerMsg.entries) {
            videoDecoderHandler.removeMessages(e.ordinal)
        }
    }

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState in activeStates) {
                state.set(DecoderState.Released)
                removeAllHandlerMessages()
                videoDecoderThread.quit()
                videoDecoderThread.quitSafely()
            }
        }
    }

    fun getState(): DecoderState = state.get()

    companion object {
        private const val TAG = "VideoFrameDecoder"
    }
}