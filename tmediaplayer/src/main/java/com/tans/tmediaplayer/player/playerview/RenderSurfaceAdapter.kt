package com.tans.tmediaplayer.player.playerview

import android.content.Context
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

internal interface RenderSurfaceAdapter {

    var isReleased: Boolean

    val listeners: MutableList<WrapperListener>

    var activeSurface: Surface?

    var activeSurfaceWidth: Int?

    var activeSurfaceHeight: Int?


    fun addListener(l: Listener) {
        synchronized(this) {
            if (!isReleased) {
                val wrapper = listeners.find { it.l === l }
                if (wrapper == null) {
                    val new = WrapperListener(l)
                    listeners.add(new)
                    val s = activeSurface
                    if (s != null) {
                        new.dispatchSurfaceCreated(s, activeSurfaceWidth ?: 0, activeSurfaceHeight ?: 0)
                    }
                }
            }
        }
    }

    fun removeListener(l: Listener) {
        synchronized(this) {
            val toRemove = listeners.find { it.l === l }
            if (toRemove != null) {
                listeners.remove(toRemove)
                toRemove.dispatchSurfaceDestroyed()
            }
        }
    }

    fun doRelease()

    fun dispatchSurfaceCreated(s: Surface, width: Int, height: Int) {
        synchronized(this) {
            activeSurfaceWidth = width
            activeSurfaceHeight = height
            for (l in listeners) {
                l.dispatchSurfaceCreated(s, width, height)
            }
        }
    }

    fun dispatchSurfaceSizeChanged(width: Int, height: Int) {
        synchronized(this) {
            if (activeSurface != null) {
                activeSurfaceWidth = width
                activeSurfaceHeight = height
                for (l in listeners) {
                    l.dispatchSurfaceSizeChanged(width, height)
                }
            }
        }
    }

    fun dispatchSurfaceDestroyed() {
        synchronized(this) {
            activeSurface = null
            activeSurfaceWidth = null
            activeSurfaceHeight = null
            for (l in listeners) {
                l.dispatchSurfaceDestroyed()
            }
        }
    }

    fun release() {
        synchronized(this) {
            if (!isReleased) {
                isReleased = true
                doRelease()
                dispatchSurfaceDestroyed()
            }
        }
    }

    class WrapperListener(val l: Listener) {
        private val isSurfaceCreated: AtomicBoolean = AtomicBoolean(false)

        fun dispatchSurfaceCreated(s: Surface, width: Int, height: Int) {
            if (isSurfaceCreated.compareAndSet(false, true)) {
                l.onSurfaceCreated(s, width, height)
            }
        }

        fun dispatchSurfaceSizeChanged(width: Int, height: Int) {
            if (isSurfaceCreated.get()) {
                l.onSurfaceSizeChanged(width, height)
            }
        }

        fun dispatchSurfaceDestroyed() {
            if (isSurfaceCreated.compareAndSet(true, false)) {
                l.onSurfaceDestroyed()
            }
        }
    }

    interface Listener {

        fun onSurfaceCreated(surface: Surface, width: Int, height: Int)

        fun onSurfaceSizeChanged(width: Int, height: Int)

        fun onSurfaceDestroyed()
    }

    companion object {

        @Volatile
        private var applicationContext: Context? = null

        fun setApplicationContext(context: Context?) {
            if (applicationContext == null && context != null) {
                applicationContext = context.applicationContext
            }
        }

        fun getAndroidApplicationContext(): Context? = applicationContext
    }
}