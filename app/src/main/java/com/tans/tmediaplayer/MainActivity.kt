package com.tans.tmediaplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
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

    private val rootView: View by lazy {
        findViewById(R.id.root_layout)
    }

    private val playerSb: SeekBar by lazy {
        findViewById(R.id.player_sb)
    }

    private val fileName = "gokuraku.mp4"

    private val testVideoFile: File by lazy {
        val parentDir = filesDir
        File(parentDir, fileName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        playerSb.setOnTouchListener { _, _ -> true }
        rootView.setOnClickListener {
            if (actionLayout.visibility == View.VISIBLE) {
                actionLayout.visibility = View.GONE
            } else {
                actionLayout.visibility = View.VISIBLE
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

        mediaPlayer.setListener(object : tMediaPlayerListener {

            override fun onPlayerState(state: tMediaPlayerState) {
                runOnUiThread {
                    if (state is tMediaPlayerState.Playing) {
                        pauseIv.visibility = View.VISIBLE
                    } else {
                        pauseIv.visibility = View.GONE
                    }
                    if (state is tMediaPlayerState.Prepared ||
                            state is tMediaPlayerState.Paused ||
                            state is tMediaPlayerState.Stopped ||
                            state is tMediaPlayerState.PlayEnd) {
                        playIv.visibility = View.VISIBLE
                    } else {
                        playIv.visibility = View.GONE
                    }
                }
            }

            override fun onProgressUpdate(progress: Long, duration: Long) {
                runOnUiThread {
                    progressTv.text = progress.formatDuration()
                    val progressInPercent = (progress * 100 / duration).toInt()
                    playerSb.progress = progressInPercent
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