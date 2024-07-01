package com.tans.tmediaplayer.subtitle

import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicReference

internal class InternalSubtitle(val player: tMediaPlayer) {

    val subtitle: tMediaSubtitle = tMediaSubtitle(player)

    private val selectedStreamId: AtomicReference<Int?> = AtomicReference(null)

    fun selectSubtitleStream(streamId: Int?) {
        val lastStreamId = selectedStreamId.get()
        if (lastStreamId != streamId) {
            selectedStreamId.set(streamId)
            if (streamId == null) {
                subtitle.decoder.requestFlushDecoder()
            } else {
                subtitle.decoder.requestSetupSubtitleStream(streamId)
            }
        }
    }

    fun resetSubtitle() {
        selectedStreamId.set(null)
        subtitle.decoder.requestFlushDecoder()
    }

    fun enqueueSubtitlePacket() {
        val playerNative = player.getMediaInfo()?.nativePlayer
        val selectedStreamIndex = selectedStreamId.get()
        if (playerNative != null && selectedStreamIndex != null) {
            val pkt = subtitle.packetQueue.dequeueWriteableForce()
            player.movePacketRefInternal(nativePlayer = playerNative, nativePacket = pkt.nativePacket)
            val index = player.getPacketStreamIndexInternal(pkt.nativePacket)
            if (selectedStreamIndex == index) {
                subtitle.packetQueue.enqueueReadable(pkt)
                subtitle.decoder.readablePacketReady()
            } else {
                subtitle.packetQueue.enqueueWritable(pkt)
            }
        }
    }

    fun release() {
        subtitle.release()
        selectedStreamId.set(null)
    }
}