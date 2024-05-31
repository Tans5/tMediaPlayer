package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.player.tMediaPlayer2
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class PacketQueue(
    val player: tMediaPlayer2
) : BaseReadWriteQueue<Packet>() {

    override val maxQueueSize: Int = INFINITY_MAX_QUEUE_SIZE

    private val duration: AtomicLong by lazy {
        AtomicLong(0L)
    }

    private val sizeInBytes: AtomicLong by lazy {
        AtomicLong(0L)
    }

    private val serial: AtomicInteger by lazy {
        AtomicInteger(0)
    }

    var lastDequeuePacket: LastDequeuePacket? = null
        private set

    override fun allocBuffer(): Packet {
        val nativeBuffer = player.allocPacketInternal()
        return Packet(nativeBuffer)
    }

    override fun recycleBuffer(b: Packet) {
        player.releasePacketInternal(b.nativePacket)
    }

    override fun flushReadableBuffer() {
        super.flushReadableBuffer()
        serial.incrementAndGet()
        duration.set(0L)
        sizeInBytes.set(0L)
    }

    override fun enqueueReadable(b: Packet) {
        b.sizeInBytes = player.getPacketBytesSizeInternal(b.nativePacket)
        b.duration = player.getPacketDurationInternal(b.nativePacket)
        b.pts = player.getPacketPtsInternal(b.nativePacket)
        b.serial = serial.get()
        sizeInBytes.addAndGet(b.sizeInBytes.toLong())
        duration.addAndGet(b.duration)
        super.enqueueReadable(b)
    }

    override fun enqueueWritable(b: Packet) {
        b.sizeInBytes = 0
        b.duration = 0
        b.pts = 0
        b.serial = 0
        b.isEof = false
        super.enqueueWritable(b)
    }

    override fun dequeueReadable(): Packet? {
        val b = super.dequeueReadable()
        if (b != null) {
            sizeInBytes.addAndGet(b.sizeInBytes * -1L)
            duration.addAndGet(b.duration * -1L)
            lastDequeuePacket = LastDequeuePacket(
                pts = b.pts,
                duration = b.duration,
                sizeInBytes = b.sizeInBytes,
                serial = b.serial,
                isEof = b.isEof
            )
        }
        return b
    }

    fun getDuration(): Long = duration.get()

    fun getSizeInBytes(): Long = sizeInBytes.get()

    fun getSerial(): Int = serial.get()

    override fun release() {
        super.release()
        sizeInBytes.set(0L)
        duration.set(0L)
        serial.set(0)
    }

    companion object {
        data class LastDequeuePacket(
            val pts: Long,
            val duration: Long,
            val sizeInBytes: Int,
            val serial: Int,
            val isEof: Boolean,
        )
    }
}