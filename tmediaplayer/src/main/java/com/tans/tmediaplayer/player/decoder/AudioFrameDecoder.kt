package com.tans.tmediaplayer.player.decoder

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.rwqueue.AudioFrame
import com.tans.tmediaplayer.player.rwqueue.AudioFrameQueue
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.ReadWriteQueueListener
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AudioFrameDecoder(
    private val player: tMediaPlayer,
    private val audioPacketQueue: PacketQueue,
    private val audioFrameQueue: AudioFrameQueue
) {

    private val state: AtomicReference<DecoderState> = AtomicReference(DecoderState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Audio decode thread.
    private val audioDecoderThread: HandlerThread by lazy {
        object : HandlerThread("tMP_AudioDecoder", MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val packetQueueListener: ReadWriteQueueListener = object : ReadWriteQueueListener {
        override fun onNewWriteableFrame() { }

        override fun onNewReadableFrame() {
            readablePacketReady()
        }
    }

    private val frameQueueListener: ReadWriteQueueListener = object : ReadWriteQueueListener {
        override fun onNewWriteableFrame() {
            writeableFrameReady()
        }
        override fun onNewReadableFrame() { }
    }

    private val activeStates = arrayOf(DecoderState.Ready, DecoderState.Eof, DecoderState.WaitingWritableFrameBuffer, DecoderState.WaitingReadablePacketBuffer)

    private val audioDecoderHandler: Handler by lazy {
        object : Handler(audioDecoderThread.looper) {

            private var skipNextPktRead: Boolean = false

            private var packetSerial: Int = -1

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@AudioFrameDecoder) {
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
                                        if (serialChanged) { // Serial changed (because of seeking) flush decoder context
                                            tMediaPlayerLog.d(TAG) { "Serial changed, flush audio decoder, serial: $packetSerial" }
                                            player.flushAudioCodecBufferInternal(nativePlayer)
                                        }
                                        if (skipNextPktRead || pkt != null) { // Decode
                                            skipNextPktRead = false
                                            if (pkt?.isEof == true) { // Eof, add a eof frame.
                                                val frame = audioFrameQueue.dequeueWriteableForce()
                                                frame.isEof = true
                                                frame.serial = packetSerial
                                                tMediaPlayerLog.d(TAG) { "Decode audio frame eof." }
                                                this@AudioFrameDecoder.state.set(DecoderState.Eof)
                                                audioFrameQueue.enqueueReadable(frame)
                                            } else { // Not eof.
//                                                val start = SystemClock.uptimeMillis()
                                                val decodeResult = player.decodeAudioInternal(nativePlayer, pkt)  // do decode.
                                                var audioFrame: AudioFrame? = null
                                                when (decodeResult) {
                                                    DecodeResult.Success, DecodeResult.SuccessAndSkipNextPkt -> { // decode success.
                                                        val frame = audioFrameQueue.dequeueWriteableForce()
                                                        frame.serial = packetSerial
                                                        val moveResult = player.moveDecodedAudioFrameToBufferInternal(nativePlayer, frame)
                                                        if (moveResult == OptResult.Success) {
                                                            audioFrame = frame
                                                            audioFrameQueue.enqueueReadable(frame)
                                                        } else {
                                                            audioFrameQueue.enqueueWritable(frame)
                                                            tMediaPlayerLog.e(TAG) { "Move audio frame fail." }
                                                        }
                                                        skipNextPktRead = decodeResult == DecodeResult.SuccessAndSkipNextPkt
                                                        requestDecode()
                                                    }
                                                    DecodeResult.Fail, DecodeResult.FailAndNeedMorePkt, DecodeResult.DecodeEnd -> { // decode fail.
                                                        if (decodeResult == DecodeResult.Fail) {
                                                            tMediaPlayerLog.e(TAG) { "Decode audio fail." }
                                                        }
                                                        skipNextPktRead = false
                                                        requestDecode()
                                                    }
                                                }
//                                                val end = SystemClock.uptimeMillis()
//                                                tMediaPlayerLog.d(TAG) { "Decode audio cost ${end - start}ms, DecodeResult=${decodeResult}, pkt=${pkt}, audioFrame=${audioFrame}" }
                                                if (state != DecoderState.Ready) {
                                                    this@AudioFrameDecoder.state.set(DecoderState.Ready)
                                                }
                                            }
                                            if (pkt != null) {
                                                audioPacketQueue.enqueueWritable(pkt)
                                            }
                                        } else {
                                            requestDecode()
                                        }
                                    } else {
                                        // tMediaPlayerLog.d(TAG) { "Waiting frame queue writeable buffer." }
                                        this@AudioFrameDecoder.state.set(DecoderState.WaitingWritableFrameBuffer)
                                    }
                                } else {
                                    // tMediaPlayerLog.d(TAG) { "Waiting packet queue readable buffer." }
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
        audioPacketQueue.addListener(packetQueueListener)
        audioFrameQueue.addListener(frameQueueListener)
        state.set(DecoderState.Ready)
        tMediaPlayerLog.d(TAG) { "Audio decoder inited." }
    }

    fun requestDecode() {
        val state = getState()
        if (state in activeStates) {
            audioDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            audioDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        } else {
            tMediaPlayerLog.e(TAG) { "Request decode fail, wrong state: $state" }
        }
    }

    fun readablePacketReady() {
        val state = getState()
        if (state == DecoderState.WaitingReadablePacketBuffer ||
            state == DecoderState.Eof) {
            requestDecode()
        } else {
            // tMediaPlayerLog.d(TAG) { "Skip handle readable package ready, because of state: $state" }
        }
    }

    fun writeableFrameReady() {
        val state = getState()
        if (state == DecoderState.WaitingWritableFrameBuffer) {
            requestDecode()
        } else {
            // tMediaPlayerLog.d(TAG) { "Skip handle writeable frame ready, because of state: $state" }
        }
    }

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState != DecoderState.NotInit && oldState != DecoderState.Released) {
                state.set(DecoderState.Released)
                audioDecoderThread.quit()
                audioDecoderThread.quitSafely()
                audioPacketQueue.removeListener(packetQueueListener)
                audioFrameQueue.removeListener(frameQueueListener)
                tMediaPlayerLog.d(TAG) { "Video decoder released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release fail, wrong state: $oldState" }
            }
        }
    }

    fun getState(): DecoderState = state.get()

    companion object {
        private const val TAG = "AudioFrameDecoder"
    }
}