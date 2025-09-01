package com.tans.tmediaplayer.player.decoder

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.model.DecodeResult
import com.tans.tmediaplayer.player.model.ImageRawType
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.VIDEO_FRAME_QUEUE_SIZE
import com.tans.tmediaplayer.player.playerview.GLRenderer
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.ReadWriteQueueListener
import com.tans.tmediaplayer.player.rwqueue.VideoFrame
import com.tans.tmediaplayer.player.rwqueue.VideoFrameQueue
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.LinkedBlockingDeque
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

    @Volatile
    private var oesTextureAndBufferTextures: Pair<Int, IntArray>? = null

    private var textureBufferIndex: Long = -1

    private val glContextListener: GLRenderer.Companion.GLContextListener by lazy {
        object : GLRenderer.Companion.GLContextListener {

            override fun glContextCreated() {
                val hwSurfaces = player.getHwSurfaces()
                if (getState() != DecoderState.Released && hwSurfaces != null) {
                    val textures = player.getGLRenderer().genHwOesTextureAndBufferTextures(VIDEO_FRAME_QUEUE_SIZE)
                    oesTextureAndBufferTextures = textures
                    try {
                        hwSurfaces.second.attachToGLContext(textures.first)
                        tMediaPlayerLog.d(TAG) { "SurfaceTexture attached to gl context to ${Thread.currentThread().name}" }
                    } catch (e: Throwable) {
                        tMediaPlayerLog.e(TAG) { "SurfaceTexture attach to gl context fail \"${e.message}\" from ${Thread.currentThread().name}" }
                    }
                }
            }

            override fun glContextDestroying() {
                val textures = oesTextureAndBufferTextures
                if (textures != null) {
                    oesTextureAndBufferTextures = null
                    player.getGLRenderer().destroyHwOesTextureAndBufferTextures(textures)
                }
                val surfaceTexture = player.getHwSurfaces()?.second
                if (surfaceTexture != null) {
                    try {
                        surfaceTexture.detachFromGLContext()
                        tMediaPlayerLog.d(TAG) { "SurfaceTexture detached from gl context from ${Thread.currentThread().name}" }
                    } catch (e: Throwable) {
                        tMediaPlayerLog.e(TAG) { "SurfaceTexture detach from gl context fail \"${e.message}\" from ${Thread.currentThread().name}" }
                    }
                }
            }
        }
    }

    private val activeStates = arrayOf(
        DecoderState.Ready,
        DecoderState.Eof,
        DecoderState.WaitingWritableFrameBuffer,
        DecoderState.WaitingReadablePacketBuffer
    )

    private val waitingGLRendererFramesLazyDelete = lazy { LinkedBlockingDeque<VideoFrame>() }
    private val waitingGLRendererFrames: LinkedBlockingDeque<VideoFrame> by waitingGLRendererFramesLazyDelete

    private val videoDecoderHandler: Handler by lazy {
        object : Handler(videoDecoderThread.looper) {

            private var skipNextPktRead: Boolean = false

            private var packetSerial: Int = 1

            // GL render task: move oes texture to texture buffer.
            fun glRenderTaskCallback(containGLContext: Boolean) {
                val frame = waitingGLRendererFrames.poll()
                if (frame != null) {
                    if (containGLContext) {
                        try {
                            val surfaceTexture = player.getHwSurfaces()!!.second
                            val oesTexture = oesTextureAndBufferTextures!!.first
                            val textureBuffers = oesTextureAndBufferTextures!!.second
                            surfaceTexture.updateTexImage()
                            textureBufferIndex ++
                            val textureBuffer = textureBuffers[(textureBufferIndex % textureBuffers.size).toInt()]
                            // tMediaPlayerLog.d(TAG) { "Request write texture: pts=${frame.pts}, texture=$textureBuffer" }
                            if (player.getGLRenderer().oesTexture2Texture2D(surfaceTexture, oesTexture, textureBuffer, frame.width, frame.height)) {
                                frame.textureBuffer = textureBuffer
                                // tMediaPlayerLog.d(TAG) { "Write texture success: pts=${frame.pts}, texture=$textureBuffer" }
                                frame.isBadTextureBuffer = false
                            } else {
                                frame.isBadTextureBuffer = true
                                tMediaPlayerLog.e(TAG) { "Update hw frame fail: oes texture to 2d fail, pts=${frame.pts}, texture=$textureBuffer" }
                            }
                        } catch (e: Throwable) {
                            frame.isBadTextureBuffer = true
                            tMediaPlayerLog.e(TAG) { "Update hw frame fail: ${e.message}" }
                        }
                    } else {
                        frame.isBadTextureBuffer = true
                        tMediaPlayerLog.e(TAG) { "Update hw frame fail: not in gl context thread." }
                    }
                    videoFrameQueue.enqueueReadable(frame)
                } else {
                    tMediaPlayerLog.e(TAG) { "Waiting gl frames is empty." }
                }
            }

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
                                            } else { // Not eof.
                                                // val start = SystemClock.uptimeMillis()
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
                                                            if (type == ImageRawType.HwSurface) { // OES texture image
                                                                frame.imageType = ImageRawType.HwSurface
                                                                frame.width = player.getVideoWidthNativeInternal(frame.nativeFrame)
                                                                frame.height = player.getVideoHeightNativeInternal(frame.nativeFrame)
                                                                frame.pts = player.getVideoPtsInternal(frame.nativeFrame)
                                                                val surfaceTexture = player.getHwSurfaces()?.second
                                                                val oesTexture = oesTextureAndBufferTextures?.first
                                                                val textureBuffers = oesTextureAndBufferTextures?.second
                                                                if (surfaceTexture != null && oesTexture != null && textureBuffers != null) {
                                                                    waitingGLRendererFrames.offer(frame)
                                                                    player.getGLRenderer().enqueueTask(::glRenderTaskCallback)
                                                                } else {
                                                                    tMediaPlayerLog.e(TAG) { "Can't handle hw surface data, no oes textures." }
                                                                    frame.isBadTextureBuffer = true
                                                                    videoFrameQueue.enqueueReadable(frame)
                                                                }
                                                            } else { // Bytes image
                                                                videoFrameQueue.enqueueReadable(frame)
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
                                                // val end = SystemClock.uptimeMillis()
                                                // tMediaPlayerLog.d(TAG) { "Decode video cost ${end - start}ms, DecodeResult=${decodeResult}, pkt=${pkt}, videoFrame=${videoFrame}" }
                                                if (state != DecoderState.Ready) {
                                                    this@VideoFrameDecoder.state.set(DecoderState.Ready)
                                                }
                                            }
                                            if (pkt != null) {
                                                videoPacketQueue.enqueueWritable(pkt)
                                            }
                                        } else { // No pkt to decode.
                                            requestDecode()
                                        }
                                    } else { // Waiting for renderer.
                                        // tMediaPlayerLog.d(TAG) { "Waiting frame queue writeable buffer." }
                                        this@VideoFrameDecoder.state.set(DecoderState.WaitingWritableFrameBuffer)
                                    }
                                } else { // Waiting for packet reader
                                    // tMediaPlayerLog.d(TAG) { "Waiting packet queue readable buffer." }
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
        videoPacketQueue.addListener(packetQueueListener)
        videoFrameQueue.addListener(frameQueueListener)
        player.getGLRenderer().addGLContextListener(glContextListener)
        state.set(DecoderState.Ready)
        tMediaPlayerLog.d(TAG) { "Video decoder inited." }
//        player.getHwSurfaces()?.second?.let {
//            it.setOnFrameAvailableListener {
//                tMediaPlayerLog.d(TAG) { "Surface texture hw frame on available." }
//            }
//        }
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
            // tMediaPlayerLog.d(TAG) { "Skip handle readable package ready, because of state: $state" }
        }
    }

    fun writeableFrameReady() {
        val state = getState()
        if (state == DecoderState.WaitingWritableFrameBuffer) {
            requestDecode()
        } else {
            // tMediaPlayerLog.d(TAG) { "Skip handle writable frame ready, because of state: $state" }
        }
    }

    fun release() {
        synchronized(this) {
            val oldState = getState()
            if (oldState != DecoderState.NotInit && oldState != DecoderState.Released) {
                state.set(DecoderState.Released)
                videoDecoderThread.quit()
                videoDecoderThread.quitSafely()
                videoPacketQueue.removeListener(packetQueueListener)
                videoFrameQueue.removeListener(frameQueueListener)
                player.getGLRenderer().removeGLContextListener(glContextListener)
                if (waitingGLRendererFramesLazyDelete.isInitialized()) {
                    if (waitingGLRendererFrames.isNotEmpty()) { // should not be here
                        tMediaPlayerLog.e(TAG) { "Waiting gl render queue not empty, size=${waitingGLRendererFrames.size}" }
                        while (waitingGLRendererFrames.isNotEmpty()) {
                            val f = waitingGLRendererFrames.poll()
                            if (f != null) {
                                videoFrameQueue.enqueueWritable(f)
                            }
                        }
                    }
                }
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