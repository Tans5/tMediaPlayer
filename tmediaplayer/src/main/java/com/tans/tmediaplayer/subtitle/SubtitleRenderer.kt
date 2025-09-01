package com.tans.tmediaplayer.subtitle

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.TextView
import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.renderer.RendererHandlerMsg
import com.tans.tmediaplayer.player.renderer.RendererState
import com.tans.tmediaplayer.player.rwqueue.ReadWriteQueueListener
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

    private val frameListener: ReadWriteQueueListener = object : ReadWriteQueueListener {
        override fun onNewWriteableFrame() {}
        override fun onNewReadableFrame() {
            readableFrameReady()
        }
    }

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
                                            tMediaPlayerLog.e(TAG) { "Drop subtitle frame, playerPts=$playerPts, frame=${frame}" }
                                        } else if (f != null) {
                                            frameQueue.enqueueWritable(f)
                                        }
                                        requestRender()
                                    }
                                    playerPts < frameShowRange.first -> {
                                        val needDelay = min( frameShowRange.first - playerPts, MAX_RENDER_DELAY_INTERVAL)
                                        tMediaPlayerLog.d(TAG) { "Need delay ${needDelay}ms to show subtitle frame=$frame" }
                                        requestRender(needDelay)
                                    }
                                    else -> {
                                        val f = frameQueue.dequeueReadable()
                                        if (f === frame) {
                                            frameQueue.enqueueWritable(f)
                                            // TODO: Render subtitle frame.
//                                            latestSubtitleShowingRange.set(frameShowRange)
//                                            val textView = player.getSubtitleView()
//                                            if (textView != null) {
//                                                val textBuilder = StringBuilder()
//                                                val subtitles = (frame.subtitles ?: emptyList()).withIndex().toList()
//                                                for ((i, s) in subtitles) {
//                                                    textBuilder.append(s)
//                                                    if (i != subtitles.lastIndex) {
//                                                        textBuilder.append('\n')
//                                                    }
//                                                }
//                                                val text = textBuilder.toString()
//                                                uiThreadHandler.post {
//                                                    textView.text = text
//                                                }
//                                            }
                                            tMediaPlayerLog.d(TAG) { "Show subtitle: $frame" }
                                        }
                                        if (f != null) {
                                            frameQueue.enqueueWritable(f)
                                        }
                                        requestRender()
                                    }
                                }

                            } else {
                                if (state == RendererState.Playing) {
                                    this@SubtitleRenderer.state.set(RendererState.WaitingReadableFrameBuffer)
                                }
                                // tMediaPlayerLog.d(TAG) { "Waiting readable subtitle frame." }
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        frameQueue.addListener(frameListener)
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
                tMediaPlayerLog.e(TAG) { "Play error, because of state: $state" }
            }
        }
    }

    fun pause() {
        synchronized(this) {
            val state = getState()
            if (state in canRenderStates) {
                this.state.set(RendererState.Paused)
            } else {
                tMediaPlayerLog.e(TAG) { "Pause error, because of state: $state" }
            }
        }
    }

    fun release() {
        synchronized(this) {
            val state = getState()
            if (state != RendererState.NotInit && state != RendererState.Released) {
                this.state.set(RendererState.Released)
                frameQueue.removeListener(frameListener)
                this.latestSubtitleShowingRange.set(null)
                uiThreadHandler.post {
                    val view = player.getSubtitleView()
                    if (view != null && view.isVisible()) {
                        view.hide()
                    }
                }
                tMediaPlayerLog.d(TAG) { "Subtitle renderer released." }
            } else {
                tMediaPlayerLog.e(TAG) { "Release error, because of state: $state" }
            }
        }
    }

    fun playerProgressUpdated(pts: Long) {
        val fixedShowingRange = latestSubtitleShowingRange.get()?.let { LongRange(it.first - SHOW_TEXT_BUFFER, it.last + SHOW_TEXT_BUFFER) }
        val textView = player.getSubtitleView()
        if (textView != null) {
            if ((fixedShowingRange == null || pts !in fixedShowingRange)) {
                // Do hide.
                if (textView.isVisible()) {
                    uiThreadHandler.post {
                        textView.hide()
                        tMediaPlayerLog.d(TAG) { "Hide text view, pts=$pts, range=${latestSubtitleShowingRange.get()}" }
                    }
                }
            } else {
                // Do show
                if (!textView.isVisible()) {
                    uiThreadHandler.post {
                        textView.show()
                        tMediaPlayerLog.d(TAG) { "Show text view, pts=$pts, range=${latestSubtitleShowingRange.get()}" }
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
            tMediaPlayerLog.e(TAG) { "Request render error, because of state: $state" }
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

        private const val SHOW_TEXT_BUFFER = 50L

    }
}