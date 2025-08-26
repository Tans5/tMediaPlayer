package com.tans.tmediaplayer.player

import android.view.SurfaceView
import android.view.TextureView
import android.widget.TextView
import com.tans.tmediaplayer.player.model.MediaInfo
import com.tans.tmediaplayer.player.model.OptResult
import com.tans.tmediaplayer.player.model.SubtitleStreamInfo
import com.tans.tmediaplayer.player.playerview.ScaleType
import com.tans.tmediaplayer.player.playerview.filter.ImageFilter


internal interface IPlayer {

    fun prepare(file: String): OptResult

    fun play(): OptResult

    fun pause(): OptResult

    fun seekTo(position: Long): OptResult

    fun stop(): OptResult

    fun release(): OptResult

    fun getProgress(): Long

    fun getState(): tMediaPlayerState

    fun getMediaInfo(): MediaInfo?

    fun setListener(l: tMediaPlayerListener?)

    fun attachPlayerView(view: TextureView?)

    fun attachPlayerView(view: SurfaceView?)

    fun attachSubtitleView(view: TextView?)

    fun selectSubtitleStream(subtitle: SubtitleStreamInfo?)

    fun getSelectedSubtitleStream(): SubtitleStreamInfo?

    fun loadExternalSubtitleFile(file: String)

    fun getExternalSubtitleFile(): String?

    fun setScaleType(scaleType: ScaleType)

    fun getScaleType(): ScaleType

    fun setFilter(filter: ImageFilter?)

    fun getFilter(): ImageFilter?

    fun refreshVideoFrame()
}