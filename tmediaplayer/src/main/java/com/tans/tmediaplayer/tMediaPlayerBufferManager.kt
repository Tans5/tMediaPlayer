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


    /**
     * Check if could get a new decode buffer.
     * Call by decoder, decode thread.
     */
    fun isAudioDecodeBufferCanUse(): Boolean {
        return audioNativeDecodeBuffersDeque.isNotEmpty() or (hasAllocAudioNativeBufferSize.get() < maxNativeAudioBufferSize)
    }

    /**
     * Native player want a buffer for decode.
     * Call by player, decode thread.
     */
    fun requestAudioNativeDecodeBufferForce(): Long {
        val cache = audioNativeDecodeBuffersDeque.pollFirst()
        return if (cache != null) {
            cache.nativeBuffer
        } else {
            hasAllocAudioNativeBufferSize.incrementAndGet()
            player.allocAudioDecodeDataNativeInternal()
        }
    }

    /**
     * Renderer want a buffer to render.
     * Call by renderer, render thread.
     */
    fun requestAudioNativeRenderBuffer(): MediaBuffer? {
        return audioNativeRenderBuffersDeque.pollFirst()
    }

    /**
     * After render finished, move buffer to encode buffers queue.
     * Call by renderer, render thread.
     */
    fun enqueueAudioNativeEncodeBuffer(buffer: MediaBuffer) {
        if (!isReleased.get()) {
            if (hasAllocAudioNativeBufferSize.get() > maxNativeAudioBufferSize) {
                hasAllocAudioNativeBufferSize.decrementAndGet()
                player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
            } else {
                audioNativeDecodeBuffersDeque.push(buffer)
            }
        } else {
            player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
        }
    }


    /**
     * Check if could get a new decode buffer.
     * Call by decoder, decode thread.
     */
    fun isVideoDecodeBufferCanUse(): Boolean {
        return videoNativeDecodeBuffersDeque.isNotEmpty() or (hasAllocVideoNativeBufferSize.get() < maxNativeVideoBufferSize)
    }

    /**
     * Native player want a buffer for decode.
     * Call by player, decode thread.
     */
    fun requestVideoNativeDecodeBufferForce(): Long {
        val cache = videoNativeDecodeBuffersDeque.pollFirst()
        return if (cache != null) {
            cache.nativeBuffer
        } else {
            hasAllocVideoNativeBufferSize.incrementAndGet()
            player.allocVideoDecodeDataNativeInternal()
        }
    }

    /**
     * Renderer want a buffer to render.
     * Call by renderer, render thread.
     */
    fun requestVideoNativeRenderBuffer(): MediaBuffer? {
        return videoNativeRenderBuffersDeque.pollFirst()
    }

    /**
     * After render finished, move buffer to encode buffers queue.
     * Call by renderer, render thread.
     */
    fun enqueueVideoNativeEncodeBuffer(buffer: MediaBuffer) {
        if (!isReleased.get()) {
            if (hasAllocVideoNativeBufferSize.get() > maxNativeVideoBufferSize) {
                hasAllocVideoNativeBufferSize.decrementAndGet()
                player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
            } else {
                videoNativeDecodeBuffersDeque.push(buffer)
            }
        } else {
            player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
        }
    }

    /**
     * Clear all render buffers, call by player.
     */
    fun clearRenderData() {
        while (renderBufferDeque.isNotEmpty()) {
            val b = renderBufferDeque.pollFirst()
            if (b != null) {
                decodeBufferDeque.push(b)
            }
        }

        // audio
        while (audioNativeRenderBuffersDeque.isNotEmpty()) {
            val b = audioNativeRenderBuffersDeque.pollFirst()
            if (b != null) {
                audioNativeDecodeBuffersDeque.push(b)
            }
        }

        // video
        while (videoNativeRenderBuffersDeque.isNotEmpty()) {
            val b = videoNativeRenderBuffersDeque.pollFirst()
            if (b != null) {
                videoNativeDecodeBuffersDeque.push(b)
            }
        }
    }

    /**
     * Force get a decode buffer.
     */
    fun requestDecodeBufferForce(): MediaBuffer {
        return requestRenderBuffer().let {
            if (it == null) {
                hasAllocBufferSize.addAndGet(1)
                val result = MediaBuffer(player.allocDecodeDataNativeInternal())
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
                    MediaBuffer(player.allocDecodeDataNativeInternal())
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
        } else {
            player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
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
                player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
                hasAllocBufferSize.decrementAndGet()
            } else {
                if (!decodeBufferDeque.contains(buffer)) {
                    decodeBufferDeque.add(buffer)
                }
            }
        } else {
            player.freeDecodeDataNativeInternal(buffer.nativeBuffer)
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
            while (decodeBufferDeque.isNotEmpty()) {
                val b = decodeBufferDeque.pollFirst()
                if (b != null) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }
            while (renderBufferDeque.isNotEmpty()) {
                val b = renderBufferDeque.pollFirst()
                if (b != null) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }
            hasAllocBufferSize.set(0)

            // Audio
            hasAllocAudioNativeBufferSize.set(0)
            while (audioNativeDecodeBuffersDeque.isNotEmpty()) {
                val b = audioNativeDecodeBuffersDeque.pollFirst()
                if (b != null) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }
            while (audioNativeRenderBuffersDeque.isNotEmpty()) {
                val b = audioNativeRenderBuffersDeque.pollFirst()
                if (b != null) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }

            // Video
            hasAllocVideoNativeBufferSize.set(0)
            while (videoNativeDecodeBuffersDeque.isNotEmpty()) {
                val b = videoNativeDecodeBuffersDeque.pollFirst()
                if (b != null) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }
            while (videoNativeRenderBuffersDeque.isNotEmpty()) {
                val b = videoNativeRenderBuffersDeque.pollFirst()
                if (b != null) {
                    player.freeDecodeDataNativeInternal(b.nativeBuffer)
                }
            }

            // Java
            javaBuffers.clear()
        }
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