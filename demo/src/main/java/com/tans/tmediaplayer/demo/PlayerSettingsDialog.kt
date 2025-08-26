package com.tans.tmediaplayer.demo

import android.app.Dialog
import android.view.View
import android.widget.SeekBar
import com.tans.tmediaplayer.demo.databinding.PlayerSettingsDialogBinding
import com.tans.tmediaplayer.player.playerview.ScaleType
import com.tans.tmediaplayer.player.playerview.filter.AsciiArtImageFilter
import com.tans.tmediaplayer.player.tMediaPlayer
import com.tans.tmediaplayer.player.tMediaPlayerState
import com.tans.tuiutils.dialog.BaseCoroutineStateDialogFragment
import com.tans.tuiutils.dialog.createDefaultDialog

class PlayerSettingsDialog : BaseCoroutineStateDialogFragment<Unit> {


    private val player: tMediaPlayer?

    constructor() : super(Unit) {
        this.player = null
    }

    constructor(player: tMediaPlayer) : super(Unit) {
        this.player = player
    }

    override val contentViewWidthInScreenRatio: Float = 0.5f

    override val layoutId: Int = R.layout.player_settings_dialog

    override fun createDialog(contentView: View): Dialog {
        return requireActivity().createDefaultDialog(contentView = contentView, dimAmount = 0.0f)
    }

    override fun firstLaunchInitData() {}

    override fun bindContentView(view: View) {
        val player = this.player ?: return
        val viewBinding = PlayerSettingsDialogBinding.bind(view)

        fun requestRender() {
            val info = player.getMediaInfo()
            val state = player.getState()
            if (info?.videoStreamInfo?.isAttachment == true
                || state is tMediaPlayerState.Paused
                || state is tMediaPlayerState.PlayEnd
                || state is tMediaPlayerState.Stopped
            ) {
                player.refreshVideoFrame()
            }
        }

        viewBinding.cropImageSw.isChecked = player.getScaleType() == ScaleType.CenterCrop
        viewBinding.cropImageSw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                player.setScaleType(ScaleType.CenterCrop)
            } else {
                player.setScaleType(ScaleType.CenterFit)
            }
            requestRender()
        }

        val asciiArtFilter: AsciiArtImageFilter = (player.getFilter() as? AsciiArtImageFilter).let {
            if (it == null) {
                val filter = AsciiArtImageFilter()
                filter.enable(false)
                player.setFilter(filter)
                filter
            } else {
                it
            }
        }
        viewBinding.asciiFilterSw.isChecked = asciiArtFilter.isEnable()
        viewBinding.asciiFilterSw.setOnCheckedChangeListener { _, isChecked ->
            asciiArtFilter.enable(isChecked)
            requestRender()
        }


        viewBinding.charReverseSw.isChecked = asciiArtFilter.isReverseChar()
        viewBinding.charReverseSw.setOnCheckedChangeListener { _, isChecked ->
            asciiArtFilter.reverseChar(isChecked)
            requestRender()
        }

        viewBinding.colorReverseSw.isChecked = asciiArtFilter.isReverseColor()
        viewBinding.colorReverseSw.setOnCheckedChangeListener { _, isChecked ->
            asciiArtFilter.reverseColor(isChecked)
            requestRender()
        }


        viewBinding.charWidthSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val requestWidth = (progress.toFloat() / 100.0f * (AsciiArtImageFilter.MAX_CHAR_LINE_WIDTH - AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH).toFloat() + AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH.toFloat() + 0.5f).toInt()
                if (fromUser) {
                    asciiArtFilter.setCharLineWidth(requestWidth)
                }
                requestRender()
                viewBinding.charWidthTv.text = "Char Width: $requestWidth"
            }
        })
        viewBinding.charWidthSb.progress = ((asciiArtFilter.getCharLineWith().toFloat() - AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH.toFloat()) / (AsciiArtImageFilter.MAX_CHAR_LINE_WIDTH - AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH).toFloat() * 100.0f + 0.5f).toInt()

        viewBinding.imageColorFillRateSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val requestRate = progress.toFloat() / 100.0f
                    asciiArtFilter.colorFillRate(requestRate)
                }
                requestRender()
                viewBinding.imageColorFillRateTv.text = "Image Color Fill Rate: $progress"
            }
        })
        viewBinding.imageColorFillRateSb.progress = (asciiArtFilter.getColorFillRate() * 100.0f + 0.5f).toInt()
    }
}