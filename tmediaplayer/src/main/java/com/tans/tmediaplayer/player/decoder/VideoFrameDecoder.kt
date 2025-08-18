package com.tans.tmediaplayer.player.decoder

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.playerview.HWTextures
import com.tans.tmediaplayer.player.playerview.tMediaPlayerView
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

    private val playerView: AtomicReference<tMediaPlayerView?> by lazy {
        AtomicReference()
    }

    private val hwTextures: AtomicReference<HWTextures?> by lazy {
        AtomicReference()
    }

    private val renderListener: tMediaPlayerView.Companion.RenderListener by lazy {
        object : tMediaPlayerView.Companion.RenderListener {
            override fun onSurfaceCreated(hwTextures: HWTextures) {
                this@VideoFrameDecoder.hwTextures.set(hwTextures)
                requestSetHwSurface()
            }

            override fun onSurfaceDestroyed() {
                this@VideoFrameDecoder.hwTextures.set(null)
                requestSetHwSurface()
            }
        }
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

                                        if (serialChanged) { // Serial changed (because of seeking) flush decoder context
                                            tMediaPlayerLog.d(TAG) { "Serial changed, flush video decoder, serial: $packetSerial" }
                                            player.flushVideoCodecBufferInternal(nativePlayer)
                                        }
                                        if (skipNextPktRead || pkt != null) { // Decode
                                            skipNextPktRead = false
                                            if (pkt?.isEof == true) { // Eof, add a eof frame.
                                                val frame = videoFrameQueue.dequeueWriteableForce()
                                                frame.isEof = true
                                                frame.serial = packetSerial
                                                videoFrameQueue.enqueueReadable(frame)
                                                tMediaPlayerLog.d(TAG) { "Decode video frame eof." }
                                                this@VideoFrameDecoder.state.set(DecoderState.Eof)
                                                player.readableVideoFrameReady()
                                            } else { // Not eof.
                                                val start = SystemClock.uptimeMillis()
                                                val decodeResult = player.decodeVideoInternal(nativePlayer, pkt) // do decode.
                                                var videoFrame: VideoFrame? = null
                                                when (decodeResult) {
                                                    DecodeResult.Success, DecodeResult.SuccessAndSkipNextPkt -> { // decode success.
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
                                                            val type = player.getVideoFrameTypeNativeInternal(frame.nativeFrame)
                                                            if (type == ImageRawType.HwSurface) {
                                                                val playerView = playerView.get()
                                                                if (playerView != null) {
                                                                    playerView.queueEvent {
                                                                        val hwTextures = hwTextures.get()
                                                                        if (hwTextures != null) {
                                                                            try {
                                                                                hwTextures.oesTextureSurface.surfaceTexture.updateTexImage()
                                                                                tMediaPlayerLog.d(TAG) { "Update hw oes texture." }
                                                                                // TODO: handle hw surface.
                                                                                videoFrameQueue.enqueueWritable(frame)
                                                                            } catch (e: Throwable) {
                                                                                tMediaPlayerLog.e(TAG) { "Update hw surface fail: ${e.message}" }
                                                                                videoFrameQueue.enqueueWritable(frame)
                                                                            }
                                                                        } else {
                                                                            tMediaPlayerLog.e(TAG) { "Can handle hw surface data, hw textures is null." }
                                                                            videoFrameQueue.enqueueWritable(frame)
                                                                        }
                                                                    }
                                                                } else {
                                                                    tMediaPlayerLog.e(TAG) { "Can handle hw surface data, player view is null." }
                                                                    videoFrameQueue.enqueueWritable(frame)
                                                                }
                                                            } else {
                                                                videoFrameQueue.enqueueReadable(frame)
                                                                player.readableVideoFrameReady()
                                                            }
                                                        } else {
                                                            videoFrameQueue.enqueueWritable(frame)
                                                            tMediaPlayerLog.e(TAG) { "Move video frame fail." }
                                                        }
                                                        skipNextPktRead = decodeResult == DecodeResult.SuccessAndSkipNextPkt // Is next decode need a new pkt?
                                                        requestDecode()
                                                    }

                                                    DecodeResult.Fail, DecodeResult.FailAndNeedMorePkt, DecodeResult.DecodeEnd -> { // decode fail.
                                                        if (decodeResult == DecodeResult.Fail) {
                                                            tMediaPlayerLog.e(TAG) { "Decode video fail." }
                                                        }
                                                        requestDecode()
                                                    }
                                                }
                                                val end = SystemClock.uptimeMillis()
                                                tMediaPlayerLog.d(TAG) { "Decode video cost ${end - start}ms, DecodeResult=${decodeResult}, pkt=${pkt}, videoFrame=${videoFrame}" }
                                                if (state != DecoderState.Ready) {
                                                    this@VideoFrameDecoder.state.set(DecoderState.Ready)
                                                }
                                            }
                                            if (pkt != null) {
                                                videoPacketQueue.enqueueWritable(pkt)
                                                player.writeableVideoPacketReady()
                                            }
                                        } else { // No pkt to decode.
                                            requestDecode()
                                        }
                                    } else { // Waiting for renderer.
                                        tMediaPlayerLog.d(TAG) { "Waiting frame queue writeable buffer." }
                                        this@VideoFrameDecoder.state.set(DecoderState.WaitingWritableFrameBuffer)
                                    }
                                } else { // Waiting for packet reader
                                    tMediaPlayerLog.d(TAG) { "Waiting packet queue readable buffer." }
                                    this@VideoFrameDecoder.state.set(DecoderState.WaitingReadablePacketBuffer)
                                }
                            }

                            DecoderHandlerMsg.RequestSetHwSurface.ordinal -> {
                                val surface = hwTextures.get()?.oesTextureSurface?.surface
                                val ret = player.setHwSurfaceInternal(nativePlayer, surface)
                                tMediaPlayerLog.d(TAG) { "Set hw surface ret: $ret" }
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
        tMediaPlayerLog.d(TAG) { "Video decoder inited." }
    }

    fun requestDecode() {
        val state = getState()
        if (state in activeStates) {
            videoDecoderHandler.removeMessages(DecoderHandlerMsg.RequestDecode.ordinal)
            videoDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestDecode.ordinal)
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
            tMediaPlayerLog.d(TAG) { "Skip handle readable package ready, because of state: $state" }
        }
    }

    fun writeableFrameReady() {
        val state = getState()
        if (state == DecoderState.WaitingWritableFrameBuffer) {
            requestDecode()
        } else {
            tMediaPlayerLog.d(TAG) { "Skip handle writable frame ready, because of state: $state" }
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        val previous = this.playerView.get()
        if (this.playerView.compareAndSet(previous, view)) {
            previous?.removeRenderListener(renderListener)
            hwTextures.set(null)
            view?.addRenderListener(renderListener)
            if (previous != view && view == null) {
                requestSetHwSurface()
            }
        }
    }

    fun requestSetHwSurface() {
        val state = getState()
        if (state in activeStates) {
            videoDecoderHandler.removeMessages(DecoderHandlerMsg.RequestSetHwSurface.ordinal)
            videoDecoderHandler.sendEmptyMessage(DecoderHandlerMsg.RequestSetHwSurface.ordinal)
        } else {
            tMediaPlayerLog.e(TAG) { "Request decode fail, wrong state: $state" }
        }
    }

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState != DecoderState.NotInit && oldState != DecoderState.Released) {
                state.set(DecoderState.Released)
                videoDecoderThread.quit()
                videoDecoderThread.quitSafely()
                playerView.set(null)
                hwTextures.set(null)
                tMediaPlayerLog.d(TAG) { "Video decoder released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release fail, wrong state: $oldState" }
            }
        }
    }

    fun getState(): DecoderState = state.get()

    companion object {
        private const val TAG = "VideoFrameDecoder"
    }
}