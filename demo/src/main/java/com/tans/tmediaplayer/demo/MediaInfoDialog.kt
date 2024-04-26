package com.tans.tmediaplayer.demo

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tans.tmediaplayer.demo.databinding.MediaInfoDialogBinding
import com.tans.tmediaplayer.demo.databinding.MediaInfoItemLayoutBinding
import com.tans.tmediaplayer.player.model.MediaInfo
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.dialog.BaseCoroutineStateDialogFragment
import com.tans.tuiutils.dialog.createDefaultDialog
import kotlinx.coroutines.flow.flow

class MediaInfoDialog : BaseCoroutineStateDialogFragment<Unit> {

    private val mediaInfo: MediaInfo?

    constructor() : super(Unit) {
        this.mediaInfo = null
    }

    constructor(mediaInfo: MediaInfo) : super(Unit) {
        this.mediaInfo = mediaInfo
    }

    override val contentViewWidthInScreenRatio: Float = 0.5f

    override fun createContentView(context: Context, parent: ViewGroup): View {
       return LayoutInflater.from(context).inflate(R.layout.media_info_dialog, parent, false)
    }

    override fun createDialog(contentView: View): Dialog {
        return requireActivity().createDefaultDialog(contentView = contentView, dimAmount = 0.0f)
    }

    override fun firstLaunchInitData() {

    }

    override fun bindContentView(view: View) {
        val mediaInfo = this.mediaInfo ?: return
        val viewBinding = MediaInfoDialogBinding.bind(view)

        viewBinding.metadataRv.adapter = SimpleAdapterBuilderImpl<String>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.media_info_item_layout),
            dataSource = FlowDataSourceImpl(flow { emit(mediaInfo.metadata.map { "${it.key}: ${it.value}" }) }),
            dataBinder = DataBinderImpl { data, itemView, _ ->
                val itemViewBinding = MediaInfoItemLayoutBinding.bind(itemView)
                itemViewBinding.keyValueTv.text = data
            }
        ).build()

        viewBinding.videoRv.adapter = SimpleAdapterBuilderImpl<String>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.media_info_item_layout),
            dataSource = FlowDataSourceImpl(flow {
                val result = mutableListOf<String>()
                mediaInfo.videoStreamInfo?.let {
                    result.add("Codec: ${it.videoCodec}")
                    result.add("Resolution: ${it.videoWidth}x${it.videoHeight}")
                    result.add("Fps: ${String.format("%.1f", it.videoFps)}")
                }
                emit(result)
            }),
            dataBinder = DataBinderImpl { data, itemView, _ ->
                val itemViewBinding = MediaInfoItemLayoutBinding.bind(itemView)
                itemViewBinding.keyValueTv.text = data
            }
        ).build()

        viewBinding.audioRv.adapter = SimpleAdapterBuilderImpl<String>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.media_info_item_layout),
            dataSource = FlowDataSourceImpl(flow {
                val result = mutableListOf<String>()
                mediaInfo.audioStreamInfo?.let {
                    result.add("Codec: ${it.audioCodec}")
                    result.add("Channels: ${it.audioChannels}")
                    result.add("SimpleRate: ${it.audioSimpleRate} Hz")
                    result.add("PerSimpleBytes: ${it.audioPerSampleBytes} Bytes")
                }
                emit(result)
            }),
            dataBinder = DataBinderImpl { data, itemView, _ ->
                val itemViewBinding = MediaInfoItemLayoutBinding.bind(itemView)
                itemViewBinding.keyValueTv.text = data
            }
        ).build()
    }


}