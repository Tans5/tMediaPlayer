package com.tans.tmediaplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

typealias StateObserver = (state: MediaPlayerState) -> Unit

typealias ProgressObserver = (position: Long, duration: Long) -> Unit

typealias RenderStateObserver = (isRenderActive: Boolean) -> Unit

class MediaPlayer(private val rowBufferSize: Int = 50) {

    private val playerId: AtomicReference<Long?> by lazy {
        AtomicReference(null)
    }

    private val duration: AtomicLong by lazy {
        AtomicLong(0L)
    }

    private val mediaWorker: MediaPlayerWorker by lazy {
        MediaPlayerWorker()
    }

    private val playerState: AtomicReference<MediaPlayerState> by lazy {
        AtomicReference(MediaPlayerState.NotInit)
    }

    private val pool: AtomicReference<MediaRawDataPool?> by lazy {
        AtomicReference(null)
    }

    private val startAnchorTime: AtomicReference<Long?> by lazy {
        AtomicReference(null)
    }

    private val pauseTime: AtomicReference<Long?> by lazy {
        AtomicReference(null)
    }

    private val internalSurfaceTextureListener: TextureView.SurfaceTextureListener by lazy {
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, p1: Int, p2: Int) { setSurface(
                Surface(st)
            ) }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                setSurface(null)
                return true
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {}
        }
    }

    private val stateObserver: AtomicReference<StateObserver?> by lazy {
        AtomicReference(null)
    }

    private val progressObserver: AtomicReference<ProgressObserver?> by lazy {
        AtomicReference(null)
    }

    private val renderStateObserver: AtomicReference<RenderStateObserver?> by lazy {
        AtomicReference(null)
    }

    private var surface: SoftReference<Surface?>? = null

    fun setupPlayer(filePath: String) {
        startAnchorTime.set(null)
        pauseTime.set(null)
        mediaWorker.postOpt {
            val currentState = getPlayerState()
            if (currentState == MediaPlayerState.Playing) {
                stopInternal()
                releasePlayerInternal()
                mediaWorker.postOpt {
                    setupPlayerInternal(filePath)
                }
            } else {
                setupPlayerInternal(filePath)
            }
        }
    }

    private fun setupPlayerInternal(filePath: String) {
        val optResult = setupPlayerNative(filePath)
        if (optResult.toInt() != OptResult.OptFail.code) {
            playerId.set(optResult)
            duration.set(getDurationNative(optResult))
            val poolLocal = pool.get()
            if (poolLocal == null) {
                val values = List(rowBufferSize) {
                    newRawDataNative(optResult)
                }
                MediaRawDataPool(values = values).let {
                    it.reset()
                    pool.set(it)
                }
            } else {
                poolLocal.reset()
            }
            newState(MediaPlayerState.Prepared)
            val s = this.surface?.get()
            if (s != null) {
                setWindowNative(optResult, s)
            }
        } else {
            playerId.set(null)
            newState(MediaPlayerState.Error)
        }
    }

    fun setTextureView(textureView: TextureView?) {
        if (textureView == null) {
            setSurface(null)
        } else {
            textureView.surfaceTextureListener = internalSurfaceTextureListener
        }
    }

    private fun setSurface(surface: Surface?) {
        val lastSurface = this.surface?.get()
        if (lastSurface != surface) {
            this.surface = SoftReference(surface)
            mediaWorker.postOpt {
                val id = playerId.get()
                if (id != null) {
                    setWindowNative(id, surface)
                }
                if (surface != null) {
                    renderStateObserver.get()?.invoke(true)
                } else {
                    renderStateObserver.get()?.invoke(false)
                }
            }
        }
    }

    fun playStart() {
        mediaWorker.postOpt {
            playerStartInternal()
        }
    }

    private fun playerStartInternal() {
        val playerId = playerId.get()
        if (playerId != null) {
            pool.get()?.consume(MediaRawDataPool.PRODUCE_STOPPED)
            pool.get()?.produce(MediaRawDataPool.PRODUCE_STOPPED to 0)
            mediaWorker.postDecode {
                resetPlayProgressNative(playerId)
                pool.get()?.reset()
                decodeInternal()
                mediaWorker.postOpt {
                    renderInternal()
                }
            }
            startAnchorTime.set(SystemClock.uptimeMillis())
            pauseTime.set(null)
            newState(MediaPlayerState.PlayStared)
            newState(MediaPlayerState.Playing)
        }
    }

    fun play() {
        mediaWorker.postOpt {
            playInternal()
        }
    }

    private fun playInternal() {
        val state = getPlayerState()
        if (state != MediaPlayerState.Playing) {
            val anchorTime = startAnchorTime.get()
            if (anchorTime == null) {
                startAnchorTime.set(SystemClock.uptimeMillis())
                pauseTime.set(null)
            } else {
                val pauseTime = this.pauseTime.get()
                if (pauseTime != null) {
                    val newAnchorTime = anchorTime + max(SystemClock.uptimeMillis() - pauseTime, 0)
                    startAnchorTime.set(newAnchorTime)
                    this.pauseTime.set(null)
                }
            }

            renderInternal()
            newState(MediaPlayerState.Playing)
        }
    }

    fun pause() {
        mediaWorker.postOpt {
            pauseInternal()
        }
    }

    private fun pauseInternal() {
        if (getPlayerState() == MediaPlayerState.Playing) {
            pauseTime.set(SystemClock.uptimeMillis())
            newState(MediaPlayerState.Paused)
        }
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        val currentState = getPlayerState()
        if (currentState == MediaPlayerState.Playing || currentState == MediaPlayerState.Paused) {
            startAnchorTime.set(null)
            pauseTime.set(null)
            newState(MediaPlayerState.PlayStopped)
            mediaWorker.postOpt {
                pool.get()?.consume(MediaRawDataPool.PRODUCE_STOPPED)
                pool.get()?.produce(MediaRawDataPool.PRODUCE_STOPPED to 0)
                mediaWorker.postDecode {
                    val playerId = playerId.get()
                    if (playerId != null) {
                        resetPlayProgressNative(playerId)
                        pool.get()?.reset()
                    }
                }
            }
        }
    }

    fun getPlayerState(): MediaPlayerState = playerState.get()

    fun setPlayerStateObserver(o: StateObserver?) {
        stateObserver.set(o)
    }

    fun setProgressObserver(o: ProgressObserver?) {
        progressObserver.set(o)
    }

    fun setRenderStateObserver(o: RenderStateObserver?) {
        renderStateObserver.set(o)
    }

    fun isRenderActive(): Boolean = surface?.get() != null

    fun releasePlayer() {
        stopInternal()
        mediaWorker.postOpt {
            mediaWorker.postDecode {
                val playerId = playerId.get()
                releasePlayerInternal()
                val values = pool.get()?.values
                if (playerId != null && values != null) {
                    for (v in values) {
                        releaseRawDataNative(playerId = playerId, dataId = v)
                    }
                }
                pool.set(null)
                mediaWorker.release()
                newState(MediaPlayerState.Released)
                setPlayerStateObserver(null)
                setProgressObserver(null)
                setRenderStateObserver(null)
            }
        }
    }

    private fun releasePlayerInternal() {
        val id = playerId.get()
        if (id != null) {
            releasePlayerNative(id)
            playerId.set(null)
            duration.set(0L)
            newState(MediaPlayerState.NotInit)
        }
    }

    private fun newState(state: MediaPlayerState) {
        playerState.set(state)
        stateObserver.get()?.invoke(state)
    }

    private fun renderInternal(delay: Long = 0L) {
        mediaWorker.postOpt(delay = delay) {
            val playerId = playerId.get()
            val pool = pool.get()
            if (playerId != null && pool != null) {
                val state = getPlayerState()
                if (state == MediaPlayerState.Playing) {
                    val (renderData, pts) = pool.waitProducer()
                    if (renderData != MediaRawDataPool.PRODUCE_END) {
                        if (renderData != MediaRawDataPool.PRODUCE_STOPPED) {
                            val renderResult = renderRawDataNative(playerId = playerId, dataId = renderData)
                            pool.consume(renderData)
                            if (renderResult == OptResult.OptSuccess.code) {
                                if (pts > 0) {
                                    progressObserver.get()?.invoke(pts, duration.get())
                                    val d = startAnchorTime.get().let { anchor ->
                                        if (anchor == null) {
                                            Log.e(TAG, "Anchor time is null.")
                                            0L
                                        } else {
                                            val playTime = SystemClock.uptimeMillis() - anchor
                                            val d = pts - playTime
                                            if (d < 0) {
                                                Log.w(TAG, "Render behind: $d ms")
                                            }
                                            max(0, d)
                                        }
                                    }
                                    renderInternal(d)
                                } else {
                                    renderInternal()
                                }
                            } else {
                                renderInternal()
                            }
                        }
                    } else {
                        newState(MediaPlayerState.PlayEnd)
                    }
                }
            } else {
                newState(MediaPlayerState.Error)
            }
        }
    }

    private fun decodeInternal() {
        mediaWorker.postDecode {
            val playerId = playerId.get()
            val pool = pool.get()
            if (playerId != null && pool != null) {
                val renderData = pool.waitConsumer()
                if (renderData != MediaRawDataPool.PRODUCE_STOPPED && renderData != MediaRawDataPool.PRODUCE_END) {
                    val decodeResult = decodeNextFrameNative(playerId = playerId, dataId = renderData)
                    when (decodeResult.getOrNull(0)?.toInt()) {
                        DecodeResult.DecodeSuccess.code -> {
                            pool.produce(renderData to decodeResult[1])
                            decodeInternal()
                        }
                        DecodeResult.DecodeEnd.code -> {
                            pool.produce(MediaRawDataPool.PRODUCE_END to MediaRawDataPool.PRODUCE_END)
                        }
                        else -> {
                            pool.consume(renderData);
                            decodeInternal()
                        }
                    }
                }
            } else {
                newState(MediaPlayerState.Error)
            }
        }
    }

    private external fun setupPlayerNative(filePath: String): Long

    private external fun getDurationNative(playerId: Long): Long

    private external fun setWindowNative(playerId: Long, surface: Surface?): Int

    private external fun resetPlayProgressNative(playerId: Long): Int

    private external fun decodeNextFrameNative(playerId: Long, dataId: Long): LongArray

    private external fun renderRawDataNative(playerId: Long, dataId: Long): Int

    private external fun newRawDataNative(playerId: Long): Long

    private external fun releaseRawDataNative(playerId: Long, dataId: Long): Int

    private external fun releasePlayerNative(playerId: Long)

    fun onNewVideoFrame(width: Int, height: Int, bytes: ByteArray) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        println(bytes)
    }

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
        const val TAG = "tMediaPlayer"
    }
}