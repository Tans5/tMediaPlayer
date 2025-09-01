package com.tans.tmediaplayer.player.rwqueue

import com.tans.tmediaplayer.tMediaPlayerLog
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class PacketQueue(
    val player: tMediaPlayer,
    override val maxQueueSize: Int = INFINITY_MAX_QUEUE_SIZE
) : BaseReadWriteQueue<Packet>() {
    private val duration: AtomicLong by lazy {
        AtomicLong(0L)
    }

    private val sizeInBytes: AtomicLong by lazy {
        AtomicLong(0L)
    }

    private val serial: AtomicInteger by lazy {
        AtomicInteger(0)
    }

    override fun allocBuffer(): Packet {
        val nativeBuffer = player.allocPacketInternal()
        packetSize.incrementAndGet()
        tMediaPlayerLog.d(TAG) { "Alloc new packet, size=${packetSize.get()}" }
        return Packet(nativeBuffer)
    }

    override fun recycleBuffer(b: Packet) {
        packetSize.decrementAndGet()
        tMediaPlayerLog.d(TAG) { "Recycle packet, size=${packetSize.get()}" }
        player.releasePacketInternal(b.nativePacket)
    }

    override fun flushReadableBuffer() {
        super.flushReadableBuffer()
        serial.incrementAndGet()
        duration.set(0L)
        sizeInBytes.set(0L)
    }

    override fun enqueueReadable(b: Packet) {
        b.streamIndex = player.getPacketStreamIndexInternal(b.nativePacket)
        b.sizeInBytes = player.getPacketBytesSizeInternal(b.nativePacket)
        b.duration = player.getPacketDurationInternal(b.nativePacket)
        b.pts = player.getPacketPtsInternal(b.nativePacket)
        b.serial = serial.get()
        sizeInBytes.addAndGet(b.sizeInBytes.toLong())
        duration.addAndGet(b.duration)
        super.enqueueReadable(b)
    }

    override fun dequeueWritable(): Packet? {
        return super.dequeueWritable()?.apply { reset() }
    }

    override fun dequeueWriteableForce(): Packet {
        return super.dequeueWriteableForce().apply { reset() }
    }

    override fun dequeueReadable(): Packet? {
        val b = super.dequeueReadable()
        if (b != null) {
            sizeInBytes.addAndGet(b.sizeInBytes * -1L)
            duration.addAndGet(b.duration * -1L)
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
        private const val TAG = "PacketQueue"
        private val packetSize = AtomicInteger()
    }
}