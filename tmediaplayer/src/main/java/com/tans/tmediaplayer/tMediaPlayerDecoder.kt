package com.tans.tmediaplayer

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Decode audio and video frames by native code.
 */
@Suppress("ClassName")
internal class tMediaPlayerDecoder(
    private val player: tMediaPlayer,
    private val bufferManager: tMediaPlayerBufferManager
) {

    // Is decode thread ready?
    private val isLooperPrepared: AtomicBoolean by lazy { AtomicBoolean(false) }

    // Decode thread.
    private val decoderThread: HandlerThread by lazy {
        object : HandlerThread("tMediaPlayerDecoderThread", Thread.MAX_PRIORITY) {
            override fun onLooperPrepared() {
                super.onLooperPrepared()
                isLooperPrepared.set(true)
            }
        }.apply { start() }
    }

    // Decode handler.
    private val decoderHandler: Handler by lazy {
        while (!isLooperPrepared.get()) {}
        object : Handler(decoderThread.looper) {
            override fun dispatchMessage(msg: Message) {
                super.dispatchMessage(msg)
                // Decoder thread do decode need lock player.
                synchronized(player) {
                    val mediaInfo = player.getMediaInfo()
                    if (mediaInfo == null) {
                        MediaLog.e(TAG, "DecoderHandler decode error media info is null.")
                        return
                    }
                    val state = getState()
                    if (state == tMediaPlayerDecoderState.Released || state == tMediaPlayerDecoderState.NotInit) {
                        MediaLog.e(TAG, "DecoderHandler wrong state: $state")
                        return
                    }
                    when (msg.what) {
                        /**
                         * Decode media frame.
                         */
                        DECODE_MEDIA_FRAME -> {
                            if (state == tMediaPlayerDecoderState.Decoding) {
                                if (bufferManager.isVideoDecodeBufferCanUse() && bufferManager.isAudioDecodeBufferCanUse()) {
                                    val nativePlayer = player.getMediaInfo()?.nativePlayer
                                    if (nativePlayer != null) {

                                        // Invoke native decode code.
                                        val nativeBuffer = player.decodeNativeInternal(nativePlayer)
                                        val isVideo = player.isVideoBufferNativeInternal(nativeBuffer)
                                        val decodeResult = player.getBufferResultNativeInternal(nativeBuffer).toDecodeResult()

                                        if (isVideo) {
                                            // Video Frame
                                            when (decodeResult) {
                                                DecodeResult.Success -> {
                                                    // add buffer to waiting render.
                                                    bufferManager.enqueueVideoNativeRenderBuffer(
                                                        tMediaPlayerBufferManager.Companion.MediaBuffer(nativeBuffer))
                                                    // notify player.
                                                    player.decodeSuccess()
                                                    // do next frame decode.
                                                    this.sendEmptyMessage(DECODE_MEDIA_FRAME)
                                                }
                                                DecodeResult.Fail -> {
                                                    // decode fail.
                                                    bufferManager.enqueueVideoNativeEncodeBuffer(tMediaPlayerBufferManager.Companion.MediaBuffer(nativeBuffer))
                                                    MediaLog.e(TAG, "Decode video fail.")
                                                    this.sendEmptyMessage(DECODE_MEDIA_FRAME)
                                                }
                                                else -> {

                                                }
                                            }

                                        } else {
                                            // Audio Frame
                                            when (decodeResult) {
                                                DecodeResult.Success -> {
                                                    // add buffer to waiting render.
                                                    bufferManager.enqueueAudioNativeRenderBuffer(
                                                        tMediaPlayerBufferManager.Companion.MediaBuffer(nativeBuffer))
                                                    // notify player.
                                                    player.decodeSuccess()
                                                    // do next frame decode.
                                                    this.sendEmptyMessage(DECODE_MEDIA_FRAME)
                                                }
                                                // Last frame must be audio buffer.
                                                DecodeResult.DecodeEnd -> {
                                                    // no next frame to decode.
                                                    MediaLog.d(TAG, "Decode end.")
                                                    bufferManager.enqueueAudioNativeRenderBuffer(
                                                        tMediaPlayerBufferManager.Companion.MediaBuffer(nativeBuffer))
                                                    this@tMediaPlayerDecoder.state.set(
                                                        tMediaPlayerDecoderState.DecodingEnd
                                                    )
                                                    player.decodeSuccess()
                                                }
                                                DecodeResult.Fail -> {
                                                    // decode fail.
                                                    bufferManager.enqueueAudioNativeEncodeBuffer(tMediaPlayerBufferManager.Companion.MediaBuffer(nativeBuffer))
                                                    MediaLog.e(TAG, "Decode audio fail.")
                                                    this.sendEmptyMessage(DECODE_MEDIA_FRAME)
                                                }
                                            }
                                        }
                                    } else {
                                        MediaLog.e(TAG, "Native player is null.")
                                    }
                                } else {
                                    // no decode buffer to use, waiting renderer release buffer.
                                    this@tMediaPlayerDecoder.state.set(tMediaPlayerDecoderState.WaitingRender)
                                    MediaLog.d(TAG, "Waiting render buffer.")
                                }
                            } else {
                                MediaLog.d(TAG, "Skip decode frame, because of state: $state")
                            }
                        }
                        /**
                         * Player State: Pause -> Playing.
                         * Restart to decode.
                         */
                        REQUEST_DECODE -> {
                            if (state in listOf(
                                    tMediaPlayerDecoderState.DecodingEnd,
                                    tMediaPlayerDecoderState.WaitingRender,
                                    tMediaPlayerDecoderState.Paused,
                                    tMediaPlayerDecoderState.Prepared
                                )
                            ) {
                                this@tMediaPlayerDecoder.state.set(tMediaPlayerDecoderState.Decoding)
                                this.sendEmptyMessage(DECODE_MEDIA_FRAME)
                            } else {
                                MediaLog.d(TAG, "Skip request decode, because of state: $state")
                            }
                        }

                        /**
                         * Player State: Playing -> Pause
                         * Pause decode.
                         */
                        REQUEST_PAUSE -> {
                            if (state == tMediaPlayerDecoderState.Decoding || state == tMediaPlayerDecoderState.WaitingRender) {
                                this@tMediaPlayerDecoder.state.set(tMediaPlayerDecoderState.Paused)
                            } else {
                                MediaLog.d(TAG, "Skip request pause, because of state: $state")
                            }
                        }

                        /**
                         * Player do seeking.
                         */
                        SEEK_TO -> {
                            val position = msg.obj as? Long
                            if (position != null) {
                                val start = SystemClock.uptimeMillis()
                                val buffer = bufferManager.requestDecodeBufferForce()
                                // Do seeking by native code.
                                val seekResult = player.seekToNativeInternal(
                                    nativePlayer = mediaInfo.nativePlayer,
                                    videoNativeBuffer = buffer.nativeBuffer,
                                    targetPtsInMillis = position
                                ).toOptResult()
                                val end = SystemClock.uptimeMillis()
                                // Notify player seeking finished.
                                player.handleSeekingBuffer(buffer, seekResult)
                                if (seekResult == OptResult.Success) {
                                    MediaLog.d(TAG, "Seek to $position success, cost: ${end - start} ms.")
                                } else {
                                    MediaLog.e(TAG, "Seek to $position fail, cost ${end - start} ms.")
                                    bufferManager.enqueueDecodeBuffer(buffer)
                                }
                            } else {
                                MediaLog.e(TAG, "Seek wrong arg: ${msg.obj}")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private val state: AtomicReference<tMediaPlayerDecoderState> by lazy { AtomicReference(tMediaPlayerDecoderState.NotInit) }

    /**
     * Init decoder
     */
    fun prepare() {
        val lastState = getState()
        if (lastState == tMediaPlayerDecoderState.Released) {
            MediaLog.e(TAG, "Prepare fail, decoder has released.")
            return
        }
        // Trigger decoder thread create.
        decoderThread
        decoderHandler
        decoderHandler.removeMessages(DECODE_MEDIA_FRAME)
        decoderHandler.removeMessages(REQUEST_DECODE)
        decoderHandler.removeMessages(REQUEST_PAUSE)
        state.set(tMediaPlayerDecoderState.Prepared)
    }

    /**
     * Start decode.
     */
    fun decode() {
        val state = getState()
        if (state != tMediaPlayerDecoderState.NotInit && state != tMediaPlayerDecoderState.Released) {
            decoderHandler.sendEmptyMessage(REQUEST_DECODE)
        }
    }

    /**
     * Pause decode.
     */
    fun pause() {
        val state = getState()
        if (state != tMediaPlayerDecoderState.NotInit && state != tMediaPlayerDecoderState.Released) {
            decoderHandler.sendEmptyMessage(REQUEST_PAUSE)
        }
    }

    /**
     * Do seek
     */
    fun seekTo(position: Long) {
        val msg = Message.obtain()
        msg.what = SEEK_TO
        msg.obj = position
        decoderHandler.sendMessage(msg)
    }

    /**
     * Contain new empty buffer to decode。
     */
    fun checkDecoderBufferIfWaiting() {
        if (getState() == tMediaPlayerDecoderState.WaitingRender) {
            decode()
        }
    }

    fun release() {
        decoderHandler.removeMessages(DECODE_MEDIA_FRAME)
        decoderHandler.removeMessages(REQUEST_DECODE)
        decoderHandler.removeMessages(REQUEST_PAUSE)
        decoderThread.quit()
        decoderThread.quitSafely()
        this.state.set(tMediaPlayerDecoderState.Released)
    }

    fun getState(): tMediaPlayerDecoderState = state.get()

    companion object {

        private const val DECODE_MEDIA_FRAME = 0
        private const val REQUEST_DECODE = 1
        private const val REQUEST_PAUSE = 2
        private const val SEEK_TO = 3
        private const val TAG = "tMediaPlayerDecoder"
    }
}