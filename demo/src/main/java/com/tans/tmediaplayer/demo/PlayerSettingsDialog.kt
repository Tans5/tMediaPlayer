package com.tans.tmediaplayer.demo

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.tans.tmediaplayer.demo.databinding.PlayerSettingsDialogBinding
import com.tans.tmediaplayer.player.render.filter.AsciiArtImageFilter
import com.tans.tmediaplayer.player.render.tMediaPlayerView
import com.tans.tmediaplayer.player.tMediaPlayer
import com.tans.tuiutils.dialog.BaseCoroutineStateDialogFragment
import com.tans.tuiutils.dialog.createDefaultDialog

class PlayerSettingsDialog : BaseCoroutineStateDialogFragment<Unit> {

    private val playerView: tMediaPlayerView?

    constructor() : super(Unit) {
        this.playerView = null
    }

    constructor(playerView: tMediaPlayerView) : super(Unit) {
        this.playerView = playerView
    }

    override val contentViewWidthInScreenRatio: Float = 0.5f

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.player_settings_dialog, parent, false)
    }

    override fun createDialog(contentView: View): Dialog {
        return requireActivity().createDefaultDialog(contentView = contentView, dimAmount = 0.0f)
    }

    override fun firstLaunchInitData() {}

    override fun bindContentView(view: View) {
        val playerView = this.playerView ?: return
        val viewBinding = PlayerSettingsDialogBinding.bind(view)

        viewBinding.cropImageSw.isChecked = playerView.getScaleType() == tMediaPlayerView.Companion.ScaleType.CenterCrop
        viewBinding.cropImageSw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                playerView.setScaleType(tMediaPlayerView.Companion.ScaleType.CenterCrop)
            } else {
                playerView.setScaleType(tMediaPlayerView.Companion.ScaleType.CenterFit)
            }
            playerView.requestRender()
        }

        val asciiArtFilter = playerView.getAsciiArtImageFilter()
        viewBinding.asciiFilterSw.isChecked = asciiArtFilter.isEnable()
        viewBinding.asciiFilterSw.setOnCheckedChangeListener { _, isChecked ->
            playerView.enableAsciiArtFilter(isChecked)
            playerView.requestRender()
        }


        viewBinding.charReverseSw.isChecked = asciiArtFilter.isReverseChar()
        viewBinding.charReverseSw.setOnCheckedChangeListener { _, isChecked ->
            asciiArtFilter.reverseChar(isChecked)
            playerView.requestRender()
        }

        viewBinding.colorReverseSw.isChecked = asciiArtFilter.isReverseColor()
        viewBinding.colorReverseSw.setOnCheckedChangeListener { _, isChecked ->
            asciiArtFilter.reverseColor(isChecked)
            playerView.requestRender()
        }


        viewBinding.charWidthSb.progress = ((asciiArtFilter.getCharLineWith().toFloat() - AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH.toFloat()) / (AsciiArtImageFilter.MAX_CHAR_LINE_WIDTH - AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH).toFloat() * 100.0f).toInt()
        viewBinding.charWidthSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val requestWidth = (progress.toFloat() / 100.0f * (AsciiArtImageFilter.MAX_CHAR_LINE_WIDTH - AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH).toFloat() + AsciiArtImageFilter.MIN_CHAR_LINE_WIDTH.toFloat()).toInt()
                if (fromUser) {
                    asciiArtFilter.setCharLineWidth(requestWidth)
                    playerView.requestRender()
                }
                viewBinding.charWidthTv.text = "Char Width: $requestWidth"
            }
        })

        viewBinding.imageColorFillRateSb.progress = (asciiArtFilter.getColorFillRate() * 100.0f).toInt()
        viewBinding.imageColorFillRateSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val requestRate = progress.toFloat() / 100.0f
                    asciiArtFilter.colorFillRate(requestRate)
                    playerView.requestRender()
                }
                viewBinding.imageColorFillRateTv.text = "Image Color Fill Rate: $progress"
            }
        })
    }
}