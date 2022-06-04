package com.tans.tmediaplayer

import android.view.Surface
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MediaPlayer {

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
            decodeInternal()
        } else {
            playerId.set(null)
        }
    }

    fun setSurface(surface: Surface?) {
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
        }
    }

    fun getCurrentState(): MediaPlayerState = playerState.get()

    fun releasePlayer() {
        if (getCurrentState() == MediaPlayerState.Playing) {
            mediaWorker.postOpt {
                stopInternal()
                mediaWorker.postDecode {
                    releasePlayerInternal()
                    mediaWorker.release()
                    newState(MediaPlayerState.Released)
                }
            }
        } else {
            mediaWorker.postDecode {
                releasePlayerInternal()
                mediaWorker.release()
                newState(MediaPlayerState.Released)
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
    }

    private fun renderInternal(delay: Long = 0L) {
        mediaWorker.postOpt(delay = delay) {

        }
    }

    private fun decodeInternal() {
        mediaWorker.postDecode {

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