package com.tans.tmediaplayer.player.renderer

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.audiotrack.tMediaAudioTrack
import com.tans.tmediaplayer.player.model.AudioChannel
import com.tans.tmediaplayer.player.model.AudioSampleBitDepth
import com.tans.tmediaplayer.player.model.AudioSampleRate
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.rwqueue.AudioFrame
import com.tans.tmediaplayer.player.rwqueue.AudioFrameQueue
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AudioRenderer(
    outputChannel: AudioChannel,
    outputSampleRate: AudioSampleRate,
    outputSampleBitDepth: AudioSampleBitDepth,
    bufferQueueSize: Int = 12,
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
            renderCallbackExecutor.execute {
                val frame = waitingRenderFrames.pollFirst()
                if (frame != null) {
                    player.audioClock.setClock(frame.pts, frame.serial)
                    player.externalClock.syncToClock(player.audioClock)
                    audioFrameQueue.enqueueWritable(frame)
                    player.writeableAudioFrameReady()
                }
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

            fun enqueueWritableFrame(frame: AudioFrame) {
                audioFrameQueue.enqueueWritable(frame)
                player.writeableAudioFrameReady()
            }

            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                synchronized(this@AudioRenderer) {
                    when (msg.what) {
                        RendererHandlerMsg.RequestRender.ordinal -> {
                            val playerState = player.getState()
                            val state = getState()
                            val mediaInfo = player.getMediaInfo()
                            if (mediaInfo != null && state in canRenderStates) {
                                val frame = audioFrameQueue.dequeueReadable()
                                if (frame != null) {
                                    if (frame.serial != audioPacketQueue.getSerial()) {
                                        enqueueWritableFrame(frame)
                                        MediaLog.d(TAG, "Serial changed, skip render.")
                                        requestRender()
                                        return@synchronized
                                    }

                                    if (!frame.isEof) {
                                        if (audioTrack.enqueueBuffer(frame.nativeFrame) == OptResult.Success) {
                                            waitingRenderFrames.addLast(frame)
                                        } else {
                                            MediaLog.e(TAG, "Audio frame enqueue fail, audio track queue count: ${audioTrack.getBufferQueueCount()}")
                                            var retryTimes = 5
                                            var retryEnqueueSuccess = false
                                            while (retryTimes > 0) {
                                                try {
                                                    Thread.sleep(6)
                                                } catch (e: Throwable) {
                                                    MediaLog.e(TAG, "Sleep error: ${e.message}", e)
                                                    break
                                                }
                                                retryTimes --
                                                if (audioTrack.enqueueBuffer(frame.nativeFrame) == OptResult.Success) {
                                                    retryEnqueueSuccess = true
                                                    waitingRenderFrames.addLast(frame)
                                                    MediaLog.d(TAG, "Retry audio frame enqueue success, audio track queue count: ${audioTrack.getBufferQueueCount()}")
                                                    break
                                                } else {
                                                    MediaLog.e(TAG, "Retry audio frame enqueue fail, audio track queue count: ${audioTrack.getBufferQueueCount()}")
                                                }
                                            }
                                            if (!retryEnqueueSuccess) {
                                                MediaLog.e(TAG, "After retry enqueue audio frame fail.")
                                                enqueueWritableFrame(frame)
                                            } else {
                                                MediaLog.d(TAG, "After retry enqueue audio frame success.")
                                            }
                                        }
                                        if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                            this@AudioRenderer.state.set(RendererState.Playing)
                                        }
                                        requestRender()
                                    } else {
                                        var bufferCount = audioTrack.getBufferQueueCount()
                                        while (bufferCount > 0) {
                                            MediaLog.d(TAG, "Waiting audio track buffer finish, audio track queue count: $bufferCount")
                                            try {
                                                Thread.sleep(6)
                                            } catch (e: Throwable) {
                                                MediaLog.e(TAG, "Sleep error: ${e.message}", e)
                                                break
                                            }
                                            bufferCount = audioTrack.getBufferQueueCount()
                                        }
                                        while (waitingRenderFrames.isNotEmpty()) {
                                            val b = waitingRenderFrames.pollFirst()
                                            if (b != null) {
                                                audioFrameQueue.enqueueWritable(b)
                                            }
                                        }
                                        MediaLog.d(TAG, "Render audio eof.")
                                        this@AudioRenderer.state.set(RendererState.Eof)
                                        enqueueWritableFrame(frame)
                                    }
                                } else {
                                    if (state == RendererState.Playing) {
                                        this@AudioRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                    }
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
        MediaLog.d(TAG, "Audio renderer inited.")
    }

    fun play() {
        synchronized(this) {
            val state = getState()
            if (state == RendererState.Paused ||
                state == RendererState.Eof) {
                this.state.set(RendererState.Playing)
                requestRender()
                audioTrack.play()
            } else {
                MediaLog.e(TAG, "Play error, because of state: $state")
            }
        }
    }

    fun pause() {
        synchronized(this) {
            val state = getState()
            if (state in canRenderStates) {
                this.state.set(RendererState.Paused)
                audioTrack.pause()
            } else {
                MediaLog.e(TAG, "Pause error, because of state: $state")
            }
        }
    }

    fun flush() {
        val state = getState()
        if (state != RendererState.NotInit && state != RendererState.Released) {
            audioTrack.clearBuffers()
            var isAddWritingBuffer = false
            while (waitingRenderFrames.isNotEmpty()) {
                val b = waitingRenderFrames.pollFirst()
                if (b != null) {
                    audioFrameQueue.enqueueWritable(b)
                    isAddWritingBuffer = true
                }
            }
            if (isAddWritingBuffer) {
                player.writeableAudioFrameReady()
            }
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
                audioRendererThread.quit()
                audioRendererThread.quitSafely()
                MediaLog.d(TAG, "Audio renderer released.")
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