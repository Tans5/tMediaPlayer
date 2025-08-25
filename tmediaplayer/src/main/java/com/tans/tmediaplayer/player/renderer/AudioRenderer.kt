package com.tans.tmediaplayer.player.renderer

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.audiotrack.tMediaAudioTrack
import com.tans.tmediaplayer.player.model.AUDIO_TRACK_QUEUE_SIZE
import com.tans.tmediaplayer.player.model.AudioChannel
import com.tans.tmediaplayer.player.model.AudioSampleBitDepth
import com.tans.tmediaplayer.player.model.AudioSampleRate
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.rwqueue.AudioFrame
import com.tans.tmediaplayer.player.rwqueue.AudioFrameQueue
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import com.tans.tmediaplayer.player.rwqueue.ReadWriteQueueListener
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

internal class AudioRenderer(
    outputChannel: AudioChannel,
    outputSampleRate: AudioSampleRate,
    outputSampleBitDepth: AudioSampleBitDepth,
    bufferQueueSize: Int = AUDIO_TRACK_QUEUE_SIZE,
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
            audioRendererHandler.sendEmptyMessage(RendererHandlerMsg.Rendered.ordinal)
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

    private val frameQueueListener: ReadWriteQueueListener = object : ReadWriteQueueListener {
        override fun onNewWriteableFrame() { }

        override fun onNewReadableFrame() {
            readableFrameReady()
        }
    }

    private val canRenderStates = arrayOf(RendererState.Playing, RendererState.Eof, RendererState.WaitingReadableFrameBuffer)

    private val waitingRenderFrames: LinkedBlockingDeque<AudioFrame> by lazy {
        LinkedBlockingDeque()
    }

    private val audioRendererHandler: Handler by lazy {
        object : Handler(audioRendererThread.looper) {

            private val lastRenderedFrame = LastRenderedFrame()

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
                                if (frame != null) { // Contain frame to render.
                                    if (frame.serial != audioPacketQueue.getSerial()) { // Frame serial changed cause seeking or change files, skip render this frame.
                                        enqueueWritableFrame(frame)
                                        tMediaPlayerLog.d(TAG) { "Serial changed, skip render." }
                                        requestRender()
                                        return@synchronized
                                    }

                                    if (!frame.isEof) { // Not eof frame

                                        if (audioTrack.enqueueBuffer(frame.nativeFrame) == OptResult.Success) { // Send frame to audio track success and add frame to waiting
                                            waitingRenderFrames.addLast(frame)
                                        } else { // Send frame to audio track fail, maybe buffer queue is full.
                                            tMediaPlayerLog.e(TAG) { "Audio frame enqueue fail, audio track queue count: ${audioTrack.getBufferQueueCount()}" }
                                            var retryTimes = 5
                                            var retryEnqueueSuccess = false
                                            // Retry send frame to audio track.
                                            while (retryTimes > 0) {
                                                try {
                                                    Thread.sleep(6)
                                                } catch (e: Throwable) {
                                                    tMediaPlayerLog.e(
                                                        tag = TAG,
                                                        msgGetter = { "Sleep error: ${e.message}" },
                                                        errorGetter = { e }
                                                    )
                                                    break
                                                }
                                                retryTimes --
                                                if (audioTrack.enqueueBuffer(frame.nativeFrame) == OptResult.Success) {
                                                    retryEnqueueSuccess = true
                                                    waitingRenderFrames.addLast(frame)
                                                    tMediaPlayerLog.d(TAG) { "Retry audio frame enqueue success, audio track queue count: ${audioTrack.getBufferQueueCount()}" }
                                                    break
                                                } else {
                                                    tMediaPlayerLog.e(TAG) { "Retry audio frame enqueue fail, audio track queue count: ${audioTrack.getBufferQueueCount()}" }
                                                }
                                            }
                                            if (!retryEnqueueSuccess) {
                                                tMediaPlayerLog.e(TAG) { "After retry enqueue audio frame fail." }
                                                enqueueWritableFrame(frame)
                                            } else {
                                                tMediaPlayerLog.d(TAG) { "After retry enqueue audio frame success." }
                                            }
                                        }
                                        if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                            this@AudioRenderer.state.set(RendererState.Playing)
                                        }
                                        requestRender()
                                    } else { // eof frame
                                        var bufferCount = audioTrack.getBufferQueueCount()
                                        var checkTimes = 0
                                        // Waiting audio track finish all frames.
                                        val maxCheckTimes = bufferCount
                                        while (bufferCount > 0) {
                                            checkTimes++
                                            tMediaPlayerLog.d(TAG) { "Waiting audio track buffer finish, queueCount$bufferCount, checkTimes=$checkTimes" }
                                            try {
                                                Thread.sleep(max(lastRenderedFrame.duration, 6))
                                            } catch (e: Throwable) {
                                                tMediaPlayerLog.e(tag = TAG, msgGetter = { "Sleep error: ${e.message}" }, errorGetter = { e })
                                                break
                                            }
                                            bufferCount = audioTrack.getBufferQueueCount()
                                            if (checkTimes >= maxCheckTimes) {
                                                tMediaPlayerLog.e(TAG) { "Waiting audio track max times $maxCheckTimes, bufferCount=$bufferCount" }
                                                break
                                            }
                                        }
                                        // Recycle all waiting frames.
                                        while (waitingRenderFrames.isNotEmpty()) {
                                            val b = waitingRenderFrames.pollFirst()
                                            if (b != null) {
                                                enqueueWritableFrame(b)
                                            }
                                        }
                                        this@AudioRenderer.state.set(RendererState.Eof)
                                        enqueueWritableFrame(frame)
                                        tMediaPlayerLog.d(TAG) { "Render audio eof." }
                                    }
                                } else {
                                    if (state == RendererState.Playing) {
                                        this@AudioRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                    }
                                    tMediaPlayerLog.d(TAG) { "Waiting readable audio frame." }
                                }
                            } else {
                                tMediaPlayerLog.e(TAG) { "Render audio fail, playerState=$playerState, state=$state, mediaInfo=$mediaInfo" }
                            }
                        }

                        RendererHandlerMsg.Rendered.ordinal -> {
                            val audioTrackBufferCount = audioTrack.getBufferQueueCount()
                            val waitingBufferCount = waitingRenderFrames.size
                            val frame: AudioFrame? = waitingRenderFrames.pollFirst()
                            // Update clock and recycle finished frames.
                            if (frame != null) {
                                val fixedPts = if (lastRenderedFrame.serial == frame.serial) {
                                    lastRenderedFrame.pts + lastRenderedFrame.duration
                                } else {
                                    frame.pts
                                }
                                lastRenderedFrame.serial = frame.serial
                                lastRenderedFrame.pts = frame.pts
                                lastRenderedFrame.duration = frame.duration
                                tMediaPlayerLog.d(TAG) { "Rendered audio frame: fixedPts=$fixedPts, originPts=${frame.pts}, audioTrackBufferCount=$audioTrackBufferCount, waitingBufferCount=$waitingBufferCount" }
                                player.audioClock.setClock(fixedPts, frame.serial)
                                player.externalClock.syncToClock(player.audioClock)
                                enqueueWritableFrame(frame)
                            } else {
                                tMediaPlayerLog.e(TAG) { "No waiting audio buffer, audioTrackBufferCount=$audioTrackBufferCount, waitingBufferCount=$waitingBufferCount" }
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
        audioFrameQueue.addListener(frameQueueListener)
        state.set(RendererState.Paused)
        audioTrack
        tMediaPlayerLog.d(TAG) { "Audio renderer inited." }
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
                tMediaPlayerLog.e(TAG) { "Play error, because of state: $state" }
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
                tMediaPlayerLog.e(TAG) { "Pause error, because of state: $state" }
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
                player.renderedAudioFrame()
            }
        } else {
            tMediaPlayerLog.e(TAG) { "Flush error, because of state: $state" }
        }
    }

    fun readableFrameReady() {
        val state = getState()
        if (state == RendererState.WaitingReadableFrameBuffer) {
            requestRender()
        } else {
            tMediaPlayerLog.d(TAG) { "Skip handle readable audio frame ready, because of state: $state" }
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
                audioFrameQueue.removeListener(frameQueueListener)
                tMediaPlayerLog.d(TAG) { "Audio renderer released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release error, because of state: $state" }
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
            tMediaPlayerLog.e(TAG) { "Request render error, because of state: $state" }
        }
    }

    private fun enqueueWritableFrame(frame: AudioFrame) {
        audioFrameQueue.enqueueWritable(frame)
        player.renderedAudioFrame()
    }

    companion object {

        private class LastRenderedFrame {
            var pts: Long = 0
            var serial: Int = -1
            var duration: Long = 0
        }

        private const val TAG = "AudioRenderer"
    }

}