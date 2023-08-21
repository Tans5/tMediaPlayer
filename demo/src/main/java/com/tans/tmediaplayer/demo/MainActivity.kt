package com.tans.tmediaplayer.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tans.tmediaplayer.render.filter.AsciiArtImageFilter
import com.tans.tmediaplayer.tMediaPlayer
import com.tans.tmediaplayer.tMediaPlayerListener
import com.tans.tmediaplayer.tMediaPlayerState
import com.tans.tmediaplayer.render.tMediaPlayerView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

val ioExecutor: Executor by lazy {
    Executors.newFixedThreadPool(2)
}

class MainActivity : AppCompatActivity() {

    private val mediaPlayer: tMediaPlayer by lazy {
        tMediaPlayer()
    }
    private val playerView by lazy {
        findViewById<tMediaPlayerView>(R.id.player_view)
    }

    private val actionLayout: View by lazy {
        findViewById(R.id.action_layout)
    }

    private val settingsLayout: View by lazy {
        findViewById(R.id.settings_layout)
    }

    private val settingsIv: ImageView by lazy {
        findViewById(R.id.settings_iv)
    }

    private val progressTv: TextView by lazy {
        findViewById(R.id.progress_tv)
    }

    private val durationTv: TextView by lazy {
        findViewById(R.id.duration_tv)
    }

    private val playIv: ImageView by lazy {
        findViewById(R.id.play_iv)
    }

    private val pauseIv: ImageView by lazy {
        findViewById(R.id.pause_iv)
    }

    private val replayIv: ImageView by lazy {
        findViewById(R.id.replay_iv)
    }

    private val rootView: View by lazy {
        findViewById(R.id.root_layout)
    }

    private val playerSb: SeekBar by lazy {
        findViewById(R.id.player_sb)
    }

    private val seekingLoadingPb: ProgressBar by lazy {
        findViewById(R.id.seeking_loading_pb)
    }

    private val cropImageSw: SwitchCompat by lazy {
        findViewById(R.id.crop_image_sw)
    }

    private val asciiFilterSw: SwitchCompat by lazy {
        findViewById(R.id.ascii_filter_sw)
    }

    private val charReverseSw: SwitchCompat by lazy {
        findViewById(R.id.char_reverse_sw)
    }

    private val colorReverseSw: SwitchCompat by lazy {
        findViewById(R.id.color_reverse_sw)
    }

    private val charWidthTv: TextView by lazy {
        findViewById(R.id.char_width_tv)
    }

    private val charWidthSb: SeekBar by lazy {
        findViewById(R.id.char_width_sb)
    }

    private val imageColorFillRateTv: TextView by lazy {
        findViewById(R.id.image_color_fill_rate_tv)
    }

    private val imageColorFillRateSb: SeekBar by lazy {
        findViewById(R.id.image_color_fill_rate_sb)
    }

    private val fileName = "oceans.mp4"

