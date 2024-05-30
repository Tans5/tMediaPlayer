package com.tans.tmediaplayer.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.player.rwqueue.AudioFrameQueue
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this) {
                    val nativePlayer = player.getMediaInfo()?.nativePlayer
                    val state = getState()
                    if (nativePlayer != null && state in activeStates) {
                        when (msg.what) {
                            DecoderHandlerMsg.RequestDecode.ordinal -> {
                                // TODO: Decode audio
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