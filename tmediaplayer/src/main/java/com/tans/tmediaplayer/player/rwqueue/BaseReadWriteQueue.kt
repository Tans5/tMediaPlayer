package com.tans.tmediaplayer.player.rwqueue

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal const val INFINITY_MAX_QUEUE_SIZE = -1

internal abstract class BaseReadWriteQueue<T : Any> {

    private val listeners = LinkedBlockingDeque<ReadWriteQueueListener>()

    abstract val maxQueueSize: Int

    private val currentQueueSize: AtomicInteger by lazy {
        AtomicInteger(0)
    }

    private val isReleased: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    private val readableQueue: LinkedBlockingDeque<T> by lazy {
        LinkedBlockingDeque<T>()
    }

    private val writeableQueue: LinkedBlockingDeque<T> by lazy {
        LinkedBlockingDeque<T>()
    }

    fun getCurrentQueueSize() = if (isReleased.get()) 0 else currentQueueSize.get()

    // region Readable
    fun readableQueueSize() = if (isReleased.get()) 0 else readableQueue.size

    fun isCanRead(): Boolean = readableQueueSize() > 0

    open fun enqueueReadable(b: T) {
        if (!isReleased.get()) {
            readableQueue.addLast(b)
            for (l in listeners) {
                l.onNewReadableFrame()
            }
        } else {
            recycleBuffer(b)
        }
    }

    open fun dequeueReadable(): T? {
        return if (isReleased.get()) {
            null
        } else {
            readableQueue.pollFirst()
        }
    }

    open fun peekReadable(): T? {
        return if (isReleased.get()) {
            null
        } else {
            readableQueue.peekFirst()
        }
    }
    // endregion

    // region Writeable
    fun writeableQueueSize() = if (isReleased.get()) 0 else writeableQueue.size

    fun isCanWrite(): Boolean {
        return if (isReleased.get()) {
            false
        } else {
            if (maxQueueSize == INFINITY_MAX_QUEUE_SIZE) {
                true
            } else {
                if (writeableQueueSize() > 0) {
                    true
                } else {
                    maxQueueSize > getCurrentQueueSize()
                }
            }
        }
    }

    open fun enqueueWritable(b: T) {
        if (isReleased.get()) {
            recycleBuffer(b)
        } else {
            if (maxQueueSize != INFINITY_MAX_QUEUE_SIZE && getCurrentQueueSize() > maxQueueSize) {
                recycleBuffer(b)
                currentQueueSize.decrementAndGet()
            } else {
                writeableQueue.addFirst(b)
                for (l in listeners) {
                    l.onNewWriteableFrame()
                }
            }
        }
    }

    open fun dequeueWritable(): T? {
        return if (isReleased.get()) {
            null
        } else {
            writeableQueue.pollFirst()
                ?: if (getCurrentQueueSize() < maxQueueSize || maxQueueSize == INFINITY_MAX_QUEUE_SIZE) {
                    val new = allocBuffer()
                    currentQueueSize.incrementAndGet()
                    new
                } else {
                    null
                }
        }
    }

    open fun dequeueWriteableForce(): T {
        val b = dequeueWritable()
        return if (b != null) {
            b
        } else {
            val new = allocBuffer()
            currentQueueSize.incrementAndGet()
            new
        }
    }

    open fun flushReadableBuffer() {
        if (!isReleased.get()) {
            val needNotifyWrite = readableQueue.isNotEmpty()
            while (readableQueue.isNotEmpty()) {
                val b = readableQueue.pollFirst()
                if (b != null) {
                    writeableQueue.addLast(b)
                }
            }
            if (needNotifyWrite) {
                for (l in listeners) {
                    l.onNewWriteableFrame()
                }
            }
        }
    }

    // endregion

    fun addListener(l: ReadWriteQueueListener) {
        if (!isReleased.get()) {
            listeners.add(l)
        }
    }

    fun removeListener(l: ReadWriteQueueListener) {
        listeners.remove(l)
    }

    protected abstract fun recycleBuffer(b: T)

    protected abstract fun allocBuffer(): T

    open fun release() {
        if (isReleased.compareAndSet(false, true)) {
            while (readableQueue.isNotEmpty()) {
                val b = readableQueue.pollFirst()
                if (b != null) {
                    recycleBuffer(b)
                }
            }
            while (writeableQueue.isNotEmpty()) {
                val b = writeableQueue.pollFirst()
                if (b != null) {
                    recycleBuffer(b)
                }
            }
            currentQueueSize.set(0)
            listeners.clear()
        }
    }
}