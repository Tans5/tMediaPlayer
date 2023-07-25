package com.tans.tmediaplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.TextureView
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

    private val fileName = "gokuraku2.mp4"

    private val testVideoFile: File by lazy {
        val parentDir = filesDir
        File(parentDir, fileName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
            mediaPlayer.play()
        }

        mediaPlayer.attachPlayerView(playerView)

    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}