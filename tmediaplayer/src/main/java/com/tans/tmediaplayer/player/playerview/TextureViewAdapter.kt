package com.tans.tmediaplayer.player.playerview

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import java.lang.ref.WeakReference
import java.util.LinkedList

internal class TextureViewAdapter(textureView: TextureView) : RenderSurfaceAdapter, TextureView.SurfaceTextureListener {

    override var isReleased: Boolean = false
    override val listeners: MutableList<RenderSurfaceAdapter.WrapperListener> = LinkedList()
    override var activeSurface: Surface? = null
    override var activeSurfaceWidth: Int? = null
    override var activeSurfaceHeight: Int? = null

    private val textureViewWeakRef = WeakReference(textureView)

    init {
        textureView.surfaceTextureListener = this
    }

    // region TextureView Callback
    override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        RenderSurfaceAdapter.setApplicationContext(textureViewWeakRef.get()?.context)
        dispatchSurfaceCreated(Surface(surface), width, height)
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        dispatchSurfaceSizeChanged(width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Nothing to do.
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        dispatchSurfaceDestroyed()
        return true
    }
    // endregion

    override fun doRelease() {
        textureViewWeakRef.get()?.surfaceTextureListener = null
        activeSurface?.release()
    }
}