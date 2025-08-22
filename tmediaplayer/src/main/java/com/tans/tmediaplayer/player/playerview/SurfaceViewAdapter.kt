package com.tans.tmediaplayer.player.playerview

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.lang.ref.WeakReference
import java.util.LinkedList

internal class SurfaceViewAdapter(surfaceView: SurfaceView) : SurfaceHolder.Callback, RenderSurfaceAdapter {

    override var isReleased: Boolean = false
    override val listeners: MutableList<RenderSurfaceAdapter.WrapperListener> = LinkedList()
    @Volatile
    override var activeSurface: Surface? = null
    override var activeSurfaceWidth: Int? = null
    override var activeSurfaceHeight: Int? = null

    private val surfaceViewWeakRef: WeakReference<SurfaceView> = WeakReference(surfaceView)

    init {
        surfaceView.holder.addCallback(this)
        if (surfaceView.holder.surface != null) {
            surfaceCreated(surfaceView.holder)
        }
    }


    // region SurfaceView Callback
    override fun surfaceCreated(holder: SurfaceHolder) {
        val v = surfaceViewWeakRef.get()
        RenderSurfaceAdapter.Companion.setApplicationContext(v?.context)
        dispatchSurfaceCreated(holder.surface, v?.measuredWidth ?: 0, v?.measuredHeight ?: 0)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        dispatchSurfaceSizeChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        dispatchSurfaceDestroyed()
    }
    // endregion

    override fun doRelease() {
        surfaceViewWeakRef.get()?.holder?.removeCallback(this)
    }

}