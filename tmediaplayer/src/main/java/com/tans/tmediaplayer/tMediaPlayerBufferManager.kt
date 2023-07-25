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

    private val decodeBufferDeque: LinkedBlockingDeque<Long> by lazy {
        LinkedBlockingDeque()
    }

    private val renderBufferDeque: LinkedBlockingDeque<Long> by lazy {
        LinkedBlockingDeque()
    }

    private val allBuffers: LinkedBlockingDeque<Long> by lazy {
        LinkedBlockingDeque()
    }

    @Synchronized
    fun prepare() {
        isReleased.set(false)
    }

    @Synchronized
    fun clearRenderData() {
        while (renderBufferDeque.isNotEmpty()) {
            val b = renderBufferDeque.pollFirst()
            if (b != null) {
                decodeBufferDeque.add(b)
            }
        }
    }

    @Synchronized
    fun requestDecodeBuffer(): Long? {
        return if (!isReleased.get()) {
            decodeBufferDeque.pollFirst()
                ?: if (hasAllocBufferSize.get() >= maxBufferSize) {
                    null
                } else {
                    hasAllocBufferSize.addAndGet(1)
                    val result = player.allocDecodeDataNativeInternal()
                    allBuffers.add(result)
                    result
                }
        } else {
            null
        }
    }

    @Synchronized
    fun requestRenderBuffer(): Long? = if (isReleased.get()) {
        null
    } else {
        renderBufferDeque.pollFirst()
    }

    @Synchronized
    fun enqueueRenderBuffer(buffer: Long) {
        if (!isReleased.get()) {
            renderBufferDeque.add(buffer)
        }
    }

    @Synchronized
    fun enqueueDecodeBuffer(buffer: Long) {
        if (!isReleased.get()) {
            decodeBufferDeque.add(buffer)
        }
    }


    @Synchronized
    fun release() {
        isReleased.set(true)
        decodeBufferDeque.clear()
        renderBufferDeque.clear()
        for (b in allBuffers) {
            player.freeDecodeDataNativeInternal(b)
        }
        allBuffers.clear()
    }
}