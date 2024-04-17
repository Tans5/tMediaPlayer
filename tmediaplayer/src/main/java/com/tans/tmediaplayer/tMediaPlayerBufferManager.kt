package com.tans.tmediaplayer

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manage all native buffers and Java buffers.
 */
@Suppress("ClassName")
internal class tMediaPlayerBufferManager(
    private val player: tMediaPlayer,
    private val maxNativeBufferSize: Int,
    private val maxNativeAudioBufferSize: Int = 30,
    private val maxNativeVideoBufferSize: Int = 10) {

    private val isReleased: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    /**
     * Current audio native buffer size.
     */
    private val hasAllocAudioNativeBufferSize: AtomicInteger by lazy {
        AtomicInteger(0)
    }

    /**
     * Current audio native buffer size.
     */
    private val hasAllocVideoNativeBufferSize: AtomicInteger by lazy {
        AtomicInteger(0)
    }

    /**
     * Empty audio buffers for decoder use.
     */
    private val audioNativeDecodeBuffersDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    /**
     * Decoded audio buffers waiting to render.
     */
    private val audioNativeRenderBuffersDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    private val allAudioNativeBuffers: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    /**
     * Empty video buffers for decoder use.
     */
    private val videoNativeDecodeBuffersDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    /**
     * Decoded video buffers waiting to render.
     */
    private val videoNativeRenderBuffersDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    private val allVideoNativeBuffers: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
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
    private val javaBuffers: ConcurrentHashMap<Int, LinkedBlockingDeque<JavaBuffer>> by lazy {
        ConcurrentHashMap()
    }

    /**
     * Call by player
     */
    fun prepare() {
        isReleased.set(false)
    }


    private fun audioNativeBufferAlloced(buffer: MediaBuffer) {
        hasAllocAudioNativeBufferSize.incrementAndGet()
        allAudioNativeBuffers.add(buffer)
    }

    /**
     * Call by decoder, decode thread.
     */
    fun isAudioDecodeBufferCanUse(): Boolean {
        return audioNativeDecodeBuffersDeque.isNotEmpty() or (hasAllocAudioNativeBufferSize.get() < maxNativeAudioBufferSize)
    }

    /**
     * Call by player, decode thread.
     */
    fun requestAudioNativeDecodeBufferForce(): MediaBuffer {
        val cache = audioNativeDecodeBuffersDeque.pollFirst()
        return if (cache != null) {
            cache
        } else {
            val nativeBuffer = player.allocAudioDecodeDataNativeInternal()
            val buffer = MediaBuffer(nativeBuffer)
            audioNativeBufferAlloced(buffer)
            buffer
        }
    }

    /**
     * Call by renderer, render thread.
     */
    fun requestAudioNativeRenderBuffer(): MediaBuffer? {
        return audioNativeRenderBuffersDeque.pollFirst()
    }

    /**
     * Call by renderer, render thread.
     */
    fun enqueueAudioNativeEncodeBuffer(buffer: MediaBuffer) {
        if (hasAllocAudioNativeBufferSize.get() > maxNativeAudioBufferSize) {
            hasAllocAudioNativeBufferSize.decrementAndGet()
            allAudioNativeBuffers.remove(buffer)
            player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
        } else {
            audioNativeDecodeBuffersDeque.push(buffer)
        }
    }

    private fun videoNativeBufferAlloced(buffer: MediaBuffer) {
        hasAllocVideoNativeBufferSize.incrementAndGet()
        allVideoNativeBuffers.add(buffer)
    }

    /**
     * Call by decoder, decode thread.
     */
    fun isVideoDecodeBufferCanUse(): Boolean {
        return videoNativeDecodeBuffersDeque.isNotEmpty() or (hasAllocVideoNativeBufferSize.get() < maxNativeVideoBufferSize)
    }

    /**
     * Call by player, decode thread.
     */
    fun requestVideoNativeDecodeBufferForce(): MediaBuffer {
        val cache = videoNativeDecodeBuffersDeque.pollFirst()
        return if (cache != null) {
            cache
        } else {
            val nativeBuffer = player.allocVideoDecodeDataNativeInternal()
            val buffer = MediaBuffer(nativeBuffer)
            videoNativeBufferAlloced(buffer)
            buffer
        }
    }

    /**
     * Call by renderer, render thread.
     */
    fun requestVideoNativeRenderBuffer(): MediaBuffer? {
        return videoNativeRenderBuffersDeque.pollFirst()
    }

    /**
     * Call by renderer, render thread.
     */
    fun enqueueVideoNativeEncodeBuffer(buffer: MediaBuffer) {
        if (hasAllocVideoNativeBufferSize.get() > maxNativeVideoBufferSize) {
            hasAllocVideoNativeBufferSize.decrementAndGet()
            allVideoNativeBuffers.remove(buffer)
            player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
        } else {
            videoNativeDecodeBuffersDeque.push(buffer)
        }
    }

    /**
     * Clear all render buffers, call by player.
     */
    fun clearRenderData() {
        assertReleaseState()
        renderBufferDeque.clear()
        decodeBufferDeque.clear()
        decodeBufferDeque.addAll(allBuffers)

        // audio
        audioNativeRenderBuffersDeque.clear()
        audioNativeDecodeBuffersDeque.clear()
        audioNativeDecodeBuffersDeque.addAll(allAudioNativeBuffers)

        // video
        videoNativeRenderBuffersDeque.clear()
        videoNativeDecodeBuffersDeque.clear()
        videoNativeDecodeBuffersDeque.addAll(allVideoNativeBuffers)
    }

    /**
     * Force get a decode buffer.
     */
    fun requestDecodeBufferForce(): MediaBuffer {
        assertReleaseState()
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
        assertReleaseState()
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
     * Request JavaBuffer and no limit, call by render thread.
     */
    fun requestJavaBuffer(size: Int): JavaBuffer {
        val cacheList = javaBuffers[size].let {
            if (it == null) {
                val l = LinkedBlockingDeque<JavaBuffer>()
                javaBuffers[size] = l
                l
            } else {
                it
            }
        }
        val cache = cacheList.pollFirst()
        return cache
            ?: JavaBuffer(
                size = size,
                bytes = ByteArray(size)
            )
    }

    /**
     * Call by render thread.
     */
    fun enqueueJavaBuffer(javaBuffer: JavaBuffer) {
        if (!isReleased.get()) {
            val cacheList = javaBuffers[javaBuffer.size].let {
                if (it == null) {
                    val l = LinkedBlockingDeque<JavaBuffer>()
                    javaBuffers[javaBuffer.size] = l
                    l
                } else {
                    it
                }
            }
            cacheList.push(javaBuffer)
        }
    }

    /**
     * Release all java buffers and native buffers, call by player.
     */
    fun release() {
        if (isReleased.compareAndSet(true, false)) {
            decodeBufferDeque.clear()
            renderBufferDeque.clear()
            for (b in allBuffers) {
                synchronized(b) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }
            hasAllocBufferSize.set(0)
            allBuffers.clear()

            // Audio
            hasAllocAudioNativeBufferSize.set(0)
            audioNativeDecodeBuffersDeque.clear()
            audioNativeRenderBuffersDeque.clear()
            for (b in allAudioNativeBuffers) {
                synchronized(b) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }
            allAudioNativeBuffers.clear()

            // Video
            hasAllocVideoNativeBufferSize.set(0)
            videoNativeDecodeBuffersDeque.clear()
            videoNativeRenderBuffersDeque.clear()
            for (b in allVideoNativeBuffers) {
                synchronized(b) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }
            allVideoNativeBuffers.clear()

            // Java
            javaBuffers.clear()
        }
    }

    private fun assertReleaseState() {
        if (isReleased.get()) error("tMediaPlayerBufferManager released.")
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