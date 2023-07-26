package com.tans.tmediaplayer

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Suppress("ClassName")
internal class tMediaPlayerBufferManager(
    private val player: tMediaPlayer,
    private val maxBufferSize: Int) {

    private val isReleased: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    private val hasAllocBufferSize: AtomicInteger by lazy {
        AtomicInteger(0)
    }

    private val decodeBufferDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    private val renderBufferDeque: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    private val allBuffers: LinkedBlockingDeque<MediaBuffer> by lazy {
        LinkedBlockingDeque()
    }

    @Synchronized
    fun prepare() {
        isReleased.set(false)
    }

    fun clearRenderData() {
        while (renderBufferDeque.isNotEmpty()) {
            val b = renderBufferDeque.pollFirst()
            if (b != null) {
                decodeBufferDeque.add(b)
            }
        }
    }

    fun requestDecodeBuffer(): MediaBuffer? {
        return if (!isReleased.get()) {
            decodeBufferDeque.pollFirst()
                ?: if (hasAllocBufferSize.get() >= maxBufferSize) {
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

    fun requestRenderBuffer(): MediaBuffer? = if (isReleased.get()) {
        null
    } else {
        renderBufferDeque.pollFirst()
    }

    fun enqueueRenderBuffer(buffer: MediaBuffer) {
        if (!isReleased.get()) {
            renderBufferDeque.add(buffer)
        }
    }

    fun enqueueDecodeBuffer(buffer: MediaBuffer) {
        if (!isReleased.get()) {
            decodeBufferDeque.add(buffer)
        }
    }


    fun release() {
        isReleased.set(true)
        decodeBufferDeque.clear()
        renderBufferDeque.clear()
        for (b in allBuffers) {
            synchronized(b) {
                player.freeDecodeDataNativeInternal(b.nativeBuffer)
            }
        }
        allBuffers.clear()
    }


    companion object {
        data class MediaBuffer(
            val nativeBuffer: Long
        )
    }
}