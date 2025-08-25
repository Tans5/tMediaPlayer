package com.tans.tmediaplayer.player.rwqueue

interface ReadWriteQueueListener {

    fun onNewWriteableFrame()

    fun onNewReadableFrame()
}