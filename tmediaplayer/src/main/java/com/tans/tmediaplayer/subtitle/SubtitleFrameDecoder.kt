package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.decoder.DecoderState
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import java.util.concurrent.atomic.AtomicReference

internal class SubtitleFrameDecoder(
    private val subtitle: tMediaSubtitle,
    looper: Looper,
    private val writeablePktReady: (() -> Unit)? = null
) {
    private val state: AtomicReference<DecoderState> = AtomicReference(DecoderState.Ready)

    private val activeStates = arrayOf(
        DecoderState.Ready,
        DecoderState.Eof,
        DecoderState.WaitingWritableFrameBuffer,
        DecoderState.WaitingReadablePacketBuffer
    )

    private val packetQueue: PacketQueue = subtitle.packetQueue

    private val frameQueue: SubtitleFrameQueue = subtitle.frameQueue

    private val decoderHandler: Handler = object : Handler(looper) {

        private var skipNextPktRead: Boolean = false

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            synchronized(this@SubtitleFrameDecoder) {
                val nativeSubtitle = subtitle.getNativeSubtitle()
                val state = getState()
                if (nativeSubtitle != null && state in activeStates) {
                    when (msg.what) {
                        DecoderHandlerMsg.RequestDecode.ordinal -> {
                            if (skipNextPktRead || packetQueue.isCanRead()) {
                                if (frameQueue.isCanWrite()) {
                                    val pkt = if (skipNextPktRead) {
                                        null
                                    } else {
                                        packetQueue.dequeueReadable()
                                    }
                                    if (skipNextPktRead || pkt != null) {
                                        val decodeResult = subtitle.decodeSubtitleInternal(pkt?.nativePacket ?: 0L)
                                        skipNextPktRead = decodeResult == DecodeResult.SuccessAndSkipNextPkt
                                        when (decodeResult) {
                                            DecodeResult.Success, DecodeResult.SuccessAndSkipNextPkt -> {
                                                val frame = frameQueue.dequeueWriteableForce()
                                                subtitle.moveDecodedSubtitleFrameToBufferInternal(frame.nativeFrame)
                                                frameQueue.enqueueReadable(frame)
                                                subtitle.readableFrameReady()
                                                requestDecode()
                                                MediaLog.d(TAG, "Decode subtitle success: $frame")
                                            }
                                            DecodeResult.Fail, DecodeResult.FailAndNeedMorePkt, DecodeResult.DecodeEnd -> {
                                                if (decodeResult == DecodeResult.Fail) {
                                                    MediaLog.e(TAG, "Decode subtitle fail.")
                                                }
                                                requestDecode()
                                            }
                                        }
                                    } else {
                                        requestDecode()
                                    }
                                    if (pkt != null) {
                                        packetQueue.enqueueWritable(pkt)
                                        writeablePktReady?.invoke()
                                    }
                                    if (state != DecoderState.Ready) {
                                        this@SubtitleFrameDecoder.state.set(DecoderState.Ready)
                                    }
                                } else {
                                    MediaLog.d(TAG, "Waiting frame queue writeable buffer.")
                                    this@SubtitleFrameDecoder.state.set(DecoderState.WaitingWritableFrameBuffer)
                                }
                            } else {
                                MediaLog.d(TAG, "Waiting packet queue readable buffer.")
                                this@SubtitleFrameDecoder.state.set(DecoderState.WaitingReadablePacketBuffer)
                            }
                        }
                        DecoderHandlerMsg.RequestFlushDecoder.ordinal -> {
                            skipNextPktRead = false
                            subtitle.flushSubtitleDecoder()
                            subtitle.frameQueue.flushReadableBuffer()
                            subtitle.packetQueue.flushReadableBuffer()
                            MediaLog.d(TAG, "Flush decoder.")
                            requestDecode()
                        }
                        DecoderHandlerMsg.RequestSetupSubtitleStream.ordinal -> {
                            val subtitleStreamId = msg.obj
                            if (subtitleStreamId is Int) {
                                skipNextPktRead = false
                                subtitle.frameQueue.flushReadableBuffer()
                                subtitle.packetQueue.flushReadableBuffer()
                                val result = subtitle.setupSubtitleStreamFromPlayer(subtitleStreamId)
                                if (result == OptResult.Success) {
                                    MediaLog.d(TAG, "Setup subtitle stream success: $subtitleStreamId")
                                } else {
                                    MediaLog.e(TAG, "Setup subtitle stream fail: $subtitleStreamId")
                                }
                                requestDecode()
                            }
                        }
                    }
                }
            }
        }
    }

    fun requestDecode() {
        val state = getState()
        if (state in activeStates) {
            decoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            decoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        } else {
            MediaLog.e(TAG, "Request decode fail, wrong state: $state")
        }
    }

    fun requestFlushDecoder() {
        val state = getState()
        if (state in activeStates) {
            decoderHandler.removeMessages(DecoderHandlerMsg.RequestFlushDecoder.ordinal)
            decoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestFlushDecoder.ordinal)
        } else {
            MediaLog.e(TAG, "Request flush decoder fail, wrong state: $state")
        }
    }

    fun requestSetupSubtitleStream(streamIndex: Int) {
        val state = getState()
        if (state in activeStates) {
            decoderHandler.removeMessages(DecoderHandlerMsg.RequestSetupSubtitleStream.ordinal)
            val msg = decoderHandler.obtainMessage(DecoderHandlerMsg.RequestSetupSubtitleStream.ordinal, streamIndex)
            decoderHandler.sendMessage(msg)
        } else {
            MediaLog.d(TAG, "Request setup subtitle stream, wrong state: $state")
        }
    }

    fun readablePacketReady() {
        val state = getState()
        if (state == DecoderState.WaitingReadablePacketBuffer ||
            state == DecoderState.Ready) {
            requestDecode()
        }
    }

    fun writeableFrameReady() {
        val state = getState()
        if (state == DecoderState.WaitingWritableFrameBuffer) {
            requestDecode()
        }
    }

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState != DecoderState.NotInit && oldState != DecoderState.Released) {
                state.set(DecoderState.Released)
                MediaLog.d(TAG, "Subtitle decoder released.")
            } else {
                MediaLog.e(TAG, "Release fail, wrong state: $oldState")
            }
        }
    }

    fun getState(): DecoderState = state.get()

    companion object {
        private enum class DecoderHandlerMsg {
            RequestDecode,
            RequestFlushDecoder,
            RequestSetupSubtitleStream
        }
        private const val TAG = "SubtitleFrameDecoder"
    }
}