    private val testVideoFile: File by lazy {
        val parentDir = filesDir
        File(parentDir, fileName)
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContentView(R.layout.activity_main)

        rootView.setOnClickListener {
           if (settingsLayout.isVisible()) {
               settingsLayout.hide()
           } else {
               if (actionLayout.isVisible()) {
                   actionLayout.hide()
               } else {
                   actionLayout.show()
               }
           }
        }
        ioExecutor.execute {
            if (!testVideoFile.exists()) {
                testVideoFile.createNewFile()
                FileOutputStream(testVideoFile).buffered(1024).use { output ->
                    val buffer = ByteArray(1024)
                    assets.open(fileName).buffered(1024).use { input ->
                        var thisTimeRead: Int = 0
                        do {
                            thisTimeRead = input.read(buffer)
                            if (thisTimeRead > 0) {
                                output.write(buffer, 0, thisTimeRead)
                            }
                        } while (thisTimeRead > 0)
                    }
                    output.flush()
                }
            }
            mediaPlayer.prepare(testVideoFile.absolutePath)
            runOnUiThread {
                durationTv.text = mediaPlayer.getMediaInfo()?.duration?.formatDuration() ?: ""
            }
            mediaPlayer.play()
        }
        mediaPlayer.attachPlayerView(playerView)

        playIv.setOnClickListener {
            mediaPlayer.play()
        }

        pauseIv.setOnClickListener {
            mediaPlayer.pause()
        }

        replayIv.setOnClickListener {
            mediaPlayer.play()
        }

        var isPlayerSbInTouching = false
        playerSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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

        mediaPlayer.setListener(object : tMediaPlayerListener {

            override fun onPlayerState(state: tMediaPlayerState) {
                runOnUiThread {
                    if (state is tMediaPlayerState.Seeking) {
                        seekingLoadingPb.visibility = View.VISIBLE
                    } else {
                        seekingLoadingPb.visibility = View.GONE
                    }

                    val fixedState = when (state) {
                        is tMediaPlayerState.Seeking -> state.lastState
                        else -> state
                    }
                    if (fixedState is tMediaPlayerState.Playing) {
                        pauseIv.visibility = View.VISIBLE
                    } else {
                        pauseIv.visibility = View.GONE
                    }

                    if (fixedState is tMediaPlayerState.Prepared ||
                            fixedState is tMediaPlayerState.Paused ||
                            fixedState is tMediaPlayerState.Stopped
                    ) {
                        playIv.visibility = View.VISIBLE
                    } else {
                        playIv.visibility = View.GONE
                    }

                    if (fixedState is tMediaPlayerState.PlayEnd) {
                        replayIv.visibility = View.VISIBLE
                    } else {
                        replayIv.visibility = View.GONE
                    }
                }
            }

            override fun onProgressUpdate(progress: Long, duration: Long) {
                runOnUiThread {
                    progressTv.text = progress.formatDuration()
                    if (!isPlayerSbInTouching && mediaPlayer.getState() !is tMediaPlayerState.Seeking) {
                        val progressInPercent = (progress.toFloat() * 100.0 / duration.toFloat() + 0.5f).toInt()
                        playerSb.progress = progressInPercent
                    }
                }
            }
        })

        settingsIv.setOnClickListener {
            settingsLayout.show()
            actionLayout.hide()
        }

        cropImageSw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                playerView.setScaleType(tMediaPlayerView.Companion.ScaleType.CenterCrop)
            } else {
                playerView.setScaleType(tMediaPlayerView.Companion.ScaleType.CenterFit)
            }
        }

        asciiFilterSw.setOnCheckedChangeListener { _, isChecked ->
            playerView.enableAsciiArtFilter(isChecked)
        }

        val asciiArtFilter = playerView.getAsciiArtImageFilter()

        charReverseSw.setOnCheckedChangeListener { _, isChecked ->
            asciiArtFilter.reverseChar(isChecked)
        }

        colorReverseSw.setOnCheckedChangeListener { _, isChecked ->
            asciiArtFilter.reverseColor(isChecked)
        }
        charWidthSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val requestWidth = (progress.toFloat() / 100.0f * (AsciiArtImageFilter.MAX_CHAR_LINE_WIDTH - AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH).toFloat() + AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH.toFloat()).toInt()
                    asciiArtFilter.setCharLineWidth(requestWidth)
                    charWidthTv.text = "Char Width: $requestWidth"
                }
            }
        })

        imageColorFillRateSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val requestRate = progress.toFloat() / 100.0f
                    asciiArtFilter.colorFillRate(requestRate)
                    imageColorFillRateTv.text = "Image Color Fill Rate: $progress"
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer.getState() is tMediaPlayerState.Playing) {
            mediaPlayer.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    private fun Long.formatDuration(): String {
        val durationInSeconds = this / 1000
        val minuets = durationInSeconds / 60
        val seconds = durationInSeconds % 60
        return "%02d:%02d".format(minuets, seconds)
    }

    companion object {
        const val TAG = "MainActivity"
    }
}