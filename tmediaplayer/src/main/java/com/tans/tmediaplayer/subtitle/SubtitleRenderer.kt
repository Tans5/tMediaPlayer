package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.TextView
import com.tans.tmediaplayer.MediaLog
import com.tans.tmediaplayer.player.renderer.RendererHandlerMsg
import com.tans.tmediaplayer.player.renderer.RendererState
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

internal class SubtitleRenderer(
    private val player: tMediaPlayer,
    private val subtitle: tMediaSubtitle,
    looper: Looper
) {
    private val state: AtomicReference<RendererState> = AtomicReference(RendererState.Paused)

    private val frameQueue: SubtitleFrameQueue = subtitle.frameQueue

    private val canRenderStates = arrayOf(
        RendererState.Playing,
        RendererState.Eof,
        RendererState.WaitingReadableFrameBuffer
    )

    private val uiThreadHandler: Handler = Handler(Looper.getMainLooper())

    private val latestSubtitleShowingRange: AtomicReference<LongRange?> = AtomicReference(null)

    private val rendererHandler: Handler = object : Handler(looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            synchronized(this@SubtitleRenderer) {
                when (msg.what) {
                    RendererHandlerMsg.RequestRender.ordinal -> {
                        val state = getState()
                        if (state in canRenderStates) {
                            val frame = frameQueue.peekReadable()
                            if (frame != null) {
                                if (state == RendererState.WaitingReadableFrameBuffer || state == RendererState.Eof) {
                                    this@SubtitleRenderer.state.set(RendererState.Playing)
                                }
                                val playerPts = player.getProgress()
                                val frameShowRange = LongRange(frame.startPts, frame.endPts)
                                when  {
                                    playerPts > frameShowRange.last -> {
                                        val f = frameQueue.dequeueReadable()
                                        if (f === frame) {
                                            frameQueue.enqueueWritable(f)
                                            subtitle.writeableFrameReady()
                                            MediaLog.e(TAG, "Drop subtitle frame, playerPts=$playerPts, frame=${frame}")
                                        } else if (f != null) {
                                            frameQueue.enqueueWritable(f)
                                        }
                                        requestRender()
                                    }
                                    playerPts < frameShowRange.first -> {
                                        val needDelay = min( frameShowRange.first - playerPts, MAX_RENDER_DELAY_INTERVAL)
                                        MediaLog.d(TAG, "Need delay ${needDelay}ms to show subtitle frame=$frame")
                                        val lastShowingRange = latestSubtitleShowingRange.get()
                                        if (lastShowingRange != null &&
                                            frameShowRange.first >= lastShowingRange.last &&
                                            frameShowRange.first - lastShowingRange.last <= 100L) {
                                            latestSubtitleShowingRange.set(LongRange(lastShowingRange.first, frameShowRange.last))
                                        }
                                        requestRender(needDelay)
                                    }
                                    else -> {
                                        val f = frameQueue.dequeueReadable()
                                        if (f === frame) {
                                            latestSubtitleShowingRange.set(frameShowRange)
                                            val textView = player.getSubtitleView()
                                            if (textView != null) {
                                                val textBuilder = StringBuilder()
                                                val subtitles = (frame.subtitles ?: emptyList()).withIndex().toList()
                                                for ((i, s) in subtitles) {
                                                    textBuilder.append(s)
                                                    if (i != subtitles.lastIndex) {
                                                        textBuilder.append('\n')
                                                    }
                                                }
                                                val text = textBuilder.toString()
                                                uiThreadHandler.post {
                                                    textView.text = text
                                                    textView.show()
                                                }
                                            }
                                            MediaLog.d(TAG, "Show subtitle: $frame")
                                        }
                                        if (f != null) {
                                            frameQueue.enqueueWritable(f)
                                            subtitle.writeableFrameReady()
                                        }
                                        requestRender()
                                    }
                                }

                            } else {
                                if (state == RendererState.Playing) {
                                    this@SubtitleRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                }
                                MediaLog.d(TAG, "Waiting readable subtitle frame.")
                            }
                        }
                    }
                }
            }
        }
    }

    fun play() {
        synchronized(this) {
            val state = getState()
            if (state == RendererState.Paused ||
                state == RendererState.Eof
            ) {
                this.state.set(RendererState.Playing)
                requestRender()
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
            } else {
                MediaLog.e(TAG, "Pause error, because of state: $state")
            }
        }
    }

    fun release() {
        synchronized(this) {
            val state = getState()
            if (state != RendererState.NotInit && state != RendererState.Released) {
                this.state.set(RendererState.Released)
                this.latestSubtitleShowingRange.set(null)
                uiThreadHandler.post {
                    val view = player.getSubtitleView()
                    if (view != null && view.isVisible()) {
                        view.hide()
                    }
                }
                MediaLog.d(TAG, "Subtitle renderer released.")
            } else {
                MediaLog.e(TAG, "Release error, because of state: $state")
            }
        }
    }

    fun playerProgressUpdated() {
        val showingRange = latestSubtitleShowingRange.get()
        val textView = player.getSubtitleView()
        if (showingRange != null && textView != null) {
            val pts = player.getProgress()
            if (pts !in showingRange && textView.isVisible()) {
                uiThreadHandler.post {
                    // Check twice
                    val p = player.getProgress()
                    val r = latestSubtitleShowingRange.get()
                    if (r != null && p !in r && textView.isVisible()) {
                        textView.hide()
                    }
                }
            }
        }
    }

    fun readableFrameReady() {
        val state = getState()
        if (state == RendererState.WaitingReadableFrameBuffer) {
            requestRender()
        }
    }

    fun getState(): RendererState = state.get()

    private fun requestRender(delay: Long = 0) {
        val state = getState()
        if (state in canRenderStates) {
            rendererHandler.removeMessages(RendererHandlerMsg.RequestRender.ordinal)
            rendererHandler.sendEmptyMessageDelayed(RendererHandlerMsg.RequestRender.ordinal, delay)
        } else {
            MediaLog.e(TAG, "Request render error, because of state: $state")
        }
    }

    companion object {
        private const val TAG = "SubtitleRenderer"

        private fun TextView.isVisible(): Boolean {
            return this.visibility == View.VISIBLE
        }

        private fun TextView.show() {
            if (!isVisible()) {
                this.visibility = View.VISIBLE
            }
        }

        private fun TextView.hide() {
            if (isVisible()) {
                this.visibility = View.GONE
            }
        }

        private const val MAX_RENDER_DELAY_INTERVAL = 5000L
    }
}