package com.tans.tmediaplayer

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

typealias StateObserver = (state: MediaPlayerState) -> Unit

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

    private var surface: SoftReference<Surface?>? = null

    fun setupPlayer(filePath: String) {
        mediaWorker.postOpt {
            val currentState = getCurrentState()
            if (currentState == MediaPlayerState.Playing) {
                stopInternal()
                mediaWorker.postDecode {
                    releasePlayerInternal()
                    mediaWorker.postOpt {
                        setupPlayerInternal(filePath)
                    }
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
            decodeInternal()
            newState(MediaPlayerState.Prepared)
            val s = this.surface?.get()
            if (s != null) {
                setSurface(s)
            }
        } else {
            playerId.set(null)
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
        this.surface = SoftReference(surface)
        mediaWorker.postOpt {
            val id = playerId.get()
            if (id != null) {
                setWindowNative(id, surface)
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
            resetPlayProgress(playerId)
            val needInvokeRender = getCurrentState() != MediaPlayerState.Playing
            newState(MediaPlayerState.PlayStared)
            if (needInvokeRender) {
                renderInternal()
            }
            newState(MediaPlayerState.Playing)
        }
    }

    fun play() {
        mediaWorker.postOpt {
            playInternal()
        }
    }

    private fun playInternal() {
        val state = getCurrentState()
        if (state != MediaPlayerState.Playing) {
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
        newState(MediaPlayerState.Paused)
    }

    fun stop() {
        mediaWorker.postOpt {
            stopInternal()
        }
    }

    private fun stopInternal() {
        newState(MediaPlayerState.PlayStopped)
        val playerId = playerId.get()
        if (playerId != null) {
            resetPlayProgress(playerId)
            pool.get()?.reset()
        }
    }

    fun getCurrentState(): MediaPlayerState = playerState.get()

    fun setStateObserver(o: StateObserver?) {
        stateObserver.set(o)
    }

    fun releasePlayer() {
        pool.get()?.consume(MediaRawDataPool.PRODUCE_RELEASED)
        if (getCurrentState() == MediaPlayerState.Playing) {
            mediaWorker.postOpt {
                stopInternal()
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
                }
                setStateObserver(null)
            }
        } else {
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
                setStateObserver(null)
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
                val state = getCurrentState()
                if (state == MediaPlayerState.Playing) {
                    val (renderData, pts) = pool.waitProducer()
                    if (renderData != MediaRawDataPool.PRODUCE_END && renderData != MediaRawDataPool.PRODUCE_RELEASED) {
                        val renderResult = renderRawDataNative(playerId = playerId, dataId = renderData)
                        if (renderResult == OptResult.OptSuccess.code) {
                            pool.consume(renderData)
                            renderInternal()
                        } else {
                            newState(MediaPlayerState.Error)
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
                if (renderData != MediaRawDataPool.PRODUCE_END && renderData != MediaRawDataPool.PRODUCE_RELEASED) {
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
                            newState(MediaPlayerState.Error)
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

    private external fun resetPlayProgress(playerId: Long): Int

    private external fun decodeNextFrameNative(playerId: Long, dataId: Long): LongArray

    private external fun renderRawDataNative(playerId: Long, dataId: Long): Int

    private external fun newRawDataNative(playerId: Long): Long

    private external fun releaseRawDataNative(playerId: Long, dataId: Long): Int

    private external fun releasePlayerNative(playerId: Long)

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
    }
}