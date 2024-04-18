package com.tans.tmediaplayer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.render.tMediaPlayerView
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Render video frames by [tMediaPlayerView], render audio frames by [AudioTrack].
 */
@Suppress("ClassName")
internal class tMediaPlayerRenderer(
    private val player: tMediaPlayer,
    private val bufferManager: tMediaPlayerBufferManager
) {

    private val playerView: AtomicReference<tMediaPlayerView?> by lazy {
        AtomicReference(null)
    }

    /**
     * Waiting to render video buffers.
     */
    private val pendingRenderVideoBuffers: LinkedBlockingDeque<tMediaPlayerBufferManager.Companion.MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    /**
     * Waiting to render audio buffers.
     */
    private val pendingRenderAudioBuffers: LinkedBlockingDeque<tMediaPlayerBufferManager.Companion.MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    private val audioTrack: AudioTrack by lazy {
        val bufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setSampleRate(44100)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    // Is renderer thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Renderer thread.
    private val rendererThread: HandlerThread by lazy {
        object : HandlerThread("tMediaPlayerRenderThread", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val state: AtomicReference<tMediaPlayerRendererState> by lazy { AtomicReference(tMediaPlayerRendererState.NotInit) }


    private val lastRequestRenderPts: AtomicLong by lazy {
        AtomicLong(0)
    }

    // Renderer handler.
    private val rendererHandler: Handler by lazy {
        while (!isLooperPrepared.get()) {}
        object : Handler(rendererThread.looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                val mediaInfo = player.getMediaInfo()
                if (mediaInfo == null) {
                    MediaLog.e(TAG, "RenderHandler render error, media info is null.")
                    return
                }
                val state = getState()
                if (state == tMediaPlayerRendererState.Released || state == tMediaPlayerRendererState.NotInit) {
                    MediaLog.e(TAG, "RenderHandler wrong state: $state")
                    return
                }
                when (msg.what) {

                    /**
                     * Get render data from bufferManager and calculate render time.
                     */
                    CALCULATE_RENDER_MEDIA_FRAME -> {
                        if (state == tMediaPlayerRendererState.Rendering) {
                            val buffer = bufferManager.requestRenderBuffer()
                            if (buffer != null) {
                                // Contain data to render.

                                if (getState() == tMediaPlayerRendererState.Released) {
                                    bufferManager.enqueueDecodeBuffer(buffer)
                                } else {
                                    if (player.isLastFrameBufferNativeInternal(buffer.nativeBuffer)) {
                                        // Current frame is last frame.

                                        bufferManager.enqueueDecodeBuffer(buffer)
                                        val pts = lastRequestRenderPts.get() + 10
                                        this.sendEmptyMessageDelayed(
                                            RENDER_END,
                                            player.calculateRenderDelay(pts)
                                        )
                                    } else {
                                        // Not last frame.

                                        if (player.isVideoBufferNativeInternal(buffer.nativeBuffer)) {
                                            // Video frame
                                            val pts = player.getPtsNativeInternal(buffer.nativeBuffer)
                                            // Calculate current frame render delay.
                                            val delay = player.calculateRenderDelay(pts)
                                            val m = Message.obtain()
                                            m.what = RENDER_VIDEO
                                            m.obj = buffer
                                            lastRequestRenderPts.set(pts)
                                            // Add to render task.
                                            this.sendMessageDelayed(m, delay)
                                            // Add to pending.
                                            pendingRenderVideoBuffers.push(buffer)
                                        } else {
                                            // Audio frame.
                                            val pts = player.getPtsNativeInternal(buffer.nativeBuffer)
                                            // Calculate current frame render delay.
                                            val delay = player.calculateRenderDelay(pts)
                                            val m = Message.obtain()
                                            m.what = RENDER_AUDIO
                                            m.obj = buffer
                                            lastRequestRenderPts.set(pts)
                                            // Add to render task.
                                            this.sendMessageDelayed(m, delay)
                                            // Add to pending.
                                            pendingRenderAudioBuffers.push(buffer)
                                        }
                                        // Do next task.
                                        this.sendEmptyMessage(CALCULATE_RENDER_MEDIA_FRAME)
                                    }
                                }
                            } else {
                                // No data to render, waiting decoder.
                                this@tMediaPlayerRenderer.state.set(tMediaPlayerRendererState.WaitingDecoder)
                                MediaLog.d(TAG, "Waiting decoder buffer.")
                            }
                        } else {
                            MediaLog.d(TAG, "Skip render frame, because of state: $state")
                        }
                    }

                    /**
                     * Player State: Pause -> Playing.
                     * Restart render.
                     */
                    REQUEST_RENDER -> {
                        if (state in listOf(
                                tMediaPlayerRendererState.RenderEnd,
                                tMediaPlayerRendererState.WaitingDecoder,
                                tMediaPlayerRendererState.Paused,
                                tMediaPlayerRendererState.Prepared
                            )
                        ) {
                            this@tMediaPlayerRenderer.state.set(tMediaPlayerRendererState.Rendering)
                            this.sendEmptyMessage(CALCULATE_RENDER_MEDIA_FRAME)
                        } else {
                            MediaLog.d(TAG, "Skip request render, because of state: $state")
                        }
                    }

                    /**
                     * Player State: Playing -> Pause
                     * Pause render.
                     */
                    REQUEST_PAUSE -> {
                        if (state == tMediaPlayerRendererState.Rendering || state == tMediaPlayerRendererState.WaitingDecoder) {
                            this@tMediaPlayerRenderer.state.set(tMediaPlayerRendererState.Paused)
                        } else {
                            MediaLog.d(TAG, "Skip request pause, because of state: $state")
                        }
                    }

                    /**
                     * Render video.
                     */
                    RENDER_VIDEO -> {
                        val buffer = msg.obj as? tMediaPlayerBufferManager.Companion.MediaBuffer
                        if (buffer != null) {
                            // Remove pending.
                            pendingRenderVideoBuffers.remove(buffer)
                            MediaLog.d(TAG, "Render Video.")
                            val ls = getState()
                            if (ls == tMediaPlayerRendererState.Released || ls == tMediaPlayerRendererState.NotInit) {
                                return
                            }
                            val progress = player.getPtsNativeInternal(buffer.nativeBuffer)
                            // Notify to player update progress.
                            player.dispatchProgress(progress)
                            val view = playerView.get()
                            if (view != null) {
                                // Contain playerView to render.
                                val width = player.getVideoWidthNativeInternal(buffer.nativeBuffer)
                                val height =
                                    player.getVideoHeightNativeInternal(buffer.nativeBuffer)
                                // Render different image type.
                                when (player.getVideoFrameTypeNativeInternal(buffer.nativeBuffer)
                                    .toImageRawType()) {
                                    ImageRawType.Yuv420p -> {
                                        val ySize =
                                            player.getVideoFrameYSizeNativeInternal(buffer.nativeBuffer)
                                        val yJavaBuffer = bufferManager.requestJavaBuffer(ySize)
                                        val uSize =
                                            player.getVideoFrameUSizeNativeInternal(buffer.nativeBuffer)
                                        val uJavaBuffer = bufferManager.requestJavaBuffer(uSize)
                                        val vSize =
                                            player.getVideoFrameVSizeNativeInternal(buffer.nativeBuffer)
                                        val vJavaBuffer = bufferManager.requestJavaBuffer(vSize)
                                        player.getVideoFrameYBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            yJavaBuffer.bytes
                                        )
                                        player.getVideoFrameUBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            uJavaBuffer.bytes
                                        )
                                        player.getVideoFrameVBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            vJavaBuffer.bytes
                                        )
                                        view.requestRenderYuv420pFrame(
                                            width = width,
                                            height = height,
                                            yBytes = yJavaBuffer.bytes,
                                            uBytes = uJavaBuffer.bytes,
                                            vBytes = vJavaBuffer.bytes
                                        )
                                        bufferManager.enqueueJavaBuffer(yJavaBuffer)
                                        bufferManager.enqueueJavaBuffer(uJavaBuffer)
                                        bufferManager.enqueueJavaBuffer(vJavaBuffer)
                                    }

                                    ImageRawType.Nv12 -> {
                                        val ySize =
                                            player.getVideoFrameYSizeNativeInternal(buffer.nativeBuffer)
                                        val yJavaBuffer = bufferManager.requestJavaBuffer(ySize)
                                        val uvSize =
                                            player.getVideoFrameUVSizeNativeInternal(buffer.nativeBuffer)
                                        val uvJavaBuffer = bufferManager.requestJavaBuffer(uvSize)
                                        player.getVideoFrameYBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            yJavaBuffer.bytes
                                        )
                                        player.getVideoFrameUVBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            uvJavaBuffer.bytes
                                        )
                                        view.requestRenderNv12Frame(
                                            width = width,
                                            height = height,
                                            yBytes = yJavaBuffer.bytes,
                                            uvBytes = uvJavaBuffer.bytes
                                        )
                                        bufferManager.enqueueJavaBuffer(yJavaBuffer)
                                        bufferManager.enqueueJavaBuffer(uvJavaBuffer)
                                    }

                                    ImageRawType.Nv21 -> {
                                        val ySize =
                                            player.getVideoFrameYSizeNativeInternal(buffer.nativeBuffer)
                                        val yJavaBuffer = bufferManager.requestJavaBuffer(ySize)
                                        val vuSize =
                                            player.getVideoFrameUVSizeNativeInternal(buffer.nativeBuffer)
                                        val vuJavaBuffer = bufferManager.requestJavaBuffer(vuSize)
                                        player.getVideoFrameYBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            yJavaBuffer.bytes
                                        )
                                        player.getVideoFrameUVBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            vuJavaBuffer.bytes
                                        )
                                        view.requestRenderNv21Frame(
                                            width = width,
                                            height = height,
                                            yBytes = yJavaBuffer.bytes,
                                            vuBytes = vuJavaBuffer.bytes
                                        )
                                        bufferManager.enqueueJavaBuffer(yJavaBuffer)
                                        bufferManager.enqueueJavaBuffer(vuJavaBuffer)
                                    }

                                    ImageRawType.Rgba -> {
                                        val bufferSize =
                                            player.getVideoFrameRgbaSizeNativeInternal(buffer.nativeBuffer)
                                        val javaBuffer = bufferManager.requestJavaBuffer(bufferSize)
                                        player.getVideoFrameRgbaBytesNativeInternal(
                                            buffer.nativeBuffer,
                                            javaBuffer.bytes
                                        )
                                        view.requestRenderRgbaFrame(
                                            width = width,
                                            height = height,
                                            imageBytes = javaBuffer.bytes
                                        )
                                        bufferManager.enqueueJavaBuffer(javaBuffer)
                                    }

                                    ImageRawType.Unknown -> {
                                        MediaLog.e(
                                            TAG,
                                            "Render video frame fail. Unknown frame type."
                                        )
                                    }
                                }
                            }
                            bufferManager.enqueueDecodeBuffer(buffer)
                            // Notify to player render success.
                            player.renderSuccess()
                        }
                        Unit
                    }

                    /**
                     * Render audio.
                     */
                    RENDER_AUDIO -> {
                        val buffer = msg.obj as? tMediaPlayerBufferManager.Companion.MediaBuffer
                        if (buffer != null) {
                            // Remove pending.
                            pendingRenderAudioBuffers.remove(buffer)
                            MediaLog.d(TAG, "Render Audio.")
                            val ls = getState()
                            if (ls == tMediaPlayerRendererState.Released || ls == tMediaPlayerRendererState.NotInit) { return }
                            val progress = player.getPtsNativeInternal(buffer.nativeBuffer)
                            player.dispatchProgress(progress)
                            val size = player.getAudioFrameSizeNativeInternal(buffer.nativeBuffer)
                            val javaBuffer = bufferManager.requestJavaBuffer(size)
                            player.getAudioFrameBytesNativeInternal(buffer.nativeBuffer, javaBuffer.bytes)
                            audioTrackExecutor.execute {
                                try {
                                    audioTrack.write(javaBuffer.bytes, 0, javaBuffer.size)
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                } finally {
                                    bufferManager.enqueueJavaBuffer(javaBuffer)
                                }
                            }
                            bufferManager.enqueueDecodeBuffer(buffer)
                            player.renderSuccess()
                        }
                        Unit
                    }
                    RENDER_END -> {
                        player.dispatchPlayEnd()
                        this@tMediaPlayerRenderer.state.set(tMediaPlayerRendererState.RenderEnd)
                        MediaLog.d(TAG, "Render end.")
                    }
                    else -> {}
                }
            }
        }
    }

    fun prepare() {
        val lastState = getState()
        if (lastState == tMediaPlayerRendererState.Released) {
            MediaLog.e(TAG, "Prepare fail, render has released.")
            return
        }
        rendererThread
        rendererHandler
        audioTrack
        rendererHandler.removeMessages(CALCULATE_RENDER_MEDIA_FRAME)
        rendererHandler.removeMessages(REQUEST_RENDER)
        rendererHandler.removeMessages(REQUEST_PAUSE)
        removeRenderMessages()
        rendererHandler.removeMessages(RENDER_END)
        lastRequestRenderPts.set(0L)
        state.set(tMediaPlayerRendererState.Prepared)
    }

    /**
     * Audio track play.
     */
    fun audioTrackPlay() {
        try {
            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.play()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Audio track pause
     */
    fun audioTrackPause() {
        try {
            if (audioTrack.playState == AudioTrack.PLAYSTATE_PAUSED) {
                audioTrack.pause()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Clear audio track data.
     */
    fun audioTrackFlush() {
        try {
            audioTrack.flush()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Start render.
     */
    fun render() {
        val state = getState()
        if (state != tMediaPlayerRendererState.Released && state != tMediaPlayerRendererState.NotInit) {
            rendererHandler.sendEmptyMessage(REQUEST_RENDER)
        }
    }

    /**
     * Render pause.
     */
    fun pause() {
        val state = getState()
        if (state != tMediaPlayerRendererState.Released && state != tMediaPlayerRendererState.NotInit) {
            rendererHandler.sendEmptyMessage(REQUEST_PAUSE)
        }
    }

    /**
     * Contain new buffer to render.
     */
    fun checkRenderBufferIfWaiting() {
        if (getState() == tMediaPlayerRendererState.WaitingDecoder) {
            render()
        }
    }

    /**
     * Render seek success buffer data.
     */
    fun handleSeekingBuffer(b: tMediaPlayerBufferManager.Companion.MediaBuffer) {
        val state = getState()
        if (state != tMediaPlayerRendererState.Released && state != tMediaPlayerRendererState.NotInit) {
            synchronized(b) {
                if (getState() == tMediaPlayerRendererState.Released) {
                    return
                }
                lastRequestRenderPts.set(player.getPtsNativeInternal(b.nativeBuffer))
                if (player.isVideoBufferNativeInternal(b.nativeBuffer)) {
                    // If current buffer is video buffer, render it with no delay.
                    val m = Message.obtain()
                    m.what = RENDER_VIDEO
                    m.obj = b
                    rendererHandler.sendMessage(m)
                } else {
                    bufferManager.enqueueDecodeBuffer(b)
                }
            }
        }
    }

    fun release() {
        rendererHandler.removeMessages(CALCULATE_RENDER_MEDIA_FRAME)
        rendererHandler.removeMessages(REQUEST_RENDER)
        rendererHandler.removeMessages(REQUEST_PAUSE)
        removeRenderMessages()
        rendererHandler.removeMessages(RENDER_END)
        rendererThread.quit()
        rendererThread.quitSafely()
        audioTrack.release()
        this.state.set(tMediaPlayerRendererState.Released)
        lastRequestRenderPts.set(0L)
        playerView.set(null)
    }

    /**
     * Drop all waiting render data.
     */
    fun removeRenderMessages() {
        // Remove handler messages.
        rendererHandler.removeMessages(RENDER_VIDEO)
        rendererHandler.removeMessages(RENDER_AUDIO)

        // Move pending render buffer to decode.
        while (pendingRenderVideoBuffers.isNotEmpty()) {
            val b = pendingRenderVideoBuffers.pollFirst()
            if (b == null) {
                break
            } else {
                bufferManager.enqueueDecodeBuffer(b)
            }
        }
        while (pendingRenderAudioBuffers.isNotEmpty()) {
            val b = pendingRenderAudioBuffers.pollFirst()
            if (b == null) {
                break
            } else {
                bufferManager.enqueueDecodeBuffer(b)
            }
        }
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        playerView.set(view)
    }

    fun getState(): tMediaPlayerRendererState = state.get()

    companion object {
        private const val CALCULATE_RENDER_MEDIA_FRAME = 0
        private const val REQUEST_RENDER = 1
        private const val REQUEST_PAUSE = 2
        private const val RENDER_VIDEO = 3
        private const val RENDER_AUDIO = 4
        private const val RENDER_END = 5
        private const val TAG = "tMediaPlayerRender"

        private val audioTrackExecutor: Executor by lazy {
            Executors.newSingleThreadExecutor(ThreadFactory {
                Thread(it, "tMediaTrackAudioTrackThread")
            })
        }
    }

}