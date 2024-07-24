package com.tans.tmediaplayer.player.decoder

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.VideoFrame
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class VideoFrameDecoder(
    private val player: tMediaPlayer,
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

    private val activeStates = arrayOf(
        DecoderState.Ready,
        DecoderState.Eof,
        DecoderState.WaitingWritableFrameBuffer,
        DecoderState.WaitingReadablePacketBuffer
    )

    private val videoDecoderHandler: Handler by lazy {
        object : Handler(videoDecoderThread.looper) {

            private var skipNextPktRead: Boolean = false

            private var packetSerial: Int = -1

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@VideoFrameDecoder) {
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
                                        if (serialChanged) {
                                            MediaLog.d(TAG, "Serial changed, flush video decoder, serial: $packetSerial")
                                            player.flushVideoCodecBufferInternal(nativePlayer)
                                        }
                                        if (skipNextPktRead || pkt != null) {
                                            skipNextPktRead = false
                                            if (pkt?.isEof == true) {
                                                val frame = videoFrameQueue.dequeueWriteableForce()
                                                frame.isEof = true
                                                frame.serial = packetSerial
                                                videoFrameQueue.enqueueReadable(frame)
                                                MediaLog.d(TAG, "Decode video frame eof.")
                                                this@VideoFrameDecoder.state.set(DecoderState.Eof)
                                                player.readableVideoFrameReady()
                                            } else {
                                                val start = SystemClock.uptimeMillis()
                                                val decodeResult = player.decodeVideoInternal(nativePlayer, pkt)
                                                var videoFrame: VideoFrame? = null
                                                when (decodeResult) {
                                                    DecodeResult.Success, DecodeResult.SuccessAndSkipNextPkt -> {
                                                        val frame =
                                                            videoFrameQueue.dequeueWriteableForce()
                                                        frame.serial = packetSerial
                                                        val moveResult =
                                                            player.moveDecodedVideoFrameToBufferInternal(
                                                                nativePlayer,
                                                                frame
                                                            )
                                                        if (moveResult == OptResult.Success) {
                                                            videoFrame = frame
                                                            videoFrameQueue.enqueueReadable(frame)
                                                            player.readableVideoFrameReady()
                                                        } else {
                                                            videoFrameQueue.enqueueWritable(frame)
                                                            MediaLog.e(TAG, "Move video frame fail.")
                                                        }
                                                        skipNextPktRead = decodeResult == DecodeResult.SuccessAndSkipNextPkt
                                                        requestDecode()
                                                    }

                                                    DecodeResult.Fail, DecodeResult.FailAndNeedMorePkt, DecodeResult.DecodeEnd -> {
                                                        if (decodeResult == DecodeResult.Fail) {
                                                            MediaLog.e(TAG, "Decode video fail.")
                                                        }
                                                        requestDecode()
                                                    }
                                                }
                                                val end = SystemClock.uptimeMillis()
                                                MediaLog.d(TAG, "Decode video cost ${end - start}ms, DecodeResult=${decodeResult}, pkt=${pkt}, videoFrame=${videoFrame}")
                                                if (state != DecoderState.Ready) {
                                                    this@VideoFrameDecoder.state.set(DecoderState.Ready)
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
        MediaLog.d(TAG, "Video decoder inited.")
    }

    fun requestDecode() {
        val state = getState()
        if (state in activeStates) {
            videoDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            videoDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
        } else {
            MediaLog.e(TAG, "Request decode fail, wrong state: $state")
        }
    }

    fun readablePacketReady() {
        val state = getState()
        if (state == DecoderState.WaitingReadablePacketBuffer) {
            requestDecode()
        } else {
            MediaLog.d(TAG, "Skip handle readable package ready, because of state: $state")
        }
    }

    fun writeableFrameReady() {
        val state = getState()
        if (state == DecoderState.WaitingWritableFrameBuffer) {
            requestDecode()
        } else {
            MediaLog.d(TAG, "Skip handle writable frame ready, because of state: $state")
        }
    }

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState != DecoderState.NotInit && oldState != DecoderState.Released) {
                state.set(DecoderState.Released)
                videoDecoderThread.quit()
                videoDecoderThread.quitSafely()
                MediaLog.d(TAG, "Video decoder released.")
            } else {
                MediaLog.e(TAG, "Release fail, wrong state: $oldState")
            }
        }
    }

    fun getState(): DecoderState = state.get()

    companion object {
        private const val TAG = "VideoFrameDecoder"
    }
}