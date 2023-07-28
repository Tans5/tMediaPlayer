package com.tans.tmediaplayer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
internal class tMediaPlayerRender(
    private val player: tMediaPlayer,
    private val bufferManager: tMediaPlayerBufferManager
) {

    private val playerView: AtomicReference<tMediaPlayerView?> by lazy {
        AtomicReference(null)
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

    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    private val renderThread: HandlerThread by lazy {
        object : HandlerThread("tMediaPlayerRenderThread", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val state: AtomicReference<tMediaPlayerRenderState> by lazy { AtomicReference(tMediaPlayerRenderState.NotInit) }

    private val lastRequestRenderPts: AtomicLong by lazy {
        AtomicLong(0)
    }

    private val renderHandler: Handler by lazy {
        while (!isLooperPrepared.get()) {}
        object : Handler(renderThread.looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                val mediaInfo = player.getMediaInfo()
                if (mediaInfo == null) {
                    MediaLog.e(TAG, "RenderHandler render error, media info is null.")
                    return
                }
                val state = getState()
                if (state == tMediaPlayerRenderState.Released || state == tMediaPlayerRenderState.NotInit) {
                    MediaLog.e(TAG, "RenderHandler wrong state: $state")
                    return
                }
                when (msg.what) {
                    CALCULATE_RENDER_MEDIA_FRAME -> {
                        if (state == tMediaPlayerRenderState.Rendering) {
                            val buffer = bufferManager.requestRenderBuffer()
                            if (buffer != null) {
                                synchronized(buffer) {
                                    if (getState() == tMediaPlayerRenderState.Released) { return@synchronized }
                                    if (player.isLastFrameBufferNativeInternal(buffer.nativeBuffer)) {
                                        bufferManager.enqueueDecodeBuffer(buffer)
                                        val pts = lastRequestRenderPts.get() + 10
                                        this.sendEmptyMessageDelayed(RENDER_END, player.calculateRenderDelay(pts))
                                    } else {
                                        if (player.isVideoBufferNativeInternal(buffer.nativeBuffer)) {
                                            // VIDEO
                                            val pts = player.getVideoPtsNativeInternal(buffer.nativeBuffer)
                                            val delay = player.calculateRenderDelay(pts)
                                            val m = Message.obtain()
                                            m.what = RENDER_VIDEO
                                            m.obj = buffer
                                            lastRequestRenderPts.set(pts)
                                            this.sendMessageDelayed(m, delay)
                                        } else {
                                            // AUDIO
                                            val pts = player.getAudioPtsNativeInternal(buffer.nativeBuffer)
                                            val delay = player.calculateRenderDelay(pts)
                                            val m = Message.obtain()
                                            m.what = RENDER_AUDIO
                                            m.obj = buffer
                                            lastRequestRenderPts.set(pts)
                                            this.sendMessageDelayed(m, delay)
                                        }
                                        this.sendEmptyMessage(CALCULATE_RENDER_MEDIA_FRAME)
                                    }
                                }
                            } else {
                                this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.WaitingDecoder)
                                MediaLog.d(TAG, "Waiting decoder buffer.")
                            }
                        } else {
                            MediaLog.d(TAG, "Skip render frame, because of state: $state")
                        }
                    }
                    REQUEST_RENDER -> {
                        if (state in listOf(
                                tMediaPlayerRenderState.RenderEnd,
                                tMediaPlayerRenderState.WaitingDecoder,
                                tMediaPlayerRenderState.Paused,
                                tMediaPlayerRenderState.Prepared
                            )
                        ) {
                            this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.Rendering)
                            this.sendEmptyMessage(CALCULATE_RENDER_MEDIA_FRAME)
                        } else {
                            MediaLog.d(TAG, "Skip request render, because of state: $state")
                        }
                    }
                    REQUEST_PAUSE -> {
                        if (state == tMediaPlayerRenderState.Rendering || state == tMediaPlayerRenderState.WaitingDecoder) {
                            this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.Paused)
                        } else {
                            MediaLog.d(TAG, "Skip request pause, because of state: $state")
                        }
                    }
                    RENDER_VIDEO -> {
                        val buffer = msg.obj as? tMediaPlayerBufferManager.Companion.MediaBuffer
                        if (buffer != null) {
                            synchronized(buffer) {
                                MediaLog.d(TAG, "Render Video.")
                                val ls = getState()
                                if (ls == tMediaPlayerRenderState.Released || ls == tMediaPlayerRenderState.NotInit) { return }
                                val progress = player.getVideoPtsNativeInternal(buffer.nativeBuffer)
                                player.dispatchProgress(progress)
                                val view = playerView.get()
                                view?.requestRenderFrame(
                                    width = player.getVideoWidthNativeInternal(buffer.nativeBuffer),
                                    height = player.getVideoHeightNativeInternal(buffer.nativeBuffer),
                                    imageBytes = player.getVideoFrameBytesNativeInternal(buffer.nativeBuffer)
                                )
                                bufferManager.enqueueDecodeBuffer(buffer)
                                player.renderSuccess()
                            }
                        }
                        Unit
                    }
                    RENDER_AUDIO -> {
                        val buffer = msg.obj as? tMediaPlayerBufferManager.Companion.MediaBuffer
                        if (buffer != null) {
                            synchronized(buffer) {
                                MediaLog.d(TAG, "Render Audio.")
                                val ls = getState()
                                if (ls == tMediaPlayerRenderState.Released || ls == tMediaPlayerRenderState.NotInit) { return }
                                val progress = player.getAudioPtsNativeInternal(buffer.nativeBuffer)
                                player.dispatchProgress(progress)
                                val bytes = player.getAudioFrameBytesNativeInternal(buffer.nativeBuffer)
                                audioTrackExecutor.execute {
                                    try {
                                        audioTrack.write(bytes, 0, bytes.size)
                                    } catch (e: Throwable) {
                                        e.printStackTrace()
                                    }
                                }
                                bufferManager.enqueueDecodeBuffer(buffer)
                                player.renderSuccess()
                            }
                        }
                        Unit
                    }
                    RENDER_END -> {
                        player.dispatchPlayEnd()
                        this@tMediaPlayerRender.state.set(tMediaPlayerRenderState.RenderEnd)
                        MediaLog.d(TAG, "Render end.")
                    }
                    else -> {}
                }
            }
        }
    }

    fun prepare() {
        val lastState = getState()
        if (lastState == tMediaPlayerRenderState.Released) {
            MediaLog.e(TAG, "Prepare fail, render has released.")
            return
        }
        renderThread
        renderHandler
        audioTrack
        renderHandler.removeMessages(CALCULATE_RENDER_MEDIA_FRAME)
        renderHandler.removeMessages(REQUEST_RENDER)
        renderHandler.removeMessages(REQUEST_PAUSE)
        renderHandler.removeMessages(RENDER_VIDEO)
        renderHandler.removeMessages(RENDER_AUDIO)
        renderHandler.removeMessages(RENDER_END)
        lastRequestRenderPts.set(0L)
        state.set(tMediaPlayerRenderState.Prepared)
    }

    fun audioTrackPlay() {
        try {
            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.play()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun audioTrackPause() {
        try {
            if (audioTrack.playState == AudioTrack.PLAYSTATE_PAUSED) {
                audioTrack.pause()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun render() {
        val state = getState()
        if (state != tMediaPlayerRenderState.Released && state != tMediaPlayerRenderState.NotInit) {
            renderHandler.sendEmptyMessage(REQUEST_RENDER)
        }
    }

    fun pause() {
        val state = getState()
        if (state != tMediaPlayerRenderState.Released && state != tMediaPlayerRenderState.NotInit) {
            renderHandler.sendEmptyMessage(REQUEST_PAUSE)
        }
    }

    fun checkRenderBufferIfWaiting() {
        if (getState() == tMediaPlayerRenderState.WaitingDecoder) {
            render()
        }
    }

    fun handleSeekingBuffer(b: tMediaPlayerBufferManager.Companion.MediaBuffer) {
        val state = getState()
        if (state != tMediaPlayerRenderState.Released && state != tMediaPlayerRenderState.NotInit) {
            synchronized(b) {
                if (getState() == tMediaPlayerRenderState.Released) {
                    return
                }
                if (player.isLastFrameBufferNativeInternal(b.nativeBuffer)) {
                    player.dispatchProgress(player.getMediaInfo()?.duration ?: 0L)
                    player.dispatchPlayEnd()
                } else {
                    if (player.isVideoBufferNativeInternal(b.nativeBuffer)) {
                        val m = Message.obtain()
                        m.what = RENDER_VIDEO
                        m.obj = b
                        lastRequestRenderPts.set(player.getVideoPtsNativeInternal(b.nativeBuffer))
                        renderHandler.sendMessage(m)
                    } else {
                        bufferManager.enqueueDecodeBuffer(b)
                    }
                }
            }
        }
    }

    fun release() {
        renderHandler.removeMessages(CALCULATE_RENDER_MEDIA_FRAME)
        renderHandler.removeMessages(REQUEST_RENDER)
        renderHandler.removeMessages(REQUEST_PAUSE)
        renderHandler.removeMessages(RENDER_VIDEO)
        renderHandler.removeMessages(RENDER_AUDIO)
        renderHandler.removeMessages(RENDER_END)
        renderThread.quit()
        renderThread.quitSafely()
        audioTrack.release()
        this.state.set(tMediaPlayerRenderState.Released)
        lastRequestRenderPts.set(0L)
        playerView.set(null)
    }

    fun attachPlayerView(view: tMediaPlayerView?) {
        playerView.set(view)
    }

    fun getState(): tMediaPlayerRenderState = state.get()

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
                Thread(it, "audio-track")
            })
        }
    }

}