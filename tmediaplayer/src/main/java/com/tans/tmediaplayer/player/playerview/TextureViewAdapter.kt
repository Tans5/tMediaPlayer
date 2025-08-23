package com.tans.tmediaplayer.player.playerview

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import com.tans.tmediaplayer.tMediaPlayerLog
import java.lang.ref.WeakReference
import java.util.LinkedList

internal class TextureViewAdapter(textureView: TextureView) : RenderSurfaceAdapter, TextureView.SurfaceTextureListener {

    override var isReleased: Boolean = false
    override val listeners: MutableList<RenderSurfaceAdapter.WrapperListener> = LinkedList()
    @Volatile
    override var activeSurface: Surface? = null
    override var activeSurfaceWidth: Int? = null
    override var activeSurfaceHeight: Int? = null

    private val textureViewWeakRef = WeakReference(textureView)

    init {
        textureView.surfaceTextureListener = this
        textureView.post {
            val s = textureView.surfaceTexture
            if (s != null) {
                tMediaPlayerLog.d(SurfaceViewAdapter.Companion.TAG) { "Get surface from post: surfaceTexture=$s" }
                onSurfaceTextureAvailable(s, textureView.measuredWidth, textureView.measuredHeight)
            }
        }
    }

    // region TextureView Callback
    override fun onSurfaceTextureAvailable(
        surface: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        RenderSurfaceAdapter.setApplicationContext(textureViewWeakRef.get()?.context)
        if (activeSurface == null) {
            dispatchSurfaceCreated(Surface(surface), width, height)
            tMediaPlayerLog.d(SurfaceViewAdapter.Companion.TAG) { "Surface created: $activeSurface" }
        }
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        tMediaPlayerLog.d(SurfaceViewAdapter.Companion.TAG) { "Surface size changed: surface=${activeSurface}, size=${width}x${height}" }
        dispatchSurfaceSizeChanged(width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Nothing to do.
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        tMediaPlayerLog.d(SurfaceViewAdapter.Companion.TAG) { "Surface destroyed: $activeSurface" }
        dispatchSurfaceDestroyed()
        return true
    }
    // endregion

    override fun doRelease() {
        textureViewWeakRef.get()?.surfaceTextureListener = null
        activeSurface?.release()
    }

    companion object {
        private const val TAG = "TextureViewAdapter"
    }
}