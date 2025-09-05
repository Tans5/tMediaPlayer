package com.tans.tmediaplayer.demo

import android.app.Dialog
import android.view.View
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
import java.io.File
import java.util.Locale

class MediaInfoDialog : BaseCoroutineStateDialogFragment<Unit> {

    private val mediaInfo: MediaInfo?

    constructor() : super(Unit) {
        this.mediaInfo = null
    }

    constructor(mediaInfo: MediaInfo) : super(Unit) {
        this.mediaInfo = mediaInfo
    }

    override val contentViewWidthInScreenRatio: Float = 0.5f

    override val layoutId: Int = R.layout.media_info_dialog

    override fun createDialog(contentView: View): Dialog {
        return requireActivity().createDefaultDialog(contentView = contentView, dimAmount = 0.0f)
    }

    override fun firstLaunchInitData() {

    }

    override fun bindContentView(view: View) {
        val mediaInfo = this.mediaInfo ?: return
        val viewBinding = MediaInfoDialogBinding.bind(view)

        viewBinding.fileRv.adapter = SimpleAdapterBuilderImpl<String>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.media_info_item_layout),
            dataSource = FlowDataSourceImpl(flow {
                val result = mutableListOf<String>()
                result.add("FilePath: ${mediaInfo.file}")
                val f = File(mediaInfo.file)
                if (f.isFile && f.canRead()) {
                    val fileSizeStr = f.length().toSizeString()
                    result.add("FileSize: $fileSizeStr")
                }
                result.add("FileFormat: ${mediaInfo.containerName}")
                if (mediaInfo.metadata.isNotEmpty()) {
                    result.add("")
                    result.add("Metadata: ")
                    for ((key, value) in mediaInfo.metadata) {
                        result.add(" $key: $value")
                    }
                }
                emit(result)
            }),
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
                    result.add("Decoder: ${it.videoDecoderName}")
                    result.add("Codec: ${it.videoCodec.name}")
                    result.add("Resolution: ${it.videoWidth}x${it.videoHeight}")
                    result.add("Fps: ${String.format(Locale.US, "%.1f", it.videoFps)}")
                    result.add("Bitrate: ${it.videoBitrate / 1024} kbps")
                    result.add("PixelDepth: ${it.videoPixelBitDepth} bits")
                    result.add("PixelFormat: ${it.videoPixelFormat.name}")
                    result.add("DisplayRotation: ${it.videoDisplayRotation}")
                    result.add("DisplayRatio: ${String.format(Locale.US, "%.3f", it.videoDisplayRatio)}")
                    if (it.videoStreamMetadata.isNotEmpty()) {
                        result.add("")
                        result.add("Metadata: ")
                        for ((key, value) in it.videoStreamMetadata) {
                            result.add(" $key: $value")
                        }
                    }
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
                    result.add("Decoder: ${it.audioDecoderName}")
                    result.add("Codec: ${it.audioCodec.name}")
                    result.add("Channels: ${it.audioChannels}")
                    result.add("SimpleRate: ${it.audioSimpleRate} Hz")
                    result.add("Bitrate: ${it.audioBitrate / 1024} kbps")
                    result.add("SimpleDepth: ${it.audioSampleBitDepth} bits")
                    result.add("SimpleFormat: ${it.audioSampleFormat.name}")
                    if (it.audioStreamMetadata.isNotEmpty()) {
                        result.add("")
                        result.add("Metadata: ")
                        for ((key, value) in it.audioStreamMetadata) {
                            result.add(" $key: $value")
                        }
                    }
                }
                emit(result)
            }),
            dataBinder = DataBinderImpl { data, itemView, _ ->
                val itemViewBinding = MediaInfoItemLayoutBinding.bind(itemView)
                itemViewBinding.keyValueTv.text = data
            }
        ).build()

        viewBinding.subtitlesRv.adapter = SimpleAdapterBuilderImpl<String>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.media_info_item_layout),
            dataSource = FlowDataSourceImpl(flow {
                val result = mutableListOf<String>()
                for (subtitle in mediaInfo.subtitleStreams) {
                    for ((key, value) in subtitle.metadata) {
                        result.add("$key: $value")
                    }
                    result.add("")
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