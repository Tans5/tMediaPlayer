package com.tans.tmediaplayer.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.activity.addCallback
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.demo.databinding.PlayerActivityBinding
import com.tans.tmediaplayer.frameloader.tMediaFrameLoader
import com.tans.tmediaplayer.player.model.AudioSampleBitDepth
import com.tans.tmediaplayer.player.model.AudioSampleRate
import com.tans.tmediaplayer.player.tMediaPlayer
import com.tans.tmediaplayer.player.tMediaPlayerListener
import com.tans.tmediaplayer.player.tMediaPlayerState
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.systembar.annotation.FullScreenStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@FullScreenStyle
class PlayerActivity : BaseCoroutineStateActivity<PlayerActivity.Companion.State>(State()) {

    override val layoutId: Int = R.layout.player_activity

    private val mediaPlayer: tMediaPlayer by lazyViewModelField("mediaPlayer") {
        tMediaPlayer(
            audioOutputSampleRate = AudioSampleRate.Rate96000,
            audioOutputSampleBitDepth = AudioSampleBitDepth.ThreeTwoBits,
            enableVideoHardwareDecoder = true
        )
    }

    private fun View.isVisible(): Boolean = this.visibility == View.VISIBLE

    private fun View.isInvisible(): Boolean = !isVisible()

    private fun View.hide() {
        if (isVisible()) {
            this.visibility = View.GONE
        }
    }

    private fun View.show() {
        if (isInvisible()) {
            this.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback {
            finish()
        }
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        launch(Dispatchers.IO) {

            mediaPlayer.setListener(object : tMediaPlayerListener {
                override fun onPlayerState(state: tMediaPlayerState) {
                    updateState { it.copy(playerState = state) }
                }

                override fun onProgressUpdate(progress: Long, duration: Long) {
                    updateState { it.copy(progress = Progress(progress = progress, duration = duration)) }
                }
            })

            val loadResult = mediaPlayer.prepare(intent.getMediaFileExtra())
            when (loadResult) {
                OptResult.Success -> {
                    Log.d(TAG, "Load media file success.")
                }

                OptResult.Fail -> {
                    Log.e(TAG, "Load media file fail.")
                }
            }
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        tMediaFrameLoader
        val viewBinding = PlayerActivityBinding.bind(contentView)

        launch {
            stateFlow.map { it.playerState }.filterIsInstance<tMediaPlayerState.Prepared>().first()
            mediaPlayer.attachPlayerView(viewBinding.playerView)
            mediaPlayer.attachSubtitleView(viewBinding.subtitleTv)
            // mediaPlayer.loadExternalSubtitleFile(File(filesDir, "test.ass").canonicalPath)
            if (mediaPlayer.getState() is tMediaPlayerState.Prepared) {
                mediaPlayer.play()
            }

            if (mediaPlayer.getMediaInfo()?.subtitleStreams?.isEmpty() == false) {
                viewBinding.subtitlesIv.show()
                viewBinding.subtitlesIv.clicks(this) {
                    viewBinding.actionLayout.hide()
                    val d = SubtitleSelectDialog(mediaPlayer)
                    d.show(supportFragmentManager, "SubtitleSelectDialog#${System.currentTimeMillis()}")
                }
            } else {
                viewBinding.subtitlesIv.hide()
            }
        }


        renderStateNewCoroutine({ it.progress.duration }) { duration ->
            viewBinding.durationTv.text = duration.formatDuration()
        }

        var isPlayerSbInTouching = false
        renderStateNewCoroutine({ it.progress }) { (progress, duration) ->
            viewBinding.progressTv.text = progress.formatDuration()
            if (!isPlayerSbInTouching && mediaPlayer.getState() !is tMediaPlayerState.Seeking) {
                val progressInPercent = (progress.toFloat() * 100.0 / duration.toFloat() + 0.5f).toInt()
                viewBinding.playerSb.progress = progressInPercent
            }
        }

        renderStateNewCoroutine({ it.playerState }) { playerState ->
            if (playerState is tMediaPlayerState.Seeking) {
                viewBinding.seekingLoadingPb.visibility = View.VISIBLE
            } else {
                viewBinding.seekingLoadingPb.visibility = View.GONE
            }

            val fixedState = when (playerState) {
                is tMediaPlayerState.Seeking -> playerState.lastState
                else -> playerState
            }
            if (fixedState is tMediaPlayerState.Playing) {
                viewBinding.pauseIv.visibility = View.VISIBLE
            } else {
                viewBinding.pauseIv.visibility = View.GONE
            }

            if (fixedState is tMediaPlayerState.Prepared ||
                fixedState is tMediaPlayerState.Paused ||
                fixedState is tMediaPlayerState.Stopped
            ) {
                viewBinding.playIv.visibility = View.VISIBLE
            } else {
                viewBinding.playIv.visibility = View.GONE
            }

            if (fixedState is tMediaPlayerState.PlayEnd) {
                viewBinding.replayIv.visibility = View.VISIBLE
            } else {
                viewBinding.replayIv.visibility = View.GONE
            }
        }

        viewBinding.rootLayout.clicks(this) {
            if (viewBinding.actionLayout.isVisible()) {
                viewBinding.actionLayout.hide()
            } else {
                viewBinding.actionLayout.show()
            }
        }

        viewBinding.playIv.clicks(this) {
            mediaPlayer.play()
        }

        viewBinding.pauseIv.clicks(this) {
            mediaPlayer.pause()
        }

        viewBinding.replayIv.clicks(this) {
            mediaPlayer.play()
        }

        viewBinding.playerSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isPlayerSbInTouching = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isPlayerSbInTouching = false
                val mediaInfo = mediaPlayer.getMediaInfo()
                if (seekBar != null && mediaInfo != null) {
                    val progressF = seekBar.progress.toFloat() / seekBar.max.toFloat()
                    val requestMediaProgress = (progressF * mediaInfo.duration.toDouble()).toLong()
                    mediaPlayer.seekTo(requestMediaProgress)
                }
            }
        })

        viewBinding.infoIv.clicks(this) {
            val info = mediaPlayer.getMediaInfo()
            if (info != null) {
                viewBinding.actionLayout.hide()
                val d = MediaInfoDialog(info, intent.getMediaFileExtra())
                d.show(supportFragmentManager, "MediaInfoDialog#${System.currentTimeMillis()}")
            }

        }

        viewBinding.settingsIv.clicks(this) {
            viewBinding.actionLayout.hide()
            val d = PlayerSettingsDialog(playerView = viewBinding.playerView, player = mediaPlayer)
            d.show(supportFragmentManager, "PlayerSettingsDialog#${System.currentTimeMillis()}}")
        }

        viewBinding.actionLayout.setOnClickListener {  }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer.getState() is tMediaPlayerState.Playing) {
            mediaPlayer.pause()
        }
    }

    override fun onViewModelCleared() {
        super.onViewModelCleared()
        mediaPlayer.release()
    }

    companion object {

        private const val MEDIA_FILE_EXTRA = "media_file_extra"

        fun createIntent(context: Context, mediaFile: String): Intent {
            val intent = Intent(context, PlayerActivity::class.java)
            intent.putExtra(MEDIA_FILE_EXTRA, mediaFile)
            return intent
        }

        private fun Intent.getMediaFileExtra(): String = this.getStringExtra(MEDIA_FILE_EXTRA) ?: ""

        data class Progress(
            val progress: Long = 0L,
            val duration: Long = 0L
        )

        data class State(
            val playerState: tMediaPlayerState = tMediaPlayerState.NoInit,
            val progress: Progress = Progress()
        )

        const val TAG = "MainActivity"
    }
}