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
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AudioRenderer(
    outputChannel: AudioChannel,
    outputSampleRate: AudioSampleRate,
    outputSampleBitDepth: AudioSampleBitDepth,
    bufferQueueSize: Int = 18,
    private val audioFrameQueue: AudioFrameQueue,
    private val audioPacketQueue: PacketQueue,
    private val player: tMediaPlayer
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
                player.audioClock.setClock(frame.pts, frame.serial)
                player.externalClock.syncToClock(player.audioClock)
                audioFrameQueue.enqueueWritable(frame)
                player.writeableAudioFrameReady()
            }
        }
    }

    private val state: AtomicReference<RendererState> = AtomicReference(RendererState.NotInit)

    // Is read thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Audio renderer thread.
    private val audioRendererThread: HandlerThread by lazy {
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

    private val audioRendererHandler: Handler by lazy {
        object : Handler(audioRendererThread.looper) {

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
                                    if (!frame.isEof) {
                                        if (frame.serial == audioPacketQueue.getSerial() && audioTrack.enqueueBuffer(frame.nativeFrame) == OptResult.Success) {
                                            waitingRenderFrames.addLast(frame)
                                        } else {
                                            MediaLog.e(TAG, "Audio render fail.")
                                            audioFrameQueue.enqueueWritable(frame)
                                            player.writeableAudioFrameReady()
                                        }
                                        if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                            this@AudioRenderer.state.set(RendererState.Playing)
                                        }
                                        requestRender()
                                    } else {
                                        this@AudioRenderer.state.set(RendererState.Eof)
                                        audioFrameQueue.enqueueWritable(frame)
                                        MediaLog.d(TAG, "Render audio frame eof.")
                                        player.writeableAudioFrameReady()
                                    }
                                } else {
                                    this@AudioRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                    MediaLog.d(TAG, "Waiting readable audio frame.")
                                }
                            } else {
                                MediaLog.e(TAG, "Render audio fail, playerState=$playerState, state=$state, mediaInfo=$mediaInfo")
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        audioRendererThread
        while (!isLooperPrepared.get()) {}
        audioRendererHandler
        state.set(RendererState.Paused)
        audioTrack
    }

    fun play() {
        val state = getState()
        if (state == RendererState.Paused ||
            state == RendererState.Eof ||
            state == RendererState.WaitingReadableFrameBuffer) {
            if (state == RendererState.Paused || state == RendererState.Eof) {
                this.state.set(RendererState.Playing)
            }
            requestRender()
            audioTrack.play()
        } else {
            MediaLog.e(TAG, "Play error, because of state: $state")
        }
    }

    fun pause() {
        val state = getState()
        if (state == RendererState.Playing ||
            state == RendererState.Eof ||
            state == RendererState.WaitingReadableFrameBuffer) {
            if (state == RendererState.Playing || state == RendererState.Eof) {
                this.state.set(RendererState.Paused)
            }
            audioTrack.pause()
        } else {
            MediaLog.e(TAG, "Pause error, because of state: $state")
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
        } else {
            MediaLog.e(TAG, "Flush error, because of state: $state")
        }
    }

    fun readableFrameReady() {
        val state = getState()
        if (state == RendererState.WaitingReadableFrameBuffer) {
            requestRender()
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
            } else {
                MediaLog.e(TAG, "Release error, because of state: $state")
            }
        }
    }

    fun getState(): RendererState = state.get()

    private fun requestRender() {
        val state = getState()
        if (state in canRenderStates) {
            audioRendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            audioRendererHandler.sendEmptyMessage(RendererHandlerMsg.RequestRender.ordinal)
        } else {
            MediaLog.e(TAG, "Request render error, because of state: $state")
        }
    }

    companion object {
        private const val TAG = "AudioRenderer"
    }

}