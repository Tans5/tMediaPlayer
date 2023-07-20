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

    fun prepare() {
        isReleased.set(false)
    }

    fun requestDecodeBuffer(): Long? {
        return if (!isReleased.get()) {
            decodeBufferDeque.pollFirst()
                ?: if (hasAllocBufferSize.get() >= maxBufferSize) {
                    null
                } else {
                    hasAllocBufferSize.addAndGet(1)
                    player.allocDecodeDataNativeInternal()
                }
        } else {
            null
        }
    }

    fun requestRenderBuffer(): Long? = if (isReleased.get()) {
        null
    } else {
        renderBufferDeque.pollFirst()
    }

    fun enqueueRenderBuffer(buffer: Long) {
        if (isReleased.get()) {
            player.freeDecodeDataNativeInternal(buffer)
        } else {
            renderBufferDeque.add(buffer)
        }
    }

    fun enqueueDecodeBuffer(buffer: Long) {
        if (isReleased.get()) {
            player.freeDecodeDataNativeInternal(buffer)
        } else {
            decodeBufferDeque.add(buffer)
        }
    }

    fun release() {
        isReleased.set(true)
        for (b in decodeBufferDeque) {
            player.freeDecodeDataNativeInternal(b)
        }
        for (b in renderBufferDeque) {
            player.freeDecodeDataNativeInternal(b)
        }
    }
}