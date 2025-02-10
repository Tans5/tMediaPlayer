package com.tans.tmediaplayer.demo

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tans.tmediaplayer.demo.databinding.SubtitleSelectDialogBinding
import com.tans.tmediaplayer.demo.databinding.SubtitleSelectItemLayoutBinding
import com.tans.tmediaplayer.player.model.SubtitleStreamInfo
import com.tans.tmediaplayer.player.tMediaPlayer
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.dialog.BaseCoroutineStateDialogFragment
import com.tans.tuiutils.dialog.createDefaultDialog
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.flow.map

class SubtitleSelectDialog : BaseCoroutineStateDialogFragment<SubtitleSelectDialog.Companion.State> {

    private val player: tMediaPlayer?

    constructor() : super(State()) {
        this.player = null
    }

    constructor(player: tMediaPlayer) : super(State()) {
        this.player = player
    }

    override val contentViewWidthInScreenRatio: Float = 0.5f

    override val layoutId: Int = R.layout.subtitle_select_dialog

    override fun createDialog(contentView: View): Dialog {
        return requireActivity().createDefaultDialog(contentView = contentView, dimAmount = 0.0f)
    }

    override fun firstLaunchInitData() {  }

    override fun bindContentView(view: View) {
        val viewBinding = SubtitleSelectDialogBinding.bind(view)
        val player = this.player ?: return
        val subtitleStreams = player.getMediaInfo()?.subtitleStreams ?: emptyList()
        val selectedSubtitleStream = player.getSelectedSubtitleStream()
        val subtitles = (listOf(
            Subtitle(
                selected = selectedSubtitleStream == null,
                title = "None",
                streamInfo = null
            )
        ) + subtitleStreams.map {
            Subtitle(
                selected = selectedSubtitleStream == it,
                title = "[${it.metadata["language"] ?: "unknown"}] ${it.metadata["title"] ?: "Default"}",
                streamInfo = it
            )
        })
        updateState { it.copy(subtitles = subtitles) }

        fun selectSubtitle(subtitle: Subtitle) {
            player.selectSubtitleStream(subtitle.streamInfo)
            updateState { state ->
                state.copy(subtitles = state.subtitles.map {
                    if (it.streamInfo == subtitle.streamInfo) {
                        it.copy(selected = true)
                    } else {
                        it.copy(selected = false)
                    }
                })
            }
        }

        viewBinding.subtitleRv.adapter = SimpleAdapterBuilderImpl<Subtitle>(
            itemViewCreator = SingleItemViewCreatorImpl<Subtitle>(itemViewLayoutRes = R.layout.subtitle_select_item_layout),
            dataSource = FlowDataSourceImpl<Subtitle>(
                dataFlow = stateFlow().map { it.subtitles },
                areDataItemsTheSameParam = { d1, d2 -> d1.streamInfo == d2.streamInfo },
                areDataItemsContentTheSameParam = { d1, d2 -> d1 == d2 },
                getDataItemsChangePayloadParam = { d1, d2 ->
                    if (d1.streamInfo == d2.streamInfo && d1.selected != d2.selected) {
                        Unit
                    } else {
                        null
                    }
                }
            ),
            dataBinder = DataBinderImpl<Subtitle> { data, itemView, _ ->
                val itemViewBinding = SubtitleSelectItemLayoutBinding.bind(itemView)
                itemViewBinding.subtitleTv.text = data.title
            }.addPayloadDataBinder(Unit) { data, itemView, _ ->
                val itemViewBinding = SubtitleSelectItemLayoutBinding.bind(itemView)
                itemViewBinding.selectRb.isChecked = data.selected
                itemViewBinding.root.clicks(this@SubtitleSelectDialog, 1000L) {
                    if (!data.selected) {
                        selectSubtitle(data)
                    }
                }
            }
        ).build()
    }

    companion object {

        data class State(
            val subtitles: List<Subtitle> = emptyList()
        )

        data class Subtitle(
            val selected: Boolean,
            val title: String,
            val streamInfo: SubtitleStreamInfo?
        )
    }
}