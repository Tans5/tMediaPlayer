package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.decoder.DecoderState
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.ReadWriteQueueListener
import java.util.concurrent.atomic.AtomicReference

internal class SubtitleFrameDecoder(
    private val subtitle: tMediaSubtitle,
    looper: Looper
) {
    private val state: AtomicReference<DecoderState> = AtomicReference(DecoderState.WaitingReadablePacketBuffer)

    private val activeStates = arrayOf(
        DecoderState.Ready,
        DecoderState.Eof,
        DecoderState.WaitingWritableFrameBuffer,
        DecoderState.WaitingReadablePacketBuffer
    )

    private val packetQueue: PacketQueue = subtitle.packetQueue

    private val frameQueue: SubtitleFrameQueue = subtitle.frameQueue

    private val packetQueueListener: ReadWriteQueueListener = object : ReadWriteQueueListener {
        override fun onNewWriteableFrame() {
        }
        override fun onNewReadableFrame() {
            readablePacketReady()
        }
    }

    private val frameQueueListener: ReadWriteQueueListener = object : ReadWriteQueueListener {
        override fun onNewWriteableFrame() {
            writeableFrameReady()
        }
        override fun onNewReadableFrame() {  }
    }

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
                                        val frame = frameQueue.dequeueWriteableForce()
                                        val decodeResult = subtitle.decodeSubtitleInternal(pkt?.nativePacket ?: 0L, frame.nativeFrame)
                                        skipNextPktRead = decodeResult == DecodeResult.SuccessAndSkipNextPkt
                                        when (decodeResult) {
                                            DecodeResult.Success, DecodeResult.SuccessAndSkipNextPkt -> {
                                                frameQueue.enqueueReadable(frame)
                                                requestDecode()
                                                tMediaPlayerLog.d(TAG) { "Decode subtitle success: $frame" }
                                            }
                                            DecodeResult.Fail, DecodeResult.FailAndNeedMorePkt, DecodeResult.DecodeEnd -> {
                                                if (decodeResult == DecodeResult.Fail) {
                                                    tMediaPlayerLog.e(TAG) { "Decode subtitle fail." }
                                                }
                                                frameQueue.enqueueWritable(frame)
                                                requestDecode()
                                            }
                                        }
                                    } else {
                                        requestDecode()
                                    }
                                    if (pkt != null) {
                                        packetQueue.enqueueWritable(pkt)
                                    }
                                    if (state != DecoderState.Ready) {
                                        this@SubtitleFrameDecoder.state.set(DecoderState.Ready)
                                    }
                                } else {
                                    tMediaPlayerLog.d(TAG) { "Waiting frame queue writeable buffer." }
                                    this@SubtitleFrameDecoder.state.set(DecoderState.WaitingWritableFrameBuffer)
                                }
                            } else {
                                tMediaPlayerLog.d(TAG) { "Waiting packet queue readable buffer." }
                                this@SubtitleFrameDecoder.state.set(DecoderState.WaitingReadablePacketBuffer)
                            }
                        }
                        DecoderHandlerMsg.RequestFlushDecoder.ordinal -> {
                            skipNextPktRead = false
                            subtitle.flushSubtitleDecoder()
                            frameQueue.flushReadableBuffer()
                            packetQueue.flushReadableBuffer()
                            tMediaPlayerLog.d(TAG) { "Flush decoder." }
                            requestDecode()
                        }
                        DecoderHandlerMsg.RequestSetupInternalSubtitleStream.ordinal -> {
                            val subtitleStreamId = msg.obj
                            if (subtitleStreamId is Int) {
                                skipNextPktRead = false
                                frameQueue.flushReadableBuffer()
                                packetQueue.flushReadableBuffer()
                                val result = subtitle.setupSubtitleStreamFromPlayer(subtitleStreamId)
                                if (result == OptResult.Success) {
                                    tMediaPlayerLog.d(TAG) { "Setup internal subtitle stream success: $subtitleStreamId" }
                                    requestDecode()
                                } else {
                                    tMediaPlayerLog.e(TAG) { "Setup internal subtitle stream fail: $subtitleStreamId" }
                                }
                            }
                        }
                        DecoderHandlerMsg.RequestSetupExternalSubtitleStream.ordinal -> {
                            val readerNative = msg.obj
                            if (readerNative is Long) {
                                skipNextPktRead = false
                                frameQueue.flushReadableBuffer()
                                packetQueue.flushReadableBuffer()
                                val result = subtitle.setupSubtitleStreamFromPktReaderInternal(subtitleNative = nativeSubtitle, readerNative = readerNative)
                                if (result == OptResult.Success) {
                                    tMediaPlayerLog.d(TAG) { "Setup external subtitle stream success." }
                                    requestDecode()
                                } else {
                                    tMediaPlayerLog.e(TAG) { "Setup external subtitle stream fail." }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        packetQueue.addListener(packetQueueListener)
        frameQueue.addListener(frameQueueListener)
    }

    fun requestDecode() {
        val state = getState()
        if (state in activeStates) {
            decoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            decoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        } else {
            tMediaPlayerLog.e(TAG) { "Request decode fail, wrong state: $state" }
        }
    }

    fun requestFlushDecoder() {
        val state = getState()
        if (state in activeStates) {
            decoderHandler.removeMessages(DecoderHandlerMsg.RequestFlushDecoder.ordinal)
            decoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestFlushDecoder.ordinal)
        } else {
            tMediaPlayerLog.e(TAG) { "Request flush decoder fail, wrong state: $state" }
        }
    }

    fun requestSetupInternalSubtitleStream(streamIndex: Int) {
        val state = getState()
        if (state in activeStates) {
            decoderHandler.removeMessages(DecoderHandlerMsg.RequestSetupInternalSubtitleStream.ordinal)
            val msg = decoderHandler.obtainMessage(DecoderHandlerMsg.RequestSetupInternalSubtitleStream.ordinal, streamIndex)
            decoderHandler.sendMessage(msg)
        } else {
            tMediaPlayerLog.d(TAG) { "Request setup subtitle stream, wrong state: $state" }
        }
    }

    fun requestSetupExternalSubtitleStream(readerNative: Long) {
        val state = getState()
        if (state in activeStates) {
            decoderHandler.removeMessages(DecoderHandlerMsg.RequestSetupExternalSubtitleStream.ordinal)
            val msg = decoderHandler.obtainMessage(DecoderHandlerMsg.RequestSetupExternalSubtitleStream.ordinal, readerNative)
            decoderHandler.sendMessage(msg)
        } else {
            tMediaPlayerLog.d(TAG) { "Request setup subtitle stream, wrong state: $state" }
        }
    }

    fun readablePacketReady() {
        val state = getState()
        if (state == DecoderState.WaitingReadablePacketBuffer ||
            state == DecoderState.Eof) {
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
                packetQueue.removeListener(packetQueueListener)
                frameQueue.removeListener(frameQueueListener)
                tMediaPlayerLog.d(TAG) { "Subtitle decoder released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release fail, wrong state: $oldState" }
            }
        }
    }

    fun getState(): DecoderState = state.get()

    companion object {
        private enum class DecoderHandlerMsg {
            RequestDecode,
            RequestFlushDecoder,
            RequestSetupInternalSubtitleStream,
            RequestSetupExternalSubtitleStream
        }
        private const val TAG = "SubtitleFrameDecoder"
    }
}