package com.tans.tmediaplayer.player

import android.os.SystemClock
import com.tans.tmediaplayer.player.model.NO_SYNC_THRESHOLD
import com.tans.tmediaplayer.player.rwqueue.PacketQueue
import kotlin.math.abs

internal class Clock {
    private var pts: Long = -1L
    private var lastUpdate: Long = -1L
    private var ptsDrift: Long = -1L
    private var speed: Double = 1.0
    private var serial: Int = -1
    private var paused: Boolean = false
    private var packetQueue: PacketQueue? = null

    @Synchronized
    fun initClock(pktQueue: PacketQueue?) {
        speed = 1.0
        paused = false
        packetQueue = pktQueue
        pts = -1
        lastUpdate = SystemClock.uptimeMillis()
        ptsDrift = pts - lastUpdate
        serial = -1
    }

    @Synchronized
    fun setClock(pts: Long, serial: Int) {
        this.pts = pts
        this.serial = serial
        this.lastUpdate = SystemClock.uptimeMillis()
        this.ptsDrift = pts - lastUpdate
    }


    fun setSpeed(s: Double) {
        this.speed = s
    }

    fun play() {
        this.paused = false
    }

    fun pause() {
        this.paused = true
    }

    fun syncToClock(baseClock: Clock) {
        val mine = getClock()
        val base = baseClock.getClock()
        if (base > 0L && (mine <= 0L || abs(base - mine) > NO_SYNC_THRESHOLD)) {
            setClock(base, baseClock.serial)
        }
    }

    fun getClock(): Long {
        val queue = packetQueue
        if (queue != null && queue.getSerial() != serial) {
            return -1L
        }
        if (paused) {
            return pts
        }
        val time = SystemClock.uptimeMillis()
        return ptsDrift + time - ((time - lastUpdate).toDouble() * (1.0 - speed)).toLong()
    }
}