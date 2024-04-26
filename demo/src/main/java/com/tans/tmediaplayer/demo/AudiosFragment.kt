package com.tans.tmediaplayer.demo

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.tans.tmediaplayer.demo.databinding.AudioItemLayoutBinding
import com.tans.tmediaplayer.demo.databinding.AudiosFragmentBinding
import com.tans.tmediaplayer.demo.glide.MediaImageModel
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.dialog.dp2px
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import com.tans.tuiutils.mediastore.MediaStoreAudio
import com.tans.tuiutils.mediastore.queryAudioFromMediaStore
import com.tans.tuiutils.view.clicks
import com.tans.tuiutils.view.refreshes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AudiosFragment : BaseCoroutineStateFragment<AudiosFragment.Companion.State>(State()) {

    override val layoutId: Int = R.layout.audios_fragment

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        launch { refreshAudios() }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = AudiosFragmentBinding.bind(contentView)
        viewBinding.refreshLayout.refreshes(this, Dispatchers.IO) {
            refreshAudios()
        }
        val adapter = SimpleAdapterBuilderImpl<MediaStoreAudio>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.audio_item_layout),
            dataSource = FlowDataSourceImpl(stateFlow().map { it.audios }),
            dataBinder = DataBinderImpl { data, view, _ ->
                val itemViewBinding = AudioItemLayoutBinding.bind(view)
                itemViewBinding.titleTv.text = data.title
                itemViewBinding.artistAlbumTv.text = "${data.artist}-${data.album}"
                itemViewBinding.durationTv.text = data.duration.formatDuration()
                Glide.with(this@AudiosFragment)
                    .load(MediaImageModel(data.file?.canonicalPath ?: "", 0L))
                    .error(R.drawable.ic_audio)
                    .placeholder(R.drawable.ic_audio)
                    .into(itemViewBinding.audioImgIv)
                itemViewBinding.root.clicks(this) {
                    startActivity(PlayerActivity.createIntent(requireActivity(), data.file?.canonicalPath ?: ""))
                }
            }
        ).build()
        viewBinding.audiosRv.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.audiosRv) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + requireContext().dp2px(8))
            insets
        }
    }

    private fun refreshAudios() {
        val audios = queryAudioFromMediaStore().sortedByDescending { it.dateModified }.filter { it.file != null }
        updateState {
            it.copy(audios = audios)
        }
    }

    companion object {
        data class State(
            val audios: List<MediaStoreAudio> = emptyList()
        )
    }
}