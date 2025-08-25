package com.tans.tmediaplayer.subtitle

import com.tans.tmediaplayer.player.model.SubtitleStreamInfo
import com.tans.tmediaplayer.player.tMediaPlayer
import java.util.concurrent.atomic.AtomicReference

internal class InternalSubtitle(val player: tMediaPlayer) {

    private val subtitle: tMediaSubtitle = tMediaSubtitle(player)

    private val selectedSubtitleStream: AtomicReference<SubtitleStreamInfo?> = AtomicReference(null)

    fun selectSubtitleStream(subtitleStream: SubtitleStreamInfo?) {
        val streamId = subtitleStream?.streamId
        val lastStream = selectedSubtitleStream.get()
        if (lastStream != subtitleStream) {
            selectedSubtitleStream.set(subtitleStream)
            if (streamId == null) {
                subtitle.decoder.requestFlushDecoder()
            } else {
                subtitle.decoder.requestSetupInternalSubtitleStream(subtitleStream.streamId)
            }
        }
    }

    fun getSelectedSubtitleStream(): SubtitleStreamInfo? = selectedSubtitleStream.get()

    fun resetSubtitle() {
        selectedSubtitleStream.set(null)
        subtitle.decoder.requestFlushDecoder()
    }

    fun flushDecoder() {
        subtitle.decoder.requestFlushDecoder()
    }

    fun enqueueSubtitlePacket() {
        val playerNative = player.getMediaInfo()?.nativePlayer
        val selectedStreamIndex = selectedSubtitleStream.get()?.streamId
        if (playerNative != null && selectedStreamIndex != null) {
            val pkt = subtitle.packetQueue.dequeueWriteableForce()
            player.movePacketRefInternal(nativePlayer = playerNative, nativePacket = pkt.nativePacket)
            val index = player.getPacketStreamIndexInternal(pkt.nativePacket)
            if (selectedStreamIndex == index) {
                subtitle.packetQueue.enqueueReadable(pkt)
            } else {
                subtitle.packetQueue.enqueueWritable(pkt)
            }
        }
    }

    fun play() {
        subtitle.play()
    }

    fun pause() {
        subtitle.pause()
    }

    fun playerProgressUpdated(pts: Long) {
        subtitle.playerProgressUpdated(pts)
    }

    fun release() {
        subtitle.release()
        selectedSubtitleStream.set(null)
    }
}