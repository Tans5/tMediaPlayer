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

    private val mediaPlayer: MediaPlayer by lazy {
        MediaPlayer()
    }
    private val textureView by lazy {
        findViewById<TextureView>(R.id.texture_view)
    }

    private val fileName = "gokuraku.mp4"

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
            mediaPlayer.setupPlayer(testVideoFile.absolutePath)
        }
        mediaPlayer.setTextureView(textureView)
        mediaPlayer.setStateObserver { state ->
            println("PlayerState: $state")
            if (state == MediaPlayerState.Prepared) {
                mediaPlayer.playStart()
            }
        }
        mediaPlayer.setProgressObserver { position, duration ->
            println("Progress: $position, Duration: $duration")
        }
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayer.getCurrentState() == MediaPlayerState.Paused) {
            mediaPlayer.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer.getCurrentState() == MediaPlayerState.Playing) {
            mediaPlayer.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.releasePlayer()
    }
}