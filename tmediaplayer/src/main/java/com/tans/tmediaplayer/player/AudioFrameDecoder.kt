package com.tans.tmediaplayer.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.rwqueue.AudioFrameQueue
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis

internal class AudioFrameDecoder(
    private val player: tMediaPlayer2,
    private val audioPacketQueue: PacketQueue,
    private val audioFrameQueue: AudioFrameQueue
) {

    private val state: AtomicReference<DecoderState> = AtomicReference(DecoderState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Audio decode thread.
    private val audioDecoderThread: HandlerThread by lazy {
        object : HandlerThread("tMP_AudioDecoder", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val activeStates = arrayOf(DecoderState.Ready, DecoderState.Eof, DecoderState.WaitingWritableFrameBuffer, DecoderState.WaitingReadablePacketBuffer)

    private val audioDecoderHandler: Handler by lazy {
        object : Handler(audioDecoderThread.looper) {

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
                                if (skipNextPktRead || audioPacketQueue.isCanRead()) {
                                    if (audioFrameQueue.isCanWrite()) {
                                        val pkt = if (skipNextPktRead) {
                                            null
                                        } else {
                                            audioPacketQueue.dequeueReadable()
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
                                                val frame = audioFrameQueue.dequeueWriteableForce()
                                                frame.isEof = true
                                                frame.serial = packetSerial
                                                audioFrameQueue.enqueueReadable(frame)
                                                MediaLog.d(TAG, "Decode audio frame eof.")
                                                skipNextPktRead = false
                                                this@AudioFrameDecoder.state.set(DecoderState.Eof)
                                                player.readableAudioFrameReady()
                                            } else {
                                                if (serialChanged) {
                                                    MediaLog.d(TAG, "Serial changed, flush audio decoder, serial: $packetSerial")
                                                    player.flushAudioCodecBufferInternal(nativePlayer)
                                                }
                                                val cost = measureTimeMillis {
                                                    val decodeResult = player.decodeAudioInternal(nativePlayer, pkt)
                                                    when (decodeResult) {
                                                        DecodeResult2.Success, DecodeResult2.SuccessAndSkipNextPkt -> {
                                                            val frame = audioFrameQueue.dequeueWriteableForce()
                                                            frame.serial = packetSerial
                                                            val moveResult = player.moveDecodedAudioFrameToBufferInternal(nativePlayer, frame)
                                                            if (moveResult == OptResult.Success) {
                                                                audioFrameQueue.enqueueReadable(frame)
                                                                player.readableAudioFrameReady()
                                                            } else {
                                                                audioFrameQueue.enqueueWritable(frame)
                                                                MediaLog.e(TAG, "Move audio frame fail.")
                                                            }
                                                            skipNextPktRead = decodeResult == DecodeResult2.SuccessAndSkipNextPkt
                                                            requestDecode()
                                                        }
                                                        DecodeResult2.Fail, DecodeResult2.FailAndNeedMorePkt, DecodeResult2.DecodeEnd -> {
                                                            if (decodeResult == DecodeResult2.Fail) {
                                                                MediaLog.e(TAG, "Decode audio fail.")
                                                            }
                                                            if (decodeResult == DecodeResult2.FailAndNeedMorePkt) {
                                                                MediaLog.d(TAG, "Decode audio fail and need more pkt.")
                                                            }
                                                            skipNextPktRead = false
                                                            requestDecode()
                                                        }
                                                    }
                                                }
                                                MediaLog.d(TAG, "Decode audio cost ${cost}ms.")

                                            }
                                            if (pkt != null) {
                                                audioPacketQueue.enqueueWritable(pkt)
                                                player.writeableAudioPacketReady()
                                            }
                                        } else {
                                            requestDecode()
                                        }
                                    } else {
                                        MediaLog.d(TAG, "Waiting frame queue writeable buffer.")
                                        this@AudioFrameDecoder.state.set(DecoderState.WaitingWritableFrameBuffer)
                                    }
                                } else {
                                    MediaLog.d(TAG, "Waiting packet queue readable buffer.")
                                    this@AudioFrameDecoder.state.set(DecoderState.WaitingReadablePacketBuffer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        audioDecoderThread
        while (!isLooperPrepared.get()) {}
        audioDecoderHandler
        state.set(DecoderState.Ready)
    }

    fun requestDecode() {
        val state = getState()
        if (state in activeStates) {
            audioDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            audioDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        }
    }

    fun readablePacketReady() {
        val state = getState()
        if (state == DecoderState.WaitingReadablePacketBuffer) {
            audioDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            audioDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        }
    }

    fun writeableFrameReady() {
        val state = getState()
        if (state == DecoderState.WaitingWritableFrameBuffer) {
            audioDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            audioDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        }
    }

    fun removeAllHandlerMessages() {
        for (e in DecoderHandlerMsg.entries) {
            audioDecoderHandler.removeMessages(e.ordinal)
        }
    }

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState in activeStates) {
                state.set(DecoderState.Released)
                removeAllHandlerMessages()
                audioDecoderThread.quit()
                audioDecoderThread.quitSafely()
            }
        }
    }

    fun getState(): DecoderState = state.get()

    companion object {
        private const val TAG = "AudioFrameDecoder"
    }
}