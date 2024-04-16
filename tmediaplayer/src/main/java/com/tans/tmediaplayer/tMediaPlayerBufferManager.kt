package com.tans.tmediaplayer

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manage all native buffers and Java buffers.
 */
@Suppress("ClassName")
internal class tMediaPlayerBufferManager(
    private val player: tMediaPlayer,
    private val maxNativeBufferSize: Int) {

    private val isReleased: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    /**
     * Buffer buffer size.
     */
    private val hasAllocBufferSize: AtomicInteger by lazy {
        AtomicInteger(0)
    }

    /**
     * Empty Buffers for decoder use.
     */
    private val decodeBufferDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    /**
     * Decoded buffers waiting to render.
     */
    private val renderBufferDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    /**
     * All buffers including [decodeBufferDeque] and [renderBufferDeque].
     */
    private val allBuffers: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    /**
     * Buffer for java use.
     */
    private val javaBuffers: LinkedBlockingDeque<JavaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    @Synchronized
    fun prepare() {
        isReleased.set(false)
    }

    /**
     * Clear all render buffers and move to [decodeBufferDeque]
     */
    fun clearRenderData() {
        renderBufferDeque.clear()
        decodeBufferDeque.clear()
        decodeBufferDeque.addAll(allBuffers)
    }

    /**
     * Force get a decode buffer.
     */
    fun requestDecodeBufferForce(): MediaBuffer {
        return requestRenderBuffer().let {
            if (it == null) {
                hasAllocBufferSize.addAndGet(1)
                val result = MediaBuffer(player.allocDecodeDataNativeInternal())
                allBuffers.add(result)
                result
            } else {
                it
            }
        }
    }

    /**
     * Get decode buffer, if current buffer size great than [maxNativeBufferSize] would return null.
     */
    fun requestDecodeBuffer(): MediaBuffer? {
        return if (!isReleased.get()) {
            decodeBufferDeque.pollFirst()
                ?: if (hasAllocBufferSize.get() >= maxNativeBufferSize) {
                    null
                } else {
                    hasAllocBufferSize.addAndGet(1)
                    val result = MediaBuffer(player.allocDecodeDataNativeInternal())
                    allBuffers.add(result)
                    result
                }
        } else {
            null
        }
    }

    /**
     * Get render buffer
     */
    fun requestRenderBuffer(): MediaBuffer? = if (isReleased.get()) {
        null
    } else {
        renderBufferDeque.pollFirst()
    }

    /**
     * When decoder finish decode, move it to [renderBufferDeque], waiting to render.
     */
    fun enqueueRenderBuffer(buffer: MediaBuffer) {
        if (!isReleased.get()) {
            if (decodeBufferDeque.contains(buffer)) {
                decodeBufferDeque.remove(buffer)
            }
            if (!renderBufferDeque.contains(buffer)) {
                renderBufferDeque.add(buffer)
            }
        }
    }

    /**
     * When renderer finished rendering, move buffer to [renderBufferDeque], waiting decoder use it.
     */
    fun enqueueDecodeBuffer(buffer: MediaBuffer) {
        if (!isReleased.get()) {
            if (renderBufferDeque.contains(buffer)) {
                renderBufferDeque.remove(buffer)
            }
            if (hasAllocBufferSize.get() > maxNativeBufferSize) {
                synchronized(buffer) {
                    allBuffers.remove(buffer)
                    player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
                    hasAllocBufferSize.decrementAndGet()
                }
            } else {
                if (!decodeBufferDeque.contains(buffer)) {
                    decodeBufferDeque.add(buffer)
                }
            }
        }
    }

    /**
     * Request JavaBuffer and no limit.
     */
    fun requestJavaBuffer(size: Int): JavaBuffer {
        val cache = javaBuffers.find { it.size == size }
        return if (cache != null) {
            javaBuffers.remove(cache)
            cache
        } else {
            JavaBuffer(
                size = size,
                bytes = ByteArray(size)
            )
        }
    }

    fun enqueueJavaBuffer(javaBuffer: JavaBuffer) {
        if (!isReleased.get()) {
            javaBuffers.add(javaBuffer)
        }
    }

    /**
     * Release all java buffers and native buffers.
     */
    fun release() {
        isReleased.set(true)
        decodeBufferDeque.clear()
        renderBufferDeque.clear()
        for (b in allBuffers) {
            synchronized(b) {
                player.freeDecodeDataNativeInternal(b.nativeBuffer)
            }
        }
        hasAllocBufferSize.set(0)
        allBuffers.clear()
        javaBuffers.clear()
    }


    companion object {
        data class MediaBuffer(
            val nativeBuffer: Long
        )

        class JavaBuffer(
            val size: Int,
            val bytes: ByteArray
        )
    }
}