package com.tans.tmediaplayer.player

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.audiotrack.tMediaAudioTrack
import com.tans.tmediaplayer.player.model.AudioChannel
import com.tans.tmediaplayer.player.model.AudioSampleBitDepth
import com.tans.tmediaplayer.player.model.AudioSampleRate
import com.tans.tmediaplayer.player.rwqueue.AudioFrame
import com.tans.tmediaplayer.player.rwqueue.AudioFrameQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AudioRenderer(
    outputChannel: AudioChannel,
    outputSampleRate: AudioSampleRate,
    outputSampleBitDepth: AudioSampleBitDepth,
    bufferQueueSize: Int = 18,
    private val audioFrameQueue: AudioFrameQueue,
    private val player: tMediaPlayer2
) {
    private val audioTrack: tMediaAudioTrack by lazy {
        tMediaAudioTrack(
            outputChannel = outputChannel,
            outputSampleRate = outputSampleRate,
            outputSampleBitDepth = outputSampleBitDepth,
            bufferQueueSize = bufferQueueSize
        ) {
            val frame = waitingRenderFrames.pollFirst()
            if (frame != null) {
                val pts = player.getAudioPtsInternal(frame.pts)
                player.audioClock.setClock(pts, frame.serial)
                player.externalClock.syncToClock(player.audioClock)
                audioFrameQueue.enqueueWritable(frame)
                player.writeableAudioFrameReady()
            }
        }
    }

    private val state: AtomicReference<RendererState> = AtomicReference(RendererState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Video renderer thread.
    private val videoRendererThread: HandlerThread by lazy {
        object : HandlerThread("tMP_AudioRenderer", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    private val canRenderStates = arrayOf(RendererState.Playing, RendererState.Eof, RendererState.WaitingReadableFrameBuffer)

    private val waitingRenderFrames: LinkedBlockingDeque<AudioFrame> by lazy {
        LinkedBlockingDeque()
    }

    private val videoRendererHandler: Handler by lazy {
        object : Handler(videoRendererThread.looper) {

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@AudioRenderer) {
                    when (msg.what) {
                        RendererHandlerMsg.RequestRender.ordinal -> {
                            val playerState = player.getState()
                            val state = getState()
                            val mediaInfo = player.getMediaInfo()
                            if (mediaInfo != null && state in canRenderStates) {
                                if (state == RendererState.WaitingReadableFrameBuffer && playerState is tMediaPlayerState.Playing) {
                                    this@AudioRenderer.state.set(RendererState.Playing)
                                    requestRender()
                                    return@synchronized
                                }
                                if (state == RendererState.WaitingReadableFrameBuffer && playerState is tMediaPlayerState.Paused) {
                                    this@AudioRenderer.state.set(RendererState.Paused)
                                    return@synchronized
                                }
                                val frame = audioFrameQueue.dequeueReadable()
                                if (frame != null) {
                                    val result = audioTrack.enqueueBuffer(frame.nativeFrame)
                                    if (result == OptResult.Success) {
                                        waitingRenderFrames.addLast(frame)
                                    } else {
                                        MediaLog.e(TAG, "Audio render fail.")
                                        audioFrameQueue.enqueueWritable(frame)
                                        player.writeableAudioFrameReady()
                                    }
                                    if (state == RendererState.WaitingReadableFrameBuffer) {
                                        this@AudioRenderer.state.set(RendererState.Playing)
                                    }
                                    requestRender()
                                } else {
                                    this@AudioRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                    MediaLog.d(TAG, "Waiting readable audio frame.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        videoRendererThread
        while (!isLooperPrepared.get()) {}
        videoRendererHandler
        state.set(RendererState.Paused)
        audioTrack
    }

    fun play() {
        val state = getState()
        if (state == RendererState.Paused || state == RendererState.WaitingReadableFrameBuffer) {
            requestRender()
            audioTrack.play()
        }
    }

    fun pause() {
        val state = getState()
        if (state == RendererState.Playing || state == RendererState.WaitingReadableFrameBuffer) {
            this.state.set(RendererState.Paused)
            audioTrack.pause()
        }
    }

    fun flush() {
        val state = getState()
        if (state != RendererState.NotInit && state != RendererState.Released) {
            audioTrack.clearBuffers()
            while (waitingRenderFrames.isNotEmpty()) {
                val b = waitingRenderFrames.pollFirst()
                if (b != null) {
                    audioFrameQueue.enqueueWritable(b)
                }
            }
            player.writeableAudioFrameReady()
        }
    }

    fun release() {
        synchronized(this) {
            val state = getState()
            if (state != RendererState.NotInit && state != RendererState.Released) {
                this.state.set(RendererState.Released)
                audioTrack.release()
                while (waitingRenderFrames.isNotEmpty()) {
                    val b = waitingRenderFrames.pollFirst()
                    if (b != null) {
                        audioFrameQueue.enqueueWritable(b)
                    }
                }
            }
        }
    }

    fun getState(): RendererState = state.get()

    private fun requestRender() {
        val state = getState()
        if (state in canRenderStates) {
            videoRendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            videoRendererHandler.sendEmptyMessage(RendererHandlerMsg.RequestRender.ordinal)
        }
    }

    companion object {
        private const val TAG = "AudioRenderer"
    }

}