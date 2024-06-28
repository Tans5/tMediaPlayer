package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.decoder.DecoderState
import java.util.concurrent.atomic.AtomicReference

internal class SubtitleFrameDecoder(
    private val subtitle: tMediaSubtitle,
    looper: Looper
) {
    private val state: AtomicReference<DecoderState> = AtomicReference(DecoderState.Ready)

    private val activeStates = arrayOf(
        DecoderState.Ready,
        DecoderState.Eof,
        DecoderState.WaitingWritableFrameBuffer,
        DecoderState.WaitingReadablePacketBuffer
    )

    private val decoderHandler: Handler = object : Handler(looper) {

        private var skipNextPktRead: Boolean = false

        private var packetSerial: Int = -1

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            synchronized(this@SubtitleFrameDecoder) {
                val nativeSubtitle = subtitle.getNativeSubtitle()
                val state = getState()
                if (nativeSubtitle != null && state in activeStates) {
                    when (msg.what) {
                        DecoderHandlerMsg.RequestDecode.ordinal -> {
                            // TODO: Do decode.
                        }
                        DecoderHandlerMsg.RequestFlushDecoder.ordinal -> {
                            // TODO: Do flush decoder
                        }
                        DecoderHandlerMsg.RequestSetupSubtitleStream.ordinal -> {
                            // TODO: setup subtitle stream.
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

    fun readablePacketReady() {
        val state = getState()
        if (state == DecoderState.WaitingReadablePacketBuffer) {
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