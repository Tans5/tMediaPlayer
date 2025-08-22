package com.tans.tmediaplayer.player.playerview

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.tans.tmediaplayer.tMediaPlayerLog
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean

internal class GLRenderer {

    private var renderSurfaceAdapter: RenderSurfaceAdapter? = null

    private val surfaceListener: RenderSurfaceAdapter.Listener by lazy {
        object : RenderSurfaceAdapter.Listener {
            override fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
                tMediaPlayerLog.d(TAG) { "Surface created: ${surface.isValid}, ${width}x$height" }
                // TODO:
            }

            override fun onSurfaceSizeChanged(width: Int, height: Int) {
                tMediaPlayerLog.d(TAG) { "Surface size changed: ${width}x$height" }
                // TODO:
            }

            override fun onSurfaceDestroyed() {
                tMediaPlayerLog.d(TAG) { "Surface destroyed." }
                // TODO:
            }
        }
    }

    private val realRenderer: RealRenderer by lazy {
        RealRenderer()
    }

    private val glThread: GLThread by lazy {
        GLThread()
    }

    private var isReleased: Boolean = false


    @Synchronized
    fun attachRendererSurface(surfaceView: SurfaceView) {
        if (!isReleased) {
            val new = SurfaceViewAdapter(surfaceView)
            renderSurfaceAdapter?.release()
            new.addListener(surfaceListener)
            renderSurfaceAdapter = new
            if (!glThread.isStarted()) {
                glThread.start()
            }
        }
    }

    @Synchronized
    fun attachRendererSurface(textureView: TextureView) {
        if (!isReleased) {
            val new = TextureViewAdapter(textureView)
            renderSurfaceAdapter?.release()
            new.addListener(surfaceListener)
            renderSurfaceAdapter = new
            if (!glThread.isStarted()) {
                glThread.start()
            }
        }
    }

    @Synchronized
    fun detachRenderSurface() {
        if (!isReleased) {
            renderSurfaceAdapter?.release()
            renderSurfaceAdapter = null
        }
    }


    @Synchronized
    fun release() {
        if (!isReleased) {
            isReleased = true
            if (glThread.isStarted()) {
                glThread.requestQuitAndWait()
            }

            // TODO:
        }
    }

    private inner class RealRenderer {

        fun glContextCreated() {
            // TODO:
        }

        fun surfaceSizeChanged(width: Int, height: Int) {
            // TODO:
        }

        fun drawFrame() {
            // TODO:
        }

        fun glContextDestroying() {
            // TODO:
        }
    }

    private inner class GLThread : Thread("tMediaGLThread") {

        private val isStarted: AtomicBoolean = AtomicBoolean(false)

        @Volatile
        private var requestQuit: Boolean = false

        @Volatile
        private var isQuited: Boolean = false

        @Volatile
        private var surface: Surface? = null

        @Volatile
        private var surfaceSizeChange: Pair<Int, Int>? = null

        @Volatile
        private var requestRender: Boolean = false

        private val tasks: LinkedBlockingDeque<(Boolean) -> Unit> = LinkedBlockingDeque()

        fun isStarted() = isStarted.get()

        override fun start() {
            isStarted.set(true)
            super.start()
        }

        @Synchronized
        fun requestQuitAndWait() {
            if (!isQuited && isStarted.get()) {
                if (!requestQuit) {
                    requestQuit = true
                    (this as Object).notifyAll()
                }
                while (!isQuited) {
                    (this as Object).wait()
                }
            }
        }

        @Synchronized
        fun requestAttachSurface(s: Surface) {
            if (surface == null && isThreadAlive()) {
                surface = s
                (this as Object).notifyAll()
            }
        }

        @Synchronized
        fun requestDetachSurface() {
            if (surface != null && isThreadAlive()) {
                surface = null
                (this as Object).notifyAll()
            }
        }

        @Synchronized
        fun requestSizeChange(width: Int, height: Int) {
            if (isThreadAlive()) {
                surfaceSizeChange = width to height
                (this as Object).notifyAll()
            }
        }


        @Synchronized
        fun requestRender() {
            if (isThreadAlive()) {
                requestRender = true
                (this as Object).notifyAll()
            }
        }

        @Synchronized
        fun queueTask(task: (containGLContext: Boolean) -> Unit) {
            if (isThreadAlive()) {
                tasks.add(task)
                if (tasks.size > 100) {
                    tMediaPlayerLog.e(TAG) { "Waiting task size: ${tasks.size}" }
                    tasks.pollFirst()?.invoke(false)
                }
                (this as Object).notifyAll()
            } else {
                task(false)
            }
        }

        fun isThreadAlive(): Boolean = !isQuited && !requestQuit && isStarted.get()

        override fun run() {
            tMediaPlayerLog.d(TAG) { "GLThread start run!!!" }
            // 1. Display
            var display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)
            tMediaPlayerLog.d(TAG) { "EGL display inited, major version: ${version[0]}, minor version: ${version[1]}" }


            // 2. Choose configure
            val eglConfigureAttrs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            )
            val eglConfigure = Array<EGLConfig?>(1) { null }
            val numConfigure = IntArray(1)
            EGL14.eglChooseConfig(display, eglConfigureAttrs, 0, eglConfigure,
                0, 1, numConfigure, 0)
            tMediaPlayerLog.d(TAG) { "Choose egl configure: ${eglConfigure[0]}" }

            // 3. Create egl context.
            val eglContextAttrs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            var eglContext = EGL14.eglCreateContext(display, eglConfigure[0], EGL14.EGL_NO_CONTEXT, eglContextAttrs, 0)
            tMediaPlayerLog.d(TAG) { "Create egl context: $eglContext" }

            // 4. Wait create surface.
            var eglSurface = EGL14.EGL_NO_SURFACE

            while (true) {
                synchronized(this) {
                    // Destroy
                    if (requestQuit) {
                        realRenderer.glContextDestroying()

                        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        // Destroy surface
                        if (eglSurface != EGL14.EGL_NO_SURFACE) {
                            EGL14.eglDestroySurface(display, eglSurface)
                            eglSurface = EGL14.EGL_NO_SURFACE
                        }
                        // Destroy context
                        EGL14.eglDestroyContext(display, eglContext)
                        eglContext = EGL14.EGL_NO_CONTEXT

                        // Destroy display
                        EGL14.eglTerminate(display)
                        display = EGL14.EGL_NO_DISPLAY
                        requestQuit = false
                        isQuited = true
                        (this as Object).notifyAll()
                        tMediaPlayerLog.d(TAG) { "GL thread quited." }
                        break
                    }

                    // Check surface
                    val sur = surface
                    if (sur != null) {
                        if (eglSurface == EGL14.EGL_NO_SURFACE) {
                            // Create new egl surface
                            eglSurface = EGL14.eglCreateWindowSurface(display, eglConfigure[0], sur, intArrayOf(
                                EGL14.EGL_NONE), 0)
                            EGL14.eglMakeCurrent(display, eglSurface, EGL14.EGL_NO_SURFACE, eglContext)
                            realRenderer.glContextCreated()
                            tMediaPlayerLog.d(TAG) { "GL context created." }
                        }
                    } else {
                        if (eglSurface != EGL14.EGL_NO_SURFACE) {
                            // Destroy egl surface.
                            realRenderer.glContextDestroying()
                            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                            EGL14.eglDestroySurface(display, eglSurface)
                            eglSurface = EGL14.EGL_NO_SURFACE
                            tMediaPlayerLog.d(TAG) { "GL context destroyed." }
                        }
                    }

                    if (eglSurface == EGL14.EGL_NO_SURFACE) {
                        tMediaPlayerLog.d(TAG) { "GL waiting surface..." }
                        (this as Object).wait()
                        continue
                    }

                    val size = surfaceSizeChange
                    if (size != null) {
                        realRenderer.surfaceSizeChanged(size.first, size.second)
                        surfaceSizeChange = null
                    }

                    if (requestRender) {
                        realRenderer.drawFrame()
                        EGL14.eglSwapBuffers(display, eglSurface)
                    }
                    while (tasks.isNotEmpty()) {
                        tasks.pollFirst()?.invoke(true)
                    }

                    (this as Object).wait()
                }
            }

            while (tasks.isNotEmpty()) {
                tasks.pollFirst()?.invoke(false)
            }
        }
    }

    companion object {
        private const val TAG = "GLRenderer"
    }
}