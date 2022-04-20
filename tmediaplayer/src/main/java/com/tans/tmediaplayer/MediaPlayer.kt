package com.tans.tmediaplayer

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.Executors

private val playerExecutor = Executors.newFixedThreadPool(2)

class MediaPlayer {

    fun setupPlayer(filePath: String, textureView: TextureView) {

        playerExecutor.execute {
            setupPlayerNative(filePath)
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, p1: Int, p2: Int) {
                setWindow(Surface(surface))
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

            }

        }
    }

    fun releasePlayer() {
        releasePlayerNative()
    }

    private external fun setupPlayerNative(filePath: String)

    private external fun setWindow(surface: Surface)

    private external fun releasePlayerNative()

    companion object {
        init {
            System.loadLibrary("tmediaplayer")
        }
    }
}