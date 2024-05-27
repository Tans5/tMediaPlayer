package com.tans.tmediaplayer.player

import androidx.annotation.Keep
import com.tans.tmediaplayer.player.model.MediaInfo
import com.tans.tmediaplayer.player.render.tMediaPlayerView
import java.util.concurrent.atomic.AtomicReference

@Suppress("ClassName")
@Keep
class tMediaPlayer2(
    private val audioOutputChannel: AudioChannel = AudioChannel.Stereo,
    private val audioOutputSampleRate: AudioSampleRate = AudioSampleRate.Rate48000,
    private val audioOutputSampleBitDepth: AudioSampleBitDepth = AudioSampleBitDepth.SixteenBits,
    private val enableVideoHardwareDecoder: Boolean = true,
) : IPlayer {

    private val listener: AtomicReference<tMediaPlayerListener?> by lazy {
        AtomicReference(null)
    }

    private val state: AtomicReference<tMediaPlayerState> by lazy {
        AtomicReference(tMediaPlayerState.NoInit)
    }


    // region public methods

    @Synchronized
    override fun prepare(file: String): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun play(): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun pause(): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun seekTo(position: Long): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun stop(): OptResult {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun release(): OptResult {
        TODO("Not yet implemented")
    }

    override fun getProgress(): Long {
        TODO("Not yet implemented")
    }

    override fun getState(): tMediaPlayerState = state.get()

    override fun getMediaInfo(): MediaInfo? {
        TODO("Not yet implemented")
    }

    override fun setListener(l: tMediaPlayerListener?) {
        listener.set(l)
        l?.onPlayerState(getState())
    }

    override fun attachPlayerView(view: tMediaPlayerView?) {
        TODO("Not yet implemented")
    }
    // endregion

    companion object {
        private const val TAG = "tMediaPlayer"

        init {
            System.loadLibrary("tmediaplayer2")
        }
    }
}